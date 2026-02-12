package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

public enum AuxiliaryTextures {
    SPECULAR("specular", "_s", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String suffixedFileName = String.join("",
            new String[]{fileNameComponents[0], "_s.", fileNameComponents[1]});

        // Primary: same-directory LabPBR layout (e.g. textures/block/stone_s.png)
        String[] sameDir = pathComponents.clone();
        sameDir[sameDir.length - 1] = suffixedFileName;
        String sameDirPath = String.join("/", sameDir);
        Identifier sameDirId = Identifier.of(namespace, sameDirPath);

        // Fallback: separate subfolder layout (e.g. textures/specular/block/stone_s.png)
        String subfolderPath = sameDirPath.replace("textures/", "textures/specular/");
        Identifier subfolderId = Identifier.of(namespace, subfolderPath);

        return List.of(sameDirId, subfolderId);
    }, INativeImageExt::neoVoxelRT$getSpecularNativeImage,
        INativeImageExt::neoVoxelRT$setSpecularNativeImage,
        INativeImageExt::neoVoxelRT$getSpecularUploadedLevelsMask,
        INativeImageExt::neoVoxelRT$setSpecularUploadedLevelsMask,
        TextureTracker.GLID2SpecularGLID), NORMAL("normal", "_n", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String suffixedFileName = String.join("",
            new String[]{fileNameComponents[0], "_n.", fileNameComponents[1]});

        // Primary: same-directory LabPBR layout (e.g. textures/block/stone_n.png)
        String[] sameDir = pathComponents.clone();
        sameDir[sameDir.length - 1] = suffixedFileName;
        String sameDirPath = String.join("/", sameDir);
        Identifier sameDirId = Identifier.of(namespace, sameDirPath);

        // Fallback: separate subfolder layout (e.g. textures/normal/block/stone_n.png)
        String subfolderPath = sameDirPath.replace("textures/", "textures/normal/");
        Identifier subfolderId = Identifier.of(namespace, subfolderPath);

        return List.of(sameDirId, subfolderId);
    }, INativeImageExt::neoVoxelRT$getNormalNativeImage,
        INativeImageExt::neoVoxelRT$setNormalNativeImage,
        INativeImageExt::neoVoxelRT$getNormalUploadedLevelsMask,
        INativeImageExt::neoVoxelRT$setNormalUploadedLevelsMask,
        TextureTracker.GLID2NormalGLID), FLAG(
        "flag", "_f", (identifier, source) -> {
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        String[] pathComponents = path.split("/");
        String[] fileNameComponents = pathComponents[pathComponents.length - 1].split("\\.");
        String suffixedFileName = String.join("",
            new String[]{fileNameComponents[0], "_f.", fileNameComponents[1]});

        // Primary: same-directory layout (e.g. textures/block/stone_f.png)
        String[] sameDir = pathComponents.clone();
        sameDir[sameDir.length - 1] = suffixedFileName;
        String sameDirPath = String.join("/", sameDir);
        Identifier sameDirId = Identifier.of(namespace, sameDirPath);

        // Fallback: separate subfolder layout (e.g. textures/flag/block/stone_f.png)
        String subfolderPath = sameDirPath.replace("textures/", "textures/flag/");
        Identifier subfolderId = Identifier.of(namespace, subfolderPath);

        return List.of(sameDirId, subfolderId);
    }, INativeImageExt::neoVoxelRT$getFlagNativeImage,
        INativeImageExt::neoVoxelRT$setFlagNativeImage,
        INativeImageExt::neoVoxelRT$getFlagUploadedLevelsMask,
        INativeImageExt::neoVoxelRT$setFlagUploadedLevelsMask,
        TextureTracker.GLID2FlagGLID);

    private static final List<AuxiliaryTextures> ALL_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private final String suffix;
    private final IdentifierCandidateProvider identifierCandidateProvider;
    private final Getter getter;
    private final Setter setter;
    private final IntGetter uploadedLevelsMaskGetter;
    private final IntSetter uploadedLevelsMaskSetter;
    private final String name;
    private final Map<Integer, Integer> GLIDMapping;

    AuxiliaryTextures(String name, String suffix,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        IntGetter uploadedLevelsMaskGetter, IntSetter uploadedLevelsMaskSetter,
        Map<Integer, Integer> GLIDMapping) {
        this.suffix = suffix;
        this.identifierCandidateProvider = identifierCandidateProvider;
        this.getter = getter;
        this.setter = setter;
        this.uploadedLevelsMaskGetter = uploadedLevelsMaskGetter;
        this.uploadedLevelsMaskSetter = uploadedLevelsMaskSetter;
        this.name = name;
        this.GLIDMapping = GLIDMapping;
    }

    private static int getLevelBit(int level) {
        if (level <= 0) {
            return 1;
        }
        if (level >= 30) {
            return 1 << 30;
        }
        return 1 << level;
    }

    public static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight, boolean blur) {
        int targetId = sourceExt.neoVoxelRT$getTargetID();
        Identifier identifier = sourceExt.neoVoxelRT$getIdentifier();

        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();

        if (identifier != null) {
            if (ALL_TEXTURES.stream().anyMatch(texture -> {
                String path = identifier.getPath();
                int dotIndex = path.lastIndexOf('.');
                String baseName = (dotIndex != -1) ? path.substring(0, dotIndex) : path;

                return baseName.endsWith(texture.suffix);
            })) {
                return;
            }

            int levelBit = getLevelBit(level);
            for (AuxiliaryTextures auxiliaryTexture : ALL_TEXTURES) {
                NativeImage auxiliaryTemplateImage = auxiliaryTexture.getter.get(sourceExt);
                int uploadedLevelsMask = auxiliaryTexture.uploadedLevelsMaskGetter.get(sourceExt);

                if (auxiliaryTemplateImage != null
                    && (uploadedLevelsMask & levelBit) != 0
                    && auxiliaryTexture.GLIDMapping.containsKey(targetId)) {
                    continue;
                }

                int auxiliaryTargetId;

                // ensure the texture exists
                TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(targetId);
                VulkanConstants.VkFormat auxFormat = texture.format().toUnorm();
                if (!auxiliaryTexture.GLIDMapping.containsKey(targetId)) {
                    auxiliaryTargetId = TextureProxy.generateTextureId();
//                    System.out.println(
//                        "generate " + auxiliaryTexture.name + " texture for " + targetId + ": "
//                            + auxiliaryTargetId);

                    TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer() + 1,
                        texture.width(), texture.height(), auxFormat);
                    TextureTracker.GLID2Texture.put(auxiliaryTargetId,
                        new TextureTracker.Texture(texture.width(), texture.height(),
                            texture.channel(), auxFormat, texture.maxLayer()));
                    auxiliaryTexture.GLIDMapping.put(targetId, auxiliaryTargetId);
                } else {
                    auxiliaryTargetId = auxiliaryTexture.GLIDMapping.get(targetId);

                    TextureTracker.Texture auxiliaryTrackerTexture = TextureTracker.GLID2Texture.get(
                        auxiliaryTargetId);
                    if (texture.width() != auxiliaryTrackerTexture.width()
                        || texture.height() != auxiliaryTrackerTexture.height()
                        || auxiliaryTrackerTexture.format() != auxFormat) {
                        TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer() + 1,
                            texture.width(), texture.height(), auxFormat);
                        TextureTracker.GLID2Texture.put(auxiliaryTargetId,
                            new TextureTracker.Texture(texture.width(), texture.height(),
                                texture.channel(), auxFormat, texture.maxLayer()));
                    }
                }

                if (auxiliaryTemplateImage == null && (
                    identifier.getPath().contains("textures/block") || identifier.getPath()
                        .contains("textures/item") || identifier.getPath()
                        .contains("textures/entity"))) {
                    List<Identifier> candidates = auxiliaryTexture.identifierCandidateProvider.get(
                        identifier, source);

                    boolean success = false;
                    for (Identifier candidate : candidates) {
                        Optional<Resource> optionalResource = resourceManager.getResource(
                            candidate);
                        if (optionalResource.isPresent()) {
                            try (NativeImage tmpImage = NativeImage.read(
                                optionalResource.get().getInputStream())) {
                                auxiliaryTemplateImage = MipmapUtil.getSpecificMipmapLevelImage(
                                    tmpImage, level);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            success = true;
                            break;
                        }
                    }

                    if (!success) {
                        auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                    }
                }

                if (auxiliaryTemplateImage != null) {
                    NativeImage auxiliaryImage = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) auxiliaryTemplateImage).neoVoxelRT$alignTo(
                        source);
                    ((INativeImageExt) (Object) auxiliaryImage).neoVoxelRT$setTargetID(
                        auxiliaryTargetId);
                    if (auxiliaryTemplateImage != auxiliaryImage) {
                        auxiliaryTemplateImage.close();
                    }

                    if (auxiliaryImage.getWidth() != source.getWidth()
                        || auxiliaryImage.getHeight() != source.getHeight()
                        || auxiliaryImage.getFormat() != source.getFormat()) {
                        throw new RuntimeException(
                            auxiliaryTexture.name + " image size / format mismatch");
                    }

                    auxiliaryImage.upload(level, offsetX, offsetY, unpackSkipPixels, unpackSkipRows,
                        regionWidth, regionHeight, blur);
                    auxiliaryTexture.setter.set(sourceExt, auxiliaryImage);
                    auxiliaryTexture.uploadedLevelsMaskSetter.set(sourceExt,
                        uploadedLevelsMask | levelBit);
                }
            }
        }
    }

    public interface IdentifierCandidateProvider {

        List<Identifier> get(Identifier identifier, NativeImage source);
    }

    public interface Getter {

        NativeImage get(INativeImageExt nativeImageExt);
    }

    public interface Setter {

        void set(INativeImageExt nativeImageExt, NativeImage nativeImage);
    }

    public interface IntGetter {

        int get(INativeImageExt nativeImageExt);
    }

    public interface IntSetter {

        void set(INativeImageExt nativeImageExt, int value);
    }
}
