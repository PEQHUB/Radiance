package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.option.Options;
import com.radiance.client.texture.AutoPBRGenerator;
import com.radiance.client.texture.LiveNormalReuploader;
import com.radiance.client.util.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.GameOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.SimpleOption;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.registry.Registries;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MaterialsSettingsScreen extends GameOptionsScreen {

    private final Screen parentScreen;
    private static int currentBlockIndex = 0;  // persists across screen rebuilds
    private static int currentPresetIndex = 0; // persists across screen rebuilds
    private static String searchQuery = "";    // persists across screen rebuilds
    private static final List<Integer> searchMatches = new ArrayList<>();
    private static int searchMatchIndex = 0;
    private TextFieldWidget searchField;
    private NoiseTypeDropdownWidget noiseDropdown;

    // Auto-PBR preview textures — STATIC to survive screen rebuilds and avoid Vulkan destroy/recreate crashes
    private NativeImage sourceAlbedo;
    private static final Identifier PREVIEW_ALBEDO_ID = Identifier.of("radiance", "autopbr_preview/albedo");
    private static final Identifier PREVIEW_NORMAL_ID = Identifier.of("radiance", "autopbr_preview/normal");
    private static final Identifier PREVIEW_ROUGHNESS_ID = Identifier.of("radiance", "autopbr_preview/roughness");
    private static NativeImageBackedTexture previewAlbedoTex;
    private static NativeImageBackedTexture previewNormalTex;
    private static NativeImageBackedTexture previewRoughTex;
    private static boolean previewsRegistered = false;

    /** Set the block index to show when this screen opens (used by Texture Editor child clicks). */
    public static void setCurrentBlockIndex(int index) { currentBlockIndex = index; }

    /** Called after any slider changes to handle parent→child propagation or child override marking. */
    private static void onSliderChanged(int blockOrdinal) {
        MaterialBlock block = MaterialBlock.values()[blockOrdinal];
        if (block.isParent() && !block.getChildren().isEmpty()) {
            Options.propagateParentMaterial(blockOrdinal);
        } else if (!block.isParent()) {
            Options.materialChildOverride[blockOrdinal] = true;
        }
    }

    // Snapshot of all values when screen first opens — used by Cancel
    private static boolean snapshotTaken = false;
    private static final int[] snapF0R = new int[Options.MAX_MATERIALS], snapF0G = new int[Options.MAX_MATERIALS], snapF0B = new int[Options.MAX_MATERIALS];
    private static final int[] snapRoughness = new int[Options.MAX_MATERIALS], snapMetallic = new int[Options.MAX_MATERIALS];
    private static final int[] snapTransmission = new int[Options.MAX_MATERIALS], snapIOR = new int[Options.MAX_MATERIALS];
    private static final int[] snapSubsurface = new int[Options.MAX_MATERIALS], snapAnisotropic = new int[Options.MAX_MATERIALS];
    private static final int[] snapSheenWeight = new int[Options.MAX_MATERIALS], snapSheenTint = new int[Options.MAX_MATERIALS];
    private static final int[] snapCoatWeight = new int[Options.MAX_MATERIALS], snapCoatRoughness = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseScale = new int[Options.MAX_MATERIALS], snapNoiseStrength = new int[Options.MAX_MATERIALS], snapNoiseOctaves = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseType = new int[Options.MAX_MATERIALS], snapNoiseSeed = new int[Options.MAX_MATERIALS];
    private static final int[] snapChannelR = new int[Options.MAX_MATERIALS], snapChannelG = new int[Options.MAX_MATERIALS], snapChannelB = new int[Options.MAX_MATERIALS];
    private static final int[] snapTextureBlend = new int[Options.MAX_MATERIALS];
    private static final int[] snapGamutBoost = new int[Options.MAX_MATERIALS];
    private static final boolean[] snapAutoPBR = new boolean[Options.MAX_MATERIALS];
    private static final boolean[] snapChildOverride = new boolean[Options.MAX_MATERIALS];
    private static boolean snapAutoPBREnabled;
    private static int snapAutoPBRRoughnessGamma, snapAutoPBRRoughnessMin, snapAutoPBRRoughnessMax;
    private static int snapAutoPBRNormalStrength, snapAutoPBRVarianceWeight, snapAutoPBREdgeWeight;
    private static boolean snapAutoPBRInvertNormal;
    private static boolean snapAutoPBRInvertRoughness;

    public MaterialsSettingsScreen(Screen parent) {
        super(parent, MinecraftClient.getInstance().options, Text.translatable("radiance.settings.materials.title"));
        this.parentScreen = parent;
        if (!snapshotTaken) {
            takeSnapshot();
            snapshotTaken = true;
        }
    }

    private static void takeSnapshot() {
        System.arraycopy(Options.materialF0R, 0, snapF0R, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialF0G, 0, snapF0G, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialF0B, 0, snapF0B, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialRoughness, 0, snapRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialMetallic, 0, snapMetallic, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialTransmission, 0, snapTransmission, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialIOR, 0, snapIOR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSubsurface, 0, snapSubsurface, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAnisotropic, 0, snapAnisotropic, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSheenWeight, 0, snapSheenWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSheenTint, 0, snapSheenTint, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialCoatWeight, 0, snapCoatWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialCoatRoughness, 0, snapCoatRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseScale, 0, snapNoiseScale, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseStrength, 0, snapNoiseStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseOctaves, 0, snapNoiseOctaves, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseType, 0, snapNoiseType, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseSeed, 0, snapNoiseSeed, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChannelR, 0, snapChannelR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChannelG, 0, snapChannelG, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChannelB, 0, snapChannelB, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialTextureBlend, 0, snapTextureBlend, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialGamutBoost, 0, snapGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBR, 0, snapAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChildOverride, 0, snapChildOverride, 0, Options.MAX_MATERIALS);
        snapAutoPBREnabled = Options.autoPBREnabled;
        snapAutoPBRRoughnessGamma = Options.autoPBRRoughnessGamma;
        snapAutoPBRRoughnessMin = Options.autoPBRRoughnessMin;
        snapAutoPBRRoughnessMax = Options.autoPBRRoughnessMax;
        snapAutoPBRNormalStrength = Options.autoPBRNormalStrength;
        snapAutoPBRVarianceWeight = Options.autoPBRVarianceWeight;
        snapAutoPBREdgeWeight = Options.autoPBREdgeWeight;
        snapAutoPBRInvertNormal = Options.autoPBRInvertNormal;
        snapAutoPBRInvertRoughness = Options.autoPBRInvertRoughness;
    }

    private static void restoreSnapshot() {
        System.arraycopy(snapF0R, 0, Options.materialF0R, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapF0G, 0, Options.materialF0G, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapF0B, 0, Options.materialF0B, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapRoughness, 0, Options.materialRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapMetallic, 0, Options.materialMetallic, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapTransmission, 0, Options.materialTransmission, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapIOR, 0, Options.materialIOR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSubsurface, 0, Options.materialSubsurface, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAnisotropic, 0, Options.materialAnisotropic, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSheenWeight, 0, Options.materialSheenWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSheenTint, 0, Options.materialSheenTint, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapCoatWeight, 0, Options.materialCoatWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapCoatRoughness, 0, Options.materialCoatRoughness, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseScale, 0, Options.materialNoiseScale, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseStrength, 0, Options.materialNoiseStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseOctaves, 0, Options.materialNoiseOctaves, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseType, 0, Options.materialNoiseType, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseSeed, 0, Options.materialNoiseSeed, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChannelR, 0, Options.materialChannelR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChannelG, 0, Options.materialChannelG, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChannelB, 0, Options.materialChannelB, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapTextureBlend, 0, Options.materialTextureBlend, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapGamutBoost, 0, Options.materialGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBR, 0, Options.materialAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChildOverride, 0, Options.materialChildOverride, 0, Options.MAX_MATERIALS);
        Options.autoPBREnabled = snapAutoPBREnabled;
        Options.autoPBRRoughnessGamma = snapAutoPBRRoughnessGamma;
        Options.autoPBRRoughnessMin = snapAutoPBRRoughnessMin;
        Options.autoPBRRoughnessMax = snapAutoPBRRoughnessMax;
        Options.autoPBRNormalStrength = snapAutoPBRNormalStrength;
        Options.autoPBRVarianceWeight = snapAutoPBRVarianceWeight;
        Options.autoPBREdgeWeight = snapAutoPBREdgeWeight;
        Options.autoPBRInvertNormal = snapAutoPBRInvertNormal;
        Options.autoPBRInvertRoughness = snapAutoPBRInvertRoughness;
    }

    private boolean autoPBRParamsChanged() {
        if (Options.autoPBREnabled != snapAutoPBREnabled
            || Options.autoPBRRoughnessGamma != snapAutoPBRRoughnessGamma
            || Options.autoPBRRoughnessMin != snapAutoPBRRoughnessMin
            || Options.autoPBRRoughnessMax != snapAutoPBRRoughnessMax
            || Options.autoPBRNormalStrength != snapAutoPBRNormalStrength
            || Options.autoPBRVarianceWeight != snapAutoPBRVarianceWeight
            || Options.autoPBREdgeWeight != snapAutoPBREdgeWeight
            || Options.autoPBRInvertNormal != snapAutoPBRInvertNormal
            || Options.autoPBRInvertRoughness != snapAutoPBRInvertRoughness) return true;
        for (int j = 0; j < Options.MAX_MATERIALS; j++) {
            if (Options.materialAutoPBR[j] != snapAutoPBR[j]) return true;
        }
        return false;
    }

    private void applyChanges() {
        boolean needsTextureReload = autoPBRParamsChanged();
        snapshotTaken = false;
        Options.overwriteConfig();
        if (needsTextureReload) {
            // Full resource reload to regenerate auto-PBR textures with new parameters
            MinecraftClient.getInstance().reloadResources();
        } else {
            // No texture changes — just rebuild chunks for material constant updates
            MinecraftClient.getInstance().worldRenderer.reload();
        }
        this.client.setScreen(this.parentScreen);
    }

    private void cancelChanges() {
        restoreSnapshot();
        snapshotTaken = false;
        Options.overwriteConfig();
        this.client.setScreen(this.parentScreen);
    }

    @Override
    protected void init() {
        super.init();
        // Shift body down to make room for search field
        this.body.setY(this.body.getY() + 22);
        this.body.setHeight(this.body.getHeight() - 22);

        int fieldW = Math.min(300, this.width - 80);
        int fieldX = (this.width - fieldW) / 2;
        int fieldY = this.body.getY() - 20;
        searchField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, 16,
            Text.literal("Search"));
        searchField.setMaxLength(50);
        searchField.setText(searchQuery);
        searchField.setChangedListener(q -> {
            if (!q.equals(searchQuery)) {
                searchQuery = q;
                searchMatchIndex = 0;
                updateSearchMatches();
            }
        });
        addDrawableChild(searchField);
        searchField.setFocused(true);
        setFocused(searchField);
        updateSearchMatches();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // Render noise type dropdown overlay above the list widget
        if (noiseDropdown != null) {
            noiseDropdown.renderDropdownOverlay(context, mouseX, mouseY);
        }
        RadianceTheme.drawBreadcrumb(context, this.textRenderer, "Radiance > Lighting > Texture Editor", parentScreen);

        // Auto-PBR pending changes warning
        if (autoPBRParamsChanged()) {
            String warning = "Auto-PBR changes pending - press Apply to update textures";
            int ww = this.textRenderer.getWidth(warning);
            int wx = (this.width - ww) / 2;
            int wy = this.body.getY() - 10;
            context.drawText(this.textRenderer, Text.literal(warning), wx, wy, 0xFFFFAA00, false);
        }

        // Search match preview (to the right of the search field)
        if (searchField != null && !searchQuery.isEmpty()) {
            int infoX = searchField.getX() + searchField.getWidth() + 8;
            int infoY = searchField.getY() + 4;
            if (searchMatches.isEmpty()) {
                context.drawText(this.textRenderer, Text.literal("No matches"),
                    infoX, infoY, 0xFF808080, false);
            } else {
                if (searchMatchIndex >= searchMatches.size()) searchMatchIndex = 0;
                int matchIdx = searchMatches.get(searchMatchIndex);
                MaterialBlock matchBlock = MaterialBlock.values()[matchIdx];
                String matchName = Text.translatable("options.video.materials." + matchBlock.getId()).getString();
                String info = matchName + " (" + (searchMatchIndex + 1) + "/" + searchMatches.size() + ")";
                int color = matchIdx == currentBlockIndex ? 0xFF80FF80 : 0xFFFFFFFF;
                context.drawText(this.textRenderer, Text.literal(info), infoX, infoY, color, false);
            }
        }

        // Block icon next to the block selector
        MaterialBlock[] blocks = MaterialBlock.values();
        if (currentBlockIndex < blocks.length) {
            Block iconBlock = blocks[currentBlockIndex].getPrimaryBlock();
            if (iconBlock != null) {
                RadianceBlockIcon.drawBlockIcon(context, iconBlock, this.width - 44, 20, 24);
            }
        }

        // Auto-PBR channel preview panel (Cinema 4D / Blender style)
        if (previewsRegistered) {
            int thumbSize = 64;
            int gap = 6;
            int padding = 8;
            int labelH = 10;
            int totalW = thumbSize * 3 + gap * 2 + padding * 2;
            int totalH = thumbSize + labelH + padding * 2 + 2;
            int panelX = this.width - totalW - 6;
            int panelY = this.body.getY() + 4;
            var tr = this.textRenderer;

            // Dark background panel
            context.fill(panelX, panelY, panelX + totalW, panelY + totalH, 0xCC000000);
            // Thin border
            context.fill(panelX, panelY, panelX + totalW, panelY + 1, 0xFF444444);
            context.fill(panelX, panelY + totalH - 1, panelX + totalW, panelY + totalH, 0xFF444444);
            context.fill(panelX, panelY, panelX + 1, panelY + totalH, 0xFF444444);
            context.fill(panelX + totalW - 1, panelY, panelX + totalW, panelY + totalH, 0xFF444444);

            int tx = panelX + padding;
            int ty = panelY + padding;

            // Albedo
            context.drawTexture(RenderLayer::getGuiTextured, PREVIEW_ALBEDO_ID,
                tx, ty, 0, 0, thumbSize, thumbSize, thumbSize, thumbSize);
            String aLabel = "Albedo";
            context.drawText(tr, Text.literal(aLabel),
                tx + (thumbSize - tr.getWidth(aLabel)) / 2, ty + thumbSize + 2, 0xFFCCCCCC, false);

            // Normal
            int nx = tx + thumbSize + gap;
            context.drawTexture(RenderLayer::getGuiTextured, PREVIEW_NORMAL_ID,
                nx, ty, 0, 0, thumbSize, thumbSize, thumbSize, thumbSize);
            String nLabel = "Normal";
            context.drawText(tr, Text.literal(nLabel),
                nx + (thumbSize - tr.getWidth(nLabel)) / 2, ty + thumbSize + 2, 0xFFCCCCCC, false);

            // Roughness
            int rx = nx + thumbSize + gap;
            context.drawTexture(RenderLayer::getGuiTextured, PREVIEW_ROUGHNESS_ID,
                rx, ty, 0, 0, thumbSize, thumbSize, thumbSize, thumbSize);
            String rLabel = "Roughness";
            context.drawText(tr, Text.literal(rLabel),
                rx + (thumbSize - tr.getWidth(rLabel)) / 2, ty + thumbSize + 2, 0xFFCCCCCC, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;
        // Handle noise type dropdown clicks (overlay is above list widget)
        if (noiseDropdown != null && noiseDropdown.isOpen()) {
            if (noiseDropdown.mouseClicked(mouseX, mouseY, button)) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        // Propagate right-click drag for precision slider mode (button 1)
        // Minecraft's default only propagates button 0.
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        // Always forward printable characters to the search field
        if (searchField != null) {
            searchField.setFocused(true);
            if (searchField.charTyped(chr, modifiers)) return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        if (searchField != null) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                commitSearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB && !searchMatches.isEmpty()) {
                if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) {
                    searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
                } else {
                    searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN && !searchMatches.isEmpty()) {
                searchMatchIndex = (searchMatchIndex + 1) % searchMatches.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP && !searchMatches.isEmpty()) {
                searchMatchIndex = (searchMatchIndex - 1 + searchMatches.size()) % searchMatches.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) {
                    searchQuery = "";
                    searchField.setText("");
                    searchMatches.clear();
                    searchMatchIndex = 0;
                } else {
                    this.close();
                }
                return true;
            }
            // Forward backspace/delete to search field
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                searchField.setFocused(true);
                if (searchField.keyPressed(keyCode, scanCode, modifiers)) return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Transparent — game world shows through
    }

    @Override
    protected void initBody() {
        NoiseTypeDropdownWidget.clearInstances();
        this.body = this.layout.addBody(
            new WideOptionListWidget(this.client, this.width, this));
        addOptions();
    }

    @Override
    protected void initFooter() {
        DirectionalLayoutWidget footer = DirectionalLayoutWidget.horizontal().spacing(8);
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.settings.materials.apply"), btn -> applyChanges())
            .width(100).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.settings.materials.cancel"), btn -> cancelChanges())
            .width(100).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.materials.exportAll"), btn -> {
                MaterialsPack pack = MaterialsPack.fromCurrentOptions();
                pack.name = "All Materials";
                MaterialFileManager.savePack(pack, "all-materials");
            }).width(100).build());
        footer.add(ButtonWidget.builder(
            Text.translatable("radiance.materials.importPack"), btn -> {
                MinecraftClient.getInstance().setScreen(
                    new MaterialBrowserScreen(this, MaterialBrowserScreen.TAB_PACKS));
            }).width(100).build());
        this.layout.addFooter(footer);
    }

    @Override
    public void close() {
        NoiseTypeDropdownWidget.clearInstances();
        cleanupPreviews();
        cancelChanges();
    }

    private void loadSourceAlbedo(MaterialBlock block) {
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
        String id = block.getId();
        // Try multiple path patterns: exact match, then common suffixes for multi-face blocks
        String[] candidates = {
            id, id + "_top", id + "_side", id + "_front", id + "_still"
        };
        var rm = MinecraftClient.getInstance().getResourceManager();
        for (String name : candidates) {
            try {
                Identifier texId = Identifier.of("minecraft", "textures/block/" + name + ".png");
                Optional<Resource> res = rm.getResource(texId);
                if (res.isPresent()) {
                    sourceAlbedo = NativeImage.read(res.get().getInputStream());
                    break;
                }
            } catch (IOException e) {
                // Try next candidate
            }
        }
    }

    private void regeneratePreview() {
        if (sourceAlbedo == null) return;

        // Generate new pixel data
        NativeImage albedoCopy = new NativeImage(sourceAlbedo.getWidth(), sourceAlbedo.getHeight(), false);
        for (int y = 0; y < sourceAlbedo.getHeight(); y++)
            for (int x = 0; x < sourceAlbedo.getWidth(); x++)
                albedoCopy.setColorArgb(x, y, sourceAlbedo.getColorArgb(x, y));
        NativeImage normalImg = AutoPBRGenerator.generateNormal(sourceAlbedo);
        NativeImage roughImg = AutoPBRGenerator.generateRoughnessPreview(sourceAlbedo);

        if (!previewsRegistered) {
            // First time: create textures and register them once
            var texManager = MinecraftClient.getInstance().getTextureManager();
            previewAlbedoTex = new NativeImageBackedTexture(albedoCopy);
            previewAlbedoTex.upload();
            texManager.registerTexture(PREVIEW_ALBEDO_ID, previewAlbedoTex);

            previewNormalTex = new NativeImageBackedTexture(normalImg);
            previewNormalTex.upload();
            texManager.registerTexture(PREVIEW_NORMAL_ID, previewNormalTex);

            previewRoughTex = new NativeImageBackedTexture(roughImg);
            previewRoughTex.upload();
            texManager.registerTexture(PREVIEW_ROUGHNESS_ID, previewRoughTex);

            previewsRegistered = true;
        } else {
            // Subsequent updates: replace pixel data in-place, reuse same GL/Vulkan resources
            previewAlbedoTex.setImage(albedoCopy);
            previewAlbedoTex.upload();

            previewNormalTex.setImage(normalImg);
            previewNormalTex.upload();

            previewRoughTex.setImage(roughImg);
            previewRoughTex.upload();
        }
    }

    private void cleanupPreviews() {
        // NOTE: Do NOT call texManager.destroyTexture() — Vulkan has no intercept for
        // texture destruction and calling it causes stale descriptors → VK_ERROR_DEVICE_LOST.
        // Preview textures are static and persist across screen rebuilds (tiny memory: 3× 64×64 RGBA).
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
    }


    private void commitSearch() {
        if (searchMatches.isEmpty()) return;
        int match = searchMatches.get(searchMatchIndex);
        currentBlockIndex = match;
        // Clear search so the field is ready for a new query
        searchQuery = "";
        searchMatches.clear();
        searchMatchIndex = 0;
        MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
    }

    private static void updateSearchMatches() {
        searchMatches.clear();
        if (searchQuery == null || searchQuery.isEmpty()) return;
        String lower = searchQuery.toLowerCase();
        MaterialBlock[] blocks = MaterialBlock.values();
        for (int j = 0; j < blocks.length; j++) {
            String name = Text.translatable("options.video.materials." + blocks[j].getId()).getString().toLowerCase();
            String id = blocks[j].getId().toLowerCase();
            if (name.contains(lower) || id.contains(lower)) {
                searchMatches.add(j);
            }
        }
        if (searchMatchIndex >= searchMatches.size()) searchMatchIndex = 0;
    }

    @Override
    protected void addOptions() {
        MaterialBlock[] blocks = MaterialBlock.values();
        if (currentBlockIndex >= blocks.length) currentBlockIndex = 0;
        MaterialBlock block = blocks[currentBlockIndex];
        int i = block.ordinal();

        // === Global toggles (immediate effect) ===
        SimpleOption<Boolean> overridesToggle = SimpleOption.ofBoolean(
            "options.video.materials.overridesEnabled",
            Options.materialOverridesEnabled,
            value -> {
                Options.materialOverridesEnabled = value;
                Options.overwriteConfig();
            });

        SimpleOption<Boolean> autoPBRToggle = SimpleOption.ofBoolean(
            "options.video.materials.autoPBR",
            Options.autoPBREnabled,
            value -> {
                Options.autoPBREnabled = value;
                LiveNormalReuploader.scheduleReupload();
            });

        this.body.addAll(new SimpleOption[]{overridesToggle, autoPBRToggle});

        // === Block selector (full-width slider) ===
        ResettableSliderWidget blockSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, blocks.length - 1, currentBlockIndex, 0,
            v -> {
                MaterialBlock b = MaterialBlock.values()[v];
                String name = Text.translatable("options.video.materials." + b.getId()).getString();
                return Text.literal(name + " (" + (v + 1) + "/" + blocks.length + ")");
            },
            v -> { currentBlockIndex = v; });
        blockSelector.setOnRelease(() -> {
            if (currentBlockIndex != i) {
                MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
            }
        });
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(blockSelector, body));

        // === Per-block Auto-PBR toggle ===
        final int blockIdx_apbr = i;
        ButtonWidget autoPBRBlockBtn = ButtonWidget.builder(
            Text.literal("Auto-PBR: " + (Options.materialAutoPBR[i] ? "ON" : "OFF")),
            btn -> {
                Options.materialAutoPBR[blockIdx_apbr] = !Options.materialAutoPBR[blockIdx_apbr];
                btn.setMessage(Text.literal("Auto-PBR: " + (Options.materialAutoPBR[blockIdx_apbr] ? "ON" : "OFF")));
                // Hot-swap: re-upload Auto-PBR textures without disrupting DLSS-RR temporal history
                LiveNormalReuploader.scheduleReupload();
            }).width(150).build();
        // Normal Source dropdown: Auto / Custom / Flat / Blender PBR
        final String[] inputTypeLabels = {"Auto", "Custom", "Flat", "Blender PBR"};
        final int blockIdx_ns = i;
        ButtonWidget normalSourceBtn = ButtonWidget.builder(
            Text.literal("Normal: " + inputTypeLabels[Options.materialNormalInputType[i]]),
            btn -> {
                int next = (Options.materialNormalInputType[blockIdx_ns] + 1) % 4;
                Options.materialNormalInputType[blockIdx_ns] = next;
                // Blender PBR forces both channels to match
                if (next == 3) Options.materialSpecularInputType[blockIdx_ns] = 3;
                btn.setMessage(Text.literal("Normal: " + inputTypeLabels[next]));
            }).width(150).build();
        // Specular Source dropdown: Auto / Custom / Flat / Blender PBR
        final int blockIdx_ss = i;
        ButtonWidget specularSourceBtn = ButtonWidget.builder(
            Text.literal("Specular: " + inputTypeLabels[Options.materialSpecularInputType[i]]),
            btn -> {
                int next = (Options.materialSpecularInputType[blockIdx_ss] + 1) % 4;
                Options.materialSpecularInputType[blockIdx_ss] = next;
                // Blender PBR forces both channels to match
                if (next == 3) Options.materialNormalInputType[blockIdx_ss] = 3;
                btn.setMessage(Text.literal("Specular: " + inputTypeLabels[next]));
            }).width(150).build();
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(autoPBRBlockBtn, null, body));
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(normalSourceBtn, specularSourceBtn, body));

        // === Auto-PBR Preview (loaded here, rendered in render()) ===
        if (Options.autoPBREnabled && Options.materialAutoPBR[i]) {
            loadSourceAlbedo(block);
            regeneratePreview();
        }

        // === Reset to Defaults ===
        final int currentIdx = i;
        ButtonWidget resetDefaultsBtn = ButtonWidget.builder(
            Text.literal("Reset to Defaults"),
            btn -> {
                MaterialData defaults = MaterialData.fromBlock(block);
                if (defaults != null) {
                    defaults.applyToOptions(currentIdx);
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }
            }).width(150).build();
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(resetDefaultsBtn, null, body));

        // === Material Actions (all in one row) ===
        ButtonWidget copyBtn = ButtonWidget.builder(
            Text.literal("Copy"),
            btn -> {
                MaterialClipboard.copy(currentIdx);
                btn.setMessage(Text.literal("Copied!"));
            }).width(70).build();

        ButtonWidget pasteBtn = ButtonWidget.builder(
            Text.literal("Paste"),
            btn -> {
                if (MaterialClipboard.paste(currentIdx)) {
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }
            }).width(70).build();

        ButtonWidget exportBtn = ButtonWidget.builder(
            Text.literal("Export"),
            btn -> {
                MaterialData data = MaterialData.fromOptions(currentIdx);
                if (data != null) {
                    Path path = MaterialFileManager.saveMaterial(data, data.blockId);
                    if (path != null) btn.setMessage(Text.literal("Saved!"));
                }
            }).width(70).build();

        ButtonWidget browserBtn = ButtonWidget.builder(
            Text.literal("Browser"),
            btn -> MinecraftClient.getInstance().setScreen(new MaterialBrowserScreen(this))
        ).width(70).build();

        this.body.addEntry(new RadianceSettingsScreen.FourColumnButtonEntry(copyBtn, pasteBtn, exportBtn, browserBtn, body));

        // === Parent/Child indicator ===
        if (!block.isParent()) {
            MaterialBlock parentBlock = block.getParentMaterial();
            String parentName = Text.translatable("options.video.materials." + parentBlock.getId()).getString();
            ButtonWidget resetParentBtn = ButtonWidget.builder(
                Text.translatable("radiance.texture_editor.reset_to_parent"),
                btn -> {
                    int pi = parentBlock.ordinal();
                    Options.materialF0R[i] = Options.materialF0R[pi];
                    Options.materialF0G[i] = Options.materialF0G[pi];
                    Options.materialF0B[i] = Options.materialF0B[pi];
                    Options.materialRoughness[i] = Options.materialRoughness[pi];
                    Options.materialMetallic[i] = Options.materialMetallic[pi];
                    Options.materialTransmission[i] = Options.materialTransmission[pi];
                    Options.materialIOR[i] = Options.materialIOR[pi];
                    Options.materialSubsurface[i] = Options.materialSubsurface[pi];
                    Options.materialAnisotropic[i] = Options.materialAnisotropic[pi];
                    Options.materialSheenWeight[i] = Options.materialSheenWeight[pi];
                    Options.materialSheenTint[i] = Options.materialSheenTint[pi];
                    Options.materialCoatWeight[i] = Options.materialCoatWeight[pi];
                    Options.materialCoatRoughness[i] = Options.materialCoatRoughness[pi];
                    Options.materialChannelR[i] = Options.materialChannelR[pi];
                    Options.materialChannelG[i] = Options.materialChannelG[pi];
                    Options.materialChannelB[i] = Options.materialChannelB[pi];
                    Options.materialTextureBlend[i] = Options.materialTextureBlend[pi];
                    Options.materialChildOverride[i] = false;
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }).width(150).build();
            // Show as a label + button row
            ButtonWidget variantLabel = ButtonWidget.builder(
                Text.literal("Variant of: " + parentName),
                btn -> {
                    // Navigate to parent
                    currentBlockIndex = parentBlock.ordinal();
                    MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
                }).width(150).build();
            this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(variantLabel, resetParentBtn, body));
        }

        // === Metal Presets (quick setup, near top) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Presets"), body));

        MetalPreset[] presets = MetalPreset.values();
        if (currentPresetIndex >= presets.length) currentPresetIndex = 0;

        ResettableSliderWidget presetSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, presets.length - 1, currentPresetIndex, 0,
            v -> {
                MetalPreset p = MetalPreset.values()[v];
                return Text.literal(p.getDisplayName());
            },
            v -> { currentPresetIndex = v; });

        final int blockIdx = i;
        ButtonWidget loadPresetBtn = ButtonWidget.builder(
            Text.translatable("options.video.materials.loadPreset"),
            btn -> {
                MetalPreset p = MetalPreset.values()[currentPresetIndex];
                Options.materialF0R[blockIdx]       = p.getF0R();
                Options.materialF0G[blockIdx]       = p.getF0G();
                Options.materialF0B[blockIdx]       = p.getF0B();
                Options.materialRoughness[blockIdx] = p.getRoughness();
                Options.materialMetallic[blockIdx]  = 1000;
                MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
            })
            .width(150).build();
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(presetSelector, loadPresetBtn, body));

        // === Surface (core Principled BSDF) ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Surface"), body));

        // Metallic + Roughness (primary pair)
        ResettableSliderWidget metallic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialMetallic[i], block.getDefaultMetallic(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.metallic"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> {
                Options.materialMetallic[i] = v;
                // Auto-set minimum F0 when turning metallic on for blocks with zero F0 (e.g. obsidian).
                // Without F0, metals are invisible black (albedo = F0 = 0,0,0).
                if (v >= 500 && Options.materialF0R[i] == 0 && Options.materialF0G[i] == 0 && Options.materialF0B[i] == 0) {
                    Options.materialF0R[i] = 500;
                    Options.materialF0G[i] = 500;
                    Options.materialF0B[i] = 500;
                }
                onSliderChanged(i);
            });
        ResettableSliderWidget roughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialRoughness[i], block.getDefaultRoughness(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.roughness"),
                Text.literal(v + "%")),
            v -> { Options.materialRoughness[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(metallic, roughness, body));

        // Fresnel F0 — right after metallic (determines metal color and dielectric reflectance)
        ResettableSliderWidget f0r = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0R[i], block.getDefaultF0R(),
            v -> getGenericValueText(
                Text.literal("F0 R"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0R[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0g = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0G[i], block.getDefaultF0G(),
            v -> getGenericValueText(
                Text.literal("F0 G"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0G[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0b = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0B[i], block.getDefaultF0B(),
            v -> getGenericValueText(
                Text.literal("F0 B"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0B[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(f0r, f0g, f0b, null, body));

        // IOR + Transmission
        ResettableSliderWidget ior = new ResettableSliderWidget(0, 0, 150, 20,
            1000, 3000, Math.max(Options.materialIOR[i], 1000), Math.max(block.getDefaultIOR(), 1000),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.ior"),
                Text.literal(String.format("%.3f", v / 1000.0))),
            v -> {
                Options.materialIOR[i] = v;
                if (Options.materialMetallic[i] < 500) {
                    int f0pm = MaterialBlock.iorToF0Permille(v);
                    Options.materialF0R[i] = f0pm;
                    Options.materialF0G[i] = f0pm;
                    Options.materialF0B[i] = f0pm;
                }
                onSliderChanged(i);
            });
        ResettableSliderWidget transmission = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialTransmission[i], block.getDefaultTransmission(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.transmission"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialTransmission[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(ior, transmission, body));

        // Subsurface + Anisotropic
        ResettableSliderWidget subsurface = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSubsurface[i], block.getDefaultSubsurface(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.subsurface"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSubsurface[i] = v; onSliderChanged(i); });
        ResettableSliderWidget anisotropic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialAnisotropic[i], block.getDefaultAnisotropic(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.anisotropic"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialAnisotropic[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(subsurface, anisotropic, body));

        // === Coating & Fabric ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Coating & Fabric"), body));

        ResettableSliderWidget coatWeight = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialCoatWeight[i], block.getDefaultCoatWeight(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.coatWeight"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialCoatWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget coatRoughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialCoatRoughness[i], block.getDefaultCoatRoughness(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.coatRoughness"),
                Text.literal(v + "%")),
            v -> { Options.materialCoatRoughness[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(coatWeight, coatRoughness, body));

        ResettableSliderWidget sheenWeight = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSheenWeight[i], block.getDefaultSheenWeight(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.sheenWeight"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget sheenTint = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialSheenTint[i], block.getDefaultSheenTint(),
            v -> getGenericValueText(
                Text.translatable("options.video.materials.sheenTint"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenTint[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(sheenWeight, sheenTint, body));

        // === Texture Roughness Override ===
        // WARNING: When Texture Blend > 0%, this OVERRIDES the Roughness slider above.
        // The roughness is derived from the albedo texture luminance instead.
        {
            String blendLabel = Options.materialTextureBlend[i] > 0
                ? "Texture Roughness Override (ACTIVE: " + Options.materialTextureBlend[i] + "%)"
                : "Texture Roughness Override (off)";
            this.body.addEntry(new CategoryVideoOptionEntry(
                Text.literal(blendLabel), body));
        }

        // Blend amount — prominent, full-width, shown first
        ResettableSliderWidget textureBlend = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialTextureBlend[i], 30,
            v -> getGenericValueText(
                Text.literal("Blend Amount"),
                Text.literal(v > 0 ? v + "% (overrides roughness)" : "0% (off)")),
            v -> { Options.materialTextureBlend[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(textureBlend, body));

        // Channel routing (only meaningful when blend > 0)
        ResettableSliderWidget channelR = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialChannelR[i], 213,
            v -> getGenericValueText(
                Text.literal("Ch R"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialChannelR[i] = v; onSliderChanged(i); });
        ResettableSliderWidget channelG = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialChannelG[i], 715,
            v -> getGenericValueText(
                Text.literal("Ch G"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialChannelG[i] = v; onSliderChanged(i); });
        ResettableSliderWidget channelB = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialChannelB[i], 72,
            v -> getGenericValueText(
                Text.literal("Ch B"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialChannelB[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(channelR, channelG, channelB, null, body));

        // === Color ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Color"), body));
        ResettableSliderWidget gamutBoost = new ResettableSliderWidget(0, 0, 150, 20,
            0, 200, Options.materialGamutBoost[i], 100,
            v -> getGenericValueText(
                Text.literal("Gamut Boost"),
                Text.literal(String.format("\u00d7%.2f", v / 100.0))),
            v -> { Options.materialGamutBoost[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.SliderEntry(gamutBoost, body));

        // === Procedural Noise ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Procedural Noise"), body));

        // Row 1: Noise Type dropdown + Seed slider
        noiseDropdown = new NoiseTypeDropdownWidget(0, 0, 100, 20, type -> {
            Options.materialNoiseType[i] = type; onSliderChanged(i);
        });
        noiseDropdown.setNoiseType(Options.materialNoiseType[i]);
        ResettableSliderWidget noiseSeed = new ResettableSliderWidget(0, 0, 100, 20,
            0, 999, Options.materialNoiseSeed[i], 0,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseSeed"),
                Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseSeed[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(noiseDropdown, noiseSeed, body));

        // Row 2: Scale, Strength, Octaves
        ResettableSliderWidget noiseScale = new ResettableSliderWidget(0, 0, 100, 20,
            1, 1000, Options.materialNoiseScale[i], 50,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseScale"),
                Text.literal(String.format("%.1f", v / 10.0))),
            v -> { Options.materialNoiseScale[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseStrength = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialNoiseStrength[i], 0,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseStrength"),
                Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialNoiseStrength[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseOctaves = new ResettableSliderWidget(0, 0, 100, 20,
            1, 8, Options.materialNoiseOctaves[i], 2,
            v -> getGenericValueText(
                Text.translatable("options.video.materials.noiseOctaves"),
                Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseOctaves[i] = v; onSliderChanged(i); });
        this.body.addEntry(new RadianceSettingsScreen.FourColumnSliderEntry(noiseScale, noiseStrength, noiseOctaves, null, body));

        // === Auto-PBR Parameters ===
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.translatable("options.video.category.materials.autoPBR"), body));

        // Separate invert toggles for normal and roughness
        ButtonWidget invertNormalBtn = ButtonWidget.builder(
            Text.literal("Invert Normal: " + (Options.autoPBRInvertNormal ? "ON" : "OFF")),
            btn -> {
                Options.autoPBRInvertNormal = !Options.autoPBRInvertNormal;
                btn.setMessage(Text.literal("Invert Normal: " + (Options.autoPBRInvertNormal ? "ON" : "OFF")));
                regeneratePreview();
                LiveNormalReuploader.scheduleReupload();
            }).width(150).build();
        ButtonWidget invertRoughBtn = ButtonWidget.builder(
            Text.literal("Invert Roughness: " + (Options.autoPBRInvertRoughness ? "ON" : "OFF")),
            btn -> {
                Options.autoPBRInvertRoughness = !Options.autoPBRInvertRoughness;
                btn.setMessage(Text.literal("Invert Roughness: " + (Options.autoPBRInvertRoughness ? "ON" : "OFF")));
                regeneratePreview();
                LiveNormalReuploader.scheduleReupload();
            }).width(150).build();
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnOptionEntry(invertNormalBtn, invertRoughBtn, body));

        ResettableSliderWidget roughGamma = new ResettableSliderWidget(0, 0, 150, 20,
            10, 200, Options.autoPBRRoughnessGamma, 50,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessGamma"),
                Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.autoPBRRoughnessGamma = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget roughMin = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBRRoughnessMin, 30,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessMin"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMin = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(roughGamma, roughMin, body));

        ResettableSliderWidget roughMax = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBRRoughnessMax, 95,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.roughnessMax"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMax = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget normStr = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.autoPBRNormalStrength, 250,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.normalStrength"),
                Text.literal(String.format("%.1f", v / 100.0))),
            v -> { Options.autoPBRNormalStrength = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(roughMax, normStr, body));

        ResettableSliderWidget varWt = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBRVarianceWeight, 30,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.varianceWeight"),
                Text.literal(v + "%")),
            v -> { Options.autoPBRVarianceWeight = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget edgeWt = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.autoPBREdgeWeight, 15,
            v -> getGenericValueText(
                Text.translatable("options.video.autoPBR.edgeWeight"),
                Text.literal(v + "%")),
            v -> { Options.autoPBREdgeWeight = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        this.body.addEntry(new RadianceSettingsScreen.TwoColumnSliderEntry(varWt, edgeWt, body));
    }

}
