package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.vertex.PBRVertexFormatElements;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import net.minecraft.block.BlockState;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackTextureVariantResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static final int RULE_LIMIT = 4096;
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

    public record BlockOverlaySprite(int spriteId, int tintRgb) {
    }

    public record ResolvedBlockSprite(int spriteId,
                                      boolean ruleMatched,
                                      int tintRgb,
                                      boolean tintOverride,
                                      int alphaMode) {
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
        return index.resolveDetailed(source, sourceSpriteId, world, state, pos, face);
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
        return index.resolveOverlay(source, sourceSpriteId, world, state, pos, face);
    }

    public static ResolverIndex buildForTest(ResourceManager resourceManager, boolean legacyMcPatcher) {
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
                    parseRule(entry.getKey(), entry.getValue()).ifPresent(rules::add);
                }
            });
    }

    private static Optional<VariantRule> parseRule(Identifier propertyId, Resource resource) {
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
            case "overlay_random" -> RuleMethod.OVERLAY_RANDOM;
            default -> null;
        };
        if (ruleMethod == null) {
            return Optional.empty();
        }

        String propertyAssetPath = ResourcePackCompatCtmTiles.assetPath(propertyId);
        List<String> tileAssetPaths = ResourcePackCompatCtmTiles.ctmTileDependencyAssetPaths(propertyAssetPath, props);
        List<TileChoice> choices = ruleMethod.overlayRule()
            ? tileChoices(propertyAssetPath, props.getProperty("tiles", ""), ruleMethod)
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
        List<String> matchBlocks = matchBlockTokens(props);
        List<String> connectTiles = propertyTileTokens(props, "connectTiles");
        List<String> connectBlocks = blockTokens(props, "connectBlocks");
        EnumSet<Direction> faces = parseFaces(props.getProperty("faces", ""));
        ConnectMode connectMode = parseConnectMode(props.getProperty("connect", "block"));
        int[] weights = parseWeights(props.getProperty("weights", ""),
            ruleMethod.overlayRule() ? choices.size() : outputs.size());
        int randomLoops = parsePositiveInt(props.getProperty("randomLoops", "1"), 1);
        RandomSymmetry randomSymmetry = parseRandomSymmetry(props.getProperty("symmetry", ""));
        int repeatWidth = parsePositiveInt(props.getProperty("width", "1"), 1);
        int repeatHeight = parsePositiveInt(props.getProperty("height", "1"), 1);
        int tintIndex = parseInt(props.getProperty("tintIndex", "-1"), -1);
        String tintBlock = normalizeBlockToken(props.getProperty("tintBlock", ""));
        int alphaMode = parseLayerAlphaMode(props.getProperty("layer", ""));
        int[] ctmReplacementMap = parseCtmReplacementMap(props);
        return Optional.of(new VariantRule(propertyId.toString(), ruleMethod, matchTiles, matchBlocks,
            connectTiles, connectBlocks,
            faces, connectMode, List.copyOf(outputs), List.copyOf(choices), weights, randomLoops, randomSymmetry,
            repeatWidth, repeatHeight, tintIndex, tintBlock, alphaMode,
            ctmReplacementMap));
    }

    private static List<TileChoice> tileChoices(String propertyAssetPath, String tilesValue, RuleMethod method) {
        String value = tilesValue == null ? "" : tilesValue.trim();
        if (value.isEmpty()) {
            value = inferredTilesForResolver(method);
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

    private static String inferredTilesForResolver(RuleMethod method) {
        return switch (method) {
            case CTM -> "0-46";
            case CTM_COMPACT -> "0-4";
            case HORIZONTAL, VERTICAL -> "0-3";
            case HORIZONTAL_THEN_VERTICAL, VERTICAL_THEN_HORIZONTAL -> "0-6";
            case TOP, FIXED -> "0";
            case OVERLAY -> "0-16";
            default -> "";
        };
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

    private static List<String> matchBlockTokens(Properties props) {
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

    private static List<String> blockTokens(Properties props, String key) {
        String value = props.getProperty(key, "").trim();
        if (value.isEmpty()) {
            return List.of();
        }
        String[] tokens = value.split("[\\s,]+");
        ArrayList<String> normalized = new ArrayList<>();
        for (String token : tokens) {
            String blockId = normalizeBlockToken(token);
            if (!blockId.isEmpty()) {
                normalized.add(blockId);
            }
        }
        return List.copyOf(normalized);
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

    private static String normalizeBlockToken(String raw) {
        String token = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (token.isEmpty()) {
            return "";
        }
        int bracket = token.indexOf('[');
        if (bracket >= 0) {
            token = token.substring(0, bracket);
        }
        int equals = token.indexOf('=');
        if (equals >= 0) {
            token = token.substring(0, equals);
        }
        if (!token.contains(":")) {
            token = "minecraft:" + token;
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
        for (int i = 0; i < count; i++) {
            int weight = i < tokens.length ? parsePositiveInt(tokens[i], 1) : 1;
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

    private static Identifier spriteIdentifier(@Nullable Sprite sprite) {
        return sprite == null || sprite.getContents() == null ? null : sprite.getContents().getId();
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

        public int resolveForTest(Identifier source, int sourceSpriteId, @Nullable BlockPos pos,
            @Nullable Direction face) {
            return resolve(source, sourceSpriteId, null, null, pos, face);
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
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector);
        }

        int resolve(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector) {
            return resolveDetailed(source, sourceSpriteId, world, state, pos, face, connector).spriteId();
        }

        ResolvedBlockSprite resolveDetailed(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
            }
            for (VariantRule rule : rules) {
                if (rule.method().overlayRule() || !rule.enabledByOptions() || !rule.matches(source, state, face)) {
                    continue;
                }
                ResolvedBlockSprite resolved =
                    rule.resolveSprite(source, sourceSpriteId, world, state, pos, face, connector);
                if (resolved.spriteId() >= 0) {
                    return resolved;
                }
            }
            return new ResolvedBlockSprite(sourceSpriteId, false, 0xFFFFFF, false, -1);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face) {
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
            return resolveOverlay(source, sourceSpriteId, world, state, pos, face, connector);
        }

        BlockOverlaySprite[] resolveOverlay(Identifier source, int sourceSpriteId, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            if (source == null || sourceSpriteId < 0 || rules.isEmpty()) {
                return new BlockOverlaySprite[0];
            }
            for (VariantRule rule : rules) {
                if (!rule.method().overlayRule() || !rule.enabledByOptions() || !rule.matches(source, state, face)) {
                    continue;
                }
                BlockOverlaySprite[] resolved =
                    rule.resolveOverlaySpriteIds(source, world, state, pos, face, connector);
                if (resolved.length > 0) {
                    return resolved;
                }
            }
            return new BlockOverlaySprite[0];
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
                               RuleMethod method,
                               List<String> matchTiles,
                               List<String> matchBlocks,
                               List<String> connectTiles,
                               List<String> connectBlocks,
                               EnumSet<Direction> faces,
                               ConnectMode connectMode,
                               List<Identifier> outputs,
                               List<TileChoice> choices,
                               int[] weights,
                               int randomLoops,
                               RandomSymmetry randomSymmetry,
                               int repeatWidth,
                               int repeatHeight,
                               int tintIndex,
                               String tintBlock,
                               int alphaMode,
                               int[] ctmReplacementMap) {
        boolean enabledByOptions() {
            return switch (method) {
                case CTM, CTM_COMPACT, FIXED -> Options.materialCompatCtmEnabled;
                case RANDOM -> Options.materialCompatRandomEnabled;
                case REPEAT, HORIZONTAL, VERTICAL, HORIZONTAL_THEN_VERTICAL, VERTICAL_THEN_HORIZONTAL, TOP ->
                    Options.materialCompatCtmEnabled;
                case OVERLAY, OVERLAY_RANDOM -> Options.materialCompatOverlaysEnabled;
            };
        }

        boolean matches(Identifier source, @Nullable BlockState state, @Nullable Direction face) {
            if (face != null && !faces.contains(face)) {
                return false;
            }
            if (!matchBlocks.isEmpty() && state != null) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                for (String match : matchBlocks) {
                    if (blockId.equals(match)) {
                        return true;
                    }
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

        ResolvedBlockSprite resolveSprite(Identifier source, int sourceSpriteId,
            @Nullable BlockRenderView world, @Nullable BlockState state,
            @Nullable BlockPos pos, @Nullable Direction face, NeighborConnector connector) {
            int outputIndex = switch (method) {
                case CTM -> ctm47Index(connector, face);
                case CTM_COMPACT -> compactCtmIndex(connector, face);
                case FIXED -> 0;
                case RANDOM -> weightedIndex(source, pos, face);
                case REPEAT -> repeatIndex(pos, face);
                case HORIZONTAL -> twoBitIndex(connector, horizontalDirections(face));
                case VERTICAL -> twoBitIndex(connector, verticalDirections(face));
                case HORIZONTAL_THEN_VERTICAL ->
                    sevenTileIndex(connector, horizontalDirections(face), verticalDirections(face));
                case VERTICAL_THEN_HORIZONTAL ->
                    sevenTileIndex(connector, verticalDirections(face), horizontalDirections(face));
                case TOP -> connector.connects(Direction.UP, connectMode) ? 0 : -1;
                case OVERLAY, OVERLAY_RANDOM -> -1;
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

        BlockOverlaySprite[] resolveOverlaySpriteIds(Identifier source, @Nullable BlockRenderView world,
            @Nullable BlockState state, @Nullable BlockPos pos, @Nullable Direction face,
            NeighborConnector connector) {
            return switch (method) {
                case OVERLAY -> overlaySprites(overlayTileIndices(world, state, pos, face, connector), false,
                    overlayTintRgb(world, state, pos));
                case OVERLAY_RANDOM -> randomOverlaySpriteIds(source, pos, face);
                default -> new BlockOverlaySprite[0];
            };
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
                    overlays.add(new BlockOverlaySprite(spriteId, tintRgb & 0x00FFFFFF));
                }
            }
            return overlays.toArray(BlockOverlaySprite[]::new);
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
            if (world.getBlockState(occluderPos).isOpaqueFullCube()) {
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
            return !world.getBlockState(occluderPos).isOpaqueFullCube();
        }

        private boolean overlayStateApplies(BlockRenderView world, @Nullable BlockState other,
            BlockPos otherPos, Direction face) {
            if (other == null || !other.isFullCube(world, otherPos)) {
                return false;
            }
            if (!connectBlocks.isEmpty() && !matchesBlockTokens(connectBlocks, other)) {
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
            if (!matchBlocks.isEmpty() && matchesBlockTokens(matchBlocks, other)) {
                return true;
            }
            return !matchTiles.isEmpty() && matchesBlockTextureTokens(matchTiles, other,
                face == null ? Direction.NORTH : face);
        }

        private boolean matchesBlockTokens(List<String> blockTokens, BlockState state) {
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            for (String match : blockTokens) {
                if (blockId.equals(match)) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchesBlockTextureTokens(List<String> tileTokens, BlockState state, Direction face) {
            Identifier blockId = Registries.BLOCK.getId(state.getBlock());
            String path = normalizeMatchToken(blockId.getPath());
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

        private int repeatIndex(@Nullable BlockPos pos, @Nullable Direction face) {
            if (pos == null) {
                return 0;
            }
            int u;
            int v;
            switch (face == null ? Direction.NORTH : face) {
                case NORTH -> {
                    u = pos.getX();
                    v = pos.getY();
                }
                case SOUTH -> {
                    u = -pos.getX();
                    v = pos.getY();
                }
                case EAST -> {
                    u = pos.getZ();
                    v = pos.getY();
                }
                case WEST -> {
                    u = -pos.getZ();
                    v = pos.getY();
                }
                case UP -> {
                    u = pos.getX();
                    v = pos.getZ();
                }
                case DOWN -> {
                    u = pos.getX();
                    v = -pos.getZ();
                }
                default -> {
                    u = pos.getX();
                    v = pos.getY();
                }
            }
            int width = Math.max(1, repeatWidth);
            int height = Math.max(1, repeatHeight);
            int x = Math.floorMod(u, width);
            int y = Math.floorMod(v, height);
            return y * width + x;
        }

        private long stableHash(Identifier source, @Nullable BlockPos pos, @Nullable Direction face) {
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
        OVERLAY_RANDOM;

        boolean overlayRule() {
            return this == OVERLAY || this == OVERLAY_RANDOM;
        }
    }

    private enum ConnectMode {
        BLOCK,
        STATE,
        TILE_AS_BLOCK
    }

    private enum RandomSymmetry {
        NONE,
        OPPOSITE,
        ALL
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
