package com.radiance.client.texture;


import com.radiance.client.option.Options;
import net.minecraft.client.texture.NativeImage;

/**
 * Auto-generates LabPBR specular and normal maps from vanilla albedo textures.
 * CPU-side processing at texture upload time — sub-millisecond for 16x16 textures.
 *
 * Roughness: darker pixels are rougher, gamma-curved with variance and edge boost,
 *            then compressed into [roughnessMin, roughnessMax] output range.
 * Normal: Height-to-normal via configurable filter (5 modes), LabPBR DirectX convention.
 * Height: Configurable source channel with remap, contrast, offset pipeline.
 * AO: Multi-scale Laplacian curvature from height field.
 *
 * Output is encoded in LabPBR format for direct consumption by convertLabPBRMaterial().
 */
public final class AutoPBRGenerator {

    private AutoPBRGenerator() {}

    // ── Height pipeline parameters (mirrors GPU pomPacked0/1/2 fields) ──────────

    /**
     * Encapsulates all height-to-normal pipeline parameters.
     * Maps 1:1 to existing Options arrays and GPU Pack 4 fields.
     */
    public record HeightParams(
        int heightSource,   // 0=Lum, 1=R, 2=G, 3=B, 4=A, 5=MaxRGB, 6=MinRGB
        int remapMin,       // 0-100 (%)
        int remapMax,       // 0-100 (%)
        int contrast,       // 0-30 (÷10 = 0.0-3.0)
        int offset,         // 0-200 (centered at 100)
        int filterMode,     // 0=Forward, 1=Central, 2=Sobel, 3=Bilinear, 4=Bicubic4th
        int normalClamp     // 0-100 (÷100 = 0.0-1.0, 100=disabled)
    ) {
        /** Default: luminance, full range, no contrast/offset, Sobel, no clamp */
        public static final HeightParams DEFAULT = new HeightParams(0, 0, 100, 10, 100, 2, 100);

        /** Construct from Options arrays for a given material ordinal. */
        public static HeightParams fromOptions(int ordinal) {
            return new HeightParams(
                Options.materialHeightSource[ordinal],
                Options.materialHeightRemapMin[ordinal],
                Options.materialHeightRemapMax[ordinal],
                Options.materialHeightContrast[ordinal],
                Options.materialHeightOffset[ordinal],
                Options.materialHeightFilter[ordinal],
                Options.materialNormalClamp[ordinal]
            );
        }
    }

    // ── BT.709 luminance weights (sRGB/Rec.709 primaries — matches source textures) ──

    private static final float BT709_R = 0.2126f;
    private static final float BT709_G = 0.7152f;
    private static final float BT709_B = 0.0722f;

    // ── Height field computation (7 source modes) ───────────────────────────────

    /**
     * Compute a height field from albedo using the specified source channel mode.
     * All channels are linearized from sRGB before extraction.
     */
    private static float[][] computeHeightField(NativeImage img, int w, int h, int heightSource) {
        float[][] field = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getColorArgb(x, y);
                float r = srgbToLinear(((pixel >> 16) & 0xFF) / 255.0f);
                float g = srgbToLinear(((pixel >> 8) & 0xFF) / 255.0f);
                float b = srgbToLinear((pixel & 0xFF) / 255.0f);
                float a = ((pixel >> 24) & 0xFF) / 255.0f;
                field[y][x] = switch (heightSource) {
                    case 1 -> r;
                    case 2 -> g;
                    case 3 -> b;
                    case 4 -> a;
                    case 5 -> Math.max(r, Math.max(g, b));
                    case 6 -> Math.min(r, Math.min(g, b));
                    default -> BT709_R * r + BT709_G * g + BT709_B * b; // 0 = luminance
                };
            }
        }
        return field;
    }

    /**
     * Bicubic (Catmull-Rom) upscale of height field with configurable source mode.
     */
    private static float[][] bicubicUpscaleHeightField(NativeImage albedo, int srcW, int srcH,
            int dstW, int dstH, int heightSource) {
        float[][] src = computeHeightField(albedo, srcW, srcH, heightSource);
        float[][] dst = new float[dstH][dstW];

        for (int dy = 0; dy < dstH; dy++) {
            float sy = (dy + 0.5f) * srcH / dstH - 0.5f;
            int iy = (int) Math.floor(sy);
            float fy = sy - iy;

            for (int dx = 0; dx < dstW; dx++) {
                float sx = (dx + 0.5f) * srcW / dstW - 0.5f;
                int ix = (int) Math.floor(sx);
                float fx = sx - ix;

                float value = 0;
                for (int j = -1; j <= 2; j++) {
                    float wy = catmullRom(fy - j);
                    for (int k = -1; k <= 2; k++) {
                        float wx = catmullRom(fx - k);
                        int px = ((ix + k) % srcW + srcW) % srcW;
                        int py = ((iy + j) % srcH + srcH) % srcH;
                        value += src[py][px] * wx * wy;
                    }
                }
                dst[dy][dx] = clamp(value, 0, 1);
            }
        }
        return dst;
    }

    /**
     * Process a raw height value through the full pipeline: remap → gamma → contrast → offset.
     * Matches GPU processHeight() in world_solid_transparent.rchit.
     */
    private static float processHeight(float raw, float lumMin, float lumSpan,
            boolean invertHeight, float heightGamma, HeightParams hp) {
        float h = (lumSpan > 0.001f) ? clamp((raw - lumMin) / lumSpan, 0, 1) : 0.5f;
        if (invertHeight) h = 1.0f - h;
        // Remap
        float rMin = hp.remapMin() / 100.0f;
        float rMax = hp.remapMax() / 100.0f;
        h = rMin + h * (rMax - rMin);
        // Gamma
        h = (float) Math.pow(clamp(h, 0.001f, 1), heightGamma);
        // Contrast (separate from gamma)
        float contrast = hp.contrast() / 10.0f;
        if (contrast != 1.0f) h = (float) Math.pow(clamp(h, 0.001f, 1), contrast);
        // Offset
        float offsetVal = (hp.offset() - 100) / 100.0f;
        return clamp(h + offsetVal, 0, 1);
    }

    // ── Roughness computation ───────────────────────────────────────────────────

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

    private static float computeRoughness(float[][] lum, int x, int y, int w, int h,
            float gamma, float rMin, float rMax, float varWeight, float edgeWt,
            float lumMin, float lumRange, boolean invertRoughness) {
        float normLum = (lumRange > 0.001f) ? (lum[y][x] - lumMin) / lumRange : 0.5f;
        float roughness = (float) Math.pow(1.0 - normLum, gamma);

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

        float gx = sobelX(lum, x, y, w, h);
        float gy = sobelY(lum, x, y, w, h);
        float edge = (float) Math.sqrt(gx * gx + gy * gy);
        roughness += edge * edgeWt;
        roughness = clamp(roughness, 0.0f, 1.0f);
        roughness = lerp(rMin, rMax, roughness);
        if (invertRoughness) roughness = rMin + rMax - roughness;
        return roughness;
    }

    private static float computeRoughnessPercentile(float normLum,
            float centerPct, float spreadPct, float rMin, float rMax, boolean invertRoughness) {
        float windowStart = (centerPct - spreadPct * 0.5f) / 100.0f;
        float windowEnd = (centerPct + spreadPct * 0.5f) / 100.0f;
        float windowSize = Math.max(windowEnd - windowStart, 0.01f);
        float t = clamp((normLum - windowStart) / windowSize, 0.0f, 1.0f);
        float roughness = lerp(rMax, rMin, t);
        if (invertRoughness) roughness = rMin + rMax - roughness;
        return roughness;
    }

    // ── Specular generation ─────────────────────────────────────────────────────

    public static NativeImage generateSpecular(NativeImage albedo) {
        return generateSpecular(albedo, 30, 95, 100, 0, 0, false);
    }

    public static NativeImage generateSpecular(NativeImage albedo, int roughnessMinPct, int roughnessMaxPct,
            int gammaPct, int varWeightPct, int edgeWeightPct, boolean invertRoughness) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, BT709_R, BT709_G, BT709_B);
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
                if (alpha == 0) { specular.setColorArgb(x, y, 0); continue; }

                float roughness = computeRoughness(lum, x, y, w, h, gamma, rMin, rMax, varWeight, edgeWt, lumMin, lumSpan, invertRoughness);
                int smoothness = (int) (clamp(1.0f - (float) Math.sqrt(roughness), 0, 1) * 255);
                int pixel = (255 << 24) | (smoothness << 16) | (10 << 8) | 0;
                specular.setColorArgb(x, y, pixel);
            }
        }
        return specular;
    }

    public static NativeImage generateSpecularPercentile(NativeImage albedo,
            int roughnessMinPct, int roughnessMaxPct,
            int centerPct, int spreadPct, boolean invertRoughness) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, BT709_R, BT709_G, BT709_B);
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
                if (alpha == 0) { specular.setColorArgb(x, y, 0); continue; }

                float normLum = (lumSpan > 0.001f) ? (lum[y][x] - lumMin) / lumSpan : 0.5f;
                float roughness = computeRoughnessPercentile(normLum, centerPct, spreadPct, rMin, rMax, invertRoughness);
                int smoothness = (int) (clamp(1.0f - (float) Math.sqrt(roughness), 0, 1) * 255);
                int pixel = (255 << 24) | (smoothness << 16) | (10 << 8) | 0;
                specular.setColorArgb(x, y, pixel);
            }
        }
        return specular;
    }

    // ── Normal + height + AO generation ─────────────────────────────────────────

    /** Backward-compatible overload (existing callers unchanged). */
    public static NativeImage generateNormal(NativeImage albedo) {
        return generateNormal(albedo, 25, false, 100, false, HeightParams.DEFAULT, 0);
    }

    /** Backward-compatible overload with basic params. */
    public static NativeImage generateNormal(NativeImage albedo, int normalStrengthPct,
            boolean invertNormal, int heightGammaPct, boolean invertHeight) {
        return generateNormal(albedo, normalStrengthPct, invertNormal, heightGammaPct,
                              invertHeight, HeightParams.DEFAULT, 0);
    }

    /**
     * Full-featured normal map generation with height pipeline parity to GPU applyAutoPBR().
     *
     * @param albedo           Source albedo texture
     * @param normalStrengthPct Normal strength 0-200 (÷100)
     * @param invertNormal     Flip normal XY
     * @param heightGammaPct   Height gamma 0-300 (÷100)
     * @param invertHeight     Invert height channel
     * @param hp               Height pipeline parameters (source, remap, contrast, offset, filter, clamp)
     * @param aoStrengthPct    AO from curvature 0-100 (0=disabled, writes flat 255)
     */
    public static NativeImage generateNormal(NativeImage albedo, int normalStrengthPct,
            boolean invertNormal, int heightGammaPct, boolean invertHeight,
            HeightParams hp, int aoStrengthPct) {
        int srcW = albedo.getWidth();
        int srcH = albedo.getHeight();

        // Bicubic upscale to at least 128px before differentiation.
        // 16px Minecraft textures produce staircase normals at native res because
        // the filter kernel spans too much of the image per sample.
        int minRes = 128;
        int scale = Math.max(1, minRes / Math.max(srcW, srcH));
        int w, h;
        float[][] lum;
        NativeImage alphaSource;
        if (scale > 1) {
            w = srcW * scale;
            h = srcH * scale;
            lum = bicubicUpscaleHeightField(albedo, srcW, srcH, w, h, hp.heightSource());
            alphaSource = null;
        } else {
            w = srcW;
            h = srcH;
            lum = computeHeightField(albedo, w, h, hp.heightSource());
            alphaSource = albedo;
        }

        // High-pass filter: extract local detail, reject global brightness
        lum = highPass(lum, w, h);

        float[] lumRange = computeLumRangeFromArray(lum, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];
        float strength = normalStrengthPct / 100.0f;
        float heightGamma = Math.max(heightGammaPct / 100.0f, 0.01f);
        float aoStrength = aoStrengthPct / 10.0f; // 0-100 → 0-10 multiplier for visible effect

        NativeImage normalHR = new NativeImage(w, h, false);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (alphaSource != null) {
                    int alpha = (alphaSource.getColorArgb(x, y) >> 24) & 0xFF;
                    if (alpha == 0) {
                        normalHR.setColorArgb(x, y, (128 << 24) | (128 << 16) | (128 << 8) | 255);
                        continue;
                    }
                }

                // Compute gradients using selected filter mode
                float gx, gy;
                int fm = hp.filterMode();
                if (fm == 0) {
                    // Forward differences (2 samples)
                    gx = lum[y][(x + 1) % w] - lum[y][x];
                    gy = lum[(y + 1) % h][x] - lum[y][x];
                } else if (fm == 1) {
                    // Central differences (4 samples, symmetric)
                    gx = (lum[y][(x + 1) % w] - lum[y][(x - 1 + w) % w]) * 0.5f;
                    gy = (lum[(y + 1) % h][x] - lum[(y - 1 + h) % h][x]) * 0.5f;
                } else if (fm == 3) {
                    // Bilinear: half-texel offset interpolation
                    float r0 = (lum[y][x] + lum[y][(x + 1) % w]) * 0.5f;
                    float l0 = (lum[y][x] + lum[y][(x - 1 + w) % w]) * 0.5f;
                    gx = r0 - l0;
                    float u0 = (lum[y][x] + lum[(y + 1) % h][x]) * 0.5f;
                    float d0 = (lum[y][x] + lum[(y - 1 + h) % h][x]) * 0.5f;
                    gy = u0 - d0;
                } else if (fm == 4) {
                    // Bicubic 4th-order finite difference (wider stencil, smoothest)
                    gx = (-lum[y][(x - 2 + w) % w] + 8 * lum[y][(x - 1 + w) % w]
                          - 8 * lum[y][(x + 1) % w] + lum[y][(x + 2) % w]) / 12.0f;
                    gy = (-lum[(y - 2 + h) % h][x] + 8 * lum[(y - 1 + h) % h][x]
                          - 8 * lum[(y + 1) % h][x] + lum[(y + 2) % h][x]) / 12.0f;
                } else {
                    // Default: Sobel 3x3 (8 samples, best edge detection)
                    gx = sobelX(lum, x, y, w, h);
                    gy = sobelY(lum, x, y, w, h);
                }

                // Normal clamp: limit gradient magnitude
                if (hp.normalClamp() < 100) {
                    float clampVal = hp.normalClamp() / 100.0f;
                    float gradMag = (float) Math.sqrt(gx * gx + gy * gy);
                    if (gradMag > clampVal) {
                        float s = clampVal / gradMag;
                        gx *= s;
                        gy *= s;
                    }
                }

                float nx = -gx * strength;
                float ny = gy * strength;
                float nz = 1.0f;
                if (invertNormal) { nx = -nx; ny = -ny; }

                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx /= len; ny /= len;

                int encodedX = (int) (clamp(nx * 0.5f + 0.5f, 0, 1) * 255);
                int encodedY = (int) (clamp(ny * 0.5f + 0.5f, 0, 1) * 255);

                // AO from multi-scale Laplacian curvature
                int ao = 255;
                if (aoStrengthPct > 0) {
                    float aoAccum = 0;
                    int[] radii = {1, 2, 4};
                    for (int r : radii) {
                        float lap = lum[(y - r + h) % h][x] + lum[(y + r) % h][x]
                                  + lum[y][(x - r + w) % w] + lum[y][(x + r) % w]
                                  - 4 * lum[y][x];
                        aoAccum += lap;
                    }
                    float aoVal = clamp(1.0f + (aoAccum / 3.0f) * aoStrength, 0, 1);
                    ao = (int) (aoVal * 255);
                }

                // Height through full pipeline (remap → gamma → contrast → offset)
                float heightVal = processHeight(lum[y][x], lumMin, lumSpan, invertHeight, heightGamma, hp);
                int height = (int) (heightVal * 255);

                normalHR.setColorArgb(x, y, (height << 24) | (encodedX << 16) | (encodedY << 8) | ao);
            }
        }

        // Downsample back to original resolution if upscaled
        if (scale > 1) {
            NativeImage normal = new NativeImage(srcW, srcH, false);
            for (int y = 0; y < srcH; y++) {
                for (int x = 0; x < srcW; x++) {
                    int rSum = 0, gSum = 0, bSum = 0, aSum = 0;
                    for (int dy = 0; dy < scale; dy++) {
                        for (int dx = 0; dx < scale; dx++) {
                            int p = normalHR.getColorArgb(x * scale + dx, y * scale + dy);
                            aSum += (p >> 24) & 0xFF;
                            rSum += (p >> 16) & 0xFF;
                            gSum += (p >> 8) & 0xFF;
                            bSum += p & 0xFF;
                        }
                    }
                    int n = scale * scale;
                    normal.setColorArgb(x, y, ((aSum / n) << 24) | ((rSum / n) << 16) | ((gSum / n) << 8) | (bSum / n));
                }
            }
            normalHR.close();
            return normal;
        }
        return normalHR;
    }

    // ── Preview generation (UI only, not GPU upload) ────────────────────────────

    public static NativeImage generateRoughnessPreview(NativeImage albedo) {
        return generateRoughnessPreview(albedo, 30, 95, 100, 0, 0, false);
    }

    public static NativeImage generateRoughnessPreview(NativeImage albedo, int roughnessMinPct, int roughnessMaxPct) {
        return generateRoughnessPreview(albedo, roughnessMinPct, roughnessMaxPct, 100, 0, 0, false);
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
                int alpha = (albedo.getColorArgb(x, y) >> 24) & 0xFF;
                if (alpha == 0) { preview.setColorArgb(x, y, 0); continue; }

                float roughness = computeRoughness(lum, x, y, w, h, gamma, rMin, rMax, varWeight, edgeWt, lumMin, lumSpan, invertRoughness);
                int gray = (int) (clamp(roughness, 0, 1) * 255);
                preview.setColorArgb(x, y, (255 << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return preview;
    }

    public static NativeImage generateRoughnessPreviewPercentile(NativeImage albedo,
            int roughnessMinPct, int roughnessMaxPct,
            int centerPct, int spreadPct, boolean invertRoughness) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] lum = computeLuminance(albedo, w, h, BT709_R, BT709_G, BT709_B);
        float[] lumRange = computeLumRange(lum, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];
        float rMin = roughnessMinPct / 100.0f;
        float rMax = roughnessMaxPct / 100.0f;

        NativeImage preview = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (albedo.getColorArgb(x, y) >> 24) & 0xFF;
                if (alpha == 0) { preview.setColorArgb(x, y, 0); continue; }

                float normLum = (lumSpan > 0.001f) ? (lum[y][x] - lumMin) / lumSpan : 0.5f;
                float roughness = computeRoughnessPercentile(normLum, centerPct, spreadPct, rMin, rMax, invertRoughness);
                int gray = (int) (clamp(roughness, 0, 1) * 255);
                preview.setColorArgb(x, y, (255 << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return preview;
    }

    /** Backward-compatible height preview. */
    public static NativeImage generateHeightPreview(NativeImage albedo) {
        return generateHeightPreview(albedo, 100, false, HeightParams.DEFAULT);
    }

    /** Backward-compatible height preview with gamma/invert. */
    public static NativeImage generateHeightPreview(NativeImage albedo, int heightGammaPct, boolean invertHeight) {
        return generateHeightPreview(albedo, heightGammaPct, invertHeight, HeightParams.DEFAULT);
    }

    /**
     * Height preview with full pipeline (source, remap, contrast, offset).
     */
    public static NativeImage generateHeightPreview(NativeImage albedo, int heightGammaPct,
            boolean invertHeight, HeightParams hp) {
        int w = albedo.getWidth();
        int h = albedo.getHeight();

        float[][] field = computeHeightField(albedo, w, h, hp.heightSource());
        float[] lumRange = computeLumRange(field, albedo, w, h);
        float lumMin = lumRange[0];
        float lumSpan = lumRange[1] - lumRange[0];
        float heightGamma = Math.max(heightGammaPct / 100.0f, 0.01f);

        NativeImage preview = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (albedo.getColorArgb(x, y) >> 24) & 0xFF;
                if (alpha == 0) { preview.setColorArgb(x, y, 0); continue; }

                float heightVal = processHeight(field[y][x], lumMin, lumSpan, invertHeight, heightGamma, hp);
                int gray = (int) (heightVal * 255);
                preview.setColorArgb(x, y, (255 << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return preview;
    }

    // ── Internal utilities ──────────────────────────────────────────────────────

    private static float[][] highPass(float[][] lum, int w, int h) {
        float[] kernel = {0.0625f, 0.25f, 0.375f, 0.25f, 0.0625f};
        int r = 2;

        float[][] blurH = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) {
                    int sx = ((x + k) % w + w) % w;
                    sum += lum[y][sx] * kernel[k + r];
                }
                blurH[y][x] = sum;
            }
        }

        float[][] blurred = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float sum = 0;
                for (int k = -r; k <= r; k++) {
                    int sy = ((y + k) % h + h) % h;
                    sum += blurH[sy][x] * kernel[k + r];
                }
                blurred[y][x] = sum;
            }
        }

        float[][] detail = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                detail[y][x] = 0.5f + (lum[y][x] - blurred[y][x]);
            }
        }
        return detail;
    }

    private static float[] computeLumRangeFromArray(float[][] lum, int w, int h) {
        float lumMin = 1.0f, lumMax = 0.0f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                lumMin = Math.min(lumMin, lum[y][x]);
                lumMax = Math.max(lumMax, lum[y][x]);
            }
        }
        return new float[]{lumMin, lumMax};
    }

    private static float catmullRom(float x) {
        float ax = Math.abs(x);
        if (ax <= 1.0f) return 1.5f * ax * ax * ax - 2.5f * ax * ax + 1.0f;
        if (ax <= 2.0f) return -0.5f * ax * ax * ax + 2.5f * ax * ax - 4.0f * ax + 2.0f;
        return 0;
    }

    private static float[][] computeLuminance(NativeImage img, int w, int h) {
        return computeLuminance(img, w, h, BT709_R, BT709_G, BT709_B);
    }

    private static float[][] computeLuminance(NativeImage img, int w, int h,
            float wR, float wG, float wB) {
        float sum = wR + wG + wB;
        if (sum < 0.001f) { wR = 0.2126f; wG = 0.7152f; wB = 0.0722f; sum = 1.0f; }
        wR /= sum; wG /= sum; wB /= sum;
        float[][] lum = new float[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pixel = img.getColorArgb(x, y);
                float r = srgbToLinear(((pixel >> 16) & 0xFF) / 255.0f);
                float g = srgbToLinear(((pixel >> 8) & 0xFF) / 255.0f);
                float b = srgbToLinear((pixel & 0xFF) / 255.0f);
                lum[y][x] = wR * r + wG * g + wB * b;
            }
        }
        return lum;
    }

    private static float sobelX(float[][] lum, int x, int y, int w, int h) {
        return -lum[(y - 1 + h) % h][(x - 1 + w) % w] + lum[(y - 1 + h) % h][(x + 1) % w]
            - 2 * lum[y][(x - 1 + w) % w] + 2 * lum[y][(x + 1) % w]
            - lum[(y + 1) % h][(x - 1 + w) % w] + lum[(y + 1) % h][(x + 1) % w];
    }

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
