package com.radiance.client.gui;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class DlssMissingScreen extends Screen {

    private final Screen parent;

    /** Ticks remaining to show the "Copied!" confirmation, or 0 if not showing. */
    private int copiedTicks = 0;
    private String copiedLabel = "";

    public DlssMissingScreen(Screen parent) {
        super(Text.translatable("radiance.dlss_missing.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Copy URL button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("radiance.dlss_missing.copy_url"),
                btn -> {
                    this.client.keyboard.setClipboard(RadianceClient.dlssDownloadUrl);
                    copiedLabel = "radiance.dlss_missing.copy_url";
                    copiedTicks = 40;
                })
            .dimensions(this.width / 2 - 155, this.height / 2 + 40, 150, 20)
            .build());

        // Copy install path button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("radiance.dlss_missing.copy_path"),
                btn -> {
                    this.client.keyboard.setClipboard(
                        RadianceClient.dlssInstallDir.toAbsolutePath().toString());
                    copiedLabel = "radiance.dlss_missing.copy_path";
                    copiedTicks = 40;
                })
            .dimensions(this.width / 2 + 5, this.height / 2 + 40, 150, 20)
            .build());

        // Re-check button — lets user engage DLSS after copying DLLs while game is running
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("radiance.dlss_missing.recheck"),
                btn -> {
                    boolean found = RadianceClient.recheckDlssFiles();
                    if (found) {
                        // Re-run module collection so DLSS is registered now that the DLL is present,
                        // then rebuild the pipeline to activate it.
                        Pipeline.recollectNativeModules();
                        Pipeline.assembleDefault();
                        Pipeline.savePipeline();
                        Pipeline.build();
                        this.client.setScreen(this.parent);
                    } else {
                        copiedLabel = "radiance.dlss_missing.recheck_failed";
                        copiedTicks = 60;
                    }
                })
            .dimensions(this.width / 2 - 75, this.height / 2 + 68, 150, 20)
            .build());

        // OK button
        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("gui.ok"),
                btn -> this.client.setScreen(this.parent))
            .dimensions(this.width / 2 - 75, this.height / 2 + 92, 150, 20)
            .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderBackground(context, mouseX, mouseY, delta);

        // Title
        context.drawCenteredTextWithShadow(
            this.textRenderer, this.title,
            this.width / 2, this.height / 2 - 80, 0xFF5555);

        // Body lines
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("radiance.dlss_missing.line1"),
            this.width / 2, this.height / 2 - 58, 0xFFFFFF);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("radiance.dlss_missing.line2"),
            this.width / 2, this.height / 2 - 44, 0xFFFFFF);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("radiance.dlss_missing.line3"),
            this.width / 2, this.height / 2 - 24, 0xFFFFFF);

        // URL (cyan, selectable-looking)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(RadianceClient.dlssDownloadUrl),
            this.width / 2, this.height / 2 - 10, 0x55FFFF);

        // Install path (gold/yellow)
        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.translatable("radiance.dlss_missing.line4"),
            this.width / 2, this.height / 2 + 4, 0xFFFFFF);

        context.drawCenteredTextWithShadow(
            this.textRenderer,
            Text.literal(RadianceClient.dlssInstallDir.toAbsolutePath().toString()),
            this.width / 2, this.height / 2 + 14, 0xFFAA00);

        // Confirmation flash (copied / recheck failed)
        if (copiedTicks > 0) {
            boolean isError = copiedLabel.equals("radiance.dlss_missing.recheck_failed");
            context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.translatable(copiedLabel.isEmpty() ? "radiance.dlss_missing.copied" : copiedLabel),
                this.width / 2, this.height / 2 + 118, isError ? 0xFF5555 : 0x55FF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        if (copiedTicks > 0) copiedTicks--;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
