package com.radiance.client.materiallab;

/**
 * Saved Material Lab intent for one sprite.
 *
 * <p>This is deliberately a modifier recipe, not generated texture bytes. Runtime
 * texture layers are rebuilt from the active resource-pack stack and these fields.
 */
public final class MaterialRecipe {
    public static final int VERSION = 4;

    public int version = VERSION;

    public boolean generateRoughness;
    public boolean generateHeight;
    public boolean generateNormal;
    public boolean generateAo;

    public boolean roughnessOverride;
    public String roughnessSource = "pack_generated_flat";
    public String roughnessGeneratorMode = "luminance";
    public float roughness = 0.55f;
    public float roughnessGeneratedBlend = 0.65f;
    public float roughnessFlatBlend = 0.0f;
    public float roughnessBlackPoint = 0.0f;
    public float roughnessMidpoint = 0.5f;
    public float roughnessWhitePoint = 1.0f;
    public float roughnessMin = 0.04f;
    public float roughnessMax = 0.96f;
    public float roughnessGamma = 1.0f;
    public float roughnessContrast = 1.0f;
    public float roughnessEdge = 1.0f;
    public float roughnessBroadDetail = 1.0f;
    public float roughnessMidDetail = 1.0f;
    public float roughnessFineDetail = 1.0f;
    public float roughnessSharpen = 0.0f;
    public float roughnessDenoise = 0.0f;
    public float roughnessWear = 0.0f;
    public float roughnessPolish = 0.0f;
    public float roughnessWetness = 0.0f;
    public float roughnessEdgeRoughness = 0.0f;
    public int roughnessBlurRadius = 0;
    public int roughnessSmoothing = 0;
    public boolean roughnessAutoRange;
    public boolean roughnessInvert;

    public boolean metallicOverride;
    public String metalMode = "pack";
    public String metalMaskSource = "flat";
    public int labPbrMetalCode = 0;
    public float metallic = 0.0f;
    public float metalMaskThreshold = 0.50f;
    public float metalMaskSoftness = 0.10f;
    public int metalMaskDilate = 0;
    public int metalMaskErode = 0;
    public int metalMaskDespeckle = 0;
    public float metalMaskClampMin = 0.0f;
    public float metalMaskClampMax = 1.0f;
    public float oxideAmount = 0.0f;
    public float oxideRoughnessInfluence = 0.0f;
    public float oxideColorBias = 0.0f;
    public String metalPreset = "";
    public boolean conductorF0RgbOverride;
    public float conductorF0R = 0.04f;
    public float conductorF0G = 0.04f;
    public float conductorF0B = 0.04f;

    public boolean porosityOverride;
    public String porosityMode = "preserve";
    public String porositySource = "luminance";
    public float porosityAmount = 0.0f;
    public float porosityBlackPoint = 0.0f;
    public float porosityMidpoint = 0.5f;
    public float porosityWhitePoint = 1.0f;
    public float porosityCavityInfluence = 0.0f;
    public float porosityWetnessCoupling = 0.0f;
    public float porositySssStrength = 0.0f;
    public float porositySssRadius = 0.0f;
    public float porositySssTintR = 1.0f;
    public float porositySssTintG = 0.75f;
    public float porositySssTintB = 0.55f;
    public float porositySssThickness = 0.5f;
    public boolean porosityInvert;

    public boolean f0Override;
    public float f0 = 0.04f;

    public boolean iorOverride;
    public float ior = 1.45f;
    public String dielectricPreset = "";

    public boolean transmissionOverride;
    public float transmission = 0.0f;

    public boolean normalStrengthOverride;
    public String normalSource = "pack_generated";
    public String normalCombineMode = "overlay_generated";
    public String normalKernelMode = "sobel";
    public String normalOrientation = "opengl";
    public float normalGeneratedBlend = 0.0f;
    public float normalPackStrength = 1.0f;
    public float normalGeneratedStrength = 1.0f;
    public float normalXStrength = 1.0f;
    public float normalYStrength = 1.0f;
    public float normalDetailFrequency = 1.0f;
    // Deprecated: retained only so old JSON with the hidden detail blend field still loads.
    @Deprecated
    public String normalDetailBlendMode = "add_detail";
    public int normalGeneratorRadius = 1;
    public int normalSmoothing = 0;
    public float normalStrength = 1.0f;

    public boolean heightOverride;
    public String heightSource = "pack_generated_flat";
    public String heightGeneratorMode = "luminance";
    public float heightGeneratedBlend = 0.75f;
    public float heightFlatBlend = 0.0f;
    public float heightFlat = 0.5f;
    public float heightBlackPoint = 0.0f;
    public float heightMidpoint = 0.5f;
    public float heightWhitePoint = 1.0f;
    public float heightMin = 0.0f;
    public float heightMax = 1.0f;
    public float heightOffset = 0.0f;
    public int heightSmoothing = 0;
    public int heightBlurRadius = 0;
    public float heightSharpen = 0.0f;
    public int heightErode = 0;
    public int heightDilate = 0;
    public float heightDenoise = 0.0f;
    public float heightScale = 0.25f;
    public float heightGamma = 1.0f;
    public boolean heightAutoRange;
    public boolean invertHeight;

    public boolean aoOverride;
    public String aoSource = "pack_generated_flat";
    public float aoGeneratedBlend = 0.0f;
    public float aoFlat = 1.0f;
    public float aoMin = 0.0f;
    public float aoMax = 1.0f;
    public boolean aoInvert;
    public float aoStrength = 1.0f;

    public boolean emissionOverride;
    public String emissionMode = "none";
    public float emissionGain = 0.0f;
    public float emissionThreshold = 0.85f;
    public float emissionThresholdLow = 0.75f;
    public float emissionThresholdHigh = 0.90f;
    public float emissionSoftness = 0.08f;
    public int emissionDilate = 0;
    public int emissionErode = 0;
    public int emissionDespeckle = 0;
    public int emissionBlurRadius = 0;
    public float emissionTintR = 1.0f;
    public float emissionTintG = 0.75f;
    public float emissionTintB = 0.45f;
    public float emissionNits = 0.0f;
    public String emissionAnimationMode = "none";
    public float emissionAnimationSpeed = 0.0f;
    public float emissionAnimationPhase = 0.0f;
    public boolean emissionInvert;

    public float absorptionR = 0.0f;
    public float absorptionG = 0.0f;
    public float absorptionB = 0.0f;
    public float absorptionDistance = 16.0f;
    public String transmissionVolumeMode = "solid_volume";
    public float refractionRoughness = 0.0f;
    public String thicknessSource = "flat";
    public float thicknessAmount = 0.5f;
    public float thicknessMin = 0.0f;
    public float thicknessMax = 1.0f;
    public float thicknessGamma = 1.0f;

    public boolean anisotropicOverride;
    public float anisotropic = 0.0f;
    public float anisotropicRotation = 0.0f;

    public boolean sheenOverride;
    public float sheenWeight = 0.0f;
    public float sheenTint = 0.0f;
    public float sheenRoughness = 0.5f;
    public String diffuseModel = "global";

    public boolean coatOverride;
    public float coatWeight = 0.0f;
    public float coatRoughness = 0.03f;
    public float coatIor = 1.5f;
    public float coatTintR = 1.0f;
    public float coatTintG = 1.0f;
    public float coatTintB = 1.0f;
    public float coatMask = 1.0f;
    public String coatMaskSource = "flat";

    public boolean displacementOverride;
    public float displacementScale = 1.0f;

    public float detailNormalStrength = 1.0f;
    // Deprecated: retained only so old JSON with the hidden anisotropy direction field still loads.
    @Deprecated
    public String anisotropicDirectionMode = "texture_tangent";
    public float filterRadius = 0.0f;
    public float mipBias = 0.0f;
    public float uvScale = 1.0f;
    public float uvOffset = 0.0f;
    public boolean flipGreen;

    public static MaterialRecipe defaults() {
        return new MaterialRecipe();
    }

    public MaterialRecipe copy() {
        MaterialRecipe copy = new MaterialRecipe();
        copy.version = version;
        copy.generateRoughness = generateRoughness;
        copy.generateHeight = generateHeight;
        copy.generateNormal = generateNormal;
        copy.generateAo = generateAo;
        copy.roughnessOverride = roughnessOverride;
        copy.roughnessSource = roughnessSource;
        copy.roughnessGeneratorMode = roughnessGeneratorMode;
        copy.roughness = roughness;
        copy.roughnessGeneratedBlend = roughnessGeneratedBlend;
        copy.roughnessFlatBlend = roughnessFlatBlend;
        copy.roughnessBlackPoint = roughnessBlackPoint;
        copy.roughnessMidpoint = roughnessMidpoint;
        copy.roughnessWhitePoint = roughnessWhitePoint;
        copy.roughnessMin = roughnessMin;
        copy.roughnessMax = roughnessMax;
        copy.roughnessGamma = roughnessGamma;
        copy.roughnessContrast = roughnessContrast;
        copy.roughnessEdge = roughnessEdge;
        copy.roughnessBroadDetail = roughnessBroadDetail;
        copy.roughnessMidDetail = roughnessMidDetail;
        copy.roughnessFineDetail = roughnessFineDetail;
        copy.roughnessSharpen = roughnessSharpen;
        copy.roughnessDenoise = roughnessDenoise;
        copy.roughnessWear = roughnessWear;
        copy.roughnessPolish = roughnessPolish;
        copy.roughnessWetness = roughnessWetness;
        copy.roughnessEdgeRoughness = roughnessEdgeRoughness;
        copy.roughnessBlurRadius = roughnessBlurRadius;
        copy.roughnessSmoothing = roughnessSmoothing;
        copy.roughnessAutoRange = roughnessAutoRange;
        copy.roughnessInvert = roughnessInvert;
        copy.metallicOverride = metallicOverride;
        copy.metalMode = metalMode;
        copy.metalMaskSource = metalMaskSource;
        copy.labPbrMetalCode = labPbrMetalCode;
        copy.metallic = metallic;
        copy.metalMaskThreshold = metalMaskThreshold;
        copy.metalMaskSoftness = metalMaskSoftness;
        copy.metalMaskDilate = metalMaskDilate;
        copy.metalMaskErode = metalMaskErode;
        copy.metalMaskDespeckle = metalMaskDespeckle;
        copy.metalMaskClampMin = metalMaskClampMin;
        copy.metalMaskClampMax = metalMaskClampMax;
        copy.oxideAmount = oxideAmount;
        copy.oxideRoughnessInfluence = oxideRoughnessInfluence;
        copy.oxideColorBias = oxideColorBias;
        copy.metalPreset = metalPreset;
        copy.conductorF0RgbOverride = conductorF0RgbOverride;
        copy.conductorF0R = conductorF0R;
        copy.conductorF0G = conductorF0G;
        copy.conductorF0B = conductorF0B;
        copy.porosityOverride = porosityOverride;
        copy.porosityMode = porosityMode;
        copy.porositySource = porositySource;
        copy.porosityAmount = porosityAmount;
        copy.porosityBlackPoint = porosityBlackPoint;
        copy.porosityMidpoint = porosityMidpoint;
        copy.porosityWhitePoint = porosityWhitePoint;
        copy.porosityCavityInfluence = porosityCavityInfluence;
        copy.porosityWetnessCoupling = porosityWetnessCoupling;
        copy.porositySssStrength = porositySssStrength;
        copy.porositySssRadius = porositySssRadius;
        copy.porositySssTintR = porositySssTintR;
        copy.porositySssTintG = porositySssTintG;
        copy.porositySssTintB = porositySssTintB;
        copy.porositySssThickness = porositySssThickness;
        copy.porosityInvert = porosityInvert;
        copy.f0Override = f0Override;
        copy.f0 = f0;
        copy.iorOverride = iorOverride;
        copy.ior = ior;
        copy.dielectricPreset = dielectricPreset;
        copy.transmissionOverride = transmissionOverride;
        copy.transmission = transmission;
        copy.normalStrengthOverride = normalStrengthOverride;
        copy.normalSource = normalSource;
        copy.normalCombineMode = normalCombineMode;
        copy.normalKernelMode = normalKernelMode;
        copy.normalOrientation = normalOrientation;
        copy.normalGeneratedBlend = normalGeneratedBlend;
        copy.normalPackStrength = normalPackStrength;
        copy.normalGeneratedStrength = normalGeneratedStrength;
        copy.normalXStrength = normalXStrength;
        copy.normalYStrength = normalYStrength;
        copy.normalDetailFrequency = normalDetailFrequency;
        copy.normalDetailBlendMode = normalDetailBlendMode;
        copy.normalGeneratorRadius = normalGeneratorRadius;
        copy.normalSmoothing = normalSmoothing;
        copy.normalStrength = normalStrength;
        copy.heightOverride = heightOverride;
        copy.heightSource = heightSource;
        copy.heightGeneratorMode = heightGeneratorMode;
        copy.heightGeneratedBlend = heightGeneratedBlend;
        copy.heightFlatBlend = heightFlatBlend;
        copy.heightFlat = heightFlat;
        copy.heightBlackPoint = heightBlackPoint;
        copy.heightMidpoint = heightMidpoint;
        copy.heightWhitePoint = heightWhitePoint;
        copy.heightMin = heightMin;
        copy.heightMax = heightMax;
        copy.heightOffset = heightOffset;
        copy.heightSmoothing = heightSmoothing;
        copy.heightBlurRadius = heightBlurRadius;
        copy.heightSharpen = heightSharpen;
        copy.heightErode = heightErode;
        copy.heightDilate = heightDilate;
        copy.heightDenoise = heightDenoise;
        copy.heightScale = heightScale;
        copy.heightGamma = heightGamma;
        copy.heightAutoRange = heightAutoRange;
        copy.invertHeight = invertHeight;
        copy.aoOverride = aoOverride;
        copy.aoSource = aoSource;
        copy.aoGeneratedBlend = aoGeneratedBlend;
        copy.aoFlat = aoFlat;
        copy.aoMin = aoMin;
        copy.aoMax = aoMax;
        copy.aoInvert = aoInvert;
        copy.aoStrength = aoStrength;
        copy.emissionOverride = emissionOverride;
        copy.emissionMode = emissionMode;
        copy.emissionGain = emissionGain;
        copy.emissionThreshold = emissionThreshold;
        copy.emissionThresholdLow = emissionThresholdLow;
        copy.emissionThresholdHigh = emissionThresholdHigh;
        copy.emissionSoftness = emissionSoftness;
        copy.emissionDilate = emissionDilate;
        copy.emissionErode = emissionErode;
        copy.emissionDespeckle = emissionDespeckle;
        copy.emissionBlurRadius = emissionBlurRadius;
        copy.emissionTintR = emissionTintR;
        copy.emissionTintG = emissionTintG;
        copy.emissionTintB = emissionTintB;
        copy.emissionNits = emissionNits;
        copy.emissionAnimationMode = emissionAnimationMode;
        copy.emissionAnimationSpeed = emissionAnimationSpeed;
        copy.emissionAnimationPhase = emissionAnimationPhase;
        copy.emissionInvert = emissionInvert;
        copy.absorptionR = absorptionR;
        copy.absorptionG = absorptionG;
        copy.absorptionB = absorptionB;
        copy.absorptionDistance = absorptionDistance;
        copy.transmissionVolumeMode = transmissionVolumeMode;
        copy.refractionRoughness = refractionRoughness;
        copy.thicknessSource = thicknessSource;
        copy.thicknessAmount = thicknessAmount;
        copy.thicknessMin = thicknessMin;
        copy.thicknessMax = thicknessMax;
        copy.thicknessGamma = thicknessGamma;
        copy.anisotropicOverride = anisotropicOverride;
        copy.anisotropic = anisotropic;
        copy.anisotropicRotation = anisotropicRotation;
        copy.sheenOverride = sheenOverride;
        copy.sheenWeight = sheenWeight;
        copy.sheenTint = sheenTint;
        copy.sheenRoughness = sheenRoughness;
        copy.diffuseModel = diffuseModel;
        copy.coatOverride = coatOverride;
        copy.coatWeight = coatWeight;
        copy.coatRoughness = coatRoughness;
        copy.coatIor = coatIor;
        copy.coatTintR = coatTintR;
        copy.coatTintG = coatTintG;
        copy.coatTintB = coatTintB;
        copy.coatMask = coatMask;
        copy.coatMaskSource = coatMaskSource;
        copy.displacementOverride = displacementOverride;
        copy.displacementScale = displacementScale;
        copy.detailNormalStrength = detailNormalStrength;
        copy.anisotropicDirectionMode = anisotropicDirectionMode;
        copy.filterRadius = filterRadius;
        copy.mipBias = mipBias;
        copy.uvScale = uvScale;
        copy.uvOffset = uvOffset;
        copy.flipGreen = flipGreen;
        return copy;
    }

    public boolean isDefaultIntent() {
        return !generateRoughness
            && !generateHeight
            && !generateNormal
            && !generateAo
            && !roughnessOverride
            && !metallicOverride
            && !porosityOverride
            && !conductorF0RgbOverride
            && !f0Override
            && !iorOverride
            && !transmissionOverride
            && !normalStrengthOverride
            && !heightOverride
            && !aoOverride
            && !emissionOverride
            && !anisotropicOverride
            && !sheenOverride
            && !coatOverride
            && !displacementOverride
            && !invertHeight
            && !flipGreen
            && !roughnessInvert
            && !porosityInvert
            && !aoInvert
            && !emissionInvert
            && !differs(oxideAmount, 0.0f)
            && !differs(oxideRoughnessInfluence, 0.0f)
            && !differs(oxideColorBias, 0.0f)
            && !differs(porositySssStrength, 0.0f)
            && !differs(porositySssRadius, 0.0f)
            && !differs(porositySssThickness, 0.5f)
            && !differs(porositySssTintR, 1.0f)
            && !differs(porositySssTintG, 0.75f)
            && !differs(porositySssTintB, 0.55f)
            && stringEquals(thicknessSource, "flat")
            && !differs(thicknessAmount, 0.5f)
            && !differs(thicknessMin, 0.0f)
            && !differs(thicknessMax, 1.0f)
            && !differs(thicknessGamma, 1.0f)
            && !differs(absorptionR, 0.0f)
            && !differs(absorptionG, 0.0f)
            && !differs(absorptionB, 0.0f)
            && !differs(absorptionDistance, 16.0f)
            && stringEquals(transmissionVolumeMode, "solid_volume")
            && !differs(refractionRoughness, 0.0f)
            && !differs(anisotropicRotation, 0.0f)
            && !differs(sheenRoughness, 0.5f)
            && stringEquals(diffuseModel, "global")
            && !differs(coatIor, 1.5f)
            && !differs(coatTintR, 1.0f)
            && !differs(coatTintG, 1.0f)
            && !differs(coatTintB, 1.0f)
            && !differs(coatMask, 1.0f)
            && !differs(uvScale, 1.0f)
            && !differs(uvOffset, 0.0f)
            && !differs(filterRadius, 0.0f)
            && !differs(mipBias, 0.0f)
            && !differs(displacementScale, 1.0f);
    }

    private static boolean differs(float value, float baseline) {
        return Math.abs(value - baseline) > 0.0001f;
    }

    private static boolean stringEquals(String value, String baseline) {
        return baseline.equals(value == null ? "" : value);
    }

    public void applyPreset(MaterialPresetCatalog.Preset preset) {
        if (preset == null) return;
        if (preset.metal()) {
            metallicOverride = true;
            metalMode = preset.labPbrMetalCode() >= 230 ? "radser_measured" : "radser_measured";
            metalMaskSource = "flat";
            labPbrMetalCode = preset.labPbrMetalCode();
            metallic = 1.0f;
            f0Override = true;
            f0 = preset.f0();
            conductorF0RgbOverride = preset.hasConductorF0Rgb();
            conductorF0R = preset.conductorF0R();
            conductorF0G = preset.conductorF0G();
            conductorF0B = preset.conductorF0B();
            metalPreset = preset.id();
            roughnessOverride = true;
            roughness = preset.roughness();
            transmissionOverride = true;
            transmission = 0.0f;
        } else {
            metallicOverride = true;
            metalMode = "dielectric";
            metalMaskSource = "flat";
            labPbrMetalCode = 0;
            metallic = 0.0f;
            conductorF0RgbOverride = false;
            iorOverride = true;
            ior = preset.ior();
            f0Override = true;
            f0 = f0FromIor(ior);
            dielectricPreset = preset.id();
            roughnessOverride = true;
            roughness = preset.roughness();
            transmissionOverride = preset.transmission() > 0.0f;
            transmission = preset.transmission();
        }
    }

    public static float f0FromIor(float ior) {
        float clamped = clamp(ior, 1.0f, 3.0f);
        float v = (clamped - 1.0f) / (clamped + 1.0f);
        return clamp(v * v, 0.0f, 0.99f);
    }

    static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
