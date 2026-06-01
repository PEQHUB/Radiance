package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.material.MaterialRegistry;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.world.BlockModelBridge;
import com.radiance.client.texture.AutoPBRGenerator;
import com.radiance.client.texture.LiveNormalReuploader;
import com.radiance.client.texture.NoisePreviewGenerator;
import com.radiance.client.texture.TextureTracker;
import com.radiance.client.util.MaterialBlock;
import com.radiance.client.util.MaterialData;
import com.radiance.client.util.MaterialFileManager;
import com.radiance.client.util.MaterialsPack;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
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
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class MaterialsSettingsScreen extends Screen {
    private static final int DESIGN_W = 1672;
    private static final int DESIGN_H = 941;

    private static final int PANEL_FILL = 0xEC161A1B;
    private static final int PANEL_BORDER = 0xCC353736;
    private static final int FOOTER_FILL = 0xEA101510;
    private static final int ROW_FILL = 0xB0202322;
    private static final int ROW_DISABLED = 0x80202322;
    private static final int SELECTED_ROW = 0xB036AA83;
    private static final int TEAL = 0xFF20C4B0;
    private static final int ORANGE = 0xFFF89C08;
    private static final int TEXT_PRIMARY = 0xFFEDEDED;
    private static final int TEXT_SECONDARY = 0xFFB8B8B8;
    private static final int TEXT_DISABLED = 0xFF7A7D7C;
    private static final int TRACK = 0xFF2D2E2D;
    private static final int THUMB = 0xFFE5E5E5;
    private static final String UNBACKED_REASON = "Not currently backed by Radiance material data.";

    private final Screen parentScreen;
    private static int currentBlockIndex = 0;
    private static String searchQuery = "";
    private static int selectedCategory = 0;
    private static final int PREVIEW_ALBEDO_INDEX = 0;
    private static final int PREVIEW_ROUGHNESS_INDEX = 1;
    private static final int PREVIEW_NORMAL_INDEX = 2;
    private static final int PREVIEW_HEIGHT_INDEX = 3;
    private static final int PREVIEW_AO_INDEX = 4;
    private static final int PREVIEW_NOISE_INDEX = 5;
    private static int selectedPreviewMask = PREVIEW_ROUGHNESS_INDEX;
    private static int listScroll = 0;

    private TextFieldWidget searchField;
    private NativeImage sourceAlbedo;
    private final List<Integer> visibleMaterialIndices = new ArrayList<>();
    private String tooltipReason;

    private double designScale = 1.0;
    private int designOx = 0;
    private int designOy = 0;

    private DRect leftPanel, centerPanel, rightPanel, footerRect, listRect, previewStripRect;

    private static final Identifier PREVIEW_ALBEDO_ID = Identifier.of("radiance", "autopbr_preview/albedo");
    private static final Identifier PREVIEW_NORMAL_ID = Identifier.of("radiance", "autopbr_preview/normal");
    private static final Identifier PREVIEW_ROUGHNESS_ID = Identifier.of("radiance", "autopbr_preview/roughness");
    private static final Identifier PREVIEW_HEIGHT_ID = Identifier.of("radiance", "autopbr_preview/height");
    private static final Identifier PREVIEW_AO_ID = Identifier.of("radiance", "autopbr_preview/ao");
    private static final Identifier PREVIEW_NOISE_ID = Identifier.of("radiance", "autopbr_preview/noise");
    private static final Identifier[] PREVIEW_TEXTURES = {
        PREVIEW_ALBEDO_ID,
        PREVIEW_ROUGHNESS_ID,
        PREVIEW_NORMAL_ID,
        PREVIEW_HEIGHT_ID,
        PREVIEW_AO_ID,
        PREVIEW_NOISE_ID
    };
    private static final String[] PREVIEW_LABELS = {"Albedo", "Roughness", "Normal", "Height", "AO", "Noise"};
    private static NativeImageBackedTexture previewAlbedoTex;
    private static NativeImageBackedTexture previewNormalTex;
    private static NativeImageBackedTexture previewRoughTex;
    private static NativeImageBackedTexture previewHeightTex;
    private static NativeImageBackedTexture previewAoTex;
    private static NativeImageBackedTexture previewNoiseTex;
    private static boolean previewsRegistered = false;

    private static boolean snapshotTaken = false;
    private static final int[] snapF0R = new int[Options.MAX_MATERIALS], snapF0G = new int[Options.MAX_MATERIALS], snapF0B = new int[Options.MAX_MATERIALS];
    private static final int[] snapRoughness = new int[Options.MAX_MATERIALS], snapMetallic = new int[Options.MAX_MATERIALS];
    private static final int[] snapTransmission = new int[Options.MAX_MATERIALS], snapIOR = new int[Options.MAX_MATERIALS];
    private static final int[] snapSubsurface = new int[Options.MAX_MATERIALS], snapAnisotropic = new int[Options.MAX_MATERIALS];
    private static final int[] snapSheenWeight = new int[Options.MAX_MATERIALS], snapSheenTint = new int[Options.MAX_MATERIALS];
    private static final int[] snapCoatWeight = new int[Options.MAX_MATERIALS], snapCoatRoughness = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseScale = new int[Options.MAX_MATERIALS], snapNoiseStrength = new int[Options.MAX_MATERIALS], snapNoiseOctaves = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseType = new int[Options.MAX_MATERIALS], snapNoiseSeed = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseTarget = new int[Options.MAX_MATERIALS], snapNoiseMaskMode = new int[Options.MAX_MATERIALS], snapNoiseMaskThreshold = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseWrap = new int[Options.MAX_MATERIALS], snapNoiseRotation = new int[Options.MAX_MATERIALS], snapNoiseAspect = new int[Options.MAX_MATERIALS];
    private static final int[] snapNoiseLacunarity = new int[Options.MAX_MATERIALS], snapNoiseContrast = new int[Options.MAX_MATERIALS];
    private static final int[] snapGamutBoost = new int[Options.MAX_MATERIALS], snapGamutBoostMode = new int[Options.MAX_MATERIALS];
    private static final int[] snapDisplacementMode = new int[Options.MAX_MATERIALS], snapPomDepth = new int[Options.MAX_MATERIALS];
    private static final int[] snapAutoPBRRoughnessMin = new int[Options.MAX_MATERIALS], snapAutoPBRRoughnessMax = new int[Options.MAX_MATERIALS];
    private static final int[] snapRoughnessBlend = new int[Options.MAX_MATERIALS];
    private static final int[] snapPercentileCenter = new int[Options.MAX_MATERIALS], snapPercentileSpread = new int[Options.MAX_MATERIALS];
    private static final int[] snapPerBlockAutoPBRHtGamma = new int[Options.MAX_MATERIALS], snapPerBlockAutoPBRFlags = new int[Options.MAX_MATERIALS];
    private static final int[] snapHeightFilter = new int[Options.MAX_MATERIALS], snapFilterRadius = new int[Options.MAX_MATERIALS], snapMipBias = new int[Options.MAX_MATERIALS];
    private static final int[] snapHeightSource = new int[Options.MAX_MATERIALS], snapHeightContrast = new int[Options.MAX_MATERIALS], snapHeightOffset = new int[Options.MAX_MATERIALS];
    private static final int[] snapHeightRemapMin = new int[Options.MAX_MATERIALS], snapHeightRemapMax = new int[Options.MAX_MATERIALS];
    private static final int[] snapNormalStrength = new int[Options.MAX_MATERIALS], snapNormalClamp = new int[Options.MAX_MATERIALS], snapGeometricBlend = new int[Options.MAX_MATERIALS];
    private static final int[] snapPomAoStrength = new int[Options.MAX_MATERIALS];
    private static final int[] snapNormalInputType = new int[Options.MAX_MATERIALS], snapSpecularInputType = new int[Options.MAX_MATERIALS];
    private static final String[] snapCustomNormalPath = new String[Options.MAX_MATERIALS], snapCustomSpecularPath = new String[Options.MAX_MATERIALS];
    private static final boolean[] snapAutoPBR = new boolean[Options.MAX_MATERIALS], snapChildOverride = new boolean[Options.MAX_MATERIALS];
    private static final boolean[] snapNoiseMaskInvert = new boolean[Options.MAX_MATERIALS];
    private static final boolean[] snapDisplacementSelfShadow = new boolean[Options.MAX_MATERIALS];
    private static boolean snapAutoPBREnabled, snapMaterialOverridesEnabled;

    public MaterialsSettingsScreen(Screen parent) {
        super(Text.translatable("radiance.settings.materials.title"));
        this.parentScreen = parent;
        if (!snapshotTaken) {
            takeSnapshot();
            snapshotTaken = true;
        }
    }

    public static void setCurrentBlockIndex(int index) {
        currentBlockIndex = index;
    }

    public static void setCurrentOrdinal(int ordinal) {
        currentBlockIndex = MaterialBlock.indexOfOrdinal(ordinal);
    }

    @Override
    protected void init() {
        applyRadianceScale();
        tooltipReason = null;
        computeDesignTransform();
        computeRects();
        rebuildVisibleMaterials();
        clampCurrentMaterial();
        loadSourceAlbedo(MaterialBlock.getIdForOrdinal(currentOrdinal()));
        regeneratePreview();

        int ord = currentOrdinal();
        boolean thin = isThin(ord);

        searchField = new TextFieldWidget(textRenderer, sx(29), sy(91), sw(329), sh(35), Text.literal("Search materials..."));
        searchField.setMaxLength(64);
        searchField.setText(searchQuery);
        searchField.setPlaceholder(Text.literal("Search materials..."));
        searchField.setChangedListener(q -> {
            searchQuery = q;
            listScroll = 0;
            rebuildVisibleMaterials();
        });
        addDrawableChild(searchField);

        addDrawableChild(new CButton(367, 91, 34, 35, "v", () -> {
            selectedCategory = (selectedCategory + 1) % categoryCount();
            listScroll = 0;
            rebuildVisibleMaterials();
        }));
        addDrawableChild(new CDropdown(116, 138, 285, 35, "Category", categoryValues(), selectedCategory, value -> {
            selectedCategory = value;
            listScroll = 0;
            rebuildVisibleMaterials();
        }, null));
        addDrawableChild(new CButton(1622, 16, 34, 34, "X", this::cancelChanges));
        addDrawableChild(new CButton(1578, 16, 34, 34, "?", () -> {}, "Hover disabled controls for reasons."));
        addDrawableChild(new CButton(29, 749, 349, 35, "Manage Materials...", this::openMaterialsFolder));

        addFooterButtons();
        addCenterControls(ord, thin);
        addRightControls(ord, thin);
    }

    private void addFooterButtons() {
        addDrawableChild(new CButton(1095, 864, 85, 36, "Export", this::exportPack));
        addDrawableChild(new CButton(1194, 864, 99, 36, "Save", this::applyChanges));
        addDrawableChild(new CButton(1306, 864, 108, 36, "Save As...", this::saveSelectedMaterial));
        addDrawableChild(new CButton(1426, 864, 112, 36, "Cancel", this::cancelChanges));
        addDrawableChild(new CButton(1548, 864, 100, 36, "Apply", this::applyStay, () -> hasUnsavedChanges(), "No material edits to apply."));
    }

    private void addCenterControls(int ord, boolean thin) {
        addDrawableChild(new ReadOnlyField(653, 91, 164, 35, MaterialBlock.getDisplayNameForOrdinal(ord)));
        addDrawableChild(new ReadOnlyField(829, 91, 190, 35, MaterialBlock.getIdForOrdinal(ord)));

        String thinLock = thin ? "Thin plant cards use constrained cutout lighting; this channel is shader-locked off." : null;

        addFullSlider(449, 164, 570, 42, "Roughness", 0, 100, Options.materialRoughness[ord], defaultValue(ord, MaterialBlock::getDefaultRoughness),
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> { Options.materialRoughness[ord] = v; onMaterialChanged(ord); }, null, null);
        addFullSlider(449, 206, 570, 42, "Metallic", 0, 1000, Options.materialMetallic[ord], defaultValue(ord, MaterialBlock::getDefaultMetallic),
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialMetallic[ord] = v; onMaterialChanged(ord); }, thinLock, null);
        addFullSlider(449, 248, 570, 42, "Transmission", 0, 1000, Options.materialTransmission[ord], defaultValue(ord, MaterialBlock::getDefaultTransmission),
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialTransmission[ord] = v; onMaterialChanged(ord); }, thinLock, null);
        addFullSlider(449, 290, 570, 42, "IOR", 1000, 3000, Math.max(Options.materialIOR[ord], 1000), Math.max(defaultValue(ord, MaterialBlock::getDefaultIOR), 1000),
            v -> Text.literal(String.format("%.3f", v / 1000.0)), v -> {
                Options.materialIOR[ord] = v;
                if (Options.materialMetallic[ord] < 500) {
                    int f0 = MaterialBlock.iorToF0Permille(v);
                    Options.materialF0R[ord] = f0;
                    Options.materialF0G[ord] = f0;
                    Options.materialF0B[ord] = f0;
                }
                onMaterialChanged(ord);
            }, thinLock, null);

        addSliderCell(449, 373, 278, 42, thin ? "Plant Fill" : "Subsurface", 0, 1000, Options.materialSubsurface[ord], defaultValue(ord, MaterialBlock::getDefaultSubsurface),
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialSubsurface[ord] = v; onMaterialChanged(ord); }, null, null);
        if (thin) {
            addSliderCell(741, 373, 278, 42, "Plant Shadow", 0, 200, Options.materialNormalStrength[ord], 100,
                v -> Text.literal(String.format("%.2f", v / 100.0)), v -> { Options.materialNormalStrength[ord] = v; onMaterialChanged(ord); }, null, null);
        } else {
            addSliderCell(741, 373, 278, 42, "Anisotropic", 0, 1000, Options.materialAnisotropic[ord], defaultValue(ord, MaterialBlock::getDefaultAnisotropic),
                v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialAnisotropic[ord] = v; onMaterialChanged(ord); }, null, null);
        }
        addSliderCell(449, 415, 278, 42, "Coat", 0, 1000, Options.materialCoatWeight[ord], defaultValue(ord, MaterialBlock::getDefaultCoatWeight),
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialCoatWeight[ord] = v; onMaterialChanged(ord); }, thinLock, null);
        addSliderCell(741, 415, 278, 42, "Coat Roughness", 0, 100, Options.materialCoatRoughness[ord], defaultValue(ord, MaterialBlock::getDefaultCoatRoughness),
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> { Options.materialCoatRoughness[ord] = v; onMaterialChanged(ord); }, thinLock, null);
        addSliderCell(449, 457, 278, 42, "Sheen", 0, 1000, Options.materialSheenWeight[ord], defaultValue(ord, MaterialBlock::getDefaultSheenWeight),
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialSheenWeight[ord] = v; onMaterialChanged(ord); }, thinLock, null);
        addSliderCell(741, 457, 278, 42, "Sheen Tint", 0, 1000, Options.materialSheenTint[ord], defaultValue(ord, MaterialBlock::getDefaultSheenTint),
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> { Options.materialSheenTint[ord] = v; onMaterialChanged(ord); }, thinLock, null);

        addDrawableChild(new CDropdown(449, 533, 278, 42, "Mode", new String[]{"Inherit", "Off", "Custom"}, Math.min(Options.materialPomMode[ord], 2), v -> {
            Options.materialPomMode[ord] = v;
            if (v == 2 && Options.materialPomDepth[ord] == 0) Options.materialPomDepth[ord] = 5;
            onMaterialChanged(ord);
            rebuildSelf();
        }, thinLock));
        addSliderCell(741, 533, 278, 42, "Depth", 0, 50, Options.materialPomDepth[ord], 0,
            v -> v == 0 ? Text.literal("Off") : Text.literal(String.format("%.2f blocks", v / 100.0)), v -> {
                Options.materialPomDepth[ord] = v;
                onMaterialChanged(ord);
            }, thinLock, null);
        addDrawableChild(new CToggle(449, 575, 278, 35, "Self Shadow", () -> Options.materialDisplacementSelfShadow[ord], value -> {
            Options.materialDisplacementSelfShadow[ord] = value;
            onMaterialChanged(ord);
        }, thinLock));

        String parentName = parentNameFor(ord);
        String inheritanceReason = inheritanceDisabledReason(ord);
        addDrawableChild(new CDropdown(449, 642, 278, 35, "Parent Material", new String[]{parentName}, 0, v -> {}, ord < MaterialBlock.COUNT ? null : UNBACKED_REASON));
        addDrawableChild(new CDropdown(741, 642, 278, 35, "Blend", new String[]{"Replace"}, 0, v -> {}, UNBACKED_REASON));
        addDrawableChild(new CToggle(449, 684, 570, 35, "Inherit From Parent", () -> ord < MaterialBlock.COUNT && !Options.materialChildOverride[ord], value -> {
            if (ord < MaterialBlock.COUNT) {
                Options.materialChildOverride[ord] = !value;
                onMaterialChanged(ord);
            }
        }, inheritanceReason));
    }

    private void addRightControls(int ord, boolean thin) {
        switch (selectedPreviewIndex()) {
            case PREVIEW_ALBEDO_INDEX -> addAlbedoControls(ord);
            case PREVIEW_ROUGHNESS_INDEX -> addRoughnessControls(ord, thin);
            case PREVIEW_NORMAL_INDEX -> addNormalControls(ord, thin);
            case PREVIEW_HEIGHT_INDEX -> addHeightControls(ord, thin);
            case PREVIEW_AO_INDEX -> addAoControls(ord, thin);
            case PREVIEW_NOISE_INDEX -> addNoiseControls(ord, thin);
            default -> { }
        }
    }

    private void addAlbedoControls(int ord) {
        addSliderCell(1066, 274, 576, 42, "Gamut Boost", 0, 200, Options.materialGamutBoost[ord], 100,
            v -> Text.literal(String.format("%.2fx", v / 100.0)), v -> {
                Options.materialGamutBoost[ord] = v;
                onMaterialChanged(ord);
            }, null, null);
        addDrawableChild(new CDropdown(1066, 330, 576, 42, "Boost Mode", new String[]{"Uniform", "Saturation"}, Math.min(Options.materialGamutBoostMode[ord], 1), v -> {
            Options.materialGamutBoostMode[ord] = v;
            onMaterialChanged(ord);
        }, null));
        addDrawableChild(new CButton(1364, 548, 278, 42, "Reset", () -> resetAlbedoMask(ord)));
    }

    private void addRoughnessControls(int ord, boolean thin) {
        if (thin) {
            addSliderCell(1066, 274, 576, 42, "Plant Roughness", 0, 100, Options.materialRoughness[ord], defaultValue(ord, MaterialBlock::getDefaultRoughness),
                v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                    Options.materialRoughness[ord] = v;
                    onMaterialChanged(ord);
                }, null, null);
            return;
        }
        addDrawableChild(new CDropdown(1066, 274, 576, 42, "Specular Source", sourceModeLabels(), Math.min(Options.materialSpecularInputType[ord], 4), v -> {
            Options.materialSpecularInputType[ord] = v;
            onRoughnessMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false);
            rebuildSelf();
        }, null));
        addDrawableChild(new CToggle(1066, 326, 576, 42, "Auto-PBR Enabled", () -> Options.materialAutoPBR[ord], value -> {
            Options.materialAutoPBR[ord] = value;
            onMaterialChanged(ord);
            regeneratePreview();
            LiveNormalReuploader.scheduleGeneratedReupload(ord, true, true);
            rebuildSelf();
        }, null));
        addSliderCell(1066, 378, 576, 42, "Roughness Min", 0, 100, Options.materialAutoPBRRoughnessMin[ord], 30,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialAutoPBRRoughnessMin[ord] = v;
                onRoughnessMaskChanged(ord);
            }, null, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false));
        addSliderCell(1066, 430, 576, 42, "Roughness Max", 0, 100, Options.materialAutoPBRRoughnessMax[ord], 95,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialAutoPBRRoughnessMax[ord] = v;
                onRoughnessMaskChanged(ord);
            }, null, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false));
        addSliderCell(1066, 482, 576, 42, "Center", 0, 100, Options.materialPercentileCenter[ord], 50,
            v -> Text.literal(String.valueOf(v)), v -> {
                Options.materialPercentileCenter[ord] = v;
                onRoughnessMaskChanged(ord);
            }, null, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false));
        addSliderCell(1066, 534, 576, 42, "Spread", 1, 100, Options.materialPercentileSpread[ord], 80,
            v -> Text.literal(String.valueOf(v)), v -> {
                Options.materialPercentileSpread[ord] = v;
                onRoughnessMaskChanged(ord);
            }, null, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false));
        addSliderCell(1066, 586, 576, 42, "Roughness Blend", 0, 100, Options.materialRoughnessBlend[ord], 100,
            v -> Text.literal(v + "% slider"), v -> {
                Options.materialRoughnessBlend[ord] = v;
                onMaterialChanged(ord);
            }, null, null);
        addDrawableChild(new CToggle(1066, 638, 278, 42, "Invert", () -> (Options.materialAutoPBRFlags[ord] & 1) != 0, value -> {
            Options.materialAutoPBRFlags[ord] = (Options.materialAutoPBRFlags[ord] & ~1) | (value ? 1 : 0);
            onRoughnessMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, true, false);
        }, null));
        addDrawableChild(new CButton(1364, 638, 278, 42, "Reset", () -> resetRoughnessMask(ord)));
    }

    private void addNormalControls(int ord, boolean thin) {
        if (thin) {
            addSliderCell(1066, 274, 576, 42, "Plant Shadow", 0, 200, Options.materialNormalStrength[ord], 100,
                v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                    Options.materialNormalStrength[ord] = v;
                    onMaterialChanged(ord);
                }, null, null);
            addSliderCell(1066, 330, 576, 42, "Plant Fill", 0, 1000, Options.materialSubsurface[ord], defaultValue(ord, MaterialBlock::getDefaultSubsurface),
                v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> {
                    Options.materialSubsurface[ord] = v;
                    onMaterialChanged(ord);
                }, null, null);
            return;
        }
        String reason = normalGenerationReason(ord, false);
        addDrawableChild(new CDropdown(1066, 274, 576, 42, "Normal Source", sourceModeLabels(), Math.min(Options.materialNormalInputType[ord], 4), v -> {
            Options.materialNormalInputType[ord] = v;
            onGeneratedMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
            rebuildSelf();
        }, null));
        addDrawableChild(new CToggle(1066, 326, 576, 42, "Auto-PBR Enabled", () -> Options.materialAutoPBR[ord], value -> {
            Options.materialAutoPBR[ord] = value;
            onGeneratedMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
            rebuildSelf();
        }, null));
        addSliderCell(1066, 378, 576, 42, "Normal Strength", 0, 200, Options.materialNormalStrength[ord], 100,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialNormalStrength[ord] = v;
                onGeneratedMaskChanged(ord);
            }, reason, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1066, 430, 576, 42, "Normal Clamp", 0, 100, Options.materialNormalClamp[ord], 100,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialNormalClamp[ord] = v;
                onGeneratedMaskChanged(ord);
            }, reason, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1066, 482, 576, 42, "Geometric Blend", 0, 100, Options.materialGeometricBlend[ord], 0,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialGeometricBlend[ord] = v;
                onGeneratedMaskChanged(ord);
            }, null, null);
        addDrawableChild(new CToggle(1066, 534, 278, 42, "Flip Green (Y)", () -> (Options.materialAutoPBRFlags[ord] & 2) != 0, value -> {
            Options.materialAutoPBRFlags[ord] = (Options.materialAutoPBRFlags[ord] & ~2) | (value ? 2 : 0);
            onGeneratedMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        }, reason));
        addDrawableChild(new CButton(1364, 586, 278, 42, "Reset", () -> resetNormalMask(ord)));
    }

    private void addHeightControls(int ord, boolean thin) {
        String lock = thin ? "Thin plant cards force flat generated normals and bypass Auto-PBR height." : normalGenerationReason(ord, false);
        addDrawableChild(new CDropdown(1066, 274, 576, 42, "Source", new String[]{"Luminance", "Red", "Green", "Blue", "Alpha", "Max RGB", "Min RGB", "Custom"}, Math.min(Options.materialHeightSource[ord], 7), v -> {
            Options.materialHeightSource[ord] = v;
            onGeneratedMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        }, lock));
        addSliderCell(1066, 330, 576, 42, "Height Gamma", 10, 300, Options.materialAutoPBRHeightGamma[ord], 100,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialAutoPBRHeightGamma[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1066, 382, 278, 42, "Remap Min", 0, 100, Options.materialHeightRemapMin[ord], 0,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialHeightRemapMin[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1364, 382, 278, 42, "Remap Max", 0, 100, Options.materialHeightRemapMax[ord], 100,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialHeightRemapMax[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1066, 434, 278, 42, "Contrast", 0, 30, Options.materialHeightContrast[ord], 10,
            v -> Text.literal(String.format("%.1f", v / 10.0)), v -> {
                Options.materialHeightContrast[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1364, 434, 278, 42, "Offset", 0, 200, Options.materialHeightOffset[ord], 100,
            v -> Text.literal(String.format("%+.2f", (v - 100) / 100.0)), v -> {
                Options.materialHeightOffset[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addDrawableChild(new CDropdown(1066, 486, 278, 42, "Filter", new String[]{"Forward", "Central", "Sobel", "Bilinear", "Bicubic"}, Math.min(Options.materialHeightFilter[ord], 4), v -> {
            Options.materialHeightFilter[ord] = v;
            onGeneratedMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        }, lock));
        addDrawableChild(new CToggle(1364, 486, 278, 42, "Invert Height", () -> (Options.materialAutoPBRFlags[ord] & 4) != 0, value -> {
            Options.materialAutoPBRFlags[ord] = (Options.materialAutoPBRFlags[ord] & ~4) | (value ? 4 : 0);
            onGeneratedMaskChanged(ord);
            LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        }, lock));
        addDrawableChild(new CButton(1364, 548, 278, 42, "Reset", () -> resetHeightMask(ord)));
    }

    private void addAoControls(int ord, boolean thin) {
        String lock = thin ? "Thin plant cards force flat generated normals and disable generated AO." : normalGenerationReason(ord, false);
        addSliderCell(1066, 274, 576, 42, "AO Strength", 0, 100, Options.materialPomAOStrength[ord], 0,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialPomAOStrength[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addSliderCell(1066, 330, 576, 42, "Height Gamma", 10, 300, Options.materialAutoPBRHeightGamma[ord], 100,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialAutoPBRHeightGamma[ord] = v;
                onGeneratedMaskChanged(ord);
            }, lock, () -> LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true));
        addDrawableChild(new CButton(1364, 548, 278, 42, "Reset", () -> resetAoMask(ord)));
    }

    private void addNoiseControls(int ord, boolean thin) {
        String lock = thin ? "Thin plant cards disable procedural material noise." : null;
        addSliderCell(1066, 274, 576, 42, "Noise Strength", 0, 1000, Options.materialNoiseStrength[ord], 0,
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> {
                Options.materialNoiseStrength[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addSliderCell(1066, 330, 278, 42, "Scale", 1, 5000, Options.materialNoiseScale[ord], 50,
            v -> Text.literal(String.format("%.1f", v / 10.0)), v -> {
                Options.materialNoiseScale[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addSliderCell(1364, 330, 278, 42, "Octaves", 1, 8, Options.materialNoiseOctaves[ord], 2,
            v -> Text.literal(String.valueOf(v)), v -> {
                Options.materialNoiseOctaves[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addDrawableChild(new CDropdown(1066, 382, 576, 42, "Type", new String[]{"Simplex", "Worley F1", "Worley F2-F1", "Voronoi", "Ridged", "Turbulence", "Marble", "Wood", "Checker", "Brick", "Hex", "Scratches", "Dots", "Gradient", "Rings", "Crackle"}, Math.min(Options.materialNoiseType[ord], 15), v -> {
            Options.materialNoiseType[ord] = v;
            onNoiseMaskChanged(ord);
        }, lock));
        addSliderCell(1066, 434, 278, 42, "Seed", 0, 999, Options.materialNoiseSeed[ord], 0,
            v -> Text.literal(String.valueOf(v)), v -> {
                Options.materialNoiseSeed[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addSliderCell(1364, 434, 278, 42, "Contrast", 0, 200, Options.materialNoiseContrast[ord], 100,
            v -> Text.literal(String.format("%.2f", v / 100.0)), v -> {
                Options.materialNoiseContrast[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addSliderCell(1066, 486, 278, 42, "Rotation", 0, 3600, Options.materialNoiseRotation[ord], 0,
            v -> Text.literal(String.format("%.1f deg", v / 10.0)), v -> {
                Options.materialNoiseRotation[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addSliderCell(1364, 486, 278, 42, "Lacunarity", 10, 40, Options.materialNoiseLacunarity[ord], 20,
            v -> Text.literal(String.format("%.1f", v / 10.0)), v -> {
                Options.materialNoiseLacunarity[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addDrawableChild(new CDropdown(1066, 538, 278, 42, "Target", new String[]{"Off", "Roughness", "Normal", "Rough+Normal", "Metallic", "Rough+Metal", "Normal+Metal", "All"}, Math.min(Options.materialNoiseTarget[ord], 7), v -> {
            Options.materialNoiseTarget[ord] = v;
            onNoiseMaskChanged(ord);
        }, lock));
        addDrawableChild(new CDropdown(1364, 538, 278, 42, "Wrap", new String[]{"3D", "Surface", "Triplanar", "XZ", "XY", "YZ"}, Math.min(Options.materialNoiseWrap[ord], 5), v -> {
            Options.materialNoiseWrap[ord] = v;
            onNoiseMaskChanged(ord);
        }, lock));
        addDrawableChild(new CDropdown(1066, 590, 278, 42, "Mask", new String[]{"None", "Luminance", "Roughness", "Edge", "Normal"}, Math.min(Options.materialNoiseMaskMode[ord], 4), v -> {
            Options.materialNoiseMaskMode[ord] = v;
            onNoiseMaskChanged(ord);
        }, lock));
        addSliderCell(1364, 590, 278, 42, "Threshold", 0, 1000, Options.materialNoiseMaskThreshold[ord], 500,
            v -> Text.literal(String.format("%.2f", v / 1000.0)), v -> {
                Options.materialNoiseMaskThreshold[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addSliderCell(1066, 642, 278, 42, "Aspect", 10, 1000, Options.materialNoiseAspect[ord], 100,
            v -> Text.literal(String.format("%.1fx", v / 100.0)), v -> {
                Options.materialNoiseAspect[ord] = v;
                onNoiseMaskChanged(ord);
            }, lock, null);
        addDrawableChild(new CToggle(1364, 642, 278, 42, "Invert Mask", () -> Options.materialNoiseMaskInvert[ord], value -> {
            Options.materialNoiseMaskInvert[ord] = value;
            onNoiseMaskChanged(ord);
        }, lock));
        addDrawableChild(new CButton(1364, 694, 278, 42, "Reset", () -> resetNoiseMask(ord)));
    }

    private void onRoughnessMaskChanged(int ord) {
        onMaterialChanged(ord);
        regeneratePreview();
    }

    private void onGeneratedMaskChanged(int ord) {
        onMaterialChanged(ord);
        regeneratePreview();
    }

    private void onNoiseMaskChanged(int ord) {
        onMaterialChanged(ord);
        regeneratePreview();
    }

    private void resetAlbedoMask(int ord) {
        Options.materialGamutBoost[ord] = 100;
        Options.materialGamutBoostMode[ord] = 1;
        onMaterialChanged(ord);
        rebuildSelf();
    }

    private void resetRoughnessMask(int ord) {
        Options.materialAutoPBR[ord] = ord >= MaterialBlock.COUNT || !MaterialBlock.values()[ord].isThinCutoutPlantMaterial();
        Options.materialAutoPBRRoughnessMin[ord] = 30;
        Options.materialAutoPBRRoughnessMax[ord] = 95;
        Options.materialRoughnessBlend[ord] = 100;
        Options.materialSpecularInputType[ord] = Options.MATERIAL_SOURCE_AUTO;
        Options.materialPercentileCenter[ord] = 50;
        Options.materialPercentileSpread[ord] = 80;
        Options.materialAutoPBRFlags[ord] &= ~1;
        onRoughnessMaskChanged(ord);
        LiveNormalReuploader.scheduleGeneratedReupload(ord, true, true);
        rebuildSelf();
    }

    private void resetNormalMask(int ord) {
        Options.materialNormalStrength[ord] = 100;
        Options.materialNormalInputType[ord] = Options.MATERIAL_SOURCE_AUTO;
        Options.materialNormalClamp[ord] = 100;
        Options.materialGeometricBlend[ord] = 0;
        Options.materialAutoPBRFlags[ord] &= ~2;
        onGeneratedMaskChanged(ord);
        LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        rebuildSelf();
    }

    private void resetHeightMask(int ord) {
        Options.materialHeightSource[ord] = 0;
        Options.materialAutoPBRHeightGamma[ord] = 100;
        Options.materialHeightRemapMin[ord] = 0;
        Options.materialHeightRemapMax[ord] = 100;
        Options.materialHeightContrast[ord] = 10;
        Options.materialHeightOffset[ord] = 100;
        Options.materialHeightFilter[ord] = 0;
        Options.materialAutoPBRFlags[ord] &= ~4;
        onGeneratedMaskChanged(ord);
        LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        rebuildSelf();
    }

    private void resetAoMask(int ord) {
        Options.materialPomAOStrength[ord] = 0;
        onGeneratedMaskChanged(ord);
        LiveNormalReuploader.scheduleGeneratedReupload(ord, false, true);
        rebuildSelf();
    }

    private void resetNoiseMask(int ord) {
        Options.materialNoiseStrength[ord] = 0;
        Options.materialNoiseScale[ord] = 50;
        Options.materialNoiseOctaves[ord] = 2;
        Options.materialNoiseType[ord] = 0;
        Options.materialNoiseSeed[ord] = 0;
        Options.materialNoiseTarget[ord] = 1;
        Options.materialNoiseMaskMode[ord] = 0;
        Options.materialNoiseMaskInvert[ord] = false;
        Options.materialNoiseMaskThreshold[ord] = 500;
        Options.materialNoiseWrap[ord] = 1;
        Options.materialNoiseContrast[ord] = 100;
        Options.materialNoiseRotation[ord] = 0;
        Options.materialNoiseAspect[ord] = 100;
        Options.materialNoiseLacunarity[ord] = 20;
        onNoiseMaskChanged(ord);
        rebuildSelf();
    }

    private void addFullSlider(int x, int y, int w, int h, String label, int min, int max, int current, int def,
            IntFunction<Text> formatter, IntConsumer onChange, String disabledReason, Runnable onRelease) {
        addDrawableChild(new CSlider(x, y, w, h, label, min, max, current, def, formatter, onChange, disabledReason, onRelease, true));
    }

    private void addSliderCell(int x, int y, int w, int h, String label, int min, int max, int current, int def,
            IntFunction<Text> formatter, IntConsumer onChange, String disabledReason, Runnable onRelease) {
        addDrawableChild(new CSlider(x, y, w, h, label, min, max, current, def, formatter, onChange, disabledReason, onRelease, false));
    }

    private void addDisabledCell(int x, int y, int w, int h, String label, String value, String reason) {
        addDrawableChild(new DisabledCell(x, y, w, h, label, value, reason));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (RadianceTheme.peekActive) return;
        tooltipReason = null;
        context.fill(0, 0, width, height, 0xA8050A08);
        drawTopChrome(context);
        drawPanel(context, leftPanel, "MATERIAL SELECTOR");
        drawPanel(context, centerPanel, "MATERIAL PROPERTIES");
        drawPanel(context, rightPanel, selectedMaskTitle());
        drawStaticLabels(context);
        renderMaterialList(context, mouseX, mouseY);
        renderPreviewStrip(context, mouseX, mouseY);
        renderSelectedMaskPreview(context);
        drawFooter(context);
        super.render(context, mouseX, mouseY, delta);
        if (tooltipReason != null) {
            context.drawTooltip(textRenderer, Text.literal(tooltipReason), mouseX, mouseY);
        }
    }

    private void drawTopChrome(DrawContext context) {
        drawBoldText(context, "Radiance > Surfaces > Materials", 18, 18, ORANGE);
    }

    private void drawPanel(DrawContext context, DRect rect, String title) {
        context.fill(rect.x, rect.y, rect.x + rect.w, rect.y + rect.h, PANEL_FILL);
        context.drawBorder(rect.x, rect.y, rect.w, rect.h, PANEL_BORDER);
        drawBoldText(context, title, rect.dx + 17, 72, ORANGE);
    }

    private void drawStaticLabels(DrawContext context) {
        drawText(context, "Category:", 29, 149, TEXT_SECONDARY);
        drawText(context, "Material Name", 449, 108, TEXT_PRIMARY);
        drawSection(context, "Surface Response (Auto-PBR)", 449, 145);
        drawSmallText(context, "Index of Refraction. 1.0 is air, ~1.5 is typical for glass.", 459, 326, TEXT_DISABLED);
        drawSection(context, "Surface Microdetail", 449, 354);
        drawSection(context, "Displacement", 449, 514);
        drawSection(context, "Material Hierarchy", 449, 623);
        drawSection(context, "Selected Preview", 1066, 104);
    }

    private void drawFooter(DrawContext context) {
        DRect f = footerRect;
        context.fill(f.x, f.y, f.x + f.w, f.y + f.h, FOOTER_FILL);
        fillDesign(context, 30, 874, 18, 18, TEAL);
        drawText(context, "Enabled", 60, 879, TEXT_PRIMARY);
        fillDesign(context, 146, 874, 18, 18, TEXT_DISABLED);
        drawText(context, "Disabled", 176, 879, TEXT_PRIMARY);
        drawQuestion(context, designX(266), designY(873), designW(20), designH(20), null);
        drawText(context, "More info", 299, 879, TEXT_PRIMARY);
    }

    private void renderMaterialList(DrawContext context, int mouseX, int mouseY) {
        int rowH = designH(31);
        int viewportRows = Math.max(1, listRect.h / rowH);
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, visibleMaterialIndices.size() - viewportRows)));
        for (int row = 0; row < viewportRows; row++) {
            int idx = listScroll + row;
            if (idx >= visibleMaterialIndices.size()) break;
            int materialIndex = visibleMaterialIndices.get(idx);
            int ord = MaterialBlock.getUniqueOrdinals().get(materialIndex);
            int y = listRect.y + row * rowH;
            boolean selected = materialIndex == currentBlockIndex;
            boolean hovered = mouseX >= listRect.x && mouseX < listRect.x + listRect.w && mouseY >= y && mouseY < y + rowH;
            if (selected) {
                context.fill(listRect.x, y, listRect.x + listRect.w, y + rowH, SELECTED_ROW);
                context.drawBorder(listRect.x, y, listRect.w, rowH, TEAL);
            } else if (hovered) {
                context.fill(listRect.x, y, listRect.x + listRect.w, y + rowH, 0x6636AA83);
            } else {
                context.fill(listRect.x, y, listRect.x + listRect.w, y + rowH, 0x33202322);
            }
            drawMaterialIcon(context, ord, designX(44), y + (rowH - designH(24)) / 2, designW(24));
            Text label = RadianceTheme.trimText(textRenderer, Text.literal(MaterialBlock.getDisplayNameForOrdinal(ord)), designW(282));
            RadianceTheme.drawOutlinedText(context, textRenderer, label, designX(82), y + (rowH - 8) / 2, selected ? TEXT_PRIMARY : TEXT_SECONDARY);
        }
        context.drawBorder(listRect.x, listRect.y, listRect.w, listRect.h, 0x80353736);
        int barX = designX(391);
        int barY = designY(184);
        int barH = designH(558);
        context.fill(barX, barY, barX + designW(9), barY + barH, 0x66353736);
        if (!visibleMaterialIndices.isEmpty()) {
            int thumbH = Math.max(designH(40), barH * viewportRows / Math.max(viewportRows, visibleMaterialIndices.size()));
            int maxScroll = Math.max(1, visibleMaterialIndices.size() - viewportRows);
            int thumbY = barY + (barH - thumbH) * listScroll / maxScroll;
            context.fill(barX, thumbY, barX + designW(9), thumbY + thumbH, 0xCCB8B8B8);
        }
    }

    private void renderPreviewStrip(DrawContext context, int mouseX, int mouseY) {
        if (!previewsRegistered) return;
        DRect r = previewStripRect;
        context.fill(r.x, r.y, r.x + r.w, r.y + r.h, 0xB9161A1B);
        context.drawBorder(r.x, r.y, r.w, r.h, PANEL_BORDER);
        drawBoldText(context, "MASK PREVIEWS", 449, 737, ORANGE);

        int x = designX(449);
        int y = designY(760);
        int size = designW(64);
        int gap = designW(27);
        for (int i = 0; i < PREVIEW_TEXTURES.length; i++) {
            int tx = x + i * (size + gap);
            context.drawTexture(RenderLayer::getGuiTextured, PREVIEW_TEXTURES[i], tx, y, 0, 0, size, size, size, size);
            context.drawBorder(tx - 1, y - 1, size + 2, size + 2, i == selectedPreviewIndex() ? TEAL : PANEL_BORDER);
            Text previewLabel = RadianceTheme.trimText(textRenderer, Text.literal(PREVIEW_LABELS[i]), size + gap - designW(4));
            int labelX = tx + (size - textRenderer.getWidth(previewLabel)) / 2;
            context.drawText(textRenderer, previewLabel, labelX, designY(833), i == selectedPreviewIndex() ? TEAL : TEXT_PRIMARY, false);
        }
    }

    private void renderSelectedMaskPreview(DrawContext context) {
        if (!previewsRegistered) return;
        int index = selectedPreviewIndex();
        int x = designX(1066);
        int y = designY(121);
        int size = designW(132);
        context.fill(x, y, x + size, y + size, 0xB9161A1B);
        context.drawTexture(RenderLayer::getGuiTextured, PREVIEW_TEXTURES[index], x, y, 0, 0, size, size, size, size);
        context.drawBorder(x - 1, y - 1, size + 2, size + 2, TEAL);
        Text label = RadianceTheme.trimText(textRenderer, Text.literal(PREVIEW_LABELS[index]), size);
        context.drawText(textRenderer, label, x + (size - textRenderer.getWidth(label)) / 2, y + size + designH(8), TEXT_PRIMARY, false);
    }

    private int selectedPreviewIndex() {
        return MathHelper.clamp(selectedPreviewMask, 0, PREVIEW_LABELS.length - 1);
    }

    private String selectedMaskTitle() {
        int index = selectedPreviewIndex();
        if (index == PREVIEW_ALBEDO_INDEX) return "ALBEDO CONTROLS";
        return PREVIEW_LABELS[index].toUpperCase(java.util.Locale.ROOT) + " MASK";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && listRect != null && mouseX >= listRect.x && mouseX < listRect.x + listRect.w && mouseY >= listRect.y && mouseY < listRect.y + listRect.h) {
            int row = ((int) mouseY - listRect.y) / designH(31);
            int idx = listScroll + row;
            if (idx >= 0 && idx < visibleMaterialIndices.size()) {
                currentBlockIndex = visibleMaterialIndices.get(idx);
                rebuildSelf();
                return true;
            }
        }
        if (button == 0 && previewStripRect != null) {
            int x = designX(449);
            int y = designY(760);
            int size = designW(64);
            int gap = designW(27);
            for (int i = 0; i < PREVIEW_TEXTURES.length; i++) {
                int tx = x + i * (size + gap);
                if (mouseX >= tx && mouseX < tx + size && mouseY >= y && mouseY < y + size) {
                    selectedPreviewMask = i;
                    rebuildSelf();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (listRect != null && mouseX >= listRect.x && mouseX < listRect.x + listRect.w && mouseY >= listRect.y && mouseY < listRect.y + listRect.h) {
            listScroll -= (int) Math.signum(verticalAmount);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        if (button == 1) {
            Element focused = getFocused();
            if (focused != null) return focused.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return false;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchField != null) {
            searchField.setFocused(true);
            if (searchField.charTyped(chr, modifiers)) return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelChanges();
            return true;
        }
        if (RadianceTheme.handlePeekKeyPressed(keyCode)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (RadianceTheme.handlePeekKeyReleased(keyCode)) return true;
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
        Screen next = this.client != null ? this.client.currentScreen : null;
        if (!(next instanceof MaterialsSettingsScreen)
                && !(next instanceof com.radiance.client.gui.unified.RadianceUnifiedScreen)) {
            GuiScaleHelper.restoreOriginalScale();
        }
    }

    private void applyChanges() {
        snapshotTaken = false;
        Options.overwriteConfig();
        this.client.setScreen(this.parentScreen);
    }

    private void applyStay() {
        snapshotTaken = false;
        takeSnapshot();
        snapshotTaken = true;
        Options.overwriteConfig();
    }

    private void cancelChanges() {
        restoreSnapshot();
        snapshotTaken = false;
        Options.overwriteConfig();
        if (this.client != null) this.client.setScreen(this.parentScreen);
    }

    private void exportPack() {
        MaterialsPack pack = MaterialsPack.fromCurrentOptions();
        pack.name = "All Materials";
        MaterialFileManager.savePack(pack, "all-materials");
    }

    private void saveSelectedMaterial() {
        int ord = currentOrdinal();
        if (ord >= 0 && ord < MaterialBlock.COUNT) {
            MaterialData data = MaterialData.fromOptions(ord);
            if (data != null) MaterialFileManager.saveMaterial(data, MaterialBlock.getIdForOrdinal(ord));
        }
    }

    private void openMaterialsFolder() {
        MaterialFileManager.ensureDirectories();
        Path dir = MaterialFileManager.getMaterialsDir();
        Util.getOperatingSystem().open(dir.toFile());
    }

    private void resetMaterial(int ord) {
        if (ord < 0 || ord >= MaterialBlock.COUNT) return;
        MaterialData defaults = MaterialData.fromBlock(MaterialBlock.values()[ord]);
        if (defaults != null) {
            defaults.applyToOptions(ord);
            onMaterialChanged(ord);
            rebuildSelf();
        }
    }

    private void rebuildSelf() {
        if (parentScreen instanceof com.radiance.client.gui.unified.RadianceUnifiedScreen unified
                && unified.isOverlayShowing()) {
            unified.showOverlay(new MaterialsSettingsScreen(parentScreen));
        } else {
            MinecraftClient.getInstance().setScreen(new MaterialsSettingsScreen(parentScreen));
        }
    }

    private void computeDesignTransform() {
        designScale = Math.min(width / (double) DESIGN_W, height / (double) DESIGN_H);
        designOx = (int) Math.round((width - DESIGN_W * designScale) / 2.0);
        designOy = (int) Math.round((height - DESIGN_H * designScale) / 2.0);
    }

    private void computeRects() {
        leftPanel = rect(15, 52, 401, 743);
        centerPanel = rect(433, 52, 603, 831);
        rightPanel = rect(1052, 52, 606, 720);
        footerRect = rect(15, 859, 1642, 45);
        listRect = rect(29, 183, 349, 557);
        previewStripRect = rect(433, 725, 603, 129);
    }

    private DRect rect(int x, int y, int w, int h) {
        return new DRect(x, y, sx(x), sy(y), sw(w), sh(h));
    }

    private int sx(int x) { return designX(x); }
    private int sy(int y) { return designY(y); }
    private int sw(int w) { return designW(w); }
    private int sh(int h) { return designH(h); }
    private int designX(int x) { return designOx + (int) Math.round(x * designScale); }
    private int designY(int y) { return designOy + (int) Math.round(y * designScale); }
    private int designW(int w) { return Math.max(1, (int) Math.round(w * designScale)); }
    private int designH(int h) { return Math.max(1, (int) Math.round(h * designScale)); }

    private void drawText(DrawContext ctx, String text, int x, int y, int color) {
        RadianceTheme.drawOutlinedText(ctx, textRenderer, Text.literal(text), designX(x), designY(y), color);
    }

    private void drawBoldText(DrawContext ctx, String text, int x, int y, int color) {
        int dx = designX(x);
        int dy = designY(y);
        Text literal = Text.literal(text);
        RadianceTheme.drawOutlinedText(ctx, textRenderer, literal, dx, dy, color);
        RadianceTheme.drawOutlinedText(ctx, textRenderer, literal, dx + Math.max(1, designW(1)), dy, color);
    }

    private void drawSmallText(DrawContext ctx, String text, int x, int y, int color) {
        contextText(ctx, text, designX(x), designY(y), color);
    }

    private void contextText(DrawContext ctx, String text, int x, int y, int color) {
        ctx.drawText(textRenderer, Text.literal(text), x, y, color, false);
    }

    private void drawSection(DrawContext ctx, String text, int x, int y) {
        drawBoldText(ctx, text, x, y, TEAL);
    }

    private void drawDropdownShell(DrawContext ctx, int x, int y, int w, int h, String text, boolean enabled) {
        ctx.fill(x, y, x + w, y + h, enabled ? ROW_FILL : ROW_DISABLED);
        ctx.drawBorder(x, y, w, h, enabled ? PANEL_BORDER : 0x80353736);
        int textX = x + designW(10);
        int reserveRight = enabled ? designW(30) : designW(56);
        Text display = RadianceTheme.trimText(textRenderer, Text.literal(text), Math.max(1, w - designW(10) - reserveRight));
        ctx.drawText(textRenderer, display, textX, y + (h - 8) / 2, enabled ? TEXT_PRIMARY : TEXT_DISABLED, false);
        ctx.drawText(textRenderer, Text.literal("v"), x + w - designW(18), y + (h - 8) / 2, enabled ? TEXT_SECONDARY : TEXT_DISABLED, false);
    }

    private void fillDesign(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(designX(x), designY(y), designX(x) + designW(w), designY(y) + designH(h), color);
    }

    private void drawQuestion(DrawContext ctx, int x, int y, int w, int h, String reason) {
        ctx.drawBorder(x, y, w, h, reason == null ? TEXT_SECONDARY : TEXT_DISABLED);
        ctx.drawText(textRenderer, Text.literal("?"), x + (w - textRenderer.getWidth("?")) / 2, y + (h - 8) / 2, reason == null ? TEXT_PRIMARY : TEXT_DISABLED, false);
        if (reason != null && isPointIn(MinecraftClient.getInstance().mouse.getX(), MinecraftClient.getInstance().mouse.getY(), x, y, w, h)) {
            tooltipReason = reason;
        }
    }

    private void drawMaterialIcon(DrawContext context, int ord, int x, int y, int size) {
        if (ord >= 0 && ord < MaterialBlock.COUNT) {
            Block block = MaterialBlock.values()[ord].getPrimaryBlock();
            if (block != null) {
                RadianceBlockIcon.drawBlockIcon(context, block, x, y, size);
                return;
            }
        }
        context.fill(x, y, x + size, y + size, 0xFF555A55);
        context.drawBorder(x, y, size, size, PANEL_BORDER);
    }

    private boolean isPointIn(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private String categoryLabel() {
        if (selectedCategory == 0) return "All";
        MaterialBlock.MaterialCategory[] categories = MaterialBlock.MaterialCategory.values();
        int idx = MathHelper.clamp(selectedCategory - 1, 0, categories.length - 1);
        return categories[idx].getDisplayName();
    }

    private String[] categoryValues() {
        MaterialBlock.MaterialCategory[] categories = MaterialBlock.MaterialCategory.values();
        String[] values = new String[categories.length + 1];
        values[0] = "All";
        for (int i = 0; i < categories.length; i++) values[i + 1] = categories[i].getDisplayName();
        return values;
    }

    private int categoryCount() {
        return MaterialBlock.MaterialCategory.values().length + 1;
    }

    private void rebuildVisibleMaterials() {
        visibleMaterialIndices.clear();
        String q = searchQuery == null ? "" : searchQuery.toLowerCase();
        List<Integer> ordinals = MaterialBlock.getUniqueOrdinals();
        for (int index = 0; index < ordinals.size(); index++) {
            int ord = ordinals.get(index);
            if (selectedCategory > 0) {
                if (ord >= MaterialBlock.COUNT) continue;
                MaterialBlock.MaterialCategory category = MaterialBlock.MaterialCategory.values()[selectedCategory - 1];
                if (MaterialBlock.values()[ord].getCategory() != category) continue;
            }
            String name = MaterialBlock.getDisplayNameForOrdinal(ord).toLowerCase();
            String id = MaterialBlock.getIdForOrdinal(ord).toLowerCase();
            if (!q.isEmpty() && !name.contains(q) && !id.contains(q)) continue;
            visibleMaterialIndices.add(index);
        }
    }

    private void clampCurrentMaterial() {
        List<Integer> ordinals = MaterialBlock.getUniqueOrdinals();
        if (ordinals.isEmpty()) {
            currentBlockIndex = 0;
            return;
        }
        currentBlockIndex = MathHelper.clamp(currentBlockIndex, 0, ordinals.size() - 1);
    }

    private int currentOrdinal() {
        List<Integer> ordinals = MaterialBlock.getUniqueOrdinals();
        if (ordinals.isEmpty()) return 0;
        currentBlockIndex = MathHelper.clamp(currentBlockIndex, 0, ordinals.size() - 1);
        return ordinals.get(currentBlockIndex);
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

    private int defaultValue(int ord, java.util.function.ToIntFunction<MaterialBlock> getter) {
        if (ord >= 0 && ord < MaterialBlock.COUNT) return getter.applyAsInt(MaterialBlock.values()[ord]);
        return 0;
    }

    private boolean isThin(int ord) {
        return MaterialBlock.isThinCutoutPlantMaterialOrdinal(ord);
    }

    private String normalGenerationReason(int ord, boolean thin) {
        if (thin) return "Thin plant cards force flat generated normals and bypass Auto-PBR height.";
        if (!Options.materialOverridesEnabled) return "Material Overrides is disabled; the material menu is bypassed.";
        if (!Options.autoPBREnabled) return "Global Auto-PBR is disabled.";
        if (!Options.materialAutoPBR[ord]) return "Auto-PBR is disabled for this material.";
        String external = normalExternalSource(ord);
        if (external != null) return external;
        return null;
    }

    private static String[] sourceModeLabels() {
        return new String[]{"Auto", "Custom", "Flat", "Generated", "Authored"};
    }

    private String controlTooltip(String label) {
        return switch (label) {
            case "Roughness Blend" -> "Blends authored/generated roughness with the material roughness slider. 100% means the slider fully controls runtime roughness.";
            case "Specular Source" -> "Chooses where the roughness/specular map comes from: Auto, Custom file, Flat neutral map, forced generated map, or pack-authored map.";
            case "Normal Source" -> "Chooses where the normal/height map comes from: Auto, Custom file, Flat neutral map, forced generated map, or pack-authored map.";
            case "Auto-PBR Enabled" -> "Allows generated roughness, normal, and height masks for this material when global Auto-PBR and Material Overrides are enabled.";
            case "Roughness Min" -> "Lower bound for generated roughness from albedo luminance.";
            case "Roughness Max" -> "Upper bound for generated roughness from albedo luminance.";
            case "Center" -> "Albedo luminance percentile mapped to mid roughness.";
            case "Spread" -> "Generated roughness contrast around the center percentile.";
            case "Invert" -> "Inverts generated roughness before runtime blending.";
            case "Normal Strength", "Plant Shadow" -> "Normal intensity for generated maps; thin plants use this as their card shadow response.";
            case "Plant Fill" -> "Thin plant fill-light/subsurface response.";
            case "Plant Roughness" -> "Runtime roughness for thin plant cutout cards.";
            case "Height Gamma" -> "Gamma curve applied to generated height from albedo.";
            case "Flip Green (Y)" -> "Flips the generated normal map Y channel.";
            default -> label;
        };
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

    private String parentNameFor(int ord) {
        if (ord < 0 || ord >= MaterialBlock.COUNT) return "None";
        MaterialBlock block = MaterialBlock.values()[ord];
        return block.isParent() ? "None" : MaterialBlock.getDisplayNameForOrdinal(block.getParentMaterial().ordinal());
    }

    private String inheritanceDisabledReason(int ord) {
        if (ord < 0 || ord >= MaterialBlock.COUNT) return UNBACKED_REASON;
        MaterialBlock block = MaterialBlock.values()[ord];
        return block.isParent() ? "This material has no parent to inherit from." : null;
    }

    private boolean hasUnsavedChanges() {
        if (Options.autoPBREnabled != snapAutoPBREnabled || Options.materialOverridesEnabled != snapMaterialOverridesEnabled) return true;
        return diff(snapF0R, Options.materialF0R) || diff(snapF0G, Options.materialF0G) || diff(snapF0B, Options.materialF0B)
            || diff(snapRoughness, Options.materialRoughness) || diff(snapMetallic, Options.materialMetallic)
            || diff(snapTransmission, Options.materialTransmission) || diff(snapIOR, Options.materialIOR)
            || diff(snapSubsurface, Options.materialSubsurface) || diff(snapAnisotropic, Options.materialAnisotropic)
            || diff(snapSheenWeight, Options.materialSheenWeight) || diff(snapSheenTint, Options.materialSheenTint)
            || diff(snapCoatWeight, Options.materialCoatWeight) || diff(snapCoatRoughness, Options.materialCoatRoughness)
            || diff(snapNoiseScale, Options.materialNoiseScale) || diff(snapNoiseStrength, Options.materialNoiseStrength)
            || diff(snapNoiseOctaves, Options.materialNoiseOctaves) || diff(snapNoiseType, Options.materialNoiseType)
            || diff(snapNoiseSeed, Options.materialNoiseSeed) || diff(snapNoiseTarget, Options.materialNoiseTarget)
            || diff(snapNoiseMaskMode, Options.materialNoiseMaskMode) || diff(snapNoiseMaskInvert, Options.materialNoiseMaskInvert)
            || diff(snapNoiseMaskThreshold, Options.materialNoiseMaskThreshold) || diff(snapNoiseWrap, Options.materialNoiseWrap)
            || diff(snapNoiseRotation, Options.materialNoiseRotation) || diff(snapNoiseAspect, Options.materialNoiseAspect)
            || diff(snapNoiseLacunarity, Options.materialNoiseLacunarity) || diff(snapNoiseContrast, Options.materialNoiseContrast)
            || diff(snapGamutBoost, Options.materialGamutBoost) || diff(snapGamutBoostMode, Options.materialGamutBoostMode)
            || diff(snapDisplacementMode, Options.materialPomMode) || diff(snapPomDepth, Options.materialPomDepth)
            || diff(snapAutoPBRRoughnessMin, Options.materialAutoPBRRoughnessMin) || diff(snapAutoPBRRoughnessMax, Options.materialAutoPBRRoughnessMax)
            || diff(snapRoughnessBlend, Options.materialRoughnessBlend)
            || diff(snapPercentileCenter, Options.materialPercentileCenter) || diff(snapPercentileSpread, Options.materialPercentileSpread)
            || diff(snapPerBlockAutoPBRHtGamma, Options.materialAutoPBRHeightGamma) || diff(snapPerBlockAutoPBRFlags, Options.materialAutoPBRFlags)
            || diff(snapHeightFilter, Options.materialHeightFilter) || diff(snapFilterRadius, Options.materialFilterRadius)
            || diff(snapMipBias, Options.materialMipBias) || diff(snapHeightSource, Options.materialHeightSource)
            || diff(snapHeightContrast, Options.materialHeightContrast) || diff(snapHeightOffset, Options.materialHeightOffset)
            || diff(snapHeightRemapMin, Options.materialHeightRemapMin) || diff(snapHeightRemapMax, Options.materialHeightRemapMax)
            || diff(snapNormalStrength, Options.materialNormalStrength) || diff(snapNormalClamp, Options.materialNormalClamp)
            || diff(snapGeometricBlend, Options.materialGeometricBlend) || diff(snapPomAoStrength, Options.materialPomAOStrength)
            || diff(snapNormalInputType, Options.materialNormalInputType) || diff(snapSpecularInputType, Options.materialSpecularInputType)
            || diff(snapCustomNormalPath, Options.materialCustomNormalPath) || diff(snapCustomSpecularPath, Options.materialCustomSpecularPath)
            || diff(snapAutoPBR, Options.materialAutoPBR) || diff(snapChildOverride, Options.materialChildOverride)
            || diff(snapDisplacementSelfShadow, Options.materialDisplacementSelfShadow);
    }

    private boolean diff(int[] a, int[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) if (a[i] != b[i]) return true;
        return false;
    }

    private boolean diff(boolean[] a, boolean[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) if (a[i] != b[i]) return true;
        return false;
    }

    private boolean diff(String[] a, String[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            String av = a[i] == null ? "" : a[i];
            String bv = b[i] == null ? "" : b[i];
            if (!av.equals(bv)) return true;
        }
        return false;
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
        System.arraycopy(Options.materialNoiseTarget, 0, snapNoiseTarget, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseMaskMode, 0, snapNoiseMaskMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseMaskThreshold, 0, snapNoiseMaskThreshold, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseWrap, 0, snapNoiseWrap, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseRotation, 0, snapNoiseRotation, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseAspect, 0, snapNoiseAspect, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseLacunarity, 0, snapNoiseLacunarity, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseContrast, 0, snapNoiseContrast, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialGamutBoost, 0, snapGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialGamutBoostMode, 0, snapGamutBoostMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPomMode, 0, snapDisplacementMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPomDepth, 0, snapPomDepth, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRRoughnessMin, 0, snapAutoPBRRoughnessMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRRoughnessMax, 0, snapAutoPBRRoughnessMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialRoughnessBlend, 0, snapRoughnessBlend, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPercentileCenter, 0, snapPercentileCenter, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPercentileSpread, 0, snapPercentileSpread, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRHeightGamma, 0, snapPerBlockAutoPBRHtGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBRFlags, 0, snapPerBlockAutoPBRFlags, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialHeightFilter, 0, snapHeightFilter, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialFilterRadius, 0, snapFilterRadius, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialMipBias, 0, snapMipBias, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialHeightSource, 0, snapHeightSource, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialHeightContrast, 0, snapHeightContrast, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialHeightOffset, 0, snapHeightOffset, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialHeightRemapMin, 0, snapHeightRemapMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialHeightRemapMax, 0, snapHeightRemapMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNormalStrength, 0, snapNormalStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNormalClamp, 0, snapNormalClamp, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialGeometricBlend, 0, snapGeometricBlend, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialPomAOStrength, 0, snapPomAoStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNormalInputType, 0, snapNormalInputType, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialSpecularInputType, 0, snapSpecularInputType, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialCustomNormalPath, 0, snapCustomNormalPath, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialCustomSpecularPath, 0, snapCustomSpecularPath, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialAutoPBR, 0, snapAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialChildOverride, 0, snapChildOverride, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialNoiseMaskInvert, 0, snapNoiseMaskInvert, 0, Options.MAX_MATERIALS);
        System.arraycopy(Options.materialDisplacementSelfShadow, 0, snapDisplacementSelfShadow, 0, Options.MAX_MATERIALS);
        snapAutoPBREnabled = Options.autoPBREnabled;
        snapMaterialOverridesEnabled = Options.materialOverridesEnabled;
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
        System.arraycopy(snapNoiseTarget, 0, Options.materialNoiseTarget, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseMaskMode, 0, Options.materialNoiseMaskMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseMaskThreshold, 0, Options.materialNoiseMaskThreshold, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseWrap, 0, Options.materialNoiseWrap, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseRotation, 0, Options.materialNoiseRotation, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseAspect, 0, Options.materialNoiseAspect, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseLacunarity, 0, Options.materialNoiseLacunarity, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseContrast, 0, Options.materialNoiseContrast, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapGamutBoost, 0, Options.materialGamutBoost, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapGamutBoostMode, 0, Options.materialGamutBoostMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapDisplacementMode, 0, Options.materialPomMode, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPomDepth, 0, Options.materialPomDepth, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBRRoughnessMin, 0, Options.materialAutoPBRRoughnessMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBRRoughnessMax, 0, Options.materialAutoPBRRoughnessMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapRoughnessBlend, 0, Options.materialRoughnessBlend, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPercentileCenter, 0, Options.materialPercentileCenter, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPercentileSpread, 0, Options.materialPercentileSpread, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRHtGamma, 0, Options.materialAutoPBRHeightGamma, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPerBlockAutoPBRFlags, 0, Options.materialAutoPBRFlags, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapHeightFilter, 0, Options.materialHeightFilter, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapFilterRadius, 0, Options.materialFilterRadius, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapMipBias, 0, Options.materialMipBias, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapHeightSource, 0, Options.materialHeightSource, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapHeightContrast, 0, Options.materialHeightContrast, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapHeightOffset, 0, Options.materialHeightOffset, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapHeightRemapMin, 0, Options.materialHeightRemapMin, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapHeightRemapMax, 0, Options.materialHeightRemapMax, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNormalStrength, 0, Options.materialNormalStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNormalClamp, 0, Options.materialNormalClamp, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapGeometricBlend, 0, Options.materialGeometricBlend, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapPomAoStrength, 0, Options.materialPomAOStrength, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNormalInputType, 0, Options.materialNormalInputType, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapSpecularInputType, 0, Options.materialSpecularInputType, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapCustomNormalPath, 0, Options.materialCustomNormalPath, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapCustomSpecularPath, 0, Options.materialCustomSpecularPath, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapAutoPBR, 0, Options.materialAutoPBR, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapChildOverride, 0, Options.materialChildOverride, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapNoiseMaskInvert, 0, Options.materialNoiseMaskInvert, 0, Options.MAX_MATERIALS);
        System.arraycopy(snapDisplacementSelfShadow, 0, Options.materialDisplacementSelfShadow, 0, Options.MAX_MATERIALS);
        Options.autoPBREnabled = snapAutoPBREnabled;
        Options.materialOverridesEnabled = snapMaterialOverridesEnabled;
        Options.markMaterialDirty();
        MaterialRegistry.markDirty();
    }

    private void applyRadianceScale() {
        // The material menu must not mutate Minecraft's global GUI scale.
        // Layout scaling is handled by sx/sy/sw/sh against the current screen size.
    }

    private void loadSourceAlbedo(String id) {
        if (sourceAlbedo != null) {
            sourceAlbedo.close();
            sourceAlbedo = null;
        }
        List<String> candidates = new ArrayList<>();
        Set<Integer> mappedSprites = BlockModelBridge.materialOrdinal2SpriteIds.get(currentOrdinal());
        if (mappedSprites != null && !mappedSprites.isEmpty()) {
            for (int spriteId : mappedSprites) {
                if (spriteId < 0 || spriteId >= BlockModelBridge.sortedSpriteIds.size()) continue;
                Identifier sprite = BlockModelBridge.sortedSpriteIds.get(spriteId);
                if ("minecraft".equals(sprite.getNamespace())) {
                    candidates.add(sprite.getPath());
                }
            }
        }
        candidates.add(id);
        candidates.add(id + "_top");
        candidates.add(id + "_side");
        candidates.add(id + "_front");
        candidates.add(id + "_still");
        candidates.add(id + "_block");
        candidates.add(id + "_block_top");
        candidates.add(id + "_block_side");
        candidates.add("white_" + id);
        candidates.add("oak_" + id);
        candidates.add(id + "_planks");
        candidates.add("brain_" + id);
        candidates.add(id + "_inner");
        candidates.add(id + "_block_side_inner");
        var rm = MinecraftClient.getInstance().getResourceManager();
        for (String name : candidates) {
            try {
                Identifier texId = Identifier.of("minecraft", "textures/block/" + name + ".png");
                Optional<Resource> res = rm.getResource(texId);
                if (res.isPresent()) {
                    sourceAlbedo = NativeImage.read(res.get().getInputStream());
                    break;
                }
            } catch (IOException ignored) {
            }
        }
    }

    private void regeneratePreview() {
        int ord = currentOrdinal();
        NativeImage albedoCopy = scaledAlbedo();
        int flags = Options.materialAutoPBRFlags[ord];
        boolean invertRough = (flags & 1) != 0;
        boolean invertNormal = (flags & 2) != 0;
        boolean invertHeight = (flags & 4) != 0;
        AutoPBRGenerator.HeightParams hp = AutoPBRGenerator.HeightParams.fromOptions(ord);
        NativeImage normalImg = AutoPBRGenerator.generateNormal(albedoCopy,
            Options.materialNormalStrength[ord], invertNormal,
            Options.materialAutoPBRHeightGamma[ord], invertHeight,
            hp, Options.materialPomAOStrength[ord]);
        NativeImage roughImg = AutoPBRGenerator.generateRoughnessPreviewPercentile(albedoCopy,
            Options.materialAutoPBRRoughnessMin[ord], Options.materialAutoPBRRoughnessMax[ord],
            Options.materialPercentileCenter[ord], Options.materialPercentileSpread[ord], invertRough);
        NativeImage heightImg = AutoPBRGenerator.generateHeightPreview(albedoCopy,
            Options.materialAutoPBRHeightGamma[ord], invertHeight, hp);
        NativeImage aoImg = aoPreviewFromNormal(normalImg);
        NativeImage noiseImg = NoisePreviewGenerator.generate(160, ord);
        uploadPreview(albedoCopy, normalImg, roughImg, heightImg, aoImg, noiseImg);
    }

    private NativeImage aoPreviewFromNormal(NativeImage normal) {
        NativeImage out = new NativeImage(normal.getWidth(), normal.getHeight(), false);
        for (int y = 0; y < normal.getHeight(); y++) {
            for (int x = 0; x < normal.getWidth(); x++) {
                int pixel = normal.getColorArgb(x, y);
                int alpha = (pixel >>> 24) & 0xFF;
                int ao = pixel & 0xFF;
                out.setColorArgb(x, y, (alpha << 24) | (ao << 16) | (ao << 8) | ao);
            }
        }
        return out;
    }

    private NativeImage scaledAlbedo() {
        if (sourceAlbedo == null) {
            NativeImage blank = new NativeImage(96, 96, false);
            for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) blank.setColorArgb(x, y, 0xFF202020);
            return blank;
        }
        int fullW = sourceAlbedo.getWidth();
        int fullH = Math.min(sourceAlbedo.getHeight(), fullW);
        NativeImage out = new NativeImage(160, 160, false);
        for (int y = 0; y < 160; y++) {
            int sy = Math.min(fullH - 1, y * fullH / 160);
            for (int x = 0; x < 160; x++) {
                int sx = Math.min(fullW - 1, x * fullW / 160);
                out.setColorArgb(x, y, sourceAlbedo.getColorArgb(sx, sy));
            }
        }
        return out;
    }

    private void uploadPreview(NativeImage albedo, NativeImage normal, NativeImage rough, NativeImage height, NativeImage ao, NativeImage noise) {
        if (previewsRegistered && (previewAlbedoTex == null || previewNormalTex == null || previewRoughTex == null
                || previewHeightTex == null || previewAoTex == null || previewNoiseTex == null)) {
            previewsRegistered = false;
        }
        if (!previewsRegistered) {
            var texManager = MinecraftClient.getInstance().getTextureManager();
            previewAlbedoTex = new NativeImageBackedTexture(albedo); previewAlbedoTex.upload(); texManager.registerTexture(PREVIEW_ALBEDO_ID, previewAlbedoTex);
            previewNormalTex = new NativeImageBackedTexture(normal); previewNormalTex.upload(); texManager.registerTexture(PREVIEW_NORMAL_ID, previewNormalTex);
            previewRoughTex = new NativeImageBackedTexture(rough); previewRoughTex.upload(); texManager.registerTexture(PREVIEW_ROUGHNESS_ID, previewRoughTex);
            previewHeightTex = new NativeImageBackedTexture(height); previewHeightTex.upload(); texManager.registerTexture(PREVIEW_HEIGHT_ID, previewHeightTex);
            previewAoTex = new NativeImageBackedTexture(ao); previewAoTex.upload(); texManager.registerTexture(PREVIEW_AO_ID, previewAoTex);
            previewNoiseTex = new NativeImageBackedTexture(noise); previewNoiseTex.upload(); texManager.registerTexture(PREVIEW_NOISE_ID, previewNoiseTex);
            previewsRegistered = true;
            return;
        }
        previewAlbedoTex.setImage(albedo); previewAlbedoTex.upload();
        previewNormalTex.setImage(normal); previewNormalTex.upload();
        previewRoughTex.setImage(rough); previewRoughTex.upload();
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

    private record DRect(int dx, int dy, int x, int y, int w, int h) {}

    private final class CSlider extends ClickableWidget {
        private final String label;
        private final int min, max, stockDefault;
        private final IntFunction<Text> formatter;
        private final IntConsumer onChange;
        private final String disabledReason;
        private final Runnable onRelease;
        private final boolean fullWidth;
        private double value;
        private boolean dragging, precisionDragging;
        private double dragAnchorX, valueAtDragStart;

        CSlider(int x, int y, int w, int h, String label, int min, int max, int current, int stockDefault,
                IntFunction<Text> formatter, IntConsumer onChange, String disabledReason, Runnable onRelease, boolean fullWidth) {
            super(sx(x), sy(y), sw(w), sh(h), Text.literal(label));
            this.label = label;
            this.min = min;
            this.max = max;
            this.stockDefault = stockDefault;
            this.formatter = formatter;
            this.onChange = onChange;
            this.disabledReason = disabledReason;
            this.onRelease = onRelease;
            this.fullWidth = fullWidth;
            this.value = max == min ? 0.0 : (MathHelper.clamp(current, min, max) - min) / (double) (max - min);
            this.active = disabledReason == null;
        }

        private int current() {
            return max == min ? min : min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean enabled = disabledReason == null;
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), enabled ? ROW_FILL : ROW_DISABLED);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), enabled ? 0x70353736 : 0x60353736);
            int textColor = enabled ? TEXT_PRIMARY : TEXT_DISABLED;
            int q = designW(18);
            int qx = getX() + getWidth() - q - designW(8);
            int valueRight = disabledReason == null ? getX() + getWidth() - designW(10) : qx - designW(6);
            int labelX = getX() + designW(10);
            Text val = formatter.apply(current());
            int valueX = valueRight - textRenderer.getWidth(val);
            Text labelText = RadianceTheme.trimText(textRenderer, Text.literal(label), Math.max(1, valueX - labelX - designW(6)));
            context.drawText(textRenderer, labelText, labelX, getY() + designH(9), textColor, false);
            context.drawText(textRenderer, val, valueX, getY() + designH(9), textColor, false);
            int trackX = fullWidth ? designX(590) : getX() + designW(12);
            int trackW = fullWidth ? designW(345) : getWidth() - designW(56);
            int trackY = fullWidth ? getY() + (getHeight() - designH(4)) / 2 : getY() + getHeight() - designH(13);
            if (!fullWidth) trackX = getX() + designW(12);
            context.fill(trackX, trackY, trackX + trackW, trackY + designH(4), enabled ? TRACK : 0xFF242625);
            int fill = (int) Math.round(trackW * value);
            context.fill(trackX, trackY, trackX + fill, trackY + designH(4), enabled ? TEAL : 0xFF5F6462);
            int thumbW = designW(8);
            int thumbH = designH(18);
            int thumbX = MathHelper.clamp(trackX + fill - thumbW / 2, trackX, trackX + trackW - thumbW);
            context.fill(thumbX, trackY + designH(2) - thumbH / 2, thumbX + thumbW, trackY + designH(2) + thumbH / 2, enabled ? THUMB : TEXT_DISABLED);
            if (current() != stockDefault) context.fill(getX() + designW(4), getY() + designH(4), getX() + designW(8), getY() + getHeight() - designH(4), enabled ? ORANGE : TEXT_DISABLED);
            if (disabledReason != null) {
                int qy = getY() + (getHeight() - q) / 2;
                drawQuestion(context, qx, qy, q, q, disabledReason);
                if (isPointIn(mouseX, mouseY, qx, qy, q, q) || isMouseOver(mouseX, mouseY)) tooltipReason = disabledReason;
            } else if (isMouseOver(mouseX, mouseY)) {
                tooltipReason = controlTooltip(label);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (disabledReason != null || !isMouseOver(mouseX, mouseY)) return false;
            if (button == 0 && Screen.hasControlDown()) {
                MinecraftClient mc = MinecraftClient.getInstance();
                Screen parent = mc.currentScreen;
                mc.setScreen(new NumericSliderInputScreen(parent, Text.literal(label), current(), min, max, v -> {
                    setCurrent(v);
                    applyValue();
                    if (onRelease != null) onRelease.run();
                }));
                return true;
            }
            if (button == 0 && Screen.hasShiftDown()) {
                setCurrent(stockDefault);
                applyValue();
                if (onRelease != null) onRelease.run();
                return true;
            }
            if (button == 0 || button == 1) {
                dragging = true;
                precisionDragging = button == 1;
                dragAnchorX = mouseX;
                valueAtDragStart = value;
                updateFromMouse(mouseX, precisionDragging);
                RadianceTheme.beginSliderFocus(this);
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
            if (!dragging || (button != 0 && button != 1)) return false;
            updateFromMouse(mouseX, precisionDragging);
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (!dragging) return false;
            dragging = false;
            precisionDragging = false;
            RadianceTheme.endSliderFocus();
            if (onRelease != null) onRelease.run();
            return true;
        }

        private void updateFromMouse(double mouseX, boolean precision) {
            int trackX = fullWidth ? designX(590) : getX() + designW(12);
            int trackW = fullWidth ? designW(345) : getWidth() - designW(56);
            double next;
            if (precision) {
                next = valueAtDragStart + ((mouseX - dragAnchorX) / Math.max(1.0, trackW)) * 0.1;
            } else {
                next = (mouseX - trackX) / Math.max(1.0, trackW);
            }
            value = MathHelper.clamp(next, 0.0, 1.0);
            applyValue();
        }

        private void setCurrent(int v) {
            value = max == min ? 0.0 : (MathHelper.clamp(v, min, max) - min) / (double) (max - min);
        }

        private void applyValue() {
            onChange.accept(current());
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class CButton extends ClickableWidget {
        private final Runnable action;
        private final String disabledReason;
        private final BooleanSupplier enabledSupplier;

        CButton(int x, int y, int w, int h, String label, Runnable action) {
            this(x, y, w, h, label, action, () -> true, null);
        }

        CButton(int x, int y, int w, int h, String label, Runnable action, String disabledReason) {
            this(x, y, w, h, label, action, () -> disabledReason == null, disabledReason);
        }

        CButton(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier enabledSupplier, String disabledReason) {
            super(sx(x), sy(y), sw(w), sh(h), Text.literal(label));
            this.action = action;
            this.disabledReason = disabledReason;
            this.enabledSupplier = enabledSupplier;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean enabled = enabledSupplier.getAsBoolean();
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), enabled ? ROW_FILL : ROW_DISABLED);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), isHovered() && enabled ? TEAL : PANEL_BORDER);
            Text msg = RadianceTheme.trimText(textRenderer, getMessage(), getWidth() - designW(12));
            context.drawText(textRenderer, msg, getX() + (getWidth() - textRenderer.getWidth(msg)) / 2, getY() + (getHeight() - 8) / 2, enabled ? TEXT_PRIMARY : TEXT_DISABLED, false);
            if (!enabled && disabledReason != null && isMouseOver(mouseX, mouseY)) tooltipReason = disabledReason;
            if (enabled && isMouseOver(mouseX, mouseY)) tooltipReason = controlTooltip(getMessage().getString());
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !enabledSupplier.getAsBoolean() || !isMouseOver(mouseX, mouseY)) return false;
            action.run();
            return true;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class CToggle extends ClickableWidget {
        private final BooleanSupplier state;
        private final java.util.function.Consumer<Boolean> onChange;
        private final String disabledReason;

        CToggle(int x, int y, int w, int h, String label, BooleanSupplier state, java.util.function.Consumer<Boolean> onChange, String disabledReason) {
            super(sx(x), sy(y), sw(w), sh(h), Text.literal(label));
            this.state = state;
            this.onChange = onChange;
            this.disabledReason = disabledReason;
            this.active = disabledReason == null;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            boolean enabled = disabledReason == null;
            boolean on = state.getAsBoolean();
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), enabled ? ROW_FILL : ROW_DISABLED);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), enabled ? PANEL_BORDER : 0x60353736);
            int box = designW(16);
            int bx = getX() + designW(12);
            int by = getY() + (getHeight() - box) / 2;
            context.drawBorder(bx, by, box, box, enabled ? TEXT_SECONDARY : TEXT_DISABLED);
            if (on) context.fill(bx + 3, by + 3, bx + box - 3, by + box - 3, enabled ? TEAL : TEXT_DISABLED);
            int labelX = bx + box + designW(8);
            int labelRight = getX() + getWidth() - designW(10);
            if (disabledReason != null) labelRight -= designW(26);
            Text label = RadianceTheme.trimText(textRenderer, getMessage(), Math.max(1, labelRight - labelX));
            context.drawText(textRenderer, label, labelX, getY() + (getHeight() - 8) / 2, enabled ? TEXT_PRIMARY : TEXT_DISABLED, false);
            if (disabledReason != null) {
                int q = designW(18);
                int qx = getX() + getWidth() - q - designW(8);
                int qy = getY() + (getHeight() - q) / 2;
                drawQuestion(context, qx, qy, q, q, disabledReason);
                if (isMouseOver(mouseX, mouseY)) tooltipReason = disabledReason;
            } else if (isMouseOver(mouseX, mouseY)) {
                tooltipReason = controlTooltip(getMessage().getString());
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || disabledReason != null || !isMouseOver(mouseX, mouseY)) return false;
            onChange.accept(!state.getAsBoolean());
            return true;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class CDropdown extends ClickableWidget {
        private final String label;
        private final String[] values;
        private final IntConsumer onSelect;
        private final String disabledReason;
        private int selected;

        CDropdown(int x, int y, int w, int h, String label, String[] values, int selected, IntConsumer onSelect, String disabledReason) {
            super(sx(x), sy(y), sw(w), sh(h), Text.literal(label));
            this.label = label;
            this.values = values;
            this.selected = MathHelper.clamp(selected, 0, Math.max(0, values.length - 1));
            this.onSelect = onSelect;
            this.disabledReason = disabledReason;
            this.active = disabledReason == null;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            String value = values.length == 0 ? "" : values[selected];
            String display = "Category".equals(label) ? value : label + ": " + value;
            drawDropdownShell(context, getX(), getY(), getWidth(), getHeight(), display, disabledReason == null);
            if (disabledReason != null) {
                int q = designW(18);
                int qx = getX() + getWidth() - q - designW(28);
                int qy = getY() + (getHeight() - q) / 2;
                drawQuestion(context, qx, qy, q, q, disabledReason);
                if (isMouseOver(mouseX, mouseY)) tooltipReason = disabledReason;
            } else if (isMouseOver(mouseX, mouseY)) {
                tooltipReason = controlTooltip(label);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || disabledReason != null || !isMouseOver(mouseX, mouseY) || values.length == 0) return false;
            selected = (selected + 1) % values.length;
            onSelect.accept(selected);
            return true;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class DisabledCell extends ClickableWidget {
        private final String label;
        private final String value;
        private final String reason;

        DisabledCell(int x, int y, int w, int h, String label, String value, String reason) {
            super(sx(x), sy(y), sw(w), sh(h), Text.literal(label));
            this.label = label;
            this.value = value;
            this.reason = reason;
            this.active = false;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), ROW_DISABLED);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), 0x60353736);
            int q = designW(18);
            int qx = getX() + getWidth() - q - designW(8);
            int qy = getY() + (getHeight() - q) / 2;
            int valueRight = qx - designW(6);
            Text valueText = RadianceTheme.trimText(textRenderer, Text.literal(value), Math.max(1, getWidth() / 2));
            int valueX = value.isEmpty() ? valueRight : valueRight - textRenderer.getWidth(valueText);
            int labelX = getX() + designW(10);
            Text labelText = RadianceTheme.trimText(textRenderer, Text.literal(label), Math.max(1, valueX - labelX - designW(6)));
            context.drawText(textRenderer, labelText, labelX, getY() + designH(9), TEXT_DISABLED, false);
            if (!value.isEmpty()) context.drawText(textRenderer, valueText, valueX, getY() + designH(9), TEXT_DISABLED, false);
            drawQuestion(context, qx, qy, q, q, reason);
            if (isMouseOver(mouseX, mouseY)) tooltipReason = reason;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private final class ReadOnlyField extends ClickableWidget {
        ReadOnlyField(int x, int y, int w, int h, String text) {
            super(sx(x), sy(y), sw(w), sh(h), Text.literal(text));
            this.active = false;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), ROW_FILL);
            context.drawBorder(getX(), getY(), getWidth(), getHeight(), PANEL_BORDER);
            Text msg = RadianceTheme.trimText(textRenderer, getMessage(), getWidth() - designW(18));
            context.drawText(textRenderer, msg, getX() + designW(10), getY() + (getHeight() - 8) / 2, TEXT_PRIMARY, false);
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
