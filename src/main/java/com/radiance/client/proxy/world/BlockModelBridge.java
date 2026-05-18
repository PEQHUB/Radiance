package com.radiance.client.proxy.world;

import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.material.BlockTypeIdRegistry;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.VividColorBlock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
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

    // Texture generation counter — incremented on each texture atlas reload.
    // Used by ChunkProxy to tag chunk build tasks with the generation they were built for,
    // so the C++ side can detect stale chunks after a resource pack change.
    private static long activeTextureGeneration = 0;

    public static long getActiveTextureGeneration() {
        return activeTextureGeneration;
    }

    public static void incrementTextureGeneration() {
        activeTextureGeneration++;
    }

    // Texture system: sorted sprite identifier list, populated during atlas stitching
    // (SpriteAtlasTextureMixins). spriteId = index in this sorted list.
    // Used during block model serialization to write deterministic spriteIds.
    public static java.util.List<net.minecraft.util.Identifier> sortedSpriteIds = new java.util.ArrayList<>();
    // Reverse lookup: Identifier → spriteId (built from sortedSpriteIds)
    private static java.util.Map<net.minecraft.util.Identifier, Integer> spriteIdLookup = new java.util.HashMap<>();
    // Material mapping: spriteId ↔ MaterialBlock ordinal (for CPU Auto-PBR bake + live re-upload)
    public static java.util.Map<Integer, Integer> spriteId2MaterialOrdinal = new java.util.HashMap<>();
    public static java.util.Map<Integer, java.util.Set<Integer>> materialOrdinal2SpriteIds = new java.util.HashMap<>();

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

        // Build reverse lookup from sorted sprite list (populated during atlas load)
        spriteIdLookup.clear();
        for (int i = 0; i < sortedSpriteIds.size(); i++) {
            spriteIdLookup.put(sortedSpriteIds.get(i), i);
        }
        LOGGER.info("[TextureSystem] Built spriteId lookup: {} entries", spriteIdLookup.size());

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

            // renderType (uint8): 0=invisible, 1=model, 2=fluid
            byte renderType = 0;
            if (block instanceof FluidBlock) {
                renderType = 2;
            } else if (state.getRenderType() == net.minecraft.block.BlockRenderType.MODEL) {
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
            // For fluid blocks: repurposed as packed sprite IDs (low16=still, high16=flowing)
            if (fluidType != 0) {
                net.minecraft.util.Identifier stillId = fluidType == 1
                    ? net.minecraft.util.Identifier.ofVanilla("block/water_still")
                    : net.minecraft.util.Identifier.ofVanilla("block/lava_still");
                net.minecraft.util.Identifier flowId = fluidType == 1
                    ? net.minecraft.util.Identifier.ofVanilla("block/water_flow")
                    : net.minecraft.util.Identifier.ofVanilla("block/lava_flow");
                int stillSprite = spriteIdLookup.getOrDefault(stillId, 0);
                int flowSprite = spriteIdLookup.getOrDefault(flowId, 0);
                entryBuf.putInt(entryPos + 12, (stillSprite & 0xFFFF) | ((flowSprite & 0xFFFF) << 16));
            } else {
                entryBuf.putInt(entryPos + 12, quadOffset);
            }

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
            // For fluid blocks: repurposed as fluidLevel (0=falling, 1-7=flowing, 8=source)
            if (fluidType != 0) {
                entryBuf.put(entryPos + 23, (byte) state.getFluidState().getLevel());
            } else {
                entryBuf.put(entryPos + 23, (byte) Math.min(totalQuadCount, 255));
            }

            // emissionNits (float)
            float emissionNits = eb != null ? eb.getDefaultSurfaceNits() : 0.0f;
            entryBuf.putFloat(entryPos + 24, emissionNits);

            // tintColorType + fixedTintRGB (4 bytes at offset 28-31)
            // For water fluids: force tintColorType=2 (TINT_WATER) since fluids have no quads with tintIndex
            if (fluidType == 1) {
                entryBuf.put(entryPos + 28, TINT_WATER);
                entryBuf.put(entryPos + 29, (byte) 0);
                entryBuf.put(entryPos + 30, (byte) 0);
                entryBuf.put(entryPos + 31, (byte) 0);
            } else if (fluidType == 2) {
                entryBuf.put(entryPos + 28, TINT_NONE);
                entryBuf.put(entryPos + 29, (byte) 0);
                entryBuf.put(entryPos + 30, (byte) 0);
                entryBuf.put(entryPos + 31, (byte) 0);
            } else if (hasTintedQuad) {
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

            // Waterlogged detection: non-FluidBlock with non-empty fluid state.
            // Set fluidType AFTER model serialization so quadOffset/totalQuadCount/tint keep model values.
            // C++ mesher uses table-stored water sprite IDs and source level for these.
            if (fluidType == 0 && !state.getFluidState().isEmpty()) {
                if (state.getFluidState().isOf(Fluids.WATER) || state.getFluidState().isOf(Fluids.FLOWING_WATER)) {
                    entryBuf.put(entryPos + 11, (byte) 1);
                }
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
        // spriteId (uint16) at +84 — deterministic sorted index from TextureSystem
        Sprite sprite = quad.getSprite();
        int spriteId = 0;
        if (sprite != null && !spriteIdLookup.isEmpty()) {
            Integer id = spriteIdLookup.get(sprite.getContents().getId());
            if (id != null) {
                spriteId = id;
            }
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

    public static void markForReupload() {
        uploaded = false;
    }

    public static void reset() {
        uploaded = false;
        biomeTintsUploaded = false;
        // Don't clear sortedSpriteIds or spriteIdLookup — they stay valid
        // until atomically replaced by the next extractSpritesForTextureArrays().
        // Clearing them creates a race with chunk building threads.
        spriteId2MaterialOrdinal = new java.util.HashMap<>();
        materialOrdinal2SpriteIds = new java.util.HashMap<>();
    }

    /**
     * Serialize the complete BlockState → string registry for C++ region file loading.
     * Maps every globalStateId to its canonical string form (e.g., "minecraft:stone"
     * or "minecraft:oak_stairs[facing=north,half=top,shape=straight]").
     *
     * Called once alongside serializeAndUpload(). C++ uses this to decode Anvil region
     * file palettes into globalStateIds for the BlockModelTable / BlockMesher.
     */
    public static void serializeBlockStateRegistry() {
        long startTime = System.nanoTime();

        List<BlockState> allStates = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            allStates.addAll(block.getStateManager().getStates());
        }

        // First pass: compute total buffer size
        // Format: [uint32 count][entries...] where entry = [uint32 stateId][uint16 strLen][utf8 string]
        int totalSize = 4; // count header
        List<String> stateStrings = new ArrayList<>(allStates.size());
        for (BlockState state : allStates) {
            String canonical = getCanonicalString(state);
            stateStrings.add(canonical);
            totalSize += 4 + 2 + canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }

        ByteBuffer buf = ByteBuffer.allocateDirect(totalSize).order(ByteOrder.nativeOrder());
        buf.putInt(allStates.size());

        for (int i = 0; i < allStates.size(); i++) {
            BlockState state = allStates.get(i);
            int stateId = Block.getRawIdFromState(state);
            byte[] strBytes = stateStrings.get(i).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buf.putInt(stateId);
            buf.putShort((short) strBytes.length);
            buf.put(strBytes);
        }

        buf.position(0); // Reset position — memAddress returns addr+position
        nativeUploadBlockStateRegistry(memAddress(buf), totalSize);

        double elapsed = (System.nanoTime() - startTime) / 1e6;
        LOGGER.info("Block state registry uploaded: {} states, {:.1f}ms", allStates.size(), elapsed);
    }

    /**
     * Build canonical string for a block state, matching Minecraft's NBT palette format.
     * Default state: "minecraft:stone"
     * With properties: "minecraft:oak_stairs[facing=north,half=top,shape=straight]"
     * Properties are sorted alphabetically by key.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String getCanonicalString(BlockState state) {
        net.minecraft.util.Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        String name = blockId.toString();

        var entries = state.getEntries();
        if (entries.isEmpty()) return name;

        StringBuilder sb = new StringBuilder(name);
        sb.append('[');
        boolean first = true;
        // state.getEntries() returns an ImmutableSortedMap — already sorted by property name
        for (var entry : entries.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(entry.getKey().getName());
            sb.append('=');
            // Use raw Property to avoid generic type bound issues
            net.minecraft.state.property.Property prop =
                (net.minecraft.state.property.Property) entry.getKey();
            sb.append(prop.name(entry.getValue()));
        }
        sb.append(']');
        return sb.toString();
    }

    // ---- JNI native methods ----

    // Block model table (unchanged)
    private static native void uploadBlockModelTable(long entryPtr, int entryCount,
        long quadPtr, int quadCount);
    private static native void uploadBiomeTintTable(long tintPtr, int tintCount);
    private static native void nativeUploadBlockStateRegistry(long dataPtr, int dataSize);

    // TextureSystem: C++-owned texture pipeline
    public static native void nativeReceiveSpriteTable(long metaPtr, int count,
        int atlasWidth, int atlasHeight);
    public static native void nativeReceiveSpritePixels(long dataPtr, int totalBytes);
    public static native void nativeReceiveAnimationFrames(long dataPtr, int totalBytes);
    public static native void nativeTextureFinalize();
    public static native void nativeTickAnimation(int gameTick);
    public static native void nativeReceiveSpriteAuxPixels(
        long specularDataPtr, long normalDataPtr, int totalBytesPerType);
    public static native void nativeUpdateSpecularLayer(int spriteId, long pixelPtr, int sizeBytes);
    public static native void nativeUpdateNormalLayer(int spriteId, long pixelPtr, int sizeBytes);

    /**
     * Called per game tick from BufferProxy. C++ owns animation — just forward the tick.
     */
    public static void updateAnimatedSprites(int animTick) {
        nativeTickAnimation(animTick);
    }
}
