package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COORDINATE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_GLINT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_LIGHT;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_OVERLAY;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_USE_TEXTURE;

import net.minecraft.client.render.VertexFormat;

public class PBRVertexFormats {

    public static final VertexFormat
        PBR_TRIANGLE =
        VertexFormat.builder()
            .add("Pos", PBR_POS)
            .add("UseNorm", PBR_USE_NORM)

            .add("Norm", PBR_NORM)
            .add("UseColorLayer", PBR_USE_COLOR_LAYER)

            .add("ColorLayer", PBR_COLOR_LAYER)

            .add("UseTexture", PBR_USE_TEXTURE)
            .add("UseOverlay", PBR_USE_OVERLAY)
            .add("TextureUV", PBR_TEXTURE_UV)

            .add("OverlayUV", PBR_OVERLAY_UV)
            .add("UseGlint", PBR_USE_GLINT)
            .add("TextureID", PBR_TEXTURE_ID)

            .add("GlintUV", PBR_GLINT_UV)
            .add("GlintTexture", PBR_GLINT_TEXTURE)
            .add("UseLight", PBR_USE_LIGHT)

            .add("LightUV", PBR_LIGHT_UV)
            .add("Coordinate", PBR_COORDINATE)
            .add("AlbedoEmission", PBR_ALBEDO_EMISSION)

            .add("PostBase", PBR_POST_BASE)

            .skip(4)
            .build();
}
