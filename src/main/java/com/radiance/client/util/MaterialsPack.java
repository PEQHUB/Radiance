package com.radiance.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/**
 * A collection of {@link MaterialData} entries with pack-level metadata.
 * Serializable to JSON or ZIP (.radpack) format for sharing.
 */
public class MaterialsPack {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Pack metadata
    public String name = "";
    public String author = "";
    public String description = "";
    public int version = 1;
    public String createdDate = Instant.now().toString();

    // Material entries keyed by blockId (preserves insertion order)
    public LinkedHashMap<String, MaterialData> materials = new LinkedHashMap<>();

    public MaterialsPack() {}

    /** Snapshot all 35 blocks from current Options. */
    public static MaterialsPack fromCurrentOptions() {
        MaterialsPack pack = new MaterialsPack();
        pack.name = "All Materials";
        pack.createdDate = Instant.now().toString();
        MaterialBlock[] blocks = MaterialBlock.values();
        for (int i = 0; i < blocks.length; i++) {
            MaterialData d = MaterialData.fromOptions(i);
            if (d != null) pack.materials.put(d.blockId, d);
        }
        return pack;
    }

    /** Snapshot a subset of blocks by index. */
    public static MaterialsPack fromCurrentOptions(List<Integer> blockIndices) {
        MaterialsPack pack = new MaterialsPack();
        pack.createdDate = Instant.now().toString();
        for (int idx : blockIndices) {
            MaterialData d = MaterialData.fromOptions(idx);
            if (d != null) pack.materials.put(d.blockId, d);
        }
        return pack;
    }

    /** Apply all contained materials to Options arrays. Caller persists. */
    public void applyToOptions() {
        for (MaterialData data : materials.values()) {
            int idx = data.findBlockIndex();
            if (idx >= 0) data.applyToOptions(idx);
        }
    }

    public int size() { return materials.size(); }
    public boolean contains(String blockId) { return materials.containsKey(blockId); }
    public MaterialData get(String blockId) { return materials.get(blockId); }

    // ── JSON serialization ──

    public String toJson() { return GSON.toJson(this); }

    public static MaterialsPack fromJson(String json) {
        try {
            return GSON.fromJson(json, MaterialsPack.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ── ZIP (.radpack) serialization ──

    /** Save as a ZIP file containing manifest.json + individual .radmat files. */
    public void saveToZip(Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {
            // Write manifest
            zos.putNextEntry(new ZipEntry("manifest.json"));
            String manifest = GSON.toJson(new Manifest(this));
            zos.write(manifest.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // Write individual materials
            for (MaterialData data : materials.values()) {
                zos.putNextEntry(new ZipEntry(data.blockId + ".radmat"));
                zos.write(data.toJson().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
    }

    /** Load from a ZIP (.radpack) file. */
    public static MaterialsPack loadFromZip(Path zipPath) throws IOException {
        MaterialsPack pack = new MaterialsPack();
        try (ZipInputStream zis = new ZipInputStream(
                Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                if (entry.getName().equals("manifest.json")) {
                    Manifest m = GSON.fromJson(content, Manifest.class);
                    if (m != null) {
                        pack.name = m.name != null ? m.name : "";
                        pack.author = m.author != null ? m.author : "";
                        pack.description = m.description != null ? m.description : "";
                        pack.version = m.version;
                        pack.createdDate = m.createdDate != null ? m.createdDate : "";
                    }
                } else if (entry.getName().endsWith(".radmat")) {
                    MaterialData data = MaterialData.fromJson(content);
                    if (data != null) pack.materials.put(data.blockId, data);
                }
                zis.closeEntry();
            }
        }
        return pack;
    }

    /** Save as a folder with manifest.json + .radmat files. */
    public void saveToFolder(Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("manifest.json"),
            GSON.toJson(new Manifest(this)), StandardCharsets.UTF_8);
        for (MaterialData data : materials.values()) {
            Files.writeString(directory.resolve(data.blockId + ".radmat"),
                data.toJson(), StandardCharsets.UTF_8);
        }
    }

    /** Load from a folder containing manifest.json + .radmat files. */
    public static MaterialsPack loadFromFolder(Path directory) throws IOException {
        MaterialsPack pack = new MaterialsPack();
        Path manifestPath = directory.resolve("manifest.json");
        if (Files.exists(manifestPath)) {
            Manifest m = GSON.fromJson(Files.readString(manifestPath), Manifest.class);
            if (m != null) {
                pack.name = m.name != null ? m.name : "";
                pack.author = m.author != null ? m.author : "";
                pack.description = m.description != null ? m.description : "";
                pack.version = m.version;
                pack.createdDate = m.createdDate != null ? m.createdDate : "";
            }
        }
        try (var files = Files.list(directory)) {
            files.filter(p -> p.toString().endsWith(".radmat")).forEach(p -> {
                try {
                    MaterialData data = MaterialData.fromJson(Files.readString(p));
                    if (data != null) pack.materials.put(data.blockId, data);
                } catch (IOException ignored) {}
            });
        }
        return pack;
    }

    // ── Manifest (minimal metadata for the ZIP) ──

    private static class Manifest {
        String name, author, description, createdDate;
        int version;
        List<String> materials;

        Manifest() {}
        Manifest(MaterialsPack pack) {
            this.name = pack.name;
            this.author = pack.author;
            this.description = pack.description;
            this.version = pack.version;
            this.createdDate = pack.createdDate;
            this.materials = new ArrayList<>(pack.materials.keySet());
        }
    }
}
