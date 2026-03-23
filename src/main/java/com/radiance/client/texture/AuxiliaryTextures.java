package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.util.MaterialBlock;
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

                    // Precompute per-block luminance histogram bounds for GPU-side AutoPBR
                    // Must run for ALL AutoPBR blocks, even those with existing LabPBR textures,
                    // because the GPU shader always needs tight bounds for roughness derivation.
                    if (level == 0) {
                        int lumOrdinal = com.radiance.client.util.MaterialBlock.getOrdinalForTexture(identifier.getPath());
                        if (lumOrdinal >= 0 && com.radiance.client.option.Options.autoPBREnabled
                                && com.radiance.client.option.Options.materialAutoPBR[lumOrdinal]) {
                            float wR = 0.2627f, wG = 0.6780f, wB = 0.0593f;
                            // Use block's sprite region, not the full atlas
                            int rx = offsetX + unpackSkipPixels;
                            int ry = offsetY + unpackSkipRows;
                            int rw = regionWidth > 0 ? regionWidth : source.getWidth();
                            int rh = regionHeight > 0 ? regionHeight : source.getHeight();
                            float lMin = Float.MAX_VALUE, lMax = 0f;
                            for (int py = ry; py < ry + rh; py++) {
                                for (int px = rx; px < rx + rw; px++) {
                                    if (px < 0 || py < 0 || px >= source.getWidth() || py >= source.getHeight()) continue;
                                    int pixel = source.getColorArgb(px, py);
                                    if (((pixel >> 24) & 0xFF) == 0) continue;
                                    float sr = ((pixel >> 16) & 0xFF) / 255f;
                                    float sg = ((pixel >> 8) & 0xFF) / 255f;
                                    float sb = (pixel & 0xFF) / 255f;
                                    float lr = sr <= 0.04045f ? sr / 12.92f : (float) Math.pow((sr + 0.055) / 1.055, 2.4);
                                    float lg = sg <= 0.04045f ? sg / 12.92f : (float) Math.pow((sg + 0.055) / 1.055, 2.4);
                                    float lb = sb <= 0.04045f ? sb / 12.92f : (float) Math.pow((sb + 0.055) / 1.055, 2.4);
                                    float lum = wR * lr + wG * lg + wB * lb;
                                    lMin = Math.min(lMin, lum);
                                    lMax = Math.max(lMax, lum);
                                }
                            }
                            if (lMin >= lMax) { lMin = 0f; lMax = 1f; }
                            com.radiance.client.material.MaterialRegistry.setLumRange(lumOrdinal, lMin, lMax);
                        }
                    }

                    if (!success) {
                        int mbOrdinal = com.radiance.client.util.MaterialBlock.getOrdinalForTexture(identifier.getPath());

                        // Check per-material input type override
                        int inputType = 0; // 0=Auto
                        if (mbOrdinal >= 0) {
                            if (auxiliaryTexture == NORMAL) {
                                inputType = com.radiance.client.option.Options.materialNormalInputType[mbOrdinal];
                            } else if (auxiliaryTexture == SPECULAR) {
                                inputType = com.radiance.client.option.Options.materialSpecularInputType[mbOrdinal];
                            }
                        }

                        if (inputType == 2) {
                            // Flat: neutral normal or zero specular
                            if (auxiliaryTexture == NORMAL) {
                                auxiliaryTemplateImage = source.applyToCopy(i -> (128 << 24) | (128 << 16) | (128 << 8) | 255);
                            } else {
                                auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                            }
                        } else if (inputType == 1 && mbOrdinal >= 0) {
                            // Custom: load from user-specified path
                            String customPath = (auxiliaryTexture == NORMAL)
                                ? com.radiance.client.option.Options.materialCustomNormalPath[mbOrdinal]
                                : com.radiance.client.option.Options.materialCustomSpecularPath[mbOrdinal];
                            if (customPath != null && !customPath.isEmpty()) {
                                int customGlid = CustomTextureLoader.loadAndUpload(customPath, source);
                                if (customGlid >= 0) {
                                    auxiliaryTexture.GLIDMapping.put(targetId, customGlid);
                                    auxiliaryTexture.uploadedLevelsMaskSetter.set(sourceExt,
                                        uploadedLevelsMask | levelBit);
                                    // Track albedo GLID -> block ordinal so material editor works for custom textures
                                    if (level == 0 && mbOrdinal >= 0) {
                                        TextureTracker.albedoGLID2BlockOrdinal.put(targetId, mbOrdinal);
                                    }
                                    continue;
                                }
                            }
                            auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                        } else {
                            // Auto: existing LabPBR/auto-PBR path
                            boolean autoPBR = mbOrdinal >= 0
                                && com.radiance.client.option.Options.autoPBREnabled && com.radiance.client.option.Options.materialAutoPBR[mbOrdinal];
                            // lumMin/lumMax already computed above (before !success guard)
                            if (autoPBR && auxiliaryTexture == NORMAL) {
                                // Keep generating normal texture for POM height data (alpha channel)
                                auxiliaryTemplateImage = AutoPBRGenerator.generateNormal(source,
                                    com.radiance.client.option.Options.materialNormalStrength[mbOrdinal],
                                    (com.radiance.client.option.Options.materialAutoPBRFlags[mbOrdinal] & 2) != 0,
                                    com.radiance.client.option.Options.materialAutoPBRHeightGamma[mbOrdinal],
                                    (com.radiance.client.option.Options.materialAutoPBRFlags[mbOrdinal] & 4) != 0);
                                TextureTracker.hasHeightMap.add(targetId);
                            } else if (autoPBR && auxiliaryTexture == SPECULAR) {
                                // GPU-side AutoPBR: roughness computed in shader, skip CPU bake
                                auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                            } else {
                                auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                            }
                            // Always cache albedo and track GLIDs for block textures at mip 0,
                            // so LiveNormalReuploader can generate Auto-PBR on demand later
                            if (level == 0 && mbOrdinal >= 0) {
                                TextureTracker.albedoGLID2BlockOrdinal.put(targetId, mbOrdinal);
                                if (auxiliaryTexture == NORMAL) {
                                    if (!TextureTracker.materialBlockAlbedoCache.containsKey(targetId)) {
                                        TextureTracker.materialBlockAlbedoCache.put(targetId, source.applyToCopy(i -> i));
                                    }
                                    TextureTracker.autoPBRNormalGLIDs.add(targetId);
                                } else if (auxiliaryTexture == SPECULAR) {
                                    TextureTracker.autoPBRSpecularGLIDs.add(targetId);
                                }
                            }
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

            // Material class mask auto-generation (Phase B): create 1x1 R8_UNORM mask
            // for registered blocks so every texel maps to the correct MaterialClass.
            if (level == 0 && !TextureTracker.GLID2MaskGLID.containsKey(targetId)
                    && !TextureTracker.pendingMaskGLID.containsKey(targetId)) {
                int mbOrdinal = MaterialBlock.getOrdinalForTexture(identifier.getPath());
                if (mbOrdinal >= 0 && mbOrdinal < 256) {
                    // Registered block: create per-block 1x1 mask texture
                    int maskTexId = TextureProxy.generateTextureId();
                    TextureProxy.prepareImage(maskTexId, 1, 1, 1,
                        VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM);
                    TextureProxy.setFilter(maskTexId, 0, 0); // NEAREST — no interpolation

                    java.nio.ByteBuffer maskData = org.lwjgl.system.MemoryUtil.memAlloc(1);
                    maskData.put(0, (byte) mbOrdinal);
                    long maskPtr = org.lwjgl.system.MemoryUtil.memAddress(maskData);
                    TextureProxy.queueUpload(maskPtr, 1, 1, maskTexId, 0, 0, 0, 0, 1, 1, 0);
                    org.lwjgl.system.MemoryUtil.memFree(maskData);

                    TextureTracker.pendingMaskGLID.put(targetId, maskTexId);
                    TextureTracker.GLID2Texture.put(maskTexId,
                        new TextureTracker.Texture(1, 1, 1,
                            VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM, 0));
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
