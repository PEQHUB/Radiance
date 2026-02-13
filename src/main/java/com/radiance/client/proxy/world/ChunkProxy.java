package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.mojang.blaze3d.systems.VertexSorter;
import com.radiance.client.constant.Constants;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderBuiltChunkExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IChunkBuilderExt;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
    private static final int numNormalChunkRebuildThreads = 1;
    private static final int numImportantChunkRebuildThreads = 1;
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
    }

    private static void resetWorldLoadSmoothing() {
        smoothImportantUntilNanos = System.nanoTime() + worldLoadSmoothDurationNanos;
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

    public static void rebuild(Camera camera) {

        BlockPos blockPos = camera.getBlockPos();
        boolean smoothing = inWorldLoadSmoothingWindow();
        int maxImportantTasksPerFrame = smoothing ?
            maxImportantTasksPerFrameWarmup :
            maxImportantTasksPerFrameNormal;
        double importantDistanceSq = smoothing ? importantDistanceSqWarmup : importantDistanceSqNormal;
        int importantTaskCount = 0;

        for (ChunkBuilder.BuiltChunk builtChunk : rebuildQueue.values()) {
            if (builtChunk == null) {
                continue;
            }

            if (builtChunk.needsRebuild() && builtChunk.shouldBuild()) {
                builtChunk.cancelRebuild();

                BlockPos
                    chunkCenterPos =
                    builtChunk.getOrigin()
                        .add(8, 8, 8);
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
            }
        }

        rebuildQueue.clear();
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

            BlockBufferAllocatorStorage storage = blockBufferAllocatorStorageThreadLocal.get();
            rebuildSingle(chunkRendererRegion, chunkBuilder, chunkBuilderExt, builtChunk, storage,
                important);
        } catch (Exception e) {
            throw new RuntimeException(e);
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

        SectionBuilder.RenderData renderData;
        synchronized (ChunkBuilder.class) {
            renderData =
                ((IChunkBuilderExt) chunkBuilder).neoVoxelRT$getSectionBuilder()
                    .build(chunkSectionPos, chunkRendererRegion, vertexSorter, storage);
        }

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

    public static native boolean isChunkReady(long index);

    public static boolean isChunkReady(ChunkBuilder.BuiltChunk builtChunk) {
        return isChunkReady(builtChunk.index);
    }

    public static native void invalidateSingle(long index);
}
