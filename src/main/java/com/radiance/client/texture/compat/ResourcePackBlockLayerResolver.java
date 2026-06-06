package com.radiance.client.texture.compat;

import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.vertex.PBRVertexFormatElements;
import java.io.IOException;
import java.io.StringReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackBlockLayerResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static volatile Cache cache = Cache.empty();

    private ResourcePackBlockLayerResolver() {
    }

    public static int resolveBlockAlphaMode(@Nullable BlockState state) {
        if (state == null || !Options.materialCompatEnabled || !Options.materialCompatOverlaysEnabled) {
            return -1;
        }
        LayerIndex index = activeIndex();
        if (index.rules().isEmpty()) {
            return -1;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null) {
            return -1;
        }
        return index.alphaMode(id.toString());
    }

    public static int resolveBlockAlphaModeForTest(String blockPropertiesText, String blockId) {
        return parse(blockPropertiesText).alphaMode(normalizeBlockToken(blockId));
    }

    public static int ruleCountForTest(String blockPropertiesText) {
        return parse(blockPropertiesText).rules().size();
    }

    private static LayerIndex activeIndex() {
        Path path = activeShaderPackPath();
        String key = cacheKey(path);
        Cache local = cache;
        if (Objects.equals(local.key(), key)) {
            return local.index();
        }
        String text = readBlockProperties(path);
        LayerIndex next = parse(text);
        cache = new Cache(key, next);
        if (!next.rules().isEmpty()) {
            LOGGER.info("[MaterialCompat] Shader block layer resolver compiled {} layer entries from {}",
                next.rules().size(), key);
        }
        return next;
    }

    private static String cacheKey(@Nullable Path shaderPackPath) {
        if (shaderPackPath == null) {
            return "";
        }
        Path watched = shaderPackPath;
        if (Files.isDirectory(shaderPackPath)) {
            watched = shaderPackPath.resolve("shaders").resolve("block.properties");
        }
        long modified = 0L;
        try {
            if (Files.exists(watched)) {
                modified = Files.getLastModifiedTime(watched).toMillis();
            }
        } catch (IOException ignored) {
        }
        return shaderPackPath + "|" + modified;
    }

    @Nullable
    private static Path activeShaderPackPath() {
        String configured = Options.shaderPackPath == null || Options.shaderPackPath.isBlank()
            ? Pipeline.VANILLA_RAY_TRACING_SHADER_PACK_PATH
            : Options.shaderPackPath.trim();
        try {
            Path path = Path.of(configured);
            if (!path.isAbsolute() && RadianceClient.radianceDir != null) {
                path = RadianceClient.radianceDir.resolve(path);
            }
            return path.toAbsolutePath().normalize();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readBlockProperties(@Nullable Path shaderPackPath) {
        if (shaderPackPath == null) {
            return "";
        }
        try {
            if (Files.isDirectory(shaderPackPath)) {
                Path file = shaderPackPath.resolve("shaders").resolve("block.properties");
                return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
            }
            if (!Files.exists(shaderPackPath)) {
                return "";
            }
            try (ZipFile zip = new ZipFile(shaderPackPath.toFile())) {
                ZipEntry entry = zip.getEntry("shaders/block.properties");
                if (entry == null) {
                    return "";
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[MaterialCompat] Failed to read shaders/block.properties from {}", shaderPackPath, e);
            return "";
        }
    }

    private static LayerIndex parse(String text) {
        if (text == null || text.isBlank()) {
            return LayerIndex.empty();
        }
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            return LayerIndex.empty();
        }

        Map<String, Integer> rules = new HashMap<>();
        addLayer(rules, props.getProperty("layer.solid"), PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE);
        addLayer(rules, props.getProperty("layer.cutout"), PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT);
        addLayer(rules, props.getProperty("layer.cutout_mipped"), PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT);
        addLayer(rules, props.getProperty("layer.translucent"), PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT);
        return rules.isEmpty() ? LayerIndex.empty() : new LayerIndex(Map.copyOf(rules));
    }

    private static void addLayer(Map<String, Integer> rules, @Nullable String raw, int alphaMode) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String piece : raw.split("[\\s,]+")) {
            String block = normalizeBlockToken(piece);
            if (!block.isEmpty()) {
                rules.put(block, alphaMode);
            }
        }
    }

    private static String normalizeBlockToken(String raw) {
        String token = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (token.isEmpty() || token.startsWith("#")) {
            return "";
        }
        int bracket = token.indexOf('[');
        if (bracket >= 0) {
            token = token.substring(0, bracket);
        }
        if (token.startsWith("block/")) {
            token = token.substring("block/".length());
        }
        int secondColon = token.indexOf(':', token.indexOf(':') + 1);
        if (secondColon > 0) {
            token = token.substring(0, secondColon);
        }
        if (token.isEmpty()) {
            return "";
        }
        if (!token.contains(":")) {
            token = "minecraft:" + token;
        }
        return Identifier.tryParse(token) == null ? "" : token;
    }

    private record LayerIndex(Map<String, Integer> rules) {
        static LayerIndex empty() {
            return new LayerIndex(Map.of());
        }

        int alphaMode(String blockId) {
            return rules.getOrDefault(blockId, -1);
        }
    }

    private record Cache(String key, LayerIndex index) {
        static Cache empty() {
            return new Cache("", LayerIndex.empty());
        }
    }
}
