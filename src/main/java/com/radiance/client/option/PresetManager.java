package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

/**
 * Quick-save/load preset system. 3 slots stored as properties files alongside options.properties.
 * Presets capture all render settings but exclude machine-specific keys (window geometry, debug flags).
 */
public class PresetManager {

    private static final int SLOT_COUNT = 3;

    /** Keys excluded from presets (machine-specific, not transferable). */
    private static final Set<String> EXCLUDED_KEYS = Set.of(
        "windowPosX", "windowPosY", "windowWidth", "windowHeight",
        "optionsVersion", "loggingEnabled", "gpuDiagnostics",
        "advancedMode", "uiGlobalAlphaPercent"
    );

    /** Which slot (1-3) was last loaded, or 0 if none. */
    private static int activeSlot = 0;

    private static Path presetPath(int slot) {
        return RadianceClient.radianceDir.resolve("preset_" + slot + ".properties");
    }

    public static boolean isOccupied(int slot) {
        return Files.exists(presetPath(slot));
    }

    public static int getActiveSlot() {
        return activeSlot;
    }

    /**
     * Save current settings to a slot (1-3).
     * Reads the live options.properties and writes a filtered copy.
     */
    public static void save(int slot) {
        Path sourcePath = RadianceClient.radianceDir.resolve(Options.OPTION_PROPERTIES);
        Path targetPath = presetPath(slot);
        try {
            Properties source = new Properties();
            if (Files.exists(sourcePath)) {
                try (InputStream is = Files.newInputStream(sourcePath)) {
                    source.load(is);
                }
            }
            for (String key : EXCLUDED_KEYS) {
                source.remove(key);
            }
            try (OutputStream os = Files.newOutputStream(targetPath)) {
                source.store(os, "Radiance Preset " + slot);
            }
            activeSlot = slot;
        } catch (IOException e) {
            RadianceClient.LOGGER.error("[PresetManager] Failed to save preset {}", slot, e);
        }
    }

    /**
     * Load a preset from a slot. Merges preset values into current config, then re-reads.
     */
    public static void load(int slot) {
        Path path = presetPath(slot);
        if (!Files.exists(path)) return;
        try {
            Properties preset = new Properties();
            try (InputStream is = Files.newInputStream(path)) {
                preset.load(is);
            }
            // Merge into current options (preserve excluded keys from current config)
            Path optionsPath = RadianceClient.radianceDir.resolve(Options.OPTION_PROPERTIES);
            Properties current = new Properties();
            if (Files.exists(optionsPath)) {
                try (InputStream is = Files.newInputStream(optionsPath)) {
                    current.load(is);
                }
            }
            for (String key : preset.stringPropertyNames()) {
                if (!EXCLUDED_KEYS.contains(key)) {
                    current.setProperty(key, preset.getProperty(key));
                }
            }
            try (OutputStream os = Files.newOutputStream(optionsPath)) {
                current.store(os, "Radiance Options");
            }
            // Re-read from disk to apply all values + push to native renderer
            Options.readOptions();
            activeSlot = slot;
        } catch (IOException e) {
            RadianceClient.LOGGER.error("[PresetManager] Failed to load preset {}", slot, e);
        }
    }

    /**
     * Clear a preset slot (delete the file).
     */
    public static void clear(int slot) {
        try {
            Files.deleteIfExists(presetPath(slot));
            if (activeSlot == slot) activeSlot = 0;
        } catch (IOException e) {
            RadianceClient.LOGGER.error("[PresetManager] Failed to clear preset {}", slot, e);
        }
    }
}
