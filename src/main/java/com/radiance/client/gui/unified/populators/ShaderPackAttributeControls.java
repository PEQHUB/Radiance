package com.radiance.client.gui.unified.populators;

import com.radiance.client.gui.AttributeWidgetUtil;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;

final class ShaderPackAttributeControls {

    private ShaderPackAttributeControls() {
    }

    static boolean addSection(ContentPanelWidget panel, String titleKey, Predicate<String> nameFilter) {
        Module module = Pipeline.getRayTracingModule();
        if (module == null) {
            return false;
        }

        List<AttributeConfig> attributes = Pipeline.getRayTracingShaderPackAttributes().stream()
            .filter(attribute -> attribute != null && attribute.name != null)
            .filter(attribute -> nameFilter.test(attribute.name))
            .toList();
        if (attributes.isEmpty()) {
            return false;
        }

        SettingsSection section = panel.addSection(titleKey);
        var textRenderer = MinecraftClient.getInstance().textRenderer;
        for (AttributeConfig attribute : attributes) {
            ClickableWidget widget = AttributeWidgetUtil.buildCompactWidget(module, attribute, textRenderer,
                150, ShaderPackAttributeControls::commit);
            section.addTwoWidgets(widget, null);
        }
        return true;
    }

    static boolean nameContainsAny(String name, String... tokens) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (token != null && lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static void commit() {
        Pipeline.savePipeline();
        Pipeline.build();
    }
}
