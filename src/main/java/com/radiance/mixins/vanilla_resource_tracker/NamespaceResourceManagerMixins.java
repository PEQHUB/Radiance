package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.IdentifierInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.resource.InputSupplier;
import net.minecraft.resource.NamespaceResourceManager;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NamespaceResourceManager.class)
public abstract class NamespaceResourceManagerMixins {

    @Shadow
    private static InputSupplier<InputStream> wrapForDebug(Identifier id, ResourcePack pack,
        InputSupplier<InputStream> supplier) {
        return null;
    }

    @Inject(method = "createResource(Lnet/minecraft/resource/ResourcePack;Lnet/minecraft/util/Identifier;Lnet/minecraft/resource/InputSupplier;Lnet/minecraft/resource/InputSupplier;)Lnet/minecraft/resource/Resource;", at = @At(value = "HEAD"),
        cancellable = true)
    private static void addIdentifierToInputStream(ResourcePack pack,
        Identifier id,
        InputSupplier<InputStream> supplier,
        InputSupplier<ResourceMetadata> metadataSupplier,
        CallbackInfoReturnable<Resource> cir) {
        cir.setReturnValue(new Resource(pack, () -> {
            InputSupplier<InputStream> inputStreamInputSupplier = wrapForDebug(id, pack, supplier);
            if (inputStreamInputSupplier == null) {
                return null;
            }
            InputStream inputStream = inputStreamInputSupplier.get();
            return new IdentifierInputStream(inputStream, id);
        }, metadataSupplier));
    }

    /**
     * Filter PBR auxiliary textures (_s, _n, _f suffixed) from resource discovery
     * results so they are not stitched into the vanilla sprite atlas. This prevents
     * the 4x memory waste that occurs when specular/normal map PNGs end up in the
     * block/item atlas alongside their base textures.
     *
     * Only filters textures in atlas-eligible paths (textures/block, textures/item,
     * textures/entity) — other resource types are left untouched.
     */
    @Inject(method = "findResources", at = @At("RETURN"), cancellable = true)
    public void filterPbrTexturesFromAtlas(String startingPath,
        Predicate<Identifier> pathPredicate,
        CallbackInfoReturnable<Map<Identifier, Resource>> cir) {
        Map<Identifier, Resource> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }

        Map<Identifier, Resource> filtered = null;

        for (Map.Entry<Identifier, Resource> entry : original.entrySet()) {
            Identifier id = entry.getKey();
            if (radiance$isPbrAuxiliaryTexture(id)) {
                if (filtered == null) {
                    // Lazily copy — only allocate if we actually need to filter something
                    filtered = new LinkedHashMap<>(original);
                }
                filtered.remove(id);
            }
        }

        if (filtered != null) {
            cir.setReturnValue(filtered);
        }
    }

    /**
     * Returns true if the given identifier refers to a PBR auxiliary texture
     * (_s, _n, or _f suffix) that sits in an atlas-eligible path and should
     * therefore be excluded from the vanilla sprite atlas.
     */
    @Unique
    private static boolean radiance$isPbrAuxiliaryTexture(Identifier id) {
        String path = id.getPath();

        // Only filter textures in atlas-eligible directories
        if (!path.startsWith("textures/block/") && !path.startsWith("textures/item/")
            && !path.startsWith("textures/entity/")) {
            return false;
        }

        // Must be a .png file
        if (!path.endsWith(".png")) {
            return false;
        }

        // Strip extension, check for PBR suffixes
        String baseName = path.substring(0, path.length() - 4); // remove ".png"
        return baseName.endsWith("_s") || baseName.endsWith("_n") || baseName.endsWith("_f");
    }
}
