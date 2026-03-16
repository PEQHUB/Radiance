package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.mojang.serialization.Codec;
import com.radiance.client.gui.unified.*;
import com.radiance.client.option.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class RayTracingPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var gameOptions = MinecraftClient.getInstance().options;

        SettingsSection section = panel.addSection(Options.CATEGORY_RAY_TRACING);

        SimpleOption<Integer> rayBounces = new SimpleOption<>(
            Options.RAY_BOUNCES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 32),
            Codec.intRange(0, 32),
            Options.rayBounces,
            value -> Options.setRayBounces(value, true));

        SimpleOption<Boolean> ommEnabled = SimpleOption.ofBoolean(
            Options.OMM_ENABLED_KEY, Options.ommEnabled,
            value -> Options.setOMMEnabled(value, true));
        section.addTwoWidgets(rayBounces.createWidget(gameOptions), ommEnabled.createWidget(gameOptions));

        SimpleOption<Integer> ommBakerLevel = new SimpleOption<>(
            Options.OMM_BAKER_LEVEL_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText, Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 8),
            Codec.intRange(1, 8),
            Options.ommBakerLevel,
            value -> Options.setOMMBakerLevel(value, true));

        SimpleOption<Boolean> simplifiedIndirect = SimpleOption.ofBoolean(
            Options.SIMPLIFIED_INDIRECT_KEY, Options.simplifiedIndirect,
            value -> Options.setSimplifiedIndirect(value, true));
        section.addTwoWidgets(ommBakerLevel.createWidget(gameOptions), simplifiedIndirect.createWidget(gameOptions));

        SimpleOption<Boolean> multiScatterGGX = SimpleOption.ofBoolean(
            Options.MULTI_SCATTER_GGX_KEY, Options.multiScatterGGX,
            value -> Options.setMultiScatterGGX(value, true));

        SimpleOption<Boolean> eonDiffuse = SimpleOption.ofBoolean(
            Options.EON_DIFFUSE_KEY, Options.eonDiffuse,
            value -> Options.setEonDiffuse(value, true));
        section.addTwoWidgets(multiScatterGGX.createWidget(gameOptions), eonDiffuse.createWidget(gameOptions));
    }
}
