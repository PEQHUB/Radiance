package com.radiance.client.texture.compat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.RadianceClient;
import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.material.ResourceMaterialRegistry;
import com.radiance.client.texture.material.ResourceMaterialRegistry.Snapshot;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import net.minecraft.util.Identifier;

/**
 * Resource-pack compatibility parser diagnostics.
 *
 * The dumps mirror the Java-side parser state that feeds chunk-side rule
 * resolution. Shaders consume resolved sprite/material ids, not rule files.
 */
public final class ResourcePackCompatDiagnostics {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MINECRAFT_VERSION = "1.21.4";
    private static final int SAMPLE_LIMIT = 24;
    private static final int COMPAT_RECORD_LIMIT = 4096;
    private static final int CTM_DEPENDENCY_LIMIT = 65536;
    private static final int CTM_DEPENDENCY_RECORD_LIMIT = 65536;
    private static final int MAX_PROPERTY_BYTES = 1024 * 1024;
    private static final int VALUE_LIMIT = 256;
    private static final List<String> DIAGNOSTIC_DUMP_FILES = List.of(
        "radser-pack-index.json",
        "radser-texture-assets.json",
        "radser-material-sets.json",
        "radser-ctm-rules.json",
        "radser-rule-precedence.json",
        "radser-parser-warnings.json");

    private ResourcePackCompatDiagnostics() {
    }

    public static String statusJson() {
        return GSON.toJson(buildStatus(false));
    }

    public static String writeReportJson() {
        JsonObject status = buildStatus(true);
        return GSON.toJson(status);
    }

    public static void writeReportAsync(String reason) {
        Thread thread = new Thread(() -> {
            try {
                writeReportJson();
            } catch (Throwable t) {
                System.err.println("[ResourcePackCompat] async report failed"
                    + (reason == null || reason.isBlank() ? "" : " reason=" + reason)
                    + " error=" + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
        }, "RadSER Material Compat Report");
        thread.setDaemon(true);
        thread.start();
    }

    public static String scanPackJsonForTest(String path) {
        return GSON.toJson(scanPack(Path.of(path), SAMPLE_LIMIT));
    }

    public static String scanRunDirectoryJsonForTest(String path) {
        return GSON.toJson(buildStatus(Path.of(path), false));
    }

    public static String writeRunDirectoryReportsForTest(String path) {
        return GSON.toJson(buildStatus(Path.of(path), true));
    }

    public static String flagsSummary() {
        return "enabled=" + Options.materialCompatEnabled
            + " ctm=" + Options.materialCompatCtmEnabled
            + " random=" + Options.materialCompatRandomEnabled
            + " natural=" + Options.materialCompatNaturalEnabled
            + " colors=" + Options.materialCompatColorsEnabled
            + " overlays=" + Options.materialCompatOverlaysEnabled
            + " legacyMcPatcher=" + Options.materialCompatLegacyMcPatcherEnabled
            + " physicalEmissive=" + Options.materialCompatPhysicalEmissiveEnabled
            + " ctmAtlasAdmissionLimit=" + Options.materialCompatCtmAtlasAdmissionLimit;
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
        json.addProperty("ctmAtlasAdmissionLimit", Options.materialCompatCtmAtlasAdmissionLimit);
        return json;
    }

    public static boolean renderingConsumesCompatibilityForDebug() {
        return renderingConsumesCompatibility();
    }

    private static JsonObject buildStatus(boolean writeReport) {
        return buildStatus(runDirectory(), writeReport);
    }

    private static JsonObject buildStatus(Path runDirectory, boolean writeReport) {
        long startedNanos = System.nanoTime();
        JsonObject root = new JsonObject();
        root.addProperty("schema", "radser_material_compat_pack_scan_v1");
        root.addProperty("createdAt", Instant.now().toString());
        root.addProperty("minecraftVersion", MINECRAFT_VERSION);
        root.addProperty("renderingConsumesCompatibility", renderingConsumesCompatibility());
        root.add("flags", flagsJson());
        root.add("optionsProvenance", optionsProvenanceJson(runDirectory));
        root.add("compatibilityConsumption", compatibilityConsumptionJson());
        root.add("renderSafety", renderSafetyJson());
        root.addProperty("runDirectory", runDirectory.toAbsolutePath().toString());

        ActivePackSelection activeSelection = readActivePackSelection(runDirectory);
        root.addProperty("packStackHash", activeSelection.packStackHash());
        root.add("activePackStack", activeSelection.toJson());
        long activeSelectionNanos = System.nanoTime();
        Path resourcePacks = runDirectory.resolve("resourcepacks");
        root.addProperty("resourcePacksDirectory", resourcePacks.toAbsolutePath().toString());
        JsonArray packs = scanResourcePackDirectory(resourcePacks, activeSelection);
        long scanPacksNanos = System.nanoTime();
        root.add("packs", packs);
        JsonArray activePacks = activePacks(packs);
        long activePacksNanos = System.nanoTime();
        root.add("activePacks", activePacks);
        JsonObject activeCtm = aggregateCtmDependencies(activePacks);
        long aggregateCtmNanos = System.nanoTime();
        root.add("activeCtmAtlasDependencies", activeCtm);
        root.add("ctmAtlasAdmission", ctmAtlasAdmissionJson(activeCtm));
        root.add("compatibility", compatibilityJson(activePacks));
        root.add("labPbr", labPbrJson(activePacks));
        root.add("materialUniverse", materialUniverseJson(activeSelection, activePacks, activeCtm));
        Snapshot materialSnapshot =
            ResourceMaterialRegistry.publishFromCompatReport(root, TextureArrayBridge.getActiveTextureGeneration());
        root.add("materialRegistry", materialSnapshot.toSummaryJson());
        root.add("materialRecordSamples", materialSnapshot.recordsJson(SAMPLE_LIMIT));
        long materialRegistryNanos = System.nanoTime();
        root.add("reloadStageTimings", reloadStageTimingsJson(
            startedNanos,
            activeSelectionNanos,
            scanPacksNanos,
            activePacksNanos,
            aggregateCtmNanos,
            materialRegistryNanos));

        if (writeReport) {
            try {
                writeReports(runDirectory, root);
            } catch (IOException e) {
                root.addProperty("reportError", e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
        return root;
    }

    private static JsonObject reloadStageTimingsJson(long startedNanos,
        long activeSelectionNanos,
        long scanPacksNanos,
        long activePacksNanos,
        long aggregateCtmNanos,
        long materialRegistryNanos) {
        JsonObject json = new JsonObject();
        json.addProperty("activeSelectionMs", millis(startedNanos, activeSelectionNanos));
        json.addProperty("resourcePackScanMs", millis(activeSelectionNanos, scanPacksNanos));
        json.addProperty("activePackOrderingMs", millis(scanPacksNanos, activePacksNanos));
        json.addProperty("ctmDependencyAggregationMs", millis(activePacksNanos, aggregateCtmNanos));
        json.addProperty("materialRegistryPublishMs", millis(aggregateCtmNanos, materialRegistryNanos));
        json.addProperty("totalMaterialCompatReportMs", millis(startedNanos, materialRegistryNanos));
        json.addProperty("asyncSafe", true);
        return json;
    }

    private static double millis(long fromNanos, long toNanos) {
        return Math.max(0.0, (toNanos - fromNanos) / 1_000_000.0);
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
        json.addProperty("randomEntityTextureReplacement",
            Options.materialCompatEnabled && Options.materialCompatRandomEnabled);
        json.addProperty("repeatPatternReplacement", Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("naturalTextureUvTransforms",
            Options.materialCompatEnabled && Options.materialCompatNaturalEnabled);
        json.addProperty("colorPropertiesFixedBlockTints",
            Options.materialCompatEnabled && Options.materialCompatColorsEnabled);
        json.addProperty("optifineFixedBlockColormapProperties",
            Options.materialCompatEnabled && Options.materialCompatColorsEnabled);
        json.addProperty("colorPropertiesBiomePalettes",
            Options.materialCompatEnabled && Options.materialCompatColorsEnabled);
        json.addProperty("colorPropertiesBiomePalettesMetadataOnly", false);
        json.addProperty("optifineCustomLightmaps",
            Options.materialCompatEnabled && Options.materialCompatColorsEnabled);
        json.addProperty("optifineCustomLightmapsMetadataOnly", false);
        json.addProperty("horizontalVerticalTopNeighborMasks",
            Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("ctmTileAtlasAdmission", Options.materialCompatEnabled
            && Options.materialCompatCtmEnabled
            && Options.materialCompatCtmAtlasAdmissionLimit > 0);
        json.addProperty("ctmTileAtlasAdmissionLimit", Options.materialCompatCtmAtlasAdmissionLimit);
        json.addProperty("full47CtmNeighborMasks", Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("compactCtmWholeQuadAndExplicitOverrides",
            Options.materialCompatEnabled && Options.materialCompatCtmEnabled);
        json.addProperty("compactCtmSplitQuadQuadrants",
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
        json.addProperty("shaderBlockPropertiesDisableSolidCheck",
            Options.materialCompatEnabled && Options.materialCompatOverlaysEnabled);
        json.addProperty("javaSideRuleParsing", Options.materialCompatEnabled);
        json.addProperty("javaChunkQuadRuleResolution", renderingConsumesCompatibility());
        json.addProperty("shaderSideRuleParsing", false);
        json.addProperty("shaderSideRuleNativeBinding", false);
        json.addProperty("shaderConsumesResolvedSpriteAndMaterialIds", renderingConsumesCompatibility());
        return json;
    }

    private static JsonObject ctmAtlasAdmissionJson(JsonObject activeCtm) {
        int limit = Math.max(0, Options.materialCompatCtmAtlasAdmissionLimit);
        boolean enabled = Options.materialCompatEnabled
            && Options.materialCompatCtmEnabled
            && limit > 0;
        int candidateTiles = intProperty(activeCtm, "presentTilesRequiringAtlasAdmission");
        JsonObject json = new JsonObject();
        json.addProperty("enabled", enabled);
        json.addProperty("admissionLimit", limit);
        json.addProperty("admittedToVanillaAtlas", enabled ? Math.min(limit, candidateTiles) : 0);
        json.addProperty("candidateTilesRequiringAdmission", candidateTiles);
        json.addProperty("rendererCorrectnessDependsOnAdmission", false);
        json.addProperty("policy", enabled
            ? "debug_or_explicit_cap_only"
            : "disabled_by_default");
        return json;
    }

    private static JsonObject compatibilityJson(JsonArray activePacks) {
        JsonObject json = new JsonObject();
        json.addProperty("minecraftVersion", MINECRAFT_VERSION);
        JsonArray incompatible = new JsonArray();
        JsonArray warnings = new JsonArray();
        for (JsonElement packElement : activePacks) {
            JsonObject pack = packElement.getAsJsonObject();
            if (!boolProperty(pack, "incompatibleSelected")) {
                continue;
            }
            String name = stringProperty(pack, "name");
            incompatible.add(name);
            JsonObject warning = warning("warning", "selected_pack_marked_incompatible",
                "Minecraft options.txt marks this selected pack as incompatible.");
            addPackIdentity(warning, pack);
            warnings.add(warning);
        }
        json.add("incompatiblePacks", incompatible);
        json.add("packFormatWarnings", warnings);
        return json;
    }

    private static JsonObject labPbrJson(JsonArray activePacks) {
        JsonObject json = new JsonObject();
        json.addProperty("declared", false);
        json.addProperty("format", "");
        json.addProperty("source", "");
        for (JsonElement packElement : activePacks) {
            JsonObject pack = packElement.getAsJsonObject();
            for (JsonElement recordElement : array(pack, "compatRecords")) {
                JsonObject record = recordElement.getAsJsonObject();
                if (!"texture_properties".equals(stringProperty(record, "kind"))) {
                    continue;
                }
                String format = stringProperty(record, "format");
                if (format.toLowerCase(Locale.ROOT).contains("lab-pbr")) {
                    json.addProperty("declared", true);
                    json.addProperty("format", format);
                    json.addProperty("source", stringProperty(record, "path"));
                    return json;
                }
            }
        }
        return json;
    }

    private static JsonObject materialUniverseJson(ActivePackSelection activeSelection,
        JsonArray activePacks, JsonObject activeCtm) {
        JsonObject json = new JsonObject();
        json.addProperty("minecraftVersion", MINECRAFT_VERSION);
        json.addProperty("packStackHash", activeSelection.packStackHash());
        json.addProperty("vanillaSprites", TextureArrayBridge.sortedSpriteIds.size());
        json.addProperty("ctmPropertyFiles", activeCtmPropertyFiles(activePacks));
        json.add("ctmRulesByMethod", ctmRulesByMethod(activePacks));
        json.add("labPbr", labPbrJson(activePacks));
        json.addProperty("ctmTileDependencies", intProperty(activeCtm, "uniqueTiles"));
        json.addProperty("ctmTilesPresent", intProperty(activeCtm, "presentTiles"));
        json.addProperty("ctmTilesMissing", intProperty(activeCtm, "missingTiles"));
        json.addProperty("ctmTilesWithNormal", intProperty(activeCtm, "tilesWithNormal"));
        json.addProperty("ctmTilesWithSpecular", intProperty(activeCtm, "tilesWithSpecular"));
        json.addProperty("virtualCompatMaterials", intProperty(activeCtm, "uniqueTiles"));
        json.addProperty("virtualCompatMaterialsPresent", intProperty(activeCtm, "presentTiles"));
        json.addProperty("gpuResidentCompatMaterials", 0);
        json.addProperty("pendingCompatMaterials", intProperty(activeCtm, "presentTiles"));
        json.addProperty("missingDueToCapacity", 0);
        json.addProperty("capacityLimitAppliesToCompatMaterials", false);
        json.addProperty("admittedToVanillaAtlas",
            intProperty(ctmAtlasAdmissionJson(activeCtm), "admittedToVanillaAtlas"));
        return json;
    }

    private static int activeCtmPropertyFiles(JsonArray activePacks) {
        int count = 0;
        for (JsonElement packElement : activePacks) {
            JsonObject counts = object(packElement.getAsJsonObject(), "counts");
            count += intProperty(counts, "optifineCtmProperties");
            count += intProperty(counts, "mcpatcherCtmProperties");
        }
        return count;
    }

    private static JsonObject ctmRulesByMethod(JsonArray activePacks) {
        JsonObject methods = new JsonObject();
        for (String method : List.of("ctm", "ctm_compact", "random", "repeat", "fixed",
            "horizontal", "vertical", "top", "overlay", "overlay_ctm", "overlay_random",
            "overlay_repeat", "overlay_fixed")) {
            methods.addProperty(method, 0);
        }
        for (JsonElement packElement : activePacks) {
            JsonObject pack = packElement.getAsJsonObject();
            for (JsonElement recordElement : array(pack, "compatRecords")) {
                JsonObject record = recordElement.getAsJsonObject();
                if (!"ctm".equals(stringProperty(record, "kind"))) {
                    continue;
                }
                String method = stringProperty(record, "method");
                if (method.isBlank()) {
                    method = "unspecified";
                }
                methods.addProperty(method, intProperty(methods, method) + 1);
            }
        }
        return methods;
    }

    private static JsonObject renderSafetyJson() {
        JsonObject json = new JsonObject();
        json.addProperty("filtersEmissiveAuxiliaryTexturesFromAtlas", true);
        json.addProperty("filtersPbrAuxiliarySuffixes",
            ResourcePackTextureNames.PBR_AUXILIARY_SUFFIX_DESCRIPTION);
        json.addProperty("missingSpriteFallbackId", TextureArrayBridge.missingSpriteFallbackIdForTest());
        json.addProperty("missingSpriteFallbackSprite", TextureArrayBridge.missingSpriteFallbackLabel());
        json.addProperty("javaSpriteCount", TextureArrayBridge.sortedSpriteIds.size());
        json.addProperty("nativeRenderableSpriteCapacity", TextureArrayBridge.renderableSpriteCapacityForTest());
        int overflow = Math.max(0,
            TextureArrayBridge.sortedSpriteIds.size() - TextureArrayBridge.renderableSpriteCapacityForTest());
        json.addProperty("overflowSpriteCount", overflow);
        json.addProperty("overflowSpritesFallbackToMissingSafeSprite", true);
        return json;
    }

    private static void writeReports(Path runDirectory, JsonObject root) throws IOException {
        Path base = RadianceClient.radianceDir != null
            ? RadianceClient.radianceDir
            : runDirectory.resolve("radiance");
        Path logs = base.resolve("logs");
        Files.createDirectories(logs);

        JsonObject paths = new JsonObject();
        for (String file : DIAGNOSTIC_DUMP_FILES) {
            paths.addProperty(fileNameKey(file), logs.resolve(file).toAbsolutePath().toString());
        }
        root.add("diagnosticReports", paths);
        root.addProperty("reportPath", logs.resolve("radser-pack-index.json").toAbsolutePath().toString());

        Files.writeString(logs.resolve("radser-texture-assets.json"),
            GSON.toJson(textureAssetsDump(root)), StandardCharsets.UTF_8);
        Files.writeString(logs.resolve("radser-material-sets.json"),
            GSON.toJson(materialSetsDump(root)), StandardCharsets.UTF_8);
        Files.writeString(logs.resolve("radser-ctm-rules.json"),
            GSON.toJson(ctmRulesDump(root)), StandardCharsets.UTF_8);
        Files.writeString(logs.resolve("radser-rule-precedence.json"),
            GSON.toJson(rulePrecedenceDump(root)), StandardCharsets.UTF_8);
        Files.writeString(logs.resolve("radser-parser-warnings.json"),
            GSON.toJson(parserWarningsDump(root)), StandardCharsets.UTF_8);
        Files.writeString(logs.resolve("radser-pack-index.json"), GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static String fileNameKey(String fileName) {
        return switch (fileName) {
            case "radser-pack-index.json" -> "packIndex";
            case "radser-texture-assets.json" -> "textureAssets";
            case "radser-material-sets.json" -> "materialSets";
            case "radser-ctm-rules.json" -> "ctmRules";
            case "radser-rule-precedence.json" -> "rulePrecedence";
            case "radser-parser-warnings.json" -> "parserWarnings";
            default -> fileName.replace(".json", "");
        };
    }

    private static JsonObject textureAssetsDump(JsonObject root) {
        JsonArray active = array(root, "activePacks");
        JsonObject dump = baseDump("radser_texture_assets_v1", root);
        dump.addProperty("activePackCount", active.size());
        dump.add("aggregateCounts", aggregateCounts(active));
        dump.add("aggregateLabpbrCoverage", aggregateLabPbrCoverage(active));
        dump.add("activePacks", packAssetSummaries(active));
        return dump;
    }

    private static JsonObject materialSetsDump(JsonObject root) {
        JsonArray active = array(root, "activePacks");
        JsonObject dump = baseDump("radser_material_sets_v1", root);
        JsonObject source = new JsonObject();
        source.addProperty("materialFormat", "LabPBR");
        source.addProperty("specularSource", "direct_labpbr_specular_map");
        source.addProperty("normalSource", "direct_labpbr_normal_map");
        source.addProperty("scalarSpecularFallback", "roughness_or_metallic_sidecar_composed_to_labpbr_specular");
        source.addProperty("scalarNormalFallback", "height_or_ao_sidecar_composed_to_labpbr_normal");
        source.addProperty("ctmTileMaterialFallback",
            "selected_tile_sidecar_then_matchtiles_base_sidecar");
        source.addProperty("displacementSource", "direct_labpbr_normal_alpha");
        source.addProperty("displacementNormalization", "none");
        source.addProperty("heightAlphaRangeRole", "diagnostic_metadata_only");
        source.addProperty("materialSetBindingPolicy", AutoPbrTextureCatalog.MATERIAL_SET_BINDING_POLICY);
        source.addProperty("shaderLookupKey", AutoPbrTextureCatalog.MATERIAL_SET_SHADER_LOOKUP_KEY);
        source.addProperty("nativeMaterialSetTablePresent",
            AutoPbrTextureCatalog.MATERIAL_SET_NATIVE_TABLE_PRESENT);
        source.addProperty("materialSetAliasesResolvedSprite", true);
        dump.add("source", source);

        JsonObject aggregate = aggregateLabPbrCoverage(active);
        aggregate.addProperty("materialSetCandidates", intProperty(aggregate, "albedoBases"));
        aggregate.addProperty("completeLabpbrCandidates", intProperty(aggregate, "albedoWithSpecularAndNormal"));
        aggregate.addProperty("renderableLabpbrCandidates",
            intProperty(aggregate, "albedoWithSpecularOrScalarAndNormalOrScalar"));
        JsonObject ctm = object(root, "activeCtmAtlasDependencies");
        aggregate.addProperty("ctmTileMaterialCandidates", intProperty(ctm, "presentTilesRequiringAtlasAdmission"));
        aggregate.addProperty("ctmTileRenderableMaterialCandidates",
            intProperty(ctm, "tilesWithAnyMaterialSidecar"));
        dump.add("aggregate", aggregate);
        dump.add("activePacks", packMaterialSummaries(active));
        dump.add("renderSafety", copyObject(object(root, "renderSafety")));
        dump.add("materialRegistry", copyObject(object(root, "materialRegistry")));
        dump.add("materialRecordSamples", copyArray(array(root, "materialRecordSamples")));
        return dump;
    }

    private static JsonObject ctmRulesDump(JsonObject root) {
        JsonArray active = array(root, "activePacks");
        JsonObject dump = baseDump("radser_ctm_rules_v1", root);
        JsonArray rules = new JsonArray();
        JsonObject methodCounts = new JsonObject();
        for (JsonElement packElement : active) {
            JsonObject pack = packElement.getAsJsonObject();
            JsonArray records = array(pack, "compatRecords");
            for (JsonElement recordElement : records) {
                JsonObject record = recordElement.getAsJsonObject();
                if (!"ctm".equals(stringProperty(record, "kind"))) {
                    continue;
                }
                String method = stringProperty(record, "method");
                if (method.isEmpty()) {
                    method = "unspecified";
                }
                methodCounts.addProperty(method, intProperty(methodCounts, method) + 1);
                JsonObject rule = copyObject(record);
                addPackIdentity(rule, pack);
                rules.add(rule);
            }
        }
        dump.addProperty("ruleCount", rules.size());
        dump.add("methodCounts", methodCounts);
        dump.add("activeCtmAtlasDependencies", copyObject(object(root, "activeCtmAtlasDependencies")));
        dump.add("rules", rules);
        return dump;
    }

    private static JsonObject rulePrecedenceDump(JsonObject root) {
        JsonObject dump = baseDump("radser_rule_precedence_v1", root);
        JsonObject policy = new JsonObject();
        policy.addProperty("packPriority", "minecraft_active_resource_stack_order");
        policy.addProperty("ruleParsing", "java_side");
        policy.addProperty("quadResolution", "chunk_build_side");
        policy.addProperty("shaderInput", "global_material_id_with_sprite_array_compat_fallback");
        policy.addProperty("shaderLookupKey", AutoPbrTextureCatalog.MATERIAL_SET_SHADER_LOOKUP_KEY);
        policy.addProperty("nativeMaterialSetTablePresent",
            AutoPbrTextureCatalog.MATERIAL_SET_NATIVE_TABLE_PRESENT);
        policy.addProperty("legacyMcPatcher", "enabled_only_when_materialCompatLegacyMcPatcherEnabled");
        policy.addProperty("ctmTileAtlasAdmission", "disabled_by_default_debug_only_not_required_for_correctness");
        policy.addProperty("compatTileRenderability", "renderer_owned_material_pools_required_not_vanilla_atlas");
        dump.add("policy", policy);
        dump.add("activePackStack", copyObject(object(root, "activePackStack")));
        dump.add("materialUniverse", copyObject(object(root, "materialUniverse")));
        dump.add("materialRegistry", copyObject(object(root, "materialRegistry")));
        dump.add("compatibilityConsumption", copyObject(object(root, "compatibilityConsumption")));
        return dump;
    }

    private static JsonObject parserWarningsDump(JsonObject root) {
        JsonObject dump = baseDump("radser_parser_warnings_v1", root);
        JsonArray warnings = new JsonArray();
        for (JsonElement packElement : array(root, "packs")) {
            JsonObject pack = packElement.getAsJsonObject();
            if (pack.has("error")) {
                addWarning(warnings, "error", "pack_scan_error", pack, stringProperty(pack, "error"));
            }
            if (boolProperty(pack, "wrapperArchive")) {
                JsonObject warning = warning("warning", "wrapper_zip_resource_pack",
                    "This zip contains nested resource-pack archives and no root pack.mcmeta; Minecraft and RadSER will not load the inner packs from this wrapper.");
                addPackIdentity(warning, pack);
                warning.addProperty("nestedArchiveCount", intProperty(pack, "nestedArchiveCount"));
                warning.add("nestedArchives", array(pack, "nestedArchives").deepCopy());
                warnings.add(warning);
            }
            if (boolProperty(pack, "incompatibleSelected")) {
                addWarning(warnings, "warning", "selected_pack_marked_incompatible", pack,
                    "Minecraft options.txt marks this selected pack as incompatible.");
            }
            if (boolProperty(pack, "compatRecordsTruncated")) {
                addWarning(warnings, "warning", "compat_records_truncated", pack,
                    "The diagnostics record limit was reached for compatibility properties.");
            }
            JsonObject ctm = object(pack, "ctmAtlasDependencies");
            if (boolProperty(ctm, "truncated")) {
                addWarning(warnings, "warning", "ctm_dependencies_truncated", pack,
                    "The CTM dependency diagnostics record limit was reached.");
            }
        }

        JsonObject activeCtm = object(root, "activeCtmAtlasDependencies");
        if (intProperty(activeCtm, "missingTiles") > 0) {
            JsonObject warning = warning("warning", "active_ctm_missing_tiles",
                "Active CTM rules reference tiles that are not present in the selected packs.");
            warning.addProperty("missingTiles", intProperty(activeCtm, "missingTiles"));
            warnings.add(warning);
        }
        if (boolProperty(activeCtm, "truncated")) {
            warnings.add(warning("warning", "active_ctm_dependency_summary_truncated",
                "The active CTM dependency aggregate was truncated."));
        }
        JsonObject admission = object(root, "ctmAtlasAdmission");
        if (boolProperty(admission, "enabled")) {
            warnings.add(warning("warning", "ctm_atlas_admission_enabled",
                "CTM tile admission into Minecraft's vanilla atlas is enabled only as an explicit debug/capped path; renderer-owned material pools are the correctness path."));
        }
        JsonObject options = object(root, "optionsProvenance");
        if (boolProperty(options, "liveCompatibilityLikelyUnloadedFromPersistedOptions")) {
            warnings.add(warning("warning", "material_compat_live_options_disagree_with_persisted_options",
                "Persisted material compatibility options are enabled, but the live report flags are disabled."));
        } else if (boolProperty(options, "materialCompatFlagsMismatch")) {
            warnings.add(warning("warning", "material_compat_option_flag_mismatch",
                "Live material compatibility flags differ from persisted radiance/options.properties."));
        }
        dump.addProperty("warningCount", warnings.size());
        dump.add("warnings", warnings);
        return dump;
    }

    private static JsonObject baseDump(String schema, JsonObject root) {
        JsonObject dump = new JsonObject();
        dump.addProperty("schema", schema);
        dump.addProperty("createdAt", stringProperty(root, "createdAt"));
        dump.addProperty("minecraftVersion", stringProperty(root, "minecraftVersion"));
        dump.addProperty("packStackHash", stringProperty(root, "packStackHash"));
        dump.addProperty("runDirectory", stringProperty(root, "runDirectory"));
        dump.addProperty("renderingConsumesCompatibility", boolProperty(root, "renderingConsumesCompatibility"));
        dump.add("flags", copyObject(object(root, "flags")));
        dump.add("optionsProvenance", copyObject(object(root, "optionsProvenance")));
        dump.add("ctmAtlasAdmission", copyObject(object(root, "ctmAtlasAdmission")));
        return dump;
    }

    private static JsonObject optionsProvenanceJson(Path runDirectory) {
        JsonObject json = new JsonObject();
        JsonObject liveFlags = flagsJson();
        boolean liveConsumes = renderingConsumesCompatibility();
        json.addProperty("liveOptionsVersion", Options.optionsVersion);
        json.addProperty("currentOptionsVersion", Options.CURRENT_OPTIONS_VERSION);
        json.addProperty("liveRenderingConsumesCompatibility", liveConsumes);
        json.add("liveFlags", copyObject(liveFlags));

        Path optionsPath = optionsPropertiesPath(runDirectory);
        json.addProperty("persistedPath", optionsPath.toAbsolutePath().toString());
        json.addProperty("persistedPresent", Files.isRegularFile(optionsPath));
        if (!Files.isRegularFile(optionsPath)) {
            json.addProperty("persistedRenderingConsumesCompatibility", false);
            json.addProperty("materialCompatFlagsMismatch", false);
            json.addProperty("liveCompatibilityLikelyUnloadedFromPersistedOptions", false);
            json.addProperty("diagnosticState", "persisted_options_missing");
            return json;
        }

        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(optionsPath, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            json.addProperty("persistedReadError", e.getClass().getSimpleName() + ": " + e.getMessage());
            json.addProperty("persistedRenderingConsumesCompatibility", false);
            json.addProperty("materialCompatFlagsMismatch", false);
            json.addProperty("liveCompatibilityLikelyUnloadedFromPersistedOptions", false);
            json.addProperty("diagnosticState", "persisted_options_unreadable");
            return json;
        }

        json.addProperty("persistedOptionsVersion", parseInt(props.getProperty("optionsVersion"), -1));
        JsonObject persistedFlags = persistedFlagsJson(props);
        boolean persistedConsumes = persistedRenderingConsumesCompatibility(persistedFlags);
        boolean flagsMismatch = !materialCompatFlagsMatch(liveFlags, persistedFlags);
        json.add("persistedFlags", persistedFlags);
        json.addProperty("persistedRenderingConsumesCompatibility", persistedConsumes);
        json.addProperty("materialCompatFlagsMismatch", flagsMismatch);
        json.addProperty("liveCompatibilityLikelyUnloadedFromPersistedOptions", persistedConsumes && !liveConsumes);
        json.addProperty("diagnosticState", flagsMismatch ? "live_options_differ_from_persisted" : "live_options_match_persisted");
        return json;
    }

    private static Path optionsPropertiesPath(Path runDirectory) {
        if (RadianceClient.radianceDir != null) {
            return RadianceClient.radianceDir.resolve(Options.OPTION_PROPERTIES);
        }
        return runDirectory.resolve("radiance").resolve(Options.OPTION_PROPERTIES);
    }

    private static JsonObject persistedFlagsJson(Properties props) {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", Boolean.parseBoolean(props.getProperty("materialCompatEnabled", "false")));
        json.addProperty("ctm", Boolean.parseBoolean(props.getProperty("materialCompatCtmEnabled", "false")));
        json.addProperty("random", Boolean.parseBoolean(props.getProperty("materialCompatRandomEnabled", "false")));
        json.addProperty("natural", Boolean.parseBoolean(props.getProperty("materialCompatNaturalEnabled", "false")));
        json.addProperty("colors", Boolean.parseBoolean(props.getProperty("materialCompatColorsEnabled", "false")));
        json.addProperty("overlays", Boolean.parseBoolean(props.getProperty("materialCompatOverlaysEnabled", "false")));
        json.addProperty("legacyMcPatcher",
            Boolean.parseBoolean(props.getProperty("materialCompatLegacyMcPatcherEnabled", "false")));
        json.addProperty("physicalEmissive",
            Boolean.parseBoolean(props.getProperty("materialCompatPhysicalEmissiveEnabled", "false")));
        json.addProperty("ctmAtlasAdmissionLimit",
            Math.max(0, parseInt(props.getProperty("materialCompatCtmAtlasAdmissionLimit"), 0)));
        return json;
    }

    private static boolean persistedRenderingConsumesCompatibility(JsonObject flags) {
        return boolProperty(flags, "enabled")
            && (boolProperty(flags, "ctm")
                || boolProperty(flags, "random")
                || boolProperty(flags, "natural")
                || boolProperty(flags, "colors")
                || boolProperty(flags, "overlays")
                || boolProperty(flags, "physicalEmissive"));
    }

    private static boolean materialCompatFlagsMatch(JsonObject live, JsonObject persisted) {
        for (String key : List.of("enabled", "ctm", "random", "natural", "colors", "overlays",
            "legacyMcPatcher", "physicalEmissive")) {
            if (boolProperty(live, key) != boolProperty(persisted, key)) {
                return false;
            }
        }
        return intProperty(live, "ctmAtlasAdmissionLimit") == intProperty(persisted, "ctmAtlasAdmissionLimit");
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static JsonObject aggregateCounts(JsonArray packs) {
        JsonObject aggregate = new JsonObject();
        List<String> keys = List.of(
            "texturePng",
            "albedoPng",
            "specularMaps",
            "normalMaps",
            "roughnessScalarMaps",
            "metallicScalarMaps",
            "heightScalarMaps",
            "aoScalarMaps",
            "flag_f",
            "emissiveMaps",
            "properties",
            "textureProperties",
            "optifineCtmProperties",
            "mcpatcherCtmProperties",
            "emissiveProperties",
            "naturalProperties",
            "colorProperties",
            "blockColormapProperties",
            "lightmapProperties",
            "customLightmapPng",
            "blockProperties",
            "customAnimationEntries",
            "citEntries",
            "cemEntries",
            "randomEntityEntries");
        for (String key : keys) {
            int sum = 0;
            for (JsonElement element : packs) {
                sum += intProperty(object(element.getAsJsonObject(), "counts"), key);
            }
            aggregate.addProperty(key, sum);
        }
        return aggregate;
    }

    private static JsonObject aggregateLabPbrCoverage(JsonArray packs) {
        JsonObject aggregate = new JsonObject();
        List<String> keys = List.of(
            "albedoBases",
            "specularBases",
            "normalBases",
            "roughnessBases",
            "metallicBases",
            "heightBases",
            "aoBases",
            "flagBases",
            "emissiveBases",
            "albedoWithSpecular",
            "albedoWithNormal",
            "albedoWithSpecularAndNormal",
            "albedoWithSpecularOrScalar",
            "albedoWithNormalOrScalar",
            "albedoWithSpecularOrScalarAndNormalOrScalar",
            "orphanSpecular",
            "orphanNormal",
            "orphanRoughness",
            "orphanMetallic",
            "orphanHeight",
            "orphanAo",
            "orphanEmissive");
        for (String key : keys) {
            int sum = 0;
            for (JsonElement element : packs) {
                sum += intProperty(object(element.getAsJsonObject(), "labpbrCoverage"), key);
            }
            aggregate.addProperty(key, sum);
        }
        return aggregate;
    }

    private static JsonArray packAssetSummaries(JsonArray packs) {
        JsonArray summaries = new JsonArray();
        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            JsonObject summary = new JsonObject();
            addPackIdentity(summary, pack);
            summary.add("counts", copyObject(object(pack, "counts")));
            summary.add("labpbrCoverage", copyObject(object(pack, "labpbrCoverage")));
            summary.add("compatFeatures", copyObject(object(pack, "compatFeatures")));
            summaries.add(summary);
        }
        return summaries;
    }

    private static JsonArray packMaterialSummaries(JsonArray packs) {
        JsonArray summaries = new JsonArray();
        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            JsonObject coverage = object(pack, "labpbrCoverage");
            JsonObject summary = new JsonObject();
            addPackIdentity(summary, pack);
            summary.addProperty("materialSetCandidates", intProperty(coverage, "albedoBases"));
            summary.addProperty("completeLabpbrCandidates", intProperty(coverage, "albedoWithSpecularAndNormal"));
            summary.addProperty("renderableLabpbrCandidates",
                intProperty(coverage, "albedoWithSpecularOrScalarAndNormalOrScalar"));
            summary.addProperty("orphanSpecular", intProperty(coverage, "orphanSpecular"));
            summary.addProperty("orphanNormal", intProperty(coverage, "orphanNormal"));
            summary.addProperty("orphanScalarSpecular",
                intProperty(coverage, "orphanRoughness") + intProperty(coverage, "orphanMetallic"));
            summary.addProperty("orphanScalarNormal",
                intProperty(coverage, "orphanHeight") + intProperty(coverage, "orphanAo"));
            summary.add("labpbrCoverage", copyObject(coverage));
            summaries.add(summary);
        }
        return summaries;
    }

    private static void addPackIdentity(JsonObject target, JsonObject pack) {
        target.addProperty("packName", stringProperty(pack, "name"));
        target.addProperty("packPath", stringProperty(pack, "path"));
        target.addProperty("activeReference", stringProperty(pack, "activeReference"));
        target.addProperty("activeOrder", intProperty(pack, "activeOrder"));
        target.addProperty("activePriority", intProperty(pack, "activePriority"));
        target.addProperty("incompatibleSelected", boolProperty(pack, "incompatibleSelected"));
    }

    private static void addWarning(JsonArray warnings, String severity, String code, JsonObject pack, String detail) {
        JsonObject warning = warning(severity, code, detail);
        addPackIdentity(warning, pack);
        warnings.add(warning);
    }

    private static JsonObject warning(String severity, String code, String detail) {
        JsonObject warning = new JsonObject();
        warning.addProperty("severity", severity);
        warning.addProperty("code", code);
        warning.addProperty("detail", detail);
        return warning;
    }

    private static JsonObject copyObject(JsonObject source) {
        return source == null ? new JsonObject() : source.deepCopy();
    }

    private static JsonArray copyArray(JsonArray source) {
        return source == null ? new JsonArray() : source.deepCopy();
    }

    private static JsonObject object(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonObject()) {
            return new JsonObject();
        }
        return parent.getAsJsonObject(name);
    }

    private static JsonArray array(JsonObject parent, String name) {
        if (parent == null || !parent.has(name) || !parent.get(name).isJsonArray()) {
            return new JsonArray();
        }
        return parent.getAsJsonArray(name);
    }

    private static int intProperty(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) {
            return 0;
        }
        try {
            return object.get(name).getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static boolean boolProperty(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) {
            return false;
        }
        try {
            return object.get(name).getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String stringProperty(JsonObject object, String name) {
        if (object == null || !object.has(name) || !object.get(name).isJsonPrimitive()) {
            return "";
        }
        try {
            return object.get(name).getAsString();
        } catch (RuntimeException ignored) {
            return "";
        }
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
                    report.addProperty("activeOrder", active ? activeSelection.activeOrder(fileName) : -1);
                    report.addProperty("activePriority", active ? activeSelection.activePriority(fileName) : -1);
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
        ArrayList<JsonObject> ordered = new ArrayList<>();
        int fallbackOrder = 0;
        for (JsonElement element : packs) {
            JsonObject pack = element.getAsJsonObject();
            if (pack.has("active") && pack.get("active").getAsBoolean()) {
                if (!pack.has("activeOrder")) {
                    pack.addProperty("activeOrder", fallbackOrder);
                }
                ordered.add(pack);
                fallbackOrder++;
            }
        }
        ordered.sort(Comparator
            .comparingInt((JsonObject pack) -> intProperty(pack, "activeOrder"))
            .thenComparing(pack -> stringProperty(pack, "name")));
        JsonArray active = new JsonArray();
        for (JsonObject pack : ordered) {
            active.add(pack);
        }
        return active;
    }

    private static JsonObject aggregateCtmDependencies(JsonArray activePacks) {
        LinkedHashMap<String, JsonObject> dependenciesByPath = new LinkedHashMap<>();
        boolean useSummaryTotals = false;
        boolean truncated = false;
        int totalUnique = 0;
        int totalPresent = 0;
        int totalMissing = 0;
        int totalWithSpecular = 0;
        int totalWithNormal = 0;
        int totalWithDerivedSpecular = 0;
        int totalWithDerivedNormal = 0;
        int totalWithAnyMaterialSidecar = 0;
        int totalWithEmissive = 0;
        int totalRequiringAtlasAdmission = 0;
        int totalPresentRequiringAtlasAdmission = 0;
        for (JsonElement packElement : activePacks) {
            JsonObject pack = packElement.getAsJsonObject();
            JsonObject summary = pack.getAsJsonObject("ctmAtlasDependencies");
            if (summary == null) {
                continue;
            }
            boolean summaryTruncated = boolProperty(summary, "truncated");
            useSummaryTotals |= summaryTruncated;
            totalUnique += intProperty(summary, "uniqueTiles");
            totalPresent += intProperty(summary, "presentTiles");
            totalMissing += intProperty(summary, "missingTiles");
            totalWithSpecular += intProperty(summary, "tilesWithSpecular");
            totalWithNormal += intProperty(summary, "tilesWithNormal");
            totalWithDerivedSpecular += intProperty(summary, "tilesWithSpecularOrScalar");
            totalWithDerivedNormal += intProperty(summary, "tilesWithNormalOrScalar");
            totalWithAnyMaterialSidecar += intProperty(summary, "tilesWithAnyMaterialSidecar");
            totalWithEmissive += intProperty(summary, "tilesWithEmissive");
            totalRequiringAtlasAdmission += intProperty(summary, "tilesRequiringAtlasAdmission");
            totalPresentRequiringAtlasAdmission += intProperty(summary, "presentTilesRequiringAtlasAdmission");
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
            truncated |= summaryTruncated;
        }

        int present = 0;
        int missing = 0;
        int withSpecular = 0;
        int withNormal = 0;
        int withDerivedSpecular = 0;
        int withDerivedNormal = 0;
        int withAnyMaterialSidecar = 0;
        int withEmissive = 0;
        int requiringAtlasAdmission = 0;
        int presentRequiringAtlasAdmission = 0;
        JsonArray dependencies = new JsonArray();
        for (JsonObject dependency : dependenciesByPath.values()) {
            if (dependency.get("present").getAsBoolean()) present++; else missing++;
            if (dependency.get("specularPresent").getAsBoolean()) withSpecular++;
            if (dependency.get("normalPresent").getAsBoolean()) withNormal++;
            if (boolProperty(dependency, "derivedSpecularPresent")) withDerivedSpecular++;
            if (boolProperty(dependency, "derivedNormalPresent")) withDerivedNormal++;
            if (boolProperty(dependency, "anyMaterialSidecarPresent")) withAnyMaterialSidecar++;
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
        json.addProperty("aggregationMode", useSummaryTotals
            ? "summary_totals_due_to_truncated_dependency_details"
            : "deduplicated_dependency_paths");
        json.addProperty("deduplicated", !useSummaryTotals);
        json.addProperty("uniqueTiles", useSummaryTotals ? totalUnique : dependenciesByPath.size());
        json.addProperty("presentTiles", useSummaryTotals ? totalPresent : present);
        json.addProperty("missingTiles", useSummaryTotals ? totalMissing : missing);
        json.addProperty("tilesWithSpecular", useSummaryTotals ? totalWithSpecular : withSpecular);
        json.addProperty("tilesWithNormal", useSummaryTotals ? totalWithNormal : withNormal);
        json.addProperty("tilesWithSpecularOrScalar",
            useSummaryTotals ? totalWithDerivedSpecular : withDerivedSpecular);
        json.addProperty("tilesWithNormalOrScalar",
            useSummaryTotals ? totalWithDerivedNormal : withDerivedNormal);
        json.addProperty("tilesWithAnyMaterialSidecar",
            useSummaryTotals ? totalWithAnyMaterialSidecar : withAnyMaterialSidecar);
        json.addProperty("tilesWithEmissive", useSummaryTotals ? totalWithEmissive : withEmissive);
        json.addProperty("tilesRequiringAtlasAdmission",
            useSummaryTotals ? totalRequiringAtlasAdmission : requiringAtlasAdmission);
        json.addProperty("presentTilesRequiringAtlasAdmission",
            useSummaryTotals ? totalPresentRequiringAtlasAdmission : presentRequiringAtlasAdmission);
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

    private static String stableHash(List<String> values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            byte[] bytes = digest.digest();
            StringBuilder out = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                out.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static final class ActivePackSelection {
        private final List<String> resourcePacks;
        private final List<String> incompatibleResourcePacks;
        private final Set<String> activeFilePackNames = new HashSet<>();
        private final Map<String, Integer> activeFilePackOrder = new LinkedHashMap<>();
        private final Set<String> incompatibleFilePackNames = new HashSet<>();
        private final String source;

        ActivePackSelection(List<String> resourcePacks, List<String> incompatibleResourcePacks, String source) {
            this.resourcePacks = resourcePacks;
            this.incompatibleResourcePacks = incompatibleResourcePacks;
            this.source = source;
            for (int i = 0; i < resourcePacks.size(); i++) {
                String entry = resourcePacks.get(i);
                String fileName = filePackName(entry);
                if (!fileName.isEmpty()) {
                    activeFilePackNames.add(fileName);
                    activeFilePackOrder.putIfAbsent(fileName, i);
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
            json.addProperty("packStackHash", packStackHash());
            json.add("resourcePacks", stringArray(resourcePacks));
            json.add("incompatibleResourcePacks", stringArray(incompatibleResourcePacks));
            json.add("activeFilePackNames", stringArray(activeFilePackNames.stream().sorted().toList()));
            JsonArray ordered = new JsonArray();
            activeFilePackOrder.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(entry -> {
                    JsonObject item = new JsonObject();
                    item.addProperty("name", entry.getKey());
                    item.addProperty("activeOrder", entry.getValue());
                    item.addProperty("activePriority", activePriority(entry.getKey()));
                    ordered.add(item);
                });
            json.add("activeFilePackOrder", ordered);
            json.add("incompatibleFilePackNames", stringArray(incompatibleFilePackNames.stream().sorted().toList()));
            return json;
        }

        int activeOrder(String fileName) {
            return activeFilePackOrder.getOrDefault(fileName, Integer.MAX_VALUE);
        }

        int activePriority(String fileName) {
            int order = activeOrder(fileName);
            return order == Integer.MAX_VALUE ? -1 : order;
        }

        String packStackHash() {
            ArrayList<String> values = new ArrayList<>();
            values.add("resources");
            values.addAll(resourcePacks);
            values.add("incompatible");
            values.addAll(incompatibleResourcePacks);
            return stableHash(values);
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
        private final Set<String> roughnessBases = new HashSet<>();
        private final Set<String> metallicBases = new HashSet<>();
        private final Set<String> heightBases = new HashSet<>();
        private final Set<String> aoBases = new HashSet<>();
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
        private int roughnessPng;
        private int metallicPng;
        private int heightPng;
        private int aoPng;
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
        private int customLightmapPng;
        private int blockProperties;
        private int customAnimationEntries;
        private int citEntries;
        private int cemEntries;
        private int randomEntityEntries;
        private int properties;
        private int packMcmeta;
        private int rootPackMcmeta;
        private int nestedArchiveCount;
        private boolean compatRecordsTruncated;
        private boolean ctmDependenciesTruncated;
        private boolean ctmDependenciesFinalized;
        private final JsonArray nestedArchives = new JsonArray();

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
            if ("pack.mcmeta".equals(lower)) rootPackMcmeta++;
            if (lower.endsWith(".zip")) {
                nestedArchiveCount++;
                if (nestedArchives.size() < sampleLimit) {
                    nestedArchives.add(name);
                }
            }
            if (lower.endsWith(".png") && lower.contains("/textures/")) {
                texturePng++;
                String base = textureBase(lower);
                if (ResourcePackTextureNames.isSpecularAuxiliaryPath(lower)) {
                    specularPng++;
                    specularBases.add(base);
                } else if (ResourcePackTextureNames.isNormalAuxiliaryPath(lower)) {
                    normalPng++;
                    normalBases.add(base);
                } else if (ResourcePackTextureNames.isRoughnessScalarPath(lower)) {
                    roughnessPng++;
                    roughnessBases.add(base);
                } else if (ResourcePackTextureNames.isMetallicScalarPath(lower)) {
                    metallicPng++;
                    metallicBases.add(base);
                } else if (ResourcePackTextureNames.isHeightScalarPath(lower)) {
                    heightPng++;
                    heightBases.add(base);
                } else if (ResourcePackTextureNames.isAoScalarPath(lower)) {
                    aoPng++;
                    aoBases.add(base);
                } else if (ResourcePackTextureNames.isFlagAuxiliaryPath(lower)) {
                    flagPng++;
                    flagBases.add(base);
                } else if (ResourcePackTextureNames.isEmissiveAuxiliaryPath(lower)) {
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
            if ((lower.contains("/optifine/lightmap/") || lower.contains("/mcpatcher/lightmap/"))
                && lower.endsWith(".png")) customLightmapPng++;
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
            if (lower.endsWith("lightmap.properties")) return "lightmap_properties";
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
            addIfPresent(record, props, "optifineOnly");
            addIfPresent(record, props, "layer.cutout");
            addIfPresent(record, props, "layer.cutout_mipped");
            addIfPresent(record, props, "layer.translucent");
            addIfPresent(record, props, "disableSolidCheck");
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
                int repeatWidth = parseInt(record.has("width") ? record.get("width").getAsString() : null, 1);
                int repeatHeight = parseInt(record.has("height") ? record.get("height").getAsString() : null, 1);
                String propertyPath = record.has("path") ? record.get("path").getAsString() : "";
                List<String> dependencyPaths =
                    ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
                        propertyPath, tiles, method, repeatWidth, repeatHeight);
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
            Identifier resourceId = ResourcePackCompatCtmTiles.resourceIdentifierFromAssetPath(path);
            String specularPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_s");
            boolean specularPresent = sidecarPresent(path, "_s", "_spec", "_specular");
            String normalPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_n");
            boolean normalPresent = sidecarPresent(path, "_n", "_normal", "_norm");
            String roughnessPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_roughness");
            boolean roughnessPresent = sidecarPresent(path, "_roughness", "_rough");
            String metallicPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_metallic");
            boolean metallicPresent = sidecarPresent(path, "_metallic", "_metalness");
            String heightPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_height");
            boolean heightPresent = sidecarPresent(path, "_height", "_displacement", "_disp");
            String aoPath = ResourcePackCompatCtmTiles.sidecarPath(path, "_ao");
            boolean aoPresent = sidecarPresent(path, "_ao", "_ambientocclusion", "_ambient_occlusion");
            boolean derivedSpecularPresent = specularPresent || roughnessPresent || metallicPresent;
            boolean derivedNormalPresent = normalPresent || heightPresent || aoPresent;
            return new CtmDependency(
                path,
                resourceId == null ? "" : resourceId.toString(),
                ResourcePackCompatCtmTiles.atlasSpriteIdentifier(path),
                ResourcePackCompatCtmTiles.requiresAtlasAdmission(path),
                entryNames.contains(lower),
                specularPath,
                specularPresent,
                normalPath,
                normalPresent,
                roughnessPath,
                roughnessPresent,
                metallicPath,
                metallicPresent,
                heightPath,
                heightPresent,
                aoPath,
                aoPresent,
                derivedSpecularPresent,
                derivedNormalPresent,
                derivedSpecularPresent || derivedNormalPresent,
                ResourcePackCompatCtmTiles.sidecarPath(path, "_f"),
                sidecarPresent(path, "_f"),
                ResourcePackCompatCtmTiles.sidecarPath(path, "_e"),
                sidecarPresent(path, "_e"));
        }

        private boolean sidecarPresent(String path, String... suffixes) {
            for (String suffix : suffixes) {
                String sidecar = ResourcePackCompatCtmTiles.sidecarPath(path, suffix);
                if (entryNames.contains(sidecar.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
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
            json.addProperty("rootPackMcmeta", rootPackMcmeta);
            json.addProperty("nestedPackMcmeta", Math.max(0, packMcmeta - rootPackMcmeta));
            json.addProperty("nestedArchiveCount", nestedArchiveCount);
            json.addProperty("wrapperArchive", wrapperArchive());
            json.addProperty("packLayout", packLayout());
            json.add("nestedArchives", nestedArchives);
            JsonObject counts = new JsonObject();
            counts.addProperty("texturePng", texturePng);
            counts.addProperty("albedoPng", albedoPng);
            counts.addProperty("specularMaps", specularPng);
            counts.addProperty("normalMaps", normalPng);
            counts.addProperty("roughnessScalarMaps", roughnessPng);
            counts.addProperty("metallicScalarMaps", metallicPng);
            counts.addProperty("heightScalarMaps", heightPng);
            counts.addProperty("aoScalarMaps", aoPng);
            counts.addProperty("flag_f", flagPng);
            counts.addProperty("emissiveMaps", emissivePng);
            counts.addProperty("properties", properties);
            counts.addProperty("textureProperties", textureProperties);
            counts.addProperty("optifineCtmProperties", optifineCtmProperties);
            counts.addProperty("mcpatcherCtmProperties", mcpatcherCtmProperties);
            counts.addProperty("emissiveProperties", emissiveProperties);
            counts.addProperty("naturalProperties", naturalProperties);
            counts.addProperty("colorProperties", colorProperties);
            counts.addProperty("blockColormapProperties", blockColormapProperties);
            counts.addProperty("lightmapProperties", lightmapProperties);
            counts.addProperty("customLightmapPng", customLightmapPng);
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

        private boolean wrapperArchive() {
            return "zip".equals(kind) && rootPackMcmeta == 0 && nestedArchiveCount > 0;
        }

        private String packLayout() {
            if (!"zip".equals(kind)) {
                return rootPackMcmeta > 0 ? "directory_resource_pack" : "directory_without_root_pack_mcmeta";
            }
            if (rootPackMcmeta > 0) {
                return "root_resource_pack";
            }
            if (nestedArchiveCount > 0) {
                return "nested_archive_wrapper";
            }
            if (packMcmeta > 0) {
                return "nested_directory_wrapper";
            }
            return "zip_without_root_pack_mcmeta";
        }

        private JsonObject ctmAtlasDependenciesJson() {
            JsonObject json = new JsonObject();
            int present = 0;
            int missing = 0;
            int withSpecular = 0;
            int withNormal = 0;
            int withDerivedSpecular = 0;
            int withDerivedNormal = 0;
            int withAnyMaterialSidecar = 0;
            int withEmissive = 0;
            int requiringAtlasAdmission = 0;
            int presentRequiringAtlasAdmission = 0;
            JsonArray dependencies = new JsonArray();
            for (CtmDependency dependency : ctmDependencies.values()) {
                if (dependency.present) present++; else missing++;
                if (dependency.specularPresent) withSpecular++;
                if (dependency.normalPresent) withNormal++;
                if (dependency.derivedSpecularPresent) withDerivedSpecular++;
                if (dependency.derivedNormalPresent) withDerivedNormal++;
                if (dependency.anyMaterialSidecarPresent) withAnyMaterialSidecar++;
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
            json.addProperty("tilesWithSpecularOrScalar", withDerivedSpecular);
            json.addProperty("tilesWithNormalOrScalar", withDerivedNormal);
            json.addProperty("tilesWithAnyMaterialSidecar", withAnyMaterialSidecar);
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
            Set<String> specularOrScalarBases = merged(specularBases, roughnessBases, metallicBases);
            Set<String> normalOrScalarBases = merged(normalBases, heightBases, aoBases);
            json.addProperty("albedoBases", albedoBases.size());
            json.addProperty("specularBases", specularBases.size());
            json.addProperty("normalBases", normalBases.size());
            json.addProperty("roughnessBases", roughnessBases.size());
            json.addProperty("metallicBases", metallicBases.size());
            json.addProperty("heightBases", heightBases.size());
            json.addProperty("aoBases", aoBases.size());
            json.addProperty("flagBases", flagBases.size());
            json.addProperty("emissiveBases", emissiveBases.size());
            json.addProperty("albedoWithSpecular", intersectionCount(albedoBases, specularBases));
            json.addProperty("albedoWithNormal", intersectionCount(albedoBases, normalBases));
            json.addProperty("albedoWithSpecularAndNormal", tripleIntersectionCount(albedoBases, specularBases, normalBases));
            json.addProperty("albedoWithSpecularOrScalar", intersectionCount(albedoBases, specularOrScalarBases));
            json.addProperty("albedoWithNormalOrScalar", intersectionCount(albedoBases, normalOrScalarBases));
            json.addProperty("albedoWithSpecularOrScalarAndNormalOrScalar",
                tripleIntersectionCount(albedoBases, specularOrScalarBases, normalOrScalarBases));
            json.addProperty("orphanSpecular", orphanCount(specularBases, albedoBases));
            json.addProperty("orphanNormal", orphanCount(normalBases, albedoBases));
            json.addProperty("orphanRoughness", orphanCount(roughnessBases, albedoBases));
            json.addProperty("orphanMetallic", orphanCount(metallicBases, albedoBases));
            json.addProperty("orphanHeight", orphanCount(heightBases, albedoBases));
            json.addProperty("orphanAo", orphanCount(aoBases, albedoBases));
            json.addProperty("orphanEmissive", orphanCount(emissiveBases, albedoBases));
            return json;
        }

        private JsonObject compatFeaturesJson() {
            JsonObject json = new JsonObject();
            compatFeatureCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> json.addProperty(entry.getKey(), entry.getValue()));
            if (customLightmapPng > 0) {
                json.addProperty("custom_lightmap_images", customLightmapPng);
            }
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

        private Set<String> merged(Set<String> first, Set<String> second, Set<String> third) {
            LinkedHashSet<String> values = new LinkedHashSet<>(first);
            values.addAll(second);
            values.addAll(third);
            return values;
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
            for (String suffix : ResourcePackTextureNames.PBR_AUXILIARY_SUFFIX_DESCRIPTION.split(",")) {
                if (base.endsWith(suffix)) {
                    return base.substring(0, base.length() - suffix.length());
                }
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
                                 String roughnessPath,
                                 boolean roughnessPresent,
                                 String metallicPath,
                                 boolean metallicPresent,
                                 String heightPath,
                                 boolean heightPresent,
                                 String aoPath,
                                 boolean aoPresent,
                                 boolean derivedSpecularPresent,
                                 boolean derivedNormalPresent,
                                 boolean anyMaterialSidecarPresent,
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
            json.addProperty("anyMaterialSidecarPresent", anyMaterialSidecarPresent);
            json.addProperty("flagPath", flagPath);
            json.addProperty("flagPresent", flagPresent);
            json.addProperty("emissivePath", emissivePath);
            json.addProperty("emissivePresent", emissivePresent);
            return json;
        }
    }
}
