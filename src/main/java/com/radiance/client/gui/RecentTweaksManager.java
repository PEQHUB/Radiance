package com.radiance.client.gui;

import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Tracks the 3 most recently tweaked settings and persists them to disk.
 * Entries are stored as "key|displayName" lines in radiance_recent_tweaks.txt.
 */
public final class RecentTweaksManager {

    private RecentTweaksManager() {}

    private static final String FILE_NAME = "radiance_recent_tweaks.txt";
    private static final int MAX_RECENT = 3;
    private static final LinkedList<RecentEntry> recents = new LinkedList<>();
    private static boolean loaded = false;

    public record RecentEntry(String settingKey, String displayName) {}

    /**
     * Record that a setting was just tweaked. Moves it to the front of the list
     * and persists to disk.
     */
    public static void recordTweak(String settingKey, String displayName) {
        ensureLoaded();
        if (settingKey == null || settingKey.isEmpty()) return;

        // Remove duplicate if already in list
        recents.removeIf(e -> e.settingKey().equals(settingKey));

        // Add to front
        recents.addFirst(new RecentEntry(settingKey, displayName));

        // Trim to max
        while (recents.size() > MAX_RECENT) {
            recents.removeLast();
        }

        save();
    }

    /**
     * Get the most recent tweaks (up to 3), most recent first.
     */
    public static List<RecentEntry> getRecent() {
        ensureLoaded();
        return new ArrayList<>(recents);
    }

    // ── File I/O ──

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        load();
    }

    private static File getFile() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.runDirectory == null) return new File(FILE_NAME);
        return new File(mc.runDirectory, FILE_NAME);
    }

    private static void load() {
        File file = getFile();
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int sep = line.indexOf('|');
                if (sep > 0 && sep < line.length() - 1) {
                    String key = line.substring(0, sep);
                    String name = line.substring(sep + 1);
                    recents.add(new RecentEntry(key, name));
                }
            }
        } catch (IOException ignored) {
            // Silent — file may not exist on first run
        }
    }

    private static void save() {
        File file = getFile();
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            for (RecentEntry entry : recents) {
                writer.write(entry.settingKey() + "|" + entry.displayName());
                writer.newLine();
            }
        } catch (IOException ignored) {
            // Silent — non-critical persistence
        }
    }
}
