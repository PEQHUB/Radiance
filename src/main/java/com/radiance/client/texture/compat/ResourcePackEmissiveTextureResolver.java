package com.radiance.client.texture.compat;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public final class ResourcePackEmissiveTextureResolver {
    private static final String DEFAULT_SUFFIX = "_e";
    private static final Map<Identifier, Identifier> REGISTERED_OVERLAY_BASES = new ConcurrentHashMap<>();
    private static final Map<Identifier, Identifier> REGISTERED_BASE_OVERLAYS = new ConcurrentHashMap<>();

    private ResourcePackEmissiveTextureResolver() {
    }

    public static String suffix(ResourceManager resourceManager, boolean legacyMcPatcher) {
        String suffix = suffixFrom(resourceManager, Identifier.ofVanilla("optifine/emissive.properties"));
        if (DEFAULT_SUFFIX.equals(suffix) && legacyMcPatcher) {
            suffix = suffixFrom(resourceManager, Identifier.ofVanilla("mcpatcher/emissive.properties"));
        }
        return normalizeSuffix(suffix);
    }

    public static String defaultSuffixForTest() {
        return DEFAULT_SUFFIX;
    }

    public static void clearRegisteredOverlaySprites() {
        REGISTERED_OVERLAY_BASES.clear();
        REGISTERED_BASE_OVERLAYS.clear();
    }

    public static void registerOverlaySprite(Identifier emissiveSprite, Identifier baseSprite) {
        if (emissiveSprite != null && baseSprite != null) {
            REGISTERED_OVERLAY_BASES.put(emissiveSprite, baseSprite);
            REGISTERED_BASE_OVERLAYS.put(baseSprite, emissiveSprite);
        }
    }

    @Nullable
    public static Identifier registeredOverlayForBaseSprite(@Nullable Identifier baseSprite) {
        return baseSprite == null ? null : REGISTERED_BASE_OVERLAYS.get(baseSprite);
    }

    public static boolean isDefaultEmissiveResource(Identifier id) {
        return isEmissiveResource(id, DEFAULT_SUFFIX);
    }

    public static boolean isEmissiveResource(@Nullable Identifier id, String suffix) {
        if (id == null || suffix == null || suffix.isBlank()) {
            return false;
        }
        String path = id.getPath();
        if (!path.endsWith(".png")) {
            return false;
        }
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        String name = path.substring(slash + 1, dot);
        return name.toLowerCase(Locale.ROOT).endsWith(normalizeSuffix(suffix).toLowerCase(Locale.ROOT));
    }

    @Nullable
    public static Identifier baseSpriteForEmissiveSprite(Identifier spriteId, String suffix) {
        if (spriteId == null) {
            return null;
        }
        Identifier registered = REGISTERED_OVERLAY_BASES.get(spriteId);
        if (registered != null) {
            return registered;
        }
        if (!isEmissiveResource(spritePathAsResource(spriteId), suffix)) {
            return null;
        }
        String path = spriteId.getPath();
        String normalizedSuffix = normalizeSuffix(suffix);
        String basePath = stripSuffixBeforeExtension(path, normalizedSuffix);
        if (basePath == null) {
            return null;
        }
        if (basePath.endsWith(".png")) {
            basePath = basePath.substring(0, basePath.length() - 4);
        }
        return Identifier.of(spriteId.getNamespace(), basePath);
    }

    @Nullable
    public static String emissiveAssetPath(String baseAssetPath, String suffix) {
        if (baseAssetPath == null || !baseAssetPath.toLowerCase(Locale.ROOT).endsWith(".png")) {
            return null;
        }
        String normalizedSuffix = normalizeSuffix(suffix);
        int dot = baseAssetPath.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        return baseAssetPath.substring(0, dot) + normalizedSuffix + baseAssetPath.substring(dot);
    }

    @Nullable
    public static String baseAssetPathForEmissive(String emissiveAssetPath, String suffix) {
        return stripSuffixBeforeExtension(emissiveAssetPath, normalizeSuffix(suffix));
    }

    private static String suffixFrom(ResourceManager resourceManager, Identifier id) {
        if (resourceManager == null) {
            return DEFAULT_SUFFIX;
        }
        Optional<Resource> resource = resourceManager.getResource(id);
        if (resource.isEmpty()) {
            return DEFAULT_SUFFIX;
        }
        Properties props = new Properties();
        try (BufferedReader reader = resource.get().getReader()) {
            props.load(reader);
        } catch (IOException e) {
            return DEFAULT_SUFFIX;
        }
        String suffix = props.getProperty("suffix.emissive", props.getProperty("suffix", DEFAULT_SUFFIX));
        return normalizeSuffix(suffix);
    }

    private static Identifier spritePathAsResource(Identifier spriteId) {
        String path = spriteId.getPath();
        if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return Identifier.of(spriteId.getNamespace(), path);
    }

    @Nullable
    private static String stripSuffixBeforeExtension(String path, String suffix) {
        if (path == null || suffix == null || suffix.isBlank()) {
            return null;
        }
        int dot = path.lastIndexOf('.');
        String extension = "";
        String base = path;
        if (dot >= 0) {
            extension = path.substring(dot);
            base = path.substring(0, dot);
        }
        if (!base.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return base.substring(0, base.length() - suffix.length()) + extension;
    }

    private static String normalizeSuffix(String suffix) {
        String value = suffix == null ? "" : suffix.trim();
        return value.isEmpty() ? DEFAULT_SUFFIX : value;
    }
}
