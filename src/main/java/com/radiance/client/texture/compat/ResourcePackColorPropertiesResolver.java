package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Properties;
import net.minecraft.world.biome.Biome;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResourcePackColorPropertiesResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger("RadSER Material Compat");
    private static volatile Cache cache = Cache.empty();

    private ResourcePackColorPropertiesResolver() {
    }

    public static int resolveBlockColor(@Nullable BlockState state,
        @Nullable BlockRenderView world,
        @Nullable BlockPos pos,
        int tintIndex,
        int vanillaRgb) {
        if (state == null || !Options.materialCompatEnabled || !Options.materialCompatColorsEnabled) {
            return vanillaRgb & 0x00FFFFFF;
        }
        ResourceManager resourceManager = currentResourceManager();
        if (resourceManager == null) {
            return vanillaRgb & 0x00FFFFFF;
        }
        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        return activeIndex(resourceManager).resolveBlockColor(blockId, world, pos, vanillaRgb);
    }

    public static ColorIndex buildForTest(ResourceManager resourceManager, boolean legacyMcPatcher) {
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

    private static ColorIndex activeIndex(ResourceManager resourceManager) {
        long generation = TextureArrayBridge.getActiveTextureGeneration();
        Cache local = cache;
        if (local.resourceManager == resourceManager && local.textureGeneration == generation) {
            return local.index;
        }
        ColorIndex next = build(resourceManager, Options.materialCompatLegacyMcPatcherEnabled);
        cache = new Cache(resourceManager, generation, next);
        if (next.fixedBlockTintCountForTest() > 0 || next.variablePaletteCountForTest() > 0) {
            LOGGER.info("[MaterialCompat] Color resolver compiled {} fixed block tints from {} flat palettes and {} fixed colormaps; {} biome palettes pending",
                next.fixedBlockTintCountForTest(), next.flatPaletteCountForTest(),
                next.fixedBlockColormapCountForTest(), next.variablePaletteCountForTest());
        }
        return next;
    }

    private static ColorIndex build(ResourceManager resourceManager, boolean legacyMcPatcher) {
        if (resourceManager == null) {
            return ColorIndex.empty();
        }
        LinkedHashMap<String, Integer> blockColors = new LinkedHashMap<>();
        LinkedHashMap<String, PaletteImage> blockPalettes = new LinkedHashMap<>();
        int[] counts = new int[3];
        loadColorProperties(resourceManager, Identifier.ofVanilla("optifine/color.properties"),
            blockColors, blockPalettes, counts);
        loadFixedBlockColormapProperties(resourceManager, "optifine/colormap/blocks",
            blockColors, counts);
        if (legacyMcPatcher) {
            loadColorProperties(resourceManager, Identifier.ofVanilla("mcpatcher/color.properties"),
                blockColors, blockPalettes, counts);
            loadFixedBlockColormapProperties(resourceManager, "mcpatcher/colormap/blocks",
                blockColors, counts);
        }
        return new ColorIndex(Map.copyOf(blockColors), Map.copyOf(blockPalettes), counts[0], counts[1], counts[2]);
    }

    private static void loadColorProperties(ResourceManager resourceManager,
        Identifier propertiesId,
        LinkedHashMap<String, Integer> blockColors,
        LinkedHashMap<String, PaletteImage> blockPalettes,
        int[] counts) {
        Optional<Resource> resource = resourceManager.getResource(propertiesId);
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
            String lower = key.toLowerCase(Locale.ROOT);
            if (!lower.startsWith("palette.block.")) {
                continue;
            }
            String paletteRef = key.substring("palette.block.".length()).trim();
            Optional<Resource> palette = firstPresent(resourceManager, paletteCandidates(paletteRef));
            if (palette.isEmpty()) {
                continue;
            }
            Optional<PaletteImage> image = readPaletteImage(palette.get());
            if (image.isEmpty()) {
                continue;
            }
            int assigned = 0;
            OptionalInt flat = image.get().flatRgb();
            if (flat.isPresent()) {
                int color = flat.getAsInt() & 0x00FFFFFF;
                for (String block : blockTokens(props.getProperty(key))) {
                    blockColors.put(block, color);
                    assigned++;
                }
                if (assigned > 0) {
                    counts[0]++;
                }
            } else {
                for (String block : blockTokens(props.getProperty(key))) {
                    blockPalettes.put(block, image.get());
                    assigned++;
                }
                if (assigned > 0) {
                    counts[1]++;
                }
            }
        }
    }

    private static void loadFixedBlockColormapProperties(ResourceManager resourceManager,
        String root,
        LinkedHashMap<String, Integer> blockColors,
        int[] counts) {
        Map<Identifier, Resource> resources = resourceManager.findResources(root,
            id -> id.getPath().endsWith(".properties"));
        resources.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> loadFixedBlockColormap(entry.getValue(), blockColors, counts));
    }

    private static void loadFixedBlockColormap(Resource resource,
        LinkedHashMap<String, Integer> blockColors,
        int[] counts) {
        Properties props = new Properties();
        try (BufferedReader reader = resource.getReader()) {
            props.load(reader);
        } catch (IOException e) {
            return;
        }
        String format = props.getProperty("format", "").trim().toLowerCase(Locale.ROOT);
        if (!"fixed".equals(format)) {
            return;
        }
        OptionalInt color = parseRgb(props.getProperty("color", ""));
        if (color.isEmpty()) {
            return;
        }
        int assigned = 0;
        for (String block : blockTokens(props.getProperty("blocks"))) {
            blockColors.put(block, color.getAsInt() & 0x00FFFFFF);
            assigned++;
        }
        if (assigned > 0) {
            counts[2]++;
        }
    }

    private static OptionalInt parseRgb(String raw) {
        String token = raw == null ? "" : raw.trim();
        if (token.startsWith("#")) {
            token = token.substring(1);
        }
        if (token.length() == 8) {
            token = token.substring(2);
        }
        if (token.length() != 6) {
            return OptionalInt.empty();
        }
        try {
            return OptionalInt.of(Integer.parseInt(token, 16) & 0x00FFFFFF);
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    private static Optional<Resource> firstPresent(ResourceManager resourceManager, List<Identifier> ids) {
        for (Identifier id : ids) {
            Optional<Resource> resource = resourceManager.getResource(id);
            if (resource.isPresent()) {
                return resource;
            }
        }
        return Optional.empty();
    }

    private static List<Identifier> paletteCandidates(String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/');
        if (token.isEmpty()) {
            return List.of();
        }
        ArrayList<Identifier> ids = new ArrayList<>();
        addPaletteCandidate(ids, token);
        if (token.startsWith("~/")) {
            addPaletteCandidate(ids, "optifine/" + token.substring(2));
        } else if (token.startsWith("./")) {
            addPaletteCandidate(ids, "optifine/" + token.substring(2));
        } else if (token.startsWith("/")) {
            addPaletteCandidate(ids, token.substring(1));
            addPaletteCandidate(ids, "optifine/" + token.substring(1));
        } else if (!token.contains(":") && !token.startsWith("optifine/")
            && !token.startsWith("mcpatcher/") && !token.startsWith("textures/")) {
            addPaletteCandidate(ids, "optifine/" + token);
        }
        return List.copyOf(ids);
    }

    private static void addPaletteCandidate(List<Identifier> ids, String raw) {
        String token = raw == null ? "" : raw.trim().replace('\\', '/');
        if (token.isEmpty()) {
            return;
        }
        if (token.endsWith(".properties")) {
            return;
        }
        if (!token.endsWith(".png")) {
            token = token + ".png";
        }
        if (token.startsWith("assets/")) {
            String[] parts = token.split("/", 4);
            if (parts.length == 4) {
                addIfValid(ids, parts[1] + ":" + parts[3]);
            }
            return;
        }
        if (token.startsWith("~/")) {
            token = "optifine/" + token.substring(2);
        }
        if (token.startsWith("./")) {
            token = "optifine/" + token.substring(2);
        }
        if (token.startsWith("/")) {
            token = token.substring(1);
        }
        addIfValid(ids, token.contains(":") ? token : "minecraft:" + token);
    }

    private static void addIfValid(List<Identifier> ids, String raw) {
        Identifier id = Identifier.tryParse(raw.toLowerCase(Locale.ROOT));
        if (id != null && !ids.contains(id)) {
            ids.add(id);
        }
    }

    private static Optional<PaletteImage> readPaletteImage(Resource resource) {
        try (InputStream input = resource.getInputStream();
             NativeImage image = NativeImage.read(input)) {
            int width = image.getWidth();
            int height = image.getHeight();
            if (width <= 0 || height <= 0) {
                return Optional.empty();
            }
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = image.getColorArgb(x, y) & 0x00FFFFFF;
                }
            }
            return Optional.of(new PaletteImage(width, height, pixels));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static List<String> blockTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        ArrayList<String> blocks = new ArrayList<>();
        for (String token : raw.trim().split("[\\s,]+")) {
            String normalized = normalizeBlockToken(token);
            if (!normalized.isEmpty()) {
                blocks.add(normalized);
            }
        }
        return List.copyOf(blocks);
    }

    private static String normalizeBlockToken(String raw) {
        String token = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (token.isEmpty() || Character.isDigit(token.charAt(0))) {
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
        String[] colonParts = token.split(":");
        if (colonParts.length > 2) {
            token = colonParts[0] + ":" + colonParts[1];
        }
        if (!token.contains(":")) {
            token = "minecraft:" + token;
        }
        return Identifier.tryParse(token) == null ? "" : token;
    }

    public static final class ColorIndex {
        private static final ColorIndex EMPTY = new ColorIndex(Map.of(), Map.of(), 0, 0, 0);
        private final Map<String, Integer> blockColors;
        private final Map<String, PaletteImage> blockPalettes;
        private final int flatPaletteCount;
        private final int variablePaletteCount;
        private final int fixedBlockColormapCount;

        private ColorIndex(Map<String, Integer> blockColors,
            Map<String, PaletteImage> blockPalettes,
            int flatPaletteCount,
            int variablePaletteCount,
            int fixedBlockColormapCount) {
            this.blockColors = blockColors;
            this.blockPalettes = blockPalettes;
            this.flatPaletteCount = flatPaletteCount;
            this.variablePaletteCount = variablePaletteCount;
            this.fixedBlockColormapCount = fixedBlockColormapCount;
        }

        public static ColorIndex empty() {
            return EMPTY;
        }

        public int resolveBlockColor(String blockId, int vanillaRgb) {
            Integer rgb = blockColors.get(normalizeBlockToken(blockId));
            if (rgb != null) {
                return rgb & 0x00FFFFFF;
            }
            return vanillaRgb & 0x00FFFFFF;
        }

        public int resolveBlockColor(String blockId, @Nullable BlockRenderView world,
            @Nullable BlockPos pos, int vanillaRgb) {
            String normalized = normalizeBlockToken(blockId);
            Integer rgb = blockColors.get(normalized);
            if (rgb != null) {
                return rgb & 0x00FFFFFF;
            }
            PaletteImage palette = blockPalettes.get(normalized);
            if (palette != null && world != null && pos != null) {
                return palette.sample(world, pos, vanillaRgb) & 0x00FFFFFF;
            }
            return vanillaRgb & 0x00FFFFFF;
        }

        public int resolveBlockColorForClimateForTest(String blockId, double temperature,
            double downfall, int vanillaRgb) {
            String normalized = normalizeBlockToken(blockId);
            Integer rgb = blockColors.get(normalized);
            if (rgb != null) {
                return rgb & 0x00FFFFFF;
            }
            PaletteImage palette = blockPalettes.get(normalized);
            return palette == null ? vanillaRgb & 0x00FFFFFF : palette.sample(temperature, downfall);
        }

        public int fixedBlockTintCountForTest() {
            return blockColors.size();
        }

        public int flatPaletteCountForTest() {
            return flatPaletteCount;
        }

        public int variablePaletteCountForTest() {
            return variablePaletteCount;
        }

        public int fixedBlockColormapCountForTest() {
            return fixedBlockColormapCount;
        }
    }

    private record PaletteImage(int width, int height, int[] pixels) {
        OptionalInt flatRgb() {
            if (pixels.length == 0) {
                return OptionalInt.empty();
            }
            int rgb = pixels[0] & 0x00FFFFFF;
            for (int pixel : pixels) {
                if ((pixel & 0x00FFFFFF) != rgb) {
                    return OptionalInt.empty();
                }
            }
            return OptionalInt.of(rgb);
        }

        int sample(BlockRenderView world, BlockPos pos, int fallbackRgb) {
            if (world instanceof WorldView worldView) {
                try {
                    return sample(worldView.getBiome(pos).value(), pos.getX(), pos.getZ());
                } catch (Throwable ignored) {
                }
            }
            return fallbackRgb & 0x00FFFFFF;
        }

        int sample(Biome biome, double x, double z) {
            double temperature = biome == null ? 0.5 : biome.getTemperature();
            double downfall = biome != null && biome.hasPrecipitation() ? 1.0 : 0.0;
            return sample(temperature, downfall);
        }

        int sample(double temperature, double downfall) {
            double t = clamp01(temperature);
            double h = clamp01(downfall) * t;
            int x = clampIndex((int) Math.round((1.0 - t) * (width - 1)), width);
            int y = clampIndex((int) Math.round((1.0 - h) * (height - 1)), height);
            return pixels[y * width + x] & 0x00FFFFFF;
        }

        private static double clamp01(double value) {
            if (!Double.isFinite(value)) {
                return 0.0;
            }
            return Math.max(0.0, Math.min(1.0, value));
        }

        private static int clampIndex(int value, int size) {
            return Math.max(0, Math.min(Math.max(0, size - 1), value));
        }
    }

    private record Cache(ResourceManager resourceManager, long textureGeneration, ColorIndex index) {
        static Cache empty() {
            return new Cache(null, -1L, ColorIndex.empty());
        }
    }
}
