package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackNaturalTextureResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static volatile Cache cache = Cache.empty();

    private ResourcePackNaturalTextureResolver() {
    }

    public static NaturalTransform resolveBlockTransform(@Nullable Sprite sourceSprite,
        int resolvedSpriteId,
        @Nullable BlockPos pos,
        @Nullable Direction face) {
        Identifier source = spriteIdentifier(sourceSprite);
        if (source == null || pos == null || !Options.materialCompatEnabled
            || !Options.materialCompatNaturalEnabled) {
            return NaturalTransform.identity();
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return NaturalTransform.identity();
        }
        return resolveBlockTransform(source, resolvedSpriteId, pos, face, activeIndex(resourceManager));
    }

    public static NaturalTransform resolveBlockTransformForTest(ResourceManager resourceManager,
        Identifier source,
        int resolvedSpriteId,
        BlockPos pos,
        @Nullable Direction face,
        boolean legacyMcPatcher) {
        if (source == null || pos == null) {
            return NaturalTransform.identity();
        }
        return resolveBlockTransform(source, resolvedSpriteId, pos, face,
            build(resourceManager, legacyMcPatcher));
    }

    public static NaturalIndex buildForTest(ResourceManager resourceManager, boolean legacyMcPatcher) {
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

    private static NaturalIndex activeIndex(ResourceManager resourceManager) {
        long generation = TextureArrayBridge.getActiveTextureGeneration();
        Cache local = cache;
        if (local.resourceManager == resourceManager && local.textureGeneration == generation) {
            return local.index;
        }
        NaturalIndex next = build(resourceManager, Options.materialCompatLegacyMcPatcherEnabled);
        cache = new Cache(resourceManager, generation, next);
        if (!next.rules.isEmpty()) {
            LOGGER.info("[MaterialCompat] Natural texture resolver compiled {} rules", next.rules.size());
        }
        return next;
    }

    private static NaturalIndex build(ResourceManager resourceManager, boolean legacyMcPatcher) {
        if (resourceManager == null) {
            return NaturalIndex.empty();
        }
        LinkedHashMap<String, NaturalRule> rules = new LinkedHashMap<>();
        loadRules(resourceManager, Identifier.ofVanilla("optifine/natural.properties"), rules);
        if (legacyMcPatcher) {
            loadRules(resourceManager, Identifier.ofVanilla("mcpatcher/natural.properties"), rules);
        }
        return new NaturalIndex(Map.copyOf(rules));
    }

    private static void loadRules(ResourceManager resourceManager, Identifier id,
        LinkedHashMap<String, NaturalRule> rules) {
        Optional<Resource> resource = resourceManager.getResource(id);
        if (resource.isEmpty()) {
            return;
        }
        Properties props = new Properties();
        try (BufferedReader reader = resource.get().getReader()) {
            props.load(reader);
        } catch (IOException e) {
            return;
        }
        for (String key : props.stringPropertyNames()) {
            NaturalRule.parse(props.getProperty(key)).ifPresent(rule -> {
                if (rule.enabled()) {
                    rules.put(normalizeTextureKey(key), rule);
                } else {
                    rules.remove(normalizeTextureKey(key));
                }
            });
        }
    }

    private static String normalizeTextureKey(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (token.endsWith(".png")) {
            token = token.substring(0, token.length() - 4);
        }
        if (token.startsWith("textures/")) {
            token = token.substring("textures/".length());
        }
        int colon = token.indexOf(':');
        if (colon >= 0) {
            token = token.substring(colon + 1);
            if (token.startsWith("textures/")) {
                token = token.substring("textures/".length());
            }
        }
        return token;
    }

    private static Identifier spriteIdentifier(@Nullable Sprite sprite) {
        return sprite == null || sprite.getContents() == null ? null : sprite.getContents().getId();
    }

    private static NaturalTransform resolveBlockTransform(Identifier source,
        int resolvedSpriteId,
        BlockPos pos,
        @Nullable Direction face,
        NaturalIndex index) {
        Identifier resolved = spriteIdentifierForId(resolvedSpriteId);
        if (resolved != null && index.hasRule(resolved)) {
            return index.resolve(resolved, pos, face);
        }
        int sourceSpriteId = TextureArrayBridge.resolveRenderableSpriteId(source);
        if (resolvedSpriteId != sourceSpriteId) {
            return NaturalTransform.identity();
        }
        return index.resolve(source, pos, face);
    }

    @Nullable
    private static Identifier spriteIdentifierForId(int spriteId) {
        if (spriteId < 0) {
            return null;
        }
        List<Identifier> ids = TextureArrayBridge.sortedSpriteIds;
        return spriteId < ids.size() ? ids.get(spriteId) : null;
    }

    public static final class NaturalIndex {
        private static final NaturalIndex EMPTY = new NaturalIndex(Map.of());
        private final Map<String, NaturalRule> rules;

        private NaturalIndex(Map<String, NaturalRule> rules) {
            this.rules = rules;
        }

        public static NaturalIndex empty() {
            return EMPTY;
        }

        public int ruleCountForTest() {
            return rules.size();
        }

        public NaturalTransform resolve(Identifier source, BlockPos pos, @Nullable Direction face) {
            NaturalRule rule = ruleFor(source);
            if (rule == null) {
                return NaturalTransform.identity();
            }
            long hash = stableHash(source, pos, face);
            int variants = rule.rotationVariants * (rule.flip ? 2 : 1);
            int pick = variants <= 1 ? 0 : (int) Math.floorMod(hash, variants);
            boolean flip = rule.flip && (pick % 2) == 1;
            int rotationPick = rule.flip ? pick / 2 : pick;
            int quarterTurns = rule.rotationVariants == 4
                ? rotationPick & 3
                : rule.rotationVariants == 2 ? (rotationPick & 1) * 2 : 0;
            return new NaturalTransform(quarterTurns, flip);
        }

        private boolean hasRule(Identifier source) {
            return ruleFor(source) != null;
        }

        private NaturalRule ruleFor(Identifier source) {
            String path = normalizeTextureKey(source.getPath());
            NaturalRule rule = rules.get(path);
            if (rule != null) {
                return rule;
            }
            rule = rules.get("block/" + path);
            if (rule != null) {
                return rule;
            }
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? rules.get(path.substring(slash + 1)) : null;
        }

        private static long stableHash(Identifier source, BlockPos pos, @Nullable Direction face) {
            long h = 0xcbf29ce484222325L;
            h = mix(h, source.hashCode());
            h = mix(h, pos.getX());
            h = mix(h, pos.getY());
            h = mix(h, pos.getZ());
            h = mix(h, face == null ? 7 : face.ordinal());
            return h;
        }

        private static long mix(long h, int value) {
            h ^= value;
            return h * 0x100000001b3L;
        }
    }

    private record NaturalRule(int rotationVariants, boolean flip) {
        static Optional<NaturalRule> parse(String raw) {
            String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
            return switch (value) {
                case "4" -> Optional.of(new NaturalRule(4, false));
                case "2" -> Optional.of(new NaturalRule(2, false));
                case "F" -> Optional.of(new NaturalRule(1, true));
                case "4F" -> Optional.of(new NaturalRule(4, true));
                case "2F" -> Optional.of(new NaturalRule(2, true));
                case "0" -> Optional.of(new NaturalRule(1, false));
                default -> Optional.empty();
            };
        }

        boolean enabled() {
            return rotationVariants > 1 || flip;
        }
    }

    public record NaturalTransform(int quarterTurns, boolean flipU) {
        private static final NaturalTransform IDENTITY = new NaturalTransform(0, false);

        public static NaturalTransform identity() {
            return IDENTITY;
        }

        public boolean isIdentity() {
            return (quarterTurns & 3) == 0 && !flipU;
        }

        public float transformU(float u, float v) {
            if (flipU) {
                u = 1.0f - u;
            }
            return switch (quarterTurns & 3) {
                case 1 -> 1.0f - v;
                case 2 -> 1.0f - u;
                case 3 -> v;
                default -> u;
            };
        }

        public float transformV(float u, float v) {
            if (flipU) {
                u = 1.0f - u;
            }
            return switch (quarterTurns & 3) {
                case 1 -> u;
                case 2 -> 1.0f - v;
                case 3 -> 1.0f - u;
                default -> v;
            };
        }
    }

    private record Cache(ResourceManager resourceManager, long textureGeneration, NaturalIndex index) {
        static Cache empty() {
            return new Cache(null, -1L, NaturalIndex.empty());
        }
    }
}
