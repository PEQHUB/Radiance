package com.radiance.client.gui.unified.populators;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.ContentPopulator;
import com.radiance.client.gui.unified.RadianceUnifiedScreen;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.gui.unified.UnifiedSearchOverlay;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.text.Text;

public class FreecamPopulator implements ContentPopulator {
    @Override
    public void populate(ContentPanelWidget panel, RadianceUnifiedScreen screen) {
        SettingsSection freecam = panel.addSection("Freecam");

        SimpleOption<Boolean> freecamToggle = SimpleOption.ofBoolean(
            "Freecam Mode",
            Options.freecamEnabled,
            value -> Options.freecamEnabled = value);
        freecam.addToggle(freecamToggle.createWidget(MinecraftClient.getInstance().options));

        SimpleOption<Boolean> showPlayerToggle = SimpleOption.ofBoolean(
            "Show Player",
            Options.freecamShowPlayer,
            value -> Options.freecamShowPlayer = value);
        freecam.addToggle(showPlayerToggle.createWidget(MinecraftClient.getInstance().options));

        int speedInt = Math.round(Options.freecamSpeed * 10.0f);
        freecam.addSlider(new ResettableSliderWidget(
            0, 0, 150, 20,
            1, 500, speedInt, 100,
            v -> getGenericValueText(Text.literal("Movement Speed"),
                Text.literal(String.format("%.1fx", v / 10.0))),
            v -> Options.freecamSpeed = v / 10.0f));
    }

    @Override
    public List<UnifiedSearchOverlay.SearchEntry> getSearchEntries(String nodeId, String category) {
        return List.of(
            new UnifiedSearchOverlay.SearchEntry("Freecam Mode", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Freecam Show Player", category, nodeId, false),
            new UnifiedSearchOverlay.SearchEntry("Freecam Movement Speed", category, nodeId, false)
        );
    }
}
