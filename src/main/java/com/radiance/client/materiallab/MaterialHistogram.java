package com.radiance.client.materiallab;

import net.minecraft.util.math.MathHelper;

public final class MaterialHistogram {
    private static final int MIN_BINS = 256;
    private static final int MAX_BINS = 512;

    private MaterialHistogram() {
    }

    public static Stats build(float[] values, int plotWidth) {
        int binCount = MathHelper.clamp(Math.max(1, plotWidth), MIN_BINS, MAX_BINS);
        float[] bins = new float[binCount];
        float min = 1.0f;
        float max = 0.0f;
        float sum = 0.0f;
        int count = 0;
        if (values != null) {
            for (float raw : values) {
                if (!Float.isFinite(raw)) continue;
                float value = MathHelper.clamp(raw, 0.0f, 1.0f);
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
                count++;

                float pos = value * (binCount - 1);
                int lo = MathHelper.clamp((int) Math.floor(pos), 0, binCount - 1);
                int hi = Math.min(binCount - 1, lo + 1);
                float t = pos - lo;
                bins[lo] += 1.0f - t;
                bins[hi] += t;
            }
        }
        if (count == 0) {
            return new Stats(bins, 0.0f, 0.0f, 0.0f, 0, 0.0f);
        }

        float[] smooth = new float[binCount];
        float maxDensity = 0.0f;
        for (int i = 0; i < binCount; i++) {
            float total = bins[Math.max(0, i - 2)]
                + bins[Math.max(0, i - 1)] * 2.0f
                + bins[i] * 3.0f
                + bins[Math.min(binCount - 1, i + 1)] * 2.0f
                + bins[Math.min(binCount - 1, i + 2)];
            smooth[i] = total / 9.0f;
            maxDensity = Math.max(maxDensity, smooth[i]);
        }
        return new Stats(smooth, min, sum / count, max, count, maxDensity);
    }

    public static final class Stats {
        public final float[] density;
        public final float min;
        public final float avg;
        public final float max;
        public final int count;
        public final float maxDensity;

        private Stats(float[] density, float min, float avg, float max, int count, float maxDensity) {
            this.density = density;
            this.min = min;
            this.avg = avg;
            this.max = max;
            this.count = count;
            this.maxDensity = maxDensity;
        }

        public float sample(float u) {
            if (density.length == 0) return 0.0f;
            float pos = MathHelper.clamp(u, 0.0f, 1.0f) * (density.length - 1);
            int lo = MathHelper.clamp((int) Math.floor(pos), 0, density.length - 1);
            int hi = Math.min(density.length - 1, lo + 1);
            float t = pos - lo;
            return density[lo] * (1.0f - t) + density[hi] * t;
        }
    }
}
