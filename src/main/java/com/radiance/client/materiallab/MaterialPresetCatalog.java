package com.radiance.client.materiallab;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MaterialPresetCatalog {
    private static final String RESOURCE = "/assets/radiance/material_lab/common_materials.json";
    private static final List<Preset> PRESETS = loadPresets();

    private MaterialPresetCatalog() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    public static List<Preset> metals() {
        List<Preset> result = new ArrayList<>();
        for (Preset preset : PRESETS) {
            if (preset.metal()) result.add(preset);
        }
        return result;
    }

    public static List<Preset> dielectrics() {
        List<Preset> result = new ArrayList<>();
        for (Preset preset : PRESETS) {
            if (!preset.metal()) result.add(preset);
        }
        return result;
    }

    public static Preset byId(String id) {
        if (id == null || id.isBlank()) return null;
        for (Preset preset : PRESETS) {
            if (preset.id().equals(id)) return preset;
        }
        return null;
    }

    public static boolean loadedFromResource() {
        return !PRESETS.isEmpty() && PRESETS.get(0).resourceBacked();
    }

    private static List<Preset> loadPresets() {
        try (InputStream stream = MaterialPresetCatalog.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) return fallback();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray materials = root.getAsJsonArray("materials");
            if (materials == null || materials.isEmpty()) return fallback();
            List<Preset> parsed = new ArrayList<>();
            for (JsonElement element : materials) {
                if (!element.isJsonObject()) continue;
                JsonObject json = element.getAsJsonObject();
                String id = string(json, "id", "");
                String label = string(json, "label", id);
                String group = string(json, "group", "Materials");
                boolean metal = bool(json, "metal", false);
                float f0 = number(json, "f0", metal ? 0.5f : 0.04f);
                float ior = number(json, "ior", 1.5f);
                float roughness = number(json, "roughness", 0.5f);
                float transmission = number(json, "transmission", 0.0f);
                int code = (int) number(json, "labPbrMetalCode", 0.0f);
                float r = f0;
                float g = f0;
                float b = f0;
                boolean hasRgb = false;
                JsonArray conductor = json.getAsJsonArray("conductorF0Rgb");
                if (conductor != null && conductor.size() >= 3) {
                    r = conductor.get(0).getAsFloat();
                    g = conductor.get(1).getAsFloat();
                    b = conductor.get(2).getAsFloat();
                    hasRgb = true;
                }
                String note = string(json, "note", "");
                if (!id.isBlank()) {
                    parsed.add(new Preset(id, label, group, metal, f0, ior, roughness, transmission,
                        code, r, g, b, hasRgb, note, true));
                }
            }
            parsed.sort(Comparator.comparing(Preset::group).thenComparing(Preset::label));
            return parsed.isEmpty() ? fallback() : List.copyOf(parsed);
        } catch (Exception ignored) {
            return fallback();
        }
    }

    private static List<Preset> fallback() {
        return List.of(
            new Preset("iron", "Iron", "Common Metals", true, 0.512f, 1.0f, 0.35f, 0.0f,
                230, 0.531f, 0.512f, 0.496f, true, "Fallback measured conductor", false),
            new Preset("gold", "Gold", "Precious Metals", true, 0.776f, 1.0f, 0.25f, 0.0f,
                231, 0.944f, 0.776f, 0.373f, true, "Fallback measured conductor", false),
            new Preset("copper", "Copper", "Common Metals", true, 0.721f, 1.0f, 0.32f, 0.0f,
                234, 0.926f, 0.721f, 0.504f, true, "Fallback measured conductor", false),
            new Preset("silver", "Silver", "Precious Metals", true, 0.950f, 1.0f, 0.18f, 0.0f,
                237, 0.969f, 0.950f, 0.920f, true, "Fallback measured conductor", false),
            new Preset("water", "Water", "Transparent Dielectrics", false, 0.020f, 1.333f, 0.02f, 1.0f,
                0, 0.020f, 0.020f, 0.020f, false, "Fallback dielectric", false),
            new Preset("glass", "Glass", "Transparent Dielectrics", false, 0.040f, 1.50f, 0.02f, 0.95f,
                0, 0.040f, 0.040f, 0.040f, false, "Fallback dielectric", false)
        );
    }

    private static String string(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static float number(JsonObject json, String key, float fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsFloat();
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
    }

    public record Preset(String id, String label, String group, boolean metal,
                         float f0, float ior, float roughness, float transmission,
                         int labPbrMetalCode,
                         float conductorF0R, float conductorF0G, float conductorF0B,
                         boolean hasConductorF0Rgb, String note, boolean resourceBacked) {
        public String display() {
            return group + " / " + label;
        }

        public String measuredSummary() {
            if (metal && hasConductorF0Rgb) {
                String code = labPbrMetalCode >= 230 ? " LabPBR " + labPbrMetalCode : "";
                return String.format("F0 RGB %.3f %.3f %.3f%s", conductorF0R, conductorF0G, conductorF0B, code);
            }
            return String.format("IOR %.3f  F0 %.3f", ior, f0);
        }
    }
}
