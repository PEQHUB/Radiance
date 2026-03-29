package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.util.ChunkLightCollector;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.client.render.chunk.SectionBuilder;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.lwjgl.system.MemoryStack;

public class ChunkProxy {

    public static final ChunkBuilder.ChunkData PROCESSED = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    public static final ChunkBuilder.ChunkData TERRAIN_EMPTY = new ChunkBuilder.ChunkData() {
        @Override
        public boolean isVisibleThrough(Direction from, Direction to) {
            return false;
        }
    };
    private static final Map<Integer, ChunkBuilder.BuiltChunk> rebuildQueue = new ConcurrentHashMap<>();
    private static final List<Future<?>> rebuildTasks = new ArrayList<>();
    private static final int numNormalChunkRebuildThreads = 2;
    private static final int numImportantChunkRebuildThreads = 2;
    private static final long worldLoadSmoothDurationNanos = TimeUnit.SECONDS.toNanos(4);
    private static final int maxImportantTasksPerFrameWarmup = 1;
    private static final int maxImportantTasksPerFrameNormal = 1;
    private static final double importantDistanceSqWarmup = 256.0;
    private static final double importantDistanceSqNormal = 768.0;
    private static volatile long smoothImportantUntilNanos = 0L;
    private static final ExecutorService
        importantChunkRebuildExecutor =
        Executors.newFixedThreadPool(numImportantChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });
    private static final ThreadLocal<BlockBufferAllocatorStorage>
        blockBufferAllocatorStorageThreadLocal =
        ThreadLocal.withInitial(BlockBufferAllocatorStorage::new);
    public static int builtChunkNum = 0;
    private static ExecutorService backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(
        numNormalChunkRebuildThreads, r -> {
            Thread thread = new Thread(r);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        });

    public static native void initNative(int numChunks);

    public static void init(int numChunks) {
        clear();
        resetWorldLoadSmoothing();
        initNative(numChunks);

        // Serialize block model data to C++ for future C++ meshing.
        // Must run after BakedModels are loaded (guaranteed by this point in world init).
        if (!BlockModelBridge.isUploaded()) {
            BlockModelBridge.serializeAndUpload();
        }
    }

    private static void resetWorldLoadSmoothing() {
        smoothImportantUntilNanos = System.nanoTime() + worldLoadSmoothDurationNanos;
        // Reset exposure adaptation timer so it adapts near-instantly as chunks load
        try { com.radiance.client.option.Options.nativeResetExposureAdaptation(); } catch (Exception ignored) {}
    }

    private static boolean inWorldLoadSmoothingWindow() {
        return System.nanoTime() < smoothImportantUntilNanos;
    }

    public static AutoCloseable scopedBlockBufferAllocatorStorage() {
        final BlockBufferAllocatorStorage s = blockBufferAllocatorStorageThreadLocal.get();
        s.reset();
        return s::clear;
    }

    public static void clear() {
        waitImportantChunkRebuild();

        backgroundChunkRebuildExecutor.shutdown();
        try {
            backgroundChunkRebuildExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        backgroundChunkRebuildExecutor = Executors.newFixedThreadPool(numNormalChunkRebuildThreads,
            r -> {
                Thread thread = new Thread(r);
                thread.setPriority(Thread.NORM_PRIORITY);
                return thread;
            });

        rebuildQueue.clear();
        rebuildTasks.clear();
    }

    public static void enqueueRebuild(ChunkBuilder.BuiltChunk chunk) {
        rebuildQueue.put(chunk.index, chunk);
    }

    public static int getRebuildQueueSize() {
        return rebuildQueue.size();
    }

    public static void rebuild(Camera camera) {

        BlockPos blockPos = camera.getBlockPos();
        boolean smoothing = inWorldLoadSmoothingWindow();
        int maxImportantTasksPerFrame = smoothing ?
            maxImportantTasksPerFrameWarmup :
            maxImportantTasksPerFrameNormal;
        double importantDistanceSq = smoothing ? importantDistanceSqWarmup : importantDistanceSqNormal;
        int importantTaskCount = 0;

        int maxTotalPerFrame = smoothing ? 32 : 64;
        int totalSubmitted = 0;

        final double bx = blockPos.getX(), by = blockPos.getY(), bz = blockPos.getZ();

        // O(n) partial selection: pick the nearest maxTotalPerFrame chunks by squared
        // distance, avoiding the O(n log n) full sort + sqrt/atan2 that was costing 30-40ms.
        // Use a bounded max-heap: maintain the K nearest candidates seen so far.
        // For each chunk, compute distSq (cheap). If heap is not full or distSq < heap max,
        // insert and evict the farthest. Result: K nearest chunks in O(n log K) with no
        // transcendental math. K=64, log(64)=6, so this is effectively O(n).
        // Bounded max-heap: track K nearest chunks by squared distance.
        // O(n log K) where K=64 — effectively O(n) vs the old O(n log n) full sort.
        // No sqrt/atan2 — just distSq comparisons.
        java.util.PriorityQueue<double[]> nearest = new java.util.PriorityQueue<>(
            maxTotalPerFrame + 1, (a, b) -> Double.compare(b[1], a[1])); // max-heap by distSq
        java.util.HashMap<Integer, ChunkBuilder.BuiltChunk> candidateMap = new java.util.HashMap<>();

        java.util.Iterator<ChunkBuilder.BuiltChunk> iter = rebuildQueue.values().iterator();
        while (iter.hasNext()) {
            ChunkBuilder.BuiltChunk builtChunk = iter.next();
            if (builtChunk == null) { iter.remove(); continue; }
            if (!builtChunk.needsRebuild()) { iter.remove(); continue; }
            if (!builtChunk.shouldBuild()) continue; // keep — may become buildable later
            double cx = builtChunk.getOrigin().getX() + 8 - bx;
            double cy = builtChunk.getOrigin().getY() + 8 - by;
            double cz = builtChunk.getOrigin().getZ() + 8 - bz;
            double distSq = cx * cx + cy * cy + cz * cz;
            int key = builtChunk.index;
            if (nearest.size() < maxTotalPerFrame) {
                nearest.add(new double[]{key, distSq});
                candidateMap.put(key, builtChunk);
            } else {
                double worstDistSq = nearest.peek()[1];
                if (distSq < worstDistSq) {
                    double[] evicted = nearest.poll();
                    candidateMap.remove((int) evicted[0]);
                    nearest.add(new double[]{key, distSq});
                    candidateMap.put(key, builtChunk);
                }
            }
        }

        // Drain heap in nearest-first order (smallest distSq first)
        java.util.ArrayList<double[]> selected = new java.util.ArrayList<>(nearest);
        selected.sort((a, b) -> Double.compare(a[1], b[1]));

        for (double[] entry : selected) {
            ChunkBuilder.BuiltChunk builtChunk = candidateMap.get((int) entry[0]);
            if (builtChunk == null) continue;

            builtChunk.cancelRebuild();

            BlockPos chunkCenterPos = builtChunk.getOrigin().add(8, 8, 8);
            boolean forceImportant = builtChunk.needsImportantRebuild();
            boolean shouldPrioritize = forceImportant ||
                chunkCenterPos.getSquaredDistance(blockPos) < importantDistanceSq;

            boolean isImportant = shouldPrioritize &&
                (forceImportant || importantTaskCount < maxImportantTasksPerFrame);

            if (isImportant) {
                Future<?> rebuildTask = importantChunkRebuildExecutor.submit(() -> {
                    rebuildSingle(builtChunk, true);
                });
                rebuildTasks.add(rebuildTask);
                importantTaskCount++;
            } else {
                backgroundChunkRebuildExecutor.execute(() -> {
                    rebuildSingle(builtChunk, false);
                });
            }

            rebuildQueue.remove(builtChunk.index);
            totalSubmitted++;
        }
    }

    public static void waitImportantChunkRebuild() {
        if (rebuildTasks.isEmpty()) {
            return;
        }

        for (Future<?> rebuildTask : rebuildTasks) {
            try {
                rebuildTask.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        rebuildTasks.clear();
    }

    private static void rebuildSingle(ChunkBuilder.BuiltChunk builtChunk, boolean important) {
        try (var scope = scopedBlockBufferAllocatorStorage()) {
            ChunkRendererRegionBuilder chunkRendererRegionBuilder = new ChunkRendererRegionBuilder();
            IChunkBuilderBuiltChunkExt builtChunkExt = (IChunkBuilderBuiltChunkExt) builtChunk;
            ChunkBuilder chunkBuilder = builtChunkExt.neoVoxelRT$getChunkBuilder();
            IChunkBuilderExt chunkBuilderExt = (IChunkBuilderExt) chunkBuilder;
            ChunkRendererRegion
                chunkRendererRegion =
                chunkRendererRegionBuilder.build(chunkBuilderExt.neoVoxelRT$getWorld(),
                    ChunkSectionPos.from(builtChunk.getSectionPos()));

            if (chunkRendererRegion == null) {
                invalidateSingle(builtChunk.index);
                builtChunk.data.set(ChunkBuilder.ChunkData.EMPTY);
                return;
            }

            // Try C++ meshing path if model table is loaded
            if (BlockModelBridge.isUploaded()) {
                rebuildSingleCpp(chunkRendererRegion, builtChunk, important);
                return;
            }

            // Fallback: Java meshing path
            BlockBufferAllocatorStorage storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, builtChunk, storage,
                important);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * C++ meshing path: extract block states from ChunkRendererRegion, send to C++ for meshing.
     * Sends ~12KB of block state data instead of ~200-500KB of pre-meshed vertices.
     */
    private static void rebuildSingleCpp(ChunkRendererRegion region,
                                          ChunkBuilder.BuiltChunk builtChunk,
                                          boolean important) {
        BlockPos origin = builtChunk.getOrigin();
        int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();

        try (MemoryStack stack = stackPush()) {
            // Extract block states: 4096 x uint16 palette indices + palette array
            // For simplicity, we send pre-decoded global state IDs as uint16 (state IDs fit in 16 bits)
            ByteBuffer statesBuf = stack.malloc(4096 * Short.BYTES);
            long statesAddr = memAddress(statesBuf);

            // Build palette on-the-fly: map unique states to indices
            java.util.ArrayList<Integer> palette = new java.util.ArrayList<>();
            java.util.HashMap<Integer, Integer> paletteMap = new java.util.HashMap<>();
            boolean hasFluid = false;
            boolean hasBlockEntity = false;

            // Also build occlusion data for vanilla compat
            net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder occlusionBuilder =
                new net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder();
            java.util.List<net.minecraft.block.entity.BlockEntity> blockEntities = new java.util.ArrayList<>();
            java.util.List<net.minecraft.block.entity.BlockEntity> noCullingBlockEntities = new java.util.ArrayList<>();

            BlockPos.Mutable mutablePos = new BlockPos.Mutable();
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        mutablePos.set(ox + x, oy + y, oz + z);
                        BlockState state = region.getBlockState(mutablePos);
                        int rawId = Block.getRawIdFromState(state);

                        // Palette encode
                        Integer paletteIdx = paletteMap.get(rawId);
                        if (paletteIdx == null) {
                            paletteIdx = palette.size();
                            palette.add(rawId);
                            paletteMap.put(rawId, paletteIdx);
                        }
                        statesBuf.putShort((y * 256 + z * 16 + x) * Short.BYTES, paletteIdx.shortValue());

                        // Track occlusion
                        if (state.isOpaqueFullCube()) {
                            occlusionBuilder.markClosed(mutablePos);
                        }

                        // Track block entities
                        if (state.hasBlockEntity()) {
                            net.minecraft.block.entity.BlockEntity be = region.getBlockEntity(mutablePos);
                            if (be != null) {
                                blockEntities.add(be);
                            }
                        }

                        // Track fluids (C++ mesher doesn't handle these yet)
                        if (!state.getFluidState().isEmpty()) {
                            hasFluid = true;
                        }
                    }
                }
            }

            // Fluids: C++ mesher handles solid blocks; Java path handles fluids separately.
            // Previously, ANY fluid in a section forced full Java fallback — causing textureID
            // corruption (Java uses GLIDs, C++ uses spriteIds, shader expects spriteIds).
            // Now: always use C++ for solids. Fluids are rendered via entity/particle path.

            // Palette array: uint32 per entry
            ByteBuffer paletteBuf = stack.malloc(palette.size() * Integer.BYTES);
            long paletteAddr = memAddress(paletteBuf);
            for (int i = 0; i < palette.size(); i++) {
                paletteBuf.putInt(i * Integer.BYTES, palette.get(i));
            }

            // Upload biome tint table on first use (needs world reference)
            if (!BlockModelBridge.areBiomeTintsUploaded()) {
                BlockModelBridge.serializeBiomeTints();
            }

            // Biome data: 64 x uint16 (4x4x4 grid within section)
            ByteBuffer biomeBuf = stack.malloc(64 * Short.BYTES);
            long biomeAddr = memAddress(biomeBuf);
            var world = MinecraftClient.getInstance().world;
            var biomeRegistry = world.getRegistryManager()
                .getOrThrow(RegistryKeys.BIOME);
            for (int by = 0; by < 4; by++) {
                for (int bz = 0; bz < 4; bz++) {
                    for (int bx = 0; bx < 4; bx++) {
                        // Sample biome at center of each 4x4x4 cell
                        mutablePos.set(ox + bx * 4 + 2, oy + by * 4 + 2, oz + bz * 4 + 2);
                        RegistryEntry<Biome> biomeEntry = world.getBiome(mutablePos);
                        int biomeRawId = biomeRegistry.getRawId(biomeEntry.value());
                        biomeBuf.putShort((by * 16 + bz * 4 + bx) * Short.BYTES, (short) biomeRawId);
                    }
                }
            }

            // Neighbor face states: 6 directions x 256 x uint32
            // Each face stores the 16x16 block states of the adjacent section face
            ByteBuffer neighborBuf = stack.malloc(6 * 256 * Integer.BYTES);
            long neighborAddr = memAddress(neighborBuf);
            for (int dir = 0; dir < 6; dir++) {
                for (int i = 0; i < 256; i++) {
                    int nx, ny, nz;
                    switch (dir) {
                        case 0: // DOWN: z*16+x at y=-1
                            nx = ox + (i % 16); ny = oy - 1; nz = oz + (i / 16); break;
                        case 1: // UP: z*16+x at y=16
                            nx = ox + (i % 16); ny = oy + 16; nz = oz + (i / 16); break;
                        case 2: // NORTH: y*16+x at z=-1
                            nx = ox + (i % 16); ny = oy + (i / 16); nz = oz - 1; break;
                        case 3: // SOUTH: y*16+x at z=16
                            nx = ox + (i % 16); ny = oy + (i / 16); nz = oz + 16; break;
                        case 4: // WEST: y*16+z at x=-1
                            nx = ox - 1; ny = oy + (i / 16); nz = oz + (i % 16); break;
                        default: // EAST: y*16+z at x=16
                            nx = ox + 16; ny = oy + (i / 16); nz = oz + (i % 16); break;
                    }
                    mutablePos.set(nx, ny, nz);
                    try {
                        BlockState neighborState = region.getBlockState(mutablePos);
                        neighborBuf.putInt((dir * 256 + i) * Integer.BYTES,
                            Block.getRawIdFromState(neighborState));
                    } catch (Exception e) {
                        neighborBuf.putInt((dir * 256 + i) * Integer.BYTES, 0); // air
                    }
                }
            }

            // Get block atlas texture ID
            int blockAtlasTextureId = MinecraftClient.getInstance()
                .getTextureManager()
                .getTexture(net.minecraft.client.texture.SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)
                .getGlId();

            // Resolve dominant biome colors for shader-side tinting (sample center of section)
            mutablePos.set(ox + 8, oy + 8, oz + 8);
            RegistryEntry<Biome> centerBiome = world.getBiome(mutablePos);
            Biome biome = centerBiome.value();
            int grassColor, foliageColor, waterColor;
            try { grassColor = biome.getGrassColorAt(ox + 8, oz + 8); }
            catch (Exception e) { grassColor = 0x91BD59; }
            try { foliageColor = biome.getFoliageColor(); }
            catch (Exception e) { foliageColor = 0x77AB2F; }
            waterColor = biome.getWaterColor();

            // Call C++ meshing path
            rebuildSingleBlockStates(ox, oy, oz, builtChunk.index,
                statesAddr, paletteAddr, palette.size(),
                biomeAddr, neighborAddr, blockAtlasTextureId, important,
                grassColor, foliageColor, waterColor);

            // Set vanilla chunk data (occlusion, block entities)
            final var occlusionData = occlusionBuilder.build();
            final var finalBlockEntities = blockEntities;
            ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
                @Override
                public List<BlockEntity> getBlockEntities() {
                    return finalBlockEntities;
                }

                @Override
                public boolean isVisibleThrough(Direction from, Direction to) {
                    return occlusionData.isVisibleThrough(from, to);
                }

                @Override
                public boolean isEmpty(RenderLayer layer) {
                    return false;
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;
        }
    }

    private static void rebuildSingle(ChunkRendererRegion chunkRendererRegion,
        ChunkBuilder chunkBuilder,
        IChunkBuilderExt chunkBuilderExt,
        ChunkBuilder.BuiltChunk builtChunk,
        BlockBufferAllocatorStorage storage,
        boolean important) {

        ChunkSectionPos chunkSectionPos = ChunkSectionPos.from(builtChunk.getOrigin());

        Vec3d vec3d = chunkBuilder.getCameraPosition();
        // TODO: cancel out the sort operation in section builder
        VertexSorter
            vertexSorter =
            VertexSorter.byDistance((float) (vec3d.x - builtChunk.getOrigin()
                    .getX()),
                (float) (vec3d.y - builtChunk.getOrigin()
                    .getY()),
                (float) (vec3d.z - builtChunk.getOrigin()
                    .getZ()));

        ChunkLightCollector.begin();
        SectionBuilder.RenderData renderData;
        synchronized (ChunkBuilder.class) {
            renderData =
                ((IChunkBuilderExt) chunkBuilder).neoVoxelRT$getSectionBuilder()
                    .build(chunkSectionPos, chunkRendererRegion, vertexSorter, storage);
        }
        Map<BlockPos, ChunkLightCollector.LightEntry> collectedLights = ChunkLightCollector.end();

        Map<RenderLayer, BuiltBuffer> buffers = renderData.buffers;
        builtChunk.setNoCullingBlockEntities(renderData.noCullingBlockEntities);

        if (buffers.isEmpty()) {
            ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
                @Override
                public List<BlockEntity> getBlockEntities() {
                    return renderData.blockEntities;
                }

                @Override
                public boolean isVisibleThrough(Direction from, Direction to) {
                    return renderData.chunkOcclusionData.isVisibleThrough(from, to);
                }

                @Override
                public boolean isEmpty(RenderLayer layer) {
                    return true;
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;

            invalidateSingle(builtChunk.index);
            setChunkLights(builtChunk.index, 0, 0);
        } else {
            ChunkBuilder.ChunkData chunkData = new ChunkBuilder.ChunkData() {
                @Override
                public List<BlockEntity> getBlockEntities() {
                    return renderData.blockEntities;
                }

                @Override
                public boolean isVisibleThrough(Direction from, Direction to) {
                    return renderData.chunkOcclusionData.isVisibleThrough(from, to);
                }

                @Override
                public boolean isEmpty(RenderLayer layer) {
                    return false;
                }
            };
            builtChunk.data.set(chunkData);
            builtChunkNum++;

            try (MemoryStack stack = stackPush()) {
                int geometryTypeSize = buffers.size() * Integer.BYTES;
                ByteBuffer geometryTypeBB = stack.malloc(geometryTypeSize);
                long geometryTypeAddr = memAddress(geometryTypeBB);
                int geometryTypeBaseAddr = 0;

                int geometryTextureSize = buffers.size() * Integer.BYTES;
                ByteBuffer geometryTextureBB = stack.malloc(geometryTextureSize);
                long geometryTextureAddr = memAddress(geometryTextureBB);
                int geometryTextureBaseAddr = 0;

                int vertexFormatSize = buffers.size() * Integer.BYTES;
                ByteBuffer vertexFormatBB = stack.malloc(vertexFormatSize);
                long vertexFormatAddr = memAddress(vertexFormatBB);
                int vertexFormatBaseAddr = 0;

                int vertexCountSize = buffers.size() * Integer.BYTES;
                ByteBuffer vertexCountBB = stack.malloc(vertexCountSize);
                long vertexCountAddr = memAddress(vertexCountBB);
                int vertexCountBaseAddr = 0;

                int verticesSize = buffers.size() * Long.BYTES;
                ByteBuffer verticesBB = stack.malloc(verticesSize);
                long verticesAddr = memAddress(verticesBB);
                int verticesBaseAddr = 0;

                for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
                    RenderLayer renderLayer = entry.getKey();
                    assert renderLayer.getDrawMode() == QUADS;

                    BuiltBuffer vertexBuffer = entry.getValue();
                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                        vertexBuffer.getBuffer());
                    assert vertexBuffer.getDrawParameters()
                        .indexCount() == vertexBuffer.getDrawParameters()
                        .vertexCount() / 4 * 6;

                    TextureManager
                        textureManager =
                        MinecraftClient.getInstance()
                            .getTextureManager();

                    int
                        geometryTypeID =
                        Constants.GeometryTypes.getGeometryType(renderLayer, true)
                            .getValue();
                    int
                        geometryTextureID =
                        textureManager.getTexture(
                                ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                                    .orElse(MissingSprite.getMissingSpriteId()))
                            .getGlId();
                    int vertexFormatID = Constants.VertexFormats.getValue(
                        vertexBuffer.getDrawParameters()
                            .format());

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr,
                        vertexBuffer.getDrawParameters()
                            .vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }

                rebuildSingle(builtChunk.getOrigin()
                        .getX(),
                    builtChunk.getOrigin()
                        .getY(),
                    builtChunk.getOrigin()
                        .getZ(),
                    builtChunk.index,
                    buffers.size(),
                    geometryTypeAddr,
                    geometryTextureAddr,
                    vertexFormatAddr,
                    vertexCountAddr,
                    verticesAddr,
                    important);
            }

            // Pack and transmit collected light sources for this chunk
            if (!collectedLights.isEmpty()) {
                int lightCount = collectedLights.size();
                // 16 bytes per light: float x, float y, float z, int typeId
                int dataSize = lightCount * 16;
                try (MemoryStack lightStack = stackPush()) {
                    ByteBuffer lightBuf = lightStack.malloc(dataSize);
                    int offset = 0;
                    for (ChunkLightCollector.LightEntry entry : collectedLights.values()) {
                        lightBuf.putFloat(offset, entry.worldX);
                        lightBuf.putFloat(offset + 4, entry.worldY);
                        lightBuf.putFloat(offset + 8, entry.worldZ);
                        lightBuf.putInt(offset + 12, entry.typeId);
                        offset += 16;
                    }
                    setChunkLights(builtChunk.index, lightCount, memAddress(lightBuf));
                }
            } else {
                setChunkLights(builtChunk.index, 0, 0);
            }
        }

        for (Map.Entry<RenderLayer, BuiltBuffer> entry : buffers.entrySet()) {
            entry.getValue()
                .close();
        }
    }

    private static native void rebuildSingle(int originX,
        int originY,
        int originZ,
        long index,
        int size,
        long geometryTypes,
        long geometryTextures,
        long vertexFormats,
        long vertexCounts,
        long vertices,
        boolean important);

    // Phase 2: C++ meshing path — sends block states instead of pre-meshed vertices
    private static native void rebuildSingleBlockStates(int originX, int originY, int originZ,
        long index, long blockStateArrayPtr, long palettePtr, int paletteSize,
        long biomeDataPtr, long neighborFacesPtr, int blockAtlasTextureId, boolean important,
        int biomeGrassColor, int biomeFoliageColor, int biomeWaterColor);

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void invalidateSingle(long index);

    private static native void setChunkLights(long chunkIndex, int lightCount, long lightDataPtr);
}
