package com.radiance.client.autopbr;

import com.google.gson.JsonArray;
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
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.VanillaTextureManifest;
import com.radiance.client.texture.compat.ResourcePackBlockLayerResolver;
import com.radiance.client.texture.compat.ResourcePackColorPropertiesResolver;
import com.radiance.client.texture.compat.ResourcePackCompatAtlasSource;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import com.radiance.client.texture.compat.ResourcePackCompatDiagnostics;
import com.radiance.client.texture.compat.ResourcePackEmissiveTextureResolver;
import com.radiance.client.texture.compat.ResourcePackModelFallback;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver.NaturalTransform;
import com.radiance.client.texture.compat.ResourcePackTextureNames;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver;
import com.radiance.client.vertex.PBRVertexFormatElements;
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
        generatedMasksAreDeterministic();
        histogramClampAndSmoothingAreDeterministic();
        dropdownRowSelectionUsesClickCoordinates();
        textureRuleEntrySizeStaysStable();
        textureNameFilterTreatsEmissiveAsAuxiliary();
        textureArrayLayerSizeUsesLargestSprite();
        missingSpriteFallbackUsesRenderableBlockSprite();
        malformedModelJsonFallsBackToLowerPriorityResource();
        materialCompatFlagsDefaultEnabled();
        materialCompatScannerRecognizesCoreFeatures();
        materialCompatRunScanMarksActivePacksAndParsesRecords();
        materialCompatDiagnosticsReportNaturalConsumption();
        materialCompatDiagnosticsReportColorConsumption();
        materialCompatDiagnosticsReportPhysicalEmissiveConsumption();
        materialCompatDiagnosticsReportShaderBlockLayerConsumption();
        ctmAtlasAdmissionUtilitiesAreStable();
        ctmAtlasSourceCollectsPresentTiles();
        emissiveTextureResolverMapsSuffixesAndAtlasAdmission();
        textureVariantResolverSelectsFixedAndRandomSprites();
        textureVariantResolverSelectsRepeatSprites();
        textureVariantResolverSelectsOverlayRandomSprites();
        textureVariantResolverSelectsOverlaySprites();
        textureVariantResolverSelectsNeighborMasks();
        textureVariantResolverSelectsFullAndCompactCtm();
        shaderBlockLayerResolverParsesAlphaModes();
        colorPropertiesResolverParsesFlatBlockPalettes();
        naturalTextureResolverParsesRulesAndTransformsUv();
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
            Identifier.ofVanilla("textures/block/stone_n.png")), "_n should be atlas-filtered aux");
        expect(ResourcePackTextureNames.isAtlasEligiblePbrAuxiliaryTexture(
            Identifier.ofVanilla("textures/block/stone_f.png")), "_f should be atlas-filtered aux");
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
    }

    private static void textureArrayLayerSizeUsesLargestSprite() {
        expect(VanillaTextureManifest.chooseFixedLayerSizeFromCountsForTest(
            16, 974,
            64, 824,
            128, 14,
            256, 4,
            1024, 3) == 64,
            "texture array layer size should preserve common pack resolution and ignore rare outliers");
        expect(VanillaTextureManifest.chooseFixedLayerSizeForTest(16, 16, 64, 64, 128, 128) == 128,
            "small diagnostic atlases should still choose the largest common square size");
        expect(VanillaTextureManifest.chooseFixedLayerSizeForTest(16, 32, 64, 16) == 64,
            "texture array layer size should handle non-square diagnostic inputs conservatively");
    }

    private static void missingSpriteFallbackUsesRenderableBlockSprite() {
        TextureArrayBridge.setSortedSpriteIds(List.of(
            MissingSprite.getMissingSpriteId(),
            Identifier.ofVanilla("block/dirt"),
            Identifier.ofVanilla("block/stone")));
        try {
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
        } finally {
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

        Optional<Resource> unchanged = ResourcePackModelFallback.selectFallbackForTest(
            modelId, List.of(validLowerModel, validLowerModel));
        expect(unchanged.isEmpty(), "valid high-priority model should not be overridden");

        Optional<Resource> nonModel = ResourcePackModelFallback.selectFallbackForTest(
            Identifier.ofVanilla("textures/block/oak_leaves.png"),
            List.of(validLowerModel, malformedTopModel));
        expect(nonModel.isEmpty(), "model fallback must not affect texture resources");
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

    private static void materialCompatScannerRecognizesCoreFeatures() {
        Path root = null;
        try {
            root = Files.createTempDirectory("radser-material-compat-fixture");
            Files.createDirectories(root.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(root.resolve("assets/minecraft/optifine/ctm/stone"));
            Files.createDirectories(root.resolve("assets/minecraft/optifine/colormap/blocks/terracotta"));
            Files.writeString(root.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":46,\"description\":\"fixture\"}}", StandardCharsets.UTF_8);
            Files.write(root.resolve("assets/minecraft/textures/block/stone_s.png"), new byte[] {0});
            Files.write(root.resolve("assets/minecraft/textures/block/stone_n.png"), new byte[] {0});
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

            JsonObject report = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanPackJsonForTest(root.toString())).getAsJsonObject();
            JsonObject counts = report.getAsJsonObject("counts");
            expect(report.get("scannable").getAsBoolean(), "compat fixture should be scannable");
            expect(counts.get("specular_s").getAsInt() == 1, "scanner should count _s maps");
            expect(counts.get("normal_n").getAsInt() == 1, "scanner should count _n maps");
            expect(counts.get("textureProperties").getAsInt() == 1, "scanner should count texture.properties");
            expect(counts.get("emissiveProperties").getAsInt() == 1, "scanner should count emissive.properties");
            expect(counts.get("naturalProperties").getAsInt() == 1, "scanner should count natural.properties");
            expect(counts.get("blockColormapProperties").getAsInt() == 1,
                "scanner should count OptiFine block colormap properties");
            expect(counts.get("optifineCtmProperties").getAsInt() == 1, "scanner should count OptiFine CTM properties");
            expect(report.getAsJsonObject("compatFeatures").get("ctm").getAsInt() == 1, "scanner should classify CTM records");
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
            Path inactive = resourcePacks.resolve("Inactive Pack");
            Files.createDirectories(active.resolve("assets/minecraft/textures/block"));
            Files.createDirectories(active.resolve("assets/minecraft/optifine/ctm/stone"));
            Files.createDirectories(inactive.resolve("assets/minecraft/textures/block"));
            Files.writeString(run.resolve("options.txt"),
                "resourcePacks:[\"vanilla\",\"fabric\",\"file/Active Pack\"]\n"
                    + "incompatibleResourcePacks:[\"file/Active Pack\"]\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/textures/block/stone.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/stone_s.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/textures/block/stone_n.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/texture.properties"),
                "format=lab-pbr/1.3\n", StandardCharsets.UTF_8);
            Files.writeString(active.resolve("assets/minecraft/optifine/emissive.properties"),
                "suffix.emissive=_e\n", StandardCharsets.UTF_8);
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/0.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1_s.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/1_n.png"), new byte[] {0});
            Files.write(active.resolve("assets/minecraft/optifine/ctm/stone/2.png"), new byte[] {0});
            Files.writeString(active.resolve("assets/minecraft/optifine/ctm/stone/stone.properties"),
                "method=ctm\nmatchTiles=stone\ntiles=textures/block/stone 0-2 custom_tile\nconnect=block\n", StandardCharsets.UTF_8);
            Files.write(inactive.resolve("assets/minecraft/textures/block/dirt.png"), new byte[] {0});

            JsonObject status = JsonParser.parseString(
                ResourcePackCompatDiagnostics.scanRunDirectoryJsonForTest(run.toString())).getAsJsonObject();
            JsonArray activePacks = status.getAsJsonArray("activePacks");
            expect(activePacks.size() == 1, "run scan should expose only active file packs");
            JsonObject activePack = activePacks.get(0).getAsJsonObject();
            expect("Active Pack".equals(activePack.get("name").getAsString()), "active pack name should round trip from options.txt");
            expect(activePack.get("active").getAsBoolean(), "active pack should be marked active");
            expect(activePack.get("incompatibleSelected").getAsBoolean(), "incompatible selected pack should be flagged");
            expect(activePack.getAsJsonObject("labpbrCoverage").get("albedoWithSpecularAndNormal").getAsInt() == 1,
                "active pack should report paired LabPBR sidecars");
            expect(activePack.getAsJsonObject("compatFeatures").get("ctm").getAsInt() == 1,
                "active pack should report parsed CTM feature records");
            expect(hasCompatRecord(activePack.getAsJsonArray("compatRecords"), "ctm", "method", "ctm"),
                "CTM record should retain method value");
            expect(hasCompatRecord(activePack.getAsJsonArray("compatRecords"), "emissive_properties", "suffix.emissive", "_e"),
                "emissive record should retain suffix value");
            JsonObject ctmDeps = activePack.getAsJsonObject("ctmAtlasDependencies");
            JsonObject activeCtmDeps = status.getAsJsonObject("activeCtmAtlasDependencies");
            expect(ctmDeps.get("uniqueTiles").getAsInt() == 5, "CTM dependency index should expand path and tile range");
            expect(activeCtmDeps.get("uniqueTiles").getAsInt() == 5,
                "active CTM dependency aggregate should include active pack dependencies");
            expect(ctmDeps.get("presentTiles").getAsInt() == 4, "CTM dependency index should count present tiles");
            expect(ctmDeps.get("missingTiles").getAsInt() == 1, "CTM dependency index should count missing tiles");
            expect(ctmDeps.get("tilesWithSpecular").getAsInt() == 2, "CTM dependency index should see tile specular sidecars");
            expect(ctmDeps.get("tilesWithNormal").getAsInt() == 2, "CTM dependency index should see tile normal sidecars");
            expect(ctmDeps.get("tilesRequiringAtlasAdmission").getAsInt() == 4,
                "CTM dependency index should mark non-vanilla CTM tiles for atlas admission");
            expect(ctmDeps.get("presentTilesRequiringAtlasAdmission").getAsInt() == 3,
                "CTM dependency index should separate present and missing admission tiles");
            JsonArray dependencies = ctmDeps.getAsJsonArray("dependencies");
            expect(hasCtmDependency(dependencies, "assets/minecraft/textures/block/stone.png", true, "minecraft:block/stone"),
                "CTM dependency index should include base texture path dependencies");
            expect(hasCtmDependency(dependencies, "assets/minecraft/optifine/ctm/stone/custom_tile.png", false,
                ResourcePackCompatCtmTiles.atlasSpriteIdentifier("assets/minecraft/optifine/ctm/stone/custom_tile.png")),
                "CTM dependency index should retain missing relative tile dependencies");
        } catch (IOException e) {
            throw new AssertionError("material compat run fixture failed", e);
        } finally {
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
            expect(consumption.get("colorPropertiesBiomePalettesMetadataOnly").getAsBoolean(),
                "diagnostics should state biome palettes remain metadata-only");
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
        } finally {
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
        } finally {
            Options.materialCompatEnabled = oldEnabled;
            Options.materialCompatPhysicalEmissiveEnabled = oldPhysical;
            Options.materialCompatLegacyMcPatcherEnabled = oldLegacy;
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
            expect(fixed != null && random0 != null && random1 != null, "variant fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, fixed, random0, random1));

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
            int fixedId = TextureArrayBridge.resolveSpriteId(fixed.toString());
            expect(fixedIndex.resolveForTest(stone, stoneId, new BlockPos(1, 2, 3), Direction.NORTH) == fixedId,
                "fixed rule should replace matching source sprite with admitted CTM tile");
            Options.materialCompatCtmEnabled = false;
            expect(fixedIndex.resolveForTest(stone, stoneId, new BlockPos(1, 2, 3), Direction.NORTH) == stoneId,
                "fixed rule should respect CTM feature flag");
            Options.materialCompatCtmEnabled = true;

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
                "method=repeat\nmatchTiles=stone\ntiles=0-3\nwidth=2\nheight=2\nlayer=cutout\ntintIndex=1\n"
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
            Identifier compact5 = ctmFixtureId("compact", 5);
            expect(ctm0 != null && ctm15 != null && ctm26 != null && ctm46 != null
                    && compact0 != null && compact1 != null && compact2 != null && compact5 != null,
                "full and compact CTM fixture ids should parse");
            TextureArrayBridge.setSortedSpriteIds(List.of(stone, ctm0, ctm15, ctm26, ctm46,
                compact0, compact1, compact2, compact5));

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
                "compact CTM should leave mixed quadrant cases for a future split-quad pass");
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
            "color resolver should leave non-flat biome palettes as metadata");
        expect(index.fixedBlockTintCountForTest() == 5,
            "color resolver should compile fixed block tint entries from flat palettes");
        expect(index.resolveBlockColor("minecraft:stone", 0xFFFFFF) == 0x3366AA,
            "flat color.properties palette should override vanilla block tint");
        expect(index.resolveBlockColor("minecraft:oak_leaves", 0xFFFFFF) == 0x3366AA,
            "flat color.properties palette should normalize namespaced block ids");
        expect(index.resolveBlockColor("minecraft:grass_block", 0x112233) == 0x112233,
            "non-flat biome palettes should not be averaged into fixed tint");
        expect(index.resolveBlockColor("minecraft:terracotta", 0xFFFFFF) == 0x985E44,
            "fixed OptiFine block colormap should override non-tinted block color");
        expect(index.resolveBlockColor("minecraft:red_concrete", 0xFFFFFF) == 0x985E44,
            "fixed OptiFine block colormap should normalize block states");
        expect(index.resolveBlockColor("minecraft:glass", 0x445566) == 0x445566,
            "blocks without color.properties entries should keep vanilla tint");
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

    private static boolean isRayTraceShaderFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".rgen") || name.endsWith(".rmiss") || name.endsWith(".rchit") ||
            name.endsWith(".rahit") || name.endsWith(".rint") || name.endsWith(".rcall");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
