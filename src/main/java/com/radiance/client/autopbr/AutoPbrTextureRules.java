package com.radiance.client.autopbr;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.materiallab.MaterialBakePlan;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.lwjgl.system.MemoryUtil.memAddress;

public final class AutoPbrTextureRules {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoPBR");
    private static final int MAX_SPRITES = 4096;
    private static final int ENTRY_SIZE = 192;
    private static final float DEFAULT_IOR = 1.45f;
    static final int FLAG_ENABLED = 1;
    static final int FLAG_TRANSMISSION = 1 << 1;
    static final int FLAG_IOR = 1 << 2;
    static final int FLAG_METALLIC = 1 << 3;
    static final int FLAG_F0 = 1 << 4;
    static final int FLAG_ROUGHNESS = 1 << 5;
    static final int FLAG_ANISOTROPIC = 1 << 6;
    static final int FLAG_SHEEN = 1 << 7;
    static final int FLAG_COAT = 1 << 8;
    static final int FLAG_EMISSION = 1 << 9;
    static final int FLAG_CONDUCTOR_F0_RGB = 1 << 10;
    static final int FLAG_ABSORPTION = 1 << 11;
    static final int FLAG_THICKNESS = 1 << 12;
    static final int FLAG_VOLUME_MODE = 1 << 13;
    static final int FLAG_REFRACTION_ROUGHNESS = 1 << 14;
    static final int FLAG_EMISSION_TINT_NITS = 1 << 15;
    static final int FLAG_ANISOTROPIC_ROTATION = 1 << 16;
    static final int FLAG_COAT_EXT = 1 << 17;
    static final int FLAG_SHEEN_ROUGHNESS = 1 << 18;
    static final int FLAG_UV_TRANSFORM = 1 << 19;
    static final int FLAG_FILTER_RADIUS = 1 << 20;
    static final int FLAG_MIP_BIAS = 1 << 21;
    static final int FLAG_DISPLACEMENT_SCALE = 1 << 22;
    static final int FLAG_SUBSURFACE_EXT = 1 << 23;
    static final int FLAG_DIFFUSE_MODEL = 1 << 24;
    private static final ByteBuffer RULES = ByteBuffer.allocateDirect(MAX_SPRITES * ENTRY_SIZE)
        .order(ByteOrder.nativeOrder());
    private static volatile int defaultSeedCount = 0;

    private AutoPbrTextureRules() {
    }

    public static boolean update(int spriteId, MaterialBakePlan plan) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES || plan == null || !plan.ok || plan.surface == null) return false;
        if (AutoPbrTextureCatalog.isPhysicalWaterSprite(spriteId)) return false;
        return updateSurface(spriteId, plan.surface, true);
    }

    private static boolean updateSurface(int spriteId, AutoPbrValue.Surface surface, boolean allowAdvancedRules) {
        int flags = 0;
        if (surface.transmissionOverride) flags |= FLAG_TRANSMISSION;
        if (shouldEmitIorRule(spriteId, surface)) flags |= FLAG_IOR;
        if (surface.metallicOverride) flags |= FLAG_METALLIC;
        if (surface.f0Override) flags |= FLAG_F0;
        if (surface.conductorF0RgbOverride) flags |= FLAG_CONDUCTOR_F0_RGB;
        if (surface.absorptionOverride) flags |= FLAG_ABSORPTION;
        if (surface.thicknessOverride) flags |= FLAG_THICKNESS;
        if (surface.volumeModeOverride) flags |= FLAG_VOLUME_MODE;
        if (surface.diffuseModelOverride) flags |= FLAG_DIFFUSE_MODEL;
        if (surface.refractionRoughnessOverride) flags |= FLAG_REFRACTION_ROUGHNESS;
        // Roughness can be per-pixel and is represented by the baked LabPBR specular layer.
        // A scalar texture rule would flatten that map, so no roughness rule is emitted here.
        if (surface.emissionOverride) flags |= FLAG_EMISSION;
        if (surface.emissionPhysicalOverride) flags |= FLAG_EMISSION_TINT_NITS;
        if (allowAdvancedRules) {
            if (surface.anisotropicOverride) flags |= FLAG_ANISOTROPIC;
            if (surface.anisotropicRotationOverride) flags |= FLAG_ANISOTROPIC_ROTATION;
            if (surface.sheenOverride) flags |= FLAG_SHEEN;
            if (surface.sheenRoughnessOverride) flags |= FLAG_SHEEN_ROUGHNESS;
            if (surface.coatOverride) flags |= FLAG_COAT;
            if (surface.coatExtOverride) flags |= FLAG_COAT_EXT;
            if (surface.uvTransformOverride) flags |= FLAG_UV_TRANSFORM;
            if (surface.filterRadiusOverride) flags |= FLAG_FILTER_RADIUS;
            if (surface.mipBiasOverride) flags |= FLAG_MIP_BIAS;
            if (surface.displacementOverride) flags |= FLAG_DISPLACEMENT_SCALE;
            if (surface.subSurfaceExtOverride) flags |= FLAG_SUBSURFACE_EXT;
        }
        writeMergedEntry(spriteId, flags, surface);
        return upload();
    }

    public static void clear() {
        clearEntries();
        defaultSeedCount = AutoPbrDefaultTextureRules.apply();
        if (defaultSeedCount > 0) {
            LOGGER.debug("[AutoPBR] Seeded {} default texture material rules", defaultSeedCount);
        }
        upload();
    }

    public static void disableAll() {
        clearEntries();
        defaultSeedCount = 0;
        upload();
    }

    private static void clearEntries() {
        RULES.clear();
        for (int i = 0; i < MAX_SPRITES * ENTRY_SIZE; i++) {
            RULES.put(i, (byte) 0);
        }
    }

    public static int defaultSeedCount() {
        return defaultSeedCount;
    }

    public static boolean upload() {
        try {
            return TextureArrayBridge.nativeReceiveTextureRules(memAddress(RULES), MAX_SPRITES,
                TextureArrayBridge.getActiveTextureGeneration());
        } catch (UnsatisfiedLinkError ignored) {
            // Native renderer not loaded yet.
            return false;
        }
    }

    private static float first(float[] values, float fallback) {
        if (values == null || values.length == 0) return fallback;
        return MathHelper.clamp(values[0], 0.0f, 1.0f);
    }

    static void writeDefaultRule(int spriteId,
        float transmission,
        float ior,
        float metallic,
        float f0,
        float emission) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES) return;
        int flags = defaultFlagsForSprite(spriteId, metallic, f0, emission);
        writeEntry(spriteId, flags, transmission, ior, metallic, f0, 0.45f, emission,
            0.0f, 0.0f, 0.0f, 0.0f, 0.03f,
            0.0f, 0.0f, 0.0f, 0,
            0.0f, 0.0f, 0.0f, 16.0f, 0.5f, 0.0f,
            1.0f, 0.75f, 0.45f, 0.0f,
            0.0f, 0.5f,
            1.5f, 1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f,
            0.0f, 0.5f, 1.0f, 0.75f, 0.55f);
    }

    private static void writeMergedEntry(int spriteId, int overrideFlags, AutoPbrValue.Surface surface) {
        AutoPbrDefaultTextureRules.DefaultRule seed = AutoPbrDefaultTextureRules.ruleForSprite(spriteId);
        int flags = seed == null ? 0 : defaultFlagsForSprite(spriteId, seed.metallic(), seed.f0(), seed.emission());
        float transmission = seed == null ? surface.transmission : seed.transmission();
        float ior = seed == null ? surface.ior : seed.ior();
        float metallic = seed == null ? surface.metallic : seed.metallic();
        float f0 = seed == null ? surface.f0 : seed.f0();
        float roughness = first(surface.roughness, 0.45f);
        float emission = seed == null ? surface.emission : seed.emission();
        float conductorF0R = surface.conductorF0R;
        float conductorF0G = surface.conductorF0G;
        float conductorF0B = surface.conductorF0B;
        int modeFlags = surface.textureRuleModeFlags;

        if (overrideFlags != 0) {
            flags |= FLAG_ENABLED | overrideFlags;
            if ((overrideFlags & FLAG_TRANSMISSION) != 0) transmission = surface.transmission;
            if ((overrideFlags & FLAG_IOR) != 0) ior = surface.ior;
            if ((overrideFlags & FLAG_METALLIC) != 0) metallic = surface.metallic;
            if ((overrideFlags & FLAG_F0) != 0) f0 = surface.f0;
            if ((overrideFlags & FLAG_EMISSION) != 0) emission = surface.emission;
        }
        writeEntry(spriteId, flags, transmission, ior, metallic, f0, roughness, emission,
            surface.anisotropic, surface.sheenWeight, surface.sheenTint,
            surface.coatWeight, surface.coatRoughness,
            conductorF0R, conductorF0G, conductorF0B, modeFlags,
            surface.absorptionR, surface.absorptionG, surface.absorptionB, surface.absorptionDistance,
            surface.thicknessAmount, surface.refractionRoughness,
            surface.emissionTintR, surface.emissionTintG, surface.emissionTintB, surface.emissionNits,
            surface.anisotropicRotation, surface.sheenRoughness,
            surface.coatIor, surface.coatTintR, surface.coatTintG, surface.coatTintB, surface.coatMask,
            surface.uvScaleU, surface.uvScaleV, surface.uvOffsetU, surface.uvOffsetV,
            surface.filterRadius, surface.mipBias, surface.displacementScale,
            surface.subSurfaceRadius, surface.subSurfaceThickness,
            surface.subSurfaceTintR, surface.subSurfaceTintG, surface.subSurfaceTintB);
    }

    private static int defaultFlagsForSprite(int spriteId, float metallic, float f0, float emission) {
        int flags = FLAG_ENABLED | FLAG_TRANSMISSION;
        if (!AutoPbrTextureCatalog.hasAuthoredSpecularMaterial(spriteId)) {
            flags |= FLAG_IOR;
            if (differs(metallic, 0.0f)) flags |= FLAG_METALLIC;
            if (differs(f0, 0.04f)) flags |= FLAG_F0;
            if (differs(emission, 0.0f)) flags |= FLAG_EMISSION;
        }
        return flags;
    }

    private static boolean shouldEmitIorRule(int spriteId, AutoPbrValue.Surface surface) {
        if (!surface.iorOverride) return false;
        if (AutoPbrTextureCatalog.hasAuthoredSpecularMaterial(spriteId)
            && !surface.f0Override
            && !differs(surface.ior, DEFAULT_IOR)) {
            return false;
        }
        return true;
    }

    private static boolean differs(float value, float baseline) {
        return Math.abs(value - baseline) > 0.0001f;
    }

    private static void writeEntry(int spriteId,
        int flags,
        float transmission,
        float ior,
        float metallic,
        float f0,
        float roughness,
        float emission,
        float anisotropic,
        float sheenWeight,
        float sheenTint,
        float coatWeight,
        float coatRoughness,
        float conductorF0R,
        float conductorF0G,
        float conductorF0B,
        int modeFlags,
        float absorptionR,
        float absorptionG,
        float absorptionB,
        float absorptionDistance,
        float thicknessAmount,
        float refractionRoughness,
        float emissionTintR,
        float emissionTintG,
        float emissionTintB,
        float emissionNits,
        float anisotropicRotation,
        float sheenRoughness,
        float coatIor,
        float coatTintR,
        float coatTintG,
        float coatTintB,
        float coatMask,
        float uvScaleU,
        float uvScaleV,
        float uvOffsetU,
        float uvOffsetV,
        float filterRadius,
        float mipBias,
        float displacementScale,
        float subSurfaceRadius,
        float subSurfaceThickness,
        float subSurfaceTintR,
        float subSurfaceTintG,
        float subSurfaceTintB) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES) return;
        int off = spriteId * ENTRY_SIZE;
        RULES.putInt(off, flags);
        RULES.putFloat(off + 4, MathHelper.clamp(transmission, 0.0f, 1.0f));
        RULES.putFloat(off + 8, MathHelper.clamp(ior, 1.0f, 3.0f));
        RULES.putFloat(off + 12, MathHelper.clamp(metallic, 0.0f, 1.0f));
        RULES.putFloat(off + 16, MathHelper.clamp(f0, 0.0f, 0.99f));
        RULES.putFloat(off + 20, MathHelper.clamp(roughness, 0.0f, 1.0f));
        RULES.putFloat(off + 24, MathHelper.clamp(anisotropic, 0.0f, 1.0f));
        RULES.putFloat(off + 28, MathHelper.clamp(sheenWeight, 0.0f, 1.0f));
        RULES.putFloat(off + 32, MathHelper.clamp(sheenTint, 0.0f, 1.0f));
        RULES.putFloat(off + 36, MathHelper.clamp(coatWeight, 0.0f, 1.0f));
        RULES.putFloat(off + 40, MathHelper.clamp(coatRoughness, 0.0f, 1.0f));
        RULES.putFloat(off + 44, MathHelper.clamp(emission, 0.0f, 1.0f));
        RULES.putFloat(off + 48, MathHelper.clamp(conductorF0R, 0.0f, 0.99f));
        RULES.putFloat(off + 52, MathHelper.clamp(conductorF0G, 0.0f, 0.99f));
        RULES.putFloat(off + 56, MathHelper.clamp(conductorF0B, 0.0f, 0.99f));
        RULES.putInt(off + 60, modeFlags);
        RULES.putFloat(off + 64, MathHelper.clamp(absorptionR, 0.0f, 1.0f));
        RULES.putFloat(off + 68, MathHelper.clamp(absorptionG, 0.0f, 1.0f));
        RULES.putFloat(off + 72, MathHelper.clamp(absorptionB, 0.0f, 1.0f));
        RULES.putFloat(off + 76, Math.max(0.01f, absorptionDistance));
        RULES.putFloat(off + 80, MathHelper.clamp(thicknessAmount, 0.0f, 1.0f));
        RULES.putFloat(off + 84, MathHelper.clamp(refractionRoughness, 0.0f, 1.0f));
        RULES.putFloat(off + 88, MathHelper.clamp(emissionTintR, 0.0f, 1.0f));
        RULES.putFloat(off + 92, MathHelper.clamp(emissionTintG, 0.0f, 1.0f));
        RULES.putFloat(off + 96, MathHelper.clamp(emissionTintB, 0.0f, 1.0f));
        RULES.putFloat(off + 100, Math.max(0.0f, emissionNits));
        RULES.putFloat(off + 104, MathHelper.clamp(anisotropicRotation, 0.0f, 1.0f));
        RULES.putFloat(off + 108, MathHelper.clamp(sheenRoughness, 0.0f, 1.0f));
        RULES.putFloat(off + 112, MathHelper.clamp(coatIor, 1.0f, 3.0f));
        RULES.putFloat(off + 116, MathHelper.clamp(coatTintR, 0.0f, 1.0f));
        RULES.putFloat(off + 120, MathHelper.clamp(coatTintG, 0.0f, 1.0f));
        RULES.putFloat(off + 124, MathHelper.clamp(coatTintB, 0.0f, 1.0f));
        RULES.putFloat(off + 128, MathHelper.clamp(coatMask, 0.0f, 1.0f));
        RULES.putFloat(off + 132, MathHelper.clamp(uvScaleU, 0.01f, 16.0f));
        RULES.putFloat(off + 136, MathHelper.clamp(uvScaleV, 0.01f, 16.0f));
        RULES.putFloat(off + 140, uvOffsetU);
        RULES.putFloat(off + 144, uvOffsetV);
        RULES.putFloat(off + 148, MathHelper.clamp(filterRadius, 0.0f, 16.0f));
        RULES.putFloat(off + 152, MathHelper.clamp(mipBias, -8.0f, 8.0f));
        RULES.putFloat(off + 156, MathHelper.clamp(displacementScale, 0.0f, 4.0f));
        RULES.putFloat(off + 160, MathHelper.clamp(subSurfaceRadius, 0.0f, 1.0f));
        RULES.putFloat(off + 164, MathHelper.clamp(subSurfaceThickness, 0.0f, 1.0f));
        RULES.putFloat(off + 168, MathHelper.clamp(subSurfaceTintR, 0.0f, 1.0f));
        RULES.putFloat(off + 172, MathHelper.clamp(subSurfaceTintG, 0.0f, 1.0f));
        RULES.putFloat(off + 176, MathHelper.clamp(subSurfaceTintB, 0.0f, 1.0f));
        RULES.putFloat(off + 180, 0.0f);
        RULES.putFloat(off + 184, 0.0f);
        RULES.putFloat(off + 188, 0.0f);
    }

    static int flagsForTest(int spriteId) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES) return 0;
        return RULES.getInt(spriteId * ENTRY_SIZE);
    }

    static int entrySizeForTest() {
        return ENTRY_SIZE;
    }

    static float conductorF0ForTest(int spriteId, int component) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES) return 0.0f;
        int c = MathHelper.clamp(component, 0, 2);
        return RULES.getFloat(spriteId * ENTRY_SIZE + 48 + c * 4);
    }

    static float floatForTest(int spriteId, int offset) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES || offset < 0 || offset + 4 > ENTRY_SIZE) return 0.0f;
        return RULES.getFloat(spriteId * ENTRY_SIZE + offset);
    }

    static int intForTest(int spriteId, int offset) {
        if (spriteId < 0 || spriteId >= MAX_SPRITES || offset < 0 || offset + 4 > ENTRY_SIZE) return 0;
        return RULES.getInt(spriteId * ENTRY_SIZE + offset);
    }
}
