package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.LINES;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINE_STRIP;
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.fpv.FirstPersonView;
import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.option.Options;
import com.radiance.client.texture.compat.ResourcePackRandomEntityTextureResolver;
import com.radiance.client.util.SpectralColor;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleExt;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.particle.FireworksSparkParticle;
import net.minecraft.client.particle.FlameParticle;
import net.minecraft.client.particle.LavaEmberParticle;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.EndRodParticle;
import net.minecraft.client.particle.GlowParticle;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.WorldBorderRendering;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.model.ModelBaker;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.BlockBreakingInfo;

import net.minecraft.util.Colors;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.tick.TickManager;
import org.lwjgl.system.MemoryUtil;

public class EntityProxy {

    public static final ConcurrentMap<Class<? extends Particle>, AtomicInteger> PARTICLE_COUNTERS = new ConcurrentHashMap<>();
    private static final int DEFAULT_WORLD_ENTITY_BUFFER_SIZE = 786432;
    private static final int BLOCK_ENTITY_BUFFER_SIZE = 131072;
    private static final int BLOCK_CRUMBLING_BUFFER_SIZE = 65536;
    private static final double BLOCK_ENTITY_RENDER_DISTANCE_SQ = 96.0 * 96.0;

    // --- SVCP pool (render-thread only, no synchronization needed) ---
    private static final int SVCP_POOL_CAP = 32;
    private static final ArrayDeque<StorageVertexConsumerProvider> SVCP_POOL = new ArrayDeque<>();

    private static StorageVertexConsumerProvider acquireSVCP(int initialSize) {
        StorageVertexConsumerProvider p = SVCP_POOL.poll();
        if (p != null) {
            p.reset();
            p.setSize(initialSize);
            return p;
        }
        return new StorageVertexConsumerProvider(initialSize);
    }

    private static void releaseSVCP(StorageVertexConsumerProvider p) {
        if (SVCP_POOL.size() < SVCP_POOL_CAP) {
            SVCP_POOL.offer(p);
        }
    }

    private static int optifineRandomTextureSize(Entity entity) {
        if (entity instanceof SlimeEntity slime) {
            return Math.max(0, slime.getSize() - 1);
        }
        return -1;
    }

    private static int resolveEntityLayerTextureId(TextureManager textureManager, Identifier identifier,
        EntityRenderData entityRenderData) {
        Identifier resolved = ResourcePackRandomEntityTextureResolver.resolveTexture(
            identifier,
            entityRenderData.hashCode,
            entityRenderData.x,
            entityRenderData.y,
            entityRenderData.z,
            entityRenderData.optifineRandomTextureSize);
        ResourcePackRandomEntityTextureResolver.ensureTextureRegistered(textureManager, resolved);
        return TextureArrayBridge.resolveRenderableTextureGlId(resolved,
            textureManager.getTexture(resolved).getGlId());
    }

    // --- Native ByteBuffer pool for queueBuild (render-thread only) ---
    private static final int BB_POOL_COUNT = 14;
    private static final ByteBuffer[] bbPool = new ByteBuffer[BB_POOL_COUNT];

    /**
     * Returns a native ByteBuffer of at least {@code requiredSize} bytes from
     * slot {@code index}. If the existing buffer is too small it is freed and a
     * larger one allocated (with 50% headroom to avoid frequent realloc).
     */
    private static ByteBuffer acquireBB(int index, int requiredSize) {
        if (requiredSize <= 0) requiredSize = 4; // guard against zero-size alloc
        ByteBuffer existing = bbPool[index];
        if (existing != null && existing.capacity() >= requiredSize) {
            existing.clear().limit(requiredSize);
            return existing;
        }
        if (existing != null) {
            MemoryUtil.memFree(existing);
        }
        int allocSize = requiredSize + (requiredSize >> 1); // 1.5x headroom
        ByteBuffer buf = MemoryUtil.memAlloc(allocSize);
        buf.limit(requiredSize);
        bbPool[index] = buf;
        return buf;
    }

    /** Frees all pooled ByteBuffers — call on shutdown. */
    public static void freeByteBufferPool() {
        for (int i = 0; i < BB_POOL_COUNT; i++) {
            if (bbPool[i] != null) {
                MemoryUtil.memFree(bbPool[i]);
                bbPool[i] = null;
            }
        }
    }

    private static final Identifier SUN_TEXTURE = Identifier.ofVanilla(
        "textures/environment/sun.png");
    private static final Identifier MOON_PHASES_TEXTURE = Identifier.ofVanilla(
        "textures/environment/moon_phases.png");

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rtFlag,
        boolean reflect,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            rtFlag.getValue(),
            -1,
            reflect,
            false,
            -1,
            entityRenderDataList);
    }

    public static void processWorldEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        Constants.RayTracingFlags rtFlag,
        boolean reflect,
        int optifineRandomTextureSize,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            rtFlag.getValue(),
            -1,
            reflect,
            false,
            optifineRandomTextureSize,
            entityRenderDataList);
    }

    public static void processPostEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        EntityRenderDataList entityRenderDataList) {
        processEntityRenderData(storageVertexConsumerProvider,
            hashCode,
            entityPosX,
            entityPosY,
            entityPosZ,
            0,
            -1,
            false,
            true,
            -1,
            entityRenderDataList);
    }

    private static void processEntityRenderData(
        StorageVertexConsumerProvider storageVertexConsumerProvider,
        int hashCode,
        double entityPosX,
        double entityPosY,
        double entityPosZ,
        int rtFlag,
        int prebuiltBLAS,
        boolean reflect,
        boolean post,
        int optifineRandomTextureSize,
        EntityRenderDataList entityRenderDataList) {
        Map<RenderLayer, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
        EntityRenderData
            entityRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                rtFlag, prebuiltBLAS, post, optifineRandomTextureSize);
        EntityRenderData
            waterMaskRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                RayTracingFlags.BOAT_WATER_MASK.getValue(), prebuiltBLAS, post, optifineRandomTextureSize);
        for (Map.Entry<RenderLayer, VertexConsumer> layerBuffer : layerBuffers.entrySet()) {
            RenderLayer layer = layerBuffer.getKey();
            BuiltBuffer buffer = null;

            VertexConsumer vertexConsumer = layerBuffer.getValue();
            if (vertexConsumer instanceof BufferBuilder bufferBuilder) {
                buffer = bufferBuilder.endNullable();
            } else if (vertexConsumer instanceof PBRVertexConsumer pbrVertexConsumer) {
                buffer = pbrVertexConsumer.endNullable();
            }

            if (layer.getDrawMode() != QUADS && layer.getDrawMode() != TRIANGLE_STRIP
                && layer.getDrawMode() != LINE_STRIP &&
                layer.getDrawMode() != LINES) {
                continue;
            }
            if (buffer == null) {
                continue;
            }

            if (layer.name.contains("water_mask")) {
                waterMaskRenderData.add(new EntityRenderLayer(layer, buffer, reflect));
            } else {
                entityRenderData.add(new EntityRenderLayer(layer, buffer, reflect));
            }
        }

        if (!entityRenderData.isEmpty()) {
            entityRenderDataList.add(entityRenderData);
        }
        if (!waterMaskRenderData.isEmpty()) {
            entityRenderDataList.add(waterMaskRenderData);
        }

    }

    public static void queueEntitiesBuild(Camera camera,
        List<Entity> renderedEntities,
        EntityRenderDispatcher entityRenderDispatcher,
        RenderTickCounter tickCounter,
        boolean canDrawEntityOutlines) {
        MatrixStack matrixStack = new MatrixStack();

        MinecraftClient client = MinecraftClient.getInstance();
        TickManager
            tickManager =
            Objects.requireNonNull(client.world)
                .getTickManager();

        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();
        for (Entity entity : renderedEntities) {

            if (entity.age == 0) {
                entity.lastRenderX = entity.getX();
                entity.lastRenderY = entity.getY();
                entity.lastRenderZ = entity.getZ();
            }

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = acquireSVCP(
                DEFAULT_WORLD_ENTITY_BUFFER_SIZE);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            VertexConsumerProvider vertexConsumerProvider;
            if (canDrawEntityOutlines && client.hasOutline(entity)) {
//                 TODO: add outline
//                StorageOutlineVertexConsumerProvider
//                    outlineVertexConsumerProvider =
//                    new StorageOutlineVertexConsumerProvider(entityStorageVertexConsumerProvider);
//                vertexConsumerProvider = outlineVertexConsumerProvider;
//                int color = entity.getTeamColorValue();
//                outlineVertexConsumerProvider.setColor(ColorHelper.getRed(color),
//                                                       ColorHelper.getGreen(color),
//                                                       ColorHelper.getBlue(color),
//                                                       255);
                vertexConsumerProvider = entityStorageVertexConsumerProvider;
            } else {
                vertexConsumerProvider = entityStorageVertexConsumerProvider;
            }

            // In offline mode, freeze entity interpolation to prevent jitter
            // (entities aren't ticking, so lastRenderPos == currentPos, but tickDelta varies)
            float tickDelta = (Options.offlineState != 0) ? 1.0f
                : tickCounter.getTickDelta(!tickManager.shouldSkipTick(entity));
            double entityPosX = MathHelper.lerp(tickDelta, entity.lastRenderX,
                entity.getX());
            double entityPosY = MathHelper.lerp(tickDelta, entity.lastRenderY,
                entity.getY());
            double entityPosZ = MathHelper.lerp(tickDelta, entity.lastRenderZ,
                entity.getZ());

            boolean isPlayerEntity = entity.equals(camera.getFocusedEntity());
            boolean fpvActive = isPlayerEntity && FirstPersonView.isActive();
            int optifineRandomTextureSize = optifineRandomTextureSize(entity);

            // Update smooth crouch progress before FPV rendering
            if (fpvActive) {
                FirstPersonView.updateCrouchProgress(tickDelta, camera);
            }

            if (fpvActive) {
                // Two-pass FPV render: body (head hidden) + head (body hidden)
                // Items rendered into separate fpvItemProvider during body pass via
                // PlayerEntityRendererMixins redirect — no Pass 3 needed.
                //
                // The offset is applied ONLY to BLAS positioning, never to entity state.
                // EntityRenderDispatcher.render(entity, 0,0,0, ...) captures model
                // vertices in model-local space; processWorldEntityRenderData places
                // the BLAS at the offset world position. Entity fields (pos, lastRenderX/Y/Z)
                // stay untouched so other mods see the real entity position.
                double[] fpvOffset = FirstPersonView.computeOffset(entity, tickDelta, camera);
                double fpvPosX = entityPosX + fpvOffset[0];
                double fpvPosY = entityPosY + fpvOffset[1];
                double fpvPosZ = entityPosZ + fpvOffset[2];
                int entityLight = entityRenderDispatcher.getLight(entity, tickDelta);

                // Create separate provider for held items — populated by the mixin's
                // feature renderer redirect during the body pass. This completely
                // isolates item vertices from body vertices (no ThreadLocal contamination).
                StorageVertexConsumerProvider itemProvider = acquireSVCP(8192);
                entityStorageVertexConsumerProviders.add(itemProvider);
                FirstPersonView.fpvItemProvider = itemProvider;

                // Pass 1: Body (head/hat hidden by PlayerEntityRendererMixins)
                // HeldItemFeatureRenderer is redirected to fpvItemProvider by the mixin.
                FirstPersonView.renderingBodyPass = true;
                try {
                    entityRenderDispatcher.render(entity, 0, 0, 0, tickDelta,
                        matrixStack, vertexConsumerProvider, entityLight);
                } finally {
                    FirstPersonView.renderingBodyPass = false;
                    FirstPersonView.fpvItemProvider = null;
                }

                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    fpvPosX, fpvPosY, fpvPosZ,
                    Constants.RayTracingFlags.PLAYER,
                    true, optifineRandomTextureSize, entityRenderDataList);

                // Pass 2: Head only (body hidden by PlayerEntityRendererMixins)
                // Head uses same offset as body — one forward slider controls both.
                StorageVertexConsumerProvider headProvider = acquireSVCP(
                    DEFAULT_WORLD_ENTITY_BUFFER_SIZE);
                entityStorageVertexConsumerProviders.add(headProvider);

                FirstPersonView.renderingHeadPass = true;
                try {
                    entityRenderDispatcher.render(entity, 0, 0, 0, tickDelta,
                        matrixStack, headProvider, entityLight);
                } finally {
                    FirstPersonView.renderingHeadPass = false;
                }

                processWorldEntityRenderData(headProvider,
                    System.identityHashCode(entity) ^ 0x48454144, // "HEAD" xor
                    fpvPosX, fpvPosY, fpvPosZ,
                    Constants.RayTracingFlags.PLAYER_HEAD,
                    true, optifineRandomTextureSize, entityRenderDataList);

                // Submit held items with HAND flag (10-block range, hand.rmiss,
                // no self-shadow, softer sun, ambient floor, correct DLSS guide buffers)
                processWorldEntityRenderData(itemProvider,
                    System.identityHashCode(entity) ^ 0x4954454D, // "ITEM" xor
                    fpvPosX, fpvPosY, fpvPosZ,
                    Constants.RayTracingFlags.HAND,
                    true, optifineRandomTextureSize, entityRenderDataList);
            } else {
                // Normal render (non-player entities or third-person)
                try {
                    entityRenderDispatcher.render(entity, 0, 0, 0, tickDelta,
                        matrixStack, vertexConsumerProvider,
                        entityRenderDispatcher.getLight(entity, tickDelta));
                } finally {
                }

                if (isPlayerEntity) {
                    processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                        System.identityHashCode(entity),
                        entityPosX, entityPosY, entityPosZ,
                        Constants.RayTracingFlags.PLAYER,
                        true, optifineRandomTextureSize, entityRenderDataList);
                } else {
                    processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                        System.identityHashCode(entity),
                        entityPosX, entityPosY, entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true, optifineRandomTextureSize, entityRenderDataList);
                }
            }
        }

        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);
    }

    public static synchronized Pair<List<StorageVertexConsumerProvider>, EntityRenderDataList> queueBlockEntitiesRebuild(
        Camera camera,
        BuiltChunkStorage chunks,
        Set<BlockEntity> noCullingBlockEntities,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockEntityRenderDispatcher blockEntityRenderDispatcher,
        float tickDelta) {
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> entityStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList entityRenderDataList = new EntityRenderDataList();

        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList crumblingRenderDataList = new EntityRenderDataList();

        Vec3d cameraPos = camera.getPos();
        BlockPos cameraBlockPos = camera.getBlockPos();
        double cameraX = cameraPos.getX();
        double cameraY = cameraPos.getY();
        double cameraZ = cameraPos.getZ();
        int camBX = cameraBlockPos.getX();
        int camBY = cameraBlockPos.getY();
        int camBZ = cameraBlockPos.getZ();
        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            if (builtChunk == null) {
                continue;
            }

            // Raw int distance check — avoids BlockPos.add() allocation per chunk
            BlockPos origin = builtChunk.getOrigin();
            int dx = origin.getX() + 8 - camBX;
            int dy = origin.getY() + 8 - camBY;
            int dz = origin.getZ() + 8 - camBZ;
            if ((long)dx * dx + (long)dy * dy + (long)dz * dz > BLOCK_ENTITY_RENDER_DISTANCE_SQ) {
                continue;
            }

            List<BlockEntity>
                list =
                builtChunk.getData()
                    .getBlockEntities();
            if (!list.isEmpty()) {
                for (BlockEntity blockEntity : list) {
                    BlockPos blockPos = blockEntity.getPos();
                    if (blockPos.getSquaredDistanceFromCenter(cameraX, cameraY, cameraZ)
                        > BLOCK_ENTITY_RENDER_DISTANCE_SQ) {
                        continue;
                    }

                    StorageVertexConsumerProvider entityStorageVertexConsumerProvider = acquireSVCP(
                        BLOCK_ENTITY_BUFFER_SIZE);
                    entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);
                    StorageVertexConsumerProvider crumblingStorageVertexConsumerProvider = null;

                    VertexConsumerProvider vertexConsumerProvider = entityStorageVertexConsumerProvider;

                    double entityPosX = blockPos.getX();
                    double entityPosY = blockPos.getY();
                    double entityPosZ = blockPos.getZ();

                    matrixStack.push();
                    SortedSet<BlockBreakingInfo> sortedSet = blockBreakingProgressions.get(
                        blockPos.asLong());
                    if (sortedSet != null && !sortedSet.isEmpty()) {
                        int
                            stage =
                            sortedSet.last()
                                .getStage();
                        if (stage >= 0 && stage < ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.size()) {
                            crumblingStorageVertexConsumerProvider = acquireSVCP(
                                BLOCK_CRUMBLING_BUFFER_SIZE);
                            crumblingStorageVertexConsumerProviders.add(
                                crumblingStorageVertexConsumerProvider);

                            MatrixStack.Entry entry = matrixStack.peek();
                            VertexConsumer
                                crumblingConsumer =
                                new OverlayVertexConsumer(
                                    crumblingStorageVertexConsumerProvider.getBuffer(
                                        ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.get(
                                            stage)), entry, 1.0F);
                            vertexConsumerProvider = renderLayer -> {
                                VertexConsumer vertexConsumer2 = entityStorageVertexConsumerProvider.getBuffer(
                                    renderLayer);
                                return renderLayer.hasCrumbling() ? VertexConsumers.union(
                                    crumblingConsumer,
                                    vertexConsumer2) :
                                    vertexConsumer2;
                            };
                        }
                    }

                    blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                        vertexConsumerProvider);
                    matrixStack.pop();

                    processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                        System.identityHashCode(blockEntity),
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        entityRenderDataList);
                    if (crumblingStorageVertexConsumerProvider != null) {
                        processWorldEntityRenderData(crumblingStorageVertexConsumerProvider,
                            System.identityHashCode(blockEntity) + 1,
                            entityPosX,
                            entityPosY,
                            entityPosZ,
                            Constants.RayTracingFlags.WORLD,
                            true,
                            crumblingRenderDataList);
                    }
                }
            }
        }

        for (BlockEntity blockEntity : noCullingBlockEntities) {
            BlockPos blockPos = blockEntity.getPos();
            if (blockPos.getSquaredDistanceFromCenter(cameraX, cameraY, cameraZ)
                > BLOCK_ENTITY_RENDER_DISTANCE_SQ) {
                continue;
            }

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = acquireSVCP(
                BLOCK_ENTITY_BUFFER_SIZE);
            entityStorageVertexConsumerProviders.add(entityStorageVertexConsumerProvider);

            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            matrixStack.push();
            blockEntityRenderDispatcher.render(blockEntity, tickDelta, matrixStack,
                entityStorageVertexConsumerProvider);
            matrixStack.pop();

            processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                System.identityHashCode(blockEntity),
                entityPosX,
                entityPosY,
                entityPosZ,
                Constants.RayTracingFlags.WORLD,
                true,
                entityRenderDataList);
        }

        queueBuild(entityStorageVertexConsumerProviders, entityRenderDataList);

        return new Pair<>(crumblingStorageVertexConsumerProviders, crumblingRenderDataList);
    }

    public static void queueCrumblingRebuild(Camera camera,
        Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions,
        BlockRenderManager blockRenderManager,
        ClientWorld world,
        List<StorageVertexConsumerProvider> crumblingStorageVertexConsumerProviders,
        EntityRenderDataList crumblingRenderDataList) {
        if (crumblingStorageVertexConsumerProviders.isEmpty() && blockBreakingProgressions.isEmpty()) {
            return;
        }

        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider>
            blockCrumblingStorageVertexConsumerProviders =
            new ArrayList<>(Math.max(1, blockBreakingProgressions.size()));
        EntityRenderDataList blockCrumblingRenderDataList = new EntityRenderDataList();

        Vec3d vec3d = camera.getPos();
        double d = vec3d.getX();
        double e = vec3d.getY();
        double f = vec3d.getZ();

        for (Long2ObjectMap.Entry<SortedSet<BlockBreakingInfo>> blockBreakingProgression :
            blockBreakingProgressions.long2ObjectEntrySet()) {
            BlockPos blockPos = BlockPos.fromLong(blockBreakingProgression.getLongKey());
            double entityPosX = blockPos.getX();
            double entityPosY = blockPos.getY();
            double entityPosZ = blockPos.getZ();

            if (!(blockPos.getSquaredDistanceFromCenter(d, e, f) > 1024.0)) {
                SortedSet<BlockBreakingInfo> sortedSet = blockBreakingProgression.getValue();
                if (sortedSet != null && !sortedSet.isEmpty()) {
                    int
                        stage =
                        sortedSet.last()
                            .getStage();
                    if (stage < 0 || stage >= ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.size()) {
                        continue;
                    }

                    StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = acquireSVCP(
                        BLOCK_CRUMBLING_BUFFER_SIZE);
                    blockCrumblingStorageVertexConsumerProviders.add(
                        blockCrumblingStorageVertexConsumerProvider);

                    matrixStack.push();
                    MatrixStack.Entry entry = matrixStack.peek();
                    VertexConsumer
                        vertexConsumer =
                        new OverlayVertexConsumer(
                            blockCrumblingStorageVertexConsumerProvider.getBuffer(
                                ModelBaker.BLOCK_DESTRUCTION_RENDER_LAYERS.get(
                                    stage)), entry, 1.0F);
                    blockRenderManager.renderDamage(world.getBlockState(blockPos), blockPos, world,
                        matrixStack, vertexConsumer);
                    matrixStack.pop();

                    processWorldEntityRenderData(blockCrumblingStorageVertexConsumerProvider,
                        0,
                        entityPosX,
                        entityPosY,
                        entityPosZ,
                        Constants.RayTracingFlags.WORLD,
                        true,
                        blockCrumblingRenderDataList);
                }
            }
        }

        List<StorageVertexConsumerProvider>
            storageVertexConsumerProviders =
            new ArrayList<>(
                crumblingStorageVertexConsumerProviders.size() +
                    blockCrumblingStorageVertexConsumerProviders.size());
        storageVertexConsumerProviders.addAll(crumblingStorageVertexConsumerProviders);
        storageVertexConsumerProviders.addAll(blockCrumblingStorageVertexConsumerProviders);

        EntityRenderDataList renderDataList = new EntityRenderDataList();
        for (EntityRenderData entityRenderData : crumblingRenderDataList) {
            renderDataList.add(entityRenderData);
        }
        for (EntityRenderData entityRenderData : blockCrumblingRenderDataList) {
            renderDataList.add(entityRenderData);
        }

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.WORLD,
            true);
    }

    public static void queueHandRebuild(BufferBuilderStorage buffers, float tickDelta,
        HeldItemRenderer firstPersonRenderer) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = acquireSVCP(
            8192);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.push();

        boolean bl = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        // Skip first-person hand render when FPV is active (third-person arms show items)
        boolean fpvSkipHands = FirstPersonView.isActive();

        if (client.options.getPerspective()
            .isFirstPerson() && !bl && !client.options.hudHidden &&
            client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR && !fpvSkipHands) {
            ((IHeldItemRendererExt) firstPersonRenderer).neoVoxelRT$renderItem(tickDelta,
                matrixStack,
                storageVertexConsumerProvider,
                client.player,
                client.getEntityRenderDispatcher()
                    .getLight(client.player, tickDelta));

            processWorldEntityRenderData(storageVertexConsumerProvider,
                System.identityHashCode(Constants.RayTracingFlags.HAND),
                0,
                0,
                0,
                Constants.RayTracingFlags.HAND,
                true,
                renderDataList);
            queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
                Constants.Coordinates.CAMERA,
                false);
        }

        matrixStack.pop();

        if (client.options.getPerspective()
            .isFirstPerson() && !bl) {
            VertexConsumerProvider.Immediate immediate = buffers.getEntityVertexConsumers();
            InGameOverlayRenderer.renderOverlays(client, matrixStack, immediate);
            immediate.draw();
        }
    }

    // Firework emission intensity: nits / EMISSION_REFERENCE_NITS (200.0)
    // Sparks: ~20 nits (subtle glow), Flash: ~200 nits (bright burst)

    /** Maps particle type flags to Options.particleTemperatures/Wavelengths/Purities index (0-13). */
    private static int getParticleTypeIndex(boolean isSoulFlame, boolean isCandleFlame, boolean isFlame,
            boolean isLavaDrip, boolean isLava, boolean isPortal, boolean isEndRod,
            boolean isEnchanting, boolean isSculk, boolean isTotem, boolean isDragonBreath, boolean isGlow) {
        if (isFlame && !isSoulFlame && !isCandleFlame) return 0; // flame
        if (isLava) return 1;                                      // lavaEmber
        // 2=fireworkSpark, 3=fireworkFlash handled separately
        if (isPortal) return 4;
        if (isEndRod) return 5;
        if (isGlow) return 6;
        if (isSoulFlame) return 7;
        if (isCandleFlame) return 8;
        if (isEnchanting) return 9;
        if (isSculk) return 10;
        if (isTotem) return 11;
        if (isDragonBreath) return 12;
        if (isLavaDrip) return 13;
        return 0; // fallback to flame
    }

    public static void queueParticleRebuild(Camera camera, float tickDelta, Frustum frustum) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider postStorageVertexConsumerProvider = acquireSVCP(0);
        storageVertexConsumerProviders.add(postStorageVertexConsumerProvider);

        // Separate provider for emissive firework particles (routed to RT pipeline)
        StorageVertexConsumerProvider fireworkProvider = acquireSVCP(0);
        storageVertexConsumerProviders.add(fireworkProvider);

        ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
        Map<ParticleTextureSheet, Queue<Particle>> particles = particleManagerExt.neoVoxelRT$getParticles();

        for (ParticleTextureSheet particleTextureSheet : particleManagerExt.neoVoxelRT$getTextureSheets()) {
            Queue<Particle> particleQueue = particles.get(particleTextureSheet);
            if (particleQueue != null && !particleQueue.isEmpty()) {
                for (Particle particle : particleQueue) {

                    // Route emissive particles to RT pipeline
                    boolean isFirework = particle instanceof FireworksSparkParticle.FireworkParticle
                                      || particle instanceof FireworksSparkParticle.Flash
                                      || particle instanceof FireworksSparkParticle.Explosion;
                    boolean isFlash = particle instanceof FireworksSparkParticle.Flash;

                    // Glowing particles: flames, lava, portals, end rods, glow, and new types
                    boolean isFlame = particle instanceof FlameParticle;
                    boolean isLava = particle instanceof LavaEmberParticle;
                    boolean isPortal = particle instanceof PortalParticle;
                    boolean isEndRod = particle instanceof EndRodParticle;
                    boolean isGlow = particle instanceof GlowParticle;
                    // ParticleType-based detection for particles that share base classes
                    ParticleType<?> pType = (particle instanceof IParticleExt ext) ? ext.neoVoxelRT$getParticleType() : null;
                    boolean isSoulFlame = pType == ParticleTypes.SOUL_FIRE_FLAME;
                    boolean isCandleFlame = pType == ParticleTypes.SMALL_FLAME;
                    boolean isEnchanting = pType == ParticleTypes.ENCHANT;
                    boolean isSculk = pType == ParticleTypes.SCULK_CHARGE || pType == ParticleTypes.SCULK_CHARGE_POP;
                    boolean isTotem = pType == ParticleTypes.TOTEM_OF_UNDYING;
                    boolean isDragonBreath = pType == ParticleTypes.DRAGON_BREATH;
                    boolean isLavaDrip = pType == ParticleTypes.DRIPPING_LAVA || pType == ParticleTypes.FALLING_LAVA || pType == ParticleTypes.LANDING_LAVA;
                    boolean isNewEmissive = isSoulFlame || isCandleFlame || isEnchanting || isSculk || isTotem || isDragonBreath || isLavaDrip;
                    boolean isEmissiveParticle = isFirework || isFlame || isLava || isPortal || isEndRod || isGlow || isNewEmissive;

                    StorageVertexConsumerProvider targetProvider = isEmissiveParticle
                        ? fireworkProvider : postStorageVertexConsumerProvider;

                    VertexConsumer
                        vertexConsumer =
                        targetProvider.getBuffer(
                            Objects.requireNonNull(
                                particleTextureSheet.renderType()));

                    // Set emission (nits) for emissive particles
                    float savedR = 0, savedG = 0, savedB = 0;
                    if (isEmissiveParticle) {
                        float emission;
                        if (isFlash) {
                            emission = Options.fireworkFlashEmission;
                        } else if (isFirework) {
                            emission = Options.fireworkSparkEmission;
                        } else if (isSoulFlame) {
                            emission = Options.soulFireFlameParticleEmission;
                        } else if (isCandleFlame) {
                            emission = Options.candleFlameParticleEmission;
                        } else if (isFlame) {
                            emission = Options.flameParticleEmission;
                        } else if (isLavaDrip) {
                            emission = Options.lavaDripParticleEmission;
                        } else if (isLava) {
                            emission = Options.lavaParticleEmission;
                        } else if (isPortal) {
                            emission = Options.portalParticleEmission;
                        } else if (isEndRod) {
                            emission = Options.endRodParticleEmission;
                        } else if (isEnchanting) {
                            emission = Options.enchantingParticleEmission;
                        } else if (isSculk) {
                            emission = Options.sculkParticleEmission;
                        } else if (isTotem) {
                            emission = Options.totemParticleEmission;
                        } else if (isDragonBreath) {
                            emission = Options.dragonBreathParticleEmission;
                        } else {
                            emission = Options.glowParticleEmission;
                        }
                        if (vertexConsumer instanceof PBRVertexConsumer pbr) {
                            pbr.setPendingEmission(emission);
                        }
                    }
                    // Apply spectral color to all emissive particles
                    boolean colorOverridden = false;
                    if (isEmissiveParticle && particle instanceof IParticleExt ext) {
                        savedR = ext.neoVoxelRT$getRed();
                        savedG = ext.neoVoxelRT$getGreen();
                        savedB = ext.neoVoxelRT$getBlue();
                        float tempK; int wl; float pur;
                        if (isFirework) {
                            // Fireworks use per-dye-color system (temperatures stored as Kelvin)
                            int idx = Options.lookupFireworkColorIndex(savedR, savedG, savedB);
                            tempK = Options.fireworkColorTemperatures[idx];
                            wl = Options.fireworkColorWavelength[idx];
                            pur = Options.fireworkColorPurity[idx] / 100.0f;
                        } else {
                            // All other particles use per-type spectral arrays (stored as Celsius)
                            int pIdx = getParticleTypeIndex(isSoulFlame, isCandleFlame, isFlame, isLavaDrip,
                                isLava, isPortal, isEndRod, isEnchanting, isSculk, isTotem, isDragonBreath, isGlow);
                            tempK = Options.particleTemperatures[pIdx] + 273.15f;
                            wl = Options.particleWavelengths[pIdx];
                            pur = Options.particlePurities[pIdx] / 100.0f;
                        }
                        if (tempK > 273.15f) {
                            float[] bt2020 = SpectralColor.computeFlameColor(tempK, wl, pur);
                            ext.neoVoxelRT$setRed(Math.max(0, bt2020[0]));
                            ext.neoVoxelRT$setGreen(Math.max(0, bt2020[1]));
                            ext.neoVoxelRT$setBlue(Math.max(0, bt2020[2]));
                            colorOverridden = true;
                        }
                    }

                    try {
                        // Offline: use fixed tickDelta to prevent particle position interpolation
                        // (prevPos != pos from last tick before freeze → varying tickDelta = jitter)
                        float effectiveTickDelta = Options.offlineState != 0 ? 1.0f : tickDelta;
                        particle.render(vertexConsumer, camera, effectiveTickDelta);
                        if (isEmissiveParticle) {
                            // Restore original color and clear emission
                            if (colorOverridden && particle instanceof IParticleExt ext2) {
                                ext2.neoVoxelRT$setRed(savedR);
                                ext2.neoVoxelRT$setGreen(savedG);
                                ext2.neoVoxelRT$setBlue(savedB);
                            }
                            if (vertexConsumer instanceof PBRVertexConsumer pbr) {
                                pbr.setPendingEmission(0.0f);
                            }
                        }
                    } catch (Throwable var11) {
                        CrashReport crashReport = CrashReport.create(var11, "Rendering Particle");
                        CrashReportSection crashReportSection = crashReport.addElement(
                            "Particle being rendered");
                        crashReportSection.add("Particle", particle);
                        crashReportSection.add("Particle Type", particleTextureSheet);
                        throw new CrashException(crashReport);
                    }
                }
            }
        }

        processPostEntityRenderData(postStorageVertexConsumerProvider, 0, 0, 0, 0, renderDataList);

        // Submit emissive firework particles to RT pipeline
        processWorldEntityRenderData(fireworkProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.PARTICLE, true, renderDataList);

        StorageVertexConsumerProvider storageVertexConsumerProvider = acquireSVCP(0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        Queue<Particle> customParticleQueue = particles.get(ParticleTextureSheet.CUSTOM);
        if (customParticleQueue != null && !customParticleQueue.isEmpty()) {
            for (Particle particle : customParticleQueue) {

                MatrixStack matrixStack = new MatrixStack();

                try {
                    particle.renderCustom(matrixStack, storageVertexConsumerProvider, camera,
                        tickDelta);
                } catch (Throwable var10) {
                    CrashReport crashReport = CrashReport.create(var10, "Rendering Particle");
                    CrashReportSection crashReportSection = crashReport.addElement(
                        "Particle being rendered");
                    crashReportSection.add("Particle", particle::toString);
                    crashReportSection.add("Particle Type", "Custom");
                    throw new CrashException(crashReport);
                }
            }
        }

        processWorldEntityRenderData(storageVertexConsumerProvider, 0, 0, 0, 0,
            Constants.RayTracingFlags.PARTICLE, true, renderDataList);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    public static void queueTargetBlockOutlineRebuild(Camera camera, ClientWorld world) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrixStack = new MatrixStack();

        StorageVertexConsumerProvider storageVertexConsumerProvider = acquireSVCP(0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        if (client.crosshairTarget instanceof BlockHitResult blockHitResult) {
            if (blockHitResult.getType() != HitResult.Type.MISS) {
                BlockPos blockPos = blockHitResult.getBlockPos();
                BlockState blockState = world.getBlockState(blockPos);
                if (!blockState.isAir() && world.getWorldBorder()
                    .contains(blockPos)) {
                    Boolean
                        isHighContrastBlockOutline =
                        client.options.getHighContrastBlockOutline()
                            .getValue();
                    if (isHighContrastBlockOutline) {
                        VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                            RenderLayer.getSecondaryBlockOutline());
                        VertexRendering.drawOutline(matrixStack,
                            vertexConsumer,
                            blockState.getOutlineShape(world, blockPos,
                                ShapeContext.of(camera.getFocusedEntity())),
                            0,
                            0,
                            0,
                            -16777216);
                    }

                    VertexConsumer vertexConsumer = storageVertexConsumerProvider.getBuffer(
                        RenderLayer.getLines());
                    int color =
                        isHighContrastBlockOutline ? Colors.CYAN
                            : ColorHelper.withAlpha(102, Colors.BLACK);
                    VertexRendering.drawOutline(matrixStack,
                        vertexConsumer,
                        blockState.getOutlineShape(world, blockPos,
                            ShapeContext.of(camera.getFocusedEntity())),
                        0,
                        0,
                        0,
                        color);

                    processWorldEntityRenderData(storageVertexConsumerProvider,
                        0,
                        blockPos.getX(),
                        blockPos.getY(),
                        blockPos.getZ(),
                        Constants.RayTracingFlags.WORLD,
                        false,
                        renderDataList);
                }
            }
        }

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0075f,
            Constants.Coordinates.WORLD,
            false);
    }

    public static void queueWeatherBuild(WeatherRendering weatherRendering,
        WorldBorderRendering worldBorderRendering,
        ClientWorld world,
        Camera camera,
        int ticks,
        float tickDelta) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider storageVertexConsumerProvider = acquireSVCP(0);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        weatherRendering.renderPrecipitation(world, storageVertexConsumerProvider, ticks, tickDelta,
            camera.getPos());

        MinecraftClient client = MinecraftClient.getInstance();
        int clampedViewDistance = client.options.getClampedViewDistance() * 16;
        float farPlaneDistance = client.gameRenderer.getFarPlaneDistance();
        worldBorderRendering.render(world.getWorldBorder(), camera.getPos(), clampedViewDistance,
            farPlaneDistance);

        processPostEntityRenderData(storageVertexConsumerProvider, 0, 0, 0, 0, renderDataList);

        queueBuild(storageVertexConsumerProviders, renderDataList, 0.0f,
            Constants.Coordinates.CAMERA_SHIFT, false);
    }

    public static void queueBuild(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList) {
        queueBuild(storageVertexConsumerProviders, entityRenderDataList, 0.0125f,
            Constants.Coordinates.WORLD, false);
    }

    public static void queueBuild(
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders,
        EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        if (entityRenderDataList.isEmpty()) {
            for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
                storageVertexConsumerProvider.close();
                releaseSVCP(storageVertexConsumerProvider);
            }
            return;
        }

        TextureManager
            textureManager =
            MinecraftClient.getInstance()
                .getTextureManager();

        try {
            int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            ByteBuffer entityHashCodeBB = acquireBB(0, entityHashCodeSize);
            long entityHashCodeAddr = memAddress(entityHashCodeBB);
            int entityHashCodeBaseAddr = 0;

            int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            ByteBuffer entityPosXBB = acquireBB(1, entityPosXSize);
            long entityPosXAddr = memAddress(entityPosXBB);
            int entityPosXBaseAddr = 0;

            int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            ByteBuffer entityPosYBB = acquireBB(2, entityPosYSize);
            long entityPosYAddr = memAddress(entityPosYBB);
            int entityPosYBaseAddr = 0;

            int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            ByteBuffer entityPosZBB = acquireBB(3, entityPosZSize);
            long entityPosZAddr = memAddress(entityPosZBB);
            int entityPosZBaseAddr = 0;

            int entityRTFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            ByteBuffer entityRTFlagBB = acquireBB(4, entityRTFlagSize);
            long entityRTFlagAddr = memAddress(entityRTFlagBB);
            int entityRTFlagBaseAddr = 0;

            int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            ByteBuffer entityPrebuiltBLASBB = acquireBB(5, entityPrebuiltBLASSize);
            long entityPrebuiltBLASAddr = memAddress(entityPrebuiltBLASBB);
            int entityPrebuiltBLASBaseAddr = 0;

            int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            ByteBuffer entityPostBB = acquireBB(6, entityPostSize);
            long entityPostAddr = memAddress(entityPostBB);
            int entityPostBaseAddr = 0;

            int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            ByteBuffer entityLayerCountBB = acquireBB(7, entityLayerCountSize);
            long entityLayerCountAddr = memAddress(entityLayerCountBB);
            int entityLayerCountBaseAddr = 0;

            int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            ByteBuffer geometryTypeBB = acquireBB(8, geometryTypeSize);
            long geometryTypeAddr = memAddress(geometryTypeBB);
            int geometryTypeBaseAddr = 0;

            int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            ByteBuffer geometryTextureBB = acquireBB(9, geometryTextureSize);
            long geometryTextureAddr = memAddress(geometryTextureBB);
            int geometryTextureBaseAddr = 0;

            int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            ByteBuffer vertexFormatBB = acquireBB(10, vertexFormatSize);
            long vertexFormatAddr = memAddress(vertexFormatBB);
            int vertexFormatBaseAddr = 0;

            int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            ByteBuffer indexFormatBB = acquireBB(11, indexFormatSize);
            long indexFormatAddr = memAddress(indexFormatBB);
            int indexFormatBaseAddr = 0;

            int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            ByteBuffer vertexCountBB = acquireBB(12, vertexCountSize);
            long vertexCountAddr = memAddress(vertexCountBB);
            int vertexCountBaseAddr = 0;

            int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            ByteBuffer verticesBB = acquireBB(13, verticesSize);
            long verticesAddr = memAddress(verticesBB);
            int verticesBaseAddr = 0;

            for (EntityRenderData entityRenderData : entityRenderDataList) {
                entityHashCodeBB.putInt(entityHashCodeBaseAddr, entityRenderData.hashCode);
                entityHashCodeBaseAddr += Integer.BYTES;

                entityPosXBB.putDouble(entityPosXBaseAddr, entityRenderData.x);
                entityPosXBaseAddr += Double.BYTES;

                entityPosYBB.putDouble(entityPosYBaseAddr, entityRenderData.y);
                entityPosYBaseAddr += Double.BYTES;

                entityPosZBB.putDouble(entityPosZBaseAddr, entityRenderData.z);
                entityPosZBaseAddr += Double.BYTES;

                entityRTFlagBB.putInt(entityRTFlagBaseAddr, entityRenderData.rtFlag);
                entityRTFlagBaseAddr += Integer.BYTES;

                entityPrebuiltBLASBB.putInt(entityPrebuiltBLASBaseAddr, entityRenderData.prebuiltBLAS);
                entityPrebuiltBLASBaseAddr += Integer.BYTES;

                entityPostBB.putInt(entityPostBaseAddr, entityRenderData.post ? 1 : 0);
                entityPostBaseAddr += Integer.BYTES;

                entityLayerCountBB.putInt(entityLayerCountBaseAddr, entityRenderData.size());
                entityLayerCountBaseAddr += Integer.BYTES;

                for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                    RenderLayer renderLayer = entityRenderLayer.renderLayer;
                    BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;

                    Identifier identifier = ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                        .orElse(MissingSprite.getMissingSpriteId());
                    int geometryTypeID = Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                        .getValue();
                    int geometryTextureID = resolveEntityLayerTextureId(textureManager, identifier, entityRenderData);
                    int vertexFormatID = Constants.VertexFormats.getValue(vertexBuffer.getDrawParameters().format());
                    int indexFormatID = Constants.DrawModes.getValue(vertexBuffer.getDrawParameters().mode());

                    BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(vertexBuffer.getBuffer());
                    assert vertexBuffer.getDrawParameters().indexCount() == vertexBuffer.getDrawParameters().vertexCount() / 4 * 6;

                    geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                    geometryTypeBaseAddr += Integer.BYTES;

                    geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                    geometryTextureBaseAddr += Integer.BYTES;

                    vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                    vertexFormatBaseAddr += Integer.BYTES;

                    indexFormatBB.putInt(indexFormatBaseAddr, indexFormatID);
                    indexFormatBaseAddr += Integer.BYTES;

                    vertexCountBB.putInt(vertexCountBaseAddr, vertexBuffer.getDrawParameters().vertexCount());
                    vertexCountBaseAddr += Integer.BYTES;

                    verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                    verticesBaseAddr += Long.BYTES;
                }
            }

            queueBuild(lineWidth,
                coordinate.getValue(),
                normalOffset,
                entityRenderDataList.getTotalEntityCount(),
                entityHashCodeAddr,
                entityPosXAddr,
                entityPosYAddr,
                entityPosZAddr,
                entityRTFlagAddr,
                entityPrebuiltBLASAddr,
                entityPostAddr,
                entityLayerCountAddr,
                geometryTypeAddr,
                geometryTextureAddr,
                vertexFormatAddr,
                indexFormatAddr,
                vertexCountAddr,
                verticesAddr);
        } finally {
            // ByteBuffers are pooled in bbPool[] — no memFree needed here.
            // They persist across frames and are freed via freeByteBufferPool() on shutdown.

            // Close built buffers and providers (original behavior), but don't let cleanup mask errors.
            try {
                for (EntityRenderData entityRenderData : entityRenderDataList) {
                    for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                        BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;
                        vertexBuffer.close();
                    }
                }
            } catch (Throwable ignored) {
            }

            try {
                for (StorageVertexConsumerProvider storageVertexConsumerProvider : storageVertexConsumerProviders) {
                    storageVertexConsumerProvider.close();
                    releaseSVCP(storageVertexConsumerProvider);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList) {
        queueBuildWithoutClose(entityRenderDataList, 0.0125f, Constants.Coordinates.WORLD, false);
    }

    public static void queueBuildWithoutClose(EntityRenderDataList entityRenderDataList,
        float lineWidth,
        Constants.Coordinates coordinate,
        boolean normalOffset) {
        TextureManager
            textureManager =
            MinecraftClient.getInstance()
                .getTextureManager();

        int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityHashCodeBB = MemoryUtil.memAlloc(entityHashCodeSize);
        long entityHashCodeAddr = memAddress(entityHashCodeBB);
        int entityHashCodeBaseAddr = 0;

        int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
        ByteBuffer entityPosXBB = MemoryUtil.memAlloc(entityPosXSize);
        long entityPosXAddr = memAddress(entityPosXBB);
        int entityPosXBaseAddr = 0;

        int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
        ByteBuffer entityPosYBB = MemoryUtil.memAlloc(entityPosYSize);
        long entityPosYAddr = memAddress(entityPosYBB);
        int entityPosYBaseAddr = 0;

        int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
        ByteBuffer entityPosZBB = MemoryUtil.memAlloc(entityPosZSize);
        long entityPosZAddr = memAddress(entityPosZBB);
        int entityPosZBaseAddr = 0;

        int entityRTFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityRTFlagBB = MemoryUtil.memAlloc(entityRTFlagSize);
        long entityRTFlagAddr = memAddress(entityRTFlagBB);
        int entityRTFlagBaseAddr = 0;

        int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityPrebuiltBLASBB = MemoryUtil.memAlloc(entityPrebuiltBLASSize);
        long entityPrebuiltBLASAddr = memAddress(entityPrebuiltBLASBB);
        int entityPrebuiltBLASBaseAddr = 0;

        int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityPostBB = MemoryUtil.memAlloc(entityPostSize);
        long entityPostAddr = memAddress(entityPostBB);
        int entityPostBaseAddr = 0;

        int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
        ByteBuffer entityLayerCountBB = MemoryUtil.memAlloc(entityLayerCountSize);
        long entityLayerCountAddr = memAddress(entityLayerCountBB);
        int entityLayerCountBaseAddr = 0;

        int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
        long geometryTypeAddr = memAddress(geometryTypeBB);
        int geometryTypeBaseAddr = 0;

        int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
        long geometryTextureAddr = memAddress(geometryTextureBB);
        int geometryTextureBaseAddr = 0;

        int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
        long vertexFormatAddr = memAddress(vertexFormatBB);
        int vertexFormatBaseAddr = 0;

        int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer indexFormatBB = MemoryUtil.memAlloc(indexFormatSize);
        long indexFormatAddr = memAddress(indexFormatBB);
        int indexFormatBaseAddr = 0;

        int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
        ByteBuffer vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
        long vertexCountAddr = memAddress(vertexCountBB);
        int vertexCountBaseAddr = 0;

        int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
        ByteBuffer verticesBB = MemoryUtil.memAlloc(verticesSize);
        long verticesAddr = memAddress(verticesBB);
        int verticesBaseAddr = 0;

        for (EntityRenderData entityRenderData : entityRenderDataList) {
            entityHashCodeBB.putInt(entityHashCodeBaseAddr, entityRenderData.hashCode);
            entityHashCodeBaseAddr += Integer.BYTES;

            entityPosXBB.putDouble(entityPosXBaseAddr, entityRenderData.x);
            entityPosXBaseAddr += Double.BYTES;

            entityPosYBB.putDouble(entityPosYBaseAddr, entityRenderData.y);
            entityPosYBaseAddr += Double.BYTES;

            entityPosZBB.putDouble(entityPosZBaseAddr, entityRenderData.z);
            entityPosZBaseAddr += Double.BYTES;

            entityRTFlagBB.putInt(entityRTFlagBaseAddr, entityRenderData.rtFlag);
            entityRTFlagBaseAddr += Integer.BYTES;

            entityPrebuiltBLASBB.putInt(entityPrebuiltBLASBaseAddr, entityRenderData.prebuiltBLAS);
            entityPrebuiltBLASBaseAddr += Integer.BYTES;

            entityPostBB.putInt(entityPostBaseAddr, entityRenderData.post ? 1 : 0);
            entityPostBaseAddr += Integer.BYTES;

            entityLayerCountBB.putInt(entityLayerCountBaseAddr, entityRenderData.size());
            entityLayerCountBaseAddr += Integer.BYTES;

            for (EntityRenderLayer entityRenderLayer : entityRenderData) {
                RenderLayer renderLayer = entityRenderLayer.renderLayer;
                BuiltBuffer vertexBuffer = entityRenderLayer.builtBuffer;

                Identifier
                    identifier =
                    ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                        .orElse(MissingSprite.getMissingSpriteId());
                int
                    geometryTypeID =
                    Constants.GeometryTypes.getGeometryType(renderLayer, entityRenderLayer.reflect)
                        .getValue();
                int
                    geometryTextureID =
                    resolveEntityLayerTextureId(textureManager, identifier, entityRenderData);
                int
                    vertexFormatID =
                    Constants.VertexFormats.getValue(vertexBuffer.getDrawParameters()
                        .format());
                int
                    indexFormatID =
                    Constants.DrawModes.getValue(vertexBuffer.getDrawParameters()
                        .mode());

                BufferProxy.BufferInfo vertexBufferInfo = BufferProxy.getBufferInfo(
                    vertexBuffer.getBuffer());
                assert vertexBuffer.getDrawParameters()
                    .indexCount() == vertexBuffer.getDrawParameters()
                    .vertexCount() / 4 * 6;

                geometryTypeBB.putInt(geometryTypeBaseAddr, geometryTypeID);
                geometryTypeBaseAddr += Integer.BYTES;

                geometryTextureBB.putInt(geometryTextureBaseAddr, geometryTextureID);
                geometryTextureBaseAddr += Integer.BYTES;

                vertexFormatBB.putInt(vertexFormatBaseAddr, vertexFormatID);
                vertexFormatBaseAddr += Integer.BYTES;

                indexFormatBB.putInt(indexFormatBaseAddr, indexFormatID);
                indexFormatBaseAddr += Integer.BYTES;

                vertexCountBB.putInt(vertexCountBaseAddr,
                    vertexBuffer.getDrawParameters()
                        .vertexCount());
                vertexCountBaseAddr += Integer.BYTES;

                verticesBB.putLong(verticesBaseAddr, vertexBufferInfo.addr());
                verticesBaseAddr += Long.BYTES;
            }
        }

        queueBuild(lineWidth,
            coordinate.getValue(),
            normalOffset,
            entityRenderDataList.getTotalEntityCount(),
            entityHashCodeAddr,
            entityPosXAddr,
            entityPosYAddr,
            entityPosZAddr,
            entityRTFlagAddr,
            entityPrebuiltBLASAddr,
            entityPostAddr,
            entityLayerCountAddr,
            geometryTypeAddr,
            geometryTextureAddr,
            vertexFormatAddr,
            indexFormatAddr,
            vertexCountAddr,
            verticesAddr);

        // free
        MemoryUtil.memFree(entityHashCodeBB);
        MemoryUtil.memFree(entityPosXBB);
        MemoryUtil.memFree(entityPosYBB);
        MemoryUtil.memFree(entityPosZBB);
        MemoryUtil.memFree(entityRTFlagBB);
        MemoryUtil.memFree(entityPrebuiltBLASBB);
        MemoryUtil.memFree(entityPostBB);
        MemoryUtil.memFree(entityLayerCountBB);
        MemoryUtil.memFree(geometryTypeBB);
        MemoryUtil.memFree(geometryTextureBB);
        MemoryUtil.memFree(vertexFormatBB);
        MemoryUtil.memFree(indexFormatBB);
        MemoryUtil.memFree(vertexCountBB);
        MemoryUtil.memFree(verticesBB);
    }

    private static native void queueBuild(float lineWidth,
        int coordinate,
        boolean normalOffset,
        int size,
        long entityHashCodes,
        long entityPosXs,
        long entityPosYs,
        long entityPosZs,
        long entityRTFlags,
        long entityPrebuiltBLASs,
        long entityPosts,
        long entityLayerCounts,
        long geometryTypes,
        long geometryTextures,
        long vertexFormats,
        long indexFormats,
        long vertexCounts,
        long vertices);

    public static native void build();

    public record EntityRenderLayer(RenderLayer renderLayer, BuiltBuffer builtBuffer,
                                    boolean reflect) {

    }

    public static class EntityRenderData extends ArrayList<EntityRenderLayer> {

        private final int hashCode;
        private final int rtFlag;
        private final int prebuiltBLAS;
        private final int optifineRandomTextureSize;
        private final boolean post;
        private double x;
        private double y;
        private double z;

        public EntityRenderData(int hashCode, double x, double y, double z, boolean post) {
            this(hashCode, x, y, z, 0, -1, post);
        }

        public EntityRenderData(int hashCode, double x, double y, double z, int rtFlag) {
            this(hashCode, x, y, z, rtFlag, -1, false);
        }

        public EntityRenderData(int hashCode, double x, double y, double z, int rtFlag,
            int prebuiltBLAS,
            boolean post) {
            this(hashCode, x, y, z, rtFlag, prebuiltBLAS, post, -1);
        }

        public EntityRenderData(int hashCode, double x, double y, double z, int rtFlag,
            int prebuiltBLAS,
            boolean post,
            int optifineRandomTextureSize) {
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rtFlag = rtFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.post = post;
            this.optifineRandomTextureSize = optifineRandomTextureSize;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }

        public double getZ() {
            return z;
        }

        public void setZ(double z) {
            this.z = z;
        }

        public int getRtFlag() {
            return rtFlag;
        }

        public int getPrebuiltBLAS() {
            return prebuiltBLAS;
        }

        public int getHashCode() {
            return hashCode;
        }

        public int getOptifineRandomTextureSize() {
            return optifineRandomTextureSize;
        }

        public boolean isPost() {
            return post;
        }
    }

    public static class EntityRenderDataList extends ArrayList<EntityRenderData> {

        private int totalLayersCount;

        @Override
        public boolean add(EntityRenderData entityRenderData) {
            totalLayersCount += entityRenderData.size();
            return super.add(entityRenderData);
        }

        public int getTotalLayersCount() {
            return totalLayersCount;
        }

        public int getTotalEntityCount() {
            return this.size();
        }
    }
}
