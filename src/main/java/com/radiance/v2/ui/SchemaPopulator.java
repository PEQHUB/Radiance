package com.radiance.v2.ui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.unified.*;
import com.radiance.v2.config.ConfigUIMetadata;
import com.radiance.v2.config.ConfigUIMetadata.ControlType;
import com.radiance.v2.config.ConfigUIMetadata.OptionMeta;
import com.radiance.v2.config.GeneratedConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Schema-driven settings populator. Reads ConfigUIMetadata.ALL to construct
 * settings sections and widgets automatically from the JSON Schema definitions.
 *
 * Each category in the schema maps to a SettingsSection. Each option maps to
 * a widget (slider, toggle, or dropdown) based on x-ui-control metadata.
 *
 * This replaces manual populator classes for categories covered by the schema.
 * Categories with custom UI (materials editor, area lights, pipeline graph)
 * keep their handwritten populators.
 */
public class SchemaPopulator implements ContentPopulator {

    private final String targetCategory;

    /**
     * @param targetCategory The x-category value to filter options for (e.g., "options.video.category.window")
     */
    public SchemaPopulator(String targetCategory) {
        this.targetCategory = targetCategory;
    }

    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        List<OptionMeta> options = ConfigUIMetadata.ALL.stream()
            .filter(m -> m.category().equals(targetCategory))
            .filter(m -> !m.experimental()) // Hide experimental unless enabled
            .collect(Collectors.toList());

        if (options.isEmpty()) return;

        SettingsSection section = panel.addSection(targetCategory);

        // Build widgets in pairs for two-column layout where possible
        List<ClickableWidget> pending = new ArrayList<>();

        for (OptionMeta meta : options) {
            ClickableWidget widget = createWidget(meta, gameOptions, screen);
            if (widget == null) continue;

            pending.add(widget);

            if (pending.size() == 2) {
                section.addTwoWidgets(pending.get(0), pending.get(1))
                    .tooltip(pending.size() > 0 ? options.get(options.indexOf(meta) - 1).doc() : "");
                pending.clear();
            }
        }

        // Flush remaining single widget
        if (pending.size() == 1) {
            section.addToggle(pending.get(0)); // addToggle works for any single widget
        }
    }

    private ClickableWidget createWidget(OptionMeta meta, net.minecraft.client.option.GameOptions gameOptions,
                                          RadianceUnifiedScreen screen) {
        return switch (meta.control()) {
            case TOGGLE -> createToggle(meta, gameOptions);
            case SLIDER -> createSlider(meta, gameOptions);
            case DROPDOWN -> createDropdown(meta, gameOptions);
        };
    }

    private ClickableWidget createToggle(OptionMeta meta, net.minecraft.client.option.GameOptions gameOptions) {
        boolean currentValue = getBoolean(meta.key());
        SimpleOption<Boolean> option = SimpleOption.ofBoolean(
            meta.key(), currentValue,
            value -> setField(meta.key(), value));
        return option.createWidget(gameOptions);
    }

    private ClickableWidget createSlider(OptionMeta meta, net.minecraft.client.option.GameOptions gameOptions) {
        int min = (int) meta.min();
        int max = (int) meta.max();
        int current = getInt(meta.key());
        int step = meta.step() > 0 ? (int) meta.step() : 1;

        // Normalize to step-based slider
        int stepsCount = (max - min) / step;
        SimpleOption<Integer> option = new SimpleOption<>(
            meta.key(),
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(String.valueOf(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, stepsCount).withModifier(
                value -> min + value * step,
                value -> (value - min) / step),
            Codec.intRange(min, max),
            current,
            value -> setField(meta.key(), value));

        return option.createWidget(gameOptions);
    }

    private ClickableWidget createDropdown(OptionMeta meta, net.minecraft.client.option.GameOptions gameOptions) {
        String[] labels = meta.enumLabels();
        if (labels == null || labels.length == 0) {
            return createSlider(meta, gameOptions); // Fallback
        }
        int current = getInt(meta.key());

        SimpleOption<Integer> option = new SimpleOption<>(
            meta.key(),
            SimpleOption.emptyTooltip(),
            (optionText, value) -> {
                String label = (value >= 0 && value < labels.length) ? labels[value] : String.valueOf(value);
                return getGenericValueText(optionText, Text.literal(label));
            },
            new SimpleOption.ValidatingIntSliderCallbacks(0, labels.length - 1),
            Codec.intRange(0, labels.length - 1),
            current,
            value -> setField(meta.key(), value));

        return option.createWidget(gameOptions);
    }

    // --- Reflective field access into GeneratedConfig ---

    private static boolean getBoolean(String key) {
        try {
            Field f = GeneratedConfig.class.getField(key);
            return f.getBoolean(null);
        } catch (Exception e) {
            return false;
        }
    }

    private static int getInt(String key) {
        try {
            Field f = GeneratedConfig.class.getField(key);
            return f.getInt(null);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setField(String key, Object value) {
        try {
            Field f = GeneratedConfig.class.getField(key);
            f.set(null, value);
            // Also call the native setter via reflection
            String setterName = "nativeSet" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
            if (value instanceof Boolean) {
                var m = GeneratedConfig.class.getMethod(setterName, boolean.class, boolean.class);
                m.invoke(null, value, true);
            } else if (value instanceof Integer) {
                var m = GeneratedConfig.class.getMethod(setterName, int.class, boolean.class);
                m.invoke(null, value, true);
            } else if (value instanceof Float) {
                var m = GeneratedConfig.class.getMethod(setterName, float.class, boolean.class);
                m.invoke(null, value, true);
            }
        } catch (Exception e) {
            // Silently fail for now — field may not exist in generated config yet
        }
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return ConfigUIMetadata.ALL.stream()
            .filter(m -> m.category().equals(targetCategory))
            .filter(m -> !m.experimental())
            .map(m -> new UnifiedSearchOverlay.SearchEntry(m.doc(), category, nodeId, false))
            .collect(Collectors.toList());
    }
}
