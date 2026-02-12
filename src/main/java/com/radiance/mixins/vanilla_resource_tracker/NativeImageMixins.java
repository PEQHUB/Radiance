package com.radiance.mixins.vanilla_resource_tracker;

import com.radiance.client.texture.IdentifierInputStream;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.InputStream;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NativeImage.class)
public abstract class NativeImageMixins implements INativeImageExt {

    @Unique
    private int targetID = -1;

    @Unique
    private Identifier identifier = null;

    @Unique
    private NativeImage specularImage = null;

    @Unique
    private NativeImage normalImage = null;

    @Unique
    private NativeImage flagImage = null;

    @Unique
    private int specularUploadedLevelsMask = 0;

    @Unique
    private int normalUploadedLevelsMask = 0;

    @Unique
    private int flagUploadedLevelsMask = 0;

    @Inject(method = "read(Lnet/minecraft/client/texture/NativeImage$Format;Ljava/io/InputStream;)"
        +
        "Lnet/minecraft/client/texture/NativeImage;", at = @At(value = "RETURN"), cancellable = true)
    private static void readIdentifier(NativeImage.Format format, InputStream stream,
        CallbackInfoReturnable<NativeImage> cir) {
        NativeImage nativeImage = cir.getReturnValue();

        if (stream instanceof IdentifierInputStream) {
            Identifier identifier = ((IdentifierInputStream) stream).getResourceId();
            ((INativeImageExt) (Object) nativeImage).neoVoxelRT$setIdentifier(identifier);
            cir.setReturnValue(nativeImage);
        } else {
            cir.setReturnValue(nativeImage);
        }
    }

    @Override
    public int neoVoxelRT$getTargetID() {
        return targetID;
    }

    @Override
    public void neoVoxelRT$setTargetID(int id) {
        this.targetID = id;
    }

    @Override
    public Identifier neoVoxelRT$getIdentifier() {
        return identifier;
    }

    @Override
    public void neoVoxelRT$setIdentifier(Identifier id) {
        this.identifier = id;
    }

    @Override
    public NativeImage neoVoxelRT$getSpecularNativeImage() {
        return specularImage;
    }

    @Override
    public void neoVoxelRT$setSpecularNativeImage(NativeImage image) {
        this.specularImage = image;
        this.specularUploadedLevelsMask = 0;
    }

    @Override
    public NativeImage neoVoxelRT$getNormalNativeImage() {
        return normalImage;
    }

    @Override
    public void neoVoxelRT$setNormalNativeImage(NativeImage image) {
        this.normalImage = image;
        this.normalUploadedLevelsMask = 0;
    }

    @Override
    public NativeImage neoVoxelRT$getFlagNativeImage() {
        return flagImage;
    }

    @Override
    public void neoVoxelRT$setFlagNativeImage(NativeImage image) {
        this.flagImage = image;
        this.flagUploadedLevelsMask = 0;
    }

    @Override
    public int neoVoxelRT$getSpecularUploadedLevelsMask() {
        return specularUploadedLevelsMask;
    }

    @Override
    public void neoVoxelRT$setSpecularUploadedLevelsMask(int uploadedLevelsMask) {
        this.specularUploadedLevelsMask = uploadedLevelsMask;
    }

    @Override
    public int neoVoxelRT$getNormalUploadedLevelsMask() {
        return normalUploadedLevelsMask;
    }

    @Override
    public void neoVoxelRT$setNormalUploadedLevelsMask(int uploadedLevelsMask) {
        this.normalUploadedLevelsMask = uploadedLevelsMask;
    }

    @Override
    public int neoVoxelRT$getFlagUploadedLevelsMask() {
        return flagUploadedLevelsMask;
    }

    @Override
    public void neoVoxelRT$setFlagUploadedLevelsMask(int uploadedLevelsMask) {
        this.flagUploadedLevelsMask = uploadedLevelsMask;
    }
}
