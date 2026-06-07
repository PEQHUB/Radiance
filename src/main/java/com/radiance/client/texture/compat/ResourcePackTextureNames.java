package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import java.util.Locale;
import net.minecraft.util.Identifier;

public final class ResourcePackTextureNames {
    private static final String[] SPECULAR_SUFFIXES = {"_s", "_spec", "_specular"};
    private static final String[] NORMAL_SUFFIXES = {"_n", "_normal", "_norm"};
    private static final String[] FLAG_SUFFIXES = {"_f"};
    private static final String[] EMISSIVE_SUFFIXES = {"_e"};
    private static final String[] SCALAR_SUFFIXES = {
        "_roughness", "_rough",
        "_metallic", "_metalness",
        "_height", "_displacement", "_disp",
        "_ao", "_ambientocclusion", "_ambient_occlusion"
    };
    public static final String PBR_AUXILIARY_SUFFIX_DESCRIPTION =
        "_s,_spec,_specular,_n,_normal,_norm,_f,_e,"
            + "_roughness,_rough,_metallic,_metalness,_height,_displacement,_disp,"
            + "_ao,_ambientocclusion,_ambient_occlusion";

    private ResourcePackTextureNames() {
    }

    public static boolean isAtlasEligiblePbrAuxiliaryTexture(Identifier id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        if (!path.startsWith("textures/block/")
            && !path.startsWith("textures/item/")
            && !path.startsWith("textures/entity/")) {
            return false;
        }
        return hasPbrAuxiliarySuffix(path)
            && !(Options.materialCompatEnabled
                && Options.materialCompatPhysicalEmissiveEnabled
                && ResourcePackEmissiveTextureResolver.isDefaultEmissiveResource(id));
    }

    public static boolean allowsPbrAuxiliaryLookup(Identifier id) {
        if (id == null) {
            return false;
        }
        String path = id.getPath();
        return path.startsWith("textures/block/")
            || path.startsWith("textures/item/")
            || path.startsWith("textures/entity/")
            || ResourcePackCompatCtmTiles.isCtmTileResourceIdentifier(id);
    }

    public static boolean hasPbrAuxiliarySuffix(String path) {
        return hasNonEmissivePbrAuxiliarySuffix(path) || isEmissiveAuxiliaryPath(path);
    }

    public static boolean hasNonEmissivePbrAuxiliarySuffix(String path) {
        return isSpecularAuxiliaryPath(path)
            || isNormalAuxiliaryPath(path)
            || isFlagAuxiliaryPath(path)
            || hasSuffix(path, SCALAR_SUFFIXES);
    }

    public static boolean isSpecularAuxiliaryPath(String path) {
        return hasSuffix(path, SPECULAR_SUFFIXES);
    }

    public static boolean isNormalAuxiliaryPath(String path) {
        return hasSuffix(path, NORMAL_SUFFIXES);
    }

    public static boolean isFlagAuxiliaryPath(String path) {
        return hasSuffix(path, FLAG_SUFFIXES);
    }

    public static boolean isEmissiveAuxiliaryPath(String path) {
        return hasSuffix(path, EMISSIVE_SUFFIXES);
    }

    private static boolean hasSuffix(String path, String[] suffixes) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String baseName = path.toLowerCase(Locale.ROOT);
        if (baseName.endsWith(".png")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        }
        for (String suffix : suffixes) {
            if (baseName.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
