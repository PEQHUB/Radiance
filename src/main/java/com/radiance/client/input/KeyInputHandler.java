package com.radiance.client.input;

import com.radiance.client.RadianceClient;
import com.radiance.client.gui.RadianceSettingsScreen;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.option.Options;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    public static KeyBinding radianceSettingsKey;
    public static KeyBinding offlineModeKey;
    public static KeyBinding lockCameraKey;

    public static void register() {
        radianceSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            Options.KEY_RADIANCE_SETTINGS,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            Options.KEY_CATEGORY_RADIANCE
        ));

        offlineModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.radiance.offline_mode",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            Options.KEY_CATEGORY_RADIANCE
        ));

        lockCameraKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.radiance.lock_camera",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_F5,
            Options.KEY_CATEGORY_RADIANCE
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (radianceSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    if (Options.useUnifiedUI) {
                        MinecraftClient.getInstance().setScreen(new RadianceUnifiedScreen(null));
                    } else {
                        MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(null));
                    }
                }
            }

            // F7: toggle offline mode (NORMAL <-> FREE)
            while (offlineModeKey.wasPressed()) {
                if (client.currentScreen == null && client.world != null) {
                    if (Options.offlineState == 0) {
                        // Enter offline free mode
                        Options.offlineState = 1;
                        Options.frozenDayTimeTicks = client.world.getTimeOfDay() % 24000L;
                        Options.nativeSetOfflineState(1, false);
                        RadianceClient.LOGGER.info("[Offline] Entered FREE mode (time frozen at {})", Options.frozenDayTimeTicks);
                    } else {
                        // Exit offline mode entirely
                        Options.offlineState = 0;
                        Options.frozenDayTimeTicks = -1;
                        Options.nativeSetOfflineState(0, false);
                        Options.nativeResetAccumulation();
                        RadianceClient.LOGGER.info("[Offline] Exited offline mode");
                    }
                }
            }

            // F5: toggle camera lock (FREE <-> ACCUMULATING)
            // Only consumed when in offline mode — vanilla third-person toggle works normally otherwise
            if (Options.offlineState != 0) {
                while (lockCameraKey.wasPressed()) {
                    if (client.currentScreen == null && client.world != null) {
                        if (Options.offlineState == 1) {
                            // Lock camera and start accumulating
                            var camera = client.gameRenderer.getCamera();
                            var pos = camera.getPos();
                            Options.frozenCamX = pos.x;
                            Options.frozenCamY = pos.y;
                            Options.frozenCamZ = pos.z;
                            Options.frozenCamYaw = camera.getYaw();
                            Options.frozenCamPitch = camera.getPitch();
                            Options.offlineState = 2;
                            Options.nativeSetOfflineState(2, false);
                            Options.nativeResetAccumulation();
                            RadianceClient.LOGGER.info("[Offline] Camera locked, accumulation started");
                        } else if (Options.offlineState == 2) {
                            // Unlock camera, return to free mode
                            Options.offlineState = 1;
                            Options.nativeSetOfflineState(1, false);
                            Options.nativeResetAccumulation();
                            RadianceClient.LOGGER.info("[Offline] Camera unlocked, accumulation reset");
                        }
                    }
                }
            }
        });
    }
}
