package com.radiance.client.texture.material;

import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memByteBuffer;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memPutByte;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridgeV4;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import com.radiance.client.texture.compat.TextureLoaderDiskCache;
import com.radiance.client.texture.v4.NativeUploadGuards;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourceMaterialResidencyUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final int FIRST_COMPAT_PAGE = TextureTracker.FIRST_COMPAT_MATERIAL_PAGE;
    private static final int PAGE_BUDGET = ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX - FIRST_COMPAT_PAGE;
    private static final int TARGET_PAGE_CAPACITY = 512;
    private static final int DEFAULT_LAYER_DECODE_THREADS = 4;
    private static final int MAX_LAYER_DECODE_THREADS = 8;
    private static final int DEFAULT_DEMAND_BATCH_MATERIALS = 128;
    private static final int DEFAULT_DEMAND_BATCH_MIB = 32;
    private static final long MAX_PAGE_BYTES = 128L * 1024L * 1024L;
    private static final Object PAGE_ALLOCATOR_LOCK = new Object();
    private static long pageAllocatorGeneration = -1L;
    private static int nextAllocatorPage = FIRST_COMPAT_PAGE;
    private static int nextAllocatorLayer = 0;

    private ResourceMaterialResidencyUploader() {
    }

    public static JsonObject uploadFromCompatReport(JsonObject root,
        ResourceMaterialRegistry.Snapshot snapshot, boolean enabled) {
        return uploadFromCompatReport(root, snapshot, enabled, false);
    }

    public static JsonObject uploadFromCompatReport(JsonObject root,
        ResourceMaterialRegistry.Snapshot snapshot, boolean enabled, boolean visibleOnly) {
        return uploadFromCompatReport(root, snapshot, enabled, visibleOnly, "");
    }

    public static JsonObject uploadFromCompatReport(JsonObject root,
        ResourceMaterialRegistry.Snapshot snapshot, boolean enabled, boolean visibleOnly, String cacheKey) {
        JsonObject json = new JsonObject();
        json.addProperty("requested", enabled);
        json.addProperty("backend", "renderer_owned_resolution_tiered_pages");
        json.addProperty("firstCompatPage", FIRST_COMPAT_PAGE);
        json.addProperty("pageBudget", PAGE_BUDGET);
        json.addProperty("visibleOnly", visibleOnly);
        json.addProperty("fullPreloadStarted", enabled && !visibleOnly);
        json.addProperty("layerPayloadCacheKey", cacheKey == null ? "" : cacheKey);
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

        long generation = snapshot.generation();
        List<UploadItem> items = collectUploadItems(root, generation, visibleOnly);
        if (visibleOnly && items.isEmpty()) {
            json.addProperty("attempted", false);
            json.addProperty("reason", "no_visible_material_demand");
            json.add("visibleResidency", ResourceMaterialResidencyDemand.summaryJson(generation));
            ResourceMaterialRuntimeStatus.write("residencySkippedNoVisibleDemand", generation, json);
            return json;
        }
        int bytesPerLayer = layerSize * layerSize * 4;
        int queuedMaterialCount = items.size();
        int budgetedMaterialCount = demandBatchLimit(queuedMaterialCount, bytesPerLayer, visibleOnly);
        if (budgetedMaterialCount < items.size()) {
            items = new ArrayList<>(items.subList(0, budgetedMaterialCount));
        }
        int pageCapacity = pageCapacity(bytesPerLayer);
        int materialCapacity = pageCapacity * PAGE_BUDGET;
        int pagesRequired = pagesRequired(items.size(), pageCapacity);
        int ctmUnaddressableMaterials = Math.max(0, queuedMaterialCount - materialCapacity);
        boolean pagesExhausted = pagesRequired > PAGE_BUDGET;
        int layerDecodeThreads = layerDecodeThreads();
        json.addProperty("attempted", true);
        json.addProperty("layerSize", layerSize);
        json.addProperty("bytesPerLayer", bytesPerLayer);
        json.addProperty("pageCapacity", pageCapacity);
        json.addProperty("pageMax", ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX);
        json.addProperty("ctmFirstMaterialPage", FIRST_COMPAT_PAGE);
        json.addProperty("ctmMaterialPageBudget", PAGE_BUDGET);
        json.addProperty("ctmResidentCapacity", materialCapacity);
        json.addProperty("ctmPresentMaterials", queuedMaterialCount);
        json.addProperty("ctmUnaddressableMaterials", ctmUnaddressableMaterials);
        json.addProperty("pagesExhausted", pagesExhausted);
        json.addProperty("pagesRequired", pagesRequired);
        json.addProperty("layerDecodeThreads", layerDecodeThreads);
        json.addProperty("percentOfPageBudgetRequired",
            PAGE_BUDGET <= 0 ? 0.0 : (100.0 * pagesRequired) / PAGE_BUDGET);
        json.addProperty("materialCapacity", materialCapacity);
        json.addProperty("queuedMaterialCount", queuedMaterialCount);
        json.addProperty("candidateMaterialCount", items.size());
        json.addProperty("deferredQueuedMaterialCount", Math.max(0, queuedMaterialCount - items.size()));
        json.addProperty("demandBatchLimitMaterials", demandBatchLimitMaterials());
        json.addProperty("demandBatchLimitMiB", demandBatchLimitMiB());

        long uploadStartedNanos = System.nanoTime();
        UploadStats totalStats = new UploadStats();
        Map<Integer, ResourceMaterialRegistry.ResidencyHandle> handles = new LinkedHashMap<>();
        JsonArray pageReports = new JsonArray();
        int nextItem = 0;
        int uploadedPages = 0;
        int skippedMissingAlbedo = 0;
        int failedImages = 0;
        int nativePageFailures = 0;
        int nativePageFailedMaterials = 0;
        int materialTableUploadFailures = 0;
        int materialTableUploadSuccesses = 0;
        int displacementEligible = 0;
        int displacementBlocked = 0;
        LOGGER.info("[MaterialCompat] Material page upload starting: {} candidate materials, layerSize={}, "
                + "pageCapacity={}, pageBudget={}, decodeThreads={}, generation={}, visibleOnly={}",
            items.size(), layerSize, pageCapacity, PAGE_BUDGET, layerDecodeThreads, generation, visibleOnly);
        JsonObject startEvent = new JsonObject();
        startEvent.addProperty("candidateMaterialCount", items.size());
        startEvent.addProperty("layerSize", layerSize);
        startEvent.addProperty("pageCapacity", pageCapacity);
        startEvent.addProperty("pageBudget", PAGE_BUDGET);
        startEvent.addProperty("layerDecodeThreads", layerDecodeThreads);
        startEvent.addProperty("pageMax", ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX);
        startEvent.addProperty("ctmFirstMaterialPage", FIRST_COMPAT_PAGE);
        startEvent.addProperty("ctmMaterialPageBudget", PAGE_BUDGET);
        startEvent.addProperty("ctmResidentCapacity", materialCapacity);
        startEvent.addProperty("ctmPresentMaterials", queuedMaterialCount);
        startEvent.addProperty("ctmUnaddressableMaterials", ctmUnaddressableMaterials);
        startEvent.addProperty("pagesExhausted", pagesExhausted);
        startEvent.addProperty("pagesRequired", pagesRequired);
        startEvent.addProperty("percentOfPageBudgetRequired",
            PAGE_BUDGET <= 0 ? 0.0 : (100.0 * pagesRequired) / PAGE_BUDGET);
        startEvent.addProperty("materialCapacity", materialCapacity);
        startEvent.addProperty("queuedMaterialCount", queuedMaterialCount);
        startEvent.addProperty("deferredQueuedMaterialCount", Math.max(0, queuedMaterialCount - items.size()));
        startEvent.addProperty("demandBatchLimitMaterials", demandBatchLimitMaterials());
        startEvent.addProperty("demandBatchLimitMiB", demandBatchLimitMiB());
        startEvent.addProperty("visibleOnly", visibleOnly);
        startEvent.addProperty("fullPreloadStarted", !visibleOnly);
        startEvent.add("visibleResidency", ResourceMaterialResidencyDemand.summaryJson(generation));
        ResourceMaterialRuntimeStatus.write("residencyStarted", generation, startEvent);

        ByteBuffer defaultNormal = defaultNormalLayer(bytesPerLayer);
        long defaultNormalPtr = memAddress(defaultNormal);
        ExecutorService layerExecutor = newLayerExecutor(layerDecodeThreads);
        try {
            while (nextItem < items.size()) {
                if (!generationMatches(generation)) {
                    json.addProperty("cancelled", true);
                    json.addProperty("cancelReason", "stale_generation_before_page");
                    ResourceMaterialRuntimeStatus.write("residencyCancelled", generation, json);
                    break;
                }
                PageAllocation allocation = allocatePageRange(generation, items.size() - nextItem, pageCapacity);
                if (allocation.layerCount() <= 0) {
                    json.addProperty("pagePoolFull", true);
                    break;
                }
                int page = allocation.page();
                int visiblePendingCandidates = promoteVisibleItems(items, nextItem, generation);
                long pageStartedNanos = System.nanoTime();
                UploadStats pageStats = new UploadStats();
                long allocationStartedNanos = System.nanoTime();
                ByteBuffer albedo = directPageBuffer(allocation.layerCount(), bytesPerLayer);
                ByteBuffer specular = directPageBuffer(allocation.layerCount(), bytesPerLayer);
                ByteBuffer normal = directPageBuffer(allocation.layerCount(), bytesPerLayer);
                ByteBuffer flag = directPageBuffer(allocation.layerCount(), bytesPerLayer);
                pageStats.pageAllocationNanos += elapsedNanos(allocationStartedNanos);
                long albedoPtr = memAddress(albedo);
                long specularPtr = memAddress(specular);
                long normalPtr = memAddress(normal);
                long flagPtr = memAddress(flag);
                long initStartedNanos = System.nanoTime();
                long uploadedBytes = (long) allocation.layerCount() * bytesPerLayer;
                assertDirectCapacity(albedo, uploadedBytes, "material albedo page upload");
                assertDirectCapacity(specular, uploadedBytes, "material specular page upload");
                assertDirectCapacity(normal, uploadedBytes, "material normal page upload");
                assertDirectCapacity(flag, uploadedBytes, "material flag page upload");
                assertDirectCapacity(defaultNormal, bytesPerLayer, "material default normal layer");
                memSet(specularPtr, 0, uploadedBytes);
                memSet(flagPtr, 0, uploadedBytes);
                pageStats.pageInitNanos += elapsedNanos(initStartedNanos);

                JsonObject pageReport = new JsonObject();
                pageReport.addProperty("page", page);
                pageReport.addProperty("startLayer", allocation.startLayer());
                pageReport.addProperty("layerCapacity", allocation.layerCapacity());
                pageReport.addProperty("visiblePendingCandidates", visiblePendingCandidates);
                pageReport.addProperty("layerDecodeThreads", layerDecodeThreads);
                List<LayerUploadFuture> layerFutures = new ArrayList<>();
                int layer = 0;
                while (layer < allocation.layerCount() && nextItem < items.size()) {
                    UploadItem item = items.get(nextItem++);
                    int targetLayer = layer++;
                    long targetAlbedoPtr = albedoPtr + (long) targetLayer * bytesPerLayer;
                    long targetSpecularPtr = specularPtr + (long) targetLayer * bytesPerLayer;
                    long targetNormalPtr = normalPtr + (long) targetLayer * bytesPerLayer;
                    long targetFlagPtr = flagPtr + (long) targetLayer * bytesPerLayer;
                    long targetOffset = (long) targetLayer * bytesPerLayer;
                    assertRange(albedo, targetOffset, bytesPerLayer, "material albedo layer write");
                    assertRange(specular, targetOffset, bytesPerLayer, "material specular layer write");
                    assertRange(normal, targetOffset, bytesPerLayer, "material normal layer write");
                    assertRange(flag, targetOffset, bytesPerLayer, "material flag layer write");
                    Future<LayerUploadResult> future = layerExecutor.submit(() -> {
                        UploadStats layerStats = new UploadStats();
                        LayerResult result = writeLayer(resourceManager, item, layerSize,
                            targetAlbedoPtr, targetSpecularPtr, targetNormalPtr, targetFlagPtr,
                            defaultNormalPtr, bytesPerLayer, layerStats, cacheKey);
                        return new LayerUploadResult(item.materialId(), result, layerStats);
                    });
                    layerFutures.add(new LayerUploadFuture(targetLayer, item, future));
                }

                Map<Integer, ResourceMaterialRegistry.ResidencyHandle> pageHandles = new LinkedHashMap<>();
                for (LayerUploadFuture layerFuture : layerFutures) {
                    LayerUploadResult uploadResult = awaitLayerUpload(layerFuture);
                    pageStats.add(uploadResult.stats());
                    LayerResult result = uploadResult.result();
                    if (!result.uploaded()) {
                        if (result.missingAlbedo()) {
                            skippedMissingAlbedo++;
                            ResourceMaterialResidencyDemand.recordPermanentFailed(generation,
                                java.util.List.of(uploadResult.materialId()));
                        } else {
                            failedImages++;
                            ResourceMaterialResidencyDemand.recordRetryableFailed(generation,
                                java.util.List.of(uploadResult.materialId()));
                        }
                        continue;
                    }
                    if (result.displacementEligible()) {
                        displacementEligible++;
                    }
                    if (result.displacementBlocked()) {
                        displacementBlocked++;
                    }
                    pageHandles.put(uploadResult.materialId(), ResourceMaterialRegistry.ResidencyHandle.sameLayer(
                        page, allocation.startLayer() + layerFuture.layer(), layerSize, result.hasSpecular(),
                        result.displacementEligible(), result.displacementBlocked(),
                        result.heightRangePacked()));
                }

                pageReport.addProperty("layerCount", layer);
                pageReport.addProperty("residentLayerCount", pageHandles.size());
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
                    long nativeBytes = (long) layer * bytesPerLayer;
                    assertDirectCapacity(albedo, nativeBytes, "material albedo native upload");
                    assertDirectCapacity(specular, nativeBytes, "material specular native upload");
                    assertDirectCapacity(normal, nativeBytes, "material normal native upload");
                    assertDirectCapacity(flag, nativeBytes, "material flag native upload");
                    uploaded = TextureArrayBridgeV4.nativeUploadTexturePageV4(
                        generation,
                        2, // NAMESPACE_CTM
                        tierIndexForLayerSize(layerSize),
                        page,
                        allocation.startLayer(),
                        layer,
                        allocation.layerCapacity(),
                        layerSize,
                        layerSize,
                        TextureArrayBridgeV4.VK_FORMAT_R8G8B8A8_UNORM,
                        albedoPtr,
                        specularPtr,
                        normalPtr,
                        flagPtr,
                        bytesPerLayer,
                        TextureArrayBridgeV4.CHANNEL_ALBEDO
                            | TextureArrayBridgeV4.CHANNEL_SPECULAR
                            | TextureArrayBridgeV4.CHANNEL_NORMAL
                            | TextureArrayBridgeV4.CHANNEL_FLAG,
                        visibleOnly);
                } catch (UnsatisfiedLinkError e) {
                    uploaded = false;
                }
                pageStats.nativePageUploadNanos += elapsedNanos(nativeStartedNanos);
                pageReport.addProperty("uploaded", uploaded);
                if (uploaded) {
                    uploadedPages++;
                    handles.putAll(pageHandles);
                    ResourceMaterialResidencyDemand.recordResident(generation, pageHandles.keySet());
                    long tableStartedNanos = System.nanoTime();
                    ResourceMaterialRegistry.ResidencyMergeStats mergeStats =
                        ResourceMaterialRegistry.mergeResidentMaterialHandles(pageHandles);
                    boolean materialTableUploaded = Options.materialTableDirtyUpdates
                        ? ResourceMaterialRegistry.uploadMaterialTableEntriesToNative(pageHandles.keySet())
                        : ResourceMaterialRegistry.uploadActiveTableToNative();
                    if (materialTableUploaded) {
                        materialTableUploadSuccesses++;
                    } else {
                        materialTableUploadFailures++;
                    }
                    pageStats.materialTableReuploadNanos += elapsedNanos(tableStartedNanos);
                    pageReport.add("residentHandleMerge", mergeStats.toJson());
                    pageReport.addProperty("materialTableUploaded", materialTableUploaded);
                    pageReport.addProperty("materialTableSparseUpdate", Options.materialTableDirtyUpdates);
                    pageStats.pageTotalNanos += elapsedNanos(pageStartedNanos);
                    writePageStatus(generation, "residencyPageUploaded", page, layer, uploadedPages,
                        handles.size(), items.size(), nextItem, materialTableUploaded, pagesRequired, pageStats);
                    LOGGER.info("[MaterialCompat] Material page {} uploaded: {} layers, {} resident handles, "
                            + "{} total resident handles",
                        page, layer, pageHandles.size(), handles.size());
                } else {
                    nativePageFailures++;
                    nativePageFailedMaterials += pageHandles.size();
                    rewindAllocation(generation, allocation);
                    ResourceMaterialResidencyDemand.recordRetryableFailed(generation, pageHandles.keySet());
                    pageStats.pageTotalNanos += elapsedNanos(pageStartedNanos);
                    writePageStatus(generation, "residencyPageFailed", page, layer, uploadedPages,
                        handles.size(), items.size(), nextItem, false, pagesRequired, pageStats);
                    LOGGER.warn("[MaterialCompat] Material page {} upload failed: {} layers", page, layer);
                }
                totalStats.add(pageStats);
                pageReport.add("timingMs", pageStats.timingJson());
                pageReport.add("displacement", pageStats.displacementJson());
                pageReports.add(pageReport);
            }
        } finally {
            shutdownLayerExecutor(layerExecutor);
        }

        ResourceMaterialRegistry.ResidencyMergeStats finalMergeStats =
            ResourceMaterialRegistry.mergeResidentMaterialHandles(handles);
        boolean finalMaterialTableUploaded = false;
        if (!handles.isEmpty() && !Options.materialTableDirtyUpdates && generationMatches(generation)) {
            long tableStartedNanos = System.nanoTime();
            finalMaterialTableUploaded = ResourceMaterialRegistry.uploadActiveTableToNative();
            if (finalMaterialTableUploaded) {
                materialTableUploadSuccesses++;
            } else {
                materialTableUploadFailures++;
            }
            totalStats.materialTableReuploadNanos += elapsedNanos(tableStartedNanos);
        }
        boolean semanticResidencyUploaded = !handles.isEmpty()
            && nativePageFailures == 0
            && materialTableUploadFailures == 0
            && generationMatches(generation);
        json.addProperty("uploadedPages", uploadedPages);
        json.addProperty("uploadedMaterials", handles.size());
        json.addProperty("residentCompatMaterials", handles.size());
        json.addProperty("semanticResidencyUploaded", semanticResidencyUploaded);
        json.add("residentHandleMerge", finalMergeStats.toJson());
        json.addProperty("materialTableUploadedFinal", finalMaterialTableUploaded);
        json.addProperty("materialTableUploadSuccesses", materialTableUploadSuccesses);
        json.addProperty("materialTableUploadFailures", materialTableUploadFailures);
        json.addProperty("materialTableFullUploadPolicy",
            Options.materialTableDirtyUpdates ? "sparse_entries_after_each_uploaded_page" : "legacy_full_uploads");
        json.addProperty("skippedMissingAlbedo", skippedMissingAlbedo);
        json.addProperty("failedImages", failedImages);
        json.addProperty("nativePageFailures", nativePageFailures);
        json.addProperty("nativePageFailedMaterials", nativePageFailedMaterials);
        json.addProperty("displacementEligibleMaterials", displacementEligible);
        json.addProperty("displacementBlockedMaterials", displacementBlocked);
        json.addProperty("deferredCandidateMaterials", Math.max(0, items.size() - nextItem));
        json.addProperty("ctmUnaddressableMaterials", Math.max(0, queuedMaterialCount - materialCapacity));
        json.addProperty("pagesExhausted", pagesRequired > PAGE_BUDGET);
        totalStats.totalUploadNanos = elapsedNanos(uploadStartedNanos);
        json.add("timingMs", totalStats.timingJson());
        json.add("displacement", totalStats.displacementJson());
        json.add("visibleResidency", ResourceMaterialResidencyDemand.summaryJson(generation));
        json.add("pages", pageReports);
        ResourceMaterialRuntimeStatus.write("residencyUploadFinished", generation, json);
        LOGGER.info("[MaterialCompat] Material page upload: {} materials across {} pages, {} deferred",
            handles.size(), uploadedPages, Math.max(0, items.size() - nextItem));
        return json;
    }

    private static PageAllocation allocatePageRange(long generation, int requestedCount, int pageCapacity) {
        if (requestedCount <= 0 || pageCapacity <= 0) {
            return PageAllocation.empty();
        }
        synchronized (PAGE_ALLOCATOR_LOCK) {
            if (pageAllocatorGeneration != generation) {
                pageAllocatorGeneration = generation;
                nextAllocatorPage = FIRST_COMPAT_PAGE;
                nextAllocatorLayer = 0;
            }
            if (nextAllocatorPage >= ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX) {
                return PageAllocation.empty();
            }
            if (nextAllocatorLayer >= pageCapacity) {
                nextAllocatorPage++;
                nextAllocatorLayer = 0;
            }
            if (nextAllocatorPage >= ResourceMaterialRegistry.MATERIAL_TEXTURE_PAGE_MAX) {
                return PageAllocation.empty();
            }
            int available = pageCapacity - nextAllocatorLayer;
            int count = Math.min(Math.max(1, requestedCount), available);
            PageAllocation allocation =
                new PageAllocation(nextAllocatorPage, nextAllocatorLayer, pageCapacity, count);
            nextAllocatorLayer += count;
            if (nextAllocatorLayer >= pageCapacity) {
                nextAllocatorPage++;
                nextAllocatorLayer = 0;
            }
            return allocation;
        }
    }

    private static void rewindAllocation(long generation, PageAllocation allocation) {
        if (allocation == null || allocation.layerCount() <= 0) {
            return;
        }
        synchronized (PAGE_ALLOCATOR_LOCK) {
            if (pageAllocatorGeneration != generation) {
                return;
            }
            int expectedPage = allocation.startLayer() + allocation.layerCount() >= allocation.layerCapacity()
                ? allocation.page() + 1
                : allocation.page();
            int expectedLayer = allocation.startLayer() + allocation.layerCount() >= allocation.layerCapacity()
                ? 0
                : allocation.startLayer() + allocation.layerCount();
            if (nextAllocatorPage == expectedPage && nextAllocatorLayer == expectedLayer) {
                nextAllocatorPage = allocation.page();
                nextAllocatorLayer = allocation.startLayer();
            }
        }
    }

    private static int tierIndexForLayerSize(int layerSize) {
        if (layerSize <= 16) return 0;
        if (layerSize <= 32) return 1;
        if (layerSize <= 64) return 2;
        if (layerSize <= 128) return 3;
        if (layerSize <= 256) return 4;
        if (layerSize <= 512) return 5;
        return 6;
    }

    private static ExecutorService newLayerExecutor(int threads) {
        return Executors.newFixedThreadPool(Math.max(1, threads), runnable -> {
            Thread thread = new Thread(runnable, "RadSER Material Layer Decode");
            thread.setDaemon(true);
            return thread;
        });
    }

    private static void shutdownLayerExecutor(ExecutorService executor) {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private static LayerUploadResult awaitLayerUpload(LayerUploadFuture layerFuture) {
        try {
            return layerFuture.future().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return LayerUploadResult.failed(layerFuture.item());
        } catch (ExecutionException e) {
            LOGGER.debug("[MaterialCompat] Failed to decode CTM material {}", layerFuture.item().albedoPath(),
                e.getCause());
            return LayerUploadResult.failed(layerFuture.item());
        }
    }

    private static int pagesRequired(int materialCount, int pageCapacity) {
        if (materialCount <= 0 || pageCapacity <= 0) {
            return 0;
        }
        return (materialCount + pageCapacity - 1) / pageCapacity;
    }

    private static int layerDecodeThreads() {
        int requested = DEFAULT_LAYER_DECODE_THREADS;
        String property = System.getProperty("radser.materialLayerDecodeThreads", "");
        if (!property.isBlank()) {
            try {
                requested = Integer.parseInt(property.trim());
            } catch (NumberFormatException ignored) {
                requested = DEFAULT_LAYER_DECODE_THREADS;
            }
        }
        int cpuLimit = Math.max(1, Runtime.getRuntime().availableProcessors());
        return Math.max(1, Math.min(Math.min(MAX_LAYER_DECODE_THREADS, cpuLimit), requested));
    }

    private static int demandBatchLimit(int queuedMaterialCount, int bytesPerLayer, boolean visibleOnly) {
        if (!visibleOnly || queuedMaterialCount <= 0) {
            return queuedMaterialCount;
        }
        int byCount = Math.max(1, Math.min(queuedMaterialCount, demandBatchLimitMaterials()));
        long bytesPerMaterial = Math.max(1L, (long) bytesPerLayer * 4L);
        long byteBudget = Math.max(1L, demandBatchLimitMiB()) * 1024L * 1024L;
        int byBytes = (int) Math.max(1L, byteBudget / bytesPerMaterial);
        return Math.max(1, Math.min(byCount, byBytes));
    }

    private static int demandBatchLimitMaterials() {
        return positiveIntProperty("radser.ctmResidencyBatchMaterials", DEFAULT_DEMAND_BATCH_MATERIALS);
    }

    private static int demandBatchLimitMiB() {
        return positiveIntProperty("radser.ctmResidencyBatchMiB", DEFAULT_DEMAND_BATCH_MIB);
    }

    private static int positiveIntProperty(String name, int fallback) {
        String property = System.getProperty(name, "");
        if (!property.isBlank()) {
            try {
                return Math.max(1, Integer.parseInt(property.trim()));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
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
        event.addProperty("ctmFirstMaterialPage", FIRST_COMPAT_PAGE);
        event.addProperty("ctmMaterialPageBudget", PAGE_BUDGET);
        event.addProperty("pagesExhausted", pagesRequired > PAGE_BUDGET);
        event.addProperty("pagesRequired", pagesRequired);
        event.addProperty("materialTableUploaded", materialTableUploaded);
        if (pageStats != null) {
            event.add("timingMs", pageStats.timingJson());
            event.add("displacement", pageStats.displacementJson());
        }
        ResourceMaterialRuntimeStatus.write(status, generation, event);
    }

    private static List<UploadItem> collectUploadItems(JsonObject root, long generation, boolean visibleOnly) {
        JsonArray dependencies = array(object(root, "activeCtmAtlasDependencies"), "dependencies");
        Set<Integer> requestedMaterialIds = visibleOnly
            ? ResourceMaterialResidencyDemand.residencyMaterialIds(generation)
            : Set.of();
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
            if (visibleOnly && (!requestedMaterialIds.contains(materialId)
                || ResourceMaterialResidencyDemand.isVisibleResident(generation, materialId))) {
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

    private static int promoteVisibleItems(List<UploadItem> items, int startIndex, long generation) {
        if (items == null || startIndex < 0 || startIndex >= items.size()) {
            return 0;
        }
        Set<Integer> requestedMaterialIds = ResourceMaterialResidencyDemand.residencyMaterialIds(generation);
        if (requestedMaterialIds.isEmpty()) {
            return 0;
        }
        int writeIndex = startIndex;
        int visiblePending = 0;
        for (int scanIndex = startIndex; scanIndex < items.size(); scanIndex++) {
            UploadItem item = items.get(scanIndex);
            if (item != null && requestedMaterialIds.contains(item.materialId())) {
                if (scanIndex != writeIndex) {
                    Collections.swap(items, scanIndex, writeIndex);
                }
                writeIndex++;
                visiblePending++;
            }
        }
        ResourceMaterialResidencyDemand.recordPriorityCandidates(generation, visiblePending);
        return visiblePending;
    }

    private static boolean generationMatches(long generation) {
        JsonObject summary = ResourceMaterialResidencyDemand.summaryJson(generation);
        return summary.has("generationMatches") && summary.get("generationMatches").getAsBoolean();
    }

    private static LayerResult writeLayer(ResourceManager resourceManager, UploadItem item,
        int layerSize, long albedoPtr, long specularPtr, long normalPtr, long flagPtr,
        long defaultNormalPtr, int bytesPerLayer, UploadStats stats, String cacheKey) {
        NativeImage albedo = null;
        NativeImage specular = null;
        NativeImage normal = null;
        NativeImage flag = null;
        String layerKey = layerPayloadKey(item, layerSize);
        try {
            TextureLoaderDiskCache.LayerPayload cached =
                TextureLoaderDiskCache.readLayerPayload(cacheKey, layerKey, bytesPerLayer);
            if (cached != null) {
                writeCachedPlane(cached.albedo(), albedoPtr);
                writeCachedPlane(cached.specular(), specularPtr);
                writeCachedPlane(cached.normal(), normalPtr);
                writeCachedPlane(cached.flag(), flagPtr);
                if (stats != null) {
                    stats.layerPayloadCacheHits++;
                    stats.cachedLayerBytes += (long) bytesPerLayer * 4L;
                }
                return new LayerResult(true, false, cached.hasSpecular(),
                    cached.displacementEligible(), cached.displacementBlocked(), cached.heightRangePacked());
            }
            if (stats != null) {
                stats.layerPayloadCacheMisses++;
            }
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
            LayerResult result = new LayerResult(true, false, hasSpecular,
                height.eligible(), height.blocked(), height.rangePacked());
            TextureLoaderDiskCache.writeLayerPayloadAsync(cacheKey, layerKey,
                layerPayloadFromPointers(albedoPtr, specularPtr, normalPtr, flagPtr, bytesPerLayer, result));
            if (stats != null && cacheKey != null && !cacheKey.isBlank() && !layerKey.isBlank()) {
                stats.layerPayloadCacheWrites++;
            }
            return result;
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

    private static String layerPayloadKey(UploadItem item, int layerSize) {
        if (item == null || item.albedoPath() == null || item.albedoPath().isBlank() || layerSize <= 0) {
            return "";
        }
        return TextureLoaderDiskCache.keyFor("layer-v1|size=" + layerSize
            + "|albedo=" + item.albedoPath()
            + "|specular=" + item.specularPresent() + ":" + item.specularPath()
            + "|normal=" + item.normalPresent() + ":" + item.normalPath()
            + "|flag=" + item.flagPresent() + ":" + item.flagPath());
    }

    private static TextureLoaderDiskCache.LayerPayload layerPayloadFromPointers(long albedoPtr,
        long specularPtr, long normalPtr, long flagPtr, int bytesPerLayer, LayerResult result) {
        return new TextureLoaderDiskCache.LayerPayload(
            copyPlane(albedoPtr, bytesPerLayer),
            copyPlane(specularPtr, bytesPerLayer),
            copyPlane(normalPtr, bytesPerLayer),
            copyPlane(flagPtr, bytesPerLayer),
            result.hasSpecular(),
            result.displacementEligible(),
            result.displacementBlocked(),
            result.heightRangePacked());
    }

    private static byte[] copyPlane(long ptr, int bytes) {
        byte[] data = new byte[Math.max(0, bytes)];
        if (ptr != 0L && bytes > 0) {
            memByteBuffer(ptr, bytes).get(data);
        }
        return data;
    }

    private static void writeCachedPlane(byte[] data, long dstPtr) {
        if (data != null && data.length > 0 && dstPtr != 0L) {
            memByteBuffer(dstPtr, data.length).put(data);
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

    private static ByteBuffer directPageBuffer(int layerCount, int bytesPerLayer) {
        long bytes = (long) layerCount * bytesPerLayer;
        if (layerCount < 0 || bytesPerLayer < 0 || bytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("material page buffer size out of range layers="
                + layerCount + " bytesPerLayer=" + bytesPerLayer);
        }
        return ByteBuffer.allocateDirect((int) bytes).order(ByteOrder.nativeOrder());
    }

    private static void assertDirectCapacity(ByteBuffer buffer, long requiredBytes, String label) {
        NativeUploadGuards.assertDirectCapacity(buffer, requiredBytes, label);
    }

    private static void assertRange(ByteBuffer buffer, long offset, long bytes, String label) {
        NativeUploadGuards.assertRange(buffer, offset, bytes, label);
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

    private record LayerUploadFuture(int layer,
                                     UploadItem item,
                                     Future<LayerUploadResult> future) {
    }

    private record PageAllocation(int page,
                                  int startLayer,
                                  int layerCapacity,
                                  int layerCount) {
        static PageAllocation empty() {
            return new PageAllocation(-1, 0, 0, 0);
        }
    }

    private record LayerUploadResult(int materialId,
                                     LayerResult result,
                                     UploadStats stats) {
        static LayerUploadResult failed(UploadItem item) {
            int materialId = item == null ? -1 : item.materialId();
            return new LayerUploadResult(materialId, LayerResult.failed(), new UploadStats());
        }
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
        long cachedLayerBytes;

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
        int layerPayloadCacheHits;
        int layerPayloadCacheMisses;
        int layerPayloadCacheWrites;

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
            cachedLayerBytes += other.cachedLayerBytes;

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
            layerPayloadCacheHits += other.layerPayloadCacheHits;
            layerPayloadCacheMisses += other.layerPayloadCacheMisses;
            layerPayloadCacheWrites += other.layerPayloadCacheWrites;
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
            json.addProperty("cachedLayerMiB", cachedLayerBytes / (1024.0 * 1024.0));
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
            json.addProperty("layerPayloadCacheHits", layerPayloadCacheHits);
            json.addProperty("layerPayloadCacheMisses", layerPayloadCacheMisses);
            json.addProperty("layerPayloadCacheWrites", layerPayloadCacheWrites);
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
