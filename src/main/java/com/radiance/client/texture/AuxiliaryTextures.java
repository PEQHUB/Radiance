package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import com.radiance.client.texture.compat.ResourcePackCompatCtmTiles;
import com.radiance.client.texture.compat.ResourcePackTextureNames;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.INativeImageExt;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public enum AuxiliaryTextures {
    SPECULAR("specular", (identifier, source) ->
        auxiliaryCandidates(identifier, "specular", true, "_s", "_spec", "_specular"),
        INativeImageExt::neoVoxelRT$getSpecularNativeImage,
        INativeImageExt::neoVoxelRT$setSpecularNativeImage,
        INativeImageExt::neoVoxelRT$getSpecularUploadedLevelsMask,
        INativeImageExt::neoVoxelRT$setSpecularUploadedLevelsMask,
        TextureTracker.GLID2SpecularGLID), NORMAL("normal", (identifier, source) ->
        auxiliaryCandidates(identifier, "normal", true, "_n", "_normal", "_norm"),
        INativeImageExt::neoVoxelRT$getNormalNativeImage,
        INativeImageExt::neoVoxelRT$setNormalNativeImage,
        INativeImageExt::neoVoxelRT$getNormalUploadedLevelsMask,
        INativeImageExt::neoVoxelRT$setNormalUploadedLevelsMask,
        TextureTracker.GLID2NormalGLID), FLAG(
        "flag", (identifier, source) ->
        auxiliaryCandidates(identifier, "flag", true, "_f"),
        INativeImageExt::neoVoxelRT$getFlagNativeImage,
        INativeImageExt::neoVoxelRT$setFlagNativeImage,
        INativeImageExt::neoVoxelRT$getFlagUploadedLevelsMask,
        INativeImageExt::neoVoxelRT$setFlagUploadedLevelsMask,
        TextureTracker.GLID2FlagGLID);

    private static final List<AuxiliaryTextures> ALL_TEXTURES = Collections.unmodifiableList(
        Arrays.stream(values()).collect(Collectors.toList()));
    private static final String[] ROUGHNESS_SUFFIXES = {"_roughness", "_rough"};
    private static final String[] METALLIC_SUFFIXES = {"_metallic", "_metalness"};
    private static final String[] HEIGHT_SUFFIXES = {"_height", "_displacement", "_disp"};
    private static final String[] AO_SUFFIXES = {"_ao", "_ambientocclusion", "_ambient_occlusion"};
    private static final int LABPBR_DEFAULT_SPECULAR_ARGB = 0xFF000000;
    private static final int LABPBR_FLAT_NORMAL_ARGB = 0xFF8080FF;
    private static final int LABPBR_METAL_CODE = 238;
    private final IdentifierCandidateProvider identifierCandidateProvider;
    private final Getter getter;
    private final Setter setter;
    private final IntGetter uploadedLevelsMaskGetter;
    private final IntSetter uploadedLevelsMaskSetter;
    private final String name;
    private final int[] GLIDMapping;

    private static List<Identifier> auxiliaryCandidates(Identifier identifier, String folder,
        boolean includeUnsuffixedFolder, String... suffixes) {
        if (identifier == null) {
            return List.of();
        }
        String namespace = identifier.getNamespace();
        String path = identifier.getPath();
        int dot = path.lastIndexOf('.');
        int slash = path.lastIndexOf('/');
        LinkedHashSet<Identifier> fallbacks = new LinkedHashSet<>();

        if (dot > slash) {
            String basePath = path.substring(0, dot);
            String extension = path.substring(dot);
            for (String suffix : suffixes) {
                fallbacks.add(Identifier.of(namespace, basePath + suffix + extension));
            }
            if (basePath.startsWith("textures/")) {
                String folderBase = "textures/" + folder + "/" + basePath.substring("textures/".length());
                for (String suffix : suffixes) {
                    fallbacks.add(Identifier.of(namespace, folderBase + suffix + extension));
                }
                if (includeUnsuffixedFolder) {
                    fallbacks.add(Identifier.of(namespace, folderBase + extension));
                }
            }
        }

        return withCtmSidecarCandidates(identifier, suffixes, fallbacks);
    }

    private static List<Identifier> withCtmSidecarCandidates(Identifier identifier, String[] suffixes,
        LinkedHashSet<Identifier> fallbackIds) {
        LinkedHashSet<Identifier> candidates = new LinkedHashSet<>();
        for (String suffix : suffixes) {
            Identifier ctmSidecar = ResourcePackCompatCtmTiles.ctmSidecarResourceIdentifier(identifier, suffix);
            if (ctmSidecar != null) {
                candidates.add(ctmSidecar);
            }
        }
        for (String suffix : suffixes) {
            candidates.addAll(ResourcePackCompatCtmTiles.ctmMaterialFallbackSidecarResourceIdentifiers(
                identifier, suffix));
        }
        candidates.addAll(fallbackIds);
        return List.copyOf(candidates);
    }

    private static List<Identifier> scalarCandidates(Identifier identifier, ScalarSidecar sidecar) {
        LinkedHashSet<Identifier> candidates = new LinkedHashSet<>();
        for (String folder : sidecar.folders()) {
            candidates.addAll(auxiliaryCandidates(identifier, folder, true, sidecar.suffixes()));
        }
        return List.copyOf(candidates);
    }

    AuxiliaryTextures(String name,
        IdentifierCandidateProvider identifierCandidateProvider, Getter getter, Setter setter,
        IntGetter uploadedLevelsMaskGetter, IntSetter uploadedLevelsMaskSetter,
        int[] GLIDMapping) {
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

    private ComposedAuxiliary composeFromScalarSidecars(ResourceManager resourceManager,
        Identifier identifier, NativeImage source, int level) {
        return switch (this) {
            case SPECULAR -> composeSpecularFromScalarSidecars(resourceManager, identifier, source, level);
            case NORMAL -> composeNormalFromScalarSidecars(resourceManager, identifier, source, level);
            case FLAG -> null;
        };
    }

    private static ComposedAuxiliary composeSpecularFromScalarSidecars(ResourceManager resourceManager,
        Identifier identifier, NativeImage source, int level) {
        try (NativeImage roughness = readPackImage(resourceManager,
            scalarCandidates(identifier, ScalarSidecar.ROUGHNESS), level);
             NativeImage metallic = readPackImage(resourceManager,
                 scalarCandidates(identifier, ScalarSidecar.METALLIC), level)) {
            if (roughness == null && metallic == null) {
                return null;
            }
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, source.getWidth(),
                source.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int roughnessArgb = roughness == null ? 0 : sampleScaled(roughness, x, y, image);
                    int metallicArgb = metallic == null ? 0 : sampleScaled(metallic, x, y, image);
                    image.setColorArgb(x, y, composeSpecularPixel(
                        roughnessArgb, roughness != null, metallicArgb, metallic != null));
                }
            }
            return new ComposedAuxiliary(image);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static ComposedAuxiliary composeNormalFromScalarSidecars(ResourceManager resourceManager,
        Identifier identifier, NativeImage source, int level) {
        try (NativeImage height = readPackImage(resourceManager,
            scalarCandidates(identifier, ScalarSidecar.HEIGHT), level);
             NativeImage ao = readPackImage(resourceManager,
                 scalarCandidates(identifier, ScalarSidecar.AO), level)) {
            if (height == null && ao == null) {
                return null;
            }
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, source.getWidth(),
                source.getHeight(), false);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int heightArgb = height == null ? 0 : sampleScaled(height, x, y, image);
                    int aoArgb = ao == null ? 0 : sampleScaled(ao, x, y, image);
                    image.setColorArgb(x, y, composeNormalPixel(
                        heightArgb, height != null, aoArgb, ao != null));
                }
            }
            return new ComposedAuxiliary(image);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static NativeImage readPackImage(ResourceManager resourceManager, List<Identifier> candidates,
        int level) throws IOException {
        for (Identifier candidate : candidates) {
            Optional<Resource> optionalResource = resourceManager.getResource(candidate);
            if (optionalResource.isPresent()) {
                try (InputStream input = optionalResource.get().getInputStream();
                     NativeImage tmpImage = NativeImage.read(input)) {
                    return MipmapUtil.getSpecificMipmapLevelImage(tmpImage, level);
                }
            }
        }
        return null;
    }

    private static int composeSpecularPixel(int roughnessArgb, boolean hasRoughness,
        int metallicArgb, boolean hasMetallic) {
        int smoothness = 0;
        if (hasRoughness) {
            float roughness = luminance(roughnessArgb);
            smoothness = MathHelper.clamp(
                Math.round((1.0f - (float) Math.sqrt(MathHelper.clamp(roughness, 0.02f, 0.98f))) * 255.0f),
                0, 255);
        }
        int metallic = hasMetallic && luminanceByte(metallicArgb) >= 128 ? LABPBR_METAL_CODE : 0;
        return (LABPBR_DEFAULT_SPECULAR_ARGB & 0xFF0000FF)
            | (smoothness << 16)
            | (metallic << 8);
    }

    private static int composeNormalPixel(int heightArgb, boolean hasHeight, int aoArgb, boolean hasAo) {
        int height = hasHeight ? luminanceByte(heightArgb) : 255;
        int ao = hasAo ? luminanceByte(aoArgb) : 255;
        return (height << 24)
            | (LABPBR_FLAT_NORMAL_ARGB & 0x00FFFF00)
            | ao;
    }

    private static int sampleScaled(NativeImage image, int x, int y, NativeImage sourceSpace) {
        if (image.getWidth() == sourceSpace.getWidth() && image.getHeight() == sourceSpace.getHeight()) {
            return image.getColorArgb(x, y);
        }
        int sx = MathHelper.clamp(x * image.getWidth() / sourceSpace.getWidth(), 0, image.getWidth() - 1);
        int sy = MathHelper.clamp(y * image.getHeight() / sourceSpace.getHeight(), 0, image.getHeight() - 1);
        return image.getColorArgb(sx, sy);
    }

    private static float luminance(int argb) {
        return luminanceByte(argb) / 255.0f;
    }

    private static int luminanceByte(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return MathHelper.clamp(Math.round((r * 0.2126f + g * 0.7152f + b * 0.0722f) * 255.0f), 0, 255);
    }

    public static boolean hasVisibleHeightAlphaRange(NativeImage normal, NativeImage albedo) {
        if (normal == null || normal.getWidth() <= 0 || normal.getHeight() <= 0) {
            return false;
        }
        int min = 255;
        int max = 0;
        boolean foundOpaquePixel = false;
        int stepX = Math.max(1, normal.getWidth() / 64);
        int stepY = Math.max(1, normal.getHeight() / 64);
        for (int y = 0; y < normal.getHeight(); y += stepY) {
            for (int x = 0; x < normal.getWidth(); x += stepX) {
                if (albedo != null
                    && (((sampleScaled(albedo, x, y, normal) >>> 24) & 0xFF) == 0)) {
                    continue;
                }
                int alpha = (normal.getColorArgb(x, y) >>> 24) & 0xFF;
                min = Math.min(min, alpha);
                max = Math.max(max, alpha);
                foundOpaquePixel = true;
            }
        }
        return foundOpaquePixel && max > min;
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
            if (ResourcePackTextureNames.hasNonEmissivePbrAuxiliarySuffix(identifier.getPath())) {
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

                    boolean success;
                    try {
                        auxiliaryTemplateImage = readPackImage(resourceManager, candidates, level);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    success = auxiliaryTemplateImage != null;
                    if (!success) {
                        ComposedAuxiliary composed = auxiliaryTexture.composeFromScalarSidecars(
                            resourceManager, identifier, source, level);
                        if (composed != null) {
                            auxiliaryTemplateImage = composed.image();
                            success = true;
                        }
                    }

                    if (success && level == 0) {
                        auxiliaryTexture.markPackProvided(auxiliaryTargetId);
                        auxiliarySource = TextureTracker.SOURCE_PACK_AUTHORED;
                    }

                    if (!success) {
                        if (level == 0) {
                            auxiliaryTexture.markGenerated(auxiliaryTargetId);
                        }

                        if (auxiliaryTexture == NORMAL) {
                            auxiliarySource = TextureTracker.SOURCE_FLAT;
                            auxiliaryTemplateImage = source.applyToCopy(i -> LABPBR_FLAT_NORMAL_ARGB);
                        } else {
                            auxiliarySource = TextureTracker.SOURCE_FLAT;
                            auxiliaryTemplateImage = source.applyToCopy(i -> 0);
                        }
                    } else if (auxiliaryTexture == NORMAL
                        && hasVisibleHeightAlphaRange(auxiliaryTemplateImage, source)) {
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

    public static List<Identifier> candidatesForTest(AuxiliaryTextures texture, Identifier identifier) {
        if (texture == null) {
            return List.of();
        }
        return texture.identifierCandidateProvider.get(identifier, null);
    }

    public static List<Identifier> scalarCandidatesForTest(String channel, Identifier identifier) {
        ScalarSidecar sidecar = ScalarSidecar.fromChannel(channel);
        return sidecar == null ? List.of() : scalarCandidates(identifier, sidecar);
    }

    public static int composedSpecularPixelForTest(Integer roughnessArgb, Integer metallicArgb) {
        return composeSpecularPixel(
            roughnessArgb == null ? 0 : roughnessArgb,
            roughnessArgb != null,
            metallicArgb == null ? 0 : metallicArgb,
            metallicArgb != null);
    }

    public static int composedNormalPixelForTest(Integer heightArgb, Integer aoArgb) {
        return composeNormalPixel(
            heightArgb == null ? 0 : heightArgb,
            heightArgb != null,
            aoArgb == null ? 0 : aoArgb,
            aoArgb != null);
    }

    private enum ScalarSidecar {
        ROUGHNESS(new String[] {"roughness"}, ROUGHNESS_SUFFIXES),
        METALLIC(new String[] {"metallic", "metalness"}, METALLIC_SUFFIXES),
        HEIGHT(new String[] {"height", "displacement"}, HEIGHT_SUFFIXES),
        AO(new String[] {"ao", "ambientocclusion", "ambient_occlusion"}, AO_SUFFIXES);

        private final String[] folders;
        private final String[] suffixes;

        ScalarSidecar(String[] folders, String[] suffixes) {
            this.folders = folders;
            this.suffixes = suffixes;
        }

        String[] folders() {
            return folders;
        }

        String[] suffixes() {
            return suffixes;
        }

        static ScalarSidecar fromChannel(String channel) {
            if (channel == null) {
                return null;
            }
            return switch (channel) {
                case "roughness" -> ROUGHNESS;
                case "metallic", "metalness" -> METALLIC;
                case "height", "displacement" -> HEIGHT;
                case "ao", "ambientocclusion", "ambient_occlusion" -> AO;
                default -> null;
            };
        }
    }

    private record ComposedAuxiliary(NativeImage image) {
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
