package com.radiance.client.texture.compat;

import com.radiance.client.option.Options;
import net.minecraft.util.Identifier;

public final class ResourcePackTextureNames {
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
        if (path == null || !path.endsWith(".png")) {
            return false;
        }
        String baseName = path.substring(0, path.length() - 4);
        return baseName.endsWith("_s")
            || baseName.endsWith("_n")
            || baseName.endsWith("_f")
            || baseName.endsWith("_e");
    }
}
