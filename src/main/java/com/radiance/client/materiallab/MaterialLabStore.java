package com.radiance.client.materiallab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MaterialLabStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaterialLab");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private MaterialLabStore() {
    }

    public static MaterialRecipe loadRecipe(MinecraftClient client, Identifier sprite) {
        MaterialLabProfile profile = loadProfile(client);
        return profile.recipe(key(sprite)).copy();
    }

    public static boolean saveRecipe(MinecraftClient client, Identifier sprite, MaterialRecipe recipe) {
        MaterialLabProfile profile = loadProfile(client);
        String key = key(sprite);
        if (recipe == null || recipe.isDefaultIntent()) {
            profile.recipes.remove(key);
        } else {
            recipe.version = MaterialRecipe.VERSION;
            profile.recipes.put(key, recipe.copy());
        }
        return saveProfile(client, profile);
    }

    public static List<Identifier> savedSprites(MinecraftClient client) {
        MaterialLabProfile profile = loadProfile(client);
        List<Identifier> sprites = new ArrayList<>();
        for (String key : profile.recipes.keySet()) {
            Identifier id = Identifier.tryParse(key);
            if (id != null) sprites.add(id);
        }
        return sprites;
    }

    public static Path profilePath(MinecraftClient client) {
        return profilesRoot(client).resolve(stackFingerprint(client) + ".json");
    }

    private static Path legacyProfilePath(MinecraftClient client) {
        return profilesRoot(client).resolve(legacyStackFingerprint(client) + ".json");
    }

    public static boolean currentProfileExists(MinecraftClient client) {
        return Files.exists(profilePath(client));
    }

    public static boolean hasAnyProfiles(MinecraftClient client) {
        Path root = profilesRoot(client);
        if (!Files.isDirectory(root)) return false;
        try (var stream = Files.newDirectoryStream(root, "*.json")) {
            return stream.iterator().hasNext();
        } catch (IOException e) {
            LOGGER.warn("[MaterialLab] Failed to scan profiles in {}", root, e);
            return false;
        }
    }

    public static Path profilesRoot(MinecraftClient client) {
        return runDirectory(client).resolve("radiance").resolve("material_lab").resolve("profiles");
    }

    public static MaterialLabProfile loadProfile(MinecraftClient client) {
        Path path = profilePath(client);
        if (Files.exists(path)) {
            MaterialLabProfile profile = readProfile(path, stackFingerprint(client));
            if (profile != null) return profile;
        }
        Path legacyPath = legacyProfilePath(client);
        if (!legacyPath.equals(path) && Files.exists(legacyPath)) {
            MaterialLabProfile profile = readProfile(legacyPath, stackFingerprint(client));
            if (profile != null) {
                LOGGER.info("[MaterialLab] Loaded legacy profile {}; it will migrate on next save", legacyPath);
                return profile;
            }
        }
        MaterialLabProfile profile = new MaterialLabProfile();
        profile.stackFingerprint = stackFingerprint(client);
        return profile;
    }

    private static MaterialLabProfile readProfile(Path path, String fingerprint) {
        try (Reader reader = Files.newBufferedReader(path)) {
            MaterialLabProfile profile = GSON.fromJson(reader, MaterialLabProfile.class);
            if (profile != null) {
                profile.version = MaterialLabProfile.VERSION;
                profile.stackFingerprint = fingerprint;
                if (profile.recipes == null) profile.recipes = new java.util.LinkedHashMap<>();
                if (profile.remapHints == null) profile.remapHints = new java.util.LinkedHashMap<>();
                return profile;
            }
        } catch (Exception e) {
            LOGGER.warn("[MaterialLab] Failed to read profile {}; using empty profile", path, e);
        }
        return null;
    }

    private static boolean saveProfile(MinecraftClient client, MaterialLabProfile profile) {
        Path path = profilePath(client);
        try {
            Files.createDirectories(path.getParent());
            profile.version = MaterialLabProfile.VERSION;
            profile.stackFingerprint = stackFingerprint(client);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(profile, writer);
            }
            return true;
        } catch (IOException e) {
            LOGGER.warn("[MaterialLab] Failed to save profile {}", path, e);
            return false;
        }
    }

    public static String stackFingerprint(MinecraftClient client) {
        CRC32 crc = new CRC32();
        update(crc, "material-lab-v2");
        update(crc, Integer.toString(TextureArrayBridge.sortedSpriteIds.size()));
        int limit = Math.min(TextureArrayBridge.sortedSpriteIds.size(), TextureTracker.MAX_TEXTURES);
        for (int i = 0; i < limit; i++) {
            Identifier id = TextureArrayBridge.sortedSpriteIds.get(i);
            NativeImage albedo = AutoPbrTextureCatalog.albedo(i);
            NativeImage specular = TextureTracker.spriteBaselineSpecularCache.get(i);
            NativeImage normal = TextureTracker.spriteBaselineNormalCache.get(i);
            NativeImage flag = AutoPbrTextureCatalog.flag(i);
            byte specularSource = source(TextureTracker.spriteBaselineSpecularSource, i);
            byte normalSource = source(TextureTracker.spriteBaselineNormalSource, i);
            int heightRange = heightAlphaRangePacked(normal, albedo);
            update(crc, id == null ? "null" : id.toString());
            update(crc, Integer.toString(i));
            update(crc, "specSource=" + AutoPbrTextureCatalog.sourceName(specularSource));
            update(crc, "normalSource=" + AutoPbrTextureCatalog.sourceName(normalSource));
            update(crc, "flags=" + baselineSourceFlags(specular, normal, specularSource, normalSource, heightRange));
            update(crc, "height=" + heightRange);
            updateImage(crc, "albedo", albedo);
            updateImage(crc, "specular", specular);
            updateImage(crc, "normal", normal);
            updateImage(crc, "flag", flag);
        }
        return "stack-" + Long.toHexString(crc.getValue());
    }

    private static String legacyStackFingerprint(MinecraftClient client) {
        CRC32 crc = new CRC32();
        update(crc, "material-lab-v1");
        update(crc, Integer.toString(TextureArrayBridge.sortedSpriteIds.size()));
        int limit = Math.min(TextureArrayBridge.sortedSpriteIds.size(), 2048);
        for (int i = 0; i < limit; i++) {
            Identifier id = TextureArrayBridge.sortedSpriteIds.get(i);
            update(crc, id == null ? "null" : id.toString());
            NativeImage albedo = AutoPbrTextureCatalog.albedo(i);
            if (albedo != null) {
                update(crc, albedo.getWidth() + "x" + albedo.getHeight());
                update(crc, Integer.toHexString(AutoPbrTextureCatalog.averageColor(albedo, 0)));
            }
        }
        return "stack-" + Long.toHexString(crc.getValue());
    }

    private static void updateImage(CRC32 crc, String label, NativeImage image) {
        update(crc, label);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
            update(crc, "none");
            return;
        }
        update(crc, image.getWidth() + "x" + image.getHeight());
        update(crc, Long.toHexString(imageHash(image)));
    }

    private static long imageHash(NativeImage image) {
        CRC32 crc = new CRC32();
        int stepX = Math.max(1, image.getWidth() / 8);
        int stepY = Math.max(1, image.getHeight() / 8);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                update(crc, Integer.toHexString(image.getColorArgb(x, y)));
            }
        }
        return crc.getValue();
    }

    private static int baselineSourceFlags(NativeImage specular, NativeImage normal,
                                           byte specularSource, byte normalSource, int heightRange) {
        int flags = TextureTracker.encodeSpriteSourceFlags(specularSource, normalSource);
        if (specular != null) flags |= TextureTracker.SPRITE_FLAG_HAS_SPECULAR;
        if (normal != null) flags |= TextureTracker.SPRITE_FLAG_HAS_NORMAL;
        if (normal != null && authoredSource(normalSource) && heightRange >= 0) {
            flags |= TextureTracker.SPRITE_FLAG_HAS_HEIGHT;
        }
        return flags;
    }

    private static int heightAlphaRangePacked(NativeImage normal, NativeImage albedo) {
        if (normal == null || normal.getWidth() <= 0 || normal.getHeight() <= 0) return -1;
        int min = 255;
        int max = 0;
        boolean found = false;
        int stepX = Math.max(1, normal.getWidth() / 64);
        int stepY = Math.max(1, normal.getHeight() / 64);
        for (int y = 0; y < normal.getHeight(); y += stepY) {
            for (int x = 0; x < normal.getWidth(); x += stepX) {
                if (albedo != null
                    && (((sampleScaled(albedo, x, y, normal) >>> 24) & 0xFF) == 0)) {
                    continue;
                }
                int alpha = (normal.getColorArgb(x, y) >>> 24) & 0xFF;
                min = Math.min(min, alpha);
                max = Math.max(max, alpha);
                found = true;
            }
        }
        return !found || max <= min ? -1 : min | (max << 8);
    }

    private static int sampleScaled(NativeImage image, int x, int y, NativeImage sourceSpace) {
        int sx = Math.max(0, Math.min(image.getWidth() - 1, x * image.getWidth() / sourceSpace.getWidth()));
        int sy = Math.max(0, Math.min(image.getHeight() - 1, y * image.getHeight() / sourceSpace.getHeight()));
        return image.getColorArgb(sx, sy);
    }

    private static boolean authoredSource(byte source) {
        return source == TextureTracker.SOURCE_PACK_AUTHORED || source == TextureTracker.SOURCE_USER_CUSTOM;
    }

    private static byte source(byte[] sources, int spriteId) {
        if (spriteId < 0 || spriteId >= sources.length) return TextureTracker.SOURCE_FLAT;
        return sources[spriteId];
    }

    private static void update(CRC32 crc, String value) {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
    }

    private static String key(Identifier sprite) {
        return sprite == null ? "minecraft:block/oak_planks" : sprite.toString();
    }

    private static Path runDirectory(MinecraftClient client) {
        return client == null ? Path.of(".") : client.runDirectory.toPath();
    }
}
