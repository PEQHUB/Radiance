package com.radiance.client.texture;

import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.util.Identifier;

/**
 * Immutable description of the vanilla block atlas at a resource reload boundary.
 *
 * This is the compatibility spine for the texture refactor: vanilla sprite identity,
 * dimensions, animation shape, and atlas bounds are recorded before the renderer-specific
 * texture-array path receives any bytes.
 */
public final class VanillaTextureManifest {
    private static final int MAX_U16 = 0xFFFF;
    private static final int MAX_DEFAULT_LAYER_SIZE = 256;

    private final Identifier atlasId;
    private final int atlasWidth;
    private final int atlasHeight;
    private final int fixedLayerSize;
    private final String sizeBucketsSummary;
    private final List<SpriteEntry> sprites;
    private final List<String> errors;
    private final List<String> warnings;

    private VanillaTextureManifest(Identifier atlasId, int atlasWidth, int atlasHeight,
                                   int fixedLayerSize, String sizeBucketsSummary, List<SpriteEntry> sprites,
                                   List<String> errors, List<String> warnings) {
        this.atlasId = atlasId;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        this.fixedLayerSize = fixedLayerSize;
        this.sizeBucketsSummary = sizeBucketsSummary;
        this.sprites = Collections.unmodifiableList(sprites);
        this.errors = Collections.unmodifiableList(errors);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public static VanillaTextureManifest fromBlockAtlas(Identifier atlasId,
                                                        List<Map.Entry<Identifier, Sprite>> sortedSprites,
                                                        int atlasWidth,
                                                        int atlasHeight) {
        ArrayList<SpriteEntry> entries = new ArrayList<>(sortedSprites.size());
        ArrayList<String> errors = new ArrayList<>();
        ArrayList<String> warnings = new ArrayList<>();

        if (atlasWidth <= 0 || atlasHeight <= 0) {
            errors.add("atlas dimensions must be positive: " + atlasWidth + "x" + atlasHeight);
        }
        if (atlasWidth > MAX_U16 || atlasHeight > MAX_U16) {
            errors.add("atlas dimensions exceed native u16 metadata: " + atlasWidth + "x" + atlasHeight);
        }
        if (sortedSprites.isEmpty()) {
            errors.add("block atlas has no sprites");
        }
        if (sortedSprites.size() > TextureTracker.MAX_SPRITES) {
            errors.add("sprite count " + sortedSprites.size()
                + " exceeds native capacity " + TextureTracker.MAX_SPRITES);
        }

        SizeChoice sizeChoice = chooseFixedLayerSize(sortedSprites);
        int layerSize = sizeChoice.layerSize;
        for (int i = 0; i < sortedSprites.size(); i++) {
            Map.Entry<Identifier, Sprite> mapEntry = sortedSprites.get(i);
            Sprite sprite = mapEntry.getValue();
            SpriteContents contents = sprite.getContents();
            NativeImage image = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();

            int width = contents.getWidth();
            int height = contents.getHeight();
            int imageHeight = image != null ? image.getHeight() : height;
            int atlasX = Math.round(sprite.getMinU() * atlasWidth);
            int atlasY = Math.round(sprite.getMinV() * atlasHeight);
            int tierSize = TextureTracker.tierSizeForPage(
                TextureTracker.tierPageForSpriteSize(width, height));

            if (width <= 0 || height <= 0) {
                errors.add("sprite " + i + " has invalid size: " + mapEntry.getKey()
                    + " " + width + "x" + height);
            }
            if (width != height) {
                warnings.add("sprite " + i + " is not square and will be resampled into "
                    + "texture-array tier: " + mapEntry.getKey() + " " + width + "x" + height
                    + " -> " + tierSize + "x" + tierSize);
            } else if (width != tierSize || height != tierSize) {
                warnings.add("sprite " + i + " will be resampled into texture-array tier: "
                    + mapEntry.getKey() + " " + width + "x" + height + " -> "
                    + tierSize + "x" + tierSize);
            }
            if (width > MAX_U16 || height > MAX_U16 || atlasX > MAX_U16 || atlasY > MAX_U16) {
                errors.add("sprite " + i + " exceeds native u16 metadata: " + mapEntry.getKey());
            }
            if (atlasX < 0 || atlasY < 0 || atlasX + width > atlasWidth || atlasY + height > atlasHeight) {
                errors.add("sprite " + i + " is outside atlas bounds: " + mapEntry.getKey()
                    + " at " + atlasX + "," + atlasY + " size " + width + "x" + height);
            }
            if (height > 0 && imageHeight % height != 0) {
                warnings.add("sprite " + i + " image height is not a multiple of frame height: "
                    + mapEntry.getKey() + " imageHeight=" + imageHeight + " frameHeight=" + height);
            }
            int frameCount = height > 0 ? Math.max(1, imageHeight / height) : 1;
            if (frameCount > MAX_U16) {
                errors.add("sprite " + i + " frame count exceeds native u16 metadata: "
                    + mapEntry.getKey() + " frames=" + frameCount);
            }

            entries.add(new SpriteEntry(i, mapEntry.getKey(), atlasX, atlasY,
                width, height, frameCount, image != null));
        }

        return new VanillaTextureManifest(atlasId, atlasWidth, atlasHeight, layerSize,
            sizeChoice.summary, entries, errors, warnings);
    }

    private static SizeChoice chooseFixedLayerSize(List<Map.Entry<Identifier, Sprite>> sortedSprites) {
        HashMap<Integer, SizeBucket> buckets = new HashMap<>();
        for (Map.Entry<Identifier, Sprite> mapEntry : sortedSprites) {
            SpriteContents contents = mapEntry.getValue().getContents();
            addSizeSample(buckets, contents.getWidth(), contents.getHeight(), 1);
        }
        return chooseFixedLayerSize(buckets);
    }

    public static int chooseFixedLayerSizeForTest(int... widthHeightPairs) {
        if (widthHeightPairs == null || widthHeightPairs.length % 2 != 0) {
            throw new IllegalArgumentException("widthHeightPairs must contain width/height pairs");
        }
        HashMap<Integer, SizeBucket> buckets = new HashMap<>();
        for (int i = 0; i < widthHeightPairs.length; i += 2) {
            int width = widthHeightPairs[i];
            int height = widthHeightPairs[i + 1];
            addSizeSample(buckets, width, height, 1);
        }
        return chooseFixedLayerSize(buckets).layerSize;
    }

    public static int chooseFixedLayerSizeFromCountsForTest(int... sizeCountPairs) {
        if (sizeCountPairs == null || sizeCountPairs.length % 2 != 0) {
            throw new IllegalArgumentException("sizeCountPairs must contain size/count pairs");
        }
        HashMap<Integer, SizeBucket> buckets = new HashMap<>();
        for (int i = 0; i < sizeCountPairs.length; i += 2) {
            int size = sizeCountPairs[i];
            int count = Math.max(0, sizeCountPairs[i + 1]);
            if (count > 0) {
                addSizeSample(buckets, size, size, count);
            }
        }
        return chooseFixedLayerSize(buckets).layerSize;
    }

    private static void addSizeSample(Map<Integer, SizeBucket> buckets, int width, int height, int count) {
        if (width <= 0 || height <= 0 || count <= 0) {
            return;
        }
        int candidate = qualityLayerSizeFor(width, height);
        if (candidate <= 0) {
            return;
        }
        SizeBucket bucket = buckets.computeIfAbsent(candidate, SizeBucket::new);
        bucket.count += count;
        bucket.score += (long) candidate * candidate * count;
    }

    private static SizeChoice chooseFixedLayerSize(Map<Integer, SizeBucket> buckets) {
        SizeBucket best = null;
        ArrayList<SizeBucket> sortedBuckets = new ArrayList<>(buckets.values());
        sortedBuckets.sort((a, b) -> Integer.compare(a.size, b.size));
        for (SizeBucket bucket : sortedBuckets) {
            if (best == null
                || bucket.score > best.score
                || (bucket.score == best.score && bucket.size > best.size)) {
                best = bucket;
            }
        }
        int layerSize = best != null ? best.size : 0;
        return new SizeChoice(layerSize, summarizeBuckets(sortedBuckets));
    }

    private static String summarizeBuckets(List<SizeBucket> buckets) {
        if (buckets.isEmpty()) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < buckets.size(); i++) {
            SizeBucket bucket = buckets.get(i);
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(bucket.size).append("x").append(bucket.size)
                .append(":").append(bucket.count);
        }
        return summary.toString();
    }

    private static int qualityLayerSizeFor(int width, int height) {
        int candidate = Math.max(width, height);
        if (candidate <= 0) {
            return 0;
        }
        return Math.min(candidate, MAX_DEFAULT_LAYER_SIZE);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> errors() {
        return errors;
    }

    public List<String> warnings() {
        return warnings;
    }

    public int spriteCount() {
        return sprites.size();
    }

    public int fixedLayerSize() {
        return fixedLayerSize;
    }

    public long baseLayerBytes() {
        return (long) sprites.size() * fixedLayerSize * fixedLayerSize * 4L;
    }

    public String summary() {
        return "atlas=" + atlasId
            + " sprites=" + sprites.size()
            + " atlasSize=" + atlasWidth + "x" + atlasHeight
            + " layerSize=" + fixedLayerSize
            + " sizeBuckets=[" + sizeBucketsSummary + "]"
            + " baseBytes=" + baseLayerBytes()
            + " warnings=" + warnings.size()
            + " errors=" + errors.size();
    }

    public void writeDebugDump(Path runDirectory) {
        Path output = runDirectory.resolve("radiance").resolve("logs").resolve("texture_manifest.json");
        try {
            Files.createDirectories(output.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(output)) {
                writer.write("{\n");
                writer.write("  \"atlas\": \"" + json(atlasId.toString()) + "\",\n");
                writer.write("  \"atlasWidth\": " + atlasWidth + ",\n");
                writer.write("  \"atlasHeight\": " + atlasHeight + ",\n");
                writer.write("  \"fixedLayerSize\": " + fixedLayerSize + ",\n");
                writer.write("  \"sizeBuckets\": \"" + json(sizeBucketsSummary) + "\",\n");
                writer.write("  \"spriteCount\": " + sprites.size() + ",\n");
                writer.write("  \"baseLayerBytes\": " + baseLayerBytes() + ",\n");
                writeStringArray(writer, "errors", errors, true);
                writeStringArray(writer, "warnings", warnings, true);
                writer.write("  \"sprites\": [\n");
                for (int i = 0; i < sprites.size(); i++) {
                    SpriteEntry sprite = sprites.get(i);
                    writer.write("    {\"id\": " + sprite.id
                        + ", \"name\": \"" + json(sprite.identifier.toString()) + "\""
                        + ", \"atlasX\": " + sprite.atlasX
                        + ", \"atlasY\": " + sprite.atlasY
                        + ", \"width\": " + sprite.width
                        + ", \"height\": " + sprite.height
                        + ", \"frames\": " + sprite.frameCount
                        + ", \"hasImage\": " + sprite.hasImage + "}");
                    writer.write(i + 1 == sprites.size() ? "\n" : ",\n");
                }
                writer.write("  ]\n");
                writer.write("}\n");
            }
        } catch (IOException ignored) {
            // Manifest dumps are diagnostics only; never make resource reload fail because logging failed.
        }
    }

    private static void writeStringArray(BufferedWriter writer, String name,
                                         List<String> values, boolean comma) throws IOException {
        writer.write("  \"" + name + "\": [");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(", ");
            writer.write("\"" + json(values.get(i)) + "\"");
        }
        writer.write(comma ? "],\n" : "]\n");
    }

    private static String json(String value) {
        StringBuilder out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private record SpriteEntry(int id, Identifier identifier, int atlasX, int atlasY,
                               int width, int height, int frameCount, boolean hasImage) {
    }

    private static final class SizeBucket {
        final int size;
        int count;
        long score;

        SizeBucket(int size) {
            this.size = size;
        }
    }

    private record SizeChoice(int layerSize, String summary) {
    }
}
