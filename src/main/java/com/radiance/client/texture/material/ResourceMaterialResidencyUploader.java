package com.radiance.client.texture.material;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceMaterialResidencyUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final int FIRST_COMPAT_PAGE = 1;
    private static final int PAGE_BUDGET = ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX - FIRST_COMPAT_PAGE;
    private static final int TARGET_PAGE_CAPACITY = 512;
    private static final long MAX_PAGE_BYTES = 128L * 1024L * 1024L;

    private ResourceMaterialResidencyUploader() {
    }

    public static JsonObject uploadFromCompatReport(JsonObject root,
        ResourceMaterialRegistry.Snapshot snapshot, boolean enabled) {
        JsonObject json = new JsonObject();
        json.addProperty("requested", enabled);
        json.addProperty("backend", "renderer_owned_resolution_tiered_pages");
        json.addProperty("firstCompatPage", FIRST_COMPAT_PAGE);
        json.addProperty("pageBudget", PAGE_BUDGET);
        if (!enabled) {
            json.addProperty("attempted", false);
            json.addProperty("reason", "native_upload_not_requested");
            return json;
        }
        int layerSize = TextureTracker.currentSpriteLayerSize;
        if (snapshot == null || snapshot.records().isEmpty()) {
            json.addProperty("attempted", false);
            json.addProperty("reason", "empty_material_snapshot");
            return json;
        }
        if (layerSize <= 0) {
            json.addProperty("attempted", false);
            json.addProperty("reason", "unknown_sprite_layer_size");
            return json;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ResourceManager resourceManager = client == null ? null : client.getResourceManager();
        if (resourceManager == null) {
            json.addProperty("attempted", false);
            json.addProperty("reason", "resource_manager_unavailable");
            return json;
        }

        List<UploadItem> items = collectUploadItems(root);
        int bytesPerLayer = layerSize * layerSize * 4;
        int pageCapacity = pageCapacity(bytesPerLayer);
        int materialCapacity = pageCapacity * PAGE_BUDGET;
        int pagesRequired = pagesRequired(items.size(), pageCapacity);
        json.addProperty("attempted", true);
        json.addProperty("layerSize", layerSize);
        json.addProperty("bytesPerLayer", bytesPerLayer);
        json.addProperty("pageCapacity", pageCapacity);
        json.addProperty("pageMax", ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX);
        json.addProperty("pagesRequired", pagesRequired);
        json.addProperty("percentOfPageBudgetRequired",
            PAGE_BUDGET <= 0 ? 0.0 : (100.0 * pagesRequired) / PAGE_BUDGET);
        json.addProperty("materialCapacity", materialCapacity);
        json.addProperty("candidateMaterialCount", items.size());

        long uploadStartedNanos = System.nanoTime();
        UploadStats totalStats = new UploadStats();
        Map<Integer, ResourceMaterialRegistry.ResidencyHandle> handles = new LinkedHashMap<>();
        JsonArray pageReports = new JsonArray();
        int nextItem = 0;
        int uploadedPages = 0;
        int skippedMissingAlbedo = 0;
        int failedImages = 0;
        int nativePageFailures = 0;
        int displacementEligible = 0;
        int displacementBlocked = 0;
        long generation = snapshot.generation();
        LOGGER.info("[MaterialCompat] Material page upload starting: {} candidate materials, layerSize={}, "
                + "pageCapacity={}, pageBudget={}, generation={}",
            items.size(), layerSize, pageCapacity, PAGE_BUDGET, generation);
        JsonObject startEvent = new JsonObject();
        startEvent.addProperty("candidateMaterialCount", items.size());
        startEvent.addProperty("layerSize", layerSize);
        startEvent.addProperty("pageCapacity", pageCapacity);
        startEvent.addProperty("pageBudget", PAGE_BUDGET);
        startEvent.addProperty("pageMax", ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX);
        startEvent.addProperty("pagesRequired", pagesRequired);
        startEvent.addProperty("percentOfPageBudgetRequired",
            PAGE_BUDGET <= 0 ? 0.0 : (100.0 * pagesRequired) / PAGE_BUDGET);
        startEvent.addProperty("materialCapacity", materialCapacity);
        ResourceMaterialRuntimeStatus.write("residencyStarted", generation, startEvent);

        ByteBuffer defaultNormal = defaultNormalLayer(bytesPerLayer);
        long defaultNormalPtr = memAddress(defaultNormal);
        for (int page = FIRST_COMPAT_PAGE;
             page < ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX && nextItem < items.size();
             page++) {
            long pageStartedNanos = System.nanoTime();
            UploadStats pageStats = new UploadStats();
            long allocationStartedNanos = System.nanoTime();
            ByteBuffer albedo = directPageBuffer(pageCapacity, bytesPerLayer);
            ByteBuffer specular = directPageBuffer(pageCapacity, bytesPerLayer);
            ByteBuffer normal = directPageBuffer(pageCapacity, bytesPerLayer);
            ByteBuffer flag = directPageBuffer(pageCapacity, bytesPerLayer);
            pageStats.pageAllocationNanos += elapsedNanos(allocationStartedNanos);
            long albedoPtr = memAddress(albedo);
            long specularPtr = memAddress(specular);
            long normalPtr = memAddress(normal);
            long flagPtr = memAddress(flag);
            long initStartedNanos = System.nanoTime();
            memSet(specularPtr, 0, (long) pageCapacity * bytesPerLayer);
            memSet(flagPtr, 0, (long) pageCapacity * bytesPerLayer);
            pageStats.pageInitNanos += elapsedNanos(initStartedNanos);

            JsonObject pageReport = new JsonObject();
            pageReport.addProperty("page", page);
            int layer = 0;
            Map<Integer, ResourceMaterialRegistry.ResidencyHandle> pageHandles = new LinkedHashMap<>();
            while (layer < pageCapacity && nextItem < items.size()) {
                UploadItem item = items.get(nextItem++);
                LayerResult result = writeLayer(resourceManager, item, layerSize,
                    albedoPtr + (long) layer * bytesPerLayer,
                    specularPtr + (long) layer * bytesPerLayer,
                    normalPtr + (long) layer * bytesPerLayer,
                    flagPtr + (long) layer * bytesPerLayer,
                    defaultNormalPtr, bytesPerLayer, pageStats);
                if (!result.uploaded()) {
                    if (result.missingAlbedo()) {
                        skippedMissingAlbedo++;
                    } else {
                        failedImages++;
                    }
                    continue;
                }
                if (result.displacementEligible()) {
                    displacementEligible++;
                }
                if (result.displacementBlocked()) {
                    displacementBlocked++;
                }
                pageHandles.put(item.materialId(), ResourceMaterialRegistry.ResidencyHandle.sameLayer(
                    page, layer, layerSize, result.hasSpecular(),
                    result.displacementEligible(), result.displacementBlocked(),
                    result.heightRangePacked()));
                layer++;
            }

            pageReport.addProperty("layerCount", layer);
            if (layer == 0) {
                pageStats.pageTotalNanos += elapsedNanos(pageStartedNanos);
                totalStats.add(pageStats);
                pageReport.add("timingMs", pageStats.timingJson());
                pageReport.add("displacement", pageStats.displacementJson());
                pageReports.add(pageReport);
                continue;
            }
            boolean uploaded;
            long nativeStartedNanos = System.nanoTime();
            try {
                uploaded = TextureArrayBridge.nativeReceiveMaterialTexturePage(
                    page, layerSize, layer,
                    albedoPtr, specularPtr, normalPtr, flagPtr, generation);
            } catch (UnsatisfiedLinkError e) {
                uploaded = false;
            }
            pageStats.nativePageUploadNanos += elapsedNanos(nativeStartedNanos);
            pageReport.addProperty("uploaded", uploaded);
            if (uploaded) {
                uploadedPages++;
                handles.putAll(pageHandles);
                long tableStartedNanos = System.nanoTime();
                ResourceMaterialRegistry.mergeResidentMaterialHandles(pageHandles);
                boolean materialTableUploaded = ResourceMaterialRegistry.uploadActiveTableToNative();
                pageStats.materialTableReuploadNanos += elapsedNanos(tableStartedNanos);
                pageReport.addProperty("materialTableUploaded", materialTableUploaded);
                writePageStatus(generation, "residencyPageUploaded", page, layer, uploadedPages,
                    handles.size(), items.size(), nextItem, materialTableUploaded, pagesRequired, pageStats);
                LOGGER.info("[MaterialCompat] Material page {} uploaded: {} layers, {} total resident handles",
                    page, layer, handles.size());
            } else {
                nativePageFailures++;
                writePageStatus(generation, "residencyPageFailed", page, layer, uploadedPages,
                    handles.size(), items.size(), nextItem, false, pagesRequired, pageStats);
                LOGGER.warn("[MaterialCompat] Material page {} upload failed: {} layers", page, layer);
            }
            pageStats.pageTotalNanos += elapsedNanos(pageStartedNanos);
            totalStats.add(pageStats);
            pageReport.add("timingMs", pageStats.timingJson());
            pageReport.add("displacement", pageStats.displacementJson());
            pageReports.add(pageReport);
        }

        ResourceMaterialRegistry.registerResidentMaterialHandles(handles);
        json.addProperty("uploadedPages", uploadedPages);
        json.addProperty("uploadedMaterials", handles.size());
        json.addProperty("skippedMissingAlbedo", skippedMissingAlbedo);
        json.addProperty("failedImages", failedImages);
        json.addProperty("nativePageFailures", nativePageFailures);
        json.addProperty("displacementEligibleMaterials", displacementEligible);
        json.addProperty("displacementBlockedMaterials", displacementBlocked);
        json.addProperty("deferredCandidateMaterials", Math.max(0, items.size() - nextItem));
        totalStats.totalUploadNanos = elapsedNanos(uploadStartedNanos);
        json.add("timingMs", totalStats.timingJson());
        json.add("displacement", totalStats.displacementJson());
        json.add("pages", pageReports);
        ResourceMaterialRuntimeStatus.write("residencyUploadFinished", generation, json);
        LOGGER.info("[MaterialCompat] Material page upload: {} materials across {} pages, {} deferred",
            handles.size(), uploadedPages, Math.max(0, items.size() - nextItem));
        return json;
    }

    private static int pagesRequired(int materialCount, int pageCapacity) {
        if (materialCount <= 0 || pageCapacity <= 0) {
            return 0;
        }
        return (materialCount + pageCapacity - 1) / pageCapacity;
    }

    private static void writePageStatus(long generation, String status, int page, int layerCount,
        int uploadedPages, int uploadedMaterials, int candidateMaterialCount, int nextItem,
        boolean materialTableUploaded, int pagesRequired, UploadStats pageStats) {
        JsonObject event = new JsonObject();
        event.addProperty("page", page);
        event.addProperty("layerCount", layerCount);
        event.addProperty("uploadedPages", uploadedPages);
        event.addProperty("uploadedMaterials", uploadedMaterials);
        event.addProperty("candidateMaterialCount", candidateMaterialCount);
        event.addProperty("remainingCandidateMaterials", Math.max(0, candidateMaterialCount - nextItem));
        event.addProperty("pageBudget", PAGE_BUDGET);
        event.addProperty("pageMax", ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX);
        event.addProperty("pagesRequired", pagesRequired);
        event.addProperty("materialTableUploaded", materialTableUploaded);
        if (pageStats != null) {
            event.add("timingMs", pageStats.timingJson());
            event.add("displacement", pageStats.displacementJson());
        }
        ResourceMaterialRuntimeStatus.write(status, generation, event);
    }

    private static List<UploadItem> collectUploadItems(JsonObject root) {
        JsonArray dependencies = array(object(root, "activeCtmAtlasDependencies"), "dependencies");
        ArrayList<UploadItem> items = new ArrayList<>();
        for (JsonElement element : dependencies) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject dependency = element.getAsJsonObject();
            if (!boolProperty(dependency, "present")) {
                continue;
            }
            String path = stringProperty(dependency, "path");
            int materialId = ResourceMaterialRegistry.materialIdForCompatCtmAssetPathExact(path);
            if (materialId < 0) {
                continue;
            }
            items.add(new UploadItem(
                materialId,
                path,
                stringProperty(dependency, "specularPath"),
                stringProperty(dependency, "normalPath"),
                stringProperty(dependency, "flagPath"),
                boolProperty(dependency, "specularPresent"),
                boolProperty(dependency, "normalPresent"),
                boolProperty(dependency, "flagPresent")));
        }
        return items;
    }

    private static LayerResult writeLayer(ResourceManager resourceManager, UploadItem item,
        int layerSize, long albedoPtr, long specularPtr, long normalPtr, long flagPtr,
        long defaultNormalPtr, int bytesPerLayer, UploadStats stats) {
        NativeImage albedo = null;
        NativeImage specular = null;
        NativeImage normal = null;
        NativeImage flag = null;
        try {
            if (stats != null) {
                stats.albedoRequested++;
            }
            albedo = readImage(resourceManager, item.albedoPath(), ImageChannel.ALBEDO, stats);
            if (albedo == null) {
                if (stats != null) {
                    stats.missingAlbedo++;
                }
                return LayerResult.missingAlbedoResult();
            }
            memCopy(defaultNormalPtr, normalPtr, bytesPerLayer);
            writeImagePixels(albedo, layerSize, albedoPtr, stats);
            boolean hasSpecular = false;
            if (item.specularPresent() && !item.specularPath().isBlank()) {
                if (stats != null) {
                    stats.specularRequested++;
                }
                specular = readImage(resourceManager, item.specularPath(), ImageChannel.SPECULAR, stats);
                if (specular != null) {
                    if (stats != null) {
                        stats.specularLoaded++;
                    }
                    writeImagePixels(specular, layerSize, specularPtr, stats);
                    hasSpecular = true;
                }
            }
            if (item.normalPresent() && !item.normalPath().isBlank()) {
                if (stats != null) {
                    stats.normalRequested++;
                }
                normal = readImage(resourceManager, item.normalPath(), ImageChannel.NORMAL, stats);
                if (normal != null) {
                    if (stats != null) {
                        stats.normalLoaded++;
                    }
                    writeImagePixels(normal, layerSize, normalPtr, stats);
                }
            }
            if (item.flagPresent() && !item.flagPath().isBlank()) {
                if (stats != null) {
                    stats.flagRequested++;
                }
                flag = readImage(resourceManager, item.flagPath(), ImageChannel.FLAG, stats);
                if (flag != null) {
                    if (stats != null) {
                        stats.flagLoaded++;
                    }
                    writeImagePixels(flag, layerSize, flagPtr, stats);
                }
            }
            HeightInfo height = heightInfo(item.albedoPath(), albedo, normal, stats);
            return new LayerResult(true, false, hasSpecular,
                height.eligible(), height.blocked(), height.rangePacked());
        } catch (IOException | RuntimeException e) {
            LOGGER.debug("[MaterialCompat] Failed to upload CTM material {}", item.albedoPath(), e);
            return LayerResult.failed();
        } finally {
            closeQuietly(flag);
            closeQuietly(normal);
            closeQuietly(specular);
            closeQuietly(albedo);
        }
    }

    private static NativeImage readImage(ResourceManager resourceManager, String assetPath,
        ImageChannel channel, UploadStats stats)
        throws IOException {
        Identifier id = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(assetPath);
        if (id == null) {
            return null;
        }
        long lookupStartedNanos = System.nanoTime();
        Optional<Resource> optional = resourceManager.getResource(id);
        if (stats != null) {
            stats.resourceLookupNanos += elapsedNanos(lookupStartedNanos);
        }
        if (optional.isEmpty()) {
            return null;
        }
        long decodeStartedNanos = System.nanoTime();
        try (InputStream input = optional.get().getInputStream()) {
            NativeImage image = NativeImage.read(input);
            if (stats != null) {
                long decodeNanos = elapsedNanos(decodeStartedNanos);
                stats.pngDecodeNanos += decodeNanos;
                if (channel == ImageChannel.ALBEDO) {
                    stats.albedoDecodeNanos += decodeNanos;
                    stats.albedoLoaded++;
                } else {
                    stats.sidecarDecodeNanos += decodeNanos;
                }
            }
            return image;
        }
    }

    private static void writeImagePixels(NativeImage image, int dstSize, long dstPtr,
        UploadStats stats) {
        if (image == null || dstSize <= 0 || dstPtr == 0L) {
            return;
        }
        long startedNanos = System.nanoTime();
        if (image.getWidth() == dstSize && image.getHeight() == dstSize
            && image.getFormat().getChannelCount() == 4) {
            long srcPtr = ((INativeImageExt) (Object) image).neoVoxelRT$getPointer();
            memCopy(srcPtr, dstPtr, (long) dstSize * dstSize * 4L);
            if (stats != null) {
                stats.pixelTransferNanos += elapsedNanos(startedNanos);
                stats.directPixelCopies++;
            }
            return;
        }
        int sourceW = Math.max(1, image.getWidth());
        int sourceH = Math.max(1, image.getHeight());
        for (int y = 0; y < dstSize; y++) {
            int sampleY = Math.min(sourceH - 1, (int) (((long) y * sourceH) / dstSize));
            for (int x = 0; x < dstSize; x++) {
                int sampleX = Math.min(sourceW - 1, (int) (((long) x * sourceW) / dstSize));
                int argb = image.getColorArgb(sampleX, sampleY);
                long offset = ((long) y * dstSize + x) * 4L;
                memPutByte(dstPtr + offset, (byte) ((argb >> 16) & 0xFF));
                memPutByte(dstPtr + offset + 1, (byte) ((argb >> 8) & 0xFF));
                memPutByte(dstPtr + offset + 2, (byte) (argb & 0xFF));
                memPutByte(dstPtr + offset + 3, (byte) ((argb >> 24) & 0xFF));
            }
        }
        if (stats != null) {
            stats.pixelTransferNanos += elapsedNanos(startedNanos);
            stats.resizedPixelCopies++;
        }
    }

    private static HeightInfo heightInfo(String assetPath, NativeImage albedo, NativeImage normal,
        UploadStats stats) {
        long startedNanos = System.nanoTime();
        if (normal == null || normal.getWidth() <= 0 || normal.getHeight() <= 0) {
            if (stats != null) {
                stats.heightClassificationNanos += elapsedNanos(startedNanos);
                stats.heightMissingNormal++;
            }
            return HeightInfo.none(false);
        }
        int min = 255;
        int max = 0;
        for (int y = 0; y < normal.getHeight(); y++) {
            for (int x = 0; x < normal.getWidth(); x++) {
                int alpha = (normal.getColorArgb(x, y) >>> 24) & 0xFF;
                min = Math.min(min, alpha);
                max = Math.max(max, alpha);
            }
        }
        boolean nonUniform = max > min;
        boolean uniform255 = min == 255 && max == 255;
        boolean blockedByCutout = isKnownCutoutOrFluid(assetPath);
        boolean blockedByAlpha = hasNonOpaqueAlpha(albedo);
        if (stats != null) {
            stats.heightClassificationNanos += elapsedNanos(startedNanos);
            if (nonUniform) {
                stats.normalAlphaNonUniform++;
                stats.authoredHeightMaterials++;
            } else if (uniform255) {
                stats.normalAlphaUniform255++;
            } else {
                stats.normalAlphaUniformOther++;
            }
            if (blockedByCutout) {
                stats.heightBlockedByCutoutOrFluid++;
            }
            if (blockedByAlpha) {
                stats.heightBlockedByAlpha++;
            }
            if (!nonUniform) {
                stats.heightBlockedByUniformAlpha++;
            }
        }
        if (blockedByCutout || blockedByAlpha) {
            return HeightInfo.none(nonUniform);
        }
        if (max <= min) {
            return HeightInfo.none(false);
        }
        return new HeightInfo(true, false, min | (max << 8));
    }

    private static boolean hasNonOpaqueAlpha(NativeImage image) {
        if (image == null) {
            return false;
        }
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (((image.getColorArgb(x, y) >>> 24) & 0xFF) < 255) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isKnownCutoutOrFluid(String assetPath) {
        String p = assetPath == null ? "" : assetPath.toLowerCase(Locale.ROOT);
        return p.contains("leaves") || p.contains("leaf") || p.contains("glass")
            || p.contains("pane") || p.contains("water") || p.contains("lava")
            || p.contains("ice") || p.contains("vine") || p.contains("fern")
            || p.contains("grass") || p.contains("sapling") || p.contains("flower")
            || p.contains("mushroom") || p.contains("rail") || p.contains("ladder")
            || p.contains("chain") || p.contains("torch") || p.contains("fire")
            || p.contains("portal") || p.contains("crop") || p.contains("door")
            || p.contains("trapdoor");
    }

    private static ByteBuffer defaultNormalLayer(int bytesPerLayer) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(bytesPerLayer).order(ByteOrder.nativeOrder());
        long ptr = memAddress(buffer);
        for (int offset = 0; offset < bytesPerLayer; offset += 4) {
            memPutByte(ptr + offset, (byte) 128);
            memPutByte(ptr + offset + 1, (byte) 128);
            memPutByte(ptr + offset + 2, (byte) 255);
            memPutByte(ptr + offset + 3, (byte) 255);
        }
        return buffer;
    }

    private static ByteBuffer directPageBuffer(int pageCapacity, int bytesPerLayer) {
        return ByteBuffer.allocateDirect(pageCapacity * bytesPerLayer).order(ByteOrder.nativeOrder());
    }

    private static int pageCapacity(int bytesPerLayer) {
        if (bytesPerLayer <= 0) {
            return 1;
        }
        long capacity = Math.max(1L, MAX_PAGE_BYTES / (bytesPerLayer * 4L));
        return (int) Math.max(1L, Math.min(TARGET_PAGE_CAPACITY, capacity));
    }

    private static long elapsedNanos(long startedNanos) {
        return Math.max(0L, System.nanoTime() - startedNanos);
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static void removeHandlesForPage(Map<Integer, ResourceMaterialRegistry.ResidencyHandle> handles,
        int page) {
        handles.entrySet().removeIf(entry -> entry.getValue().albedoPage() == page);
    }

    private static void closeQuietly(NativeImage image) {
        if (image != null) {
            image.close();
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        if (json != null && json.has(key) && json.get(key).isJsonObject()) {
            return json.getAsJsonObject(key);
        }
        return new JsonObject();
    }

    private static JsonArray array(JsonObject json, String key) {
        if (json != null && json.has(key) && json.get(key).isJsonArray()) {
            return json.getAsJsonArray(key);
        }
        return new JsonArray();
    }

    private static String stringProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return "";
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static boolean boolProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return false;
        try {
            return json.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private record UploadItem(int materialId,
                              String albedoPath,
                              String specularPath,
                              String normalPath,
                              String flagPath,
                              boolean specularPresent,
                              boolean normalPresent,
                              boolean flagPresent) {
    }

    private enum ImageChannel {
        ALBEDO,
        SPECULAR,
        NORMAL,
        FLAG
    }

    private static final class UploadStats {
        long totalUploadNanos;
        long pageTotalNanos;
        long pageAllocationNanos;
        long pageInitNanos;
        long resourceLookupNanos;
        long pngDecodeNanos;
        long albedoDecodeNanos;
        long sidecarDecodeNanos;
        long pixelTransferNanos;
        long heightClassificationNanos;
        long nativePageUploadNanos;
        long materialTableReuploadNanos;

        int albedoRequested;
        int albedoLoaded;
        int missingAlbedo;
        int specularRequested;
        int specularLoaded;
        int normalRequested;
        int normalLoaded;
        int flagRequested;
        int flagLoaded;
        int directPixelCopies;
        int resizedPixelCopies;
        int heightMissingNormal;
        int normalAlphaUniform255;
        int normalAlphaUniformOther;
        int normalAlphaNonUniform;
        int authoredHeightMaterials;
        int heightBlockedByCutoutOrFluid;
        int heightBlockedByAlpha;
        int heightBlockedByUniformAlpha;

        void add(UploadStats other) {
            if (other == null) {
                return;
            }
            totalUploadNanos += other.totalUploadNanos;
            pageTotalNanos += other.pageTotalNanos;
            pageAllocationNanos += other.pageAllocationNanos;
            pageInitNanos += other.pageInitNanos;
            resourceLookupNanos += other.resourceLookupNanos;
            pngDecodeNanos += other.pngDecodeNanos;
            albedoDecodeNanos += other.albedoDecodeNanos;
            sidecarDecodeNanos += other.sidecarDecodeNanos;
            pixelTransferNanos += other.pixelTransferNanos;
            heightClassificationNanos += other.heightClassificationNanos;
            nativePageUploadNanos += other.nativePageUploadNanos;
            materialTableReuploadNanos += other.materialTableReuploadNanos;

            albedoRequested += other.albedoRequested;
            albedoLoaded += other.albedoLoaded;
            missingAlbedo += other.missingAlbedo;
            specularRequested += other.specularRequested;
            specularLoaded += other.specularLoaded;
            normalRequested += other.normalRequested;
            normalLoaded += other.normalLoaded;
            flagRequested += other.flagRequested;
            flagLoaded += other.flagLoaded;
            directPixelCopies += other.directPixelCopies;
            resizedPixelCopies += other.resizedPixelCopies;
            heightMissingNormal += other.heightMissingNormal;
            normalAlphaUniform255 += other.normalAlphaUniform255;
            normalAlphaUniformOther += other.normalAlphaUniformOther;
            normalAlphaNonUniform += other.normalAlphaNonUniform;
            authoredHeightMaterials += other.authoredHeightMaterials;
            heightBlockedByCutoutOrFluid += other.heightBlockedByCutoutOrFluid;
            heightBlockedByAlpha += other.heightBlockedByAlpha;
            heightBlockedByUniformAlpha += other.heightBlockedByUniformAlpha;
        }

        JsonObject timingJson() {
            JsonObject json = new JsonObject();
            json.addProperty("totalUploadMs", millis(totalUploadNanos));
            json.addProperty("pageTotalMs", millis(pageTotalNanos));
            json.addProperty("pageAllocationMs", millis(pageAllocationNanos));
            json.addProperty("pageInitMs", millis(pageInitNanos));
            json.addProperty("resourceLookupMs", millis(resourceLookupNanos));
            json.addProperty("pngDecodeMs", millis(pngDecodeNanos));
            json.addProperty("albedoPngDecodeMs", millis(albedoDecodeNanos));
            json.addProperty("sidecarPngDecodeMs", millis(sidecarDecodeNanos));
            json.addProperty("pixelTransferMs", millis(pixelTransferNanos));
            json.addProperty("heightClassificationMs", millis(heightClassificationNanos));
            json.addProperty("nativePageUploadMs", millis(nativePageUploadNanos));
            json.addProperty("materialTableReuploadMs", millis(materialTableReuploadNanos));
            return json;
        }

        JsonObject displacementJson() {
            JsonObject json = new JsonObject();
            json.addProperty("albedoRequested", albedoRequested);
            json.addProperty("albedoLoaded", albedoLoaded);
            json.addProperty("missingAlbedo", missingAlbedo);
            json.addProperty("specularSidecarRequested", specularRequested);
            json.addProperty("specularSidecarResident", specularLoaded);
            json.addProperty("normalSidecarRequested", normalRequested);
            json.addProperty("normalSidecarResident", normalLoaded);
            json.addProperty("flagSidecarRequested", flagRequested);
            json.addProperty("flagSidecarResident", flagLoaded);
            json.addProperty("directPixelCopies", directPixelCopies);
            json.addProperty("resizedPixelCopies", resizedPixelCopies);
            json.addProperty("heightMissingNormal", heightMissingNormal);
            json.addProperty("normalAlphaUniform255", normalAlphaUniform255);
            json.addProperty("normalAlphaUniformOther", normalAlphaUniformOther);
            json.addProperty("normalAlphaNonUniform", normalAlphaNonUniform);
            json.addProperty("authoredHeightMaterials", authoredHeightMaterials);
            json.addProperty("heightBlockedByCutoutOrFluid", heightBlockedByCutoutOrFluid);
            json.addProperty("heightBlockedByAlpha", heightBlockedByAlpha);
            json.addProperty("heightBlockedByUniformAlpha", heightBlockedByUniformAlpha);
            return json;
        }
    }

    private record LayerResult(boolean uploaded,
                               boolean missingAlbedo,
                               boolean hasSpecular,
                               boolean displacementEligible,
                               boolean displacementBlocked,
                               int heightRangePacked) {
        static LayerResult missingAlbedoResult() {
            return new LayerResult(false, true, false, false, false, -1);
        }

        static LayerResult failed() {
            return new LayerResult(false, false, false, false, false, -1);
        }
    }

    private record HeightInfo(boolean eligible, boolean blocked, int rangePacked) {
        static HeightInfo none(boolean blocked) {
            return new HeightInfo(false, blocked, -1);
        }
    }
}
