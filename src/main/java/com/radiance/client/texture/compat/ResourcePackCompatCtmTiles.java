package com.radiance.client.texture.compat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import net.minecraft.util.Identifier;

public final class ResourcePackCompatCtmTiles {
    private static final int TILE_EXPANSION_LIMIT = 512;
    private static final Map<Identifier, String> REGISTERED_CTM_SPRITE_ASSET_PATHS =
        new ConcurrentHashMap<>();
    private static final Map<Identifier, List<String>> REGISTERED_CTM_MATERIAL_FALLBACK_ASSET_PATHS =
        new ConcurrentHashMap<>();

    private ResourcePackCompatCtmTiles() {
    }

    public static String assetPath(Identifier id) {
        if (id == null) {
            return "";
        }
        return "assets/" + id.getNamespace() + "/" + normalize(id.getPath());
    }

    public static Identifier resourceIdentifierFromAssetPath(String assetPath) {
        String normalized = normalize(assetPath);
        if (!normalized.startsWith("assets/")) {
            return null;
        }
        int namespaceStart = "assets/".length();
        int namespaceEnd = normalized.indexOf('/', namespaceStart);
        if (namespaceEnd <= namespaceStart || namespaceEnd >= normalized.length() - 1) {
            return null;
        }
        String namespace = normalized.substring(namespaceStart, namespaceEnd).toLowerCase(Locale.ROOT);
        String path = normalized.substring(namespaceEnd + 1);
        return Identifier.tryParse(namespace + ":" + path);
    }

    public static List<String> ctmTileDependencyAssetPaths(String propertyAssetPath, Properties props) {
        if (props == null) {
            return List.of();
        }
        int repeatWidth = parsePositiveInt(props.getProperty("width", "1"), 1);
        int repeatHeight = parsePositiveInt(props.getProperty("height", "1"), 1);
        return ctmTileDependencyAssetPaths(propertyAssetPath,
            props.getProperty("tiles", ""),
            props.getProperty("method", ""),
            repeatWidth,
            repeatHeight);
    }

    public static List<String> ctmTileDependencyAssetPaths(String propertyAssetPath, String tilesValue,
        String methodValue) {
        return ctmTileDependencyAssetPaths(propertyAssetPath, tilesValue, methodValue, 1, 1);
    }

    public static List<String> ctmTileDependencyAssetPaths(String propertyAssetPath, String tilesValue,
        String methodValue, int repeatWidth, int repeatHeight) {
        String value = tilesValue == null ? "" : tilesValue.trim();
        if (value.isEmpty()) {
            value = inferredTilesValue(methodValue, repeatWidth, repeatHeight);
        }
        return ctmTileDependencyAssetPaths(propertyAssetPath, value);
    }

    public static List<String> ctmTileDependencyAssetPaths(String propertyAssetPath, String tilesValue) {
        if (propertyAssetPath == null || tilesValue == null || tilesValue.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        String[] tokens = tilesValue.trim().split("[\\s,]+");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty() || token.startsWith("<")) {
                continue;
            }
            int dash = token.indexOf('-');
            if (dash > 0 && dash < token.length() - 1
                && isPositiveInt(token.substring(0, dash))
                && isPositiveInt(token.substring(dash + 1))) {
                int start = Integer.parseInt(token.substring(0, dash));
                int end = Integer.parseInt(token.substring(dash + 1));
                int step = start <= end ? 1 : -1;
                int emitted = 0;
                for (int value = start; value != end + step; value += step) {
                    dependencies.add(resolveCtmTileAssetPath(propertyAssetPath, String.valueOf(value)));
                    if (++emitted >= TILE_EXPANSION_LIMIT) {
                        break;
                    }
                }
                continue;
            }
            dependencies.add(resolveCtmTileAssetPath(propertyAssetPath, token));
            if (dependencies.size() >= TILE_EXPANSION_LIMIT) {
                break;
            }
        }
        return new ArrayList<>(dependencies);
    }

    public static String resolveCtmTileAssetPath(String propertyAssetPath, String tileToken) {
        String token = normalize(tileToken).trim();
        while (token.startsWith("./")) {
            token = token.substring(2);
        }
        if (token.endsWith(".png")) {
            token = token.substring(0, token.length() - 4);
        }
        if (token.startsWith("assets/")) {
            return token + ".png";
        }
        int colon = token.indexOf(':');
        if (colon > 0) {
            String namespace = token.substring(0, colon);
            String path = token.substring(colon + 1);
            if (path.startsWith("textures/")) {
                return "assets/" + namespace + "/" + path + ".png";
            }
            return "assets/" + namespace + "/textures/" + path + ".png";
        }
        if (token.startsWith("textures/")) {
            return "assets/minecraft/" + token + ".png";
        }
        if (token.startsWith("block/") || token.startsWith("item/") || token.startsWith("entity/")) {
            return "assets/minecraft/textures/" + token + ".png";
        }
        String baseDir = normalize(propertyAssetPath);
        int slash = baseDir.lastIndexOf('/');
        baseDir = slash >= 0 ? baseDir.substring(0, slash) : "";
        return (baseDir.isEmpty() ? token : baseDir + "/" + token) + ".png";
    }

    public static String atlasSpriteIdentifier(String assetPath) {
        String natural = naturalAtlasSpriteIdentifier(assetPath);
        if (!natural.isEmpty()) {
            return natural;
        }
        return syntheticAtlasSpriteIdentifier(assetPath);
    }

    public static String naturalAtlasSpriteIdentifier(String assetPath) {
        String normalized = normalize(assetPath);
        if (!normalized.startsWith("assets/") || !normalized.endsWith(".png")) {
            return "";
        }
        int namespaceStart = "assets/".length();
        int namespaceEnd = normalized.indexOf('/', namespaceStart);
        if (namespaceEnd < 0) {
            return "";
        }
        String namespace = normalized.substring(namespaceStart, namespaceEnd);
        String prefix = "assets/" + namespace + "/textures/";
        if (!normalized.startsWith(prefix)) {
            return "";
        }
        String spritePath = normalized.substring(prefix.length(), normalized.length() - 4);
        return namespace + ":" + spritePath;
    }

    public static boolean requiresAtlasAdmission(String assetPath) {
        String normalized = normalize(assetPath);
        return normalized.startsWith("assets/")
            && normalized.endsWith(".png")
            && naturalAtlasSpriteIdentifier(normalized).isEmpty();
    }

    public static boolean isCtmTileResourceIdentifier(Identifier id) {
        if (id == null) {
            return false;
        }
        String path = normalize(id.getPath());
        return path.endsWith(".png")
            && !ResourcePackTextureNames.hasPbrAuxiliarySuffix(path)
            && (path.startsWith("optifine/ctm/") || path.startsWith("mcpatcher/ctm/"));
    }

    public static void clearRegisteredCtmSpriteAssetPaths() {
        REGISTERED_CTM_SPRITE_ASSET_PATHS.clear();
        REGISTERED_CTM_MATERIAL_FALLBACK_ASSET_PATHS.clear();
    }

    public static void registerCtmSpriteAssetPath(Identifier spriteId, String assetPath) {
        registerCtmSpriteAssetPath(spriteId, assetPath, List.of());
    }

    public static void registerCtmSpriteAssetPath(Identifier spriteId, String assetPath,
        List<String> materialFallbackAssetPaths) {
        if (spriteId == null || assetPath == null || assetPath.isBlank()) {
            return;
        }
        String normalized = normalize(assetPath);
        if (!requiresAtlasAdmission(normalized)) {
            return;
        }
        REGISTERED_CTM_SPRITE_ASSET_PATHS.put(spriteId, normalized);
        List<String> fallbacks = normalizeFallbackAssetPaths(materialFallbackAssetPaths);
        if (fallbacks.isEmpty()) {
            REGISTERED_CTM_MATERIAL_FALLBACK_ASSET_PATHS.remove(spriteId);
        } else {
            REGISTERED_CTM_MATERIAL_FALLBACK_ASSET_PATHS.put(spriteId, fallbacks);
        }
    }

    public static Identifier ctmSidecarResourceIdentifier(Identifier spriteId, String suffix) {
        if (spriteId == null || suffix == null || suffix.isBlank()) {
            return null;
        }
        String assetPath = REGISTERED_CTM_SPRITE_ASSET_PATHS.get(spriteId);
        if (assetPath == null || assetPath.isBlank()) {
            return null;
        }
        return resourceIdentifierFromAssetPath(sidecarPath(assetPath, suffix));
    }

    public static List<Identifier> ctmMaterialFallbackSidecarResourceIdentifiers(Identifier spriteId, String suffix) {
        if (spriteId == null || suffix == null || suffix.isBlank()) {
            return List.of();
        }
        List<String> assetPaths = REGISTERED_CTM_MATERIAL_FALLBACK_ASSET_PATHS.get(spriteId);
        if (assetPaths == null || assetPaths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Identifier> ids = new LinkedHashSet<>();
        for (String assetPath : assetPaths) {
            Identifier id = resourceIdentifierFromAssetPath(sidecarPath(assetPath, suffix));
            if (id != null) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    public static List<String> materialFallbackAssetPaths(String propertyAssetPath, Properties props) {
        if (propertyAssetPath == null || props == null) {
            return List.of();
        }
        String value = props.getProperty("matchTiles", "").trim();
        if (value.isEmpty()) {
            String path = normalize(propertyAssetPath);
            int slash = path.lastIndexOf('/');
            int dot = path.lastIndexOf('.');
            if (dot <= slash) {
                dot = path.length();
            }
            value = slash >= 0 ? path.substring(slash + 1, dot) : path.substring(0, dot);
        }
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (String raw : value.split("[\\s,]+")) {
            String path = materialFallbackAssetPathFromMatchTile(raw);
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return List.copyOf(paths);
    }

    public static String materialFallbackAssetPathFromMatchTile(String raw) {
        String token = normalize(raw).trim();
        if (token.isEmpty() || token.startsWith("<")) {
            return "";
        }
        if (token.endsWith(".png")) {
            token = token.substring(0, token.length() - 4);
        }

        String namespace = "minecraft";
        int colon = token.indexOf(':');
        if (colon > 0) {
            namespace = token.substring(0, colon).toLowerCase(Locale.ROOT);
            token = token.substring(colon + 1);
        }
        if (token.startsWith("textures/")) {
            return "assets/" + namespace + "/" + token + ".png";
        }
        if (token.startsWith("block/") || token.startsWith("item/") || token.startsWith("entity/")) {
            return "assets/" + namespace + "/textures/" + token + ".png";
        }
        if (token.contains("/")) {
            return "assets/" + namespace + "/textures/" + token + ".png";
        }
        return "assets/" + namespace + "/textures/block/" + token + ".png";
    }

    public static String sidecarPath(String assetPath, String suffix) {
        String normalized = normalize(assetPath);
        String base = normalized.endsWith(".png") ? normalized.substring(0, normalized.length() - 4) : normalized;
        return base + suffix + ".png";
    }

    private static List<String> normalizeFallbackAssetPaths(List<String> materialFallbackAssetPaths) {
        if (materialFallbackAssetPaths == null || materialFallbackAssetPaths.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String assetPath : materialFallbackAssetPaths) {
            String value = normalize(assetPath);
            if (value.startsWith("assets/") && value.endsWith(".png")
                && !ResourcePackTextureNames.hasPbrAuxiliarySuffix(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static String inferredTilesValue(String methodValue, int repeatWidth, int repeatHeight) {
        String method = methodValue == null ? "" : methodValue.trim().toLowerCase(Locale.ROOT);
        return switch (method) {
            case "ctm" -> "0-46";
            case "ctm_compact" -> "0-4";
            case "horizontal", "vertical" -> "0-3";
            case "horizontal+vertical", "vertical+horizontal" -> "0-6";
            case "top", "fixed" -> "0";
            case "repeat", "overlay_repeat" -> inferredRepeatTiles(repeatWidth, repeatHeight);
            case "overlay", "overlay_ctm" -> "0-16";
            case "overlay_fixed" -> "0";
            default -> "";
        };
    }

    private static String inferredRepeatTiles(int repeatWidth, int repeatHeight) {
        long rawCount = (long) Math.max(1, repeatWidth) * (long) Math.max(1, repeatHeight);
        int count = (int) Math.min(rawCount, TILE_EXPANSION_LIMIT);
        return "0-" + (count - 1);
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String syntheticAtlasSpriteIdentifier(String assetPath) {
        String normalized = normalize(assetPath);
        if (!normalized.startsWith("assets/") || !normalized.endsWith(".png")) {
            return "";
        }
        int namespaceStart = "assets/".length();
        int namespaceEnd = normalized.indexOf('/', namespaceStart);
        if (namespaceEnd <= namespaceStart) {
            return "";
        }
        String namespace = normalized.substring(namespaceStart, namespaceEnd).toLowerCase(Locale.ROOT);
        String path = normalized.substring(namespaceEnd + 1, normalized.length() - 4);
        String sanitized = sanitizePath(path);
        return namespace + ":radser_ctm/" + sanitized + "_" + stableHash(normalized);
    }

    private static String sanitizePath(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean previousSlash = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '-' || c == '.';
            if (c == '/') {
                if (!previousSlash && !out.isEmpty()) {
                    out.append('/');
                    previousSlash = true;
                }
            } else {
                out.append(valid ? c : '_');
                previousSlash = false;
            }
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '/') {
            out.deleteCharAt(out.length() - 1);
        }
        if (out.isEmpty()) {
            return "tile";
        }
        return out.toString();
    }

    private static String stableHash(String value) {
        CRC32 crc = new CRC32();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        crc.update(bytes, 0, bytes.length);
        return String.format(Locale.ROOT, "%08x", crc.getValue());
    }

    private static boolean isPositiveInt(String token) {
        if (token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }
}
