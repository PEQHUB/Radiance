package com.radiance.client.texture;

import com.radiance.client.constant.VulkanConstants;
import com.radiance.client.proxy.vulkan.TextureProxy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.lwjgl.system.MemoryUtil;

/**
 * Discovers and uploads individual Blender PBR texture maps as separate VkImages.
 * Each channel gets its own texture in the bindless array at native resolution
 * using optimal per-channel Vulkan formats (R8 for scalars, RG8 for normals, RGBA8 for packed).
 *
 * Resource pack convention:
 *   assets/<namespace>/textures/pbr/<path>/<basename>/roughness.png
 *   assets/<namespace>/textures/pbr/<path>/<basename>/metallic.png
 *   assets/<namespace>/textures/pbr/<path>/<basename>/normal.png
 *   assets/<namespace>/textures/pbr/<path>/<basename>/height.png
 *   assets/<namespace>/textures/pbr/<path>/<basename>/emission.png
 *   assets/<namespace>/textures/pbr/<path>/<basename>/ao.png
 *   assets/<namespace>/textures/pbr/<path>/<basename>/extra.png  (RGBA: sub/trans/coat/aniso)
 *   assets/<namespace>/textures/pbr/<path>/<basename>/orm.png    (R=AO, G=Roughness, B=Metallic)
 */
public class BlenderTextureLoader {

    // Filename variants per channel (case-insensitive matching done at probe time)
    private static final String[][] ROUGHNESS_NAMES = {{"roughness"}, {"rough"}, {"rgh"}};
    private static final String[][] METALLIC_NAMES  = {{"metallic"}, {"metal"}, {"mtl"}};
    private static final String[][] NORMAL_NAMES    = {{"normal"}, {"nor"}, {"nrm"}};
    private static final String[][] HEIGHT_NAMES    = {{"height"}, {"displacement"}, {"disp"}};
    private static final String[][] EMISSION_NAMES  = {{"emission"}, {"emissive"}, {"emit"}};
    private static final String[][] AO_NAMES        = {{"ao"}, {"occlusion"}};
    private static final String[][] EXTRA_NAMES     = {{"extra"}};
    private static final String[][] ORM_NAMES       = {{"orm"}};

    /**
     * Scan for Blender PBR textures for a given albedo texture identifier.
     * Returns a map of detected channels → Resource handles ready for loading.
     */
    public static Map<TextureTracker.BlenderChannel, ChannelSource> scan(
            ResourceManager resources, Identifier albedoId) {

        Map<TextureTracker.BlenderChannel, ChannelSource> result = new EnumMap<>(TextureTracker.BlenderChannel.class);

        // Derive PBR folder path: textures/block/stone.png → textures/pbr/block/stone/
        String namespace = albedoId.getNamespace();
        String path = albedoId.getPath(); // e.g. "textures/block/stone.png"
        int dotIdx = path.lastIndexOf('.');
        String basePath = (dotIdx >= 0) ? path.substring(0, dotIdx) : path;
        // textures/block/stone → textures/pbr/block/stone
        String pbrFolder = basePath.replace("textures/", "textures/pbr/") + "/";

        // Probe each channel
        probeChannel(resources, namespace, pbrFolder, ROUGHNESS_NAMES, TextureTracker.BlenderChannel.ROUGHNESS, result);
        probeChannel(resources, namespace, pbrFolder, METALLIC_NAMES,  TextureTracker.BlenderChannel.METALLIC,  result);
        probeChannel(resources, namespace, pbrFolder, NORMAL_NAMES,    TextureTracker.BlenderChannel.NORMAL,    result);
        probeChannel(resources, namespace, pbrFolder, HEIGHT_NAMES,    TextureTracker.BlenderChannel.HEIGHT,    result);
        probeChannel(resources, namespace, pbrFolder, EMISSION_NAMES,  TextureTracker.BlenderChannel.EMISSION,  result);
        probeChannel(resources, namespace, pbrFolder, AO_NAMES,        TextureTracker.BlenderChannel.AO,        result);
        probeChannel(resources, namespace, pbrFolder, EXTRA_NAMES,     TextureTracker.BlenderChannel.EXTRA,     result);

        // ORM auto-detection: if orm.png exists, unpack to individual channels (individual files take precedence)
        for (String[] variants : ORM_NAMES) {
            for (String variant : variants) {
                for (String ext : new String[]{".png", ".jpg", ".tga"}) {
                    Identifier ormId = Identifier.of(namespace, pbrFolder + variant + ext);
                    Optional<Resource> ormRes = resources.getResource(ormId);
                    if (ormRes.isPresent()) {
                        // ORM: R=AO, G=Roughness, B=Metallic
                        if (!result.containsKey(TextureTracker.BlenderChannel.AO)) {
                            result.put(TextureTracker.BlenderChannel.AO, new ChannelSource(ormRes.get(), 0)); // R channel
                        }
                        if (!result.containsKey(TextureTracker.BlenderChannel.ROUGHNESS)) {
                            result.put(TextureTracker.BlenderChannel.ROUGHNESS, new ChannelSource(ormRes.get(), 1)); // G channel
                        }
                        if (!result.containsKey(TextureTracker.BlenderChannel.METALLIC)) {
                            result.put(TextureTracker.BlenderChannel.METALLIC, new ChannelSource(ormRes.get(), 2)); // B channel
                        }
                        return result;
                    }
                }
            }
        }

        return result;
    }

    private static void probeChannel(ResourceManager resources, String namespace, String pbrFolder,
                                     String[][] nameVariants, TextureTracker.BlenderChannel channel,
                                     Map<TextureTracker.BlenderChannel, ChannelSource> result) {
        for (String[] variants : nameVariants) {
            for (String variant : variants) {
                for (String ext : new String[]{".png", ".jpg", ".tga"}) {
                    Identifier id = Identifier.of(namespace, pbrFolder + variant + ext);
                    Optional<Resource> res = resources.getResource(id);
                    if (res.isPresent()) {
                        // For single-channel targets, extract channel 0 (R) from whatever image format
                        int sourceChannel = (channel == TextureTracker.BlenderChannel.EXTRA) ? -1 : 0;
                        result.put(channel, new ChannelSource(res.get(), sourceChannel));
                        return;
                    }
                }
            }
        }
    }

    /**
     * Upload all detected Blender PBR channels as individual VkImage textures.
     * Returns map of channel → Vulkan texture ID.
     */
    public static Map<TextureTracker.BlenderChannel, Integer> uploadAll(
            Map<TextureTracker.BlenderChannel, ChannelSource> channels) {

        Map<TextureTracker.BlenderChannel, Integer> result = new EnumMap<>(TextureTracker.BlenderChannel.class);

        for (Map.Entry<TextureTracker.BlenderChannel, ChannelSource> entry : channels.entrySet()) {
            TextureTracker.BlenderChannel channel = entry.getKey();
            ChannelSource source = entry.getValue();

            try (InputStream is = source.resource.getInputStream();
                 NativeImage img = NativeImage.read(is)) {

                int w = img.getWidth();
                int h = img.getHeight();

                // Determine format and extract channel data
                VulkanConstants.VkFormat format;
                ByteBuffer data;

                if (channel == TextureTracker.BlenderChannel.NORMAL) {
                    // 2-channel: RG8 UNORM
                    format = VulkanConstants.VkFormat.VK_FORMAT_R8G8_UNORM;
                    data = extractTwoChannels(img, 0, 1); // R, G
                } else if (channel == TextureTracker.BlenderChannel.EXTRA) {
                    // 4-channel: RGBA8 UNORM (sub/trans/coat/aniso)
                    format = VulkanConstants.VkFormat.VK_FORMAT_R8G8B8A8_UNORM;
                    data = extractFourChannels(img);
                } else {
                    // 1-channel: R8 UNORM (roughness, metallic, emission, height, AO)
                    format = VulkanConstants.VkFormat.VK_FORMAT_R8_UNORM;
                    data = extractSingleChannel(img, source.channelIndex);
                }

                int texId = TextureProxy.generateTextureId();
                TextureProxy.prepareImage(texId, 1, w, h, format);

                // LINEAR filtering for PBR data (smooth interpolation, not pixelated)
                // VK_FILTER_LINEAR = 1, VK_SAMPLER_MIPMAP_MODE_LINEAR = 1
                TextureProxy.setFilter(texId, 1, 1);

                // Upload raw channel data
                long ptr = MemoryUtil.memAddress(data);
                TextureProxy.queueUpload(ptr, data.remaining(), w, texId, 0, 0, 0, 0, w, h, 0);

                // Track in GLID2Texture for the texture system
                int channelCount = (channel == TextureTracker.BlenderChannel.NORMAL) ? 2
                    : (channel == TextureTracker.BlenderChannel.EXTRA) ? 4 : 1;
                TextureTracker.GLID2Texture.put(texId,
                    new TextureTracker.Texture(w, h, channelCount, format, 0));

                result.put(channel, texId);

                MemoryUtil.memFree(data);

            } catch (IOException e) {
                System.err.println("[Radiance] Failed to load Blender PBR " + channel + ": " + e.getMessage());
            }
        }

        return result;
    }

    // NativeImage.getColorArgb() returns 0xAARRGGBB (ARGB packed int)
    // Channel extraction helpers use this packing consistently.

    private static int getChannel(int argb, int channelIndex) {
        return switch (channelIndex) {
            case 0 -> (argb >> 16) & 0xFF; // R
            case 1 -> (argb >> 8) & 0xFF;  // G
            case 2 -> argb & 0xFF;          // B
            case 3 -> (argb >> 24) & 0xFF;  // A
            default -> (argb >> 16) & 0xFF; // default R
        };
    }

    /**
     * Extract a single channel from an RGBA NativeImage into an R8 byte buffer.
     * channelIndex: 0=R, 1=G, 2=B, 3=A
     */
    private static ByteBuffer extractSingleChannel(NativeImage img, int channelIndex) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer out = MemoryUtil.memAlloc(w * h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.put((byte) getChannel(img.getColorArgb(x, y), channelIndex));
            }
        }
        out.flip();
        return out;
    }

    /**
     * Extract two channels (R, G) from an RGBA NativeImage into an RG8 byte buffer.
     */
    private static ByteBuffer extractTwoChannels(NativeImage img, int ch0, int ch1) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer out = MemoryUtil.memAlloc(w * h * 2);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getColorArgb(x, y);
                out.put((byte) getChannel(argb, ch0));
                out.put((byte) getChannel(argb, ch1));
            }
        }
        out.flip();
        return out;
    }

    /**
     * Extract all four channels from an RGBA NativeImage as RGBA byte buffer.
     */
    private static ByteBuffer extractFourChannels(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        ByteBuffer out = MemoryUtil.memAlloc(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getColorArgb(x, y);
                out.put((byte) ((argb >> 16) & 0xFF)); // R
                out.put((byte) ((argb >> 8) & 0xFF));  // G
                out.put((byte) (argb & 0xFF));          // B
                out.put((byte) ((argb >> 24) & 0xFF)); // A
            }
        }
        out.flip();
        return out;
    }

    /**
     * Clean up all Blender PBR textures (call on resource pack reload / world unload).
     */
    public static void cleanup() {
        for (int texId : TextureTracker.blenderTextureIDs) {
            TextureProxy.destroyTexture(texId);
            TextureTracker.GLID2Texture.remove(texId);
        }
        TextureTracker.blenderPBRTextures.clear();
        TextureTracker.blenderTextureIDs.clear();
    }

    /**
     * Source descriptor for a single PBR channel — the resource to load from
     * and which color channel to extract (-1 = all channels / RGBA).
     */
    public record ChannelSource(Resource resource, int channelIndex) {}
}
