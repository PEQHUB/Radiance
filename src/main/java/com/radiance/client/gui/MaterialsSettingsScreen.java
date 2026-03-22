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
    private static final Identifier PREVIEW_HEIGHT_ID = Identifier.of("radiance", "autopbr_preview/height");
    private static NativeImageBackedTexture previewAlbedoTex;
    private static NativeImageBackedTexture previewNormalTex;
    private static NativeImageBackedTexture previewRoughTex;
    private static NativeImageBackedTexture previewHeightTex;
    private static boolean previewsRegistered = false;
    // 0=Albedo, 1=Normal, 2=Roughness, 3=Height
    private static int selectedPreviewMask = 2;

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
        Options.markMaterialDirty();
        com.radiance.client.material.MaterialRegistry.markDirty();
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
    // snapChannelR/G/B and snapTextureBlend removed — dead fields (Pack 4 now uses pomPacked0/1/2 + pomDepth)
    private static final int[] snapGamutBoost = new int[Options.MAX_MATERIALS];
    private static final int[] snapPomDepth = new int[Options.MAX_MATERIALS];
    private static final int[] snapAutoPBRRoughnessMin = new int[Options.MAX_MATERIALS];
    private static final int[] snapAutoPBRRoughnessMax = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRGamma = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRNormStr = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRVarWt = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBREdgeWt = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRHtGamma = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRFlags = new int[Options.MAX_MATERIALS];
    private static final boolean[] snapAutoPBR = new boolean[Options.MAX_MATERIALS];
    private static final boolean[] snapChildOverride = new boolean[Options.MAX_MATERIALS];
    private static boolean snapAutoPBREnabled;
    private static int snapAutoPBRRoughnessGamma, snapAutoPBRGlobalRoughnessMin, snapAutoPBRGlobalRoughnessMax;
    private static int snapAutoPBRNormalStrength, snapAutoPBRVarianceWeight, snapAutoPBREdgeWeight;
    private static int snapAutoPBRHeightGamma;

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
        System.arraycopy(Options.materialGamutBoost, 0, snapGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPomDepth, 0, snapPomDepth, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRRoughnessMin, 0, snapAutoPBRRoughnessMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRRoughnessMax, 0, snapAutoPBRRoughnessMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRGamma, 0, snapPerBlockAutoPBRGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRNormalStrength, 0, snapPerBlockAutoPBRNormStr, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRVarianceWeight, 0, snapPerBlockAutoPBRVarWt, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBREdgeWeight, 0, snapPerBlockAutoPBREdgeWt, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRHeightGamma, 0, snapPerBlockAutoPBRHtGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRFlags, 0, snapPerBlockAutoPBRFlags, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBR, 0, snapAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChildOverride, 0, snapChildOverride, 0, Options.MAX_MATERIALS);
        snapAutoPBREnabled = Options.autoPBREnabled;
        snapAutoPBRRoughnessGamma = Options.autoPBRRoughnessGamma;
        snapAutoPBRGlobalRoughnessMin = Options.autoPBRRoughnessMin;
        snapAutoPBRGlobalRoughnessMax = Options.autoPBRRoughnessMax;
        snapAutoPBRNormalStrength = Options.autoPBRNormalStrength;
        snapAutoPBRVarianceWeight = Options.autoPBRVarianceWeight;
        snapAutoPBREdgeWeight = Options.autoPBREdgeWeight;
        snapAutoPBRHeightGamma = Options.autoPBRHeightGamma;
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
        System.arraycopy(snapGamutBoost, 0, Options.materialGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPomDepth, 0, Options.materialPomDepth, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBRRoughnessMin, 0, Options.materialAutoPBRRoughnessMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBRRoughnessMax, 0, Options.materialAutoPBRRoughnessMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRGamma, 0, Options.materialAutoPBRGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRNormStr, 0, Options.materialAutoPBRNormalStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRVarWt, 0, Options.materialAutoPBRVarianceWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBREdgeWt, 0, Options.materialAutoPBREdgeWeight, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRHtGamma, 0, Options.materialAutoPBRHeightGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRFlags, 0, Options.materialAutoPBRFlags, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBR, 0, Options.materialAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChildOverride, 0, Options.materialChildOverride, 0, Options.MAX_MATERIALS);
        Options.autoPBREnabled = snapAutoPBREnabled;
        Options.autoPBRRoughnessGamma = snapAutoPBRRoughnessGamma;
        Options.autoPBRRoughnessMin = snapAutoPBRGlobalRoughnessMin;
        Options.autoPBRRoughnessMax = snapAutoPBRGlobalRoughnessMax;
        Options.autoPBRNormalStrength = snapAutoPBRNormalStrength;
        Options.autoPBRVarianceWeight = snapAutoPBRVarianceWeight;
        Options.autoPBREdgeWeight = snapAutoPBREdgeWeight;
        Options.autoPBRHeightGamma = snapAutoPBRHeightGamma;
        Options.markMaterialDirty();
        com.radiance.client.material.MaterialRegistry.markDirty();
    }

    private boolean autoPBRParamsChanged() {
        if (Options.autoPBREnabled != snapAutoPBREnabled
            || Options.autoPBRRoughnessGamma != snapAutoPBRRoughnessGamma
            || Options.autoPBRRoughnessMin != snapAutoPBRGlobalRoughnessMin
            || Options.autoPBRRoughnessMax != snapAutoPBRGlobalRoughnessMax
            || Options.autoPBRNormalStrength != snapAutoPBRNormalStrength
            || Options.autoPBRVarianceWeight != snapAutoPBRVarianceWeight
            || Options.autoPBREdgeWeight != snapAutoPBREdgeWeight
            || Options.autoPBRHeightGamma != snapAutoPBRHeightGamma) return true;
        for (int j = 0; j < Options.MAX_MATERIALS; j++) {
            if (Options.materialAutoPBR[j] != snapAutoPBR[j]) return true;
            if (Options.materialAutoPBRRoughnessMin[j] != snapAutoPBRRoughnessMin[j]) return true;
            if (Options.materialAutoPBRRoughnessMax[j] != snapAutoPBRRoughnessMax[j]) return true;
            if (Options.materialAutoPBRGamma[j] != snapPerBlockAutoPBRGamma[j]) return true;
            if (Options.materialAutoPBRNormalStrength[j] != snapPerBlockAutoPBRNormStr[j]) return true;
            if (Options.materialAutoPBRVarianceWeight[j] != snapPerBlockAutoPBRVarWt[j]) return true;
            if (Options.materialAutoPBREdgeWeight[j] != snapPerBlockAutoPBREdgeWt[j]) return true;
            if (Options.materialAutoPBRHeightGamma[j] != snapPerBlockAutoPBRHtGamma[j]) return true;
            if (Options.materialAutoPBRFlags[j] != snapPerBlockAutoPBRFlags[j]) return true;
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
        applyRadianceScale();
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
                int rowW = Math.min(600, this.width - 40);
                int rowRight = (this.width + rowW) / 2;
                RadianceBlockIcon.drawBlockIcon(context, iconBlock, rowRight - 56, 18, 48);
            }
        }

        // Auto-PBR channel preview panel (Cinema 4D / Blender style)
        if (previewsRegistered) {
            int thumbSize = 36;
            int gap = 3;
            int padding = 4;
            int labelH = 10;
            int totalW = thumbSize * 4 + gap * 3 + padding * 2;
            int totalH = thumbSize + labelH + padding * 2 + 2;
            int contentRowW = Math.min(600, this.width - 40);
            int contentRight = (this.width + contentRowW) / 2;
            int panelX = contentRight - totalW;
            int panelY = this.body.getY() + 4;
            var tr = this.textRenderer;

            // Dark background panel
            context.fill(panelX, panelY, panelX + totalW, panelY + totalH, 0xCC000000);
            context.fill(panelX, panelY, panelX + totalW, panelY + 1, 0xFF444444);
            context.fill(panelX, panelY + totalH - 1, panelX + totalW, panelY + totalH, 0xFF444444);
            context.fill(panelX, panelY, panelX + 1, panelY + totalH, 0xFF444444);
            context.fill(panelX + totalW - 1, panelY, panelX + totalW, panelY + totalH, 0xFF444444);

            int tx = panelX + padding;
            int ty = panelY + padding;

            Identifier[] texIds = {PREVIEW_ALBEDO_ID, PREVIEW_NORMAL_ID, PREVIEW_ROUGHNESS_ID, PREVIEW_HEIGHT_ID};
            String[] labels = {"Albedo", "Normal", "Rough", "Height"};
            for (int mi = 0; mi < 4; mi++) {
                int mx = tx + mi * (thumbSize + gap);
                context.drawTexture(RenderLayer::getGuiTextured, texIds[mi],
                    mx, ty, 0, 0, thumbSize, thumbSize, thumbSize, thumbSize);
                // Selection outline
                if (mi == selectedPreviewMask) {
                    int oc = 0xFF2AB5A0; // teal
                    context.fill(mx - 1, ty - 1, mx + thumbSize + 1, ty, oc);           // top
                    context.fill(mx - 1, ty + thumbSize, mx + thumbSize + 1, ty + thumbSize + 1, oc); // bottom
                    context.fill(mx - 1, ty - 1, mx, ty + thumbSize + 1, oc);            // left
                    context.fill(mx + thumbSize, ty - 1, mx + thumbSize + 1, ty + thumbSize + 1, oc); // right
                }
                String label = labels[mi];
                int labelColor = mi == selectedPreviewMask ? 0xFF2AB5A0 : 0xFFCCCCCC;
                context.drawText(tr, Text.literal(label),
                    mx + (thumbSize - tr.getWidth(label)) / 2, ty + thumbSize + 2, labelColor, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (RadianceTheme.handleBreadcrumbClick(mouseX, mouseY, parentScreen)) return true;
        // Handle preview thumbnail clicks
        if (previewsRegistered) {
            int thumbSize = 36, gap = 3, padding = 4, labelH = 10;
            int totalW = thumbSize * 4 + gap * 3 + padding * 2;
            int clickContentRowW = Math.min(600, this.width - 40);
            int clickContentRight = (this.width + clickContentRowW) / 2;
            int panelX = clickContentRight - totalW;
            int panelY = this.body.getY() + 4;
            int tx = panelX + padding;
            int ty = panelY + padding;
            for (int mi = 0; mi < 4; mi++) {
                int mx = tx + mi * (thumbSize + gap);
                if (mouseX >= mx && mouseX < mx + thumbSize && mouseY >= ty && mouseY < ty + thumbSize) {
                    selectedPreviewMask = mi;
                    // Rebuild screen to show mask-specific controls
                    rebuildSelf();
                    return true;
                }
            }
        }
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
        if (!RadianceTheme.peekActive) {
            context.fill(0, 0, this.width, this.height, 0xF0080808);
        }
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
        this.layout.addFooter(footer);
    }

    @Override
    public void close() {
        RadianceTheme.endSliderFocus(); // clear any stuck slider focus
        NoiseTypeDropdownWidget.clearInstances();
        cleanupPreviews();
        cancelChanges();
    }

    /**
     * Rebuild this screen in place. If running as an overlay inside RadianceUnifiedScreen,
     * refreshes the overlay without calling setScreen(). Otherwise falls back to setScreen().
     */
    private void rebuildSelf() {
        if (parentScreen instanceof com.radiance.client.gui.unified.RadianceUnifiedScreen unified
                && unified.isOverlayShowing()) {
            unified.showOverlay(new MaterialsSettingsScreen(parentScreen));
        } else {
            MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
        }
    }

    @Override
    protected void refreshWidgetPositions() {
        // Screen.init(client, w, h) skips init() when screenInitialized=true
        // and calls refreshWidgetPositions() instead. Re-apply Radiance scaling
        // before the layout refresh so the body gets the correct dimensions.
        applyRadianceScale();
        super.refreshWidgetPositions();
    }

    /** Apply Radiance fixed scaling to the screen dimensions. */
    private void applyRadianceScale() {
        var window = this.client.getWindow();
        var accessor = (com.radiance.mixins.vulkan_render_integration.WindowAccessorMixin) (Object) window;
        int pixelWidth = window.getWidth();
        double radianceScale = Math.max(1.0, Math.round((double) pixelWidth / 900.0));
        if (GuiScaleHelper.savedScaleFactor < 0) {
            GuiScaleHelper.savedScaleFactor = accessor.radiance$getScaleFactor();
            GuiScaleHelper.savedScaledWidth = accessor.radiance$getScaledWidth();
            GuiScaleHelper.savedScaledHeight = accessor.radiance$getScaledHeight();
        }
        accessor.radiance$setScaleFactor(radianceScale);
        accessor.radiance$setScaledWidth((int) Math.ceil((double) pixelWidth / radianceScale));
        accessor.radiance$setScaledHeight((int) Math.ceil((double) window.getHeight() / radianceScale));
        this.width = accessor.radiance$getScaledWidth();
        this.height = accessor.radiance$getScaledHeight();
    }

    @Override
    public void removed() {
        RadianceTheme.endSliderFocus();
        super.removed();
        // Restore scale when leaving to non-Radiance screen
        Screen next = this.client != null ? this.client.currentScreen : null;
        if (!(next instanceof MaterialsSettingsScreen)
                && !(next instanceof com.radiance.client.gui.unified.RadianceUnifiedScreen)) {
            GuiScaleHelper.restoreOriginalScale();
        }
    }

    private void loadSourceAlbedo(MaterialBlock block) {
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
        String id = block.getId();
        // Try multiple path patterns: exact match, then common suffixes for multi-face blocks
        String[] candidates = {
            id, id + "_top", id + "_side", id + "_front", id + "_still",
            id + "_block", id + "_block_top", id + "_block_side",
            "white_" + id, "oak_" + id,
            id + "_planks", "brain_" + id,
            id + "_inner", id + "_block_side_inner"
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
        if (sourceAlbedo == null) {
            // Clear preview to avoid stale data from previous block
            if (previewsRegistered) {
                NativeImage blank = new NativeImage(16, 16, false);
                for (int py = 0; py < 16; py++)
                    for (int px = 0; px < 16; px++)
                        blank.setColorArgb(px, py, 0xFF202020);
                previewAlbedoTex.setImage(blank.applyToCopy(i -> i));
                previewAlbedoTex.upload();
                previewNormalTex.setImage(blank.applyToCopy(i -> (128 << 24) | (128 << 16) | (128 << 8) | 255));
                previewNormalTex.upload();
                previewRoughTex.setImage(blank.applyToCopy(i -> i));
                previewRoughTex.upload();
                previewHeightTex.setImage(blank.applyToCopy(i -> i));
                previewHeightTex.upload();
                blank.close();
            }
            return;
        }

        // Generate new pixel data — crop to first frame for animated spritesheets
        // (e.g. water_still.png is 16x512 = 32 frames; only use first 16x16 frame)
        int srcW = sourceAlbedo.getWidth();
        int srcH = Math.min(sourceAlbedo.getHeight(), srcW); // first frame = square
        NativeImage albedoCopy = new NativeImage(srcW, srcH, false);
        for (int y = 0; y < srcH; y++)
            for (int x = 0; x < srcW; x++)
                albedoCopy.setColorArgb(x, y, sourceAlbedo.getColorArgb(x, y));
        int ci = currentBlockIndex;
        int flags = Options.materialAutoPBRFlags[ci];
        boolean invertRough = (flags & 1) != 0;
        boolean invertNormal = (flags & 2) != 0;
        boolean invertHeight = (flags & 4) != 0;
        int chR = Options.materialChannelR[ci], chG = Options.materialChannelG[ci], chB = Options.materialChannelB[ci];
        // Use cropped copy for AutoPBR generation (first frame only for animated textures)
        NativeImage normalImg = AutoPBRGenerator.generateNormal(albedoCopy,
            Options.materialAutoPBRNormalStrength[ci], invertNormal,
            Options.materialAutoPBRHeightGamma[ci], invertHeight, chR, chG, chB);
        NativeImage roughImg = AutoPBRGenerator.generateRoughnessPreviewPercentile(albedoCopy,
            Options.materialAutoPBRRoughnessMin[ci], Options.materialAutoPBRRoughnessMax[ci],
            Options.materialPercentileCenter[ci], Options.materialPercentileSpread[ci],
            invertRough, chR, chG, chB);
        NativeImage heightImg = AutoPBRGenerator.generateHeightPreview(albedoCopy,
            Options.materialAutoPBRHeightGamma[ci], invertHeight);

        if (!previewsRegistered) {
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

            previewHeightTex = new NativeImageBackedTexture(heightImg);
            previewHeightTex.upload();
            texManager.registerTexture(PREVIEW_HEIGHT_ID, previewHeightTex);

            previewsRegistered = true;
        } else {
            // If dimensions changed (e.g. texture pack switch), recreate Vulkan images
            var existingImg = previewAlbedoTex.getImage();
            if (existingImg == null || existingImg.getWidth() != albedoCopy.getWidth()
                    || existingImg.getHeight() != albedoCopy.getHeight()) {
                // Destroy and re-register at new dimensions
                var texManager = MinecraftClient.getInstance().getTextureManager();
                texManager.destroyTexture(PREVIEW_ALBEDO_ID);
                texManager.destroyTexture(PREVIEW_NORMAL_ID);
                texManager.destroyTexture(PREVIEW_ROUGHNESS_ID);
                texManager.destroyTexture(PREVIEW_HEIGHT_ID);
                previewsRegistered = false;
                regeneratePreview();
                return;
            }
            previewAlbedoTex.setImage(albedoCopy);
            previewAlbedoTex.upload();

            previewNormalTex.setImage(normalImg);
            previewNormalTex.upload();

            previewRoughTex.setImage(roughImg);
            previewRoughTex.upload();

            previewHeightTex.setImage(heightImg);
            previewHeightTex.upload();
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
        rebuildSelf();
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
        final int idx = i;

        // Load source albedo for Auto-PBR preview (must happen before sliders that call regeneratePreview)
        loadSourceAlbedo(block);
        regeneratePreview();

        // === Global killswitch ===
        SimpleOption<Boolean> overridesToggle = SimpleOption.ofBoolean(
            "options.video.materials.overridesEnabled",
            Options.materialOverridesEnabled,
            value -> { Options.materialOverridesEnabled = value; Options.markMaterialDirty(); com.radiance.client.material.MaterialRegistry.markDirty(); Options.overwriteConfig(); });
        this.body.addAll(new SimpleOption[]{overridesToggle});

        // === Block selector ===
        ResettableSliderWidget blockSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, blocks.length - 1, currentBlockIndex, 0,
            v -> {
                MaterialBlock b = MaterialBlock.values()[v];
                String name = Text.translatable("options.video.materials." + b.getId()).getString();
                return Text.literal(name + " (" + (v + 1) + "/" + blocks.length + ")");
            },
            v -> { currentBlockIndex = v; });
        blockSelector.setOnRelease(() -> {
            // Rebuild screen to show the newly selected block's settings
            rebuildSelf();
        });
        this.body.addEntry(new LegacySliderEntry(blockSelector, body));

        // === Reset + Actions (one compact row) ===
        ButtonWidget resetBtn = ButtonWidget.builder(Text.literal("Reset"), btn -> {
            MaterialData defaults = MaterialData.fromBlock(block);
            if (defaults != null) { defaults.applyToOptions(idx); rebuildSelf(); }
        }).width(70).build();
        ButtonWidget copyBtn = ButtonWidget.builder(Text.literal("Copy"), btn -> {
            MaterialClipboard.copy(idx); btn.setMessage(Text.literal("Copied!"));
        }).width(70).build();
        ButtonWidget pasteBtn = ButtonWidget.builder(Text.literal("Paste"), btn -> {
            if (MaterialClipboard.paste(idx)) rebuildSelf();
        }).width(70).build();
        this.body.addEntry(new LegacyFourColumnButtonEntry(resetBtn, copyBtn, pasteBtn, null, body));

        // === Presets (one-click metal setup) ===
        MetalPreset[] presets = MetalPreset.values();
        if (currentPresetIndex >= presets.length) currentPresetIndex = 0;
        ResettableSliderWidget presetSelector = new ResettableSliderWidget(0, 0, 150, 20,
            0, presets.length - 1, currentPresetIndex, 0,
            v -> Text.literal("Preset: " + MetalPreset.values()[v].getDisplayName()),
            v -> { currentPresetIndex = v; });
        ButtonWidget loadPresetBtn = ButtonWidget.builder(Text.literal("Apply Preset"), btn -> {
            MetalPreset p = MetalPreset.values()[currentPresetIndex];
            Options.materialF0R[idx] = p.getF0R(); Options.materialF0G[idx] = p.getF0G(); Options.materialF0B[idx] = p.getF0B();
            Options.materialRoughness[idx] = p.getRoughness(); Options.materialMetallic[idx] = 1000;
            Options.markMaterialDirty();
            com.radiance.client.material.MaterialRegistry.markDirty();
            rebuildSelf();
        }).width(150).build();
        this.body.addEntry(new LegacyTwoColumnOptionEntry(presetSelector, loadPresetBtn, body));

        // === Surface ===
        this.body.addEntry(new CategoryVideoOptionEntry(Text.literal("Surface"), body));

        ResettableSliderWidget metallic = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialMetallic[i], block.getDefaultMetallic(),
            v -> getGenericValueText(Text.literal("Metallic"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> {
                Options.materialMetallic[i] = v;
                if (v >= 500 && Options.materialF0R[i] == 0 && Options.materialF0G[i] == 0 && Options.materialF0B[i] == 0) {
                    Options.materialF0R[i] = 500; Options.materialF0G[i] = 500; Options.materialF0B[i] = 500;
                }
                onSliderChanged(i);
            });
        ResettableSliderWidget roughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialRoughness[i], block.getDefaultRoughness(),
            v -> getGenericValueText(Text.literal("Roughness"), Text.literal(v + "%")),
            v -> { Options.materialRoughness[i] = v; onSliderChanged(i); });
        ResettableSliderWidget ior = new ResettableSliderWidget(0, 0, 100, 20,
            1000, 3000, Math.max(Options.materialIOR[i], 1000), Math.max(block.getDefaultIOR(), 1000),
            v -> getGenericValueText(Text.literal("IOR"), Text.literal(String.format("%.3f", v / 1000.0))),
            v -> {
                Options.materialIOR[i] = v;
                if (Options.materialMetallic[i] < 500) {
                    int f0pm = MaterialBlock.iorToF0Permille(v);
                    Options.materialF0R[i] = f0pm; Options.materialF0G[i] = f0pm; Options.materialF0B[i] = f0pm;
                }
                onSliderChanged(i);
            });
        ResettableSliderWidget transmission = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialTransmission[i], block.getDefaultTransmission(),
            v -> getGenericValueText(Text.literal("Transmission"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialTransmission[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyFourColumnSliderEntry(metallic, roughness, ior, transmission, body));

        ResettableSliderWidget subsurface = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialSubsurface[i], block.getDefaultSubsurface(),
            v -> getGenericValueText(Text.literal("Subsurface"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSubsurface[i] = v; onSliderChanged(i); });
        ResettableSliderWidget anisotropic = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialAnisotropic[i], block.getDefaultAnisotropic(),
            v -> getGenericValueText(Text.literal("Anisotropic"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialAnisotropic[i] = v; onSliderChanged(i); });
        ResettableSliderWidget normalStr = new ResettableSliderWidget(0, 0, 100, 20,
            0, 200, Options.materialNormalStrength[i], 100,
            v -> getGenericValueText(Text.literal("Norm Str"),
                Text.literal(String.format("\u00d7%.2f", v / 100.0))),
            v -> { Options.materialNormalStrength[i] = v; onSliderChanged(i); });
        ResettableSliderWidget normalSmooth = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.materialNormalSmoothing[i], 0,
            v -> getGenericValueText(Text.literal("Norm Smooth"),
                Text.literal(v > 0 ? v + "%" : "Sharp")),
            v -> { Options.materialNormalSmoothing[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyFourColumnSliderEntry(subsurface, anisotropic, normalStr, normalSmooth, body));

        // === Material validation warnings ===
        List<String> warnings = Options.validateMaterial(i);
        for (String warning : warnings) {
            this.body.addEntry(new WarningTextEntry(warning, body));
        }

        // === Coating ===
        this.body.addEntry(new CategoryVideoOptionEntry(Text.literal("Coating"), body));

        ResettableSliderWidget coatWeight = new ResettableSliderWidget(0, 0, 150, 20,
            0, 1000, Options.materialCoatWeight[i], block.getDefaultCoatWeight(),
            v -> getGenericValueText(Text.literal("Clear Coat"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialCoatWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget coatRoughness = new ResettableSliderWidget(0, 0, 150, 20,
            0, 100, Options.materialCoatRoughness[i], block.getDefaultCoatRoughness(),
            v -> getGenericValueText(Text.literal("Coat Roughness"), Text.literal(v + "%")),
            v -> { Options.materialCoatRoughness[i] = v; onSliderChanged(i); });
        ResettableSliderWidget sheenWeight = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialSheenWeight[i], block.getDefaultSheenWeight(),
            v -> getGenericValueText(Text.literal("Sheen"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenWeight[i] = v; onSliderChanged(i); });
        ResettableSliderWidget sheenTint = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialSheenTint[i], block.getDefaultSheenTint(),
            v -> getGenericValueText(Text.literal("Sheen Tint"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialSheenTint[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyFourColumnSliderEntry(coatWeight, coatRoughness, sheenWeight, sheenTint, body));

        // === Advanced (F0 + Texture Mapping) ===
        this.body.addEntry(new CategoryVideoOptionEntry(Text.literal("Advanced"), body));
        ResettableSliderWidget f0r = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0R[i], block.getDefaultF0R(),
            v -> getGenericValueText(Text.literal("F0 R"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0R[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0g = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0G[i], block.getDefaultF0G(),
            v -> getGenericValueText(Text.literal("F0 G"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0G[i] = v; onSliderChanged(i); });
        ResettableSliderWidget f0b = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialF0B[i], block.getDefaultF0B(),
            v -> getGenericValueText(Text.literal("F0 B"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> { Options.materialF0B[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyFourColumnSliderEntry(f0r, f0g, f0b, null, body));

        ResettableSliderWidget gamutBoost = new ResettableSliderWidget(0, 0, 100, 20,
            0, 200, Options.materialGamutBoost[i], 100,
            v -> getGenericValueText(Text.literal("Gamut"), Text.literal(String.format("\u00d7%.2f", v / 100.0))),
            v -> { Options.materialGamutBoost[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyTwoColumnSliderEntry(gamutBoost, null, body));

        // Procedural Noise
        this.body.addEntry(new CategoryVideoOptionEntry(Text.literal("Procedural Noise"), body));
        noiseDropdown = new NoiseTypeDropdownWidget(0, 0, 100, 20, type -> {
            Options.materialNoiseType[i] = type; onSliderChanged(i); });
        noiseDropdown.setNoiseType(Options.materialNoiseType[i]);
        // Noise target: cycle through Roughness / Roughness+ / Normal / Metallic
        String[] targetNames = {"Rough", "Rough+", "Normal", "Metal"};
        int[] targetValues = {1, 8, 2, 4};
        int currentTarget = Options.materialNoiseTarget[i];
        int targetIdx = 0;
        for (int t = 0; t < targetValues.length; t++) {
            if (targetValues[t] == currentTarget) { targetIdx = t; break; }
        }
        final int tIdx = targetIdx;
        ButtonWidget noiseTargetBtn = ButtonWidget.builder(
            Text.literal("Target: " + targetNames[tIdx]),
            btn -> {
                int cur = Options.materialNoiseTarget[i];
                int next = 0;
                for (int t = 0; t < targetValues.length; t++) {
                    if (targetValues[t] == cur) { next = (t + 1) % targetValues.length; break; }
                }
                Options.materialNoiseTarget[i] = targetValues[next];
                onSliderChanged(i);
                MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
            }).width(100).build();
        // Row 1a: Dropdown + Target
        String[] wrapLabels = {"3D", "Surface", "Triplanar", "XZ", "XY", "YZ"};
        net.minecraft.client.gui.widget.ButtonWidget wrapBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(
            Text.literal("Wrap: " + wrapLabels[Options.materialNoiseWrap[i]]),
            btn -> {
                int next = (Options.materialNoiseWrap[i] + 1) % wrapLabels.length;
                Options.materialNoiseWrap[i] = next;
                btn.setMessage(Text.literal("Wrap: " + wrapLabels[next]));
                onSliderChanged(i);
            }).dimensions(0, 0, 100, 20).build();
        String[] maskModeLabels = {"None", "Lum", "Rough", "Metal", "NrmDev"};
        net.minecraft.client.gui.widget.ButtonWidget maskModeBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(
            Text.literal("Mask: " + maskModeLabels[Options.materialNoiseMaskMode[i]]),
            btn -> {
                int next = (Options.materialNoiseMaskMode[i] + 1) % maskModeLabels.length;
                Options.materialNoiseMaskMode[i] = next;
                btn.setMessage(Text.literal("Mask: " + maskModeLabels[next]));
                onSliderChanged(i);
            }).dimensions(0, 0, 100, 20).build();
        this.body.addEntry(new LegacyTwoColumnOptionEntry(noiseDropdown, noiseTargetBtn, body));
        // Row 1b: Wrap + Mask
        this.body.addEntry(new LegacyTwoColumnOptionEntry(wrapBtn, maskModeBtn, body));

        // Row 2: Strength / Scale / Octaves / Seed
        ResettableSliderWidget noiseStrength = new ResettableSliderWidget(0, 0, 100, 20,
            0, 1000, Options.materialNoiseStrength[i], 0,
            v -> getGenericValueText(Text.literal("Str"), Text.literal(String.format("%.0f%%", v / 10.0))),
            v -> { Options.materialNoiseStrength[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseScale = new ResettableSliderWidget(0, 0, 100, 20,
            1, 500, Math.min(Options.materialNoiseScale[i], 500), 30,
            v -> getGenericValueText(Text.literal("Scale"), Text.literal(String.format("%.1fx", v / 10.0))),
            v -> { Options.materialNoiseScale[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseOctaves = new ResettableSliderWidget(0, 0, 100, 20,
            1, 12, Options.materialNoiseOctaves[i], 2,
            v -> getGenericValueText(Text.literal("Oct"), Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseOctaves[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseSeed = new ResettableSliderWidget(0, 0, 100, 20,
            0, 999, Options.materialNoiseSeed[i], 0,
            v -> getGenericValueText(Text.literal("Seed"), Text.literal(String.valueOf(v))),
            v -> { Options.materialNoiseSeed[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyFourColumnSliderEntry(noiseStrength, noiseScale, noiseOctaves, noiseSeed, body));

        // Row 3: Rotation / Aspect / Lacunarity / Contrast
        ResettableSliderWidget noiseRotation = new ResettableSliderWidget(0, 0, 100, 20,
            0, 3600, Options.materialNoiseRotation[i], 0,
            v -> getGenericValueText(Text.literal("Rot"), Text.literal(v / 10 + "\u00B0")),
            v -> { Options.materialNoiseRotation[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseAspect = new ResettableSliderWidget(0, 0, 100, 20,
            10, 500, Options.materialNoiseAspect[i], 100,
            v -> getGenericValueText(Text.literal("Asp"), Text.literal(String.format("%.1fx", v / 100.0))),
            v -> { Options.materialNoiseAspect[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseLacunarity = new ResettableSliderWidget(0, 0, 100, 20,
            10, 40, Options.materialNoiseLacunarity[i], 20,
            v -> getGenericValueText(Text.literal("Lac"), Text.literal(String.format("%.1f", v / 10.0))),
            v -> { Options.materialNoiseLacunarity[i] = v; onSliderChanged(i); });
        ResettableSliderWidget noiseContrast = new ResettableSliderWidget(0, 0, 100, 20,
            10, 200, Options.materialNoiseContrast[i], 100,
            v -> getGenericValueText(Text.literal("Con"), Text.literal(String.format("%.1f", v / 100.0))),
            v -> { Options.materialNoiseContrast[i] = v; onSliderChanged(i); });
        this.body.addEntry(new LegacyFourColumnSliderEntry(noiseRotation, noiseAspect, noiseLacunarity, noiseContrast, body));

        // Row 4 (conditional): Mask Threshold + Invert — only when mask is active
        if (Options.materialNoiseMaskMode[i] > 0) {
            ResettableSliderWidget maskThreshold = new ResettableSliderWidget(0, 0, 100, 20,
                0, 1000, Options.materialNoiseMaskThreshold[i], 500,
                v -> getGenericValueText(Text.literal("Threshold"), Text.literal(String.format("%.0f%%", v / 10.0))),
                v -> { Options.materialNoiseMaskThreshold[i] = v; onSliderChanged(i); });
            net.minecraft.client.gui.widget.ButtonWidget maskInvertBtn = net.minecraft.client.gui.widget.ButtonWidget.builder(
                Text.literal("Invert: " + (Options.materialNoiseMaskInvert[i] ? "ON" : "OFF")),
                btn -> {
                    Options.materialNoiseMaskInvert[i] = !Options.materialNoiseMaskInvert[i];
                    btn.setMessage(Text.literal("Invert: " + (Options.materialNoiseMaskInvert[i] ? "ON" : "OFF")));
                    onSliderChanged(i);
                }).dimensions(0, 0, 100, 20).build();
            this.body.addEntry(new LegacyTwoColumnOptionEntry(maskThreshold, maskInvertBtn, body));
        }

        // === Auto-PBR (This Block) — mask-specific controls ===
        String[] maskNames = {"Albedo", "Normal", "Roughness", "Height"};
        this.body.addEntry(new CategoryVideoOptionEntry(
            Text.literal("Auto-PBR \u2014 " + maskNames[selectedPreviewMask]), body));
        boolean autoPBRActive = Options.autoPBREnabled || Options.materialAutoPBR[i];
        SimpleOption<Boolean> perBlockAutoPBR = SimpleOption.ofBoolean(
            "options.video.materials.autoPBRBlock",
            Options.materialAutoPBR[i],
            value -> {
                Options.materialAutoPBR[i] = value;
                if (value && Options.materialTextureBlend[i] == 0) {
                    Options.materialTextureBlend[i] = 100;
                }
                onSliderChanged(i); LiveNormalReuploader.scheduleReupload();
                rebuildSelf();
            });
        this.body.addAll(new SimpleOption[]{perBlockAutoPBR});

        if (selectedPreviewMask == 2) {
            // === Roughness mask controls ===
            ButtonWidget presetBtn = ButtonWidget.builder(
                Text.literal("Preset: " + MaterialPreset.fromSettings(
                    Options.materialPercentileCenter[i], Options.materialPercentileSpread[i],
                    Options.materialAutoPBRRoughnessMin[i], Options.materialAutoPBRRoughnessMax[i]).getDisplayName()),
                btn -> {
                    MaterialPreset current = MaterialPreset.fromSettings(
                        Options.materialPercentileCenter[i], Options.materialPercentileSpread[i],
                        Options.materialAutoPBRRoughnessMin[i], Options.materialAutoPBRRoughnessMax[i]);
                    MaterialPreset[] matPresets = MaterialPreset.values();
                    int nextIdx = (current.ordinal() + 1) % matPresets.length;
                    MaterialPreset next = matPresets[nextIdx];
                    Options.materialPercentileCenter[i] = next.center;
                    Options.materialPercentileSpread[i] = next.spread;
                    Options.materialAutoPBRRoughnessMin[i] = next.roughMin;
                    Options.materialAutoPBRRoughnessMax[i] = next.roughMax;
                    onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload();
                    rebuildSelf();
                }).width(150).build();
            presetBtn.active = autoPBRActive;
            this.body.addEntry(new LegacyTwoColumnOptionEntry(presetBtn, null, body));

            ResettableSliderWidget roughMin = new ResettableSliderWidget(0, 0, 100, 20,
                0, 100, Options.materialAutoPBRRoughnessMin[i], Options.autoPBRRoughnessMin,
                v -> getGenericValueText(Text.literal("Rough Min"), Text.literal(v + "%")),
                v -> { Options.materialAutoPBRRoughnessMin[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            ResettableSliderWidget roughMax = new ResettableSliderWidget(0, 0, 100, 20,
                0, 100, Options.materialAutoPBRRoughnessMax[i], Options.autoPBRRoughnessMax,
                v -> getGenericValueText(Text.literal("Rough Max"), Text.literal(v + "%")),
                v -> { Options.materialAutoPBRRoughnessMax[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            ResettableSliderWidget perCenter = new ResettableSliderWidget(0, 0, 100, 20,
                0, 100, Options.materialPercentileCenter[i], 50,
                v -> getGenericValueText(Text.literal("Center"), Text.literal(v + "%")),
                v -> { Options.materialPercentileCenter[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            ResettableSliderWidget perSpread = new ResettableSliderWidget(0, 0, 100, 20,
                1, 100, Options.materialPercentileSpread[i], 80,
                v -> getGenericValueText(Text.literal("Spread"), Text.literal(v + "%")),
                v -> { Options.materialPercentileSpread[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            roughMin.active = autoPBRActive;
            roughMax.active = autoPBRActive;
            perCenter.active = autoPBRActive;
            perSpread.active = autoPBRActive;
            this.body.addEntry(new LegacyFourColumnSliderEntry(roughMin, roughMax, perCenter, perSpread, body));

            SimpleOption<Boolean> perInvertRough = SimpleOption.ofBoolean(
                "options.video.materials.autoPBRInvertRoughness",
                (Options.materialAutoPBRFlags[i] & 1) != 0,
                value -> { Options.materialAutoPBRFlags[i] = (Options.materialAutoPBRFlags[i] & ~1) | (value ? 1 : 0); onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            this.body.addAll(new SimpleOption[]{perInvertRough});

        } else if (selectedPreviewMask == 1) {
            // === Normal mask controls ===
            ResettableSliderWidget perNormStr = new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.materialAutoPBRNormalStrength[i], Options.autoPBRNormalStrength,
                v -> getGenericValueText(Text.literal("Normal Strength"), Text.literal(String.format("%.2f", v / 100.0))),
                v -> { Options.materialAutoPBRNormalStrength[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            ResettableSliderWidget perEdgeWt = new ResettableSliderWidget(0, 0, 150, 20,
                0, 100, Options.materialAutoPBREdgeWeight[i], Options.autoPBREdgeWeight,
                v -> getGenericValueText(Text.literal("Edge Height"), Text.literal(v + "%")),
                v -> { Options.materialAutoPBREdgeWeight[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            perNormStr.active = autoPBRActive;
            perEdgeWt.active = autoPBRActive;
            this.body.addEntry(new LegacyTwoColumnSliderEntry(perNormStr, perEdgeWt, body));

            SimpleOption<Boolean> perInvertNormal = SimpleOption.ofBoolean(
                "options.video.materials.autoPBRInvertNormal",
                (Options.materialAutoPBRFlags[i] & 2) != 0,
                value -> { Options.materialAutoPBRFlags[i] = (Options.materialAutoPBRFlags[i] & ~2) | (value ? 2 : 0); onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            this.body.addAll(new SimpleOption[]{perInvertNormal});

        } else if (selectedPreviewMask == 3) {
            // === Height mask controls ===
            ResettableSliderWidget perHeightGamma = new ResettableSliderWidget(0, 0, 150, 20,
                10, 300, Options.materialAutoPBRHeightGamma[i], Options.autoPBRHeightGamma,
                v -> getGenericValueText(Text.literal("Height Gamma"), Text.literal(String.format("%.2f", v / 100.0))),
                v -> { Options.materialAutoPBRHeightGamma[i] = v; onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            perHeightGamma.active = autoPBRActive;
            this.body.addEntry(new LegacyTwoColumnSliderEntry(perHeightGamma, null, body));

            SimpleOption<Boolean> perInvertHeight = SimpleOption.ofBoolean(
                "options.video.materials.autoPBRInvertHeight",
                (Options.materialAutoPBRFlags[i] & 4) != 0,
                value -> { Options.materialAutoPBRFlags[i] = (Options.materialAutoPBRFlags[i] & ~4) | (value ? 4 : 0); onSliderChanged(i); regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
            this.body.addAll(new SimpleOption[]{perInvertHeight});

        }
        // Albedo (mask 0): no Auto-PBR specific controls — channel weights are in Advanced section

        // Auto-PBR Global Parameters (shared across all blocks)
        this.body.addEntry(new CategoryVideoOptionEntry(Text.literal("Auto-PBR (Global)"), body));

        SimpleOption<Boolean> autoPBRToggle = SimpleOption.ofBoolean(
            "options.video.materials.autoPBR",
            Options.autoPBREnabled,
            value -> {
                Options.autoPBREnabled = value;
                LiveNormalReuploader.scheduleReupload();
                rebuildSelf();
            });
        this.body.addAll(new SimpleOption[]{autoPBRToggle});

        boolean globalPBROn = Options.autoPBREnabled;
        ResettableSliderWidget roughGamma = new ResettableSliderWidget(0, 0, 100, 20,
            1, 2000, Options.autoPBRRoughnessGamma, 50,
            v -> getGenericValueText(Text.literal("Gamma"), Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.autoPBRRoughnessGamma = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget normStr = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.autoPBRNormalStrength, 25,
            v -> getGenericValueText(Text.literal("Norm Str"), Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.autoPBRNormalStrength = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget globalRoughMin = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.autoPBRRoughnessMin, 30,
            v -> getGenericValueText(Text.literal("Rough Min"), Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMin = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget globalRoughMax = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.autoPBRRoughnessMax, 95,
            v -> getGenericValueText(Text.literal("Rough Max"), Text.literal(v + "%")),
            v -> { Options.autoPBRRoughnessMax = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        roughGamma.active = globalPBROn;
        normStr.active = globalPBROn;
        globalRoughMin.active = globalPBROn;
        globalRoughMax.active = globalPBROn;
        this.body.addEntry(new LegacyFourColumnSliderEntry(roughGamma, normStr, globalRoughMin, globalRoughMax, body));

        ResettableSliderWidget varianceWt = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.autoPBRVarianceWeight, 30,
            v -> getGenericValueText(Text.literal("Variance"), Text.literal(v + "%")),
            v -> { Options.autoPBRVarianceWeight = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget edgeWt = new ResettableSliderWidget(0, 0, 100, 20,
            0, 100, Options.autoPBREdgeWeight, 15,
            v -> getGenericValueText(Text.literal("Edge Wt"), Text.literal(v + "%")),
            v -> { Options.autoPBREdgeWeight = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        ResettableSliderWidget heightGamma = new ResettableSliderWidget(0, 0, 100, 20,
            10, 300, Options.autoPBRHeightGamma, 100,
            v -> getGenericValueText(Text.literal("Ht Contrast"), Text.literal(String.format("%.2f", v / 100.0))),
            v -> { Options.autoPBRHeightGamma = v; regeneratePreview(); LiveNormalReuploader.scheduleReupload(); });
        varianceWt.active = globalPBROn;
        edgeWt.active = globalPBROn;
        heightGamma.active = globalPBROn;
        this.body.addEntry(new LegacyFourColumnSliderEntry(varianceWt, edgeWt, heightGamma, null, body));

        // === Parent/Child ===
        if (!block.isParent()) {
            MaterialBlock parentBlock = block.getParentMaterial();
            String parentName = Text.translatable("options.video.materials." + parentBlock.getId()).getString();
            ButtonWidget variantLabel = ButtonWidget.builder(Text.literal("Variant of: " + parentName), btn -> {
                currentBlockIndex = parentBlock.ordinal();
                rebuildSelf();
            }).width(150).build();
            ButtonWidget resetParentBtn = ButtonWidget.builder(Text.literal("Reset to Parent"), btn -> {
                int pi = parentBlock.ordinal();
                MaterialData.fromOptions(pi).applyToOptions(i);
                Options.materialChildOverride[i] = false;
                rebuildSelf();
            }).width(150).build();
            this.body.addEntry(new LegacyTwoColumnOptionEntry(variantLabel, resetParentBtn, body));
        }
    }

}
