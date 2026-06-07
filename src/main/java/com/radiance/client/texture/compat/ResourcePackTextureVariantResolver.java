package com.radiance.client.texture.compat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.vertex.PBRVertexFormatElements;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourcePack;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackTextureVariantResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final Gson GSON = new Gson();
    private static final int RULE_LIMIT = 4096;
    private static final int OVERLAY_STACK_LIMIT = 16;
    private static final int CHOICE_EXPANSION_LIMIT = 512;
    // Indices are the OptiFine/Continuity 8-way CTM mask:
    // 128 64 32
    // 1   *  16
    // 2   4   8
    private static final int[] CTM_47_INDEX_MAP = new int[] {
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        16, 18, 16, 18, 6, 46, 6, 21, 16, 18, 16, 18, 28, 9, 28, 22,
        36, 17, 36, 17, 24, 19, 24, 43, 36, 17, 36, 17, 24, 19, 24, 43,
        37, 40, 37, 40, 30, 8, 30, 34, 37, 40, 37, 40, 25, 23, 25, 45,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        0, 3, 0, 3, 12, 5, 12, 15, 0, 3, 0, 3, 12, 5, 12, 15,
        1, 2, 1, 2, 4, 7, 4, 29, 1, 2, 1, 2, 13, 31, 13, 14,
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        16, 42, 16, 42, 6, 20, 6, 10, 16, 42, 16, 42, 28, 35, 28, 44,
        36, 39, 36, 39, 24, 41, 24, 27, 36, 39, 36, 39, 24, 41, 24, 27,
        37, 38, 37, 38, 30, 11, 30, 32, 37, 38, 37, 38, 25, 33, 25, 26,
    };
    private static volatile Cache cache = Cache.empty();

    private ResourcePackTextureVariantResolver() {
    }

    public record BlockOverlaySprite(int spriteId, int tintRgb, int alphaMode) {
    }

    public record ResolvedBlockSprite(int spriteId,
                                      boolean ruleMatched,
                                      int tintRgb,
                                      boolean tintOverride,
                                      int alphaMode) {
    }

    public record CompactCtmQuadrants(int[] spriteIds,
                                      int tintRgb,
                                      boolean tintOverride,
                                      int alphaMode) {
        public CompactCtmQuadrants {
            if (spriteIds == null || spriteIds.length != 4) {
                throw new IllegalArgumentException("Compact CTM quadrant sprite ids must have length 4");
            }
            spriteIds = spriteIds.clone();
        }

        @Override
        public int[] spriteIds() {
            return spriteIds.clone();
        }

        public int spriteId(int quadrant) {
            return spriteIds[quadrant];
        }
    }

    public record RepeatTextureBasis(Direction.Axis uAxis,
                                     int uSign,
                                     Direction.Axis vAxis,
                                     int vSign) {
        public RepeatTextureBasis {
            uSign = uSign < 0 ? -1 : 1;
            vSign = vSign < 0 ? -1 : 1;
        }

        public int u(BlockPos pos) {
            return coordinate(pos, uAxis, uSign);
        }

        public int v(BlockPos pos) {
            return coordinate(pos, vAxis, vSign);
        }

        private static int coordinate(BlockPos pos, Direction.Axis axis, int sign) {
            int value = switch (axis) {
                case X -> pos.getX();
                case Y -> pos.getY();
                case Z -> pos.getZ();
            };
            return sign < 0 ? -value : value;
        }
    }

    public static int resolveBlockSpriteId(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        return resolveBlockSprite(sourceSprite, world, state, pos, face).spriteId();
    }

    public static ResolvedBlockSprite resolveBlockSprite(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        return resolveBlockSprite(sourceSprite, world, state, pos, face, null);
    }

    public static ResolvedBlockSprite resolveBlockSprite(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face,
        @Nullable RepeatTextureBasis textureBasis) {
        Identifier source = spriteIdentifier(sourceSprite);
        int sourceSpriteId = TextureArrayBridge.resolveRenderableSpriteId(source);
        if (source == null || sourceSpriteId < 0 || !Options.materialCompatEnabled) {
            return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
        }
        ResolverIndex index = activeIndex(resourceManager);
        return index.resolveDetailed(source, sourceSpriteId, world, state, pos, face, textureBasis);
    }

    @Nullable
    public static CompactCtmQuadrants resolveCompactCtmQuadrants(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        Identifier source = spriteIdentifier(sourceSprite);
        int sourceSpriteId = TextureArrayBridge.resolveRenderableSpriteId(source);
        if (source == null || sourceSpriteId < 0 || !Options.materialCompatEnabled
            || !Options.materialCompatCtmEnabled) {
            return null;
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return null;
        }
        ResolverIndex index = activeIndex(resourceManager);
        return index.resolveCompactCtmQuadrants(source, sourceSpriteId, world, state, pos, face);
    }

    public static boolean hasBlockSpriteRule(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        Identifier source = spriteIdentifier(sourceSprite);
        return hasBlockSpriteRule(source, world, state, pos, face);
    }

    public static boolean hasBlockSpriteRule(@Nullable Identifier source,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        int sourceSpriteId = TextureArrayBridge.resolveRenderableSpriteId(source);
        if (source == null || sourceSpriteId < 0 || !Options.materialCompatEnabled) {
            return false;
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return false;
        }
        ResolverIndex index = activeIndex(resourceManager);
        return index.resolveDetailed(source, sourceSpriteId, world, state, pos, face).ruleMatched();
    }

    public static int resolveBlockOverlaySpriteId(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        int[] ids = resolveBlockOverlaySpriteIds(sourceSprite, world, state, pos, face);
        return ids.length == 0 ? -1 : ids[0];
    }

    public static int[] resolveBlockOverlaySpriteIds(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        BlockOverlaySprite[] overlays = resolveBlockOverlaySprites(sourceSprite, world, state, pos, face);
        int[] ids = new int[overlays.length];
        for (int i = 0; i < overlays.length; i++) {
            ids[i] = overlays[i].spriteId();
        }
        return ids;
    }

    public static BlockOverlaySprite[] resolveBlockOverlaySprites(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        return resolveBlockOverlaySprites(sourceSprite, world, state, pos, face, null);
    }

    public static BlockOverlaySprite[] resolveBlockOverlaySprites(@Nullable Sprite sourceSprite,
        @Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos,
        @Nullable Direction face,
        @Nullable RepeatTextureBasis textureBasis) {
        Identifier source = spriteIdentifier(sourceSprite);
        int sourceSpriteId = TextureArrayBridge.resolveRenderableSpriteId(source);
        if (source == null || sourceSpriteId < 0 || !Options.materialCompatEnabled
            || !Options.materialCompatOverlaysEnabled) {
            return new BlockOverlaySprite[0];
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return new BlockOverlaySprite[0];
        }
        ResolverIndex index = activeIndex(resourceManager);
        return index.resolveOverlay(source, sourceSpriteId, world, state, pos, face, textureBasis);
    }

    public static ResolverIndex buildForTest(ResourceManager resourceManager, boolean legacyMcPatcher) {
        return build(resourceManager, legacyMcPatcher);
    }

    public static String runtimeRuleRegistryJson(int limit) {
        ResourceManager resourceManager = currentResourceManager();
        ResolverIndex index = resourceManager == null ? ResolverIndex.empty() : activeIndex(resourceManager);
        return GSON.toJson(index.registryJson(limit, "runtime_resource_manager", resourceManager != null));
    }

    public static String registryJsonForTest(ResourceManager resourceManager, boolean legacyMcPatcher, int limit) {
        ResolverIndex index = build(resourceManager, legacyMcPatcher);
        return GSON.toJson(index.registryJson(limit, "test_resource_manager", resourceManager != null));
    }

    public static boolean blockPredicateMatchesForTest(String ruleToken, String blockId,
        Map<String, String> stateValues) {
        String actualBlockId = normalizeBlockIdToken(blockId);
        Map<String, String> normalizedStateValues = normalizeStateValues(stateValues);
        return parseBlockPredicate(ruleToken)
            .map(predicate -> predicate.matches(actualBlockId, normalizedStateValues))
            .orElse(false);
    }

    public static int weightForTest(String raw, int count, int index) {
        int[] weights = parseWeights(raw, count);
        return index >= 0 && index < weights.length ? weights[index] : -1;
    }

    private static ResourceManager currentResourceManager() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client == null ? null : client.getResourceManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ResolverIndex activeIndex(ResourceManager resourceManager) {
        long generation = TextureArrayBridge.getActiveTextureGeneration();
        Cache local = cache;
        if (local.resourceManager == resourceManager && local.textureGeneration == generation) {
            return local.index;
        }
        ResolverIndex next = build(resourceManager, Options.materialCompatLegacyMcPatcherEnabled);
        cache = new Cache(resourceManager, generation, next);
        if (!next.rules.isEmpty()) {
            LOGGER.info("[MaterialCompat] Variant resolver compiled {} block texture rules", next.rules.size());
        }
        return next;
    }

    private static ResolverIndex build(ResourceManager resourceManager, boolean legacyMcPatcher) {
        if (resourceManager == null) {
            return ResolverIndex.empty();
        }
        List<VariantRule> rules = new ArrayList<>();
        collectRules(resourceManager, "optifine/ctm", rules);
        if (legacyMcPatcher) {
            collectRules(resourceManager, "mcpatcher/ctm", rules);
        }
        return new ResolverIndex(List.copyOf(rules));
    }

    private static void collectRules(ResourceManager resourceManager, String root, List<VariantRule> rules) {
        Map<Identifier, Resource> properties = resourceManager.findResources(root,
            id -> id.getPath().endsWith(".properties"));
        properties.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> {
                if (rules.size() < RULE_LIMIT) {
                    parseRule(root, rules.size(), entry.getKey(), entry.getValue()).ifPresent(rules::add);
                }
            });
    }

    private static Optional<VariantRule> parseRule(String root, int precedenceOrdinal, Identifier propertyId,
        Resource resource) {
        Properties props = new Properties();
        try (BufferedReader reader = resource.getReader()) {
            props.load(reader);
        } catch (IOException e) {
            return Optional.empty();
        }

        String method = props.getProperty("method", "ctm").trim().toLowerCase(Locale.ROOT);
        RuleMethod ruleMethod = switch (method) {
            case "ctm" -> RuleMethod.CTM;
            case "ctm_compact" -> RuleMethod.CTM_COMPACT;
            case "fixed" -> RuleMethod.FIXED;
            case "random" -> RuleMethod.RANDOM;
            case "repeat" -> RuleMethod.REPEAT;
            case "horizontal" -> RuleMethod.HORIZONTAL;
            case "vertical" -> RuleMethod.VERTICAL;
            case "horizontal+vertical" -> RuleMethod.HORIZONTAL_THEN_VERTICAL;
            case "vertical+horizontal" -> RuleMethod.VERTICAL_THEN_HORIZONTAL;
            case "top" -> RuleMethod.TOP;
            case "overlay" -> RuleMethod.OVERLAY;
            case "overlay_ctm" -> RuleMethod.OVERLAY_CTM;
            case "overlay_random" -> RuleMethod.OVERLAY_RANDOM;
            case "overlay_repeat" -> RuleMethod.OVERLAY_REPEAT;
            case "overlay_fixed" -> RuleMethod.OVERLAY_FIXED;
            default -> null;
        };
        if (ruleMethod == null) {
            return Optional.empty();
        }
        if (parseBoolean(props.getProperty("optifineOnly", "false"))) {
            return Optional.empty();
        }

        int repeatWidth = parsePositiveInt(props.getProperty("width", "1"), 1);
        int repeatHeight = parsePositiveInt(props.getProperty("height", "1"), 1);
        String propertyAssetPath = ResourcePackCompatCtmTiles.assetPath(propertyId);
        List<String> tileAssetPaths = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(propertyAssetPath, props);
        List<TileChoice> choices = ruleMethod.overlayRule()
            ? tileChoices(propertyAssetPath, props.getProperty("tiles", ""), ruleMethod, repeatWidth, repeatHeight)
            : List.of();
        if (tileAssetPaths.isEmpty() && !ruleMethod.overlayRule()) {
            return Optional.empty();
        }
        if (choices.isEmpty() && ruleMethod.overlayRule()) {
            return Optional.empty();
        }

        List<Identifier> outputs = new ArrayList<>();
        for (String assetPath : tileAssetPaths) {
            Identifier output = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(assetPath));
            if (output != null) {
                outputs.add(output);
            }
        }
        if (outputs.isEmpty() && !ruleMethod.overlayRule()) {
            return Optional.empty();
        }

        List<String> matchTiles = matchTileTokens(propertyId, props);
        List<BlockPredicate> matchBlocks = matchBlockTokens(props);
        List<String> connectTiles = propertyTileTokens(props, "connectTiles");
        List<BlockPredicate> connectBlocks = blockTokens(props, "connectBlocks");
        EnumSet<Direction> faces = parseFaces(props.getProperty("faces", ""));
        ConnectMode connectMode = parseConnectMode(props.getProperty("connect", "block"));
        int[] weights = parseWeights(props.getProperty("weights", ""),
            ruleMethod.overlayRule() ? choices.size() : outputs.size());
        int randomLoops = parseNonNegativeInt(props.getProperty("randomLoops", "0"), 0);
        RandomSymmetry randomSymmetry = parseRandomSymmetry(props.getProperty("symmetry", ""));
        boolean linkedRandom = parseBoolean(props.getProperty("linked", "false"));
        RepeatOrientation repeatOrientation = parseRepeatOrientation(props.getProperty("orient", ""));
        int tintIndex = parseInt(props.getProperty("tintIndex", "-1"), -1);
        String tintBlock = normalizeBlockIdToken(props.getProperty("tintBlock", ""));
        int alphaMode = parseLayerAlphaMode(props.getProperty("layer", ""));
        boolean disableSolidCheck = parseBoolean(props.getProperty("disableSolidCheck", "false"));
        int[] ctmReplacementMap = parseCtmReplacementMap(props);
        BiomePredicate biomePredicate = parseBiomePredicate(props.getProperty("biomes", ""));
        HeightPredicate heightPredicate = parseHeightPredicate(props);
        return Optional.of(new VariantRule(propertyId.toString(), root, safePackId(resource), precedenceOrdinal,
            ruleMethod, matchTiles, matchBlocks,
            connectTiles, connectBlocks,
            faces, connectMode, List.copyOf(outputs), List.copyOf(choices), weights, randomLoops, randomSymmetry,
            linkedRandom,
            repeatOrientation, repeatWidth, repeatHeight, tintIndex, tintBlock, alphaMode,
            disableSolidCheck, ctmReplacementMap, biomePredicate, heightPredicate));
    }

    private static String safePackId(Resource resource) {
        if (resource == null) {
            return "unknown";
        }
        try {
            ResourcePack pack = resource.getPack();
            return pack == null ? "unknown" : pack.getId();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static List<TileChoice> tileChoices(String propertyAssetPath, String tilesValue, RuleMethod method,
        int repeatWidth, int repeatHeight) {
        String value = tilesValue == null ? "" : tilesValue.trim();
        if (value.isEmpty()) {
            value = inferredTilesForResolver(method, repeatWidth, repeatHeight);
        }
        if (propertyAssetPath == null || value.isBlank()) {
            return List.of();
        }
        ArrayList<TileChoice> choices = new ArrayList<>();
        for (String rawToken : value.split("[\\s,]+")) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (token.startsWith("<")) {
                choices.add(TileChoice.skipped());
                if (choices.size() >= CHOICE_EXPANSION_LIMIT) {
                    break;
                }
                continue;
            }
            int dash = token.indexOf('-');
            if (dash > 0 && dash < token.length() - 1
                && isPositiveInt(token.substring(0, dash))
                && isPositiveInt(token.substring(dash + 1))) {
                int start = Integer.parseInt(token.substring(0, dash));
                int end = Integer.parseInt(token.substring(dash + 1));
                int step = start <= end ? 1 : -1;
                for (int valueIndex = start; valueIndex != end + step; valueIndex += step) {
                    addTileChoice(choices, propertyAssetPath, String.valueOf(valueIndex));
                    if (choices.size() >= CHOICE_EXPANSION_LIMIT) {
                        break;
                    }
                }
                if (choices.size() >= CHOICE_EXPANSION_LIMIT) {
                    break;
                }
                continue;
            }
            addTileChoice(choices, propertyAssetPath, token);
            if (choices.size() >= CHOICE_EXPANSION_LIMIT) {
                break;
            }
        }
        return choices;
    }

    private static void addTileChoice(List<TileChoice> choices, String propertyAssetPath, String token) {
        String assetPath = ResourcePackCompatCtmTiles.resolveCtmTileAssetPath(propertyAssetPath, token);
        Identifier sprite = Identifier.tryParse(ResourcePackCompatCtmTiles.atlasSpriteIdentifier(assetPath));
        if (sprite != null) {
            choices.add(TileChoice.sprite(sprite));
        }
    }

    private static String inferredTilesForResolver(RuleMethod method, int repeatWidth, int repeatHeight) {
        return switch (method) {
            case CTM -> "0-46";
            case CTM_COMPACT -> "0-4";
            case HORIZONTAL, VERTICAL -> "0-3";
            case HORIZONTAL_THEN_VERTICAL, VERTICAL_THEN_HORIZONTAL -> "0-6";
            case TOP, FIXED -> "0";
            case REPEAT, OVERLAY_REPEAT -> inferredRepeatTiles(repeatWidth, repeatHeight);
            case OVERLAY, OVERLAY_CTM -> "0-16";
            case OVERLAY_FIXED -> "0";
            default -> "";
        };
    }

    private static String inferredRepeatTiles(int repeatWidth, int repeatHeight) {
        long rawCount = (long) Math.max(1, repeatWidth) * (long) Math.max(1, repeatHeight);
        int count = (int) Math.min(rawCount, CHOICE_EXPANSION_LIMIT);
        return "0-" + (count - 1);
    }

    private static List<String> matchTileTokens(Identifier propertyId, Properties props) {
        String matchTiles = props.getProperty("matchTiles", "").trim();
        if (!matchTiles.isEmpty()) {
            String[] tokens = matchTiles.split("[\\s,]+");
            ArrayList<String> normalized = new ArrayList<>();
            for (String token : tokens) {
                String cleaned = normalizeMatchToken(token);
                if (!cleaned.isEmpty()) {
                    normalized.add(cleaned);
                }
            }
            return normalized;
        }

        String path = propertyId.getPath();
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash) {
            dot = path.length();
        }
        String base = path.substring(slash + 1, dot);
        return base.isEmpty() ? List.of() : List.of(normalizeMatchToken(base));
    }

    private static List<BlockPredicate> matchBlockTokens(Properties props) {
        return blockTokens(props, "matchBlocks");
    }

    private static List<String> propertyTileTokens(Properties props, String key) {
        String value = props.getProperty(key, "").trim();
        if (value.isEmpty()) {
            return List.of();
        }
        String[] tokens = value.split("[\\s,]+");
        ArrayList<String> normalized = new ArrayList<>();
        for (String token : tokens) {
            String cleaned = normalizeMatchToken(token);
            if (!cleaned.isEmpty()) {
                normalized.add(cleaned);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<BlockPredicate> blockTokens(Properties props, String key) {
        String value = props.getProperty(key, "").trim();
        if (value.isEmpty()) {
            return List.of();
        }
        ArrayList<BlockPredicate> normalized = new ArrayList<>();
        for (String token : splitBlockTokens(value)) {
            parseBlockPredicate(token).ifPresent(normalized::add);
        }
        return List.copyOf(normalized);
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
        if (token.isEmpty()) {
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
        int stateColon = firstColon < 0 ? -1 : token.indexOf(':', firstColon + 1);
        if (stateExpression.isEmpty() && stateColon > 0 && token.substring(stateColon + 1).contains("=")) {
            stateExpression = token.substring(stateColon + 1);
            token = token.substring(0, stateColon);
        }
        String blockId = normalizeBlockIdToken(token);
        if (blockId.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BlockPredicate(blockId, parseStatePredicates(stateExpression)));
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

    private static String normalizeBlockIdToken(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (token.isEmpty()) {
            return "";
        }
        int bracket = token.indexOf('[');
        if (bracket >= 0) {
            token = token.substring(0, bracket);
        }
        int firstColon = token.indexOf(':');
        int stateColon = firstColon < 0 ? -1 : token.indexOf(':', firstColon + 1);
        if (stateColon > 0 && token.substring(stateColon + 1).contains("=")) {
            token = token.substring(0, stateColon);
        }
        int equals = token.indexOf('=');
        if (equals >= 0) {
            token = token.substring(0, equals);
        }
        if (token.startsWith("block/")) {
            token = token.substring("block/".length());
        }
        int colon = token.indexOf(':');
        if (colon > 0) {
            String namespace = token.substring(0, colon);
            String path = token.substring(colon + 1);
            if (path.startsWith("block/")) {
                path = path.substring("block/".length());
            }
            return namespace + ":" + path;
        }
        return "minecraft:" + token;
    }

    private static boolean matchesBlockPredicates(List<BlockPredicate> predicates, BlockState state) {
        for (BlockPredicate predicate : predicates) {
            if (predicate.matches(state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBlockPredicates(List<BlockPredicate> predicates, String actualBlockId,
        Map<String, String> stateValues) {
        for (BlockPredicate predicate : predicates) {
            if (predicate.matches(actualBlockId, stateValues)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> normalizeStateValues(Map<String, String> stateValues) {
        if (stateValues == null || stateValues.isEmpty()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> normalized = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stateValues.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            normalized.put(entry.getKey().trim().toLowerCase(Locale.ROOT),
                entry.getValue().trim().toLowerCase(Locale.ROOT));
        }
        return Map.copyOf(normalized);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValueName(Property property, Comparable value) {
        return property.name(value).toLowerCase(Locale.ROOT);
    }

    private static Direction.Axis stateAxis(@Nullable BlockState state) {
        if (state == null) {
            return Direction.Axis.Y;
        }
        for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
            Property<?> property = entry.getKey();
            if (!"axis".equals(property.getName())) {
                continue;
            }
            String value = propertyValueName(property, entry.getValue());
            return switch (value) {
                case "x" -> Direction.Axis.X;
                case "z" -> Direction.Axis.Z;
                default -> Direction.Axis.Y;
            };
        }
        return Direction.Axis.Y;
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

    private record HeightPredicate(List<HeightRange> ranges) {
        static HeightPredicate empty() {
            return new HeightPredicate(List.of());
        }

        static HeightPredicate of(int min, int max) {
            return new HeightPredicate(List.of(HeightRange.of(min, max)));
        }

        static HeightPredicate parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return empty();
            }
            ArrayList<HeightRange> parsed = new ArrayList<>();
            for (String token : raw.trim().split("[\\s,]+")) {
                Optional<HeightRange> range = HeightRange.parse(token);
                range.ifPresent(parsed::add);
            }
            return parsed.isEmpty() ? empty() : new HeightPredicate(List.copyOf(parsed));
        }

        boolean matches(@Nullable BlockPos pos) {
            if (ranges.isEmpty()) {
                return true;
            }
            if (pos == null) {
                return false;
            }
            int y = pos.getY();
            for (HeightRange range : ranges) {
                if (range.matches(y)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record HeightRange(int min, int max) {
        static HeightRange of(int min, int max) {
            return min <= max ? new HeightRange(min, max) : new HeightRange(max, min);
        }

        static Optional<HeightRange> parse(String raw) {
            String token = raw == null ? "" : raw.trim();
            if (token.isEmpty()) {
                return Optional.empty();
            }
            int dash = token.indexOf('-', 1);
            try {
                if (dash > 0 && dash < token.length() - 1) {
                    return Optional.of(of(Integer.parseInt(token.substring(0, dash)),
                        Integer.parseInt(token.substring(dash + 1))));
                }
                int value = Integer.parseInt(token);
                return Optional.of(of(value, value));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }

        boolean matches(int y) {
            return y >= min && y <= max;
        }
    }

    private record BiomePredicate(boolean inverted, List<String> biomes) {
        static BiomePredicate any() {
            return new BiomePredicate(false, List.of());
        }

        boolean matches(@Nullable BlockRenderView world, @Nullable BlockPos pos, @Nullable String biomeId) {
            if (biomes.isEmpty()) {
                return true;
            }
            String actual = normalizeBiomeToken(biomeId);
            if (actual.isEmpty() && world instanceof WorldView worldView && pos != null) {
                try {
                    actual = worldView.getBiome(pos).getKey()
                        .map(key -> normalizeBiomeToken(key.getValue().toString()))
                        .orElse("");
                } catch (Throwable ignored) {
                    actual = "";
                }
            }
            if (actual.isEmpty()) {
                return false;
            }
            boolean listed = biomes.contains(actual);
            return inverted ? !listed : listed;
        }
    }

    private static String normalizeMatchToken(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (token.endsWith(".png")) {
            token = token.substring(0, token.length() - 4);
        }
        if (token.startsWith("textures/")) {
            token = token.substring("textures/".length());
        }
        int colon = token.indexOf(':');
        if (colon > 0) {
            token = token.substring(colon + 1);
            if (token.startsWith("textures/")) {
                token = token.substring("textures/".length());
            }
        }
        return token;
    }

    private static EnumSet<Direction> parseFaces(String raw) {
        EnumSet<Direction> faces = EnumSet.allOf(Direction.class);
        if (raw == null || raw.isBlank()) {
            return faces;
        }
        faces.clear();
        for (String piece : raw.trim().toLowerCase(Locale.ROOT).split("[\\s,]+")) {
            switch (piece) {
                case "all" -> faces.addAll(EnumSet.allOf(Direction.class));
                case "sides" -> {
                    faces.add(Direction.NORTH);
                    faces.add(Direction.SOUTH);
                    faces.add(Direction.EAST);
                    faces.add(Direction.WEST);
                }
                case "top", "up" -> faces.add(Direction.UP);
                case "bottom", "down" -> faces.add(Direction.DOWN);
                case "north" -> faces.add(Direction.NORTH);
                case "south" -> faces.add(Direction.SOUTH);
                case "east" -> faces.add(Direction.EAST);
                case "west" -> faces.add(Direction.WEST);
                default -> {
                }
            }
        }
        if (faces.isEmpty()) {
            faces.addAll(EnumSet.allOf(Direction.class));
        }
        return faces;
    }

    private static ConnectMode parseConnectMode(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "state" -> ConnectMode.STATE;
            case "tile" -> ConnectMode.TILE_AS_BLOCK;
            default -> ConnectMode.BLOCK;
        };
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

    private static int parseNonNegativeInt(String raw, int fallback) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= 0 ? value : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static boolean parseBoolean(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("on");
    }

    private static RepeatOrientation parseRepeatOrientation(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "texture" -> RepeatOrientation.TEXTURE;
            case "state_axis" -> RepeatOrientation.STATE_AXIS;
            default -> RepeatOrientation.NONE;
        };
    }

    private static int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static int parseLayerAlphaMode(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "solid" -> PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE;
            case "cutout", "cutout_mipped" -> PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT;
            case "translucent", "transparency" -> PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT;
            default -> -1;
        };
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

    private static RandomSymmetry parseRandomSymmetry(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "opposite" -> RandomSymmetry.OPPOSITE;
            case "all" -> RandomSymmetry.ALL;
            default -> RandomSymmetry.NONE;
        };
    }

    private static int[] parseCtmReplacementMap(Properties props) {
        int[] replacements = new int[47];
        for (int i = 0; i < replacements.length; i++) {
            replacements[i] = -1;
        }
        for (String key : props.stringPropertyNames()) {
            if (!key.startsWith("ctm.")) {
                continue;
            }
            try {
                int ctmIndex = Integer.parseInt(key.substring(4));
                int outputIndex = Integer.parseInt(props.getProperty(key, "").trim());
                if (ctmIndex >= 0 && ctmIndex < replacements.length && outputIndex >= 0) {
                    replacements[ctmIndex] = outputIndex;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return replacements;
    }

    private static BiomePredicate parseBiomePredicate(String raw) {
        if (raw == null || raw.isBlank()) {
            return BiomePredicate.any();
        }
        boolean inverted = false;
        ArrayList<String> biomes = new ArrayList<>();
        for (String token : raw.trim().split("[\\s,]+")) {
            if (token.startsWith("!")) {
                inverted = true;
                token = token.substring(1);
            }
            String normalized = normalizeBiomeToken(token);
            if (!normalized.isEmpty()) {
                biomes.add(normalized);
            }
        }
        return biomes.isEmpty() ? BiomePredicate.any() : new BiomePredicate(inverted, List.copyOf(biomes));
    }

    private static HeightPredicate parseHeightPredicate(Properties props) {
        String heights = props.getProperty("heights", "").trim();
        if (!heights.isEmpty()) {
            return HeightPredicate.parse(heights);
        }
        String min = props.getProperty("minHeight", "").trim();
        String max = props.getProperty("maxHeight", "").trim();
        if (min.isEmpty() && max.isEmpty()) {
            return HeightPredicate.empty();
        }
        int minValue = min.isEmpty() ? Integer.MIN_VALUE : parseInt(min, Integer.MIN_VALUE);
        int maxValue = max.isEmpty() ? Integer.MAX_VALUE : parseInt(max, Integer.MAX_VALUE);
        return HeightPredicate.of(minValue, maxValue);
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
        if (colon > 0) {
            return token;
        }
        return "minecraft:" + token;
    }

    private static Identifier spriteIdentifier(@Nullable Sprite sprite) {
        return sprite == null || sprite.getContents() == null ? null : sprite.getContents().getId();
    }

    private static JsonObject optionsJson() {
        JsonObject json = new JsonObject();
        json.addProperty("materialCompat", Options.materialCompatEnabled);
        json.addProperty("ctm", Options.materialCompatCtmEnabled);
        json.addProperty("random", Options.materialCompatRandomEnabled);
        json.addProperty("overlays", Options.materialCompatOverlaysEnabled);
        json.addProperty("legacyMcPatcher", Options.materialCompatLegacyMcPatcherEnabled);
        return json;
    }

    private static JsonObject ruleJson(VariantRule rule, int ordinal) {
        JsonObject json = new JsonObject();
        json.addProperty("ordinal", ordinal);
        json.addProperty("id", rule.id());
        json.addProperty("root", rule.root());
        json.addProperty("sourcePack", rule.sourcePack());
        json.addProperty("precedenceOrdinal", rule.precedenceOrdinal());
        json.addProperty("method", methodName(rule.method()));
        json.addProperty("overlayRule", rule.method().overlayRule());
        json.addProperty("enabledByOptions", Options.materialCompatEnabled && rule.enabledByOptions());
        json.add("matchTiles", stringArray(rule.matchTiles()));
        json.add("matchBlocks", blockPredicateArray(rule.matchBlocks()));
        json.add("connectTiles", stringArray(rule.connectTiles()));
        json.add("connectBlocks", blockPredicateArray(rule.connectBlocks()));
        json.add("faces", facesArray(rule.faces()));
        json.addProperty("connectMode", lowerName(rule.connectMode()));
        json.addProperty("outputCount", rule.outputs().size());
        json.add("outputs", outputArray(rule.outputs()));
        json.addProperty("choiceCount", rule.choices().size());
        json.add("choices", choiceArray(rule.choices()));
        json.add("weights", intArray(rule.weights()));
        json.addProperty("randomLoops", rule.randomLoops());
        json.addProperty("randomSymmetry", lowerName(rule.randomSymmetry()));
        json.addProperty("linkedRandom", rule.linkedRandom());
        json.addProperty("repeatOrientation", lowerName(rule.repeatOrientation()));
        json.addProperty("repeatWidth", rule.repeatWidth());
        json.addProperty("repeatHeight", rule.repeatHeight());
        json.addProperty("tintIndex", rule.tintIndex());
        json.addProperty("tintBlock", rule.tintBlock());
        json.addProperty("alphaMode", rule.alphaMode());
        json.addProperty("disableSolidCheck", rule.disableSolidCheck());
        json.addProperty("ctmReplacementOverrides", ctmReplacementOverrideCount(rule.ctmReplacementMap()));
        json.add("biomes", biomePredicateJson(rule.biomePredicate()));
        json.add("heights", heightPredicateJson(rule.heightPredicate()));
        return json;
    }

    private static JsonArray outputArray(List<Identifier> outputs) {
        JsonArray array = new JsonArray();
        for (Identifier output : outputs) {
            array.add(spriteBindingJson(output, false));
        }
        return array;
    }

    private static JsonArray choiceArray(List<TileChoice> choices) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < choices.size(); i++) {
            TileChoice choice = choices.get(i);
            JsonObject json = new JsonObject();
            json.addProperty("index", i);
            json.addProperty("skip", choice.skip());
            if (choice.sprite() != null) {
                JsonObject binding = spriteBindingJson(choice.sprite(), false);
                for (String key : binding.keySet()) {
                    json.add(key, binding.get(key));
                }
            } else {
                json.addProperty("sprite", "");
                json.addProperty("spriteId", -1);
                json.addProperty("materialSetId", -1);
                addMaterialSetBinding(json, -1);
                json.addProperty("resolved", false);
            }
            array.add(json);
        }
        return array;
    }

    private static JsonObject spriteBindingJson(Identifier sprite, boolean skipped) {
        JsonObject json = new JsonObject();
        int spriteId = skipped || sprite == null ? -1 : TextureArrayBridge.resolveSpriteId(sprite.toString());
        json.addProperty("sprite", sprite == null ? "" : sprite.toString());
        json.addProperty("spriteId", spriteId);
        json.addProperty("materialSetId", spriteId);
        addMaterialSetBinding(json, spriteId);
        json.addProperty("resolved", spriteId >= 0);
        return json;
    }

    private static void addMaterialSetBinding(JsonObject json, int spriteId) {
        json.addProperty("materialSetBindingPolicy", AutoPbrTextureCatalog.MATERIAL_SET_BINDING_POLICY);
        json.addProperty("shaderLookupKey", AutoPbrTextureCatalog.MATERIAL_SET_SHADER_LOOKUP_KEY);
        json.addProperty("nativeMaterialSetTablePresent",
            AutoPbrTextureCatalog.MATERIAL_SET_NATIVE_TABLE_PRESENT);
        json.addProperty("materialSetAliasesResolvedSprite", spriteId >= 0);
    }

    private static JsonArray blockPredicateArray(List<BlockPredicate> predicates) {
        JsonArray array = new JsonArray();
        for (BlockPredicate predicate : predicates) {
            JsonObject json = new JsonObject();
            json.addProperty("blockId", predicate.blockId());
            JsonArray states = new JsonArray();
            for (StatePredicate state : predicate.states()) {
                JsonObject stateJson = new JsonObject();
                stateJson.addProperty("name", state.name());
                JsonArray values = new JsonArray();
                for (String value : new TreeSet<>(state.values())) {
                    values.add(value);
                }
                stateJson.add("values", values);
                states.add(stateJson);
            }
            json.add("states", states);
            array.add(json);
        }
        return array;
    }

    private static JsonArray facesArray(EnumSet<Direction> faces) {
        JsonArray array = new JsonArray();
        for (Direction face : Direction.values()) {
            if (faces.contains(face)) {
                array.add(face.getName());
            }
        }
        return array;
    }

    private static JsonObject biomePredicateJson(BiomePredicate predicate) {
        JsonObject json = new JsonObject();
        json.addProperty("inverted", predicate.inverted());
        json.add("values", stringArray(predicate.biomes()));
        return json;
    }

    private static JsonObject heightPredicateJson(HeightPredicate predicate) {
        JsonObject json = new JsonObject();
        json.addProperty("unrestricted", predicate.ranges().isEmpty());
        JsonArray ranges = new JsonArray();
        for (HeightRange range : predicate.ranges()) {
            JsonObject rangeJson = new JsonObject();
            rangeJson.addProperty("min", range.min());
            rangeJson.addProperty("max", range.max());
            ranges.add(rangeJson);
        }
        json.add("ranges", ranges);
        return json;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray intArray(int[] values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }

    private static int ctmReplacementOverrideCount(int[] replacements) {
        int count = 0;
        for (int replacement : replacements) {
            if (replacement >= 0) {
                count++;
            }
        }
        return count;
    }

    private static int intProperty(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsInt() : 0;
    }

    private static JsonObject precedencePolicyJson() {
        JsonObject json = new JsonObject();
        json.addProperty("samePath", "resource_manager_effective_resource");
        json.addProperty("rootOrder", "optifine/ctm before optional mcpatcher/ctm");
        json.addProperty("ruleOrder", "property_id_ascending_within_root");
        json.addProperty("facesPredicateSpace", "axis_aware_local_block_faces");
        json.addProperty("nonOverlayResolution", "first_enabled_matching_rule");
        json.addProperty("overlayResolution", "stack_enabled_matching_overlay_groups_in_precedence_order");
        json.addProperty("overlayStackLimit", OVERLAY_STACK_LIMIT);
        return json;
    }

    private static String methodName(RuleMethod method) {
        return switch (method) {
            case CTM -> "ctm";
            case CTM_COMPACT -> "ctm_compact";
            case FIXED -> "fixed";
            case RANDOM -> "random";
            case REPEAT -> "repeat";
            case HORIZONTAL -> "horizontal";
            case VERTICAL -> "vertical";
            case HORIZONTAL_THEN_VERTICAL -> "horizontal+vertical";
            case VERTICAL_THEN_HORIZONTAL -> "vertical+horizontal";
            case TOP -> "top";
            case OVERLAY -> "overlay";
            case OVERLAY_CTM -> "overlay_ctm";
            case OVERLAY_RANDOM -> "overlay_random";
            case OVERLAY_REPEAT -> "overlay_repeat";
            case OVERLAY_FIXED -> "overlay_fixed";
        };
    }

    private static String lowerName(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    public static final class ResolverIndex {
        private static final ResolverIndex EMPTY = new ResolverIndex(List.of());
        private final List<VariantRule> rules;

        private ResolverIndex(List<VariantRule> rules) {
            this.rules = rules;
        }

        public static ResolverIndex empty() {
            return EMPTY;
        }

        public int ruleCountForTest() {
            return rules.size();
        }

        JsonObject registryJson(int limit, String source, boolean resourceManagerAvailable) {
            int max = limit <= 0 ? rules.size() : Math.min(limit, rules.size());
            JsonObject json = new JsonObject();
            json.addProperty("schema", "radser_runtime_variant_rule_registry_v1");
            json.addProperty("source", source);
            json.addProperty("resourceManagerAvailable", resourceManagerAvailable);
            json.addProperty("textureGeneration", TextureArrayBridge.getActiveTextureGeneration());
            json.addProperty("ruleLimit", RULE_LIMIT);
            json.addProperty("ruleCount", rules.size());
            json.addProperty("reported", max);
            json.add("options", optionsJson());
            json.add("precedencePolicy", precedencePolicyJson());
            JsonObject methodCounts = new JsonObject();
            JsonArray ruleArray = new JsonArray();
            for (int i = 0; i < rules.size(); i++) {
                VariantRule rule = rules.get(i);
                String method = methodName(rule.method());
                methodCounts.addProperty(method, intProperty(methodCounts, method) + 1);
                if (i < max) {
                    ruleArray.add(ruleJson(rule, i));
                }
            }
            json.add("methodCounts", methodCounts);
            json.add("rules", ruleArray);
            return json;
        }

        public int resolveForTest(Identifier source, int sourceSpriteId, @Nullable BlockPos pos,
            @Nullable Direction face) {
            return resolve(source, sourceSpriteId, null, null, pos, face);
        }

        public int resolveForTest(Identifier source, int sourceSpriteId, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face) {
            return resolve(source, sourceSpriteId, null, state, pos, face);
        }

        public int resolveForTest(Identifier source, int sourceSpriteId, @Nullable Direction.Axis stateAxis,
            @Nullable BlockPos pos, @Nullable Direction face) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return false;
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return false;
                }
            };
            return resolveDetailed(source, sourceSpriteId, null, null, pos, face, connector, null, stateAxis)
                .spriteId();
        }

        public int resolveForTest(Identifier source, int sourceSpriteId, @Nullable RepeatTextureBasis textureBasis,
            @Nullable BlockPos pos, @Nullable Direction face) {
            return resolveDetailed(source, sourceSpriteId, null, null, pos, face, textureBasis).spriteId();
        }

        public int resolveForTest(Identifier source, int sourceSpriteId, @Nullable BlockPos pos,
            @Nullable Direction face, @Nullable String biomeId) {
            return resolveDetailed(source, sourceSpriteId, null, null, pos, face, biomeId).spriteId();
        }

        public ResolvedBlockSprite resolveDetailedForTest(Identifier source, int sourceSpriteId,
            @Nullable BlockPos pos, @Nullable Direction face) {
            return resolveDetailed(source, sourceSpriteId, null, null, pos, face);
        }

        public int resolveOverlayForTest(Identifier source, int sourceSpriteId, @Nullable BlockPos pos,
            @Nullable Direction face) {
            BlockOverlaySprite[] overlays = resolveOverlay(source, sourceSpriteId, null, null, pos, face);
            return overlays.length == 0 ? -1 : overlays[0].spriteId();
        }

        public BlockOverlaySprite[] resolveOverlayDetailsForTest(Identifier source, int sourceSpriteId,
            @Nullable BlockPos pos, @Nullable Direction face) {
            return resolveOverlay(source, sourceSpriteId, null, null, pos, face);
        }

        public boolean overlaySolidOccluderBlocksForTest(Identifier source, @Nullable Direction face,
            boolean opaqueFullCube) {
            for (VariantRule rule : rules) {
                if (rule.method().overlayRule()
                    && rule.matches(source, null, null, null, face, null)) {
                    return rule.overlayOccluderBlocksForTest(opaqueFullCube);
                }
            }
            return false;
        }

        public int[] resolveOverlaysWithConnectionsForTest(Identifier source, int sourceSpriteId,
            @Nullable Direction face, Set<Direction> connectedDirections,
            Set<String> connectedDiagonalDirections) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return connectedDirections != null && connectedDirections.contains(direction);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return connectedDiagonalDirections != null
                        && connectedDiagonalDirections.contains(diagonalKeyForTest(first, second));
                }
            };
            BlockOverlaySprite[] overlays =
                resolveOverlay(source, sourceSpriteId, null, null, null, face, connector);
            int[] ids = new int[overlays.length];
            for (int i = 0; i < overlays.length; i++) {
                ids[i] = overlays[i].spriteId();
            }
            return ids;
        }

        public int resolveWithConnectionsForTest(Identifier source, int sourceSpriteId,
            @Nullable BlockState state, @Nullable Direction face, Set<Direction> connectedDirections) {
            NeighborConnector connector = (direction, mode) ->
                connectedDirections != null && connectedDirections.contains(direction);
            return resolve(source, sourceSpriteId, null, state, null, face, connector);
        }

        public int resolveWithConnectionsForTest(Identifier source, int sourceSpriteId,
            @Nullable BlockState state, @Nullable Direction face, Set<Direction> connectedDirections,
            Set<String> connectedDiagonalDirections) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return connectedDirections != null && connectedDirections.contains(direction);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return connectedDiagonalDirections != null
                        && connectedDiagonalDirections.contains(diagonalKeyForTest(first, second));
                }
            };
            return resolve(source, sourceSpriteId, null, state, null, face, connector);
        }

        @Nullable
        public CompactCtmQuadrants resolveCompactCtmQuadrantsWithConnectionsForTest(Identifier source,
            int sourceSpriteId, @Nullable BlockState state, @Nullable Direction face,
            Set<Direction> connectedDirections, Set<String> connectedDiagonalDirections) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return connectedDirections != null && connectedDirections.contains(direction);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return connectedDiagonalDirections != null
                        && connectedDiagonalDirections.contains(diagonalKeyForTest(first, second));
                }
            };
            return resolveCompactCtmQuadrants(source, sourceSpriteId, null, state, null, face, connector, null);
        }

        public int resolveWithNeighborStatesForTest(Identifier source, int sourceSpriteId,
            @Nullable BlockState state, @Nullable Direction face, Map<Direction, BlockState> neighborStates) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return sourceSpriteId;
            }
            Map<Direction, BlockState> neighbors = neighborStates == null ? Map.of() : neighborStates;
            for (VariantRule rule : rules) {
                if (rule.method().overlayRule() || !rule.enabledByOptions()
                    || !rule.matches(source, null, state, null, face, null)) {
                    continue;
                }
                ResolvedBlockSprite resolved = rule.resolveSprite(source, sourceSpriteId, null, state,
                    null, face, rule.neighborConnectorForTest(source, state, face, neighbors), null, null);
                if (resolved.spriteId() >= 0) {
                    return resolved.spriteId();
                }
            }
            return sourceSpriteId;
        }

        public int resolveWithNeighborBlockIdsForTest(Identifier source, int sourceSpriteId,
            String stateBlockId, @Nullable Direction face, Map<Direction, String> neighborBlockIds) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return sourceSpriteId;
            }
            Map<Direction, String> neighbors = neighborBlockIds == null ? Map.of() : neighborBlockIds;
            for (VariantRule rule : rules) {
                if (rule.method().overlayRule() || !rule.enabledByOptions()
                    || !rule.matches(source, null, null, null, face, null)) {
                    continue;
                }
                ResolvedBlockSprite resolved = rule.resolveSprite(source, sourceSpriteId, null, null,
                    null, face, rule.neighborConnectorForBlockIdsForTest(source, stateBlockId, face, neighbors),
                    null, null);
                if (resolved.spriteId() >= 0) {
                    return resolved.spriteId();
                }
            }
            return sourceSpriteId;
        }

        public static String diagonalKeyForTest(Direction first, Direction second) {
            return first.name() + "+" + second.name();
        }

        int resolve(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face).spriteId();
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face,
                (RepeatTextureBasis) null);
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, @Nullable RepeatTextureBasis textureBasis) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, direction, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, first, second, mode);
                }
            };
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector, null, textureBasis,
                null);
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, @Nullable String biomeId) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, direction, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, first, second, mode);
                }
            };
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector, biomeId);
        }

        int resolve(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector).spriteId();
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector, null);
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector,
            @Nullable String biomeId) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector, biomeId, null, null);
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector,
            @Nullable String biomeId, @Nullable Direction.Axis repeatAxisOverride) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector, biomeId, null,
                repeatAxisOverride);
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector,
            @Nullable String biomeId, @Nullable RepeatTextureBasis textureBasis,
            @Nullable Direction.Axis repeatAxisOverride) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
            }
            for (VariantRule rule : rules) {
                if (rule.method().overlayRule() || !rule.enabledByOptions()
                    || !rule.matches(source, world, state, pos, face, biomeId, repeatAxisOverride)) {
                    continue;
                }
                ResolvedBlockSprite resolved =
                    rule.resolveSprite(source, sourceSpriteId, world, state, pos, face,
                        rule.neighborConnector(source, world, state, pos, face, connector),
                        textureBasis, repeatAxisOverride);
                if (resolved.spriteId() >= 0) {
                    return resolved;
                }
            }
            return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
        }

        @Nullable
        CompactCtmQuadrants resolveCompactCtmQuadrants(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face) {
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, direction, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, first, second, mode);
                }
            };
            return resolveCompactCtmQuadrants(source, sourceSpriteId, world, state, pos, face, connector, null);
        }

        @Nullable
        CompactCtmQuadrants resolveCompactCtmQuadrants(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector,
            @Nullable String biomeId) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return null;
            }
            for (VariantRule rule : rules) {
                if (rule.method() != RuleMethod.CTM_COMPACT || !rule.enabledByOptions()
                    || !rule.matches(source, world, state, pos, face, biomeId)) {
                    continue;
                }
                CompactCtmQuadrants quadrants =
                    rule.resolveCompactCtmQuadrants(world, state, pos, face,
                        rule.neighborConnector(source, world, state, pos, face, connector));
                if (quadrants != null) {
                    return quadrants;
                }
            }
            return null;
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face) {
            return resolveOverlay(source, sourceSpriteId, world, state, pos, face,
                (RepeatTextureBasis) null);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            @Nullable RepeatTextureBasis textureBasis) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return new BlockOverlaySprite[0];
            }
            NeighborConnector connector = new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, direction, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return ResolverIndex.this.connects(world, state, pos, first, second, mode);
                }
            };
            return resolveOverlay(source, sourceSpriteId, world, state, pos, face, connector, textureBasis);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            return resolveOverlay(source, sourceSpriteId, world, state, pos, face, connector,
                (RepeatTextureBasis) null);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector, @Nullable RepeatTextureBasis textureBasis) {
            return resolveOverlay(source, sourceSpriteId, world, state, pos, face, connector, null, textureBasis);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector, @Nullable String biomeId) {
            return resolveOverlay(source, sourceSpriteId, world, state, pos, face, connector, biomeId, null);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector, @Nullable String biomeId, @Nullable RepeatTextureBasis textureBasis) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return new BlockOverlaySprite[0];
            }
            ArrayList<BlockOverlaySprite> stacked = new ArrayList<>();
            for (VariantRule rule : rules) {
                if (!rule.method().overlayRule() || !rule.enabledByOptions()
                    || !rule.matches(source, world, state, pos, face, biomeId)) {
                    continue;
                }
                BlockOverlaySprite[] resolved =
                    rule.resolveOverlaySpriteIds(source, world, state, pos, face, connector, textureBasis);
                if (resolved.length > 0) {
                    for (BlockOverlaySprite overlay : resolved) {
                        if (stacked.size() >= OVERLAY_STACK_LIMIT) {
                            return stacked.toArray(BlockOverlaySprite[]::new);
                        }
                        stacked.add(overlay);
                    }
                }
            }
            return stacked.toArray(BlockOverlaySprite[]::new);
        }

        private boolean connects(@Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, Direction direction, ConnectMode mode) {
            if (world == null || state == null || pos == null || direction == null) {
                return false;
            }
            BlockState neighbor = world.getBlockState(pos.offset(direction));
            if (neighbor == null) {
                return false;
            }
            return switch (mode) {
                case STATE -> state.equals(neighbor);
                case BLOCK, TILE_AS_BLOCK -> state.getBlock() == neighbor.getBlock();
            };
        }

        private boolean connects(@Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, Direction first, Direction second, ConnectMode mode) {
            if (world == null || state == null || pos == null || first == null || second == null) {
                return false;
            }
            BlockState neighbor = world.getBlockState(pos.offset(first).offset(second));
            if (neighbor == null) {
                return false;
            }
            return switch (mode) {
                case STATE -> state.equals(neighbor);
                case BLOCK, TILE_AS_BLOCK -> state.getBlock() == neighbor.getBlock();
            };
        }
    }

    private record VariantRule(String id,
                               String root,
                               String sourcePack,
                               int precedenceOrdinal,
                               RuleMethod method,
                               List<String> matchTiles,
                               List<BlockPredicate> matchBlocks,
                               List<String> connectTiles,
                               List<BlockPredicate> connectBlocks,
                               EnumSet<Direction> faces,
                               ConnectMode connectMode,
                               List<Identifier> outputs,
                               List<TileChoice> choices,
                               int[] weights,
                               int randomLoops,
                               RandomSymmetry randomSymmetry,
                               boolean linkedRandom,
                               RepeatOrientation repeatOrientation,
                               int repeatWidth,
                               int repeatHeight,
                               int tintIndex,
                               String tintBlock,
                               int alphaMode,
                               boolean disableSolidCheck,
                               int[] ctmReplacementMap,
                               BiomePredicate biomePredicate,
                               HeightPredicate heightPredicate) {
        boolean enabledByOptions() {
            return switch (method) {
                case CTM, CTM_COMPACT, FIXED -> Options.materialCompatCtmEnabled;
                case RANDOM -> Options.materialCompatRandomEnabled;
                case REPEAT, HORIZONTAL, VERTICAL, HORIZONTAL_THEN_VERTICAL, VERTICAL_THEN_HORIZONTAL, TOP ->
                    Options.materialCompatCtmEnabled;
                case OVERLAY, OVERLAY_CTM, OVERLAY_RANDOM, OVERLAY_REPEAT, OVERLAY_FIXED ->
                    Options.materialCompatOverlaysEnabled;
            };
        }

        boolean matches(Identifier source, @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, @Nullable String biomeId) {
            return matches(source, world, state, pos, face, biomeId, null);
        }

        boolean matches(Identifier source, @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, @Nullable String biomeId,
            @Nullable Direction.Axis stateAxisOverride) {
            if (face != null && !faceApplies(face, state, stateAxisOverride)) {
                return false;
            }
            if (!heightPredicate.matches(pos)) {
                return false;
            }
            if (!biomePredicate.matches(world, pos, biomeId)) {
                return false;
            }
            if (!matchBlocks.isEmpty() && state != null) {
                if (matchesBlockPredicates(matchBlocks, state)) {
                    return true;
                }
            }
            String sourcePath = normalizeMatchToken(source.getPath());
            for (String match : matchTiles) {
                if (sourcePath.equals(match)
                    || sourcePath.equals("block/" + match)
                    || sourcePath.equals("item/" + match)
                    || sourcePath.endsWith("/" + match)) {
                    return true;
                }
            }
            return false;
        }

        private boolean faceApplies(Direction face, @Nullable BlockState state,
            @Nullable Direction.Axis stateAxisOverride) {
            Direction.Axis axis = stateAxisOverride == null ? stateAxis(state) : stateAxisOverride;
            Direction localFace = switch (axis) {
                case X -> localFaceForXAxis(face);
                case Z -> localFaceForZAxis(face);
                default -> face;
            };
            return faces.contains(localFace);
        }

        ResolvedBlockSprite resolveSprite(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector,
            @Nullable RepeatTextureBasis textureBasis, @Nullable Direction.Axis repeatAxisOverride) {
            int outputIndex = switch (method) {
                case CTM -> ctm47Index(connector, face);
                case CTM_COMPACT -> compactCtmIndex(connector, face);
                case FIXED -> 0;
                case RANDOM -> weightedIndex(source, pos, face);
                case REPEAT -> repeatIndex(state, repeatAxisOverride, textureBasis, pos, face);
                case HORIZONTAL -> twoBitIndex(connector, horizontalDirections(face));
                case VERTICAL -> twoBitIndex(connector, verticalDirections(face));
                case HORIZONTAL_THEN_VERTICAL ->
                    sevenTileIndex(connector, horizontalDirections(face), verticalDirections(face));
                case VERTICAL_THEN_HORIZONTAL ->
                    sevenTileIndex(connector, verticalDirections(face), horizontalDirections(face));
                case TOP -> connector.connects(Direction.UP, connectMode) ? 0 : -1;
                case OVERLAY, OVERLAY_CTM, OVERLAY_RANDOM, OVERLAY_REPEAT, OVERLAY_FIXED -> -1;
            };
            if (outputIndex < 0) {
                return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
            }
            for (int i = 0; i < outputs.size(); i++) {
                int index = (outputIndex + i) % outputs.size();
                Identifier sprite = outputs.get(index);
                int spriteId = TextureArrayBridge.resolveSpriteId(sprite.toString());
                if (spriteId >= 0) {
                    boolean tintOverride = tintOverride();
                    int tintRgb = tintOverride ? overlayTintRgb(world, state, pos) : 0xFFFFFF;
                    return new ResolvedBlockSprite(spriteId, true, tintRgb, tintOverride, alphaMode);
                }
            }
            return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
        }

        @Nullable
        CompactCtmQuadrants resolveCompactCtmQuadrants(@Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            if (method != RuleMethod.CTM_COMPACT) {
                return null;
            }
            int connections = ctmConnections(connector, face);
            int ctmIndex = CTM_47_INDEX_MAP[connections];
            if (ctmReplacementMap != null && ctmIndex >= 0 && ctmIndex < ctmReplacementMap.length
                && ctmReplacementMap[ctmIndex] >= 0) {
                return null;
            }
            int[] outputIndices = compactQuadrantIndices(connections);
            boolean mixed = false;
            for (int i = 1; i < outputIndices.length; i++) {
                if (outputIndices[i] != outputIndices[0]) {
                    mixed = true;
                    break;
                }
            }
            if (!mixed) {
                return null;
            }
            int[] spriteIds = new int[4];
            for (int i = 0; i < spriteIds.length; i++) {
                spriteIds[i] = spriteIdForOutputIndex(outputIndices[i]);
                if (spriteIds[i] < 0) {
                    return null;
                }
            }
            boolean tintOverride = tintOverride();
            int tintRgb = tintOverride ? overlayTintRgb(world, state, pos) : 0xFFFFFF;
            return new CompactCtmQuadrants(spriteIds, tintRgb, tintOverride, alphaMode);
        }

        BlockOverlaySprite[] resolveOverlaySpriteIds(Identifier source, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector, @Nullable RepeatTextureBasis textureBasis) {
            return switch (method) {
                case OVERLAY, OVERLAY_CTM -> overlaySprites(
                    overlayTileIndices(world, state, pos, face, connector), false,
                    overlayTintRgb(world, state, pos));
                case OVERLAY_RANDOM -> randomOverlaySpriteIds(source, pos, face);
                case OVERLAY_REPEAT -> indexedOverlaySpriteId(repeatIndex(state, null, textureBasis, pos, face), true,
                    overlayTintRgb(world, state, pos));
                case OVERLAY_FIXED -> indexedOverlaySpriteId(0, false, overlayTintRgb(world, state, pos));
                default -> new BlockOverlaySprite[0];
            };
        }

        private BlockOverlaySprite[] indexedOverlaySpriteId(int outputIndex, boolean fallbackFromSelected,
            int tintRgb) {
            if (outputIndex < 0) {
                return new BlockOverlaySprite[0];
            }
            return overlaySprites(List.of(outputIndex), fallbackFromSelected, tintRgb);
        }

        private BlockOverlaySprite[] randomOverlaySpriteIds(Identifier source, @Nullable BlockPos pos,
            @Nullable Direction face) {
            int outputIndex = weightedIndex(source, pos, face);
            if (outputIndex < 0 || outputIndex >= choices.size() || choices.get(outputIndex).skip()) {
                return new BlockOverlaySprite[0];
            }
            return overlaySprites(List.of(outputIndex), true, 0xFFFFFF);
        }

        private BlockOverlaySprite[] overlaySprites(List<Integer> tileIndices, boolean fallbackFromSelected,
            int tintRgb) {
            ArrayList<BlockOverlaySprite> overlays = new ArrayList<>();
            for (int tileIndex : tileIndices) {
                if (tileIndex < 0 || tileIndex >= choices.size()) {
                    continue;
                }
                int spriteId = spriteIdForChoice(tileIndex, fallbackFromSelected);
                if (spriteId >= 0) {
                    overlays.add(new BlockOverlaySprite(spriteId, tintRgb & 0x00FFFFFF, alphaMode));
                }
            }
            return overlays.toArray(BlockOverlaySprite[]::new);
        }

        private int spriteIdForOutputIndex(int outputIndex) {
            if (outputIndex < 0 || outputs.isEmpty()) {
                return -1;
            }
            for (int i = 0; i < outputs.size(); i++) {
                int index = Math.floorMod(outputIndex + i, outputs.size());
                Identifier sprite = outputs.get(index);
                int spriteId = TextureArrayBridge.resolveSpriteId(sprite.toString());
                if (spriteId >= 0) {
                    return spriteId;
                }
            }
            return -1;
        }

        private int spriteIdForChoice(int tileIndex, boolean fallbackFromSelected) {
            int count = fallbackFromSelected ? choices.size() : 1;
            for (int i = 0; i < count; i++) {
                int index = Math.floorMod(tileIndex + i, choices.size());
                TileChoice choice = choices.get(index);
                if (choice.skip() || choice.sprite() == null) {
                    if (i == 0) {
                        return -1;
                    }
                    continue;
                }
                int spriteId = TextureArrayBridge.resolveSpriteId(choice.sprite().toString());
                if (spriteId >= 0) {
                    return spriteId;
                }
            }
            return -1;
        }

        private NeighborConnector neighborConnector(Identifier source, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector fallback) {
            return new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    if (world == null || pos == null || direction == null) {
                        return fallback.connects(direction, mode);
                    }
                    return connectsNeighborState(source, state, world.getBlockState(pos.offset(direction)),
                        face, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    if (world == null || pos == null || first == null || second == null) {
                        return fallback.connects(first, second, mode);
                    }
                    return connectsNeighborState(source, state,
                        world.getBlockState(pos.offset(first).offset(second)), face, mode);
                }
            };
        }

        private NeighborConnector neighborConnectorForTest(Identifier source, @Nullable BlockState state,
            @Nullable Direction face, Map<Direction, BlockState> neighbors) {
            return new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return connectsNeighborState(source, state, neighbors.get(direction), face, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return false;
                }
            };
        }

        private NeighborConnector neighborConnectorForBlockIdsForTest(Identifier source, String stateBlockId,
            @Nullable Direction face, Map<Direction, String> neighbors) {
            return new NeighborConnector() {
                @Override
                public boolean connects(Direction direction, ConnectMode mode) {
                    return connectsNeighborBlockId(source, stateBlockId, neighbors.get(direction), face, mode);
                }

                @Override
                public boolean connects(Direction first, Direction second, ConnectMode mode) {
                    return false;
                }
            };
        }

        private boolean connectsNeighborState(Identifier source, @Nullable BlockState state,
            @Nullable BlockState neighbor, @Nullable Direction face, ConnectMode mode) {
            if (neighbor == null) {
                return false;
            }
            Direction lightFace = face == null ? Direction.NORTH : face;
            if (!connectBlocks.isEmpty() && !matchesBlockPredicates(connectBlocks, neighbor)) {
                return false;
            }
            if (!connectTiles.isEmpty() && !matchesBlockTextureTokens(connectTiles, neighbor, lightFace)) {
                return false;
            }
            if (!connectBlocks.isEmpty() || !connectTiles.isEmpty()) {
                return true;
            }
            return switch (mode) {
                case STATE -> state != null && state.equals(neighbor);
                case BLOCK -> state != null && state.getBlock() == neighbor.getBlock();
                case TILE_AS_BLOCK -> source != null
                    && matchesBlockTextureTokens(List.of(source.getPath()), neighbor, lightFace);
            };
        }

        private boolean connectsNeighborBlockId(Identifier source, String stateBlockId, String neighborBlockId,
            @Nullable Direction face, ConnectMode mode) {
            String normalizedNeighbor = normalizeBlockIdToken(neighborBlockId);
            if (normalizedNeighbor.isEmpty()) {
                return false;
            }
            Direction lightFace = face == null ? Direction.NORTH : face;
            if (!connectBlocks.isEmpty()
                && !matchesBlockPredicates(connectBlocks, normalizedNeighbor, Map.of())) {
                return false;
            }
            if (!connectTiles.isEmpty()
                && !matchesBlockTextureTokens(connectTiles, normalizedNeighbor, lightFace)) {
                return false;
            }
            if (!connectBlocks.isEmpty() || !connectTiles.isEmpty()) {
                return true;
            }
            String normalizedState = normalizeBlockIdToken(stateBlockId);
            return switch (mode) {
                case STATE, BLOCK -> !normalizedState.isEmpty() && normalizedState.equals(normalizedNeighbor);
                case TILE_AS_BLOCK -> source != null
                    && matchesBlockTextureTokens(List.of(source.getPath()), normalizedNeighbor, lightFace);
            };
        }

        private boolean tintOverride() {
            return tintIndex >= 0 || (tintBlock != null && !tintBlock.isEmpty());
        }

        private int overlayTintRgb(@Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos) {
            if (!tintOverride()) {
                return 0xFFFFFF;
            }
            BlockState tintState = tintBlockState();
            if (tintState == null) {
                tintState = state;
            }
            if (tintState == null) {
                return 0xFFFFFF;
            }
            int vanillaRgb = 0xFFFFFF;
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                BlockColors colors = client == null ? null : client.getBlockColors();
                if (colors != null && tintIndex >= 0) {
                    vanillaRgb = colors.getColor(tintState, world, pos, tintIndex) & 0x00FFFFFF;
                }
            } catch (Throwable ignored) {
            }
            return ResourcePackColorPropertiesResolver.resolveBlockColor(tintState, world, pos,
                tintIndex, vanillaRgb) & 0x00FFFFFF;
        }

        @Nullable
        private BlockState tintBlockState() {
            if (tintBlock == null || tintBlock.isEmpty()) {
                return null;
            }
            Identifier id = Identifier.tryParse(tintBlock);
            if (id == null || !Registries.BLOCK.containsId(id)) {
                return null;
            }
            return Registries.BLOCK.get(id).getDefaultState();
        }

        private List<Integer> overlayTileIndices(@Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector) {
            Direction[] directions = ctmDirections(face);
            boolean[] side = new boolean[4];
            boolean[] same = new boolean[4];
            for (int i = 0; i < 4; i++) {
                side[i] = overlaySideApplies(world, pos, face, directions[i], connector);
                same[i] = overlaySameNeighbor(world, pos, face, directions[i]);
            }
            int applications = 0;
            if (side[0]) applications |= 0b0001;
            if (side[1]) applications |= 0b0010;
            if (side[2]) applications |= 0b0100;
            if (side[3]) applications |= 0b1000;

            ArrayList<Integer> out = new ArrayList<>(4);
            switch (applications) {
                case 0b1111 -> out.add(8);
                case 0b0111 -> out.add(5);
                case 0b1011 -> out.add(6);
                case 0b1101 -> out.add(13);
                case 0b1110 -> out.add(12);
                case 0b0101 -> {
                    out.add(9);
                    out.add(7);
                }
                case 0b1010 -> {
                    out.add(1);
                    out.add(15);
                }
                case 0b0011 -> addTwoSideOverlay(out, same, directions, 2, 3, 4, 14,
                    world, pos, face, connector);
                case 0b0110 -> addTwoSideOverlay(out, same, directions, 3, 0, 3, 16,
                    world, pos, face, connector);
                case 0b1100 -> addTwoSideOverlay(out, same, directions, 0, 1, 10, 2,
                    world, pos, face, connector);
                case 0b1001 -> addTwoSideOverlay(out, same, directions, 1, 2, 11, 0,
                    world, pos, face, connector);
                case 0b0001 -> addOneSideOverlay(out, same, directions, 1, 2, 3, 9, 0, 14,
                    world, pos, face, connector);
                case 0b0010 -> addOneSideOverlay(out, same, directions, 2, 3, 0, 1, 14, 16,
                    world, pos, face, connector);
                case 0b0100 -> addOneSideOverlay(out, same, directions, 3, 0, 1, 7, 16, 2,
                    world, pos, face, connector);
                case 0b1000 -> addOneSideOverlay(out, same, directions, 0, 1, 2, 15, 2, 0,
                    world, pos, face, connector);
                case 0b0000 -> addCornerOnlyOverlays(out, same, directions, world, pos, face, connector);
                default -> {
                }
            }
            return out;
        }

        private void addTwoSideOverlay(List<Integer> out, boolean[] same, Direction[] directions,
            int cornerA, int cornerB, int sideTile, int cornerTile,
            @Nullable BlockRenderView world, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            out.add(sideTile);
            if ((same[cornerA] || same[cornerB])
                && overlayCornerApplies(world, pos, face, directions[cornerA], directions[cornerB], connector)) {
                out.add(cornerTile);
            }
        }

        private void addOneSideOverlay(List<Integer> out, boolean[] same, Direction[] directions,
            int cornerA, int middle, int cornerB, int sideTile, int cornerTileA, int cornerTileB,
            @Nullable BlockRenderView world, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            boolean addA;
            boolean addB;
            if (same[middle]) {
                addA = true;
                addB = true;
            } else {
                addA = same[cornerA];
                addB = same[cornerB];
            }
            out.add(sideTile);
            if (addA && overlayCornerApplies(world, pos, face, directions[cornerA], directions[middle], connector)) {
                out.add(cornerTileA);
            }
            if (addB && overlayCornerApplies(world, pos, face, directions[middle], directions[cornerB], connector)) {
                out.add(cornerTileB);
            }
        }

        private void addCornerOnlyOverlays(List<Integer> out, boolean[] same, Direction[] directions,
            @Nullable BlockRenderView world, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            if ((same[0] || same[1])
                && overlayCornerApplies(world, pos, face, directions[0], directions[1], connector)) {
                out.add(2);
            }
            if ((same[1] || same[2])
                && overlayCornerApplies(world, pos, face, directions[1], directions[2], connector)) {
                out.add(0);
            }
            if ((same[2] || same[3])
                && overlayCornerApplies(world, pos, face, directions[2], directions[3], connector)) {
                out.add(14);
            }
            if ((same[3] || same[0])
                && overlayCornerApplies(world, pos, face, directions[3], directions[0], connector)) {
                out.add(16);
            }
        }

        private boolean overlaySideApplies(@Nullable BlockRenderView world, @Nullable BlockPos pos,
            @Nullable Direction face, Direction side, NeighborConnector connector) {
            if (world == null || pos == null) {
                return connector.connects(side, connectMode);
            }
            Direction lightFace = face == null ? Direction.NORTH : face;
            BlockPos occluderPos = pos.offset(side).offset(lightFace);
            if (overlayOccluderBlocks(world, occluderPos)) {
                return false;
            }
            BlockPos otherPos = pos.offset(side);
            BlockState other = world.getBlockState(otherPos);
            return overlayStateApplies(world, other, otherPos, lightFace);
        }

        private boolean overlayCornerApplies(@Nullable BlockRenderView world, @Nullable BlockPos pos,
            @Nullable Direction face, Direction first, Direction second, NeighborConnector connector) {
            if (world == null || pos == null) {
                return connector.connects(first, second, connectMode);
            }
            Direction lightFace = face == null ? Direction.NORTH : face;
            BlockPos otherPos = pos.offset(first).offset(second);
            BlockState other = world.getBlockState(otherPos);
            if (!overlayStateApplies(world, other, otherPos, lightFace)) {
                return false;
            }
            BlockPos occluderPos = otherPos.offset(lightFace);
            return !overlayOccluderBlocks(world, occluderPos);
        }

        boolean overlayOccluderBlocksForTest(boolean opaqueFullCube) {
            return !overlaySolidCheckDisabled() && opaqueFullCube;
        }

        private boolean overlayOccluderBlocks(BlockRenderView world, BlockPos occluderPos) {
            return !overlaySolidCheckDisabled() && world.getBlockState(occluderPos).isOpaqueFullCube();
        }

        private boolean overlaySolidCheckDisabled() {
            return disableSolidCheck || ResourcePackBlockLayerResolver.disablesOverlaySolidCheck();
        }

        private boolean overlayStateApplies(BlockRenderView world, @Nullable BlockState other,
            BlockPos otherPos, Direction face) {
            if (other == null || !other.isFullCube(world, otherPos)) {
                return false;
            }
            if (!connectBlocks.isEmpty() && !matchesBlockPredicates(connectBlocks, other)) {
                return false;
            }
            if (!connectTiles.isEmpty() && !matchesBlockTextureTokens(connectTiles, other, face)) {
                return false;
            }
            return !connectBlocks.isEmpty() || !connectTiles.isEmpty();
        }

        private boolean overlaySameNeighbor(@Nullable BlockRenderView world, @Nullable BlockPos pos,
            @Nullable Direction face, Direction side) {
            if (world == null || pos == null) {
                return false;
            }
            BlockPos otherPos = pos.offset(side);
            BlockState other = world.getBlockState(otherPos);
            if (other == null) {
                return false;
            }
            if (!matchBlocks.isEmpty() && matchesBlockPredicates(matchBlocks, other)) {
                return true;
            }
            return !matchTiles.isEmpty() && matchesBlockTextureTokens(matchTiles, other,
                face == null ? Direction.NORTH : face);
        }

        private boolean matchesBlockTextureTokens(List<String> tileTokens, BlockState state, Direction face) {
            Identifier blockId = Registries.BLOCK.getId(state.getBlock());
            return matchesBlockTextureTokens(tileTokens, blockId.toString(), face);
        }

        private boolean matchesBlockTextureTokens(List<String> tileTokens, String normalizedBlockId,
            Direction face) {
            String blockId = normalizeBlockIdToken(normalizedBlockId);
            int colon = blockId.indexOf(':');
            String path = normalizeMatchToken(colon >= 0 ? blockId.substring(colon + 1) : blockId);
            String suffix = switch (face) {
                case UP -> "_top";
                case DOWN -> "_bottom";
                default -> "_side";
            };
            for (String raw : tileTokens) {
                String token = normalizeMatchToken(raw);
                if (token.equals(path)
                    || token.equals("block/" + path)
                    || token.equals(path + suffix)
                    || token.equals("block/" + path + suffix)) {
                    return true;
                }
                if (token.startsWith(path + "_") || token.startsWith("block/" + path + "_")) {
                    return true;
                }
            }
            return false;
        }

        private int twoBitIndex(NeighborConnector connector, DirectionPair pair) {
            int bits = 0;
            if (connector.connects(pair.negative, connectMode)) bits |= 1;
            if (connector.connects(pair.positive, connectMode)) bits |= 2;
            return bits;
        }

        private int sevenTileIndex(NeighborConnector connector, DirectionPair primary,
            DirectionPair secondary) {
            int primaryBits = twoBitIndex(connector, primary);
            int secondaryBits = twoBitIndex(connector, secondary);
            if (primaryBits == 0 && secondaryBits == 0) return 0;
            if (primaryBits != 0 && secondaryBits == 0) return primaryBits;
            if (primaryBits == 0) return Math.min(6, 3 + secondaryBits);
            return 6;
        }

        private int ctm47Index(NeighborConnector connector, @Nullable Direction face) {
            int connections = ctmConnections(connector, face);
            return CTM_47_INDEX_MAP[connections];
        }

        private int compactCtmIndex(NeighborConnector connector, @Nullable Direction face) {
            int connections = ctmConnections(connector, face);
            int ctmIndex = CTM_47_INDEX_MAP[connections];
            if (ctmReplacementMap != null && ctmIndex >= 0 && ctmIndex < ctmReplacementMap.length) {
                int replacement = ctmReplacementMap[ctmIndex];
                if (replacement >= 0) {
                    return replacement;
                }
            }
            int first = compactQuadrantIndex(0, connections);
            for (int quadrant = 1; quadrant < 4; quadrant++) {
                if (compactQuadrantIndex(quadrant, connections) != first) {
                    return -1;
                }
            }
            return first;
        }

        private int[] compactQuadrantIndices(int connections) {
            return new int[] {
                compactQuadrantIndex(0, connections),
                compactQuadrantIndex(1, connections),
                compactQuadrantIndex(2, connections),
                compactQuadrantIndex(3, connections)
            };
        }

        private int ctmConnections(NeighborConnector connector, @Nullable Direction face) {
            Direction[] directions = ctmDirections(face);
            int connections = 0;
            for (int i = 0; i < 4; i++) {
                if (connector.connects(directions[i], connectMode)) {
                    connections |= 1 << (i * 2);
                }
            }
            for (int i = 0; i < 4; i++) {
                int first = i;
                int second = (i + 1) % 4;
                if (((connections >>> (first * 2)) & 1) == 1
                    && ((connections >>> (second * 2)) & 1) == 1
                    && connector.connects(directions[first], directions[second], connectMode)) {
                    connections |= 1 << (i * 2 + 1);
                }
            }
            return connections;
        }

        private int compactQuadrantIndex(int quadrantIndex, int connections) {
            int first = quadrantIndex;
            int second = (quadrantIndex + 3) % 4;
            boolean connectedFirst = ((connections >>> (first * 2)) & 1) == 1;
            boolean connectedSecond = ((connections >>> (second * 2)) & 1) == 1;
            if (connectedFirst && connectedSecond) {
                return ((connections >>> (second * 2 + 1)) & 1) == 1 ? 1 : 4;
            }
            if (connectedFirst) {
                return 3 - quadrantIndex % 2;
            }
            if (connectedSecond) {
                return 2 + quadrantIndex % 2;
            }
            return 0;
        }

        private Direction[] ctmDirections(@Nullable Direction face) {
            return switch (face == null ? Direction.NORTH : face) {
                case NORTH -> new Direction[] { Direction.WEST, Direction.DOWN, Direction.EAST, Direction.UP };
                case SOUTH -> new Direction[] { Direction.EAST, Direction.DOWN, Direction.WEST, Direction.UP };
                case EAST -> new Direction[] { Direction.NORTH, Direction.DOWN, Direction.SOUTH, Direction.UP };
                case WEST -> new Direction[] { Direction.SOUTH, Direction.DOWN, Direction.NORTH, Direction.UP };
                case UP -> new Direction[] { Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH };
                case DOWN -> new Direction[] { Direction.WEST, Direction.NORTH, Direction.EAST, Direction.SOUTH };
            };
        }

        private DirectionPair horizontalDirections(@Nullable Direction face) {
            return switch (face == null ? Direction.NORTH : face) {
                case NORTH -> new DirectionPair(Direction.WEST, Direction.EAST);
                case SOUTH -> new DirectionPair(Direction.EAST, Direction.WEST);
                case EAST -> new DirectionPair(Direction.NORTH, Direction.SOUTH);
                case WEST -> new DirectionPair(Direction.SOUTH, Direction.NORTH);
                case UP, DOWN -> new DirectionPair(Direction.WEST, Direction.EAST);
            };
        }

        private DirectionPair verticalDirections(@Nullable Direction face) {
            return switch (face == null ? Direction.NORTH : face) {
                case NORTH, SOUTH, EAST, WEST -> new DirectionPair(Direction.DOWN, Direction.UP);
                case UP, DOWN -> new DirectionPair(Direction.NORTH, Direction.SOUTH);
            };
        }

        private int weightedIndex(Identifier source, @Nullable BlockPos pos, @Nullable Direction face) {
            int total = 0;
            for (int weight : weights) {
                total += Math.max(1, weight);
            }
            if (total <= 0) {
                return 0;
            }
            long hash = stableHash(source, pos, face);
            int pick = (int) Math.floorMod(hash, total);
            int accum = 0;
            for (int i = 0; i < weights.length; i++) {
                accum += Math.max(1, weights[i]);
                if (pick < accum) {
                    return i;
                }
            }
            return 0;
        }

        private int repeatIndex(@Nullable BlockState state, @Nullable Direction.Axis axisOverride,
            @Nullable RepeatTextureBasis textureBasis, @Nullable BlockPos pos, @Nullable Direction face) {
            if (pos == null) {
                return 0;
            }
            if (repeatOrientation == RepeatOrientation.TEXTURE && textureBasis != null) {
                return repeatIndexFromCoordinates(textureBasis.u(pos), textureBasis.v(pos));
            }
            RepeatBasis basis = repeatBasis(state, axisOverride, pos, face);
            int u;
            int v;
            switch (basis.face()) {
                case NORTH -> {
                    u = basis.x();
                    v = basis.y();
                }
                case SOUTH -> {
                    u = -basis.x();
                    v = basis.y();
                }
                case EAST -> {
                    u = basis.z();
                    v = basis.y();
                }
                case WEST -> {
                    u = -basis.z();
                    v = basis.y();
                }
                case UP -> {
                    u = basis.x();
                    v = basis.z();
                }
                case DOWN -> {
                    u = basis.x();
                    v = -basis.z();
                }
                default -> {
                    u = basis.x();
                    v = basis.y();
                }
            }
            return repeatIndexFromCoordinates(u, v);
        }

        private int repeatIndexFromCoordinates(int u, int v) {
            int width = Math.max(1, repeatWidth);
            int height = Math.max(1, repeatHeight);
            int x = Math.floorMod(u, width);
            int y = Math.floorMod(v, height);
            return y * width + x;
        }

        private RepeatBasis repeatBasis(@Nullable BlockState state, @Nullable Direction.Axis axisOverride,
            BlockPos pos, @Nullable Direction face) {
            Direction direction = face == null ? Direction.NORTH : face;
            if (repeatOrientation != RepeatOrientation.STATE_AXIS
                && repeatOrientation != RepeatOrientation.TEXTURE) {
                return new RepeatBasis(pos.getX(), pos.getY(), pos.getZ(), direction);
            }
            Direction.Axis axis = axisOverride == null ? stateAxis(state) : axisOverride;
            if (axis == Direction.Axis.X) {
                return new RepeatBasis(-pos.getZ(), pos.getX(), -pos.getY(), localFaceForXAxis(direction));
            }
            if (axis == Direction.Axis.Z) {
                return new RepeatBasis(pos.getX(), pos.getZ(), -pos.getY(), localFaceForZAxis(direction));
            }
            return new RepeatBasis(pos.getX(), pos.getY(), pos.getZ(), direction);
        }

        private Direction localFaceForXAxis(Direction face) {
            return switch (face) {
                case EAST -> Direction.UP;
                case WEST -> Direction.DOWN;
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                case SOUTH -> Direction.WEST;
                case NORTH -> Direction.EAST;
            };
        }

        private Direction localFaceForZAxis(Direction face) {
            return switch (face) {
                case EAST -> Direction.EAST;
                case WEST -> Direction.WEST;
                case UP -> Direction.NORTH;
                case DOWN -> Direction.SOUTH;
                case SOUTH -> Direction.UP;
                case NORTH -> Direction.DOWN;
            };
        }

        private long stableHash(Identifier source, @Nullable BlockPos pos, @Nullable Direction face) {
            if (linkedRandom) {
                return linkedStableHash(pos, face);
            }
            long h = 0xcbf29ce484222325L;
            h = mix(h, id.hashCode());
            h = mix(h, source.hashCode());
            h = mix(h, randomLoops);
            if (pos != null) {
                h = mix(h, pos.getX());
                h = mix(h, pos.getY());
                h = mix(h, pos.getZ());
            }
            h = mix(h, symmetryFaceBucket(face));
            return h;
        }

        private long linkedStableHash(@Nullable BlockPos pos, @Nullable Direction face) {
            long h = 0xcbf29ce484222325L;
            h = mix(h, randomLoops);
            if (pos != null) {
                h = mix(h, pos.getX());
                h = mix(h, pos.getZ());
            }
            h = mix(h, symmetryFaceBucket(face));
            return h;
        }

        private int symmetryFaceBucket(@Nullable Direction face) {
            Direction direction = face == null ? Direction.NORTH : face;
            return switch (randomSymmetry) {
                case ALL -> 0;
                case OPPOSITE -> switch (direction) {
                    case NORTH, SOUTH -> 1;
                    case EAST, WEST -> 2;
                    case UP, DOWN -> 3;
                };
                case NONE -> direction.ordinal();
            };
        }

        private long mix(long h, int value) {
            h ^= value;
            return h * 0x100000001b3L;
        }
    }

    private enum RuleMethod {
        CTM,
        CTM_COMPACT,
        FIXED,
        RANDOM,
        REPEAT,
        HORIZONTAL,
        VERTICAL,
        HORIZONTAL_THEN_VERTICAL,
        VERTICAL_THEN_HORIZONTAL,
        TOP,
        OVERLAY,
        OVERLAY_CTM,
        OVERLAY_RANDOM,
        OVERLAY_REPEAT,
        OVERLAY_FIXED;

        boolean overlayRule() {
            return this == OVERLAY
                || this == OVERLAY_CTM
                || this == OVERLAY_RANDOM
                || this == OVERLAY_REPEAT
                || this == OVERLAY_FIXED;
        }
    }

    private enum ConnectMode {
        BLOCK,
        STATE,
        TILE_AS_BLOCK
    }

    private enum RepeatOrientation {
        NONE,
        TEXTURE,
        STATE_AXIS
    }

    private enum RandomSymmetry {
        NONE,
        OPPOSITE,
        ALL
    }

    private record RepeatBasis(int x, int y, int z, Direction face) {
    }

    private record DirectionPair(Direction negative, Direction positive) {
    }

    private record TileChoice(@Nullable Identifier sprite, boolean skip) {
        static TileChoice sprite(Identifier sprite) {
            return new TileChoice(sprite, false);
        }

        static TileChoice skipped() {
            return new TileChoice(null, true);
        }
    }

    private interface NeighborConnector {
        boolean connects(Direction direction, ConnectMode mode);

        default boolean connects(Direction first, Direction second, ConnectMode mode) {
            return false;
        }
    }

    private record Cache(ResourceManager resourceManager, long textureGeneration, ResolverIndex index) {
        static Cache empty() {
            return new Cache(null, -1L, ResolverIndex.empty());
        }
    }
}
