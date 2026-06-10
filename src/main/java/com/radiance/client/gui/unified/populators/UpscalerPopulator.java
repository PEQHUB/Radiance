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
            spatial.tooltip("Opens DLSS runtime installation instructions. DLSS-RR and DLSS-G need the required NVIDIA runtime files.");
        }

        SelectionDropdownWidget upscalerModeDropdown = new SelectionDropdownWidget(
            0, 0, 150, 20, "Upscaler",
            new String[]{"DLSS-RR", "Off"},
            Options.upscalerMode == 2 ? 1 : 0, value -> {
                Options.setUpscalerMode(value == 0 ? 0 : 2, true);
                screen.refreshContent();
            });

        if (Options.upscalerMode == 0) {
            SimpleOption<Boolean> dlssDToggle = SimpleOption.ofBoolean(
                "options.video.dlss_d_enabled", Options.dlssDEnabled,
                value -> Options.setDlssDEnabled(value, true));

            spatial.addTwoWidgets(upscalerModeDropdown, dlssDToggle.createWidget(gameOptions))
                  .tooltip("Upscaler selects reconstruction backend. DLSS-RR uses NVIDIA ray reconstruction; Off disables reconstruction. DLSS-D Enabled enables DLSS denoising without spatial upscaling when DLSS-RR mode is active.");
        } else {
            spatial.addTwoWidgets(upscalerModeDropdown, null)
                  .tooltip("Upscaler selects reconstruction backend. DLSS-RR uses NVIDIA ray reconstruction; Off disables reconstruction.");
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
                       .tooltip("Quality controls render resolution preset. Custom exposes a manual resolution percentage. RR Model selects the DLSS Ray Reconstruction model preset.");
            } else {
                spatial.addTwoWidgets(qualityDropdown, null)
                       .tooltip("Quality controls render resolution preset. Custom exposes a manual resolution percentage.");
            }

            if (Options.upscalerQuality == 4) {
                spatial.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                    1, 100, Options.upscalerResOverride, 67,
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
                         .tooltip("Frame Generation generates interpolated frames between real renders. Requires supported hardware and stable pacing. FG Multiplier is the number of output frames per real rendered frame. Higher multipliers need stronger hardware support.");
            } else {
                frameRate.addTwoWidgets(fgModeDropdown, null)
                         .tooltip(Options.frameGenMode == 2
                             ? "Auto dynamically varies frame generation multiplier based on scene load."
                             : "Frame Generation generates interpolated frames between real renders. Requires supported hardware and stable pacing.");
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
              .tooltip("Sharpener selects output sharpening filter. CAS/RCAS sharpen the reconstructed image; None disables sharpening. Output Scale 2x renders final output at 2x display resolution for supersampled screenshots or sharper output.");

        if (Options.sharpenerMode != 0) {
            output.addSlider(new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.casSharpnessPercent, 50,
                v -> getGenericValueText(Text.translatable(Options.CAS_SHARPNESS_KEY), Text.literal(v + "%")),
                v -> Options.setCasSharpnessPercent(v, true)));
            output.tooltip("CAS/RCAS Sharpness sharpening intensity for the selected output filter. Too high can create halos or shimmer.");
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
