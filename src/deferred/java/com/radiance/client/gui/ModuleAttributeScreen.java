package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ModuleAttributeScreen extends Screen {

    private static final int OK_BORDER = 0xFF34D058;
    private static final int BAD_BORDER = 0xFFE5534B;

    private static final int HEADER_HEIGHT = 32;
    private static final String MODULE_ATTRIBUTE_SCREEN_NO_ATTRIBUTES = "module_attribute_screen.no_attributes";
    private final Screen parent;
    private final Module module;
    private final List<Row> rows = new ArrayList<>();
    private int scrollY = 0;

    public ModuleAttributeScreen(Screen parent, Module module) {
        super(Text.translatable(module.name));
        this.parent = parent;
        this.module = module;
    }

    @Override
    protected void init() {
        addDrawableChild(
            ButtonWidget.builder(Text.translatable("Back"), button -> close())
                .dimensions(10, 6, 60, 20)
                .build());

        rows.clear();

        List<AttributeConfig> list = module.attributeConfigs;
        if (list == null || list.isEmpty()) {
            return;
        }

        for (AttributeConfig cfg : list) {
            List<ClickableWidget> ws = buildWidgets(cfg);
            for (ClickableWidget w : ws) {
                addDrawableChild(w);
            }
            rows.add(new Row(cfg, ws));
        }
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawTextWithShadow(textRenderer, Text.translatable(module.name), 10,
            HEADER_HEIGHT + 8, 0xFFEAEAEA);

        if (rows.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "module_attribute_screen.no_attributes", 10,
                60, 0xFFB0B0B0);
            return;
        }

        int baseY = (HEADER_HEIGHT + 28) + scrollY;
        int rowH = 22;

        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = baseY + i * rowH;

            context.drawTextWithShadow(textRenderer, Text.translatable(row.cfg.name), 20, y + 6,
                0xFFD0D0D0);

            boolean visible = y >= (HEADER_HEIGHT + 18) && y <= (this.height - 24);
            if (visible) {
                context.drawTextWithShadow(textRenderer, Text.translatable(row.cfg.name), 20, y + 6,
                    0xFFD0D0D0);
            }

            layoutRowWidgets(row, y);

            String type = row.cfg.type == null ? "" : row.cfg.type.toLowerCase(Locale.ROOT);

            boolean doBorder = shouldValidateBorder(type);

            for (ClickableWidget w : row.widgets) {
                w.visible = visible;
                w.active = visible;

                if (!doBorder) {
                    continue;
                }

                boolean ok = true;

                if (type.equals("vec3")) {
                    if (w instanceof TextFieldWidget tf) {
                        ok = isStrictFloat(tf.getText());
                    }
                } else if (type.equals("int")) {
                    if (w instanceof TextFieldWidget tf) {
                        ok = isStrictInt(tf.getText());
                    }
                } else if (type.equals("float")) {
                    if (w instanceof TextFieldWidget tf) {
                        ok = isStrictFloat(tf.getText());
                    }
                } else { // string and other types
                    ok = true;
                }

                int bx = w.getX() - 1;
                int by = w.getY() - 1;
                int bw = w.getWidth() + 2;
                int bh = w.getHeight() + 2;
                drawBorder(context, bx, by, bw, bh, ok ? OK_BORDER : BAD_BORDER);
            }
        }
    }

    private boolean shouldValidateBorder(String type) {
        return type.equals("int") || type.equals("float") || type.equals("string") || type.equals(
            "vec3");
    }

    private boolean isStrictInt(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isStrictFloat(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void layoutRowWidgets(Row row, int y) {
        int x = 200;

        if (row.widgets.size() == 1) {
            ClickableWidget w = row.widgets.get(0);
            w.setX(x);
            w.setY(y);
            w.setWidth(160);
            return;
        }

        if (row.widgets.size() == 3) {
            int w = 52;
            int gap = 2;
            for (int i = 0; i < 3; i++) {
                ClickableWidget cw = row.widgets.get(i);
                cw.setX(x + i * (w + gap));
                cw.setY(y);
                cw.setWidth(w);
            }
        }
    }

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount,
        double verticalAmount) {
        int rowH = 22;
        int contentH = 60 + rows.size() * rowH + 20;
        int minScroll = Math.min(0, this.height - contentH);

        scrollY += (int) (verticalAmount * 10);
        if (scrollY > 0) {
            scrollY = 0;
        }
        if (scrollY < minScroll) {
            scrollY = minScroll;
        }
        return true;
    }

    private List<ClickableWidget> buildWidgets(AttributeConfig cfg) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return List.of(buildEnumWidget(cfg, cfg.type.substring(5)));
        }

        if (type.startsWith("int_range:")) {
            return List.of(buildIntRange(cfg, cfg.type.substring(10)));
        }

        if (type.startsWith("float_range:")) {
            return List.of(buildFloatRange(cfg, cfg.type.substring(12)));
        }

        return switch (type) {
            case "bool" -> List.of(buildBoolWidget(cfg));
            case "int" -> List.of(buildIntWidget(cfg));
            case "float" -> List.of(buildFloatWidget(cfg));
            case "string" -> List.of(buildStringWidget(cfg));
            case "vec3" -> buildVec3Widget(cfg);
            default -> List.of(buildStringWidget(cfg));
        };
    }

    private ClickableWidget buildBoolWidget(AttributeConfig cfg) {
        boolean b = "true".equalsIgnoreCase(cfg.value);
        return ButtonWidget.builder(Text.translatable(b ? "true" : "false"), btn -> {
            boolean nv = !"true".equalsIgnoreCase(cfg.value);
            cfg.value = nv ? "true" : "false";
            btn.setMessage(Text.translatable(cfg.value));
        }).dimensions(200, 0, 160, 20).build();
    }

    private ClickableWidget buildEnumWidget(AttributeConfig cfg, String raw) {
        String[] values = raw.isEmpty() ? new String[]{"<empty>"} : raw.split("-");
        int idx = 0;
        if (cfg.value != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(cfg.value)) {
                    idx = i;
                    break;
                }
            }
        } else {
            cfg.value = values[0];
        }

        int[] index = new int[]{idx};
        return ButtonWidget.builder(Text.translatable(values[index[0]]), btn -> {
            index[0] = (index[0] + 1) % values.length;
            cfg.value = values[index[0]];
            btn.setMessage(Text.translatable(cfg.value));
        }).dimensions(200, 0, 160, 20).build();
    }

    private ClickableWidget buildIntWidget(AttributeConfig cfg) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 200, 0, 160, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(s -> s.isEmpty() || s.equals("-") || s.matches("-?\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictInt(text)) {
                cfg.value = text;
            }
        });
        return tf;
    }

    private ClickableWidget buildFloatWidget(AttributeConfig cfg) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 200, 0, 160, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+") || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictFloat(text)) {
                cfg.value = text;
            }
        });
        return tf;
    }

    private ClickableWidget buildStringWidget(AttributeConfig cfg) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 200, 0, 160, 20, Text.empty());
        tf.setMaxLength(128);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setChangedListener(text -> cfg.value = text);
        return tf;
    }

    private List<ClickableWidget> buildVec3Widget(AttributeConfig cfg) {
        if (cfg.value == null || cfg.value.isEmpty()) {
            cfg.value = "0,0,0";
        }

        float[] v = parseVec3(cfg.value);
        TextFieldWidget x = vecField(v[0]);
        TextFieldWidget y = vecField(v[1]);
        TextFieldWidget z = vecField(v[2]);

        Runnable syncIfValid = () -> {
            String sx = x.getText();
            String sy = y.getText();
            String sz = z.getText();

            if (isStrictFloat(sx) && isStrictFloat(sy) && isStrictFloat(sz)) {
                cfg.value = sx + "," + sy + "," + sz;
            }
        };

        x.setChangedListener(s -> syncIfValid.run());
        y.setChangedListener(s -> syncIfValid.run());
        z.setChangedListener(s -> syncIfValid.run());

        syncIfValid.run();
        return List.of(x, y, z);
    }

    private TextFieldWidget vecField(float v) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, 52, 20, Text.empty());
        tf.setMaxLength(32);
        tf.setText(trimFloat(v));
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches(
                "-?\\d+") || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        return tf;
    }

    private ClickableWidget buildIntRange(AttributeConfig cfg, String raw) {
        Range r = parseRange(raw);
        int start = (int) r.start;
        int end = (int) r.end;
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }

        int cur = start;
        if (isInt(cfg.value)) {
            cur = Integer.parseInt(cfg.value);
        } else {
            cfg.value = String.valueOf(start);
        }
        cur = MathHelper.clamp(cur, start, end);

        IntRangeSlider slider = new IntRangeSlider(200, 0, 160, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private ClickableWidget buildFloatRange(AttributeConfig cfg, String raw) {
        Range r = parseRange(raw);
        float start = (float) r.start;
        float end = (float) r.end;
        if (start > end) {
            float t = start;
            start = end;
            end = t;
        }

        float cur = start;
        if (isFloat(cfg.value)) {
            cur = Float.parseFloat(cfg.value);
        } else {
            cfg.value = trimFloat(start);
        }
        cur = MathHelper.clamp(cur, start, end);

        FloatRangeSlider slider = new FloatRangeSlider(200, 0, 160, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private boolean isValueValid(AttributeConfig cfg, List<ClickableWidget> widgets) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:") || type.startsWith("int_range:") || type.startsWith(
            "float_range:") || type.equals("bool") || type.equals("string")) {
            return true;
        }

        if (type.equals("int")) {
            return cfg.value == null || cfg.value.isEmpty() || isInt(cfg.value);
        }

        if (type.equals("float")) {
            return cfg.value == null || cfg.value.isEmpty() || isFloat(cfg.value);
        }

        if (type.equals("vec3")) {
            if (widgets.size() != 3) {
                return false;
            }
            for (ClickableWidget w : widgets) {
                if (w instanceof TextFieldWidget tf) {
                    String s = tf.getText();
                    if (!(s.isEmpty() || isFloat(s))) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
            return true;
        }

        return true;
    }

    private boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isFloat(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private float[] parseVec3(String s) {
        if (s == null || s.isEmpty()) {
            return new float[]{0, 0, 0};
        }

        String[] p = s.split("[,\\s]+");
        float x = p.length > 0 && isFloat(p[0]) ? Float.parseFloat(p[0]) : 0;
        float y = p.length > 1 && isFloat(p[1]) ? Float.parseFloat(p[1]) : 0;
        float z = p.length > 2 && isFloat(p[2]) ? Float.parseFloat(p[2]) : 0;
        return new float[]{x, y, z};
    }

    private String trimFloat(float v) {
        String s = Float.toString(v);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }

    private Range parseRange(String raw) {
        int dash = raw.lastIndexOf('-');
        if (dash <= 0) {
            return new Range(0, 1);
        }
        String a = raw.substring(0, dash);
        String b = raw.substring(dash + 1);
        double start = 0;
        double end = 1;
        try {
            start = Double.parseDouble(a);
            end = Double.parseDouble(b);
        } catch (Exception ignored) {
        }
        return new Range(start, end);
    }

    private record Range(double start, double end) {

    }

    private record Row(AttributeConfig cfg, List<ClickableWidget> widgets) {

    }

    private static class IntRangeSlider extends SliderWidget {

        private final int start;
        private final int end;
        private final AttributeConfig cfg;

        public IntRangeSlider(int x, int y, int width, int height, int start, int end, int cur,
            AttributeConfig cfg) {
            super(x, y, width, height, Text.empty(),
                (cur - (double) start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.cfg = cfg;
            this.value = (cur - (double) start) / (double) (end - start);
        }

        private int current() {
            if (end == start) {
                return start;
            }
            return start + (int) Math.round(this.value * (end - start));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(Integer.toString(current())));
        }

        @Override
        protected void applyValue() {
            int v = MathHelper.clamp(current(), start, end);
            cfg.value = Integer.toString(v);
        }
    }

    private static class FloatRangeSlider extends SliderWidget {

        private final float start;
        private final float end;
        private final AttributeConfig cfg;

        public FloatRangeSlider(int x, int y, int width, int height, float start, float end,
            float cur, AttributeConfig cfg) {
            super(x, y, width, height, Text.empty(), (cur - start) / (double) (end - start));
            this.start = start;
            this.end = end;
            this.cfg = cfg;
            this.value = (cur - start) / (double) (end - start);
        }

        private float current() {
            if (end == start) {
                return start;
            }
            return (float) (start + this.value * (end - start));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(trim(current())));
        }

        @Override
        protected void applyValue() {
            float v = MathHelper.clamp(current(), start, end);
            cfg.value = trim(v);
        }

        private String trim(float v) {
            String s = Float.toString(v);
            if (s.endsWith(".0")) {
                return s.substring(0, s.length() - 2);
            }
            return s;
        }
    }
}
