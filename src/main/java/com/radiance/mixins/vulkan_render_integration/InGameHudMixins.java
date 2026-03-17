package com.radiance.mixins.vulkan_render_integration;

import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.glfw.GLFW;
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

        // Screenshot suppression: skip overlay when F2 is pressed (so screenshot is clean)
        // Also supports programmatic suppression via suppressHudOverlay flag
        if (Options.suppressHudOverlay) {
            Options.suppressHudOverlay = false;
            return;
        }
        long windowHandle = client.getWindow().getHandle();
        if (GLFW.glfwGetKey(windowHandle, GLFW.GLFW_KEY_F2) == GLFW.GLFW_PRESS) {
            return; // F2 held — screenshot being taken, skip overlay
        }
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        var renderer = client.textRenderer;

        if (Options.offlineState == 2) {
            // ═══════ ACCUMULATING MODE ═══════
            // Bottom-left corner, 8px margin
            int x = 8;
            int lineHeight = 11;

            // Compute lines bottom-up (we know how many lines, draw from bottom)
            int samples = 0;
            try { samples = Options.nativeGetAccumFrameCount(); } catch (UnsatisfiedLinkError ignored) {}

            // Mode label
            String modeLabel;
            int modeColor;
            if (Options.offlineGroundTruth) {
                modeLabel = "GROUND TRUTH";
                modeColor = 0xFFAA00; // orange
            } else if (Options.offlineDenoised == 2) {
                modeLabel = "DLSS-RR Temporal";
                modeColor = 0x00FF88; // cyan
            } else if (Options.offlineDenoised == 1) {
                modeLabel = "DLSS + Welford";
                modeColor = 0x00FF88;
            } else {
                modeLabel = "Raw";
                modeColor = 0x00FF88;
            }

            // Elapsed time
            long elapsedNanos = System.nanoTime() - Options.accumStartTimeNanos;
            String elapsed = formatElapsedTime(elapsedNanos);

            // Stats line: "Samples: N | Xm Xs | N bounces"
            String statsLine = "Samples: " + samples + " | " + elapsed + " | " + Options.offlineBounces + " bounces";

            // Camera line (only when DOF active: fStop < 22)
            boolean dofActive = Options.fStop < 21.9f;
            String cameraLine = null;
            if (dofActive) {
                String focusLabel = "[" + Options.FOCUS_MODE_NAMES[Math.min(Options.focusMode, 2)] + "] ";
                cameraLine = focusLabel + "f/" + String.format("%.1f", Options.fStop)
                    + " " + Options.focalLengthMM + "mm"
                    + " | Focus: " + Options.offlineFocalDistance + " blocks";
            }

            // Game time
            String timeLine = null;
            long ticks = Options.frozenDayTimeTicks;
            if (ticks >= 0) {
                int hours = (int) (((ticks + 6000) % 24000) / 1000);
                int minutes = (int) ((((ticks + 6000) % 24000) % 1000) * 60 / 1000);
                timeLine = String.format("%02d:%02d", hours, minutes);
            }

            // Count total lines and compute Y start
            int totalLines = 2; // mode + stats always
            if (cameraLine != null) totalLines++;
            if (timeLine != null) totalLines++;
            int y = height - 8 - (totalLines * lineHeight);

            // Draw
            context.drawTextWithShadow(renderer, modeLabel, x, y, modeColor);
            y += lineHeight;
            context.drawTextWithShadow(renderer, statsLine, x, y, 0xAAAAAA);
            y += lineHeight;
            if (cameraLine != null) {
                context.drawTextWithShadow(renderer, cameraLine, x, y, 0xAAAAAA);
                y += lineHeight;
            }
            if (timeLine != null) {
                context.drawTextWithShadow(renderer, timeLine, x, y, 0x888888);
            }

        } else if (Options.offlineState == 1) {
            // ═══════ FREE MODE ═══════

            // Top-center: minimal mode indicator
            String topText = "OFFLINE \u2014 F5 to render";
            int topWidth = renderer.getWidth(topText);
            context.drawTextWithShadow(renderer, topText, (width - topWidth) / 2, 4, 0xFFFF00);

            // Bottom-left: camera settings summary
            int x = 8;
            int lineHeight = 11;

            // Game time
            String timeStr = null;
            long ticks = Options.frozenDayTimeTicks;
            if (ticks >= 0) {
                int hours = (int) (((ticks + 6000) % 24000) / 1000);
                int minutes = (int) ((((ticks + 6000) % 24000) % 1000) * 60 / 1000);
                timeStr = String.format("%02d:%02d", hours, minutes);
            }

            int totalLines = 4;
            if (timeStr != null) totalLines++;
            int y = height - 8 - (totalLines * lineHeight);

            // Focus mode indicator + camera info
            boolean dofActive = Options.fStop < 21.9f;
            String focusModeTag = "[" + Options.FOCUS_MODE_NAMES[Math.min(Options.focusMode, 2)] + "] ";
            int focusModeColor;
            switch (Options.focusMode) {
                case 1: focusModeColor = 0xFFFF00; break;  // AF-S: yellow
                case 2: focusModeColor = 0x00FFCC; break;  // AF-C: cyan
                default: focusModeColor = 0xCCCCCC; break; // MF: white/grey
            }

            String cameraInfo;
            if (Options.focusMode == 1) {
                cameraInfo = focusModeTag + "Click to focus (Esc: cancel)";
            } else if (dofActive) {
                cameraInfo = focusModeTag + "f/" + String.format("%.1f", Options.fStop)
                    + " " + Options.focalLengthMM + "mm"
                    + " | Focus: " + Options.offlineFocalDistance + " blocks";
            } else {
                cameraInfo = focusModeTag + Options.focalLengthMM + "mm | Pinhole";
            }
            context.drawTextWithShadow(renderer, cameraInfo, x, y, focusModeColor);
            y += lineHeight;

            // Render info
            StringBuilder renderInfo = new StringBuilder();
            renderInfo.append(Options.offlineBounces).append(" bounces | ");
            String[] modeNames = {"Raw", "DLSS + Welford", "DLSS-RR Temporal"};
            renderInfo.append(modeNames[Math.min(Options.offlineDenoised, 2)]);
            if (Options.offlineGroundTruth) {
                renderInfo.append(" | Ground Truth");
            }
            context.drawTextWithShadow(renderer, renderInfo.toString(), x, y, 0xCCCCCC);
            y += lineHeight;

            // Game time + scroll hint
            if (timeStr != null) {
                String timeLine = timeStr + "  (Shift+Scroll: time | Scroll: focus)";
                context.drawTextWithShadow(renderer, timeLine, x, y, 0x888888);
                y += lineHeight;
            }

            // Camera mode indicator
            String camMode = Options.freecamEnabled ? "[Freecam]" : "[Player]";
            context.drawTextWithShadow(renderer, camMode, x, y, 0x888888);

            // Focus toast (centered, 30% from top)
            if (Options.focusToastMessage != null) {
                if (System.currentTimeMillis() < Options.focusToastExpireMs) {
                    int toastWidth = renderer.getWidth(Options.focusToastMessage);
                    int toastY = (int)(height * 0.3);
                    context.drawTextWithShadow(renderer, Options.focusToastMessage,
                        (width - toastWidth) / 2, toastY, Options.focusToastColor);
                } else {
                    Options.focusToastMessage = null;
                }
            }
        }
    }

    private static String formatElapsedTime(long nanos) {
        long totalSeconds = nanos / 1_000_000_000L;
        if (totalSeconds < 60) {
            return "0m " + totalSeconds + "s";
        }
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes < 60) {
            return minutes + "m " + String.format("%02d", seconds) + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        return hours + "h " + String.format("%02d", minutes) + "m";
    }
}
