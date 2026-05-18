package com.radiance.client.util;

import com.radiance.client.RadianceClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * File I/O manager for saving/loading materials and packs.
 * All files are stored under {@code radiance/materials/} and {@code radiance/material-packs/}.
 */
public final class MaterialFileManager {

    private MaterialFileManager() {}

    public static Path getMaterialsDir() {
        return RadianceClient.radianceDir.resolve("materials");
    }

    public static Path getPacksDir() {
        return RadianceClient.radianceDir.resolve("material-packs");
    }

    /** Ensure storage directories exist. Call once during init. */
    public static void ensureDirectories() {
        try {
            Files.createDirectories(getMaterialsDir());
            Files.createDirectories(getPacksDir());
        } catch (IOException e) {
            System.err.println("[Radiance] Failed to create material directories: " + e.getMessage());
        }
    }

    // ── Single material ──

    /** Save a material as .radmat JSON. */
    public static Path saveMaterial(MaterialData data, String filename) {
        ensureDirectories();
        String safeName = sanitizeFilename(filename);
        Path path = getMaterialsDir().resolve(safeName + ".radmat");
        try {
            Files.writeString(path, data.toJson(), StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            System.err.println("[Radiance] Failed to save material: " + e.getMessage());
            return null;
        }
    }

    /** Export a material as human-readable .txt. */
    public static Path exportMaterialAsText(MaterialData data, String filename) {
        ensureDirectories();
        String safeName = sanitizeFilename(filename);
        Path path = getMaterialsDir().resolve(safeName + ".txt");
        try {
            Files.writeString(path, data.toReadableText(), StandardCharsets.UTF_8);
            return path;
        } catch (IOException e) {
            System.err.println("[Radiance] Failed to export material: " + e.getMessage());
            return null;
        }
    }

    /** Load a material from a .radmat or .txt file. */
    public static MaterialData loadMaterial(Path file) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (file.toString().endsWith(".txt")) {
                return MaterialData.fromReadableText(content);
            } else {
                return MaterialData.fromJson(content);
            }
        } catch (IOException e) {
            System.err.println("[Radiance] Failed to load material: " + e.getMessage());
            return null;
        }
    }

    /** List all saved materials (scans materials/ directory). */
    public static List<MaterialData> listSavedMaterials() {
        ensureDirectories();
        List<MaterialData> results = new ArrayList<>();
        try (var files = Files.list(getMaterialsDir())) {
            files.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".radmat") || name.endsWith(".txt");
            }).forEach(p -> {
                MaterialData d = loadMaterial(p);
                if (d != null) results.add(d);
            });
        } catch (IOException ignored) {}
        return results;
    }

    // ── Material packs ──

    /** Save a pack as .radpack ZIP. */
    public static Path savePack(MaterialsPack pack, String filename) {
        ensureDirectories();
        String safeName = sanitizeFilename(filename);
        Path path = getPacksDir().resolve(safeName + ".radpack");
        try {
            pack.saveToZip(path);
            return path;
        } catch (IOException e) {
            System.err.println("[Radiance] Failed to save pack: " + e.getMessage());
            return null;
        }
    }

    /** Load a pack from a .radpack ZIP or folder. */
    public static MaterialsPack loadPack(Path file) {
        try {
            if (Files.isDirectory(file)) {
                return MaterialsPack.loadFromFolder(file);
            } else {
                return MaterialsPack.loadFromZip(file);
            }
        } catch (IOException e) {
            System.err.println("[Radiance] Failed to load pack: " + e.getMessage());
            return null;
        }
    }

    /** List all saved packs (scans material-packs/ directory). */
    public static List<MaterialsPack> listSavedPacks() {
        ensureDirectories();
        List<MaterialsPack> results = new ArrayList<>();
        try (var entries = Files.list(getPacksDir())) {
            entries.filter(p -> {
                String name = p.getFileName().toString();
                return name.endsWith(".radpack") || Files.isDirectory(p);
            }).forEach(p -> {
                MaterialsPack pack = loadPack(p);
                if (pack != null) results.add(pack);
            });
        } catch (IOException ignored) {}
        return results;
    }

    /** Sanitize a filename — remove path separators and special chars. */
    private static String sanitizeFilename(String name) {
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}
