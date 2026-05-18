package com.radiance.client.vertex;

import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_ALBEDO_EMISSION;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_COLOR_LAYER;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_EMISSIVE_BLOCK_TYPE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_FLAGS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_TEXTURE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_GLINT_UV;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_LIGHT_PACKED;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_NORM;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_OVERLAY_PACKED;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POS;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_POST_BASE;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_ID;
import static com.radiance.client.vertex.PBRVertexFormatElements.PBR_TEXTURE_UV;

import net.minecraft.client.render.VertexFormat;

public class PBRVertexFormats {

    // 96-byte packed PBRTriangle (6 x vec4, std430)
    public static final VertexFormat
        PBR_TRIANGLE =
        VertexFormat.builder()
            // [0..15]  pos(vec3) + flags(uint)
            .add("Pos", PBR_POS)
            .add("Flags", PBR_FLAGS)

            // [16..31] norm(vec3) + albedoEmission(float)
            .add("Norm", PBR_NORM)
            .add("AlbedoEmission", PBR_ALBEDO_EMISSION)

            // [32..47] colorLayer(vec4)
            .add("ColorLayer", PBR_COLOR_LAYER)

            // [48..63] postBase(vec3) + emissiveBlockType(uint)
            .add("PostBase", PBR_POST_BASE)
            .add("EmissiveBlockType", PBR_EMISSIVE_BLOCK_TYPE)

            // [64..79] textureUV(vec2) + glintUV(vec2)
            .add("TextureUV", PBR_TEXTURE_UV)
            .add("GlintUV", PBR_GLINT_UV)

            // [80..95] textureID(uint) + glintTexture(uint) + overlayPacked(uint) + lightPacked(uint)
            .add("TextureID", PBR_TEXTURE_ID)
            .add("GlintTexture", PBR_GLINT_TEXTURE)
            .add("OverlayPacked", PBR_OVERLAY_PACKED)
            .add("LightPacked", PBR_LIGHT_PACKED)
            .build();
}
