package com.radiance.client.vertex;

import net.minecraft.client.render.VertexFormatElement;

public class PBRVertexFormatElements {

    // --- Flag bit constants (must match shared.hpp PBR_FLAG_*) ---
    public static final int PBR_FLAG_USE_NORM        = 1 << 0;
    public static final int PBR_FLAG_USE_COLOR_LAYER  = 1 << 1;
    public static final int PBR_FLAG_USE_TEXTURE      = 1 << 2;
    public static final int PBR_FLAG_USE_OVERLAY      = 1 << 3;
    public static final int PBR_FLAG_USE_GLINT        = 1 << 4;
    public static final int PBR_FLAG_USE_LIGHT        = 1 << 5;
    public static final int PBR_FLAG_COORD_SHIFT      = 8;
    public static final int PBR_FLAG_BLOCK_GEOMETRY   = 1 << 14;
    public static final int PBR_FLAG_FLUID_GEOMETRY   = 1 << 15;

    // --- Vertex format elements (order matches 96-byte PBRTriangle struct) ---

    // pos (vec3, 12 bytes) + flags (uint, 4 bytes) = 16 bytes
    public static final VertexFormatElement
        PBR_POS =
        VertexFormatElement.register(6, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_FLAGS =
        VertexFormatElement.register(7, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    // norm (vec3, 12 bytes) + albedoEmission (float, 4 bytes) = 16 bytes
    public static final VertexFormatElement
        PBR_NORM =
        VertexFormatElement.register(8, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_ALBEDO_EMISSION =
        VertexFormatElement.register(9, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 1);

    // colorLayer (vec4, 16 bytes)
    public static final VertexFormatElement
        PBR_COLOR_LAYER =
        VertexFormatElement.register(10, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 4);

    // postBase (vec3, 12 bytes) + emissiveBlockType (uint, 4 bytes) = 16 bytes
    public static final VertexFormatElement
        PBR_POST_BASE =
        VertexFormatElement.register(11, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_EMISSIVE_BLOCK_TYPE =
        VertexFormatElement.register(12, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    // textureUV (vec2, 8 bytes) + glintUV (vec2, 8 bytes) = 16 bytes
    public static final VertexFormatElement
        PBR_TEXTURE_UV =
        VertexFormatElement.register(13, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_GLINT_UV =
        VertexFormatElement.register(14, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 2);

    // textureID (uint) + glintTexture (uint) + overlayPacked (uint) + lightPacked (uint) = 16 bytes
    public static final VertexFormatElement
        PBR_TEXTURE_ID =
        VertexFormatElement.register(15, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_TEXTURE =
        VertexFormatElement.register(16, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_OVERLAY_PACKED =
        VertexFormatElement.register(17, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_LIGHT_PACKED =
        VertexFormatElement.register(18, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);
}
