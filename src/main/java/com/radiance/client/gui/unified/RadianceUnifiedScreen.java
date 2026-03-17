package com.radiance.client.gui.unified;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.populators.*;
import com.radiance.client.option.Options;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Unified Radiance settings screen with tree navigation (left) and content panel (right).
 * Replaces RadianceSettingsScreen and all 15+ sub-screens in a single panel+sidebar layout.
 *
 * Features:
 * - Category nodes populate ALL child sections at once
 * - Leaf nodes populate only their own section
 * - Sub-screens open as modal overlays, not separate screens
 * - Custom-rendered widgets (no Minecraft textures)
 * - Global opacity slider and Reset to Defaults in header
 */
public class RadianceUnifiedScreen extends Screen {

    private static final int TREE_WIDTH = 160;
    private static final int HEADER_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 0;
    private static final int TREE_TOP_PAD = 4;

    // ── Persisted menu state (survives close/reopen within same game session) ──
    private static String rememberedNodeId = null;
    private static double rememberedScrollY = 0;

    /** True when opened via Tab hold — screen closes on Tab release. */
    public static boolean openedViaTab = false;
    /** Timestamp when Tab peek was opened — prevents immediate close from key state race. */
    public static long tabPeekOpenTimeMs = 0;

    private final Screen parent;
    private TreeNavigationWidget tree;
    private ContentPanelWidget content;

    /** Track which node is currently populating the content panel. */
    private TreeNode activeNode;

    // ── Modal overlay ──
    private Screen overlayScreen;
    private boolean overlayShowing = false;

    // ── Header controls ──
    private ResettableSliderWidget opacitySlider;
    private ButtonWidget resetDefaultsButton;

    public RadianceUnifiedScreen(Screen parent) {
        super(Text.translatable("radiance.settings.title"));
        this.parent = parent;
    }

    // ── Initialization ──

    @Override
    protected void init() {
        super.init();

        int treeTop = HEADER_HEIGHT + TREE_TOP_PAD;
        int bodyHeight = this.height - treeTop - FOOTER_HEIGHT;

        // Create tree navigation (left panel)
        tree = new TreeNavigationWidget(0, treeTop, TREE_WIDTH, bodyHeight);
        populateTree();
        this.addDrawableChild(tree);

        // Create content panel (right panel)
        int contentX = TREE_WIDTH;
        int contentW = this.width - TREE_WIDTH;
        content = new ContentPanelWidget(contentX, treeTop, contentW, bodyHeight);
        this.addDrawableChild(content);

        // ── Header controls ──

        // Reset to Defaults button (top-right)
        int resetBtnW = 110;
        int resetBtnH = 18;
        int resetBtnX = this.width - resetBtnW - 6;
        int resetBtnY = (HEADER_HEIGHT - resetBtnH) / 2;
        resetDefaultsButton = ButtonWidget.builder(
            Text.translatable("radiance.settings.reset_defaults"),
            btn -> {
                Options.resetAllToDefaults();
                refreshContent();
            })
            .dimensions(resetBtnX, resetBtnY, resetBtnW, resetBtnH)
            .build();
        this.addDrawableChild(resetDefaultsButton);

        // Opacity slider (to the left of Reset button)
        int sliderW = 140;
        int sliderH = 18;
        int sliderX = resetBtnX - sliderW - 8;
        int sliderY = (HEADER_HEIGHT - sliderH) / 2;
        opacitySlider = new ResettableSliderWidget(
            sliderX, sliderY, sliderW, sliderH,
            0, 100, Options.uiGlobalAlphaPercent, 55,
            v -> getGenericValueText(
                Text.translatable("radiance.settings.menu_transparency"),
                Text.literal(v + "%")),
            v -> Options.setUiGlobalAlphaPercent(v, false));
        opacitySlider.setOnRelease(() -> Options.overwriteConfig());
        opacitySlider.settingKey = "uiGlobalAlphaPercent";
        this.addDrawableChild(opacitySlider);

        // Re-init overlay if active (window may have resized)
        if (overlayShowing && overlayScreen != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            overlayScreen.init(mc, this.width, this.height);
        }

        // Restore remembered position, or select first category
        if (rememberedNodeId != null) {
            tree.selectById(rememberedNodeId);
            if (rememberedScrollY > 0) {
                content.setScrollTarget(rememberedScrollY);
            }
        } else {
            tree.selectFirst();
        }
    }

    // ── Tree structure ──

    private void populateTree() {
        // ▼ Offline Rendering (top of tree)
        TreeNode offline = new TreeNode("offline", "Offline Rendering");
        offline.populator = new OfflinePopulator();
        tree.addRoot(offline);

        // ▼ Lighting
        TreeNode lighting = new TreeNode("lighting", "Lighting");
        lighting.addChild(new TreeNode("area_lights", "Area Lights", new AreaLightPopulator()));
        lighting.addChild(new TreeNode("emission", "Emission", new EmissionPopulator()));
        lighting.addChild(new TreeNode("fireworks", "Fireworks", new FireworksPopulator()));
        lighting.addChild(new TreeNode("materials", "Materials", new MaterialsPopulator()));
        lighting.populator = compositePopulator(lighting.children);
        tree.addRoot(lighting);

        // ▼ Light & Color
        TreeNode lightColor = new TreeNode("light_color", "Light & Color");
        lightColor.addChild(new TreeNode("exposure", "Exposure", new ExposurePopulator()));
        lightColor.addChild(new TreeNode("psychov", "PsychoV", new PsychoVPopulator()));
        lightColor.addChild(new TreeNode("post_processing", "Post Processing", new PostProcessingPopulator()));
        lightColor.addChild(new TreeNode("hdr_saturation", "HDR / Saturation", new HdrSaturationPopulator()));
        lightColor.populator = compositePopulator(lightColor.children);
        tree.addRoot(lightColor);

        // ▼ Image Quality
        TreeNode imageQuality = new TreeNode("image_quality", "Image Quality");
        imageQuality.addChild(new TreeNode("upscaler", "Upscaler", new UpscalerPopulator()));
        imageQuality.addChild(new TreeNode("ray_tracing", "Ray Tracing", new RayTracingPopulator()));
        imageQuality.addChild(new TreeNode("sharc", "SHARC", new SharcPopulator()));
        imageQuality.populator = compositePopulator(imageQuality.children);
        tree.addRoot(imageQuality);

        // ▼ Environment
        TreeNode environment = new TreeNode("environment", "Environment");
        environment.addChild(new TreeNode("sky", "Sky", new SkyPopulator()));
        environment.addChild(new TreeNode("clouds", "Clouds", new CloudPopulator()));
        environment.addChild(new TreeNode("water", "Water", new WaterPopulator()));
        environment.addChild(new TreeNode("sun", "Sun", new SunPopulator()));
        environment.addChild(new TreeNode("moon", "Moon", new MoonPopulator()));
        environment.populator = compositePopulator(environment.children);
        tree.addRoot(environment);

        // ▼ Display
        TreeNode display = new TreeNode("display", "Display");
        display.addChild(new TreeNode("window", "Window", new WindowPopulator()));
        display.addChild(new TreeNode("terrain", "Terrain", new TerrainPopulator()));
        display.addChild(new TreeNode("pipeline", "Pipeline", new PipelinePopulator()));
        display.addChild(new TreeNode("ui_settings", "UI Settings", new UiSettingsPopulator()));
        display.populator = compositePopulator(display.children);
        tree.addRoot(display);

        // Wire selection callback
        tree.setOnSelect(this::onCategorySelected);
    }

    /**
     * Create a composite populator that runs ALL child populators sequentially.
     * Used for category nodes so clicking "Lighting" shows all sub-sections at once.
     */
    private ContentPopulator compositePopulator(List<TreeNode> children) {
        return (panel, screen) -> {
            for (TreeNode child : children) {
                if (child.populator != null) {
                    child.populator.populate(panel, screen);
                }
            }
        };
    }

    private void onCategorySelected(TreeNode node) {
        if (node == null || node.populator == null) return;
        if (node == activeNode) return; // Already showing this content

        activeNode = node;
        rememberedNodeId = node.id; // Persist for next open
        content.clearContent();
        node.populator.populate(content, this);
    }

    /** Re-populate the currently active content (for screen rebuilds after toggle changes). */
    public void refreshContent() {
        if (activeNode != null && activeNode.populator != null) {
            content.clearContent();
            activeNode.populator.populate(content, this);
        }
    }

    /** Navigate to a specific tree node by ID and populate its content. */
    public void navigateTo(String nodeId) {
        tree.selectById(nodeId);
    }

    // ── Modal overlay ──

    /** Show a sub-screen as a modal overlay within this unified screen. */
    public void showOverlay(Screen target) {
        this.overlayScreen = target;
        this.overlayShowing = true;
        // Initialize the sub-screen with the full window dimensions
        MinecraftClient mc = MinecraftClient.getInstance();
        target.init(mc, this.width, this.height);
    }

    /** Close the modal overlay and return to the unified screen. */
    public void closeOverlay() {
        this.overlayScreen = null;
        this.overlayShowing = false;
    }

    /** Re-initialize the current overlay screen in place (e.g., after block selector change). */
    public void refreshOverlay() {
        if (overlayScreen != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            overlayScreen.init(mc, this.width, this.height);
        }
    }

    public boolean isOverlayShowing() {
        return overlayShowing;
    }

    // ── Rendering ──

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent — game world shows through
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Tab peek: poll key state every frame — close when Tab is released
        // Require minimum 200ms hold to avoid race between wasPressed() and glfwGetKey()
        if (openedViaTab) {
            long elapsed = System.currentTimeMillis() - tabPeekOpenTimeMs;
            if (elapsed > 200) {
                long handle = MinecraftClient.getInstance().getWindow().getHandle();
                if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_TAB) != GLFW.GLFW_PRESS) {
                    openedViaTab = false;
                    this.close();
                    return;
                }
            }
        }

        // Don't render anything in peek mode
        if (RadianceTheme.peekActive) return;

        super.render(context, mouseX, mouseY, delta);

        // Header background drawn UNDER the widgets that super.render already drew
        // We need to draw it before super, but that's not possible, so draw the header
        // bg first in a pre-pass and re-render header widgets on top
        renderHeader(context, mouseX, mouseY, delta);

        // Modal overlay on top
        if (overlayShowing && overlayScreen != null) {
            renderOverlay(context, mouseX, mouseY, delta);
        }
    }

    private void renderHeader(DrawContext context, int mouseX, int mouseY, float delta) {
        // Header background (hidden during slider drag)
        float fade = RadianceTheme.inactiveFadeFactor();
        context.fill(0, 0, this.width, HEADER_HEIGHT,
            RadianceTheme.scaleAlpha(RadianceTheme.unifiedHeaderBg, fade));

        if (fade > 0.005f) {
            // Title in accent color
            RadianceTheme.drawOutlinedText(context, this.textRenderer,
                Text.translatable("radiance.settings.title"),
                8, (HEADER_HEIGHT - 8) / 2, RadianceTheme.textAccent, fade);

            // Re-render the header widgets on top of the background
            // (super.render drew them but the bg was painted over them)
            if (opacitySlider != null) {
                opacitySlider.render(context, mouseX, mouseY, delta);
            }
            if (resetDefaultsButton != null) {
                // Custom render for the reset button
                int x = resetDefaultsButton.getX();
                int y = resetDefaultsButton.getY();
                int w = resetDefaultsButton.getWidth();
                int h = resetDefaultsButton.getHeight();
                boolean hovered = mouseX >= x && mouseX < x + w
                    && mouseY >= y && mouseY < y + h;
                RadianceTheme.drawCustomButton(context, x, y, w, h,
                    hovered, this.textRenderer, resetDefaultsButton.getMessage());
            }
        }
    }

    private void renderOverlay(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark semi-transparent backdrop
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);
        context.fill(0, 0, this.width, this.height,
            RadianceTheme.withAlpha(0x000000, 0.6f));

        // Render the sub-screen
        overlayScreen.render(context, mouseX, mouseY, delta);
        context.getMatrices().pop();
    }

    // ── Input handling ──

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overlayShowing && overlayScreen != null) {
            return overlayScreen.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (overlayShowing && overlayScreen != null) {
            return overlayScreen.mouseReleased(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double horizontalAmount, double verticalAmount) {
        if (overlayShowing && overlayScreen != null) {
            return overlayScreen.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Overlay intercepts input
        if (overlayShowing && overlayScreen != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeOverlay();
                return true;
            }
            return overlayScreen.keyPressed(keyCode, scanCode, modifiers);
        }

        // Peek mode
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = true;
            return true;
        }

        // Escape closes screen
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.close();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (overlayShowing && overlayScreen != null) {
            return overlayScreen.keyReleased(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double deltaX, double deltaY) {
        if (overlayShowing && overlayScreen != null) {
            return overlayScreen.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        // Propagate right-click drag (button 1) for precision slider mode
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public void close() {
        if (overlayShowing) {
            closeOverlay();
            return;
        }
        // Save scroll position before closing
        if (content != null) {
            rememberedScrollY = content.getScrollTarget();
        }
        // Ensure overlay is fully cleaned up
        overlayScreen = null;
        overlayShowing = false;
        RadianceTheme.peekActive = false;
        RadianceTheme.endSliderFocus();
        openedViaTab = false;
        MinecraftClient.getInstance().setScreen(parent);
    }

    // ── Accessors ──

    public Screen getParent() { return parent; }
    public ContentPanelWidget getContentPanel() { return content; }
    public TreeNavigationWidget getTree() { return tree; }
}
