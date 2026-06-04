package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.autopbr.AutoPbrRuntime;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.VanillaTextureManifest;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
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

        // Build the sorted identifier list for TextureArrayBridge.serializeQuad() to use
        List<Identifier> sortedIds = new ArrayList<>(sorted.size());
        for (var entry : sorted) {
            sortedIds.add(entry.getKey());
        }

        VanillaTextureManifest manifest =
            VanillaTextureManifest.fromBlockAtlas(atlasId, sorted, atlasW, atlasH);
        manifest.writeDebugDump(MinecraftClient.getInstance().runDirectory.toPath());
        LOGGER.info("[TextureRefactor] Vanilla texture manifest: {}", manifest.summary());
        int warningLimit = Math.min(manifest.warnings().size(), 32);
        for (int i = 0; i < warningLimit; i++) {
            LOGGER.warn("[TextureRefactor] {}", manifest.warnings().get(i));
        }
        if (manifest.warnings().size() > warningLimit) {
            LOGGER.warn("[TextureRefactor] {} additional manifest warnings written to texture_manifest.json",
                manifest.warnings().size() - warningLimit);
        }
        if (!manifest.isValid()) {
            for (String error : manifest.errors()) {
                LOGGER.error("[TextureRefactor] {}", error);
            }
            LOGGER.error("[TextureRefactor] Aborting texture-array extraction for invalid vanilla manifest");
            return;
        }

        TextureArrayBridge.setSortedSpriteIds(sortedIds);
        TextureArrayBridge.incrementTextureGeneration();
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
        TextureTracker.currentSpriteLayerSize = spriteSize;

        TextureTracker.resetSpriteAuxSources(count);
        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            NativeImage img = ((ISpriteContentsExt) sprite.getContents()).neoVoxelRT$getImage();
            if (img == null) continue;

            INativeImageExt auxExt = (INativeImageExt) (Object) img;
            NativeImage specImg = auxExt.neoVoxelRT$getSpecularNativeImage();
            if (specImg != null) {
                TextureTracker.spriteSpecularSource[i] =
                    ((INativeImageExt) (Object) specImg).neoVoxelRT$getAuxSource();
            }

            NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
            if (normalImg != null) {
                TextureTracker.spriteNormalSource[i] =
                    ((INativeImageExt) (Object) normalImg).neoVoxelRT$getAuxSource();
            }
        }

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
            // Flags: aux presence, authored/generated source, and whether normal alpha is height.
            int flags = TextureTracker.encodeSpriteSourceFlags(
                TextureTracker.spriteSpecularSource[i],
                TextureTracker.spriteNormalSource[i]);
            if (img != null) {
                INativeImageExt auxExt = (INativeImageExt) (Object) img;
                NativeImage specImg = auxExt.neoVoxelRT$getSpecularNativeImage();
                NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
                byte normalSource = TextureTracker.spriteNormalSource[i];
                boolean authoredHeight = normalSource == TextureTracker.SOURCE_PACK_AUTHORED
                    || normalSource == TextureTracker.SOURCE_USER_CUSTOM;
                if (specImg != null) flags |= TextureTracker.SPRITE_FLAG_HAS_SPECULAR;
                if (normalImg != null) flags |= TextureTracker.SPRITE_FLAG_HAS_NORMAL;
                if (normalImg != null && authoredHeight) {
                    flags |= TextureTracker.SPRITE_FLAG_HAS_HEIGHT;
                }
            }
            metaBuf.putShort(off + 14, (short) flags);
        }

        TextureArrayBridge.nativeReceiveSpriteTable(memAddress(metaBuf), count, atlasW, atlasH);
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
                TextureTracker.spriteAlbedoCache.put(i, copySpriteImage(img, w, h, spriteSize));
                // Raw copy frame 0 from NativeImage (RGBA8 bytes)
                long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                    (Object) img).neoVoxelRT$getPointer();
                long dstPtr = memAddress(pixelBuf) + (long) i * bytesPerSprite;
                memCopy(srcPtr, dstPtr, bytesPerSprite);
                uploaded++;
            } else if (img != null) {
                TextureTracker.spriteAlbedoCache.put(i, copySpriteImage(img, w, h, spriteSize));
                // Size mismatch — extract per-pixel with conversion
                long dstBase = memAddress(pixelBuf) + (long) i * bytesPerSprite;
                extractPixelsManual(img, w, h, spriteSize, dstBase);
                uploaded++;
            }
            // else: leave zeroed (black transparent)

            int imgH = (img != null) ? img.getHeight() : h;
            if (imgH > h) animatedCount++;
        }

        TextureArrayBridge.nativeReceiveSpritePixels(memAddress(pixelBuf), count * bytesPerSprite);
        LOGGER.info("[TextureSystem] Sent {} sprite pixels ({} KB), {} animated",
            uploaded, (count * bytesPerSprite) / 1024, animatedCount);

        // ---- Step 4B: Build specular + normal + flag pixel buffers for texture arrays ----
        ByteBuffer specPixelBuf = ByteBuffer.allocateDirect(count * bytesPerSprite)
            .order(ByteOrder.nativeOrder());
        ByteBuffer normalPixelBuf = ByteBuffer.allocateDirect(count * bytesPerSprite)
            .order(ByteOrder.nativeOrder());
        ByteBuffer flagPixelBuf = ByteBuffer.allocateDirect(count * bytesPerSprite)
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

        int specCount = 0, normalCount = 0, flagCount = 0;
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
                TextureTracker.spriteSpecularCache.put(i,
                    copySpriteImage(specImg, specImg.getWidth(), specImg.getHeight(), spriteSize));
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
                TextureTracker.spriteNormalCache.put(i,
                    copySpriteImage(normalImg, normalImg.getWidth(), normalImg.getHeight(), spriteSize));
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

            NativeImage flagImg = auxExt.neoVoxelRT$getFlagNativeImage();
            if (flagImg != null) {
                TextureTracker.spriteFlagCache.put(i,
                    copySpriteImage(flagImg, flagImg.getWidth(), flagImg.getHeight(), spriteSize));
                long dstPtr = memAddress(flagPixelBuf) + (long) i * bytesPerSprite;
                int flagW = flagImg.getWidth();
                int flagH = flagImg.getHeight();
                if (flagW == spriteSize && flagH == spriteSize) {
                    long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                        (Object) flagImg).neoVoxelRT$getPointer();
                    memCopy(srcPtr, dstPtr, bytesPerSprite);
                } else {
                    extractPixelsManual(flagImg, flagW, flagH, spriteSize, dstPtr);
                }
                flagCount++;
            }
        }

        TextureArrayBridge.nativeReceiveSpriteAuxPixels(
            memAddress(specPixelBuf), memAddress(normalPixelBuf), memAddress(flagPixelBuf), count * bytesPerSprite);
        LOGGER.info("[TextureSystem] Sent aux pixels: {} specular, {} normal, {} flags ({} KB each)",
            specCount, normalCount, flagCount, (count * bytesPerSprite) / 1024);

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

            TextureArrayBridge.nativeReceiveAnimationFrames(memAddress(animBuf), animOffset);
            LOGGER.info("[TextureSystem] Sent {} bytes of animation data", animOffset);
        } else {
            // No animations — send empty
            TextureArrayBridge.nativeReceiveAnimationFrames(0, 0);
        }

        // ---- Step 6: Finalize ----
        TextureArrayBridge.nativeTextureFinalize();
        TextureArrayBridge.publishTextureGeneration();
        MinecraftClient mc = MinecraftClient.getInstance();
        AutoPbrRuntime.RehydrateReport autoPbrReport = AutoPbrRuntime.rehydrateSavedSidecars(mc);
        if (autoPbrReport.discovered() > 0) {
            LOGGER.info("[TextureSystem] Material Lab recipes applied after finalize: {}", autoPbrReport);
        }
        if (mc != null && mc.world != null && mc.worldRenderer != null) {
            try {
                Options.nativeRebuildChunks();
            } catch (UnsatisfiedLinkError e) {
                LOGGER.debug("[TextureSystem] Native chunk rebuild skipped after texture finalize", e);
            }
            Options.debouncedChunkReload();
        } else {
            LOGGER.debug("[TextureSystem] Texture generation published before world load; chunk reload deferred");
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

    private static NativeImage copySpriteImage(NativeImage img, int srcW, int srcH, int dstSize) {
        NativeImage copy = new NativeImage(NativeImage.Format.RGBA, dstSize, dstSize, false);
        int copyW = Math.min(srcW, dstSize);
        int copyH = Math.min(srcH, dstSize);
        for (int y = 0; y < copyH; y++) {
            for (int x = 0; x < copyW; x++) {
                copy.setColorArgb(x, y, img.getColorArgb(x, y));
            }
        }
        return copy;
    }
}
