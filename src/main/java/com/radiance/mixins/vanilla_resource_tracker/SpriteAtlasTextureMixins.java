package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.proxy.world.BlockModelBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.v2.bridge.EngineBridge;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memPutByte;

@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixins extends AbstractTextureMixins {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpriteAtlasTexture");

    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/Sprite;upload()V"))
    public void setImageTargetIDBeforeUpload(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci, @Local Sprite sprite) {
        int id = getGlId();
        ((ISpriteExt) sprite).neoVoxelRT$setTargetID(id);
    }

    /**
     * After all sprites are uploaded to the atlas, extract sprite data and send to C++.
     * Minimal Java role: sort, build metadata, send raw pixels. C++ owns the rest.
     */
    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At("RETURN"))
    public void extractSpritesForTextureArrays(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci) {
        // Only process the block atlas
        SpriteAtlasTexture self = (SpriteAtlasTexture) (Object) this;
        Identifier atlasId = self.getId();
        if (!atlasId.getPath().contains("blocks")) {
            return;
        }

        Map<Identifier, Sprite> regions = stitchResult.regions();
        if (regions == null || regions.isEmpty()) return;

        // Force model table re-serialization on next chunk build.
        // Don't call reset() — it clears spriteIdLookup, creating a race with chunk threads.
        // The sortedSpriteIds reference is atomically replaced at line 76 below.

        int atlasW = stitchResult.width();
        int atlasH = stitchResult.height();

        LOGGER.info("[TextureSystem] Processing block atlas: {} sprites, {}x{}", regions.size(), atlasW, atlasH);

        // ---- Step 1: Sort sprites alphabetically for deterministic spriteId assignment ----
        List<Map.Entry<Identifier, Sprite>> sorted = new ArrayList<>(regions.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));

        // Build the sorted identifier list for BlockModelBridge.serializeQuad() to use
        List<Identifier> sortedIds = new ArrayList<>(sorted.size());
        for (var entry : sorted) {
            sortedIds.add(entry.getKey());
        }
        BlockModelBridge.sortedSpriteIds = sortedIds;
        BlockModelBridge.markForReupload(); // Force model table re-serialization with new IDs

        // Task 1.4 diagnostic (copper-grate hunt): dump the first 16 sorted sprite
        // identifiers + their (would-be) spriteId index, and specifically locate
        // minecraft:block/copper_grate so the C++ DIAG reg[] output (which logs
        // atlasXY per registry entry) can be cross-referenced with the shader's
        // dominant textureID to confirm or refute H-A.
        {
            int n = Math.min(16, sortedIds.size());
            StringBuilder head = new StringBuilder();
            for (int i = 0; i < n; i++) {
                if (i > 0) head.append(", ");
                head.append(i).append('=').append(sortedIds.get(i).toString());
            }
            LOGGER.warn("[TextureSystem] DIAG sortedSpriteIds size={} head[0..{}]={}",
                sortedIds.size(), n - 1, head);

            Identifier copperGrate = Identifier.of("minecraft", "block/copper_grate");
            int copperGrateIdx = sortedIds.indexOf(copperGrate);
            LOGGER.warn("[TextureSystem] DIAG copper_grate sprite index = {} (-1 means not in block atlas)",
                copperGrateIdx);

            // Also log all copper_* sprites since the visual symptom shows a weathered
            // green patina — could be exposed_copper_grate, weathered_copper_grate,
            // oxidized_copper_grate, or the base copper_grate.
            StringBuilder coppers = new StringBuilder();
            int copperCount = 0;
            for (int i = 0; i < sortedIds.size(); i++) {
                String s = sortedIds.get(i).getPath();
                if (s.contains("copper")) {
                    if (copperCount++ > 0) coppers.append(", ");
                    coppers.append(i).append('=').append(s);
                    if (copperCount >= 24) { coppers.append(", ..."); break; }
                }
            }
            LOGGER.warn("[TextureSystem] DIAG copper_* sprites: {}", coppers);
        }


        // ---- Step 1B: Build spriteId ↔ materialOrdinal mapping for CPU Auto-PBR ----
        BlockModelBridge.spriteId2MaterialOrdinal.clear();
        BlockModelBridge.materialOrdinal2SpriteIds.clear();
        for (int i = 0; i < sortedIds.size(); i++) {
            int ord = com.radiance.client.util.MaterialBlock.getOrdinalForTexture(sortedIds.get(i).getPath());
            if (ord >= 0) {
                BlockModelBridge.spriteId2MaterialOrdinal.put(i, ord);
                BlockModelBridge.materialOrdinal2SpriteIds
                    .computeIfAbsent(ord, k -> new java.util.HashSet<>()).add(i);
            }
        }
        LOGGER.info("[TextureSystem] Mapped {} sprites to {} materials",
            BlockModelBridge.spriteId2MaterialOrdinal.size(),
            BlockModelBridge.materialOrdinal2SpriteIds.size());

        // ---- Step 1C: Cache per-sprite albedo images for LiveNormalReuploader ----
        // The atlas-level albedo cache (materialBlockAlbedoCache) is atlas-sized and keyed by
        // atlas GLID — useless for texture array live re-upload which needs sprite-sized images.
        // Cache a copy of each material block sprite's NativeImage keyed by spriteId.
        TextureTracker.spriteAlbedoCache.clear();
        for (var mapEntry : BlockModelBridge.spriteId2MaterialOrdinal.entrySet()) {
            int si = mapEntry.getKey();
            if (si >= sorted.size()) continue;
            Sprite sp = sorted.get(si).getValue();
            NativeImage img = ((ISpriteContentsExt) sp.getContents()).neoVoxelRT$getImage();
            if (img == null) continue;
            TextureTracker.spriteAlbedoCache.put(si, img.applyToCopy(i -> i));
        }
        LOGGER.info("[TextureSystem] Cached {} sprite albedos for live re-upload",
            TextureTracker.spriteAlbedoCache.size());

        // Detect sprite size (all block sprites should be uniform)
        int spriteSize = 0;
        for (var entry : sorted) {
            int w = entry.getValue().getContents().getWidth();
            if (spriteSize == 0) spriteSize = w;
            break;
        }
        if (spriteSize == 0) return;

        int count = sorted.size();
        int bytesPerSprite = spriteSize * spriteSize * 4; // RGBA8

        // ---- Step 2: Detect overlay sprites (grass_block_side_overlay → grass_block_side) ----
        // overlayOf[i] = spriteId that sprite i is an overlay FOR, or -1
        short[] overlayOf = new short[count];
        java.util.Arrays.fill(overlayOf, (short) -1);
        for (int i = 0; i < count; i++) {
            String name = sorted.get(i).getKey().toString();
            if (name.endsWith("_overlay")) {
                String baseName = name.substring(0, name.length() - "_overlay".length());
                for (int j = 0; j < count; j++) {
                    if (sorted.get(j).getKey().toString().equals(baseName)) {
                        overlayOf[i] = (short) j;
                        break;
                    }
                }
            }
        }

        // ---- Step 3: Build metadata table (SpriteMetadata = 16 bytes, packed) ----
        int META_SIZE = 16;
        ByteBuffer metaBuf = ByteBuffer.allocateDirect(count * META_SIZE)
            .order(ByteOrder.nativeOrder());

        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();

            int w = contents.getWidth();
            int h = contents.getHeight();
            int imgH = (img != null) ? img.getHeight() : h;
            int frameCount = Math.max(1, imgH / h);

            // Compute atlas pixel position from UV coordinates
            int atlasX = Math.round(sprite.getMinU() * atlasW);
            int atlasY = Math.round(sprite.getMinV() * atlasH);

            int off = i * META_SIZE;
            metaBuf.putShort(off +  0, (short) atlasX);
            metaBuf.putShort(off +  2, (short) atlasY);
            metaBuf.putShort(off +  4, (short) w);
            metaBuf.putShort(off +  6, (short) h);
            metaBuf.putShort(off +  8, (short) frameCount);
            metaBuf.putShort(off + 10, (short) 3); // tickRate (~3 render frames per anim advance)
            metaBuf.putShort(off + 12, overlayOf[i]);
            // Flags: bit 0 = hasSpecular, bit 1 = hasNormal
            short flags = 0;
            if (img != null) {
                INativeImageExt auxExt = (INativeImageExt) (Object) img;
                if (auxExt.neoVoxelRT$getSpecularNativeImage() != null) flags |= 1;
                if (auxExt.neoVoxelRT$getNormalNativeImage() != null) flags |= 2;
            }
            metaBuf.putShort(off + 14, flags);
        }

        if (!EngineBridge.isV2Active()) BlockModelBridge.nativeReceiveSpriteTable(memAddress(metaBuf), count, atlasW, atlasH);
        if (EngineBridge.isV2Active()) {
            EngineBridge.submitSpriteTable(memAddress(metaBuf), count, atlasW, atlasH, spriteSize);
        }
        LOGGER.info("[TextureSystem] Sent sprite table: {} entries", count);

        // ---- Step 4: Build pixel bulk buffer (frame 0 for each sprite, RGBA8) ----
        // NativeImage stores RGBA bytes natively (STB format). We can raw-copy.
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(count * bytesPerSprite)
            .order(ByteOrder.nativeOrder());

        int uploaded = 0;
        int animatedCount = 0;
        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();

            int w = contents.getWidth();
            int h = contents.getHeight();

            if (img != null && w == spriteSize && h == spriteSize) {
                // Raw copy frame 0 from NativeImage (RGBA8 bytes)
                long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                    (Object) img).neoVoxelRT$getPointer();
                long dstPtr = memAddress(pixelBuf) + (long) i * bytesPerSprite;
                memCopy(srcPtr, dstPtr, bytesPerSprite);
                uploaded++;
            } else if (img != null) {
                // Size mismatch — extract per-pixel with conversion
                long dstBase = memAddress(pixelBuf) + (long) i * bytesPerSprite;
                extractPixelsManual(img, w, h, spriteSize, dstBase);
                uploaded++;
            }
            // else: leave zeroed (black transparent)

            int imgH = (img != null) ? img.getHeight() : h;
            if (imgH > h) animatedCount++;
        }

        if (!EngineBridge.isV2Active()) BlockModelBridge.nativeReceiveSpritePixels(memAddress(pixelBuf), count * bytesPerSprite);
        if (EngineBridge.isV2Active()) {
            EngineBridge.submitSpritePixels(memAddress(pixelBuf), count * bytesPerSprite);
        }
        LOGGER.info("[TextureSystem] Sent {} sprite pixels ({} KB), {} animated",
            uploaded, (count * bytesPerSprite) / 1024, animatedCount);

        // ---- Step 4B: Build specular + normal pixel buffers for texture arrays ----
        ByteBuffer specPixelBuf = ByteBuffer.allocateDirect(count * bytesPerSprite)
            .order(ByteOrder.nativeOrder());
        ByteBuffer normalPixelBuf = ByteBuffer.allocateDirect(count * bytesPerSprite)
            .order(ByteOrder.nativeOrder());

        // Fill normal default: (128, 128, 255, 255) = flat normal, AO=1.0, height=1.0
        {
            long normBase = memAddress(normalPixelBuf);
            for (int px = 0; px < count * spriteSize * spriteSize; px++) {
                long off = normBase + (long) px * 4;
                memPutByte(off,     (byte) 128); // R: normal X = 0.5
                memPutByte(off + 1, (byte) 128); // G: normal Y = 0.5
                memPutByte(off + 2, (byte) 255); // B: AO = 1.0
                memPutByte(off + 3, (byte) 255); // A: height = 1.0
            }
        }
        // Specular default is all zeros (roughness=1.0, F0=0.02) — already zeroed by allocateDirect

        int specCount = 0, normalCount = 0;
        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;

            INativeImageExt auxExt = (INativeImageExt) (Object) img;
            int w = contents.getWidth();
            int h = contents.getHeight();

            // Specular (_s) texture
            NativeImage specImg = auxExt.neoVoxelRT$getSpecularNativeImage();
            if (specImg != null) {
                long dstPtr = memAddress(specPixelBuf) + (long) i * bytesPerSprite;
                int specW = specImg.getWidth();
                int specH = specImg.getHeight();
                if (specW == spriteSize && specH == spriteSize) {
                    long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                        (Object) specImg).neoVoxelRT$getPointer();
                    memCopy(srcPtr, dstPtr, bytesPerSprite);
                } else {
                    extractPixelsManual(specImg, specW, specH, spriteSize, dstPtr);
                }
                specCount++;
            }

            // Normal (_n) texture
            NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
            if (normalImg != null) {
                long dstPtr = memAddress(normalPixelBuf) + (long) i * bytesPerSprite;
                int normW = normalImg.getWidth();
                int normH = normalImg.getHeight();
                if (normW == spriteSize && normH == spriteSize) {
                    long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                        (Object) normalImg).neoVoxelRT$getPointer();
                    memCopy(srcPtr, dstPtr, bytesPerSprite);
                } else {
                    extractPixelsManual(normalImg, normW, normH, spriteSize, dstPtr);
                }
                normalCount++;
            }
        }

        // ---- Step 4C: CPU-bake Auto-PBR specular for registered material blocks ----
        // Overwrite zeroed specular data with real roughness computed from albedo luminance
        int bakedCount = 0;
        for (var mapEntry : BlockModelBridge.spriteId2MaterialOrdinal.entrySet()) {
            int si = mapEntry.getKey();
            int ordinal = mapEntry.getValue();
            if (!com.radiance.client.option.Options.autoPBREnabled) continue;
            if (!com.radiance.client.option.Options.materialAutoPBR[ordinal]) continue;
            if (com.radiance.client.option.Options.materialSpecularInputType[ordinal] != 0) continue;
            // Skip transmissive blocks — roughness handled by material class slider
            if (com.radiance.client.option.Options.materialTransmission[ordinal] > 0) continue;
            if (si >= count) continue;

            Sprite sp = sorted.get(si).getValue();
            NativeImage albedo = ((ISpriteContentsExt) sp.getContents()).neoVoxelRT$getImage();
            if (albedo == null) continue;
            int sw = sp.getContents().getWidth();
            int sh = sp.getContents().getHeight();
            if (sw != spriteSize || sh != spriteSize) continue;

            NativeImage specBaked = com.radiance.client.texture.AutoPBRGenerator.generateSpecularPercentile(
                albedo,
                com.radiance.client.option.Options.materialAutoPBRRoughnessMin[ordinal],
                com.radiance.client.option.Options.materialAutoPBRRoughnessMax[ordinal],
                com.radiance.client.option.Options.materialPercentileCenter[ordinal],
                com.radiance.client.option.Options.materialPercentileSpread[ordinal],
                (com.radiance.client.option.Options.materialAutoPBRFlags[ordinal] & 1) != 0);

            long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                (Object) specBaked).neoVoxelRT$getPointer();
            long dstPtr = memAddress(specPixelBuf) + (long) si * bytesPerSprite;
            memCopy(srcPtr, dstPtr, bytesPerSprite);
            specBaked.close();
            bakedCount++;
        }
        if (bakedCount > 0) {
            LOGGER.info("[TextureSystem] CPU-baked {} Auto-PBR specular sprites", bakedCount);
        }

        // ---- Step 4D: Re-bake Auto-PBR normals with neutral strength for texture arrays ----
        // The shader applies runtime normal strength (pack5.w), so baked normals must use
        // strength=1.0 (100) to avoid double-application. Preserves invertNormal/invertHeight.
        int normalBakedCount = 0;
        for (var mapEntry : BlockModelBridge.spriteId2MaterialOrdinal.entrySet()) {
            int si = mapEntry.getKey();
            int ordinal = mapEntry.getValue();
            if (!com.radiance.client.option.Options.autoPBREnabled) continue;
            if (!com.radiance.client.option.Options.materialAutoPBR[ordinal]) continue;
            if (com.radiance.client.option.Options.materialNormalInputType[ordinal] != 0) continue;
            if (si >= count) continue;

            Sprite sp = sorted.get(si).getValue();
            NativeImage albedo = ((ISpriteContentsExt) sp.getContents()).neoVoxelRT$getImage();
            if (albedo == null) continue;
            int sw = sp.getContents().getWidth();
            int sh = sp.getContents().getHeight();
            if (sw != spriteSize || sh != spriteSize) continue;

            NativeImage normalBaked = com.radiance.client.texture.AutoPBRGenerator.generateNormal(
                albedo,
                100, // neutral strength — shader applies runtime value from pack5.w
                (com.radiance.client.option.Options.materialAutoPBRFlags[ordinal] & 2) != 0,
                com.radiance.client.option.Options.materialAutoPBRHeightGamma[ordinal],
                (com.radiance.client.option.Options.materialAutoPBRFlags[ordinal] & 4) != 0);

            long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                (Object) normalBaked).neoVoxelRT$getPointer();
            long dstPtr = memAddress(normalPixelBuf) + (long) si * bytesPerSprite;
            memCopy(srcPtr, dstPtr, bytesPerSprite);
            normalBaked.close();
            normalBakedCount++;
        }
        if (normalBakedCount > 0) {
            LOGGER.info("[TextureSystem] CPU-baked {} Auto-PBR normal sprites (neutral strength)", normalBakedCount);
        }

        if (!EngineBridge.isV2Active()) BlockModelBridge.nativeReceiveSpriteAuxPixels(
            memAddress(specPixelBuf), memAddress(normalPixelBuf), count * bytesPerSprite);
        if (EngineBridge.isV2Active()) {
            EngineBridge.submitSpriteAuxPixels(memAddress(specPixelBuf), memAddress(normalPixelBuf), count * bytesPerSprite);
        }
        LOGGER.info("[TextureSystem] Sent aux pixels: {} specular, {} normal ({} KB each)",
            specCount, normalCount, (count * bytesPerSprite) / 1024);

        // ---- Step 5: Build animation frame data ----
        // Format: [spriteId(u16), frameIndex(u16), pixels(w*h*4)] repeated
        // Only for sprites with frameCount > 1
        int animDataSize = 0;
        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;
            int h = contents.getHeight();
            int imgH = img.getHeight();
            int frameCount = Math.max(1, imgH / h);
            if (frameCount > 1) {
                animDataSize += frameCount * (4 + bytesPerSprite); // header + pixels per frame
            }
        }

        if (animDataSize > 0) {
            ByteBuffer animBuf = ByteBuffer.allocateDirect(animDataSize)
                .order(ByteOrder.nativeOrder());
            int animOffset = 0;

            for (int i = 0; i < count; i++) {
                Sprite sprite = sorted.get(i).getValue();
                SpriteContents contents = sprite.getContents();
                NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
                if (img == null) continue;

                int w = contents.getWidth();
                int h = contents.getHeight();
                int imgH = img.getHeight();
                int frameCount = Math.max(1, imgH / h);
                if (frameCount <= 1) continue;

                long srcBase = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                    (Object) img).neoVoxelRT$getPointer();
                int srcRowBytes = img.getWidth() * 4; // NativeImage may have different width

                for (int frame = 0; frame < frameCount; frame++) {
                    // Header: spriteId + frameIndex
                    animBuf.putShort(animOffset, (short) i);
                    animBuf.putShort(animOffset + 2, (short) frame);
                    animOffset += 4;

                    // Pixel data for this frame (raw copy from NativeImage)
                    if (w == spriteSize && srcRowBytes == w * 4) {
                        // Fast path: contiguous, correct width
                        long frameSrc = srcBase + (long) frame * h * srcRowBytes;
                        memCopy(frameSrc, memAddress(animBuf) + animOffset, bytesPerSprite);
                    } else {
                        // Row-by-row copy (different NativeImage width)
                        for (int y = 0; y < Math.min(h, spriteSize); y++) {
                            long rowSrc = srcBase + ((long) frame * h + y) * srcRowBytes;
                            long rowDst = memAddress(animBuf) + animOffset + (long) y * spriteSize * 4;
                            int copyBytes = Math.min(w, spriteSize) * 4;
                            memCopy(rowSrc, rowDst, copyBytes);
                        }
                    }
                    animOffset += bytesPerSprite;
                }
            }

            if (!EngineBridge.isV2Active()) BlockModelBridge.nativeReceiveAnimationFrames(memAddress(animBuf), animOffset);
            if (EngineBridge.isV2Active()) {
                EngineBridge.submitAnimationFrames(memAddress(animBuf), animOffset);
            }
            LOGGER.info("[TextureSystem] Sent {} bytes of animation data", animOffset);
        } else {
            // No animations — send empty
            if (!EngineBridge.isV2Active()) BlockModelBridge.nativeReceiveAnimationFrames(0, 0);
            if (EngineBridge.isV2Active()) {
                EngineBridge.submitAnimationFrames(0, 0);
            }
        }

        // ---- Step 6: Finalize ----
        if (!EngineBridge.isV2Active()) BlockModelBridge.nativeTextureFinalize();
        if (EngineBridge.isV2Active()) {
            EngineBridge.textureFinalize();
        }
        LOGGER.info("[TextureSystem] Finalized. {} sprites ({} animated)", count, animatedCount);
    }

    /** Manual pixel extraction for size-mismatched sprites (rare). */
    private static void extractPixelsManual(NativeImage img, int srcW, int srcH,
                                             int dstSize, long dstPtr) {
        int copyW = Math.min(srcW, dstSize);
        int copyH = Math.min(srcH, dstSize);
        for (int y = 0; y < copyH; y++) {
            for (int x = 0; x < copyW; x++) {
                int argb = img.getColorArgb(x, y);
                int offset = (y * dstSize + x) * 4;
                org.lwjgl.system.MemoryUtil.memPutByte(dstPtr + offset,     (byte) ((argb >> 16) & 0xFF)); // R
                org.lwjgl.system.MemoryUtil.memPutByte(dstPtr + offset + 1, (byte) ((argb >> 8) & 0xFF));  // G
                org.lwjgl.system.MemoryUtil.memPutByte(dstPtr + offset + 2, (byte) (argb & 0xFF));         // B
                org.lwjgl.system.MemoryUtil.memPutByte(dstPtr + offset + 3, (byte) ((argb >> 24) & 0xFF)); // A
            }
        }
    }
}
