package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import com.radiance.client.RadianceClient;
import com.radiance.client.option.Options;
import com.radiance.client.util.CategoryVideoOptionEntry;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.OptionListWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class RadianceSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private RadianceSearchOverlay searchOverlay;

    public RadianceSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.title"));
        this.parentScreen = parent;
    }

    // ── Fixed scaling: override Minecraft GUI scale for consistent menu size ──
    static double savedScaleFactor = -1;
    static int savedScaledWidth = -1;
    static int savedScaledHeight = -1;

    private void applyRadianceScale() {
        var window = this.client.getWindow();
        var accessor = (com.radiance.mixins.vulkan_render_integration.WindowAccessorMixin) (Object) window;

        // Save original
        if (savedScaleFactor < 0) {
            savedScaleFactor = accessor.radiance$getScaleFactor();
            savedScaledWidth = accessor.radiance$getScaledWidth();
            savedScaledHeight = accessor.radiance$getScaledHeight();
        }

        // Compute our scale: target ~900px effective width
        int pixelWidth = window.getWidth();
        int pixelHeight = window.getHeight();
        int targetWidth = 900;
        double radianceScale = Math.max(1.0, Math.round((double) pixelWidth / targetWidth));

        accessor.radiance$setScaleFactor(radianceScale);
        accessor.radiance$setScaledWidth((int) Math.ceil((double) pixelWidth / radianceScale));
        accessor.radiance$setScaledHeight((int) Math.ceil((double) pixelHeight / radianceScale));

        // Update Screen's own width/height fields
        this.width = accessor.radiance$getScaledWidth();
        this.height = accessor.radiance$getScaledHeight();
    }

    public static void restoreOriginalScale() {
        if (savedScaleFactor < 0) return;
        var mc = MinecraftClient.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        var accessor = (com.radiance.mixins.vulkan_render_integration.WindowAccessorMixin) (Object) mc.getWindow();
        accessor.radiance$setScaleFactor(savedScaleFactor);
        accessor.radiance$setScaledWidth(savedScaledWidth);
        accessor.radiance$setScaledHeight(savedScaledHeight);
        savedScaleFactor = -1;
    }

    @Override
    protected void init() {
        applyRadianceScale();
        super.init();
        this.searchOverlay = new RadianceSearchOverlay(this);

        // Shift the body list down to make room for the hint lines below the title
        this.body.setY(this.body.getY() + 22);
        this.body.setHeight(this.body.getHeight() - 22);

        // "Reset to Defaults" button — top-right corner
        int btnW = 110;
        int btnH = 18;
        int btnX = this.width - btnW - 6;
        int btnY = 6;
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("radiance.settings.reset_defaults"),
            btn -> {
                Options.resetAllToDefaults();
                MinecraftClient.getInstance().setScreen(new RadianceSettingsScreen(parentScreen));
            })
            .dimensions(btnX, btnY, btnW, btnH)
            .build());

        // "Welcome Message: On/Off" toggle button — top-left corner
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable(Options.showWelcomeMessage
                ? "radiance.settings.welcome_message.on"
                : "radiance.settings.welcome_message.off"),
            btn -> {
                Options.showWelcomeMessage = !Options.showWelcomeMessage;
                Options.overwriteConfig();
                btn.setMessage(Text.translatable(Options.showWelcomeMessage
                    ? "radiance.settings.welcome_message.on"
                    : "radiance.settings.welcome_message.off"));
            })
            .dimensions(6, btnY, btnW, btnH)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Hint lines below the title, above the body list
        int hintY = 26;
        int centerX = this.width / 2;
        RadianceTheme.drawCenteredOutlinedText(
            context, this.textRenderer,
            Text.literal(Formatting.GRAY + "Ctrl+Click a slider to type a value  \u2502  Shift+Click to reset  \u2502  Space to search"),
            centerX, hintY, RadianceTheme.textSecondary);

        // Recently tweaked settings strip
        var recents = RecentTweaksManager.getRecent();
        if (!recents.isEmpty()) {
            StringBuilder recentText = new StringBuilder("Recent: ");
            for (int i = 0; i < recents.size(); i++) {
                if (i > 0) recentText.append("  \u2502  ");
                recentText.append(recents.get(i).displayName());
            }
            RadianceTheme.drawCenteredOutlinedText(
                context, this.textRenderer,
                Text.literal(recentText.toString()),
                centerX, hintY + 11, RadianceTheme.textAccent, 0.7f);
        }

        // Search overlay (rendered last, on top of everything)
        if (searchOverlay != null && searchOverlay.isVisible()) {
            searchOverlay.render(context, this.textRenderer, this.width, this.height);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Search overlay consumes all input when visible
        if (searchOverlay != null && searchOverlay.isVisible()) {
            return searchOverlay.keyPressed(keyCode, scanCode, modifiers);
        }
        // Peek mode: hold Tab to hide all UI
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = true;
            return true;
        }
        // Search overlay: Space when no text field is focused
        if (keyCode == GLFW.GLFW_KEY_SPACE && !(getFocused() instanceof net.minecraft.client.gui.widget.TextFieldWidget)) {
            if (searchOverlay != null) searchOverlay.toggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchOverlay != null && searchOverlay.isVisible()) {
            return searchOverlay.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (searchOverlay != null && searchOverlay.isVisible()) {
            return searchOverlay.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        // Propagate right-click drag for precision slider mode (button 1)
        // Minecraft's default only propagates button 0.
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        // Peek mode: release Tab to restore UI
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    protected void refreshWidgetPositions() {
        applyRadianceScale();
        super.refreshWidgetPositions();
    }

    @Override
    public void removed() {
        super.removed();
        // Restore scale when leaving Radiance screens (but not when navigating between them)
        Screen next = this.client != null ? this.client.currentScreen : null;
        if (!(next instanceof RadianceSettingsScreen) && !(next instanceof MaterialsSettingsScreen)) {
            restoreOriginalScale();
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Frosted glass: heavy dark overlay so game world is barely visible
        if (!RadianceTheme.peekActive) {
            context.fill(0, 0, this.width, this.height, 0xF0080808); // ~94% dark overlay
        }
    }

    @Override
    protected void initBody() {
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    /** Helper: create a ButtonWidget that opens a sub-screen. */
    private ButtonWidget subScreenButton(String translationKey, Screen target) {
        return ButtonWidget.builder(
            Text.translatable(translationKey),
            btn -> MinecraftClient.getInstance().setScreen(target))
            .width(150).build();
    }

    @Override
    protected void addOptions() {
        MinecraftClient mc = MinecraftClient.getInstance();
        RadianceSettingsScreen self = this;

        // ── MENU TRANSPARENCY (global alpha slider, before all categories) ──
        ResettableSliderWidget alphaSlider = new ResettableSliderWidget(
            0, 0, 150, 20,
            0, 100, Options.uiGlobalAlphaPercent, 55,
            v -> getGenericValueText(
                Text.translatable("radiance.settings.menu_transparency"),
                Text.literal(v + "%")),
            v -> Options.setUiGlobalAlphaPercent(v, false));
        alphaSlider.setOnRelease(() -> Options.overwriteConfig());
        alphaSlider.settingKey = "uiGlobalAlphaPercent";
        this.body.addEntry(new SliderEntry(alphaSlider, body));

        // ── DISPLAY ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_WINDOW), body));

        // [Max FPS | VSync]
        SimpleOption<Integer> maxFps = new SimpleOption<>(
            "options.framerateLimit",
            SimpleOption.emptyTooltip(),
            (optionText, value) -> value == 260
                ? getGenericValueText(optionText, Text.translatable("options.framerateLimit.max"))
                : getGenericValueText(optionText, Text.translatable("options.framerate", value)),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 26).withModifier(
                value -> value * 10, value -> value / 10),
            Codec.intRange(10, 260),
            Options.maxFps,
            value -> {
                mc.getInactivityFpsLimiter().setMaxFps(value);
                Options.setMaxFps(value, true);
            });
        SimpleOption<Boolean> enableVsync = SimpleOption.ofBoolean("options.vsync", Options.vsync,
            value -> Options.setVsync(value, true));
        this.body.addEntry(new TwoColumnOptionEntry(
            maxFps.createWidget(gameOptions), enableVsync.createWidget(gameOptions), body));

        // ── LIGHTING ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_LIGHTING), body));

        // [Global Light Mode | Area Lights]
        SimpleOption<Integer> globalLightMode = new SimpleOption<>(
            Options.GLOBAL_LIGHT_MODE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.translatable(value == 0 ? Options.GLOBAL_LIGHT_MODE_AUTO_KEY
                    : value == 1 ? Options.GLOBAL_LIGHT_MODE_AREA_KEY
                    : Options.GLOBAL_LIGHT_MODE_EMISSIVE_KEY)),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 2),
            Codec.intRange(0, 2),
            Options.globalLightMode,
            value -> Options.setGlobalLightMode(value, true));
        SimpleOption<Boolean> areaLightsEnabled = SimpleOption.ofBoolean(
            Options.AREA_LIGHTS_ENABLED_KEY,
            Options.areaLightsEnabled,
            value -> Options.setAreaLightsEnabled(value, true));
        this.body.addEntry(new TwoColumnOptionEntry(
            globalLightMode.createWidget(gameOptions), areaLightsEnabled.createWidget(gameOptions), body));

        // [Area Light Settings... | Emission Settings...]
        this.body.addEntry(new TwoColumnOptionEntry(
            subScreenButton(Options.AREA_LIGHT_SETTINGS_KEY, new AreaLightSettingsScreen(this)),
            subScreenButton("options.video.emission_settings", new EmissiveBlockSettingsScreen(this)),
            body));

        // [Materials... | Material Browser...]
        this.body.addEntry(new TwoColumnOptionEntry(
            subScreenButton("options.video.materials_settings", new MaterialsSettingsScreen(this)),
            subScreenButton("radiance.materials.browser", new MaterialBrowserScreen(this)),
            body));

        // ── RENDERING ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable("options.video.category.rendering"), body));
        this.body.addEntry(new TwoColumnOptionEntry(
            subScreenButton("options.video.rendering_settings", new RenderingSettingsScreen(this)),
            null, body));

        // ── UPSCALER ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_UPSCALER), body));

        if (RadianceClient.dlssMissing) {
            ButtonWidget dlssWarningBtn = ButtonWidget.builder(
                Text.translatable("options.video.dlss_missing_warning"),
                btn -> mc.setScreen(new DlssMissingScreen(this)))
                .width(150).build();
            this.body.addEntry(new TwoColumnOptionEntry(dlssWarningBtn, null, body));
        }

        // [DLSS-D: ON | Upscaler Quality] (quality only when DLSS enabled)
        SimpleOption<Boolean> dlssDEnabled = SimpleOption.ofBoolean(
            Options.DLSS_D_ENABLED_KEY,
            Options.dlssDEnabled,
            value -> {
                Options.setDlssDEnabled(value, true);
                mc.setScreen(new RadianceSettingsScreen(self.parentScreen));
            });
        if (Options.dlssDEnabled) {
            String[] upscalerQualityKeys = {
                Options.UPSCALER_QUALITY_PERFORMANCE, Options.UPSCALER_QUALITY_BALANCED,
                Options.UPSCALER_QUALITY_QUALITY, Options.UPSCALER_QUALITY_NATIVE,
                Options.UPSCALER_QUALITY_CUSTOM
            };
            SimpleOption<Integer> upscalerQuality = new SimpleOption<>(
                Options.UPSCALER_QUALITY_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.translatable(upscalerQualityKeys[Math.min(value, upscalerQualityKeys.length - 1)])),
                new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerQualityKeys.length - 1),
                Codec.intRange(0, upscalerQualityKeys.length - 1),
                Options.upscalerQuality,
                value -> Options.setUpscalerQuality(value, true));
            this.body.addEntry(new TwoColumnOptionEntry(
                dlssDEnabled.createWidget(gameOptions), upscalerQuality.createWidget(gameOptions), body));

            // [Upscaler Preset | Resolution Override]
            String[] upscalerPresets = {"D", "E"};
            SimpleOption<Integer> upscalerPreset = new SimpleOption<>(
                Options.UPSCALER_PRESET_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(upscalerPresets[Math.min(value, upscalerPresets.length - 1)])),
                new SimpleOption.ValidatingIntSliderCallbacks(0, upscalerPresets.length - 1),
                Codec.intRange(0, upscalerPresets.length - 1),
                Options.upscalerPreset == 5 ? 1 : 0,
                value -> Options.setUpscalerPreset(value == 0 ? 4 : 5, true));
            SimpleOption<Integer> upscalerResOverride = new SimpleOption<>(
                Options.UPSCALER_RES_OVERRIDE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(value + "%")),
                new SimpleOption.ValidatingIntSliderCallbacks(33, 100),
                Codec.intRange(33, 100),
                Options.upscalerResOverride,
                value -> Options.setUpscalerResOverride(value, true));
            this.body.addEntry(new TwoColumnOptionEntry(
                upscalerPreset.createWidget(gameOptions), upscalerResOverride.createWidget(gameOptions), body));

            // [Output Scale 2x | Reflex] or [Output Scale 2x | (empty)]
            SimpleOption<Boolean> outputScale2x = SimpleOption.ofBoolean(
                Options.OUTPUT_SCALE_2X_KEY, Options.outputScale2x,
                value -> Options.setOutputScale2x(value, true));
            ClickableWidget rightWidget = null;
            if (Options.isReflexSupported()) {
                SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                    Options.REFLEX_ENABLED_KEY,
                    Options.reflexEnabled,
                    value -> {
                        Options.setReflexEnabled(value, true);
                        mc.setScreen(new RadianceSettingsScreen(self.parentScreen));
                    });
                rightWidget = reflexEnabled.createWidget(gameOptions);
            }
            this.body.addEntry(new TwoColumnOptionEntry(
                outputScale2x.createWidget(gameOptions), rightWidget, body));

            // VRR removed — frame limit is now controlled by maxFps slider in UpscalerPopulator
        } else {
            // DLSS disabled — just show DLSS toggle + Output Scale 2x
            SimpleOption<Boolean> outputScale2x = SimpleOption.ofBoolean(
                Options.OUTPUT_SCALE_2X_KEY, Options.outputScale2x,
                value -> Options.setOutputScale2x(value, true));
            this.body.addEntry(new TwoColumnOptionEntry(
                dlssDEnabled.createWidget(gameOptions), outputScale2x.createWidget(gameOptions), body));

            // Reflex (independent of DLSS)
            if (Options.isReflexSupported()) {
                SimpleOption<Boolean> reflexEnabled = SimpleOption.ofBoolean(
                    Options.REFLEX_ENABLED_KEY,
                    Options.reflexEnabled,
                    value -> {
                        Options.setReflexEnabled(value, true);
                        mc.setScreen(new RadianceSettingsScreen(self.parentScreen));
                    });
                this.body.addEntry(new TwoColumnOptionEntry(
                    reflexEnabled.createWidget(gameOptions), null, body));
            }
        }

        // ── FRAME GENERATION ──
        if (Options.isFrameGenSupported()) {
            String[] fgModeNames = {"Off", "On", "Auto"};
            int maxModes = 3; // Off, On, Auto
            SimpleOption<Integer> frameGenMode = new SimpleOption<>(
                Options.FRAME_GEN_MODE_KEY,
                SimpleOption.emptyTooltip(),
                (optionText, value) -> getGenericValueText(optionText,
                    Text.literal(value < fgModeNames.length ? fgModeNames[value] : "Off")),
                new SimpleOption.ValidatingIntSliderCallbacks(0, maxModes - 1),
                Codec.intRange(0, maxModes - 1),
                Options.frameGenMode,
                value -> {
                    Options.setFrameGenMode(value, true);
                    mc.setScreen(new RadianceSettingsScreen(self.parentScreen));
                });

            ClickableWidget fgRightWidget = null;
            int maxMulti = Options.getFrameGenMaxMultiplier();
            if (Options.frameGenMode != 0 && maxMulti > 1) {
                String[] multiNames = {"2x", "3x", "4x"};
                SimpleOption<Integer> frameGenMulti = new SimpleOption<>(
                    Options.FRAME_GEN_MULTIPLIER_KEY,
                    SimpleOption.emptyTooltip(),
                    (optionText, value) -> getGenericValueText(optionText,
                        Text.literal(value >= 1 && value <= 3 ? multiNames[value - 1] : "2x")),
                    new SimpleOption.ValidatingIntSliderCallbacks(1, maxMulti),
                    Codec.intRange(1, maxMulti),
                    Options.frameGenMultiplier,
                    value -> Options.setFrameGenMultiplier(value, true));
                fgRightWidget = frameGenMulti.createWidget(gameOptions);
            }

            this.body.addEntry(new TwoColumnOptionEntry(
                frameGenMode.createWidget(gameOptions), fgRightWidget, body));
        }

        // ── ENVIRONMENT ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_ENVIRONMENT), body));

        this.body.addEntry(new TwoColumnOptionEntry(
            subScreenButton(Options.ENVIRONMENT_SETTINGS_KEY, new EnvironmentalSettingsScreen(this)),
            null, body));

        // ── RAY TRACING ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_RAY_TRACING), body));

        // [Ray Bounces | OMM]
        SimpleOption<Integer> rayBounces = new SimpleOption<>(
            Options.RAY_BOUNCES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 32),
            Codec.intRange(0, 32),
            Options.rayBounces,
            value -> Options.setRayBounces(value, true));
        this.body.addEntry(new TwoColumnOptionEntry(rayBounces.createWidget(gameOptions), null, body));

        // ── DIAGNOSTICS ──
        SimpleOption<Boolean> loggingToggle = SimpleOption.ofBoolean(
            "options.video.logging_enabled",
            Options.loggingEnabled,
            value -> Options.setLoggingEnabled(value, true));
        this.body.addAll(new SimpleOption[]{loggingToggle});

        // ── SHARC RADIANCE CACHE ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_SHARC), body));

        SimpleOption<Boolean> sharcEnabled = SimpleOption.ofBoolean(
            Options.SHARC_ENABLED_KEY,
            Options.sharcEnabled,
            value -> Options.setSharcEnabled(value, true));
        SimpleOption<Integer> sharcSceneScale = new SimpleOption<>(
            Options.SHARC_SCENE_SCALE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(String.format("%.1f", value / 10.0))),
            new SimpleOption.ValidatingIntSliderCallbacks(10, 200),
            Codec.intRange(10, 200),
            Options.sharcSceneScaleTenths,
            value -> Options.setSharcSceneScaleTenths(value, true));
        SimpleOption<Integer> sharcRoughnessThreshold = new SimpleOption<>(
            Options.SHARC_ROUGHNESS_THRESHOLD_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(String.format("%.2f", value / 100.0))),
            new SimpleOption.ValidatingIntSliderCallbacks(0, 100),
            Codec.intRange(0, 100),
            Options.sharcRoughnessThresholdPercent,
            value -> Options.setSharcRoughnessThresholdPercent(value, true));
        SimpleOption<Integer> sharcAccumulationFrames = new SimpleOption<>(
            Options.SHARC_ACCUMULATION_FRAMES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(4, 256),
            Codec.intRange(4, 256),
            Options.sharcAccumulationFrames,
            value -> Options.setSharcAccumulationFrames(value, true));
        SimpleOption<Integer> sharcStaleFrames = new SimpleOption<>(
            Options.SHARC_STALE_FRAMES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(4, 128),
            Codec.intRange(4, 128),
            Options.sharcStaleFrames,
            value -> Options.setSharcStaleFrames(value, true));
        SimpleOption<Integer> sharcDownscale = new SimpleOption<>(
            Options.SHARC_DOWNSCALE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(value + "x")),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 8),
            Codec.intRange(1, 8),
            Options.sharcDownscale,
            value -> Options.setSharcDownscale(value, true));

        this.body.addEntry(new TwoColumnOptionEntry(
            sharcEnabled.createWidget(gameOptions), sharcDownscale.createWidget(gameOptions), body));
        this.body.addEntry(new TwoColumnOptionEntry(
            sharcSceneScale.createWidget(gameOptions), sharcRoughnessThreshold.createWidget(gameOptions), body));
        this.body.addEntry(new TwoColumnOptionEntry(
            sharcAccumulationFrames.createWidget(gameOptions), sharcStaleFrames.createWidget(gameOptions), body));

        // ── TERRAIN ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_TERRAIN), body));

        // [Chunk Batch Size | Chunk Total Batches]
        SimpleOption<Integer> chunkBatchSize = new SimpleOption<>(
            Options.CHUNK_BUILDING_BATCH_SIZE_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingBatchSize,
            value -> Options.setChunkBuildingBatchSize(value, true));
        SimpleOption<Integer> chunkTotalBatches = new SimpleOption<>(
            Options.CHUNK_BUILDING_TOTAL_BATCHES_KEY,
            SimpleOption.emptyTooltip(),
            (optionText, value) -> getGenericValueText(optionText,
                Text.literal(Integer.toString(value))),
            new SimpleOption.ValidatingIntSliderCallbacks(1, 32),
            Codec.intRange(1, 32),
            Options.chunkBuildingTotalBatches,
            value -> Options.setChunkBuildingTotalBatches(value, true));
        this.body.addEntry(new TwoColumnOptionEntry(
            chunkBatchSize.createWidget(gameOptions), chunkTotalBatches.createWidget(gameOptions), body));

        // ── PIPELINE ──
        this.body.addEntry(
            new CategoryVideoOptionEntry(Text.translatable(Options.CATEGORY_PIPELINE), body));

        this.body.addEntry(new TwoColumnOptionEntry(
            subScreenButton(Options.PIPELINE_SETUP_KEY, new RenderPipelineScreen(this)),
            null, body));

    }

    /** WidgetEntry that renders a single centered text label with no interactive widgets. */
    static class WarningLabelEntry extends OptionListWidget.WidgetEntry {
        private final Text label;

        WarningLabelEntry(Text label, OptionListWidget parent) {
            super(ImmutableList.of(), null);
            this.label = label;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
            context.drawCenteredTextWithShadow(mc.textRenderer, label, x + entryWidth / 2, y + 6, 0xFFFFFF);
        }

        @Override
        public List<? extends Element> children() {
            return ImmutableList.of();
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return ImmutableList.of();
        }
    }

    /** WidgetEntry that holds a single ResettableSliderWidget, centered like SimpleOption entries. */
    static class SliderEntry extends OptionListWidget.WidgetEntry {
        private final ResettableSliderWidget slider;
        private final OptionListWidget parent;

        SliderEntry(ResettableSliderWidget slider, OptionListWidget parent) {
            super(ImmutableList.of(slider), null);
            this.slider = slider;
            this.parent = parent;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            // Full-width slider, rounded down to even pixels
            int w = entryWidth - (entryWidth % 2);
            slider.setX(x + (entryWidth - w) / 2);
            slider.setY(y);
            slider.setWidth(w);
            slider.render(context, mouseX, mouseY, tickDelta);
            // Modified dot for non-default values
            RadianceTheme.drawModifiedDot(context, slider.getX(), y, entryHeight, !slider.isDefault());
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public List<? extends Element> children() {
            return ImmutableList.of(slider);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return ImmutableList.of(slider);
        }
    }

    /** WidgetEntry that renders two ResettableSliderWidgets side by side. */
    static class TwoColumnSliderEntry extends OptionListWidget.WidgetEntry {
        private final ResettableSliderWidget left;
        private final ResettableSliderWidget right; // nullable

        TwoColumnSliderEntry(ResettableSliderWidget left, ResettableSliderWidget right, OptionListWidget parent) {
            super(right != null ? ImmutableList.of(left, right) : ImmutableList.of(left), null);
            this.left = left;
            this.right = right;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            int gap = 8;
            int colW = (entryWidth - gap) / 2;
            left.setX(x);
            left.setY(y);
            left.setWidth(right != null ? colW : entryWidth);
            left.render(context, mouseX, mouseY, tickDelta);
            RadianceTheme.drawModifiedDot(context, left.getX(), y, entryHeight, !left.isDefault());
            if (right != null) {
                right.setX(x + colW + gap);
                right.setY(y);
                right.setWidth(colW);
                right.render(context, mouseX, mouseY, tickDelta);
                RadianceTheme.drawModifiedDot(context, right.getX(), y, entryHeight, !right.isDefault());
            }
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (left.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            return right != null && right.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public List<? extends Element> children() {
            return right != null ? ImmutableList.of(left, right) : ImmutableList.of(left);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return right != null ? ImmutableList.of(left, right) : ImmutableList.of(left);
        }
    }

    /** WidgetEntry that renders four ResettableSliderWidgets in a row. */
    static class FourColumnSliderEntry extends OptionListWidget.WidgetEntry {
        private final ResettableSliderWidget s0, s1, s2, s3;

        FourColumnSliderEntry(ResettableSliderWidget s0, ResettableSliderWidget s1,
                              ResettableSliderWidget s2, ResettableSliderWidget s3,
                              OptionListWidget parent) {
            super(buildWidgetList(s0, s1, s2, s3), null);
            this.s0 = s0; this.s1 = s1; this.s2 = s2; this.s3 = s3;
        }

        private static ImmutableList<ClickableWidget> buildWidgetList(
                ResettableSliderWidget s0, ResettableSliderWidget s1,
                ResettableSliderWidget s2, ResettableSliderWidget s3) {
            var b = ImmutableList.<ClickableWidget>builder();
            if (s0 != null) b.add(s0);
            if (s1 != null) b.add(s1);
            if (s2 != null) b.add(s2);
            if (s3 != null) b.add(s3);
            return b.build();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            // Count populated slots to determine layout
            int count = (s0 != null ? 1 : 0) + (s1 != null ? 1 : 0) + (s2 != null ? 1 : 0) + (s3 != null ? 1 : 0);
            if (count <= 3 && s3 == null) {
                // 3-column layout: wider sliders, evenly spaced
                int gap = 8;
                int colW = (entryWidth - gap * (count - 1)) / count;
                int slot = 0;
                for (ResettableSliderWidget s : new ResettableSliderWidget[]{s0, s1, s2}) {
                    if (s == null) continue;
                    s.setX(x + slot * (colW + gap));
                    s.setY(y);
                    s.setWidth(colW);
                    s.render(context, mouseX, mouseY, tickDelta);
                    slot++;
                }
            } else {
                // 4-column layout — even spacing with small gaps
                int gap = 4;
                int colW = (entryWidth - gap * 3) / 4;
                ResettableSliderWidget[] slots = {s0, s1, s2, s3};
                int sx = x;
                for (ResettableSliderWidget s : slots) {
                    if (s != null) {
                        s.setX(sx);
                        s.setY(y);
                        s.setWidth(colW);
                        s.render(context, mouseX, mouseY, tickDelta);
                    }
                    sx += colW + gap;
                }
            }
        }

        private void renderSlot(ResettableSliderWidget s, int x, int y, int ew, int sw, int col,
                                int mx, int my, float td, DrawContext ctx) {
            if (s == null) return;
            s.setX(x + ew * col / 8 - sw / 2);
            s.setY(y);
            s.setWidth(sw);
            s.render(ctx, mx, my, td);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (s0 != null && s0.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            if (s1 != null && s1.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            if (s2 != null && s2.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            return s3 != null && s3.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public List<? extends Element> children() { return buildWidgetList(s0, s1, s2, s3); }

        @Override
        public List<? extends Selectable> selectableChildren() { return buildWidgetList(s0, s1, s2, s3); }
    }

    /** WidgetEntry that renders 5 columns: [Emission | Temperature | Material | Wavelength | Purity]. */
    static class FiveColumnEmissionEntry extends OptionListWidget.WidgetEntry {
        private final ResettableSliderWidget s0, s1, s3, s4;
        private final MaterialDropdownWidget dropdown;

        FiveColumnEmissionEntry(ResettableSliderWidget s0, ResettableSliderWidget s1,
                                MaterialDropdownWidget dropdown,
                                ResettableSliderWidget s3, ResettableSliderWidget s4,
                                OptionListWidget parent) {
            super(buildWidgetList5(s0, s1, dropdown, s3, s4), null);
            this.s0 = s0; this.s1 = s1; this.dropdown = dropdown; this.s3 = s3; this.s4 = s4;
        }

        private static ImmutableList<ClickableWidget> buildWidgetList5(
                ResettableSliderWidget s0, ResettableSliderWidget s1,
                MaterialDropdownWidget dropdown,
                ResettableSliderWidget s3, ResettableSliderWidget s4) {
            var b = ImmutableList.<ClickableWidget>builder();
            if (s0 != null) b.add(s0);
            if (s1 != null) b.add(s1);
            if (dropdown != null) b.add(dropdown);
            if (s3 != null) b.add(s3);
            if (s4 != null) b.add(s4);
            return b.build();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            int gap = 4;
            int sw = (entryWidth - gap * 4) / 5;
            renderSlider5(s0, x, y, entryWidth, sw, 1, mouseX, mouseY, tickDelta, context);
            renderSlider5(s1, x, y, entryWidth, sw, 3, mouseX, mouseY, tickDelta, context);
            if (dropdown != null) {
                dropdown.setX(x + entryWidth * 5 / 10 - sw / 2);
                dropdown.setY(y);
                dropdown.setWidth(sw);
                dropdown.renderWidget(context, mouseX, mouseY, tickDelta);
            }
            renderSlider5(s3, x, y, entryWidth, sw, 7, mouseX, mouseY, tickDelta, context);
            renderSlider5(s4, x, y, entryWidth, sw, 9, mouseX, mouseY, tickDelta, context);
            // Dropdown overlay is rendered by the screen, not here (avoids list widget clipping)
        }

        private void renderSlider5(ResettableSliderWidget s, int x, int y, int ew, int sw, int col,
                                   int mx, int my, float td, DrawContext ctx) {
            if (s == null) return;
            s.setX(x + ew * col / 10 - sw / 2);
            s.setY(y);
            s.setWidth(sw);
            s.render(ctx, mx, my, td);
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (s0 != null && s0.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            if (s1 != null && s1.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            if (s3 != null && s3.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            return s4 != null && s4.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public List<? extends Element> children() { return buildWidgetList5(s0, s1, dropdown, s3, s4); }

        @Override
        public List<? extends Selectable> selectableChildren() { return buildWidgetList5(s0, s1, dropdown, s3, s4); }
    }

    /** WidgetEntry that renders two ClickableWidgets (buttons/toggles) side by side. */
    static class TwoColumnOptionEntry extends OptionListWidget.WidgetEntry {
        private final ClickableWidget left;
        private final ClickableWidget right; // nullable

        TwoColumnOptionEntry(ClickableWidget left, ClickableWidget right, OptionListWidget parent) {
            super(right != null ? ImmutableList.of(left, right) : ImmutableList.of(left), null);
            this.left = left;
            this.right = right;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            int gap = 8;
            int colW = (entryWidth - gap) / 2;
            left.setX(x);
            left.setY(y);
            left.setWidth(right != null ? colW : entryWidth);
            left.render(context, mouseX, mouseY, tickDelta);
            if (right != null) {
                right.setX(x + colW + gap);
                right.setY(y);
                right.setWidth(colW);
                right.render(context, mouseX, mouseY, tickDelta);
            }
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (left.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
            return right != null && right.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }

        @Override
        public List<? extends Element> children() {
            return right != null ? ImmutableList.of(left, right) : ImmutableList.of(left);
        }

        @Override
        public List<? extends Selectable> selectableChildren() {
            return right != null ? ImmutableList.of(left, right) : ImmutableList.of(left);
        }
    }

    /** WidgetEntry that renders four ClickableWidgets (buttons) in a single row. */
    static class FourColumnButtonEntry extends OptionListWidget.WidgetEntry {
        private final ClickableWidget b0, b1, b2, b3;

        FourColumnButtonEntry(ClickableWidget b0, ClickableWidget b1,
                              ClickableWidget b2, ClickableWidget b3,
                              OptionListWidget parent) {
            super(buildBtnList(b0, b1, b2, b3), null);
            this.b0 = b0; this.b1 = b1; this.b2 = b2; this.b3 = b3;
        }

        private static ImmutableList<ClickableWidget> buildBtnList(
                ClickableWidget a, ClickableWidget b, ClickableWidget c, ClickableWidget d) {
            var builder = ImmutableList.<ClickableWidget>builder();
            if (a != null) builder.add(a);
            if (b != null) builder.add(b);
            if (c != null) builder.add(c);
            if (d != null) builder.add(d);
            return builder.build();
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
            int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int gap = 4;
            var btns = buildBtnList(b0, b1, b2, b3);
            int bw = (entryWidth - gap * (btns.size() - 1)) / btns.size();
            int bx = x;
            for (var btn : btns) {
                btn.setX(bx);
                btn.setY(y);
                btn.setWidth(bw);
                btn.render(context, mouseX, mouseY, tickDelta);
                bx += bw + gap;
            }
        }

        @Override
        public List<? extends Element> children() { return buildBtnList(b0, b1, b2, b3); }

        @Override
        public List<? extends Selectable> selectableChildren() { return buildBtnList(b0, b1, b2, b3); }
    }

    /** WidgetEntry that renders a single ButtonWidget, full-width like SliderEntry. */
    public static class ButtonEntry extends OptionListWidget.WidgetEntry {
        private final ButtonWidget button;

        public ButtonEntry(ButtonWidget button, OptionListWidget parent) {
            super(ImmutableList.of(button), null);
            this.button = button;
        }

        @Override
        public void render(DrawContext context, int index, int y, int x, int entryWidth,
                int entryHeight, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            float fadeFactor = RadianceTheme.isActiveEntry(children()) ? 1f : RadianceTheme.inactiveFadeFactor();
            if (fadeFactor <= 0f) return;
            int w = entryWidth - (entryWidth % 2);
            button.setX(x + (entryWidth - w) / 2);
            button.setY(y);
            button.setWidth(w);
            button.render(context, mouseX, mouseY, tickDelta);
        }

        @Override
        public List<? extends Element> children() { return ImmutableList.of(button); }

        @Override
        public List<? extends Selectable> selectableChildren() { return ImmutableList.of(button); }
    }
}
