package com.radiance.mixins.vulkan_render_integration;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.UnsafeManager;
import com.radiance.client.option.Options;
import com.radiance.client.cloud.CloudTileManager;
import com.radiance.client.proxy.vulkan.BufferProxy;
import com.radiance.client.proxy.world.ChunkProxy;
import com.radiance.client.proxy.world.EntityProxy;
import com.radiance.client.proxy.world.PlayerProxy;
import com.radiance.v2.bridge.EngineBridge;
import com.radiance.client.vertex.StorageVertexConsumerProvider;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IGameRendererExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.ILightMapManagerExt;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IOverlayTextureExt;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.ChunkRenderingDataPreparer;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.client.render.DimensionEffects;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.client.render.WorldBorderRendering;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.EndPortalBlockEntityRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.BlockBreakingInfo;
import net.minecraft.util.Pair;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profiler;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererMixins {

    // ── Java-side frame timing (logs every 120 frames to java_timing.log) ──
    private static long jtSetupTerrain, jtEntities, jtBlockEntities, jtParticles;
    private static long jtWeather, jtChunkRebuild, jtTotal, jtQueueSize;
    private static int jtFrameCount;
    private static void jtLog() {
        if (++jtFrameCount < 120) return;
        double n = jtFrameCount;
        double ms = 1_000_000.0; // ns to ms
        double other = (jtTotal - jtSetupTerrain - jtEntities - jtBlockEntities
            - jtParticles - jtWeather - jtChunkRebuild) / n / ms;
        String line = String.format(
            "[JAVA] setupTerrain=%.2f entities=%.2f blockEntities=%.2f particles=%.2f weather=%.2f chunkRebuild=%.2f other=%.2f TOTAL=%.2f rebuildQueue=%d",
            jtSetupTerrain/n/ms, jtEntities/n/ms, jtBlockEntities/n/ms,
            jtParticles/n/ms, jtWeather/n/ms, jtChunkRebuild/n/ms,
            other, jtTotal/n/ms, jtQueueSize / jtFrameCount);
        System.out.println(line);
        try (java.io.FileWriter fw = new java.io.FileWriter("C:/RadSER/results/java_timing.log", true)) {
            fw.write(line + "\n");
        } catch (Exception ignored) {}
        jtSetupTerrain = jtEntities = jtBlockEntities = jtParticles = 0;
        jtWeather = jtChunkRebuild = jtTotal = jtQueueSize = 0;
        jtFrameCount = 0;
    }

    @Shadow
    private ClientWorld world;

    @Final
    @Shadow
    private MinecraftClient client;

    @Final
    @Shadow
    private EntityRenderDispatcher entityRenderDispatcher;

    @Final
    @Shadow
    private BlockEntityRenderDispatcher blockEntityRenderDispatcher;

    @Shadow
    private BuiltChunkStorage chunks;

    @Shadow
    private Frustum frustum;

    @Final
    @Shadow
    private List<Entity> renderedEntities;

    @Shadow
    private int renderedEntitiesCount;

    @Shadow
    private double lastCameraPitch;

    @Shadow
    private double lastCameraYaw;

    @Final
    @Shadow
    private ObjectArrayList<ChunkBuilder.BuiltChunk> builtChunks;

    @Shadow
    @Final
    private Long2ObjectMap<SortedSet<BlockBreakingInfo>> blockBreakingProgressions;

    @Shadow
    @Final
    private Set<BlockEntity> noCullingBlockEntities;

    @Shadow
    @Final
    private WeatherRendering weatherRendering;

    @Shadow
    @Final
    private WorldBorderRendering worldBorderRendering;

    @Shadow
    private int ticks;
    @Shadow
    @Final
    private CloudRenderer cloudRenderer;
    // endregion

    // region <init>
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "net/minecraft/client/render/SkyRendering"))
    private SkyRendering cancelNewSkyRendering() {
        return UnsafeManager.INSTANCE.allocateInstance(SkyRendering.class);
    }
    // endregion

    @Redirect(method = "scheduleTerrainUpdate()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;scheduleTerrainUpdate()V"))
    public void cancelTerrainUpdateWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance) {

    }

    // region <close>
    @Redirect(method = "close()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/SkyRendering;close()V"))
    public void cancelSkyRenderingClose(SkyRendering instance) {

    }

    @Redirect(method = "reload(Lnet/minecraft/resource/ResourceManager;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/WorldRenderer;loadEntityOutlinePostProcessor()V"))
    public void cancelReloadWithResourceManager(WorldRenderer instance) {

    }

    @Redirect(method = "reload()V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;setStorage"
            + "(Lnet/minecraft/client/render/BuiltChunkStorage;)V"))
    public void cancelReloadWithChunkRenderingDataPreparerSetStorage(
        ChunkRenderingDataPreparer instance, BuiltChunkStorage storage) {

    }

    @Redirect(method = "getEntitiesToRender(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;isThirdPerson()Z"))
    public boolean enablePlayerRendererInFirstPlayer(Camera instance) {
        return true;
    }

    @Redirect(method = "getEntitiesToRender(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;Ljava/util/List;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z"))
    public <E extends Entity> boolean loosenEntityFiltering(EntityRenderDispatcher instance,
        E entity, Frustum frustum, double x, double y, double z) {
        double dx = entity.getX() - x, dy = entity.getY() - y, dz = entity.getZ() - z;
        if (dx * dx + dy * dy + dz * dz < 2304.0) { // 48^2
            return true;
        }
        return this.entityRenderDispatcher.shouldRender(entity, frustum, x, y, z);
    }

    // region <render>
    @Shadow
    protected abstract void setupTerrain(Camera camera, Frustum frustum, boolean hasForcedFrustum,
        boolean spectator);

    @Shadow
    protected abstract boolean getEntitiesToRender(Camera camera, Frustum frustum,
        List<Entity> output);

    @Shadow
    protected abstract boolean canDrawEntityOutlines();

    @Shadow
    protected abstract void applyFrustum(Frustum frustum);

    @Shadow
    protected abstract boolean isSkyDark(float tickDelta);

    @Shadow
    protected abstract boolean hasBlindnessOrDarkness(Camera camera);

    @Inject(method =
        "render(Lnet/minecraft/client/util/ObjectAllocator;Lnet/minecraft/client/render/RenderTickCounter;"
            + "ZLnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/GameRenderer;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V", at = @At("HEAD"), cancellable = true)
    public void redirectRender(ObjectAllocator allocator, RenderTickCounter tickCounter,
        boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
        Matrix4f effectedRotationMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        // Offline: camera position override
        if (Options.offlineState == 2) {
            // Accumulating: use frozen camera position
            PlayerProxy.setCameraPos(new net.minecraft.util.math.Vec3d(
                Options.frozenCamX, Options.frozenCamY, Options.frozenCamZ));
        } else if (Options.offlineState == 1 && Options.freecamEnabled) {
            // Per-frame freecam movement update (smooth, frame-rate independent)
            long handle = this.client.getWindow().getHandle();
            if (this.client.currentScreen == null) {
                float fwd = 0, str = 0, up = 0;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_W) == 1) fwd += 1;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_S) == 1) fwd -= 1;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_A) == 1) str += 1;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_D) == 1) str -= 1;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) == 1) up += 1;
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == 1) up -= 1;
                boolean slow = org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL) == 1;
                Options.freecam.tick(fwd, str, up, Options.freecamSpeed, slow);
            }
            // Free mode + freecam: use freecam position
            PlayerProxy.setCameraPos(new net.minecraft.util.math.Vec3d(
                Options.freecam.x, Options.freecam.y, Options.freecam.z));
        } else {
            PlayerProxy.setCameraPos(camera.getPos());
        }

        float f = tickCounter.getTickDelta(false);
        RenderSystem.setShaderGameTime(this.world.getTime(), f);
        this.blockEntityRenderDispatcher.configure(this.world, camera, this.client.crosshairTarget);
        this.entityRenderDispatcher.configure(this.world, camera, this.client.targetedEntity);

        this.world.runQueuedChunkUpdates();
        this.world.getChunkManager().getLightingProvider().doLightUpdates();

        Frustum frustum = this.frustum;

        Vec3d vec3d = camera.getPos();
        double x = vec3d.getX();
        double y = vec3d.getY();
        double z = vec3d.getZ();

        long jtT0 = System.nanoTime(), jtTframe = jtT0;
        this.setupTerrain(camera, frustum, false, false);

        boolean renderEntityOutline = this.getEntitiesToRender(camera, frustum,
            this.renderedEntities);
        jtSetupTerrain += System.nanoTime() - jtT0;

        Matrix4f viewMatrix;
        Matrix4f effectedViewMatrix;

        // Offline freecam/frozen: override view matrix from freecam or frozen rotation
        if (Options.offlineState == 2) {
            // Accumulating: build view matrix from frozen yaw/pitch
            viewMatrix = buildViewMatrix(Options.frozenCamYaw, Options.frozenCamPitch);
            effectedViewMatrix = new Matrix4f(viewMatrix);
        } else if (Options.offlineState == 1 && Options.freecamEnabled) {
            // Free mode + freecam: build view matrix from freecam rotation (from instance)
            viewMatrix = buildViewMatrix(Options.freecam.yaw, Options.freecam.pitch);
            effectedViewMatrix = new Matrix4f(viewMatrix);
        } else {
            viewMatrix = new Matrix4f(
                ((IGameRendererExt) gameRenderer).neoVoxelRT$getRotationMatrix());
            effectedViewMatrix = new Matrix4f(effectedRotationMatrix);
        }

        // Offline: override projection matrix with camera model FOV
        if (Options.offlineState != 0) {
            float fovRad = Options.computeFovVerticalRad();
            float aspectRatio = (float) this.client.getWindow().getWidth()
                / (float) this.client.getWindow().getHeight();
            projectionMatrix = new Matrix4f().setPerspective(fovRad, aspectRatio, 0.05f,
                gameRenderer.getViewDistance() * 4.0f);
        }

        // fog
        float h = gameRenderer.getViewDistance();
        boolean bl2 = this.client.world.getDimensionEffects()
            .useThickFog(MathHelper.floor(x), MathHelper.floor(y))
            || this.client.inGameHud.getBossBarHud().shouldThickenFog();
        Vector4f vector4f = BackgroundRenderer.getFogColor(camera, f, this.client.world,
            this.client.options.getClampedViewDistance(), gameRenderer.getSkyDarkness(f));
        Fog fog = BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN,
            vector4f, h, bl2, f);

        TextureManager textureManager = MinecraftClient.getInstance().getTextureManager();
        OverlayTexture overlayTexture = gameRenderer.getOverlayTexture();
        int overlayTextureID = ((IOverlayTextureExt) overlayTexture).neoVoxelRT$getTexture()
            .getGlId();
        int endSkyTextureID = textureManager.getTexture(EndPortalBlockEntityRenderer.SKY_TEXTURE)
            .getGlId();
        int endPortalTextureID = textureManager.getTexture(
            EndPortalBlockEntityRenderer.PORTAL_TEXTURE).getGlId();
        BufferProxy.updateWorldUniform(camera, viewMatrix, effectedViewMatrix, projectionMatrix,
            overlayTextureID, fog, world, endSkyTextureID, endPortalTextureID);

        // V2 camera update
        if (EngineBridge.isV2Active()) {
            float[] viewArr = new float[16];
            float[] projArr = new float[16];
            viewMatrix.get(viewArr);
            projectionMatrix.get(projArr);
            net.minecraft.util.math.Vec3d camPos = camera.getPos();
            org.joml.Vector3f camDir = camera.getHorizontalPlane();
            EngineBridge.updateCamera(viewArr, projArr,
                (float) camPos.x, (float) camPos.y, (float) camPos.z,
                camDir.x(), camDir.y(), camDir.z(),
                0.05f, gameRenderer.getViewDistance() * 4.0f);
        }

        // Sky
        float tickDelta = tickCounter.getTickDelta(false);
        int envDim = Options.getEnvironmentDimensionIndex(this.world);
        float skyBrightness = Options.getSkyBrightness(envDim);
        float rainBlendStrength = Options.getRainBlendStrength(envDim);
        float sunSizeMultiplier = Options.getSunSizeMultiplier(envDim);
        float moonSizeMultiplier = Options.getMoonSizeMultiplier(envDim);
        float sunIntensityMultiplier = Options.getSunIntensityMultiplier(envDim);
        float moonIntensityMultiplier = Options.getMoonIntensityMultiplier(envDim);
        float waterTintR = Options.getWaterTintR(envDim);
        float waterTintG = Options.getWaterTintG(envDim);
        float waterTintB = Options.getWaterTintB(envDim);
        float waterFogStrength = Options.getWaterFogStrength(envDim);
        float skyAngle = this.world.getSkyAngle(tickDelta);

        int baseColor = this.world.getSkyColor(this.client.gameRenderer.getCamera().getPos(),
            tickDelta);
        float baseColorR = ColorHelper.getRedFloat(baseColor) * skyBrightness;
        float baseColorG = ColorHelper.getGreenFloat(baseColor) * skyBrightness;
        float baseColorB = ColorHelper.getBlueFloat(baseColor) * skyBrightness;

        DimensionEffects dimensionEffects = this.world.getDimensionEffects();
        int horizontalColor = dimensionEffects.getSkyColor(skyAngle);
        float horizontalColorR = ColorHelper.getRedFloat(horizontalColor) * skyBrightness;
        float horizontalColorG = ColorHelper.getGreenFloat(horizontalColor) * skyBrightness;
        float horizontalColorB = ColorHelper.getBlueFloat(horizontalColor) * skyBrightness;
        float horizontalColorA = ColorHelper.getAlphaFloat(horizontalColor);

        // Compute sun/moon direction from the actual day-time tick value.
        // This keeps lighting aligned with `/time set ...` and avoids phase errors from getSkyAngle().
        long dayTimeTicks = Options.frozenDayTimeTicks >= 0
            ? Options.frozenDayTimeTicks
            : (this.world.getTimeOfDay() % 24000L);
        float dayFrac = (dayTimeTicks + tickDelta) / 24000.0F;
        float skyAngleForSun = dayFrac - 0.25F;

        Vector3f sunDirection;
        Vector3f moonDirection;

        if (Options.sunPathMode == 1) {
            // Physical mode: apply inclination (axial tilt) and azimuth offset
            float inclDeg = (float) Options.sunInclinationDeg;
            float azOffDeg = (float) Options.sunAzimuthOffsetDeg;

            MatrixStack ms = new MatrixStack();
            ms.push();
            // 1. Base Y rotation (vanilla baseline) + azimuth offset
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F + azOffDeg));
            // 2. Tilt the orbital plane by inclination (Z-axis rotation tilts the orbit)
            ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(inclDeg));
            // 3. Rotate sun around the tilted orbital plane
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngleForSun * 360.0F));
            Matrix4f rot = ms.peek().getPositionMatrix();
            sunDirection = rot.transformPosition(0, 1, 0, new Vector3f()).normalize();
            ms.pop();

            // Moon direction
            float moonInclDeg, moonAzOffDeg;
            if (Options.moonFollowSun) {
                moonInclDeg = inclDeg;
                moonAzOffDeg = azOffDeg;
            } else {
                moonInclDeg = (float) Options.moonInclinationDeg;
                moonAzOffDeg = (float) Options.moonAzimuthOffsetDeg;
            }
            MatrixStack moonMs = new MatrixStack();
            moonMs.push();
            moonMs.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F + moonAzOffDeg));
            moonMs.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(moonInclDeg));
            // Moon is 180deg opposite the sun in its orbit
            moonMs.multiply(RotationAxis.POSITIVE_X.rotationDegrees((skyAngleForSun + 0.5F) * 360.0F));
            Matrix4f moonRot = moonMs.peek().getPositionMatrix();
            moonDirection = moonRot.transformPosition(0, 1, 0, new Vector3f()).normalize();
            moonMs.pop();
        } else {
            // Legacy mode: vanilla-style overhead arc (current behavior)
            MatrixStack ms = new MatrixStack();
            ms.push();
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-90.0F));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(skyAngleForSun * 360.0F));
            Matrix4f rot = ms.peek().getPositionMatrix();
            // Shaders treat sunDirection.y as elevation above the horizon.
            // In vanilla sky rendering the sun quad starts at +Y before being rotated.
            sunDirection = rot.transformPosition(0, 1, 0, new Vector3f()).normalize();
            ms.pop();

            // Moon opposite sun in legacy mode
            moonDirection = new Vector3f(-sunDirection.x, -sunDirection.y, -sunDirection.z);
        }

        int skyType = dimensionEffects.getSkyType().ordinal();

        boolean sunRisingOrSetting = dimensionEffects.isSunRisingOrSetting(skyAngle);

        boolean skyDark = this.isSkyDark(tickDelta);

        boolean hasBlindnessOrDarkness = this.hasBlindnessOrDarkness(camera);

        int submersionType = camera.getSubmersionType().ordinal();

        int moonPhase = this.world.getMoonPhase();

        float rainGradient = this.world.getRainGradient(tickDelta);
        float thunderGradient = this.world.getThunderGradient(tickDelta);

        int sunTextureID = textureManager.getTexture(SkyRendering.SUN_TEXTURE).getGlId();

        int moonTextureID = textureManager.getTexture(SkyRendering.MOON_PHASES_TEXTURE).getGlId();

        CloudRenderMode cloudRenderMode = this.client.options.getCloudRenderModeValue();
        float cloudBaseHeight = Float.NaN;
        float cloudThickness = Options.getCloudThicknessBlocks(envDim);
        float cloudDensityScale = 0.0F;
        float cloudAlbedoScale = Options.getCloudBrightness(envDim);

        float cloudPuffiness = Options.getCloudPuffiness(envDim);
        float cloudDetailScale = Options.getCloudDetailScale(envDim);
        // Fast = analytic flat slab (detailStrength=0); Fancy = 3D FBM stepped volumetric.
        float cloudDetailStrength = (cloudRenderMode == CloudRenderMode.FANCY)
            ? Options.getCloudDetailStrength(envDim)
            : 0.0f;
        float cloudAnisotropy = Options.getCloudAnisotropy(envDim);
        float cloudShadowStrength = Options.getCloudShadowStrength(envDim);
        float cloudDensity = Options.getCloudDensity(envDim);
        float cloudNoiseAffectsShadows = Options.getCloudNoiseAffectsShadows(envDim) ? 1.0F : 0.0F;

        float cloudAmbientStrength = 0.15F;
        float cloudSunOcclusionStrength = 1.0F;

        float vanillaCloudsHeight = this.world.getDimensionEffects().getCloudsHeight();
        if (cloudRenderMode != CloudRenderMode.OFF && !Float.isNaN(vanillaCloudsHeight)) {
            cloudBaseHeight = vanillaCloudsHeight + 0.33F + Options.getCloudHeightOffset(envDim);
            float cloudAlpha = Options.getCloudAlpha(envDim);
            // Extinction coefficient in "per block" units. Tuned so 100% alpha produces visible sun occlusion and ground shadows.
            cloudDensityScale = 0.35F * cloudAlpha * cloudDensity;
        }

        BufferProxy.updateMapping();

        ILightMapManagerExt lightMapManagerExt = (ILightMapManagerExt) (gameRenderer.getLightmapTextureManager());
        BufferProxy.updateLightMapUniform(lightMapManagerExt.neoVoxelRT$getAmbientLightFactor(),
            lightMapManagerExt.neoVoxelRT$getSkyFactor(),
            lightMapManagerExt.neoVoxelRT$getBlockFactor(),
            lightMapManagerExt.neoVoxelRT$isUseBrightLightmap(),
            lightMapManagerExt.neoVoxelRT$getSkyLightColor(),
            lightMapManagerExt.neoVoxelRT$getNightVisionFactor(),
            lightMapManagerExt.neoVoxelRT$getDarknessScale(),
            lightMapManagerExt.neoVoxelRT$getDarkenWorldFactor(),
            lightMapManagerExt.neoVoxelRT$getBrightnessFactor(),
            this.world,
            tickDelta);

        // Entities
        jtT0 = System.nanoTime();
        EntityProxy.queueEntitiesBuild(camera, renderedEntities, this.entityRenderDispatcher,
            tickCounter, canDrawEntityOutlines());
        jtEntities += System.nanoTime() - jtT0;

        jtT0 = System.nanoTime();
        Pair<List<StorageVertexConsumerProvider>, EntityProxy.EntityRenderDataList> crumblingRenderData = EntityProxy.queueBlockEntitiesRebuild(
            camera, chunks, this.noCullingBlockEntities, blockBreakingProgressions,
            blockEntityRenderDispatcher, tickDelta);
        EntityProxy.queueCrumblingRebuild(camera, blockBreakingProgressions,
            this.client.getBlockRenderManager(), this.world, crumblingRenderData.getLeft(),
            crumblingRenderData.getRight());
        jtBlockEntities += System.nanoTime() - jtT0;

        jtT0 = System.nanoTime();
        EntityProxy.queueParticleRebuild(camera, tickDelta, frustum);
        jtParticles += System.nanoTime() - jtT0;

        if (renderBlockOutline) {
            EntityProxy.queueTargetBlockOutlineRebuild(camera, world);
        }

        jtT0 = System.nanoTime();
        EntityProxy.queueWeatherBuild(this.weatherRendering, this.worldBorderRendering, this.world,
            camera, this.ticks, tickDelta);
        jtWeather += System.nanoTime() - jtT0;

        // clouds
        if (cloudRenderMode != CloudRenderMode.OFF) {
            if (!Float.isNaN(vanillaCloudsHeight)) {
                float ticks = (float) this.ticks + f;
                int color = this.world.getCloudsColor(f);
                float cloudHeight = vanillaCloudsHeight + 0.33F + Options.getCloudHeightOffset(envDim);
                this.cloudRenderer.renderClouds(color, cloudRenderMode, cloudHeight, null, null,
                    camera.getPos(), ticks);
            }
        }

        int cloudTileTextureID = -1;
        int cloudCenterX = 0;
        int cloudCenterZ = 0;
        float cloudOffsetX = 0.0F;
        float cloudOffsetZ = 0.0F;
        float cloudTicks = 0.0F;
        if (CloudTileManager.isValid()) {
            cloudTileTextureID = CloudTileManager.getCloudMaskTextureId();
            cloudCenterX = CloudTileManager.getCenterCellX();
            cloudCenterZ = CloudTileManager.getCenterCellZ();
            cloudOffsetX = CloudTileManager.getOffsetX();
            cloudOffsetZ = CloudTileManager.getOffsetZ();
            cloudTicks = CloudTileManager.getTicks();
        }

        BufferProxy.updateSkyUniform(baseColorR, baseColorG, baseColorB, horizontalColorR,
            horizontalColorG, horizontalColorB, horizontalColorA, sunDirection, moonDirection,
            skyType, sunRisingOrSetting, skyDark, hasBlindnessOrDarkness, submersionType,
            moonPhase, rainGradient, thunderGradient, sunTextureID, moonTextureID, sunSizeMultiplier,
            moonSizeMultiplier, sunIntensityMultiplier, moonIntensityMultiplier,
            waterTintR, waterTintG, waterTintB, waterFogStrength, rainBlendStrength,
            skyBrightness, cloudBaseHeight, cloudThickness, cloudDensityScale, cloudAlbedoScale,
            cloudTileTextureID, cloudCenterX, cloudCenterZ,
            cloudOffsetX, cloudOffsetZ, cloudTicks,
            cloudPuffiness, cloudDetailScale, cloudDetailStrength, cloudAnisotropy,
            cloudShadowStrength, cloudAmbientStrength, cloudSunOcclusionStrength,
            cloudNoiseAffectsShadows);

        // V2 sky update — subset of V1 fields, atmosphere/clouds added later
        if (EngineBridge.isV2Active()) {
            EngineBridge.updateSky(
                baseColorR, baseColorG, baseColorB,
                horizontalColorR, horizontalColorG, horizontalColorB, horizontalColorA,
                sunDirection.x, sunDirection.y, sunDirection.z,
                moonDirection.x, moonDirection.y, moonDirection.z,
                skyType, sunRisingOrSetting, skyDark,
                hasBlindnessOrDarkness, submersionType, moonPhase,
                rainGradient, thunderGradient,
                sunTextureID, moonTextureID);
        }

        // Chunks
        jtT0 = System.nanoTime();
        ChunkProxy.rebuild(camera);
        jtChunkRebuild += System.nanoTime() - jtT0;
        jtQueueSize += ChunkProxy.getRebuildQueueSize();

        long jtTend = System.nanoTime();
        long jtThisFrame = jtTend - jtTframe;
        jtTotal += jtThisFrame;
        jtLog();

        this.renderedEntities.clear();

        ci.cancel();
    }
    // endregion

    /** Build a view matrix from yaw/pitch (degrees). Matches Minecraft camera convention. */
    private static Matrix4f buildViewMatrix(float yaw, float pitch) {
        Matrix4f mat = new Matrix4f();
        mat.rotateX((float) Math.toRadians(pitch));
        mat.rotateY((float) Math.toRadians(yaw + 180.0f));
        return mat;
    }

    // region <setWorld>
    @Redirect(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;setStorage"
            + "(Lnet/minecraft/client/render/BuiltChunkStorage;)V"))
    public void cancelSetWorldChunkRenderingDataPreparerSetStorage(
        ChunkRenderingDataPreparer instance, BuiltChunkStorage storage) {

    }
    // endregion

    //region <setupTerrain>
    @Inject(method = "setupTerrain(Lnet/minecraft/client/render/Camera;Lnet/minecraft/client/render/Frustum;ZZ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V", shift = At.Shift.AFTER), cancellable = true)
    public void cancelCullAndUpdateWithChunkRenderingDataPreparer(Camera camera, Frustum frustum,
        boolean hasForcedFrustum, boolean spectator, CallbackInfo ci, @Local Profiler profiler) {
//        PlayerProxy.setCameraPos(camera.getPos());
        profiler.pop();
        ci.cancel();
    }
    //endregion

    // region <addBuiltChunk>
    @Redirect(method = "addBuiltChunk(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;schedulePropagationFrom"
            + "(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V"))
    public void cancelPropagateWithChunkRenderingDataPreparer(ChunkRenderingDataPreparer instance,
        ChunkBuilder.BuiltChunk builtChunk) {

    }
    // endregion

    // region <onChunkUnload>
    @Redirect(method = "onChunkUnload(J)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;schedulePropagationFrom"
            + "(Lnet/minecraft/client/render/chunk/ChunkBuilder$BuiltChunk;)V"))
    public void cancelPropagateUnloadWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance, ChunkBuilder.BuiltChunk builtChunk) {

    }
    // endregion

    // region <scheduleNeighborUpdates>
    @Redirect(method = "scheduleNeighborUpdates(Lnet/minecraft/util/math/ChunkPos;)V", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/ChunkRenderingDataPreparer;addNeighbors(Lnet/minecraft/util/math/ChunkPos;)"
            + "V"))
    public void cancelNeighborUpdatesWithChunkRenderingDataPreparer(
        ChunkRenderingDataPreparer instance, ChunkPos chunkPos) {

    }
    // endregion

    // region <isRenderingReady>
    @Inject(method = "isRenderingReady(Lnet/minecraft/util/math/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void redirectIsRenderingReady(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ChunkBuilder.BuiltChunk builtChunk = chunks.getRenderedChunk(pos);

        if (builtChunk == null) {
            cir.setReturnValue(false);
        } else if (builtChunk.data.get().isEmpty(null)) {
            cir.setReturnValue(true);
        } else if (builtChunk.data.get() == ChunkProxy.PROCESSED) {
            cir.setReturnValue(ChunkProxy.isChunkReady(builtChunk));
        }
    }
    // endregion

    // region <>
    @Inject(method = "getCompletedChunkCount()I", at = @At(value = "HEAD"), cancellable = true)
    public void fixGetCompletedChunkCount(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(ChunkProxy.builtChunkNum - 54); // 54 + 10 = 64
    }
    // endregion
}
