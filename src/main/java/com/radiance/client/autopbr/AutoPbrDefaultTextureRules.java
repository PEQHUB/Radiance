package com.radiance.client.autopbr;

import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import net.minecraft.util.Identifier;

final class AutoPbrDefaultTextureRules {
    private static final int MAX_SPRITES = 4096;
    private static final DefaultRule[] RULES = new DefaultRule[] {
        rule(0.62f, 1.31f, 0.0f, "block/ice", "block/frosted_ice_0", "block/frosted_ice_1",
            "block/frosted_ice_2", "block/frosted_ice_3"),
        rule(0.42f, 1.31f, 0.0f, "block/packed_ice", "block/blue_ice"),
        rule(0.92f, 1.52f, 0.0f, "block/glass", "block/glass_pane_top",
            "block/white_stained_glass", "block/orange_stained_glass",
            "block/magenta_stained_glass", "block/light_blue_stained_glass",
            "block/yellow_stained_glass", "block/lime_stained_glass",
            "block/pink_stained_glass", "block/gray_stained_glass",
            "block/light_gray_stained_glass", "block/cyan_stained_glass",
            "block/purple_stained_glass", "block/blue_stained_glass",
            "block/brown_stained_glass", "block/green_stained_glass",
            "block/red_stained_glass", "block/black_stained_glass",
            "block/white_stained_glass_pane_top", "block/orange_stained_glass_pane_top",
            "block/magenta_stained_glass_pane_top", "block/light_blue_stained_glass_pane_top",
            "block/yellow_stained_glass_pane_top", "block/lime_stained_glass_pane_top",
            "block/pink_stained_glass_pane_top", "block/gray_stained_glass_pane_top",
            "block/light_gray_stained_glass_pane_top", "block/cyan_stained_glass_pane_top",
            "block/purple_stained_glass_pane_top", "block/blue_stained_glass_pane_top",
            "block/brown_stained_glass_pane_top", "block/green_stained_glass_pane_top",
            "block/red_stained_glass_pane_top", "block/black_stained_glass_pane_top"),
        rule(0.34f, 1.48f, 0.0f, "block/honey_block_side", "block/honey_block_top",
            "block/honey_block_bottom"),
        rule(0.50f, 1.36f, 0.0f, "block/slime_block"),
        rule(0.18f, 1.58f, 0.0f, "block/emerald_block"),
        rule(0.04f, 1.58f, 0.0f, "block/emerald_ore", "block/deepslate_emerald_ore")
    };

    private AutoPbrDefaultTextureRules() {
    }

    static int apply() {
        int applied = 0;
        int count = Math.min(TextureArrayBridge.sortedSpriteIds.size(), MAX_SPRITES);
        for (int spriteId = 0; spriteId < count; spriteId++) {
            if (AutoPbrTextureCatalog.isPhysicalWaterSprite(spriteId)) continue;
            DefaultRule rule = ruleForSprite(spriteId);
            if (rule == null) continue;
            AutoPbrTextureRules.writeDefaultRule(spriteId,
                rule.transmission(),
                rule.ior(),
                rule.metallic(),
                rule.f0(),
                rule.emission());
            applied++;
        }
        return applied;
    }

    static DefaultRule ruleForSprite(int spriteId) {
        if (spriteId < 0 || spriteId >= TextureArrayBridge.sortedSpriteIds.size()) return null;
        if (AutoPbrTextureCatalog.isPhysicalWaterSprite(spriteId)) return null;
        Identifier id = TextureArrayBridge.sortedSpriteIds.get(spriteId);
        if (id == null) return null;
        for (DefaultRule rule : RULES) {
            for (String sprite : rule.sprites()) {
                Identifier ruleId = parseSpriteId(sprite);
                if (ruleId != null && ruleId.equals(id)) return rule;
            }
        }
        return null;
    }

    private static Identifier parseSpriteId(String sprite) {
        if (sprite == null || sprite.isBlank()) return null;
        return sprite.indexOf(':') >= 0
            ? Identifier.tryParse(sprite)
            : Identifier.ofVanilla(sprite);
    }

    private static DefaultRule rule(float transmission, float ior, float metallic, String... sprites) {
        return new DefaultRule(transmission, ior, metallic, sprites);
    }

    private static float f0FromIor(float ior) {
        float ratio = (ior - 1.0f) / (ior + 1.0f);
        return ratio * ratio;
    }

    record DefaultRule(float transmission, float ior, float metallic, String[] sprites) {
        float f0() {
            return f0FromIor(ior);
        }

        float emission() {
            return 0.0f;
        }
    }

}
