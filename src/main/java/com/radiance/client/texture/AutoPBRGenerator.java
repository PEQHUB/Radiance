package com.radiance.client.texture;

import com.radiance.client.option.Options;
import net.minecraft.client.texture.NativeImage;

/**
 * Auto-generates LabPBR specular and normal maps from vanilla albedo textures.
 * CPU-side processing at texture upload time — sub-millisecond for 16x16 textures.
 *
 * Roughness: darker pixels are rougher, gamma-curved with variance and edge boost,
 *            then compressed into [roughnessMin, roughnessMax] output range.
 * Normal: Sobel-based height-to-normal from luminance, LabPBR DirectX convention.
 * Height: Luminance with adjustable contrast, stored in normal alpha for POM.
 *
 * Output is encoded in LabPBR format for direct consumption by convertLabPBRMaterial().
 */
public final class AutoPBRGenerator {

    private AutoPBRGenerator() {}

    /**
     * Compute normalized roughness in [0,1] from albedo luminance.
     * Full-range computation: gamma curve + variance boost + edge boost, clamped to [0,1],
     * then remapped into [roughnessMin, roughnessMax] output range.
     */
    /** Per-texture luminance range for histogram normalization. */
    private static float[] computeLumRange(float[][] lum, NativeImage albedo, int w, int h) {
        float lumMin = 1.0f, lumMax = 0.0f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (albedo.getColorArgb(x, y) >> 24) & 0xFF;
                if (alpha == 0) continue;
                lumMin = Math.min(lumMin, lum[y][x]);
                lumMax = Math.max(lumMax, lum[y][x]);
            }
        }
        return new float[]{lumMin, lumMax};
    }

    /**
     * Compute normalized roughness in [0,1] from albedo luminance.
     * Luminance is histogram-normalized per-texture so even uniform textures
     * (gold, iron) span the full roughness range before compression.
     * Then remapped into [rMin, rMax] output range.
     */
    private static float computeRoughness(float[][] lum, int x, int y, int w, int h,
            float gamma, float rMin, float rMax, float varWeight, float edgeWt,
            float lumMin, float lumRange, boolean invertRoughness) {
        // Normalize luminance to [0,1] per-texture (histogram stretch)
        float normLum = (lumRange > 0.001f) ? (lum[y][x] - lumMin) / lumRange : 0.5f;

        // Base roughness from gamma curve (full 0-1 range, dark=rough, bright=smooth)
        float roughness = (float) Math.pow(1.0 - normLum, gamma);

        // Variance boost (3x3 neighborhood, uses raw luminance for local contrast)
        float variance = 0.0f;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int ny = (y + dy + h) % h;
                int nx = (x + dx + w) % w;
                float diff = lum[ny][nx] - lum[y][x];
                variance += diff * diff;
            }
        }
        variance /= 9.0f;
        float varBoost = smoothstep(0.0f, 0.05f, variance);
        roughness = lerp(roughness, 1.0f, varBoost * varWeight);

        // Sobel edge boost
        float gx = sobelX(lum, x, y, w, h);
        float gy = sobelY(lum, x, y, w, h);
        float edge = (float) Math.sqrt(gx * gx + gy * gy);
        roughness += edge * edgeWt;

        // Clamp to [0,1] before remapping
        roughness = clamp(roughness, 0.0f, 1.0f);

        // Remap into [rMin, rMax] output range (range compression)
        roughness = lerp(rMin, rMax, roughness);

        // Invert after all processing if requested
        if (invertRoughness) {
            roughness = rMin + rMax - roughness;
        }

        return roughness;
    }

    /**
     * Percentile-based roughness: maps histogram-normalized luminance through a center+spread window.
     * Center = what brightness maps to mid-roughness. Spread = width of the mapping window.
     * Bright pixels = smooth, dark pixels = rough (before inversion).
     */
    private static float computeRoughnessPercentile(float normLum,
            float centerPct, float spreadPct, float rMin, float rMax, boolean invertRoughness) {
        float windowStart = (centerPct - spreadPct * 0.5f) / 100.0f;
        float windowEnd = (centerPct + spreadPct * 0.5f) / 100.0f;
        float windowSize = Math.max(windowEnd - windowStart, 0.01f);

        // Map normalized luminance through the percentile window
        float t = clamp((normLum - windowStart) / windowSize, 0.0f, 1.0f);

        // t=1 → bright → smooth (rMin), t=0 → dark → rough (rMax)
        float roughness = lerp(rMax, rMin, t);

        if (invertRoughness) {
            roughness = rMin + rMax - roughness;
        }
        return roughness;
    }

    /**
     * Generate LabPBR specular texture from albedo.
     * R = smoothness (1-sqrt(roughness)), G = F0 (~0.04), B = 0, A = 255 (no emission).
     */
    public static NativeImage generateSpecular(NativeImage albedo) {
        return generateSpecular(albedo, Options.autoPBRRoughnessMin, Options.autoPBRRoughnessMax,
            Options.autoPBRRoughnessGamma, Options.autoPBRVarianceWeight, Options.autoPBREdgeWeight,
            false);
    }

    public static NativeImage generateSpecular(NativeImage albedo, int roughnessMinPct, int roughnessMaxPct,
            int gammaPct, int varWeightPct, int edgeWeightPct, boolean invertRoughness) {
        return generateSpecular(albedo, roughnessMinPct, roughnessMaxPct, gammaPct, varWeightPct, edgeWeightPct, invertRoughness, 213, 715, 72);
    }

    public static NativeImage generateSpecular(NativeImage albedo, int roughnessMinPct, int roughnessMaxPct,
            int gammaPct, int varWeightPct, int edgeWeightPct, boolean invertRoughness,
            int channelR, int channelG, int channelB) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, channelR / 1000.0f, channelG / 1000.0f, channelB / 1000.0f);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];

        float gamma = gammaPct / 100.0f;
        float rMin = roughnessMinPct / 100.0f;
        float rMax = roughnessMaxPct / 100.0f;
        float varWeight = varWeightPct / 100.0f;
        float edgeWt = edgeWeightPct / 100.0f;

        NativeImage specular = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    specular.setColorArgb(x, y, 0);
                    continue;
                }

                float roughness = computeRoughness(lum, x, y, w, h, gamma, rMin, rMax, varWeight, edgeWt, lumMin, lumSpan, invertRoughness);

                // Encode LabPBR specular (ARGB format)
                // R = smoothness: convertLabPBRMaterial does roughness = pow(1.0 - R, 2.0)
                // To get desired roughness R: smoothness = 1.0 - sqrt(roughness)
                int smoothness = (int) (clamp(1.0f - (float) Math.sqrt(roughness), 0, 1) * 255);
                int f0 = 10; // ~0.04 * 255, standard dielectric
                int porosity = 0;
                int emission = 255; // 255 means no emission in LabPBR

                // ARGB: A=emission, R=smoothness, G=f0, B=porosity
                int pixel = (emission << 24) | (smoothness << 16) | (f0 << 8) | porosity;
                specular.setColorArgb(x, y, pixel);
            }
        }
        return specular;
    }

    /**
     * Generate specular using percentile-based roughness (center + spread controls).
     */
    public static NativeImage generateSpecularPercentile(NativeImage albedo,
            int roughnessMinPct, int roughnessMaxPct,
            int centerPct, int spreadPct, boolean invertRoughness,
            int channelR, int channelG, int channelB) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, channelR / 1000.0f, channelG / 1000.0f, channelB / 1000.0f);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];

        float rMin = roughnessMinPct / 100.0f;
        float rMax = roughnessMaxPct / 100.0f;

        NativeImage specular = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    specular.setColorArgb(x, y, 0);
                    continue;
                }

                float normLum = (lumSpan > 0.001f) ? (lum[y][x] - lumMin) / lumSpan : 0.5f;
                float roughness = computeRoughnessPercentile(normLum, centerPct, spreadPct, rMin, rMax, invertRoughness);

                int smoothness = (int) (clamp(1.0f - (float) Math.sqrt(roughness), 0, 1) * 255);
                int f0 = 10;
                int porosity = 0;
                int emission = 255;

                int pixel = (emission << 24) | (smoothness << 16) | (f0 << 8) | porosity;
                specular.setColorArgb(x, y, pixel);
            }
        }
        return specular;
    }

    /**
     * Generate LabPBR normal map from albedo using Sobel filter.
     * RG = tangent-space XY normal, B = AO (1.0), A = height (contrast-adjusted luminance).
     */
    public static NativeImage generateNormal(NativeImage albedo) {
        return generateNormal(albedo, Options.autoPBRNormalStrength, false,
            Options.autoPBRHeightGamma, false);
    }

    public static NativeImage generateNormal(NativeImage albedo, int normalStrengthPct,
            boolean invertNormal, int heightGammaPct, boolean invertHeight) {
        return generateNormal(albedo, normalStrengthPct, invertNormal, heightGammaPct, invertHeight, 213, 715, 72);
    }

    public static NativeImage generateNormal(NativeImage albedo, int normalStrengthPct,
            boolean invertNormal, int heightGammaPct, boolean invertHeight,
            int channelR, int channelG, int channelB) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, channelR / 1000.0f, channelG / 1000.0f, channelB / 1000.0f);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];
        float strength = normalStrengthPct / 100.0f;
        float heightGamma = heightGammaPct / 100.0f;

        NativeImage normal = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    int pixel = (128 << 24) | (128 << 16) | (128 << 8) | 255;
                    normal.setColorArgb(x, y, pixel);
                    continue;
                }

                // Sobel filter for gradient
                float gx = sobelX(lum, x, y, w, h);
                float gy = sobelY(lum, x, y, w, h);

                float nx = -gx * strength;  // standard: normal opposes gradient direction
                float ny = gy * strength;   // LabPBR DirectX Y-down: -(-gy) = gy
                float nz = 1.0f;

                // Invert after filtering — flips bump direction (bumps become indents)
                if (invertNormal) {
                    nx = -nx;
                    ny = -ny;
                }

                // Normalize
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len;
                ny /= len;

                // Encode to [0, 255] LabPBR format
                int encodedX = (int) (clamp(nx * 0.5f + 0.5f, 0, 1) * 255);
                int encodedY = (int) (clamp(ny * 0.5f + 0.5f, 0, 1) * 255);
                int ao = 255; // no AO

                // Height: histogram-normalized luminance with gamma contrast for POM
                // Without normalization, uniform textures produce flat height maps
                // (e.g., iron block lum≈0.30 everywhere → POM sees 70% depth uniformly → pure sliding)
                float normH = (lumSpan > 0.001f) ? (lum[y][x] - lumMin) / lumSpan : 0.5f;
                float heightVal = (float) Math.pow(clamp(normH, 0, 1), heightGamma);
                if (invertHeight) {
                    heightVal = 1.0f - heightVal;
                }
                int height = (int) (heightVal * 255);

                // ARGB: A=height, R=encodedX, G=encodedY, B=ao
                int pixel = (height << 24) | (encodedX << 16) | (encodedY << 8) | ao;
                normal.setColorArgb(x, y, pixel);
            }
        }
        return normal;
    }

    /**
     * Generate a grayscale roughness preview image (for UI display only, not GPU upload).
     * White = rough (1.0), Black = smooth (0.0).
     */
    public static NativeImage generateRoughnessPreview(NativeImage albedo) {
        return generateRoughnessPreview(albedo, Options.autoPBRRoughnessMin, Options.autoPBRRoughnessMax,
            Options.autoPBRRoughnessGamma, Options.autoPBRVarianceWeight, Options.autoPBREdgeWeight,
            false);
    }

    public static NativeImage generateRoughnessPreview(NativeImage albedo, int roughnessMinPct, int roughnessMaxPct) {
        return generateRoughnessPreview(albedo, roughnessMinPct, roughnessMaxPct,
            Options.autoPBRRoughnessGamma, Options.autoPBRVarianceWeight, Options.autoPBREdgeWeight,
            false);
    }

    public static NativeImage generateRoughnessPreview(NativeImage albedo, int roughnessMinPct, int roughnessMaxPct,
            int gammaPct, int varWeightPct, int edgeWeightPct, boolean invertRoughness) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];

        float gamma = gammaPct / 100.0f;
        float rMin = roughnessMinPct / 100.0f;
        float rMax = roughnessMaxPct / 100.0f;
        float varWeight = varWeightPct / 100.0f;
        float edgeWt = edgeWeightPct / 100.0f;

        NativeImage preview = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    preview.setColorArgb(x, y, 0);
                    continue;
                }

                float roughness = computeRoughness(lum, x, y, w, h, gamma, rMin, rMax, varWeight, edgeWt, lumMin, lumSpan, invertRoughness);

                int gray = (int) (clamp(roughness, 0, 1) * 255);
                int pixel = (255 << 24) | (gray << 16) | (gray << 8) | gray;
                preview.setColorArgb(x, y, pixel);
            }
        }
        return preview;
    }

    /**
     * Generate a grayscale roughness preview using percentile-based method (center + spread).
     */
    public static NativeImage generateRoughnessPreviewPercentile(NativeImage albedo,
            int roughnessMinPct, int roughnessMaxPct,
            int centerPct, int spreadPct, boolean invertRoughness,
            int channelR, int channelG, int channelB) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, channelR / 1000.0f, channelG / 1000.0f, channelB / 1000.0f);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];

        float rMin = roughnessMinPct / 100.0f;
        float rMax = roughnessMaxPct / 100.0f;

        NativeImage preview = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    preview.setColorArgb(x, y, 0);
                    continue;
                }

                float normLum = (lumSpan > 0.001f) ? (lum[y][x] - lumMin) / lumSpan : 0.5f;
                float roughness = computeRoughnessPercentile(normLum, centerPct, spreadPct, rMin, rMax, invertRoughness);

                int gray = (int) (clamp(roughness, 0, 1) * 255);
                int pixel = (255 << 24) | (gray << 16) | (gray << 8) | gray;
                preview.setColorArgb(x, y, pixel);
            }
        }
        return preview;
    }

    /**
     * Generate a grayscale height preview (for UI display only).
     * White = high, Black = low.
     */
    public static NativeImage generateHeightPreview(NativeImage albedo) {
        return generateHeightPreview(albedo, Options.autoPBRHeightGamma, false);
    }

    public static NativeImage generateHeightPreview(NativeImage albedo, int heightGammaPct, boolean invertHeight) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];
        float heightGamma = heightGammaPct / 100.0f;

        NativeImage preview = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    preview.setColorArgb(x, y, 0);
                    continue;
                }

                float normH = (lumSpan > 0.001f) ? (lum[y][x] - lumMin) / lumSpan : 0.5f;
                float heightVal = (float) Math.pow(clamp(normH, 0, 1), heightGamma);
                if (invertHeight) {
                    heightVal = 1.0f - heightVal;
                }

                int gray = (int) (heightVal * 255);
                int pixel = (255 << 24) | (gray << 16) | (gray << 8) | gray;
                preview.setColorArgb(x, y, pixel);
            }
        }
        return preview;
    }

    private static float[][] computeLuminance(NativeImage img, int w, int h) {
        return computeLuminance(img, w, h, 0.2126f, 0.7152f, 0.0722f);
    }

    private static float[][] computeLuminance(NativeImage img, int w, int h,
            float wR, float wG, float wB) {
        float sum = wR + wG + wB;
        if (sum < 0.001f) { wR = 0.2126f; wG = 0.7152f; wB = 0.0722f; sum = 1.0f; }
        wR /= sum; wG /= sum; wB /= sum; // normalize weights
        float[][] lum = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getColorArgb(x, y);
                float r = srgbToLinear(((pixel >> 16) & 0xFF) / 255.0f);
                float g = srgbToLinear(((pixel >> 8) & 0xFF) / 255.0f);
                float b = srgbToLinear((pixel & 0xFF) / 255.0f);
                float l = wR * r + wG * g + wB * b;
                lum[y][x] = l;
            }
        }
        return lum;
    }

    // Sobel X kernel: [-1 0 1; -2 0 2; -1 0 1]
    private static float sobelX(float[][] lum, int x, int y, int w, int h) {
        return -lum[(y - 1 + h) % h][(x - 1 + w) % w] + lum[(y - 1 + h) % h][(x + 1) % w]
            - 2 * lum[y][(x - 1 + w) % w] + 2 * lum[y][(x + 1) % w]
            - lum[(y + 1) % h][(x - 1 + w) % w] + lum[(y + 1) % h][(x + 1) % w];
    }

    // Sobel Y kernel: [-1 -2 -1; 0 0 0; 1 2 1]
    private static float sobelY(float[][] lum, int x, int y, int w, int h) {
        return -lum[(y - 1 + h) % h][(x - 1 + w) % w] - 2 * lum[(y - 1 + h) % h][x] - lum[(y - 1 + h) % h][(x + 1) % w]
            + lum[(y + 1) % h][(x - 1 + w) % w] + 2 * lum[(y + 1) % h][x] + lum[(y + 1) % h][(x + 1) % w];
    }

    private static float srgbToLinear(float s) {
        return s <= 0.04045f ? s / 12.92f : (float) Math.pow((s + 0.055) / 1.055, 2.4);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
