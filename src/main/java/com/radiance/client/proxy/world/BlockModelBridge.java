package com.radiance.client.proxy.world;

import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.material.BlockTypeIdRegistry;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.VividColorBlock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.FoliageColors;
import net.minecraft.world.biome.GrassColors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Serializes Minecraft block model data into packed binary format for C++ consumption.
 * Called once after resource reload when BakedModels are ready.
 * The C++ BlockModelTable receives this data and uses it for direct meshing.
 */
public class BlockModelBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("BlockModelBridge");
    private static boolean uploaded = false;
    private static boolean biomeTintsUploaded = false;
    private static int spriteIdHits = 0;
    private static int spriteIdMisses = 0;
    private static int spriteIdFallbacks = 0;

    // Texture array system: sequential spriteId → Identifier mapping.
    // Populated during atlas stitching (SpriteAtlasTextureMixins), consumed during
    // block model serialization to write sequential IDs into BlockModelQuad.spriteId.
    public static java.util.Map<net.minecraft.util.Identifier, Integer> spriteIdMap = new java.util.HashMap<>();
    public static int spriteArraySize = 0;  // total layers including animation frames

    // Must match C++ BlockModelEntry (32 bytes, packed)
    private static final int ENTRY_SIZE = 32;
    // Must match C++ BlockModelQuad (88 bytes, packed)
    private static final int QUAD_SIZE = 88;
    // Must match C++ BiomeTintEntry (8 bytes, packed)
    private static final int TINT_ENTRY_SIZE = 8;

    // Tint color types — must match C++ tintColorType values
    private static final byte TINT_GRASS = 0;
    private static final byte TINT_FOLIAGE = 1;
    private static final byte TINT_WATER = 2;
    private static final byte TINT_FIXED = 3;
    private static final byte TINT_NONE = (byte) 255;

    // Direction indices matching C++ expectations (Minecraft Direction.ordinal())
    // DOWN=0, UP=1, NORTH=2, SOUTH=3, WEST=4, EAST=5, UNCULLED=6
    private static final Direction[] DIRECTIONS = {
        Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
        Direction.WEST, Direction.EAST
    };

    /**
     * Serialize all block models and upload to C++.
     * Must be called when BakedModels are ready (after resource reload).
     */
    public static void serializeAndUpload() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getBakedModelManager() == null) {
            LOGGER.warn("BakedModelManager not ready, deferring model table upload");
            return;
        }

        long startTime = System.nanoTime();
        Random random = Random.create(42);

        // Collect all block states
        List<BlockState> allStates = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            allStates.addAll(block.getStateManager().getStates());
        }

        // First pass: count quads to size the buffer
        int totalQuads = 0;
        for (BlockState state : allStates) {
            if (state.getRenderType() == net.minecraft.block.BlockRenderType.INVISIBLE) continue;
            if (state.getRenderType() != net.minecraft.block.BlockRenderType.MODEL) continue;
            BakedModel model = mc.getBakedModelManager().getBlockModels().getModel(state);
            if (model == null) continue;
            for (Direction dir : DIRECTIONS) {
                totalQuads += model.getQuads(state, dir, random).size();
            }
            totalQuads += model.getQuads(state, null, random).size(); // unculled
        }

        // Allocate buffers
        ByteBuffer entryBuf = ByteBuffer.allocateDirect(allStates.size() * ENTRY_SIZE)
            .order(ByteOrder.nativeOrder());
        ByteBuffer quadBuf = ByteBuffer.allocateDirect(totalQuads * QUAD_SIZE)
            .order(ByteOrder.nativeOrder());

        int entryCount = 0;
        int quadOffset = 0; // byte offset into quadBuf

        for (BlockState state : allStates) {
            int stateId = Block.getRawIdFromState(state);
            Block block = state.getBlock();

            int entryPos = entryCount * ENTRY_SIZE;

            // globalStateId (uint32)
            entryBuf.putInt(entryPos, stateId);

            // renderType (uint8)
            byte renderType = 0;
            if (state.getRenderType() == net.minecraft.block.BlockRenderType.MODEL) {
                renderType = 1;
            }
            entryBuf.put(entryPos + 4, renderType);

            // isFullOpaqueCube (uint8)
            entryBuf.put(entryPos + 5, (byte) (state.isOpaqueFullCube() ? 1 : 0));

            // emissiveOrdinal (uint8)
            EmissiveBlock eb = EmissiveBlock.fromBlock(block);
            entryBuf.put(entryPos + 6, (byte) (eb != null ? eb.ordinal() : 255));

            // materialOrdinal (uint8)
            int matOrdinal = MaterialBlock.getOrdinalForBlock(block);
            entryBuf.put(entryPos + 7, (byte) (matOrdinal & 0xFF));

            // blockTypeId (uint16)
            int blockTypeId = BlockTypeIdRegistry.getBlockTypeId(block);
            entryBuf.putShort(entryPos + 8, (short) blockTypeId);

            // isVivid (uint8)
            entryBuf.put(entryPos + 10, (byte) (VividColorBlock.isVivid(block) ? 1 : 0));

            // fluidType (uint8): 0=none, 1=water, 2=lava
            byte fluidType = 0;
            if (block instanceof FluidBlock) {
                if (state.getFluidState().isOf(Fluids.WATER) || state.getFluidState().isOf(Fluids.FLOWING_WATER)) {
                    fluidType = 1;
                } else if (state.getFluidState().isOf(Fluids.LAVA) || state.getFluidState().isOf(Fluids.FLOWING_LAVA)) {
                    fluidType = 2;
                }
            }
            entryBuf.put(entryPos + 11, fluidType);

            // quadOffset (uint32) — byte offset into quad array
            entryBuf.putInt(entryPos + 12, quadOffset);

            // Serialize face quads per direction + detect tinted quads
            int totalQuadCount = 0;
            boolean hasTintedQuad = false;
            if (renderType == 1) {
                BakedModel model = mc.getBakedModelManager().getBlockModels().getModel(state);
                if (model != null) {
                    byte renderLayer = getRenderLayerByte(state);

                    // 6 directions + unculled — serialize vanilla model data faithfully.
                    // RT-specific optimizations (overlay handling) done in C++ mesher.
                    boolean loggedGrass = false;
                    for (int d = 0; d < 7; d++) {
                        Direction dir = d < 6 ? DIRECTIONS[d] : null;
                        List<BakedQuad> quads = model.getQuads(state, dir, random);
                        entryBuf.put(entryPos + 16 + d, (byte) quads.size());
                        totalQuadCount += quads.size();

                        for (BakedQuad quad : quads) {
                            if (quad.getTintIndex() >= 0) hasTintedQuad = true;
                            // One-time diagnostic: log grass block side quad UVs
                            if (block == Blocks.GRASS_BLOCK && d == 2 && !loggedGrass) {
                                int[] vd = quad.getVertexData();
                                float u0 = Float.intBitsToFloat(vd[4]), v0 = Float.intBitsToFloat(vd[5]);
                                float u2 = Float.intBitsToFloat(vd[20]), v2 = Float.intBitsToFloat(vd[21]);
                                LOGGER.info("GRASS NORTH d={} tintIdx={} sprite={} u0={} v0={} u2={} v2={}",
                                    d, quad.getTintIndex(),
                                    quad.getSprite() != null ? quad.getSprite().getContents().getId() : "null",
                                    u0, v0, u2, v2);
                            }
                            serializeQuad(quadBuf, quadOffset, quad, (byte) d, renderLayer,
                                          quad.getTintIndex());
                            quadOffset += QUAD_SIZE;
                        }
                        if (block == Blocks.GRASS_BLOCK && d == 2) loggedGrass = true;
                    }
                }
            } else {
                // Zero out face counts for non-model blocks
                for (int d = 0; d < 7; d++) {
                    entryBuf.put(entryPos + 16 + d, (byte) 0);
                }
            }

            // totalQuadCount (uint8)
            entryBuf.put(entryPos + 23, (byte) Math.min(totalQuadCount, 255));

            // emissionNits (float)
            float emissionNits = eb != null ? eb.getDefaultSurfaceNits() : 0.0f;
            entryBuf.putFloat(entryPos + 24, emissionNits);

            // tintColorType + fixedTintRGB (4 bytes at offset 28-31)
            if (hasTintedQuad) {
                byte tintType = classifyTintType(block, state);
                entryBuf.put(entryPos + 28, tintType);
                if (tintType == TINT_FIXED) {
                    int fixedColor = getFixedTintColor(block, state);
                    entryBuf.put(entryPos + 29, (byte) ((fixedColor >> 16) & 0xFF));
                    entryBuf.put(entryPos + 30, (byte) ((fixedColor >> 8) & 0xFF));
                    entryBuf.put(entryPos + 31, (byte) (fixedColor & 0xFF));
                } else {
                    entryBuf.put(entryPos + 29, (byte) 0);
                    entryBuf.put(entryPos + 30, (byte) 0);
                    entryBuf.put(entryPos + 31, (byte) 0);
                }
            } else {
                entryBuf.put(entryPos + 28, TINT_NONE);
                entryBuf.put(entryPos + 29, (byte) 0);
                entryBuf.put(entryPos + 30, (byte) 0);
                entryBuf.put(entryPos + 31, (byte) 0);
            }

            entryCount++;
        }

        int quadCount = quadOffset / QUAD_SIZE;

        // Upload to C++
        uploadBlockModelTable(memAddress(entryBuf), entryCount,
            memAddress(quadBuf), quadCount);

        uploaded = true;
        double elapsed = (System.nanoTime() - startTime) / 1e6;
        LOGGER.info("Block model table uploaded: {} states, {} quads, {:.1f}ms",
            entryCount, quadCount, elapsed);
        LOGGER.info("[spriteId] hits={}, misses={}, fallbacks={}, mapSize={}",
            spriteIdHits, spriteIdMisses, spriteIdFallbacks, spriteIdMap.size());
        // Log spriteIds for key blocks to cross-reference with texture array layers
        for (String key : new String[]{"minecraft:block/oak_log", "minecraft:block/oak_log_top",
                "minecraft:block/oak_leaves", "minecraft:block/stone", "minecraft:block/dirt",
                "minecraft:block/grass_block_top", "minecraft:block/grass_block_side",
                "minecraft:block/sand"}) {
            Integer id = spriteIdMap.get(net.minecraft.util.Identifier.of(key));
            LOGGER.info("[spriteId] {} -> {}", key, id != null ? id : "NOT FOUND");
        }
        spriteIdHits = 0;
        spriteIdMisses = 0;
        spriteIdFallbacks = 0;
    }

    private static void serializeQuad(ByteBuffer buf, int offset, BakedQuad quad,
                                       byte direction, byte renderLayer,
                                       int effectiveTintIndex) {
        int[] vertexData = quad.getVertexData();
        // BakedQuad vertexData: 8 ints per vertex, 4 vertices
        // Layout per vertex (8 ints = 32 bytes):
        //   0: x (float bits), 1: y (float bits), 2: z (float bits)
        //   3: color (ARGB packed)
        //   4: u (float bits), 5: v (float bits)
        //   6: packed light (unused here)
        //   7: packed normal (unused here)
        for (int v = 0; v < 4; v++) {
            int base = v * 8;
            // positions[v][3] — 12 bytes
            buf.putFloat(offset + v * 12, Float.intBitsToFloat(vertexData[base]));
            buf.putFloat(offset + v * 12 + 4, Float.intBitsToFloat(vertexData[base + 1]));
            buf.putFloat(offset + v * 12 + 8, Float.intBitsToFloat(vertexData[base + 2]));
        }
        // uvs[4][2] — 32 bytes at offset +48
        for (int v = 0; v < 4; v++) {
            int base = v * 8;
            buf.putFloat(offset + 48 + v * 8, Float.intBitsToFloat(vertexData[base + 4]));
            buf.putFloat(offset + 48 + v * 8 + 4, Float.intBitsToFloat(vertexData[base + 5]));
        }
        // tintIndex (int8) at +80 — may be overridden for grass block side quads
        buf.put(offset + 80, (byte) effectiveTintIndex);
        // direction (uint8) at +81
        buf.put(offset + 81, direction);
        // renderLayer (uint8) at +82
        buf.put(offset + 82, renderLayer);
        // shade (uint8) at +83
        buf.put(offset + 83, (byte) (quad.hasShade() ? 1 : 0));
        // spriteId (uint16) at +84 — sequential ID from texture array system if available
        Sprite sprite = quad.getSprite();
        int spriteId;
        if (sprite != null && !spriteIdMap.isEmpty()) {
            Integer seqId = spriteIdMap.get(sprite.getContents().getId());
            if (seqId != null) {
                spriteId = seqId;
                spriteIdHits++;
                if (spriteIdHits <= 3) {
                    LOGGER.info("[spriteId] HIT: {} -> spriteId={}", sprite.getContents().getId(), spriteId);
                }
            } else {
                spriteId = 0;
                spriteIdMisses++;
                if (spriteIdMisses <= 5) {
                    LOGGER.warn("[spriteId] MISS for sprite: {} (key type: {})",
                        sprite.getContents().getId(), sprite.getContents().getId().getClass().getName());
                }
            }
        } else {
            spriteId = sprite != null ? sprite.getContents().getId().hashCode() & 0xFFFF : 0;
            spriteIdFallbacks++;
        }
        buf.putShort(offset + 84, (short) spriteId);
        // padding (uint16) at +86
        buf.putShort(offset + 86, (short) 0);
    }

    private static byte getRenderLayerByte(BlockState state) {
        var layer = RenderLayers.getBlockLayer(state);
        if (layer == net.minecraft.client.render.RenderLayer.getSolid()) return 0;
        if (layer == net.minecraft.client.render.RenderLayer.getCutout()) return 1;
        if (layer == net.minecraft.client.render.RenderLayer.getCutoutMipped()) return 2;
        if (layer == net.minecraft.client.render.RenderLayer.getTranslucent()) return 3;
        return 0;
    }

    /**
     * Classify a block's biome tint type.
     * 0=grass, 1=foliage, 2=water, 3=fixed (non-biome-dependent color).
     */
    private static byte classifyTintType(Block block, BlockState state) {
        // Grass-colored blocks
        if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS ||
            block == Blocks.TALL_GRASS || block == Blocks.FERN ||
            block == Blocks.LARGE_FERN || block == Blocks.POTTED_FERN ||
            block == Blocks.SUGAR_CANE) {
            return TINT_GRASS;
        }

        // Foliage-colored blocks (biome-dependent)
        if (block == Blocks.OAK_LEAVES || block == Blocks.JUNGLE_LEAVES ||
            block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES ||
            block == Blocks.MANGROVE_LEAVES || block == Blocks.VINE) {
            return TINT_FOLIAGE;
        }

        // Fixed-color foliage (not biome-dependent)
        if (block == Blocks.BIRCH_LEAVES || block == Blocks.SPRUCE_LEAVES) {
            return TINT_FIXED;
        }

        // Water-colored blocks
        if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN ||
            block == Blocks.WATER_CAULDRON) {
            return TINT_WATER;
        }

        // Lily pad has a fixed green tint
        if (block == Blocks.LILY_PAD) {
            return TINT_FIXED;
        }

        // Fallback: try to detect via color provider returning a non-trivial color
        // For redstone wire, stems, etc. — treat as fixed
        return TINT_FIXED;
    }

    /**
     * Get the fixed tint color for blocks with non-biome-dependent tinting.
     * Returns RGB packed as 0x00RRGGBB.
     */
    private static int getFixedTintColor(Block block, BlockState state) {
        if (block == Blocks.BIRCH_LEAVES) return FoliageColors.BIRCH;
        if (block == Blocks.SPRUCE_LEAVES) return FoliageColors.SPRUCE;
        if (block == Blocks.LILY_PAD) return 0x208030;

        // For other blocks (redstone, stems, etc.), try the color provider with null world
        try {
            BlockColors colors = MinecraftClient.getInstance().getBlockColors();
            return colors.getColor(state, null, null, 0);
        } catch (Exception e) {
            return 0xFFFFFF; // white = no tint effect
        }
    }

    /**
     * Serialize biome tint colors and upload to C++.
     * Must be called when a world is loaded (biome registry available).
     */
    public static void serializeBiomeTints() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        Registry<Biome> biomeRegistry = mc.world.getRegistryManager()
            .getOrThrow(RegistryKeys.BIOME);

        // Count biomes and allocate: 3 entries per biome (grass, foliage, water)
        int biomeCount = 0;
        for (Biome ignored : biomeRegistry) biomeCount++;

        ByteBuffer tintBuf = ByteBuffer.allocateDirect(biomeCount * 3 * TINT_ENTRY_SIZE)
            .order(ByteOrder.nativeOrder());

        int idx = 0;
        for (RegistryEntry<Biome> entry : biomeRegistry.getIndexedEntries()) {
            if (entry == null || entry.value() == null) continue;
            Biome biome = entry.value();
            int rawId = biomeRegistry.getRawId(biome);

            // Grass color — sample at sea level
            int grassColor;
            try {
                grassColor = biome.getGrassColorAt(0.0, 0.0);
            } catch (Exception e) {
                grassColor = GrassColors.getDefaultColor();
            }

            // Foliage color
            int foliageColor;
            try {
                foliageColor = biome.getFoliageColor();
            } catch (Exception e) {
                foliageColor = FoliageColors.DEFAULT;
            }

            // Water color
            int waterColor = biome.getWaterColor();

            // Serialize grass tint entry
            writeTintEntry(tintBuf, idx++, rawId, 0, grassColor);
            // Serialize foliage tint entry
            writeTintEntry(tintBuf, idx++, rawId, 1, foliageColor);
            // Serialize water tint entry
            writeTintEntry(tintBuf, idx++, rawId, 2, waterColor);
        }

        uploadBiomeTintTable(memAddress(tintBuf), idx);
        biomeTintsUploaded = true;
        LOGGER.info("Biome tint table uploaded: {} entries ({} biomes)", idx, biomeCount);
    }

    private static void writeTintEntry(ByteBuffer buf, int index, int biomeId,
                                        int tintType, int color) {
        int off = index * TINT_ENTRY_SIZE;
        buf.putShort(off, (short) biomeId);        // biomeRawId
        buf.put(off + 2, (byte) tintType);          // tintType
        buf.put(off + 3, (byte) ((color >> 16) & 0xFF)); // r
        buf.put(off + 4, (byte) ((color >> 8) & 0xFF));  // g
        buf.put(off + 5, (byte) (color & 0xFF));          // b
        buf.putShort(off + 6, (short) 0);            // padding
    }

    public static boolean isUploaded() {
        return uploaded;
    }

    public static boolean areBiomeTintsUploaded() {
        return biomeTintsUploaded;
    }

    public static void reset() {
        uploaded = false;
        biomeTintsUploaded = false;
        clearAnimatedFrameCache();
    }

    // --- Animated sprite per-tick update system ---
    // Maps spriteId (=baseLayer) → array of RGBA byte[] for each animation frame
    private static final Map<Integer, byte[][]> animatedFrameCache = new HashMap<>();
    // Maps spriteId → sprite width (=height, square)
    private static final Map<Integer, Integer> animatedSpriteSize = new HashMap<>();
    // Maps spriteId → last uploaded frame index (to skip redundant uploads)
    private static final Map<Integer, Integer> lastUploadedFrame = new HashMap<>();
    // Reusable direct ByteBuffer for animation uploads (max 128x128x4)
    private static ByteBuffer animUploadBuf = null;

    public static void registerAnimatedSprite(int spriteId, byte[][] frames, int size) {
        animatedFrameCache.put(spriteId, frames);
        animatedSpriteSize.put(spriteId, size);
        lastUploadedFrame.put(spriteId, 0); // frame 0 uploaded during extraction
    }

    public static void clearAnimatedFrameCache() {
        animatedFrameCache.clear();
        animatedSpriteSize.clear();
        lastUploadedFrame.clear();
    }

    /**
     * Called each render frame from BufferProxy. For each animated sprite,
     * computes the current animation frame and re-uploads pixel data if it changed.
     * Animation rate: ~3 render frames per animation advance (matching Minecraft's 20 ticks/sec at 60fps).
     */
    public static void updateAnimatedSprites(int animTick) {
        if (animatedFrameCache.isEmpty()) return;

        // Animation advance rate: every 3 render frames ≈ 20 fps animation at 60 fps
        int tickRate = 3;

        for (Map.Entry<Integer, byte[][]> entry : animatedFrameCache.entrySet()) {
            int spriteId = entry.getKey();
            byte[][] frames = entry.getValue();
            int frameCount = frames.length;

            int currentFrame = (animTick / tickRate) % frameCount;
            Integer last = lastUploadedFrame.get(spriteId);
            if (last != null && last == currentFrame) continue;

            // Frame changed — upload new frame pixels to this sprite's single layer
            int size = animatedSpriteSize.get(spriteId);
            int frameBytes = size * size * 4;

            if (animUploadBuf == null || animUploadBuf.capacity() < frameBytes) {
                animUploadBuf = ByteBuffer.allocateDirect(frameBytes).order(ByteOrder.nativeOrder());
            }
            animUploadBuf.clear();
            animUploadBuf.put(frames[currentFrame]);
            animUploadBuf.flip();

            updateSpriteLayer(memAddress(animUploadBuf), spriteId, size, size);
            lastUploadedFrame.put(spriteId, currentFrame);
        }
    }

    // JNI native methods
    private static native void uploadBlockModelTable(long entryPtr, int entryCount,
        long quadPtr, int quadCount);
    private static native void uploadBiomeTintTable(long tintPtr, int tintCount);
    private static native void uploadSpriteBounds(long boundsPtr, int boundsCount);

    // Texture array system JNI
    private static native void uploadSpritePixels(long pixelPtr, int spriteId,
        int width, int height, int mipLevel, int frameIndex);
    private static native void finalizeTextureArrays(int totalBlockLayers,
        int specularLayers, int normalLayers, int spriteSize, int albedoFormat);
    private static native void updateSpriteLayer(long pixelPtr, int spriteId,
        int width, int height);

    // Public wrappers called from SpriteAtlasTextureMixins
    public static void nativeUploadSpritePixels(long pixelPtr, int spriteId,
        int width, int height, int mipLevel, int frameIndex) {
        uploadSpritePixels(pixelPtr, spriteId, width, height, mipLevel, frameIndex);
    }
    public static void nativeFinalize(int totalBlockLayers, int spriteSize) {
        finalizeTextureArrays(totalBlockLayers, 0, 0, spriteSize, 43 /* VK_FORMAT_R8G8B8A8_SRGB */);
    }
    public static void nativeUploadSpriteBounds(long boundsPtr, int boundsCount) {
        uploadSpriteBounds(boundsPtr, boundsCount);
    }
}
