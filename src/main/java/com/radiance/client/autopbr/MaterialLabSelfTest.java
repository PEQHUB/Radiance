package com.radiance.client.autopbr;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.radiance.client.materiallab.MaterialBakePlan;
import com.radiance.client.materiallab.MaterialHistogram;
import com.radiance.client.materiallab.MaterialLabStore;
import com.radiance.client.materiallab.MaterialPresetCatalog;
import com.radiance.client.materiallab.MaterialRecipe;
import com.radiance.client.materiallab.MaterialRecipeCompiler;
import com.radiance.client.materiallab.MaterialUploadResult;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.AuxiliaryTextures;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.VanillaTextureManifest;
import com.radiance.client.texture.compat.ResourcePackBlockLayerResolver;
import com.radiance.client.texture.compat.ResourcePackColorPropertiesResolver;
import com.radiance.client.texture.compat.ResourcePackCompatAtlasSource;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import com.radiance.client.texture.compat.ResourcePackCompatDiagnostics;
import com.radiance.client.texture.compat.ResourcePackEmissiveTextureResolver;
import com.radiance.client.texture.compat.ResourcePackLightmapResolver;
import com.radiance.client.texture.compat.ResourcePackLightmapResolver.LightmapSample;
import com.radiance.client.texture.compat.ResourcePackModelFallback;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver.NaturalTransform;
import com.radiance.client.texture.compat.ResourcePackRandomEntityTextureResolver;
import com.radiance.client.texture.compat.ResourcePackTextureNames;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.BlockOverlaySprite;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.RepeatTextureBasis;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.client.vertex.PBRVertexConsumer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.client.texture.atlas.AtlasSource;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class MaterialLabSelfTest {
    private static final Identifier SPRITE = Identifier.ofVanilla("block/oak_planks");
    private static final Pattern FORBIDDEN_SHADERPACK_RT_INSTANCE_MATRIX_MULTIPLY =
        Pattern.compile("\\*\\s*gl_(ObjectToWorld|WorldToObject)3x4EXT");

    private MaterialLabSelfTest() {
    }

    public static void main(String[] args) {
        TextureArrayBridge.setSortedSpriteIds(List.of(SPRITE, Identifier.ofVanilla("block/glass")));
        uploadResultTruthRejectsInvalidSprite();
        defaultRecipeEmitsNoOverride();
        hostileGraphSidecarsAreNotMaterialLabProfiles();
        advancedDefaultIntentSeesRuleFields();
        roughnessBakesInsteadOfScalarRule();
        transmissionIorEmissionRulesAreFlagged();
        advancedRulesAreFlagged();
        diffuseModelRulesAreFlaggedAndPacked();
        diffuseModelOptionsMigrationIsStable();
        fluidGeometryFlagsDistinguishWaterAndLava();
        packedBlockTypeCarriesShaderBlockIds();
        shaderPackDefaultsMatchVisualPolicy();
        shaderPackRayTraceTransformsAvoidShadercOptimizerTrap();
        measuredPresetsWritePhysicalValues();
        measuredConductorRulesReachTextureRuleBuffer();
        oxideAffectsMetallicAndRoughnessMaps();
        proceduralChannelControlsAffectBakePlan();
        completeChannelPackageControlsAffectBakePlan();
        previewLanesReflectBakePlan();
        expandedTextureRuleAbiCarriesAdvancedFields();
        shapedThicknessMinMaxGammaAffectsRule();
        aoIsHiddenContractAndPreservedByDefault();
        heightOnlyNormalBakeUsesFlatLabPbrFallback();
        materialSetRegistryReportsDirectLabPbrSidecars();
        generatedMasksAreDeterministic();
        histogramClampAndSmoothingAreDeterministic();
        dropdownRowSelectionUsesClickCoordinates();
        textureRuleEntrySizeStaysStable();
        textureNameFilterTreatsEmissiveAsAuxiliary();
        scalarPbrSidecarsComposeLabPbrLayers();
        textureArrayLayerSizeUsesLargestSprite();
        missingSpriteFallbackUsesRenderableBlockSprite();
        malformedModelJsonFallsBackToLowerPriorityResource();
        modelTextureReferenceRepairAvoidsMissingSprites();
        materialCompatFlagsDefaultEnabled();
        materialCompatLegacyDisabledOptionsMigrateToDefaults();
        materialCompatScannerRecognizesCoreFeatures();
        materialCompatRunScanMarksActivePacksAndParsesRecords();
        materialCompatWritesParserArtifactDumps();
        materialCompatDiagnosticsDetectPersistedOptionDivergence();
        materialCompatDiagnosticsReportNaturalConsumption();
        materialCompatDiagnosticsReportColorConsumption();
        materialCompatDiagnosticsReportLightmapConsumption();
        materialCompatDiagnosticsReportPhysicalEmissiveConsumption();
        materialCompatDiagnosticsReportShaderBlockLayerConsumption();
        ctmAtlasAdmissionUtilitiesAreStable();
        ctmAtlasSourceCollectsPresentTiles();
        emissiveTextureResolverMapsSuffixesAndAtlasAdmission();
        textureVariantResolverRegistryReportsCompiledRules();
        textureVariantResolverHonorsDeterministicRulePrecedence();
        textureVariantResolverSelectsFixedAndRandomSprites();
        textureVariantResolverRespectsBiomeAndHeightPredicates();
        textureVariantResolverSelectsRepeatSprites();
        textureVariantResolverHonorsStateAxisRepeatOrientation();
        textureVariantResolverHonorsTextureRepeatOrientation();
        textureVariantResolverSkipsOptifineOnlyRules();
        textureVariantResolverSelectsOverlayRandomSprites();
        textureVariantResolverStacksMatchingOverlayRules();
        textureVariantResolverSelectsOverlaySprites();
        textureVariantResolverSelectsOverlayCtmRepeatAndFixedSprites();
        textureVariantResolverCarriesOverlayLayerAlphaModes();
        textureVariantResolverSelectsNeighborMasks();
        textureVariantResolverHonorsExplicitConnectPredicates();
        textureVariantResolverSelectsFullAndCompactCtm();
        randomEntityTextureResolverSelectsWeightedAndBiomeVariants();
        shaderBlockLayerResolverParsesAlphaModes();
        colorPropertiesResolverParsesFlatBlockPalettes();
        lightmapResolverSamplesCustomPalettes();
        naturalTextureResolverParsesRulesAndTransformsUv();
        naturalTextureResolverUsesResolvedVariantRules();
        presetCatalogIsResourceBacked();
        System.out.println("Material Lab self-test passed");
    }

    private static void uploadResultTruthRejectsInvalidSprite() {
        MaterialUploadResult result = MaterialRecipeCompiler.compileAndUpload(-1, MaterialRecipe.defaults());
        expect(result != null && !result.uploadOk, "invalid sprite upload must fail truthfully");
        expect("Bake Error".equals(result.statusText), "invalid sprite upload status should be Bake Error");
    }

    private static void defaultRecipeEmitsNoOverride() {
        AutoPbrTextureRules.disableAll();
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, MaterialRecipe.defaults());
        expect(plan.ok, "default recipe should evaluate");
        expect(plan.surface != null, "default recipe should produce a surface");
        expect(!plan.surface.roughnessOverride, "default recipe must not override roughness");
        expect(!plan.surface.normalOverride, "default recipe must not override normals");
        AutoPbrTextureRules.update(0, plan);
        expect(AutoPbrTextureRules.flagsForTest(0) == 0, "default recipe must emit no texture rule");
    }

    private static void hostileGraphSidecarsAreNotMaterialLabProfiles() {
        String profileRoot = MaterialLabStore.profilesRoot(null).toString().replace('\\', '/');
        expect(profileRoot.contains("radiance/material_lab/profiles"),
            "Material Lab profiles must not live under old autopbr graph sidecars");
        expect(!profileRoot.contains("radiance/autopbr/rules"),
            "old graph sidecars must be outside Material Lab profile discovery");
    }

    private static void advancedDefaultIntentSeesRuleFields() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        expect(recipe.isDefaultIntent(), "fresh recipe should be default intent");
        recipe.uvScale = 1.25f;
        expect(!recipe.isDefaultIntent(), "uv scale must keep a profile recipe");
        recipe = MaterialRecipe.defaults();
        recipe.filterRadius = 1.0f;
        expect(!recipe.isDefaultIntent(), "filter radius must keep a profile recipe");
        recipe = MaterialRecipe.defaults();
        recipe.oxideAmount = 0.5f;
        expect(!recipe.isDefaultIntent(), "oxide controls must keep a profile recipe");
        recipe = MaterialRecipe.defaults();
        recipe.porositySssTintB = 0.25f;
        expect(!recipe.isDefaultIntent(), "SSS tint must keep a profile recipe");
        recipe = MaterialRecipe.defaults();
        recipe.thicknessGamma = 1.5f;
        expect(!recipe.isDefaultIntent(), "thickness gamma must keep a profile recipe");
    }

    private static void roughnessBakesInsteadOfScalarRule() {
        AutoPbrTextureRules.disableAll();
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.roughnessOverride = true;
        recipe.roughness = 0.21f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(plan.surface.roughnessOverride, "roughness override should be represented in bake plan");
        AutoPbrTextureRules.update(0, plan);
        expect((AutoPbrTextureRules.flagsForTest(0) & AutoPbrTextureRules.FLAG_ROUGHNESS) == 0,
            "roughness must bake into LabPBR specular, not flatten through scalar rule");
    }

    private static void transmissionIorEmissionRulesAreFlagged() {
        AutoPbrTextureRules.disableAll();
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.transmissionOverride = true;
        recipe.transmission = 0.75f;
        recipe.iorOverride = true;
        recipe.ior = 1.52f;
        recipe.emissionOverride = true;
        recipe.emissionMode = "whole_texture";
        recipe.emissionGain = 0.5f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(1, recipe);
        AutoPbrTextureRules.update(1, plan);
        int flags = AutoPbrTextureRules.flagsForTest(1);
        expect((flags & AutoPbrTextureRules.FLAG_TRANSMISSION) != 0, "transmission flag");
        expect((flags & AutoPbrTextureRules.FLAG_IOR) != 0, "ior flag");
        expect((flags & AutoPbrTextureRules.FLAG_EMISSION) != 0, "emission flag");
    }

    private static void advancedRulesAreFlagged() {
        AutoPbrTextureRules.disableAll();
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.anisotropicOverride = true;
        recipe.anisotropic = 0.5f;
        recipe.sheenOverride = true;
        recipe.sheenWeight = 0.25f;
        recipe.coatOverride = true;
        recipe.coatWeight = 0.4f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        AutoPbrTextureRules.update(0, plan);
        int flags = AutoPbrTextureRules.flagsForTest(0);
        expect((flags & AutoPbrTextureRules.FLAG_ANISOTROPIC) != 0, "anisotropic flag");
        expect((flags & AutoPbrTextureRules.FLAG_SHEEN) != 0, "sheen flag");
        expect((flags & AutoPbrTextureRules.FLAG_COAT) != 0, "coat flag");
    }

    private static void diffuseModelRulesAreFlaggedAndPacked() {
        MaterialRecipe defaultRecipe = MaterialRecipe.defaults();
        expect("global".equals(defaultRecipe.diffuseModel), "old/missing diffuseModel should default to global");
        expect(defaultRecipe.isDefaultIntent(), "global diffuse model should preserve default intent");

        AutoPbrTextureRules.disableAll();
        MaterialRecipe eon = MaterialRecipe.defaults();
        eon.diffuseModel = "eon";
        MaterialBakePlan eonPlan = MaterialRecipeCompiler.evaluate(0, eon);
        AutoPbrTextureRules.update(0, eonPlan);
        int eonFlags = AutoPbrTextureRules.flagsForTest(0);
        expect((eonFlags & AutoPbrTextureRules.FLAG_DIFFUSE_MODEL) != 0, "diffuse model flag");
        expect(((AutoPbrTextureRules.intForTest(0, 60) >> 6) & 0x3) == 1, "eon diffuse mode packing");

        AutoPbrTextureRules.disableAll();
        MaterialRecipe vmf = MaterialRecipe.defaults();
        vmf.diffuseModel = "vmf";
        expect(!vmf.isDefaultIntent(), "vmf diffuse model should keep a profile recipe");
        MaterialBakePlan vmfPlan = MaterialRecipeCompiler.evaluate(0, vmf);
        AutoPbrTextureRules.update(0, vmfPlan);
        int vmfFlags = AutoPbrTextureRules.flagsForTest(0);
        expect((vmfFlags & AutoPbrTextureRules.FLAG_DIFFUSE_MODEL) != 0, "vmf diffuse model flag");
        expect(((AutoPbrTextureRules.intForTest(0, 60) >> 6) & 0x3) == 2, "vmf diffuse mode packing");

        AutoPbrTextureRules.disableAll();
        MaterialRecipe legacy = MaterialRecipe.defaults();
        legacy.diffuseModel = "legacy";
        expect(!legacy.isDefaultIntent(), "legacy diffuse model should keep a profile recipe");
        MaterialBakePlan legacyPlan = MaterialRecipeCompiler.evaluate(0, legacy);
        AutoPbrTextureRules.update(0, legacyPlan);
        int legacyFlags = AutoPbrTextureRules.flagsForTest(0);
        expect((legacyFlags & AutoPbrTextureRules.FLAG_DIFFUSE_MODEL) != 0, "legacy diffuse model flag");
        expect(((AutoPbrTextureRules.intForTest(0, 60) >> 6) & 0x3) == 3, "legacy diffuse mode packing");
    }

    private static void diffuseModelOptionsMigrationIsStable() {
        Properties oldLegacy = new Properties();
        oldLegacy.setProperty("eonDiffuse", "false");
        expect(Options.diffuseModelFromProperties(oldLegacy, Options.DIFFUSE_MODEL_EON, true) ==
            Options.DIFFUSE_MODEL_LEGACY, "old eonDiffuse=false should migrate to legacy diffuse");

        Properties oldEon = new Properties();
        oldEon.setProperty("eonDiffuse", "true");
        expect(Options.diffuseModelFromProperties(oldEon, Options.DIFFUSE_MODEL_LEGACY, false) ==
            Options.DIFFUSE_MODEL_EON, "old eonDiffuse=true should migrate to eon diffuse");

        Properties explicitVmf = new Properties();
        explicitVmf.setProperty("eonDiffuse", "false");
        explicitVmf.setProperty("diffuseModel", String.valueOf(Options.DIFFUSE_MODEL_VMF));
        expect(Options.diffuseModelFromProperties(explicitVmf, Options.DIFFUSE_MODEL_EON, true) ==
            Options.DIFFUSE_MODEL_VMF, "explicit diffuseModel should win over legacy eonDiffuse");
    }

    private static void fluidGeometryFlagsDistinguishWaterAndLava() {
        int water = PBRVertexFormatElements.fluidGeometryFlags(true);
        int lava = PBRVertexFormatElements.fluidGeometryFlags(false);
        expect((water & PBRVertexFormatElements.PBR_FLAG_BLOCK_GEOMETRY) != 0, "water should be block geometry");
        expect((water & PBRVertexFormatElements.PBR_FLAG_FLUID_GEOMETRY) != 0, "water should be fluid geometry");
        expect((water & PBRVertexFormatElements.PBR_FLAG_WATER_GEOMETRY) != 0, "water should carry water bit");
        expect((lava & PBRVertexFormatElements.PBR_FLAG_BLOCK_GEOMETRY) != 0, "lava should be block geometry");
        expect((lava & PBRVertexFormatElements.PBR_FLAG_FLUID_GEOMETRY) != 0, "lava should be fluid geometry");
        expect((lava & PBRVertexFormatElements.PBR_FLAG_WATER_GEOMETRY) == 0, "lava must not carry water bit");
    }

    private static void packedBlockTypeCarriesShaderBlockIds() {
        int packed = PBRVertexFormatElements.PBR_PACKED_EMISSIVE_TYPE_NONE
            | PBRVertexFormatElements.packShaderBlockId(1003);
        expect((packed & PBRVertexFormatElements.PBR_PACKED_EMISSIVE_TYPE_MASK)
                == PBRVertexFormatElements.PBR_PACKED_EMISSIVE_TYPE_NONE,
            "packed shader block metadata must preserve the no-emissive low byte");
        expect(PBRVertexFormatElements.unpackShaderBlockId(packed) == 1003,
            "packed shader block metadata should round-trip OptiFine/Iris block.N ids");
        expect(PBRVertexFormatElements.packShaderBlockId(0) == 0,
            "shader block id zero should leave the packed metadata empty");
        expect(PBRVertexFormatElements.packShaderBlockId(PBRVertexFormatElements.PBR_PACKED_SHADER_BLOCK_ID_MAX + 1)
                == 0,
            "out-of-range shader block ids must not overflow packed vertex metadata");
    }

    private static void shaderPackDefaultsMatchVisualPolicy() {
        JsonObject root = readVanillaPtConfig();
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.enable_parallax")
                .equals("render_pipeline.false"), "vanilla-pt parallax should be off by default");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.cloud_mode")
                .endsWith(".vanilla"), "vanilla-pt clouds should default to vanilla geometry");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.capture_volumetric_cloud_indirect")
                .equals("render_pipeline.false"), "indirect volumetric cloud capture should be off by default");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.volumetric_cloud_temporal_accumulation")
                .equals("render_pipeline.false"), "volumetric cloud temporal should be off by default");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.volumetric_cloud_cast_shadow")
                .equals("render_pipeline.false"), "cloud shadows should be off by default");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.water_surface_mode")
                .endsWith(".realistic"), "water should default to realistic surface mode");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.water_caustics_enabled")
                .equals("render_pipeline.true"), "water caustics should be on by default");
        expect(defaultValue(root, "render_pipeline.module.ray_tracing.attribute.volumetric_light_mode")
                .endsWith(".vanilla"), "volumetric light should default to vanilla mode");
    }

    private static void shaderPackRayTraceTransformsAvoidShadercOptimizerTrap() {
        Path worldDir = vanillaPtRoot().resolve("world");
        List<String> forbiddenNeedles = List.of(
            "gl_ObjectToWorldEXT * vec4",
            "gl_WorldToObjectEXT * vec4",
            "mat3(gl_ObjectToWorld3x4EXT)",
            "mat3(gl_WorldToObject3x4EXT)"
        );

        try (Stream<Path> paths = Files.walk(worldDir)) {
            paths.filter(Files::isRegularFile)
                .filter(MaterialLabSelfTest::isRayTraceShaderFile)
                .forEach(path -> {
                    try {
                        String source = Files.readString(path, StandardCharsets.UTF_8);
                        String name = worldDir.relativize(path).toString();
                        for (String needle : forbiddenNeedles) {
                            expect(!source.contains(needle), name + " must not use optimizer-fragile " + needle);
                        }
                        expect(!FORBIDDEN_SHADERPACK_RT_INSTANCE_MATRIX_MULTIPLY.matcher(source).find(),
                            name + " must use common/raytrace_transforms.glsl instead of direct instance matrix multiply");
                    } catch (Exception e) {
                        throw new AssertionError("unable to inspect vanilla-pt shader " + path, e);
                    }
                });
        } catch (Exception e) {
            throw new AssertionError("unable to scan vanilla-pt ray-tracing shaders at " + worldDir, e);
        }
    }

    private static void measuredPresetsWritePhysicalValues() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.applyPreset(MaterialPresetCatalog.byId("gold"));
        expect(recipe.metallicOverride && close(recipe.metallic, 1.0f), "gold should be metallic");
        expect(recipe.f0Override && recipe.f0 > 0.5f, "gold should write measured F0");
        expect(recipe.conductorF0RgbOverride, "gold should write measured RGB conductor F0");
        recipe.applyPreset(MaterialPresetCatalog.byId("glass"));
        expect(recipe.iorOverride && close(recipe.ior, 1.5f), "glass should write IOR");
        expect(recipe.transmissionOverride && recipe.transmission > 0.9f, "glass should write transmission");
    }

    private static void measuredConductorRulesReachTextureRuleBuffer() {
        AutoPbrTextureRules.disableAll();
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.applyPreset(MaterialPresetCatalog.byId("gold"));
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        AutoPbrTextureRules.update(0, plan);
        int flags = AutoPbrTextureRules.flagsForTest(0);
        expect((flags & AutoPbrTextureRules.FLAG_METALLIC) != 0, "measured conductor should set metallic rule");
        expect((flags & AutoPbrTextureRules.FLAG_F0) != 0, "measured conductor should set scalar F0 fallback rule");
        expect((flags & AutoPbrTextureRules.FLAG_CONDUCTOR_F0_RGB) != 0,
            "measured conductor should set RGB conductor rule");
        expect(AutoPbrTextureRules.conductorF0ForTest(0, 0) > AutoPbrTextureRules.conductorF0ForTest(0, 2),
            "gold conductor F0 should be red-biased");
    }

    private static void oxideAffectsMetallicAndRoughnessMaps() {
        NativeImage previous = TextureTracker.spriteAlbedoCache.get(0);
        try (NativeImage albedo = new NativeImage(NativeImage.Format.RGBA, 16, 16, false)) {
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    albedo.setColorArgb(x, y, 0xFF600000);
                }
            }
            TextureTracker.spriteAlbedoCache.put(0, albedo);
            MaterialRecipe base = MaterialRecipe.defaults();
            base.roughnessOverride = true;
            base.roughnessSource = "flat";
            base.roughness = 0.2f;
            base.metallicOverride = true;
            base.metalMode = "generated_mask";
            base.metalMaskSource = "luminance";
            base.metalMaskThreshold = 0.02f;
            base.metalMaskSoftness = 0.30f;
            base.metallic = 1.0f;

            MaterialRecipe oxide = base.copy();
            oxide.oxideAmount = 1.0f;
            oxide.oxideRoughnessInfluence = 1.0f;
            oxide.oxideColorBias = 0.5f;

            MaterialBakePlan basePlan = MaterialRecipeCompiler.evaluate(0, base);
            MaterialBakePlan oxidePlan = MaterialRecipeCompiler.evaluate(0, oxide);
            expect(oxidePlan.surface.metallicMap[0] < basePlan.surface.metallicMap[0],
                "oxide should reduce metallic coverage");
            expect(oxidePlan.surface.roughness[0] > basePlan.surface.roughness[0],
                "tarnish roughness should raise roughness over oxide");
        } finally {
            if (previous == null) {
                TextureTracker.spriteAlbedoCache.remove(0);
            } else {
                TextureTracker.spriteAlbedoCache.put(0, previous);
            }
        }
    }

    private static void proceduralChannelControlsAffectBakePlan() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.roughnessOverride = true;
        recipe.roughnessSource = "generated";
        recipe.roughnessMin = 0.20f;
        recipe.roughnessMax = 0.40f;
        recipe.roughnessGamma = 2.0f;
        recipe.roughnessContrast = 1.25f;
        recipe.roughnessEdge = 2.0f;
        recipe.heightOverride = true;
        recipe.heightSource = "flat";
        recipe.heightFlat = 0.70f;
        recipe.heightScale = 1.0f;
        recipe.emissionOverride = true;
        recipe.emissionMode = "whole_texture";
        recipe.emissionGain = 0.65f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(plan.ok && plan.surface != null, "procedural recipe should evaluate");
        expect(plan.surface.roughness[0] >= 0.20f && plan.surface.roughness[0] <= 0.40f,
            "roughness min/max remap should constrain generated map");
        expect(close(plan.surface.heightMap[0], 0.70f), "flat height source should control height map");
        expect(!plan.surface.aoOverride, "AO should remain preserved unless backend recipe explicitly overrides it");
        expect(close(plan.surface.emissionMap[0], 0.65f), "manual whole-texture emission should set emission map");
    }

    private static void completeChannelPackageControlsAffectBakePlan() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.roughnessOverride = true;
        recipe.roughnessSource = "generated";
        recipe.roughnessGeneratorMode = "edges";
        recipe.roughnessBlackPoint = 0.05f;
        recipe.roughnessMidpoint = 0.45f;
        recipe.roughnessWhitePoint = 0.95f;
        recipe.roughnessBlurRadius = 1;
        recipe.roughnessWear = 0.25f;
        recipe.metallicOverride = true;
        recipe.metalMode = "generated_mask";
        recipe.metalMaskSource = "luminance";
        recipe.metalMaskThreshold = 0.25f;
        recipe.metalMaskSoftness = 0.25f;
        recipe.metallic = 1.0f;
        recipe.porosityOverride = true;
        recipe.porosityMode = "sss";
        recipe.porositySource = "luminance";
        recipe.porositySssStrength = 0.7f;
        recipe.heightOverride = true;
        recipe.heightSource = "generated";
        recipe.heightGeneratorMode = "shape_from_shading";
        recipe.heightBlurRadius = 1;
        recipe.normalStrengthOverride = true;
        recipe.normalSource = "generated";
        recipe.normalKernelMode = "scharr";
        recipe.normalGeneratorRadius = 2;
        recipe.normalXStrength = 1.5f;
        recipe.normalYStrength = 0.75f;
        recipe.normalSmoothing = 1;
        recipe.emissionOverride = true;
        recipe.emissionMode = "albedo_red";
        recipe.emissionThresholdLow = 0.1f;
        recipe.emissionThresholdHigh = 0.9f;
        recipe.emissionGain = 0.5f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(plan.ok && plan.surface != null, "complete package recipe should evaluate");
        expect(plan.surface.roughnessOverride, "roughness package should override bake");
        expect(plan.surface.metallicOverride && plan.surface.metallicMap != null, "metal mask package should evaluate");
        expect(plan.surface.sssOverride && plan.surface.sssMap != null, "porosity/SSS should override _s.B");
        expect(plan.surface.heightOverride, "height package should override _n.A");
        expect(plan.surface.normalOverride, "normal package should override _n.RG");
        expect(plan.surface.emissionOverride, "emission package should override _s.A");
    }

    private static void aoIsHiddenContractAndPreservedByDefault() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.heightOverride = true;
        recipe.heightSource = "flat";
        recipe.heightFlat = 0.25f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(plan.ok && plan.surface != null, "height-only plan should evaluate");
        expect(!plan.surface.aoOverride, "AO page is hidden and _n.B must be preserved by default");
    }

    private static void heightOnlyNormalBakeUsesFlatLabPbrFallback() {
        NativeImage previous = TextureTracker.spriteNormalCache.remove(0);
        try {
            MaterialRecipe recipe = MaterialRecipe.defaults();
            recipe.heightOverride = true;
            recipe.heightSource = "flat";
            recipe.heightFlat = 0.25f;
            int pixel = MaterialRecipeCompiler.bakeNormalPixelForTest(0, recipe, 0, 0);
            expect(((pixel >>> 16) & 0xFF) == 128,
                "height-only bake without pack normal should preserve flat normal X");
            expect(((pixel >>> 8) & 0xFF) == 128,
                "height-only bake without pack normal should preserve flat normal Y");
            expect((pixel & 0xFF) == 255,
                "height-only bake without pack normal should preserve LabPBR AO as fully open");
        } finally {
            if (previous != null) {
                TextureTracker.spriteNormalCache.put(0, previous);
            }
        }
    }

    private static void materialSetRegistryReportsDirectLabPbrSidecars() {
        List<Identifier> previousSprites = new ArrayList<>(TextureArrayBridge.sortedSpriteIds);
        byte[] previousSpecularSources = TextureTracker.spriteSpecularSource.clone();
        byte[] previousNormalSources = TextureTracker.spriteNormalSource.clone();
        NativeImage previousAlbedo = TextureTracker.spriteAlbedoCache.remove(0);
        NativeImage previousSpecular = TextureTracker.spriteSpecularCache.remove(0);
        NativeImage previousNormal = TextureTracker.spriteNormalCache.remove(0);
        NativeImage previousFlag = TextureTracker.spriteFlagCache.remove(0);
        NativeImage albedo = null;
        NativeImage specular = null;
        NativeImage normal = null;
        try {
            TextureArrayBridge.setSortedSpriteIds(List.of(SPRITE, Identifier.ofVanilla("block/glass")));
            albedo = new NativeImage(NativeImage.Format.RGBA, 2, 1, false);
            specular = new NativeImage(NativeImage.Format.RGBA, 2, 1, false);
            normal = new NativeImage(NativeImage.Format.RGBA, 2, 1, false);
            albedo.setColorArgb(0, 0, 0xFFFFFFFF);
            albedo.setColorArgb(1, 0, 0xFFFFFFFF);
            specular.setColorArgb(0, 0, 0xFF202020);
            specular.setColorArgb(1, 0, 0xFF202020);
            normal.setColorArgb(0, 0, 0xF58080FF);
            normal.setColorArgb(1, 0, 0xFF8080FF);
            TextureTracker.spriteAlbedoCache.put(0, albedo);
            TextureTracker.spriteSpecularCache.put(0, specular);
            TextureTracker.spriteNormalCache.put(0, normal);
            TextureTracker.spriteSpecularSource[0] = TextureTracker.SOURCE_PACK_AUTHORED;
            TextureTracker.spriteNormalSource[0] = TextureTracker.SOURCE_PACK_AUTHORED;

            JsonObject audit = JsonParser.parseString(AutoPbrRuntime.materialAuditJson("0")).getAsJsonObject();
            JsonObject materialSet = audit.getAsJsonObject("materialSet");
            expect(materialSet != null, "material audit should expose material-set binding");
            expect(materialSet.get("materialSetId").getAsInt() == 0, "material set id should alias sprite id");
            expect("resolved_sprite_id_material_set_v1".equals(
                    materialSet.get("nativeBindingPolicy").getAsString()),
                "material-set binding policy should be explicit");
            expect("pbr_texture_id".equals(materialSet.get("shaderLookupKey").getAsString()),
                "material set should identify the shader lookup field");
            expect(!materialSet.get("nativeMaterialSetTablePresent").getAsBoolean(),
                "material set should not claim a separate native table");
            expect(materialSet.get("materialSetAliasesResolvedSprite").getAsBoolean(),
                "material set should explicitly alias the resolved sprite id");
            expect("labpbr_direct".equals(materialSet.get("decodeMode").getAsString()),
                "material set should use direct LabPBR decode mode");
            expect("direct_labpbr_normal_alpha".equals(materialSet.get("heightSource").getAsString()),
                "material set should identify direct LabPBR normal-alpha displacement");
            expect("245..255".equals(materialSet.get("heightAlphaRange").getAsString()),
                "material set should report normal alpha range as diagnostics");
            expect(materialSet.getAsJsonObject("displacement").get("eligible").getAsBoolean(),
                "authored normal alpha should be displacement eligible");
            expect("direct_labpbr_specular_sidecar".equals(
                    materialSet.getAsJsonObject("specular").get("binding").getAsString()),
                "pack-authored specular should bind directly");
            expect("direct_labpbr_normal_sidecar".equals(
                    materialSet.getAsJsonObject("normal").get("binding").getAsString()),
                "pack-authored normal should bind directly");

            normal.setColorArgb(1, 0, 0xF58080FF);
            JsonObject uniformAudit = JsonParser.parseString(AutoPbrRuntime.materialAuditJson("0")).getAsJsonObject();
            JsonObject uniformSet = uniformAudit.getAsJsonObject("materialSet");
            expect(!uniformSet.getAsJsonObject("displacement").get("eligible").getAsBoolean(),
                "uniform authored normal alpha should not be displacement eligible");
            expect("uniform_alpha_no_visible_relief".equals(
                    uniformSet.getAsJsonObject("displacement").get("reason").getAsString()),
                "uniform authored normal alpha should explain why displacement is skipped");
            expect("none".equals(uniformSet.get("heightAlphaRange").getAsString()),
                "uniform authored normal alpha should not report a visible height range");
            expect((uniformSet.get("sourceFlags").getAsInt() & TextureTracker.SPRITE_FLAG_HAS_HEIGHT) == 0,
                "uniform authored normal alpha should not set the sprite height flag");
            normal.setColorArgb(1, 0, 0xFF8080FF);

            JsonObject registry = JsonParser.parseString(AutoPbrRuntime.materialSetRegistryJson(1)).getAsJsonObject();
            expect("radser_runtime_material_set_registry_v1".equals(registry.get("schema").getAsString()),
                "material set registry should expose a stable schema");
            expect(registry.get("reported").getAsInt() == 1,
                "bounded material set registry should honor the requested limit");
            JsonObject item = registry.getAsJsonArray("items").get(0).getAsJsonObject();
            expect("minecraft:block/oak_planks".equals(item.get("sprite").getAsString()),
                "material set registry should preserve sprite labels");
            expect("resolved_sprite_id_material_set_v1".equals(
                    registry.get("nativeBindingPolicy").getAsString()),
                "material set registry should expose the alias binding policy");
            expect("pbr_texture_id".equals(registry.get("shaderLookupKey").getAsString()),
                "material set registry should expose the shader lookup field");
            expect(!registry.get("nativeMaterialSetTablePresent").getAsBoolean(),
                "material set registry should not claim a separate native table");
            expect(registry.get("materialSetAliasesResolvedSprite").getAsBoolean(),
                "material set registry should expose the resolved-sprite alias contract");
            expect("direct_labpbr_normal_alpha".equals(item.get("heightSource").getAsString()),
                "material set registry should retain direct LabPBR height source");
        } finally {
            TextureTracker.spriteAlbedoCache.remove(0);
            TextureTracker.spriteSpecularCache.remove(0);
            TextureTracker.spriteNormalCache.remove(0);
            TextureTracker.spriteFlagCache.remove(0);
            if (albedo != null) albedo.close();
            if (specular != null) specular.close();
            if (normal != null) normal.close();
            if (previousAlbedo != null) TextureTracker.spriteAlbedoCache.put(0, previousAlbedo);
            if (previousSpecular != null) TextureTracker.spriteSpecularCache.put(0, previousSpecular);
            if (previousNormal != null) TextureTracker.spriteNormalCache.put(0, previousNormal);
            if (previousFlag != null) TextureTracker.spriteFlagCache.put(0, previousFlag);
            System.arraycopy(previousSpecularSources, 0, TextureTracker.spriteSpecularSource, 0,
                TextureTracker.spriteSpecularSource.length);
            System.arraycopy(previousNormalSources, 0, TextureTracker.spriteNormalSource, 0,
                TextureTracker.spriteNormalSource.length);
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void previewLanesReflectBakePlan() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.roughnessOverride = true;
        recipe.roughnessSource = "flat";
        recipe.roughness = 0.36f;
        recipe.heightOverride = true;
        recipe.heightSource = "flat";
        recipe.heightFlat = 0.72f;
        recipe.normalStrengthOverride = true;
        recipe.normalSource = "generated";
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(plan.ok && plan.preview != null, "preview data should exist");
        expect(plan.preview.scalar("alpha") != null, "albedo alpha preview lane");
        expect(plan.preview.scalar("luminance") != null, "albedo luminance preview lane");
        expect(close(plan.preview.scalar("final_roughness")[0], plan.surface.roughness[0]),
            "final roughness preview should match bake plan");
        expect(close(plan.preview.scalar("final_height")[0], plan.surface.heightMap[0]),
            "final height preview should match bake plan");
        expect(plan.preview.color("final_normal")[0] == plan.surface.normal[0],
            "final normal preview should match bake plan");
        expect(plan.preview.scalar("pack_smoothness") != null, "isolated _s.R preview lane");
        expect(plan.preview.scalar("pack_sg") != null, "isolated _s.G preview lane");
        expect(plan.preview.scalar("pack_sb") != null, "isolated _s.B preview lane");
        expect(plan.preview.scalar("pack_sa") != null, "isolated _s.A preview lane");
    }

    private static void expandedTextureRuleAbiCarriesAdvancedFields() {
        AutoPbrTextureRules.disableAll();
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.transmissionOverride = true;
        recipe.transmission = 0.8f;
        recipe.absorptionR = 0.2f;
        recipe.absorptionG = 0.1f;
        recipe.absorptionB = 0.05f;
        recipe.absorptionDistance = 12.0f;
        recipe.transmissionVolumeMode = "thin_glass";
        recipe.refractionRoughness = 0.25f;
        recipe.thicknessSource = "albedo_alpha";
        recipe.thicknessAmount = 0.65f;
        recipe.emissionOverride = true;
        recipe.emissionMode = "whole_texture";
        recipe.emissionGain = 0.4f;
        recipe.emissionTintR = 0.8f;
        recipe.emissionTintG = 0.5f;
        recipe.emissionTintB = 0.25f;
        recipe.emissionNits = 1200.0f;
        recipe.anisotropicOverride = true;
        recipe.anisotropic = 0.6f;
        recipe.anisotropicRotation = 0.33f;
        recipe.coatOverride = true;
        recipe.coatWeight = 0.7f;
        recipe.coatIor = 1.7f;
        recipe.coatTintR = 0.9f;
        recipe.coatMask = 0.4f;
        recipe.sheenOverride = true;
        recipe.sheenWeight = 0.2f;
        recipe.sheenRoughness = 0.75f;
        recipe.uvScale = 2.0f;
        recipe.uvOffset = 0.125f;
        recipe.filterRadius = 3.0f;
        recipe.mipBias = -1.5f;
        recipe.displacementOverride = true;
        recipe.displacementScale = 1.6f;
        recipe.porosityOverride = true;
        recipe.porosityMode = "sss";
        recipe.porositySssStrength = 0.5f;
        recipe.porositySssRadius = 0.6f;
        recipe.porositySssThickness = 0.7f;
        recipe.porositySssTintR = 0.8f;
        recipe.porositySssTintG = 0.6f;
        recipe.porositySssTintB = 0.4f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        AutoPbrTextureRules.update(0, plan);
        int flags = AutoPbrTextureRules.flagsForTest(0);
        expect((flags & AutoPbrTextureRules.FLAG_ABSORPTION) != 0, "absorption ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_THICKNESS) != 0, "thickness ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_VOLUME_MODE) != 0, "volume mode ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_REFRACTION_ROUGHNESS) != 0, "refraction roughness ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_EMISSION_TINT_NITS) != 0, "emission tint/nits ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_ANISOTROPIC_ROTATION) != 0, "anisotropic rotation ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_COAT_EXT) != 0, "coat extension ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_SHEEN_ROUGHNESS) != 0, "sheen roughness ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_UV_TRANSFORM) != 0, "uv transform ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_FILTER_RADIUS) != 0, "filter radius ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_MIP_BIAS) != 0, "mip bias ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_DISPLACEMENT_SCALE) != 0, "displacement ABI flag");
        expect((flags & AutoPbrTextureRules.FLAG_SUBSURFACE_EXT) != 0, "subsurface extension ABI flag");
        expect(close(AutoPbrTextureRules.floatForTest(0, 64), 0.2f), "absorption red offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 80), 0.65f), "thickness offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 100), 1200.0f), "emission nits offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 104), 0.33f), "anisotropic rotation offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 128), 0.4f), "coat mask offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 156), 1.6f), "displacement offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 160), 0.6f), "subsurface radius offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 164), 0.7f), "subsurface thickness offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 168), 0.8f), "subsurface tint red offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 176), 0.4f), "subsurface tint blue offset");
        expect((AutoPbrTextureRules.intForTest(0, 60) & 0x3) == 1, "thin glass mode flag packing");
    }

    private static void shapedThicknessMinMaxGammaAffectsRule() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.transmissionOverride = true;
        recipe.transmission = 0.5f;
        recipe.thicknessAmount = 0.25f;
        recipe.thicknessMin = 0.2f;
        recipe.thicknessMax = 0.8f;
        recipe.thicknessGamma = 2.0f;
        MaterialBakePlan plan = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(close(plan.surface.thicknessAmount, 0.2375f), "thickness min/max/gamma shaping");
    }

    private static void generatedMasksAreDeterministic() {
        MaterialRecipe recipe = MaterialRecipe.defaults();
        recipe.generateRoughness = true;
        recipe.generateHeight = true;
        recipe.generateNormal = true;
        recipe.generateAo = true;
        MaterialBakePlan a = MaterialRecipeCompiler.evaluate(0, recipe);
        MaterialBakePlan b = MaterialRecipeCompiler.evaluate(0, recipe);
        expect(a.ok && b.ok, "generated plans should evaluate");
        expect(close(a.surface.roughness[0], b.surface.roughness[0]), "roughness generation deterministic");
        expect(a.surface.normal[0] == b.surface.normal[0], "normal generation deterministic");
        expect(close(a.surface.heightMap[0], b.surface.heightMap[0]), "height generation deterministic");
        expect(close(a.surface.aoMap[0], b.surface.aoMap[0]), "ao generation deterministic");
    }

    private static void histogramClampAndSmoothingAreDeterministic() {
        MaterialHistogram.Stats stats = MaterialHistogram.build(
            new float[] {Float.NaN, -1.0f, 0.25f, 2.0f, 0.75f}, 80);
        expect(stats.count == 4, "histogram should skip NaN and keep finite samples");
        expect(close(stats.min, 0.0f), "histogram should clamp min");
        expect(close(stats.max, 1.0f), "histogram should clamp max");
        expect(close(stats.avg, 0.5f), "histogram should average clamped samples");
        expect(stats.density.length == 256, "histogram should retain high-detail minimum bin count");
        expect(stats.sample(0.25f) >= 0.0f, "histogram density sampling should be non-negative");
    }

    private static void dropdownRowSelectionUsesClickCoordinates() {
        expect(SelectionDropdownWidget.rowIndexForTest(10, 20, 100, 14, 3, 50, 35) == 1,
            "dropdown row helper should use click coordinates");
        expect(SelectionDropdownWidget.rowIndexForTest(10, 20, 100, 14, 3, 150, 35) == -1,
            "dropdown row helper should reject horizontal misses");
    }

    private static void textureRuleEntrySizeStaysStable() {
        expect(AutoPbrTextureRules.entrySizeForTest() == 192, "texture rule entry must remain 192 bytes");
    }

    private static void textureNameFilterTreatsEmissiveAsAuxiliary() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldPhysicalEmissive = Options.materialCompatPhysicalEmissiveEnabled;
        Options.materialCompatEnabled = false;
        Options.materialCompatPhysicalEmissiveEnabled = false;
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_s.png")), "_s should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_spec.png")), "_spec should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_specular.png")), "_specular should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_n.png")), "_n should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_normal.png")), "_normal should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_norm.png")), "_norm should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_f.png")), "_f should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_height.png")), "_height should be atlas-filtered aux metadata");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_metallic.png")), "_metallic should be atlas-filtered aux metadata");
        expect(ResourcePackTextureNames.hasNonEmissivePbrAuxiliarySuffix(
            "textures/block/stone_normal.png"), "normal aliases should be non-emissive aux maps");
        expect(!ResourcePackTextureNames.hasNonEmissivePbrAuxiliarySuffix(
            "textures/block/lamp_e.png"), "emissive overlays must not be treated as scalar PBR aux maps");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/lamp_e.png")), "_e should be atlas-filtered aux");
        Options.materialCompatEnabled = true;
        Options.materialCompatPhysicalEmissiveEnabled = true;
        expect(!ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/lamp_e.png")), "declared physical emissive overlays must remain atlas eligible");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_s.png")), "physical emissive mode must still filter _s maps");
        Options.materialCompatEnabled = oldEnabled;
        Options.materialCompatPhysicalEmissiveEnabled = oldPhysicalEmissive;
        expect(!ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone.png")), "base albedo must stay atlas eligible");
        expect(!ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/gui/icon_e.png")), "GUI emissive-looking names must not be filtered");
        expect(ResourcePackTextureNames.allowsPbrAuxiliaryLookup(
            Identifier.ofVanilla("optifine/ctm/glass/0.png")), "CTM base tiles should load same-directory PBR sidecars");
        expect(!ResourcePackTextureNames.allowsPbrAuxiliaryLookup(
            Identifier.ofVanilla("optifine/ctm/glass/0_n.png")), "CTM normal sidecars must not recursively load aux maps");
        expect(!ResourcePackTextureNames.allowsPbrAuxiliaryLookup(
            Identifier.ofVanilla("optifine/ctm/glass/0_normal.png")), "CTM normal alias sidecars must not recursively load aux maps");

        List<Identifier> normalCandidates = AuxiliaryTextures.candidatesForTest(
            AuxiliaryTextures.NORMAL, Identifier.ofVanilla("textures/block/stone.png"));
        expect(normalCandidates.contains(Identifier.ofVanilla("textures/block/stone_normal.png")),
            "normal lookup should include same-directory _normal aliases");
        expect(normalCandidates.contains(Identifier.ofVanilla("textures/normal/block/stone.png")),
            "normal lookup should include unsuffixed textures/normal fallbacks");
        List<Identifier> specularCandidates = AuxiliaryTextures.candidatesForTest(
            AuxiliaryTextures.SPECULAR, Identifier.ofVanilla("textures/block/stone.png"));
        expect(specularCandidates.contains(Identifier.ofVanilla("textures/block/stone_specular.png")),
            "specular lookup should include same-directory _specular aliases");
        expect(specularCandidates.contains(Identifier.ofVanilla("textures/specular/block/stone.png")),
            "specular lookup should include unsuffixed textures/specular fallbacks");

        try {
            ResourcePackCompatCtmTiles.clearRegisteredCtmSpriteAssetPaths();
            Identifier ctmSprite = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/glass/0.png"));
            ResourcePackCompatCtmTiles.registerCtmSpriteAssetPath(
                ctmSprite, "assets/minecraft/optifine/ctm/glass/0.png",
                List.of("assets/minecraft/textures/block/glass.png"));
            List<Identifier> ctmNormalCandidates = AuxiliaryTextures.candidatesForTest(
                AuxiliaryTextures.NORMAL, ctmSprite);
            expect(ctmNormalCandidates.get(0).equals(Identifier.ofVanilla("optifine/ctm/glass/0_n.png")),
                "CTM normal lookup should prefer exact LabPBR sidecars");
            expect(ctmNormalCandidates.indexOf(Identifier.ofVanilla("textures/block/glass_n.png")) > 0,
                "CTM normal lookup should fall back to matched base LabPBR sidecars");
            expect(ctmNormalCandidates.contains(Identifier.ofVanilla("optifine/ctm/glass/0_normal.png")),
                "CTM normal lookup should include _normal sidecars");
            List<Identifier> ctmRoughnessCandidates = AuxiliaryTextures.scalarCandidatesForTest(
                "roughness", ctmSprite);
            expect(ctmRoughnessCandidates.get(0).equals(Identifier.ofVanilla("optifine/ctm/glass/0_roughness.png")),
                "CTM roughness lookup should prefer exact scalar sidecars");
            expect(ctmRoughnessCandidates.indexOf(Identifier.ofVanilla("textures/block/glass_roughness.png")) > 0,
                "CTM roughness lookup should fall back to matched base scalar sidecars");
            expect(ctmRoughnessCandidates.contains(Identifier.ofVanilla("optifine/ctm/glass/0_rough.png")),
                "CTM roughness lookup should include _rough sidecars");
            List<Identifier> ctmHeightCandidates = AuxiliaryTextures.scalarCandidatesForTest(
                "height", ctmSprite);
            expect(ctmHeightCandidates.get(0).equals(Identifier.ofVanilla("optifine/ctm/glass/0_height.png")),
                "CTM height lookup should prefer exact scalar sidecars");
            expect(ctmHeightCandidates.indexOf(Identifier.ofVanilla("textures/block/glass_height.png")) > 0,
                "CTM height lookup should fall back to matched base scalar sidecars");
            expect(ctmHeightCandidates.contains(Identifier.ofVanilla("optifine/ctm/glass/0_disp.png")),
                "CTM height lookup should include _disp sidecars");
        } finally {
            ResourcePackCompatCtmTiles.clearRegisteredCtmSpriteAssetPaths();
        }
    }

    private static void scalarPbrSidecarsComposeLabPbrLayers() {
        List<Identifier> roughnessCandidates = AuxiliaryTextures.scalarCandidatesForTest(
            "roughness", Identifier.ofVanilla("textures/block/stone.png"));
        expect(roughnessCandidates.contains(Identifier.ofVanilla("textures/block/stone_roughness.png")),
            "scalar roughness lookup should include same-directory _roughness maps");
        expect(roughnessCandidates.contains(Identifier.ofVanilla("textures/roughness/block/stone.png")),
            "scalar roughness lookup should include unsuffixed textures/roughness fallbacks");
        List<Identifier> heightCandidates = AuxiliaryTextures.scalarCandidatesForTest(
            "height", Identifier.ofVanilla("textures/block/stone.png"));
        expect(heightCandidates.contains(Identifier.ofVanilla("textures/block/stone_disp.png")),
            "scalar height lookup should include _disp aliases");
        expect(heightCandidates.contains(Identifier.ofVanilla("textures/displacement/block/stone.png")),
            "scalar height lookup should include unsuffixed displacement folder fallbacks");

        int spec = AuxiliaryTextures.composedSpecularPixelForTest(0xFF404040, 0xFFFFFFFF);
        int smoothness = (spec >>> 16) & 0xFF;
        expect(((spec >>> 24) & 0xFF) == 255, "composed specular should encode no emission by default");
        expect(smoothness >= 126 && smoothness <= 129,
            "roughness scalar should convert to LabPBR smoothness");
        expect(((spec >>> 8) & 0xFF) == 238, "metallic scalar should encode LabPBR metal code");
        expect((spec & 0xFF) == 0, "composed specular should preserve default SSS");
        int metalOnly = AuxiliaryTextures.composedSpecularPixelForTest(null, 0xFFFFFFFF);
        expect(((metalOnly >>> 16) & 0xFF) == 0,
            "missing roughness scalar should preserve LabPBR rough default");
        expect(((metalOnly >>> 8) & 0xFF) == 238,
            "metallic scalar alone should still encode metal code");

        int normal = AuxiliaryTextures.composedNormalPixelForTest(0xFF202020, 0xFF808080);
        expect(((normal >>> 24) & 0xFF) >= 31 && ((normal >>> 24) & 0xFF) <= 33,
            "height scalar should become direct LabPBR normal alpha");
        expect(((normal >>> 16) & 0xFF) == 128,
            "composed normal should preserve flat LabPBR normal X");
        expect(((normal >>> 8) & 0xFF) == 128,
            "composed normal should preserve flat LabPBR normal Y");
        expect((normal & 0xFF) >= 127 && (normal & 0xFF) <= 129,
            "AO scalar should become LabPBR normal blue");

        try (NativeImage albedo = new NativeImage(NativeImage.Format.RGBA, 2, 1, false);
             NativeImage normalMap = new NativeImage(NativeImage.Format.RGBA, 2, 1, false)) {
            albedo.setColorArgb(0, 0, 0xFFFFFFFF);
            albedo.setColorArgb(1, 0, 0xFFFFFFFF);
            normalMap.setColorArgb(0, 0, 0xF08080FF);
            normalMap.setColorArgb(1, 0, 0xF08080FF);
            expect(!AuxiliaryTextures.hasVisibleHeightAlphaRange(normalMap, albedo),
                "uniform normal alpha should not be visible displacement");
            normalMap.setColorArgb(1, 0, 0xFF8080FF);
            expect(AuxiliaryTextures.hasVisibleHeightAlphaRange(normalMap, albedo),
                "varying normal alpha should be visible displacement");
            albedo.setColorArgb(1, 0, 0x00FFFFFF);
            expect(!AuxiliaryTextures.hasVisibleHeightAlphaRange(normalMap, albedo),
                "transparent albedo pixels should not create visible height range");
        }
    }

    private static void textureArrayLayerSizeUsesLargestSprite() {
        expect(VanillaTextureManifest.chooseFixedLayerSizeFromCountsForTest(
            16, 974,
            64, 824,
            128, 14,
            256, 4,
            1024, 3) == 256,
            "texture array layer size should preserve authored high-resolution sprites up to the runtime cap");
        expect(VanillaTextureManifest.chooseFixedLayerSizeForTest(16, 16, 64, 64, 128, 128) == 128,
            "small diagnostic atlases should choose the largest authored square size");
        expect(VanillaTextureManifest.chooseFixedLayerSizeForTest(512, 512, 1024, 1024) == 256,
            "texture array layer size should cap extreme outliers for runtime memory stability");
        expect(VanillaTextureManifest.chooseFixedLayerSizeForTest(16, 32, 64, 16) == 64,
            "texture array layer size should handle non-square diagnostic inputs conservatively");
    }

    private static void missingSpriteFallbackUsesRenderableBlockSprite() {
        Map<Identifier, Integer> oldTextureIds = new LinkedHashMap<>(TextureTracker.textureID2GLID);
        int oldRenderableCapacity = TextureArrayBridge.renderableSpriteCapacityForTest();
        TextureArrayBridge.setSortedSpriteIds(List.of(
            MissingSprite.getMissingSpriteId(),
            Identifier.ofVanilla("block/dirt"),
            Identifier.ofVanilla("block/stone"),
            Identifier.ofVanilla("block/overflow_ctm_tile")));
        try {
            TextureTracker.textureID2GLID.clear();
            TextureTracker.textureID2GLID.put(MissingSprite.getMissingSpriteId(), 11);
            TextureTracker.textureID2GLID.put(Identifier.ofVanilla("textures/atlas/blocks.png"), 22);
            int missing = TextureArrayBridge.resolveSpriteId(MissingSprite.getMissingSpriteId().toString());
            int dirt = TextureArrayBridge.resolveSpriteId("minecraft:block/dirt");
            expect(missing == 0, "diagnostic sprite lookup should still expose missingno");
            expect(dirt == 1, "dirt diagnostic lookup should be stable");
            expect(TextureArrayBridge.missingSpriteFallbackIdForTest() == dirt,
                "missing fallback should prefer dirt when available");
            expect(TextureArrayBridge.resolveRenderableSpriteId(MissingSprite.getMissingSpriteId()) == dirt,
                "renderable missing lookup should fall back to dirt");
            expect(TextureArrayBridge.resolveRenderableSpriteId(Identifier.ofVanilla("block/does_not_exist")) == dirt,
                "unknown renderable lookup should fall back to dirt");
            expect(TextureArrayBridge.resolveSpriteId("minecraft:block/overflow_ctm_tile") == 3,
                "diagnostic sprite lookup should expose in-capacity CTM tiles");
            TextureArrayBridge.setRenderableSpriteCapacityForTest(3);
            expect(TextureArrayBridge.resolveSpriteId("minecraft:block/overflow_ctm_tile") == -1,
                "overflow sprite lookup should reject ids the native texture array cannot upload");
            expect(TextureArrayBridge.resolveRenderableSpriteId(
                    Identifier.ofVanilla("block/overflow_ctm_tile")) == dirt,
                "overflow renderable sprite lookup should fall back to a valid material-safe sprite");
            expect(TextureArrayBridge.renderableSpriteCapacityForTest() == 3,
                "diagnostics should expose the active native renderable sprite capacity");
            expect(TextureArrayBridge.resolveRenderableTextureGlId(MissingSprite.getMissingSpriteId(), 11) == 22,
                "renderable GL texture lookup should replace explicit missing texture bindings");
            expect(TextureArrayBridge.resolveRenderableTextureGlId(Identifier.ofVanilla("textures/entity/missing.png"), 11) == 22,
                "renderable GL texture lookup should replace missing GL ids from invalid texture resources");
            expect(TextureArrayBridge.resolveRenderableTextureGlId(Identifier.ofVanilla("textures/entity/cow/cow.png"), 33) == 33,
                "renderable GL texture lookup should keep valid texture bindings");
        } finally {
            TextureTracker.textureID2GLID.clear();
            TextureTracker.textureID2GLID.putAll(oldTextureIds);
            TextureArrayBridge.setRenderableSpriteCapacityForTest(oldRenderableCapacity);
            TextureArrayBridge.setSortedSpriteIds(List.of(SPRITE, Identifier.ofVanilla("block/glass")));
        }
    }

    private static void malformedModelJsonFallsBackToLowerPriorityResource() {
        Identifier modelId = Identifier.ofVanilla("models/block/oak_leaves.json");
        Resource validLowerModel = resource("""
            {
              "parent": "minecraft:block/cube_all",
              "textures": {
                "all": "minecraft:block/oak_leaves"
              }
            }
            """);
        Resource malformedTopModel = resource("""
            {
              "parent": "minecraft:block/block",
              "textures": {
                "all": "minecraft:block/oak_leaves"
              },
              "elements": [
                {
                  "from": [0, 0, 0],
                  "to": [16, 16, 16],
                  "rotation": {
                    "origin": [8, 8, 8],
                    "angle": -35
                  },
                  "faces": {
                    "north": {"texture": "#all"}
                  }
                }
              ]
            }
            """);

        Optional<Resource> fallback = ResourcePackModelFallback.selectFallbackForTest(
            modelId, List.of(validLowerModel, malformedTopModel));
        expect(fallback.isPresent(), "malformed high-priority model should use valid lower-priority model");
        expect(readResourceString(fallback.get()).contains("cube_all"),
            "model fallback should return the lower-priority valid resource");

        Optional<Resource> selectedFallback = ResourcePackModelFallback.selectFallbackForTest(
            modelId, Optional.of(malformedTopModel), List.of(malformedTopModel, validLowerModel));
        expect(selectedFallback.isPresent(),
            "model fallback should validate the selected top resource instead of assuming resource-list order");
        expect(readResourceString(selectedFallback.get()).contains("cube_all"),
            "selected-model fallback should return the lower-priority valid resource");

        Optional<Resource> unchanged = ResourcePackModelFallback.selectFallbackForTest(
            modelId, List.of(validLowerModel, validLowerModel));
        expect(unchanged.isEmpty(), "valid high-priority model should not be overridden");

        Optional<Resource> nonModel = ResourcePackModelFallback.selectFallbackForTest(
            Identifier.ofVanilla("textures/block/oak_leaves.png"),
            List.of(validLowerModel, malformedTopModel));
        expect(nonModel.isEmpty(), "model fallback must not affect texture resources");
    }

    private static void modelTextureReferenceRepairAvoidsMissingSprites() {
        Identifier modelId = Identifier.ofVanilla("models/block/pack_typo_block.json");
        Resource typoModel = resource("""
            {
              "parent": "minecraft:block/block",
              "textures": {
                "leaf": "minecraft:blockoak_leaves",
                "terracotta": "minecraft:block/light_blueterracotta",
                "farmland": "minecraft:block/farmland_dirt",
                "bad": "minecraft:color"
              },
              "elements": [
                {
                  "from": [0, 0, 0],
                  "to": [16, 16, 16],
                  "faces": {
                    "north": {"texture": "#missing"},
                    "south": {"texture": "#leaf"}
                  }
                }
              ]
            }
            """);

        Optional<Resource> repaired = ResourcePackModelFallback.selectRepairedModelForTest(
            modelId, Optional.of(typoModel));
        expect(repaired.isPresent(), "model texture repair should wrap models with known bad texture references");
        String json = readResourceString(repaired.get());
        expect(json.contains("minecraft:block/oak_leaves"),
            "model texture repair should split missing block path separators");
        expect(json.contains("minecraft:block/light_blue_terracotta"),
            "model texture repair should normalize known pack color typos");
        expect(json.contains("minecraft:block/dirt"),
            "model texture repair should route unsafe missing references to a stable fallback");

        Optional<Resource> nonModel = ResourcePackModelFallback.selectRepairedModelForTest(
            Identifier.ofVanilla("textures/block/oak_leaves.png"), Optional.of(typoModel));
        expect(nonModel.isEmpty(), "model texture repair must not affect non-model resources");
    }

    private static void materialCompatFlagsDefaultEnabled() {
        expect(Options.materialCompatEnabled, "material compatibility must default enabled");
        expect(Options.materialCompatCtmEnabled, "CTM compatibility must default enabled");
        expect(Options.materialCompatRandomEnabled, "random compatibility must default enabled");
        expect(Options.materialCompatNaturalEnabled, "natural texture compatibility must default enabled");
        expect(Options.materialCompatColorsEnabled, "color properties compatibility must default enabled");
        expect(Options.materialCompatOverlaysEnabled, "overlay compatibility must default enabled");
        expect(Options.materialCompatLegacyMcPatcherEnabled, "legacy MCPatcher compatibility must default enabled");
        expect(Options.materialCompatPhysicalEmissiveEnabled, "physical emissive compatibility must default enabled");
    }

    private static void materialCompatLegacyDisabledOptionsMigrateToDefaults() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldLegacy = Options.materialCompatLegacyMcPatcherEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        try {
            Properties props = new Properties();
            props.setProperty("materialCompatEnabled", "false");
            props.setProperty("materialCompatCtmEnabled", "false");
            props.setProperty("materialCompatRandomEnabled", "false");
            props.setProperty("materialCompatNaturalEnabled", "false");
            props.setProperty("materialCompatColorsEnabled", "false");
            props.setProperty("materialCompatOverlaysEnabled", "false");
            props.setProperty("materialCompatLegacyMcPatcherEnabled", "false");
            props.setProperty("materialCompatPhysicalEmissiveEnabled", "false");

            expect(Options.materialCompatDefaultsMigrationAppliesForTest(23, props),
                "v23 all-disabled material compatibility config should migrate to enabled defaults");
            expect(!Options.materialCompatDefaultsMigrationAppliesForTest(24, props),
                "current-version material compatibility config should preserve explicit toggles");
            props.setProperty("materialCompatColorsEnabled", "true");
            expect(!Options.materialCompatDefaultsMigrationAppliesForTest(23, props),
                "legacy material compatibility migration should preserve partial opt-in configs");
            props.setProperty("materialCompatColorsEnabled", "false");

            Options.materialCompatEnabled = false;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = false;
            Options.materialCompatOverlaysEnabled = false;
            Options.materialCompatLegacyMcPatcherEnabled = false;
            Options.materialCompatPhysicalEmissiveEnabled = false;
            Options.applyMaterialCompatDefaultsMigrationForTest(23, props);
            expect(Options.materialCompatEnabled
                    && Options.materialCompatCtmEnabled
                    && Options.materialCompatRandomEnabled
                    && Options.materialCompatNaturalEnabled
                    && Options.materialCompatColorsEnabled
                    && Options.materialCompatOverlaysEnabled
                    && Options.materialCompatLegacyMcPatcherEnabled
                    && Options.materialCompatPhysicalEmissiveEnabled,
                "legacy material compatibility migration should enable the whole OptiFine/Iris feature set");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatLegacyMcPatcherEnabled = oldLegacy;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
        }
    }

    private static void materialCompatScannerRecognizesCoreFeatures() {
        Path root = null;
        try {
            root = Files.createTempDirectory("radser-material-compat-fixture");
            Files.createDirectories(root.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(root.resolve("assets/minecraft/optifine/ctm/stone"));
            Files.createDirectories(root.resolve("assets/minecraft/optifine/colormap/blocks/terracotta"));
            Files.createDirectories(root.resolve("assets/minecraft/optifine/lightmap"));
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"fixture\"}}", StandardCharsets.UTF_8);
            Files.write(root.resolve("assets/minecraft/textures/block/stone.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_s.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_n.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_specular.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_normal.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_roughness.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_metallic.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_height.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_ao.png"), new byte[] {0});
            Files.writeString(root.resolve("assets/minecraft/texture.properties"),
                "format=lab-pbr/1.3\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("assets/minecraft/optifine/emissive.properties"),
                "suffix=_e\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("assets/minecraft/optifine/natural.properties"),
                "stone=4F\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("assets/minecraft/optifine/colormap/blocks/terracotta/terracotta.properties"),
                "format=fixed\ncolor=985e44\nblocks=terracotta\n", StandardCharsets.UTF_8);
            Files.writeString(root.resolve("assets/minecraft/optifine/ctm/stone/stone.properties"),
                "method=ctm\nmatchTiles=stone\ntiles=0-46\nconnect=block\n", StandardCharsets.UTF_8);
            Files.write(root.resolve("assets/minecraft/optifine/lightmap/world0.png"),
                lightmapPngBytesForTest(0xFF220000, 0xFF002200, 0xFF660000, 0xFF006600));

            JsonObject report = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanPackJsonForTest(root.toString())).getAsJsonObject();
            JsonObject counts = report.getAsJsonObject("counts");
            expect(report.get("scannable").getAsBoolean(), "compat fixture should be scannable");
            expect(counts.get("specularMaps").getAsInt() == 2, "scanner should count specular map aliases");
            expect(counts.get("normalMaps").getAsInt() == 2, "scanner should count normal map aliases");
            expect(counts.get("roughnessScalarMaps").getAsInt() == 1, "scanner should count roughness scalar maps");
            expect(counts.get("metallicScalarMaps").getAsInt() == 1, "scanner should count metallic scalar maps");
            expect(counts.get("heightScalarMaps").getAsInt() == 1, "scanner should count height scalar maps");
            expect(counts.get("aoScalarMaps").getAsInt() == 1, "scanner should count AO scalar maps");
            expect(counts.get("albedoPng").getAsInt() == 1, "scanner should not count PBR aliases as albedo");
            JsonObject coverage = report.getAsJsonObject("labpbrCoverage");
            expect(coverage.get("roughnessBases").getAsInt() == 1, "coverage should count roughness bases");
            expect(coverage.get("metallicBases").getAsInt() == 1, "coverage should count metallic bases");
            expect(coverage.get("heightBases").getAsInt() == 1, "coverage should count height bases");
            expect(coverage.get("aoBases").getAsInt() == 1, "coverage should count AO bases");
            expect(coverage.get("albedoWithSpecularOrScalarAndNormalOrScalar").getAsInt() == 1,
                "coverage should count direct-or-scalar renderable LabPBR candidates");
            expect(counts.get("textureProperties").getAsInt() == 1, "scanner should count texture.properties");
            expect(counts.get("emissiveProperties").getAsInt() == 1, "scanner should count emissive.properties");
            expect(counts.get("naturalProperties").getAsInt() == 1, "scanner should count natural.properties");
            expect(counts.get("blockColormapProperties").getAsInt() == 1,
                "scanner should count OptiFine block colormap properties");
            expect(counts.get("customLightmapPng").getAsInt() == 1,
                "scanner should count OptiFine custom lightmap PNGs");
            expect(counts.get("optifineCtmProperties").getAsInt() == 1, "scanner should count OptiFine CTM properties");
            expect(report.getAsJsonObject("compatFeatures").get("ctm").getAsInt() == 1, "scanner should classify CTM records");
            expect(report.getAsJsonObject("compatFeatures").get("custom_lightmap_images").getAsInt() == 1,
                "scanner should classify custom lightmap image features");
            expect(report.getAsJsonObject("compatFeatures").get("block_colormap_properties").getAsInt() == 1,
                "scanner should classify fixed block colormap records");
            expect(report.getAsJsonArray("compatRecords").size() >= 3, "scanner should emit structured compatibility records");
            expect(hasCompatRecord(report.getAsJsonArray("compatRecords"), "block_colormap_properties", "color", "985e44"),
                "block colormap record should retain fixed color value");
            expect(report.getAsJsonArray("samples").size() >= 3, "scanner should retain property samples");
        } catch (IOException e) {
            throw new AssertionError("material compat scanner fixture failed", e);
        } finally {
            if (root != null) {
                deleteTree(root);
            }
        }
    }

    private static void materialCompatRunScanMarksActivePacksAndParsesRecords() {
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-run");
            Path resourcePacks = run.resolve("resourcepacks");
            Path active = resourcePacks.resolve("Active Pack");
            Path later = resourcePacks.resolve("A Later Pack");
            Path inactive = resourcePacks.resolve("Inactive Pack");
            Files.createDirectories(active.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(active.resolve("assets/minecraft/optifine/ctm/stone"));
            Files.createDirectories(active.resolve("assets/minecraft/optifine/ctm/repeat"));
            Files.createDirectories(later.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(inactive.resolve("assets/minecraft/textures/block"));
            Files.writeString(run.resolve("options.txt"),
                "resourcePacks:[\"vanilla\",\"fabric\",\"file/Active Pack\",\"file/A Later Pack\"]\n"
                    + "incompatibleResourcePacks:[\"file/Active Pack\"]\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/textures/block/stone.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/stone_s.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/stone_n.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/dirt.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/dirt_roughness.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/dirt_height.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/texture.properties"),
                "format=lab-pbr/1.3\n", StandardCharsets.UTF_8);
            Files.writeString(active.resolve("assets/minecraft/optifine/emissive.properties"),
                "suffix.emissive=_e\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/0.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1_s.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1_n.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/2.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/2_roughness.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/2_height.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/ctm/stone/stone.properties"),
                "method=ctm\nmatchTiles=stone\ntiles=textures/block/stone 0-2 custom_tile\nconnect=block\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/optifine/ctm/repeat/0.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/repeat/1.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/repeat/2.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/repeat/3.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/ctm/repeat/repeat.properties"),
                "method=overlay_repeat\nmatchTiles=stone\nwidth=2\nheight=2\n", StandardCharsets.UTF_8);
            Files.write(later.resolve("assets/minecraft/textures/block/sand.png"), new byte[] {0});
            Files.write(inactive.resolve("assets/minecraft/textures/block/dirt.png"), new byte[] {0});

            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            JsonArray activePacks = status.getAsJsonArray("activePacks");
            expect(activePacks.size() == 2, "run scan should expose only active file packs");
            JsonObject activePack = activePacks.get(0).getAsJsonObject();
            JsonObject laterPack = activePacks.get(1).getAsJsonObject();
            expect("Active Pack".equals(activePack.get("name").getAsString()), "active pack name should round trip from options.txt");
            expect("A Later Pack".equals(laterPack.get("name").getAsString()),
                "active pack diagnostics must preserve options.txt order instead of directory sort");
            expect(activePack.get("activeOrder").getAsInt() == 2, "active pack should carry selected-stack order");
            expect(laterPack.get("activeOrder").getAsInt() == 3, "later active pack should carry selected-stack order");
            expect(laterPack.get("activePriority").getAsInt() > activePack.get("activePriority").getAsInt(),
                "later selected packs should report higher active priority");
            expect(activePack.get("active").getAsBoolean(), "active pack should be marked active");
            expect(activePack.get("incompatibleSelected").getAsBoolean(), "incompatible selected pack should be flagged");
            expect(activePack.getAsJsonObject("labpbrCoverage").get("albedoWithSpecularAndNormal").getAsInt() == 1,
                "active pack should report paired LabPBR sidecars");
            expect(activePack.getAsJsonObject("labpbrCoverage")
                    .get("albedoWithSpecularOrScalarAndNormalOrScalar").getAsInt() == 2,
                "active pack should count scalar-composed LabPBR candidates");
            expect(activePack.getAsJsonObject("compatFeatures").get("ctm").getAsInt() == 2,
                "active pack should report parsed CTM feature records");
            expect(hasCompatRecord(activePack.getAsJsonArray("compatRecords"), "ctm", "method", "ctm"),
                "CTM record should retain method value");
            expect(hasCompatRecord(activePack.getAsJsonArray("compatRecords"), "ctm", "method", "overlay_repeat"),
                "CTM record should retain overlay_repeat method value");
            expect(hasCompatRecord(activePack.getAsJsonArray("compatRecords"), "emissive_properties", "suffix.emissive", "_e"),
                "emissive record should retain suffix value");
            JsonObject ctmDeps = activePack.getAsJsonObject("ctmAtlasDependencies");
            JsonObject activeCtmDeps = status.getAsJsonObject("activeCtmAtlasDependencies");
            expect(ctmDeps.get("uniqueTiles").getAsInt() == 9,
                "CTM dependency index should expand path, explicit ranges, and inferred repeat tiles");
            expect(activeCtmDeps.get("uniqueTiles").getAsInt() == 9,
                "active CTM dependency aggregate should include active pack dependencies");
            expect(ctmDeps.get("presentTiles").getAsInt() == 8, "CTM dependency index should count present tiles");
            expect(ctmDeps.get("missingTiles").getAsInt() == 1, "CTM dependency index should count missing tiles");
            expect(ctmDeps.get("tilesWithSpecular").getAsInt() == 2, "CTM dependency index should see tile specular sidecars");
            expect(ctmDeps.get("tilesWithNormal").getAsInt() == 2, "CTM dependency index should see tile normal sidecars");
            expect(ctmDeps.get("tilesWithSpecularOrScalar").getAsInt() == 3,
                "CTM dependency index should see scalar specular fallbacks");
            expect(ctmDeps.get("tilesWithNormalOrScalar").getAsInt() == 3,
                "CTM dependency index should see scalar normal fallbacks");
            expect(ctmDeps.get("tilesWithAnyMaterialSidecar").getAsInt() == 3,
                "CTM dependency index should count materialized tile sidecars once per tile");
            expect(ctmDeps.get("tilesRequiringAtlasAdmission").getAsInt() == 8,
                "CTM dependency index should mark non-vanilla CTM tiles for atlas admission");
            expect(ctmDeps.get("presentTilesRequiringAtlasAdmission").getAsInt() == 7,
                "CTM dependency index should separate present and missing admission tiles");
            JsonArray dependencies = ctmDeps.getAsJsonArray("dependencies");
            expect(hasCtmDependency(dependencies, "assets/minecraft/textures/block/stone.png", true, "minecraft:block/stone"),
                "CTM dependency index should include base texture path dependencies");
            expect(hasCtmDependency(dependencies, "assets/minecraft/optifine/ctm/stone/custom_tile.png", false,
                ResourcePackCompatCtmTiles.atlasSpriteIdentifier("assets/minecraft/optifine/ctm/stone/custom_tile.png")),
                "CTM dependency index should retain missing relative tile dependencies");
            expect(hasCtmDependency(dependencies, "assets/minecraft/optifine/ctm/repeat/3.png", true,
                ResourcePackCompatCtmTiles.atlasSpriteIdentifier("assets/minecraft/optifine/ctm/repeat/3.png")),
                "CTM dependency index should infer omitted overlay_repeat tiles from width and height");
        } catch (IOException e) {
            throw new AssertionError("material compat run fixture failed", e);
        } finally {
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatWritesParserArtifactDumps() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-dumps");
            Path active = run.resolve("resourcepacks/Active Pack");
            Files.createDirectories(active.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(active.resolve("assets/minecraft/optifine/ctm/stone"));
            Files.createDirectories(run.resolve("radiance"));
            Files.writeString(run.resolve("options.txt"),
                "resourcePacks:[\"vanilla\",\"fabric\",\"file/Active Pack\"]\n"
                    + "incompatibleResourcePacks:[\"file/Active Pack\"]\n", StandardCharsets.UTF_8);
            Files.writeString(run.resolve("radiance/options.properties"),
                "optionsVersion=24\n"
                    + "materialCompatEnabled=true\n"
                    + "materialCompatCtmEnabled=true\n"
                    + "materialCompatRandomEnabled=false\n"
                    + "materialCompatNaturalEnabled=false\n"
                    + "materialCompatColorsEnabled=false\n"
                    + "materialCompatOverlaysEnabled=false\n"
                    + "materialCompatLegacyMcPatcherEnabled=true\n"
                    + "materialCompatPhysicalEmissiveEnabled=false\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/textures/block/stone.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/stone_s.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/stone_n.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/texture.properties"),
                "format=lab-pbr/1.3\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/0.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1_s.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1_n.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/ctm/stone/stone.properties"),
                "method=ctm\nmatchTiles=stone\ntiles=0-2\nconnect=block\n", StandardCharsets.UTF_8);

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = false;
            Options.materialCompatOverlaysEnabled = false;
            Options.materialCompatPhysicalEmissiveEnabled = false;

            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.writeRunDirectoryReportsForTest(run.toString())).getAsJsonObject();
            JsonObject provenance = status.getAsJsonObject("optionsProvenance");
            expect(provenance != null, "compat dump status should expose options provenance");
            expect(provenance.get("persistedPresent").getAsBoolean(),
                "options provenance should see persisted options.properties");
            expect(provenance.get("persistedRenderingConsumesCompatibility").getAsBoolean(),
                "persisted options should report compatibility consumption enabled");
            expect(!provenance.get("materialCompatFlagsMismatch").getAsBoolean(),
                "matching live and persisted material flags should not warn as mismatched");
            JsonObject reports = status.getAsJsonObject("diagnosticReports");
            expect(reports != null && reports.has("packIndex"), "compat dump status should expose pack index path");
            expect(reports.has("textureAssets"), "compat dump status should expose texture asset path");
            expect(reports.has("materialSets"), "compat dump status should expose material set path");
            expect(reports.has("ctmRules"), "compat dump status should expose CTM rule path");
            expect(reports.has("rulePrecedence"), "compat dump status should expose rule precedence path");
            expect(reports.has("parserWarnings"), "compat dump status should expose parser warning path");

            Path logs = run.resolve("radiance/logs");
            Path textureAssetsPath = logs.resolve("radser-texture-assets.json");
            Path materialSetsPath = logs.resolve("radser-material-sets.json");
            Path ctmRulesPath = logs.resolve("radser-ctm-rules.json");
            Path precedencePath = logs.resolve("radser-rule-precedence.json");
            Path warningsPath = logs.resolve("radser-parser-warnings.json");
            expect(Files.isRegularFile(logs.resolve("radser-pack-index.json")), "pack index dump should be written");
            expect(Files.isRegularFile(textureAssetsPath), "texture asset dump should be written");
            expect(Files.isRegularFile(materialSetsPath), "material set dump should be written");
            expect(Files.isRegularFile(ctmRulesPath), "CTM rule dump should be written");
            expect(Files.isRegularFile(precedencePath), "rule precedence dump should be written");
            expect(Files.isRegularFile(warningsPath), "parser warning dump should be written");

            JsonObject assets = JsonParser.parseString(Files.readString(textureAssetsPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
            expect("radser_texture_assets_v1".equals(assets.get("schema").getAsString()),
                "texture asset dump should use stable schema");
            expect(assets.getAsJsonObject("aggregateCounts").get("albedoPng").getAsInt() == 1,
                "texture asset dump should aggregate active texture-directory albedo maps");
            expect(assets.getAsJsonObject("aggregateLabpbrCoverage").get("albedoWithSpecularAndNormal").getAsInt() == 1,
                "texture asset dump should aggregate paired LabPBR sidecars");

            JsonObject materials = JsonParser.parseString(Files.readString(materialSetsPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
            expect("direct_labpbr_normal_alpha".equals(
                    materials.getAsJsonObject("source").get("displacementSource").getAsString()),
                "material set dump should name direct LabPBR normal-alpha displacement");
            expect("diagnostic_metadata_only".equals(
                    materials.getAsJsonObject("source").get("heightAlphaRangeRole").getAsString()),
                "material set dump must not present alpha range as shader normalization input");
            expect("selected_tile_sidecar_then_matchtiles_base_sidecar".equals(
                    materials.getAsJsonObject("source").get("ctmTileMaterialFallback").getAsString()),
                "material set dump should expose CTM tile companion fallback policy");
            expect("resolved_sprite_id_material_set_v1".equals(
                    materials.getAsJsonObject("source").get("materialSetBindingPolicy").getAsString()),
                "material set dump should expose resolved-sprite alias binding");
            expect("pbr_texture_id".equals(
                    materials.getAsJsonObject("source").get("shaderLookupKey").getAsString()),
                "material set dump should expose shader lookup key");
            expect(!materials.getAsJsonObject("source").get("nativeMaterialSetTablePresent").getAsBoolean(),
                "material set dump should not claim a native material-set table");

            JsonObject ctmRules = JsonParser.parseString(Files.readString(ctmRulesPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
            expect("radser_ctm_rules_v1".equals(ctmRules.get("schema").getAsString()),
                "CTM rule dump should use stable schema");
            expect(ctmRules.get("ruleCount").getAsInt() == 1, "CTM rule dump should contain parsed CTM rules");
            expect(ctmRules.getAsJsonObject("methodCounts").get("ctm").getAsInt() == 1,
                "CTM rule dump should count CTM methods");

            JsonObject precedence = JsonParser.parseString(Files.readString(precedencePath, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonObject policy = precedence.getAsJsonObject("policy");
            expect("resolved_sprite_id_material_set_alias".equals(policy.get("shaderInput").getAsString()),
                "rule precedence dump should describe resolved-sprite material aliasing");
            expect("pbr_texture_id".equals(policy.get("shaderLookupKey").getAsString()),
                "rule precedence dump should expose shader lookup key");
            expect(!policy.get("nativeMaterialSetTablePresent").getAsBoolean(),
                "rule precedence dump should not claim a native material-set table");
            JsonObject consumption = precedence.getAsJsonObject("compatibilityConsumption");
            expect(consumption.get("javaSideRuleParsing").getAsBoolean(),
                "rule precedence dump should expose Java-side rule parsing");
            expect(consumption.get("javaChunkQuadRuleResolution").getAsBoolean(),
                "rule precedence dump should expose chunk-side rule resolution");
            expect(!consumption.get("shaderSideRuleParsing").getAsBoolean(),
                "rule precedence dump should not claim shader-side rule parsing");
            expect(!consumption.get("shaderSideRuleNativeBinding").getAsBoolean(),
                "rule precedence dump should not claim raw shader rule binding");

            JsonObject warnings = JsonParser.parseString(Files.readString(warningsPath, StandardCharsets.UTF_8))
                .getAsJsonObject();
            expect(warnings.get("warningCount").getAsInt() >= 1,
                "parser warning dump should retain incompatible selected pack diagnostics");
        } catch (IOException e) {
            throw new AssertionError("material compat parser dump fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatDiagnosticsDetectPersistedOptionDivergence() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldLegacy = Options.materialCompatLegacyMcPatcherEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-provenance");
            Path active = run.resolve("resourcepacks/Active Pack");
            Files.createDirectories(active.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(run.resolve("radiance"));
            Files.writeString(run.resolve("options.txt"),
                "resourcePacks:[\"vanilla\",\"fabric\",\"file/Active Pack\"]\n", StandardCharsets.UTF_8);
            Files.writeString(run.resolve("radiance/options.properties"),
                "optionsVersion=24\n"
                    + "materialCompatEnabled=true\n"
                    + "materialCompatCtmEnabled=true\n"
                    + "materialCompatRandomEnabled=true\n"
                    + "materialCompatNaturalEnabled=true\n"
                    + "materialCompatColorsEnabled=true\n"
                    + "materialCompatOverlaysEnabled=true\n"
                    + "materialCompatLegacyMcPatcherEnabled=true\n"
                    + "materialCompatPhysicalEmissiveEnabled=true\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/textures/block/stone.png"), new byte[] {0});

            Options.materialCompatEnabled = false;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = false;
            Options.materialCompatOverlaysEnabled = false;
            Options.materialCompatLegacyMcPatcherEnabled = false;
            Options.materialCompatPhysicalEmissiveEnabled = false;

            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.writeRunDirectoryReportsForTest(run.toString())).getAsJsonObject();
            expect(!status.get("renderingConsumesCompatibility").getAsBoolean(),
                "live disabled material flags should keep root consumption false");
            JsonObject provenance = status.getAsJsonObject("optionsProvenance");
            expect(provenance.get("persistedRenderingConsumesCompatibility").getAsBoolean(),
                "persisted options should still expose intended material consumption");
            expect(provenance.get("materialCompatFlagsMismatch").getAsBoolean(),
                "provenance should flag live/persisted material flag mismatch");
            expect(provenance.get("liveCompatibilityLikelyUnloadedFromPersistedOptions").getAsBoolean(),
                "provenance should identify stale or pre-load live compatibility flags");

            JsonObject warnings = JsonParser.parseString(Files.readString(
                run.resolve("radiance/logs/radser-parser-warnings.json"), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray warningArray = warnings.getAsJsonArray("warnings");
            boolean found = false;
            for (JsonElement warningElement : warningArray) {
                JsonObject warning = warningElement.getAsJsonObject();
                found |= "material_compat_live_options_disagree_with_persisted_options".equals(
                    warning.get("code").getAsString());
            }
            expect(found, "parser warnings should call out persisted/live material flag divergence");
        } catch (IOException e) {
            throw new AssertionError("material compat options provenance fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatLegacyMcPatcherEnabled = oldLegacy;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatDiagnosticsReportNaturalConsumption() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-natural-status");
            Files.createDirectories(run.resolve("resourcepacks"));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = true;
            Options.materialCompatColorsEnabled = false;
            Options.materialCompatOverlaysEnabled = false;
            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            expect(status.get("renderingConsumesCompatibility").getAsBoolean(),
                "natural compatibility should count as rendering consumption");
            expect(status.getAsJsonObject("flags").get("natural").getAsBoolean(),
                "diagnostics should expose natural compatibility flag");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("naturalTextureUvTransforms").getAsBoolean(),
                "diagnostics should expose natural UV transform consumption");
        } catch (IOException e) {
            throw new AssertionError("material compat natural diagnostics fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatDiagnosticsReportColorConsumption() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-color-status");
            Files.createDirectories(run.resolve("resourcepacks"));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = true;
            Options.materialCompatOverlaysEnabled = false;
            Options.materialCompatPhysicalEmissiveEnabled = false;
            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            expect(status.get("renderingConsumesCompatibility").getAsBoolean(),
                "color properties compatibility should count as rendering consumption");
            expect(status.getAsJsonObject("flags").get("colors").getAsBoolean(),
                "diagnostics should expose color properties compatibility flag");
            JsonObject consumption = status.getAsJsonObject("compatibilityConsumption");
            expect(consumption.get("colorPropertiesFixedBlockTints").getAsBoolean(),
                "diagnostics should expose fixed block tint consumption");
            expect(consumption.get("optifineFixedBlockColormapProperties").getAsBoolean(),
                "diagnostics should expose fixed OptiFine block colormap consumption");
            expect(consumption.get("colorPropertiesBiomePalettes").getAsBoolean(),
                "diagnostics should expose rendered color.properties biome palette sampling");
            expect(!consumption.get("colorPropertiesBiomePalettesMetadataOnly").getAsBoolean(),
                "diagnostics should not mark rendered biome palettes as metadata-only");
        } catch (IOException e) {
            throw new AssertionError("material compat color diagnostics fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatDiagnosticsReportLightmapConsumption() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-lightmap-status");
            Files.createDirectories(run.resolve("resourcepacks"));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = true;
            Options.materialCompatOverlaysEnabled = false;
            Options.materialCompatPhysicalEmissiveEnabled = false;
            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            JsonObject consumption = status.getAsJsonObject("compatibilityConsumption");
            expect(consumption.get("optifineCustomLightmaps").getAsBoolean(),
                "diagnostics should expose rendered OptiFine custom lightmap consumption");
            expect(!consumption.get("optifineCustomLightmapsMetadataOnly").getAsBoolean(),
                "diagnostics should not mark custom lightmaps as metadata-only");
        } catch (IOException e) {
            throw new AssertionError("material compat lightmap diagnostics fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatDiagnosticsReportPhysicalEmissiveConsumption() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-emissive-status");
            Files.createDirectories(run.resolve("resourcepacks"));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = false;
            Options.materialCompatOverlaysEnabled = false;
            Options.materialCompatPhysicalEmissiveEnabled = true;
            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            expect(status.get("renderingConsumesCompatibility").getAsBoolean(),
                "physical emissive compatibility should count as rendering consumption");
            expect(status.getAsJsonObject("flags").get("physicalEmissive").getAsBoolean(),
                "diagnostics should expose physical emissive compatibility flag");
            JsonObject consumption = status.getAsJsonObject("compatibilityConsumption");
            expect(consumption.get("optifineEmissiveOverlaySprites").getAsBoolean(),
                "diagnostics should expose OptiFine emissive overlay sprite consumption");
            expect(consumption.get("overlayLayerEmission").getAsBoolean(),
                "diagnostics should expose emissive overlay shader composition");
        } catch (IOException e) {
            throw new AssertionError("material compat physical emissive diagnostics fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void materialCompatDiagnosticsReportShaderBlockLayerConsumption() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldNatural = Options.materialCompatNaturalEnabled;
        boolean oldColors = Options.materialCompatColorsEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Path run = null;
        try {
            run = Files.createTempDirectory("radser-material-compat-layer-status");
            Files.createDirectories(run.resolve("resourcepacks"));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatNaturalEnabled = false;
            Options.materialCompatColorsEnabled = false;
            Options.materialCompatOverlaysEnabled = true;
            Options.materialCompatPhysicalEmissiveEnabled = false;
            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            expect(status.get("renderingConsumesCompatibility").getAsBoolean(),
                "shader block layers should count as rendering consumption");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("shaderBlockPropertiesLayerAlphaModes").getAsBoolean(),
                "diagnostics should expose shader block.properties layer alpha mode consumption");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("javaSideRuleParsing").getAsBoolean(),
                "diagnostics should expose Java-side shader block.N side rule parsing");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("javaChunkQuadRuleResolution").getAsBoolean(),
                "diagnostics should expose chunk-side rule resolution into packed vertex metadata");
            expect(!status.getAsJsonObject("compatibilityConsumption")
                    .get("shaderSideRuleParsing").getAsBoolean(),
                "diagnostics should not claim shader-side rule parsing");
            expect(!status.getAsJsonObject("compatibilityConsumption")
                    .get("shaderSideRuleNativeBinding").getAsBoolean(),
                "diagnostics should not claim raw shader-side rule binding");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("shaderConsumesResolvedSpriteAndMaterialIds").getAsBoolean(),
                "diagnostics should expose shader consumption of resolved sprite and material ids");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("overlayLayerEmission").getAsBoolean(),
                "diagnostics should expose CTM overlay layer emission consumption");
            expect(status.getAsJsonObject("compatibilityConsumption")
                    .get("ctmOverlayRandomLayerEmission").getAsBoolean(),
                "diagnostics should expose overlay_random layer emission consumption");
        } catch (IOException e) {
            throw new AssertionError("material compat shader block layer diagnostics fixture failed", e);
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatNaturalEnabled = oldNatural;
            Options.materialCompatColorsEnabled = oldColors;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            if (run != null) {
                deleteTree(run);
            }
        }
    }

    private static void ctmAtlasAdmissionUtilitiesAreStable() {
        Properties ctm = new Properties();
        ctm.setProperty("method", "ctm");
        List<String> inferred = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/glass/glass.properties", ctm);
        expect(inferred.size() == 47, "CTM method should infer the documented 47-tile set when tiles is omitted");
        expect("assets/minecraft/optifine/ctm/glass/0.png".equals(inferred.get(0)),
            "CTM inferred tiles should resolve relative to the property file");
        expect("assets/minecraft/optifine/ctm/glass/46.png".equals(inferred.get(46)),
            "CTM inferred tile range should include the last tile");

        List<String> explicit = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/glass/glass.properties",
            "textures/block/glass 2-0 minecraft:block/stone custom_tile", "random");
        expect("assets/minecraft/textures/block/glass.png".equals(explicit.get(0)),
            "textures/ dependencies should resolve into vanilla atlas assets");
        expect("assets/minecraft/optifine/ctm/glass/2.png".equals(explicit.get(1)),
            "numeric CTM tiles should resolve relative to the property directory");
        expect("assets/minecraft/textures/block/stone.png".equals(explicit.get(4)),
            "namespaced tile references should resolve through textures/");
        String synthetic = ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
            "assets/minecraft/optifine/ctm/glass/2.png");
        expect(synthetic.startsWith("minecraft:radser_ctm/optifine/ctm/glass/2_"),
            "non-textures CTM tiles should get stable synthetic atlas sprite ids");
        expect(!ResourcePackCompatCtmTiles.requiresAtlasAdmission(
            "assets/minecraft/textures/block/glass.png"), "vanilla texture sprites should not need synthetic atlas admission");
        expect(ResourcePackCompatCtmTiles.requiresAtlasAdmission(
            "assets/minecraft/optifine/ctm/glass/2.png"), "OptiFine CTM tiles should need atlas admission");

        List<String> overlayRandom = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/leaf_overlay/sand_overlay.properties",
            "1-4 <skip>", "overlay_random");
        expect(overlayRandom.size() == 4, "overlay_random CTM dependencies should preserve real tiles and ignore <skip>");
        expect("assets/minecraft/optifine/ctm/leaf_overlay/1.png".equals(overlayRandom.get(0)),
            "overlay_random numeric tiles should resolve relative to the property directory");

        List<String> overlayCtm = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/leaf_overlay/sand_overlay.properties",
            "", "overlay_ctm");
        expect(overlayCtm.size() == 17, "overlay_ctm should infer the documented 17-tile overlay set");
        List<String> overlayFixed = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/leaf_overlay/fixed.properties",
            "", "overlay_fixed");
        expect(overlayFixed.size() == 1 && overlayFixed.get(0).endsWith("/0.png"),
            "overlay_fixed should infer a single overlay tile");
        List<String> overlayRepeat = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/leaf_overlay/repeat.properties",
            "0-3", "overlay_repeat");
        expect(overlayRepeat.size() == 4 && overlayRepeat.get(3).endsWith("/3.png"),
            "overlay_repeat dependencies should expand explicit repeat tiles");

        Properties inferredRepeatProps = new Properties();
        inferredRepeatProps.setProperty("method", "overlay_repeat");
        inferredRepeatProps.setProperty("width", "2");
        inferredRepeatProps.setProperty("height", "3");
        List<String> inferredOverlayRepeat = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(
            "assets/minecraft/optifine/ctm/leaf_overlay/inferred_repeat.properties",
            inferredRepeatProps);
        expect(inferredOverlayRepeat.size() == 6 && inferredOverlayRepeat.get(5).endsWith("/5.png"),
            "overlay_repeat should infer width*height tile dependencies when tiles is omitted");
    }

    private static void ctmAtlasSourceCollectsPresentTiles() {
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        Options.materialCompatPhysicalEmissiveEnabled = false;
        FakeResourceManager resourceManager = new FakeResourceManager();
        try {
            resourceManager.add("minecraft:optifine/ctm/glass/glass.properties",
                "method=ctm\ntiles=textures/block/glass 0-2 missing\n".getBytes(StandardCharsets.UTF_8));
            resourceManager.add("minecraft:textures/block/glass.png", new byte[] {1});
            resourceManager.add("minecraft:optifine/ctm/glass/0.png", new byte[] {2});
            resourceManager.add("minecraft:optifine/ctm/glass/1.png", new byte[] {3});
            resourceManager.add("minecraft:optifine/ctm/glass/2.png", new byte[] {4});

            CapturingSpriteRegions regions = new CapturingSpriteRegions();
            ResourcePackCompatAtlasSource.AdmissionSummary summary =
                ResourcePackCompatAtlasSource.admitCtmSpritesForTest(resourceManager, regions);
            expect(summary.considered() == 5, "CTM atlas source should consider all expanded dependencies");
            expect(summary.natural() == 1, "CTM atlas source should skip vanilla atlas sprites");
            expect(summary.added() == 3, "CTM atlas source should admit present non-textures tiles");
            expect(summary.missing() == 1, "CTM atlas source should count missing CTM tiles");
            expect(regions.resources.size() == 3, "CTM atlas source should add admitted sprites to atlas regions");
            Identifier expected = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/glass/1.png"));
            expect(expected != null && regions.resources.containsKey(expected),
                "CTM atlas source should use the utility synthetic sprite id");
            expect(Identifier.ofVanilla("optifine/ctm/glass/1_n.png").equals(
                    ResourcePackCompatCtmTiles.ctmSidecarResourceIdentifier(expected, "_n")),
                "admitted synthetic CTM sprites should resolve real pack-authored normal sidecars");
            expect(Identifier.ofVanilla("optifine/ctm/glass/1_roughness.png").equals(
                    ResourcePackCompatCtmTiles.ctmSidecarResourceIdentifier(expected, "_roughness")),
                "admitted synthetic CTM sprites should resolve scalar roughness sidecars");
            expect(Identifier.ofVanilla("optifine/ctm/glass/1_height.png").equals(
                    ResourcePackCompatCtmTiles.ctmSidecarResourceIdentifier(expected, "_height")),
                "admitted synthetic CTM sprites should resolve scalar height sidecars");
            expect(ResourcePackCompatCtmTiles.ctmMaterialFallbackSidecarResourceIdentifiers(expected, "_n")
                    .contains(Identifier.ofVanilla("textures/block/glass_n.png")),
                "admitted synthetic CTM sprites should fall back to inferred base normal sidecars");
            expect(ResourcePackCompatCtmTiles.ctmMaterialFallbackSidecarResourceIdentifiers(expected, "_s")
                    .contains(Identifier.ofVanilla("textures/block/glass_s.png")),
                "admitted synthetic CTM sprites should fall back to inferred base specular sidecars");
        } finally {
            ResourcePackCompatCtmTiles.clearRegisteredCtmSpriteAssetPaths();
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
        }
    }

    private static void emissiveTextureResolverMapsSuffixesAndAtlasAdmission() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldPhysical = Options.materialCompatPhysicalEmissiveEnabled;
        boolean oldLegacy = Options.materialCompatLegacyMcPatcherEnabled;
        try {
            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/emissive.properties",
                "suffix.emissive=_glow\n".getBytes(StandardCharsets.UTF_8));
            expect("_glow".equals(ResourcePackEmissiveTextureResolver.suffix(manager, false)),
                "emissive resolver should parse suffix.emissive");
            expect(Identifier.ofVanilla("block/lamp").equals(
                    ResourcePackEmissiveTextureResolver.baseSpriteForEmissiveSprite(
                        Identifier.ofVanilla("block/lamp_glow"), "_glow")),
                "emissive resolver should map suffixed sprite ids to base sprites");
            expect(ResourcePackEmissiveTextureResolver.isDefaultEmissiveResource(
                    Identifier.ofVanilla("textures/block/lamp_e.png")),
                "default emissive suffix should recognize _e resources");

            Identifier lamp = Identifier.ofVanilla("block/lamp");
            Identifier lampGlow = Identifier.ofVanilla("block/lamp_glow");
            ResourcePackEmissiveTextureResolver.clearRegisteredOverlaySprites();
            ResourcePackEmissiveTextureResolver.registerOverlaySprite(lampGlow, lamp);
            expect(lampGlow.equals(ResourcePackEmissiveTextureResolver.registeredOverlayForBaseSprite(lamp)),
                "emissive resolver should map base sprites back to registered overlay sprites");
            expect(ResourcePackEmissiveTextureResolver.usesShaderOverlayForBaseSprite(lamp),
                "emissive overlays should use shader composition when the folded overlay slot is free");
            expect(!ResourcePackEmissiveTextureResolver.requiresGeometryOverlayForBaseSprite(lamp),
                "emissive overlays should not emit fallback geometry when the shader slot is free");
            ResourcePackEmissiveTextureResolver.registerOverlaySprite(lampGlow, lamp, false);
            expect(!ResourcePackEmissiveTextureResolver.usesShaderOverlayForBaseSprite(lamp),
                "emissive overlays should not use shader composition when another overlay owns the slot");
            expect(ResourcePackEmissiveTextureResolver.requiresGeometryOverlayForBaseSprite(lamp),
                "emissive overlays should request geometry fallback when the folded overlay slot is occupied");

            Identifier syntheticBase = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/lamp/0.png"));
            Identifier syntheticEmissive = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/lamp/0_glow.png"));
            ResourcePackEmissiveTextureResolver.clearRegisteredOverlaySprites();
            ResourcePackEmissiveTextureResolver.registerOverlaySprite(syntheticEmissive, syntheticBase);
            expect(syntheticBase != null && syntheticBase.equals(
                    ResourcePackEmissiveTextureResolver.baseSpriteForEmissiveSprite(syntheticEmissive, "_glow")),
                "registered CTM emissive overlays should map back to hashed synthetic base sprites");

            Options.materialCompatEnabled = true;
            Options.materialCompatPhysicalEmissiveEnabled = true;
            Options.materialCompatLegacyMcPatcherEnabled = false;
            FakeResourceManager atlasManager = new FakeResourceManager();
            atlasManager.add("minecraft:optifine/emissive.properties",
                "suffix=_glow\n".getBytes(StandardCharsets.UTF_8));
            atlasManager.add("minecraft:optifine/ctm/lamp/lamp.properties",
                "method=fixed\ntiles=0\n".getBytes(StandardCharsets.UTF_8));
            atlasManager.add("minecraft:optifine/ctm/lamp/0.png", new byte[] {1});
            atlasManager.add("minecraft:optifine/ctm/lamp/0_glow.png", new byte[] {2});
            CapturingSpriteRegions regions = new CapturingSpriteRegions();
            ResourcePackCompatAtlasSource.AdmissionSummary summary =
                ResourcePackCompatAtlasSource.admitCtmSpritesForTest(atlasManager, regions);
            expect(summary.added() == 2, "CTM atlas admission should include physical emissive sidecar overlays");
            expect(syntheticEmissive != null && regions.resources.containsKey(syntheticEmissive),
                "CTM emissive overlay sidecar should be admitted under its synthetic sprite id");
            expect(syntheticBase != null && syntheticBase.equals(
                    ResourcePackEmissiveTextureResolver.baseSpriteForEmissiveSprite(syntheticEmissive, "_glow")),
                "admitted CTM emissive sidecar should register its base sprite mapping");
            expect(syntheticEmissive != null && syntheticEmissive.equals(
                    ResourcePackEmissiveTextureResolver.registeredOverlayForBaseSprite(syntheticBase)),
                "admitted CTM emissive sidecar should register its overlay sprite mapping");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            Options.materialCompatLegacyMcPatcherEnabled = oldLegacy;
        }
    }

    private static void textureVariantResolverRegistryReportsCompiledRules() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier fixed = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/fixed.png"));
            Identifier random0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/0.png"));
            Identifier overlay = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/overlay.png"));
            expect(fixed != null && random0 != null && overlay != null,
                "variant registry fixture identifiers should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, fixed, random0, overlay));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = true;
            Options.materialCompatOverlaysEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/ctm/stone/fixed.properties",
                "method=fixed\nmatchTiles=stone\ntiles=fixed\nfaces=north west\nconnect=tile\n"
                    .getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/ctm/stone/random.properties",
                ("method=random\nmatchBlocks=minecraft:stone[axis=x]\ntiles=0\nweights=3\n"
                    + "symmetry=opposite\nlinked=true\n").getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/ctm/stone/overlay.properties",
                ("method=overlay_fixed\nmatchTiles=stone\ntiles=overlay\nlayer=translucent\n"
                    + "tintIndex=1\nbiomes=plains\nheights=60-80\n").getBytes(StandardCharsets.UTF_8));

            JsonObject registry = JsonParser.parseString(
                ResourcePackTextureVariantResolver.registryJsonForTest(manager, false, 8)).getAsJsonObject();
            expect("radser_runtime_variant_rule_registry_v1".equals(registry.get("schema").getAsString()),
                "variant registry should expose a stable schema");
            expect(registry.get("ruleCount").getAsInt() == 3,
                "variant registry should report compiled rule count");
            JsonObject precedencePolicy = registry.getAsJsonObject("precedencePolicy");
            expect("resource_manager_effective_resource".equals(precedencePolicy.get("samePath").getAsString()),
                "variant registry should document same-path resource-manager precedence");
            expect("property_id_ascending_within_root".equals(precedencePolicy.get("ruleOrder").getAsString()),
                "variant registry should document property path rule ordering");
            expect("first_enabled_matching_rule".equals(precedencePolicy.get("nonOverlayResolution").getAsString()),
                "variant registry should document first-match non-overlay resolution");
            expect("stack_enabled_matching_overlay_groups_in_precedence_order".equals(
                    precedencePolicy.get("overlayResolution").getAsString()),
                "variant registry should document stacked overlay resolution");
            expect(precedencePolicy.get("overlayStackLimit").getAsInt() == 16,
                "variant registry should document overlay stack cap");
            expect(registry.getAsJsonObject("methodCounts").get("fixed").getAsInt() == 1,
                "variant registry should count fixed rules");
            expect(registry.getAsJsonObject("methodCounts").get("random").getAsInt() == 1,
                "variant registry should count random rules");
            expect(registry.getAsJsonObject("methodCounts").get("overlay_fixed").getAsInt() == 1,
                "variant registry should count overlay fixed rules");

            JsonArray rules = registry.getAsJsonArray("rules");
            JsonObject fixedRule = ruleWithMethod(rules, "fixed");
            expect("optifine/ctm".equals(fixedRule.get("root").getAsString()),
                "variant registry should expose rule root");
            expect("unknown".equals(fixedRule.get("sourcePack").getAsString()),
                "variant registry should expose unknown source pack for null-pack fixtures");
            expect(fixedRule.get("precedenceOrdinal").getAsInt() == fixedRule.get("ordinal").getAsInt(),
                "variant registry should expose stable rule precedence ordinal");
            expect(fixedRule.get("enabledByOptions").getAsBoolean(),
                "fixed rule should report option-enabled status");
            expect("tile_as_block".equals(fixedRule.get("connectMode").getAsString()),
                "variant registry should preserve tile connect mode");
            expect(fixedRule.getAsJsonArray("faces").toString().contains("north")
                    && fixedRule.getAsJsonArray("faces").toString().contains("west"),
                "variant registry should preserve face predicates");
            JsonObject fixedOutput = fixedRule.getAsJsonArray("outputs").get(0).getAsJsonObject();
            int fixedId = TextureArrayBridge.resolveSpriteId(fixed.toString());
            expect(fixedOutput.get("spriteId").getAsInt() == fixedId,
                "variant registry should resolve output sprite ids");
            expect(fixedOutput.get("materialSetId").getAsInt() == fixedId,
                "variant registry should expose material-set id alias for outputs");
            expect("resolved_sprite_id_material_set_v1".equals(
                    fixedOutput.get("materialSetBindingPolicy").getAsString()),
                "variant outputs should expose material-set alias policy");
            expect("pbr_texture_id".equals(fixedOutput.get("shaderLookupKey").getAsString()),
                "variant outputs should expose shader lookup key");
            expect(!fixedOutput.get("nativeMaterialSetTablePresent").getAsBoolean(),
                "variant outputs should not claim a native material-set table");
            expect(fixedOutput.get("materialSetAliasesResolvedSprite").getAsBoolean(),
                "variant outputs should mark material set as resolved sprite alias");

            JsonObject randomRule = ruleWithMethod(rules, "random");
            expect(randomRule.get("linkedRandom").getAsBoolean(),
                "variant registry should expose linked random groups");
            expect("opposite".equals(randomRule.get("randomSymmetry").getAsString()),
                "variant registry should expose random symmetry");
            JsonObject matchBlock = randomRule.getAsJsonArray("matchBlocks").get(0).getAsJsonObject();
            expect("minecraft:stone".equals(matchBlock.get("blockId").getAsString()),
                "variant registry should expose normalized matchBlocks ids");
            expect("axis".equals(matchBlock.getAsJsonArray("states").get(0).getAsJsonObject()
                    .get("name").getAsString()),
                "variant registry should expose blockstate predicates");

            JsonObject overlayRule = ruleWithMethod(rules, "overlay_fixed");
            expect(overlayRule.get("overlayRule").getAsBoolean(),
                "variant registry should mark overlay rules");
            expect(overlayRule.getAsJsonArray("choices").get(0).getAsJsonObject()
                    .get("spriteId").getAsInt() == TextureArrayBridge.resolveSpriteId(overlay.toString()),
                "variant registry should resolve overlay choice sprite ids");
            expect("minecraft:plains".equals(overlayRule.getAsJsonObject("biomes")
                    .getAsJsonArray("values").get(0).getAsString()),
                "variant registry should expose biome predicates");
            expect(overlayRule.getAsJsonObject("heights").getAsJsonArray("ranges")
                    .get(0).getAsJsonObject().get("min").getAsInt() == 60,
                "variant registry should expose height predicates");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverHonorsDeterministicRulePrecedence() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier first = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/precedence/first.png"));
            Identifier second = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/precedence/second.png"));
            expect(first != null && second != null,
                "variant precedence fixture identifiers should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, first, second));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = true;
            Options.materialCompatOverlaysEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/ctm/precedence/10-second.properties",
                "method=fixed\nmatchTiles=stone\ntiles=second\n".getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/ctm/precedence/00-first.properties",
                "method=fixed\nmatchTiles=stone\ntiles=first\n".getBytes(StandardCharsets.UTF_8));

            ResourcePackTextureVariantResolver.ResolverIndex index =
                ResourcePackTextureVariantResolver.buildForTest(manager, false);
            expect(index.ruleCountForTest() == 2,
                "variant precedence fixture should compile both matching rules");
            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            int firstId = TextureArrayBridge.resolveSpriteId(first.toString());
            expect(index.resolveForTest(stone, stoneId, new BlockPos(2, 64, 2), Direction.NORTH) == firstId,
                "variant resolver should use the first matching rule after explicit property-id ordering");

            JsonObject registry = JsonParser.parseString(
                ResourcePackTextureVariantResolver.registryJsonForTest(manager, false, 8)).getAsJsonObject();
            JsonArray rules = registry.getAsJsonArray("rules");
            expect(rules.get(0).getAsJsonObject().get("id").getAsString().endsWith("00-first.properties"),
                "variant registry should report property-id sorted precedence first");
            expect(rules.get(0).getAsJsonObject().get("precedenceOrdinal").getAsInt() == 0
                    && rules.get(1).getAsJsonObject().get("precedenceOrdinal").getAsInt() == 1,
                "variant registry should expose monotonic precedence ordinals");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsFixedAndRandomSprites() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldLegacy = Options.materialCompatLegacyMcPatcherEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier fixed = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/fixed.png"));
            Identifier random0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/0.png"));
            Identifier random1 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/1.png"));
            Identifier random2 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/2.png"));
            Identifier random3 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/3.png"));
            Identifier dirt = Identifier.ofVanilla("block/dirt");
            Identifier lamp = Identifier.ofVanilla("block/redstone_lamp");
            Identifier litLamp = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/lamp/lit.png"));
            expect(fixed != null && random0 != null && random1 != null && random2 != null && random3 != null
                    && litLamp != null,
                "variant fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, fixed, random0, random1, random2, random3,
                dirt, lamp, litLamp));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = true;
            Options.materialCompatLegacyMcPatcherEnabled = false;
            expect(ResourcePackCompatAtlasSource.shouldInjectForAtlas(Identifier.ofVanilla("blocks")),
                "CTM/random flags should enable block atlas admission");
            Options.materialCompatCtmEnabled = false;
            expect(ResourcePackCompatAtlasSource.shouldInjectForAtlas(Identifier.ofVanilla("blocks")),
                "random-only material compat should still enable CTM tile atlas admission");
            Options.materialCompatCtmEnabled = true;

            FakeResourceManager fixedManager = new FakeResourceManager();
            fixedManager.add("minecraft:optifine/ctm/stone/fixed.properties",
                "method=fixed\nmatchTiles=stone\ntiles=fixed\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex fixedIndex =
                ResourcePackTextureVariantResolver.buildForTest(fixedManager, false);
            expect(fixedIndex.ruleCountForTest() == 1, "fixed rule should compile");
            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            int dirtId = TextureArrayBridge.resolveSpriteId(dirt.toString());
            int fixedId = TextureArrayBridge.resolveSpriteId(fixed.toString());
            expect(fixedIndex.resolveForTest(stone, stoneId, new BlockPos(1, 2, 3), Direction.NORTH) == fixedId,
                "fixed rule should replace matching source sprite with admitted CTM tile");
            Options.materialCompatCtmEnabled = false;
            expect(fixedIndex.resolveForTest(stone, stoneId, new BlockPos(1, 2, 3), Direction.NORTH) == stoneId,
                "fixed rule should respect CTM feature flag");
            Options.materialCompatCtmEnabled = true;

            FakeResourceManager stateManager = new FakeResourceManager();
            stateManager.add("minecraft:optifine/ctm/lamp/lamp.properties",
                "method=fixed\nmatchBlocks=redstone_lamp[lit=true]\ntiles=lit\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex stateIndex =
                ResourcePackTextureVariantResolver.buildForTest(stateManager, false);
            expect(stateIndex.ruleCountForTest() == 1, "stateful matchBlocks rule should compile");
            expect(ResourcePackTextureVariantResolver.blockPredicateMatchesForTest(
                    "redstone_lamp[lit=true]", "minecraft:redstone_lamp", Map.of("lit", "true")),
                "matchBlocks should honor true block-state predicates");
            expect(!ResourcePackTextureVariantResolver.blockPredicateMatchesForTest(
                    "redstone_lamp[lit=true]", "minecraft:redstone_lamp", Map.of("lit", "false")),
                "matchBlocks should reject the same block id when state predicates fail");
            expect(ResourcePackTextureVariantResolver.blockPredicateMatchesForTest(
                    "minecraft:redstone_lamp:lit=true", "redstone_lamp", Map.of("lit", "true")),
                "legacy colon-form block-state predicates should normalize to Minecraft block ids");

            FakeResourceManager randomManager = new FakeResourceManager();
            randomManager.add("minecraft:optifine/ctm/stone/random.properties",
                "method=random\nmatchTiles=stone\ntiles=0 1\nweights=1 3\nrandomLoops=2\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex randomIndex =
                ResourcePackTextureVariantResolver.buildForTest(randomManager, false);
            expect(randomIndex.ruleCountForTest() == 1, "random rule should compile");
            BlockPos pos = new BlockPos(9, 65, -4);
            int a = randomIndex.resolveForTest(stone, stoneId, pos, Direction.EAST);
            int b = randomIndex.resolveForTest(stone, stoneId, pos, Direction.EAST);
            expect(a == b, "random rule should be deterministic for the same position and face");
            expect(a == TextureArrayBridge.resolveSpriteId(random0.toString())
                    || a == TextureArrayBridge.resolveSpriteId(random1.toString()),
                "random rule should choose one of the admitted CTM variant sprites");
            Options.materialCompatRandomEnabled = false;
            expect(randomIndex.resolveForTest(stone, stoneId, pos, Direction.EAST) == stoneId,
                "random rule should respect random feature flag");
            Options.materialCompatRandomEnabled = true;

            FakeResourceManager oppositeManager = new FakeResourceManager();
            oppositeManager.add("minecraft:optifine/ctm/stone/opposite.properties",
                "method=random\nmatchTiles=stone\ntiles=0 1\nsymmetry=opposite\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex oppositeIndex =
                ResourcePackTextureVariantResolver.buildForTest(oppositeManager, false);
            expect(oppositeIndex.resolveForTest(stone, stoneId, pos, Direction.NORTH)
                    == oppositeIndex.resolveForTest(stone, stoneId, pos, Direction.SOUTH),
                "random symmetry=opposite should share variants across north/south faces");
            expect(oppositeIndex.resolveForTest(stone, stoneId, pos, Direction.EAST)
                    == oppositeIndex.resolveForTest(stone, stoneId, pos, Direction.WEST),
                "random symmetry=opposite should share variants across east/west faces");

            FakeResourceManager allManager = new FakeResourceManager();
            allManager.add("minecraft:optifine/ctm/stone/all.properties",
                "method=random\nmatchTiles=stone\ntiles=0 1\nsymmetry=all\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex allIndex =
                ResourcePackTextureVariantResolver.buildForTest(allManager, false);
            int north = allIndex.resolveForTest(stone, stoneId, pos, Direction.NORTH);
            expect(north == allIndex.resolveForTest(stone, stoneId, pos, Direction.SOUTH)
                    && north == allIndex.resolveForTest(stone, stoneId, pos, Direction.EAST)
                    && north == allIndex.resolveForTest(stone, stoneId, pos, Direction.WEST)
                    && north == allIndex.resolveForTest(stone, stoneId, pos, Direction.UP)
                    && north == allIndex.resolveForTest(stone, stoneId, pos, Direction.DOWN),
                "random symmetry=all should share variants across every face");

            FakeResourceManager linkedManager = new FakeResourceManager();
            linkedManager.add("minecraft:optifine/ctm/stone/linked.properties",
                "method=random\nmatchTiles=stone dirt\ntiles=0 1 2 3\nweights=4 2\nlinked=true\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex linkedIndex =
                ResourcePackTextureVariantResolver.buildForTest(linkedManager, false);
            int linkedBase = linkedIndex.resolveForTest(stone, stoneId, new BlockPos(11, 64, 17), Direction.NORTH);
            expect(linkedBase == linkedIndex.resolveForTest(stone, stoneId, new BlockPos(11, 65, 17), Direction.NORTH)
                    && linkedBase == linkedIndex.resolveForTest(dirt, dirtId, new BlockPos(11, -12, 17),
                        Direction.NORTH),
                "linked random rules should share a column seed across source tiles and Y positions");
            int averageDefaultWeight = ResourcePackTextureVariantResolver.weightForTest(
                "4 2", 4, 2);
            expect(averageDefaultWeight == 3,
                "random weights omitted after explicit entries should default to the explicit average");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatLegacyMcPatcherEnabled = oldLegacy;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverRespectsBiomeAndHeightPredicates() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier fixed = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/predicate/fixed.png"));
            Identifier legacy = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/predicate/legacy.png"));
            expect(fixed != null && legacy != null, "predicate fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, fixed, legacy));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = false;

            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            int fixedId = TextureArrayBridge.resolveSpriteId(fixed.toString());
            int legacyId = TextureArrayBridge.resolveSpriteId(legacy.toString());

            FakeResourceManager predicateManager = new FakeResourceManager();
            predicateManager.add("minecraft:optifine/ctm/predicate/predicate.properties",
                String.join("\n",
                    "method=fixed",
                    "matchTiles=stone",
                    "tiles=fixed",
                    "heights=64-70 80",
                    "biomes=desert"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex predicate =
                ResourcePackTextureVariantResolver.buildForTest(predicateManager, false);
            expect(predicate.ruleCountForTest() == 1, "height and biome predicate rule should compile");
            expect(predicate.resolveForTest(stone, stoneId, new BlockPos(0, 64, 0), Direction.NORTH,
                    "minecraft:desert") == fixedId,
                "height and biome predicates should allow matching positions");
            expect(predicate.resolveForTest(stone, stoneId, new BlockPos(0, 63, 0), Direction.NORTH,
                    "minecraft:desert") == stoneId,
                "height predicates should reject positions below the allowed range");
            expect(predicate.resolveForTest(stone, stoneId, new BlockPos(0, 80, 0), Direction.NORTH,
                    "minecraft:desert") == fixedId,
                "single-value height predicates should match exact Y values");
            expect(predicate.resolveForTest(stone, stoneId, new BlockPos(0, 64, 0), Direction.NORTH,
                    "minecraft:plains") == stoneId,
                "biome predicates should reject unmatched biome ids");
            expect(predicate.resolveForTest(stone, stoneId, new BlockPos(0, 64, 0), Direction.NORTH) == stoneId,
                "biome predicates should not match when no biome context is available");

            FakeResourceManager invertedBiomeManager = new FakeResourceManager();
            invertedBiomeManager.add("minecraft:optifine/ctm/predicate/inverted_biome.properties",
                String.join("\n",
                    "method=fixed",
                    "matchTiles=stone",
                    "tiles=fixed",
                    "biomes=!desert badlands"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex invertedBiome =
                ResourcePackTextureVariantResolver.buildForTest(invertedBiomeManager, false);
            expect(invertedBiome.resolveForTest(stone, stoneId, new BlockPos(0, 64, 0), Direction.NORTH,
                    "minecraft:plains") == fixedId,
                "inverted biome predicates should allow biomes outside the excluded list");
            expect(invertedBiome.resolveForTest(stone, stoneId, new BlockPos(0, 64, 0), Direction.NORTH,
                    "minecraft:desert") == stoneId,
                "inverted biome predicates should reject explicitly excluded biomes");

            FakeResourceManager legacyManager = new FakeResourceManager();
            legacyManager.add("minecraft:optifine/ctm/predicate/legacy.properties",
                String.join("\n",
                    "method=fixed",
                    "matchTiles=stone",
                    "tiles=legacy",
                    "minHeight=-16",
                    "maxHeight=-8"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex legacyIndex =
                ResourcePackTextureVariantResolver.buildForTest(legacyManager, false);
            expect(legacyIndex.resolveForTest(stone, stoneId, new BlockPos(0, -12, 0), Direction.UP) == legacyId,
                "legacy minHeight/maxHeight predicates should allow in-range negative Y values");
            expect(legacyIndex.resolveForTest(stone, stoneId, new BlockPos(0, -7, 0), Direction.UP) == stoneId,
                "legacy minHeight/maxHeight predicates should reject out-of-range Y values");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsRepeatSprites() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier r0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/repeat/0.png"));
            Identifier r1 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/repeat/1.png"));
            Identifier r2 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/repeat/2.png"));
            Identifier r3 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/repeat/3.png"));
            expect(r0 != null && r1 != null && r2 != null && r3 != null, "repeat fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, r0, r1, r2, r3));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = false;

            FakeResourceManager repeatManager = new FakeResourceManager();
            repeatManager.add("minecraft:optifine/ctm/repeat/repeat.properties",
                "method=repeat\nmatchTiles=stone\nwidth=2\nheight=2\nlayer=cutout\ntintIndex=1\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex repeat =
                ResourcePackTextureVariantResolver.buildForTest(repeatManager, false);
            expect(repeat.ruleCountForTest() == 1, "repeat rule should compile");
            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(0, 0, 0), Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(r0.toString()),
                "repeat CTM should select row-major tile 0 at pattern origin");
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(1, 0, 0), Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(r1.toString()),
                "repeat CTM should advance horizontally across north-facing quads");
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(0, 1, 0), Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(r2.toString()),
                "repeat CTM should advance vertically across side faces");
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(1, 1, 0), Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(r3.toString()),
                "repeat CTM should select the lower-right pattern tile");
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(2, 2, 0), Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(r0.toString()),
                "repeat CTM should wrap by width and height");
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(0, 0, 1), Direction.EAST)
                    == TextureArrayBridge.resolveSpriteId(r1.toString()),
                "repeat CTM should rotate horizontal axes for east-facing quads");
            ResourcePackTextureVariantResolver.ResolvedBlockSprite detailed =
                repeat.resolveDetailedForTest(stone, stoneId, new BlockPos(0, 0, 1), Direction.EAST);
            expect(detailed.ruleMatched(), "repeat CTM detailed result should report a matched rule");
            expect(detailed.alphaMode() == PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT,
                "repeat CTM detailed result should carry rule layer alpha mode");
            expect(detailed.tintOverride(), "repeat CTM detailed result should carry rule tint metadata");
            Options.materialCompatCtmEnabled = false;
            expect(repeat.resolveForTest(stone, stoneId, new BlockPos(1, 1, 0), Direction.NORTH) == stoneId,
                "repeat CTM should respect the CTM feature flag");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverHonorsStateAxisRepeatOrientation() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier basaltSide = Identifier.ofVanilla("block/basalt_side");
            ArrayList<Identifier> sprites = new ArrayList<>();
            sprites.add(basaltSide);
            ArrayList<Identifier> tiles = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                Identifier tile = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                    "assets/minecraft/optifine/ctm/state_axis/" + i + ".png"));
                expect(tile != null, "state-axis repeat fixture ids should parse");
                tiles.add(tile);
                sprites.add(tile);
            }
            TextureArrayBridge.setSortedSpriteIds(sprites);

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/ctm/state_axis/basalt_side.properties",
                "method=repeat\nmatchTiles=basalt_side\ntiles=0-8\nwidth=3\nheight=3\norient=state_axis\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex index =
                ResourcePackTextureVariantResolver.buildForTest(manager, false);
            expect(index.ruleCountForTest() == 1, "state-axis repeat rule should compile");

            int sourceId = TextureArrayBridge.resolveSpriteId(basaltSide.toString());
            BlockPos pos = new BlockPos(5, 9, 7);

            expect(index.resolveForTest(basaltSide, sourceId, Direction.Axis.Y, pos, Direction.SOUTH)
                    == TextureArrayBridge.resolveSpriteId(tiles.get(1).toString()),
                "state-axis repeat should preserve unrotated Y-axis side coordinates");
            expect(index.resolveForTest(basaltSide, sourceId, Direction.Axis.Z, pos, Direction.SOUTH)
                    == TextureArrayBridge.resolveSpriteId(tiles.get(2).toString()),
                "state-axis repeat should remap Z-axis south faces through the local top plane");
            expect(index.resolveForTest(basaltSide, sourceId, Direction.Axis.X, pos, Direction.EAST)
                    == TextureArrayBridge.resolveSpriteId(tiles.get(2).toString()),
                "state-axis repeat should remap X-axis east faces through the local top plane");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverHonorsTextureRepeatOrientation() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier log = Identifier.ofVanilla("block/oak_log");
            ArrayList<Identifier> sprites = new ArrayList<>();
            sprites.add(log);
            ArrayList<Identifier> tiles = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                Identifier tile = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                    "assets/minecraft/optifine/ctm/texture_orient/" + i + ".png"));
                expect(tile != null, "texture-orient repeat fixture ids should parse");
                tiles.add(tile);
                sprites.add(tile);
            }
            TextureArrayBridge.setSortedSpriteIds(sprites);

            RepeatTextureBasis basis = PBRVertexConsumer.repeatTextureBasisForTest(textureBasisVertexData());
            expect(basis != null, "texture repeat basis should be inferred from axis-aligned quad UVs");
            expect(basis.uAxis() == Direction.Axis.Y && basis.uSign() > 0,
                "texture repeat basis should infer positive world Y as texture U");
            expect(basis.vAxis() == Direction.Axis.X && basis.vSign() > 0,
                "texture repeat basis should infer positive world X as texture V");

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/ctm/texture_orient/oak_log.properties",
                "method=repeat\nmatchTiles=oak_log\ntiles=0-17\nwidth=3\nheight=6\norient=texture\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex index =
                ResourcePackTextureVariantResolver.buildForTest(manager, false);
            expect(index.ruleCountForTest() == 1, "texture-orient repeat rule should compile");

            int sourceId = TextureArrayBridge.resolveSpriteId(log.toString());
            BlockPos pos = new BlockPos(5, 8, 7);
            expect(index.resolveForTest(log, sourceId, basis, pos, Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(tiles.get(17).toString()),
                "orient=texture should select repeat tiles from the quad UV basis");
            expect(index.resolveForTest(log, sourceId, (RepeatTextureBasis) null, pos, Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(tiles.get(8).toString()),
                "orient=texture without a quad UV basis should retain face/state fallback coordinates");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static int[] textureBasisVertexData() {
        int[] data = new int[32];
        putBakedVertex(data, 0, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        putBakedVertex(data, 1, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f);
        putBakedVertex(data, 2, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f);
        putBakedVertex(data, 3, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f);
        return data;
    }

    private static void putBakedVertex(int[] data, int vertex, float x, float y, float z, float u, float v) {
        int base = vertex * 8;
        data[base] = Float.floatToRawIntBits(x);
        data[base + 1] = Float.floatToRawIntBits(y);
        data[base + 2] = Float.floatToRawIntBits(z);
        data[base + 4] = Float.floatToRawIntBits(u);
        data[base + 5] = Float.floatToRawIntBits(v);
    }

    private static void textureVariantResolverSkipsOptifineOnlyRules() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier log = Identifier.ofVanilla("block/acacia_log");
            Identifier optifineTile = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/optifine_only/optifine.png"));
            Identifier textureTile = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/optifine_only/texture.png"));
            expect(optifineTile != null && textureTile != null, "optifine-only fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(log, optifineTile, textureTile));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/ctm/optifine_only/acacia_log1.properties",
                String.join("\n",
                    "method=repeat",
                    "matchTiles=acacia_log",
                    "tiles=optifine",
                    "width=1",
                    "height=1",
                    "faces=north",
                    "optifineOnly=true"
                ).getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/ctm/optifine_only/acacia_log7.properties",
                String.join("\n",
                    "method=repeat",
                    "matchTiles=acacia_log",
                    "tiles=texture",
                    "width=1",
                    "height=1",
                    "faces=north",
                    "orient=texture"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex index =
                ResourcePackTextureVariantResolver.buildForTest(manager, false);
            expect(index.ruleCountForTest() == 1, "optifineOnly CTM rules should be skipped by RadSER");
            int sourceId = TextureArrayBridge.resolveSpriteId(log.toString());
            expect(index.resolveForTest(log, sourceId, new BlockPos(0, 0, 0), Direction.NORTH)
                    == TextureArrayBridge.resolveSpriteId(textureTile.toString()),
                "texture-oriented fallback rule should win after skipping optifineOnly rules");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsOverlayRandomSprites() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier sand = Identifier.ofVanilla("block/sand");
            Identifier overlay0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/sand_overlay/0.png"));
            expect(overlay0 != null, "overlay_random fixture id should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(sand, overlay0));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatOverlaysEnabled = true;

            FakeResourceManager overlayManager = new FakeResourceManager();
            overlayManager.add("minecraft:optifine/ctm/sand_overlay/sand_overlay.properties",
                String.join("\n",
                    "method=overlay_random",
                    "matchTiles=sand",
                    "faces=top",
                    "tiles=0",
                    "weights=1"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex overlays =
                ResourcePackTextureVariantResolver.buildForTest(overlayManager, false);
            expect(overlays.ruleCountForTest() == 1, "overlay_random rule should compile");
            int sandId = TextureArrayBridge.resolveSpriteId(sand.toString());
            int overlayId = TextureArrayBridge.resolveSpriteId(overlay0.toString());
            BlockPos pos = new BlockPos(3, 64, -7);
            expect(overlays.resolveForTest(sand, sandId, pos, Direction.UP) == sandId,
                "overlay_random must not replace the base block sprite");
            expect(overlays.resolveOverlayForTest(sand, sandId, pos, Direction.UP) == overlayId,
                "overlay_random should emit an overlay sprite on matching faces");
            expect(overlays.resolveOverlayForTest(sand, sandId, pos, Direction.NORTH) == -1,
                "overlay_random faces=top should not emit side overlays");

            Options.materialCompatOverlaysEnabled = false;
            expect(overlays.resolveOverlayForTest(sand, sandId, pos, Direction.UP) == -1,
                "overlay_random should respect the overlay feature flag");
            Options.materialCompatOverlaysEnabled = true;

            FakeResourceManager skipManager = new FakeResourceManager();
            skipManager.add("minecraft:optifine/ctm/sand_overlay/skip.properties",
                String.join("\n",
                    "method=overlay_random",
                    "matchTiles=sand",
                    "faces=top",
                    "tiles=<skip>",
                    "weights=100"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex skip =
                ResourcePackTextureVariantResolver.buildForTest(skipManager, false);
            expect(skip.ruleCountForTest() == 1, "all-skip overlay_random rule should compile");
            expect(skip.resolveOverlayForTest(sand, sandId, pos, Direction.UP) == -1,
                "<skip> overlay_random choices should emit no overlay geometry");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverStacksMatchingOverlayRules() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier first = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/overlay_stack/first.png"));
            Identifier second = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/overlay_stack/second.png"));
            expect(first != null && second != null,
                "overlay stack fixture identifiers should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, first, second));

            Options.materialCompatEnabled = true;
            Options.materialCompatOverlaysEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/ctm/overlay_stack/10-second.properties",
                String.join("\n",
                    "method=overlay_fixed",
                    "matchTiles=stone",
                    "faces=top",
                    "tiles=second"
                ).getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/ctm/overlay_stack/00-first.properties",
                String.join("\n",
                    "method=overlay_fixed",
                    "matchTiles=stone",
                    "faces=top",
                    "tiles=first",
                    "layer=translucent"
                ).getBytes(StandardCharsets.UTF_8));

            ResourcePackTextureVariantResolver.ResolverIndex overlays =
                ResourcePackTextureVariantResolver.buildForTest(manager, false);
            expect(overlays.ruleCountForTest() == 2,
                "overlay stack fixture should compile both matching rules");
            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            BlockOverlaySprite[] stacked =
                overlays.resolveOverlayDetailsForTest(stone, stoneId, new BlockPos(4, 64, 4), Direction.UP);
            expect(stacked.length == 2,
                "matching overlay rules should stack instead of stopping after the first rule");
            expect(stacked[0].spriteId() == TextureArrayBridge.resolveSpriteId(first.toString())
                    && stacked[1].spriteId() == TextureArrayBridge.resolveSpriteId(second.toString()),
                "overlay stack should preserve explicit property-id precedence order");
            expect(stacked[0].alphaMode() == 2,
                "stacked overlay should preserve per-rule translucent layer metadata");
            expect(overlays.resolveOverlayDetailsForTest(stone, stoneId,
                    new BlockPos(4, 64, 4), Direction.NORTH).length == 0,
                "stacked overlay rules should still honor face predicates");

            Options.materialCompatOverlaysEnabled = false;
            expect(overlays.resolveOverlayDetailsForTest(stone, stoneId,
                    new BlockPos(4, 64, 4), Direction.UP).length == 0,
                "overlay stack should respect the overlay feature flag");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsOverlaySprites() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            ArrayList<Identifier> sprites = new ArrayList<>();
            sprites.add(stone);
            Identifier[] overlayTiles = new Identifier[17];
            for (int i = 0; i < overlayTiles.length; i++) {
                overlayTiles[i] = ctmFixtureId("overlay", i);
                expect(overlayTiles[i] != null, "overlay fixture id should parse: " + i);
                sprites.add(overlayTiles[i]);
            }
            TextureArrayBridge.setSortedSpriteIds(sprites);

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatOverlaysEnabled = true;

            FakeResourceManager overlayManager = new FakeResourceManager();
            overlayManager.add("minecraft:optifine/ctm/overlay/overlay.properties",
                String.join("\n",
                    "method=overlay",
                    "matchTiles=stone",
                    "connectBlocks=sand",
                    "tiles=0-16"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex overlays =
                ResourcePackTextureVariantResolver.buildForTest(overlayManager, false);
            expect(overlays.ruleCountForTest() == 1, "overlay rule should compile");
            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());

            int[] all = overlays.resolveOverlaysWithConnectionsForTest(stone, stoneId, Direction.UP,
                Set.of(Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH), Set.of());
            expect(all.length == 1 && all[0] == TextureArrayBridge.resolveSpriteId(overlayTiles[8].toString()),
                "overlay should select tile 8 for all four side applications");

            int[] horizontal = overlays.resolveOverlaysWithConnectionsForTest(stone, stoneId, Direction.UP,
                Set.of(Direction.WEST, Direction.EAST), Set.of());
            expect(horizontal.length == 2
                    && horizontal[0] == TextureArrayBridge.resolveSpriteId(overlayTiles[9].toString())
                    && horizontal[1] == TextureArrayBridge.resolveSpriteId(overlayTiles[7].toString()),
                "overlay should emit left and right edge sprites for opposite horizontal applications");

            int[] vertical = overlays.resolveOverlaysWithConnectionsForTest(stone, stoneId, Direction.UP,
                Set.of(Direction.SOUTH, Direction.NORTH), Set.of());
            expect(vertical.length == 2
                    && vertical[0] == TextureArrayBridge.resolveSpriteId(overlayTiles[1].toString())
                    && vertical[1] == TextureArrayBridge.resolveSpriteId(overlayTiles[15].toString()),
                "overlay should emit down and up edge sprites for opposite vertical applications");

            int[] left = overlays.resolveOverlaysWithConnectionsForTest(stone, stoneId, Direction.UP,
                Set.of(Direction.WEST), Set.of());
            expect(left.length == 1 && left[0] == TextureArrayBridge.resolveSpriteId(overlayTiles[9].toString()),
                "overlay should emit the left edge sprite for a one-side application");

            Options.materialCompatOverlaysEnabled = false;
            expect(overlays.resolveOverlaysWithConnectionsForTest(stone, stoneId, Direction.UP,
                Set.of(Direction.WEST), Set.of()).length == 0,
                "overlay should respect the overlay feature flag");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsOverlayCtmRepeatAndFixedSprites() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier overlayCtm8 = ctmFixtureId("overlay_ctm", 8);
            Identifier overlayRepeat3 = ctmFixtureId("overlay_repeat", 3);
            Identifier overlayFixed0 = ctmFixtureId("overlay_fixed", 0);
            expect(overlayCtm8 != null && overlayRepeat3 != null && overlayFixed0 != null,
                "overlay selector fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, overlayCtm8, overlayRepeat3, overlayFixed0));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatOverlaysEnabled = true;

            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());

            FakeResourceManager overlayCtmManager = new FakeResourceManager();
            overlayCtmManager.add("minecraft:optifine/ctm/overlay_ctm/overlay.properties",
                String.join("\n",
                    "method=overlay_ctm",
                    "matchTiles=stone",
                    "connectBlocks=sand"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex overlayCtm =
                ResourcePackTextureVariantResolver.buildForTest(overlayCtmManager, false);
            expect(overlayCtm.ruleCountForTest() == 1, "overlay_ctm rule should compile");
            expect(overlayCtm.resolveForTest(stone, stoneId, new BlockPos(0, 64, 0), Direction.UP) == stoneId,
                "overlay_ctm must not replace the base sprite");
            int[] all = overlayCtm.resolveOverlaysWithConnectionsForTest(stone, stoneId, Direction.UP,
                Set.of(Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH), Set.of());
            expect(all.length == 1 && all[0] == TextureArrayBridge.resolveSpriteId(overlayCtm8.toString()),
                "overlay_ctm should use the 17-tile overlay mask selector");

            FakeResourceManager overlayRepeatManager = new FakeResourceManager();
            overlayRepeatManager.add("minecraft:optifine/ctm/overlay_repeat/repeat.properties",
                String.join("\n",
                    "method=overlay_repeat",
                    "matchTiles=stone",
                    "faces=top",
                    "width=2",
                    "height=2"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex overlayRepeat =
                ResourcePackTextureVariantResolver.buildForTest(overlayRepeatManager, false);
            expect(overlayRepeat.ruleCountForTest() == 1, "overlay_repeat rule should compile");
            expect(overlayRepeat.resolveOverlayForTest(stone, stoneId, new BlockPos(1, 64, 1), Direction.UP)
                    == TextureArrayBridge.resolveSpriteId(overlayRepeat3.toString()),
                "overlay_repeat should project repeat tiles on top faces");
            expect(overlayRepeat.resolveOverlayForTest(stone, stoneId, new BlockPos(1, 64, 1), Direction.NORTH)
                    == -1,
                "overlay_repeat should respect faces predicates");

            FakeResourceManager overlayFixedManager = new FakeResourceManager();
            overlayFixedManager.add("minecraft:optifine/ctm/overlay_fixed/fixed.properties",
                String.join("\n",
                    "method=overlay_fixed",
                    "matchTiles=stone",
                    "faces=top"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex overlayFixed =
                ResourcePackTextureVariantResolver.buildForTest(overlayFixedManager, false);
            expect(overlayFixed.ruleCountForTest() == 1, "overlay_fixed rule should compile");
            expect(overlayFixed.resolveOverlayForTest(stone, stoneId, new BlockPos(2, 64, 3), Direction.UP)
                    == TextureArrayBridge.resolveSpriteId(overlayFixed0.toString()),
                "overlay_fixed should emit its single overlay sprite");

            Options.materialCompatOverlaysEnabled = false;
            expect(overlayFixed.resolveOverlayForTest(stone, stoneId, new BlockPos(2, 64, 3), Direction.UP) == -1,
                "overlay_fixed should respect the overlay feature flag");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverCarriesOverlayLayerAlphaModes() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        boolean oldOverlays = Options.materialCompatOverlaysEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier overlayFixed0 = ctmFixtureId("overlay_layer", 0);
            expect(overlayFixed0 != null, "overlay layer fixture id should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, overlayFixed0));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = false;
            Options.materialCompatRandomEnabled = false;
            Options.materialCompatOverlaysEnabled = true;

            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            FakeResourceManager translucentManager = new FakeResourceManager();
            translucentManager.add("minecraft:optifine/ctm/overlay_layer/translucent.properties",
                String.join("\n",
                    "method=overlay_fixed",
                    "matchTiles=stone",
                    "faces=top",
                    "layer=translucent"
                ).getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex translucent =
                ResourcePackTextureVariantResolver.buildForTest(translucentManager, false);
            BlockOverlaySprite[] translucentOverlays =
                translucent.resolveOverlayDetailsForTest(stone, stoneId, new BlockPos(2, 64, 3), Direction.UP);
            expect(translucentOverlays.length == 1
                    && translucentOverlays[0].spriteId()
                        == TextureArrayBridge.resolveSpriteId(overlayFixed0.toString()),
                "layer=translucent overlay_fixed should emit its overlay sprite");
            expect(translucentOverlays[0].alphaMode() == PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT,
                "overlay layer=translucent should carry transparent alpha mode to quad emission");

            FakeResourceManager defaultManager = new FakeResourceManager();
            defaultManager.add("minecraft:optifine/ctm/overlay_layer/default.properties",
                "method=overlay_fixed\nmatchTiles=stone\nfaces=top\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex defaults =
                ResourcePackTextureVariantResolver.buildForTest(defaultManager, false);
            BlockOverlaySprite[] defaultOverlays =
                defaults.resolveOverlayDetailsForTest(stone, stoneId, new BlockPos(2, 64, 3), Direction.UP);
            expect(defaultOverlays.length == 1 && defaultOverlays[0].alphaMode() == -1,
                "overlay rules without layer should leave alpha mode unspecified for the consumer cutout fallback");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            Options.materialCompatOverlaysEnabled = oldOverlays;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsNeighborMasks() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier h0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/0.png"));
            Identifier h1 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/1.png"));
            Identifier h2 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/2.png"));
            Identifier h3 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/stone/3.png"));
            Identifier v0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/vertical/0.png"));
            Identifier v2 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/vertical/2.png"));
            Identifier top = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/top/top_tile.png"));
            expect(h0 != null && h1 != null && h2 != null && h3 != null && v0 != null && v2 != null && top != null,
                "neighbor CTM fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, h0, h1, h2, h3, v0, v2, top));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;
            Options.materialCompatRandomEnabled = false;

            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            FakeResourceManager horizontalManager = new FakeResourceManager();
            horizontalManager.add("minecraft:optifine/ctm/stone/horizontal.properties",
                "method=horizontal\nmatchTiles=stone\ntiles=0-3\nfaces=north\nconnect=block\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex horizontal =
                ResourcePackTextureVariantResolver.buildForTest(horizontalManager, false);
            int h3Id = TextureArrayBridge.resolveSpriteId(h3.toString());
            expect(horizontal.resolveWithConnectionsForTest(stone, stoneId, null,
                    Direction.NORTH, Set.of(Direction.WEST, Direction.EAST)) == h3Id,
                "horizontal CTM should select the both-neighbors tile on the matching face");
            expect(horizontal.resolveWithConnectionsForTest(stone, stoneId, null,
                    Direction.SOUTH, Set.of(Direction.WEST, Direction.EAST)) == stoneId,
                "faces=north should prevent horizontal CTM on south quads");
            Options.materialCompatCtmEnabled = false;
            expect(horizontal.resolveWithConnectionsForTest(stone, stoneId, null,
                    Direction.NORTH, Set.of(Direction.WEST, Direction.EAST)) == stoneId,
                "neighbor CTM should respect the CTM feature flag");
            Options.materialCompatCtmEnabled = true;

            FakeResourceManager verticalManager = new FakeResourceManager();
            verticalManager.add("minecraft:optifine/ctm/vertical/vertical.properties",
                "method=vertical\nmatchTiles=stone\ntiles=0-3\nconnect=state\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex vertical =
                ResourcePackTextureVariantResolver.buildForTest(verticalManager, false);
            int v2Id = TextureArrayBridge.resolveSpriteId(v2.toString());
            expect(vertical.resolveWithConnectionsForTest(stone, stoneId, null,
                    Direction.EAST, Set.of(Direction.UP)) == v2Id,
                "vertical CTM should select the upward-neighbor tile on side faces");

            FakeResourceManager topManager = new FakeResourceManager();
            topManager.add("minecraft:optifine/ctm/top/top.properties",
                "method=top\nmatchTiles=stone\ntiles=top_tile\nconnect=block\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex topIndex =
                ResourcePackTextureVariantResolver.buildForTest(topManager, false);
            int topId = TextureArrayBridge.resolveSpriteId(top.toString());
            expect(topIndex.resolveWithConnectionsForTest(stone, stoneId, null,
                    Direction.NORTH, Set.of(Direction.UP)) == topId,
                "top CTM should select its tile when the block above connects");
            expect(topIndex.resolveWithConnectionsForTest(stone, stoneId, null,
                    Direction.NORTH, Set.of()) == stoneId,
                "top CTM should leave the source sprite when the block above does not connect");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            Options.materialCompatRandomEnabled = oldRandom;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverHonorsExplicitConnectPredicates() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier v0 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/connect/0.png"));
            Identifier v1 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/connect/1.png"));
            Identifier v2 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/connect/2.png"));
            Identifier v3 = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
                "assets/minecraft/optifine/ctm/connect/3.png"));
            expect(v0 != null && v1 != null && v2 != null && v3 != null,
                "explicit connect fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, v0, v1, v2, v3));
            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;

            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            int v0Id = TextureArrayBridge.resolveSpriteId(v0.toString());
            int v2Id = TextureArrayBridge.resolveSpriteId(v2.toString());
            FakeResourceManager blockManager = new FakeResourceManager();
            blockManager.add("minecraft:optifine/ctm/connect/block.properties",
                "method=vertical\nmatchTiles=stone\ntiles=0-3\nconnectBlocks=dirt\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex blockIndex =
                ResourcePackTextureVariantResolver.buildForTest(blockManager, false);
            expect(blockIndex.resolveWithNeighborBlockIdsForTest(stone, stoneId, "stone",
                    Direction.NORTH, Map.of(Direction.UP, "dirt")) == v2Id,
                "connectBlocks should join CTM masks against matching neighbor blocks");
            expect(blockIndex.resolveWithNeighborBlockIdsForTest(stone, stoneId, "stone",
                    Direction.NORTH, Map.of(Direction.UP, "oak_planks")) == v0Id,
                "connectBlocks should keep the isolated CTM tile for non-matching neighbor blocks");

            FakeResourceManager tileManager = new FakeResourceManager();
            tileManager.add("minecraft:optifine/ctm/connect/tile.properties",
                "method=vertical\nmatchTiles=stone\ntiles=0-3\nconnectTiles=dirt\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex tileIndex =
                ResourcePackTextureVariantResolver.buildForTest(tileManager, false);
            expect(tileIndex.resolveWithNeighborBlockIdsForTest(stone, stoneId, "stone",
                    Direction.NORTH, Map.of(Direction.UP, "dirt")) == v2Id,
                "connectTiles should join CTM masks against matching neighbor tile names");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void textureVariantResolverSelectsFullAndCompactCtm() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldCtm = Options.materialCompatCtmEnabled;
        List<Identifier> previousSprites = List.copyOf(TextureArrayBridge.sortedSpriteIds);
        try {
            Identifier stone = Identifier.ofVanilla("block/stone");
            Identifier ctm0 = ctmFixtureId("full", 0);
            Identifier ctm15 = ctmFixtureId("full", 15);
            Identifier ctm26 = ctmFixtureId("full", 26);
            Identifier ctm46 = ctmFixtureId("full", 46);
            Identifier compact0 = ctmFixtureId("compact", 0);
            Identifier compact1 = ctmFixtureId("compact", 1);
            Identifier compact2 = ctmFixtureId("compact", 2);
            Identifier compact3 = ctmFixtureId("compact", 3);
            Identifier compact4 = ctmFixtureId("compact", 4);
            Identifier compact5 = ctmFixtureId("compact", 5);
            expect(ctm0 != null && ctm15 != null && ctm26 != null && ctm46 != null
                    && compact0 != null && compact1 != null && compact2 != null
                    && compact3 != null && compact4 != null && compact5 != null,
                "full and compact CTM fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, ctm0, ctm15, ctm26, ctm46,
                compact0, compact1, compact2, compact3, compact4, compact5));

            Options.materialCompatEnabled = true;
            Options.materialCompatCtmEnabled = true;

            int stoneId = TextureArrayBridge.resolveSpriteId(stone.toString());
            FakeResourceManager fullManager = new FakeResourceManager();
            fullManager.add("minecraft:optifine/ctm/full/full.properties",
                "method=ctm\nmatchTiles=stone\ntiles=0-46\nconnect=block\n".getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex full =
                ResourcePackTextureVariantResolver.buildForTest(fullManager, false);
            expect(full.ruleCountForTest() == 1, "full CTM rule should compile");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of()) == TextureArrayBridge.resolveSpriteId(ctm0.toString()),
                "full CTM should select tile 0 when isolated");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.WEST, Direction.DOWN),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.WEST, Direction.DOWN))) == TextureArrayBridge.resolveSpriteId(ctm15.toString()),
                "full CTM should include gated diagonal neighbors in the 47-tile mask");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.EAST,
                    Set.of(Direction.NORTH, Direction.DOWN),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.NORTH, Direction.DOWN))) == TextureArrayBridge.resolveSpriteId(ctm15.toString()),
                "full CTM should rotate the same mask on east-facing quads");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.SOUTH,
                    Set.of(Direction.EAST, Direction.DOWN),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.EAST, Direction.DOWN))) == TextureArrayBridge.resolveSpriteId(ctm15.toString()),
                "full CTM should rotate the same mask on south-facing quads");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.WEST,
                    Set.of(Direction.SOUTH, Direction.DOWN),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.SOUTH, Direction.DOWN))) == TextureArrayBridge.resolveSpriteId(ctm15.toString()),
                "full CTM should rotate the same mask on west-facing quads");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.WEST, Direction.DOWN, Direction.EAST, Direction.UP),
                    Set.of(
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.WEST, Direction.DOWN),
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.DOWN, Direction.EAST),
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.EAST, Direction.UP),
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.UP, Direction.WEST)
                    )) == TextureArrayBridge.resolveSpriteId(ctm26.toString()),
                "full CTM should select tile 26 for a completely surrounded face");
            expect(full.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.WEST, Direction.DOWN, Direction.EAST),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.WEST, Direction.DOWN))) == TextureArrayBridge.resolveSpriteId(ctm46.toString()),
                "full CTM should not set diagonal bits unless both adjacent edges and the diagonal connect");

            FakeResourceManager compactManager = new FakeResourceManager();
            compactManager.add("minecraft:optifine/ctm/compact/compact.properties",
                "method=ctm_compact\nmatchTiles=stone\ntiles=0-5\nctm.26=5\nconnect=block\n"
                    .getBytes(StandardCharsets.UTF_8));
            ResourcePackTextureVariantResolver.ResolverIndex compact =
                ResourcePackTextureVariantResolver.buildForTest(compactManager, false);
            expect(compact.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of()) == TextureArrayBridge.resolveSpriteId(compact0.toString()),
                "compact CTM should choose the isolated whole-quad tile");
            expect(compact.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.UP, Direction.DOWN)) == TextureArrayBridge.resolveSpriteId(compact2.toString()),
                "compact CTM should choose the vertical whole-quad tile");
            expect(compact.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.WEST, Direction.DOWN),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.WEST, Direction.DOWN))) == stoneId,
                "compact CTM whole-quad lookup should leave mixed quadrant cases for split-quad rendering");
            ResourcePackTextureVariantResolver.CompactCtmQuadrants mixedQuadrants =
                compact.resolveCompactCtmQuadrantsWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.WEST, Direction.DOWN),
                    Set.of(ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(
                        Direction.WEST, Direction.DOWN)));
            expect(mixedQuadrants != null,
                "compact CTM mixed quadrant cases should resolve split-quad sprite ids");
            expect(java.util.Arrays.equals(mixedQuadrants.spriteIds(), new int[] {
                    TextureArrayBridge.resolveSpriteId(compact3.toString()),
                    TextureArrayBridge.resolveSpriteId(compact1.toString()),
                    TextureArrayBridge.resolveSpriteId(compact2.toString()),
                    TextureArrayBridge.resolveSpriteId(compact0.toString())
                }),
                "compact CTM should expose upper-left/lower-left/lower-right/upper-right sprite ids");
            expect(compact.resolveWithConnectionsForTest(stone, stoneId, null, Direction.NORTH,
                    Set.of(Direction.WEST, Direction.DOWN, Direction.EAST, Direction.UP),
                    Set.of(
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.WEST, Direction.DOWN),
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.DOWN, Direction.EAST),
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.EAST, Direction.UP),
                        ResourcePackTextureVariantResolver.ResolverIndex.diagonalKeyForTest(Direction.UP, Direction.WEST)
                    )) == TextureArrayBridge.resolveSpriteId(compact5.toString()),
                "compact CTM should honor explicit ctm.N whole-quad overrides");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatCtmEnabled = oldCtm;
            TextureArrayBridge.setSortedSpriteIds(previousSprites.isEmpty()
                ? List.of(SPRITE, Identifier.ofVanilla("block/glass"))
                : previousSprites);
        }
    }

    private static void randomEntityTextureResolverSelectsWeightedAndBiomeVariants() {
        boolean oldEnabled = Options.materialCompatEnabled;
        boolean oldRandom = Options.materialCompatRandomEnabled;
        try {
            Options.materialCompatEnabled = true;
            Options.materialCompatRandomEnabled = true;

            FakeResourceManager manager = new FakeResourceManager();
            manager.add("minecraft:optifine/random/entity/cow/cow_temperate.properties",
                "textures.1=1-3\nweights.1=3 2 1\n".getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/random/entity/cow/cow_temperate2.png", new byte[] {1});
            manager.add("minecraft:optifine/random/entity/pig/pig_temperate.properties",
                "textures.1=1\nsizes.1=0\ntextures.2=2\n".getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/random/entity/pig/pig_temperate2.png", new byte[] {2});
            manager.add("minecraft:optifine/random/entity/bear/polarbear.properties",
                ("textures.1=1\nbiomes.1=snowy_plains ice_spikes\n"
                    + "textures.2=2\n").getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/random/entity/bear/polarbear2.png", new byte[] {3});
            manager.add("minecraft:optifine/random/entity/zombie/zombie.properties",
                ("textures.1=2\nheights.1=60-70\n"
                    + "textures.2=1\n").getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/random/entity/zombie/zombie2.png", new byte[] {4});
            manager.add("minecraft:optifine/random/entity/wolf/wolf.properties",
                "skins.1=1 2\nweights.1=1 1\n".getBytes(StandardCharsets.UTF_8));
            manager.add("minecraft:optifine/random/entity/wolf/wolf2.png", new byte[] {5});

            ResourcePackRandomEntityTextureResolver.RandomEntityIndex index =
                ResourcePackRandomEntityTextureResolver.buildForTest(manager, false);
            expect(index.ruleCountForTest() == 8,
                "random entity resolver should parse weighted, biome, height, size, and skins-alias rules");

            Identifier cow = Identifier.ofVanilla("textures/entity/cow/cow_temperate.png");
            Identifier cow2 = Identifier.ofVanilla("optifine/random/entity/cow/cow_temperate2.png");
            boolean sawCowBase = false;
            boolean sawCowAlt = false;
            for (int hash = 0; hash < 64; hash++) {
                Identifier resolved = index.resolve(cow, hash, "");
                expect(!Identifier.ofVanilla("optifine/random/entity/cow/cow_temperate3.png").equals(resolved),
                    "random entity resolver should drop missing numbered choices");
                sawCowBase |= cow.equals(resolved);
                sawCowAlt |= cow2.equals(resolved);
            }
            expect(sawCowBase && sawCowAlt,
                "random entity resolver should choose weighted base and alternate textures across stable hashes");

            Identifier pig = Identifier.ofVanilla("textures/entity/pig/pig_temperate.png");
            expect(pig.equals(index.resolve(pig, 7, "", 64, 0)),
                "random entity resolver should match OptiFine size predicates with NBT-style slime sizes");
            expect(Identifier.ofVanilla("optifine/random/entity/pig/pig_temperate2.png").equals(
                    index.resolve(pig, 7, "", 64, 1)),
                "random entity resolver should fall through when OptiFine size predicates do not match");

            Identifier polarBear = Identifier.ofVanilla("textures/entity/bear/polarbear.png");
            expect(polarBear.equals(index.resolve(polarBear, 11, "minecraft:snowy_plains")),
                "random entity resolver should honor matching biome-specific groups");
            expect(Identifier.ofVanilla("optifine/random/entity/bear/polarbear2.png").equals(
                    index.resolve(polarBear, 11, "minecraft:plains")),
                "random entity resolver should fall through to later groups when biome predicates do not match");

            Identifier zombie = Identifier.ofVanilla("textures/entity/zombie/zombie.png");
            expect(Identifier.ofVanilla("optifine/random/entity/zombie/zombie2.png").equals(
                    index.resolve(zombie, 3, "", 65, -1)),
                "random entity resolver should honor entity height predicates");
            expect(zombie.equals(index.resolve(zombie, 3, "", 71, -1)),
                "random entity resolver should fall through when entity height predicates do not match");

            Identifier wolf = Identifier.ofVanilla("textures/entity/wolf/wolf.png");
            Identifier wolf2 = Identifier.ofVanilla("optifine/random/entity/wolf/wolf2.png");
            boolean sawWolfBase = false;
            boolean sawWolfAlt = false;
            for (int hash = 0; hash < 64; hash++) {
                Identifier resolved = index.resolve(wolf, hash, "");
                sawWolfBase |= wolf.equals(resolved);
                sawWolfAlt |= wolf2.equals(resolved);
            }
            expect(sawWolfBase && sawWolfAlt,
                "random entity resolver should treat OptiFine skins.N as a texture choice alias");
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatRandomEnabled = oldRandom;
        }
    }

    private static void shaderBlockLayerResolverParsesAlphaModes() {
        String blockProperties = String.join("\n",
            "layer.solid=stone minecraft:dirt[snowy=false]",
            "layer.cutout=oak_leaves block/grass",
            "layer.cutout_mipped=minecraft:iron_bars",
            "layer.translucent=glass glass_pane"
        );
        expect(ResourcePackBlockLayerResolver.ruleCountForTest(blockProperties) == 7,
            "shader block layer resolver should parse all named block layer entries");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties, "minecraft:stone")
                == PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE,
            "layer.solid should map to opaque alpha mode");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties,
                "minecraft:dirt", Map.of("snowy", "false")) == PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE,
            "layer.solid should honor matching block-state predicates");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties,
                "minecraft:dirt", Map.of("snowy", "true")) == -1,
            "layer.solid should reject mismatched block-state predicates");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties, "minecraft:oak_leaves")
                == PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT,
            "layer.cutout should map to cutout alpha mode");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties, "minecraft:iron_bars")
                == PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT,
            "layer.cutout_mipped should map to cutout alpha mode in the ray path");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties, "minecraft:glass")
                == PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT,
            "layer.translucent should map to transparent alpha mode");
        expect(ResourcePackBlockLayerResolver.resolveBlockAlphaModeForTest(blockProperties, "minecraft:gold_block")
                == -1,
            "unlisted blocks should leave the vanilla render-layer alpha mode alone");
        expect(ResourcePackBlockLayerResolver.resolveMergedBlockAlphaModeForTest(
                "layer.solid=glass\nlayer.cutout=oak_leaves",
                "layer.translucent=glass\nlayer.cutout_mipped=ice",
                "minecraft:glass") == PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT,
            "active resource-pack block.properties should override shader-pack block layer defaults");
        expect(ResourcePackBlockLayerResolver.resolveMergedBlockAlphaModeForTest(
                "layer.solid=glass\nlayer.cutout=oak_leaves",
                "layer.translucent=glass\nlayer.cutout_mipped=ice",
                "minecraft:oak_leaves") == PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT,
            "shader-pack block layer defaults should still apply when resource packs do not override them");

        String shaderBlockRules = String.join("\n",
            "block.1003=minecraft:stone minecraft:dirt[snowy=false]",
            "block.1004=minecraft:glass block/white_stained_glass"
        );
        expect(ResourcePackBlockLayerResolver.shaderBlockRuleCountForTest(shaderBlockRules) == 4,
            "shader block.N parser should index every named block token");
        expect(ResourcePackBlockLayerResolver.resolveShaderBlockIdForTest(shaderBlockRules, "minecraft:stone") == 1003,
            "shader block.N parser should resolve plain block ids");
        expect(ResourcePackBlockLayerResolver.resolveShaderBlockIdForTest(shaderBlockRules,
                "minecraft:dirt", Map.of("snowy", "false")) == 1003,
            "shader block.N parser should honor matching block-state predicates");
        expect(ResourcePackBlockLayerResolver.resolveShaderBlockIdForTest(shaderBlockRules,
                "minecraft:dirt", Map.of("snowy", "true")) == -1,
            "shader block.N parser should reject mismatched block-state predicates");
        expect(ResourcePackBlockLayerResolver.resolveShaderBlockIdForTest(shaderBlockRules,
                "minecraft:white_stained_glass") == 1004,
            "shader block.N parser should normalize block/ prefixed tokens");
    }

    private static void colorPropertiesResolverParsesFlatBlockPalettes() {
        FakeResourceManager manager = new FakeResourceManager();
        manager.add("minecraft:optifine/color.properties",
            String.join("\n",
                "palette.block.custom/flat=stone minecraft:oak_leaves minecraft:birch_leaves[persistent=true]",
                "palette.block.~/colormap/biome=grass_block"
            ).getBytes(StandardCharsets.UTF_8));
        manager.add("minecraft:optifine/custom/flat.png", pngBytesForTest(0xFF3366AA));
        manager.add("minecraft:optifine/colormap/biome.png",
            pngBytesForTest(0xFF00AA00, 0xFFCCDD44));
        manager.add("minecraft:optifine/colormap/blocks/terracotta/terracotta.properties",
            String.join("\n",
                "format=fixed",
                "color=985e44",
                "blocks=terracotta minecraft:red_concrete[snowy=false]"
            ).getBytes(StandardCharsets.UTF_8));

        ResourcePackColorPropertiesResolver.ColorIndex index =
            ResourcePackColorPropertiesResolver.buildForTest(manager, false);
        expect(index.flatPaletteCountForTest() == 1, "color resolver should count flat custom palettes");
        expect(index.fixedBlockColormapCountForTest() == 1,
            "color resolver should count fixed OptiFine block colormap properties");
        expect(index.variablePaletteCountForTest() == 1,
            "color resolver should count non-flat biome palettes for sampling");
        expect(index.fixedBlockTintCountForTest() == 5,
            "color resolver should compile fixed block tint entries from flat palettes");
        expect(index.resolveBlockColor("minecraft:stone", 0xFFFFFF) == 0x3366AA,
            "flat color.properties palette should override vanilla block tint");
        expect(index.resolveBlockColor("minecraft:oak_leaves", 0xFFFFFF) == 0x3366AA,
            "flat color.properties palette should normalize namespaced block ids");
        expect(index.resolveBlockColor("minecraft:grass_block", 0x112233) == 0x112233,
            "biome palettes without world context should keep vanilla tint");
        expect(index.resolveBlockColorForClimateForTest("minecraft:grass_block", 1.0, 1.0, 0x112233) == 0x00AA00,
            "color.properties biome palette should sample hot humid coordinates");
        expect(index.resolveBlockColorForClimateForTest("minecraft:grass_block", 0.0, 0.0, 0x112233) == 0xCCDD44,
            "color.properties biome palette should sample cold dry coordinates");
        expect(index.resolveBlockColor("minecraft:terracotta", 0xFFFFFF) == 0x985E44,
            "fixed OptiFine block colormap should override non-tinted block color");
        expect(index.resolveBlockColor("minecraft:red_concrete", 0xFFFFFF) == 0x985E44,
            "fixed OptiFine block colormap should normalize block states");
        expect(index.resolveBlockColor("minecraft:glass", 0x445566) == 0x445566,
            "blocks without color.properties entries should keep vanilla tint");
    }

    private static void lightmapResolverSamplesCustomPalettes() {
        FakeResourceManager manager = new FakeResourceManager();
        manager.add("minecraft:optifine/lightmap/world0.png",
            lightmapPngBytesForTest(0xFF200000, 0xFF002000, 0xFF600000, 0xFF006000));
        manager.add("minecraft:optifine/lightmap/world0_rain.png",
            lightmapPngBytesForTest(0xFF000020, 0xFF000020, 0xFF000060, 0xFF000060));

        ResourcePackLightmapResolver.LightmapIndex index =
            ResourcePackLightmapResolver.buildForTest(manager, false);
        expect(index.paletteCountForTest() == 2,
            "lightmap resolver should count base and weather custom lightmap palettes");

        LightmapSample base = ResourcePackLightmapResolver.resolveForTest(manager, "world0",
            1.0f, 2.0f, 0.0f, 0.0f, 0.5f, false);
        expect(base.enabled(), "custom lightmap sample should be enabled when world0.png exists");
        expect(base.includesNightVision(), "64-row lightmap should advertise night-vision palette rows");
        expect(close(base.skyRgb()[15 * 3], 64.0f / 255.0f),
            "custom lightmap should sample and blend sky night-vision rows");
        expect(close(base.blockRgb()[15 * 3 + 1], 64.0f / 255.0f),
            "custom lightmap should sample and blend block night-vision rows");

        LightmapSample rain = ResourcePackLightmapResolver.resolveForTest(manager, "world0",
            1.0f, 2.0f, 1.0f, 0.0f, 0.5f, false);
        expect(close(rain.skyRgb()[15 * 3 + 2], 64.0f / 255.0f),
            "custom lightmap should use rain palette when rain factor reaches one");

        LightmapSample missing = ResourcePackLightmapResolver.resolveForTest(manager, "world1",
            1.0f, 2.0f, 0.0f, 0.0f, 0.0f, false);
        expect(!missing.enabled(), "custom lightmap should stay disabled for dimensions without a palette");
    }

    private static Identifier ctmFixtureId(String group, int tile) {
        return Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(
            "assets/minecraft/optifine/ctm/" + group + "/" + tile + ".png"));
    }

    private static void naturalTextureResolverParsesRulesAndTransformsUv() {
        FakeResourceManager manager = new FakeResourceManager();
        manager.add("minecraft:optifine/natural.properties",
            ("stone=4F\n"
                + "block/dirt=2\n"
                + "oak_planks=F\n"
                + "glass=0\n").getBytes(StandardCharsets.UTF_8));
        ResourcePackNaturalTextureResolver.NaturalIndex index =
            ResourcePackNaturalTextureResolver.buildForTest(manager, false);
        expect(index.ruleCountForTest() == 3, "natural resolver should parse enabled rules and drop explicit zero");

        Identifier stone = Identifier.ofVanilla("block/stone");
        BlockPos pos = new BlockPos(10, 64, -3);
        NaturalTransform stoneA = index.resolve(stone, pos, Direction.NORTH);
        NaturalTransform stoneB = index.resolve(stone, pos, Direction.NORTH);
        expect(stoneA.equals(stoneB), "natural resolver should be deterministic for the same block face");
        expect(stoneA.quarterTurns() >= 0 && stoneA.quarterTurns() <= 3,
            "4F natural resolver should emit a 0..3 quarter-turn");

        NaturalTransform dirt = index.resolve(Identifier.ofVanilla("block/dirt"), pos, Direction.UP);
        expect((dirt.quarterTurns() == 0 || dirt.quarterTurns() == 2) && !dirt.flipU(),
            "2 natural resolver should only emit 0 or 180 degree rotation");

        NaturalTransform oak = index.resolve(Identifier.ofVanilla("block/oak_planks"), pos, Direction.WEST);
        expect(oak.quarterTurns() == 0, "F natural resolver should not rotate");

        expect(index.resolve(Identifier.ofVanilla("block/glass"), pos, Direction.NORTH).isIdentity(),
            "natural=0 should disable a texture rule");
        expect(index.resolve(Identifier.ofVanilla("block/unknown"), pos, Direction.NORTH).isIdentity(),
            "textures without natural rules should stay identity");

        NaturalTransform rotate90 = new NaturalTransform(1, false);
        expect(close(rotate90.transformU(0.25f, 0.75f), 0.25f)
                && close(rotate90.transformV(0.25f, 0.75f), 0.25f),
            "natural rotate 90 should transform local UVs");
        NaturalTransform flipThenRotate180 = new NaturalTransform(2, true);
        expect(close(flipThenRotate180.transformU(0.25f, 0.75f), 0.25f)
                && close(flipThenRotate180.transformV(0.25f, 0.75f), 0.25f),
            "natural flip should be applied before rotation");
    }

    private static void naturalTextureResolverUsesResolvedVariantRules() {
        Identifier source = Identifier.ofVanilla("block/stone");
        Identifier variant = Identifier.ofVanilla("block/stone_variant");
        TextureArrayBridge.setSortedSpriteIds(List.of(source, variant));

        FakeResourceManager manager = new FakeResourceManager();
        manager.add("minecraft:optifine/natural.properties",
            ("block/stone=4F\n"
                + "block/stone_variant=F\n").getBytes(StandardCharsets.UTF_8));

        int sourceId = TextureArrayBridge.resolveRenderableSpriteId(source);
        NaturalTransform sourceTransform = firstNonIdentityBlockNaturalTransform(manager, source,
            sourceId, Direction.NORTH);
        expect(!sourceTransform.isIdentity(),
            "source natural rule should still transform the unmodified source sprite");

        int variantId = TextureArrayBridge.resolveRenderableSpriteId(variant);
        NaturalTransform variantTransform = firstNonIdentityBlockNaturalTransform(manager, source,
            variantId, Direction.NORTH);
        expect(!variantTransform.isIdentity() && variantTransform.quarterTurns() == 0,
            "resolved variant sprite should use its own natural rule instead of the source rule");

        FakeResourceManager sourceOnly = new FakeResourceManager();
        sourceOnly.add("minecraft:optifine/natural.properties",
            "block/stone=4F\n".getBytes(StandardCharsets.UTF_8));
        NaturalTransform missingVariantRule =
            ResourcePackNaturalTextureResolver.resolveBlockTransformForTest(sourceOnly, source,
                variantId, new BlockPos(3, 64, 0), Direction.NORTH, false);
        expect(missingVariantRule.isIdentity(),
            "source natural rule should not rotate a resolved variant sprite without its own rule");

        TextureArrayBridge.setSortedSpriteIds(List.of(SPRITE, Identifier.ofVanilla("block/glass")));
    }

    private static NaturalTransform firstNonIdentityBlockNaturalTransform(ResourceManager manager,
        Identifier source,
        int resolvedSpriteId,
        Direction face) {
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 4; z++) {
                NaturalTransform transform =
                    ResourcePackNaturalTextureResolver.resolveBlockTransformForTest(manager, source,
                        resolvedSpriteId, new BlockPos(x, 64, z), face, false);
                if (!transform.isIdentity()) {
                    return transform;
                }
            }
        }
        return NaturalTransform.identity();
    }

    private static boolean hasCompatRecord(JsonArray records, String kind, String key, String value) {
        for (int i = 0; i < records.size(); i++) {
            JsonObject record = records.get(i).getAsJsonObject();
            if (!kind.equals(record.get("kind").getAsString())) {
                continue;
            }
            if (record.has(key) && value.equals(record.get(key).getAsString())) {
                return true;
            }
            JsonObject values = record.getAsJsonObject("values");
            if (values != null && values.has(key) && value.equals(values.get(key).getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasCtmDependency(JsonArray dependencies, String path, boolean present, String atlasSprite) {
        for (int i = 0; i < dependencies.size(); i++) {
            JsonObject dependency = dependencies.get(i).getAsJsonObject();
            if (path.equals(dependency.get("path").getAsString())
                && present == dependency.get("present").getAsBoolean()
                && atlasSprite.equals(dependency.get("atlasSprite").getAsString())) {
                return true;
            }
        }
        return false;
    }

    private static final class CapturingSpriteRegions implements AtlasSource.SpriteRegions {
        private final Map<Identifier, Resource> resources = new LinkedHashMap<>();

        @Override
        public void add(Identifier id, AtlasSource.SpriteRegion region) {
            resources.put(id, resource(""));
        }

        @Override
        public void add(Identifier id, Resource resource) {
            resources.put(id, resource);
        }

        @Override
        public void removeIf(java.util.function.Predicate<Identifier> predicate) {
            resources.keySet().removeIf(predicate);
        }
    }

    private static final class FakeResourceManager implements ResourceManager {
        private final Map<Identifier, Resource> resources = new LinkedHashMap<>();

        void add(String id, byte[] bytes) {
            Identifier identifier = Identifier.tryParse(id);
            expect(identifier != null, "fake resource identifier should parse: " + id);
            resources.put(identifier, resource(bytes));
        }

        @Override
        public Optional<Resource> getResource(Identifier id) {
            return Optional.ofNullable(resources.get(id));
        }

        @Override
        public Set<String> getAllNamespaces() {
            return Set.of("minecraft");
        }

        @Override
        public List<Resource> getAllResources(Identifier id) {
            Resource resource = resources.get(id);
            return resource == null ? List.of() : List.of(resource);
        }

        @Override
        public Map<Identifier, Resource> findResources(String startingPath,
            java.util.function.Predicate<Identifier> pathPredicate) {
            Map<Identifier, Resource> found = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
                Identifier id = entry.getKey();
                if (id.getPath().startsWith(startingPath) && pathPredicate.test(id)) {
                    found.put(id, entry.getValue());
                }
            }
            return found;
        }

        @Override
        public Map<Identifier, List<Resource>> findAllResources(String startingPath,
            java.util.function.Predicate<Identifier> pathPredicate) {
            Map<Identifier, List<Resource>> found = new LinkedHashMap<>();
            for (Map.Entry<Identifier, Resource> entry : findResources(startingPath, pathPredicate).entrySet()) {
                found.put(entry.getKey(), List.of(entry.getValue()));
            }
            return found;
        }

        @Override
        public Stream<ResourcePack> streamResourcePacks() {
            return Stream.empty();
        }
    }

    private static Resource resource(String value) {
        return resource(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Resource resource(byte[] bytes) {
        return new Resource(null, () -> new ByteArrayInputStream(bytes));
    }

    private static String readResourceString(Resource resource) {
        try (var reader = resource.getReader()) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[256];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        } catch (IOException e) {
            throw new AssertionError("failed to read resource fixture", e);
        }
    }

    private static byte[] pngBytesForTest(int... argbs) {
        int width = Math.max(1, argbs.length);
        Path tmp = null;
        try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, 1, false)) {
            for (int x = 0; x < width; x++) {
                int argb = argbs.length == 0 ? 0xFFFFFFFF : argbs[Math.min(x, argbs.length - 1)];
                image.setColorArgb(x, 0, argb);
            }
            tmp = Files.createTempFile("radser-color-palette", ".png");
            image.writeTo(tmp);
            return Files.readAllBytes(tmp);
        } catch (IOException e) {
            throw new AssertionError("failed to build PNG fixture", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static byte[] lightmapPngBytesForTest(int skyBaseArgb,
        int blockBaseArgb,
        int skyNightVisionArgb,
        int blockNightVisionArgb) {
        Path tmp = null;
        try (NativeImage image = new NativeImage(NativeImage.Format.RGBA, 2, 64, false)) {
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 16; y++) {
                    image.setColorArgb(x, y, skyBaseArgb);
                    image.setColorArgb(x, 16 + y, blockBaseArgb);
                    image.setColorArgb(x, 32 + y, skyNightVisionArgb);
                    image.setColorArgb(x, 48 + y, blockNightVisionArgb);
                }
            }
            tmp = Files.createTempFile("radser-lightmap-palette", ".png");
            image.writeTo(tmp);
            return Files.readAllBytes(tmp);
        } catch (IOException e) {
            throw new AssertionError("failed to build lightmap PNG fixture", e);
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void presetCatalogIsResourceBacked() {
        expect(MaterialPresetCatalog.loadedFromResource(), "common material presets should load from resource JSON");
        expect(MaterialPresetCatalog.metals().size() >= 8, "resource catalog should include measured metal presets");
        expect(MaterialPresetCatalog.dielectrics().size() >= 8, "resource catalog should include dielectric presets");
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.0001f;
    }

    private static Path vanillaPtRoot() {
        Path relative = Path.of("..", "MCVR", "src", "shader", "world", "ray_tracing", "internal", "vanilla-pt");
        Path absolute = Path.of("C:\\RadSER\\MCVR\\src\\shader\\world\\ray_tracing\\internal\\vanilla-pt");
        return Files.exists(relative.resolve("configs.json")) ? relative : absolute;
    }

    private static JsonObject readVanillaPtConfig() {
        Path path = vanillaPtRoot().resolve("configs.json");
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("unable to read vanilla-pt shader-pack config at " + path, e);
        }
    }

    private static String defaultValue(JsonObject root, String name) {
        JsonArray attributes = root.getAsJsonArray("attributes");
        for (int i = 0; i < attributes.size(); i++) {
            JsonObject attr = attributes.get(i).getAsJsonObject();
            if (name.equals(attr.get("name").getAsString())) {
                return attr.get("default_value").getAsString();
            }
        }
        throw new AssertionError("missing shader-pack attribute " + name);
    }

    private static JsonObject ruleWithMethod(JsonArray rules, String method) {
        for (int i = 0; i < rules.size(); i++) {
            JsonObject rule = rules.get(i).getAsJsonObject();
            if (method.equals(rule.get("method").getAsString())) {
                return rule;
            }
        }
        throw new AssertionError("missing variant registry rule with method " + method);
    }

    private static boolean isRayTraceShaderFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".rgen") || name.endsWith(".rmiss") || name.endsWith(".rchit") ||
            name.endsWith(".rahit") || name.endsWith(".rint") || name.endsWith(".rcall");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
