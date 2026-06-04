package com.radiance.client.autopbr;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.radiance.client.materiallab.MaterialBakePlan;
import com.radiance.client.materiallab.MaterialLabStore;
import com.radiance.client.materiallab.MaterialRecipe;
import com.radiance.client.materiallab.MaterialRecipeCompiler;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.option.Options;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AutoPbrRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoPBR");
    private static final Gson GSON = new Gson();
    private static volatile RehydrateReport lastRehydrateReport = new RehydrateReport(0, 0, 0, 0);
    private static volatile long lastRehydrateAtMillis = 0L;

    private AutoPbrRuntime() {
    }

    public static RehydrateReport rehydrateSavedSidecars(MinecraftClient client) {
        if (!Options.autoPBREnabled) {
            AutoPbrTextureRules.disableAll();
            RehydrateReport report = new RehydrateReport(0, 0, 0, 0);
            lastRehydrateReport = report;
            lastRehydrateAtMillis = System.currentTimeMillis();
            LOGGER.info("[AutoPBR] Disabled; skipped sidecar rehydrate and cleared texture rules");
            return report;
        }
        List<Identifier> saved = MaterialLabStore.savedSprites(client);
        int applied = 0;
        int skipped = 0;
        int failed = 0;
        AutoPbrTextureRules.clear();

        for (Identifier sprite : saved) {
            int spriteId = TextureArrayBridge.sortedSpriteIds.indexOf(sprite);
            if (spriteId < 0) {
                skipped++;
                LOGGER.debug("[AutoPBR] Sidecar sprite {} is not in the current block atlas", sprite);
                continue;
            }
            MaterialRecipe recipe = MaterialLabStore.loadRecipe(client, sprite);
            if (recipe == null || recipe.isDefaultIntent()) {
                skipped++;
                continue;
            }
            MaterialBakePlan result = MaterialRecipeCompiler.compileAndUpload(spriteId, recipe);
            if (result != null && result.ok) {
                applied++;
            } else {
                failed++;
                String diagnostic = result == null || result.diagnostics.isEmpty()
                    ? "unknown compile failure"
                    : result.diagnostics.get(0);
                LOGGER.warn("[AutoPBR] Failed to rehydrate {}: {}", sprite, diagnostic);
            }
        }

        AutoPbrTextureRules.upload();
        RehydrateReport report = new RehydrateReport(saved.size(), applied, skipped, failed);
        lastRehydrateReport = report;
        lastRehydrateAtMillis = System.currentTimeMillis();
        if (saved.isEmpty()) {
            LOGGER.debug("[MaterialLab] No saved recipes to rehydrate");
        } else {
            LOGGER.info("[MaterialLab] Rehydrated recipes: {}", report);
        }
        return report;
    }

    public static RehydrateReport lastRehydrateReport() {
        return lastRehydrateReport;
    }

    public static long lastRehydrateAtMillis() {
        return lastRehydrateAtMillis;
    }

    public static int textureRuleFlags(int spriteId) {
        return AutoPbrTextureRules.flagsForTest(spriteId);
    }

    public static String textureRuleFlagsLabel(int flags) {
        if (flags == 0) return "none";
        StringJoiner joiner = new StringJoiner("|");
        if ((flags & AutoPbrTextureRules.FLAG_ENABLED) != 0) joiner.add("enabled");
        if ((flags & AutoPbrTextureRules.FLAG_TRANSMISSION) != 0) joiner.add("transmission");
        if ((flags & AutoPbrTextureRules.FLAG_IOR) != 0) joiner.add("ior");
        if ((flags & AutoPbrTextureRules.FLAG_METALLIC) != 0) joiner.add("metallic");
        if ((flags & AutoPbrTextureRules.FLAG_F0) != 0) joiner.add("f0");
        if ((flags & AutoPbrTextureRules.FLAG_CONDUCTOR_F0_RGB) != 0) joiner.add("conductorF0Rgb");
        if ((flags & AutoPbrTextureRules.FLAG_ROUGHNESS) != 0) joiner.add("roughness");
        if ((flags & AutoPbrTextureRules.FLAG_ANISOTROPIC) != 0) joiner.add("anisotropic");
        if ((flags & AutoPbrTextureRules.FLAG_SHEEN) != 0) joiner.add("sheen");
        if ((flags & AutoPbrTextureRules.FLAG_COAT) != 0) joiner.add("coat");
        if ((flags & AutoPbrTextureRules.FLAG_EMISSION) != 0) joiner.add("emission");
        if ((flags & AutoPbrTextureRules.FLAG_ABSORPTION) != 0) joiner.add("absorption");
        if ((flags & AutoPbrTextureRules.FLAG_THICKNESS) != 0) joiner.add("thickness");
        if ((flags & AutoPbrTextureRules.FLAG_VOLUME_MODE) != 0) joiner.add("volumeMode");
        if ((flags & AutoPbrTextureRules.FLAG_REFRACTION_ROUGHNESS) != 0) joiner.add("refractionRoughness");
        if ((flags & AutoPbrTextureRules.FLAG_EMISSION_TINT_NITS) != 0) joiner.add("emissionTintNits");
        if ((flags & AutoPbrTextureRules.FLAG_ANISOTROPIC_ROTATION) != 0) joiner.add("anisotropicRotation");
        if ((flags & AutoPbrTextureRules.FLAG_COAT_EXT) != 0) joiner.add("coatExt");
        if ((flags & AutoPbrTextureRules.FLAG_SHEEN_ROUGHNESS) != 0) joiner.add("sheenRoughness");
        if ((flags & AutoPbrTextureRules.FLAG_UV_TRANSFORM) != 0) joiner.add("uvTransform");
        if ((flags & AutoPbrTextureRules.FLAG_FILTER_RADIUS) != 0) joiner.add("filterRadius");
        if ((flags & AutoPbrTextureRules.FLAG_MIP_BIAS) != 0) joiner.add("mipBias");
        if ((flags & AutoPbrTextureRules.FLAG_DISPLACEMENT_SCALE) != 0) joiner.add("displacementScale");
        return joiner.toString();
    }

    public static String textureRuleSummary(int spriteId) {
        int flags = textureRuleFlags(spriteId);
        return "flags=0x" + Integer.toHexString(flags) + " " + textureRuleFlagsLabel(flags);
    }

    public static String spriteDiagnostics(int spriteId) {
        AutoPbrTextureCatalog.SpriteProvenance provenance =
            AutoPbrTextureCatalog.spriteProvenance(spriteId);
        AutoPbrTextureCatalog.DisplacementEligibility displacement =
            AutoPbrTextureCatalog.displacementEligibility(spriteId);
        return provenance.compact()
            + " textureRule{" + textureRuleSummary(spriteId) + "}"
            + " displacement{" + displacement.compact() + "}";
    }

    public static String materialAuditJson(String selector) {
        MinecraftClient client = MinecraftClient.getInstance();
        int spriteId = resolveAuditSpriteId(selector);
        JsonObject root = new JsonObject();
        root.addProperty("ok", spriteId >= 0 && spriteId < TextureArrayBridge.sortedSpriteIds.size());
        root.addProperty("selector", selector == null ? "" : selector);
        root.addProperty("spriteId", spriteId);
        root.addProperty("textureGeneration", TextureArrayBridge.getActiveTextureGeneration());
        if (spriteId < 0 || spriteId >= TextureArrayBridge.sortedSpriteIds.size()) {
            root.addProperty("error", "unknown sprite");
            return GSON.toJson(root);
        }

        Identifier sprite = TextureArrayBridge.sortedSpriteIds.get(spriteId);
        MaterialRecipe recipe = MaterialLabStore.loadRecipe(client, sprite);
        MaterialBakePlan evaluation = MaterialRecipeCompiler.evaluate(spriteId, recipe);
        AutoPbrTextureCatalog.SpriteProvenance provenance = AutoPbrTextureCatalog.spriteProvenance(spriteId);
        AutoPbrTextureCatalog.DisplacementEligibility displacement =
            AutoPbrTextureCatalog.displacementEligibility(spriteId);
        Path profilePath = client == null ? Path.of(".") : MaterialLabStore.profilePath(client);

        root.addProperty("sprite", sprite.toString());
        root.add("graph", deprecatedGraphSummary());
        root.add("rules", recipeSummary(recipe, profilePath));
        root.add("channels", channelSummary(sprite, spriteId, evaluation));
        root.add("textureRule", textureRuleJson(spriteId));
        root.add("nativeTexture", nativeTextureJson(spriteId));
        root.add("shaderReachability", shaderReachabilityJson(spriteId, evaluation));
        root.add("displacement", displacementJson(displacement));
        return GSON.toJson(root);
    }

    public static String materialStressAuditJson(int limit, boolean compileUpload) {
        MinecraftClient client = MinecraftClient.getInstance();
        int spriteCount = TextureArrayBridge.sortedSpriteIds.size();
        int max = Math.max(1, Math.min(limit <= 0 ? 32 : limit, spriteCount));
        Set<Integer> spriteIds = new LinkedHashSet<>();
        for (Identifier saved : MaterialLabStore.savedSprites(client)) {
            int id = AutoPbrTextureCatalog.spriteIndex(saved);
            if (id >= 0) spriteIds.add(id);
            if (spriteIds.size() >= max) break;
        }
        for (int i = 0; i < spriteCount && spriteIds.size() < max; i++) {
            spriteIds.add(i);
        }

        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        int ok = 0;
        int failed = 0;
        for (int spriteId : spriteIds) {
            Identifier sprite = TextureArrayBridge.sortedSpriteIds.get(spriteId);
            MaterialRecipe recipe = MaterialLabStore.loadRecipe(client, sprite);
            MaterialBakePlan result = compileUpload
                ? MaterialRecipeCompiler.compileAndUpload(spriteId, recipe)
                : MaterialRecipeCompiler.evaluate(spriteId, recipe);
            JsonObject item = new JsonObject();
            item.addProperty("spriteId", spriteId);
            item.addProperty("sprite", sprite.toString());
            item.addProperty("ok", result != null && result.ok);
            item.addProperty("compiled", compileUpload);
            if (result != null) {
                JsonArray diagnostics = new JsonArray();
                for (String message : result.diagnostics) diagnostics.add(message);
                item.add("diagnostics", diagnostics);
                if (result.surface != null) item.add("overrides", overrideJson(result.surface));
            }
            if (result != null && result.ok) ok++; else failed++;
            items.add(item);
        }
        root.addProperty("ok", failed == 0);
        root.addProperty("checked", spriteIds.size());
        root.addProperty("passed", ok);
        root.addProperty("failed", failed);
        root.addProperty("compiled", compileUpload);
        root.addProperty("textureGeneration", TextureArrayBridge.getActiveTextureGeneration());
        root.add("items", items);
        return GSON.toJson(root);
    }

    public static String materialRehydrateJson() {
        RehydrateReport report = rehydrateSavedSidecars(MinecraftClient.getInstance());
        JsonObject root = new JsonObject();
        root.addProperty("ok", report.failed() == 0);
        root.addProperty("discovered", report.discovered());
        root.addProperty("applied", report.applied());
        root.addProperty("skipped", report.skipped());
        root.addProperty("failed", report.failed());
        root.addProperty("atMillis", lastRehydrateAtMillis);
        return GSON.toJson(root);
    }

    public static String diagnosticsSummary() {
        return "lastRehydrate=" + lastRehydrateReport
            + " lastRehydrateAtMillis=" + lastRehydrateAtMillis
            + " defaultSeeds=" + AutoPbrTextureRules.defaultSeedCount()
            + " spriteCounts{" + spriteProvenanceCounts() + "}";
    }

    public static String spriteProvenanceCounts() {
        int count = Math.min(TextureArrayBridge.sortedSpriteIds.size(), TextureTracker.MAX_TEXTURES);
        int specPack = 0;
        int specGenerated = 0;
        int specUser = 0;
        int specFlat = 0;
        int normalPack = 0;
        int normalGenerated = 0;
        int normalUser = 0;
        int normalFlat = 0;
        int flags = 0;
        int authoredHeight = 0;
        int visibleHeightRange = 0;
        int ao = 0;
        int emission = 0;
        int textureRules = 0;

        for (int i = 0; i < count; i++) {
            switch (AutoPbrTextureCatalog.specularSource(i)) {
                case TextureTracker.SOURCE_PACK_AUTHORED -> specPack++;
                case TextureTracker.SOURCE_GENERATED -> specGenerated++;
                case TextureTracker.SOURCE_USER_CUSTOM -> specUser++;
                case TextureTracker.SOURCE_FLAT -> specFlat++;
                default -> {
                }
            }
            switch (AutoPbrTextureCatalog.normalSource(i)) {
                case TextureTracker.SOURCE_PACK_AUTHORED -> normalPack++;
                case TextureTracker.SOURCE_GENERATED -> normalGenerated++;
                case TextureTracker.SOURCE_USER_CUSTOM -> normalUser++;
                case TextureTracker.SOURCE_FLAT -> normalFlat++;
                default -> {
                }
            }
            if (AutoPbrTextureCatalog.hasFlagTexture(i)) flags++;
            if (AutoPbrTextureCatalog.hasAuthoredHeight(i)) authoredHeight++;
            if (AutoPbrTextureCatalog.heightAlphaRangePacked(i) >= 0) visibleHeightRange++;
            if (AutoPbrTextureCatalog.hasDetectableAo(i)) ao++;
            if (AutoPbrTextureCatalog.hasDetectableEmission(i)) emission++;
            if (textureRuleFlags(i) != 0) textureRules++;
        }

        return "sprites=" + count
            + " specular[pack=" + specPack + ",generated=" + specGenerated
            + ",user=" + specUser + ",flat=" + specFlat + "]"
            + " normal[pack=" + normalPack + ",generated=" + normalGenerated
            + ",user=" + normalUser + ",flat=" + normalFlat + "]"
            + " flags=" + flags
            + " authoredHeight=" + authoredHeight
            + " visibleHeightRange=" + visibleHeightRange
            + " aoDetectable=" + ao
            + " emissionDetectable=" + emission
            + " textureRules=" + textureRules;
    }

    public record RehydrateReport(int discovered, int applied, int skipped, int failed) {
    }

    private static int resolveAuditSpriteId(String selector) {
        if (selector != null && !selector.isBlank()) {
            int resolved = TextureArrayBridge.resolveSpriteId(selector);
            if (resolved >= 0) return resolved;
        }
        Identifier fallback = AutoPbrTextureCatalog.fallbackSprite();
        return AutoPbrTextureCatalog.spriteIndex(fallback);
    }

    private static JsonObject deprecatedGraphSummary() {
        JsonObject json = new JsonObject();
        json.addProperty("version", -1);
        json.addProperty("path", "");
        json.addProperty("exists", false);
        json.addProperty("variant", "deprecated");
        json.addProperty("nodes", 0);
        json.addProperty("edges", 0);
        json.addProperty("valid", true);
        JsonArray diagnostics = new JsonArray();
        diagnostics.add("Material Lab ignores legacy node graph sidecars");
        json.add("diagnostics", diagnostics);
        return json;
    }

    private static JsonObject recipeSummary(MaterialRecipe recipe, Path profilePath) {
        JsonObject json = new JsonObject();
        json.addProperty("schema", "material_lab_recipe_v3");
        json.addProperty("profilePath", profilePath == null ? "" : profilePath.toString());
        json.addProperty("profileExists", profilePath != null && Files.exists(profilePath));
        json.addProperty("stackFingerprint", MaterialLabStore.stackFingerprint(MinecraftClient.getInstance()));
        json.addProperty("recipePresent", recipe != null && !recipe.isDefaultIntent());
        json.addProperty("defaultIntent", recipe == null || recipe.isDefaultIntent());
        if (recipe != null) {
            JsonObject fields = new JsonObject();
            fields.addProperty("roughnessGenerator", recipe.roughnessGeneratorMode);
            fields.addProperty("metalMode", recipe.metalMode);
            fields.addProperty("metalMaskSource", recipe.metalMaskSource);
            fields.addProperty("porosityMode", recipe.porosityMode);
            fields.addProperty("heightGenerator", recipe.heightGeneratorMode);
            fields.addProperty("normalKernel", recipe.normalKernelMode);
            fields.addProperty("normalOrientation", recipe.normalOrientation);
            fields.addProperty("emissionMode", recipe.emissionMode);
            fields.addProperty("transmissionVolumeMode", recipe.transmissionVolumeMode);
            fields.addProperty("legacyNodeSidecars", "inert");
            json.add("fields", fields);
        }
        return json;
    }

    private static JsonObject channelSummary(Identifier sprite, int spriteId, MaterialBakePlan evaluation) {
        JsonObject json = new JsonObject();
        json.addProperty("albedo", "pack");
        json.addProperty("specular", AutoPbrTextureCatalog.sourceName(AutoPbrTextureCatalog.specularSource(spriteId)));
        json.addProperty("normal", AutoPbrTextureCatalog.sourceName(AutoPbrTextureCatalog.normalSource(spriteId)));
        json.addProperty("flags", AutoPbrTextureCatalog.hasFlagTexture(spriteId) ? "pack_or_generated" : "generated_default");
        json.addProperty("ao", AutoPbrTextureCatalog.aoAvailability(spriteId));
        json.addProperty("emission", AutoPbrTextureCatalog.emissionAvailability(spriteId));
        json.addProperty("height", AutoPbrTextureCatalog.heightAlphaRangeLabel(spriteId));
        if (evaluation != null && evaluation.surface != null) {
            json.add("overrides", overrideJson(evaluation.surface));
        }
        JsonObject caches = new JsonObject();
        MinecraftClient client = MinecraftClient.getInstance();
        for (String channel : List.of("albedo", "specular", "normal", "flags")) {
            JsonObject cache = new JsonObject();
            cache.addProperty("expectedPath", client == null ? "" :
                AutoPbrGeneratedAssetStore.cachePath(client, sprite, channel).toString());
            cache.addProperty("lastWrite", AutoPbrGeneratedAssetStore.lastWrite(sprite, channel));
            caches.add(channel, cache);
        }
        json.add("generatedCaches", caches);
        return json;
    }

    private static JsonObject overrideJson(AutoPbrValue.Surface surface) {
        JsonObject json = new JsonObject();
        json.addProperty("albedo", surface.albedoOverride);
        json.addProperty("alpha", surface.alphaOverride);
        json.addProperty("roughness", surface.roughnessOverride);
        json.addProperty("metallic", surface.metallicOverride);
        json.addProperty("f0", surface.f0Override);
        json.addProperty("sss", surface.sssOverride);
        json.addProperty("conductorF0Rgb", surface.conductorF0RgbOverride);
        json.addProperty("normal", surface.normalOverride);
        json.addProperty("height", surface.heightOverride);
        json.addProperty("ao", surface.aoOverride);
        json.addProperty("emission", surface.emissionOverride);
        json.addProperty("flags", surface.flagsOverride);
        json.addProperty("ior", surface.iorOverride);
        json.addProperty("transmission", surface.transmissionOverride);
        json.addProperty("anisotropic", surface.anisotropicOverride);
        json.addProperty("sheen", surface.sheenOverride);
        json.addProperty("coat", surface.coatOverride);
        json.addProperty("displacement", surface.displacementOverride);
        return json;
    }

    private static JsonObject textureRuleJson(int spriteId) {
        int flags = textureRuleFlags(spriteId);
        JsonObject json = new JsonObject();
        json.addProperty("flags", flags);
        json.addProperty("flagsHex", "0x" + Integer.toHexString(flags));
        json.addProperty("labels", textureRuleFlagsLabel(flags));
        if ((flags & AutoPbrTextureRules.FLAG_CONDUCTOR_F0_RGB) != 0) {
            JsonArray conductor = new JsonArray();
            conductor.add(AutoPbrTextureRules.conductorF0ForTest(spriteId, 0));
            conductor.add(AutoPbrTextureRules.conductorF0ForTest(spriteId, 1));
            conductor.add(AutoPbrTextureRules.conductorF0ForTest(spriteId, 2));
            json.add("conductorF0Rgb", conductor);
        }
        return json;
    }

    private static JsonObject nativeTextureJson(int spriteId) {
        JsonObject json = new JsonObject();
        json.addProperty("spriteId", spriteId);
        json.addProperty("spriteCount", TextureArrayBridge.sortedSpriteIds.size());
        json.addProperty("size", AutoPbrTextureCatalog.spriteSize(spriteId));
        json.addProperty("hasAlbedo", AutoPbrTextureCatalog.albedo(spriteId) != null);
        json.addProperty("hasSpecular", AutoPbrTextureCatalog.hasSpecularTexture(spriteId));
        json.addProperty("hasNormal", AutoPbrTextureCatalog.hasNormalTexture(spriteId));
        json.addProperty("hasFlags", AutoPbrTextureCatalog.hasFlagTexture(spriteId));
        json.addProperty("sourceFlags", AutoPbrTextureCatalog.spriteSourceFlags(spriteId));
        return json;
    }

    private static JsonObject shaderReachabilityJson(int spriteId, MaterialBakePlan evaluation) {
        JsonObject json = new JsonObject();
        boolean flagsReachable = AutoPbrTextureCatalog.hasFlagTexture(spriteId)
            || (evaluation != null && evaluation.surface != null && evaluation.surface.flagsOverride);
        json.addProperty("albedoArray", true);
        json.addProperty("specularArray", AutoPbrTextureCatalog.hasSpecularTexture(spriteId)
            || (evaluation != null && evaluation.surface != null
            && (evaluation.surface.roughnessOverride || evaluation.surface.metallicOverride
            || evaluation.surface.f0Override || evaluation.surface.emissionOverride)));
        json.addProperty("normalArray", AutoPbrTextureCatalog.hasNormalTexture(spriteId)
            || (evaluation != null && evaluation.surface != null
            && (evaluation.surface.normalOverride || evaluation.surface.aoOverride || evaluation.surface.heightOverride)));
        json.addProperty("flagArray", flagsReachable);
        json.addProperty("blockPathFlagFetch", true);
        json.addProperty("textureRuleSsbo", textureRuleFlags(spriteId) != 0);
        return json;
    }

    private static JsonObject displacementJson(AutoPbrTextureCatalog.DisplacementEligibility displacement) {
        JsonObject json = new JsonObject();
        if (displacement == null) {
            json.addProperty("eligible", false);
            json.addProperty("reason", "missing");
            return json;
        }
        json.addProperty("eligible", displacement.eligible());
        json.addProperty("reason", displacement.reason());
        json.addProperty("heightSource", displacement.heightSource());
        json.addProperty("authoredHeight", displacement.hasAuthoredHeight());
        json.addProperty("normalPresent", displacement.hasNormalLayer());
        json.addProperty("visibleHeightRange", displacement.hasVisibleHeightRange());
        json.addProperty("normalSource", displacement.normalSource());
        json.addProperty("heightAlphaRange", displacement.heightAlphaRange());
        JsonArray blockers = new JsonArray();
        if (!Options.displacementEnabled) blockers.add("global_displacement_disabled");
        if (Options.displacementDepthCapPercent <= 0) blockers.add("depth_cap_zero");
        if (!displacement.eligible()) blockers.add(displacement.reason());
        if (displacement.eligible() && !displacement.hasVisibleHeightRange()) blockers.add("no_visible_height_range");
        json.add("blockers", blockers);
        return json;
    }
}
