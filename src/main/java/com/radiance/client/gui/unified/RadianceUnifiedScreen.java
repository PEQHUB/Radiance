package com.radiance.client.gui.unified;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.RadianceTheme;
import com.radiance.client.gui.ResettableSliderWidget;
import com.radiance.client.gui.unified.populators.*;
import com.radiance.client.option.Options;
import com.radiance.client.option.PresetManager;
import com.radiance.client.util.EmissiveBlock;
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
 * Two modes:
 * - Simple (default): AAA-style flat menu with 6 curated categories
 * - Advanced: Full tree with all sub-categories and every setting exposed
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
    private ButtonWidget advancedToggleButton;
    private ButtonWidget[] presetButtons;

    // ── Search ──
    private UnifiedSearchOverlay searchOverlay;

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
                Options.overwriteConfig();
                refreshContent();
            })
            .dimensions(resetBtnX, resetBtnY, resetBtnW, resetBtnH)
            .build();
        this.addDrawableChild(resetDefaultsButton);

        // Advanced/Simple toggle button (to the left of Reset)
        int toggleBtnW = 80;
        int toggleBtnH = 18;
        int toggleBtnX = resetBtnX - toggleBtnW - 6;
        int toggleBtnY = (HEADER_HEIGHT - toggleBtnH) / 2;
        advancedToggleButton = ButtonWidget.builder(
            Text.literal(Options.advancedMode ? "Advanced" : "Simple"),
            btn -> toggleAdvancedMode())
            .dimensions(toggleBtnX, toggleBtnY, toggleBtnW, toggleBtnH)
            .build();
        this.addDrawableChild(advancedToggleButton);

        // Opacity slider (to the left of Advanced toggle)
        int sliderW = 140;
        int sliderH = 18;
        int sliderX = toggleBtnX - sliderW - 8;
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

        // Preset buttons (between title and opacity slider)
        int presetBtnW = 24;
        int presetBtnH = 18;
        int presetBtnY = (HEADER_HEIGHT - presetBtnH) / 2;
        int presetStartX = 8 + this.textRenderer.getWidth(
            Text.translatable("radiance.settings.title")) + 12;
        presetButtons = new ButtonWidget[3];
        for (int slot = 1; slot <= 3; slot++) {
            final int s = slot;
            int btnX = presetStartX + (slot - 1) * (presetBtnW + 4);
            presetButtons[slot - 1] = ButtonWidget.builder(
                Text.literal(String.valueOf(slot)),
                btn -> handlePresetClick(s))
                .dimensions(btnX, presetBtnY, presetBtnW, presetBtnH)
                .build();
            this.addDrawableChild(presetButtons[slot - 1]);
        }

        // Search overlay
        searchOverlay = new UnifiedSearchOverlay(this);

        // Re-init overlay if active (window may have resized)
        if (overlayShowing && overlayScreen != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            overlayScreen.init(mc, this.width, this.height);
        }

        // Restore remembered position, or select first category
        if (rememberedNodeId != null) {
            TreeNode found = findNodeById(rememberedNodeId);
            if (found != null) {
                tree.selectById(rememberedNodeId);
                if (rememberedScrollY > 0) {
                    content.setScrollTarget(rememberedScrollY);
                }
            } else {
                // Node from other mode — select first
                rememberedNodeId = null;
                tree.selectFirst();
            }
        } else {
            tree.selectFirst();
        }
    }

    private TreeNode findNodeById(String id) {
        for (TreeNode root : tree.getRootNodes()) {
            TreeNode found = root.findById(id);
            if (found != null) return found;
        }
        return null;
    }

    /** Toggle between simple and advanced modes, rebuild the tree. */
    private void toggleAdvancedMode() {
        Options.advancedMode = !Options.advancedMode;
        Options.overwriteConfig();
        // Reset remembered node since tree structure changes
        rememberedNodeId = null;
        rememberedScrollY = 0;
        activeNode = null;
        // Full reinit — re-set screen to trigger init()
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.setScreen(new RadianceUnifiedScreen(parent));
    }

    // ── Preset handling ──

    private void handlePresetClick(int slot) {
        long handle = MinecraftClient.getInstance().getWindow().getHandle();
        boolean shift = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                     || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean ctrl = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;

        if (ctrl && shift) {
            PresetManager.clear(slot);
            Options.setFocusToast("Preset " + slot + " cleared", 0xB0B0B0, 1500);
        } else if (shift) {
            Options.overwriteConfig();
            PresetManager.save(slot);
            Options.setFocusToast("Saved to Preset " + slot, 0x55FF55, 1500);
        } else {
            if (PresetManager.isOccupied(slot)) {
                PresetManager.load(slot);
                Options.setFocusToast("Loaded Preset " + slot, 0x55FF55, 1500);
                MinecraftClient.getInstance().setScreen(new RadianceUnifiedScreen(parent));
            } else {
                Options.setFocusToast("Empty — Shift+Click to save", 0xFF5555, 2000);
            }
        }
    }

    // ── Tree structure ──

    private void populateTree() {
        if (Options.advancedMode) {
            populateAdvancedTree();
        } else {
            populateSimpleTree();
        }
        tree.setOnSelect(this::onCategorySelected);
    }

    /**
     * Simple mode: 6 flat categories with curated AAA-style settings.
     * No sub-categories, no deep nesting — clean and approachable.
     */
    private void populateSimpleTree() {
        tree.addRoot(new TreeNode("s_graphics", "Graphics", new SimpleGraphicsPopulator()));
        tree.addRoot(new TreeNode("s_display", "Display", new SimpleDisplayPopulator()));
        tree.addRoot(new TreeNode("s_lighting", "Lighting", new SimpleLightingPopulator()));
        tree.addRoot(new TreeNode("s_post", "Post Processing", new SimplePostProcessingPopulator()));
        tree.addRoot(new TreeNode("s_environment", "Environment", new SimpleEnvironmentPopulator()));
        tree.addRoot(new TreeNode("s_camera", "Camera", new SimpleCameraPopulator()));
    }

    /**
     * Advanced mode: full tree with all sub-categories — every setting exposed.
     */
    private void populateAdvancedTree() {
        // ▼ Offline Rendering (top of tree)
        TreeNode offline = new TreeNode("offline", "Offline Rendering");
        offline.populator = new OfflinePopulator();
        tree.addRoot(offline);

        // ▼ Lighting
        TreeNode lighting = new TreeNode("lighting", "Lighting");
        lighting.addChild(new TreeNode("area_lights", "Area Lights", new AreaLightPopulator()));
        TreeNode emission = new TreeNode("emission", "Emission");
        emission.addChild(new TreeNode("emission_flames", "Flames",
            new BlockEmissionPopulator("Flames",
                EmissiveBlock.LAVA, EmissiveBlock.FIRE, EmissiveBlock.SOUL_FIRE,
                EmissiveBlock.TORCH, EmissiveBlock.SOUL_TORCH,
                EmissiveBlock.CAMPFIRE, EmissiveBlock.SOUL_CAMPFIRE,
                EmissiveBlock.LANTERN, EmissiveBlock.SOUL_LANTERN)));
        emission.addChild(new TreeNode("emission_heat", "Heat Sources",
            new BlockEmissionPopulator("Heat Sources",
                EmissiveBlock.MAGMA_BLOCK, EmissiveBlock.FURNACE, EmissiveBlock.BLAST_FURNACE,
                EmissiveBlock.SMOKER, EmissiveBlock.BREWING_STAND,
                EmissiveBlock.CANDLE, EmissiveBlock.JACK_O_LANTERN, EmissiveBlock.COPPER_BULB)));
        emission.addChild(new TreeNode("emission_luminous", "Luminous",
            new BlockEmissionPopulator("Luminous",
                EmissiveBlock.GLOWSTONE, EmissiveBlock.SHROOMLIGHT, EmissiveBlock.SEA_LANTERN,
                EmissiveBlock.FROGLIGHT, EmissiveBlock.BEACON, EmissiveBlock.END_ROD,
                EmissiveBlock.REDSTONE_TORCH, EmissiveBlock.REDSTONE_LAMP,
                EmissiveBlock.CAVE_VINES, EmissiveBlock.GLOW_LICHEN,
                EmissiveBlock.AMETHYST_CLUSTER, EmissiveBlock.SEA_PICKLE,
                EmissiveBlock.ENCHANTING_TABLE, EmissiveBlock.ENDER_CHEST)));
        emission.addChild(new TreeNode("emission_portals", "Portals & Sculk",
            new BlockEmissionPopulator("Portals & Sculk",
                EmissiveBlock.NETHER_PORTAL, EmissiveBlock.END_PORTAL, EmissiveBlock.END_GATEWAY,
                EmissiveBlock.CRYING_OBSIDIAN, EmissiveBlock.RESPAWN_ANCHOR, EmissiveBlock.CONDUIT,
                EmissiveBlock.SCULK, EmissiveBlock.SCULK_VEIN, EmissiveBlock.SCULK_SENSOR,
                EmissiveBlock.SCULK_CATALYST, EmissiveBlock.SCULK_SHRIEKER,
                EmissiveBlock.CALIBRATED_SCULK_SENSOR,
                EmissiveBlock.TRIAL_SPAWNER, EmissiveBlock.VAULT)));
        emission.addChild(new TreeNode("emission_particles", "Particles",
            new ParticlesEmissionPopulator()));
        emission.populator = compositePopulator(emission.children);
        lighting.addChild(emission);
        lighting.addChild(new TreeNode("fireworks", "Fireworks", new FireworksPopulator()));
        lighting.addChild(new TreeNode("materials", "Materials", new MaterialsPopulator()));
        lighting.addChild(new TreeNode("entity_materials", "Entity Materials", new EntityMaterialPopulator()));
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
        display.addChild(new TreeNode("fpv", "First-Person View", new FpvPopulator()));
        display.populator = compositePopulator(display.children);
        tree.addRoot(display);
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
        // Don't render anything in peek mode (Tab hides all UI)
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

        // Search overlay (rendered last, on top of everything)
        if (searchOverlay != null) {
            searchOverlay.render(context, this.textRenderer, this.width, this.height);
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

            // Preset buttons (custom rendering with state-aware colors)
            if (presetButtons != null) {
                for (int i = 0; i < presetButtons.length; i++) {
                    ButtonWidget btn = presetButtons[i];
                    int bx = btn.getX(), by = btn.getY(), bw = btn.getWidth(), bh = btn.getHeight();
                    boolean hovered = mouseX >= bx && mouseX < bx + bw
                        && mouseY >= by && mouseY < by + bh;
                    int slot = i + 1;
                    boolean occupied = PresetManager.isOccupied(slot);
                    boolean active = PresetManager.getActiveSlot() == slot;

                    int bg;
                    if (active) {
                        bg = RadianceTheme.withAlpha(0x2AB5A0, fade * 0.8f);
                    } else if (occupied) {
                        bg = hovered ? RadianceTheme.buttonHover : RadianceTheme.buttonBg;
                    } else {
                        bg = RadianceTheme.withAlpha(0x1A1A1A, fade * 0.5f);
                    }
                    context.fill(bx, by, bx + bw, by + bh, bg);

                    int border = active ? RadianceTheme.withAlpha(0x2AB5A0, fade)
                               : occupied ? RadianceTheme.buttonBorder
                               : RadianceTheme.withAlpha(0x303030, fade * 0.5f);
                    context.drawBorder(bx, by, bw, bh, border);

                    int textColor = active ? 0xFFFFFF
                                  : occupied ? (hovered ? 0xFFFFFF : 0xB0B0B0)
                                  : 0x606060;
                    Text label = btn.getMessage();
                    RadianceTheme.drawOutlinedText(context, this.textRenderer, label,
                        bx + (bw - this.textRenderer.getWidth(label)) / 2,
                        by + (bh - 8) / 2, textColor, fade);
                }
            }

            // Re-render the header widgets on top of the background
            // (super.render drew them but the bg was painted over them)
            if (opacitySlider != null) {
                opacitySlider.render(context, mouseX, mouseY, delta);
            }
            if (advancedToggleButton != null) {
                renderHeaderButton(context, mouseX, mouseY, advancedToggleButton);

                // Modified badge: orange dot + count when simple mode has non-default advanced settings
                if (!Options.advancedMode) {
                    int modCount = Options.countNonDefaultAdvancedSettings();
                    if (modCount > 0) {
                        int dotX = advancedToggleButton.getX() + advancedToggleButton.getWidth() + 4;
                        int dotY = advancedToggleButton.getY() + (advancedToggleButton.getHeight() - 8) / 2;
                        // Orange dot
                        context.fill(dotX, dotY, dotX + 6, dotY + 6, 0xFFE8712A);
                        // Count text
                        String badge = modCount + " modified";
                        RadianceTheme.drawOutlinedText(context, this.textRenderer,
                            Text.literal(badge), dotX + 9, dotY - 1,
                            RadianceTheme.textAccent, fade);
                    }
                }
            }
            if (resetDefaultsButton != null) {
                renderHeaderButton(context, mouseX, mouseY, resetDefaultsButton);
            }
        }
    }

    private void renderHeaderButton(DrawContext context, int mouseX, int mouseY, ButtonWidget btn) {
        int x = btn.getX();
        int y = btn.getY();
        int w = btn.getWidth();
        int h = btn.getHeight();
        boolean hovered = mouseX >= x && mouseX < x + w
            && mouseY >= y && mouseY < y + h;
        RadianceTheme.drawCustomButton(context, x, y, w, h,
            hovered, this.textRenderer, btn.getMessage());
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
        // Search overlay consumes clicks when visible
        if (searchOverlay != null && searchOverlay.isVisible()) {
            return searchOverlay.mouseClicked(mouseX, mouseY, button);
        }
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
        // Search overlay consumes all input when visible
        if (searchOverlay != null && searchOverlay.isVisible()) {
            return searchOverlay.keyPressed(keyCode, scanCode, modifiers);
        }

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

        // Don't toggle search when an overlay screen is showing
        if (overlayScreen != null) return super.keyPressed(keyCode, scanCode, modifiers);

        // Search: Space opens search (when no overlay active)
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            if (searchOverlay != null) searchOverlay.toggle();
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
    public boolean charTyped(char chr, int modifiers) {
        if (searchOverlay != null && searchOverlay.isVisible()) {
            return searchOverlay.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
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
        MinecraftClient.getInstance().setScreen(parent);
    }

    // ── Accessors ──

    public Screen getParent() { return parent; }
    public ContentPanelWidget getContentPanel() { return content; }
    public TreeNavigationWidget getTree() { return tree; }
}
