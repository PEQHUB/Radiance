package com.radiance.client.autopbr;

public final class AutoPbrValue {
    public enum Kind {
        EMPTY,
        SCALAR,
        COLOR,
        NORMAL,
        SURFACE
    }

    public final Kind kind;
    public final float[] scalar;
    public final int[] color;
    public final Surface surface;

    private AutoPbrValue(Kind kind, float[] scalar, int[] color, Surface surface) {
        this.kind = kind;
        this.scalar = scalar;
        this.color = color;
        this.surface = surface;
    }

    public static AutoPbrValue empty() {
        return new AutoPbrValue(Kind.EMPTY, null, null, null);
    }

    public static AutoPbrValue scalar(float[] scalar) {
        return new AutoPbrValue(Kind.SCALAR, scalar, null, null);
    }

    public static AutoPbrValue color(int[] color) {
        return new AutoPbrValue(Kind.COLOR, null, color, null);
    }

    public static AutoPbrValue normal(int[] normal) {
        return new AutoPbrValue(Kind.NORMAL, null, normal, null);
    }

    public static AutoPbrValue surface(Surface surface) {
        return new AutoPbrValue(Kind.SURFACE, null, null, surface);
    }

    public static final class Surface {
        public int[] albedo;
        public float[] roughness;
        public float[] alphaMap;
        public float[] metallicMap;
        public float[] f0Map;
        public float[] sssMap;
        public float[] emissionMap;
        public float[] aoMap;
        public float[] heightMap;
        public int[] normal;
        public int[] flags;
        public float metallic;
        public int labPbrMetalCode;
        public float f0;
        public float conductorF0R;
        public float conductorF0G;
        public float conductorF0B;
        public float ior;
        public float transmission;
        public float absorptionR;
        public float absorptionG;
        public float absorptionB;
        public float absorptionDistance;
        public float refractionRoughness;
        public float thicknessAmount;
        public int textureRuleModeFlags;
        public float emissionTintR;
        public float emissionTintG;
        public float emissionTintB;
        public float emissionNits;
        public float emission;
        public float ao;
        public float alpha;
        public float normalStrength;
        public float normalDetail;
        public float anisotropic;
        public float anisotropicRotation;
        public float sheenWeight;
        public float sheenTint;
        public float sheenRoughness;
        public float coatWeight;
        public float coatRoughness;
        public float coatIor;
        public float coatTintR;
        public float coatTintG;
        public float coatTintB;
        public float coatMask;
        public float uvScaleU;
        public float uvScaleV;
        public float uvOffsetU;
        public float uvOffsetV;
        public float filterRadius;
        public float mipBias;
        public float displacementScale;
        public boolean roughnessOverride;
        public boolean albedoOverride;
        public boolean normalOverride;
        public boolean heightOverride;
        public boolean flagsOverride;
        public boolean metallicOverride;
        public boolean f0Override;
        public boolean sssOverride;
        public boolean conductorF0RgbOverride;
        public boolean iorOverride;
        public boolean transmissionOverride;
        public boolean absorptionOverride;
        public boolean thicknessOverride;
        public boolean volumeModeOverride;
        public boolean refractionRoughnessOverride;
        public boolean emissionPhysicalOverride;
        public boolean emissionOverride;
        public boolean aoOverride;
        public boolean alphaOverride;
        public boolean anisotropicOverride;
        public boolean anisotropicRotationOverride;
        public boolean sheenOverride;
        public boolean sheenRoughnessOverride;
        public boolean coatOverride;
        public boolean coatExtOverride;
        public boolean uvTransformOverride;
        public boolean filterRadiusOverride;
        public boolean mipBiasOverride;
        public boolean displacementOverride;
    }
}
