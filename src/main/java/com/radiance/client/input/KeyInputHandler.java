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
    public static KeyBinding offlineDenoisedKey;
    public static KeyBinding offlineGroundTruthKey;
    public static KeyBinding offlineTabPeekKey;

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

        offlineDenoisedKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.radiance.offline_denoised",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_D,
            Options.KEY_CATEGORY_RADIANCE
        ));

        offlineGroundTruthKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.radiance.offline_ground_truth",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            Options.KEY_CATEGORY_RADIANCE
        ));

        offlineTabPeekKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.radiance.offline_tab_peek",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
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

            // Tab: peek settings menu while held (offline mode only)
            while (offlineTabPeekKey.wasPressed()) {
                if (Options.offlineState != 0 && client.currentScreen == null) {
                    RadianceUnifiedScreen.openedViaTab = true;
                    MinecraftClient.getInstance().setScreen(new RadianceUnifiedScreen(null));
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

            // G: toggle ground truth preset (global — saves/restores individual toggles)
            while (offlineGroundTruthKey.wasPressed()) {
                if (client.currentScreen == null) {
                    Options.offlineGroundTruth = !Options.offlineGroundTruth;
                    if (Options.offlineGroundTruth) {
                        applyGroundTruthPreset();
                    } else {
                        restoreGroundTruthPreset();
                    }
                    Options.nativeSetOfflineGroundTruth(Options.offlineGroundTruth, false);
                    if (Options.offlineState == 2) {
                        Options.nativeResetAccumulation();
                    }
                    RadianceClient.LOGGER.info("[Radiance] Ground truth: {}", Options.offlineGroundTruth);
                }
            }

            // D: cycle denoised mode (offline only)
            while (offlineDenoisedKey.wasPressed()) {
                if (Options.offlineState != 0 && client.currentScreen == null) {
                    Options.offlineDenoised = (Options.offlineDenoised + 1) % 3;
                    Options.nativeSetOfflineDenoised(Options.offlineDenoised, false);
                    if (Options.offlineState == 2) {
                        Options.nativeResetAccumulation();
                    }
                    RadianceClient.LOGGER.info("[Offline] Denoised mode: {}", Options.offlineDenoised);
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

    // Ground truth preset: saved values for restore
    private static boolean savedBeerLaw, savedNoEmissionClamp, savedPhysicalSun;
    private static boolean savedNoHandAmbient;
    private static boolean savedSimplifiedIndirect, savedSharcEnabled, savedNoiseLOD;
    private static boolean savedAreaLights, savedDisableRR, savedDisableClamp;

    /** Apply ground truth preset — save current values, set all to GT values. */
    public static void applyGroundTruthPreset() {
        // Save current values
        savedBeerLaw = Options.beerLawShadows;
        savedNoEmissionClamp = Options.noEmissionClamp;
        savedPhysicalSun = Options.physicalSunDisk;
        savedNoHandAmbient = Options.noHandAmbient;
        savedSimplifiedIndirect = Options.simplifiedIndirect;
        savedSharcEnabled = Options.sharcEnabled;
        savedNoiseLOD = Options.noiseLOD;
        savedAreaLights = Options.areaLightsEnabled;
        savedDisableRR = Options.offlineDisableRR;
        savedDisableClamp = Options.offlineDisableClamp;

        // Apply ground truth values
        Options.beerLawShadows = true;
        Options.noEmissionClamp = true;
        Options.physicalSunDisk = true;
        Options.noHandAmbient = true;
        Options.simplifiedIndirect = false;
        Options.sharcEnabled = false;
        Options.noiseLOD = false;
        Options.areaLightsEnabled = false;
        Options.offlineDisableRR = true;
        Options.offlineDisableClamp = true;

        // Sync all to C++
        Options.nativeSetBeerLawShadows(true, false);
        Options.nativeSetNoEmissionClamp(true, false);
        Options.nativeSetPhysicalSunDisk(true, false);
        Options.nativeSetNoHandAmbient(true, false);
        Options.setSimplifiedIndirect(false, false);
        Options.setSharcEnabled(false, false);
        Options.setNoiseLOD(false, false);
        Options.setAreaLightsEnabled(false, false);
        Options.nativeSetOfflineDisableRR(true, false);
        Options.nativeSetOfflineDisableClamp(true, false);
    }

    /** Restore values saved before ground truth was applied. */
    public static void restoreGroundTruthPreset() {
        Options.beerLawShadows = savedBeerLaw;
        Options.noEmissionClamp = savedNoEmissionClamp;
        Options.physicalSunDisk = savedPhysicalSun;
        Options.noHandAmbient = savedNoHandAmbient;
        Options.simplifiedIndirect = savedSimplifiedIndirect;
        Options.sharcEnabled = savedSharcEnabled;
        Options.noiseLOD = savedNoiseLOD;
        Options.areaLightsEnabled = savedAreaLights;
        Options.offlineDisableRR = savedDisableRR;
        Options.offlineDisableClamp = savedDisableClamp;

        Options.nativeSetBeerLawShadows(savedBeerLaw, false);
        Options.nativeSetNoEmissionClamp(savedNoEmissionClamp, false);
        Options.nativeSetPhysicalSunDisk(savedPhysicalSun, false);
        Options.nativeSetNoHandAmbient(savedNoHandAmbient, false);
        Options.setSimplifiedIndirect(savedSimplifiedIndirect, false);
        Options.setSharcEnabled(savedSharcEnabled, false);
        Options.setNoiseLOD(savedNoiseLOD, false);
        Options.setAreaLightsEnabled(savedAreaLights, false);
        Options.nativeSetOfflineDisableRR(savedDisableRR, false);
        Options.nativeSetOfflineDisableClamp(savedDisableClamp, false);
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
