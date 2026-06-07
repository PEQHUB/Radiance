package com.radiance.client.texture.compat;

import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.vertex.PBRVertexFormatElements;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
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

    public static int resolveMergedBlockAlphaModeForTest(String shaderBlockPropertiesText,
        String resourcePackBlockPropertiesText, String blockId) {
        return parseAll(List.of(shaderBlockPropertiesText, resourcePackBlockPropertiesText))
            .alphaMode(normalizeBlockToken(blockId));
    }

    public static int ruleCountForTest(String blockPropertiesText) {
        return parse(blockPropertiesText).rules().size();
    }

    private static LayerIndex activeIndex() {
        ResourceManager resourceManager = currentResourceManager();
        Path path = activeShaderPackPath();
        String key = cacheKey(path, resourceManager);
        Cache local = cache;
        if (Objects.equals(local.key(), key)) {
            return local.index();
        }
        List<String> texts = new ArrayList<>();
        texts.add(readBlockProperties(path));
        texts.addAll(readResourcePackBlockProperties(resourceManager));
        LayerIndex next = parseAll(texts);
        cache = new Cache(key, next);
        if (!next.rules().isEmpty()) {
            LOGGER.info("[MaterialCompat] Block layer resolver compiled {} layer entries from {}",
                next.rules().size(), key);
        }
        return next;
    }

    private static String cacheKey(@Nullable Path shaderPackPath, @Nullable ResourceManager resourceManager) {
        StringBuilder key = new StringBuilder();
        key.append(shaderPackPath == null ? "" : shaderPackPath.toString());
        Path watched = null;
        if (shaderPackPath != null) {
            watched = Files.isDirectory(shaderPackPath)
                ? shaderPackPath.resolve("shaders").resolve("block.properties")
                : shaderPackPath;
        }
        long modified = 0L;
        try {
            if (watched != null && Files.exists(watched)) {
                modified = Files.getLastModifiedTime(watched).toMillis();
            }
        } catch (IOException ignored) {
        }
        key.append("|").append(modified);
        key.append("|rm=").append(resourceManager == null ? 0 : System.identityHashCode(resourceManager));
        key.append("|tex=").append(TextureArrayBridge.getActiveTextureGeneration());
        return key.toString();
    }

    @Nullable
    private static ResourceManager currentResourceManager() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client == null ? null : client.getResourceManager();
        } catch (Throwable ignored) {
            return null;
        }
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

    private static List<String> readResourcePackBlockProperties(@Nullable ResourceManager resourceManager) {
        if (resourceManager == null) {
            return List.of();
        }
        ArrayList<String> texts = new ArrayList<>();
        readAllResources(resourceManager, Identifier.ofVanilla("optifine/block.properties"), texts);
        if (Options.materialCompatLegacyMcPatcherEnabled) {
            readAllResources(resourceManager, Identifier.ofVanilla("mcpatcher/block.properties"), texts);
        }
        return texts;
    }

    private static void readAllResources(ResourceManager resourceManager, Identifier id, List<String> out) {
        try {
            for (Resource resource : resourceManager.getAllResources(id)) {
                String text = readResourceText(resource);
                if (!text.isBlank()) {
                    out.add(text);
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[MaterialCompat] Failed to read {}", id, e);
        }
    }

    private static String readResourceText(Resource resource) throws IOException {
        try (BufferedReader reader = resource.getReader()) {
            StringBuilder out = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append('\n');
            }
            return out.toString();
        }
    }

    private static LayerIndex parse(String text) {
        return parseAll(List.of(text));
    }

    private static LayerIndex parseAll(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return LayerIndex.empty();
        }
        Map<String, Integer> rules = new HashMap<>();
        for (String text : texts) {
            parseInto(rules, text);
        }
        return rules.isEmpty() ? LayerIndex.empty() : new LayerIndex(Map.copyOf(rules));
    }

    private static void parseInto(Map<String, Integer> rules, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            return;
        }

        addLayer(rules, props.getProperty("layer.solid"), PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE);
        addLayer(rules, props.getProperty("layer.cutout"), PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT);
        addLayer(rules, props.getProperty("layer.cutout_mipped"), PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT);
        addLayer(rules, props.getProperty("layer.translucent"), PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT);
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
