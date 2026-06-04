package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_BLOCK_GEOMETRY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_EMISSIVE_BLOCK_TYPE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAGS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_FLUID_GEOMETRY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_COORD_SHIFT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_USE_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_PACKED;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_PACKED;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.nio.ByteOrder;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.MissingSprite;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class PBRVertexConsumer implements VertexConsumer {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final int PBR_GEOMETRY_FLAG_MASK =
        PBR_FLAG_BLOCK_GEOMETRY | PBR_FLAG_FLUID_GEOMETRY;

    private final BufferAllocator allocator;
    private final VertexFormat format;
    private final VertexFormat.DrawMode drawMode;

    private final int vertexSizeByte;
    private final int writableMask;
    private final int requiredMask;
    private final int[] offsetsByElementId;
    private long vertexPointer = -1L;
    private int vertexCount = 0;
    private int currentMask = 0;
    private boolean building = true;
    private int textureID;
    private float baseX = 0;
    private float baseY = 0;
    private float baseZ = 0;
    private float pendingEmission = 0.0f;
    private int pendingEmissiveBlockType = 255; // 255 = no type
    private int pendingTextureOverride = -1;
    private int pendingVertexFlags = 0;
    private boolean pendingSpriteUvRemap = false;
    private float pendingSpriteMinU = 0.0f;
    private float pendingSpriteMaxU = 1.0f;
    private float pendingSpriteMinV = 0.0f;
    private float pendingSpriteMaxV = 1.0f;

    public PBRVertexConsumer(BufferAllocator allocator, RenderLayer renderLayer) {
        this(allocator, VertexFormat.DrawMode.QUADS, PBRVertexFormats.PBR_TRIANGLE, renderLayer);
    }

    private PBRVertexConsumer(BufferAllocator allocator, VertexFormat.DrawMode drawMode,
        VertexFormat format, RenderLayer renderLayer) {
        this.allocator = allocator;
        this.drawMode = drawMode;
        this.format = format;

        this.vertexSizeByte = format.getVertexSizeByte();
        this.writableMask = format.getRequiredMask() & ~PBR_POS.getBit();
        this.requiredMask = 0;
        this.offsetsByElementId = format.getOffsetsByElementId();

        if (this.vertexSizeByte != 96) {
            throw new IllegalStateException(
                "PBR vertex stride must be 96, got " + this.vertexSizeByte);
        }
        if (!format.has(PBR_POS)) {
            throw new IllegalArgumentException("PBR format must contain POSITION element");
        }

        if (renderLayer instanceof RenderLayer.MultiPhase) {
            Identifier
                identifier =
                ((RenderLayer.MultiPhase) renderLayer).phases.texture.getId()
                    .orElse(MissingSprite.getMissingSpriteId());
            textureID =
                MinecraftClient.getInstance()
                    .getTextureManager()
                    .getTexture(identifier)
                    .getGlId();
        }
    }

    private static void putInt(long ptr, int v) {
        if (LITTLE_ENDIAN) {
            MemoryUtil.memPutInt(ptr, v);
        } else {
            MemoryUtil.memPutShort(ptr, (short) (v & 0xFFFF));
            MemoryUtil.memPutShort(ptr + 2L, (short) ((v >>> 16) & 0xFFFF));
        }
    }

    /** OR a flag bit into the packed flags field of the current vertex. */
    private void orFlag(int bit) {
        int off = offsetsByElementId[PBR_FLAGS.id()];
        if (off < 0) return;
        long p = vertexPointer + off;
        int cur = MemoryUtil.memGetInt(p);
        MemoryUtil.memPutInt(p, cur | bit);
    }

    public VertexFormat getFormat() {
        return this.format;
    }

    public int getVertexCount() {
        return this.vertexCount;
    }

    public void setBase(float x, float y, float z) {
        this.baseX = x;
        this.baseY = y;
        this.baseZ = z;
    }

    private void ensureBuilding() {
        if (!building) {
            throw new IllegalStateException("Not building!");
        }
    }

    @Nullable
    public BuiltBuffer endNullable() {
        ensureBuilding();
        endVertex();
        BuiltBuffer built = build();
        building = false;
        vertexPointer = -1L;
        return built;
    }

    public BuiltBuffer end() {
        BuiltBuffer built = endNullable();
        if (built == null) {
            throw new IllegalStateException("PBRBufferBuilder was empty");
        }
        return built;
    }

    @Nullable
    private BuiltBuffer build() {
        if (vertexCount == 0) {
            return null;
        }

        BufferAllocator.CloseableBuffer buf = allocator.getAllocated();
        if (buf == null) {
            return null;
        }

        int indexCount = drawMode.getIndexCount(vertexCount);
        VertexFormat.IndexType indexType = VertexFormat.IndexType.smallestFor(vertexCount);
        return new BuiltBuffer(buf,
            new BuiltBuffer.DrawParameters(format, vertexCount, indexCount, drawMode, indexType));
    }

    private long beginVertex() {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.allocate(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        initializeVertexState(ptr);

        return ptr;
    }

    private long beginVertex(int glintTextureID) {
        ensureBuilding();
        endVertex();

        vertexCount++;
        long ptr = allocator.allocate(vertexSizeByte);
        vertexPointer = ptr;
        MemoryUtil.memSet(ptr, 0, vertexSizeByte);

        initializeVertexState(ptr);

        if (glintTextureID != 0) {
            int off = this.offsetsByElementId[PBR_GLINT_TEXTURE.id()];
            if (off >= 0) {
                putInt(ptr + off, glintTextureID);
            }
        }

        return ptr;
    }

    private void initializeVertexState(long ptr) {
        int activeTextureID = this.pendingTextureOverride >= 0
            ? this.pendingTextureOverride
            : this.textureID;
        if (activeTextureID != 0) {
            int off = this.offsetsByElementId[PBR_TEXTURE_ID.id()];
            if (off >= 0) {
                putInt(ptr + off, activeTextureID);
            }
        }

        if (this.pendingVertexFlags != 0) {
            int off = this.offsetsByElementId[PBR_FLAGS.id()];
            if (off >= 0) {
                putInt(ptr + off, this.pendingVertexFlags);
            }
        }

        int offBase = this.offsetsByElementId[PBR_POST_BASE.id()];
        if (offBase >= 0) {
            MemoryUtil.memPutFloat(ptr + offBase, baseX);
            MemoryUtil.memPutFloat(ptr + offBase + 4L, baseY);
            MemoryUtil.memPutFloat(ptr + offBase + 8L, baseZ);
        }
    }

    long beginElement(VertexFormatElement element) {
        int mask = currentMask;
        int bit = element.getBit();
        if ((mask & bit) == 0) {
            return -1L;
        }

        currentMask = mask & ~bit;

        long base = vertexPointer;
        if (base == -1L) {
            throw new IllegalStateException("Not currently building vertex");
        }

        int id = element.id();
        int off = offsetsByElementId[id];
        if (off < 0) {
            throw new IllegalStateException(
                "Element present in mask but not in format: " + element);
        }
        return base + off;
    }

    private void endVertex() {
        if (vertexCount == 0) {
            return;
        }

        int missing = currentMask & requiredMask;
        if (missing != 0) {
            String
                s =
                VertexFormatElement.streamFromMask(currentMask)
                    .map(format::getName)
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Missing elements in vertex: " + s);
        }
    }

    @Override
    public VertexConsumer vertex(float x, float y, float z) {
        long base = beginVertex();
        currentMask = writableMask;

        int posOff = offsetsByElementId[PBR_POS.id()];
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        if (pendingEmission != 0.0f) {
            albedoEmission(pendingEmission);
        }
        if (pendingEmissiveBlockType != 255) {
            emissiveBlockType(pendingEmissiveBlockType & 0xFF);
        }

        return this;
    }

    public VertexConsumer vertex(float x, float y, float z, int glintTextureID) {
        long base = beginVertex(glintTextureID);
        currentMask = writableMask;

        int posOff = offsetsByElementId[PBR_POS.id()];
        long p = base + posOff;

        if (Float.isNaN(x) || Float.isNaN(y) || Float.isNaN(z)) {
            MemoryUtil.memPutFloat(p, 0);
            MemoryUtil.memPutFloat(p + 4L, 0);
            MemoryUtil.memPutFloat(p + 8L, 0);
        } else {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }

        if (pendingEmission != 0.0f) {
            albedoEmission(pendingEmission);
        }
        if (pendingEmissiveBlockType != 255) {
            emissiveBlockType(pendingEmissiveBlockType & 0xFF);
        }

        return this;
    }

    @Override
    public VertexConsumer color(int red, int green, int blue, int alpha) {
        orFlag(PBR_FLAG_USE_COLOR_LAYER);

        long p = beginElement(PBR_COLOR_LAYER);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, red / 255.0f);
            MemoryUtil.memPutFloat(p + 4L, green / 255.0f);
            MemoryUtil.memPutFloat(p + 8L, blue / 255.0f);
            MemoryUtil.memPutFloat(p + 12L, alpha / 255.0f);
        }
        return this;
    }

    @Override
    public VertexConsumer texture(float u, float v) {
        orFlag(PBR_FLAG_USE_TEXTURE);

        long p = beginElement(PBR_TEXTURE_UV);
        if (p != -1L) {
            if (pendingSpriteUvRemap) {
                u = localSpriteUvForTest(u, pendingSpriteMinU, pendingSpriteMaxU);
                v = localSpriteUvForTest(v, pendingSpriteMinV, pendingSpriteMaxV);
            }
            MemoryUtil.memPutFloat(p, u);
            MemoryUtil.memPutFloat(p + 4L, v);
        }
        return this;
    }

    @Override
    public VertexConsumer overlay(int u, int v) {
        orFlag(PBR_FLAG_USE_OVERLAY);

        long p = beginElement(PBR_OVERLAY_PACKED);
        if (p != -1L) {
            putInt(p, (u & 0xFFFF) | ((v & 0xFFFF) << 16));
        }
        return this;
    }

    @Override
    public VertexConsumer light(int u, int v) {
        orFlag(PBR_FLAG_USE_LIGHT);

        long p = beginElement(PBR_LIGHT_PACKED);
        if (p != -1L) {
            putInt(p, (u & 0xFFFF) | ((v & 0xFFFF) << 16));
        }
        return this;
    }

    @Override
    public VertexConsumer normal(float x, float y, float z) {
        orFlag(PBR_FLAG_USE_NORM);

        long p = beginElement(PBR_NORM);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, x);
            MemoryUtil.memPutFloat(p + 4L, y);
            MemoryUtil.memPutFloat(p + 8L, z);
        }
        return this;
    }

    public VertexConsumer albedoEmission(float emission) {
        long p = beginElement(PBR_ALBEDO_EMISSION);
        if (p != -1L) {
            MemoryUtil.memPutFloat(p, emission);
        }
        return this;
    }

    public void setPendingEmission(float emission) {
        this.pendingEmission = Math.max(0.0f, emission);
    }

    public void setPendingEmissiveBlockType(int ordinal) {
        this.pendingEmissiveBlockType = ordinal;
    }

    public VertexConsumer emissiveBlockType(int type) {
        long p = beginElement(PBR_EMISSIVE_BLOCK_TYPE);
        if (p != -1L) {
            MemoryUtil.memPutInt(p, type);
        }
        return this;
    }

    public int getTextureID() {
        return this.textureID;
    }

    public void setPendingTextureSprite(@Nullable Sprite sprite, int geometryFlags) {
        Identifier id = sprite == null || sprite.getContents() == null
            ? null
            : sprite.getContents().getId();
        int spriteId = id == null ? -1 : TextureArrayBridge.resolveSpriteId(id.toString());
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        if (spriteId >= 0 && sprite != null) {
            this.pendingTextureOverride = spriteId;
            this.pendingVertexFlags |= geometryFlags & PBR_GEOMETRY_FLAG_MASK;
            this.pendingSpriteUvRemap = true;
            this.pendingSpriteMinU = sprite.getMinU();
            this.pendingSpriteMaxU = sprite.getMaxU();
            this.pendingSpriteMinV = sprite.getMinV();
            this.pendingSpriteMaxV = sprite.getMaxV();
        } else {
            this.pendingTextureOverride = -1;
            this.pendingSpriteUvRemap = false;
        }
    }

    public void setPendingTextureSpriteId(int spriteId, int geometryFlags) {
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        if (spriteId >= 0) {
            this.pendingTextureOverride = spriteId;
            this.pendingVertexFlags |= geometryFlags & PBR_GEOMETRY_FLAG_MASK;
            this.pendingSpriteUvRemap = false;
        } else {
            this.pendingTextureOverride = -1;
            this.pendingSpriteUvRemap = false;
        }
    }

    public void clearPendingTextureSprite() {
        this.pendingTextureOverride = -1;
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        this.pendingSpriteUvRemap = false;
    }

    public static float localSpriteUvForTest(float atlasUv, float min, float max) {
        if (!Float.isFinite(atlasUv) || !Float.isFinite(min) || !Float.isFinite(max)) {
            return atlasUv;
        }
        float span = max - min;
        if (!Float.isFinite(span) || Math.abs(span) < 1.0e-8f) {
            return atlasUv;
        }
        float local = (atlasUv - min) / span;
        if (local < 0.0f) return 0.0f;
        if (local > 1.0f) return 1.0f;
        return local;
    }

    @Override
    public void quad(MatrixStack.Entry matrixEntry,
        BakedQuad quad,
        float[] brightnesses,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lights,
        int overlay,
        boolean useQuadColorData) {
        int previousTextureOverride = this.pendingTextureOverride;
        int previousVertexFlags = this.pendingVertexFlags;
        boolean previousSpriteUvRemap = this.pendingSpriteUvRemap;
        float previousSpriteMinU = this.pendingSpriteMinU;
        float previousSpriteMaxU = this.pendingSpriteMaxU;
        float previousSpriteMinV = this.pendingSpriteMinV;
        float previousSpriteMaxV = this.pendingSpriteMaxV;
        try {
            setPendingTextureSprite(quad.getSprite(), PBR_FLAG_BLOCK_GEOMETRY);
            VertexConsumer.super.quad(matrixEntry,
                quad,
                brightnesses,
                red,
                green,
                blue,
                alpha,
                lights,
                overlay,
                useQuadColorData);
        } finally {
            this.pendingTextureOverride = previousTextureOverride;
            this.pendingVertexFlags = previousVertexFlags;
            this.pendingSpriteUvRemap = previousSpriteUvRemap;
            this.pendingSpriteMinU = previousSpriteMinU;
            this.pendingSpriteMaxU = previousSpriteMaxU;
            this.pendingSpriteMinV = previousSpriteMinV;
            this.pendingSpriteMaxV = previousSpriteMaxV;
        }
    }

    public static class GLint implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private int glintTextureID;

        public GLint(PBRVertexConsumer delegate, RenderLayer glintRenderLayer) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderLayer.MultiPhase) {
                Identifier
                    identifier =
                    ((RenderLayer.MultiPhase) glintRenderLayer).phases.texture.getId()
                        .orElse(MissingSprite.getMissingSpriteId());
                glintTextureID =
                    MinecraftClient.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getGlId();
            }
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            delegate.orFlag(PBR_FLAG_USE_GLINT);

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, u);
                MemoryUtil.memPutFloat(p + 4L, v);
            }
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }
    }

    public static class GLintOverlay implements VertexConsumer {

        private final PBRVertexConsumer delegate;
        private final Matrix4f inverseTextureMatrix;
        private final Matrix3f inverseNormalMatrix;
        private final float textureScale;
        private final Vector3f normal = new Vector3f();
        private final Vector3f pos = new Vector3f();
        private int glintTextureID;
        private float x;
        private float y;
        private float z;

        public GLintOverlay(PBRVertexConsumer delegate, RenderLayer glintRenderLayer,
            MatrixStack.Entry matrix, float textureScale) {
            this.delegate = delegate;
            if (glintRenderLayer instanceof RenderLayer.MultiPhase) {
                Identifier
                    identifier =
                    ((RenderLayer.MultiPhase) glintRenderLayer).phases.texture.getId()
                        .orElse(MissingSprite.getMissingSpriteId());
                glintTextureID =
                    MinecraftClient.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getGlId();
            }

            this.inverseTextureMatrix = new Matrix4f(matrix.getPositionMatrix()).invert();
            this.inverseNormalMatrix = new Matrix3f(matrix.getNormalMatrix()).invert();
            this.textureScale = textureScale;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.vertex(x, y, z, this.glintTextureID);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            delegate.overlay(u, v);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            delegate.light(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            Vector3f vector3f = this.inverseNormalMatrix.transform(x, y, z, this.pos);
            Direction direction = Direction.getFacing(vector3f.x(), vector3f.y(), vector3f.z());
            Vector3f vector3f2 = this.inverseTextureMatrix.transformPosition(this.x, this.y, this.z,
                this.normal);
            vector3f2.rotateY((float) Math.PI);
            vector3f2.rotateX((float) (-Math.PI / 2));
            vector3f2.rotate(direction.getRotationQuaternion());

            delegate.orFlag(PBR_FLAG_USE_GLINT);

            long p = delegate.beginElement(PBR_GLINT_UV);
            if (p != -1L) {
                MemoryUtil.memPutFloat(p, -vector3f2.x() * this.textureScale);
                MemoryUtil.memPutFloat(p + 4L, -vector3f2.y() * this.textureScale);
            }
            return this;
        }
    }
}
