package com.radiance.client.proxy.vulkan;

import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static com.radiance.client.constant.VulkanConstants.VkBufferUsageFlagBits.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memSet;

import com.mojang.blaze3d.systems.RenderSystem;
import com.radiance.client.constant.Constants;
import com.radiance.client.compat.FirstPersonCompat;
import com.radiance.client.option.Options;
import com.radiance.client.util.EmissiveBlock;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.SpectralColor;
import com.radiance.client.texture.TextureTracker;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Map;
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
        try (MemoryStack stack = stackPush()) {
            int size = 3920; // 560 base + 800 (vec4[50] emissionData) + 2560 (vec4[160] materialData)
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
            // When FirstPerson mod is active, tell shader camera is "third person"
            // so PLAYER_MASK is included in ray mask (player body visible)
            boolean hidePlayer = !camera.isThirdPerson() && !FirstPersonCompat.isActive();
            bb.putInt(baseAddr, hidePlayer ? 1 : 0);
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

            baseAddr += Float.BYTES; // rayBounces
            baseAddr += Float.BYTES; // pad

            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos
            baseAddr += Double.BYTES; // cameraPos

            bb.putInt(baseAddr, endSkyTextureID);
            baseAddr += Integer.BYTES;
            bb.putInt(baseAddr, endPortalTextureID);
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;
            baseAddr += Integer.BYTES;

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
                    // Flame colorant: compute spectral color for thermal blocks with wavelength > 0
                    if (blk.isThermal()) {
                        int wl = Options.getBlockWavelength(blk);
                        int pur = Options.getBlockPurity(blk);
                        if (wl > 0 && pur > 0) {
                            int tempC = Options.getBlockTemperature(blk);
                            float tempK = tempC + 273.15f;
                            float[] color = SpectralColor.computeFlameColor(tempK, wl, pur / 100.0f);
                            r = color[0]; g = color[1]; b = color[2];
                        }
                    }
                }
                int off = baseAddr + i * 16; // vec4 = 16 bytes
                bb.putFloat(off,      r);
                bb.putFloat(off + 4,  g);
                bb.putFloat(off + 8,  b);
                bb.putFloat(off + 12, mult);
            }
            baseAddr += 50 * 16; // 50 × vec4

            // Principled BSDF material data: 4 × vec4[40] = 160 vec4 per block
            // Pack 0 [idx+0]:   (f0.r, f0.g, f0.b, roughness)
            // Pack 1 [idx+40]:  (metallic, transmission, ior, subsurface)
            // Pack 2 [idx+80]:  (anisotropic, sheenWeight, sheenTint, coatWeight)
            // Pack 3 [idx+120]: (coatRoughness, 0, 0, 0)
            // When disabled, write zeros so shader dot() guard skips override
            boolean matEnabled = Options.materialOverridesEnabled;
            for (int i = 0; i < 40; i++) {
                int p0 = baseAddr + i * 16;            // Pack 0
                int p1 = baseAddr + (40 + i) * 16;     // Pack 1
                int p2 = baseAddr + (80 + i) * 16;     // Pack 2
                int p3 = baseAddr + (120 + i) * 16;    // Pack 3
                if (matEnabled && i < MaterialBlock.COUNT) {
                    // Pack 0: F0 RGB + roughness
                    bb.putFloat(p0,      Options.materialF0R[i] / 1000.0f);
                    bb.putFloat(p0 + 4,  Options.materialF0G[i] / 1000.0f);
                    bb.putFloat(p0 + 8,  Options.materialF0B[i] / 1000.0f);
                    bb.putFloat(p0 + 12, Options.materialRoughness[i] / 100.0f);
                    // Pack 1: metallic, transmission, IOR, subsurface
                    bb.putFloat(p1,      Options.materialMetallic[i] / 1000.0f);
                    bb.putFloat(p1 + 4,  Options.materialTransmission[i] / 1000.0f);
                    bb.putFloat(p1 + 8,  Options.materialIOR[i] / 1000.0f);
                    bb.putFloat(p1 + 12, Options.materialSubsurface[i] / 1000.0f);
                    // Pack 2: anisotropic, sheen weight, sheen tint, coat weight
                    bb.putFloat(p2,      Options.materialAnisotropic[i] / 1000.0f);
                    bb.putFloat(p2 + 4,  Options.materialSheenWeight[i] / 1000.0f);
                    bb.putFloat(p2 + 8,  Options.materialSheenTint[i] / 1000.0f);
                    bb.putFloat(p2 + 12, Options.materialCoatWeight[i] / 1000.0f);
                    // Pack 3: coat roughness + reserved
                    bb.putFloat(p3,      Options.materialCoatRoughness[i] / 100.0f);
                    bb.putFloat(p3 + 4,  0.0f);
                    bb.putFloat(p3 + 8,  0.0f);
                    bb.putFloat(p3 + 12, 0.0f);
                } else {
                    // Zero all 4 packs
                    for (int p : new int[]{p0, p1, p2, p3}) {
                        bb.putFloat(p, 0); bb.putFloat(p + 4, 0);
                        bb.putFloat(p + 8, 0); bb.putFloat(p + 12, 0);
                    }
                }
            }
            baseAddr += 160 * 16; // 160 × vec4

            updateWorldUniform(addr);
        }
    }

    public static native void updateSkyUniform(long ptr);

    public static void updateSkyUniform(float baseColorR, float baseColorG, float baseColorB,
        float horizontalColorR, float horizontalColorG, float horizontalColorB,
        float horizontalColorA, Vector3f sunDirection, Vector3f moonDirection,
        int skyType, boolean sunRisingOrSetting,
        boolean skyDark, boolean hasBlindnessOrDarkness, int submersionType, int moonPhase,
        float rainGradient, int sunTextureID, int moonTextureID,
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
            baseAddr += Float.BYTES;
            baseAddr += Float.BYTES;
            baseAddr += Float.BYTES; // padding

            // AtmosphereParams
            baseAddr += Float.BYTES * 4 * 3; // skip

            baseAddr += Float.BYTES * 3; // sunRadiance
            bb.putInt(baseAddr, sunTextureID);
            baseAddr += Integer.BYTES; // sunTextureID

            baseAddr += Float.BYTES * 3; // moonRadiance
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

    public static void updateMapping() {
        try (MemoryStack stack = stackPush()) {
            final int elementCount = 4096;
            int size = elementCount * Integer.BYTES * 3;
            ByteBuffer bb = stack.malloc(size);
            long addr = memAddress(bb);
            memSet(addr, -1, size);
            IntBuffer intView = bb.asIntBuffer();

            for (Map.Entry<Integer, Integer> specularEntry : TextureTracker.GLID2SpecularGLID.entrySet()) {
                int sourceID = specularEntry.getKey();
                int targetID = specularEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3, targetID);
                } else {
                    throw new RuntimeException(
                        "Specular mapping sourceID " + sourceID + " out of index [0, " + (
                            elementCount - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> normalEntry : TextureTracker.GLID2NormalGLID.entrySet()) {
                int sourceID = normalEntry.getKey();
                int targetID = normalEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 1, targetID);
                } else {
                    throw new RuntimeException(
                        "Normal mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            for (Map.Entry<Integer, Integer> flagEntry : TextureTracker.GLID2FlagGLID.entrySet()) {
                int sourceID = flagEntry.getKey();
                int targetID = flagEntry.getValue();
                if (sourceID >= 0 && sourceID < elementCount) {
                    intView.put(sourceID * 3 + 2, targetID);
                } else {
                    throw new RuntimeException(
                        "Flag mapping sourceID " + sourceID + " out of index [0, " + (elementCount
                            - 1) + "]");
                }
            }

            updateMapping(addr);
        }
    }

    public static native void updateLightMapUniform(long ptr);

    public static void updateLightMapUniform(float ambientLightFactor, float skyFactor,
        float blockFactor, boolean useBrightLightmap, Vector3f skyLightColor,
        float nightVisionFactor, float darknessScale, float darkenWorldFactor,
        float brightnessFactor) {
        try (MemoryStack stack = stackPush()) {
            int size = 48;
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
            baseAddr += Integer.BYTES; // pad0

            updateLightMapUniform(addr);
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
