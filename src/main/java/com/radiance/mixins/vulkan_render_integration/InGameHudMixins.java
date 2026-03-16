package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudMixins {

    @Inject(method = "render", at = @At("TAIL"))
    private void renderOfflineOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Options.offlineState == 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();

        if (Options.offlineState == 2) {
            // Accumulating: show sample count with mode info
            int samples = 0;
            try { samples = Options.nativeGetAccumFrameCount(); } catch (UnsatisfiedLinkError ignored) {}
            String text;
            if (Options.offlineGroundTruth) {
                text = "GROUND TRUTH | Samples: " + samples;
            } else if (Options.offlineDenoised == 2) {
                text = "Frame " + samples + " (DLSS-RR temporal)";
            } else if (Options.offlineDenoised == 1) {
                text = "Samples: " + samples + " (DLSS + Welford)";
            } else if (Options.offlineNativeRes) {
                text = "Samples: " + samples + " (native)";
            } else {
                text = "Samples: " + samples + " (render-res)";
            }
            int textWidth = client.textRenderer.getWidth(text);
            int color = Options.offlineGroundTruth ? 0xFF8800 : 0x00FF88;
            context.drawTextWithShadow(client.textRenderer, text, (width - textWidth) / 2, 4, color);

            // Time display
            long ticks = Options.frozenDayTimeTicks;
            if (ticks >= 0) {
                int hours = (int) (((ticks + 6000) % 24000) / 1000);
                int minutes = (int) ((((ticks + 6000) % 24000) % 1000) * 60 / 1000);
                String time = String.format("%02d:%02d", hours, minutes);
                int tw = client.textRenderer.getWidth(time);
                context.drawTextWithShadow(client.textRenderer, time, (width - tw) / 2, 16, 0xAAAAAA);
            }
        } else if (Options.offlineState == 1) {
            // Free mode: show instructions with mode info
            StringBuilder sb = new StringBuilder("OFFLINE [O:settings Tab:peek G:ground truth D:denoised] - F5 to lock");
            if (Options.offlineGroundTruth) {
                sb.append(" [GT]");
            }
            if (Options.offlineNativeRes) {
                sb.append(" [Native]");
            }
            if (Options.offlineDenoised > 0) {
                sb.append(" [Denoised:").append(Options.offlineDenoised).append("]");
            }
            String text = sb.toString();
            int textWidth = client.textRenderer.getWidth(text);
            context.drawTextWithShadow(client.textRenderer, text, (width - textWidth) / 2, 4, 0xFFFF00);
        }
    }
}
