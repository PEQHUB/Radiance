package com.radiance.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

/**
 * Portable data class representing a single block's 13 Principled BSDF material
 * properties plus metadata. Serializable to JSON (.radmat) and human-readable text (.txt).
 */
public class MaterialData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();

    // Metadata
    public int version = 1;
    public String blockId = "";
    public String displayName = "";
    public String author = "";
    public String description = "";

    // 13 material properties (same units as Options arrays)
    public int f0R, f0G, f0B;          // 0-1000 permille
    public int roughness;              // 0-100 percent
    public int metallic;               // 0-1000 permille
    public int transmission;           // 0-1000 permille
    public int ior;                    // 0-3000 (×1000)
    public int subsurface;             // 0-1000 permille
    public int anisotropic;            // 0-1000 permille
    public int sheenWeight;            // 0-1000 permille
    public int sheenTint;              // 0-1000 permille
    public int coatWeight;             // 0-1000 permille
    public int coatRoughness;          // 0-100 percent
    // Procedural noise
    public int noiseScale = 50;        // 1-5000 (/10 = 0.1-500.0 world units)
    public int noiseStrength;          // 0-1000 permille (0.0-100.0%)
    public int noiseOctaves = 2;       // 1-8
    public int noiseType;              // 0-7 (noise algorithm)
    public int noiseSeed;              // 0-999
    // Gamut boost
    public int gamutBoost = 100;       // 0-200 (×0.01 = 0.00-2.00 multiplier)
    // POM & Normal
    public int pomDepth;               // 0-200 (×0.01, 0=off, per-block POM depth)
    public int normalStrength = 100;   // 0-200 (×0.01 = 0.00-2.00, 100=neutral)
    // Per-channel input types: 0=Auto, 1=Custom, 2=Flat
    public int normalInputType;
    public int specularInputType;
    public String customNormalPath = "";
    public String customSpecularPath = "";
    // Noise target channels: bit 0=roughness, bit 1=normal, bit 2=metallic
    public int noiseTarget = 1;
    // Displacement / POM settings
    public int heightFilter;               // 0=None, 1=Bilinear, 2=Trilinear
    public int filterRadius;               // filter kernel radius
    public int mipBias;                    // mip level bias
    public int pomMode;                    // 0=Off, 1=Basic, 2=Steep, 3=POM, 4=Contact-refine
    public int pomSteps;                   // max ray-march steps
    public int pomRefinement;              // binary search refinement steps
    public boolean pomClipSilhouette;      // clip at silhouette edges
    public boolean pomAreaLightOffset;     // offset area light origins
    public boolean pomMotionVectors;       // generate motion vectors for POM
    public int heightSource;               // 0=Alpha, 1=Luminance, 2=Red
    public int heightContrast;             // contrast adjustment
    public int heightRemapMin;             // remap black point
    public int heightRemapMax;             // remap white point
    public int heightOffset;               // vertical offset
    public int normalClamp;                // normal map clamping
    public int geometricBlend;             // geometric normal blending
    public int normalDistanceFade;         // fade normals with distance
    public int pomAOStrength;              // POM ambient occlusion strength

    public MaterialData() {}

    /** Read current values from Options arrays for the given block index. */
    public static MaterialData fromOptions(int blockIndex) {
        MaterialBlock[] blocks = MaterialBlock.values();
        if (blockIndex < 0 || blockIndex >= blocks.length) return null;
        MaterialBlock block = blocks[blockIndex];

        MaterialData d = new MaterialData();
        d.blockId = block.getId();
        d.displayName = Text.translatable("options.video.materials." + block.getId()).getString();
        d.f0R = Options.materialF0R[blockIndex];
        d.f0G = Options.materialF0G[blockIndex];
        d.f0B = Options.materialF0B[blockIndex];
        d.roughness = Options.materialRoughness[blockIndex];
        d.metallic = Options.materialMetallic[blockIndex];
        d.transmission = Options.materialTransmission[blockIndex];
        d.ior = Options.materialIOR[blockIndex];
        d.subsurface = Options.materialSubsurface[blockIndex];
        d.anisotropic = Options.materialAnisotropic[blockIndex];
        d.sheenWeight = Options.materialSheenWeight[blockIndex];
        d.sheenTint = Options.materialSheenTint[blockIndex];
        d.coatWeight = Options.materialCoatWeight[blockIndex];
        d.coatRoughness = Options.materialCoatRoughness[blockIndex];
        d.noiseScale = Options.materialNoiseScale[blockIndex];
        d.noiseStrength = Options.materialNoiseStrength[blockIndex];
        d.noiseOctaves = Options.materialNoiseOctaves[blockIndex];
        d.noiseType = Options.materialNoiseType[blockIndex];
        d.noiseSeed = Options.materialNoiseSeed[blockIndex];
        d.gamutBoost = Options.materialGamutBoost[blockIndex];
        d.pomDepth = Options.materialPomDepth[blockIndex];
        d.normalStrength = Options.materialNormalStrength[blockIndex];
        d.normalInputType = Options.materialNormalInputType[blockIndex];
        d.specularInputType = Options.materialSpecularInputType[blockIndex];
        d.customNormalPath = Options.materialCustomNormalPath[blockIndex];
        d.customSpecularPath = Options.materialCustomSpecularPath[blockIndex];
        d.noiseTarget = Options.materialNoiseTarget[blockIndex];
        d.heightFilter = Options.materialHeightFilter[blockIndex];
        d.filterRadius = Options.materialFilterRadius[blockIndex];
        d.mipBias = Options.materialMipBias[blockIndex];
        d.pomMode = Options.materialPomMode[blockIndex];
        d.pomSteps = Options.materialPomSteps[blockIndex];
        d.pomRefinement = Options.materialPomRefinement[blockIndex];
        d.pomClipSilhouette = Options.materialPomClipSilhouette[blockIndex];
        d.pomAreaLightOffset = Options.materialPomAreaLightOffset[blockIndex];
        d.pomMotionVectors = Options.materialPomMotionVectors[blockIndex];
        d.heightSource = Options.materialHeightSource[blockIndex];
        d.heightContrast = Options.materialHeightContrast[blockIndex];
        d.heightRemapMin = Options.materialHeightRemapMin[blockIndex];
        d.heightRemapMax = Options.materialHeightRemapMax[blockIndex];
        d.heightOffset = Options.materialHeightOffset[blockIndex];
        d.normalClamp = Options.materialNormalClamp[blockIndex];
        d.geometricBlend = Options.materialGeometricBlend[blockIndex];
        d.normalDistanceFade = Options.materialNormalDistanceFade[blockIndex];
        d.pomAOStrength = Options.materialPomAOStrength[blockIndex];
        return d;
    }

    /** Create from a MaterialBlock's default values. */
    public static MaterialData fromBlock(MaterialBlock block) {
        MaterialData d = new MaterialData();
        d.blockId = block.getId();
        d.displayName = Text.translatable("options.video.materials." + block.getId()).getString();
        d.f0R = block.getDefaultF0R();
        d.f0G = block.getDefaultF0G();
        d.f0B = block.getDefaultF0B();
        d.roughness = block.getDefaultRoughness();
        d.metallic = block.getDefaultMetallic();
        d.transmission = block.getDefaultTransmission();
        d.ior = block.getDefaultIOR();
        d.subsurface = block.getDefaultSubsurface();
        d.anisotropic = block.getDefaultAnisotropic();
        d.sheenWeight = block.getDefaultSheenWeight();
        d.sheenTint = block.getDefaultSheenTint();
        d.coatWeight = block.getDefaultCoatWeight();
        d.coatRoughness = block.getDefaultCoatRoughness();
        return d;
    }

    /** Write all 13 properties into Options arrays. Caller is responsible for persisting. */
    public void applyToOptions(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= MaterialBlock.values().length) return;
        Options.materialF0R[blockIndex] = f0R;
        Options.materialF0G[blockIndex] = f0G;
        Options.materialF0B[blockIndex] = f0B;
        Options.materialRoughness[blockIndex] = roughness;
        Options.materialMetallic[blockIndex] = metallic;
        Options.materialTransmission[blockIndex] = transmission;
        Options.materialIOR[blockIndex] = ior;
        Options.materialSubsurface[blockIndex] = subsurface;
        Options.materialAnisotropic[blockIndex] = anisotropic;
        Options.materialSheenWeight[blockIndex] = sheenWeight;
        Options.materialSheenTint[blockIndex] = sheenTint;
        Options.materialCoatWeight[blockIndex] = coatWeight;
        Options.materialCoatRoughness[blockIndex] = coatRoughness;
        Options.materialNoiseScale[blockIndex] = noiseScale;
        Options.materialNoiseStrength[blockIndex] = noiseStrength;
        Options.materialNoiseOctaves[blockIndex] = noiseOctaves;
        Options.materialNoiseType[blockIndex] = noiseType;
        Options.materialNoiseSeed[blockIndex] = noiseSeed;
        Options.materialGamutBoost[blockIndex] = gamutBoost;
        Options.materialPomDepth[blockIndex] = pomDepth;
        Options.materialNormalStrength[blockIndex] = normalStrength;
        Options.materialNormalInputType[blockIndex] = normalInputType;
        Options.materialSpecularInputType[blockIndex] = specularInputType;
        Options.materialCustomNormalPath[blockIndex] = customNormalPath != null ? customNormalPath : "";
        Options.materialCustomSpecularPath[blockIndex] = customSpecularPath != null ? customSpecularPath : "";
        Options.materialNoiseTarget[blockIndex] = noiseTarget;
        Options.materialHeightFilter[blockIndex] = heightFilter;
        Options.materialFilterRadius[blockIndex] = filterRadius;
        Options.materialMipBias[blockIndex] = mipBias;
        Options.materialPomMode[blockIndex] = pomMode;
        Options.materialPomSteps[blockIndex] = pomSteps;
        Options.materialPomRefinement[blockIndex] = pomRefinement;
        Options.materialPomClipSilhouette[blockIndex] = pomClipSilhouette;
        Options.materialPomAreaLightOffset[blockIndex] = pomAreaLightOffset;
        Options.materialPomMotionVectors[blockIndex] = pomMotionVectors;
        Options.materialHeightSource[blockIndex] = heightSource;
        Options.materialHeightContrast[blockIndex] = heightContrast;
        Options.materialHeightRemapMin[blockIndex] = heightRemapMin;
        Options.materialHeightRemapMax[blockIndex] = heightRemapMax;
        Options.materialHeightOffset[blockIndex] = heightOffset;
        Options.materialNormalClamp[blockIndex] = normalClamp;
        Options.materialGeometricBlend[blockIndex] = geometricBlend;
        Options.materialNormalDistanceFade[blockIndex] = normalDistanceFade;
        Options.materialPomAOStrength[blockIndex] = pomAOStrength;
        Options.markMaterialDirty();
    }

    /** Find the MaterialBlock index for this data's blockId. Returns -1 if not found. */
    public int findBlockIndex() {
        MaterialBlock[] blocks = MaterialBlock.values();
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i].getId().equals(blockId)) return i;
        }
        return -1;
    }

    /** Whether all 13 properties match the MaterialBlock enum defaults. */
    public boolean isDefault(MaterialBlock block) {
        return f0R == block.getDefaultF0R()
            && f0G == block.getDefaultF0G()
            && f0B == block.getDefaultF0B()
            && roughness == block.getDefaultRoughness()
            && metallic == block.getDefaultMetallic()
            && transmission == block.getDefaultTransmission()
            && ior == block.getDefaultIOR()
            && subsurface == block.getDefaultSubsurface()
            && anisotropic == block.getDefaultAnisotropic()
            && sheenWeight == block.getDefaultSheenWeight()
            && sheenTint == block.getDefaultSheenTint()
            && coatWeight == block.getDefaultCoatWeight()
            && coatRoughness == block.getDefaultCoatRoughness()
            && noiseScale == 50 && noiseStrength == 0 && noiseOctaves == 2
            && noiseType == 0 && noiseSeed == 0
            && gamutBoost == 100;
    }

    // ── JSON serialization ──

    public String toJson() { return GSON.toJson(this); }
    public String toCompactJson() { return GSON_COMPACT.toJson(this); }

    public static MaterialData fromJson(String json) {
        try {
            return GSON.fromJson(json, MaterialData.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Human-readable text serialization ──

    public String toReadableText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Radiance Material: ").append(displayName).append('\n');
        sb.append("Block ID: ").append(blockId).append('\n');
        if (!author.isEmpty()) sb.append("Author: ").append(author).append('\n');
        if (!description.isEmpty()) sb.append("Description: ").append(description).append('\n');
        sb.append("---\n");
        sb.append(String.format("F0 Red: %.1f%%\n", f0R / 10.0));
        sb.append(String.format("F0 Green: %.1f%%\n", f0G / 10.0));
        sb.append(String.format("F0 Blue: %.1f%%\n", f0B / 10.0));
        sb.append(String.format("Roughness: %d%%\n", roughness));
        sb.append(String.format("Metallic: %.1f%%\n", metallic / 10.0));
        sb.append(String.format("Transmission: %.1f%%\n", transmission / 10.0));
        sb.append(String.format("IOR: %.3f\n", ior / 1000.0));
        sb.append(String.format("Subsurface: %.1f%%\n", subsurface / 10.0));
        sb.append(String.format("Anisotropic: %.1f%%\n", anisotropic / 10.0));
        sb.append(String.format("Sheen Weight: %.1f%%\n", sheenWeight / 10.0));
        sb.append(String.format("Sheen Tint: %.1f%%\n", sheenTint / 10.0));
        sb.append(String.format("Coat Weight: %.1f%%\n", coatWeight / 10.0));
        sb.append(String.format("Coat Roughness: %d%%\n", coatRoughness));
        if (noiseStrength > 0) {
            sb.append(String.format("Noise Type: %d\n", noiseType));
            sb.append(String.format("Noise Seed: %d\n", noiseSeed));
            sb.append(String.format("Noise Scale: %.1f\n", noiseScale / 10.0));
            sb.append(String.format("Noise Strength: %.1f%%\n", noiseStrength / 10.0));
            sb.append(String.format("Noise Octaves: %d\n", noiseOctaves));
        }
        if (gamutBoost != 100) {
            sb.append(String.format("Gamut Boost: %.2f\n", gamutBoost / 100.0));
        }
        if (pomDepth > 0) {
            sb.append(String.format("POM Depth: %.2f\n", pomDepth / 100.0));
        }
        if (normalStrength != 100) {
            sb.append(String.format("Normal Strength: %.2f\n", normalStrength / 100.0));
        }
        if (heightFilter != 0) sb.append(String.format("Height Filter: %d\n", heightFilter));
        if (filterRadius != 0) sb.append(String.format("Filter Radius: %d\n", filterRadius));
        if (mipBias != 0) sb.append(String.format("Mip Bias: %d\n", mipBias));
        if (pomMode != 0) sb.append(String.format("POM Mode: %d\n", pomMode));
        if (pomSteps != 0) sb.append(String.format("POM Steps: %d\n", pomSteps));
        if (pomRefinement != 0) sb.append(String.format("POM Refinement: %d\n", pomRefinement));
        if (pomClipSilhouette) sb.append("POM Clip Silhouette: true\n");
        if (pomAreaLightOffset) sb.append("POM Area Light Offset: true\n");
        if (pomMotionVectors) sb.append("POM Motion Vectors: true\n");
        if (heightSource != 0) sb.append(String.format("Height Source: %d\n", heightSource));
        if (heightContrast != 0) sb.append(String.format("Height Contrast: %d\n", heightContrast));
        if (heightRemapMin != 0) sb.append(String.format("Height Remap Min: %d\n", heightRemapMin));
        if (heightRemapMax != 0) sb.append(String.format("Height Remap Max: %d\n", heightRemapMax));
        if (heightOffset != 0) sb.append(String.format("Height Offset: %d\n", heightOffset));
        if (normalClamp != 0) sb.append(String.format("Normal Clamp: %d\n", normalClamp));
        if (geometricBlend != 0) sb.append(String.format("Geometric Blend: %d\n", geometricBlend));
        if (normalDistanceFade != 0) sb.append(String.format("Normal Distance Fade: %d\n", normalDistanceFade));
        if (pomAOStrength != 0) sb.append(String.format("POM AO Strength: %d\n", pomAOStrength));
        return sb.toString();
    }

    public static MaterialData fromReadableText(String text) {
        try {
            MaterialData d = new MaterialData();
            String[] lines = text.split("\n");
            boolean pastSeparator = false;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("---")) { pastSeparator = true; continue; }
                if (!pastSeparator) {
                    if (line.startsWith("Radiance Material: ")) d.displayName = line.substring(19).trim();
                    else if (line.startsWith("Block ID: ")) d.blockId = line.substring(10).trim();
                    else if (line.startsWith("Author: ")) d.author = line.substring(8).trim();
                    else if (line.startsWith("Description: ")) d.description = line.substring(13).trim();
                } else {
                    // Parse "Key: value%" lines
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    String key = line.substring(0, colon).trim();
                    String val = line.substring(colon + 1).trim().replace("%", "");
                    switch (key) {
                        case "F0 Red"        -> d.f0R = Math.round(Float.parseFloat(val) * 10);
                        case "F0 Green"      -> d.f0G = Math.round(Float.parseFloat(val) * 10);
                        case "F0 Blue"       -> d.f0B = Math.round(Float.parseFloat(val) * 10);
                        case "Roughness"     -> d.roughness = Integer.parseInt(val);
                        case "Metallic"      -> d.metallic = Math.round(Float.parseFloat(val) * 10);
                        case "Transmission"  -> d.transmission = Math.round(Float.parseFloat(val) * 10);
                        case "IOR"           -> d.ior = Math.round(Float.parseFloat(val) * 1000);
                        case "Subsurface"    -> d.subsurface = Math.round(Float.parseFloat(val) * 10);
                        case "Anisotropic"   -> d.anisotropic = Math.round(Float.parseFloat(val) * 10);
                        case "Sheen Weight"  -> d.sheenWeight = Math.round(Float.parseFloat(val) * 10);
                        case "Sheen Tint"    -> d.sheenTint = Math.round(Float.parseFloat(val) * 10);
                        case "Coat Weight"   -> d.coatWeight = Math.round(Float.parseFloat(val) * 10);
                        case "Coat Roughness"-> d.coatRoughness = Integer.parseInt(val);
                        case "Noise Type"    -> d.noiseType = Integer.parseInt(val);
                        case "Noise Seed"    -> d.noiseSeed = Integer.parseInt(val);
                        case "Noise Scale"   -> d.noiseScale = Math.round(Float.parseFloat(val) * 10);
                        case "Noise Strength"-> d.noiseStrength = Math.round(Float.parseFloat(val) * 10);
                        case "Noise Octaves" -> d.noiseOctaves = Integer.parseInt(val);
                        case "Gamut Boost"   -> d.gamutBoost = Math.round(Float.parseFloat(val) * 100);
                        case "POM Depth"     -> d.pomDepth = Math.round(Float.parseFloat(val) * 100);
                        case "Normal Strength"  -> d.normalStrength = Math.round(Float.parseFloat(val) * 100);
                        case "Height Filter"    -> d.heightFilter = Integer.parseInt(val);
                        case "Filter Radius"    -> d.filterRadius = Integer.parseInt(val);
                        case "Mip Bias"         -> d.mipBias = Integer.parseInt(val);
                        case "POM Mode"         -> d.pomMode = Integer.parseInt(val);
                        case "POM Steps"        -> d.pomSteps = Integer.parseInt(val);
                        case "POM Refinement"   -> d.pomRefinement = Integer.parseInt(val);
                        case "POM Clip Silhouette"    -> d.pomClipSilhouette = Boolean.parseBoolean(val);
                        case "POM Area Light Offset"  -> d.pomAreaLightOffset = Boolean.parseBoolean(val);
                        case "POM Motion Vectors"     -> d.pomMotionVectors = Boolean.parseBoolean(val);
                        case "Height Source"     -> d.heightSource = Integer.parseInt(val);
                        case "Height Contrast"   -> d.heightContrast = Integer.parseInt(val);
                        case "Height Remap Min"  -> d.heightRemapMin = Integer.parseInt(val);
                        case "Height Remap Max"  -> d.heightRemapMax = Integer.parseInt(val);
                        case "Height Offset"     -> d.heightOffset = Integer.parseInt(val);
                        case "Normal Clamp"      -> d.normalClamp = Integer.parseInt(val);
                        case "Geometric Blend"   -> d.geometricBlend = Integer.parseInt(val);
                        case "Normal Distance Fade" -> d.normalDistanceFade = Integer.parseInt(val);
                        case "POM AO Strength"   -> d.pomAOStrength = Integer.parseInt(val);
                        default -> {}
                    }
                }
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Compute a 64-bit hash from the 13 material properties (for sphere cache keys). */
    public long propertyHash() {
        long h = 17;
        h = h * 31 + f0R;   h = h * 31 + f0G;   h = h * 31 + f0B;
        h = h * 31 + roughness;  h = h * 31 + metallic;
        h = h * 31 + transmission;  h = h * 31 + ior;
        h = h * 31 + subsurface;  h = h * 31 + anisotropic;
        h = h * 31 + sheenWeight;  h = h * 31 + sheenTint;
        h = h * 31 + coatWeight;  h = h * 31 + coatRoughness;
        h = h * 31 + noiseScale; h = h * 31 + noiseStrength; h = h * 31 + noiseOctaves;
        h = h * 31 + noiseType; h = h * 31 + noiseSeed;
        h = h * 31 + gamutBoost;
        h = h * 31 + heightFilter;  h = h * 31 + filterRadius;  h = h * 31 + mipBias;
        h = h * 31 + pomMode;  h = h * 31 + pomSteps;  h = h * 31 + pomRefinement;
        h = h * 31 + (pomClipSilhouette ? 1 : 0);  h = h * 31 + (pomAreaLightOffset ? 1 : 0);  h = h * 31 + (pomMotionVectors ? 1 : 0);
        h = h * 31 + heightSource;  h = h * 31 + heightContrast;
        h = h * 31 + heightRemapMin;  h = h * 31 + heightRemapMax;  h = h * 31 + heightOffset;
        h = h * 31 + normalClamp;  h = h * 31 + geometricBlend;
        h = h * 31 + normalDistanceFade;  h = h * 31 + pomAOStrength;
        return h;
    }
}
