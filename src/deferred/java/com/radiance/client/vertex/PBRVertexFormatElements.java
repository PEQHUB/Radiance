package com.radiance.client.vertex;

import net.minecraft.client.render.VertexFormatElement;

public class PBRVertexFormatElements {

    public static final VertexFormatElement
        PBR_POS =
        VertexFormatElement.register(6, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_USE_NORM =
        VertexFormatElement.register(7, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_NORM =
        VertexFormatElement.register(8, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_USE_COLOR_LAYER =
        VertexFormatElement.register(9, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_COLOR_LAYER =
        VertexFormatElement.register(10, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 4);

    public static final VertexFormatElement
        PBR_USE_TEXTURE =
        VertexFormatElement.register(11, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_USE_OVERLAY =
        VertexFormatElement.register(12, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_UV =
        VertexFormatElement.register(13, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_OVERLAY_UV =
        VertexFormatElement.register(14, 0, VertexFormatElement.ComponentType.INT,
            VertexFormatElement.Usage.UV, 2);

    public static final VertexFormatElement
        PBR_USE_GLINT =
        VertexFormatElement.register(15, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_TEXTURE_ID =
        VertexFormatElement.register(16, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_GLINT_UV =
        VertexFormatElement.register(17, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 2);

    public static final VertexFormatElement
        PBR_GLINT_TEXTURE =
        VertexFormatElement.register(18, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_USE_LIGHT =
        VertexFormatElement.register(19, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_LIGHT_UV =
        VertexFormatElement.register(20, 0, VertexFormatElement.ComponentType.INT,
            VertexFormatElement.Usage.UV, 2);

    public static final VertexFormatElement
        PBR_COORDINATE =
        VertexFormatElement.register(21, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);

    public static final VertexFormatElement
        PBR_POST_BASE =
        VertexFormatElement.register(22, 0, VertexFormatElement.ComponentType.FLOAT,
            VertexFormatElement.Usage.GENERIC, 3);

    public static final VertexFormatElement
        PBR_ALBEDO_EMISSION =
        VertexFormatElement.register(23, 0, VertexFormatElement.ComponentType.UINT,
            VertexFormatElement.Usage.UV, 1);
}
