package com.radiance.client.texture;

import com.radiance.client.option.Options;
import net.minecraft.client.texture.NativeImage;

/**
 * CPU-side noise preview generator for the material editor.
 * Approximates the GPU noise patterns for visual tuning feedback.
 * Not pixel-perfect with the shader — just representative.
 */
public class NoisePreviewGenerator {

    /**
     * Generate a grayscale noise preview for the given block material.
     * @param size output image size (square)
     * @param blockOrdinal material block index
     * @return grayscale NativeImage
     */
    public static NativeImage generate(int size, int blockOrdinal) {
        NativeImage img = new NativeImage(size, size, false);

        int noiseType = Options.materialNoiseType[blockOrdinal];
        float scale = Options.materialNoiseScale[blockOrdinal] / 10.0f;
        int octaves = Options.materialNoiseOctaves[blockOrdinal];
        int seed = Options.materialNoiseSeed[blockOrdinal];
        float strength = Options.materialNoiseStrength[blockOrdinal] / 1000.0f;
        float rotation = Options.materialNoiseRotation[blockOrdinal] / 10.0f; // degrees
        float contrast = Options.materialNoiseContrast[blockOrdinal] / 100.0f;
        float lacunarity = Options.materialNoiseLacunarity[blockOrdinal] / 10.0f;

        float cosR = (float) Math.cos(Math.toRadians(rotation));
        float sinR = (float) Math.sin(Math.toRadians(rotation));

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                // Normalize to [0,1] then apply scale
                float nx = (float) px / size * scale;
                float ny = (float) py / size * scale;

                // Apply rotation
                float rx = nx * cosR - ny * sinR;
                float ry = nx * sinR + ny * cosR;

                float value = sampleNoise(noiseType, rx, ry, seed, octaves, lacunarity);

                // Apply contrast around 0.5
                value = 0.5f + (value - 0.5f) * contrast;
                // Apply strength (blend with 0.5 gray)
                value = 0.5f + (value - 0.5f) * strength;

                int gray = clamp((int) (value * 255), 0, 255);
                img.setColorArgb(px, py, (255 << 24) | (gray << 16) | (gray << 8) | gray);
            }
        }
        return img;
    }

    private static float sampleNoise(int type, float x, float y, int seed, int octaves, float lacunarity) {
        return switch (type) {
            case 0, 20 -> fbm(x, y, seed, octaves, lacunarity); // Simplex, Value
            case 1 -> worleyF1(x, y, seed); // Worley F1
            case 2 -> worleyF2F1(x, y, seed); // Worley F2-F1
            case 3 -> voronoi(x, y, seed); // Voronoi
            case 4 -> Math.abs(fbm(x, y, seed, octaves, lacunarity) * 2.0f - 1.0f); // Ridged
            case 5 -> { // Turbulence
                float t = 0;
                float amp = 1.0f, freq = 1.0f, total = 0;
                for (int o = 0; o < octaves; o++) {
                    t += Math.abs(noise2D(x * freq, y * freq, seed + o) * 2.0f - 1.0f) * amp;
                    total += amp;
                    amp *= 0.5f; freq *= lacunarity;
                }
                yield 0.5f + 0.5f * t / total;
            }
            case 6 -> { // Marble
                float n = fbm(x, y, seed, octaves, lacunarity);
                yield 0.5f + 0.5f * (float) Math.sin(x * 6.28f + n * 5.0f);
            }
            case 7 -> { // Wood
                float dist = (float) Math.sqrt(x * x + y * y);
                float n = fbm(x, y, seed, octaves, lacunarity) * 0.5f;
                yield 0.5f + 0.5f * (float) Math.sin((dist + n) * 12.0f);
            }
            case 8 -> { // Checker
                int cx = (int) Math.floor(x) & 1;
                int cy = (int) Math.floor(y) & 1;
                yield (cx ^ cy) == 0 ? 0.0f : 1.0f;
            }
            case 9 -> { // Brick
                float by = y * 2.0f;
                int row = (int) Math.floor(by);
                float bx = x + (row % 2 == 0 ? 0 : 0.5f);
                float fx = bx - (float) Math.floor(bx);
                float fy = by - row;
                yield (fx < 0.05f || fx > 0.95f || fy < 0.1f) ? 0.3f : 0.8f;
            }
            case 10 -> { // Hex
                float n = fbm(x * 1.732f, y, seed, 1, 2.0f);
                yield n > 0.5f ? 0.9f : 0.2f;
            }
            case 11 -> { // Scratches: anisotropic stretched noise (brushed metal)
                float sx = x * 0.125f; // 8:1 stretch in X
                float raw = noise2D(sx, y, seed) * 2.0f - 1.0f; // remap to [-1,1]
                float absRaw = Math.abs(raw);
                yield clampF(absRaw * (3.0f - 2.0f * absRaw), 0, 1);
            }
            case 12 -> { // Dots
                float fx = x - (float) Math.floor(x);
                float fy = y - (float) Math.floor(y);
                float d = (fx - 0.5f) * (fx - 0.5f) + (fy - 0.5f) * (fy - 0.5f);
                yield d < 0.08f ? 1.0f : 0.0f;
            }
            case 13 -> 0.5f + 0.5f * (float) Math.sin(x * 6.28f); // Gradient
            case 14 -> { // Rings
                float d = (float) Math.sqrt(x * x + y * y);
                yield 0.5f + 0.5f * (float) Math.sin(d * 12.56f);
            }
            case 15 -> worleyF2F1(x * 1.5f, y * 1.5f, seed); // Crackle
            case 16 -> 0.5f + 0.5f * (float) Math.sin(y * 6.28f); // Waves
            case 17 -> worleyF1(x, y, seed); // Cellular
            case 18 -> { // Erosion: turbulent ridged (layered cracks)
                float n = Math.abs(noise2D(x, y, seed) * 2.0f - 1.0f);
                yield 1.0f - n * n; // squared abs, inverted = eroded surface
            }
            case 19 -> { // Fabric: woven cross-hatch pattern
                float warp = (float) Math.sin(x * 6.2831853f) * 0.5f + 0.5f;
                float weft = (float) Math.sin(y * 6.2831853f) * 0.5f + 0.5f;
                float sel = ((x + y) - (float) Math.floor(x + y)) >= 0.5f ? 1.0f : 0.0f;
                yield warp * (1.0f - sel) + weft * sel;
            }
            case 21 -> { // HashGrid: per-cell random value
                int ix = fastFloor(x);
                int iy = fastFloor(y);
                yield (hash(ix, iy, seed) & 0xFFFF) / 65535.0f;
            }
            case 22 -> { // Sine Lines
                float n = noise2D(x, y, seed);
                yield 0.5f + 0.5f * (float) Math.sin((x + y) * 6.28f + n * 3.0f);
            }
            case 23 -> { // Diamond
                float fx = Math.abs((x % 1.0f + 1.0f) % 1.0f - 0.5f);
                float fy = Math.abs((y % 1.0f + 1.0f) % 1.0f - 0.5f);
                yield 1.0f - 2.0f * (fx + fy);
            }
            default -> fbm(x, y, seed, octaves, lacunarity); // fallback
        };
    }

    // ── Noise primitives ──

    /** fBm (fractional Brownian motion) using hash-based gradient noise */
    private static float fbm(float x, float y, int seed, int octaves, float lacunarity) {
        float value = 0, amp = 1.0f, freq = 1.0f, total = 0;
        for (int o = 0; o < octaves; o++) {
            value += noise2D(x * freq, y * freq, seed + o * 31) * amp;
            total += amp;
            amp *= 0.5f;
            freq *= lacunarity;
        }
        return value / total;
    }

    /** Simple 2D gradient noise (0..1 range) */
    private static float noise2D(float x, float y, int seed) {
        int ix = fastFloor(x);
        int iy = fastFloor(y);
        float fx = x - ix;
        float fy = y - iy;
        float u = fade(fx);
        float v = fade(fy);

        float n00 = grad(hash(ix, iy, seed), fx, fy);
        float n10 = grad(hash(ix + 1, iy, seed), fx - 1, fy);
        float n01 = grad(hash(ix, iy + 1, seed), fx, fy - 1);
        float n11 = grad(hash(ix + 1, iy + 1, seed), fx - 1, fy - 1);

        float nx0 = lerp(n00, n10, u);
        float nx1 = lerp(n01, n11, u);
        return 0.5f + 0.5f * lerp(nx0, nx1, v); // remap -1..1 to 0..1
    }

    /** Worley F1 noise (0..1) */
    private static float worleyF1(float x, float y, int seed) {
        int ix = fastFloor(x), iy = fastFloor(y);
        float minDist = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int h = hash(ix + dx, iy + dy, seed);
                float px = ix + dx + (h & 0xFF) / 255.0f;
                float py = iy + dy + ((h >> 8) & 0xFF) / 255.0f;
                float d = (x - px) * (x - px) + (y - py) * (y - py);
                if (d < minDist) minDist = d;
            }
        }
        return Math.min((float) Math.sqrt(minDist) * 1.5f, 1.0f);
    }

    /** Worley F2-F1 noise (0..1) */
    private static float worleyF2F1(float x, float y, int seed) {
        int ix = fastFloor(x), iy = fastFloor(y);
        float d1 = Float.MAX_VALUE, d2 = Float.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int h = hash(ix + dx, iy + dy, seed);
                float px = ix + dx + (h & 0xFF) / 255.0f;
                float py = iy + dy + ((h >> 8) & 0xFF) / 255.0f;
                float d = (x - px) * (x - px) + (y - py) * (y - py);
                if (d < d1) { d2 = d1; d1 = d; }
                else if (d < d2) { d2 = d; }
            }
        }
        return Math.min(((float) Math.sqrt(d2) - (float) Math.sqrt(d1)) * 2.0f, 1.0f);
    }

    /** Voronoi (cell ID as color, 0..1) */
    private static float voronoi(float x, float y, int seed) {
        int ix = fastFloor(x), iy = fastFloor(y);
        float minDist = Float.MAX_VALUE;
        int cellHash = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int h = hash(ix + dx, iy + dy, seed);
                float px = ix + dx + (h & 0xFF) / 255.0f;
                float py = iy + dy + ((h >> 8) & 0xFF) / 255.0f;
                float d = (x - px) * (x - px) + (y - py) * (y - py);
                if (d < minDist) { minDist = d; cellHash = h; }
            }
        }
        return (cellHash & 0xFFFF) / 65535.0f;
    }

    // ── Math utilities ──

    private static int hash(int x, int y, int seed) {
        int h = seed + x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return h ^ (h >> 16);
    }

    private static float grad(int hash, float x, float y) {
        int h = hash & 3;
        return switch (h) {
            case 0 -> x + y;
            case 1 -> x - y;
            case 2 -> -x + y;
            default -> -x - y;
        };
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static float fade(float t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clampF(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
