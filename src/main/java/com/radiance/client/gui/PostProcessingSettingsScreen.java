package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class PostProcessingSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;

    public PostProcessingSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options,
            Text.translatable("radiance.settings.post_processing.title"));
        this.parentScreen = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Light & Color > Post Processing", parentScreen);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent — game world shows through
    }

    @Override
    protected void initBody() {
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void addOptions() {
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_POST_PROCESSING), body));

        // Sharpener mode: cycle through None(0), CAS(1), RCAS(2)
        String[] sharpenerNames = {"None", "CAS", "RCAS"};
        net.minecraft.client.gui.widget.CyclingButtonWidget<Integer> sharpenerBtn =
            net.minecraft.client.gui.widget.CyclingButtonWidget.<Integer>builder(
                    (value) -> Text.literal(sharpenerNames[value]))
                .values(0, 1, 2)
                .initially(Options.sharpenerMode)
                .build(0, 0, 150, 20, Text.translatable(Options.SHARPENER_MODE_KEY), (btn, value) -> {
                    Options.setSharpenerMode(value, true);
                    MinecraftClient.getInstance().setScreen(new PostProcessingSettingsScreen(parentScreen));
                });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(sharpenerBtn, null, body));

        if (Options.sharpenerMode != 0) {
            ResettableSliderWidget casSharpnessSlider = new ResettableSliderWidget(
                0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(
                    Text.translatable(Options.CAS_SHARPNESS_KEY),
                    Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true));
            this.body.addEntry(new RadianceSettingsScreen.SliderEntry(casSharpnessSlider, body));
        }
    }
}
