package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackLightmapResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static volatile Cache cache = Cache.empty();
    private static final int LEVELS = 16;

    private ResourcePackLightmapResolver() {
    }

    public static LightmapSample resolve(@Nullable ClientWorld world,
        float tickDelta,
        float skyFactor,
        float blockFactor,
        float nightVisionFactor) {
        if (!Options.materialCompatEnabled || !Options.materialCompatColorsEnabled) {
            return LightmapSample.disabled();
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return LightmapSample.disabled();
        }
        float rain = world == null ? 0.0f : world.getRainGradient(tickDelta);
        float thunder = world == null ? 0.0f : world.getThunderGradient(tickDelta);
        return activeIndex(resourceManager).sample(dimensionKey(world), skyFactor, blockFactor,
            rain, thunder, nightVisionFactor);
    }

    public static LightmapIndex buildForTest(ResourceManager resourceManager, boolean legacyMcPatcher) {
        return build(resourceManager, legacyMcPatcher);
    }

    public static LightmapSample resolveForTest(ResourceManager resourceManager,
        String dimensionKey,
        float skyFactor,
        float blockFactor,
        float rainFactor,
        float thunderFactor,
        float nightVisionFactor,
        boolean legacyMcPatcher) {
        return build(resourceManager, legacyMcPatcher).sample(dimensionKey, skyFactor,
            blockFactor, rainFactor, thunderFactor, nightVisionFactor);
    }

    private static ResourceManager currentResourceManager() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            return client == null ? null : client.getResourceManager();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LightmapIndex activeIndex(ResourceManager resourceManager) {
        long generation = TextureArrayBridge.getActiveTextureGeneration();
        Cache local = cache;
        if (local.resourceManager == resourceManager && local.textureGeneration == generation) {
            return local.index;
        }
        LightmapIndex next = build(resourceManager, Options.materialCompatLegacyMcPatcherEnabled);
        cache = new Cache(resourceManager, generation, next);
        if (next.paletteCountForTest() > 0) {
            LOGGER.info("[MaterialCompat] Custom lightmap resolver compiled {} OptiFine/MCPatcher palettes",
                next.paletteCountForTest());
        }
        return next;
    }

    private static LightmapIndex build(ResourceManager resourceManager, boolean legacyMcPatcher) {
        if (resourceManager == null) {
            return LightmapIndex.empty();
        }
        LightmapSet overworld = loadSet(resourceManager, "world0", legacyMcPatcher);
        LightmapSet nether = loadSet(resourceManager, "world-1", legacyMcPatcher);
        LightmapSet end = loadSet(resourceManager, "world1", legacyMcPatcher);
        return new LightmapIndex(Map.of(
            "world0", overworld,
            "world-1", nether,
            "world1", end
        ));
    }

    private static LightmapSet loadSet(ResourceManager resourceManager,
        String name,
        boolean legacyMcPatcher) {
        return new LightmapSet(
            loadPalette(resourceManager, name, "", legacyMcPatcher).orElse(null),
            loadPalette(resourceManager, name, "_rain", legacyMcPatcher).orElse(null),
            loadPalette(resourceManager, name, "_thunder", legacyMcPatcher).orElse(null)
        );
    }

    private static Optional<Palette> loadPalette(ResourceManager resourceManager,
        String name,
        String suffix,
        boolean legacyMcPatcher) {
        Optional<Resource> resource = resourceManager.getResource(
            Identifier.ofVanilla("optifine/lightmap/" + name + suffix + ".png"));
        if (resource.isEmpty() && legacyMcPatcher) {
            resource = resourceManager.getResource(
                Identifier.ofVanilla("mcpatcher/lightmap/" + name + suffix + ".png"));
        }
        if (resource.isEmpty()) {
            return Optional.empty();
        }
        try (InputStream input = resource.get().getInputStream();
             NativeImage image = NativeImage.read(input)) {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height < LEVELS * 2) {
                return Optional.empty();
            }
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = image.getColorArgb(x, y) & 0x00FFFFFF;
                }
            }
            return Optional.of(new Palette(width, height, pixels));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String dimensionKey(@Nullable ClientWorld world) {
        if (world == null || world.getRegistryKey() == null || world.getRegistryKey().getValue() == null) {
            return "world0";
        }
        String key = world.getRegistryKey().getValue().toString().toLowerCase(Locale.ROOT);
        if ("minecraft:the_nether".equals(key)) {
            return "world-1";
        }
        if ("minecraft:the_end".equals(key)) {
            return "world1";
        }
        return "world0";
    }

    public static final class LightmapIndex {
        private static final LightmapIndex EMPTY = new LightmapIndex(Map.of());
        private final Map<String, LightmapSet> sets;

        private LightmapIndex(Map<String, LightmapSet> sets) {
            this.sets = sets;
        }

        public static LightmapIndex empty() {
            return EMPTY;
        }

        public LightmapSample sample(String dimensionKey,
            float skyFactor,
            float blockFactor,
            float rainFactor,
            float thunderFactor,
            float nightVisionFactor) {
            LightmapSet set = sets.getOrDefault(dimensionKey, sets.get("world0"));
            if (set == null || set.base == null) {
                return LightmapSample.disabled();
            }
            return set.sample(skyFactor, blockFactor, rainFactor, thunderFactor, nightVisionFactor);
        }

        public int paletteCountForTest() {
            int count = 0;
            for (LightmapSet set : sets.values()) {
                count += set.paletteCount();
            }
            return count;
        }
    }

    private record LightmapSet(@Nullable Palette base,
        @Nullable Palette rain,
        @Nullable Palette thunder) {
        LightmapSample sample(float skyFactor,
            float blockFactor,
            float rainFactor,
            float thunderFactor,
            float nightVisionFactor) {
            LightmapSample sample = base.sample(skyFactor, blockFactor, nightVisionFactor);
            if (rain != null && rainFactor > 0.0f) {
                sample = sample.mix(rain.sample(skyFactor, blockFactor, nightVisionFactor), clamp01(rainFactor));
            }
            if (thunder != null && thunderFactor > 0.0f) {
                sample = sample.mix(thunder.sample(skyFactor, blockFactor, nightVisionFactor), clamp01(thunderFactor));
            }
            return sample;
        }

        int paletteCount() {
            int count = base == null ? 0 : 1;
            count += rain == null ? 0 : 1;
            count += thunder == null ? 0 : 1;
            return count;
        }
    }

    private record Palette(int width, int height, int[] pixels) {
        LightmapSample sample(float skyFactor, float blockFactor, float nightVisionFactor) {
            int skyColumn = clampIndex(Math.round(clamp01(skyFactor) * (width - 1)), width);
            int blockColumn = clampIndex(Math.round(clamp01(blockFactor - 1.0f) * (width - 1)), width);
            float[] sky = new float[LEVELS * 3];
            float[] block = new float[LEVELS * 3];
            int nightOffset = height >= LEVELS * 4 ? LEVELS * 2 : 0;
            boolean hasNightVision = nightOffset > 0;
            float nightMix = hasNightVision ? clamp01(nightVisionFactor) : 0.0f;
            for (int level = 0; level < LEVELS; level++) {
                int skyRgb = rgb(skyColumn, level);
                int blockRgb = rgb(blockColumn, LEVELS + level);
                if (nightMix > 0.0f) {
                    skyRgb = mixRgb(skyRgb, rgb(skyColumn, nightOffset + level), nightMix);
                    blockRgb = mixRgb(blockRgb, rgb(blockColumn, nightOffset + LEVELS + level), nightMix);
                }
                writeRgb(sky, level, skyRgb);
                writeRgb(block, level, blockRgb);
            }
            return new LightmapSample(true, hasNightVision, sky, block);
        }

        private int rgb(int x, int y) {
            return pixels[clampIndex(y, height) * width + clampIndex(x, width)] & 0x00FFFFFF;
        }
    }

    public record LightmapSample(boolean enabled,
        boolean includesNightVision,
        float[] skyRgb,
        float[] blockRgb) {
        public static LightmapSample disabled() {
            return new LightmapSample(false, false, new float[LEVELS * 3], new float[LEVELS * 3]);
        }

        LightmapSample mix(LightmapSample other, float amount) {
            if (!enabled) {
                return other;
            }
            float t = clamp01(amount);
            float[] sky = new float[LEVELS * 3];
            float[] block = new float[LEVELS * 3];
            for (int i = 0; i < sky.length; i++) {
                sky[i] = skyRgb[i] + (other.skyRgb[i] - skyRgb[i]) * t;
                block[i] = blockRgb[i] + (other.blockRgb[i] - blockRgb[i]) * t;
            }
            return new LightmapSample(true,
                includesNightVision || other.includesNightVision, sky, block);
        }
    }

    private static void writeRgb(float[] out, int level, int rgb) {
        int offset = level * 3;
        out[offset] = ((rgb >> 16) & 0xFF) / 255.0f;
        out[offset + 1] = ((rgb >> 8) & 0xFF) / 255.0f;
        out[offset + 2] = (rgb & 0xFF) / 255.0f;
    }

    private static int mixRgb(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int blue = Math.round(ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | blue;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static int clampIndex(int value, int size) {
        return Math.max(0, Math.min(Math.max(0, size - 1), value));
    }

    private record Cache(ResourceManager resourceManager, long textureGeneration, LightmapIndex index) {
        static Cache empty() {
            return new Cache(null, -1L, LightmapIndex.empty());
        }
    }
}
