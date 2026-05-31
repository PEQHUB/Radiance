package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.gui.unified.ContentPanelWidget;
import com.radiance.client.gui.unified.SettingsSection;
import com.radiance.client.material.MaterialRegistry;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.world.BlockModelBridge;
import com.radiance.client.texture.AutoPBRGenerator;
import com.radiance.client.texture.LiveNormalReuploader;
import com.radiance.client.texture.NoisePreviewGenerator;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.MaterialClipboard;
import com.radiance.client.util.MaterialData;
import com.radiance.client.util.MaterialFileManager;
import com.radiance.client.util.MaterialPreset;
import com.radiance.client.util.MaterialsPack;
import com.radiance.client.util.MetalPreset;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class MaterialWorkbenchScreen extends Screen {
    private static int selectedOrdinal = 0;
    private static int selectedCategoryIndex = 0;
    private static String searchQuery = "";
    private static AutoPBRMapTab selectedTab = AutoPBRMapTab.ROUGHNESS;
    private static boolean snapshotTaken = false;
    private static MaterialSnapshot snapshot;

    private static final Identifier PREVIEW_ALBEDO_ID = Identifier.of("radiance", "material_workbench/albedo");
    private static final Identifier PREVIEW_ROUGHNESS_ID = Identifier.of("radiance", "material_workbench/roughness");
    private static final Identifier PREVIEW_NORMAL_ID = Identifier.of("radiance", "material_workbench/normal");
    private static final Identifier PREVIEW_HEIGHT_ID = Identifier.of("radiance", "material_workbench/height");
    private static final Identifier PREVIEW_AO_ID = Identifier.of("radiance", "material_workbench/ao");
    private static final Identifier PREVIEW_NOISE_ID = Identifier.of("radiance", "material_workbench/noise");

    private static NativeImageBackedTexture previewAlbedoTex;
    private static NativeImageBackedTexture previewRoughTex;
    private static NativeImageBackedTexture previewNormalTex;
    private static NativeImageBackedTexture previewHeightTex;
    private static NativeImageBackedTexture previewAoTex;
    private static NativeImageBackedTexture previewNoiseTex;
    private static boolean previewsRegistered = false;

    private final Screen parentScreen;
    private TextFieldWidget searchField;
    private SelectionDropdownWidget categoryDropdown;
    private ContentPanelWidget inspector;
    private NativeImage sourceAlbedo;
    private final List<Integer> visibleOrdinals = new ArrayList<>();
    private int browserScroll = 0;

    private int headerH;
    private int footerH;
    private int leftX, leftY, leftW, leftH;
    private int centerX, centerY, centerW, centerH;
    private int rightX, rightY, rightW, rightH;

    public MaterialWorkbenchScreen(Screen parentScreen) {
        super(Text.literal("Material Workbench"));
        this.parentScreen = parentScreen;
        if (!snapshotTaken) {
            snapshot = MaterialSnapshot.capture();
            snapshotTaken = true;
        }
    }

    public static void setCurrentOrdinal(int ordinal) {
        selectedOrdinal = ordinal;
    }

    public static void setCurrentBlockIndex(int index) {
        List<Integer> all = MaterialBlock.getUniqueOrdinals();
        if (index >= 0 && index < all.size()) {
            selectedOrdinal = all.get(index);
        }
    }

    @Override
    protected void init() {
        super.init();
        SelectionDropdownWidget.clearInstances();
        MaterialDropdownWidget.clearInstances();
        MaterialSphereRenderer.validateDiskCache();

        headerH = 34;
        footerH = 28;
        int margin = 8;
        int gap = 8;
        leftW = Math.max(180, Math.min(250, (int) (width * 0.22f)));
        rightW = Math.max(320, Math.min(430, (int) (width * 0.34f)));
        leftX = margin;
        leftY = headerH + margin;
        leftH = height - headerH - footerH - margin * 2;
        rightX = width - rightW - margin;
        rightY = leftY;
        rightH = leftH;
        centerX = leftX + leftW + gap;
        centerY = leftY;
        centerW = Math.max(220, rightX - centerX - gap);
        centerH = leftH;

        rebuildVisibleOrdinals();
        if (!MaterialBlock.getUniqueOrdinals().contains(selectedOrdinal)) {
            selectedOrdinal = MaterialBlock.getUniqueOrdinals().isEmpty() ? 0 : MaterialBlock.getUniqueOrdinals().get(0);
        }

        searchField = new TextFieldWidget(textRenderer, leftX + 8, leftY + 8, leftW - 16, 18, Text.literal("Search"));
        searchField.setText(searchQuery);
        searchField.setMaxLength(64);
        searchField.setChangedListener(value -> {
            searchQuery = value;
            browserScroll = 0;
            rebuildVisibleOrdinals();
        });
        addDrawableChild(searchField);

        String[] categoryLabels = categoryLabels();
        categoryDropdown = new SelectionDropdownWidget(leftX + 8, leftY + 30, leftW - 16, 18,
            "Category", categoryLabels, Math.min(selectedCategoryIndex, categoryLabels.length - 1), idx -> {
                selectedCategoryIndex = idx;
                browserScroll = 0;
                rebuildVisibleOrdinals();
            });
        addDrawableChild(categoryDropdown);

        int buttonY = headerH + 6;
        addDrawableChild(new WorkbenchButton(width - 414, buttonY, 58, 20, "Export", this::exportPack));
        addDrawableChild(new WorkbenchButton(width - 350, buttonY, 56, 20, "Save", this::applyChanges));
        addDrawableChild(new WorkbenchButton(width - 288, buttonY, 66, 20, "Cancel", this::cancelChanges));
        addDrawableChild(new WorkbenchButton(width - 216, buttonY, 82, 20, "Reset Mat", this::resetMaterial));
        addDrawableChild(new WorkbenchButton(width - 128, buttonY, 50, 20, "Copy", this::copyMaterial));
        addDrawableChild(new WorkbenchButton(width - 72, buttonY, 64, 20, "Paste", this::pasteMaterial));

        int tabY = centerY + Math.max(220, Math.min(centerW - 24, centerH / 2)) + 58;
        int tabW = Math.max(58, Math.min(86, (centerW - 24) / AutoPBRMapTab.values().length));
        int tabX = centerX + 12;
        for (AutoPBRMapTab tab : AutoPBRMapTab.values()) {
            addDrawableChild(new SegmentButton(tabX, tabY, tabW, 20, tab.label, () -> {
                selectedTab = tab;
                rebuildSelf();
            }, () -> selectedTab == tab));
            tabX += tabW + 4;
        }

        inspector = new ContentPanelWidget(rightX, rightY, rightW, rightH);
        addDrawableChild(inspector);

        loadSourceAlbedo(MaterialBlock.getIdForOrdinal(selectedOrdinal));
        regeneratePreview();
        populateInspector();
    }

    private void populateInspector() {
        inspector.clearContent();
        int ord = selectedOrdinal;
        boolean thin = MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord);

        SettingsSection identity = inspector.addSection(Text.literal("Material"));
        identity.addInfo("Name", MaterialBlock.getDisplayNameForOrdinal(ord));
        identity.addInfo("ID", MaterialBlock.getIdForOrdinal(ord));
        identity.addInfo("Class", MaterialBlock.getMaterialClassForOrdinal(ord).name());
        if (ord < MaterialBlock.COUNT) {
            MaterialBlock block = MaterialBlock.values()[ord];
            identity.addInfo("Category", block.getCategory().getDisplayName());
            identity.addInfo("Inheritance", block.isParent() ? "Parent" : "Child of " + block.getParentMaterial().getId());
        } else {
            identity.addInfo("Category", "Dynamic");
        }

        SettingsSection surface = inspector.addSection(Text.literal("Surface Response"));
        addSlider(surface, "Roughness", 0, 100, Options.materialRoughness[ord], defaultValue(ord, MaterialBlock::getDefaultRoughness),
            v -> Text.literal("Roughness: " + v + "%"), v -> Options.materialRoughness[ord] = v, MaterialControlState.ENABLED, null);
        addSlider(surface, thin ? "Plant Fill" : "Subsurface", 0, 1000, Options.materialSubsurface[ord], defaultValue(ord, MaterialBlock::getDefaultSubsurface),
            v -> getGenericValueText(Text.literal(thin ? "Plant Fill" : "Subsurface"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> Options.materialSubsurface[ord] = v, MaterialControlState.ENABLED, null);
        addSlider(surface, thin ? "Plant Bright" : "Gamut", 0, 200, Options.materialGamutBoost[ord], 100,
            v -> getGenericValueText(Text.literal(thin ? "Plant Bright" : "Gamut"), Text.literal(String.format("x%.2f", v / 100.0))),
            v -> Options.materialGamutBoost[ord] = v, MaterialControlState.ENABLED, null);
        if (thin) {
            addSlider(surface, "Plant Shadow", 0, 200, Options.materialNormalStrength[ord], 100,
                v -> getGenericValueText(Text.literal("Plant Shadow"), Text.literal(String.format("%.0f%%", v / 2.0))),
                v -> Options.materialNormalStrength[ord] = v, MaterialControlState.ENABLED, null);
        }

        MaterialControlState thinLock = thin
            ? MaterialControlState.disabled("Thin plant cards use constrained cutout lighting; this channel is shader-locked off.")
            : MaterialControlState.ENABLED;
        addSlider(surface, "Metallic", 0, 1000, Options.materialMetallic[ord], defaultValue(ord, MaterialBlock::getDefaultMetallic),
            v -> getGenericValueText(Text.literal("Metallic"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> Options.materialMetallic[ord] = v, thinLock, thinLock.disabledReason());
        addSlider(surface, "Transmission", 0, 1000, Options.materialTransmission[ord], defaultValue(ord, MaterialBlock::getDefaultTransmission),
            v -> getGenericValueText(Text.literal("Transmission"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> Options.materialTransmission[ord] = v, thinLock, thinLock.disabledReason());
        addSlider(surface, "IOR", 1000, 3000, Options.materialIOR[ord], defaultValue(ord, MaterialBlock::getDefaultIOR),
            v -> getGenericValueText(Text.literal("IOR"), Text.literal(String.format("%.3f", v / 1000.0))),
            v -> {
                Options.materialIOR[ord] = v;
                int f0 = MaterialBlock.iorToF0Permille(v);
                Options.materialF0R[ord] = f0;
                Options.materialF0G[ord] = f0;
                Options.materialF0B[ord] = f0;
            }, thinLock, thinLock.disabledReason());

        SettingsSection layers = inspector.addSection(Text.literal("Lobes & Coats"));
        addSlider(layers, "Anisotropic", 0, 1000, Options.materialAnisotropic[ord], 0,
            v -> getGenericValueText(Text.literal("Anisotropic"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> Options.materialAnisotropic[ord] = v, thinLock, thinLock.disabledReason());
        addSlider(layers, "Coat Weight", 0, 1000, Options.materialCoatWeight[ord], defaultValue(ord, MaterialBlock::getDefaultCoatWeight),
            v -> getGenericValueText(Text.literal("Coat"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> Options.materialCoatWeight[ord] = v, thinLock, thinLock.disabledReason());
        addSlider(layers, "Coat Roughness", 0, 100, Options.materialCoatRoughness[ord], defaultValue(ord, MaterialBlock::getDefaultCoatRoughness),
            v -> Text.literal("Coat Rough: " + v + "%"), v -> Options.materialCoatRoughness[ord] = v, thinLock, thinLock.disabledReason());
        addSlider(layers, "Sheen Weight", 0, 1000, Options.materialSheenWeight[ord], defaultValue(ord, MaterialBlock::getDefaultSheenWeight),
            v -> getGenericValueText(Text.literal("Sheen"), Text.literal(String.format("%.1f%%", v / 10.0))),
            v -> Options.materialSheenWeight[ord] = v, thinLock, thinLock.disabledReason());

        SettingsSection displacement = inspector.addSection(Text.literal("Displacement"));
        MaterialControlState displacementState = thin ? thinLock
            : Options.displacementEnabled ? MaterialControlState.ENABLED
            : MaterialControlState.disabled("Global displacement is disabled in Surfaces > Materials.");
        addDropdown(displacement, "Mode", new String[]{"Inherit", "Off", "Custom"}, Options.materialPomMode[ord],
            v -> Options.materialPomMode[ord] = v, displacementState);
        addSlider(displacement, "Depth", 0, 200, Options.materialPomDepth[ord], 0,
            v -> getGenericValueText(Text.literal("Depth"), v == 0 ? Text.literal("Off") : Text.literal(String.format("%.2f blocks", v / 100.0))),
            v -> Options.materialPomDepth[ord] = v, displacementState, displacementState.disabledReason());
        addSlider(displacement, "Filter Radius", 0, 15, Options.materialFilterRadius[ord], 0,
            v -> Text.literal("Filter Radius: " + v), v -> Options.materialFilterRadius[ord] = v, displacementState, displacementState.disabledReason());
        addSlider(displacement, "Mip Bias", 0, 15, Options.materialMipBias[ord], 0,
            v -> Text.literal("Mip Bias: " + v), v -> Options.materialMipBias[ord] = v, displacementState, displacementState.disabledReason());

        SettingsSection auto = inspector.addSection(Text.literal("Auto-PBR: " + selectedTab.label));
        auto.setDescription(autoPBRDescription(ord));
        MaterialControlState autoToggleState = thin
            ? MaterialControlState.disabled("Thin plant cards bypass Auto-PBR height and normal generation.")
            : MaterialControlState.ENABLED;
        addButton(auto, "This Material: " + (Options.materialAutoPBR[ord] ? "On" : "Off"), () -> {
            Options.materialAutoPBR[ord] = !Options.materialAutoPBR[ord];
            onMaterialChanged(ord);
            regeneratePreview();
            LiveNormalReuploader.scheduleGeneratedReupload(ord, true, true);
            rebuildSelf();
        }, autoToggleState, autoToggleState.disabledReason());
        addButton(auto, "Global: " + (Options.autoPBREnabled ? "On" : "Off"), () -> {
            Options.autoPBREnabled = !Options.autoPBREnabled;
            onMaterialChanged(ord);
            regeneratePreview();
            LiveNormalReuploader.scheduleGeneratedReupload(true, true);
            rebuildSelf();
        }, autoToggleState, autoToggleState.disabledReason());

        switch (selectedTab) {
            case ROUGHNESS -> populateRoughness(auto, ord);
            case NORMAL -> populateNormal(auto, ord);
            case HEIGHT -> populateHeight(auto, ord);
            case AO -> populateAO(auto, ord);
            case SPECULAR -> populateSpecular(auto, ord);
        }
    }

    private void populateRoughness(SettingsSection section, int ord) {
        MaterialControlState state = specularGenerationState(ord);
        MaterialPreset current = MaterialPreset.fromSettings(
            Options.materialPercentileCenter[ord], Options.materialPercentileSpread[ord],
            Options.materialAutoPBRRoughnessMin[ord], Options.materialAutoPBRRoughnessMax[ord]);
        addButton(section, "Preset: " + current.getDisplayName(), () -> {
            MaterialPreset[] presets = MaterialPreset.values();
            MaterialPreset next = presets[(current.ordinal() + 1) % presets.length];
            Options.materialPercentileCenter[ord] = next.center;
            Options.materialPercentileSpread[ord] = next.spread;
            Options.materialAutoPBRRoughnessMin[ord] = next.roughMin;
            Options.materialAutoPBRRoughnessMax[ord] = next.roughMax;
            onAutoPreviewChanged(ord);
            rebuildSelf();
        }, state, state.disabledReason());
        addSlider(section, "Rough Min", 0, 100, Options.materialAutoPBRRoughnessMin[ord], 30,
            v -> Text.literal("Rough Min: " + v + "%"), v -> Options.materialAutoPBRRoughnessMin[ord] = v, state, state.disabledReason(), () -> reuploadSpecular(ord));
        addSlider(section, "Rough Max", 0, 100, Options.materialAutoPBRRoughnessMax[ord], 95,
            v -> Text.literal("Rough Max: " + v + "%"), v -> Options.materialAutoPBRRoughnessMax[ord] = v, state, state.disabledReason(), () -> reuploadSpecular(ord));
        addSlider(section, "Center", 0, 100, Options.materialPercentileCenter[ord], 50,
            v -> Text.literal("Center: " + v + "%"), v -> Options.materialPercentileCenter[ord] = v, state, state.disabledReason(), () -> reuploadSpecular(ord));
        addSlider(section, "Spread", 1, 100, Options.materialPercentileSpread[ord], 80,
            v -> Text.literal("Spread: " + v + "%"), v -> Options.materialPercentileSpread[ord] = v, state, state.disabledReason(), () -> reuploadSpecular(ord));
        addButton(section, "Invert Roughness: " + (((Options.materialAutoPBRFlags[ord] & 1) != 0) ? "On" : "Off"), () -> {
            Options.materialAutoPBRFlags[ord] ^= 1;
            onAutoPreviewChanged(ord);
            reuploadSpecular(ord);
            rebuildSelf();
        }, state, state.disabledReason());
    }

    private void populateNormal(SettingsSection section, int ord) {
        MaterialControlState inputState = MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord)
            ? MaterialControlState.disabled("Thin plant cards force flat generated normals.")
            : MaterialControlState.ENABLED;
        addDropdown(section, "Input", new String[]{"Auto", "Custom", "Flat"}, Options.materialNormalInputType[ord],
            v -> {
                Options.materialNormalInputType[ord] = v;
                onAutoPreviewChanged(ord);
                reuploadNormal(ord);
                rebuildSelf();
            }, inputState);
        MaterialControlState state = normalGenerationState(ord);
        addSlider(section, "Strength", 0, 200, Options.materialNormalStrength[ord], 100,
            v -> getGenericValueText(Text.literal("Strength"), Text.literal(String.format("x%.2f", v / 100.0))),
            v -> Options.materialNormalStrength[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addButton(section, "Invert XY: " + (((Options.materialAutoPBRFlags[ord] & 2) != 0) ? "On" : "Off"), () -> {
            Options.materialAutoPBRFlags[ord] ^= 2;
            onAutoPreviewChanged(ord);
            reuploadNormal(ord);
            rebuildSelf();
        }, state, state.disabledReason());
        addSlider(section, "Normal Clamp", 0, 100, Options.materialNormalClamp[ord], 100,
            v -> Text.literal("Clamp: " + v + "%"), v -> Options.materialNormalClamp[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addSlider(section, "Geometric Blend", 0, 100, Options.materialGeometricBlend[ord], 0,
            v -> Text.literal("Geom Blend: " + v + "%"), v -> Options.materialGeometricBlend[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addSlider(section, "Distance Fade", 0, 255, Options.materialNormalDistanceFade[ord], 0,
            v -> Text.literal(v == 0 ? "Distance Fade: Off" : "Distance Fade: " + v + " blocks"),
            v -> Options.materialNormalDistanceFade[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
    }

    private void populateHeight(SettingsSection section, int ord) {
        MaterialControlState state = normalGenerationState(ord);
        addDropdown(section, "Source", new String[]{"Lum", "Red", "Green", "Blue", "Alpha", "MaxRGB", "MinRGB"},
            Math.min(Options.materialHeightSource[ord], 6), v -> Options.materialHeightSource[ord] = v, state);
        addDropdown(section, "Filter", new String[]{"Forward", "Central", "Sobel", "Bilinear", "Bicubic"},
            Math.min(Options.materialHeightFilter[ord], 4), v -> Options.materialHeightFilter[ord] = v, state);
        addSlider(section, "Remap Min", 0, 100, Options.materialHeightRemapMin[ord], 0,
            v -> Text.literal("Remap Min: " + v + "%"), v -> Options.materialHeightRemapMin[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addSlider(section, "Remap Max", 0, 100, Options.materialHeightRemapMax[ord], 100,
            v -> Text.literal("Remap Max: " + v + "%"), v -> Options.materialHeightRemapMax[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addSlider(section, "Contrast", 0, 30, Options.materialHeightContrast[ord], 10,
            v -> Text.literal(String.format("Contrast: %.1f", v / 10.0)), v -> Options.materialHeightContrast[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addSlider(section, "Gamma", 10, 300, Options.materialAutoPBRHeightGamma[ord], 100,
            v -> Text.literal(String.format("Gamma: %.2f", v / 100.0)), v -> Options.materialAutoPBRHeightGamma[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addSlider(section, "Offset", 0, 200, Options.materialHeightOffset[ord], 100,
            v -> Text.literal(String.format("Offset: %.2f", (v - 100) / 100.0)), v -> Options.materialHeightOffset[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        addButton(section, "Invert Height: " + (((Options.materialAutoPBRFlags[ord] & 4) != 0) ? "On" : "Off"), () -> {
            Options.materialAutoPBRFlags[ord] ^= 4;
            onAutoPreviewChanged(ord);
            reuploadNormal(ord);
            rebuildSelf();
        }, state, state.disabledReason());
    }

    private void populateAO(SettingsSection section, int ord) {
        MaterialControlState state = normalGenerationState(ord);
        addSlider(section, "AO Strength", 0, 100, Options.materialPomAOStrength[ord], 0,
            v -> Text.literal("AO Strength: " + v + "%"), v -> Options.materialPomAOStrength[ord] = v, state, state.disabledReason(), () -> reuploadNormal(ord));
        section.addInfo("Preview", "AO is stored in the generated normal map alpha channel.");
    }

    private void populateSpecular(SettingsSection section, int ord) {
        MaterialControlState inputState = MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord)
            ? MaterialControlState.disabled("Thin plant cards force generated specular controls off.")
            : MaterialControlState.ENABLED;
        addDropdown(section, "Input", new String[]{"Auto", "Custom", "Flat"}, Options.materialSpecularInputType[ord],
            v -> {
                Options.materialSpecularInputType[ord] = v;
                onAutoPreviewChanged(ord);
                reuploadSpecular(ord);
                rebuildSelf();
            }, inputState);
        section.addInfo("Source", specularSourceStatus(ord));
        section.addInfo("Output", Options.materialTransmission[ord] > 0 ? "Material roughness/transmission" : "LabPBR specular smoothness");
    }

    private void addSlider(SettingsSection section, String label, int min, int max, int current, int def,
            IntFunction<Text> formatter, IntConsumer setter, MaterialControlState state, String tooltip) {
        addSlider(section, label, min, max, current, def, formatter, setter, state, tooltip, null);
    }

    private void addSlider(SettingsSection section, String label, int min, int max, int current, int def,
            IntFunction<Text> formatter, IntConsumer setter, MaterialControlState state, String tooltip, Runnable onRelease) {
        ResettableSliderWidget slider = MaterialControlFactory.slider(min, max, current, def, formatter, value -> {
            setter.accept(value);
            onAutoPreviewChanged(selectedOrdinal);
        }, state, onRelease);
        section.addSlider(slider);
        if (tooltip != null) section.tooltip(tooltip);
    }

    private void addButton(SettingsSection section, String label, Runnable action, MaterialControlState state, String tooltip) {
        section.addButton(MaterialControlFactory.button(label, action, state));
        if (tooltip != null) section.tooltip(tooltip);
    }

    private void addDropdown(SettingsSection section, String label, String[] values, int current,
            IntConsumer setter, MaterialControlState state) {
        SelectionDropdownWidget dropdown = new SelectionDropdownWidget(0, 0, 150, 20,
            label, values, current, value -> {
                setter.accept(value);
                onAutoPreviewChanged(selectedOrdinal);
                rebuildSelf();
            });
        dropdown.active = state == null || state.enabled();
        section.addToggle(dropdown);
        if (state != null && state.disabledReason() != null) section.tooltip(state.disabledReason());
    }

    private void onAutoPreviewChanged(int ord) {
        onMaterialChanged(ord);
        regeneratePreview();
    }

    private static void onMaterialChanged(int ord) {
        if (MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord)) {
            Options.applyThinPlantMaterialLocks();
        }
        if (ord >= 0 && ord < MaterialBlock.COUNT) {
            MaterialBlock block = MaterialBlock.values()[ord];
            if (block.isParent() && !block.getChildren().isEmpty()) {
                Options.propagateParentMaterial(ord);
            } else if (!block.isParent()) {
                Options.materialChildOverride[ord] = true;
            }
        }
        Options.markMaterialDirty();
        MaterialRegistry.markDirty();
    }

    private MaterialControlState specularGenerationState(int ord) {
        if (MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord)) {
            return MaterialControlState.disabled("Thin plant cards bypass generated specular maps.");
        }
        if (!Options.autoPBREnabled) return MaterialControlState.disabled("Global Auto-PBR is off.");
        if (!Options.materialAutoPBR[ord]) return MaterialControlState.disabled("Auto-PBR is off for this material.");
        if (Options.materialSpecularInputType[ord] != 0) return MaterialControlState.disabled("Specular input is not set to Auto.");
        if (Options.materialTransmission[ord] > 0) return MaterialControlState.disabled("Transmissive materials use material roughness/transmission instead.");
        String source = specularExternalSource(ord);
        if (source != null) return MaterialControlState.disabled(source);
        return MaterialControlState.ENABLED;
    }

    private MaterialControlState normalGenerationState(int ord) {
        if (MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord)) {
            return MaterialControlState.disabled("Thin plant cards force flat generated normals.");
        }
        if (!Options.autoPBREnabled) return MaterialControlState.disabled("Global Auto-PBR is off.");
        if (!Options.materialAutoPBR[ord]) return MaterialControlState.disabled("Auto-PBR is off for this material.");
        if (Options.materialNormalInputType[ord] != 0) return MaterialControlState.disabled("Normal input is not set to Auto.");
        String source = normalExternalSource(ord);
        if (source != null) return MaterialControlState.disabled(source);
        return MaterialControlState.ENABLED;
    }

    private String autoPBRDescription(int ord) {
        if (MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord)) {
            return "Thin plants expose only their safe material controls.";
        }
        if (!Options.autoPBREnabled) return "Global Auto-PBR is disabled.";
        if (!Options.materialAutoPBR[ord]) return "Auto-PBR is disabled for this material.";
        return "Preview updates live. Texture arrays reupload only when you release a control.";
    }

    private void reuploadSpecular(int ord) {
        LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false);
    }

    private void reuploadNormal(int ord) {
        LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
    }

    private void rebuildVisibleOrdinals() {
        visibleOrdinals.clear();
        String q = searchQuery == null ? "" : searchQuery.toLowerCase();
        for (int ord : MaterialBlock.getUniqueOrdinals()) {
            if (selectedCategoryIndex > 0) {
                if (ord >= MaterialBlock.COUNT) continue;
                MaterialBlock.MaterialCategory cat = MaterialBlock.MaterialCategory.values()[selectedCategoryIndex - 1];
                if (MaterialBlock.values()[ord].getCategory() != cat) continue;
            }
            String name = MaterialBlock.getDisplayNameForOrdinal(ord).toLowerCase();
            String id = MaterialBlock.getIdForOrdinal(ord).toLowerCase();
            if (!q.isEmpty() && !name.contains(q) && !id.contains(q)) continue;
            visibleOrdinals.add(ord);
        }
    }

    private String[] categoryLabels() {
        MaterialBlock.MaterialCategory[] values = MaterialBlock.MaterialCategory.values();
        String[] labels = new String[values.length + 1];
        labels[0] = "All";
        for (int i = 0; i < values.length; i++) {
            labels[i + 1] = values[i].getDisplayName();
        }
        return labels;
    }

    private int defaultValue(int ord, java.util.function.ToIntFunction<MaterialBlock> getter) {
        if (ord >= 0 && ord < MaterialBlock.COUNT) return getter.applyAsInt(MaterialBlock.values()[ord]);
        return 0;
    }

    private String specularSourceStatus(int ord) {
        String external = specularExternalSource(ord);
        if (external != null) return external;
        return "Generated / material-controlled";
    }

    private String specularExternalSource(int ord) {
        Set<Integer> sprites = BlockModelBridge.materialOrdinal2SpriteIds.get(ord);
        if (sprites == null) return null;
        for (int sprite : sprites) {
            if (sprite < 0 || sprite >= TextureTracker.spriteSpecularSource.length) continue;
            byte source = TextureTracker.spriteSpecularSource[sprite];
            if (source == TextureTracker.SOURCE_PACK_AUTHORED) return "A pack-authored specular map is active.";
            if (source == TextureTracker.SOURCE_USER_CUSTOM) return "A custom specular map is active.";
        }
        return null;
    }

    private String normalExternalSource(int ord) {
        Set<Integer> sprites = BlockModelBridge.materialOrdinal2SpriteIds.get(ord);
        if (sprites == null) return null;
        for (int sprite : sprites) {
            if (sprite < 0 || sprite >= TextureTracker.spriteNormalSource.length) continue;
            byte source = TextureTracker.spriteNormalSource[sprite];
            if (source == TextureTracker.SOURCE_PACK_AUTHORED) return "A pack-authored normal map is active.";
            if (source == TextureTracker.SOURCE_USER_CUSTOM) return "A custom normal map is active.";
        }
        return null;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (RadianceTheme.peekActive) return;
        MaterialSphereRenderer.drainCompleted();
        renderShell(context, mouseX, mouseY);
        renderBrowser(context, mouseX, mouseY);
        renderWorkspace(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        SelectionDropdownWidget.renderAllOverlays(context, mouseX, mouseY);
    }

    private void renderShell(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, width, headerH, RadianceTheme.unifiedHeaderBg);
        RadianceTheme.drawOutlinedText(ctx, textRenderer, Text.literal("Material Workbench"),
            10, 11, RadianceTheme.textAccent);
        ctx.fill(leftX, leftY, leftX + leftW, leftY + leftH, RadianceTheme.unifiedTreeBg);
        ctx.fill(centerX, centerY, centerX + centerW, centerY + centerH, RadianceTheme.unifiedContentBg);
        ctx.drawBorder(leftX, leftY, leftW, leftH, RadianceTheme.borderDefault);
        ctx.drawBorder(centerX, centerY, centerW, centerH, RadianceTheme.borderDefault);
    }

    private void renderBrowser(DrawContext ctx, int mouseX, int mouseY) {
        int listY = leftY + 56;
        int rowH = 18;
        int maxRows = Math.max(1, (leftH - 64) / rowH);
        browserScroll = Math.max(0, Math.min(browserScroll, Math.max(0, visibleOrdinals.size() - maxRows)));
        for (int row = 0; row < maxRows; row++) {
            int idx = browserScroll + row;
            if (idx >= visibleOrdinals.size()) break;
            int ord = visibleOrdinals.get(idx);
            int y = listY + row * rowH;
            boolean selected = ord == selectedOrdinal;
            boolean hovered = mouseX >= leftX + 6 && mouseX < leftX + leftW - 6 && mouseY >= y && mouseY < y + rowH;
            int bg = selected ? RadianceTheme.withAlpha(0x2AB5A0, 0.38f)
                : hovered ? RadianceTheme.scaleAlpha(RadianceTheme.widgetBgHover, 0.7f) : 0;
            if (bg != 0) ctx.fill(leftX + 6, y, leftX + leftW - 6, y + rowH, bg);
            if (selected) ctx.fill(leftX + 6, y, leftX + 9, y + rowH, RadianceTheme.SELECTED_BAR);
            Text label = RadianceTheme.trimText(textRenderer, Text.literal(MaterialBlock.getDisplayNameForOrdinal(ord)), leftW - 26);
            RadianceTheme.drawOutlinedText(ctx, textRenderer, label, leftX + 14, y + 5,
                selected ? RadianceTheme.textPrimary : RadianceTheme.textSecondary);
        }
        String count = visibleOrdinals.size() + " materials";
        RadianceTheme.drawOutlinedText(ctx, textRenderer, Text.literal(count),
            leftX + 10, leftY + leftH - 14, RadianceTheme.textSecondary);
    }

    private void renderWorkspace(DrawContext ctx, int mouseX, int mouseY) {
        int pad = 12;
        int nameY = centerY + pad;
        RadianceTheme.drawOutlinedText(ctx, textRenderer,
            Text.literal(MaterialBlock.getDisplayNameForOrdinal(selectedOrdinal)),
            centerX + pad, nameY, RadianceTheme.textPrimary);
        RadianceTheme.drawOutlinedText(ctx, textRenderer,
            Text.literal(MaterialBlock.getIdForOrdinal(selectedOrdinal)),
            centerX + pad, nameY + 12, RadianceTheme.textSecondary);

        MaterialData data = materialDataFor(selectedOrdinal);
        int sphereSize = Math.max(96, Math.min(centerW - pad * 2, Math.min(190, centerH / 3)));
        int sphereX = centerX + (centerW - sphereSize) / 2;
        int sphereY = centerY + 44;
        MaterialSphereRenderer.drawSphere(ctx, data, sphereX, sphereY, MaterialSphereRenderer.SIDEBAR_RENDER_SIZE, sphereSize);

        int previewSize = Math.max(96, Math.min(centerW - pad * 2, Math.min(220, centerH - sphereY - sphereSize - 96)));
        int previewX = centerX + (centerW - previewSize) / 2;
        int previewY = sphereY + sphereSize + 44;
        Identifier tex = selectedPreviewTexture();
        if (previewsRegistered && tex != null) {
            ctx.drawTexture(RenderLayer::getGuiTextured, tex, previewX, previewY, 0, 0,
                previewSize, previewSize, previewSize, previewSize);
            ctx.drawBorder(previewX - 1, previewY - 1, previewSize + 2, previewSize + 2, RadianceTheme.borderFocused);
        }
        RadianceTheme.drawCenteredOutlinedText(ctx, textRenderer,
            Text.literal(selectedTab.label + " Preview"), centerX + centerW / 2,
            previewY + previewSize + 6, RadianceTheme.textAccent);
    }

    private Identifier selectedPreviewTexture() {
        return switch (selectedTab) {
            case ROUGHNESS, SPECULAR -> PREVIEW_ROUGHNESS_ID;
            case NORMAL -> PREVIEW_NORMAL_ID;
            case HEIGHT -> PREVIEW_HEIGHT_ID;
            case AO -> PREVIEW_AO_ID;
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= leftX && mouseX < leftX + leftW && mouseY >= leftY + 56 && mouseY < leftY + leftH - 18) {
            int row = ((int) mouseY - (leftY + 56)) / 18;
            int idx = browserScroll + row;
            if (idx >= 0 && idx < visibleOrdinals.size()) {
                selectedOrdinal = visibleOrdinals.get(idx);
                rebuildSelf();
                return true;
            }
        }
        if (SelectionDropdownWidget.handleOverlayClick(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= leftX && mouseX < leftX + leftW && mouseY >= leftY && mouseY < leftY + leftH) {
            browserScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelChanges();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = false;
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        cancelChanges();
    }

    @Override
    public void removed() {
        RadianceTheme.endSliderFocus();
        cleanupPreviews();
        super.removed();
    }

    private void applyChanges() {
        snapshotTaken = false;
        Options.overwriteConfig();
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    private void cancelChanges() {
        if (snapshot != null) snapshot.restore();
        snapshotTaken = false;
        Options.overwriteConfig();
        MinecraftClient.getInstance().setScreen(parentScreen);
    }

    private void resetMaterial() {
        if (selectedOrdinal < 0 || selectedOrdinal >= MaterialBlock.COUNT) return;
        MaterialData defaults = MaterialData.fromBlock(MaterialBlock.values()[selectedOrdinal]);
        defaults.applyToOptions(selectedOrdinal);
        onMaterialChanged(selectedOrdinal);
        rebuildSelf();
    }

    private void copyMaterial() {
        if (selectedOrdinal >= 0 && selectedOrdinal < MaterialBlock.COUNT) {
            MaterialClipboard.copy(selectedOrdinal);
        }
    }

    private void pasteMaterial() {
        if (selectedOrdinal >= 0 && selectedOrdinal < MaterialBlock.COUNT && MaterialClipboard.paste(selectedOrdinal)) {
            onMaterialChanged(selectedOrdinal);
            rebuildSelf();
        }
    }

    private void exportPack() {
        MaterialsPack pack = MaterialsPack.fromCurrentOptions();
        pack.name = "Radiance Material Workbench Export";
        MaterialFileManager.savePack(pack, "radiance-material-workbench-export");
    }

    private void rebuildSelf() {
        MinecraftClient.getInstance().setScreen(new MaterialWorkbenchScreen(parentScreen));
    }

    private MaterialData materialDataFor(int ord) {
        MaterialData d = new MaterialData();
        d.blockId = MaterialBlock.getIdForOrdinal(ord);
        d.displayName = MaterialBlock.getDisplayNameForOrdinal(ord);
        d.f0R = Options.materialF0R[ord];
        d.f0G = Options.materialF0G[ord];
        d.f0B = Options.materialF0B[ord];
        d.roughness = Options.materialRoughness[ord];
        d.metallic = Options.materialMetallic[ord];
        d.transmission = Options.materialTransmission[ord];
        d.ior = Options.materialIOR[ord];
        d.subsurface = Options.materialSubsurface[ord];
        d.anisotropic = Options.materialAnisotropic[ord];
        d.sheenWeight = Options.materialSheenWeight[ord];
        d.sheenTint = Options.materialSheenTint[ord];
        d.coatWeight = Options.materialCoatWeight[ord];
        d.coatRoughness = Options.materialCoatRoughness[ord];
        return d;
    }

    private void loadSourceAlbedo(String id) {
        cleanupPreviews();
        String[] candidates = {
            id, id + "_top", id + "_side", id + "_front", id + "_still",
            id + "_block", id + "_block_top", id + "_block_side",
            "white_" + id, "oak_" + id, id + "_planks", "brain_" + id,
            id + "_inner", id + "_block_side_inner"
        };
        var rm = MinecraftClient.getInstance().getResourceManager();
        for (String name : candidates) {
            try {
                Identifier texId = Identifier.of("minecraft", "textures/block/" + name + ".png");
                Optional<Resource> res = rm.getResource(texId);
                if (res.isPresent()) {
                    sourceAlbedo = NativeImage.read(res.get().getInputStream());
                    return;
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void regeneratePreview() {
        NativeImage albedoCopy = scaledAlbedo();
        int ord = selectedOrdinal;
        int flags = Options.materialAutoPBRFlags[ord];
        boolean invertRough = (flags & 1) != 0;
        boolean invertNormal = (flags & 2) != 0;
        boolean invertHeight = (flags & 4) != 0;
        AutoPBRGenerator.HeightParams hp = AutoPBRGenerator.HeightParams.fromOptions(ord);
        NativeImage normal = AutoPBRGenerator.generateNormal(albedoCopy,
            Options.materialNormalStrength[ord], invertNormal,
            Options.materialAutoPBRHeightGamma[ord], invertHeight,
            hp, Options.materialPomAOStrength[ord]);
        NativeImage rough = AutoPBRGenerator.generateRoughnessPreviewPercentile(albedoCopy,
            Options.materialAutoPBRRoughnessMin[ord], Options.materialAutoPBRRoughnessMax[ord],
            Options.materialPercentileCenter[ord], Options.materialPercentileSpread[ord], invertRough);
        NativeImage height = AutoPBRGenerator.generateHeightPreview(albedoCopy,
            Options.materialAutoPBRHeightGamma[ord], invertHeight, hp);
        NativeImage ao = extractAoPreview(normal);
        NativeImage noise = NoisePreviewGenerator.generate(160, ord);
        uploadPreview(albedoCopy, rough, normal, height, ao, noise);
    }

    private NativeImage scaledAlbedo() {
        if (sourceAlbedo == null) {
            NativeImage blank = new NativeImage(64, 64, false);
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) blank.setColorArgb(x, y, 0xFF202020);
            }
            return blank;
        }
        int fullW = sourceAlbedo.getWidth();
        int fullH = Math.min(sourceAlbedo.getHeight(), fullW);
        int size = Math.min(160, Math.max(16, fullW));
        NativeImage out = new NativeImage(size, size, false);
        for (int y = 0; y < size; y++) {
            int sy = Math.min(fullH - 1, y * fullH / size);
            for (int x = 0; x < size; x++) {
                int sx = Math.min(fullW - 1, x * fullW / size);
                out.setColorArgb(x, y, sourceAlbedo.getColorArgb(sx, sy));
            }
        }
        return out;
    }

    private NativeImage extractAoPreview(NativeImage normal) {
        NativeImage ao = new NativeImage(normal.getWidth(), normal.getHeight(), false);
        for (int y = 0; y < normal.getHeight(); y++) {
            for (int x = 0; x < normal.getWidth(); x++) {
                int p = normal.getColorArgb(x, y);
                int a = p & 0xFF;
                ao.setColorArgb(x, y, (255 << 24) | (a << 16) | (a << 8) | a);
            }
        }
        return ao;
    }

    private void uploadPreview(NativeImage albedo, NativeImage rough, NativeImage normal,
            NativeImage height, NativeImage ao, NativeImage noise) {
        if (!previewsRegistered) {
            var tm = MinecraftClient.getInstance().getTextureManager();
            previewAlbedoTex = new NativeImageBackedTexture(albedo);
            previewRoughTex = new NativeImageBackedTexture(rough);
            previewNormalTex = new NativeImageBackedTexture(normal);
            previewHeightTex = new NativeImageBackedTexture(height);
            previewAoTex = new NativeImageBackedTexture(ao);
            previewNoiseTex = new NativeImageBackedTexture(noise);
            previewAlbedoTex.upload(); previewRoughTex.upload(); previewNormalTex.upload();
            previewHeightTex.upload(); previewAoTex.upload(); previewNoiseTex.upload();
            tm.registerTexture(PREVIEW_ALBEDO_ID, previewAlbedoTex);
            tm.registerTexture(PREVIEW_ROUGHNESS_ID, previewRoughTex);
            tm.registerTexture(PREVIEW_NORMAL_ID, previewNormalTex);
            tm.registerTexture(PREVIEW_HEIGHT_ID, previewHeightTex);
            tm.registerTexture(PREVIEW_AO_ID, previewAoTex);
            tm.registerTexture(PREVIEW_NOISE_ID, previewNoiseTex);
            previewsRegistered = true;
            return;
        }
        previewAlbedoTex.setImage(albedo); previewAlbedoTex.upload();
        previewRoughTex.setImage(rough); previewRoughTex.upload();
        previewNormalTex.setImage(normal); previewNormalTex.upload();
        previewHeightTex.setImage(height); previewHeightTex.upload();
        previewAoTex.setImage(ao); previewAoTex.upload();
        previewNoiseTex.setImage(noise); previewNoiseTex.upload();
    }

    private void cleanupPreviews() {
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
    }

    private static class WorkbenchButton extends ClickableWidget {
        private final Runnable action;

        WorkbenchButton(int x, int y, int width, int height, String label, Runnable action) {
            super(x, y, width, height, Text.literal(label));
            this.action = action;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            RadianceTheme.drawCustomButton(context, getX(), getY(), getWidth(), getHeight(),
                isHovered(), MinecraftClient.getInstance().textRenderer, getMessage(), active);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!active || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
            action.run();
            return true;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private static final class SegmentButton extends WorkbenchButton {
        private final java.util.function.BooleanSupplier selected;

        SegmentButton(int x, int y, int width, int height, String label, Runnable action,
                java.util.function.BooleanSupplier selected) {
            super(x, y, width, height, label, action);
            this.selected = selected;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean on = selected.getAsBoolean();
            int bg = on ? RadianceTheme.withAlpha(0x2AB5A0, 0.60f)
                : (isHovered() ? RadianceTheme.buttonHover : RadianceTheme.buttonBg);
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(),
                on ? RadianceTheme.withAlpha(0x2AB5A0, 0.95f) : RadianceTheme.buttonBorder);
            Text label = RadianceTheme.trimText(MinecraftClient.getInstance().textRenderer,
                getMessage(), getWidth() - 8);
            int tw = MinecraftClient.getInstance().textRenderer.getWidth(label);
            RadianceTheme.drawOutlinedText(context, MinecraftClient.getInstance().textRenderer,
                label, getX() + (getWidth() - tw) / 2, getY() + (getHeight() - 8) / 2,
                on ? RadianceTheme.textPrimary : RadianceTheme.textSecondary);
        }
    }

    private static final class MaterialSnapshot {
        private final int[] f0r = Options.materialF0R.clone();
        private final int[] f0g = Options.materialF0G.clone();
        private final int[] f0b = Options.materialF0B.clone();
        private final int[] roughness = Options.materialRoughness.clone();
        private final int[] metallic = Options.materialMetallic.clone();
        private final int[] transmission = Options.materialTransmission.clone();
        private final int[] ior = Options.materialIOR.clone();
        private final int[] subsurface = Options.materialSubsurface.clone();
        private final int[] anisotropic = Options.materialAnisotropic.clone();
        private final int[] sheenWeight = Options.materialSheenWeight.clone();
        private final int[] sheenTint = Options.materialSheenTint.clone();
        private final int[] coatWeight = Options.materialCoatWeight.clone();
        private final int[] coatRoughness = Options.materialCoatRoughness.clone();
        private final int[] gamutBoost = Options.materialGamutBoost.clone();
        private final int[] pomDepth = Options.materialPomDepth.clone();
        private final int[] normalStrength = Options.materialNormalStrength.clone();
        private final int[] autoRoughMin = Options.materialAutoPBRRoughnessMin.clone();
        private final int[] autoRoughMax = Options.materialAutoPBRRoughnessMax.clone();
        private final int[] percentileCenter = Options.materialPercentileCenter.clone();
        private final int[] percentileSpread = Options.materialPercentileSpread.clone();
        private final int[] autoHeightGamma = Options.materialAutoPBRHeightGamma.clone();
        private final int[] autoFlags = Options.materialAutoPBRFlags.clone();
        private final int[] normalInput = Options.materialNormalInputType.clone();
        private final int[] specularInput = Options.materialSpecularInputType.clone();
        private final int[] heightFilter = Options.materialHeightFilter.clone();
        private final int[] filterRadius = Options.materialFilterRadius.clone();
        private final int[] mipBias = Options.materialMipBias.clone();
        private final int[] pomMode = Options.materialPomMode.clone();
        private final int[] heightSource = Options.materialHeightSource.clone();
        private final int[] heightContrast = Options.materialHeightContrast.clone();
        private final int[] heightRemapMin = Options.materialHeightRemapMin.clone();
        private final int[] heightRemapMax = Options.materialHeightRemapMax.clone();
        private final int[] heightOffset = Options.materialHeightOffset.clone();
        private final int[] normalClamp = Options.materialNormalClamp.clone();
        private final int[] geometricBlend = Options.materialGeometricBlend.clone();
        private final int[] normalDistanceFade = Options.materialNormalDistanceFade.clone();
        private final int[] pomAoStrength = Options.materialPomAOStrength.clone();
        private final boolean[] autoPBR = Options.materialAutoPBR.clone();
        private final boolean[] childOverride = Options.materialChildOverride.clone();
        private final boolean[] displacementSelfShadow = Options.materialDisplacementSelfShadow.clone();
        private final boolean autoPBREnabled = Options.autoPBREnabled;
        private final boolean materialOverridesEnabled = Options.materialOverridesEnabled;

        static MaterialSnapshot capture() {
            return new MaterialSnapshot();
        }

        void restore() {
            System.arraycopy(f0r, 0, Options.materialF0R, 0, f0r.length);
            System.arraycopy(f0g, 0, Options.materialF0G, 0, f0g.length);
            System.arraycopy(f0b, 0, Options.materialF0B, 0, f0b.length);
            System.arraycopy(roughness, 0, Options.materialRoughness, 0, roughness.length);
            System.arraycopy(metallic, 0, Options.materialMetallic, 0, metallic.length);
            System.arraycopy(transmission, 0, Options.materialTransmission, 0, transmission.length);
            System.arraycopy(ior, 0, Options.materialIOR, 0, ior.length);
            System.arraycopy(subsurface, 0, Options.materialSubsurface, 0, subsurface.length);
            System.arraycopy(anisotropic, 0, Options.materialAnisotropic, 0, anisotropic.length);
            System.arraycopy(sheenWeight, 0, Options.materialSheenWeight, 0, sheenWeight.length);
            System.arraycopy(sheenTint, 0, Options.materialSheenTint, 0, sheenTint.length);
            System.arraycopy(coatWeight, 0, Options.materialCoatWeight, 0, coatWeight.length);
            System.arraycopy(coatRoughness, 0, Options.materialCoatRoughness, 0, coatRoughness.length);
            System.arraycopy(gamutBoost, 0, Options.materialGamutBoost, 0, gamutBoost.length);
            System.arraycopy(pomDepth, 0, Options.materialPomDepth, 0, pomDepth.length);
            System.arraycopy(normalStrength, 0, Options.materialNormalStrength, 0, normalStrength.length);
            System.arraycopy(autoRoughMin, 0, Options.materialAutoPBRRoughnessMin, 0, autoRoughMin.length);
            System.arraycopy(autoRoughMax, 0, Options.materialAutoPBRRoughnessMax, 0, autoRoughMax.length);
            System.arraycopy(percentileCenter, 0, Options.materialPercentileCenter, 0, percentileCenter.length);
            System.arraycopy(percentileSpread, 0, Options.materialPercentileSpread, 0, percentileSpread.length);
            System.arraycopy(autoHeightGamma, 0, Options.materialAutoPBRHeightGamma, 0, autoHeightGamma.length);
            System.arraycopy(autoFlags, 0, Options.materialAutoPBRFlags, 0, autoFlags.length);
            System.arraycopy(normalInput, 0, Options.materialNormalInputType, 0, normalInput.length);
            System.arraycopy(specularInput, 0, Options.materialSpecularInputType, 0, specularInput.length);
            System.arraycopy(heightFilter, 0, Options.materialHeightFilter, 0, heightFilter.length);
            System.arraycopy(filterRadius, 0, Options.materialFilterRadius, 0, filterRadius.length);
            System.arraycopy(mipBias, 0, Options.materialMipBias, 0, mipBias.length);
            System.arraycopy(pomMode, 0, Options.materialPomMode, 0, pomMode.length);
            System.arraycopy(heightSource, 0, Options.materialHeightSource, 0, heightSource.length);
            System.arraycopy(heightContrast, 0, Options.materialHeightContrast, 0, heightContrast.length);
            System.arraycopy(heightRemapMin, 0, Options.materialHeightRemapMin, 0, heightRemapMin.length);
            System.arraycopy(heightRemapMax, 0, Options.materialHeightRemapMax, 0, heightRemapMax.length);
            System.arraycopy(heightOffset, 0, Options.materialHeightOffset, 0, heightOffset.length);
            System.arraycopy(normalClamp, 0, Options.materialNormalClamp, 0, normalClamp.length);
            System.arraycopy(geometricBlend, 0, Options.materialGeometricBlend, 0, geometricBlend.length);
            System.arraycopy(normalDistanceFade, 0, Options.materialNormalDistanceFade, 0, normalDistanceFade.length);
            System.arraycopy(pomAoStrength, 0, Options.materialPomAOStrength, 0, pomAoStrength.length);
            System.arraycopy(autoPBR, 0, Options.materialAutoPBR, 0, autoPBR.length);
            System.arraycopy(childOverride, 0, Options.materialChildOverride, 0, childOverride.length);
            System.arraycopy(displacementSelfShadow, 0, Options.materialDisplacementSelfShadow, 0, displacementSelfShadow.length);
            Options.autoPBREnabled = autoPBREnabled;
            Options.materialOverridesEnabled = materialOverridesEnabled;
            Options.markMaterialDirty();
            MaterialRegistry.markDirty();
        }
    }
}
