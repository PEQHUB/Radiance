package com.radiance.client.input;

import com.radiance.client.RadianceClient;
import com.radiance.client.gui.MaterialsSettingsScreen;
import com.radiance.client.gui.RadianceSettingsScreen;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.option.Options;
import com.radiance.client.util.MaterialBlock;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    public static KeyBinding radianceSettingsKey;
    public static KeyBinding offlineModeKey;
    public static KeyBinding lockCameraKey;
    public static KeyBinding materialPickerKey;

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

        materialPickerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            Options.KEY_MATERIAL_PICKER,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
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
                        Options.offlineState = 1;
                        Options.frozenDayTimeTicks = client.world.getTimeOfDay() % 24000L;
                        Options.nativeSetOfflineState(1, false);
                        RadianceClient.LOGGER.info("[Offline] Entered FREE mode (time frozen at {})", Options.frozenDayTimeTicks);
                    } else {
                        Options.offlineState = 0;
                        Options.frozenDayTimeTicks = -1;
                        Options.nativeSetOfflineState(0, false);
                        Options.nativeResetAccumulation();
                        RadianceClient.LOGGER.info("[Offline] Exited offline mode");
                    }
                }
            }

            // F5: toggle camera lock (FREE <-> ACCUMULATING)
            while (lockCameraKey.wasPressed()) {
                if (Options.offlineState != 0 && client.currentScreen == null && client.world != null) {
                    if (Options.offlineState == 1) {
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
                        Options.offlineState = 1;
                        Options.nativeSetOfflineState(1, false);
                        Options.nativeResetAccumulation();
                        RadianceClient.LOGGER.info("[Offline] Camera unlocked, accumulation reset");
                    }
                }
            }

            // M: material picker
            while (materialPickerKey.wasPressed()) {
                if (client.currentScreen == null && client.world != null) {
                    MaterialBlock mb = getTargetMaterialBlock(client);
                    if (mb != null) {
                        MaterialsSettingsScreen.setCurrentBlockIndex(mb.ordinal());
                        client.setScreen(new MaterialsSettingsScreen(null));
                    }
                }
            }
        });
    }

    private static MaterialBlock getTargetMaterialBlock(MinecraftClient client) {
        if (client.world == null || client.player == null) return null;

        // 1. Try the normal crosshair target (solid blocks)
        if (client.crosshairTarget instanceof BlockHitResult blockHit
                && blockHit.getType() != HitResult.Type.MISS) {
            BlockState state = client.world.getBlockState(blockHit.getBlockPos());
            MaterialBlock mb = MaterialBlock.fromBlock(state.getBlock());
            if (mb != null) return mb;
        }

        // 2. Fallback: raycast with fluid handling for water/lava
        double reach = client.player.getBlockInteractionRange();
        HitResult fluidHit = client.player.raycast(reach, 1.0f, true);
        if (fluidHit instanceof BlockHitResult fluidBlockHit
                && fluidBlockHit.getType() != HitResult.Type.MISS) {
            BlockState state = client.world.getBlockState(fluidBlockHit.getBlockPos());
            MaterialBlock mb = MaterialBlock.fromBlock(state.getBlock());
            if (mb != null) return mb;
        }

        return null;
    }
}
