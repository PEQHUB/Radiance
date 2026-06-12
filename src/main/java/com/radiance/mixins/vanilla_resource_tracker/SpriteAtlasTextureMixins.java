package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.autopbr.AutoPbrRuntime;
import com.radiance.client.autopbr.AutoPbrTextureRules;
import com.radiance.client.build.BuildInfo;
import com.radiance.client.debug.TextureReloadTimeline;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.VanillaTextureManifest;
import com.radiance.client.texture.cache.TextureCacheKey;
import com.radiance.client.texture.cache.TextureCacheV4;
import com.radiance.client.texture.v4.TextureLoadGeneration;
import com.radiance.client.texture.v4.TextureManifestV4;
import com.radiance.client.texture.v4.TextureLoadGraph;
import com.radiance.client.texture.v4.TextureLoadScheduler;
import com.radiance.client.texture.v4.TextureLoaderV4Options;
import com.radiance.client.texture.v4.NativeUploadGuards;
import com.radiance.client.proxy.vulkan.TextureArrayBridgeV4;
import com.radiance.client.texture.compat.ResourcePackCompatDiagnostics;
import com.radiance.client.texture.compat.ResourcePackEmissiveTextureResolver;
import com.radiance.client.texture.compat.ResourcePackRuntimeMaterialBootstrap;
import com.radiance.client.texture.compat.TextureLoaderDiskCache;
import com.radiance.client.texture.material.ResourceMaterialRegistry;
import com.radiance.client.texture.packindex.PackStackSnapshot;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memSet;

@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixins extends AbstractTextureMixins {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpriteAtlasTexture");
    private static boolean lastBlockAtlasExtractionSucceeded = false;

    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/Sprite;upload()V"),
        cancellable = true)
    public void setImageTargetIDBeforeUpload(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci, @Local Sprite sprite) {
        int id = getGlId();
        ((ISpriteExt) sprite).neoVoxelRT$setTargetID(id);
        SpriteAtlasTexture self = (SpriteAtlasTexture) (Object) this;
        if (shouldBypassVanillaBlockAtlas(self.getId())) {
            TextureTracker.beginVanillaBlockAtlasUploadBypass(id);
            Map<Identifier, Sprite> regions = stitchResult.regions();
            TextureTracker.recordVanillaBlockAtlasUploadBypass(regions == null ? 0L : regions.size());

            if (TextureLoaderV4Options.enabled() && regions != null && !regions.isEmpty()) {
                // V4 path: generation-scoped, tiered upload
                long generation = TextureLoadGeneration.begin();
                try {
                    List<Map.Entry<Identifier, Sprite>> sorted = sortedEntries(regions);
                    List<Identifier> sortedIds = new ArrayList<>(sorted.size());
                    for (Map.Entry<Identifier, Sprite> entry : sorted) {
                        sortedIds.add(entry.getKey());
                    }
                    TextureArrayBridge.publishV4SpriteIds(sortedIds, generation);
                    TextureManifestV4 manifest = TextureManifestV4.fromBlockAtlas(
                        generation, self.getId(), sorted,
                        stitchResult.width(), stitchResult.height());
                    TextureTracker.currentSpriteLayerSize = manifest.dominantLayerSize();
                    TextureLoadGraph graph = TextureLoadGraph.build(
                        MinecraftClient.getInstance().getResourceManager(), manifest, generation);
                    var schedule = TextureLoadScheduler.start(graph);
                    if (schedule.isDone() && !Boolean.TRUE.equals(schedule.getNow(Boolean.FALSE))) {
                        throw new IllegalStateException("nativeBeginTextureLoaderV4 rejected generation " + generation);
                    }
                    MinecraftClient mc = MinecraftClient.getInstance();
                    var packIndexSnapshot = PackStackSnapshot.captureAndPublish(
                        mc == null ? null : mc.getResourceManager(), generation);
                    TierUploadReport tierUploadReport =
                        stageVanillaTieredMaterialPages(sorted, regions.size(), generation);
                    if (tierUploadReport.uploadedPages() <= 0 && !regions.isEmpty()) {
                        throw new IllegalStateException("v4 tier staging produced no uploaded pages");
                    }
                    if (!uploadV4SpriteRegistry(sorted, regions.size(), generation)) {
                        throw new IllegalStateException("nativeUpdateSpriteRegistrySparseV4 rejected generation " + generation);
                    }
                    ResourcePackRuntimeMaterialBootstrap.BootstrapResult runtimeMaterialBootstrap =
                        ResourcePackRuntimeMaterialBootstrap.publishFromRuntimeResourceManager(
                            mc == null ? null : mc.getResourceManager(), generation);
                    boolean materialTableUploaded = runtimeMaterialBootstrap.tableUploaded();
                    if (!materialTableUploaded) {
                        materialTableUploaded = ResourceMaterialRegistry.uploadActiveTableToNative(generation);
                    }
                    if (!materialTableUploaded) {
                        throw new IllegalStateException("nativeUpdateMaterialTableSparseV4 rejected generation " + generation);
                    }
                    AutoPbrTextureRules.clear();
                    boolean textureRulesUploaded = TextureArrayBridge.lastTextureRuleUploadSucceeded();
                    if (!textureRulesUploaded) {
                        throw new IllegalStateException("nativeUpdateTextureRulesV4 rejected generation " + generation);
                    }
                    if (!TextureLoadScheduler.commitGeneration(generation)) {
                        throw new IllegalStateException("nativeCommitTextureLoaderV4 rejected generation " + generation);
                    }
                    TextureTracker.recordVanillaBlockAtlasUploadBypass(regions == null ? 0L : regions.size());
                    LOGGER.info("[TextureLoaderV4] Block atlas v4 extraction: gen={} sprites={} pages={} layers={} bytes={} cacheHits={} cacheMisses={} cacheWrites={} materialTableUploaded={} textureRulesUploaded={} defaultRuleSeeds={} packIndex={} bootstrap={}",
                        generation, regions.size(), tierUploadReport.uploadedPages(), tierUploadReport.uploadedLayers(),
                        tierUploadReport.bytesUploaded(), tierUploadReport.cacheHits(), tierUploadReport.cacheMisses(),
                        tierUploadReport.cacheWrites(), materialTableUploaded, textureRulesUploaded,
                        AutoPbrTextureRules.defaultSeedCount(), packIndexSnapshot, runtimeMaterialBootstrap.toJson());
                    // V4 succeeded — skip legacy extraction
                    TextureTracker.endVanillaBlockAtlasUploadBypass();
                    ci.cancel();
                    return;
                } catch (Throwable t) {
                    TextureLoadGeneration.cancel(generation, 1);
                    TextureLoadScheduler.cancelGeneration(generation);
                    LOGGER.error("[TextureLoaderV4] Block atlas v4 extraction failed", t);
                    LOGGER.error("[TextureLoaderV4] v4 failed closed; legacy fixed block extractor disabled");
                    TextureTracker.endVanillaBlockAtlasUploadBypass();
                    ci.cancel();
                    return;
                }
            }

            LOGGER.error("[TextureLoaderV4] Block atlas v4 unavailable or empty; legacy fixed block extractor disabled");
            TextureTracker.endVanillaBlockAtlasUploadBypass();
            ci.cancel();
        }
    }

    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/Sprite;upload()V",
            shift = At.Shift.AFTER))
    public void clearBlockAtlasBypassAfterUpload(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci, @Local Sprite sprite) {
        TextureTracker.endVanillaBlockAtlasUploadBypass();
    }

    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At("RETURN"))
    public void clearBlockAtlasBypassAfterAtlasUpload(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci) {
        TextureTracker.endVanillaBlockAtlasUploadBypass();
    }

    /**
     * Extract sprite data before vanilla iterates the block-atlas sprite upload loop.
     * Minimal Java role: sort, build metadata, send raw pixels. C++ owns the rest.
     */
    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At("RETURN"))
    public void extractSpritesForTextureArrays(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci) {
        lastBlockAtlasExtractionSucceeded = false;
        // Only process the block atlas
        SpriteAtlasTexture self = (SpriteAtlasTexture) (Object) this;
        Identifier atlasId = self.getId();
        if (!atlasId.getPath().contains("blocks")) {
            return;
        }
        if (TextureLoaderV4Options.enabled()) {
            LOGGER.warn("[TextureLoaderV4] Ignoring legacy RETURN texture extraction for block atlas");
            return;
        }

        Map<Identifier, Sprite> regions = stitchResult.regions();
        if (regions == null || regions.isEmpty()) return;

        // Force model table re-serialization on next chunk build.
        // Don't call reset() — it clears spriteIdLookup, creating a race with chunk threads.
        // The sortedSpriteIds reference is atomically replaced at line 76 below.

        int atlasW = stitchResult.width();
        int atlasH = stitchResult.height();
        TextureReloadTimeline.begin(atlasId.toString(), regions.size(), atlasW, atlasH);
        TextureReloadTimeline.addSummary("vanillaBlockAtlasBypass", TextureTracker.lastVanillaBlockAtlasBypass ? 1 : 0);
        TextureReloadTimeline.addSummary("vanillaBlockAtlasBypassSkippedSprites",
            TextureTracker.vanillaBlockAtlasBypassSkippedSprites);

        LOGGER.info("[TextureSystem] Processing block atlas: {} sprites, {}x{}", regions.size(), atlasW, atlasH);
        LOGGER.info("[TextureSystem] Vanilla block atlas GL upload bypassed={} atlas={} skippedSprites={}",
            TextureTracker.lastVanillaBlockAtlasBypass, atlasId,
            TextureTracker.vanillaBlockAtlasBypassSkippedSprites);

        // ---- Step 1: Sort sprites alphabetically for deterministic spriteId assignment ----
        long phaseStart = TextureReloadTimeline.start("spriteSort");
        List<Map.Entry<Identifier, Sprite>> sorted = new ArrayList<>(regions.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));
        TextureReloadTimeline.end("spriteSort", phaseStart);

        // Build the sorted identifier list for TextureArrayBridge.serializeQuad() to use
        List<Identifier> sortedIds = new ArrayList<>(sorted.size());
        for (var entry : sorted) {
            sortedIds.add(entry.getKey());
        }

        phaseStart = TextureReloadTimeline.start("manifest");
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
            TextureReloadTimeline.addSummary("aborted", "invalidManifest");
            TextureReloadTimeline.finish();
            return;
        }
        TextureReloadTimeline.end("manifest", phaseStart);

        phaseStart = TextureReloadTimeline.start("capacityRefresh");
        int renderableSpriteCapacity = TextureArrayBridge.refreshNativeRenderableSpriteCapacity();
        TextureReloadTimeline.end("capacityRefresh", phaseStart);
        if (sortedIds.size() > renderableSpriteCapacity) {
            LOGGER.warn("[TextureRefactor] Block atlas has {} sprites but native texture arrays can render {}. "
                    + "Overflow sprites will resolve to the material-safe fallback until texture paging lands.",
                sortedIds.size(), renderableSpriteCapacity);
        }
        phaseStart = TextureReloadTimeline.start("generationPublish");
        TextureArrayBridge.incrementTextureGeneration();
        TextureArrayBridge.setSortedSpriteIds(sortedIds);
        TextureReloadTimeline.end("generationPublish", phaseStart);
        int tierReferenceSize = manifest.fixedLayerSize();
        boolean primaryTierUpload = true;
        int spriteSize = primaryTierUpload ? 1 : tierReferenceSize;
        if (tierReferenceSize <= 0 || spriteSize <= 0) {
            TextureReloadTimeline.addSummary("aborted", "invalidSpriteSize");
            TextureReloadTimeline.finish();
            return;
        }

        int count = sorted.size();
        int uploadCount = Math.min(count, renderableSpriteCapacity);
        int overflowSprites = Math.max(0, count - uploadCount);
        long bytesPerSpriteLong = (long) spriteSize * spriteSize * 4L; // RGBA8
        long totalSpriteBytes = bytesPerSpriteLong * uploadCount;
        if (bytesPerSpriteLong > Integer.MAX_VALUE || totalSpriteBytes > Integer.MAX_VALUE) {
            LOGGER.error("[TextureSystem] Texture upload too large: sprites={} layerSize={} bytesPerSprite={} totalBytes={}",
                uploadCount, spriteSize, bytesPerSpriteLong, totalSpriteBytes);
            TextureReloadTimeline.addSummary("aborted", "textureUploadTooLarge");
            TextureReloadTimeline.addSummary("spriteCount", count);
            TextureReloadTimeline.addSummary("spriteLayerSize", spriteSize);
            TextureReloadTimeline.addSummary("bytesPerSprite", bytesPerSpriteLong);
            TextureReloadTimeline.addSummary("totalSpriteBytes", totalSpriteBytes);
            TextureReloadTimeline.finish();
            return;
        }
        int bytesPerSprite = Math.toIntExact(bytesPerSpriteLong);
        int totalBytes = Math.toIntExact(totalSpriteBytes);
        TextureTracker.currentSpriteLayerSize = tierReferenceSize;
        TextureReloadTimeline.addSummary("spriteCount", count);
        TextureReloadTimeline.addSummary("uploadedSpriteCount", uploadCount);
        TextureReloadTimeline.addSummary("renderableSpriteCapacity", renderableSpriteCapacity);
        TextureReloadTimeline.addSummary("overflowSprites", overflowSprites);
        TextureReloadTimeline.addSummary("overflowPolicy", "fallback");
        TextureReloadTimeline.addSummary("spriteLayerSize", spriteSize);
        TextureReloadTimeline.addSummary("primaryTierUpload", primaryTierUpload ? 1 : 0);
        TextureReloadTimeline.addSummary("tierReferenceLayerSize", tierReferenceSize);
        TextureReloadTimeline.addSummary("bytesPerSprite", bytesPerSprite);
        TextureReloadTimeline.addSummary("totalSpriteBytes", totalBytes);

        TextureTracker.resetSpriteAuxSources(uploadCount);
        phaseStart = TextureReloadTimeline.start("spriteSourceScan");
        for (int i = 0; i < uploadCount; i++) {
            Sprite sprite = sorted.get(i).getValue();
            NativeImage img = ((ISpriteContentsExt) sprite.getContents()).neoVoxelRT$getImage();
            if (img == null) continue;

            INativeImageExt auxExt = (INativeImageExt) (Object) img;
            NativeImage specImg = auxExt.neoVoxelRT$getSpecularNativeImage();
            if (specImg != null) {
                byte source = ((INativeImageExt) (Object) specImg).neoVoxelRT$getAuxSource();
                TextureTracker.spriteSpecularSource[i] = source;
                TextureTracker.spriteBaselineSpecularSource[i] = source;
            }

            NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
            if (normalImg != null) {
                byte source = ((INativeImageExt) (Object) normalImg).neoVoxelRT$getAuxSource();
                TextureTracker.spriteNormalSource[i] = source;
                TextureTracker.spriteBaselineNormalSource[i] = source;
            }
        }
        TextureReloadTimeline.end("spriteSourceScan", phaseStart);

        // ---- Step 2: Detect overlay sprites (grass_block_side_overlay → grass_block_side) ----
        // overlayOf[i] = spriteId that sprite i is an overlay FOR, or -1
        phaseStart = TextureReloadTimeline.start("overlayDetect");
        short[] overlayOf = new short[count];
        boolean[] emissiveOverlay = new boolean[count];
        java.util.Arrays.fill(overlayOf, (short) -1);
        Map<Identifier, Integer> spriteIndexById = new HashMap<>();
        for (int i = 0; i < uploadCount; i++) {
            spriteIndexById.put(sorted.get(i).getKey(), i);
        }
        Set<Identifier> basesWithVanillaOverlay = new HashSet<>();
        for (int i = 0; i < uploadCount; i++) {
            Identifier spriteId = sorted.get(i).getKey();
            String name = spriteId.toString();
            if (!name.endsWith("_overlay")) {
                continue;
            }
            String baseName = name.substring(0, name.length() - "_overlay".length());
            Identifier baseId = Identifier.tryParse(baseName);
            if (baseId != null && spriteIndexById.containsKey(baseId)) {
                basesWithVanillaOverlay.add(baseId);
            }
        }
        String emissiveSuffix = ResourcePackEmissiveTextureResolver.suffix(
            MinecraftClient.getInstance().getResourceManager(),
            Options.materialCompatLegacyMcPatcherEnabled);
        for (int i = 0; i < count; i++) {
            Identifier spriteId = sorted.get(i).getKey();
            String name = spriteId.toString();
            if (name.endsWith("_overlay")) {
                String baseName = name.substring(0, name.length() - "_overlay".length());
                Identifier baseId = Identifier.tryParse(baseName);
                Integer baseIndex = baseId == null ? null : spriteIndexById.get(baseId);
                if (baseIndex != null) {
                    overlayOf[i] = baseIndex.shortValue();
                }
            } else if (Options.materialCompatEnabled && Options.materialCompatPhysicalEmissiveEnabled) {
                Identifier baseId =
                    ResourcePackEmissiveTextureResolver.baseSpriteForEmissiveSprite(spriteId, emissiveSuffix);
                Integer baseIndex = baseId == null ? null : spriteIndexById.get(baseId);
                if (baseIndex != null) {
                    boolean shaderOverlaySlotAvailable = !basesWithVanillaOverlay.contains(baseId);
                    if (shaderOverlaySlotAvailable) {
                        overlayOf[i] = baseIndex.shortValue();
                    }
                    emissiveOverlay[i] = true;
                    ResourcePackEmissiveTextureResolver.registerOverlaySprite(
                        spriteId, baseId, shaderOverlaySlotAvailable);
                }
            }
        }
        TextureReloadTimeline.end("overlayDetect", phaseStart);

        // ---- Step 3: Build metadata table (SpriteMetadata = 16 bytes, packed) ----
        phaseStart = TextureReloadTimeline.start("metadataBuild");
        int META_SIZE = 16;
        ByteBuffer metaBuf = ByteBuffer.allocateDirect(uploadCount * META_SIZE)
            .order(ByteOrder.nativeOrder());

        for (int i = 0; i < uploadCount; i++) {
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
                if (normalImg != null && authoredHeight
                    && AuxiliaryTextures.hasVisibleHeightAlphaRange(normalImg, img)) {
                    flags |= TextureTracker.SPRITE_FLAG_HAS_HEIGHT;
                }
            }
            if (emissiveOverlay[i]) {
                flags |= TextureTracker.SPRITE_FLAG_EMISSIVE_OVERLAY;
            }
            metaBuf.putShort(off + 14, (short) flags);
        }
        TextureReloadTimeline.end("metadataBuild", phaseStart);

        phaseStart = TextureReloadTimeline.start("nativeSpriteTableUpload");
        NativeUploadGuards.assertDirectCapacity(metaBuf, (long) uploadCount * 16, "nativeReceiveSpriteTable");
        TextureArrayBridge.nativeReceiveSpriteTable(memAddress(metaBuf), uploadCount, atlasW, atlasH);
        TextureReloadTimeline.end("nativeSpriteTableUpload", phaseStart);
        LOGGER.info("[TextureSystem] Sent sprite table: {} entries ({} overflow fallback)",
            uploadCount, overflowSprites);

        // ---- Step 4: Build pixel bulk buffer (frame 0 for each sprite, RGBA8) ----
        // NativeImage stores RGBA bytes natively (STB format). We can raw-copy.
        phaseStart = TextureReloadTimeline.start("albedoBufferAlloc");
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect(totalBytes)
            .order(ByteOrder.nativeOrder());
        TextureReloadTimeline.end("albedoBufferAlloc", phaseStart);

        int uploaded = 0;
        int animatedCount = 0;
        phaseStart = TextureReloadTimeline.start("albedoCopy");
        for (int i = 0; i < uploadCount; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();

            int w = contents.getWidth();
            int h = contents.getHeight();

            if (img != null) {
                if (Options.eagerSpriteImageCache) {
                    TextureTracker.spriteAlbedoCache.put(i, copySpriteImage(img, w, h, spriteSize));
                }
                long dstBase = memAddress(pixelBuf) + (long) i * bytesPerSprite;
                writeSpriteFramePixels(img, w, h, 0, spriteSize, dstBase);
                uploaded++;
            }
                // Size mismatch — extract per-pixel with conversion
            // else: leave zeroed (black transparent)

            int imgH = (img != null) ? img.getHeight() : h;
            if (imgH > h) animatedCount++;
        }
        TextureReloadTimeline.end("albedoCopy", phaseStart);

        phaseStart = TextureReloadTimeline.start("nativeAlbedoUpload");
        NativeUploadGuards.assertDirectCapacity(pixelBuf, totalBytes, "nativeReceiveSpritePixels");
        TextureArrayBridge.nativeReceiveSpritePixels(memAddress(pixelBuf), totalBytes);
        TextureReloadTimeline.end("nativeAlbedoUpload", phaseStart);
        LOGGER.info("[TextureSystem] Sent {} sprite pixels ({} KB), {} animated",
            uploaded, totalBytes / 1024, animatedCount);

        int specCount = 0, normalCount = 0, flagCount = 0;
        boolean sparseAux = !primaryTierUpload && Options.textureStreamingV2 && Options.sparseAuxUpload;
        TextureReloadTimeline.addSummary("sparseAuxUpload", String.valueOf(sparseAux));
        if (sparseAux) {
            TextureReloadTimeline.mark("auxBufferAlloc");
            TextureReloadTimeline.mark("defaultNormalFill");
            TextureReloadTimeline.mark("auxCopy");
            phaseStart = TextureReloadTimeline.start("nativeAuxUpload");
            TextureArrayBridge.nativeReceiveSpriteAuxPixels(0, 0, 0, 0);
            TextureReloadTimeline.end("nativeAuxUpload", phaseStart);
            TextureReloadTimeline.addSummary("fullAuxUploadBytesAvoided", (long) totalBytes * 3L);
            TextureReloadTimeline.addSummary("defaultNormalFillPixelsAvoided",
                (long) uploadCount * spriteSize * spriteSize);
            LOGGER.info("[TextureSystem] Deferred aux sidecars to sparse native layer updates; avoided {} KB",
                ((long) totalBytes * 3L) / 1024L);
        } else {
        // ---- Step 4B: Build specular + normal + flag pixel buffers for texture arrays ----
        phaseStart = TextureReloadTimeline.start("auxBufferAlloc");
        ByteBuffer specPixelBuf = ByteBuffer.allocateDirect(totalBytes)
            .order(ByteOrder.nativeOrder());
        ByteBuffer normalPixelBuf = ByteBuffer.allocateDirect(totalBytes)
            .order(ByteOrder.nativeOrder());
        ByteBuffer flagPixelBuf = ByteBuffer.allocateDirect(totalBytes)
            .order(ByteOrder.nativeOrder());
        TextureReloadTimeline.end("auxBufferAlloc", phaseStart);

        // Fill normal default: (128, 128, 255, 255) = flat normal, AO=1.0, height=1.0
        phaseStart = TextureReloadTimeline.start("defaultNormalFill");
        {
            long normBase = memAddress(normalPixelBuf);
            long pixelCount = (long) uploadCount * spriteSize * spriteSize;
            for (long px = 0; px < pixelCount; px++) {
                long off = normBase + px * 4L;
                memPutByte(off,     (byte) 128); // R: normal X = 0.5
                memPutByte(off + 1, (byte) 128); // G: normal Y = 0.5
                memPutByte(off + 2, (byte) 255); // B: AO = 1.0
                memPutByte(off + 3, (byte) 255); // A: height = 1.0
            }
        }
        TextureReloadTimeline.end("defaultNormalFill", phaseStart);
        // Specular default is all zeros (roughness=1.0, F0=0.02) — already zeroed by allocateDirect

        phaseStart = TextureReloadTimeline.start("auxCopy");
        for (int i = 0; i < uploadCount; i++) {
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
                int specH = Math.min(specImg.getHeight(), Math.max(1, h));
                TextureTracker.spriteSpecularCache.put(i,
                    copySpriteImage(specImg, specImg.getWidth(), specH, spriteSize));
                TextureTracker.spriteBaselineSpecularCache.put(i,
                    copySpriteImage(specImg, specImg.getWidth(), specH, spriteSize));
                long dstPtr = memAddress(specPixelBuf) + (long) i * bytesPerSprite;
                writeSpriteFramePixels(specImg, specImg.getWidth(), specH, 0, spriteSize, dstPtr);
                specCount++;
            }

            // Normal (_n) texture
            NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
            if (normalImg != null) {
                int normalH = Math.min(normalImg.getHeight(), Math.max(1, h));
                TextureTracker.spriteNormalCache.put(i,
                    copySpriteImage(normalImg, normalImg.getWidth(), normalH, spriteSize));
                TextureTracker.spriteBaselineNormalCache.put(i,
                    copySpriteImage(normalImg, normalImg.getWidth(), normalH, spriteSize));
                long dstPtr = memAddress(normalPixelBuf) + (long) i * bytesPerSprite;
                writeSpriteFramePixels(normalImg, normalImg.getWidth(), normalH, 0, spriteSize, dstPtr);
                normalCount++;
            }

            NativeImage flagImg = auxExt.neoVoxelRT$getFlagNativeImage();
            if (flagImg != null) {
                int flagH = Math.min(flagImg.getHeight(), Math.max(1, h));
                TextureTracker.spriteFlagCache.put(i,
                    copySpriteImage(flagImg, flagImg.getWidth(), flagH, spriteSize));
                long dstPtr = memAddress(flagPixelBuf) + (long) i * bytesPerSprite;
                writeSpriteFramePixels(flagImg, flagImg.getWidth(), flagH, 0, spriteSize, dstPtr);
                flagCount++;
            }
        }
        TextureReloadTimeline.end("auxCopy", phaseStart);
        TextureReloadTimeline.addSummary("specularSprites", specCount);
        TextureReloadTimeline.addSummary("normalSprites", normalCount);
        TextureReloadTimeline.addSummary("flagSprites", flagCount);

        phaseStart = TextureReloadTimeline.start("nativeAuxUpload");
        NativeUploadGuards.assertDirectCapacity(specPixelBuf, totalBytes, "nativeReceiveSpriteAuxPixels/spec");
        NativeUploadGuards.assertDirectCapacity(normalPixelBuf, totalBytes, "nativeReceiveSpriteAuxPixels/normal");
        NativeUploadGuards.assertDirectCapacity(flagPixelBuf, totalBytes, "nativeReceiveSpriteAuxPixels/flag");
        TextureArrayBridge.nativeReceiveSpriteAuxPixels(
            memAddress(specPixelBuf), memAddress(normalPixelBuf), memAddress(flagPixelBuf), totalBytes);
        TextureReloadTimeline.end("nativeAuxUpload", phaseStart);
        LOGGER.info("[TextureSystem] Sent aux pixels: {} specular, {} normal, {} flags ({} KB each)",
            specCount, normalCount, flagCount, totalBytes / 1024);
        }

        // ---- Step 5: Build animation frame data ----
        // Format: [spriteId(u16), frameIndex(u16), pixels(w*h*4)] repeated
        // Only for sprites with frameCount > 1
        if (!TextureTracker.textureArrayAnimationUpdatesEnabled) {
            TextureReloadTimeline.addSummary("animationUploadSkipped", "updatesDisabled");
            TextureReloadTimeline.addSummary("animationBytes", 0);
            phaseStart = TextureReloadTimeline.start("nativeAnimationUpload");
            TextureArrayBridge.nativeReceiveAnimationFrames(0, 0);
            TextureReloadTimeline.end("nativeAnimationUpload", phaseStart);
        } else {
            String animationCacheKey = ResourcePackRuntimeMaterialBootstrap.cacheKeyForResourceManager(
                MinecraftClient.getInstance().getResourceManager());
        phaseStart = TextureReloadTimeline.start("animationScan");
        long animDataSizeLong = 0L;
        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;
            int h = contents.getHeight();
            int imgH = img.getHeight();
            int frameCount = Math.max(1, imgH / h);
            if (frameCount > 1) {
                animDataSizeLong += (long) frameCount * (4L + bytesPerSpriteLong); // header + pixels per frame
            }
        }
        TextureReloadTimeline.end("animationScan", phaseStart);
        if (animDataSizeLong > Integer.MAX_VALUE) {
            LOGGER.error("[TextureSystem] Animation upload too large: {} bytes", animDataSizeLong);
            TextureReloadTimeline.addSummary("aborted", "animationUploadTooLarge");
            TextureReloadTimeline.addSummary("animationBytes", animDataSizeLong);
            TextureReloadTimeline.finish();
            return;
        }
        int animDataSize = Math.toIntExact(animDataSizeLong);
        TextureReloadTimeline.addSummary("animationBytes", animDataSize);

        if (animDataSize > 0) {
            phaseStart = TextureReloadTimeline.start("animationBufferBuild");
            ByteBuffer animBuf = ByteBuffer.allocateDirect(animDataSize)
                .order(ByteOrder.nativeOrder());
            int animOffset = 0;
            int animationCacheHits = 0;
            int animationCacheMisses = 0;
            int animationCacheWrites = 0;

            for (int i = 0; i < uploadCount; i++) {
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

                    long frameDst = memAddress(animBuf) + animOffset;
                    String frameKey = animationFramePayloadKey(sorted.get(i).getKey(), contents, frame, spriteSize);
                    byte[] cachedFrame =
                        TextureLoaderDiskCache.readBytePayload(animationCacheKey, frameKey, bytesPerSprite);
                    if (cachedFrame != null) {
                        memByteBuffer(frameDst, cachedFrame.length).put(cachedFrame);
                        animationCacheHits++;
                    } else {
                        animationCacheMisses++;
                        if (w == spriteSize && h == spriteSize && srcRowBytes == w * 4) {
                            // Fast path: contiguous, correct width
                            long frameSrc = srcBase + (long) frame * h * srcRowBytes;
                            memCopy(frameSrc, frameDst, bytesPerSprite);
                        } else {
                            writeSpriteFramePixels(img, w, h, frame, spriteSize, frameDst);
                        }
                        TextureLoaderDiskCache.writeBytePayloadAsync(animationCacheKey, frameKey,
                            copyPlane(frameDst, bytesPerSprite));
                        if (animationCacheKey != null && !animationCacheKey.isBlank() && !frameKey.isBlank()) {
                            animationCacheWrites++;
                        }
                    }
                    animOffset += bytesPerSprite;
                }
            }
            TextureReloadTimeline.addSummary("animationPayloadCacheHits", animationCacheHits);
            TextureReloadTimeline.addSummary("animationPayloadCacheMisses", animationCacheMisses);
            TextureReloadTimeline.addSummary("animationPayloadCacheWrites", animationCacheWrites);

            TextureReloadTimeline.end("animationBufferBuild", phaseStart);
            phaseStart = TextureReloadTimeline.start("nativeAnimationUpload");
            NativeUploadGuards.assertDirectCapacity(animBuf, animOffset, "nativeReceiveAnimationFrames");
            TextureArrayBridge.nativeReceiveAnimationFrames(memAddress(animBuf), animOffset);
            TextureReloadTimeline.end("nativeAnimationUpload", phaseStart);
            LOGGER.info("[TextureSystem] Sent {} bytes of animation data", animOffset);
        } else {
            // No animations — send empty
            phaseStart = TextureReloadTimeline.start("nativeAnimationUpload");
            TextureArrayBridge.nativeReceiveAnimationFrames(0, 0);
            TextureReloadTimeline.end("nativeAnimationUpload", phaseStart);
        }
        }

        // ---- Step 6: Finalize ----
        phaseStart = TextureReloadTimeline.start("nativeFinalize");
        TextureArrayBridge.nativeTextureFinalize();
        TierUploadReport tierUploadReport = stageVanillaTieredMaterialPages(sorted, uploadCount,
            TextureArrayBridge.getActiveTextureGeneration());
        TextureReloadTimeline.addSummary("tieredArrays", tierUploadReport.uploadedPages() > 0 ? 1 : 0);
        TextureReloadTimeline.addSummary("tieredArrayPages", tierUploadReport.uploadedPages());
        TextureReloadTimeline.addSummary("tieredArrayLayers", tierUploadReport.uploadedLayers());
        TextureReloadTimeline.addSummary("tieredArrayBytes", tierUploadReport.bytesUploaded());
        TextureReloadTimeline.addSummary("tierPayloadCacheHits", tierUploadReport.cacheHits());
        TextureReloadTimeline.addSummary("tierPayloadCacheMisses", tierUploadReport.cacheMisses());
        TextureReloadTimeline.addSummary("tierPayloadCacheWrites", tierUploadReport.cacheWrites());
        TextureReloadTimeline.addSummary("tierPayloadCachedBytes", tierUploadReport.cachedBytes());
        if (sparseAux) {
            long sparseStart = TextureReloadTimeline.start("sparseAuxLayerUpdates");
            SparseAuxReport sparseAuxReport = stageSparseAuxLayers(sorted, uploadCount, spriteSize, bytesPerSprite);
            TextureReloadTimeline.end("sparseAuxLayerUpdates", sparseStart);
            TextureReloadTimeline.addSummary("sparseSpecularLayerUpdates", sparseAuxReport.specularUploaded());
            TextureReloadTimeline.addSummary("sparseNormalLayerUpdates", sparseAuxReport.normalUploaded());
            TextureReloadTimeline.addSummary("sparseFlagLayerUpdates", sparseAuxReport.flagUploaded());
            TextureReloadTimeline.addSummary("sparseAuxLayerBytes", sparseAuxReport.bytesUploaded());
            specCount = sparseAuxReport.specularUploaded();
            normalCount = sparseAuxReport.normalUploaded();
            flagCount = sparseAuxReport.flagUploaded();
        }
        TextureArrayBridge.publishTextureGeneration();
        TextureReloadTimeline.end("nativeFinalize", phaseStart);
        MinecraftClient mc = MinecraftClient.getInstance();
        phaseStart = TextureReloadTimeline.start("runtimeMaterialBootstrap");
        ResourcePackRuntimeMaterialBootstrap.BootstrapResult runtimeMaterialBootstrap =
            ResourcePackRuntimeMaterialBootstrap.publishFromRuntimeResourceManager(
                mc == null ? null : mc.getResourceManager(),
                TextureArrayBridge.getActiveTextureGeneration());
        TextureReloadTimeline.end("runtimeMaterialBootstrap", phaseStart);
        LOGGER.info("[TextureSystem] Runtime material bootstrap: {}", runtimeMaterialBootstrap.toJson());
        phaseStart = TextureReloadTimeline.start("materialTableUpload");
        boolean materialTableUploaded = runtimeMaterialBootstrap.tableUploaded();
        if (!materialTableUploaded) {
            materialTableUploaded = ResourceMaterialRegistry.uploadActiveTableToNative();
        }
        TextureReloadTimeline.end("materialTableUpload", phaseStart);
        if (materialTableUploaded) {
            LOGGER.info("[TextureSystem] Uploaded material-id table: {}",
                ResourceMaterialRegistry.activeSummaryJson());
        } else {
            LOGGER.info("[TextureSystem] Material-id table upload unavailable; Java registry remains active: {}",
                ResourceMaterialRegistry.activeSummaryJson());
        }
        TextureReloadTimeline.mark("diagnosticsScheduled");
        ResourcePackCompatDiagnostics.writeReportAsync("block_atlas_finalize", false);
        phaseStart = TextureReloadTimeline.start("autoPbrRehydrate");
        AutoPbrRuntime.RehydrateReport autoPbrReport = AutoPbrRuntime.rehydrateSavedSidecars(mc);
        TextureReloadTimeline.end("autoPbrRehydrate", phaseStart);
        if (autoPbrReport.discovered() > 0) {
            LOGGER.info("[TextureSystem] Material Lab recipes applied after finalize: {}", autoPbrReport);
        }
        phaseStart = TextureReloadTimeline.start("chunkReloadScheduled");
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
        TextureReloadTimeline.end("chunkReloadScheduled", phaseStart);
        TextureReloadTimeline.addSummary("animatedSprites", animatedCount);
        TextureReloadTimeline.addSummary("albedoUploadedSprites", uploaded);
        TextureReloadTimeline.finish();
        lastBlockAtlasExtractionSucceeded = true;
        LOGGER.info("[TextureSystem] Finalized. {} sprites ({} animated)", count, animatedCount);
    }

    private static boolean shouldBypassVanillaBlockAtlas(Identifier atlasId) {
        return atlasId != null
            && "minecraft".equals(atlasId.getNamespace())
            && "textures/atlas/blocks.png".equals(atlasId.getPath());
    }

    private static TierUploadReport stageVanillaTieredMaterialPages(List<Map.Entry<Identifier, Sprite>> sorted,
                                                                    int uploadCount,
                                                                    long generation) {
        if (uploadCount <= 0) {
            return new TierUploadReport(0, 0, 0L, 0, 0, 0, 0L);
        }
        final long maxChunkBytes = 128L * 1024L * 1024L;
        String cacheKey = ResourcePackRuntimeMaterialBootstrap.cacheKeyForResourceManager(
            MinecraftClient.getInstance().getResourceManager());
        int[] spritePage = new int[uploadCount];
        int[] spriteLayer = new int[uploadCount];
        int[] spriteTierSize = new int[uploadCount];
        int[] spriteFrameCount = new int[uploadCount];
        int[] pageLayerCounts = new int[ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX];
        Arrays.fill(spriteLayer, -1);
        Arrays.fill(spriteFrameCount, 1);

        for (int i = 0; i < uploadCount; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            int page = TextureTracker.tierPageForSpriteSize(contents.getWidth(), contents.getHeight());
            int tierSize = TextureTracker.tierSizeForPage(page);
            if (page <= 0 || page >= pageLayerCounts.length || tierSize <= 0) {
                continue;
            }
            int frameCount = spriteFrameCount(img, contents.getHeight());
            spritePage[i] = page;
            spriteTierSize[i] = tierSize;
            int tierIndex = page - TextureTracker.VANILLA_TIER_FIRST_PAGE;
            int nativePageCapacity = TextureArrayBridgeV4.nativePageLayerCapacityForTier(tierIndex);
            int nextLayer = pageLayerCounts[page];
            if (nativePageCapacity > 0) {
                if (frameCount > nativePageCapacity) {
                    LOGGER.warn("[TextureSystem] Sprite {} has {} frames but tier {} native page holds {}; clamping v4 animation frames",
                        sorted.get(i).getKey(), frameCount, tierSize, nativePageCapacity);
                    frameCount = nativePageCapacity;
                }
                int pageOffset = nextLayer % nativePageCapacity;
                if (pageOffset > 0 && pageOffset + frameCount > nativePageCapacity) {
                    nextLayer += nativePageCapacity - pageOffset;
                }
            }
            spriteLayer[i] = nextLayer;
            spriteFrameCount[i] = frameCount;
            TextureTracker.spriteFrameCountV4[i] = frameCount;
            pageLayerCounts[page] = nextLayer + frameCount;
            int physicalPage = nativePageCapacity > 0 ? spriteLayer[i] / nativePageCapacity : 0;
            int physicalLayer = nativePageCapacity > 0 ? spriteLayer[i] % nativePageCapacity : spriteLayer[i];
            TextureTracker.setSpriteV4PhysicalLocation(i, tierIndex, physicalPage, physicalLayer, tierSize);
        }

        int uploadedPages = 0;
        int uploadedLayers = 0;
        long uploadedBytes = 0L;
        int cacheHits = 0;
        int cacheMisses = 0;
        int cacheWrites = 0;
        long cachedBytes = 0L;
        for (int page = TextureTracker.VANILLA_TIER_FIRST_PAGE;
             page < TextureTracker.FIRST_COMPAT_MATERIAL_PAGE
                 && page < ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX;
             page++) {
            int layerCapacity = pageLayerCounts[page];
            if (layerCapacity <= 0) {
                continue;
            }
            int tierSize = TextureTracker.tierSizeForPage(page);
            long bytesPerLayerLong = (long) tierSize * tierSize * 4L;
            if (bytesPerLayerLong <= 0 || bytesPerLayerLong > Integer.MAX_VALUE) {
                LOGGER.warn("[TextureSystem] Skipping oversized vanilla texture tier page={} size={} bytesPerLayer={}",
                    page, tierSize, bytesPerLayerLong);
                continue;
            }
            int bytesPerLayer = Math.toIntExact(bytesPerLayerLong);
            int tierIndex = page - TextureTracker.VANILLA_TIER_FIRST_PAGE;
            int nativePageCapacity = TextureArrayBridgeV4.nativePageLayerCapacityForTier(tierIndex);
            long bytesPerFourPlaneLayer = bytesPerLayerLong * 4L;
            int maxChunkLayers = Math.max(1, (int) Math.min(Math.min(layerCapacity,
                    Math.max(1L, maxChunkBytes / Math.max(1L, bytesPerFourPlaneLayer))),
                nativePageCapacity > 0 ? nativePageCapacity : layerCapacity));
            boolean pageUploaded = true;
            for (int startLayer = 0; startLayer < layerCapacity; startLayer += maxChunkLayers) {
                int chunkLayers = Math.min(maxChunkLayers, layerCapacity - startLayer);
                int chunkBytes = Math.toIntExact((long) chunkLayers * bytesPerLayerLong);
                ByteBuffer albedoBuf = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.nativeOrder());
                ByteBuffer specularBuf = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.nativeOrder());
                ByteBuffer normalBuf = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.nativeOrder());
                ByteBuffer flagBuf = ByteBuffer.allocateDirect(chunkBytes).order(ByteOrder.nativeOrder());
                memSet(memAddress(specularBuf), 0, chunkBytes);
                memSet(memAddress(flagBuf), 0, chunkBytes);
                fillDefaultNormal(normalBuf, chunkLayers, tierSize);

                for (int i = 0; i < uploadCount; i++) {
                    int frameCount = spriteFrameCount[i];
                    int spriteStartLayer = spriteLayer[i];
                    int spriteEndLayer = spriteStartLayer + frameCount;
                    if (spritePage[i] != page || spriteEndLayer <= startLayer
                        || spriteStartLayer >= startLayer + chunkLayers) {
                        continue;
                    }
                    Sprite sprite = sorted.get(i).getValue();
                    SpriteContents contents = sprite.getContents();
                    NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
                    if (img == null) {
                        continue;
                    }
                    int w = contents.getWidth();
                    int h = contents.getHeight();
                    INativeImageExt auxExt = (INativeImageExt) (Object) img;
                    NativeImage specImg = auxExt.neoVoxelRT$getSpecularNativeImage();
                    NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
                    NativeImage flagImg = auxExt.neoVoxelRT$getFlagNativeImage();
                    for (int frame = 0; frame < frameCount; frame++) {
                        int absoluteLayer = spriteStartLayer + frame;
                        if (absoluteLayer < startLayer || absoluteLayer >= startLayer + chunkLayers) {
                            continue;
                        }
                        int localLayer = absoluteLayer - startLayer;
                        long dstBase = (long) localLayer * bytesPerLayer;
                        writeSpriteFramePixels(img, w, h, frame, tierSize, memAddress(albedoBuf) + dstBase);

                        if (specImg != null) {
                            int specH = Math.min(specImg.getHeight(), Math.max(1, h));
                            if (frame == 0) {
                                TextureTracker.spriteSpecularCache.put(i,
                                    copySpriteImage(specImg, specImg.getWidth(), specH, tierSize));
                                TextureTracker.spriteBaselineSpecularCache.put(i,
                                    copySpriteImage(specImg, specImg.getWidth(), specH, tierSize));
                            }
                            writeSpriteFramePixels(specImg, specImg.getWidth(), specH, 0, tierSize,
                                memAddress(specularBuf) + dstBase);
                        }

                        if (normalImg != null) {
                            int normalH = Math.min(normalImg.getHeight(), Math.max(1, h));
                            if (frame == 0) {
                                TextureTracker.spriteNormalCache.put(i,
                                    copySpriteImage(normalImg, normalImg.getWidth(), normalH, tierSize));
                                TextureTracker.spriteBaselineNormalCache.put(i,
                                    copySpriteImage(normalImg, normalImg.getWidth(), normalH, tierSize));
                            }
                            writeSpriteFramePixels(normalImg, normalImg.getWidth(), normalH, 0, tierSize,
                                memAddress(normalBuf) + dstBase);
                        }

                        if (flagImg != null) {
                            int flagH = Math.min(flagImg.getHeight(), Math.max(1, h));
                            if (frame == 0) {
                                TextureTracker.spriteFlagCache.put(i,
                                    copySpriteImage(flagImg, flagImg.getWidth(), flagH, tierSize));
                            }
                            writeSpriteFramePixels(flagImg, flagImg.getWidth(), flagH, 0, tierSize,
                                memAddress(flagBuf) + dstBase);
                        }
                    }
                }

                boolean uploaded = false;
                try {
                    uploaded = TextureLoadScheduler.uploadTierPage(generation,
                        page - TextureTracker.VANILLA_TIER_FIRST_PAGE, page, startLayer, chunkLayers,
                        layerCapacity, tierSize, albedoBuf, specularBuf, normalBuf, flagBuf, true);
                } catch (UnsatisfiedLinkError ignored) {
                }
                if (!uploaded) {
                    pageUploaded = false;
                    LOGGER.warn("[TextureSystem] Vanilla tier page upload failed: generation={} namespace={} tier={} page={} size={} start={} layers={}/{}",
                        generation, 1, page - TextureTracker.VANILLA_TIER_FIRST_PAGE,
                        page, tierSize, startLayer, chunkLayers, layerCapacity);
                    break;
                }
                uploadedLayers += chunkLayers;
                uploadedBytes += (long) chunkBytes * 4L;
            }
            if (pageUploaded) {
                uploadedPages++;
                LOGGER.info("[TextureSystem] Uploaded vanilla texture tier page={} size={} layers={}",
                    page, tierSize, layerCapacity);
            }
        }
        return new TierUploadReport(uploadedPages, uploadedLayers, uploadedBytes,
            cacheHits, cacheMisses, cacheWrites, cachedBytes);
    }

    private static TextureCacheKey vanillaTierPayloadKeyV4(Identifier spriteId, SpriteContents contents,
                                                           int tierSize, String packStackHash,
                                                           String sidecarPresenceHash) {
        String resourcePath = spriteId == null ? "unknown" : spriteId.toString();
        String packIdentity = contents == null
            ? "unknown"
            : contents.getWidth() + "x" + contents.getHeight();
        return new TextureCacheKey(
            "1.21.4",
            BuildInfo.REPO_COMMIT,
            "native-abi-" + TextureArrayBridgeV4.ABI_VERSION,
            BuildInfo.TEXTURE_LOADER_ABI_VERSION,
            BuildInfo.CACHE_SCHEMA_VERSION,
            packStackHash == null || packStackHash.isBlank() ? "unknown-pack-stack" : packStackHash,
            resourcePath,
            packIdentity,
            4,
            "vanilla-tiered-pages",
            sidecarPresenceHash == null || sidecarPresenceHash.isBlank() ? "none" : sidecarPresenceHash,
            "mip0-nearest-clamp",
            "rgba8-unorm-four-plane");
    }

    private static String sidecarPresenceHash(NativeImage specImg, NativeImage normalImg, NativeImage flagImg) {
        return (specImg == null ? "s0" : "s1")
            + (normalImg == null ? "n0" : "n1")
            + (flagImg == null ? "f0" : "f1");
    }

    private static byte[] copyLayerPayloadV4(long albedoPtr, long specPtr, long normalPtr,
                                             long flagPtr, int bytesPerLayer) {
        int planeBytes = Math.max(0, bytesPerLayer);
        byte[] payload = new byte[Math.multiplyExact(planeBytes, 4)];
        copyPlaneInto(payload, 0, albedoPtr, planeBytes);
        copyPlaneInto(payload, planeBytes, specPtr, planeBytes);
        copyPlaneInto(payload, planeBytes * 2, normalPtr, planeBytes);
        copyPlaneInto(payload, planeBytes * 3, flagPtr, planeBytes);
        return payload;
    }

    private static boolean writeCachedLayerPayloadV4(byte[] payload, int bytesPerLayer,
                                                     long albedoPtr, long specPtr,
                                                     long normalPtr, long flagPtr) {
        if (payload == null || bytesPerLayer <= 0 || payload.length != bytesPerLayer * 4) {
            return false;
        }
        memByteBuffer(albedoPtr, bytesPerLayer).put(payload, 0, bytesPerLayer);
        memByteBuffer(specPtr, bytesPerLayer).put(payload, bytesPerLayer, bytesPerLayer);
        memByteBuffer(normalPtr, bytesPerLayer).put(payload, bytesPerLayer * 2, bytesPerLayer);
        memByteBuffer(flagPtr, bytesPerLayer).put(payload, bytesPerLayer * 3, bytesPerLayer);
        return true;
    }

    private static void copyPlaneInto(byte[] dst, int dstOffset, long srcPtr, int bytes) {
        if (dst == null || srcPtr == 0L || bytes <= 0 || dstOffset < 0 || dstOffset + bytes > dst.length) {
            return;
        }
        memByteBuffer(srcPtr, bytes).get(dst, dstOffset, bytes);
    }

    private static String animationFramePayloadKey(Identifier spriteId, SpriteContents contents,
                                                   int frame, int spriteSize) {
        if (spriteId == null || contents == null || frame < 0 || spriteSize <= 0) {
            return "";
        }
        return TextureLoaderDiskCache.keyFor("animation-frame-v1|sprite=" + spriteId
            + "|w=" + contents.getWidth()
            + "|h=" + contents.getHeight()
            + "|frame=" + frame
            + "|layer=" + spriteSize);
    }

    private static void fillDefaultNormal(ByteBuffer buffer, int layerCount, int spriteSize) {
        long base = memAddress(buffer);
        long pixelCount = (long) layerCount * spriteSize * spriteSize;
        for (long px = 0; px < pixelCount; px++) {
            long off = base + px * 4L;
            memPutByte(off, (byte) 128);
            memPutByte(off + 1, (byte) 128);
            memPutByte(off + 2, (byte) 255);
            memPutByte(off + 3, (byte) 255);
        }
    }

    private static int spriteFrameCount(NativeImage img, int frameHeight) {
        if (img == null) {
            return 1;
        }
        int height = Math.max(1, frameHeight);
        return Math.max(1, img.getHeight() / height);
    }

    private static byte[] copyPlane(long ptr, int bytes) {
        byte[] data = new byte[Math.max(0, bytes)];
        if (ptr != 0L && bytes > 0) {
            memByteBuffer(ptr, bytes).get(data);
        }
        return data;
    }

    private static SparseAuxReport stageSparseAuxLayers(List<Map.Entry<Identifier, Sprite>> sorted,
                                                        int uploadCount, int spriteSize, int bytesPerSprite) {
        if (uploadCount <= 0 || spriteSize <= 0 || bytesPerSprite <= 0) {
            return new SparseAuxReport(0, 0, 0, 0L);
        }
        final int UPDATE_SIZE = 24;
        final int METADATA_SIZE = 12;
        final int CHANNEL_SPECULAR = 1;
        final int CHANNEL_NORMAL = 2;
        final int CHANNEL_FLAG = 4;

        int specUploaded = 0;
        int normalUploaded = 0;
        int flagUploaded = 0;
        int updateCount = 0;
        int metadataCount = 0;
        int layerPayloadCount = 0;
        for (int i = 0; i < uploadCount; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;
            INativeImageExt auxExt = (INativeImageExt) (Object) img;
            boolean hasSpec = auxExt.neoVoxelRT$getSpecularNativeImage() != null;
            boolean hasNormal = auxExt.neoVoxelRT$getNormalNativeImage() != null;
            boolean hasFlag = auxExt.neoVoxelRT$getFlagNativeImage() != null;
            if (hasSpec || hasNormal || hasFlag) {
                updateCount++;
                if (hasSpec) layerPayloadCount++;
                if (hasNormal) {
                    layerPayloadCount++;
                    metadataCount++;
                }
                if (hasFlag) layerPayloadCount++;
            }
        }
        if (updateCount == 0 || layerPayloadCount == 0) {
            LOGGER.info("[TextureSystem] Sparse aux batch: updates=0 bytes=0 metadataUpdates=0");
            return new SparseAuxReport(0, 0, 0, 0L);
        }

        long pixelBytesLong = (long) layerPayloadCount * bytesPerSprite;
        long updateBytesLong = (long) updateCount * UPDATE_SIZE;
        long metadataBytesLong = (long) metadataCount * METADATA_SIZE;
        if (pixelBytesLong > Integer.MAX_VALUE || updateBytesLong > Integer.MAX_VALUE
            || metadataBytesLong > Integer.MAX_VALUE) {
            LOGGER.warn("[TextureSystem] Sparse aux batch too large: layers={} pixelBytes={} updates={}",
                layerPayloadCount, pixelBytesLong, updateCount);
            return new SparseAuxReport(0, 0, 0, 0L);
        }

        ByteBuffer updateBuf = ByteBuffer.allocateDirect((int) updateBytesLong)
            .order(ByteOrder.nativeOrder());
        ByteBuffer metadataBuf = ByteBuffer.allocateDirect(Math.max(1, (int) metadataBytesLong))
            .order(ByteOrder.nativeOrder());
        ByteBuffer pixelBuf = ByteBuffer.allocateDirect((int) pixelBytesLong)
            .order(ByteOrder.nativeOrder());
        int updateOffset = 0;
        int metadataOffset = 0;
        int pixelOffset = 0;
        long generation = TextureArrayBridge.getActiveTextureGeneration();
        for (int i = 0; i < uploadCount; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;

            INativeImageExt auxExt = (INativeImageExt) (Object) img;
            int h = contents.getHeight();

            NativeImage specImg = auxExt.neoVoxelRT$getSpecularNativeImage();
            NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
            NativeImage flagImg = auxExt.neoVoxelRT$getFlagNativeImage();
            if (specImg == null && normalImg == null && flagImg == null) {
                continue;
            }

            int updateBase = updateOffset;
            updateBuf.putInt(updateBase, i);
            int channelMask = 0;
            int specOffset = -1;
            int normalOffset = -1;
            int flagOffset = -1;

            if (specImg != null) {
                int specH = Math.min(specImg.getHeight(), Math.max(1, h));
                TextureTracker.spriteSpecularCache.put(i,
                    copySpriteImage(specImg, specImg.getWidth(), specH, spriteSize));
                TextureTracker.spriteBaselineSpecularCache.put(i,
                    copySpriteImage(specImg, specImg.getWidth(), specH, spriteSize));
                specOffset = pixelOffset;
                writeSpriteFramePixels(specImg, specImg.getWidth(), specH, 0, spriteSize,
                    memAddress(pixelBuf) + pixelOffset);
                pixelOffset += bytesPerSprite;
                channelMask |= CHANNEL_SPECULAR;
                specUploaded++;
            }

            if (normalImg != null) {
                int normalH = Math.min(normalImg.getHeight(), Math.max(1, h));
                TextureTracker.spriteNormalCache.put(i,
                    copySpriteImage(normalImg, normalImg.getWidth(), normalH, spriteSize));
                TextureTracker.spriteBaselineNormalCache.put(i,
                    copySpriteImage(normalImg, normalImg.getWidth(), normalH, spriteSize));
                normalOffset = pixelOffset;
                writeSpriteFramePixels(normalImg, normalImg.getWidth(), normalH, 0, spriteSize,
                    memAddress(pixelBuf) + pixelOffset);
                pixelOffset += bytesPerSprite;
                channelMask |= CHANNEL_NORMAL;
                normalUploaded++;
                int flags = TextureTracker.SPRITE_FLAG_HAS_NORMAL
                    | TextureTracker.encodeSpriteSourceFlags(
                        TextureTracker.spriteSpecularSource[i], TextureTracker.spriteNormalSource[i]);
                if (specImg != null) {
                    flags |= TextureTracker.SPRITE_FLAG_HAS_SPECULAR;
                }
                int heightRange = heightRangePacked(normalImg, img);
                if (heightRange >= 0) {
                    flags |= TextureTracker.SPRITE_FLAG_HAS_HEIGHT;
                }
                metadataBuf.putInt(metadataOffset, i);
                metadataBuf.putInt(metadataOffset + 4, flags);
                metadataBuf.putInt(metadataOffset + 8, heightRange);
                metadataOffset += METADATA_SIZE;
            }

            if (flagImg != null) {
                int flagH = Math.min(flagImg.getHeight(), Math.max(1, h));
                TextureTracker.spriteFlagCache.put(i,
                    copySpriteImage(flagImg, flagImg.getWidth(), flagH, spriteSize));
                flagOffset = pixelOffset;
                writeSpriteFramePixels(flagImg, flagImg.getWidth(), flagH, 0, spriteSize,
                    memAddress(pixelBuf) + pixelOffset);
                pixelOffset += bytesPerSprite;
                channelMask |= CHANNEL_FLAG;
                flagUploaded++;
            }

            updateBuf.putInt(updateBase + 4, channelMask);
            updateBuf.putInt(updateBase + 8, specOffset);
            updateBuf.putInt(updateBase + 12, normalOffset);
            updateBuf.putInt(updateBase + 16, flagOffset);
            updateBuf.putInt(updateBase + 20, 0);
            updateOffset += UPDATE_SIZE;
        }
        boolean uploaded = false;
        try {
            NativeUploadGuards.assertDirectCapacity(updateBuf, updateOffset, "nativeReceiveSparseAuxBatch/updates");
            NativeUploadGuards.assertDirectCapacity(pixelBuf, pixelOffset, "nativeReceiveSparseAuxBatch/pixels");
            uploaded = TextureArrayBridge.nativeReceiveSparseAuxBatch(
                memAddress(updateBuf), updateCount, memAddress(pixelBuf), pixelOffset,
                metadataCount > 0 ? memAddress(metadataBuf) : 0L, metadataCount, generation);
        } catch (UnsatisfiedLinkError ignored) {
        }
        if (!uploaded) {
            LOGGER.warn("[TextureSystem] Sparse aux batch upload failed: updates={} bytes={} metadataUpdates={}",
                updateCount, pixelOffset, metadataCount);
            return new SparseAuxReport(0, 0, 0, 0L);
        }
        LOGGER.info("[TextureSystem] Sparse aux batch: updates={} bytes={} metadataUpdates={} "
                + "specular={} normal={} flag={}",
            updateCount, pixelOffset, metadataCount, specUploaded, normalUploaded, flagUploaded);
        return new SparseAuxReport(specUploaded, normalUploaded, flagUploaded, pixelOffset);
    }

    private static int heightRangePacked(NativeImage normal, NativeImage albedo) {
        if (normal == null || normal.getWidth() <= 0 || normal.getHeight() <= 0) {
            return -1;
        }
        int min = 255;
        int max = 0;
        boolean foundOpaquePixel = false;
        int stepX = Math.max(1, normal.getWidth() / 64);
        int stepY = Math.max(1, normal.getHeight() / 64);
        for (int y = 0; y < normal.getHeight(); y += stepY) {
            for (int x = 0; x < normal.getWidth(); x += stepX) {
                if (albedo != null
                    && (((sampleScaledArgb(albedo, x, y, normal.getWidth(), normal.getHeight()) >>> 24) & 0xFF)
                    == 0)) {
                    continue;
                }
                int alpha = (normal.getColorArgb(x, y) >>> 24) & 0xFF;
                min = Math.min(min, alpha);
                max = Math.max(max, alpha);
                foundOpaquePixel = true;
            }
        }
        return foundOpaquePixel && max > min ? min | (max << 8) : -1;
    }

    private static int sampleScaledArgb(NativeImage image, int x, int y, int sourceW, int sourceH) {
        int ix = Math.max(0, Math.min(image.getWidth() - 1, x * image.getWidth() / Math.max(1, sourceW)));
        int iy = Math.max(0, Math.min(image.getHeight() - 1, y * image.getHeight() / Math.max(1, sourceH)));
        return image.getColorArgb(ix, iy);
    }

    private record SparseAuxReport(int specularUploaded,
                                   int normalUploaded,
                                   int flagUploaded,
                                   long bytesUploaded) {
    }

    private record TierUploadReport(int uploadedPages,
                                    int uploadedLayers,
                                    long bytesUploaded,
                                    int cacheHits,
                                    int cacheMisses,
                                    int cacheWrites,
                                    long cachedBytes) {
    }

    
    private static boolean uploadV4SpriteRegistry(List<Map.Entry<Identifier, Sprite>> sorted,
                                                  int uploadCount,
                                                  long generation) {
        if (uploadCount <= 0) {
            return true;
        }
        final int ENTRY_SIZE = 32;
        int count = Math.min(uploadCount, TextureTracker.MAX_TEXTURES);
        ByteBuffer entries = ByteBuffer.allocateDirect(count * ENTRY_SIZE).order(ByteOrder.nativeOrder());
        for (int i = 0; i < count; i++) {
            Sprite sprite = sorted.get(i).getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            int frameCount = Math.max(1, TextureTracker.spriteFrameCountV4[i]);
            int flags = TextureTracker.encodeSpriteSourceFlags(
                TextureTracker.spriteSpecularSource[i],
                TextureTracker.spriteNormalSource[i]);
            if (img != null) {
                INativeImageExt auxExt = (INativeImageExt) (Object) img;
                if (auxExt.neoVoxelRT$getSpecularNativeImage() != null) {
                    flags |= TextureTracker.SPRITE_FLAG_HAS_SPECULAR;
                }
                NativeImage normalImg = auxExt.neoVoxelRT$getNormalNativeImage();
                if (normalImg != null) {
                    flags |= TextureTracker.SPRITE_FLAG_HAS_NORMAL;
                    byte normalSource = TextureTracker.spriteNormalSource[i];
                    boolean authoredHeight = normalSource == TextureTracker.SOURCE_PACK_AUTHORED
                        || normalSource == TextureTracker.SOURCE_USER_CUSTOM;
                    if (authoredHeight && AuxiliaryTextures.hasVisibleHeightAlphaRange(normalImg, img)) {
                        flags |= TextureTracker.SPRITE_FLAG_HAS_HEIGHT;
                    }
                }
            }
            int layer = Math.max(0, TextureTracker.spriteAlbedoLayer[i]);
            int normalLayer = Math.max(0, TextureTracker.spriteNormalLayer[i]);
            int off = i * ENTRY_SIZE;
            entries.putInt(off, layer);
            entries.putInt(off + 4, frameCount);
            entries.putInt(off + 8, 1);
            entries.putInt(off + 12, flags);
            entries.putInt(off + 16,
                (flags & TextureTracker.SPRITE_FLAG_HAS_SPECULAR) != 0 ? layer : -1);
            entries.putInt(off + 20,
                (flags & TextureTracker.SPRITE_FLAG_HAS_NORMAL) != 0 ? normalLayer : -1);
            entries.putInt(off + 24, -1);
            entries.putInt(off + 28, -1);
        }
        try {
            NativeUploadGuards.assertDirectCapacity(entries, (long) count * ENTRY_SIZE,
                "nativeUpdateSpriteRegistrySparseV4");
            return TextureArrayBridgeV4.nativeUpdateSpriteRegistrySparseV4(
                generation, memAddress(entries), count);
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    private static List<Map.Entry<Identifier, Sprite>> sortedEntries(Map<Identifier, Sprite> regions) {
        if (regions == null) return List.of();
        List<Map.Entry<Identifier, Sprite>> sorted = new ArrayList<>(regions.entrySet());
        sorted.sort(Comparator.comparing(e -> e.getKey().toString()));
        return sorted;
    }
private static void writeSpriteFramePixels(NativeImage img, int srcW, int srcH,
                                               int frameIndex, int dstSize, long dstPtr) {
        if (img == null || dstSize <= 0 || dstPtr == 0L) return;
        int frameY = Math.max(0, frameIndex) * Math.max(1, srcH);
        int sourceW = Math.max(1, Math.min(srcW, img.getWidth()));
        int remainingH = img.getHeight() - frameY;
        if (remainingH <= 0) return;
        int sourceH = Math.max(1, Math.min(srcH, remainingH));

        if (sourceW == dstSize && sourceH == dstSize && img.getWidth() == sourceW
            && img.getFormat().getChannelCount() == 4) {
            long srcPtr = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt)
                (Object) img).neoVoxelRT$getPointer() + (long) frameY * sourceW * 4L;
            memCopy(srcPtr, dstPtr, (long) dstSize * dstSize * 4L);
            return;
        }

        for (int y = 0; y < dstSize; y++) {
            int sampleY = frameY + Math.min(sourceH - 1, (int) (((long) y * sourceH) / dstSize));
            for (int x = 0; x < dstSize; x++) {
                int sampleX = Math.min(sourceW - 1, (int) (((long) x * sourceW) / dstSize));
                int argb = img.getColorArgb(sampleX, sampleY);
                int offset = (y * dstSize + x) * 4;
                memPutByte(dstPtr + offset,     (byte) ((argb >> 16) & 0xFF));
                memPutByte(dstPtr + offset + 1, (byte) ((argb >> 8) & 0xFF));
                memPutByte(dstPtr + offset + 2, (byte) (argb & 0xFF));
                memPutByte(dstPtr + offset + 3, (byte) ((argb >> 24) & 0xFF));
            }
        }
    }

    private static NativeImage copySpriteImage(NativeImage img, int srcW, int srcH, int dstSize) {
        NativeImage copy = new NativeImage(NativeImage.Format.RGBA, dstSize, dstSize, false);
        if (img == null || dstSize <= 0) return copy;
        int sourceW = Math.max(1, Math.min(srcW, img.getWidth()));
        int sourceH = Math.max(1, Math.min(srcH, img.getHeight()));
        for (int y = 0; y < dstSize; y++) {
            int sampleY = Math.min(sourceH - 1, (int) (((long) y * sourceH) / dstSize));
            for (int x = 0; x < dstSize; x++) {
                int sampleX = Math.min(sourceW - 1, (int) (((long) x * sourceW) / dstSize));
                copy.setColorArgb(x, y, img.getColorArgb(sampleX, sampleY));
            }
        }
        return copy;
    }
}
