package com.radiance.mixins.vanilla_resource_tracker;

import com.llamalad7.mixinextras.sugar.Local;
import com.radiance.client.proxy.world.BlockModelBridge;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteContentsExt;
import com.radiance.mixin_related.extensions.vanilla_resource_tracker.ISpriteExt;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteLoader;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.lwjgl.system.MemoryUtil.memAddress;

@Mixin(SpriteAtlasTexture.class)
public abstract class SpriteAtlasTextureMixins extends AbstractTextureMixins {

    private static final Logger LOGGER = LoggerFactory.getLogger("SpriteAtlasTexture");

    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/Sprite;upload()V"))
    public void setImageTargetIDBeforeUpload(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci, @Local Sprite sprite) {
        int id = getGlId();
        ((ISpriteExt) sprite).neoVoxelRT$setTargetID(id);
    }

    /**
     * After all sprites are uploaded to the atlas, extract individual sprite pixels
     * and upload them to the C++ texture array system with sequential sprite IDs.
     */
    @Inject(method = "upload(Lnet/minecraft/client/texture/SpriteLoader$StitchResult;)V",
        at = @At("RETURN"))
    public void extractSpritesForTextureArrays(SpriteLoader.StitchResult stitchResult,
        CallbackInfo ci) {
        // Only process the block atlas, not mob/particle atlases
        SpriteAtlasTexture self = (SpriteAtlasTexture) (Object) this;
        Identifier atlasId = self.getId();
        if (!atlasId.getPath().contains("blocks")) {
            return;
        }

        Map<Identifier, Sprite> regions = stitchResult.regions();
        if (regions == null || regions.isEmpty()) return;

        LOGGER.info("[TextureArrays] Processing block atlas: {} sprites", regions.size());

        // Phase 1: Assign sequential IDs — 1 layer per sprite (static only).
        // Animated frames are NOT stored as consecutive layers; instead, Java re-uploads
        // the current animation frame each tick to the sprite's single layer.
        Map<Identifier, Integer> idMap = new HashMap<>();
        int nextLayer = 0;
        int spriteSize = 0; // will be set from first sprite

        for (Map.Entry<Identifier, Sprite> entry : regions.entrySet()) {
            Identifier id = entry.getKey();
            Sprite sprite = entry.getValue();
            SpriteContents contents = sprite.getContents();

            int w = contents.getWidth();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;

            if (spriteSize == 0) {
                spriteSize = w;
            }

            // 1 layer per sprite regardless of animation frame count
            idMap.put(id, nextLayer);
            nextLayer += 1;
        }

        // Store mapping for block model serialization
        BlockModelBridge.spriteIdMap = idMap;
        BlockModelBridge.spriteArraySize = nextLayer;

        // Check for size mismatches
        int sizeOk = 0, sizeBad = 0;
        for (Map.Entry<Identifier, Sprite> entry : regions.entrySet()) {
            SpriteContents c = entry.getValue().getContents();
            int cw = c.getWidth(), ch = c.getHeight();
            if (cw == spriteSize && ch == spriteSize) {
                sizeOk++;
            } else {
                sizeBad++;
                if (sizeBad <= 10) {
                    LOGGER.warn("[TextureArrays] Size mismatch: {} is {}x{} (expected {}x{})",
                        entry.getKey(), cw, ch, spriteSize, spriteSize);
                }
            }
        }
        LOGGER.info("[TextureArrays] Assigned {} sequential IDs ({} layers, {}x{} sprites, {} size-ok, {} size-bad)",
            idMap.size(), nextLayer, spriteSize, spriteSize, sizeOk, sizeBad);

        // Clear previous animated frame cache
        BlockModelBridge.clearAnimatedFrameCache();

        // Phase 1.5: Upload sprite UV bounds for atlas→[0,1] UV conversion in C++ mesher
        // SpriteUVBounds = 20 bytes: spriteId(uint16) + pad(uint16) + minU(float) + maxU(float) + minV(float) + maxV(float)
        int BOUNDS_SIZE = 20;
        ByteBuffer boundsBuf = ByteBuffer.allocateDirect(idMap.size() * BOUNDS_SIZE)
            .order(ByteOrder.nativeOrder());
        int boundsCount = 0;
        for (Map.Entry<Identifier, Sprite> entry : regions.entrySet()) {
            Sprite sprite = entry.getValue();
            Integer baseLayer = idMap.get(entry.getKey());
            if (baseLayer == null) continue;

            int offset = boundsCount * BOUNDS_SIZE;
            boundsBuf.putShort(offset, (short) baseLayer.intValue()); // spriteId = baseLayer
            boundsBuf.putShort(offset + 2, (short) 0); // padding
            boundsBuf.putFloat(offset + 4, sprite.getMinU());
            boundsBuf.putFloat(offset + 8, sprite.getMaxU());
            boundsBuf.putFloat(offset + 12, sprite.getMinV());
            boundsBuf.putFloat(offset + 16, sprite.getMaxV());
            boundsCount++;
        }
        BlockModelBridge.nativeUploadSpriteBounds(memAddress(boundsBuf), boundsCount);
        LOGGER.info("[TextureArrays] Uploaded {} sprite UV bounds", boundsCount);

        // Phase 2: Extract pixel data — upload frame 0 to C++, cache all frames for animated sprites
        int uploaded = 0;
        int animatedCount = 0;
        for (Map.Entry<Identifier, Sprite> entry : regions.entrySet()) {
            Identifier id = entry.getKey();
            Sprite sprite = entry.getValue();
            SpriteContents contents = sprite.getContents();

            int w = contents.getWidth();
            int h = contents.getHeight();
            NativeImage img = ((ISpriteContentsExt) contents).neoVoxelRT$getImage();
            if (img == null) continue;

            Integer baseLayer = idMap.get(id);
            if (baseLayer == null) continue;

            int imgHeight = img.getHeight();
            int frameCount = Math.max(1, imgHeight / h);

            int bytesPerPixel = 4; // RGBA
            int frameBytes = w * h * bytesPerPixel;

            if (frameCount > 1) {
                // Animated sprite: extract ALL frames into byte arrays for per-tick re-upload
                byte[][] frames = new byte[frameCount][];
                for (int frame = 0; frame < frameCount; frame++) {
                    byte[] frameData = new byte[frameBytes];
                    int yOffset = frame * h;
                    int idx = 0;
                    for (int py = 0; py < h; py++) {
                        for (int px = 0; px < w; px++) {
                            int argb = img.getColorArgb(px, yOffset + py);
                            frameData[idx++] = (byte) ((argb >> 16) & 0xFF); // R
                            frameData[idx++] = (byte) ((argb >> 8) & 0xFF);  // G
                            frameData[idx++] = (byte) (argb & 0xFF);         // B
                            frameData[idx++] = (byte) ((argb >> 24) & 0xFF); // A
                        }
                    }
                    frames[frame] = frameData;
                }
                BlockModelBridge.registerAnimatedSprite(baseLayer, frames, w);
                animatedCount++;

                // Upload frame 0 as the initial layer content
                ByteBuffer frameBuf = ByteBuffer.allocateDirect(frameBytes).order(ByteOrder.nativeOrder());
                frameBuf.put(frames[0]);
                frameBuf.flip();
                BlockModelBridge.nativeUploadSpritePixels(
                    memAddress(frameBuf), baseLayer, w, h, 0, 0);
            } else {
                // Static sprite: extract and upload single frame
                ByteBuffer frameBuf = ByteBuffer.allocateDirect(frameBytes).order(ByteOrder.nativeOrder());
                for (int py = 0; py < h; py++) {
                    for (int px = 0; px < w; px++) {
                        int argb = img.getColorArgb(px, py);
                        frameBuf.put((byte) ((argb >> 16) & 0xFF)); // R
                        frameBuf.put((byte) ((argb >> 8) & 0xFF));  // G
                        frameBuf.put((byte) (argb & 0xFF));         // B
                        frameBuf.put((byte) ((argb >> 24) & 0xFF)); // A
                    }
                }
                frameBuf.flip();
                BlockModelBridge.nativeUploadSpritePixels(
                    memAddress(frameBuf), baseLayer, w, h, 0, 0);
            }
            uploaded++;
        }

        LOGGER.info("[TextureArrays] Uploaded {} sprite layers ({} animated) to C++",
            uploaded, animatedCount);

        // Diagnostic: sample center pixel of a few key sprites
        for (Map.Entry<Identifier, Sprite> entry : regions.entrySet()) {
            String name = entry.getKey().toString();
            if (name.contains("oak_log") && !name.contains("top") && !name.contains("stripped")
                || name.contains("stone") && !name.contains("brick") && !name.contains("cut")
                    && !name.contains("smooth") && !name.contains("slab") && !name.contains("stair")
                    && !name.contains("sand") && !name.contains("end") && !name.contains("glow")
                    && !name.contains("black") && !name.contains("red") && !name.contains("lodge")
                || name.equals("minecraft:block/dirt")
                || name.equals("minecraft:block/grass_block_top")) {
                SpriteContents c = entry.getValue().getContents();
                NativeImage img = ((ISpriteContentsExt) c).neoVoxelRT$getImage();
                if (img != null) {
                    int cx = c.getWidth() / 2, cy = c.getHeight() / 2;
                    int argb = img.getColorArgb(cx, cy);
                    int a = (argb >> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                    Integer sid = idMap.get(entry.getKey());
                    LOGGER.info("[TextureArrays] Pixel sample: {} (spriteId={}) center=ARGB({},{},{},{}) size={}x{}",
                        name, sid, a, r, g, b, c.getWidth(), c.getHeight());
                }
            }
        }

        // Phase 3: Finalize — tell C++ to create the arrays
        BlockModelBridge.nativeFinalize(nextLayer, spriteSize);
    }
}
