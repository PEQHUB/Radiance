package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.*;
import com.radiance.client.material.EntityMaterial;
import com.radiance.client.material.MaterialRegistry;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class EntityMaterialPopulator implements ContentPopulator {

    // Group categories for UI organization
    private static final EntityMaterial.Category[][] GROUPS = {
        // Passive
        {EntityMaterial.Category.PASSIVE, EntityMaterial.Category.VILLAGER,
         EntityMaterial.Category.PLAYER, EntityMaterial.Category.ARMOR_STAND},
        // Hostile
        {EntityMaterial.Category.HOSTILE, EntityMaterial.Category.ARTHROPOD,
         EntityMaterial.Category.UNDEAD, EntityMaterial.Category.BLAZE,
         EntityMaterial.Category.ENDERMAN, EntityMaterial.Category.BREEZE},
        // Aquatic
        {EntityMaterial.Category.AQUATIC, EntityMaterial.Category.GUARDIAN,
         EntityMaterial.Category.STRIDER},
        // Boss & Special
        {EntityMaterial.Category.ENDER_DRAGON, EntityMaterial.Category.WITHER,
         EntityMaterial.Category.WARDEN, EntityMaterial.Category.GOLEM,
         EntityMaterial.Category.GHAST, EntityMaterial.Category.SLIME,
         EntityMaterial.Category.SHULKER},
    };

    private static final String[] GROUP_NAMES = {
        "Passive & Humanoid", "Hostile & Undead", "Aquatic & Nether", "Boss & Special"
    };

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        // Global entity normals toggle
        SettingsSection globalSection = panel.addSection("Entity Normals");
        ButtonWidget normalsToggle = ButtonWidget.builder(
            Text.literal("Entity Normal Maps: " + (Options.entityNormalsEnabled ? "ON" : "OFF")),
            btn -> {
                Options.setEntityNormalsEnabled(!Options.entityNormalsEnabled, true);
                screen.refreshContent();
            })
            .width(150).build();
        globalSection.addButton(normalsToggle);

        // Per-group sections
        for (int g = 0; g < GROUPS.length; g++) {
            SettingsSection section = panel.addSection(GROUP_NAMES[g]);

            for (EntityMaterial.Category cat : GROUPS[g]) {
                int ord = cat.getOrdinal();
                String label = formatCategoryName(cat);

                // Normal Strength slider (only when entity normals enabled)
                if (Options.entityNormalsEnabled) {
                    section.addSlider(
                        new ResettableSliderWidget(0, 0, 150, 20,
                            0, 200, Options.materialNormalStrength[ord], 100,
                            v -> getGenericValueText(Text.literal(label + " Normal"),
                                Text.literal(v + "%")),
                            v -> { Options.materialNormalStrength[ord] = v;
                                   MaterialRegistry.markDirty(); })
                    );
                }

                // Roughness + Metallic pair
                section.addTwoSliders(
                    new ResettableSliderWidget(0, 0, 150, 20,
                        0, 100, Options.materialRoughness[ord], 80,
                        v -> getGenericValueText(Text.literal(label + " Rough"), Text.literal(v + "%")),
                        v -> { Options.materialRoughness[ord] = v; MaterialRegistry.markDirty(); }),
                    new ResettableSliderWidget(0, 0, 150, 20,
                        0, 1000, Options.materialMetallic[ord], 0,
                        v -> getGenericValueText(Text.literal(label + " Metal"), Text.literal(String.format("%.1f%%", v / 10f))),
                        v -> { Options.materialMetallic[ord] = v; MaterialRegistry.markDirty(); })
                );

                // Subsurface + IOR pair
                section.addTwoSliders(
                    new ResettableSliderWidget(0, 0, 150, 20,
                        0, 1000, Options.materialSubsurface[ord], 0,
                        v -> getGenericValueText(Text.literal(label + " SSS"), Text.literal(String.format("%.1f%%", v / 10f))),
                        v -> { Options.materialSubsurface[ord] = v; MaterialRegistry.markDirty(); }),
                    new ResettableSliderWidget(0, 0, 150, 20,
                        1000, 3000, Options.materialIOR[ord], 1500,
                        v -> getGenericValueText(Text.literal(label + " IOR"), Text.literal(String.format("%.3f", v / 1000f))),
                        v -> { Options.materialIOR[ord] = v; MaterialRegistry.markDirty(); })
                );
            }
        }
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        List<UnifiedSearchOverlay.SearchEntry> entries = new ArrayList<>();
        entries.add(new UnifiedSearchOverlay.SearchEntry("Entity Materials", category, nodeId, false));
        entries.add(new UnifiedSearchOverlay.SearchEntry("Entity Normal Maps", category, nodeId, false));
        for (EntityMaterial.Category cat : EntityMaterial.Category.values()) {
            String name = formatCategoryName(cat);
            entries.add(new UnifiedSearchOverlay.SearchEntry(name + " Roughness", category, nodeId, false));
            entries.add(new UnifiedSearchOverlay.SearchEntry(name + " Metallic", category, nodeId, false));
            entries.add(new UnifiedSearchOverlay.SearchEntry(name + " Normal Strength", category, nodeId, false));
        }
        return entries;
    }

    private static String formatCategoryName(EntityMaterial.Category cat) {
        String name = cat.name();
        // PASSIVE → Passive, ENDER_DRAGON → Ender Dragon
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                sb.append(' ');
                capitalize = true;
            } else {
                sb.append(capitalize ? Character.toUpperCase(c) : Character.toLowerCase(c));
                capitalize = false;
            }
        }
        return sb.toString();
    }

}
