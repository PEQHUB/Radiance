package com.radiance.client.ui;

import com.radiance.client.option.Options;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;

public record UIStateSnapshot(
    // Player vitals
    float health,
    float maxHealth,
    int food,
    float saturation,
    int armor,
    int air,
    int maxAir,
    float experienceProgress,
    int experienceLevel,

    // Hotbar
    int selectedSlot,
    List<SlotInfo> hotbarSlots,

    // Status effects
    List<EffectInfo> effects,

    // Window
    int scaledWidth,
    int scaledHeight,
    double scaleFactor,
    int framebufferWidth,
    int framebufferHeight,

    // Time
    float tickDelta,
    long gameTimeOfDay,

    // Renderer state
    int offlineState,
    boolean freecamEnabled,
    int frameGenMode,

    // Timestamp
    long captureNanoTime
) {
    public record SlotInfo(String name, int count, float durability, boolean empty) {}
    public record EffectInfo(int rawId, int duration, int amplifier, boolean ambient) {}

    private static final AtomicReference<UIStateSnapshot> current = new AtomicReference<>(empty());

    public static UIStateSnapshot current() {
        return current.get();
    }

    public static void publish() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        ClientPlayerEntity player = client.player;

        float health = 0, maxHealth = 20, saturation = 0;
        int food = 20, armor = 0, air = 300, maxAir = 300;
        float xpProgress = 0;
        int xpLevel = 0, selectedSlot = 0;
        List<SlotInfo> hotbar = Collections.emptyList();
        List<EffectInfo> effects = Collections.emptyList();
        long gameTime = 0;

        if (player != null) {
            health = player.getHealth();
            maxHealth = player.getMaxHealth();
            food = player.getHungerManager().getFoodLevel();
            saturation = player.getHungerManager().getSaturationLevel();
            armor = player.getArmor();
            air = player.getAir();
            maxAir = player.getMaxAir();
            xpProgress = player.experienceProgress;
            xpLevel = player.experienceLevel;
            selectedSlot = player.getInventory().selectedSlot;

            // Snapshot hotbar — flat copy, no mutable references
            hotbar = new java.util.ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.isEmpty()) {
                    hotbar.add(new SlotInfo("", 0, 0f, true));
                } else {
                    hotbar.add(new SlotInfo(
                        stack.getName().getString(),
                        stack.getCount(),
                        stack.isDamageable()
                            ? 1f - (float) stack.getDamage() / stack.getMaxDamage()
                            : 1f,
                        false
                    ));
                }
            }
            hotbar = Collections.unmodifiableList(hotbar);

            // Status effects
            effects = new java.util.ArrayList<>();
            for (StatusEffectInstance effect : player.getStatusEffects()) {
                effects.add(new EffectInfo(
                    net.minecraft.registry.Registries.STATUS_EFFECT.getRawId(effect.getEffectType().value()),
                    effect.getDuration(),
                    effect.getAmplifier(),
                    effect.isAmbient()
                ));
            }
            effects = Collections.unmodifiableList(effects);
        }

        if (client.world != null) {
            gameTime = client.world.getTimeOfDay();
        }

        int sw = 0, sh = 0, fbw = 0, fbh = 0;
        double sf = 1.0;
        if (client.getWindow() != null) {
            sw = client.getWindow().getScaledWidth();
            sh = client.getWindow().getScaledHeight();
            sf = client.getWindow().getScaleFactor();
            fbw = client.getWindow().getFramebufferWidth();
            fbh = client.getWindow().getFramebufferHeight();
        }

        current.set(new UIStateSnapshot(
            health, maxHealth, food, saturation, armor, air, maxAir,
            xpProgress, xpLevel,
            selectedSlot, hotbar,
            effects,
            sw, sh, sf, fbw, fbh,
            client.getRenderTickCounter().getTickDelta(true),
            gameTime,
            Options.offlineState,
            Options.freecamEnabled,
            Options.frameGenMode,
            System.nanoTime()
        ));
    }

    public static UIStateSnapshot empty() {
        return new UIStateSnapshot(
            0, 20, 20, 0, 0, 300, 300,
            0, 0,
            0, Collections.emptyList(),
            Collections.emptyList(),
            0, 0, 1.0, 0, 0,
            0f, 0,
            0, false, 0,
            0
        );
    }
}
