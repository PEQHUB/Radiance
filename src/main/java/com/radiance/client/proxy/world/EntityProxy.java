package com.radiance.client.proxy.world;

import static net.minecraft.client.render.VertexFormat.DrawMode.LINES;
import static net.minecraft.client.render.VertexFormat.DrawMode.LINE_STRIP;
import static net.minecraft.client.render.VertexFormat.DrawMode.QUADS;
import static net.minecraft.client.render.VertexFormat.DrawMode.TRIANGLE_STRIP;
import static org.lwjgl.system.MemoryUtil.memAddress;

import com.radiance.client.constant.Constants;
import com.radiance.client.constant.Constants.RayTracingFlags;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.vertex.PBRVertexConsumer;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IHeldItemRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IParticleManagerExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.nio.ByteBuffer;
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
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.entity.projectile.FishingBobberEntity;
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
        EntityRenderDataList entityRenderDataList) {
        Map<RenderLayer, VertexConsumer> layerBuffers = storageVertexConsumerProvider.getLayers();
        EntityRenderData
            entityRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                rtFlag, prebuiltBLAS, post);
        EntityRenderData
            waterMaskRenderData =
            new EntityRenderData(hashCode, entityPosX, entityPosY,
                entityPosZ,
                RayTracingFlags.BOAT_WATER_MASK.getValue(), prebuiltBLAS, post);
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

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
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

            float tickDelta = tickCounter.getTickDelta(!tickManager.shouldSkipTick(entity));
            double entityPosX = MathHelper.lerp(tickDelta, entity.lastRenderX,
                entity.getX());
            double entityPosY = MathHelper.lerp(tickDelta, entity.lastRenderY,
                entity.getY());
            double entityPosZ = MathHelper.lerp(tickDelta, entity.lastRenderZ,
                entity.getZ());

            entityRenderDispatcher.render(entity,
                0,
                0,
                0,
                tickDelta,
                matrixStack,
                vertexConsumerProvider,
                entityRenderDispatcher.getLight(entity, tickDelta));

            if (entity.equals(camera.getFocusedEntity())) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.PLAYER,
                    true,
                    entityRenderDataList);
            } else if (entity instanceof FishingBobberEntity) {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.FISHING_BOBBER,
                    true,
                    entityRenderDataList);
            } else {
                processWorldEntityRenderData(entityStorageVertexConsumerProvider,
                    System.identityHashCode(entity),
                    entityPosX,
                    entityPosY,
                    entityPosZ,
                    Constants.RayTracingFlags.WORLD,
                    true,
                    entityRenderDataList);
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
        for (ChunkBuilder.BuiltChunk builtChunk : chunks.chunks) {
            if (builtChunk == null) {
                continue;
            }

            BlockPos chunkCenterPos = builtChunk.getOrigin().add(8, 8, 8);
            if (chunkCenterPos.getSquaredDistance(cameraBlockPos) > BLOCK_ENTITY_RENDER_DISTANCE_SQ) {
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

                    StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
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
                            crumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
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

            StorageVertexConsumerProvider entityStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
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

                    StorageVertexConsumerProvider blockCrumblingStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
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

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            8192);
        storageVertexConsumerProviders.add(storageVertexConsumerProvider);

        matrixStack.push();

        boolean bl = client.getCameraEntity() instanceof LivingEntity
            && ((LivingEntity) client.getCameraEntity()).isSleeping();
        if (client.options.getPerspective()
            .isFirstPerson() && !bl && !client.options.hudHidden &&
            client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR) {
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

    public static void queueParticleRebuild(Camera camera, float tickDelta, Frustum frustum) {
        List<StorageVertexConsumerProvider> storageVertexConsumerProviders = new ArrayList<>();
        EntityRenderDataList renderDataList = new EntityRenderDataList();

        StorageVertexConsumerProvider postStorageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
        storageVertexConsumerProviders.add(postStorageVertexConsumerProvider);

        ParticleManager particleManager = MinecraftClient.getInstance().particleManager;
        IParticleManagerExt particleManagerExt = (IParticleManagerExt) particleManager;
        Map<ParticleTextureSheet, Queue<Particle>> particles = particleManagerExt.neoVoxelRT$getParticles();

        for (ParticleTextureSheet particleTextureSheet : particleManagerExt.neoVoxelRT$getTextureSheets()) {
            Queue<Particle> particleQueue = particles.get(particleTextureSheet);
            if (particleQueue != null && !particleQueue.isEmpty()) {
                for (Particle particle : particleQueue) {

                    VertexConsumer
                        vertexConsumer =
                        postStorageVertexConsumerProvider.getBuffer(
                            Objects.requireNonNull(
                                particleTextureSheet.renderType()));

                    try {
                        particle.render(vertexConsumer, camera, tickDelta);
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

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
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

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
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

        StorageVertexConsumerProvider storageVertexConsumerProvider = new StorageVertexConsumerProvider(
            0);
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
            }
            return;
        }

        TextureManager
            textureManager =
            MinecraftClient.getInstance()
                .getTextureManager();

        ByteBuffer entityHashCodeBB = null;
        ByteBuffer entityPosXBB = null;
        ByteBuffer entityPosYBB = null;
        ByteBuffer entityPosZBB = null;
        ByteBuffer entityRTFlagBB = null;
        ByteBuffer entityPrebuiltBLASBB = null;
        ByteBuffer entityPostBB = null;
        ByteBuffer entityLayerCountBB = null;
        ByteBuffer geometryTypeBB = null;
        ByteBuffer geometryTextureBB = null;
        ByteBuffer vertexFormatBB = null;
        ByteBuffer indexFormatBB = null;
        ByteBuffer vertexCountBB = null;
        ByteBuffer verticesBB = null;

        try {
            int entityHashCodeSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityHashCodeBB = MemoryUtil.memAlloc(entityHashCodeSize);
            long entityHashCodeAddr = memAddress(entityHashCodeBB);
            int entityHashCodeBaseAddr = 0;

            int entityPosXSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosXBB = MemoryUtil.memAlloc(entityPosXSize);
            long entityPosXAddr = memAddress(entityPosXBB);
            int entityPosXBaseAddr = 0;

            int entityPosYSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosYBB = MemoryUtil.memAlloc(entityPosYSize);
            long entityPosYAddr = memAddress(entityPosYBB);
            int entityPosYBaseAddr = 0;

            int entityPosZSize = entityRenderDataList.getTotalEntityCount() * Double.BYTES;
            entityPosZBB = MemoryUtil.memAlloc(entityPosZSize);
            long entityPosZAddr = memAddress(entityPosZBB);
            int entityPosZBaseAddr = 0;

            int entityRTFlagSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityRTFlagBB = MemoryUtil.memAlloc(entityRTFlagSize);
            long entityRTFlagAddr = memAddress(entityRTFlagBB);
            int entityRTFlagBaseAddr = 0;

            int entityPrebuiltBLASSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPrebuiltBLASBB = MemoryUtil.memAlloc(entityPrebuiltBLASSize);
            long entityPrebuiltBLASAddr = memAddress(entityPrebuiltBLASBB);
            int entityPrebuiltBLASBaseAddr = 0;

            int entityPostSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityPostBB = MemoryUtil.memAlloc(entityPostSize);
            long entityPostAddr = memAddress(entityPostBB);
            int entityPostBaseAddr = 0;

            int entityLayerCountSize = entityRenderDataList.getTotalEntityCount() * Integer.BYTES;
            entityLayerCountBB = MemoryUtil.memAlloc(entityLayerCountSize);
            long entityLayerCountAddr = memAddress(entityLayerCountBB);
            int entityLayerCountBaseAddr = 0;

            int geometryTypeSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTypeBB = MemoryUtil.memAlloc(geometryTypeSize);
            long geometryTypeAddr = memAddress(geometryTypeBB);
            int geometryTypeBaseAddr = 0;

            int geometryTextureSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            geometryTextureBB = MemoryUtil.memAlloc(geometryTextureSize);
            long geometryTextureAddr = memAddress(geometryTextureBB);
            int geometryTextureBaseAddr = 0;

            int vertexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexFormatBB = MemoryUtil.memAlloc(vertexFormatSize);
            long vertexFormatAddr = memAddress(vertexFormatBB);
            int vertexFormatBaseAddr = 0;

            int indexFormatSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            indexFormatBB = MemoryUtil.memAlloc(indexFormatSize);
            long indexFormatAddr = memAddress(indexFormatBB);
            int indexFormatBaseAddr = 0;

            int vertexCountSize = entityRenderDataList.getTotalLayersCount() * Integer.BYTES;
            vertexCountBB = MemoryUtil.memAlloc(vertexCountSize);
            long vertexCountAddr = memAddress(vertexCountBB);
            int vertexCountBaseAddr = 0;

            int verticesSize = entityRenderDataList.getTotalLayersCount() * Long.BYTES;
            verticesBB = MemoryUtil.memAlloc(verticesSize);
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
                    int geometryTextureID = textureManager.getTexture(identifier).getGlId();
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
            if (entityHashCodeBB != null) MemoryUtil.memFree(entityHashCodeBB);
            if (entityPosXBB != null) MemoryUtil.memFree(entityPosXBB);
            if (entityPosYBB != null) MemoryUtil.memFree(entityPosYBB);
            if (entityPosZBB != null) MemoryUtil.memFree(entityPosZBB);
            if (entityRTFlagBB != null) MemoryUtil.memFree(entityRTFlagBB);
            if (entityPrebuiltBLASBB != null) MemoryUtil.memFree(entityPrebuiltBLASBB);
            if (entityPostBB != null) MemoryUtil.memFree(entityPostBB);
            if (entityLayerCountBB != null) MemoryUtil.memFree(entityLayerCountBB);
            if (geometryTypeBB != null) MemoryUtil.memFree(geometryTypeBB);
            if (geometryTextureBB != null) MemoryUtil.memFree(geometryTextureBB);
            if (vertexFormatBB != null) MemoryUtil.memFree(vertexFormatBB);
            if (indexFormatBB != null) MemoryUtil.memFree(indexFormatBB);
            if (vertexCountBB != null) MemoryUtil.memFree(vertexCountBB);
            if (verticesBB != null) MemoryUtil.memFree(verticesBB);

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
                    textureManager.getTexture(identifier)
                        .getGlId();
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
            this.hashCode = hashCode;
            this.x = x;
            this.y = y;
            this.z = z;
            this.rtFlag = rtFlag;
            this.prebuiltBLAS = prebuiltBLAS;
            this.post = post;
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
