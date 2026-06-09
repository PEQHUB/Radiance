package com.radiance.client.autopbr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public final class AutoPbrGeneratedAssetStore {
    private static final Map<String, String> LAST_WRITES = new ConcurrentHashMap<>();

    private AutoPbrGeneratedAssetStore() {
    }

    public static void write(Identifier sprite, String channel, NativeImage image) {
        if (sprite == null || channel == null || channel.isBlank() || image == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) return;
        Path path = cachePath(client, sprite, channel);
        try {
            Files.createDirectories(path.getParent());
            image.writeTo(path);
            LAST_WRITES.put(sprite + ":" + channel, path.toString());
        } catch (IOException ignored) {
            LAST_WRITES.put(sprite + ":" + channel, "write_failed:" + path);
        }
    }

    public static Path cachePath(MinecraftClient client, Identifier sprite, String channel) {
        String namespace = sprite == null ? "minecraft" : sprite.getNamespace();
        String spritePath = sprite == null ? "block/oak_planks" : sprite.getPath();
        return client.runDirectory.toPath()
            .resolve("radiance")
            .resolve("material_lab")
            .resolve("generated")
            .resolve(namespace)
            .resolve(spritePath + "." + channel + ".png");
    }

    public static String lastWrite(Identifier sprite, String channel) {
        if (sprite == null || channel == null) return "";
        return LAST_WRITES.getOrDefault(sprite + ":" + channel, "");
    }
}
