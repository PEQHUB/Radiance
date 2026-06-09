package com.radiance.client.texture.v4;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;

/**
 * V4 texture manifest: per-sprite tier records, no fixed-layer semantics.
 *
 * Replaces VanillaTextureManifest's fixedLayerSize with per-tier buckets.
 * Each sprite is assigned to the smallest tier that fits its dimensions,
 * and receives a tierLocalLayer index within that tier's bucket.
 */
public record TextureManifestV4(
    long generation,
    Identifier atlasId,
    int atlasWidth,
    int atlasHeight,
    List<SpriteRecord> sprites,
    Map<TextureTier, TierBucket> buckets,
    List<String> warnings,
    List<String> errors
) {

    /** Per-sprite record with tier assignment and tier-local layer index. */
    public record SpriteRecord(
        int spriteId,
        Identifier id,
        int atlasX,
        int atlasY,
        int width,
        int height,
        int frameCount,
        TextureTier tier,
        int tierLocalLayer,
        boolean hasImage
    ) {}

    /** Per-tier bucket: how many layers in this tier's texture array. */
    public record TierBucket(
        TextureTier tier,
        int layerCount
    ) {}

    /** Total albedo bytes across all tiers (RGBA8). */
    public long totalAlbedoBytes() {
        long total = 0;
        for (TierBucket bucket : buckets.values()) {
            total += (long) bucket.layerCount() * bucket.tier().bytesPerLayerRgba8();
        }
        return total;
    }

    /** True if no errors were recorded. */
    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * Build a v4 manifest from a sorted list of block atlas sprites.
     * Each sprite is assigned to its natural tier — no fixed-layer expansion.
     */
    public static TextureManifestV4 fromBlockAtlas(
        long generation,
        Identifier atlasId,
        List<Map.Entry<Identifier, Sprite>> sortedSprites,
        int atlasWidth,
        int atlasHeight
    ) {
        java.util.ArrayList<SpriteRecord> entries = new java.util.ArrayList<>(sortedSprites.size());
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>();

        if (atlasWidth <= 0 || atlasHeight <= 0) {
            errors.add("atlas dimensions must be positive: " + atlasWidth + "x" + atlasHeight);
        }
        if (sortedSprites.isEmpty()) {
            errors.add("block atlas has no sprites");
        }

        // Count sprites per tier
        Map<TextureTier, Integer> tierCounts = new LinkedHashMap<>();
        for (TextureTier tier : TextureTier.values()) {
            tierCounts.put(tier, 0);
        }

        // First pass: assign tiers and count
        TextureTier[] spriteTiers = new TextureTier[sortedSprites.size()];
        for (int i = 0; i < sortedSprites.size(); i++) {
            SpriteContents contents = sortedSprites.get(i).getValue().getContents();
            TextureTier tier = TextureTier.forDimensions(contents.getWidth(), contents.getHeight());
            spriteTiers[i] = tier;
            tierCounts.put(tier, tierCounts.get(tier) + 1);
        }

        // Second pass: assign tier-local layer indices
        Map<TextureTier, Integer> tierNextLayer = new LinkedHashMap<>();
        for (TextureTier tier : TextureTier.values()) {
            tierNextLayer.put(tier, 0);
        }

        for (int i = 0; i < sortedSprites.size(); i++) {
            Map.Entry<Identifier, Sprite> mapEntry = sortedSprites.get(i);
            Sprite sprite = mapEntry.getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage image = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();

            int width = contents.getWidth();
            int height = contents.getHeight();
            int imageHeight = image != null ? image.getHeight() : height;
            int frameCount = height > 0 ? Math.max(1, imageHeight / height) : 1;
            int atlasX = Math.round(sprite.getMinU() * atlasWidth);
            int atlasY = Math.round(sprite.getMinV() * atlasHeight);

            TextureTier tier = spriteTiers[i];
            int tierLocalLayer = tierNextLayer.get(tier);
            tierNextLayer.put(tier, tierLocalLayer + 1);

            if (width != height) {
                warnings.add("sprite " + i + " is not square: " + mapEntry.getKey()
                    + " " + width + "x" + height + " -> tier " + tier);
            }

            entries.add(new SpriteRecord(
                i, mapEntry.getKey(), atlasX, atlasY, width, height,
                frameCount, tier, tierLocalLayer, image != null
            ));
        }

        // Build tier buckets
        Map<TextureTier, TierBucket> buckets = new LinkedHashMap<>();
        for (TextureTier tier : TextureTier.values()) {
            int count = tierNextLayer.get(tier);
            if (count > 0) {
                buckets.put(tier, new TierBucket(tier, count));
            }
        }

        return new TextureManifestV4(
            generation, atlasId, atlasWidth, atlasHeight,
            Collections.unmodifiableList(entries),
            Collections.unmodifiableMap(buckets),
            Collections.unmodifiableList(warnings),
            Collections.unmodifiableList(errors)
        );
    }

    /** Summary string for logging. */
    public String summary() {
        StringBuilder bucketStr = new StringBuilder();
        boolean first = true;
        for (Map.Entry<TextureTier, TierBucket> entry : buckets.entrySet()) {
            if (!first) bucketStr.append(", ");
            first = false;
            bucketStr.append(entry.getKey().size).append("x").append(entry.getKey().size)
                .append(":").append(entry.getValue().layerCount());
        }
        return "atlas=" + atlasId
            + " sprites=" + sprites.size()
            + " atlasSize=" + atlasWidth + "x" + atlasHeight
            + " tiers=[" + bucketStr + "]"
            + " totalAlbedoBytes=" + totalAlbedoBytes()
            + " warnings=" + warnings.size()
            + " errors=" + errors.size();
    }
}
