package com.radiance.client.gui;

import com.radiance.Radiance;
import com.radiance.client.pipeline.Module;
import com.radiance.client.pipeline.ModuleEntry;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.pipeline.config.ImageConfig;
import com.radiance.mixin_related.extensions.vulkan_render_integration.IDrawContextExt;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class RenderPipelineScreen extends Screen {

    private static final Identifier GEAR_TEX = Identifier.of(Radiance.MOD_ID,
        "textures/gui/render_pipeline/gear.png");

    private static final Map<String, Integer> FORMAT_COLORS = Map.of("R8G8B8A8_SRGB", 0xFF4EA5FF,
        "R8G8B8A8_UNORM", 0xFF62E36A, "R16G16_SFLOAT", 0xFFFF6BD6, "R16_SFLOAT", 0xFF6BE6FF,
        "R16G16B16A16_SFLOAT", 0xFFFFB84E);

    private static final int HEADER_HEIGHT = 32;
    private static final float GLOBAL_SCALE = 0.6f;
    private static final String RENDER_PIPELINE_SCREEN_BACK = "render_pipeline_screen.back";
    private static final String RENDER_PIPELINE_SCREEN_SAVE_AND_BUIld = "render_pipeline_screen.save_and_build";
    private static final String RENDER_PIPELINE_SCREEN_RELOAD = "render_pipeline_screen.reload";
    private static final String RENDER_PIPELINE_SCREEN_ADD_MODULE = "render_pipeline_screen.add_module";
    private static final String RENDER_PIPELINE_SCREEN_BACK_HINT = "render_pipeline_screen.back_hint";
    private final Screen parent;
    private final List<ModuleNode> nodes = new ArrayList<>();
    private final List<ModuleConnection> moduleConnections = new ArrayList<>();
    private ModuleNode draggedNode = null;
    private double lastMouseX, lastMouseY;
    private ModuleSelector activeSelector = null;
    private ImageConfig localFinalOutput = null;
    private ImageConfig pendingPort = null;
    private boolean isPendingOutput = false;

    public RenderPipelineScreen(Screen parent) {
        super(Text.literal("Render Pipeline"));

        this.parent = parent;
    }

    private int getFormatColor(String format) {
        Integer c = FORMAT_COLORS.get(format);
        if (c == null) {
            throw new RuntimeException("No color for image format: " + format);
        }
        return c;
    }

    @Override
    protected void init() {
        refreshPipeline();

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_BACK), button -> close())
                .dimensions(10, 6, 60, 20).build());

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_SAVE_AND_BUIld),
                button -> {
                    syncToPipeline();
                }).dimensions(80, 6, 100, 20).build());

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_RELOAD), button -> {
                refreshPipeline();
            }).dimensions(190, 6, 100, 20).build());

        addDrawableChild(
            ButtonWidget.builder(Text.translatable(RENDER_PIPELINE_SCREEN_ADD_MODULE), button -> {
                Map<String, ModuleEntry> entries = Pipeline.INSTANCE.getModuleEntries();
                if (entries != null && !entries.isEmpty()) {
                    activeSelector = new ModuleSelector(300, HEADER_HEIGHT + 4, entries);

                }
            }).dimensions(300, 6, 100, 20).build());
    }

    public void refreshPipeline() {
        nodes.clear();
        for (Module module : Pipeline.INSTANCE.getModules()) {
            nodes.add(new ModuleNode(module));
        }

        moduleConnections.clear();
        var globalConnections = Pipeline.INSTANCE.getModuleConnections();
        globalConnections.forEach((src, list) -> list.forEach(
            dst -> moduleConnections.add(new ModuleConnection(src, dst))));
        for (ModuleNode node : nodes) {
            for (ImageConfig out : node.module.outputImageConfigs) {
                if (out.finalOutput) {
                    localFinalOutput = out;
                    break;
                }
            }
        }
    }

    public void syncToPipeline() {
        Pipeline.clear();
        for (ModuleNode node : nodes) {
            Pipeline.addModule(node.module);
        }

        for (ModuleConnection moduleConnection : moduleConnections) {
            Pipeline.connect(moduleConnection.src, moduleConnection.dst);
        }

        Pipeline.build();
        refreshPipeline();
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        for (ModuleNode node : nodes) {
            node.updateWidth(textRenderer);
        }

        this.renderBackground(context, mouseX, mouseY, delta);

        context.getMatrices().push();
        context.getMatrices().scale(GLOBAL_SCALE, GLOBAL_SCALE, 1f);

        int scaledMouseX = (int) (mouseX / GLOBAL_SCALE);
        int scaledMouseY = (int) (mouseY / GLOBAL_SCALE);

        for (Drawable drawable : this.drawables) {
            drawable.render(context, scaledMouseX, scaledMouseY, delta);
        }

        context.drawTextWithShadow(textRenderer,
            Text.translatable(RENDER_PIPELINE_SCREEN_BACK_HINT), 10, HEADER_HEIGHT + 8, 0xFFEAEAEA);

        for (ModuleNode node : nodes) {
            drawModuleNode(context, node);
        }

        drawConnections(context);

        if (activeSelector != null) {
            activeSelector.render(context, scaledMouseX, scaledMouseY);
        }
    }

    private PortPos getPortPosition(ImageConfig config, boolean isOutput) {
        for (ModuleNode node : nodes) {
            if (node.module == config.owner) {
                int x = (int) node.module.x;
                int y = (int) node.module.y + HEADER_HEIGHT;
                var list =
                    isOutput ? node.module.outputImageConfigs : node.module.inputImageConfigs;
                int index = list.indexOf(config);

                if (index != -1) {
                    int ry = y + node.headerH + node.pad + index * node.rowH + 7;
                    int dotY = ry + 7;
                    int dotX = isOutput ? (x + node.width - 10) : (x + 10);
                    return new PortPos(dotX, dotY);
                }
            }
        }
        return null;
    }

    private void drawBezier(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        int segments = 32;
        float prevX = x1;
        float prevY = y1;
        float thickness = 1.2f;

        float ctrlOffset = Math.abs(x2 - x1) * 0.5f;

        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float invT = 1 - t;
            float b0 = invT * invT * invT;
            float b1 = 3 * invT * invT * t;
            float b2 = 3 * invT * t * t;
            float b3 = t * t * t;

            float cx = b0 * x1 + b1 * (x1 + ctrlOffset) + b2 * (x2 - ctrlOffset) + b3 * x2;
            float cy = b0 * y1 + b1 * y1 + b2 * y2 + b3 * y2;

            ((IDrawContextExt) (Object) ctx).neoVoxelRT$drawOrientedQuad(RenderLayer.getGui(),
                prevX, prevY, cx, cy, thickness, color);

            prevX = cx;
            prevY = cy;
        }
    }

    private void drawConnections(DrawContext context) {
        for (ModuleConnection link : moduleConnections) {
            PortPos p1 = getPortPosition(link.src, true);
            PortPos p2 = getPortPosition(link.dst, false);
            if (p1 != null && p2 != null) {
                drawBezier(context, p1.x, p1.y, p2.x, p2.y, getFormatColor(link.src.format));
            }
        }
    }

    private void drawModuleNode(DrawContext context, ModuleNode moduleNode) {
        int x = (int) moduleNode.module.x;
        int y = (int) moduleNode.module.y + HEADER_HEIGHT;
        int w = moduleNode.width;
        int h = moduleNode.height();

        context.fill(x, y, x + w, y + h, 0xFF20242C);

        context.fill(x, y, x + w, y + moduleNode.headerH, 0xFF2B3240);

        context.drawTextWithShadow(textRenderer, Text.translatable(moduleNode.module.name), x + 6,
            y + 5, 0xFFEAEAEA);

        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (moduleNode.headerH - btnSize) / 2;
        int gearX = deleteX - btnSize - 2;

        context.drawTexture(RenderLayer::getGuiTextured, GEAR_TEX, gearX, btnY, 0, 0, btnSize,
            btnSize, btnSize, btnSize);

        context.drawTextWithShadow(textRenderer, "×", deleteX + 3, btnY + 2, 0xFFFF5A5A);

        for (int i = 0; i < moduleNode.rows(); i++) {
            int ry = y + moduleNode.headerH + moduleNode.pad + i * moduleNode.rowH + 7;

            if (i < moduleNode.module.inputImageConfigs.size()) {
                ImageConfig in = moduleNode.module.inputImageConfigs.get(i);
                int dotX = x + 10;
                int dotY = ry + 7;

                int color = (in == pendingPort) ? 0xFFFFFF00 : getFormatColor(in.format);
                boolean isConnected = moduleConnections.stream().anyMatch(l -> l.dst == in);
                drawPortDot(context, dotX, dotY, color, isConnected, false);

                context.drawTextWithShadow(textRenderer, in.name, x + 18, ry + 2, 0xFFD0D0D0);
            }

            if (i < moduleNode.module.outputImageConfigs.size()) {
                ImageConfig out = moduleNode.module.outputImageConfigs.get(i);
                int dotX = x + w - 10;
                int dotY = ry + 7;

                int color = (out == pendingPort) ? 0xFFFFFF00 : getFormatColor(out.format);
                boolean isConnected = moduleConnections.stream().anyMatch(l -> l.src == out);
                drawPortDot(context, dotX, dotY, color, isConnected, out == localFinalOutput);

                int nameWidth = textRenderer.getWidth(out.name);
                context.drawTextWithShadow(textRenderer, out.name, (dotX - 8) - nameWidth, ry + 2,
                    0xFFD0D0D0);
            }
        }
    }

    private void drawPortDot(DrawContext ctx, int cx, int cy, int color, boolean filled,
        boolean isFinal) {
        ctx.fill(cx - 4, cy - 4, cx + 5, cy + 5, isFinal ? 0xFF55FF55 : 0xFF000000);
        ctx.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        ctx.fill(cx - 2, cy - 2, cx + 3, cy + 3, color);
        if (!filled) {
            ctx.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF20242C);
        }
    }

    private boolean testCycle(ImageConfig src, ImageConfig dst) {
        if (src.owner == dst.owner) {
            return true;
        }
        return hasPath(dst.owner, src.owner);
    }

    private boolean hasPath(Module start, Module target) {
        if (start == target) {
            return true;
        }
        for (ModuleConnection link : moduleConnections) {
            if (link.src.owner == start) {
                if (hasPath(link.dst.owner, target)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void handleLocalConnection(ImageConfig current, boolean isOutput) {
        if (pendingPort == null) {
            pendingPort = current;
            isPendingOutput = isOutput;
        } else {
            if (isPendingOutput != isOutput) {
                ImageConfig src = isPendingOutput ? pendingPort : current;
                ImageConfig dst = isPendingOutput ? current : pendingPort;

                if (!Objects.equals(src.format, dst.format)) {
                    pendingPort = null;
                    return;
                }

                if (src.owner == dst.owner) {
                    pendingPort = null;
                    return;
                }

                if (!testCycle(src, dst)) {
                    moduleConnections.removeIf(link -> link.dst == dst);
                    moduleConnections.add(new ModuleConnection(src, dst));
                }
            }
            pendingPort = null;
        }
    }

    private boolean isGearClicked(ModuleNode node, double mouseX, double mouseY) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width;
        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (node.headerH - btnSize) / 2;
        int gearX = deleteX - btnSize - 2;
        return mouseX >= gearX && mouseX <= gearX + btnSize && mouseY >= btnY
            && mouseY <= btnY + btnSize;
    }

    private boolean isDeleteClicked(ModuleNode node, double mouseX, double mouseY) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width;
        int btnSize = 12;
        int deleteX = x + w - btnSize - 4;
        int btnY = y + (node.headerH - btnSize) / 2;
        return mouseX >= deleteX && mouseX <= deleteX + btnSize && mouseY >= btnY
            && mouseY <= btnY + btnSize;
    }

    private ImageConfig getClickedPort(ModuleNode node, double mouseX, double mouseY,
        boolean isOutput) {
        int x = (int) node.module.x;
        int y = (int) node.module.y + HEADER_HEIGHT;
        int w = node.width;

        var configs = isOutput ? node.module.outputImageConfigs : node.module.inputImageConfigs;
        if (configs == null) {
            return null;
        }

        for (int i = 0; i < configs.size(); i++) {
            int ry = y + node.headerH + node.pad + i * node.rowH + 7;
            int dotY = ry + 7;
            int dotX = isOutput ? (x + w - 10) : (x + 10);

            if (Math.abs(mouseX - dotX) <= 8 && Math.abs(mouseY - dotY) <= 8) {
                return configs.get(i);
            }
        }
        return null;
    }

    private void deleteNode(ModuleNode node) {
        nodes.remove(node);
        moduleConnections.removeIf(
            link -> link.src.owner == node.module || link.dst.owner == node.module);

        if (draggedNode == node) {
            draggedNode = null;
        }
        if (pendingPort != null && pendingPort.owner == node.module) {
            pendingPort = null;
        }
        if (localFinalOutput != null && localFinalOutput.owner == node.module) {
            localFinalOutput = null;
        }

        activeSelector = null;
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        if (mouseY < HEADER_HEIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        for (ModuleNode node : nodes) {
            if (button == 0 && isDeleteClicked(node, mouseX, mouseY)) {
                deleteNode(node);
                return true;
            }

            if (button == 0 && isGearClicked(node, mouseX, mouseY)) {
                MinecraftClient.getInstance()
                    .setScreen(new ModuleAttributeScreen(this, node.module));
                return true;
            }
        }

        for (ModuleNode node : nodes) {
            ImageConfig clickedIn = getClickedPort(node, mouseX, mouseY, false);
            ImageConfig clickedOut = getClickedPort(node, mouseX, mouseY, true);
            ImageConfig current = (clickedIn != null) ? clickedIn : clickedOut;

            if (current != null) {
                if (button == 0) {
                    handleLocalConnection(current, clickedOut != null);
                    return true;
                } else if (button == 1) {
                    boolean isConnected = moduleConnections.stream()
                        .anyMatch(l -> l.src == current || l.dst == current);

                    if (isConnected) {
                        moduleConnections.removeIf(
                            link -> link.src == current || link.dst == current);
                    } else if (clickedOut != null) {
                        if (localFinalOutput != null && localFinalOutput != current) {
                            localFinalOutput.finalOutput = false;
                        }

                        localFinalOutput = (localFinalOutput == current) ? null : current;
                        current.finalOutput = (localFinalOutput == current);
                    }
                    return true;
                }
            }
        }

        if (activeSelector != null) {
            if (activeSelector.onClick(mouseX, mouseY)) {
                activeSelector = null;
                return true;
            }
            activeSelector = null;
        }

        draggedNode = null;
        for (int i = nodes.size() - 1; i >= 0; i--) {
            ModuleNode node = nodes.get(i);
            if (mouseX >= node.module.x && mouseX <= node.module.x + node.width && mouseY >= (
                node.module.y + HEADER_HEIGHT) && mouseY <= (node.module.y + HEADER_HEIGHT
                + node.height())) {
                if (button == 0) {
                    draggedNode = node;
                }
                break;
            }
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX,
        double deltaY) {
        mouseX /= GLOBAL_SCALE;
        mouseY /= GLOBAL_SCALE;

        double dx = mouseX - lastMouseX;
        double dy = mouseY - lastMouseY;

        if (button == 0 && draggedNode != null) {
            draggedNode.module.x += dx;
            draggedNode.module.y += dy;
        } else if (button == 1 || (button == 0 && draggedNode == null)) {
            for (ModuleNode node : nodes) {
                node.module.x += dx;
                node.module.y += dy;
            }
        }

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private record ModuleConnection(ImageConfig src, ImageConfig dst) {

    }

    private record PortPos(int x, int y) {

    }

    private class ModuleSelector {

        private final int x, y, width;
        private final List<ModuleEntry> options;
        private final int itemHeight = 18;

        public ModuleSelector(int x, int y, Map<String, ModuleEntry> entries) {
            this.x = x;
            this.y = y;
            this.options = new ArrayList<>(entries.values());
            this.width = 120;
        }

        public void render(DrawContext ctx, int mouseX, int mouseY) {
            int currentY = y;
            ctx.fill(x - 1, y - 1, x + width + 1, y + (options.size() * itemHeight) + 1,
                0xFFFFFFFF);

            for (ModuleEntry entry : options) {
                boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= currentY
                    && mouseY <= currentY + itemHeight;
                ctx.fill(x, currentY, x + width, currentY + itemHeight,
                    hovered ? 0xFF444444 : 0xFF222222);
                ctx.drawTextWithShadow(textRenderer, Text.translatable(entry.name), x + 5,
                    currentY + 5, 0xFFE0E0E0);
                currentY += itemHeight;
            }
        }

        public boolean onClick(double mouseX, double mouseY) {
            if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + (options.size()
                * itemHeight)) {
                return false;
            }
            int index = (int) ((mouseY - y) / itemHeight);
            if (index >= 0 && index < options.size()) {
                ModuleEntry selected = options.get(index);
                Module module = selected.loadModule();
                module.x = 100;
                module.y = 100;
                nodes.add(new ModuleNode(module));
                return true;
            }
            return false;
        }
    }
}
