package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ShaderPackSettingsScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;
    private static final int ROW_LEFT = 20;
    private static final int ROW_RIGHT = 20;
    private static final int WIDGET_WIDTH = 170;
    private static final int VEC3_COMPONENT_WIDTH = 54;
    private static final int VEC3_GAP = 2;
    private static final int HEADER_HEIGHT = 32;

    private final Screen parent;
    private final List<Row> rows = new ArrayList<>();
    private Module module;
    private int scrollY = 0;

    public ShaderPackSettingsScreen(Screen parent) {
        super(Text.translatable("shader_pack_settings_screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("render_pipeline_screen.back"), button -> close())
                .dimensions(10, 6, 60, 20)
                .build());

        rows.clear();
        module = Pipeline.getRayTracingModule();
        List<AttributeConfig> list = Pipeline.getRayTracingShaderPackAttributes();
        if (module == null || list.isEmpty()) {
            return;
        }

        for (AttributeConfig cfg : list) {
            List<ClickableWidget> widgets = AttributeWidgetUtil.buildWidgets(module, cfg, textRenderer,
                WIDGET_WIDTH, VEC3_COMPONENT_WIDTH);
            for (ClickableWidget widget : widgets) {
                addDrawableChild(widget);
            }
            rows.add(new Row(cfg, widgets));
        }
    }

    @Override
    public void close() {
        if (module != null) {
            Pipeline.savePipeline();
            Pipeline.build();
        }
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, RadianceTheme.panelBg);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        Text title = Text.translatable("shader_pack_settings_screen.title");
        RadianceTheme.drawOutlinedText(context, textRenderer, title, 10, HEADER_HEIGHT + 8,
            RadianceTheme.textPrimary);

        if (rows.isEmpty()) {
            RadianceTheme.drawOutlinedText(context, textRenderer,
                Text.translatable("shader_pack_settings_screen.no_attributes"), 10, 60,
                RadianceTheme.textSecondary);
            return;
        }

        int baseY = (HEADER_HEIGHT + 28) + scrollY;
        int rowH = 22;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = baseY + i * rowH;
            boolean visible = y >= (HEADER_HEIGHT + 18) && y <= (this.height - 24);
            if (visible) {
                RadianceTheme.drawOutlinedText(context, textRenderer, module.translateText(row.cfg.name),
                    ROW_LEFT, y + 6, RadianceTheme.textPrimary);
            }

            layoutRowWidgets(row, y);
            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);
            boolean doBorder = AttributeWidgetUtil.shouldValidateBorder(type);

            for (ClickableWidget widget : row.widgets) {
                widget.visible = visible;
                widget.active = visible;

                if (!doBorder) {
                    continue;
                }

                boolean ok = true;
                if (type.equals("vec3")) {
                    if (widget instanceof TextFieldWidget tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getText());
                    }
                } else if (type.equals("int")) {
                    if (widget instanceof TextFieldWidget tf) {
                        ok = AttributeWidgetUtil.isStrictInt(tf.getText());
                    }
                } else if (type.equals("float")) {
                    if (widget instanceof TextFieldWidget tf) {
                        ok = AttributeWidgetUtil.isStrictFloat(tf.getText());
                    }
                }

                if (widget instanceof TextFieldWidget tf) {
                    int color = ok ? OK_BORDER : BAD_BORDER;
                    AttributeWidgetUtil.drawBorder(context, tf.getX(), tf.getY(), tf.getWidth(),
                        tf.getHeight(), color);
                }
            }
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int widgetWidth = AttributeWidgetUtil.totalWidgetWidth(row.widgets, WIDGET_WIDTH,
            VEC3_COMPONENT_WIDTH, VEC3_GAP);
        int x = this.width - ROW_RIGHT - widgetWidth;
        AttributeWidgetUtil.layoutWidgets(row.widgets, x, y, WIDGET_WIDTH, VEC3_COMPONENT_WIDTH,
            VEC3_GAP);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
        double verticalAmount) {
        if (rows.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int rowH = 22;
        int contentH = rows.size() * rowH;
        int viewportH = this.height - (HEADER_HEIGHT + 56);
        int minScroll = Math.min(0, viewportH - contentH);
        scrollY += (int) (verticalAmount * 18);
        if (scrollY > 0) {
            scrollY = 0;
        }
        if (scrollY < minScroll) {
            scrollY = minScroll;
        }
        return true;
    }

    private record Row(AttributeConfig cfg, List<ClickableWidget> widgets) {
    }
}
