package com.radiance.client.texture;

import com.radiance.client.option.Options;
import net.minecraft.client.texture.NativeImage;

/**
 * Auto-generates LabPBR specular and normal maps from vanilla albedo textures.
 * CPU-side processing at texture upload time — sub-millisecond for 16x16 textures.
 *
 * Roughness: darker pixels are rougher, gamma-curved with variance and edge boost.
 * Normal: Sobel-based height-to-normal from luminance, LabPBR DirectX convention.
 *
 * Output is encoded in LabPBR format for direct consumption by convertLabPBRMaterial().
 */
public final class AutoPBRGenerator {

    private AutoPBRGenerator() {}

    /**
     * Generate LabPBR specular texture from albedo.
     * R = smoothness (1-sqrt(roughness)), G = F0 (~0.04), B = 0, A = 255 (no emission).
     */
    public static NativeImage generateSpecular(NativeImage albedo) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h);

        float gamma = Options.autoPBRRoughnessGamma / 100.0f;
        float rMin = Options.autoPBRRoughnessMin / 100.0f;
        float rMax = Options.autoPBRRoughnessMax / 100.0f;
        float varWeight = Options.autoPBRVarianceWeight / 100.0f;
        float edgeWt = Options.autoPBREdgeWeight / 100.0f;

        NativeImage specular = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Skip fully transparent pixels
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    specular.setColorArgb(x, y, 0);
                    continue;
                }

                // Base roughness from gamma curve
                float rawRough = (float) Math.pow(1.0 - lum[y][x], gamma);
                float roughness = lerp(rMin, rMax, rawRough);

                // Variance boost (3x3 neighborhood)
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
                roughness = lerp(roughness, rMax, varBoost * varWeight);

                // Sobel edge boost
                float gx = sobelX(lum, x, y, w, h);
                float gy = sobelY(lum, x, y, w, h);
                float edge = (float) Math.sqrt(gx * gx + gy * gy);
                roughness += edge * edgeWt;

                roughness = clamp(roughness, rMin, rMax);

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
     * Generate LabPBR normal map from albedo using Sobel filter.
     * RG = tangent-space XY normal, B = AO (1.0), A = height (luminance).
     */
    public static NativeImage generateNormal(NativeImage albedo) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h);
        float strength = Options.autoPBRNormalStrength / 100.0f;

        NativeImage normal = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                // Skip fully transparent pixels
                int srcPixel = albedo.getColorArgb(x, y);
                int alpha = (srcPixel >> 24) & 0xFF;
                if (alpha == 0) {
                    // Neutral normal for transparent pixels
                    int pixel = (128 << 24) | (128 << 16) | (128 << 8) | 255;
                    normal.setColorArgb(x, y, pixel);
                    continue;
                }

                // Sobel filter for gradient
                float gx = sobelX(lum, x, y, w, h);
                float gy = sobelY(lum, x, y, w, h);

                float nx = gx * strength;
                float ny = -gy * strength; // Y- for LabPBR DirectX convention
                float nz = 1.0f;

                // Normalize
                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len;
                ny /= len;

                // Encode to [0, 255] LabPBR format
                // convertLabPBRMaterial does: normal.xy = texNormal.xy * 2.0 - 1.0
                int encodedX = (int) (clamp(nx * 0.5f + 0.5f, 0, 1) * 255);
                int encodedY = (int) (clamp(ny * 0.5f + 0.5f, 0, 1) * 255);
                int ao = 255; // no AO
                int height = (int) (clamp(lum[y][x], 0, 1) * 255); // luminance as height

                // ARGB: A=height, R=encodedX, G=encodedY, B=ao
                int pixel = (height << 24) | (encodedX << 16) | (encodedY << 8) | ao;
                normal.setColorArgb(x, y, pixel);
            }
        }
        return normal;
    }

    private static float[][] computeLuminance(NativeImage img, int w, int h) {
        float[][] lum = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getColorArgb(x, y);
                float r = srgbToLinear(((pixel >> 16) & 0xFF) / 255.0f);
                float g = srgbToLinear(((pixel >> 8) & 0xFF) / 255.0f);
                float b = srgbToLinear((pixel & 0xFF) / 255.0f);
                lum[y][x] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
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
