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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackBlockLayerResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static volatile Cache cache = Cache.empty();
    private static volatile String cachedShaderPackConfig = null;
    private static volatile Path cachedShaderPackPath = null;

    private ResourcePackBlockLayerResolver() {
    }

    public static int resolveBlockAlphaMode(@Nullable BlockState state) {
        if (state == null || !Options.materialCompatEnabled || !Options.materialCompatOverlaysEnabled) {
            return -1;
        }
        LayerIndex index = activeIndex();
        if (index.layerRules().isEmpty()) {
            return -1;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null) {
            return -1;
        }
        return index.alphaMode(state);
    }

    public static int resolveShaderBlockId(@Nullable BlockState state) {
        if (state == null || !Options.materialCompatEnabled) {
            return -1;
        }
        LayerIndex index = activeIndex();
        if (index.shaderBlockRules().isEmpty()) {
            return -1;
        }
        Identifier id = Registries.BLOCK.getId(state.getBlock());
        if (id == null) {
            return -1;
        }
        return index.shaderBlockId(state);
    }

    public static boolean disablesOverlaySolidCheck() {
        if (!Options.materialCompatEnabled || !Options.materialCompatOverlaysEnabled) {
            return false;
        }
        return activeIndex().disableSolidCheck();
    }

    public static int resolveBlockAlphaModeForTest(String blockPropertiesText, String blockId) {
        return parse(blockPropertiesText).alphaMode(normalizeBlockToken(blockId), Map.of());
    }

    public static int resolveBlockAlphaModeForTest(String blockPropertiesText, String blockId,
        Map<String, String> stateValues) {
        return parse(blockPropertiesText).alphaMode(normalizeBlockToken(blockId), normalizeStateValues(stateValues));
    }

    public static int resolveMergedBlockAlphaModeForTest(String shaderBlockPropertiesText,
        String resourcePackBlockPropertiesText, String blockId) {
        return parseAll(List.of(shaderBlockPropertiesText, resourcePackBlockPropertiesText))
            .alphaMode(normalizeBlockToken(blockId), Map.of());
    }

    public static int resolveShaderBlockIdForTest(String blockPropertiesText, String blockId) {
        return parse(blockPropertiesText).shaderBlockId(normalizeBlockToken(blockId), Map.of());
    }

    public static int resolveShaderBlockIdForTest(String blockPropertiesText, String blockId,
        Map<String, String> stateValues) {
        return parse(blockPropertiesText).shaderBlockId(normalizeBlockToken(blockId),
            normalizeStateValues(stateValues));
    }

    public static boolean disablesOverlaySolidCheckForTest(String blockPropertiesText) {
        return parse(blockPropertiesText).disableSolidCheck();
    }

    public static boolean disablesOverlaySolidCheckForTest(String shaderBlockPropertiesText,
        String resourcePackBlockPropertiesText) {
        return parseAll(List.of(shaderBlockPropertiesText, resourcePackBlockPropertiesText)).disableSolidCheck();
    }

    public static int ruleCountForTest(String blockPropertiesText) {
        return parse(blockPropertiesText).layerRules().size();
    }

    public static int shaderBlockRuleCountForTest(String blockPropertiesText) {
        return parse(blockPropertiesText).shaderBlockRules().size();
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
        if (next.totalRuleCount() > 0) {
            LOGGER.info("[MaterialCompat] Block layer resolver compiled {} block.properties entries from {}",
                next.totalRuleCount(), key);
        }
        return next;
    }

    private static String cacheKey(@Nullable Path shaderPackPath, @Nullable ResourceManager resourceManager) {
        StringBuilder key = new StringBuilder();
        key.append(shaderPackPath == null ? "" : shaderPackPath.toString());
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
        String cachedConfig = cachedShaderPackConfig;
        if (Objects.equals(cachedConfig, configured)) {
            return cachedShaderPackPath;
        }
        try {
            Path path = Path.of(configured);
            if (!path.isAbsolute() && RadianceClient.radianceDir != null) {
                path = RadianceClient.radianceDir.resolve(path);
            }
            Path normalized = path.toAbsolutePath().normalize();
            cachedShaderPackConfig = configured;
            cachedShaderPackPath = normalized;
            return normalized;
        } catch (Exception e) {
            cachedShaderPackConfig = configured;
            cachedShaderPackPath = null;
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
        ArrayList<LayerRule> layerRules = new ArrayList<>();
        ArrayList<ShaderBlockRule> shaderBlockRules = new ArrayList<>();
        Boolean disableSolidCheck = null;
        for (String text : texts) {
            Boolean parsedSolidCheck = parseInto(layerRules, shaderBlockRules, text);
            if (parsedSolidCheck != null) {
                disableSolidCheck = parsedSolidCheck;
            }
        }
        boolean disabled = Boolean.TRUE.equals(disableSolidCheck);
        return layerRules.isEmpty() && shaderBlockRules.isEmpty() && !disabled
            ? LayerIndex.empty()
            : new LayerIndex(List.copyOf(layerRules), List.copyOf(shaderBlockRules), disabled);
    }

    @Nullable
    private static Boolean parseInto(List<LayerRule> layerRules, List<ShaderBlockRule> shaderBlockRules, String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Properties props = new Properties();
        try {
            props.load(new StringReader(text));
        } catch (IOException e) {
            return null;
        }

        addLayer(layerRules, props.getProperty("layer.solid"), PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE);
        addLayer(layerRules, props.getProperty("layer.cutout"), PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT);
        addLayer(layerRules, props.getProperty("layer.cutout_mipped"), PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT);
        addLayer(layerRules, props.getProperty("layer.translucent"), PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT);
        addShaderBlockRules(shaderBlockRules, props);
        String disableSolidCheck = props.getProperty("disableSolidCheck");
        return disableSolidCheck == null ? null : parseBoolean(disableSolidCheck);
    }

    private static void addLayer(List<LayerRule> rules, @Nullable String raw, int alphaMode) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String piece : splitBlockTokens(raw)) {
            parseBlockPredicate(piece).ifPresent(predicate -> rules.add(new LayerRule(predicate, alphaMode)));
        }
    }

    private static void addShaderBlockRules(List<ShaderBlockRule> rules, Properties props) {
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("block.")) {
                continue;
            }
            int shaderBlockId;
            try {
                shaderBlockId = Integer.parseInt(key.substring("block.".length()).trim());
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (shaderBlockId < 0) {
                continue;
            }
            String raw = props.getProperty(key);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String piece : splitBlockTokens(raw)) {
                parseBlockPredicate(piece)
                    .ifPresent(predicate -> rules.add(new ShaderBlockRule(predicate, shaderBlockId)));
            }
        }
    }

    private static String normalizeBlockToken(String raw) {
        return parseBlockPredicate(raw).map(BlockPredicate::blockId).orElse("");
    }

    private static List<String> splitBlockTokens(String value) {
        ArrayList<String> tokens = new ArrayList<>();
        int start = -1;
        int bracketDepth = 0;
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '[') {
                bracketDepth++;
            } else if (ch == ']' && bracketDepth > 0) {
                bracketDepth--;
            }
            boolean separator = bracketDepth == 0 && (Character.isWhitespace(ch) || ch == ',');
            if (separator) {
                if (start >= 0) {
                    tokens.add(value.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            tokens.add(value.substring(start));
        }
        return tokens;
    }

    private static Optional<BlockPredicate> parseBlockPredicate(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (token.isEmpty() || token.startsWith("#")) {
            return Optional.empty();
        }
        String stateExpression = "";
        int bracket = token.indexOf('[');
        if (bracket >= 0) {
            int end = token.lastIndexOf(']');
            if (end > bracket) {
                stateExpression = token.substring(bracket + 1, end);
            }
            token = token.substring(0, bracket);
        }
        int firstColon = token.indexOf(':');
        int secondColon = firstColon < 0 ? -1 : token.indexOf(':', firstColon + 1);
        if (stateExpression.isEmpty() && secondColon > 0 && token.substring(secondColon + 1).contains("=")) {
            stateExpression = token.substring(secondColon + 1);
            token = token.substring(0, secondColon);
        }
        String blockId = normalizeBlockIdToken(token);
        if (blockId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPredicate(blockId, parseStatePredicates(stateExpression)));
    }

    private static String normalizeBlockIdToken(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (token.isEmpty() || token.startsWith("#")) {
            return "";
        }
        int bracket = token.indexOf('[');
        if (bracket >= 0) {
            token = token.substring(0, bracket);
        }
        int firstColon = token.indexOf(':');
        int secondColon = firstColon < 0 ? -1 : token.indexOf(':', firstColon + 1);
        if (secondColon > 0 && token.substring(secondColon + 1).contains("=")) {
            token = token.substring(0, secondColon);
        }
        if (token.startsWith("block/")) {
            token = token.substring("block/".length());
        }
        if (token.isEmpty()) {
            return "";
        }
        int colon = token.indexOf(':');
        if (colon > 0) {
            String namespace = token.substring(0, colon);
            String path = token.substring(colon + 1);
            if (path.startsWith("block/")) {
                path = path.substring("block/".length());
            }
            token = namespace + ":" + path;
        } else {
            token = "minecraft:" + token;
        }
        return Identifier.tryParse(token) == null ? "" : token;
    }

    private static List<StatePredicate> parseStatePredicates(String expression) {
        if (expression == null || expression.isBlank()) {
            return List.of();
        }
        ArrayList<StatePredicate> predicates = new ArrayList<>();
        for (String clause : expression.split(",")) {
            int equals = clause.indexOf('=');
            if (equals <= 0 || equals == clause.length() - 1) {
                continue;
            }
            String name = clause.substring(0, equals).trim().toLowerCase(Locale.ROOT);
            if (name.isEmpty()) {
                continue;
            }
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (String value : clause.substring(equals + 1).split("\\|")) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    values.add(normalized);
                }
            }
            if (!values.isEmpty()) {
                predicates.add(new StatePredicate(name, Set.copyOf(values)));
            }
        }
        return List.copyOf(predicates);
    }

    private static Map<String, String> normalizeStateValues(Map<String, String> stateValues) {
        if (stateValues == null || stateValues.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stateValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                entry.getValue().trim().toLowerCase(Locale.ROOT));
        }
        return Map.copyOf(normalized);
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        String token = raw.trim().toLowerCase(Locale.ROOT);
        return token.equals("true") || token.equals("1") || token.equals("yes") || token.equals("on");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(Property property, Comparable value) {
        return property.name(value).toLowerCase(Locale.ROOT);
    }

    private record LayerIndex(List<LayerRule> layerRules, List<ShaderBlockRule> shaderBlockRules,
                              boolean disableSolidCheck) {
        static LayerIndex empty() {
            return new LayerIndex(List.of(), List.of(), false);
        }

        int totalRuleCount() {
            return layerRules.size() + shaderBlockRules.size();
        }

        int alphaMode(BlockState state) {
            for (int i = layerRules.size() - 1; i >= 0; i--) {
                LayerRule rule = layerRules.get(i);
                if (rule.predicate().matches(state)) {
                    return rule.alphaMode();
                }
            }
            return -1;
        }

        int alphaMode(String blockId, Map<String, String> stateValues) {
            for (int i = layerRules.size() - 1; i >= 0; i--) {
                LayerRule rule = layerRules.get(i);
                if (rule.predicate().matches(blockId, stateValues)) {
                    return rule.alphaMode();
                }
            }
            return -1;
        }

        int shaderBlockId(String blockId, Map<String, String> stateValues) {
            for (int i = shaderBlockRules.size() - 1; i >= 0; i--) {
                ShaderBlockRule rule = shaderBlockRules.get(i);
                if (rule.predicate().matches(blockId, stateValues)) {
                    return rule.shaderBlockId();
                }
            }
            return -1;
        }

        int shaderBlockId(BlockState state) {
            for (int i = shaderBlockRules.size() - 1; i >= 0; i--) {
                ShaderBlockRule rule = shaderBlockRules.get(i);
                if (rule.predicate().matches(state)) {
                    return rule.shaderBlockId();
                }
            }
            return -1;
        }
    }

    private record LayerRule(BlockPredicate predicate, int alphaMode) {
    }

    private record ShaderBlockRule(BlockPredicate predicate, int shaderBlockId) {
    }

    private record BlockPredicate(String blockId, List<StatePredicate> states) {
        boolean matches(BlockState state) {
            if (!blockId.equals(Registries.BLOCK.getId(state.getBlock()).toString())) {
                return false;
            }
            for (StatePredicate predicate : states) {
                if (!predicate.matches(state)) {
                    return false;
                }
            }
            return true;
        }

        boolean matches(String actualBlockId, Map<String, String> stateValues) {
            if (!blockId.equals(actualBlockId)) {
                return false;
            }
            for (StatePredicate predicate : states) {
                if (!predicate.matches(stateValues)) {
                    return false;
                }
            }
            return true;
        }
    }

    private record StatePredicate(String name, Set<String> values) {
        boolean matches(BlockState state) {
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
                Property<?> property = entry.getKey();
                if (!name.equals(property.getName())) {
                    continue;
                }
                String valueName = propertyValueName(property, entry.getValue());
                return values.contains(valueName) || values.contains("*");
            }
            return false;
        }

        boolean matches(Map<String, String> stateValues) {
            String value = stateValues.get(name);
            return value != null && (values.contains(value) || values.contains("*"));
        }
    }

    private record Cache(String key, LayerIndex index) {
        static Cache empty() {
            return new Cache("", LayerIndex.empty());
        }
    }
}
