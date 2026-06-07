package com.radiance.client.autopbr;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

public final class AutoPbrTextureCatalog {
    public static final String DISPLACEMENT_HEIGHT_SOURCE = "direct_labpbr_normal_alpha";
    public static final String MATERIAL_SET_BINDING_POLICY = "global_material_id_table_v1";
    public static final String MATERIAL_SET_SHADER_LOOKUP_KEY = "pbr_texture_id_material_id";
    public static final boolean MATERIAL_SET_NATIVE_TABLE_PRESENT = true;
    private static final Identifier WATER_STILL = Identifier.ofVanilla("block/water_still");
    private static final Identifier WATER_FLOW = Identifier.ofVanilla("block/water_flow");
    private static final Identifier WATER_OVERLAY = Identifier.ofVanilla("block/water_overlay");

    private AutoPbrTextureCatalog() {
    }

    public static Identifier fallbackSprite() {
        Identifier oak = Identifier.ofVanilla("block/oak_planks");
        if (isEditableSprite(oak) && TextureArrayBridge.sortedSpriteIds.contains(oak)) return oak;
        for (Identifier sprite : TextureArrayBridge.sortedSpriteIds) {
            if (isEditableSprite(sprite)) return sprite;
        }
        if (!TextureArrayBridge.sortedSpriteIds.isEmpty()) return TextureArrayBridge.sortedSpriteIds.get(0);
        return oak;
    }

    public static boolean isPhysicalWaterSprite(Identifier id) {
        return WATER_STILL.equals(id) || WATER_FLOW.equals(id) || WATER_OVERLAY.equals(id);
    }

    public static boolean isPhysicalWaterSprite(int spriteId) {
        return spriteId >= 0
            && spriteId < TextureArrayBridge.sortedSpriteIds.size()
            && isPhysicalWaterSprite(TextureArrayBridge.sortedSpriteIds.get(spriteId));
    }

    public static boolean isEditableSprite(Identifier id) {
        return id != null && !isPhysicalWaterSprite(id);
    }

    public static int spriteIndex(Identifier id) {
        if (id == null) return -1;
        return TextureArrayBridge.sortedSpriteIds.indexOf(id);
    }

    public static NativeImage albedo(int spriteId) {
        return TextureTracker.spriteAlbedoCache.get(spriteId);
    }

    public static NativeImage specular(int spriteId) {
        return TextureTracker.spriteSpecularCache.get(spriteId);
    }

    public static NativeImage normal(int spriteId) {
        return TextureTracker.spriteNormalCache.get(spriteId);
    }

    public static NativeImage flag(int spriteId) {
        return TextureTracker.spriteFlagCache.get(spriteId);
    }

    public static boolean validSpriteId(int spriteId) {
        return spriteId >= 0
            && spriteId < TextureArrayBridge.sortedSpriteIds.size()
            && spriteId < TextureTracker.MAX_TEXTURES;
    }

    public static byte specularSource(int spriteId) {
        return source(TextureTracker.spriteSpecularSource, spriteId);
    }

    public static byte normalSource(int spriteId) {
        return source(TextureTracker.spriteNormalSource, spriteId);
    }

    public static boolean hasSpecularTexture(int spriteId) {
        return specular(spriteId) != null;
    }

    public static boolean hasNormalTexture(int spriteId) {
        return normal(spriteId) != null;
    }

    public static boolean hasFlagTexture(int spriteId) {
        return flag(spriteId) != null;
    }

    public static boolean hasPackSpecular(int spriteId) {
        return hasSpecularTexture(spriteId) && specularSource(spriteId) == TextureTracker.SOURCE_PACK_AUTHORED;
    }

    public static boolean hasGeneratedSpecular(int spriteId) {
        return hasSpecularTexture(spriteId) && specularSource(spriteId) == TextureTracker.SOURCE_GENERATED;
    }

    public static boolean hasUserSpecular(int spriteId) {
        return hasSpecularTexture(spriteId) && specularSource(spriteId) == TextureTracker.SOURCE_USER_CUSTOM;
    }

    public static boolean hasFlatSpecular(int spriteId) {
        return specularSource(spriteId) == TextureTracker.SOURCE_FLAT;
    }

    public static boolean hasPackNormal(int spriteId) {
        return hasNormalTexture(spriteId) && normalSource(spriteId) == TextureTracker.SOURCE_PACK_AUTHORED;
    }

    public static boolean hasGeneratedNormal(int spriteId) {
        return hasNormalTexture(spriteId) && normalSource(spriteId) == TextureTracker.SOURCE_GENERATED;
    }

    public static boolean hasUserNormal(int spriteId) {
        return hasNormalTexture(spriteId) && normalSource(spriteId) == TextureTracker.SOURCE_USER_CUSTOM;
    }

    public static boolean hasFlatNormal(int spriteId) {
        return normalSource(spriteId) == TextureTracker.SOURCE_FLAT;
    }

    public static boolean hasAuthoredSpecular(int spriteId) {
        return hasSpecularTexture(spriteId) && hasAuthoredSpecularMaterial(spriteId);
    }

    public static boolean hasAuthoredSpecularMaterial(int spriteId) {
        return authoredSource(specularSource(spriteId));
    }

    public static boolean hasAuthoredNormal(int spriteId) {
        byte source = normalSource(spriteId);
        return hasNormalTexture(spriteId)
            && authoredSource(source);
    }

    public static boolean hasAuthoredHeightSource(int spriteId) {
        return hasAuthoredNormal(spriteId);
    }

    public static boolean hasAuthoredHeight(int spriteId) {
        return hasAuthoredHeightSource(spriteId) && heightAlphaRangePacked(spriteId) >= 0;
    }

    public static int spriteSourceFlags(int spriteId) {
        int flags = TextureTracker.encodeSpriteSourceFlags(specularSource(spriteId), normalSource(spriteId));
        if (hasSpecularTexture(spriteId)) flags |= TextureTracker.SPRITE_FLAG_HAS_SPECULAR;
        if (hasNormalTexture(spriteId)) flags |= TextureTracker.SPRITE_FLAG_HAS_NORMAL;
        if (hasAuthoredHeight(spriteId)) flags |= TextureTracker.SPRITE_FLAG_HAS_HEIGHT;
        return flags;
    }

    public static boolean hasDetectableAo(int spriteId) {
        return hasAuthoredNormal(spriteId);
    }

    public static String aoAvailability(int spriteId) {
        if (!validSpriteId(spriteId)) return "none:invalid_sprite";
        if (!hasNormalTexture(spriteId)) return "none:no_normal_layer";
        byte source = normalSource(spriteId);
        if (source == TextureTracker.SOURCE_PACK_AUTHORED || source == TextureTracker.SOURCE_USER_CUSTOM) {
            return "normal_blue:" + sourceLabel(source);
        }
        return "not_detectable:normal_source_" + sourceName(source);
    }

    public static boolean hasDetectableEmission(int spriteId) {
        return hasSpecularTexture(spriteId) && hasLabPbrEmissionAlpha(specular(spriteId));
    }

    public static String emissionAvailability(int spriteId) {
        if (!validSpriteId(spriteId)) return "none:invalid_sprite";
        if (!hasSpecularTexture(spriteId)) return "none:no_specular_layer";
        if (hasLabPbrEmissionAlpha(specular(spriteId))) {
            return "specular_alpha:" + sourceLabel(specularSource(spriteId));
        }
        return "none:no_nonzero_labpbr_emission_alpha";
    }

    public static int heightAlphaRangePacked(int spriteId) {
        if (!hasAuthoredHeightSource(spriteId)) return -1;
        NativeImage image = normal(spriteId);
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return -1;
        NativeImage albedoImage = albedo(spriteId);
        int min = 255;
        int max = 0;
        boolean foundOpaquePixel = false;
        int stepX = Math.max(1, image.getWidth() / 64);
        int stepY = Math.max(1, image.getHeight() / 64);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                if (albedoImage != null
                    && (((sampleScaled(albedoImage, x, y, image) >>> 24) & 0xFF) == 0)) {
                    continue;
                }
                int alpha = (image.getColorArgb(x, y) >>> 24) & 0xFF;
                min = Math.min(min, alpha);
                max = Math.max(max, alpha);
                foundOpaquePixel = true;
            }
        }
        if (!foundOpaquePixel || max <= min) return -1;
        return min | (max << 8);
    }

    public static String heightAlphaRangeLabel(int spriteId) {
        int packed = heightAlphaRangePacked(spriteId);
        if (packed < 0) return "none";
        return (packed & 0xFF) + ".." + ((packed >>> 8) & 0xFF);
    }

    public static DisplacementEligibility displacementEligibility(int spriteId) {
        if (!validSpriteId(spriteId)) {
            return new DisplacementEligibility(false, "invalid_sprite",
                DISPLACEMENT_HEIGHT_SOURCE, false, false, false, sourceLabel(TextureTracker.SOURCE_FLAT), "none");
        }
        boolean hasNormal = hasNormalTexture(spriteId);
        byte source = normalSource(spriteId);
        boolean authored = hasAuthoredHeightSource(spriteId);
        int range = heightAlphaRangePacked(spriteId);
        boolean visibleRange = range >= 0;
        String rangeText = visibleRange
            ? ((range & 0xFF) + ".." + ((range >>> 8) & 0xFF))
            : "none";
        if (!hasNormal) {
            return new DisplacementEligibility(false, "no_normal_layer",
                DISPLACEMENT_HEIGHT_SOURCE, false, false, false, sourceLabel(source), rangeText);
        }
        if (!authored) {
            return new DisplacementEligibility(false, "normal_source_" + sourceName(source),
                DISPLACEMENT_HEIGHT_SOURCE, false, true, false, sourceLabel(source), rangeText);
        }
        if (!visibleRange) {
            return new DisplacementEligibility(false, "uniform_alpha_no_visible_relief",
                DISPLACEMENT_HEIGHT_SOURCE, true, true, false, sourceLabel(source), rangeText);
        }
        return new DisplacementEligibility(true, "eligible",
            DISPLACEMENT_HEIGHT_SOURCE, true, true, true, sourceLabel(source), rangeText);
    }

    public static SpriteProvenance spriteProvenance(int spriteId) {
        return new SpriteProvenance(
            spriteId,
            validSpriteId(spriteId),
            hasSpecularTexture(spriteId),
            sourceLabel(specularSource(spriteId)),
            hasNormalTexture(spriteId),
            sourceLabel(normalSource(spriteId)),
            hasFlagTexture(spriteId),
            hasAuthoredHeight(spriteId),
            heightAlphaRangeLabel(spriteId),
            aoAvailability(spriteId),
            emissionAvailability(spriteId));
    }

    public static MaterialTextureSet materialTextureSet(int spriteId) {
        Identifier sprite = validSpriteId(spriteId) ? TextureArrayBridge.sortedSpriteIds.get(spriteId) : null;
        DisplacementEligibility displacement = displacementEligibility(spriteId);
        return new MaterialTextureSet(
            spriteId,
            spriteId,
            sprite == null ? "" : sprite.toString(),
            validSpriteId(spriteId),
            MATERIAL_SET_BINDING_POLICY,
            MATERIAL_SET_SHADER_LOOKUP_KEY,
            MATERIAL_SET_NATIVE_TABLE_PRESENT,
            true,
            "labpbr_direct",
            albedo(spriteId) != null,
            auxChannelBinding("specular", hasSpecularTexture(spriteId), specularSource(spriteId)),
            auxChannelBinding("normal", hasNormalTexture(spriteId), normalSource(spriteId)),
            flagChannelBinding(spriteId),
            hasAuthoredHeight(spriteId),
            DISPLACEMENT_HEIGHT_SOURCE,
            heightAlphaRangeLabel(spriteId),
            displacement,
            aoAvailability(spriteId),
            emissionAvailability(spriteId),
            spriteSourceFlags(spriteId),
            spriteSize(spriteId));
    }

    public static int spriteSize(int spriteId) {
        NativeImage image = albedo(spriteId);
        if (image != null) return image.getWidth();
        return Math.max(16, TextureTracker.currentSpriteLayerSize);
    }

    public static String sourceLabel(byte source) {
        return switch (source) {
            case TextureTracker.SOURCE_PACK_AUTHORED -> "Pack";
            case TextureTracker.SOURCE_GENERATED -> "Generated";
            case TextureTracker.SOURCE_USER_CUSTOM -> "User";
            case TextureTracker.SOURCE_FLAT -> "Flat/None";
            default -> "Flat/None";
        };
    }

    public static String sourceName(byte source) {
        return switch (source) {
            case TextureTracker.SOURCE_GENERATED -> "generated";
            case TextureTracker.SOURCE_PACK_AUTHORED -> "pack_authored";
            case TextureTracker.SOURCE_USER_CUSTOM -> "user_custom";
            case TextureTracker.SOURCE_FLAT -> "flat";
            default -> "unknown_" + source;
        };
    }

    public static int averageColor(NativeImage image, int fallback) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return fallback;
        long r = 0;
        long g = 0;
        long b = 0;
        long a = 0;
        int samples = 0;
        int stepX = Math.max(1, image.getWidth() / 8);
        int stepY = Math.max(1, image.getHeight() / 8);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int argb = image.getColorArgb(x, y);
                a += (argb >>> 24) & 0xFF;
                r += (argb >>> 16) & 0xFF;
                g += (argb >>> 8) & 0xFF;
                b += argb & 0xFF;
                samples++;
            }
        }
        if (samples == 0) return fallback;
        return 0xFF000000
            | (((int) (r / samples)) << 16)
            | (((int) (g / samples)) << 8)
            | (int) (b / samples);
    }

    private static byte source(byte[] sources, int spriteId) {
        if (spriteId < 0 || spriteId >= sources.length) return TextureTracker.SOURCE_FLAT;
        return sources[spriteId];
    }

    private static boolean authoredSource(byte source) {
        return source == TextureTracker.SOURCE_PACK_AUTHORED
            || source == TextureTracker.SOURCE_USER_CUSTOM;
    }

    private static ChannelBinding auxChannelBinding(String role, boolean present, byte source) {
        String binding = switch (source) {
            case TextureTracker.SOURCE_PACK_AUTHORED -> "direct_labpbr_" + role + "_sidecar";
            case TextureTracker.SOURCE_USER_CUSTOM -> "user_labpbr_" + role + "_sidecar";
            case TextureTracker.SOURCE_GENERATED -> "generated_material_lab_" + role;
            case TextureTracker.SOURCE_FLAT -> present ? "flat_labpbr_default" : "none";
            default -> present ? "unknown_" + role + "_layer" : "none";
        };
        String fallbackReason = switch (source) {
            case TextureTracker.SOURCE_PACK_AUTHORED, TextureTracker.SOURCE_USER_CUSTOM -> "none";
            case TextureTracker.SOURCE_GENERATED -> "generated_from_material_recipe";
            case TextureTracker.SOURCE_FLAT -> "no_pack_" + role + "_sidecar";
            default -> present ? "unknown_source_" + source : "no_" + role + "_layer";
        };
        return new ChannelBinding(role, present, sourceName(source), binding, fallbackReason);
    }

    private static ChannelBinding flagChannelBinding(int spriteId) {
        boolean present = hasFlagTexture(spriteId);
        return new ChannelBinding(
            "flags",
            present,
            present ? "pack_or_generated" : "flat",
            present ? "direct_flag_layer" : "none",
            present ? "none" : "no_flag_layer");
    }

    private static boolean hasLabPbrEmissionAlpha(NativeImage image) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return false;
        int stepX = Math.max(1, image.getWidth() / 16);
        int stepY = Math.max(1, image.getHeight() / 16);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int alpha = (image.getColorArgb(x, y) >>> 24) & 0xFF;
                if (alpha > 0 && alpha < 255) return true;
            }
        }
        return false;
    }

    private static int sampleScaled(NativeImage image, int x, int y, NativeImage sourceSpace) {
        if (image.getWidth() == sourceSpace.getWidth() && image.getHeight() == sourceSpace.getHeight()) {
            return image.getColorArgb(x, y);
        }
        int sx = Math.max(0, Math.min(image.getWidth() - 1, x * image.getWidth() / sourceSpace.getWidth()));
        int sy = Math.max(0, Math.min(image.getHeight() - 1, y * image.getHeight() / sourceSpace.getHeight()));
        return image.getColorArgb(sx, sy);
    }

    public record SpriteProvenance(int spriteId, boolean valid, boolean hasSpecular,
                                   String specularSource, boolean hasNormal, String normalSource,
                                   boolean hasFlags, boolean hasAuthoredHeight,
                                   String heightAlphaRange, String aoAvailability,
                                   String emissionAvailability) {
        public String compact() {
            return "spriteId=" + spriteId
                + " valid=" + valid
                + " specular=" + hasSpecular + ":" + specularSource
                + " normal=" + hasNormal + ":" + normalSource
                + " flags=" + hasFlags
                + " authoredHeight=" + hasAuthoredHeight
                + " heightAlphaRange=" + heightAlphaRange
                + " ao=" + aoAvailability
                + " emission=" + emissionAvailability;
        }
    }

    public record DisplacementEligibility(boolean eligible, String reason, String heightSource,
                                          boolean hasAuthoredHeight, boolean hasNormalLayer,
                                          boolean hasVisibleHeightRange, String normalSource,
                                          String heightAlphaRange) {
        public String compact() {
            return "eligible=" + eligible
                + " reason=" + reason
                + " heightSource=" + heightSource
                + " normalSource=" + normalSource
                + " normalLayer=" + hasNormalLayer
                + " authoredHeight=" + hasAuthoredHeight
                + " visibleHeightRange=" + hasVisibleHeightRange
                + " alphaRange=" + heightAlphaRange;
        }
    }

    public record ChannelBinding(String role, boolean present, String source, String binding,
                                 String fallbackReason) {
    }

    public record MaterialTextureSet(int materialSetId, int baseSpriteId, String sprite,
                                     boolean valid, String nativeBindingPolicy, String shaderLookupKey,
                                     boolean nativeMaterialSetTablePresent,
                                     boolean materialSetAliasesResolvedSprite,
                                     String decodeMode,
                                     boolean hasAlbedo, ChannelBinding specular,
                                     ChannelBinding normal, ChannelBinding flags,
                                     boolean hasAuthoredHeight, String heightSource,
                                     String heightAlphaRange,
                                     DisplacementEligibility displacement,
                                     String aoAvailability, String emissionAvailability,
                                     int sourceFlags, int size) {
    }
}
