package com.radiance.client.materiallab;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MaterialPreviewData {
    public final int size;
    private final Map<String, float[]> scalarLanes;
    private final Map<String, int[]> colorLanes;

    private MaterialPreviewData(int size, Map<String, float[]> scalarLanes, Map<String, int[]> colorLanes) {
        this.size = Math.max(1, size);
        this.scalarLanes = Map.copyOf(scalarLanes);
        this.colorLanes = Map.copyOf(colorLanes);
    }

    public float[] scalar(String key) {
        return scalarLanes.get(key);
    }

    public int[] color(String key) {
        return colorLanes.get(key);
    }

    public boolean hasScalar(String key) {
        return scalarLanes.containsKey(key);
    }

    public boolean hasColor(String key) {
        return colorLanes.containsKey(key);
    }

    public static Builder builder(int size) {
        return new Builder(size);
    }

    public static final class Builder {
        private final int size;
        private final Map<String, float[]> scalarLanes = new LinkedHashMap<>();
        private final Map<String, int[]> colorLanes = new LinkedHashMap<>();

        private Builder(int size) {
            this.size = Math.max(1, size);
        }

        public Builder scalar(String key, float[] values) {
            if (key != null && values != null) {
                scalarLanes.put(key, values);
            }
            return this;
        }

        public Builder color(String key, int[] values) {
            if (key != null && values != null) {
                colorLanes.put(key, values);
            }
            return this;
        }

        public MaterialPreviewData build() {
            return new MaterialPreviewData(size, scalarLanes, colorLanes);
        }
    }
}
