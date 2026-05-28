package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.RadianceClient;
import com.radiance.client.gui.DlssMissingScreen;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.SelectionDropdownWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class UpscalerPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        var mc = MinecraftClient.getInstance();
        var gameOptions = mc.options;

        SettingsSection spatial = panel.addSection("Spatial / Reconstruction");

        if (RadianceClient.dlssMissing) {
            spatial.addButton(ButtonWidget.builder(
                Text.translatable("options.video.dlss_missing_warning"),
                btn -> screen.showOverlay(new DlssMissingScreen(screen)))
                .width(150).build());
        }

        SelectionDropdownWidget upscalerModeDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Upscaler",
            new String[]{"DLSS-RR", "FSR3", "Off"},
            Options.upscalerMode, value -> {
                Options.setUpscalerMode(value, true);
                screen.refreshContent();
            });

        if (Options.upscalerMode == 0) {
            SimpleOption<Boolean> dlssDToggle = SimpleOption.ofBoolean(
                "options.video.dlss_d_enabled", Options.dlssDEnabled,
                value -> Options.setDlssDEnabled(value, true));

            spatial.addTwoWidgets(upscalerModeDropdown, dlssDToggle.createWidget(gameOptions))
                  .tooltip("DLSS-RR = NVIDIA ray reconstruction. FSR3 = AMD upscaler. DLSS-D denoises without upscaling.");
        } else {
            spatial.addTwoWidgets(upscalerModeDropdown, null)
                  .tooltip("DLSS-RR = NVIDIA ray reconstruction. FSR3 = AMD upscaler.");
        }

        if (Options.upscalerMode != 2) {
            SelectionDropdownWidget qualityDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Quality",
                new String[]{"Performance", "Balanced", "Quality", "Native", "Custom"},
                Options.upscalerQuality, value -> {
                    Options.setUpscalerQuality(value, true);
                    screen.refreshContent();
                });

            if (Options.upscalerMode == 0) {
                SelectionDropdownWidget presetDropdown = new SelectionDropdownWidget(
                    0, 0, 150, 20, "RR Model",
                    new String[]{"Model D", "Model E"},
                    Options.upscalerPreset == 5 ? 1 : 0, value -> Options.setUpscalerPreset(value == 1 ? 5 : 4, true));
                spatial.addTwoWidgets(qualityDropdown, presetDropdown)
                       .tooltip("Quality controls render resolution. RR Model selects the ray reconstruction network.");
            } else {
                spatial.addTwoWidgets(qualityDropdown, null)
                       .tooltip("Quality controls render resolution.");
            }

            if (Options.upscalerQuality == 4) {
                spatial.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    33, 100, Options.upscalerResOverride, 67,
                    v -> getGenericValueText(Text.translatable(Options.UPSCALER_RES_OVERRIDE_KEY), Text.literal(v + "%")),
                    v -> Options.setUpscalerResOverride(v, true)));
            }
        }

        SettingsSection frameRate = panel.addSection("Frame Rate Upscaling");
        if (Options.isFrameGenSupported()) {
            SelectionDropdownWidget fgModeDropdown = new SelectionDropdownWidget(
                0, 0, 150, 20, "Frame Generation",
                new String[]{"Off", "On", "Auto"},
                Options.frameGenMode, value -> {
                    Options.setFrameGenMode(value, true);
                    screen.refreshContent();
                });

            int maxMulti = Options.getFrameGenMaxMultiplier();
            if (Options.frameGenMode == 1 && maxMulti > 1) {
                String[] multiNames = new String[maxMulti];
                for (int i = 0; i < maxMulti; i++) multiNames[i] = (i + 2) + "x";
                SelectionDropdownWidget fgMultiDropdown = new SelectionDropdownWidget(
                    0, 0, 150, 20, "FG Multiplier",
                    multiNames, Options.frameGenMultiplier - 1,
                    value -> Options.setFrameGenMultiplier(value + 1, true));
                frameRate.addTwoWidgets(fgModeDropdown, fgMultiDropdown)
                         .tooltip("Generates interpolated frames between real renders. Requires Reflex for stable pacing.");
            } else {
                frameRate.addTwoWidgets(fgModeDropdown, null)
                         .tooltip(Options.frameGenMode == 2
                             ? "Auto dynamically varies frame generation multiplier based on scene load."
                             : "Generates interpolated frames between real renders. Requires Reflex for stable pacing.");
            }
            frameRate.addInfo("Reflex Dependency",
                Options.isReflexSupported() ? "Supported; Frame Pacing controls the Reflex toggle." : "Unsupported on this system.");
        } else {
            frameRate.addInfo("DLSS Frame Generation", "Unsupported on this system.");
            frameRate.addInfo("Reflex Dependency",
                Options.isReflexSupported() ? "Reflex supported, but Frame Generation is unavailable." : "Reflex unavailable.");
        }

        SettingsSection output = panel.addSection("Output Filter");
        SelectionDropdownWidget sharpenerDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Sharpener",
            new String[]{"None", "CAS", "RCAS"},
            Options.sharpenerMode, value -> {
                Options.setSharpenerMode(value, true);
                screen.refreshContent();
            });

        SimpleOption<Boolean> ssaa4x = SimpleOption.ofBoolean(
            Options.OUTPUT_SCALE_2X_KEY,
            SimpleOption.constantTooltip(Text.translatable("options.video.output_scale_2x.tooltip")),
            Options.outputScale2x,
            value -> Options.setOutputScale2x(value, true));

        output.addTwoWidgets(sharpenerDropdown, ssaa4x.createWidget(gameOptions))
              .tooltip("Output scale / SSAA changes final output resolution. CAS and RCAS sharpen the reconstructed image.");

        if (Options.sharpenerMode != 0) {
            output.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(Text.translatable(Options.CAS_SHARPNESS_KEY), Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true)));
        }
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("Upscaler Mode", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("DLSS-D Enabled", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Upscaler Quality", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("RR Model", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Frame Generation", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("FG Multiplier", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("4x SSAA", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Resolution Override", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Sharpener", category, nodeId, true),
            new UnifiedSearchOverlay.SearchEntry("Sharpness", category, nodeId, false)
        );
    }
}
