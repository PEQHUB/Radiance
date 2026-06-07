package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackRandomEntityTextureResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final int RULE_LIMIT = 1024;
    private static final int CHOICE_LIMIT = 128;
    private static final Set<Identifier> REGISTERED_TEXTURES = ConcurrentHashMap.newKeySet();
    private static volatile Cache cache = Cache.empty();

    private ResourcePackRandomEntityTextureResolver() {
    }

    public static Identifier resolveTexture(Identifier baseTexture, int entityHash,
        double x, double y, double z) {
        if (baseTexture == null || !Options.materialCompatEnabled || !Options.materialCompatRandomEnabled) {
            return baseTexture;
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return baseTexture;
        }
        String biomeId = biomeAt(x, y, z);
        return activeIndex(resourceManager).resolve(baseTexture, entityHash, biomeId);
    }

    public static void ensureTextureRegistered(TextureManager textureManager, Identifier textureId) {
        if (textureManager == null || textureId == null || TextureTracker.textureID2GLID.containsKey(textureId)) {
            return;
        }
        if (!REGISTERED_TEXTURES.add(textureId)) {
            return;
        }
        try {
            textureManager.registerTexture(textureId);
        } catch (Throwable e) {
            REGISTERED_TEXTURES.remove(textureId);
            LOGGER.debug("[MaterialCompat] Random entity texture registration skipped for {}", textureId, e);
        }
    }

    public static RandomEntityIndex buildForTest(ResourceManager resourceManager, boolean legacyMcPatcher) {
        return build(resourceManager, legacyMcPatcher);
    }

    private static ResourceManager currentResourceManager() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client == null ? null : client.getResourceManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String biomeAt(double x, double y, double z) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            ClientWorld world = client == null ? null : client.world;
            if (world == null) {
                return "";
            }
            return world.getBiome(BlockPos.ofFloored(x, y, z)).getKey()
                .map(key -> normalizeBiomeToken(key.getValue().toString()))
                .orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static RandomEntityIndex activeIndex(ResourceManager resourceManager) {
        long generation = TextureArrayBridge.getActiveTextureGeneration();
        Cache local = cache;
        if (local.resourceManager == resourceManager && local.textureGeneration == generation) {
            return local.index;
        }
        RandomEntityIndex next = build(resourceManager, Options.materialCompatLegacyMcPatcherEnabled);
        cache = new Cache(resourceManager, generation, next);
        if (next.ruleCountForTest() > 0) {
            LOGGER.info("[MaterialCompat] Random entity resolver compiled {} texture rules",
                next.ruleCountForTest());
        }
        return next;
    }

    private static RandomEntityIndex build(ResourceManager resourceManager, boolean legacyMcPatcher) {
        if (resourceManager == null) {
            return RandomEntityIndex.empty();
        }
        LinkedHashMap<Identifier, List<RandomEntityRule>> rules = new LinkedHashMap<>();
        collect(resourceManager, "optifine/random/entity", rules);
        if (legacyMcPatcher) {
            collect(resourceManager, "mcpatcher/random/entity", rules);
        }
        return new RandomEntityIndex(copyRules(rules));
    }

    private static Map<Identifier, List<RandomEntityRule>> copyRules(
        LinkedHashMap<Identifier, List<RandomEntityRule>> rules) {
        LinkedHashMap<Identifier, List<RandomEntityRule>> copy = new LinkedHashMap<>();
        for (Map.Entry<Identifier, List<RandomEntityRule>> entry : rules.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static void collect(ResourceManager resourceManager, String root,
        LinkedHashMap<Identifier, List<RandomEntityRule>> rules) {
        Map<Identifier, Resource> properties = resourceManager.findResources(root,
            id -> id.getPath().endsWith(".properties"));
        properties.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                if (totalRules(rules) < RULE_LIMIT) {
                    parse(entry.getKey(), entry.getValue(), root, resourceManager)
                        .ifPresent(parsed -> rules.computeIfAbsent(parsed.baseTexture(), ignored -> new ArrayList<>())
                            .addAll(parsed.rules()));
                }
            });
    }

    private static int totalRules(Map<Identifier, List<RandomEntityRule>> rules) {
        int count = 0;
        for (List<RandomEntityRule> value : rules.values()) {
            count += value.size();
        }
        return count;
    }

    private static Optional<ParsedRules> parse(Identifier propertyId, Resource resource, String root,
        ResourceManager resourceManager) {
        Properties props = new Properties();
        try (BufferedReader reader = resource.getReader()) {
            props.load(reader);
        } catch (IOException e) {
            return Optional.empty();
        }
        Identifier baseTexture = baseTextureId(propertyId, root);
        if (baseTexture == null) {
            return Optional.empty();
        }
        String baseAssetStem = propertyAssetStem(propertyId);
        ArrayList<RandomEntityRule> parsed = new ArrayList<>();
        for (Integer group : sortedTextureGroups(props)) {
            if (hasUnsupportedPredicate(props, group)) {
                continue;
            }
            List<Identifier> choices = parseChoices(props.getProperty("textures." + group),
                baseTexture, baseAssetStem, propertyId.getNamespace(), resourceManager);
            if (choices.isEmpty()) {
                continue;
            }
            int[] weights = parseWeights(props.getProperty("weights." + group), choices.size());
            BiomePredicate biomes = BiomePredicate.parse(props.getProperty("biomes." + group, ""));
            parsed.add(new RandomEntityRule(propertyId.toString() + "#" + group, choices, weights, biomes));
        }
        return parsed.isEmpty() ? Optional.empty() : Optional.of(new ParsedRules(baseTexture, parsed));
    }

    private static Identifier baseTextureId(Identifier propertyId, String root) {
        String path = propertyId.getPath();
        if (!path.startsWith(root + "/") || !path.endsWith(".properties")) {
            return null;
        }
        String relative = path.substring(root.length() + 1, path.length() - ".properties".length());
        return Identifier.tryParse(propertyId.getNamespace() + ":textures/entity/" + relative + ".png");
    }

    private static String propertyAssetStem(Identifier propertyId) {
        String path = propertyId.getPath();
        int dot = path.lastIndexOf('.');
        return dot > 0 ? path.substring(0, dot) : path;
    }

    private static List<Integer> sortedTextureGroups(Properties props) {
        ArrayList<Integer> groups = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("textures.")) {
                continue;
            }
            try {
                groups.add(Integer.parseInt(key.substring("textures.".length())));
            } catch (NumberFormatException ignored) {
            }
        }
        groups.sort(Integer::compareTo);
        return groups;
    }

    private static boolean hasUnsupportedPredicate(Properties props, int group) {
        String suffix = "." + group;
        for (String key : props.stringPropertyNames()) {
            if (!key.endsWith(suffix)) {
                continue;
            }
            String name = key.substring(0, key.length() - suffix.length()).toLowerCase(Locale.ROOT);
            if (!name.equals("textures") && !name.equals("weights") && !name.equals("biomes")) {
                return true;
            }
        }
        return false;
    }

    private static List<Identifier> parseChoices(String raw, Identifier baseTexture, String baseAssetStem,
        String namespace, ResourceManager resourceManager) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        ArrayList<Identifier> choices = new ArrayList<>();
        for (String token : raw.trim().split("[\\s,]+")) {
            addChoice(token, baseTexture, baseAssetStem, namespace, resourceManager, choices);
            if (choices.size() >= CHOICE_LIMIT) {
                break;
            }
        }
        return List.copyOf(choices);
    }

    private static void addChoice(String raw, Identifier baseTexture, String baseAssetStem, String namespace,
        ResourceManager resourceManager, List<Identifier> choices) {
        String token = raw == null ? "" : raw.trim();
        if (token.isEmpty()) {
            return;
        }
        int dash = token.indexOf('-');
        if (dash > 0 && dash < token.length() - 1
            && isPositiveInt(token.substring(0, dash))
            && isPositiveInt(token.substring(dash + 1))) {
            int start = Integer.parseInt(token.substring(0, dash));
            int end = Integer.parseInt(token.substring(dash + 1));
            int step = start <= end ? 1 : -1;
            for (int value = start; value != end + step; value += step) {
                addChoice(String.valueOf(value), baseTexture, baseAssetStem, namespace, resourceManager, choices);
                if (choices.size() >= CHOICE_LIMIT) {
                    return;
                }
            }
            return;
        }
        Identifier choice = choiceTextureId(token, baseTexture, baseAssetStem, namespace);
        if (choice == null) {
            return;
        }
        if (choice.equals(baseTexture) || resourceManager.getResource(choice).isPresent()) {
            choices.add(choice);
        }
    }

    private static Identifier choiceTextureId(String token, Identifier baseTexture, String baseAssetStem,
        String namespace) {
        if (isPositiveInt(token)) {
            int number = Integer.parseInt(token);
            if (number <= 1) {
                return baseTexture;
            }
            return Identifier.tryParse(namespace + ":" + baseAssetStem + number + ".png");
        }
        String path = token.replace('\\', '/');
        if (!path.endsWith(".png")) {
            path += ".png";
        }
        if (path.contains(":")) {
            return Identifier.tryParse(path);
        }
        int slash = baseAssetStem.lastIndexOf('/');
        String directory = slash < 0 ? "" : baseAssetStem.substring(0, slash + 1);
        return Identifier.tryParse(namespace + ":" + directory + path);
    }

    private static int[] parseWeights(String raw, int count) {
        int[] weights = new int[count];
        String[] tokens = raw == null || raw.isBlank() ? new String[0] : raw.trim().split("[\\s,]+");
        int explicitSum = 0;
        int explicitCount = 0;
        for (String token : tokens) {
            int weight = parsePositiveInt(token, -1);
            if (weight > 0) {
                explicitSum += weight;
                explicitCount++;
            }
        }
        int defaultWeight = explicitCount == 0
            ? 1
            : Math.max(1, Math.round((float) explicitSum / explicitCount));
        for (int i = 0; i < count; i++) {
            int weight = i < tokens.length ? parsePositiveInt(tokens[i], defaultWeight) : defaultWeight;
            weights[i] = Math.max(1, weight);
        }
        return weights;
    }

    private static int parsePositiveInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value > 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean isPositiveInt(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeBiomeToken(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (token.isEmpty()) {
            return "";
        }
        if (token.startsWith("biome/")) {
            token = token.substring("biome/".length());
        }
        int colon = token.indexOf(':');
        return colon > 0 ? token : "minecraft:" + token;
    }

    public static final class RandomEntityIndex {
        private static final RandomEntityIndex EMPTY = new RandomEntityIndex(Map.of());
        private final Map<Identifier, List<RandomEntityRule>> rules;

        private RandomEntityIndex(Map<Identifier, List<RandomEntityRule>> rules) {
            this.rules = rules;
        }

        public static RandomEntityIndex empty() {
            return EMPTY;
        }

        public int ruleCountForTest() {
            int count = 0;
            for (List<RandomEntityRule> value : rules.values()) {
                count += value.size();
            }
            return count;
        }

        public Identifier resolve(Identifier baseTexture, int entityHash, @Nullable String biomeId) {
            List<RandomEntityRule> textureRules = rules.get(baseTexture);
            if (textureRules == null || textureRules.isEmpty()) {
                return baseTexture;
            }
            String normalizedBiome = normalizeBiomeToken(biomeId);
            for (RandomEntityRule rule : textureRules) {
                if (rule.matches(normalizedBiome)) {
                    return rule.pick(baseTexture, entityHash);
                }
            }
            return baseTexture;
        }
    }

    private record ParsedRules(Identifier baseTexture, List<RandomEntityRule> rules) {
    }

    private record RandomEntityRule(String id, List<Identifier> choices, int[] weights,
                                    BiomePredicate biomes) {
        boolean matches(String biomeId) {
            return biomes.matches(biomeId);
        }

        Identifier pick(Identifier baseTexture, int entityHash) {
            if (choices.isEmpty()) {
                return baseTexture;
            }
            int total = 0;
            for (int weight : weights) {
                total += Math.max(1, weight);
            }
            int pick = (int) Math.floorMod(stableHash(id, baseTexture, entityHash), Math.max(1, total));
            int accum = 0;
            for (int i = 0; i < choices.size(); i++) {
                accum += Math.max(1, weights[i]);
                if (pick < accum) {
                    return choices.get(i);
                }
            }
            return choices.get(0);
        }

        private static long stableHash(String id, Identifier baseTexture, int entityHash) {
            long h = 0xcbf29ce484222325L;
            h = mix(h, id.hashCode());
            h = mix(h, baseTexture.hashCode());
            h = mix(h, entityHash);
            return h;
        }

        private static long mix(long h, int value) {
            h ^= value;
            return h * 0x100000001b3L;
        }
    }

    private record BiomePredicate(List<String> biomes) {
        static BiomePredicate parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return new BiomePredicate(List.of());
            }
            ArrayList<String> parsed = new ArrayList<>();
            for (String token : raw.trim().split("[\\s,]+")) {
                String normalized = normalizeBiomeToken(token);
                if (!normalized.isEmpty()) {
                    parsed.add(normalized);
                }
            }
            return new BiomePredicate(List.copyOf(parsed));
        }

        boolean matches(String biomeId) {
            return biomes.isEmpty() || biomes.contains(biomeId);
        }
    }

    private record Cache(ResourceManager resourceManager, long textureGeneration, RandomEntityIndex index) {
        static Cache empty() {
            return new Cache(null, -1L, RandomEntityIndex.empty());
        }
    }
}
