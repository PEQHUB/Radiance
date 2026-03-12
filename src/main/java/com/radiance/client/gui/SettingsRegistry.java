package com.radiance.client.gui;

import com.radiance.client.option.Options;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

/**
 * Static registry of all Radiance settings for search overlay, recents, and profiles.
 * Call {@link #initialize()} once at startup before querying.
 */
public final class SettingsRegistry {

    private SettingsRegistry() {}

    public static final List<SettingEntry> ALL = new ArrayList<>();
    private static boolean initialized = false;

    public static record SettingEntry(
            String key,
            String displayName,
            String category,
            String subScreen,
            Class<? extends Screen> screenClass,
            int rangeMin,
            int rangeMax,
            IntSupplier currentValue,
            int defaultValue,
            String description,
            boolean gpuIntensive
    ) {}

    /**
     * Populates the registry with all known settings.
     * Safe to call multiple times; only the first call has effect.
     */
    public static void initialize() {
        if (initialized) return;
        initialized = true;

        // ── Main Screen: Lighting ──────────────────────────────────────────
        register("globalLightMode", "Global Light Mode", "Lighting", null, null,
                0, 2, () -> Options.globalLightMode, 0,
                null, false);

        register("areaLightsEnabled", "Area Lights", "Lighting", null, null,
                0, 1, () -> Options.areaLightsEnabled ? 1 : 0, 0,
                null, false);

        // ── Main Screen: Light & Color ─────────────────────────────────────
        register("saturationPercent", "Saturation", "Light & Color", null, null,
                100, 200, () -> Options.saturationPercent, 130,
                null, false);

        register("sdrTransferFunction", "SDR Transfer Function", "Light & Color", null, null,
                0, 1, () -> Options.sdrTransferFunction, 1,
                null, false);

        register("psychoEnabled", "PsychoV", "Light & Color", null, null,
                0, 1, () -> Options.psychoEnabled ? 1 : 0, 1,
                null, false);

        // ── Main Screen: Image Quality ─────────────────────────────────────
        register("dlssDEnabled", "DLSS-D", "Image Quality", null, null,
                0, 1, () -> Options.dlssDEnabled ? 1 : 0, 1,
                null, false);

        register("upscalerQuality", "Upscaler Quality", "Image Quality", null, null,
                0, 4, () -> Options.upscalerQuality, 2,
                null, true);

        register("outputScale2x", "Output Scale 2x", "Image Quality", null, null,
                0, 1, () -> Options.outputScale2x ? 1 : 0, 0,
                null, true);

        // ── Main Screen: Ray Tracing Quality ───────────────────────────────
        register("rayBounces", "Ray Bounces", "Ray Tracing Quality", null, null,
                0, 32, () -> Options.rayBounces, 16,
                "Light reflections per pixel. More = realistic, slower.", true);

        register("ommEnabled", "Opacity Micro Maps", "Ray Tracing Quality", null, null,
                0, 1, () -> Options.ommEnabled ? 1 : 0, 0,
                null, true);

        register("ommBakerLevel", "OMM Baker Level", "Ray Tracing Quality", null, null,
                1, 8, () -> Options.ommBakerLevel, 4,
                "Micro-triangle subdivision. Higher = more accurate alpha cutouts.", true);

        register("simplifiedIndirect", "Simplified Indirect", "Ray Tracing Quality", null, null,
                0, 1, () -> Options.simplifiedIndirect ? 1 : 0, 0,
                null, false);

        // ── Main Screen: Display ───────────────────────────────────────────
        register("maxFps", "Max FPS", "Display", null, null,
                10, 260, () -> Options.maxFps, 260,
                null, false);

        register("vsync", "VSync", "Display", null, null,
                0, 1, () -> Options.vsync ? 1 : 0, 1,
                null, false);

        // ── Main Screen: Radiance ──────────────────────────────────────────
        register("uiGlobalAlphaPercent", "Menu Transparency", "Radiance", null, null,
                0, 100, () -> Options.uiGlobalAlphaPercent, 55,
                null, false);

        // ── Exposure Sub-Screen ────────────────────────────────────────────
        register("manualExposureEnabled", "Manual Exposure", "Light & Color", "Exposure", null,
                0, 1, () -> Options.manualExposureEnabled ? 1 : 0, 0,
                null, false);

        register("manualExposureEV100Tenths", "Manual Exposure EV100", "Light & Color", "Exposure", null,
                -40, 200, () -> Options.manualExposureEV100Tenths, 150,
                "Scene brightness. -4=Starlight, 5=Indoor, 15=Sunny.", false);

        register("exposureUpSpeedTenths", "Exposure Up Speed", "Light & Color", "Exposure", null,
                1, 200, () -> Options.exposureUpSpeedTenths, 8,
                null, false);

        register("exposureDownSpeedTenths", "Exposure Down Speed", "Light & Color", "Exposure", null,
                1, 200, () -> Options.exposureDownSpeedTenths, 15,
                null, false);

        register("exposureHighlightProtectionPercent", "Highlight Protection", "Light & Color", "Exposure", null,
                0, 100, () -> Options.exposureHighlightProtectionPercent, 30,
                null, false);

        // ── Area Lights Sub-Screen ─────────────────────────────────────────
        register("areaLightIntensityPercent", "Area Light Intensity", "Lighting", "Area Lights", null,
                0, 500, () -> Options.areaLightIntensityPercent, 100,
                null, false);

        register("areaLightRange", "Area Light Range", "Lighting", "Area Lights", null,
                8, 512, () -> Options.areaLightRange, 128,
                "How far light reaches. Frostbite inverse-square falloff.", false);

        register("shadowSoftnessPercent", "Shadow Softness", "Lighting", "Area Lights", null,
                0, 200, () -> Options.shadowSoftnessPercent, 100,
                "Penumbra size. Higher = softer, diffused shadows.", false);

        register("restirCandidates", "ReSTIR Candidates", "Lighting", "Area Lights", null,
                8, 64, () -> Options.restirCandidates, 32,
                "Light samples per pixel. More = less noise, more cost.", true);

        register("restirTemporalMClamp", "ReSTIR M Clamp", "Lighting", "Area Lights", null,
                5, 50, () -> Options.restirTemporalMClamp, 20,
                null, false);

        register("restirWClamp", "ReSTIR W Clamp", "Lighting", "Area Lights", null,
                10, 200, () -> Options.restirWClamp, 30,
                null, false);

        // ── PsychoV Sub-Screen ─────────────────────────────────────────────
        register("psychoHighlightsPercent", "PsychoV Highlights", "Light & Color", "PsychoV", null,
                0, 300, () -> Options.psychoHighlightsPercent, 100,
                null, false);

        register("psychoShadowsPercent", "PsychoV Shadows", "Light & Color", "PsychoV", null,
                0, 300, () -> Options.psychoShadowsPercent, 100,
                null, false);

        register("psychoContrastPercent", "PsychoV Contrast", "Light & Color", "PsychoV", null,
                0, 300, () -> Options.psychoContrastPercent, 100,
                null, false);

        register("psychoPurityPercent", "PsychoV Purity", "Light & Color", "PsychoV", null,
                0, 300, () -> Options.psychoPurityPercent, 105,
                null, false);

        register("psychoBleachingPercent", "PsychoV Bleaching", "Light & Color", "PsychoV", null,
                0, 100, () -> Options.psychoBleachingPercent, 0,
                null, false);

        // ── Post Processing Sub-Screen ─────────────────────────────────────
        register("casSharpnessPercent", "CAS Sharpness", "Light & Color", "Post Processing", null,
                0, 100, () -> Options.casSharpnessPercent, 50,
                null, false);
    }

    /**
     * Search settings by matching query against display name, description, and category.
     * Case-insensitive substring match.
     *
     * @param query search string
     * @return matching entries (empty list if query is null/blank)
     */
    public static List<SettingEntry> search(String query) {
        if (query == null || query.isBlank()) return List.of();
        String lower = query.toLowerCase(Locale.ROOT);
        List<SettingEntry> results = new ArrayList<>();
        for (SettingEntry entry : ALL) {
            if (matches(entry, lower)) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * Find a single setting by its unique key.
     *
     * @param key the setting key (e.g. "rayBounces")
     * @return the entry, or null if not found
     */
    public static SettingEntry findByKey(String key) {
        for (SettingEntry entry : ALL) {
            if (entry.key().equals(key)) {
                return entry;
            }
        }
        return null;
    }

    // ── Internal helpers ───────────────────────────────────────────────────

    private static void register(String key, String displayName, String category,
                                 String subScreen, Class<? extends Screen> screenClass,
                                 int rangeMin, int rangeMax, IntSupplier currentValue,
                                 int defaultValue, String description, boolean gpuIntensive) {
        ALL.add(new SettingEntry(key, displayName, category, subScreen, screenClass,
                rangeMin, rangeMax, currentValue, defaultValue, description, gpuIntensive));
    }

    private static boolean matches(SettingEntry entry, String lowerQuery) {
        if (entry.displayName().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (entry.category().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (entry.key().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (entry.description() != null
                && entry.description().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        if (entry.subScreen() != null
                && entry.subScreen().toLowerCase(Locale.ROOT).contains(lowerQuery)) return true;
        return false;
    }
}
