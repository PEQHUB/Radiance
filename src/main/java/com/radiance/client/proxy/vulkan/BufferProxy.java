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

    // Material UBO dirty-tracking cache: direct ByteBuffer for zero-copy memCopy
    private static final int MATERIAL_UBO_SIZE = Options.MAX_MATERIALS * 7 * 16; // 17920 bytes
    private static final ByteBuffer materialUBOCacheBuf = ByteBuffer.allocateDirect(MATERIAL_UBO_SIZE);
    private static final long materialUBOCacheAddr = memAddress(materialUBOCacheBuf);
    private static boolean materialCacheValid = false;

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
            int size = 560 + 50 * 16 + 13 * 16 + Options.MAX_MATERIALS * 7 * 16; // base + emissionData[50] + emissiveGamut[13] + materialData[MAX*7]
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
            // In offline freecam: show player if freecamShowPlayer is on, else hide
            boolean hidePlayer;
            if (Options.offlineState != 0 && Options.freecamEnabled) {
                hidePlayer = !Options.freecamShowPlayer;
            } else {
                hidePlayer = !camera.isThirdPerson() && !FirstPersonCompat.isActive();
            }
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

            // Principled BSDF material data: 6 × vec4[MAX_MATERIALS] per block
            // Pack 0 [idx+0]:     (f0.r, f0.g, f0.b, roughness)
            // Pack 1 [idx+N]:     (metallic, transmission, ior, subsurface)
            // Pack 2 [idx+2*N]:   (anisotropic, sheenWeight, sheenTint, coatWeight)
            // Pack 3 [idx+3*N]:   (coatRoughness, noiseScale, noiseStrength, noisePacked)
            // Pack 4 [idx+4*N]:   (channelR, channelG, channelB, textureBlend)
            // Pack 5 [idx+5*N]:   (gamutBoost, pomDepth, reserved, reserved)
            // When disabled, write MaterialBlock defaults so physics properties are preserved
            final int N = Options.MAX_MATERIALS;
            final int materialDataOffset = baseAddr;
            final long materialDstAddr = addr + materialDataOffset;

            if (false && !Options.materialDirty && materialCacheValid) { // DIAGNOSTIC: cache disabled
                // Material state unchanged — bulk copy from cache, skip expensive packing
                memCopy(materialUBOCacheAddr, materialDstAddr, MATERIAL_UBO_SIZE);
            } else {
                // Material state changed — run full packing loop
                boolean matEnabled = Options.materialOverridesEnabled;
                for (int i = 0; i < N; i++) {
                    int p0 = baseAddr + i * 16;            // Pack 0
                    int p1 = baseAddr + (N + i) * 16;      // Pack 1
                    int p2 = baseAddr + (2 * N + i) * 16;  // Pack 2
                    int p3 = baseAddr + (3 * N + i) * 16;  // Pack 3
                    int p4 = baseAddr + (4 * N + i) * 16;  // Pack 4
                    int p5 = baseAddr + (5 * N + i) * 16;  // Pack 5
                    int p6 = baseAddr + (6 * N + i) * 16;  // Pack 6
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
                        // Pack 3: coat roughness + noise parameters
                        bb.putFloat(p3,      Options.materialCoatRoughness[i] / 100.0f);
                        bb.putFloat(p3 + 4,  Options.materialNoiseScale[i] / 10.0f);       // 0.1-100.0
                        bb.putFloat(p3 + 8,  Options.materialNoiseStrength[i] / 1000.0f);  // 0.0-1.0
                        // Pack octaves(0-3) | type(4-8) | seed(9-17) | target(20-23) — fits in 24 bits (float-safe)
                        int noisePacked = Options.materialNoiseOctaves[i]
                                        | (Options.materialNoiseType[i] << 4)
                                        | (Options.materialNoiseSeed[i] << 9)
                                        | (Options.materialNoiseTarget[i] << 20);
                        bb.putFloat(p3 + 12, (float) noisePacked);
                        // Pack 4: texture roughness channel routing
                        bb.putFloat(p4,      Options.materialChannelR[i] / 1000.0f);
                        bb.putFloat(p4 + 4,  Options.materialChannelG[i] / 1000.0f);
                        bb.putFloat(p4 + 8,  Options.materialChannelB[i] / 1000.0f);
                        bb.putFloat(p4 + 12, Options.materialTextureBlend[i] / 100.0f);
                        // Pack 5: gamutBoost, noiseMaskThreshold, noiseMaskPacked, normalStrength
                        bb.putFloat(p5,      Options.materialGamutBoost[i] / 100.0f);
                        bb.putFloat(p5 + 4,  Options.materialNoiseMaskThreshold[i] / 1000.0f);
                        // maskMode(0-2) | invert(3) | wrapMode(4-6) packed as small float
                        int maskPacked = Options.materialNoiseMaskMode[i]
                                       | ((Options.materialNoiseMaskInvert[i] ? 1 : 0) << 3)
                                       | (Options.materialNoiseWrap[i] << 4);
                        bb.putFloat(p5 + 8,  (float) maskPacked);
                        bb.putFloat(p5 + 12, Options.materialNormalStrength[i] / 100.0f);
                        // Pack 6: rotation, aspect, lacunarity, contrast
                        bb.putFloat(p6,      (float) Math.toRadians(Options.materialNoiseRotation[i] / 10.0));
                        bb.putFloat(p6 + 4,  Options.materialNoiseAspect[i] / 100.0f);
                        bb.putFloat(p6 + 8,  Options.materialNoiseLacunarity[i] / 10.0f);
                        bb.putFloat(p6 + 12, Options.materialNoiseContrast[i] / 100.0f);
                    } else if (i < MaterialBlock.COUNT) {
                        // Overrides disabled: write physics-accurate defaults so water/ice/metals
                        // retain essential properties (IOR, transmission, metallic, F0).
                        MaterialBlock mb = MaterialBlock.values()[i];
                        // Pack 0: default F0 + default roughness
                        bb.putFloat(p0,      mb.getDefaultF0R() / 1000.0f);
                        bb.putFloat(p0 + 4,  mb.getDefaultF0G() / 1000.0f);
                        bb.putFloat(p0 + 8,  mb.getDefaultF0B() / 1000.0f);
                        bb.putFloat(p0 + 12, mb.getDefaultRoughness() / 100.0f);
                        // Pack 1: metallic, transmission, IOR, subsurface
                        bb.putFloat(p1,      mb.getDefaultMetallic() / 1000.0f);
                        bb.putFloat(p1 + 4,  mb.getDefaultTransmission() / 1000.0f);
                        bb.putFloat(p1 + 8,  mb.getDefaultIOR() / 1000.0f);
                        bb.putFloat(p1 + 12, mb.getDefaultSubsurface() / 1000.0f);
                        // Pack 2-4: zeros (no cosmetic overrides)
                        for (int p : new int[]{p2, p3, p4}) {
                            bb.putFloat(p, 0); bb.putFloat(p + 4, 0);
                            bb.putFloat(p + 8, 0); bb.putFloat(p + 12, 0);
                        }
                        // Pack 5: neutral gamut (1.0) and POM depth (1.0)
                        bb.putFloat(p5, 1.0f); bb.putFloat(p5 + 4, 1.0f);
                        bb.putFloat(p5 + 8, 0); bb.putFloat(p5 + 12, 0);
                        // Pack 6: defaults (rotation=0, aspect=1, lacunarity=2, contrast=1)
                        bb.putFloat(p6, 0); bb.putFloat(p6 + 4, 1.0f);
                        bb.putFloat(p6 + 8, 2.0f); bb.putFloat(p6 + 12, 1.0f);
                    } else {
                        // Unused slot beyond MaterialBlock.COUNT: zeros + transmission sentinel
                        for (int p : new int[]{p0, p2, p3, p4}) {
                            bb.putFloat(p, 0); bb.putFloat(p + 4, 0);
                            bb.putFloat(p + 8, 0); bb.putFloat(p + 12, 0);
                        }
                        // Pack 1: metallic=0, transmission=-1 (auto-detect from LabPBR), ior=0, subsurface=0
                        bb.putFloat(p1, 0); bb.putFloat(p1 + 4, -1.0f);
                        bb.putFloat(p1 + 8, 0); bb.putFloat(p1 + 12, 0);
                        bb.putFloat(p5, 1.0f); bb.putFloat(p5 + 4, 1.0f);
                        bb.putFloat(p5 + 8, 0); bb.putFloat(p5 + 12, 0);
                        bb.putFloat(p6, 0); bb.putFloat(p6 + 4, 1.0f);
                        bb.putFloat(p6 + 8, 2.0f); bb.putFloat(p6 + 12, 1.0f);
                    }
                }
                // Cache the packed bytes for future frames
                memCopy(materialDstAddr, materialUBOCacheAddr, MATERIAL_UBO_SIZE);
                materialCacheValid = true;
                Options.materialDirty = false;
            }
            baseAddr += N * 7 * 16; // MAX_MATERIALS × 7 vec4

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
    public static native void updateBlenderPBRMapping(long ptr);

    // TextureMapEntry: 4 ints (16 bytes) — hot path, read by every ray hit
    // BlenderPBREntry: 8 ints (32 bytes) — cold path, only read when TEX_PROP_DIRECT_PBR set
    private static final int TEX_ENTRY_INTS = 4;
    private static final int BP_ENTRY_INTS = 8;
    private static final int TEX_ENTRY_COUNT = 4096;
    private static final int TEX_PROP_HAS_HEIGHT_MAP = 1;
    private static final int TEX_PROP_DIRECT_PBR = 4;

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

            for (Map.Entry<Integer, Integer> e : TextureTracker.GLID2SpecularGLID.entrySet()) {
                int id = e.getKey();
                if (id >= 0 && id < TEX_ENTRY_COUNT) {
                    texView.put(id * TEX_ENTRY_INTS, e.getValue());
                }
            }
            for (Map.Entry<Integer, Integer> e : TextureTracker.GLID2NormalGLID.entrySet()) {
                int id = e.getKey();
                if (id >= 0 && id < TEX_ENTRY_COUNT) {
                    texView.put(id * TEX_ENTRY_INTS + 1, e.getValue());
                }
            }
            for (Map.Entry<Integer, Integer> e : TextureTracker.GLID2FlagGLID.entrySet()) {
                int id = e.getKey();
                if (id >= 0 && id < TEX_ENTRY_COUNT) {
                    texView.put(id * TEX_ENTRY_INTS + 2, e.getValue());
                }
            }
            for (int albedoGLID : TextureTracker.hasHeightMap) {
                if (albedoGLID >= 0 && albedoGLID < TEX_ENTRY_COUNT) {
                    int off = albedoGLID * TEX_ENTRY_INTS + 3;
                    texView.put(off, texView.get(off) | TEX_PROP_HAS_HEIGHT_MAP);
                }
            }

            // Set TEX_PROP_DIRECT_PBR + height flag from Blender PBR entries
            for (Map.Entry<Integer, Map<TextureTracker.BlenderChannel, Integer>> entry :
                    TextureTracker.blenderPBRTextures.entrySet()) {
                int id = entry.getKey();
                if (id < 0 || id >= TEX_ENTRY_COUNT) continue;
                int off = id * TEX_ENTRY_INTS + 3;
                texView.put(off, texView.get(off) | TEX_PROP_DIRECT_PBR);
                if (entry.getValue().containsKey(TextureTracker.BlenderChannel.HEIGHT)) {
                    texView.put(off, texView.get(off) | TEX_PROP_HAS_HEIGHT_MAP);
                }
            }

            updateMapping(texAddr);
        } finally {
            memFree(texBB);
        }

        // === Blender PBR SSBO (cold path, 256KB) — only uploaded if any Blender textures exist ===
        if (!TextureTracker.blenderPBRTextures.isEmpty()) {
            int bpSize = TEX_ENTRY_COUNT * Integer.BYTES * BP_ENTRY_INTS;
            ByteBuffer bpBB = memAlloc(bpSize);
            try {
                long bpAddr = memAddress(bpBB);
                memSet(bpAddr, -1, bpSize); // all -1 = no texture
                IntBuffer bpView = bpBB.asIntBuffer();

                // Clear _reserved field to 0
                for (int i = 0; i < TEX_ENTRY_COUNT; i++) {
                    bpView.put(i * BP_ENTRY_INTS + 7, 0);
                }

                for (Map.Entry<Integer, Map<TextureTracker.BlenderChannel, Integer>> entry :
                        TextureTracker.blenderPBRTextures.entrySet()) {
                    int id = entry.getKey();
                    if (id < 0 || id >= TEX_ENTRY_COUNT) continue;
                    int base = id * BP_ENTRY_INTS;
                    for (Map.Entry<TextureTracker.BlenderChannel, Integer> ch : entry.getValue().entrySet()) {
                        // BlenderChannel ssboOffset was for the old combined struct (4-11)
                        // Remap to BlenderPBREntry offsets (0-7)
                        int bpOffset = ch.getKey().ssboOffset - 4;
                        bpView.put(base + bpOffset, ch.getValue());
                    }
                }

                updateBlenderPBRMapping(bpAddr);
            } finally {
                memFree(bpBB);
            }
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
