package com.radiance.client.gui;

import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.AttributeConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public final class AttributeWidgetUtil {

    private static final Pattern RANGE_PATTERN = Pattern.compile(
        "\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*-\\s*([+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+))\\s*");

    private AttributeWidgetUtil() {
    }

    public static boolean shouldValidateBorder(String type) {
        return type.equals("int") || type.equals("float") || type.equals("string") || type.equals("vec3");
    }

    public static boolean isStrictInt(String s) {
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

    public static boolean isStrictFloat(String s) {
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

    public static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void layoutWidgets(List<ClickableWidget> widgets, int x, int y, int singleWidth,
        int tripleWidth, int gap) {
        if (widgets.size() == 1) {
            ClickableWidget w = widgets.get(0);
            w.setX(x);
            w.setY(y);
            w.setWidth(singleWidth);
            return;
        }

        if (widgets.size() == 3) {
            for (int i = 0; i < 3; i++) {
                ClickableWidget cw = widgets.get(i);
                cw.setX(x + i * (tripleWidth + gap));
                cw.setY(y);
                cw.setWidth(tripleWidth);
            }
        }
    }

    public static int totalWidgetWidth(List<ClickableWidget> widgets, int singleWidth, int tripleWidth, int gap) {
        if (widgets.size() == 3) {
            return (tripleWidth * 3) + (gap * 2);
        }
        return singleWidth;
    }

    public static List<ClickableWidget> buildWidgets(Module module, AttributeConfig cfg, TextRenderer textRenderer,
        int width,
        int vec3ComponentWidth) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return List.of(buildEnumWidget(module, cfg, cfg.type.substring(5), width));
        }

        if (type.startsWith("int_range:")) {
            return List.of(buildIntRange(cfg, cfg.type.substring(10), width));
        }

        if (type.startsWith("float_range:")) {
            return List.of(buildFloatRange(cfg, cfg.type.substring(12), width));
        }

        return switch (type) {
            case "bool" -> List.of(buildBoolWidget(module, cfg, width));
            case "int" -> List.of(buildIntWidget(cfg, textRenderer, width));
            case "float" -> List.of(buildFloatWidget(cfg, textRenderer, width));
            case "string" -> List.of(buildStringWidget(cfg, textRenderer, width));
            case "vec3" -> buildVec3Widget(cfg, textRenderer, vec3ComponentWidth);
            default -> List.of(buildStringWidget(cfg, textRenderer, width));
        };
    }

    public static ClickableWidget buildCompactWidget(Module module, AttributeConfig cfg,
        TextRenderer textRenderer, int width, Runnable onCommit) {
        String type = cfg.type == null ? "" : cfg.type.toLowerCase(Locale.ROOT);

        if (type.startsWith("enum:")) {
            return buildCompactEnumWidget(module, cfg, cfg.type.substring(5), width, onCommit);
        }

        if (type.startsWith("int_range:")) {
            return buildCompactIntRange(module, cfg, cfg.type.substring(10), width, onCommit);
        }

        if (type.startsWith("float_range:")) {
            return buildCompactFloatRange(module, cfg, cfg.type.substring(12), width, onCommit);
        }

        return switch (type) {
            case "bool" -> buildCompactBoolWidget(module, cfg, width, onCommit);
            case "int", "float", "string" -> buildCompactTextWidget(module, cfg, textRenderer, width, onCommit);
            default -> buildCompactTextWidget(module, cfg, textRenderer, width, onCommit);
        };
    }

    private static ClickableWidget buildCompactBoolWidget(Module module, AttributeConfig cfg,
        int width, Runnable onCommit) {
        boolean b = isTrueValue(cfg.value);
        cfg.value = b ? "render_pipeline.true" : "render_pipeline.false";
        return ButtonWidget.builder(compactMessage(module, cfg.name, cfg.value), btn -> {
            boolean nv = !isTrueValue(cfg.value);
            cfg.value = nv ? "render_pipeline.true" : "render_pipeline.false";
            btn.setMessage(compactMessage(module, cfg.name, cfg.value));
            commit(onCommit);
        }).dimensions(0, 0, width, 20).build();
    }

    private static ClickableWidget buildCompactEnumWidget(Module module, AttributeConfig cfg,
        String raw, int width, Runnable onCommit) {
        String[] parsedValues = enumValues(raw);
        if (parsedValues.length == 0) {
            parsedValues = new String[]{"<empty>"};
        }
        final String[] values = parsedValues;

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

        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = module.translateText(values[i]).getString();
        }

        return new SelectionDropdownWidget(0, 0, width, 20,
            module.translateText(cfg.name).getString(), labels, idx, value -> {
                cfg.value = values[value];
                commit(onCommit);
            });
    }

    private static ClickableWidget buildCompactIntRange(Module module, AttributeConfig cfg,
        String raw, int width, Runnable onCommit) {
        Range r = parseRange(raw);
        int start = (int) Math.round(r.start);
        int end = (int) Math.round(r.end);
        if (start > end) {
            int t = start;
            start = end;
            end = t;
        }

        int cur = isInt(cfg.value) ? Integer.parseInt(cfg.value) : start;
        cur = MathHelper.clamp(cur, start, end);
        cfg.value = String.valueOf(cur);
        String defaultValue = Pipeline.getRayTracingShaderPackAttributeDefault(module, cfg);
        int stockDefault = isInt(defaultValue) ? MathHelper.clamp(Integer.parseInt(defaultValue), start, end) : cur;

        ResettableSliderWidget slider = new ResettableSliderWidget(0, 0, width, 20,
            start, end, cur, stockDefault,
            v -> compactMessage(module, cfg.name, String.valueOf(v)),
            v -> cfg.value = String.valueOf(v));
        slider.setOnRelease(() -> commit(onCommit));
        return slider;
    }

    private static ClickableWidget buildCompactFloatRange(Module module, AttributeConfig cfg,
        String raw, int width, Runnable onCommit) {
        Range r = parseRange(raw);
        float start = (float) r.start;
        float end = (float) r.end;
        if (start > end) {
            float t = start;
            start = end;
            end = t;
        }

        float cur = isFloat(cfg.value) ? Float.parseFloat(cfg.value) : start;
        cur = MathHelper.clamp(cur, start, end);
        cfg.value = trimFloat(cur);
        String defaultValue = Pipeline.getRayTracingShaderPackAttributeDefault(module, cfg);
        float stockDefault = isFloat(defaultValue) ? MathHelper.clamp(Float.parseFloat(defaultValue), start, end) : cur;
        return new CompactFloatRangeSlider(0, 0, width, 20, start, end, cur, stockDefault, module, cfg, onCommit);
    }

    private static ClickableWidget buildCompactTextWidget(Module module, AttributeConfig cfg,
        TextRenderer textRenderer, int width, Runnable onCommit) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20,
            module.translateText(cfg.name));
        tf.setMaxLength(128);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setChangedListener(text -> {
            cfg.value = text;
            commit(onCommit);
        });
        return tf;
    }

    private static ClickableWidget buildBoolWidget(Module module, AttributeConfig cfg, int width) {
        boolean b = isTrueValue(cfg.value);
        cfg.value = b ? "render_pipeline.true" : "render_pipeline.false";
        return ButtonWidget.builder(
            module.translateText(cfg.value), btn -> {
                boolean nv = !isTrueValue(cfg.value);
                cfg.value = nv ? "render_pipeline.true" : "render_pipeline.false";
                btn.setMessage(module.translateText(cfg.value));
            }).dimensions(0, 0, width, 20).build();
    }

    private static boolean isTrueValue(String value) {
        return "true".equalsIgnoreCase(value) || "render_pipeline.true".equalsIgnoreCase(value);
    }

    private static ClickableWidget buildEnumWidget(Module module, AttributeConfig cfg, String raw, int width) {
        String[] parsedValues = enumValues(raw);
        if (parsedValues.length == 0) {
            parsedValues = new String[]{"<empty>"};
        }
        final String[] values = parsedValues;
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
        return ButtonWidget.builder(module.translateText(values[index[0]]), btn -> {
            index[0] = (index[0] + 1) % values.length;
            cfg.value = values[index[0]];
            btn.setMessage(module.translateText(cfg.value));
        }).dimensions(0, 0, width, 20).build();
    }

    private static ClickableWidget buildIntWidget(AttributeConfig cfg, TextRenderer textRenderer, int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
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

    private static ClickableWidget buildFloatWidget(AttributeConfig cfg, TextRenderer textRenderer, int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(64);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches("-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        tf.setChangedListener(text -> {
            if (isStrictFloat(text)) {
                cfg.value = text;
            }
        });
        return tf;
    }

    private static ClickableWidget buildStringWidget(AttributeConfig cfg, TextRenderer textRenderer, int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(128);
        tf.setText(cfg.value == null ? "" : cfg.value);
        tf.setChangedListener(text -> cfg.value = text);
        return tf;
    }

    private static List<ClickableWidget> buildVec3Widget(AttributeConfig cfg,
        TextRenderer textRenderer,
        int componentWidth) {
        if (cfg.value == null || cfg.value.isEmpty()) {
            cfg.value = "0,0,0";
        }

        float[] v = parseVec3(cfg.value);
        TextFieldWidget x = vecField(textRenderer, v[0], componentWidth);
        TextFieldWidget y = vecField(textRenderer, v[1], componentWidth);
        TextFieldWidget z = vecField(textRenderer, v[2], componentWidth);

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

    private static TextFieldWidget vecField(TextRenderer textRenderer, float v, int width) {
        TextFieldWidget tf = new TextFieldWidget(textRenderer, 0, 0, width, 20, Text.empty());
        tf.setMaxLength(32);
        tf.setText(trimFloat(v));
        tf.setTextPredicate(
            s -> s.isEmpty() || s.equals("-") || s.equals(".") || s.equals("-.") || s.matches("-?\\d+")
                || s.matches("-?\\d+\\.") || s.matches("-?\\d*\\.\\d+"));
        return tf;
    }

    private static ClickableWidget buildIntRange(AttributeConfig cfg, String raw, int width) {
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

        IntRangeSlider slider = new IntRangeSlider(0, 0, width, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private static ClickableWidget buildFloatRange(AttributeConfig cfg, String raw, int width) {
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

        FloatRangeSlider slider = new FloatRangeSlider(0, 0, width, 20, start, end, cur, cfg);
        slider.updateMessage();
        return slider;
    }

    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isFloat(String s) {
        try {
            Float.parseFloat(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Range parseRange(String raw) {
        Matcher matcher = RANGE_PATTERN.matcher(raw == null ? "" : raw);
        if (matcher.matches()) {
            return new Range(parseDoubleSafe(matcher.group(1), 0), parseDoubleSafe(matcher.group(2), 1));
        }
        return new Range(0, 1);
    }

    private static String[] enumValues(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new String[0];
        }
        String[] split = raw.split("-");
        List<String> values = new ArrayList<>();
        for (String value : split) {
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values.toArray(String[]::new);
    }

    private static Text compactMessage(Module module, String labelKey, String valueKey) {
        return Text.literal(module.translateText(labelKey).getString() + ": "
            + module.translateText(valueKey).getString());
    }

    private static void commit(Runnable onCommit) {
        if (onCommit != null) {
            onCommit.run();
        }
    }

    private static double parseDoubleSafe(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float[] parseVec3(String s) {
        String[] p = s.split(",");
        float[] v = new float[]{0, 0, 0};
        for (int i = 0; i < Math.min(3, p.length); i++) {
            try {
                v[i] = Float.parseFloat(p[i].trim());
            } catch (Exception ignored) {
                v[i] = 0;
            }
        }
        return v;
    }

    private static String trimFloat(float f) {
        if (f == (long) f) {
            return String.valueOf((long) f);
        }
        return String.valueOf(f);
    }

    private record Range(double start, double end) {
    }

    private static class CompactFloatRangeSlider extends SliderWidget {
        private final float min, max;
        private final float stockDefault;
        private final Module module;
        private final AttributeConfig cfg;
        private final Runnable onCommit;

        CompactFloatRangeSlider(int x, int y, int w, int h, float min, float max, float cur, float stockDefault,
            Module module, AttributeConfig cfg, Runnable onCommit) {
            super(x, y, w, h, Text.empty(), (max == min) ? 0.0 : (cur - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.stockDefault = stockDefault;
            this.module = module;
            this.cfg = cfg;
            this.onCommit = onCommit;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            float val = (float) (min + value * (max - min));
            setMessage(compactMessage(module, cfg.name, trimFloat(val)));
        }

        @Override
        protected void applyValue() {
            float val = (float) (min + value * (max - min));
            cfg.value = trimFloat(val);
            updateMessage();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0 && Screen.hasShiftDown() && this.isMouseOver(mouseX, mouseY)) {
                this.value = (max == min) ? 0.0 : (stockDefault - min) / (double) (max - min);
                this.value = MathHelper.clamp(this.value, 0.0, 1.0);
                applyValue();
                commit(onCommit);
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            boolean result = super.mouseReleased(mouseX, mouseY, button);
            if (button == 0) {
                commit(onCommit);
            }
            return result;
        }
    }

    private static class IntRangeSlider extends SliderWidget {
        private final int min, max;
        private final AttributeConfig cfg;

        IntRangeSlider(int x, int y, int w, int h, int min, int max, int cur, AttributeConfig cfg) {
            super(x, y, w, h, Text.empty(), (cur - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.cfg = cfg;
        }

        @Override
        protected void updateMessage() {
            int val = (int) Math.round(min + value * (max - min));
            setMessage(Text.literal(String.valueOf(val)));
        }

        @Override
        protected void applyValue() {
            int val = (int) Math.round(min + value * (max - min));
            cfg.value = String.valueOf(val);
            updateMessage();
        }
    }

    private static class FloatRangeSlider extends SliderWidget {
        private final float min, max;
        private final AttributeConfig cfg;

        FloatRangeSlider(int x, int y, int w, int h, float min, float max, float cur,
            AttributeConfig cfg) {
            super(x, y, w, h, Text.empty(), (cur - min) / (double) (max - min));
            this.min = min;
            this.max = max;
            this.cfg = cfg;
        }

        @Override
        protected void updateMessage() {
            float val = (float) (min + value * (max - min));
            setMessage(Text.literal(trimFloat(val)));
        }

        @Override
        protected void applyValue() {
            float val = (float) (min + value * (max - min));
            cfg.value = trimFloat(val);
            updateMessage();
        }
    }
}
