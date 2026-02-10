package com.radiance.client.input;

import com.radiance.client.gui.RadianceSettingsScreen;
import com.radiance.client.option.Options;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyInputHandler {

    public static KeyBinding radianceSettingsKey;

    public static void register() {
        radianceSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            Options.KEY_RADIANCE_SETTINGS,
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            Options.KEY_CATEGORY_RADIANCE
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (radianceSettingsKey.wasPressed()) {
                if (client.currentScreen == null) {
                    MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(null));
                }
            }
        });
    }
}
