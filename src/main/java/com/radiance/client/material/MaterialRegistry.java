package com.radiance.client.material;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.util.MaterialBlock;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Registry for the unified material class system.
 * Packs Options material arrays into a 36 KB MaterialClassMapping SSBO
 * (256 entries × 144 bytes, std430) and uploads via JNI.
 */
public class MaterialRegistry {
    private static boolean initialized = false;
    private static int dirtyFrames = 0;

    private static final int MAX_ENTRIES = 512;
    private static final int ENTRY_SIZE = 144; // 9 × vec4
    private static final int BUFFER_SIZE = MAX_ENTRIES * ENTRY_SIZE;

    // Per-block luminance histogram bounds (precomputed at texture load)
    private static final float[] blockLumMin = new float[Options.MAX_MATERIALS];
    private static final float[] blockLumMax = new float[Options.MAX_MATERIALS];
    static {
        java.util.Arrays.fill(blockLumMax, 1.0f); // safe default: avoid div-by-zero
    }

    public static void setLumRange(int ordinal, float min, float max) {
        if (ordinal < 0 || ordinal >= Options.MAX_MATERIALS) return;
        blockLumMin[ordinal] = min;
        blockLumMax[ordinal] = max;
        dirtyFrames = 3;
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        dirtyFrames = 3;
    }

    public static void uploadIfDirty() {
        if (dirtyFrames <= 0) return;
        dirtyFrames--;

        ByteBuffer buf = MemoryUtil.memAlloc(BUFFER_SIZE);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        try {
            MaterialBlock[] blocks = MaterialBlock.values();
            int activeMaterials = Math.max(
                MaterialBlock.getActiveMaterialCount(),
                EntityMaterial.ENTITY_ORDINAL_BASE + EntityMaterial.getActiveCount()
            );

            for (int i = 0; i < Math.min(activeMaterials, MAX_ENTRIES); i++) {
                MaterialBlock mb = i < blocks.length ? blocks[i] : null;
                packEntry(buf, i, mb);
            }

            for (int i = Math.min(activeMaterials, MAX_ENTRIES); i < MAX_ENTRIES; i++) {
                packDefault(buf);
            }

            buf.position(0);
            BufferProxy.updateMaterialClassMapping(MemoryUtil.memAddress(buf));
        } finally {
            MemoryUtil.memFree(buf);
        }
    }

    private static void packEntry(ByteBuffer buf, int i, MaterialBlock mb) {
        // Pack 0: f0.rgb, roughness
        buf.putFloat(Options.materialF0R[i] / 1000f);
        buf.putFloat(Options.materialF0G[i] / 1000f);
        buf.putFloat(Options.materialF0B[i] / 1000f);
        buf.putFloat(Options.materialRoughness[i] / 100f);

        // Pack 1: metallic, transmission, ior, subsurface
        buf.putFloat(Options.materialMetallic[i] / 1000f);
        float transmission = Options.materialTransmission[i] == 0
                ? -1.0f
                : Options.materialTransmission[i] / 1000f;
        buf.putFloat(transmission);
        buf.putFloat(Options.materialIOR[i] / 1000f);
        buf.putFloat(Options.materialSubsurface[i] / 1000f);

        // Pack 2: anisotropic, sheenWeight, sheenTint, coatWeight
        buf.putFloat(Options.materialAnisotropic[i] / 1000f);
        buf.putFloat(Options.materialSheenWeight[i] / 1000f);
        buf.putFloat(Options.materialSheenTint[i] / 1000f);
        buf.putFloat(Options.materialCoatWeight[i] / 1000f);

        // Pack 3: coatRoughness, noiseScale, noiseStrength, noisePacked
        buf.putFloat(Options.materialCoatRoughness[i] / 100f);
        buf.putFloat(Options.materialNoiseScale[i] / 10f);
        buf.putFloat(Options.materialNoiseStrength[i] / 1000f);
        int noisePacked = Options.materialNoiseOctaves[i]
                | (Options.materialNoiseType[i] << 4)
                | (Options.materialNoiseSeed[i] << 9)
                | (Options.materialNoiseWrap[i] << 20);
        buf.putInt(noisePacked);

        // Pack 4: pomPacked0, pomPacked1, pomPacked2, pomDepth
        // Per-block displacement + height pipeline parameters (bit-packed uint32s + float depth)
        int pp0 = (Options.materialHeightFilter[i] & 0x7)                                // bits 0-2:  heightFilter
                | ((Options.materialPomMode[i] & 0x7) << 3)                              // bits 3-5:  displacementMethod
                | ((Options.materialHeightSource[i] & 0x7) << 6)                         // bits 6-8:  heightSource
                | ((Math.min(Options.materialPomSteps[i], 127) & 0x7F) << 9)             // bits 9-15: ddaSteps
                | ((Options.materialPomRefinement[i] & 0xF) << 16)                       // bits 16-19: ddaRefinement
                | ((Options.materialFilterRadius[i] & 0xF) << 20)                        // bits 20-23: filterRadius
                | ((Options.materialMipBias[i] & 0xF) << 24)                             // bits 24-27: mipBias
                | ((Options.materialPomClipSilhouette[i] ? 1 : 0) << 29)                 // bit 29: clipSilhouette
                | ((Options.materialPomMotionVectors[i] ? 1 : 0) << 30)                  // bit 30: motionVectors
                | ((Options.materialPomAreaLightOffset[i] ? 1 : 0) << 31);               // bit 31: areaLightOffset
        buf.putInt(pp0);

        int pp1 = (Options.materialNormalClamp[i] & 0xFF)                                // bits 0-7:  normalClamp
                | ((Options.materialGeometricBlend[i] & 0xFF) << 8)                      // bits 8-15: geometricBlend
                | ((Options.materialPomAOStrength[i] & 0xFF) << 16)                      // bits 16-23: pomAOStrength
                | ((Options.materialHeightContrast[i] & 0xFF) << 24);                    // bits 24-31: heightContrast
        buf.putInt(pp1);

        int pp2 = (Options.materialHeightRemapMin[i] & 0xFF)                             // bits 0-7:  heightRemapMin
                | ((Options.materialHeightRemapMax[i] & 0xFF) << 8)                      // bits 8-15: heightRemapMax
                | ((Options.materialHeightOffset[i] & 0xFF) << 16)                       // bits 16-23: heightOffset
                | ((Options.materialNormalDistanceFade[i] & 0xFF) << 24);                 // bits 24-31: normalDistanceFade
        buf.putInt(pp2);

        buf.putFloat(Options.materialPomDepth[i] / 100f);  // pomDepth: 0-200 → 0.00-2.00 blocks

        // Pack 5: gamutBoost, noiseMaskThreshold, noiseMaskPacked, normalStrength
        buf.putFloat(Options.materialGamutBoost[i] / 100f);
        buf.putFloat(Options.materialNoiseMaskThreshold[i] / 1000f);
        int noiseMaskPacked = Options.materialNoiseMaskMode[i]
                | ((Options.materialNoiseMaskInvert[i] ? 1 : 0) << 4);
        buf.putInt(noiseMaskPacked);
        buf.putFloat(Options.materialNormalStrength[i] / 100f);

        // Pack 6: noiseRotation, noiseAspect, noiseLacunarity, noiseContrast
        buf.putFloat(Options.materialNoiseRotation[i] * (float) (Math.PI / 1800.0));
        buf.putFloat(Options.materialNoiseAspect[i] / 100f);
        buf.putFloat(Options.materialNoiseLacunarity[i] / 10f);
        buf.putFloat(Options.materialNoiseContrast[i] / 100f);

        // Pack 7: emissionNits, emissionType, classId, flags
        buf.putFloat(0f);
        buf.putInt(255);
        buf.putInt(MaterialBlock.getMaterialClassForOrdinal(i).ordinal());
        int flags = 0;
        boolean hasLumData = blockLumMax[i] > blockLumMin[i];
        if (hasLumData && (Options.autoPBREnabled || Options.materialAutoPBR[i])) flags |= 0x8; // bit 3: AutoPBR
        int apbFlags = Options.materialAutoPBRFlags[i]; // bit 0=invertR, bit 1=invertN, bit 2=invertH
        flags |= (apbFlags & 0x7) << 4; // bits 4-6
        buf.putInt(flags);

        // Pack 8: AutoPBR shader-side params
        buf.putFloat(blockLumMin[i]);
        buf.putFloat(blockLumMax[i]);
        int rMin = Options.materialAutoPBRRoughnessMin[i] & 0xFF;
        int rMax = Options.materialAutoPBRRoughnessMax[i] & 0xFF;
        int center = Options.materialPercentileCenter[i] & 0xFF;
        int spread = Options.materialPercentileSpread[i] & 0xFF;
        buf.putInt(rMin | (rMax << 8) | (center << 16) | (spread << 24));
        int normStr = 25 & 0xFFFF; // AutoPBR normal strength (per-block field removed, use default)
        int htGamma = Options.materialAutoPBRHeightGamma[i] & 0xFFFF;
        buf.putInt(normStr | (htGamma << 16));
    }

    private static void packDefault(ByteBuffer buf) {
        // Pack 0
        buf.putFloat(0.04f);  // f0 (generic dielectric)
        buf.putFloat(0.04f);
        buf.putFloat(0.04f);
        buf.putFloat(0.5f);   // roughness

        // Pack 1
        buf.putFloat(0f);     // metallic
        buf.putFloat(-1.0f);  // transmission (keep LabPBR)
        buf.putFloat(1.5f);   // ior
        buf.putFloat(0f);     // subsurface

        // Pack 2
        buf.putFloat(0f);     // anisotropic
        buf.putFloat(0f);     // sheenWeight
        buf.putFloat(0f);     // sheenTint
        buf.putFloat(0f);     // coatWeight

        // Pack 3
        buf.putFloat(0f);     // coatRoughness
        buf.putFloat(5.0f);   // noiseScale
        buf.putFloat(0f);     // noiseStrength
        buf.putInt(0);        // noisePacked

        // Pack 4: pomPacked0, pomPacked1, pomPacked2, pomDepth
        buf.putInt(0);        // pomPacked0 (neutral defaults)
        buf.putInt(0);        // pomPacked1
        buf.putInt(0);        // pomPacked2
        buf.putFloat(0f);     // pomDepth (0 = disabled)

        // Pack 5
        buf.putFloat(1.0f);   // gamutBoost (neutral)
        buf.putFloat(0.5f);   // noiseMaskThreshold
        buf.putInt(0);        // noiseMaskPacked
        buf.putFloat(1.0f);   // normalStrength (neutral)

        // Pack 6
        buf.putFloat(0f);     // noiseRotation
        buf.putFloat(1.0f);   // noiseAspect (uniform)
        buf.putFloat(2.0f);   // noiseLacunarity
        buf.putFloat(1.0f);   // noiseContrast

        // Pack 7
        buf.putFloat(0f);     // emissionNits
        buf.putInt(255);      // emissionType (none)
        buf.putInt(0);        // classId
        buf.putInt(0);        // flags

        // Pack 8
        buf.putFloat(0f);     // lumMin
        buf.putFloat(1.0f);   // lumMax
        buf.putInt(0);        // autoPBRPacked0
        buf.putInt(0);        // autoPBRPacked1
    }

    public static void markDirty() {
        dirtyFrames = 3;
    }
}
