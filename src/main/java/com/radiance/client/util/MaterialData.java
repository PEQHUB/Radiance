package com.radiance.client.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.radiance.client.option.Options;
import net.minecraft.text.Text;

/**
 * Portable data class representing a single block's 13 Principled BSDF material
 * properties plus metadata. Serializable to JSON (.radmat) and human-readable text (.txt).
 */
public class MaterialData {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_COMPACT = new Gson();

    // Metadata
    public int version = 1;
    public String blockId = "";
    public String displayName = "";
    public String author = "";
    public String description = "";

    // 13 material properties (same units as Options arrays)
    public int f0R, f0G, f0B;          // 0-1000 permille
    public int roughness;              // 0-100 percent
    public int metallic;               // 0-1000 permille
    public int transmission;           // 0-1000 permille
    public int ior;                    // 0-3000 (×1000)
    public int subsurface;             // 0-1000 permille
    public int anisotropic;            // 0-1000 permille
    public int sheenWeight;            // 0-1000 permille
    public int sheenTint;              // 0-1000 permille
    public int coatWeight;             // 0-1000 permille
    public int coatRoughness;          // 0-100 percent

    public MaterialData() {}

    /** Read current values from Options arrays for the given block index. */
    public static MaterialData fromOptions(int blockIndex) {
        MaterialBlock[] blocks = MaterialBlock.values();
        if (blockIndex < 0 || blockIndex >= blocks.length) return null;
        MaterialBlock block = blocks[blockIndex];

        MaterialData d = new MaterialData();
        d.blockId = block.getId();
        d.displayName = Text.translatable("options.video.materials." + block.getId()).getString();
        d.f0R = Options.materialF0R[blockIndex];
        d.f0G = Options.materialF0G[blockIndex];
        d.f0B = Options.materialF0B[blockIndex];
        d.roughness = Options.materialRoughness[blockIndex];
        d.metallic = Options.materialMetallic[blockIndex];
        d.transmission = Options.materialTransmission[blockIndex];
        d.ior = Options.materialIOR[blockIndex];
        d.subsurface = Options.materialSubsurface[blockIndex];
        d.anisotropic = Options.materialAnisotropic[blockIndex];
        d.sheenWeight = Options.materialSheenWeight[blockIndex];
        d.sheenTint = Options.materialSheenTint[blockIndex];
        d.coatWeight = Options.materialCoatWeight[blockIndex];
        d.coatRoughness = Options.materialCoatRoughness[blockIndex];
        return d;
    }

    /** Create from a MaterialBlock's default values. */
    public static MaterialData fromBlock(MaterialBlock block) {
        MaterialData d = new MaterialData();
        d.blockId = block.getId();
        d.displayName = Text.translatable("options.video.materials." + block.getId()).getString();
        d.f0R = block.getDefaultF0R();
        d.f0G = block.getDefaultF0G();
        d.f0B = block.getDefaultF0B();
        d.roughness = block.getDefaultRoughness();
        d.metallic = block.getDefaultMetallic();
        d.transmission = block.getDefaultTransmission();
        d.ior = block.getDefaultIOR();
        d.subsurface = block.getDefaultSubsurface();
        d.anisotropic = block.getDefaultAnisotropic();
        d.sheenWeight = block.getDefaultSheenWeight();
        d.sheenTint = block.getDefaultSheenTint();
        d.coatWeight = block.getDefaultCoatWeight();
        d.coatRoughness = block.getDefaultCoatRoughness();
        return d;
    }

    /** Write all 13 properties into Options arrays. Caller is responsible for persisting. */
    public void applyToOptions(int blockIndex) {
        if (blockIndex < 0 || blockIndex >= MaterialBlock.values().length) return;
        Options.materialF0R[blockIndex] = f0R;
        Options.materialF0G[blockIndex] = f0G;
        Options.materialF0B[blockIndex] = f0B;
        Options.materialRoughness[blockIndex] = roughness;
        Options.materialMetallic[blockIndex] = metallic;
        Options.materialTransmission[blockIndex] = transmission;
        Options.materialIOR[blockIndex] = ior;
        Options.materialSubsurface[blockIndex] = subsurface;
        Options.materialAnisotropic[blockIndex] = anisotropic;
        Options.materialSheenWeight[blockIndex] = sheenWeight;
        Options.materialSheenTint[blockIndex] = sheenTint;
        Options.materialCoatWeight[blockIndex] = coatWeight;
        Options.materialCoatRoughness[blockIndex] = coatRoughness;
    }

    /** Find the MaterialBlock index for this data's blockId. Returns -1 if not found. */
    public int findBlockIndex() {
        MaterialBlock[] blocks = MaterialBlock.values();
        for (int i = 0; i < blocks.length; i++) {
            if (blocks[i].getId().equals(blockId)) return i;
        }
        return -1;
    }

    /** Whether all 13 properties match the MaterialBlock enum defaults. */
    public boolean isDefault(MaterialBlock block) {
        return f0R == block.getDefaultF0R()
            && f0G == block.getDefaultF0G()
            && f0B == block.getDefaultF0B()
            && roughness == block.getDefaultRoughness()
            && metallic == block.getDefaultMetallic()
            && transmission == block.getDefaultTransmission()
            && ior == block.getDefaultIOR()
            && subsurface == block.getDefaultSubsurface()
            && anisotropic == block.getDefaultAnisotropic()
            && sheenWeight == block.getDefaultSheenWeight()
            && sheenTint == block.getDefaultSheenTint()
            && coatWeight == block.getDefaultCoatWeight()
            && coatRoughness == block.getDefaultCoatRoughness();
    }

    // ── JSON serialization ──

    public String toJson() { return GSON.toJson(this); }
    public String toCompactJson() { return GSON_COMPACT.toJson(this); }

    public static MaterialData fromJson(String json) {
        try {
            return GSON.fromJson(json, MaterialData.class);
        } catch (Exception e) {
            return null;
        }
    }

    // ── Human-readable text serialization ──

    public String toReadableText() {
        StringBuilder sb = new StringBuilder();
        sb.append("Radiance Material: ").append(displayName).append('\n');
        sb.append("Block ID: ").append(blockId).append('\n');
        if (!author.isEmpty()) sb.append("Author: ").append(author).append('\n');
        if (!description.isEmpty()) sb.append("Description: ").append(description).append('\n');
        sb.append("---\n");
        sb.append(String.format("F0 Red: %.1f%%\n", f0R / 10.0));
        sb.append(String.format("F0 Green: %.1f%%\n", f0G / 10.0));
        sb.append(String.format("F0 Blue: %.1f%%\n", f0B / 10.0));
        sb.append(String.format("Roughness: %d%%\n", roughness));
        sb.append(String.format("Metallic: %.1f%%\n", metallic / 10.0));
        sb.append(String.format("Transmission: %.1f%%\n", transmission / 10.0));
        sb.append(String.format("IOR: %.3f\n", ior / 1000.0));
        sb.append(String.format("Subsurface: %.1f%%\n", subsurface / 10.0));
        sb.append(String.format("Anisotropic: %.1f%%\n", anisotropic / 10.0));
        sb.append(String.format("Sheen Weight: %.1f%%\n", sheenWeight / 10.0));
        sb.append(String.format("Sheen Tint: %.1f%%\n", sheenTint / 10.0));
        sb.append(String.format("Coat Weight: %.1f%%\n", coatWeight / 10.0));
        sb.append(String.format("Coat Roughness: %d%%\n", coatRoughness));
        return sb.toString();
    }

    public static MaterialData fromReadableText(String text) {
        try {
            MaterialData d = new MaterialData();
            String[] lines = text.split("\n");
            boolean pastSeparator = false;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("---")) { pastSeparator = true; continue; }
                if (!pastSeparator) {
                    if (line.startsWith("Radiance Material: ")) d.displayName = line.substring(19).trim();
                    else if (line.startsWith("Block ID: ")) d.blockId = line.substring(10).trim();
                    else if (line.startsWith("Author: ")) d.author = line.substring(8).trim();
                    else if (line.startsWith("Description: ")) d.description = line.substring(13).trim();
                } else {
                    // Parse "Key: value%" lines
                    int colon = line.indexOf(':');
                    if (colon < 0) continue;
                    String key = line.substring(0, colon).trim();
                    String val = line.substring(colon + 1).trim().replace("%", "");
                    switch (key) {
                        case "F0 Red"        -> d.f0R = Math.round(Float.parseFloat(val) * 10);
                        case "F0 Green"      -> d.f0G = Math.round(Float.parseFloat(val) * 10);
                        case "F0 Blue"       -> d.f0B = Math.round(Float.parseFloat(val) * 10);
                        case "Roughness"     -> d.roughness = Integer.parseInt(val);
                        case "Metallic"      -> d.metallic = Math.round(Float.parseFloat(val) * 10);
                        case "Transmission"  -> d.transmission = Math.round(Float.parseFloat(val) * 10);
                        case "IOR"           -> d.ior = Math.round(Float.parseFloat(val) * 1000);
                        case "Subsurface"    -> d.subsurface = Math.round(Float.parseFloat(val) * 10);
                        case "Anisotropic"   -> d.anisotropic = Math.round(Float.parseFloat(val) * 10);
                        case "Sheen Weight"  -> d.sheenWeight = Math.round(Float.parseFloat(val) * 10);
                        case "Sheen Tint"    -> d.sheenTint = Math.round(Float.parseFloat(val) * 10);
                        case "Coat Weight"   -> d.coatWeight = Math.round(Float.parseFloat(val) * 10);
                        case "Coat Roughness"-> d.coatRoughness = Integer.parseInt(val);
                        default -> {}
                    }
                }
            }
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    /** Compute a 64-bit hash from the 13 material properties (for sphere cache keys). */
    public long propertyHash() {
        long h = 17;
        h = h * 31 + f0R;   h = h * 31 + f0G;   h = h * 31 + f0B;
        h = h * 31 + roughness;  h = h * 31 + metallic;
        h = h * 31 + transmission;  h = h * 31 + ior;
        h = h * 31 + subsurface;  h = h * 31 + anisotropic;
        h = h * 31 + sheenWeight;  h = h * 31 + sheenTint;
        h = h * 31 + coatWeight;  h = h * 31 + coatRoughness;
        return h;
    }
}
