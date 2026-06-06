package com.radiance.client.texture.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.MinecraftClient;

/**
 * Read-only resource-pack compatibility diagnostics.
 *
 * This intentionally does not feed rendering yet. It gives the material system
 * a stable parser/audit foothold before CTM or variant decisions touch chunks.
 */
public final class ResourcePackCompatDiagnostics {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int SAMPLE_LIMIT = 24;
    private static final int COMPAT_RECORD_LIMIT = 4096;
    private static final int CTM_DEPENDENCY_LIMIT = 4096;
    private static final int CTM_DEPENDENCY_RECORD_LIMIT = 512;
    private static final int MAX_PROPERTY_BYTES = 1024 * 1024;
    private static final int VALUE_LIMIT = 256;

    private ResourcePackCompatDiagnostics() {
    }

    public static String statusJson() {
        return GSON.toJson(buildStatus(false));
    }

    public static String writeReportJson() {
        JsonObject status = buildStatus(true);
        return GSON.toJson(status);
    }

    public static String scanPackJsonForTest(String path) {
        return GSON.toJson(scanPack(Path.of(path), SAMPLE_LIMIT));
    }

    public static String scanRunDirectoryJsonForTest(String path) {
        return GSON.toJson(buildStatus(Path.of(path), false));
    }

    public static String flagsSummary() {
        return "enabled=" + Options.materialCompatEnabled
            + " ctm=" + Options.materialCompatCtmEnabled
            + " random=" + Options.materialCompatRandomEnabled
            + " natural=" + Options.materialCompatNaturalEnabled
            + " colors=" + Options.materialCompatColorsEnabled
            + " overlays=" + Options.materialCompatOverlaysEnabled
            + " legacyMcPatcher=" + Options.materialCompatLegacyMcPatcherEnabled
            + " physicalEmissive=" + Options.materialCompatPhysicalEmissiveEnabled;
    }

    public static JsonObject flagsJson() {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", Options.materialCompatEnabled);
        json.addProperty("ctm", Options.materialCompatCtmEnabled);
        json.addProperty("random", Options.materialCompatRandomEnabled);
        json.addProperty("natural", Options.materialCompatNaturalEnabled);
        json.addProperty("colors", Options.materialCompatColorsEnabled);
        json.addProperty("overlays", Options.materialCompatOverlaysEnabled);
        json.addProperty("legacyMcPatcher", Options.materialCompatLegacyMcPatcherEnabled);
        json.addProperty("physicalEmissive", Options.materialCompatPhysicalEmissiveEnabled);
        return json;
    }

    public static boolean renderingConsumesCompatibilityForDebug() {
        return renderingConsumesCompatibility();
    }

    private static JsonObject buildStatus(boolean writeReport) {
        return buildStatus(runDirectory(), writeReport);
    }

    private static JsonObject buildStatus(Path runDirectory, boolean writeReport) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_material_compat_pack_scan_v1");
        root.addProperty("createdAt", Instant.now().toString());
        root.addProperty("renderingConsumesCompatibility", renderingConsumesCompatibility());
        root.add("flags", flagsJson());
        root.add("compatibilityConsumption", compatibilityConsumptionJson());
        root.add("renderSafety", renderSafetyJson());
        root.addProperty("runDirectory", runDirectory.toAbsolutePath().toString());

        ActivePackSelection activeSelection = readActivePackSelection(runDirectory);
        root.add("activePackStack", activeSelection.toJson());
        Path resourcePacks = runDirectory.resolve("resourcepacks");
        root.addProperty("resourcePacksDirectory", resourcePacks.toAbsolutePath().toString());
        JsonArray packs = scanResourcePackDirectory(resourcePacks, activeSelection);
        root.add("packs", packs);
        JsonArray activePacks = activePacks(packs);
        root.add("activePacks", activePacks);
        root.add("activeCtmAtlasDependencies", aggregateCtmDependencies(activePacks));

        if (writeReport) {
            try {
                Path output = writeReport(runDirectory, root);
                root.addProperty("reportPath", output.toAbsolutePath().toString());
            } catch (IOException e) {
                root.addProperty("reportError", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return root;
    }

    private static boolean renderingConsumesCompatibility() {
        return Options.materialCompatEnabled
            && (Options.materialCompatCtmEnabled
                || Options.materialCompatRandomEnabled
                || Options.materialCompatNaturalEnabled
                || Options.materialCompatColorsEnabled
                || Options.materialCompatOverlaysEnabled
                || Options.materialCompatPhysicalEmissiveEnabled);
    }

    private static JsonObject compatibilityConsumptionJson() {
        JsonObject json = new JsonObject();
        json.addProperty("blockQuadVariantResolver", renderingConsumesCompatibility());
        json.addProperty("fixedReplacement", Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("randomReplacement", Options.materialCompatEnabled && Options.materialCompatRandomEnabled);
        json.addProperty("repeatPatternReplacement", Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("naturalTextureUvTransforms",
            Options.materialCompatEnabled && Options.materialCompatNaturalEnabled);
        json.addProperty("colorPropertiesFixedBlockTints",
            Options.materialCompatEnabled && Options.materialCompatColorsEnabled);
        json.addProperty("optifineFixedBlockColormapProperties",
            Options.materialCompatEnabled && Options.materialCompatColorsEnabled);
        json.addProperty("colorPropertiesBiomePalettesMetadataOnly", true);
        json.addProperty("horizontalVerticalTopNeighborMasks",
            Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("ctmTileAtlasAdmission", renderingConsumesCompatibility());
        json.addProperty("full47CtmNeighborMasks", Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("compactCtmWholeQuadAndExplicitOverrides",
            Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("optifineEmissiveOverlaySprites",
            Options.materialCompatEnabled && Options.materialCompatPhysicalEmissiveEnabled);
        json.addProperty("overlayLayerEmission",
            Options.materialCompatEnabled
                && (Options.materialCompatOverlaysEnabled || Options.materialCompatPhysicalEmissiveEnabled));
        json.addProperty("ctmOverlayRandomLayerEmission",
            Options.materialCompatEnabled && Options.materialCompatOverlaysEnabled);
        json.addProperty("shaderBlockPropertiesLayerAlphaModes",
            Options.materialCompatEnabled && Options.materialCompatOverlaysEnabled);
        json.addProperty("shaderSideRuleParsing", false);
        return json;
    }

    private static JsonObject renderSafetyJson() {
        JsonObject json = new JsonObject();
        json.addProperty("filtersEmissiveAuxiliaryTexturesFromAtlas", true);
        json.addProperty("filtersPbrAuxiliarySuffixes", "_s,_n,_f,_e");
        json.addProperty("missingSpriteFallbackId", TextureArrayBridge.missingSpriteFallbackIdForTest());
        json.addProperty("missingSpriteFallbackSprite", TextureArrayBridge.missingSpriteFallbackLabel());
        return json;
    }

    private static Path writeReport(Path runDirectory, JsonObject root) throws IOException {
        Path base = RadianceClient.radianceDir != null
            ? RadianceClient.radianceDir
            : runDirectory.resolve("radiance");
        Path output = base.resolve("logs").resolve("radser-pack-index.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, GSON.toJson(root), StandardCharsets.UTF_8);
        return output;
    }

    private static Path runDirectory() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.runDirectory != null) {
                return client.runDirectory.toPath();
            }
        } catch (Throwable ignored) {
        }
        return Path.of(".");
    }

    private static JsonArray scanResourcePackDirectory(Path resourcePacks, ActivePackSelection activeSelection) {
        JsonArray packs = new JsonArray();
        if (!Files.isDirectory(resourcePacks)) {
            return packs;
        }
        try {
            List<Path> candidates;
            try (var stream = Files.list(resourcePacks)) {
                candidates = stream
                    .filter(path -> Files.isDirectory(path) || Files.isRegularFile(path))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
            }
            for (Path candidate : candidates) {
                JsonObject report = scanPack(candidate, SAMPLE_LIMIT);
                if (report.has("scannable") && report.get("scannable").getAsBoolean()) {
                    String fileName = candidate.getFileName() == null ? candidate.toString() : candidate.getFileName().toString();
                    boolean active = activeSelection.activeFilePackNames.contains(fileName);
                    report.addProperty("active", active);
                    report.addProperty("activeReference", active ? "file/" + fileName : "");
                    report.addProperty("incompatibleSelected", activeSelection.incompatibleFilePackNames.contains(fileName));
                    packs.add(report);
                }
            }
        } catch (IOException e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getClass().getSimpleName() + ": " + e.getMessage());
            packs.add(error);
        }
        return packs;
    }

    private static JsonArray activePacks(JsonArray packs) {
        JsonArray active = new JsonArray();
        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            if (pack.has("active") && pack.get("active").getAsBoolean()) {
                active.add(pack);
            }
        }
        return active;
    }

    private static JsonObject aggregateCtmDependencies(JsonArray activePacks) {
        LinkedHashMap<String, JsonObject> dependenciesByPath = new LinkedHashMap<>();
        boolean truncated = false;
        for (JsonElement packElement : activePacks) {
            JsonObject pack = packElement.getAsJsonObject();
            JsonObject summary = pack.getAsJsonObject("ctmAtlasDependencies");
            if (summary == null) {
                continue;
            }
            JsonArray dependencies = summary.getAsJsonArray("dependencies");
            if (dependencies == null) {
                continue;
            }
            for (JsonElement dependencyElement : dependencies) {
                JsonObject dependency = dependencyElement.getAsJsonObject();
                String path = dependency.has("path") ? dependency.get("path").getAsString() : "";
                if (path.isEmpty()) {
                    continue;
                }
                if (dependenciesByPath.size() < CTM_DEPENDENCY_LIMIT) {
                    dependenciesByPath.putIfAbsent(path, dependency);
                } else {
                    truncated = true;
                }
            }
            truncated |= summary.has("truncated") && summary.get("truncated").getAsBoolean();
        }

        int present = 0;
        int missing = 0;
        int withSpecular = 0;
        int withNormal = 0;
        int withEmissive = 0;
        int requiringAtlasAdmission = 0;
        int presentRequiringAtlasAdmission = 0;
        JsonArray dependencies = new JsonArray();
        for (JsonObject dependency : dependenciesByPath.values()) {
            if (dependency.get("present").getAsBoolean()) present++; else missing++;
            if (dependency.get("specularPresent").getAsBoolean()) withSpecular++;
            if (dependency.get("normalPresent").getAsBoolean()) withNormal++;
            if (dependency.get("emissivePresent").getAsBoolean()) withEmissive++;
            if (dependency.has("atlasAdmissionRequired")
                && dependency.get("atlasAdmissionRequired").getAsBoolean()) {
                requiringAtlasAdmission++;
                if (dependency.get("present").getAsBoolean()) {
                    presentRequiringAtlasAdmission++;
                }
            }
            if (dependencies.size() < CTM_DEPENDENCY_RECORD_LIMIT) {
                dependencies.add(dependency);
            }
        }

        JsonObject json = new JsonObject();
        json.addProperty("uniqueTiles", dependenciesByPath.size());
        json.addProperty("presentTiles", present);
        json.addProperty("missingTiles", missing);
        json.addProperty("tilesWithSpecular", withSpecular);
        json.addProperty("tilesWithNormal", withNormal);
        json.addProperty("tilesWithEmissive", withEmissive);
        json.addProperty("tilesRequiringAtlasAdmission", requiringAtlasAdmission);
        json.addProperty("presentTilesRequiringAtlasAdmission", presentRequiringAtlasAdmission);
        json.addProperty("truncated", truncated || dependenciesByPath.size() > CTM_DEPENDENCY_RECORD_LIMIT);
        json.add("dependencies", dependencies);
        return json;
    }

    private static ActivePackSelection readActivePackSelection(Path runDirectory) {
        Path options = runDirectory.resolve("options.txt");
        if (!Files.isRegularFile(options)) {
            return new ActivePackSelection(List.of(), List.of(), "missing_options");
        }

        List<String> resourcePacks = List.of();
        List<String> incompatible = List.of();
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (line.startsWith("resourcePacks:")) {
                    resourcePacks = parseStringArrayOption(line.substring("resourcePacks:".length()));
                } else if (line.startsWith("incompatibleResourcePacks:")) {
                    incompatible = parseStringArrayOption(line.substring("incompatibleResourcePacks:".length()));
                }
            }
            return new ActivePackSelection(resourcePacks, incompatible, "options.txt");
        } catch (Exception e) {
            return new ActivePackSelection(resourcePacks, incompatible,
                e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static List<String> parseStringArrayOption(String raw) {
        ArrayList<String> values = new ArrayList<>();
        JsonElement parsed = JsonParser.parseString(raw.trim());
        if (!parsed.isJsonArray()) {
            return values;
        }
        for (JsonElement element : parsed.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private static String filePackName(String optionEntry) {
        if (optionEntry == null) return "";
        if (optionEntry.startsWith("file/")) return optionEntry.substring("file/".length());
        return "";
    }

    private static final class ActivePackSelection {
        private final List<String> resourcePacks;
        private final List<String> incompatibleResourcePacks;
        private final Set<String> activeFilePackNames = new HashSet<>();
        private final Set<String> incompatibleFilePackNames = new HashSet<>();
        private final String source;

        ActivePackSelection(List<String> resourcePacks, List<String> incompatibleResourcePacks, String source) {
            this.resourcePacks = resourcePacks;
            this.incompatibleResourcePacks = incompatibleResourcePacks;
            this.source = source;
            for (String entry : resourcePacks) {
                String fileName = filePackName(entry);
                if (!fileName.isEmpty()) {
                    activeFilePackNames.add(fileName);
                }
            }
            for (String entry : incompatibleResourcePacks) {
                String fileName = filePackName(entry);
                if (!fileName.isEmpty()) {
                    incompatibleFilePackNames.add(fileName);
                }
            }
        }

        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("source", source);
            json.add("resourcePacks", stringArray(resourcePacks));
            json.add("incompatibleResourcePacks", stringArray(incompatibleResourcePacks));
            json.add("activeFilePackNames", stringArray(activeFilePackNames.stream().sorted().toList()));
            json.add("incompatibleFilePackNames", stringArray(incompatibleFilePackNames.stream().sorted().toList()));
            return json;
        }

        private JsonArray stringArray(List<String> values) {
            JsonArray array = new JsonArray();
            for (String value : values) {
                array.add(value);
            }
            return array;
        }
    }

    private static JsonObject scanPack(Path pack, int sampleLimit) {
        PackCounters counters = new PackCounters(pack, sampleLimit);
        if (Files.isDirectory(pack)) {
            counters.scannable = true;
            counters.kind = "directory";
            try (var stream = Files.walk(pack)) {
                stream.filter(Files::isRegularFile)
                    .forEach(path -> counters.visit(pack.relativize(path).toString(), () -> Files.newInputStream(path)));
            } catch (IOException e) {
                counters.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            return counters.toJson();
        }

        if (!Files.isRegularFile(pack)) {
            counters.error = "not_a_file_or_directory";
            return counters.toJson();
        }

        try (ZipFile zip = new ZipFile(pack.toFile())) {
            counters.scannable = true;
            counters.kind = "zip";
            zip.stream()
                .filter(entry -> !entry.isDirectory())
                .forEach(entry -> counters.visit(entry.getName(), () -> zip.getInputStream(entry)));
        } catch (IOException e) {
            counters.error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return counters.toJson();
    }

    private interface StreamSupplier {
        InputStream open() throws IOException;
    }

    private static final class PackCounters {
        private final Path pack;
        private final int sampleLimit;
        private final Map<String, Integer> propertyKeyCounts = new LinkedHashMap<>();
        private final Map<String, Integer> compatFeatureCounts = new LinkedHashMap<>();
        private final Set<String> entryNames = new HashSet<>();
        private final Set<String> albedoBases = new HashSet<>();
        private final Set<String> specularBases = new HashSet<>();
        private final Set<String> normalBases = new HashSet<>();
        private final Set<String> flagBases = new HashSet<>();
        private final Set<String> emissiveBases = new HashSet<>();
        private final LinkedHashMap<String, CtmDependency> ctmDependencies = new LinkedHashMap<>();
        private final JsonArray samples = new JsonArray();
        private final JsonArray compatRecords = new JsonArray();
        private boolean scannable;
        private String kind = "unknown";
        private String error;
        private int entries;
        private int texturePng;
        private int albedoPng;
        private int specularPng;
        private int normalPng;
        private int flagPng;
        private int emissivePng;
        private int optifineCtmProperties;
        private int mcpatcherCtmProperties;
        private int textureProperties;
        private int emissiveProperties;
        private int naturalProperties;
        private int colorProperties;
        private int blockColormapProperties;
        private int lightmapProperties;
        private int blockProperties;
        private int customAnimationEntries;
        private int citEntries;
        private int cemEntries;
        private int randomEntityEntries;
        private int properties;
        private int packMcmeta;
        private boolean compatRecordsTruncated;
        private boolean ctmDependenciesTruncated;
        private boolean ctmDependenciesFinalized;

        PackCounters(Path pack, int sampleLimit) {
            this.pack = pack;
            this.sampleLimit = sampleLimit;
        }

        void visit(String rawName, StreamSupplier streamSupplier) {
            entries++;
            String name = normalize(rawName);
            String lower = name.toLowerCase(Locale.ROOT);
            entryNames.add(lower);

            if (lower.endsWith("pack.mcmeta")) packMcmeta++;
            if (lower.endsWith(".png") && lower.contains("/textures/")) {
                texturePng++;
                String base = textureBase(lower);
                if (lower.endsWith("_s.png")) {
                    specularPng++;
                    specularBases.add(base);
                } else if (lower.endsWith("_n.png")) {
                    normalPng++;
                    normalBases.add(base);
                } else if (lower.endsWith("_f.png")) {
                    flagPng++;
                    flagBases.add(base);
                } else if (lower.endsWith("_e.png")) {
                    emissivePng++;
                    emissiveBases.add(base);
                } else {
                    albedoPng++;
                    albedoBases.add(base);
                }
            }
            if (lower.contains("/optifine/ctm/") && lower.endsWith(".properties")) optifineCtmProperties++;
            if (lower.contains("/mcpatcher/ctm/") && lower.endsWith(".properties")) mcpatcherCtmProperties++;
            if (lower.endsWith("/texture.properties") || lower.endsWith("texture.properties")) textureProperties++;
            if (lower.endsWith("/emissive.properties") || lower.endsWith("emissive.properties")) emissiveProperties++;
            if (lower.endsWith("/natural.properties") || lower.endsWith("natural.properties")) naturalProperties++;
            if (lower.endsWith("/color.properties") || lower.endsWith("color.properties")) colorProperties++;
            if (lower.contains("/optifine/colormap/blocks/") && lower.endsWith(".properties")) blockColormapProperties++;
            if (lower.contains("/mcpatcher/colormap/blocks/") && lower.endsWith(".properties")) blockColormapProperties++;
            if (lower.endsWith("/lightmap.properties") || lower.endsWith("lightmap.properties")) lightmapProperties++;
            if (lower.endsWith("/block.properties") || lower.endsWith("block.properties")) blockProperties++;
            if (lower.contains("/optifine/anim/") || lower.contains("/mcpatcher/anim/")) customAnimationEntries++;
            if (lower.contains("/optifine/cit/") || lower.contains("/mcpatcher/cit/")) citEntries++;
            if (lower.contains("/optifine/cem/") || lower.contains("/mcpatcher/cem/")) cemEntries++;
            if (lower.contains("/optifine/random/") || lower.contains("/mcpatcher/random/")) randomEntityEntries++;

            if (lower.endsWith(".properties")) {
                properties++;
                parseProperties(name, streamSupplier);
            }
        }

        private void parseProperties(String name, StreamSupplier streamSupplier) {
            Properties props = new Properties();
            try (InputStream in = streamSupplier.open()) {
                props.load(new BufferedReader(new InputStreamReader(limit(in), StandardCharsets.UTF_8)));
            } catch (IOException e) {
                addSample(name, "properties_error:" + e.getClass().getSimpleName(), List.of());
                return;
            }

            ArrayList<String> keys = new ArrayList<>();
            for (Object rawKey : props.keySet()) {
                String key = String.valueOf(rawKey);
                keys.add(key);
                propertyKeyCounts.merge(key, 1, Integer::sum);
            }
            keys.sort(String::compareTo);
            if (isImportantPropertyFile(name, keys)) {
                String kind = classifyPropertyFile(name);
                compatFeatureCounts.merge(kind, 1, Integer::sum);
                addSample(name, kind, keys);
                addCompatRecord(name, kind, props, keys);
            }
        }

        private InputStream limit(InputStream in) {
            return new InputStream() {
                private int readBytes;

                @Override
                public int read() throws IOException {
                    if (readBytes >= MAX_PROPERTY_BYTES) return -1;
                    int value = in.read();
                    if (value >= 0) readBytes++;
                    return value;
                }
            };
        }

        private boolean isImportantPropertyFile(String name, List<String> keys) {
            String lower = name.toLowerCase(Locale.ROOT);
            return lower.contains("/optifine/")
                || lower.contains("/mcpatcher/")
                || lower.endsWith("texture.properties")
                || lower.endsWith("emissive.properties")
                || lower.endsWith("natural.properties")
                || keys.contains("method")
                || keys.contains("matchBlocks")
                || keys.contains("matchTiles")
                || keys.contains("format");
        }

        private String classifyPropertyFile(String name) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.contains("/ctm/")) return "ctm";
            if (lower.contains("/colormap/blocks/")) return "block_colormap_properties";
            if (lower.endsWith("texture.properties")) return "texture_properties";
            if (lower.endsWith("emissive.properties")) return "emissive_properties";
            if (lower.endsWith("natural.properties")) return "natural_properties";
            if (lower.endsWith("color.properties")) return "color_properties";
            if (lower.endsWith("block.properties")) return "block_properties";
            return "properties";
        }

        private void addCompatRecord(String path, String kind, Properties props, List<String> keys) {
            if (compatRecords.size() >= COMPAT_RECORD_LIMIT) {
                compatRecordsTruncated = true;
                return;
            }
            JsonObject record = new JsonObject();
            record.addProperty("path", path);
            record.addProperty("kind", kind);
            record.addProperty("propertyCount", keys.size());
            addIfPresent(record, props, "format");
            addIfPresent(record, props, "blocks");
            addIfPresent(record, props, "color");
            addIfPresent(record, props, "method");
            addIfPresent(record, props, "matchBlocks");
            addIfPresent(record, props, "matchTiles");
            addIfPresent(record, props, "tiles");
            addIfPresent(record, props, "width");
            addIfPresent(record, props, "height");
            addIfPresent(record, props, "faces");
            addIfPresent(record, props, "connect");
            addIfPresent(record, props, "connectBlocks");
            addIfPresent(record, props, "connectTiles");
            addIfPresent(record, props, "weights");
            addIfPresent(record, props, "symmetry");
            addIfPresent(record, props, "layer");
            addIfPresent(record, props, "layer.cutout");
            addIfPresent(record, props, "layer.cutout_mipped");
            addIfPresent(record, props, "layer.translucent");
            addIfPresent(record, props, "suffix");
            addIfPresent(record, props, "suffix.emissive");
            record.add("values", propertyValues(props, keys));
            compatRecords.add(record);
        }

        private void finalizeCtmDependencies() {
            if (ctmDependenciesFinalized) {
                return;
            }
            ctmDependenciesFinalized = true;
            for (JsonElement element : compatRecords) {
                JsonObject record = element.getAsJsonObject();
                if (!record.has("kind") || !"ctm".equals(record.get("kind").getAsString())) {
                    continue;
                }
                String tiles = record.has("tiles") ? record.get("tiles").getAsString() : "";
                String method = record.has("method") ? record.get("method").getAsString() : "";
                String propertyPath = record.has("path") ? record.get("path").getAsString() : "";
                List<String> dependencyPaths =
                    ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(propertyPath, tiles, method);
                if (dependencyPaths.isEmpty()) {
                    record.add("tileDependencies", new JsonArray());
                    record.addProperty("tileDependencyCount", 0);
                    record.addProperty("tileDependencyPresentCount", 0);
                    record.addProperty("tileDependencyMissingCount", 0);
                    continue;
                }
                JsonArray dependencies = new JsonArray();
                int present = 0;
                int missing = 0;
                for (String dependencyPath : dependencyPaths) {
                    CtmDependency dependency = ctmDependency(dependencyPath);
                    if (dependency.present) {
                        present++;
                    } else {
                        missing++;
                    }
                    if (dependencies.size() < CTM_DEPENDENCY_RECORD_LIMIT) {
                        dependencies.add(dependency.toJson());
                    }
                    if (ctmDependencies.size() < CTM_DEPENDENCY_LIMIT) {
                        ctmDependencies.putIfAbsent(dependency.path, dependency);
                    } else {
                        ctmDependenciesTruncated = true;
                    }
                }
                record.add("tileDependencies", dependencies);
                record.addProperty("tileDependencyCount", present + missing);
                record.addProperty("tileDependencyPresentCount", present);
                record.addProperty("tileDependencyMissingCount", missing);
                record.addProperty("tileDependenciesTruncated", dependencies.size() >= CTM_DEPENDENCY_RECORD_LIMIT);
            }
        }

        private CtmDependency ctmDependency(String rawPath) {
            String path = normalize(rawPath);
            String lower = path.toLowerCase(Locale.ROOT);
            return new CtmDependency(
                path,
                ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(path) == null
                    ? ""
                    : ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(path).toString(),
                ResourcePackCompatCtmTiles.atlasSpriteIdentifier(path),
                ResourcePackCompatCtmTiles.requiresAtlasAdmission(path),
                entryNames.contains(lower),
                ResourcePackCompatCtmTiles.sidecarPath(path, "_s"),
                entryNames.contains(ResourcePackCompatCtmTiles.sidecarPath(path, "_s").toLowerCase(Locale.ROOT)),
                ResourcePackCompatCtmTiles.sidecarPath(path, "_n"),
                entryNames.contains(ResourcePackCompatCtmTiles.sidecarPath(path, "_n").toLowerCase(Locale.ROOT)),
                ResourcePackCompatCtmTiles.sidecarPath(path, "_f"),
                entryNames.contains(ResourcePackCompatCtmTiles.sidecarPath(path, "_f").toLowerCase(Locale.ROOT)),
                ResourcePackCompatCtmTiles.sidecarPath(path, "_e"),
                entryNames.contains(ResourcePackCompatCtmTiles.sidecarPath(path, "_e").toLowerCase(Locale.ROOT)));
        }

        private void addIfPresent(JsonObject record, Properties props, String key) {
            String value = props.getProperty(key);
            if (value != null) {
                record.addProperty(key, trimValue(value));
            }
        }

        private JsonObject propertyValues(Properties props, List<String> keys) {
            JsonObject values = new JsonObject();
            for (int i = 0; i < Math.min(keys.size(), 96); i++) {
                String key = keys.get(i);
                values.addProperty(key, trimValue(props.getProperty(key, "")));
            }
            if (keys.size() > 96) {
                values.addProperty("_truncated", true);
            }
            return values;
        }

        private String trimValue(String value) {
            if (value == null) return "";
            String trimmed = value.trim();
            if (trimmed.length() <= VALUE_LIMIT) {
                return trimmed;
            }
            return trimmed.substring(0, VALUE_LIMIT) + "...";
        }

        private void addSample(String path, String kind, List<String> keys) {
            if (samples.size() >= sampleLimit) return;
            JsonObject sample = new JsonObject();
            sample.addProperty("path", path);
            sample.addProperty("kind", kind);
            JsonArray keyArray = new JsonArray();
            for (int i = 0; i < Math.min(keys.size(), 24); i++) {
                keyArray.add(keys.get(i));
            }
            sample.add("keys", keyArray);
            samples.add(sample);
        }

        JsonObject toJson() {
            finalizeCtmDependencies();
            JsonObject json = new JsonObject();
            json.addProperty("name", pack.getFileName() == null ? pack.toString() : pack.getFileName().toString());
            json.addProperty("path", pack.toAbsolutePath().toString());
            json.addProperty("kind", kind);
            json.addProperty("scannable", scannable);
            if (error != null) json.addProperty("error", error);
            json.addProperty("entries", entries);
            json.addProperty("packMcmeta", packMcmeta);
            JsonObject counts = new JsonObject();
            counts.addProperty("texturePng", texturePng);
            counts.addProperty("albedoPng", albedoPng);
            counts.addProperty("specular_s", specularPng);
            counts.addProperty("normal_n", normalPng);
            counts.addProperty("flag_f", flagPng);
            counts.addProperty("emissive_e", emissivePng);
            counts.addProperty("properties", properties);
            counts.addProperty("textureProperties", textureProperties);
            counts.addProperty("optifineCtmProperties", optifineCtmProperties);
            counts.addProperty("mcpatcherCtmProperties", mcpatcherCtmProperties);
            counts.addProperty("emissiveProperties", emissiveProperties);
            counts.addProperty("naturalProperties", naturalProperties);
            counts.addProperty("colorProperties", colorProperties);
            counts.addProperty("blockColormapProperties", blockColormapProperties);
            counts.addProperty("lightmapProperties", lightmapProperties);
            counts.addProperty("blockProperties", blockProperties);
            counts.addProperty("customAnimationEntries", customAnimationEntries);
            counts.addProperty("citEntries", citEntries);
            counts.addProperty("cemEntries", cemEntries);
            counts.addProperty("randomEntityEntries", randomEntityEntries);
            json.add("counts", counts);
            json.add("labpbrCoverage", labpbrCoverageJson());
            json.add("compatFeatures", compatFeaturesJson());
            json.add("ctmAtlasDependencies", ctmAtlasDependenciesJson());
            json.addProperty("compatRecordsTruncated", compatRecordsTruncated);
            json.add("compatRecords", compatRecords);
            json.add("propertyKeys", propertyKeysJson());
            json.add("samples", samples);
            return json;
        }

        private JsonObject ctmAtlasDependenciesJson() {
            JsonObject json = new JsonObject();
            int present = 0;
            int missing = 0;
            int withSpecular = 0;
            int withNormal = 0;
            int withEmissive = 0;
            int requiringAtlasAdmission = 0;
            int presentRequiringAtlasAdmission = 0;
            JsonArray dependencies = new JsonArray();
            for (CtmDependency dependency : ctmDependencies.values()) {
                if (dependency.present) present++; else missing++;
                if (dependency.specularPresent) withSpecular++;
                if (dependency.normalPresent) withNormal++;
                if (dependency.emissivePresent) withEmissive++;
                if (dependency.atlasAdmissionRequired) {
                    requiringAtlasAdmission++;
                    if (dependency.present) {
                        presentRequiringAtlasAdmission++;
                    }
                }
                if (dependencies.size() < CTM_DEPENDENCY_RECORD_LIMIT) {
                    dependencies.add(dependency.toJson());
                }
            }
            json.addProperty("uniqueTiles", ctmDependencies.size());
            json.addProperty("presentTiles", present);
            json.addProperty("missingTiles", missing);
            json.addProperty("tilesWithSpecular", withSpecular);
            json.addProperty("tilesWithNormal", withNormal);
            json.addProperty("tilesWithEmissive", withEmissive);
            json.addProperty("tilesRequiringAtlasAdmission", requiringAtlasAdmission);
            json.addProperty("presentTilesRequiringAtlasAdmission", presentRequiringAtlasAdmission);
            json.addProperty("truncated", ctmDependenciesTruncated
                || ctmDependencies.size() > CTM_DEPENDENCY_RECORD_LIMIT);
            json.add("dependencies", dependencies);
            return json;
        }

        private JsonObject labpbrCoverageJson() {
            JsonObject json = new JsonObject();
            json.addProperty("albedoBases", albedoBases.size());
            json.addProperty("specularBases", specularBases.size());
            json.addProperty("normalBases", normalBases.size());
            json.addProperty("flagBases", flagBases.size());
            json.addProperty("emissiveBases", emissiveBases.size());
            json.addProperty("albedoWithSpecular", intersectionCount(albedoBases, specularBases));
            json.addProperty("albedoWithNormal", intersectionCount(albedoBases, normalBases));
            json.addProperty("albedoWithSpecularAndNormal", tripleIntersectionCount(albedoBases, specularBases, normalBases));
            json.addProperty("orphanSpecular", orphanCount(specularBases, albedoBases));
            json.addProperty("orphanNormal", orphanCount(normalBases, albedoBases));
            json.addProperty("orphanEmissive", orphanCount(emissiveBases, albedoBases));
            return json;
        }

        private JsonObject compatFeaturesJson() {
            JsonObject json = new JsonObject();
            compatFeatureCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));
            return json;
        }

        private int intersectionCount(Set<String> primary, Set<String> secondary) {
            int count = 0;
            for (String value : primary) {
                if (secondary.contains(value)) {
                    count++;
                }
            }
            return count;
        }

        private int tripleIntersectionCount(Set<String> primary, Set<String> second, Set<String> third) {
            int count = 0;
            for (String value : primary) {
                if (second.contains(value) && third.contains(value)) {
                    count++;
                }
            }
            return count;
        }

        private int orphanCount(Set<String> sidecar, Set<String> albedo) {
            int count = 0;
            for (String value : sidecar) {
                if (!albedo.contains(value)) {
                    count++;
                }
            }
            return count;
        }

        private JsonArray propertyKeysJson() {
            JsonArray keys = new JsonArray();
            propertyKeyCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
                .limit(64)
                .forEach(entry -> {
                    JsonObject key = new JsonObject();
                    key.addProperty("key", entry.getKey());
                    key.addProperty("count", entry.getValue());
                    keys.add(key);
                });
            return keys;
        }

        private String normalize(String value) {
            return value.replace('\\', '/');
        }

        private String textureBase(String lower) {
            String base = lower.substring(0, lower.length() - ".png".length());
            if (base.endsWith("_s") || base.endsWith("_n") || base.endsWith("_f") || base.endsWith("_e")) {
                return base.substring(0, base.length() - 2);
            }
            return base;
        }
    }

    private record CtmDependency(String path,
                                 String resource,
                                 String atlasSprite,
                                 boolean atlasAdmissionRequired,
                                 boolean present,
                                 String specularPath,
                                 boolean specularPresent,
                                 String normalPath,
                                 boolean normalPresent,
                                 String flagPath,
                                 boolean flagPresent,
                                 String emissivePath,
                                 boolean emissivePresent) {
        JsonObject toJson() {
            JsonObject json = new JsonObject();
            json.addProperty("path", path);
            json.addProperty("resource", resource);
            json.addProperty("atlasSprite", atlasSprite);
            json.addProperty("atlasAdmissionRequired", atlasAdmissionRequired);
            json.addProperty("present", present);
            json.addProperty("specularPath", specularPath);
            json.addProperty("specularPresent", specularPresent);
            json.addProperty("normalPath", normalPath);
            json.addProperty("normalPresent", normalPresent);
            json.addProperty("flagPath", flagPath);
            json.addProperty("flagPresent", flagPresent);
            json.addProperty("emissivePath", emissivePath);
            json.addProperty("emissivePresent", emissivePresent);
            return json;
        }
    }
}
