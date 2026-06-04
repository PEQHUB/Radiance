package com.radiance.client.materiallab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
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
            try (Reader reader = Files.newBufferedReader(path)) {
                MaterialLabProfile profile = GSON.fromJson(reader, MaterialLabProfile.class);
                if (profile != null) {
                    profile.version = MaterialLabProfile.VERSION;
                    if (profile.stackFingerprint == null || profile.stackFingerprint.isBlank()) {
                        profile.stackFingerprint = stackFingerprint(client);
                    }
                    if (profile.recipes == null) profile.recipes = new java.util.LinkedHashMap<>();
                    if (profile.remapHints == null) profile.remapHints = new java.util.LinkedHashMap<>();
                    return profile;
                }
            } catch (Exception e) {
                LOGGER.warn("[MaterialLab] Failed to read profile {}; using empty profile", path, e);
            }
        }
        MaterialLabProfile profile = new MaterialLabProfile();
        profile.stackFingerprint = stackFingerprint(client);
        return profile;
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
        update(crc, "material-lab-v1");
        update(crc, Integer.toString(TextureArrayBridge.sortedSpriteIds.size()));
        int limit = Math.min(TextureArrayBridge.sortedSpriteIds.size(), 2048);
        for (int i = 0; i < limit; i++) {
            Identifier id = TextureArrayBridge.sortedSpriteIds.get(i);
            update(crc, id.toString());
            NativeImage albedo = AutoPbrTextureCatalog.albedo(i);
            if (albedo != null) {
                update(crc, albedo.getWidth() + "x" + albedo.getHeight());
                update(crc, Integer.toHexString(AutoPbrTextureCatalog.averageColor(albedo, 0)));
            }
        }
        return "stack-" + Long.toHexString(crc.getValue());
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
