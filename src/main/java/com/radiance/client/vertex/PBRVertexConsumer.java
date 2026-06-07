package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALPHA_MODE_CUTOUT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALPHA_MODE_OPAQUE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALPHA_MODE_TRANSPARENT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_BLOCK_GEOMETRY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_EMISSIVE_BLOCK_TYPE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAGS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_ALPHA_MODE_MASK;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_ALPHA_MODE_SHIFT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_FLUID_GEOMETRY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_OVERLAY_ALPHA_MASK;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_WATER_GEOMETRY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_COORD_SHIFT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_TEXT_MODE_MASK;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAG_TEXT_MODE_SHIFT;
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
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_PACKED_EMISSIVE_TYPE_NONE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_PACKED_SHADER_BLOCK_ID_MASK;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_BACKGROUND;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_BACKGROUND_SEE_THROUGH;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_INTENSITY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_INTENSITY_POLYGON_OFFSET;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_INTENSITY_SEE_THROUGH;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_RGBA;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_RGBA_POLYGON_OFFSET;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXT_MODE_RGBA_SEE_THROUGH;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.packShaderBlockId;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.compat.ResourcePackBlockLayerResolver;
import com.radiance.client.texture.compat.ResourcePackColorPropertiesResolver;
import com.radiance.client.texture.compat.ResourcePackEmissiveTextureResolver;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver;
import com.radiance.client.texture.compat.ResourcePackNaturalTextureResolver.NaturalTransform;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.BlockOverlaySprite;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.CompactCtmQuadrants;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.RepeatTextureBasis;
import com.radiance.client.texture.compat.ResourcePackTextureVariantResolver.ResolvedBlockSprite;
import java.nio.ByteOrder;
import java.util.stream.Collectors;
import net.minecraft.block.BlockState;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockRenderView;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryUtil;

public class PBRVertexConsumer implements VertexConsumer {

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    private static final Identifier GRASS_BLOCK_SIDE_OVERLAY =
        Identifier.ofVanilla("block/grass_block_side_overlay");
    private static final int PBR_GEOMETRY_FLAG_MASK =
        PBR_FLAG_BLOCK_GEOMETRY | PBR_FLAG_FLUID_GEOMETRY | PBR_FLAG_WATER_GEOMETRY;
    private static final float EMISSIVE_OVERLAY_REFERENCE_NITS = 200.0f;

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
    private int pendingEmissiveBlockType = PBR_PACKED_EMISSIVE_TYPE_NONE;
    private int pendingTextureOverride = -1;
    private int pendingVertexFlags = 0;
    private boolean pendingSpriteUvRemap = false;
    private float pendingSpriteMinU = 0.0f;
    private float pendingSpriteMaxU = 1.0f;
    private float pendingSpriteMinV = 0.0f;
    private float pendingSpriteMaxV = 1.0f;
    private NaturalTransform pendingNaturalUvTransform = NaturalTransform.identity();
    @Nullable
    private RepeatTextureBasis pendingRepeatTextureBasis = null;
    private int blockGeometryContextDepth = 0;
    @Nullable
    private BlockRenderView pendingBlockWorld = null;
    @Nullable
    private BlockState pendingBlockState = null;
    @Nullable
    private BlockPos pendingBlockPos = null;

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
            RenderLayer.MultiPhase multiPhase = (RenderLayer.MultiPhase) renderLayer;
            this.pendingVertexFlags =
                packAlphaMode(alphaModeFor(multiPhase)) | packTextMode(textModeFor(multiPhase));
            Identifier
                identifier =
                multiPhase.phases.texture.getId()
                    .orElse(MissingSprite.getMissingSpriteId());
            textureID =
                TextureArrayBridge.resolveRenderableTextureGlId(identifier,
                    MinecraftClient.getInstance()
                        .getTextureManager()
                        .getTexture(identifier)
                        .getGlId());
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

    public void pushBlockGeometryContext() {
        this.blockGeometryContextDepth++;
    }

    public void popBlockGeometryContext() {
        if (this.blockGeometryContextDepth > 0) {
            this.blockGeometryContextDepth--;
        }
    }

    public void setPendingBlockContext(@Nullable BlockRenderView world,
        @Nullable BlockState state,
        @Nullable BlockPos pos) {
        this.pendingBlockWorld = world;
        this.pendingBlockState = state;
        this.pendingBlockPos = pos == null ? null : pos.toImmutable();
    }

    public void clearPendingBlockContext() {
        this.pendingBlockWorld = null;
        this.pendingBlockState = null;
        this.pendingBlockPos = null;
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

    private static int packAlphaMode(int alphaMode) {
        return (alphaMode << PBR_FLAG_ALPHA_MODE_SHIFT) & PBR_FLAG_ALPHA_MODE_MASK;
    }

    private static int packTextMode(int textMode) {
        return (textMode << PBR_FLAG_TEXT_MODE_SHIFT) & PBR_FLAG_TEXT_MODE_MASK;
    }

    private static int alphaModeFor(RenderLayer.MultiPhase renderLayer) {
        if (renderLayer.name.contains("solid")) {
            return PBR_ALPHA_MODE_OPAQUE;
        }
        return renderLayer.isTranslucent() ? PBR_ALPHA_MODE_TRANSPARENT : PBR_ALPHA_MODE_CUTOUT;
    }

    private static int textModeFor(RenderLayer.MultiPhase renderLayer) {
        String name = renderLayer.name;
        if (name.equals("text_background")) {
            return PBR_TEXT_MODE_BACKGROUND;
        }
        if (name.equals("text_intensity")) {
            return PBR_TEXT_MODE_INTENSITY;
        }
        if (name.equals("text")) {
            return PBR_TEXT_MODE_RGBA;
        }
        if (name.equals("text_background_see_through")) {
            return PBR_TEXT_MODE_BACKGROUND_SEE_THROUGH;
        }
        if (name.equals("text_intensity_see_through")) {
            return PBR_TEXT_MODE_INTENSITY_SEE_THROUGH;
        }
        if (name.equals("text_see_through")) {
            return PBR_TEXT_MODE_RGBA_SEE_THROUGH;
        }
        if (name.equals("text_intensity_polygon_offset")) {
            return PBR_TEXT_MODE_INTENSITY_POLYGON_OFFSET;
        }
        if (name.equals("text_polygon_offset")) {
            return PBR_TEXT_MODE_RGBA_POLYGON_OFFSET;
        }
        return 0;
    }

    private int pendingPackedBlockType() {
        int packed = this.pendingEmissiveBlockType;
        if (this.blockGeometryContextDepth > 0) {
            int shaderBlockId = ResourcePackBlockLayerResolver.resolveShaderBlockId(this.pendingBlockState);
            int packedShaderBlockId = packShaderBlockId(shaderBlockId);
            if (packedShaderBlockId != 0) {
                packed = (packed & ~PBR_PACKED_SHADER_BLOCK_ID_MASK) | packedShaderBlockId;
            }
        }
        return packed;
    }

    private void writePendingPackedBlockType() {
        emissiveBlockType(pendingPackedBlockType());
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
        writePendingPackedBlockType();

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
        writePendingPackedBlockType();

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
            if (!pendingNaturalUvTransform.isIdentity()) {
                float oldU = u;
                float oldV = v;
                u = pendingNaturalUvTransform.transformU(oldU, oldV);
                v = pendingNaturalUvTransform.transformV(oldU, oldV);
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

    public void setPendingOverlayAlphaMask(boolean enabled) {
        if (enabled) {
            this.pendingVertexFlags |= PBR_FLAG_OVERLAY_ALPHA_MASK;
        } else {
            this.pendingVertexFlags &= ~PBR_FLAG_OVERLAY_ALPHA_MASK;
        }
    }

    private void setPendingAlphaMode(int alphaMode) {
        if (alphaMode < PBR_ALPHA_MODE_OPAQUE || alphaMode > PBR_ALPHA_MODE_TRANSPARENT) {
            return;
        }
        this.pendingVertexFlags =
            (this.pendingVertexFlags & ~PBR_FLAG_ALPHA_MODE_MASK) | packAlphaMode(alphaMode);
    }

    private void applyRegisteredEmissiveOverlayMask(int spriteId, boolean preserveExistingMask) {
        boolean enabled = preserveExistingMask && (this.pendingVertexFlags & PBR_FLAG_OVERLAY_ALPHA_MASK) != 0;
        Identifier sprite = TextureArrayBridge.spriteIdentifier(spriteId);
        if (ResourcePackEmissiveTextureResolver.usesShaderOverlayForBaseSprite(sprite)) {
            enabled = true;
        }
        setPendingOverlayAlphaMask(enabled);
    }

    private int geometryEmissiveOverlaySpriteId(int baseSpriteId) {
        Identifier baseSprite = TextureArrayBridge.spriteIdentifier(baseSpriteId);
        if (!ResourcePackEmissiveTextureResolver.requiresGeometryOverlayForBaseSprite(baseSprite)) {
            return -1;
        }
        Identifier overlaySprite = ResourcePackEmissiveTextureResolver.registeredOverlayForBaseSprite(baseSprite);
        return overlaySprite == null ? -1 : TextureArrayBridge.resolveSpriteId(overlaySprite.toString());
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

    public ResolvedBlockSprite setPendingTextureSprite(@Nullable Sprite sprite, int geometryFlags) {
        return setPendingTextureSprite(sprite, geometryFlags, null, null);
    }

    private ResolvedBlockSprite setPendingTextureSprite(@Nullable Sprite sprite, int geometryFlags,
        @Nullable Direction face) {
        return setPendingTextureSprite(sprite, geometryFlags, face, null);
    }

    private ResolvedBlockSprite setPendingTextureSprite(@Nullable Sprite sprite, int geometryFlags,
        @Nullable Direction face, @Nullable RepeatTextureBasis repeatTextureBasis) {
        Identifier id = sprite == null || sprite.getContents() == null
            ? null
            : sprite.getContents().getId();
        ResolvedBlockSprite resolved = this.blockGeometryContextDepth > 0
            ? ResourcePackTextureVariantResolver.resolveBlockSprite(
                sprite,
                this.pendingBlockWorld,
                this.pendingBlockState,
                this.pendingBlockPos,
                face,
                repeatTextureBasis)
            : new ResolvedBlockSprite(TextureArrayBridge.resolveRenderableSpriteId(id),
                false, 0xFFFFFF, false, -1);
        int spriteId = resolved.spriteId();
        if (spriteId < 0) spriteId = TextureArrayBridge.resolveRenderableSpriteId(id);
        if (this.blockGeometryContextDepth > 0) {
            int alphaMode = resolved.alphaMode() >= 0
                ? resolved.alphaMode()
                : ResourcePackBlockLayerResolver.resolveBlockAlphaMode(this.pendingBlockState);
            if (alphaMode >= 0) {
                setPendingAlphaMode(alphaMode);
            }
        }
        this.pendingNaturalUvTransform = this.blockGeometryContextDepth > 0
            ? ResourcePackNaturalTextureResolver.resolveBlockTransform(
                sprite,
                spriteId,
                this.pendingBlockPos,
                face)
            : NaturalTransform.identity();
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        if (spriteId >= 0 && sprite != null) {
            this.pendingTextureOverride = spriteId;
            this.pendingVertexFlags |= geometryFlags & PBR_GEOMETRY_FLAG_MASK;
            applyRegisteredEmissiveOverlayMask(spriteId, true);
            this.pendingSpriteUvRemap = true;
            this.pendingSpriteMinU = sprite.getMinU();
            this.pendingSpriteMaxU = sprite.getMaxU();
            this.pendingSpriteMinV = sprite.getMinV();
            this.pendingSpriteMaxV = sprite.getMaxV();
        } else {
            this.pendingTextureOverride = -1;
            this.pendingSpriteUvRemap = false;
            this.pendingNaturalUvTransform = NaturalTransform.identity();
        }
        return resolved;
    }

    public void setPendingTextureSpriteId(int spriteId, int geometryFlags) {
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        if (spriteId >= 0) {
            this.pendingTextureOverride = spriteId;
            this.pendingVertexFlags |= geometryFlags & PBR_GEOMETRY_FLAG_MASK;
            applyRegisteredEmissiveOverlayMask(spriteId, false);
            this.pendingSpriteUvRemap = false;
            this.pendingNaturalUvTransform = NaturalTransform.identity();
        } else {
            this.pendingTextureOverride = -1;
            this.pendingSpriteUvRemap = false;
            this.pendingNaturalUvTransform = NaturalTransform.identity();
        }
    }

    private void setPendingTextureSpriteIdWithSourceUv(int spriteId, int geometryFlags,
        @Nullable Sprite sourceSprite, NaturalTransform uvTransform) {
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        if (spriteId >= 0 && sourceSprite != null) {
            this.pendingTextureOverride = spriteId;
            this.pendingVertexFlags |= geometryFlags & PBR_GEOMETRY_FLAG_MASK;
            applyRegisteredEmissiveOverlayMask(spriteId, false);
            this.pendingSpriteUvRemap = true;
            this.pendingSpriteMinU = sourceSprite.getMinU();
            this.pendingSpriteMaxU = sourceSprite.getMaxU();
            this.pendingSpriteMinV = sourceSprite.getMinV();
            this.pendingSpriteMaxV = sourceSprite.getMaxV();
            this.pendingNaturalUvTransform = uvTransform == null ? NaturalTransform.identity() : uvTransform;
        } else {
            this.pendingTextureOverride = -1;
            this.pendingSpriteUvRemap = false;
            this.pendingNaturalUvTransform = NaturalTransform.identity();
        }
    }

    public void clearPendingTextureSprite() {
        this.pendingTextureOverride = -1;
        this.pendingVertexFlags &= ~PBR_GEOMETRY_FLAG_MASK;
        this.pendingSpriteUvRemap = false;
        this.pendingNaturalUvTransform = NaturalTransform.identity();
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

    private static boolean isGrassBlockSideOverlay(@Nullable Sprite sprite) {
        if (sprite == null || sprite.getContents() == null) {
            return false;
        }
        return GRASS_BLOCK_SIDE_OVERLAY.equals(sprite.getContents().getId());
    }

    @Nullable
    private static RepeatTextureBasis repeatTextureBasis(@Nullable BakedQuad quad) {
        return quad == null ? null : repeatTextureBasisForTest(quad.getVertexData());
    }

    @Nullable
    public static RepeatTextureBasis repeatTextureBasisForTest(@Nullable int[] data) {
        if (data == null || data.length < 32) {
            return null;
        }
        SignedAxis uAxis = dominantTextureAxis(data, true);
        SignedAxis vAxis = dominantTextureAxis(data, false);
        if (uAxis == null || vAxis == null || uAxis.axis() == vAxis.axis()) {
            return null;
        }
        return new RepeatTextureBasis(uAxis.axis(), uAxis.sign(), vAxis.axis(), vAxis.sign());
    }

    @Nullable
    private static SignedAxis dominantTextureAxis(int[] data, boolean useU) {
        SignedAxis best = null;
        float bestScore = 0.0f;
        for (int a = 0; a < 4; a++) {
            for (int b = a + 1; b < 4; b++) {
                int ai = a * 8;
                int bi = b * 8;
                float du = Float.intBitsToFloat(data[bi + 4]) - Float.intBitsToFloat(data[ai + 4]);
                float dv = Float.intBitsToFloat(data[bi + 5]) - Float.intBitsToFloat(data[ai + 5]);
                float target = useU ? du : dv;
                float other = useU ? dv : du;
                float targetAbs = Math.abs(target);
                if (targetAbs < 1.0e-6f || Math.abs(other) > targetAbs * 0.25f) {
                    continue;
                }
                float dx = Float.intBitsToFloat(data[bi]) - Float.intBitsToFloat(data[ai]);
                float dy = Float.intBitsToFloat(data[bi + 1]) - Float.intBitsToFloat(data[ai + 1]);
                float dz = Float.intBitsToFloat(data[bi + 2]) - Float.intBitsToFloat(data[ai + 2]);
                SignedAxis axis = signedDominantAxis(dx, dy, dz, target);
                if (axis == null) {
                    continue;
                }
                float score = targetAbs * axis.magnitude();
                if (score > bestScore) {
                    bestScore = score;
                    best = axis;
                }
            }
        }
        return best;
    }

    @Nullable
    private static SignedAxis signedDominantAxis(float dx, float dy, float dz, float textureDelta) {
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        float az = Math.abs(dz);
        if (ax < 1.0e-6f && ay < 1.0e-6f && az < 1.0e-6f) {
            return null;
        }
        if (ax >= ay && ax >= az) {
            return new SignedAxis(Direction.Axis.X, signedAxisDirection(dx, textureDelta), ax);
        }
        if (ay >= az) {
            return new SignedAxis(Direction.Axis.Y, signedAxisDirection(dy, textureDelta), ay);
        }
        return new SignedAxis(Direction.Axis.Z, signedAxisDirection(dz, textureDelta), az);
    }

    private static int signedAxisDirection(float positionDelta, float textureDelta) {
        return positionDelta * textureDelta < 0.0f ? -1 : 1;
    }

    private record SignedAxis(Direction.Axis axis, int sign, float magnitude) {
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
        NaturalTransform previousNaturalUvTransform = this.pendingNaturalUvTransform;
        RepeatTextureBasis previousRepeatTextureBasis = this.pendingRepeatTextureBasis;
        float previousPendingEmission = this.pendingEmission;
        try {
            Sprite blockSprite = null;
            BlockOverlaySprite[] blockOverlaySprites = new BlockOverlaySprite[0];
            NaturalTransform blockOverlayUvTransform = NaturalTransform.identity();
            int emissiveGeometryOverlaySpriteId = -1;
            float baseRed = red;
            float baseGreen = green;
            float baseBlue = blue;
            boolean baseUseQuadColorData = useQuadColorData;
            boolean baseQuadRendered = false;
            if (this.blockGeometryContextDepth > 0) {
                blockSprite = quad.getSprite();
                this.pendingRepeatTextureBasis = repeatTextureBasis(quad);
                if (isGrassBlockSideOverlay(blockSprite)
                    && !ResourcePackTextureVariantResolver.hasBlockSpriteRule(
                        blockSprite,
                        this.pendingBlockWorld,
                        this.pendingBlockState,
                        this.pendingBlockPos,
                        quad.getFace())) {
                    return;
                }
                CompactCtmQuadrants compactQuadrants =
                    ResourcePackTextureVariantResolver.resolveCompactCtmQuadrants(
                        blockSprite,
                        this.pendingBlockWorld,
                        this.pendingBlockState,
                        this.pendingBlockPos,
                        quad.getFace());
                if (compactQuadrants != null) {
                    if (compactQuadrants.tintOverride()) {
                        int tintRgb = compactQuadrants.tintRgb() & 0x00FFFFFF;
                        baseRed = ((tintRgb >> 16) & 0xFF) / 255.0F;
                        baseGreen = ((tintRgb >> 8) & 0xFF) / 255.0F;
                        baseBlue = (tintRgb & 0xFF) / 255.0F;
                        baseUseQuadColorData = false;
                    } else {
                        int vanillaRgb = rgbFromFloats(baseRed, baseGreen, baseBlue);
                        int compatRgb = ResourcePackColorPropertiesResolver.resolveBlockColor(
                            this.pendingBlockState,
                            this.pendingBlockWorld,
                            this.pendingBlockPos,
                            -1,
                            vanillaRgb);
                        if ((compatRgb & 0x00FFFFFF) != (vanillaRgb & 0x00FFFFFF)) {
                            baseRed = ((compatRgb >> 16) & 0xFF) / 255.0F;
                            baseGreen = ((compatRgb >> 8) & 0xFF) / 255.0F;
                            baseBlue = (compatRgb & 0xFF) / 255.0F;
                            baseUseQuadColorData = false;
                        }
                    }
                    int alphaMode = compactQuadrants.alphaMode() >= 0
                        ? compactQuadrants.alphaMode()
                        : ResourcePackBlockLayerResolver.resolveBlockAlphaMode(this.pendingBlockState);
                    baseQuadRendered = emitCompactCtmSubQuads(
                        matrixEntry,
                        quad,
                        brightnesses,
                        baseRed,
                        baseGreen,
                        baseBlue,
                        alpha,
                        lights,
                        overlay,
                        baseUseQuadColorData,
                        blockSprite,
                        compactQuadrants,
                        alphaMode);
                }
                if (!baseQuadRendered) {
                    ResolvedBlockSprite resolved =
                        setPendingTextureSprite(blockSprite, PBR_FLAG_BLOCK_GEOMETRY, quad.getFace(),
                            this.pendingRepeatTextureBasis);
                    emissiveGeometryOverlaySpriteId = geometryEmissiveOverlaySpriteId(this.pendingTextureOverride);
                    if (resolved.tintOverride()) {
                        int tintRgb = resolved.tintRgb() & 0x00FFFFFF;
                        baseRed = ((tintRgb >> 16) & 0xFF) / 255.0F;
                        baseGreen = ((tintRgb >> 8) & 0xFF) / 255.0F;
                        baseBlue = (tintRgb & 0xFF) / 255.0F;
                        baseUseQuadColorData = false;
                    } else {
                        int vanillaRgb = rgbFromFloats(baseRed, baseGreen, baseBlue);
                        int compatRgb = ResourcePackColorPropertiesResolver.resolveBlockColor(
                            this.pendingBlockState,
                            this.pendingBlockWorld,
                            this.pendingBlockPos,
                            -1,
                            vanillaRgb);
                        if ((compatRgb & 0x00FFFFFF) != (vanillaRgb & 0x00FFFFFF)) {
                            baseRed = ((compatRgb >> 16) & 0xFF) / 255.0F;
                            baseGreen = ((compatRgb >> 8) & 0xFF) / 255.0F;
                            baseBlue = (compatRgb & 0xFF) / 255.0F;
                            baseUseQuadColorData = false;
                        }
                    }
                    blockOverlayUvTransform = this.pendingNaturalUvTransform;
                } else {
                    blockOverlayUvTransform = NaturalTransform.identity();
                }
                blockOverlaySprites = ResourcePackTextureVariantResolver.resolveBlockOverlaySprites(
                    blockSprite,
                    this.pendingBlockWorld,
                    this.pendingBlockState,
                    this.pendingBlockPos,
                    quad.getFace(),
                    this.pendingRepeatTextureBasis);
            }
            if (!baseQuadRendered) {
                VertexConsumer.super.quad(matrixEntry,
                    quad,
                    brightnesses,
                    baseRed,
                    baseGreen,
                    baseBlue,
                    alpha,
                    lights,
                    overlay,
                    baseUseQuadColorData);
            }
            if (!baseQuadRendered && emissiveGeometryOverlaySpriteId >= 0 && blockSprite != null) {
                emitEmissiveGeometryOverlayQuad(
                    matrixEntry,
                    quad,
                    brightnesses,
                    alpha,
                    lights,
                    overlay,
                    blockSprite,
                    blockOverlayUvTransform,
                    emissiveGeometryOverlaySpriteId);
            }
            if (blockOverlaySprites.length > 0 && blockSprite != null) {
                for (BlockOverlaySprite blockOverlay : blockOverlaySprites) {
                    int tintRgb = blockOverlay.tintRgb() & 0x00FFFFFF;
                    setPendingTextureSpriteIdWithSourceUv(
                        blockOverlay.spriteId(),
                        PBR_FLAG_BLOCK_GEOMETRY,
                        blockSprite,
                        blockOverlayUvTransform);
                    setPendingAlphaMode(blockOverlay.alphaMode() >= 0
                        ? blockOverlay.alphaMode()
                        : PBR_ALPHA_MODE_CUTOUT);
                    VertexConsumer.super.quad(matrixEntry,
                        quad,
                        brightnesses,
                        ((tintRgb >> 16) & 0xFF) / 255.0F,
                        ((tintRgb >> 8) & 0xFF) / 255.0F,
                        (tintRgb & 0xFF) / 255.0F,
                        alpha,
                        lights,
                        overlay,
                        false);
                }
            }
        } finally {
            this.pendingTextureOverride = previousTextureOverride;
            this.pendingVertexFlags = previousVertexFlags;
            this.pendingSpriteUvRemap = previousSpriteUvRemap;
            this.pendingSpriteMinU = previousSpriteMinU;
            this.pendingSpriteMaxU = previousSpriteMaxU;
            this.pendingSpriteMinV = previousSpriteMinV;
            this.pendingSpriteMaxV = previousSpriteMaxV;
            this.pendingNaturalUvTransform = previousNaturalUvTransform;
            this.pendingRepeatTextureBasis = previousRepeatTextureBasis;
            this.pendingEmission = previousPendingEmission;
        }
    }

    private void emitEmissiveGeometryOverlayQuad(MatrixStack.Entry matrixEntry,
        BakedQuad quad,
        float[] brightnesses,
        float alpha,
        int[] lights,
        int overlay,
        Sprite sourceSprite,
        NaturalTransform uvTransform,
        int overlaySpriteId) {
        int previousTextureOverride = this.pendingTextureOverride;
        int previousVertexFlags = this.pendingVertexFlags;
        boolean previousSpriteUvRemap = this.pendingSpriteUvRemap;
        float previousSpriteMinU = this.pendingSpriteMinU;
        float previousSpriteMaxU = this.pendingSpriteMaxU;
        float previousSpriteMinV = this.pendingSpriteMinV;
        float previousSpriteMaxV = this.pendingSpriteMaxV;
        NaturalTransform previousNaturalUvTransform = this.pendingNaturalUvTransform;
        float previousPendingEmission = this.pendingEmission;
        try {
            setPendingTextureSpriteIdWithSourceUv(
                overlaySpriteId,
                PBR_FLAG_BLOCK_GEOMETRY,
                sourceSprite,
                uvTransform);
            setPendingOverlayAlphaMask(false);
            setPendingAlphaMode(PBR_ALPHA_MODE_TRANSPARENT);
            setPendingEmission(EMISSIVE_OVERLAY_REFERENCE_NITS);
            VertexConsumer.super.quad(matrixEntry,
                quad,
                brightnesses,
                1.0F,
                1.0F,
                1.0F,
                alpha,
                lights,
                overlay,
                false);
        } finally {
            this.pendingTextureOverride = previousTextureOverride;
            this.pendingVertexFlags = previousVertexFlags;
            this.pendingSpriteUvRemap = previousSpriteUvRemap;
            this.pendingSpriteMinU = previousSpriteMinU;
            this.pendingSpriteMaxU = previousSpriteMaxU;
            this.pendingSpriteMinV = previousSpriteMinV;
            this.pendingSpriteMaxV = previousSpriteMaxV;
            this.pendingNaturalUvTransform = previousNaturalUvTransform;
            this.pendingEmission = previousPendingEmission;
        }
    }

    private boolean emitCompactCtmSubQuads(MatrixStack.Entry matrixEntry,
        BakedQuad quad,
        float[] brightnesses,
        float red,
        float green,
        float blue,
        float alpha,
        int[] lights,
        int overlay,
        boolean useQuadColorData,
        Sprite sourceSprite,
        CompactCtmQuadrants quadrants,
        int alphaMode) {
        int[][] splitData = new int[4][];
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            splitData[quadrant] = compactCtmSubQuadData(quad.getVertexData(), sourceSprite, quadrant);
            if (splitData[quadrant] == null) {
                return false;
            }
        }
        for (int quadrant = 0; quadrant < 4; quadrant++) {
            setPendingTextureSpriteIdWithSourceUv(
                quadrants.spriteId(quadrant),
                PBR_FLAG_BLOCK_GEOMETRY,
                sourceSprite,
                NaturalTransform.identity());
            if (alphaMode >= 0) {
                setPendingAlphaMode(alphaMode);
            }
            BakedQuad splitQuad = new BakedQuad(
                splitData[quadrant],
                quad.getTintIndex(),
                quad.getFace(),
                sourceSprite,
                quad.hasShade(),
                quad.getLightEmission());
            VertexConsumer.super.quad(matrixEntry,
                splitQuad,
                brightnesses,
                red,
                green,
                blue,
                alpha,
                lights,
                overlay,
                useQuadColorData);
        }
        return true;
    }

    @Nullable
    private static int[] compactCtmSubQuadData(@Nullable int[] data, Sprite sourceSprite, int quadrant) {
        if (data == null || data.length < 32 || sourceSprite == null) {
            return null;
        }
        QuadSample[] corners = quadSamplesByLocalUvCorner(data, sourceSprite);
        if (corners == null) {
            return null;
        }
        float u0;
        float u1;
        float v0;
        float v1;
        switch (quadrant) {
            case 0 -> {
                u0 = 0.0f;
                u1 = 0.5f;
                v0 = 0.0f;
                v1 = 0.5f;
            }
            case 1 -> {
                u0 = 0.0f;
                u1 = 0.5f;
                v0 = 0.5f;
                v1 = 1.0f;
            }
            case 2 -> {
                u0 = 0.5f;
                u1 = 1.0f;
                v0 = 0.5f;
                v1 = 1.0f;
            }
            default -> {
                u0 = 0.5f;
                u1 = 1.0f;
                v0 = 0.0f;
                v1 = 0.5f;
            }
        }

        int[] split = data.clone();
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            float localU = localSpriteUvForTest(
                Float.intBitsToFloat(data[offset + 4]),
                sourceSprite.getMinU(),
                sourceSprite.getMaxU());
            float localV = localSpriteUvForTest(
                Float.intBitsToFloat(data[offset + 5]),
                sourceSprite.getMinV(),
                sourceSprite.getMaxV());
            float targetU = localU < 0.5f ? u0 : u1;
            float targetV = localV < 0.5f ? v0 : v1;
            writeInterpolatedQuadVertex(split, offset, corners, sourceSprite, targetU, targetV);
        }
        return split;
    }

    @Nullable
    private static QuadSample[] quadSamplesByLocalUvCorner(int[] data, Sprite sourceSprite) {
        QuadSample[] corners = new QuadSample[4];
        for (int vertex = 0; vertex < 4; vertex++) {
            int offset = vertex * 8;
            float localU = localSpriteUvForTest(
                Float.intBitsToFloat(data[offset + 4]),
                sourceSprite.getMinU(),
                sourceSprite.getMaxU());
            float localV = localSpriteUvForTest(
                Float.intBitsToFloat(data[offset + 5]),
                sourceSprite.getMinV(),
                sourceSprite.getMaxV());
            if (!Float.isFinite(localU) || !Float.isFinite(localV)) {
                return null;
            }
            int corner = localV < 0.5f
                ? (localU < 0.5f ? 0 : 1)
                : (localU < 0.5f ? 3 : 2);
            if (corners[corner] != null) {
                return null;
            }
            corners[corner] = new QuadSample(
                Float.intBitsToFloat(data[offset]),
                Float.intBitsToFloat(data[offset + 1]),
                Float.intBitsToFloat(data[offset + 2]),
                data[offset + 3],
                data[offset + 6],
                data[offset + 7]);
        }
        for (QuadSample corner : corners) {
            if (corner == null) {
                return null;
            }
        }
        return corners;
    }

    private static void writeInterpolatedQuadVertex(int[] out, int offset, QuadSample[] corners,
        Sprite sourceSprite, float u, float v) {
        out[offset] = Float.floatToRawIntBits(bilinear(
            corners[0].x(), corners[1].x(), corners[2].x(), corners[3].x(), u, v));
        out[offset + 1] = Float.floatToRawIntBits(bilinear(
            corners[0].y(), corners[1].y(), corners[2].y(), corners[3].y(), u, v));
        out[offset + 2] = Float.floatToRawIntBits(bilinear(
            corners[0].z(), corners[1].z(), corners[2].z(), corners[3].z(), u, v));
        out[offset + 3] = bilinearPackedChannels(
            corners[0].color(), corners[1].color(), corners[2].color(), corners[3].color(), u, v);
        out[offset + 4] = Float.floatToRawIntBits(
            sourceSprite.getMinU() + (sourceSprite.getMaxU() - sourceSprite.getMinU()) * u);
        out[offset + 5] = Float.floatToRawIntBits(
            sourceSprite.getMinV() + (sourceSprite.getMaxV() - sourceSprite.getMinV()) * v);
        out[offset + 6] = bilinearPackedChannels(
            corners[0].light(), corners[1].light(), corners[2].light(), corners[3].light(), u, v);
        out[offset + 7] = nearestQuadSample(corners, u, v).normal();
    }

    private static float bilinear(float c00, float c10, float c11, float c01, float u, float v) {
        float top = c00 + (c10 - c00) * u;
        float bottom = c01 + (c11 - c01) * u;
        return top + (bottom - top) * v;
    }

    private static int bilinearPackedChannels(int c00, int c10, int c11, int c01, float u, float v) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            float value = bilinear(
                (c00 >>> shift) & 0xFF,
                (c10 >>> shift) & 0xFF,
                (c11 >>> shift) & 0xFF,
                (c01 >>> shift) & 0xFF,
                u,
                v);
            out |= Math.round(clamp01(value / 255.0f) * 255.0f) << shift;
        }
        return out;
    }

    private static QuadSample nearestQuadSample(QuadSample[] corners, float u, float v) {
        if (v < 0.5f) {
            return u < 0.5f ? corners[0] : corners[1];
        }
        return u < 0.5f ? corners[3] : corners[2];
    }

    private record QuadSample(float x, float y, float z, int color, int light, int normal) {
    }

    private static int rgbFromFloats(float red, float green, float blue) {
        int r = Math.round(clamp01(red) * 255.0F);
        int g = Math.round(clamp01(green) * 255.0F);
        int b = Math.round(clamp01(blue) * 255.0F);
        return (r << 16) | (g << 8) | b;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, value));
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
                    TextureArrayBridge.resolveRenderableTextureGlId(identifier,
                        MinecraftClient.getInstance()
                            .getTextureManager()
                            .getTexture(identifier)
                            .getGlId());
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
                    TextureArrayBridge.resolveRenderableTextureGlId(identifier,
                        MinecraftClient.getInstance()
                            .getTextureManager()
                            .getTexture(identifier)
                            .getGlId());
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
