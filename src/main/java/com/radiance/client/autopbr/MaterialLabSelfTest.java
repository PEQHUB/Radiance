package com.radiance.client.autopbr;

import com.radiance.client.materiallab.MaterialBakePlan;
import com.radiance.client.materiallab.MaterialLabStore;
import com.radiance.client.materiallab.MaterialPresetCatalog;
import com.radiance.client.materiallab.MaterialRecipe;
import com.radiance.client.materiallab.MaterialRecipeCompiler;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.util.List;
import net.minecraft.util.Identifier;

public final class MaterialLabSelfTest {
    private static final Identifier SPRITE = Identifier.ofVanilla("block/oak_planks");

    private MaterialLabSelfTest() {
    }

    public static void main(String[] args) {
        TextureArrayBridge.setSortedSpriteIds(List.of(SPRITE, Identifier.ofVanilla("block/glass")));
        defaultRecipeEmitsNoOverride();
        hostileGraphSidecarsAreNotMaterialLabProfiles();
        roughnessBakesInsteadOfScalarRule();
        transmissionIorEmissionRulesAreFlagged();
        advancedRulesAreFlagged();
        measuredPresetsWritePhysicalValues();
        measuredConductorRulesReachTextureRuleBuffer();
        proceduralChannelControlsAffectBakePlan();
        completeChannelPackageControlsAffectBakePlan();
        previewLanesReflectBakePlan();
        expandedTextureRuleAbiCarriesAdvancedFields();
        aoIsHiddenContractAndPreservedByDefault();
        generatedMasksAreDeterministic();
        textureRuleEntrySizeStaysStable();
        presetCatalogIsResourceBacked();
        System.out.println("Material Lab self-test passed");
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
        expect(close(AutoPbrTextureRules.floatForTest(0, 64), 0.2f), "absorption red offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 80), 0.65f), "thickness offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 100), 1200.0f), "emission nits offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 104), 0.33f), "anisotropic rotation offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 128), 0.4f), "coat mask offset");
        expect(close(AutoPbrTextureRules.floatForTest(0, 156), 1.6f), "displacement offset");
        expect((AutoPbrTextureRules.intForTest(0, 60) & 0x3) == 1, "thin glass mode flag packing");
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

    private static void textureRuleEntrySizeStaysStable() {
        expect(AutoPbrTextureRules.entrySizeForTest() == 160, "texture rule entry must remain 160 bytes");
    }

    private static void presetCatalogIsResourceBacked() {
        expect(MaterialPresetCatalog.loadedFromResource(), "common material presets should load from resource JSON");
        expect(MaterialPresetCatalog.metals().size() >= 8, "resource catalog should include measured metal presets");
        expect(MaterialPresetCatalog.dielectrics().size() >= 8, "resource catalog should include dielectric presets");
    }

    private static boolean close(float a, float b) {
        return Math.abs(a - b) < 0.0001f;
    }

    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
