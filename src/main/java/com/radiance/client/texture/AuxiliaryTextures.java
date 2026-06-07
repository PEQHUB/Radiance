package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import com.radiance.client.texture.compat.ResourcePackTextureNames;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

        return withCtmSidecarCandidate(identifier, "_s", sameDirId, subfolderId);
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

        return withCtmSidecarCandidate(identifier, "_n", sameDirId, subfolderId);
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

        return withCtmSidecarCandidate(identifier, "_f", sameDirId, subfolderId);
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
    private final int[] GLIDMapping;

    private static List<Identifier> withCtmSidecarCandidate(Identifier identifier, String suffix,
        Identifier... fallbackIds) {
        Identifier ctmSidecar = ResourcePackCompatCtmTiles.ctmSidecarResourceIdentifier(identifier, suffix);
        if (ctmSidecar == null) {
            return List.of(fallbackIds);
        }
        ArrayList<Identifier> candidates = new ArrayList<>(fallbackIds.length + 1);
        candidates.add(ctmSidecar);
        for (Identifier fallback : fallbackIds) {
            if (!ctmSidecar.equals(fallback)) {
                candidates.add(fallback);
            }
        }
        return List.copyOf(candidates);
    }

    AuxiliaryTextures(String name, String suffix,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        IntGetter uploadedLevelsMaskGetter, IntSetter uploadedLevelsMaskSetter,
        int[] GLIDMapping) {
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

    private void markPackProvided(int glid) {
        if (glid < 0) return;
        if (this == SPECULAR) {
            TextureTracker.packProvidedSpecularGLIDs.add(glid);
            TextureTracker.customSpecularGLIDs.remove(glid);
        } else if (this == NORMAL) {
            TextureTracker.packProvidedNormalGLIDs.add(glid);
            TextureTracker.customNormalGLIDs.remove(glid);
        }
    }

    private void markCustomProvided(int glid) {
        if (glid < 0) return;
        if (this == SPECULAR) {
            TextureTracker.packProvidedSpecularGLIDs.remove(glid);
            TextureTracker.customSpecularGLIDs.add(glid);
        } else if (this == NORMAL) {
            TextureTracker.packProvidedNormalGLIDs.remove(glid);
            TextureTracker.customNormalGLIDs.add(glid);
        }
    }

    private void markGenerated(int glid) {
        if (glid < 0) return;
        if (this == SPECULAR) {
            TextureTracker.packProvidedSpecularGLIDs.remove(glid);
            TextureTracker.customSpecularGLIDs.remove(glid);
        } else if (this == NORMAL) {
            TextureTracker.packProvidedNormalGLIDs.remove(glid);
            TextureTracker.customNormalGLIDs.remove(glid);
        }
    }

    public static void loadAndUpload(NativeImage source, INativeImageExt sourceExt, int level,
        int offsetX, int offsetY, int unpackSkipPixels, int unpackSkipRows, int regionWidth,
        int regionHeight, boolean blur) {
        int targetId = sourceExt.neoVoxelRT$getTargetID();
        Identifier identifier = sourceExt.neoVoxelRT$getIdentifier();

        ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();
        if (targetId < 0 || targetId >= TextureTracker.MAX_TEXTURES
            || !TextureTracker.GLID2Texture.containsKey(targetId)) {
            return;
        }

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
                byte auxiliarySource = auxiliaryTemplateImage != null
                    ? ((INativeImageExt) (Object) auxiliaryTemplateImage).neoVoxelRT$getAuxSource()
                    : TextureTracker.SOURCE_GENERATED;
                int uploadedLevelsMask = auxiliaryTexture.uploadedLevelsMaskGetter.get(sourceExt);

                if (auxiliaryTemplateImage != null
                    && (uploadedLevelsMask & levelBit) != 0
                    && targetId >= 0 && targetId < TextureTracker.MAX_TEXTURES
                    && auxiliaryTexture.GLIDMapping[targetId] != -1) {
                    continue;
                }

                int auxiliaryTargetId;

                // ensure the texture exists
                TextureTracker.Texture texture = TextureTracker.GLID2Texture.get(targetId);
                VulkanConstants.VkFormat auxFormat = texture.format().toUnorm();
                if (targetId < 0 || targetId >= TextureTracker.MAX_TEXTURES
                        || auxiliaryTexture.GLIDMapping[targetId] == -1) {
                    auxiliaryTargetId = TextureProxy.generateTextureId();

                    TextureProxy.prepareImage(auxiliaryTargetId, texture.maxLayer() + 1,
                        texture.width(), texture.height(), auxFormat);
                    TextureTracker.GLID2Texture.put(auxiliaryTargetId,
                        new TextureTracker.Texture(texture.width(), texture.height(),
                            texture.channel(), auxFormat, texture.maxLayer()));
                    if (targetId >= 0 && targetId < TextureTracker.MAX_TEXTURES) {
                        auxiliaryTexture.GLIDMapping[targetId] = auxiliaryTargetId;
                    }
                } else {
                    auxiliaryTargetId = auxiliaryTexture.GLIDMapping[targetId];

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

                if (auxiliaryTemplateImage == null
                    && ResourcePackTextureNames.allowsPbrAuxiliaryLookup(identifier)) {
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
                            if (level == 0) {
                                auxiliaryTexture.markPackProvided(auxiliaryTargetId);
                                auxiliarySource = TextureTracker.SOURCE_PACK_AUTHORED;
                            }
                            break;
                        }
                    }

                    if (!success) {
                        if (level == 0) {
                            auxiliaryTexture.markGenerated(auxiliaryTargetId);
                        }

                        if (auxiliaryTexture == NORMAL) {
                            auxiliarySource = TextureTracker.SOURCE_FLAT;
                            auxiliaryTemplateImage = source.applyToCopy(i -> 0x00FF8080);
                        } else {
                            auxiliarySource = TextureTracker.SOURCE_FLAT;
                            auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                        }
                    } else if (auxiliaryTexture == NORMAL) {
                        // Resource pack LabPBR normal loaded — alpha contains height data
                        TextureTracker.hasHeightMap.add(targetId);
                    }
                }

                if (auxiliaryTemplateImage != null) {
                    NativeImage auxiliaryImage = ((com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt) (Object) auxiliaryTemplateImage).neoVoxelRT$alignTo(
                        source);
                    ((INativeImageExt) (Object) auxiliaryImage).neoVoxelRT$setTargetID(
                        auxiliaryTargetId);
                    ((INativeImageExt) (Object) auxiliaryImage).neoVoxelRT$setAuxSource(
                        auxiliarySource);
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
