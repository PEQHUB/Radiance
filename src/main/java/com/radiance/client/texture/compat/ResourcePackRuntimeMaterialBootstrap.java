package com.radiance.client.texture.compat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.cache.TextureCacheV4;
import com.radiance.client.texture.material.ResourceMaterialRegistry;
import com.radiance.client.texture.material.ResourceMaterialResidencyDemand;
import com.radiance.client.texture.material.ResourceMaterialResidencyUploader;
import com.radiance.client.texture.material.ResourceMaterialRuntimeStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fast live-resource bootstrap for OptiFine CTM material ids.
 *
 * <p>The filesystem diagnostics scan is intentionally rich and can be slow for
 * large packs. Chunk building needs only the stable material universe up front:
 * CTM tile asset paths, fallback sprite ids, and sidecar presence. This class
 * builds that smaller root directly from the live {@link ResourceManager}, then
 * lets the heavier texture residency upload proceed asynchronously.</p>
 */
public final class ResourcePackRuntimeMaterialBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final int DEPENDENCY_LIMIT = 65536;
    private static final AtomicLong PUBLISHED_GENERATION = new AtomicLong(-1L);
    private static final AtomicLong RESIDENCY_STARTED_GENERATION = new AtomicLong(-1L);
    private static final AtomicBoolean RESIDENCY_RUNNING = new AtomicBoolean(false);
    private static final AtomicBoolean RESIDENCY_SCHEDULED = new AtomicBoolean(false);
    private static final AtomicLong LAST_RESIDENCY_VISIBLE_COUNT = new AtomicLong(0L);
    private static final AtomicLong LAST_RESIDENCY_BATCH_SIZE = new AtomicLong(0L);
    private static final AtomicLong RESIDENCY_SCHEDULE_EVENTS = new AtomicLong(0L);
    private static final AtomicLong RESIDENCY_UPLOAD_EVENTS = new AtomicLong(0L);
    private static final AtomicLong FIRST_FRAME_PLAN_REQUESTS = new AtomicLong(0L);
    private static final AtomicLong FIRST_FRAME_DRAIN_REQUESTS = new AtomicLong(0L);
    private static final AtomicLong PARKED_GENERATION = new AtomicLong(-1L);
    private static volatile String lastBlockedReasonKey = "";
    private static final AtomicBoolean SHUTTING_DOWN = new AtomicBoolean(false);

    public static boolean isShuttingDown() {
        return SHUTTING_DOWN.get();
    }

    /**
     * Quiesce texture residency before the native renderer is destroyed.
     * The residency daemon thread was still issuing JNI uploads while
     * MinecraftClient.close() tore down the Vulkan device, corrupting native
     * texture-system state (access violation in core.dll static destructors
     * on every exit). Order: stop accepting work, cancel the active texture
     * generation (native rejects stale uploads), then wait bounded for the
     * in-flight batch to drain.
     */
    public static void shutdownForClientClose() {
        if (!SHUTTING_DOWN.compareAndSet(false, true)) {
            return;
        }
        try {
            com.radiance.client.texture.v4.TextureLoadGeneration.cancelActive();
        } catch (Throwable ignored) {
        }
        RESIDENCY_SCHEDULER.shutdownNow();
        long deadlineNanos = System.nanoTime() + 1_500_000_000L;
        while (RESIDENCY_RUNNING.get() && System.nanoTime() < deadlineNanos) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        LOGGER.info("[MaterialCompat] Residency quiesced for client close (uploadStillRunning={})",
            RESIDENCY_RUNNING.get());
    }
    private static final ScheduledExecutorService RESIDENCY_SCHEDULER =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "RadSER Material Residency Scheduler");
            thread.setDaemon(true);
            return thread;
        });
    private static volatile long activeResidencyGeneration = -1L;
    private static volatile JsonObject activeResidencyRoot = null;
    private static volatile ResourceMaterialRegistry.Snapshot activeResidencySnapshot = null;
    private static volatile String activeResidencyCacheKey = "";

    private ResourcePackRuntimeMaterialBootstrap() {
    }

    public static BootstrapResult publishFromRuntimeResourceManager(ResourceManager resourceManager,
        long generation) {
        if (!Options.materialCompatEnabled || !Options.materialCompatCtmEnabled) {
            return BootstrapResult.skipped(generation, "material_compat_ctm_disabled");
        }
        if (resourceManager == null) {
            return BootstrapResult.skipped(generation, "resource_manager_unavailable");
        }
        if (generation <= 0) {
            return BootstrapResult.skipped(generation, "texture_generation_unpublished");
        }
        if (PUBLISHED_GENERATION.get() == generation) {
            return BootstrapResult.skipped(generation, "generation_already_published");
        }

        ResourceMaterialResidencyDemand.resetForGeneration(generation);
        long startedNanos = System.nanoTime();
        String cacheKey = cacheKeyForResourceManager(resourceManager);
        JsonObject cachedRoot = TextureLoaderDiskCache.readRoot(cacheKey);
        RuntimeRoot root = runtimeRootFromCache(cachedRoot, generation);
        boolean cacheHit = root != null;
        if (root == null) {
            root = buildRoot(resourceManager, generation);
            TextureLoaderDiskCache.writeRootAsync(cacheKey, root.root());
        }
        if (root.dependencyCount() <= 0) {
            PUBLISHED_GENERATION.compareAndSet(PUBLISHED_GENERATION.get(), generation);
            JsonObject statusEvent = new JsonObject();
            statusEvent.addProperty("dependencyCount", 0);
            statusEvent.addProperty("presentDependencyCount", root.presentDependencyCount());
            statusEvent.addProperty("propertyCount", root.propertyCount());
            statusEvent.addProperty("reason", "no_ctm_dependencies");
            ResourceMaterialRuntimeStatus.write("bootstrapSkipped", generation, statusEvent);
            return new BootstrapResult(true, false, generation, 0,
                root.presentDependencyCount(), root.propertyCount(), "no_ctm_dependencies", 0.0,
                false);
        }

        ResourceMaterialRegistry.Snapshot snapshot =
            ResourceMaterialRegistry.publishFromCompatReport(root.root(), generation);
        boolean tableUploaded = ResourceMaterialRegistry.uploadActiveTableToNative();
        PUBLISHED_GENERATION.set(generation);
        double ms = millis(startedNanos);
        JsonObject statusEvent = new JsonObject();
        statusEvent.addProperty("dependencyCount", root.dependencyCount());
        statusEvent.addProperty("presentDependencyCount", root.presentDependencyCount());
        statusEvent.addProperty("propertyCount", root.propertyCount());
        statusEvent.addProperty("elapsedMs", ms);
        statusEvent.addProperty("materialTableUploaded", tableUploaded);
        statusEvent.addProperty("packStackHash", packStackHash(generation, root.dependencyCount()));
        statusEvent.addProperty("cacheHit", cacheHit);
        statusEvent.addProperty("cacheKey", cacheKey);
        statusEvent.addProperty("ctmDemandResidency", Options.ctmDemandResidency);
        statusEvent.addProperty("fullPreloadDiagnostic", Options.materialCompatFullPreloadDiagnostic);
        ResourceMaterialRuntimeStatus.write("bootstrapPublished", generation, statusEvent);
        LOGGER.info("[MaterialCompat] Runtime material bootstrap published {} CTM materials from {} property files "
                + "for generation {} in {} ms (tableUploaded={})",
            root.dependencyCount(), root.propertyCount(), generation, String.format(Locale.ROOT, "%.2f", ms),
            tableUploaded);
        rememberResidencyInput(root.root(), snapshot, generation, cacheKey);
        if (shouldDeferResidency(generation)) {
            JsonObject deferEvent = new JsonObject();
            deferEvent.addProperty("dependencyCount", root.dependencyCount());
            deferEvent.addProperty("presentDependencyCount", root.presentDependencyCount());
            deferEvent.addProperty("visibleUniqueMaterialCount", 0);
            deferEvent.addProperty("prewarmUniqueMaterialCount", 0);
            deferEvent.addProperty("fullPreloadStarted", false);
            deferEvent.addProperty("reason", "waiting_for_visible_material_demand");
            ResourceMaterialRuntimeStatus.write("residencyDeferred", generation, deferEvent);
            LOGGER.info("[MaterialCompat] CTM residency deferred for generation {}: no visible material demand",
                generation);
        } else {
            startResidencyUpload(root.root(), snapshot, generation);
        }
        return new BootstrapResult(true, true, generation, root.dependencyCount(),
            root.presentDependencyCount(), root.propertyCount(), "published", ms, tableUploaded);
    }

    public static void onVisibleMaterialDemand(long generation) {
        if (!Options.materialCompatEnabled || !Options.materialCompatCtmEnabled || generation <= 0) {
            return;
        }
        if (generation != activeResidencyGeneration) {
            return;
        }
        JsonObject root = activeResidencyRoot;
        ResourceMaterialRegistry.Snapshot snapshot = activeResidencySnapshot;
        if (root == null || snapshot == null) {
            return;
        }
        Set<Integer> visible = ResourceMaterialResidencyDemand.visibleMaterialIds(generation);
        if (visible.isEmpty()) {
            return;
        }
        if (Options.ctmDemandResidency && !Options.materialCompatFullPreloadDiagnostic
            && visible.size() <= LAST_RESIDENCY_VISIBLE_COUNT.get()) {
            return;
        }
        scheduleResidencyUpload(generation, 150L);
    }

    public static void onPrewarmMaterialDemand(long generation) {
        if (!Options.materialCompatEnabled || !Options.materialCompatCtmEnabled || generation <= 0) {
            return;
        }
        if (generation != activeResidencyGeneration) {
            return;
        }
        if (activeResidencyRoot == null || activeResidencySnapshot == null) {
            return;
        }
        if (ResourceMaterialResidencyDemand.residencyMaterialIds(generation).isEmpty()) {
            return;
        }
        scheduleResidencyUpload(generation, 50L);
    }

    public static void onPlannedFirstFrameMaterialDemand(long generation) {
        if (!Options.materialCompatEnabled || !Options.materialCompatCtmEnabled || generation <= 0) {
            return;
        }
        if (generation != activeResidencyGeneration) {
            return;
        }
        if (activeResidencyRoot == null || activeResidencySnapshot == null) {
            return;
        }
        if (ResourceMaterialResidencyDemand.residencyMaterialIds(generation).isEmpty()) {
            return;
        }
        FIRST_FRAME_PLAN_REQUESTS.incrementAndGet();
        scheduleResidencyUpload(generation, 0L);
    }

    public static void requestFirstFrameVisibleResidency(long generation) {
        if (!Options.materialCompatEnabled || !Options.materialCompatCtmEnabled || generation <= 0) {
            return;
        }
        if (generation != activeResidencyGeneration) {
            return;
        }
        if (activeResidencyRoot == null || activeResidencySnapshot == null) {
            return;
        }
        if (ResourceMaterialResidencyDemand.residencyMaterialIds(generation).isEmpty()) {
            return;
        }
        FIRST_FRAME_DRAIN_REQUESTS.incrementAndGet();
        scheduleResidencyUpload(generation, 0L);
    }

    public static JsonObject schedulerStatusJson(long generation) {
        JsonObject json = new JsonObject();
        json.addProperty("generation", activeResidencyGeneration);
        json.addProperty("requestedGeneration", generation);
        json.addProperty("generationMatches", generation == activeResidencyGeneration);
        json.addProperty("pendingQueueSize",
            ResourceMaterialResidencyDemand.residencyMaterialIds(generation).size());
        json.addProperty("uploadInFlight", RESIDENCY_RUNNING.get());
        json.addProperty("uploadScheduled", RESIDENCY_SCHEDULED.get());
        json.addProperty("lastBatchSize", LAST_RESIDENCY_BATCH_SIZE.get());
        json.addProperty("scheduleEvents", RESIDENCY_SCHEDULE_EVENTS.get());
        json.addProperty("uploadEvents", RESIDENCY_UPLOAD_EVENTS.get());
        json.addProperty("firstFramePlanRequests", FIRST_FRAME_PLAN_REQUESTS.get());
        json.addProperty("firstFrameDrainRequests", FIRST_FRAME_DRAIN_REQUESTS.get());
        json.addProperty("visibleDebounceMs", 150);
        json.addProperty("prewarmDebounceMs", 50);
        json.addProperty("firstFramePlanDebounceMs", 0);
        json.addProperty("firstFrameDrainDebounceMs", 0);
        json.addProperty("coalescesPendingRequests", true);
        return json;
    }

    public static JsonObject cacheStatusJson() {
        JsonObject json = TextureLoaderDiskCache.statusJson();
        JsonObject v4 = TextureCacheV4.statusJson();
        json.add("v4", v4);
        for (var entry : v4.entrySet()) {
            json.add(entry.getKey(), entry.getValue());
        }
        return json;
    }

    public static List<CtmWorkItem> ctmWorkItemsForResourceManager(ResourceManager resourceManager,
                                                                   long generation) {
        if (!Options.materialCompatEnabled || !Options.materialCompatCtmEnabled || resourceManager == null) {
            return List.of();
        }
        try {
            RuntimeRoot root = buildRoot(resourceManager, generation);
            JsonObject activeCtm = root.root().getAsJsonObject("activeCtmAtlasDependencies");
            if (activeCtm == null || !activeCtm.has("dependencies")
                || !activeCtm.get("dependencies").isJsonArray()) {
                return List.of();
            }
            JsonArray dependencies = activeCtm.getAsJsonArray("dependencies");
            ArrayList<CtmWorkItem> items = new ArrayList<>(dependencies.size());
            for (int i = 0; i < dependencies.size(); i++) {
                JsonObject dependency = dependencies.get(i).getAsJsonObject();
                String path = stringProperty(dependency, "path");
                boolean present = boolProperty(dependency, "present");
                boolean visible = present && !boolProperty(dependency, "atlasAdmissionRequired");
                items.add(new CtmWorkItem(i, path, present, visible));
            }
            return List.copyOf(items);
        } catch (Throwable t) {
            LOGGER.debug("[MaterialCompat] CTM graph work item scan failed", t);
            return List.of();
        }
    }

    private static RuntimeRoot buildRoot(ResourceManager resourceManager, long generation) {
        LinkedHashMap<String, JsonObject> dependencies = new LinkedHashMap<>();
        int propertyCount = 0;
        propertyCount += collectRoot(resourceManager, "optifine/ctm", dependencies);
        if (Options.materialCompatLegacyMcPatcherEnabled) {
            propertyCount += collectRoot(resourceManager, "mcpatcher/ctm", dependencies);
        }

        int present = 0;
        int missing = 0;
        int withSpecular = 0;
        int withNormal = 0;
        int withDerivedSpecular = 0;
        int withDerivedNormal = 0;
        int withAnySidecar = 0;
        int withEmissive = 0;
        int requiringAtlasAdmission = 0;
        int presentRequiringAtlasAdmission = 0;
        JsonArray dependencyArray = new JsonArray();
        for (JsonObject dependency : dependencies.values()) {
            boolean dependencyPresent = boolProperty(dependency, "present");
            if (dependencyPresent) present++; else missing++;
            if (boolProperty(dependency, "specularPresent")) withSpecular++;
            if (boolProperty(dependency, "normalPresent")) withNormal++;
            if (boolProperty(dependency, "derivedSpecularPresent")) withDerivedSpecular++;
            if (boolProperty(dependency, "derivedNormalPresent")) withDerivedNormal++;
            if (boolProperty(dependency, "anyMaterialSidecarPresent")) withAnySidecar++;
            if (boolProperty(dependency, "emissivePresent")) withEmissive++;
            if (boolProperty(dependency, "atlasAdmissionRequired")) {
                requiringAtlasAdmission++;
                if (dependencyPresent) {
                    presentRequiringAtlasAdmission++;
                }
            }
            dependencyArray.add(dependency);
        }

        JsonObject activeCtm = new JsonObject();
        activeCtm.addProperty("aggregationMode", "runtime_resource_manager");
        activeCtm.addProperty("deduplicated", true);
        activeCtm.addProperty("uniqueTiles", dependencies.size());
        activeCtm.addProperty("presentTiles", present);
        activeCtm.addProperty("missingTiles", missing);
        activeCtm.addProperty("tilesWithSpecular", withSpecular);
        activeCtm.addProperty("tilesWithNormal", withNormal);
        activeCtm.addProperty("tilesWithSpecularOrScalar", withDerivedSpecular);
        activeCtm.addProperty("tilesWithNormalOrScalar", withDerivedNormal);
        activeCtm.addProperty("tilesWithAnyMaterialSidecar", withAnySidecar);
        activeCtm.addProperty("tilesWithEmissive", withEmissive);
        activeCtm.addProperty("tilesRequiringAtlasAdmission", requiringAtlasAdmission);
        activeCtm.addProperty("presentTilesRequiringAtlasAdmission", presentRequiringAtlasAdmission);
        activeCtm.addProperty("truncated", dependencies.size() >= DEPENDENCY_LIMIT);
        activeCtm.add("dependencies", dependencyArray);

        JsonObject materialUniverse = new JsonObject();
        materialUniverse.addProperty("minecraftVersion", "1.21.4");
        materialUniverse.addProperty("packStackHash", packStackHash(generation, dependencies.size()));
        materialUniverse.addProperty("vanillaSprites", TextureArrayBridge.sortedSpriteIds.size());
        materialUniverse.addProperty("ctmPropertyFiles", propertyCount);
        materialUniverse.addProperty("ctmTileDependencies", dependencies.size());
        materialUniverse.addProperty("ctmTilesPresent", present);
        materialUniverse.addProperty("ctmTilesMissing", missing);
        materialUniverse.addProperty("ctmTilesWithNormal", withNormal);
        materialUniverse.addProperty("ctmTilesWithSpecular", withSpecular);
        materialUniverse.addProperty("virtualCompatMaterials", dependencies.size());
        materialUniverse.addProperty("virtualCompatMaterialsPresent", present);
        materialUniverse.addProperty("gpuResidentCompatMaterials", 0);
        materialUniverse.addProperty("pendingCompatMaterials", present);
        materialUniverse.addProperty("missingDueToCapacity", 0);
        materialUniverse.addProperty("capacityLimitAppliesToCompatMaterials", false);
        materialUniverse.addProperty("admittedToVanillaAtlas", 0);

        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_runtime_material_bootstrap_v1");
        root.addProperty("packStackHash", packStackHash(generation, dependencies.size()));
        root.add("activeCtmAtlasDependencies", activeCtm);
        root.add("materialUniverse", materialUniverse);
        return new RuntimeRoot(root, dependencies.size(), present, propertyCount);
    }

    private static RuntimeRoot runtimeRootFromCache(JsonObject cachedRoot, long generation) {
        if (cachedRoot == null || !cachedRoot.has("activeCtmAtlasDependencies")
            || !cachedRoot.get("activeCtmAtlasDependencies").isJsonObject()) {
            return null;
        }
        JsonObject root = cachedRoot.deepCopy();
        JsonObject activeCtm = root.getAsJsonObject("activeCtmAtlasDependencies");
        int dependencyCount = intProperty(activeCtm, "uniqueTiles");
        int presentDependencyCount = intProperty(activeCtm, "presentTiles");
        int propertyCount = root.has("materialUniverse") && root.get("materialUniverse").isJsonObject()
            ? intProperty(root.getAsJsonObject("materialUniverse"), "ctmPropertyFiles")
            : 0;
        String packStackHash = packStackHash(generation, dependencyCount);
        root.addProperty("packStackHash", packStackHash);
        if (root.has("materialUniverse") && root.get("materialUniverse").isJsonObject()) {
            root.getAsJsonObject("materialUniverse").addProperty("packStackHash", packStackHash);
        }
        return new RuntimeRoot(root, dependencyCount, presentDependencyCount, propertyCount);
    }

    public static String cacheKeyForResourceManager(ResourceManager resourceManager) {
        StringBuilder key = new StringBuilder("ctm-v1|legacy=")
            .append(Options.materialCompatLegacyMcPatcherEnabled)
            .append("|namespaces=");
        try {
            java.util.ArrayList<String> namespaces = new java.util.ArrayList<>(resourceManager.getAllNamespaces());
            namespaces.sort(String::compareTo);
            key.append(String.join(",", namespaces));
        } catch (Exception e) {
            key.append("unknown");
        }
        key.append("|packs=");
        key.append(activeResourcePackSelection());
        return TextureLoaderDiskCache.keyFor(key.toString());
    }

    private static String activeResourcePackSelection() {
        try {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            if (client == null || client.runDirectory == null) {
                return "unknown";
            }
            java.nio.file.Path options = client.runDirectory.toPath().resolve("options.txt");
            if (!java.nio.file.Files.isRegularFile(options)) {
                return "missing_options";
            }
            try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(options)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("resourcePacks:")) {
                        return line.substring("resourcePacks:".length())
                            + "|files=" + resourcePackFileInventory(client.runDirectory.toPath());
                    }
                }
            }
            return "no_resource_pack_option";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String resourcePackFileInventory(java.nio.file.Path runDirectory) {
        if (runDirectory == null) {
            return "unknown";
        }
        java.nio.file.Path resourcePacks = runDirectory.resolve("resourcepacks");
        if (!java.nio.file.Files.isDirectory(resourcePacks)) {
            return "missing_resourcepacks_dir";
        }
        try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(resourcePacks)) {
            java.util.ArrayList<String> files = new java.util.ArrayList<>();
            stream.filter(java.nio.file.Files::isRegularFile)
                .forEach(path -> {
                    try {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        long size = java.nio.file.Files.size(path);
                        long mtime = java.nio.file.Files.getLastModifiedTime(path).toMillis();
                        files.add(name + ":" + size + ":" + mtime);
                    } catch (Exception ignored) {
                        String name = path.getFileName() == null ? "" : path.getFileName().toString();
                        files.add(name + ":unreadable");
                    }
                });
            files.sort(String::compareTo);
            return String.join(",", files);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static int collectRoot(ResourceManager resourceManager, String root,
        LinkedHashMap<String, JsonObject> dependencies) {
        Map<Identifier, Resource> properties = resourceManager.findResources(root,
            id -> id.getPath().endsWith(".properties"));
        properties.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> collectProperty(resourceManager, entry.getKey(), entry.getValue(), dependencies));
        return properties.size();
    }

    private static void collectProperty(ResourceManager resourceManager, Identifier propertyId,
        Resource resource, LinkedHashMap<String, JsonObject> dependencies) {
        Properties props = new Properties();
        try (BufferedReader reader = resource.getReader()) {
            props.load(reader);
        } catch (IOException e) {
            LOGGER.debug("[MaterialCompat] Runtime material bootstrap skipped unreadable CTM rule {}", propertyId, e);
            return;
        }
        String propertyPath = ResourcePackCompatCtmTiles.assetPath(propertyId);
        String fallbackAtlasSprite = fallbackAtlasSprite(propertyPath, props);
        List<String> paths = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(propertyPath, props);
        for (String path : paths) {
            if (dependencies.size() >= DEPENDENCY_LIMIT) {
                return;
            }
            dependencies.putIfAbsent(path, dependencyJson(resourceManager, path, fallbackAtlasSprite));
        }
    }

    private static JsonObject dependencyJson(ResourceManager resourceManager, String rawPath,
        String fallbackAtlasSprite) {
        String path = normalize(rawPath);
        Identifier resourceId = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(path);
        String specularPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_s");
        boolean specularPresent = sidecarPresent(resourceManager, path, "_s", "_spec", "_specular");
        String normalPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_n");
        boolean normalPresent = sidecarPresent(resourceManager, path, "_n", "_normal", "_norm");
        String roughnessPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_roughness");
        boolean roughnessPresent = sidecarPresent(resourceManager, path, "_roughness", "_rough");
        String metallicPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_metallic");
        boolean metallicPresent = sidecarPresent(resourceManager, path, "_metallic", "_metalness");
        String heightPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_height");
        boolean heightPresent = sidecarPresent(resourceManager, path, "_height", "_displacement", "_disp");
        String aoPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_ao");
        boolean aoPresent = sidecarPresent(resourceManager, path, "_ao", "_ambientocclusion", "_ambient_occlusion");
        boolean derivedSpecularPresent = specularPresent || roughnessPresent || metallicPresent;
        boolean derivedNormalPresent = normalPresent || heightPresent || aoPresent;

        JsonObject json = new JsonObject();
        json.addProperty("path", path);
        json.addProperty("resource", resourceId == null ? "" : resourceId.toString());
        json.addProperty("atlasSprite", ResourcePackCompatCtmTiles.atlasSpriteIdentifier(path));
        json.addProperty("fallbackAtlasSprite", fallbackAtlasSprite == null ? "" : fallbackAtlasSprite);
        json.addProperty("atlasAdmissionRequired", ResourcePackCompatCtmTiles.requiresAtlasAdmission(path));
        json.addProperty("present", resourcePresent(resourceManager, path));
        json.addProperty("specularPath", specularPath);
        json.addProperty("specularPresent", specularPresent);
        json.addProperty("normalPath", normalPath);
        json.addProperty("normalPresent", normalPresent);
        json.addProperty("roughnessPath", roughnessPath);
        json.addProperty("roughnessPresent", roughnessPresent);
        json.addProperty("metallicPath", metallicPath);
        json.addProperty("metallicPresent", metallicPresent);
        json.addProperty("heightPath", heightPath);
        json.addProperty("heightPresent", heightPresent);
        json.addProperty("aoPath", aoPath);
        json.addProperty("aoPresent", aoPresent);
        json.addProperty("derivedSpecularPresent", derivedSpecularPresent);
        json.addProperty("derivedNormalPresent", derivedNormalPresent);
        json.addProperty("anyMaterialSidecarPresent", derivedSpecularPresent || derivedNormalPresent);
        json.addProperty("flagPath", ResourcePackCompatCtmTiles.sidecarPath(path, "_f"));
        json.addProperty("flagPresent", sidecarPresent(resourceManager, path, "_f"));
        json.addProperty("emissivePath", ResourcePackCompatCtmTiles.sidecarPath(path, "_e"));
        json.addProperty("emissivePresent", sidecarPresent(resourceManager, path, "_e"));
        return json;
    }

    private static String fallbackAtlasSprite(String propertyPath, Properties props) {
        for (String fallback : ResourcePackCompatCtmTiles.materialFallbackAssetPaths(propertyPath, props)) {
            String natural = ResourcePackCompatCtmTiles.naturalAtlasSpriteIdentifier(fallback);
            if (!natural.isBlank()) {
                return natural;
            }
        }
        return "";
    }

    private static boolean resourcePresent(ResourceManager resourceManager, String assetPath) {
        Identifier id = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(assetPath);
        return id != null && resourceManager.getResource(id).isPresent();
    }

    private static boolean sidecarPresent(ResourceManager resourceManager, String path, String... suffixes) {
        for (String suffix : suffixes) {
            if (resourcePresent(resourceManager, ResourcePackCompatCtmTiles.sidecarPath(path, suffix))) {
                return true;
            }
        }
        return false;
    }

    private static void scheduleResidencyUpload(long generation, long delayMs) {
        if (SHUTTING_DOWN.get() || generation != activeResidencyGeneration
            || shouldDeferResidency(generation)) {
            return;
        }
        PARKED_GENERATION.set(-1L);
        if (!RESIDENCY_SCHEDULED.compareAndSet(false, true)) {
            return;
        }
        RESIDENCY_SCHEDULE_EVENTS.incrementAndGet();
        RESIDENCY_SCHEDULER.schedule(() -> {
            RESIDENCY_SCHEDULED.set(false);
            JsonObject root = activeResidencyRoot;
            ResourceMaterialRegistry.Snapshot snapshot = activeResidencySnapshot;
            String cacheKey = activeResidencyCacheKey;
            if (root == null || snapshot == null || generation != activeResidencyGeneration) {
                return;
            }
            if (!startResidencyUpload(root, snapshot, generation, cacheKey)
                && !shouldDeferResidency(generation)) {
                scheduleResidencyUpload(generation, delayMs);
            }
        }, Math.max(0L, delayMs), TimeUnit.MILLISECONDS);
    }

    private static boolean startResidencyUpload(JsonObject root, ResourceMaterialRegistry.Snapshot snapshot,
        long generation) {
        return startResidencyUpload(root, snapshot, generation, activeResidencyCacheKey);
    }

    private static boolean startResidencyUpload(JsonObject root, ResourceMaterialRegistry.Snapshot snapshot,
        long generation, String cacheKey) {
        if (SHUTTING_DOWN.get() || shouldDeferResidency(generation)) {
            return false;
        }
        if (!RESIDENCY_RUNNING.compareAndSet(false, true)) {
            return false;
        }
        RESIDENCY_STARTED_GENERATION.set(generation);
        RESIDENCY_UPLOAD_EVENTS.incrementAndGet();
        Thread thread = new Thread(() -> {
            long startedNanos = System.nanoTime();
            long epochAtStart = ResourceMaterialResidencyDemand.demandEpoch(generation);
            final int[] uploadedHolder = {0};
            try {
                JsonObject upload =
                    ResourceMaterialResidencyUploader.uploadFromCompatReport(root, snapshot, true,
                        Options.ctmDemandResidency && !Options.materialCompatFullPreloadDiagnostic,
                        cacheKey);
                boolean tableUploaded = boolProperty(upload, "materialTableUploadedFinal")
                    || intProperty(upload, "materialTableUploadSuccesses") > 0;
                int uploadedMaterials = intProperty(upload, "uploadedMaterials");
                uploadedHolder[0] = uploadedMaterials;
                boolean semanticResidencyUploaded = boolProperty(upload, "semanticResidencyUploaded");
                int nativePageFailures = intProperty(upload, "nativePageFailures");
                LAST_RESIDENCY_BATCH_SIZE.set(uploadedMaterials);
                JsonObject statusEvent = new JsonObject();
                statusEvent.add("upload", upload.deepCopy());
                statusEvent.addProperty("materialTableUploaded", tableUploaded);
                statusEvent.addProperty("semanticResidencyUploaded", semanticResidencyUploaded);
                statusEvent.addProperty("uploadedMaterials", uploadedMaterials);
                statusEvent.addProperty("residentCompatMaterials",
                    intProperty(upload, "residentCompatMaterials"));
                statusEvent.addProperty("nativePageFailures", nativePageFailures);
                statusEvent.addProperty("nativePageFailedMaterials",
                    intProperty(upload, "nativePageFailedMaterials"));
                statusEvent.addProperty("fullPreloadStarted",
                    !Options.ctmDemandResidency || Options.materialCompatFullPreloadDiagnostic);
                statusEvent.addProperty("totalMs", millis(startedNanos));
                ResourceMaterialRuntimeStatus.write("residencyComplete", generation, statusEvent);
                if (uploadedMaterials > 0 || nativePageFailures > 0) {
                    LOGGER.info("[MaterialCompat] Runtime material residency complete for generation {}: "
                            + "{} materials resident, semanticUploaded={}, tableUploaded={}, nativePageFailures={}, totalMs={}",
                        generation, uploadedMaterials, semanticResidencyUploaded, tableUploaded, nativePageFailures,
                        String.format(Locale.ROOT, "%.2f", millis(startedNanos)));
                } else {
                    LOGGER.debug("[MaterialCompat] Runtime material residency no-op for generation {} ({} ms)",
                        generation, String.format(Locale.ROOT, "%.2f", millis(startedNanos)));
                }
                LAST_RESIDENCY_VISIBLE_COUNT.set(
                    ResourceMaterialResidencyDemand.visibleMaterialIds(generation).size());
                recordResidencyDescriptorOnlyUpdate(generation, upload);
            } catch (Throwable t) {
                JsonObject statusEvent = new JsonObject();
                statusEvent.addProperty("error", t.toString());
                ResourceMaterialRuntimeStatus.write("residencyFailed", generation, statusEvent);
                LOGGER.warn("[MaterialCompat] Runtime material residency failed for generation {}: {}",
                    generation, t.toString());
            } finally {
                RESIDENCY_RUNNING.set(false);
                if (generation == activeResidencyGeneration) {
                    boolean pendingDemand =
                        !ResourceMaterialResidencyDemand.residencyMaterialIds(generation).isEmpty();
                    boolean newDemand =
                        ResourceMaterialResidencyDemand.demandEpoch(generation) != epochAtStart;
                    boolean progressed = uploadedHolder[0] > 0;
                    if (pendingDemand && (newDemand || progressed)) {
                        // More work and a reason to believe it can advance.
                        scheduleResidencyUpload(generation, 50L);
                    } else if (ResourceMaterialResidencyDemand.visibleMaterialIds(generation).size()
                        > LAST_RESIDENCY_VISIBLE_COUNT.get()) {
                        scheduleResidencyUpload(generation, 150L);
                    } else if (pendingDemand && PARKED_GENERATION.getAndSet(generation) != generation) {
                        // Remaining demand cannot currently advance (e.g. retryable
                        // failures with no new input). Park until new demand arrives
                        // via the enqueue hooks instead of respinning.
                        LOGGER.info("[MaterialCompat] Residency parked for generation {} with {} "
                                + "unsatisfied requests; will wake on new demand",
                            generation,
                            ResourceMaterialResidencyDemand.residencyMaterialIds(generation).size());
                    }
                }
            }
        }, "RadSER Material Residency Upload");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static void rememberResidencyInput(JsonObject root,
        ResourceMaterialRegistry.Snapshot snapshot, long generation, String cacheKey) {
        activeResidencyGeneration = generation;
        activeResidencyRoot = root == null ? null : root.deepCopy();
        activeResidencySnapshot = snapshot;
        activeResidencyCacheKey = cacheKey == null ? "" : cacheKey;
        LAST_RESIDENCY_VISIBLE_COUNT.set(0L);
        LAST_RESIDENCY_BATCH_SIZE.set(0L);
        FIRST_FRAME_PLAN_REQUESTS.set(0L);
        FIRST_FRAME_DRAIN_REQUESTS.set(0L);
    }

    private static boolean shouldDeferResidency(long generation) {
        return Options.ctmDemandResidency
            && !Options.materialCompatFullPreloadDiagnostic
            && ResourceMaterialResidencyDemand.residencyMaterialIds(generation).isEmpty();
    }

    private static void recordResidencyDescriptorOnlyUpdate(long generation, JsonObject upload) {
        boolean semanticResidencyUploaded = boolProperty(upload, "semanticResidencyUploaded");
        int uploadedMaterials = intProperty(upload, "uploadedMaterials");
        int nativePageFailures = intProperty(upload, "nativePageFailures");
        int nativePageFailedMaterials = intProperty(upload, "nativePageFailedMaterials");
        int materialTableUploadFailures = intProperty(upload, "materialTableUploadFailures");
        int materialTableUploadSuccesses = intProperty(upload, "materialTableUploadSuccesses");
        boolean materialTableUploaded = boolProperty(upload, "materialTableUploadedFinal")
            || materialTableUploadSuccesses > 0;
        JsonObject visibleResidency = ResourceMaterialResidencyDemand.summaryJson(generation);
        int visibleFallbackMaterialCount = intProperty(visibleResidency, "visibleFallbackMaterialCount");
        boolean descriptorResidencyReady = semanticResidencyUploaded
            && uploadedMaterials > 0
            && materialTableUploaded
            && visibleFallbackMaterialCount == 0;
        JsonObject statusEvent = new JsonObject();
        statusEvent.addProperty("semanticResidencyUploaded", semanticResidencyUploaded);
        statusEvent.addProperty("uploadedMaterials", uploadedMaterials);
        statusEvent.addProperty("residentCompatMaterials", intProperty(upload, "residentCompatMaterials"));
        statusEvent.addProperty("nativePageFailures", nativePageFailures);
        statusEvent.addProperty("nativePageFailedMaterials", nativePageFailedMaterials);
        statusEvent.addProperty("materialTableUploadFailures", materialTableUploadFailures);
        statusEvent.addProperty("materialTableUploadSuccesses", materialTableUploadSuccesses);
        statusEvent.addProperty("materialTableUploaded", materialTableUploaded);
        statusEvent.addProperty("visibleFallbackMaterialCount", visibleFallbackMaterialCount);
        statusEvent.add("visibleResidency", visibleResidency);
        if (!descriptorResidencyReady) {
            statusEvent.addProperty("geometryAffectingMaterialChange", true);
            statusEvent.addProperty("chunkRefreshScheduled", false);
            String reason = uploadedMaterials <= 0
                ? "no_native_material_pages_uploaded"
                : !materialTableUploaded || materialTableUploadFailures > 0
                    ? "native_material_table_upload_failure"
                    : visibleFallbackMaterialCount > 0
                        ? "visible_material_residency_still_pending"
                        : "native_material_page_failure";
            statusEvent.addProperty("reason", reason);
            ResourceMaterialRuntimeStatus.write("residencyDescriptorOnlyUpdateBlocked", generation, statusEvent);
            String reasonKey = generation + ":" + reason;
            if (!reasonKey.equals(lastBlockedReasonKey)) {
                lastBlockedReasonKey = reasonKey;
                LOGGER.warn("[MaterialCompat] Residency descriptor-only success blocked for generation {}: "
                        + "reason={}, uploadedMaterials={}, nativePageFailures={}, nativePageFailedMaterials={}, "
                        + "materialTableUploadFailures={}, visibleFallbacks={} (further identical blocks logged at debug)",
                    generation, reason, uploadedMaterials, nativePageFailures, nativePageFailedMaterials,
                    materialTableUploadFailures, visibleFallbackMaterialCount);
            } else {
                LOGGER.debug("[MaterialCompat] Residency descriptor-only success blocked for generation {}: "
                        + "reason={}, uploadedMaterials={}, visibleFallbacks={}",
                    generation, reason, uploadedMaterials, visibleFallbackMaterialCount);
            }
            return;
        }
        statusEvent.addProperty("geometryAffectingMaterialChange", false);
        statusEvent.addProperty("chunkRefreshScheduled", false);
        statusEvent.addProperty("reason", "resident_handles_update_stable_material_ids");
        ResourceMaterialRuntimeStatus.write("residencyDescriptorOnlyUpdate", generation, statusEvent);
        LOGGER.info("[MaterialCompat] Residency updated descriptor/material-table state only; "
            + "chunk rebuild skipped for generation {}", generation);
    }

    private static String packStackHash(long generation, int dependencyCount) {
        return "runtime_resource_manager_generation_" + generation + "_ctm_" + dependencyCount;
    }

    private static boolean boolProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return false;
        try {
            return json.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String stringProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return "";
        try {
            return json.get(key).getAsString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int intProperty(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) return 0;
        try {
            return json.get(key).getAsInt();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private static double millis(long startedNanos) {
        return Math.max(0.0, (System.nanoTime() - startedNanos) / 1_000_000.0);
    }

    public record CtmWorkItem(int graphId,
                              String path,
                              boolean present,
                              boolean visible) {
    }

    private record RuntimeRoot(JsonObject root, int dependencyCount, int presentDependencyCount,
                               int propertyCount) {
    }

    public record BootstrapResult(boolean attempted,
                                  boolean published,
                                  long generation,
                                  int dependencyCount,
                                  int presentDependencyCount,
                                  int propertyCount,
                                  String reason,
                                  double elapsedMs,
                                  boolean tableUploaded) {
        static BootstrapResult skipped(long generation, String reason) {
            return new BootstrapResult(false, false, generation, 0, 0, 0, reason, 0.0, false);
        }

        public JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("attempted", attempted);
            json.addProperty("published", published);
            json.addProperty("generation", generation);
            json.addProperty("dependencyCount", dependencyCount);
            json.addProperty("presentDependencyCount", presentDependencyCount);
            json.addProperty("propertyCount", propertyCount);
            json.addProperty("reason", reason);
            json.addProperty("elapsedMs", elapsedMs);
            json.addProperty("tableUploaded", tableUploaded);
            return json;
        }
    }
}
