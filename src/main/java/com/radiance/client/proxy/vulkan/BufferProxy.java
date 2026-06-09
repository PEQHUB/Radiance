package com.radiance.client.proxy.vulkan;

import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memFree;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.Constants;
import com.radiance.client.fpv.FirstPersonView;
import com.radiance.client.option.Options;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.SpectralColor;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.texture.compat.ResourcePackLightmapResolver;
import com.radiance.client.texture.compat.ResourcePackLightmapResolver.LightmapSample;
import com.radiance.v2.bridge.EngineBridge;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.world.ClientWorld;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public class BufferProxy {

    // Animation tick counter — incremented per frame for texture array sprite animation
    public static int animTick = 0;

    public static native int allocateBuffer();

    public static native void initializeBuffer(int id, int size, int usageFlags);

    public static native void buildIndexBuffer(int id, int type, int drawMode, int vertexCount,
        int expectedIndexCount);

    public static native void queueUpload(long ptr, int dstId);

    public static BufferInfo getBufferInfo(ByteBuffer buf) {
        ByteBuffer b = buf.slice();

        assert b.isDirect();

        long addr = memAddress(b);
        int size = b.remaining();
        return new BufferInfo(buf, addr, size);
    }

    private static void queueUpload(ByteBuffer buf, int expectedSize, int dstId) {
        BufferInfo bufferInfo = getBufferInfo(buf);
        assert bufferInfo.size == expectedSize;
        queueUpload(bufferInfo.addr, dstId);
    }

    public static native void performQueuedUpload();

    public static VertexIndexBufferHandle createAndUploadVertexIndexBuffer(
        BuiltBuffer builtBuffer) {
        BuiltBuffer.DrawParameters drawParameters = builtBuffer.getDrawParameters();
        assert builtBuffer.getDrawParameters().mode() == VertexFormat.DrawMode.QUADS;

        int vertexSize = drawParameters.vertexCount() * drawParameters.format().getVertexSizeByte();
        int vertexId = allocateBuffer();
        initializeBuffer(vertexId, vertexSize, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT.getValue());
        queueUpload(builtBuffer.getBuffer(), vertexSize, vertexId);

        int indexSize = drawParameters.indexCount() * drawParameters.indexType().size;
        int indexId = allocateBuffer();
        initializeBuffer(indexId, indexSize, VK_BUFFER_USAGE_INDEX_BUFFER_BIT.getValue());
        if (builtBuffer.getSortedBuffer() != null) {
            queueUpload(builtBuffer.getSortedBuffer(), indexSize, indexId);
        } else {
            int type = Constants.IndexTypes.getValue(drawParameters.indexType());
            int drawMode = Constants.DrawModes.getValue(drawParameters.mode());
            buildIndexBuffer(indexId, type, drawMode, drawParameters.vertexCount(),
                drawParameters.indexCount());
        }

        return new VertexIndexBufferHandle(vertexId, indexId);
    }

    public static native void updateOverlayDrawUniform(long ptr);

    public static void updateOverlayDrawUniform() {
        try (MemoryStack stack = stackPush()) {
            int size = 336;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            for (int i = 0; i < 12; i++) {
                int texture = RenderSystem.getShaderTexture(i);
                bb.putInt(baseAddr, texture);
                baseAddr += Integer.BYTES;
            }

            Matrix4f modelViewMat = RenderSystem.getModelViewMatrix();
            modelViewMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            float[] shaderColor = RenderSystem.getShaderColor();
            for (int i = 0; i < 4; i++) {
                bb.putFloat(baseAddr, shaderColor[i]);
                baseAddr += Float.BYTES;
            }

            float shaderGlintAlpha = RenderSystem.getShaderGlintAlpha();
            bb.putFloat(baseAddr, shaderGlintAlpha);
            baseAddr += Float.BYTES;

            Fog fog = RenderSystem.getShaderFog();
            float fogStart = fog.start();
            bb.putFloat(baseAddr, fogStart);
            baseAddr += Float.BYTES;

            float fogEnd = fog.end();
            bb.putFloat(baseAddr, fogEnd);
            baseAddr += Float.BYTES;

            int fogShape = fog.shape().getId();
            bb.putInt(baseAddr, fogShape);
            baseAddr += Integer.BYTES;

            float[] fogColor = {fog.red(), fog.green(), fog.blue(), fog.alpha()};
            for (int i = 0; i < 4; i++) {
                bb.putFloat(baseAddr, fogColor[i]);
                baseAddr += Float.BYTES;
            }

            Matrix4f textureMat = RenderSystem.getTextureMatrix();
            textureMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            float gameTime = RenderSystem.getShaderGameTime();
            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            float lineWidth = RenderSystem.getShaderLineWidth();
            bb.putFloat(baseAddr, lineWidth);
            baseAddr += Float.BYTES;

            float framebufferWidth = MinecraftClient.getInstance().getWindow()
                .getFramebufferWidth();
            bb.putFloat(baseAddr, framebufferWidth);
            baseAddr += Float.BYTES;

            float framebufferHeight = MinecraftClient.getInstance().getWindow()
                .getFramebufferHeight();
            bb.putFloat(baseAddr, framebufferHeight);
            baseAddr += Float.BYTES;

            Vector3f shaderLightDirection0 = RenderSystem.shaderLightDirections[0];
            shaderLightDirection0.get(baseAddr, bb);
            baseAddr += Float.BYTES * 4;

            Vector3f shaderLightDirection1 = RenderSystem.shaderLightDirections[1];
            shaderLightDirection1.get(baseAddr, bb);

            updateOverlayDrawUniform(addr);
        }
    }

    public static native void updateOverlayPostUniform(long ptr);

    public static void updateOverlayPostUniform(float radius) {
        try (MemoryStack stack = stackPush()) {
            int size = 96;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            Matrix4f projectionMatrix = new Matrix4f();
            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            for (int i = 0; i < 2; i++) {
                baseAddr += Float.BYTES;
            }

            float[] blurDir = {1.0f, 1.0f};
            for (int i = 0; i < 2; i++) {
                bb.putFloat(baseAddr, blurDir[i]);
                baseAddr += Float.BYTES;
            }

            bb.putFloat(baseAddr, radius);
            baseAddr += Float.BYTES;

            float radiusMultiplier = 1.0f;
            bb.putFloat(baseAddr, radiusMultiplier);

            updateOverlayPostUniform(addr);
        }
    }

    public static native void updateWorldUniform(long ptr);

    public static void updateWorldUniform(Camera camera, Matrix4f viewMatrix,
        Matrix4f effectedViewMatrix, Matrix4f projectionMatrix, int overlayTextureID, Fog fog,
        ClientWorld world, int endSkyTextureID, int endPortalTextureID) {
        int nextAnimTick = world != null ? (int) world.getTime() : animTick + 1;
        if (nextAnimTick != animTick) {
            animTick = nextAnimTick;
            if (com.radiance.client.texture.TextureTracker.textureArrayAnimationUpdatesEnabled) {
                // Update animated sprite textures (water, lava, etc.) - re-uploads current frame pixels
                com.radiance.client.proxy.vulkan.TextureArrayBridge.updateAnimatedSprites(animTick);
            }
        }
        try (MemoryStack stack = stackPush()) {
            int size = 560 + 50 * 16 + 13 * 16; // base + emissionData[50] + emissiveGamut[13] (materialData moved to SSBO)
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            viewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            effectedViewMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            projectionMatrix.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;

            baseAddr += Float.BYTES * 16 * 3; // skip the inverse
            baseAddr += Float.BYTES * 2; // skip the jitter

            float gameTime = RenderSystem.getShaderGameTime();
            bb.putFloat(baseAddr, gameTime);
            baseAddr += Float.BYTES;

            baseAddr += Integer.BYTES; // skip seed

            RenderPhase.setupGlintTexturing(0.16F);
            Matrix4f textureMat = RenderSystem.getTextureMatrix();
            textureMat.get(baseAddr, bb);
            baseAddr += Float.BYTES * 16;
            RenderSystem.resetTextureMatrix();

            bb.putInt(baseAddr, overlayTextureID);
            baseAddr += Integer.BYTES;
            // isFirstPerson: 0 = third person (show body+head), 1 = first person (hide all),
            //                2 = FPV (show body, hide head from direct rays)
            int firstPersonMode;
            if (Options.offlineState != 0 && Options.freecamEnabled) {
                firstPersonMode = Options.freecamShowPlayer ? 0 : 1;
            } else if (FirstPersonView.isActive()) {
                firstPersonMode = 2; // FPV: body visible, head hidden from direct rays
            } else if (camera.isThirdPerson()) {
                firstPersonMode = 0; // third person: everything visible
            } else {
                firstPersonMode = 1; // regular first person: hide player
            }
            bb.putInt(baseAddr, firstPersonMode);
            baseAddr += Integer.BYTES;
            bb.putFloat(baseAddr, fog.start());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.end());
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, fog.red());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.green());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.blue());
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, fog.alpha());
            baseAddr += Float.BYTES;

            bb.putInt(baseAddr, fog.shape().getId());
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, world.getDimensionEffects().getSkyType().ordinal());
            baseAddr += Integer.BYTES;

            baseAddr += Float.BYTES; // rayBounces (C++ fills)

            // Do not skip world geometry with a block-sized tmin. The previous
            // camera-inside-block workaround quantized to the camera's block and
            // advanced every primary ray by up to the block diagonal, which carved
            // a large snapping void through nearby terrain. Interior foliage/camera
            // clipping needs per-hit handling, not a global ray start shift.
            float cameraTmin = 0.0f;
            bb.putFloat(baseAddr, cameraTmin);
            baseAddr += Float.BYTES;

            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos

            bb.putInt(baseAddr, endSkyTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, endPortalTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, animTick); // animation tick counter for texture array sprites
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES; // pad5

            // Emission data: vec4[40], one per EmissiveBlock ordinal
            // .rgb = BT.2020 flame color override (0,0,0 = use texture tint)
            // .a   = scalar multiplier: (currentNits * userScale) / defaultNits
            EmissiveBlock[] blocks = EmissiveBlock.values();
            for (int i = 0; i < 50; i++) {
                float r = 0, g = 0, b = 0, mult = 1.0f;
                if (i < blocks.length) {
                    EmissiveBlock blk = blocks[i];
                    float defNits = blk.getDefaultSurfaceNits();
                    if (defNits > 0) {
                        mult = (blk.getSurfaceNits() * blk.getValue()) / defNits;
                    }
                    // Lava texture emission kill-switch (diagnostic toggle)
                    if (i == 0 && !Options.lavaTextureEmissionEnabled) {
                        mult = 0;
                    }
                    // Uniform glow blocks skip texture luminance mask (emit from all texels equally).
                    // Sign convention: negative multiplier = uniform glow in shader.
                    if (blk.isUniformGlow() && mult > 0) {
                        mult = -mult;
                    }
                    // Spectral color: compute from temperature + wavelength + purity (all blocks)
                    int wl = Options.getBlockWavelength(blk);
                    int pur = Options.getBlockPurity(blk);
                    if (wl > 0 && pur > 0) {
                        int tempC = Options.getBlockTemperature(blk);
                        float tempK = tempC + 273.15f;
                        float[] color = SpectralColor.computeFlameColor(tempK, wl, pur / 100.0f);
                        r = color[0]; g = color[1]; b = color[2];
                    }
                }
                int off = baseAddr + i * 16; // vec4 = 16 bytes
                bb.putFloat(off,      r);
                bb.putFloat(off + 4,  g);
                bb.putFloat(off + 8,  b);
                bb.putFloat(off + 12, mult);
            }
            baseAddr += 50 * 16; // 50 × vec4

            // Per-emissive-block gamut boost: 13 vec4 (52 floats, indexing: [i/4][i%4])
            for (int i = 0; i < 13; i++) {
                int off = baseAddr + i * 16;
                for (int c = 0; c < 4; c++) {
                    int slot = i * 4 + c;
                    float gamut = 1.0f; // neutral default
                    if (slot < blocks.length) {
                        gamut = Options.getBlockGamutBoost(blocks[slot]) / 100.0f;
                    }
                    bb.putFloat(off + c * 4, gamut);
                }
            }
            baseAddr += 13 * 16; // 13 × vec4

            updateWorldUniform(addr);
        }
    }

    public static native void updateSkyUniform(long ptr);

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizontalColorR, float horizontalColorG, float horizontalColorB,
        float horizontalColorA, Vector3f sunDirection, Vector3f moonDirection,
        int skyType, boolean sunRisingOrSetting,
        boolean skyDark, boolean hasBlindnessOrDarkness, int submersionType, int moonPhase,
        float rainGradient, float thunderGradient, int sunTextureID, int moonTextureID,
        float sunSizeMultiplier, float moonSizeMultiplier,
        float sunIntensityMultiplier, float moonIntensityMultiplier,
        float waterTintR, float waterTintG, float waterTintB, float waterFogStrength,
        float rainBlendStrength, float skyBrightness,
        float cloudBaseHeight, float cloudThickness,
        float cloudDensityScale, float cloudAlbedoScale,
        int cloudTileTextureID, int cloudCenterX, int cloudCenterZ,
        float cloudPeriodX, float cloudPeriodZ, float cloudTicks,
        float cloudPuffiness, float cloudDetailScale, float cloudDetailStrength,
        float cloudAnisotropy,
        float cloudShadowStrength, float cloudAmbientStrength, float cloudSunOcclusionStrength,
        float cloudNoiseAffectsShadows) {
        try (MemoryStack stack = stackPush()) {
            int size = 304;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, 0, size);
            int baseAddr = 0;

            bb.putFloat(baseAddr, baseColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, baseColorB);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, skyType);
            baseAddr += Integer.BYTES;

            bb.putFloat(baseAddr, horizontalColorR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizontalColorG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizontalColorB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, horizontalColorA);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, sunDirection.x);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.y);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunDirection.z);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunRisingOrSetting ? 1 : 0);
            baseAddr += Integer.BYTES;

            bb.putFloat(baseAddr, moonDirection.x);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, moonDirection.y);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, moonDirection.z);
            baseAddr += Float.BYTES;
            baseAddr += Float.BYTES; // moonDirPad

            bb.putInt(baseAddr, skyDark ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, hasBlindnessOrDarkness ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, submersionType);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, moonPhase);
            baseAddr += Integer.BYTES; // moonPhase

            bb.putFloat(baseAddr, rainGradient);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 1.0f); // hdrRadianceScale
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, thunderGradient);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, Options.wetSurfaceStrengthPercent / 100.0f);
            baseAddr += Float.BYTES;

            // AtmosphereParams
            bb.putFloat(baseAddr, 6360000.0f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 6460000.0f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 8000.0f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 1200.0f);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, 5.802e-6f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 13.558e-6f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 33.100e-6f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 0.80f);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, 21.000e-6f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 21.000e-6f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 21.000e-6f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 0.02f);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, 100000.0f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 100000.0f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 100000.0f);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, sunTextureID);
            baseAddr += Integer.BYTES; // sunTextureID

            bb.putFloat(baseAddr, 0.05f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 0.06f);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 0.12f);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, moonTextureID);
            baseAddr += Integer.BYTES; // moonTextureID

            bb.putFloat(baseAddr, sunSizeMultiplier);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, moonSizeMultiplier);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, sunIntensityMultiplier);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, moonIntensityMultiplier);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, waterTintR);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, waterTintG);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, waterTintB);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, waterFogStrength);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, skyBrightness);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, rainBlendStrength);
            baseAddr += Float.BYTES;
            baseAddr += Float.BYTES * 2;

            // envCloud
            bb.putFloat(baseAddr, cloudBaseHeight);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudThickness);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudDensityScale);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudAlbedoScale);
            baseAddr += Float.BYTES;

            // cloudTile
            bb.putInt(baseAddr, cloudTileTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, cloudCenterX);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, cloudCenterZ);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, 0);
            baseAddr += Integer.BYTES;

            // cloudWrap
            bb.putFloat(baseAddr, cloudPeriodX);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudPeriodZ);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudTicks);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, 0.0f);
            baseAddr += Float.BYTES;

            // cloudShape
            bb.putFloat(baseAddr, cloudPuffiness);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudDetailScale);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudDetailStrength);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudAnisotropy);
            baseAddr += Float.BYTES;

            // cloudLighting
            bb.putFloat(baseAddr, cloudShadowStrength);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudAmbientStrength);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudSunOcclusionStrength);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, cloudNoiseAffectsShadows);
            baseAddr += Float.BYTES;

            updateSkyUniform(addr);
        }
    }

    public static native void updateMapping(long ptr);

    // TextureMapEntry: 5 ints + 4 reserved floats = 9 uint32s (36 bytes)
    // Sprite bounds fields (indices 5-8) unused — greedy mesher stores bounds per-vertex instead.
    private static final int TEX_ENTRY_INTS = 9;
    private static final int TEX_ENTRY_COUNT = 4096;
    private static final int TEX_PROP_HAS_HEIGHT_MAP = 1;

    public static void updateMapping() {
        // === Main TextureMapping SSBO (compact, 128KB) ===
        int texSize = TEX_ENTRY_COUNT * Integer.BYTES * TEX_ENTRY_INTS;
        ByteBuffer texBB = memAlloc(texSize);
        try {
            long texAddr = memAddress(texBB);
            memSet(texAddr, -1, texSize);
            IntBuffer texView = texBB.asIntBuffer();

            // Clear properties field to 0
            for (int i = 0; i < TEX_ENTRY_COUNT; i++) {
                texView.put(i * TEX_ENTRY_INTS + 3, 0);
            }

            for (int id = 0; id < TEX_ENTRY_COUNT; id++) {
                if (TextureTracker.GLID2SpecularGLID[id] != -1) {
                    texView.put(id * TEX_ENTRY_INTS, TextureTracker.GLID2SpecularGLID[id]);
                }
            }
            for (int id = 0; id < TEX_ENTRY_COUNT; id++) {
                if (TextureTracker.GLID2NormalGLID[id] != -1) {
                    texView.put(id * TEX_ENTRY_INTS + 1, TextureTracker.GLID2NormalGLID[id]);
                }
            }
            for (int id = 0; id < TEX_ENTRY_COUNT; id++) {
                if (TextureTracker.GLID2FlagGLID[id] != -1) {
                    texView.put(id * TEX_ENTRY_INTS + 2, TextureTracker.GLID2FlagGLID[id]);
                }
            }
            for (int albedoGLID : TextureTracker.hasHeightMap) {
                if (albedoGLID >= 0 && albedoGLID < TEX_ENTRY_COUNT) {
                    int off = albedoGLID * TEX_ENTRY_INTS + 3;
                    texView.put(off, texView.get(off) | TEX_PROP_HAS_HEIGHT_MAP);
                }
            }

            // Flush pending mask registrations — ensures texture data is uploaded before
            // the mask ID appears in the TextureMapping SSBO (prevents UNDEFINED-layout reads)
            updateMapping(texAddr);

            // V2 mirror — same byte layout, just routed through the bridge
            if (EngineBridge.isV2Active()) {
                EngineBridge.updateTextureMapping(texAddr, texSize);
            }
        } finally {
            memFree(texBB);
        }

    }

    public static native void updateLightMapUniform(long ptr);

    public static void updateLightMapUniform(float ambientLightFactor, float skyFactor,
        float blockFactor, boolean useBrightLightmap, Vector3f skyLightColor,
        float nightVisionFactor, float darknessScale, float darkenWorldFactor,
        float brightnessFactor, ClientWorld world, float tickDelta) {
        LightmapSample customLightmap = ResourcePackLightmapResolver.resolve(world, tickDelta,
            skyFactor, blockFactor, nightVisionFactor);
        try (MemoryStack stack = stackPush()) {
            int size = 576;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            int baseAddr = 0;

            bb.putFloat(baseAddr, ambientLightFactor);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, skyFactor);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, blockFactor);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, useBrightLightmap ? 1 : 0);
            baseAddr += Integer.BYTES;

            bb.putFloat(baseAddr, skyLightColor.x);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, skyLightColor.y);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, skyLightColor.z);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, nightVisionFactor);
            baseAddr += Float.BYTES;

            bb.putFloat(baseAddr, darknessScale);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, darkenWorldFactor);
            baseAddr += Float.BYTES;
            bb.putFloat(baseAddr, brightnessFactor);
            baseAddr += Float.BYTES;
            bb.putInt(baseAddr, customLightmap.enabled() ? 1 : 0);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, customLightmap.includesNightVision() ? 1 : 0);
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES * 3; // pad0..2; align vec4 arrays to 16 bytes

            writeLightmapArray(bb, baseAddr, customLightmap.skyRgb());
            baseAddr += 16 * Float.BYTES * 4;
            writeLightmapArray(bb, baseAddr, customLightmap.blockRgb());

            updateLightMapUniform(addr);
        }
    }

    private static void writeLightmapArray(ByteBuffer bb, int baseAddr, float[] rgb) {
        int offset = baseAddr;
        for (int level = 0; level < 16; level++) {
            int source = level * 3;
            bb.putFloat(offset, rgb[source]);
            offset += Float.BYTES;
            bb.putFloat(offset, rgb[source + 1]);
            offset += Float.BYTES;
            bb.putFloat(offset, rgb[source + 2]);
            offset += Float.BYTES;
            bb.putFloat(offset, 1.0f);
            offset += Float.BYTES;
        }
    }

    public record BufferInfo(ByteBuffer buf, long addr, int size) {

    }

    public static class VertexIndexBufferHandle {

        public int vertexId;
        public int indexId;

        public VertexIndexBufferHandle(int vertexId, int indexId) {
            this.vertexId = vertexId;
            this.indexId = indexId;
        }
    }
}
