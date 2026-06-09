package com.radiance.client.gui;

import static net.minecraft.client.option.GameOptions.getGenericValueText;

import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.autopbr.AutoPbrTexturePicker;
import com.radiance.client.autopbr.AutoPbrUsageIndex;
import com.radiance.client.materiallab.MaterialBakePlan;
import com.radiance.client.materiallab.MaterialHistogram;
import com.radiance.client.materiallab.MaterialLabStore;
import com.radiance.client.materiallab.MaterialPresetCatalog;
import com.radiance.client.materiallab.MaterialRecipe;
import com.radiance.client.materiallab.MaterialRecipeCompiler;
import com.radiance.client.materiallab.MaterialUploadResult;
import com.radiance.client.option.Options;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class MaterialLabScreen extends Screen {
    private static final int HEADER_HEIGHT = 34;
    private static final int LEFT_WIDTH = 230;
    private static final int INSPECTOR_WIDTH = 348;
    private static final int BOTTOM_STRIP_HEIGHT = 88;
    private static final int PAD = 8;
    private static final int CONTROL_HEIGHT = 26;
    private static final int ROW_GAP = 6;
    private static final int PREVIEW_LANE_HEIGHT = 128;
    private static final int HISTOGRAM_HEIGHT = 58;
    private static final int MAIN_TITLE_HEIGHT = 58;

    private static final Option[] BLEND_SOURCES = {
        new Option("Pack + Generated + Flat", "pack_generated_flat"),
        new Option("Pack Channel", "pack"),
        new Option("Generated", "generated"),
        new Option("Flat", "flat")
    };
    private static final Option[] NORMAL_SOURCES = {
        new Option("Pack + Generated", "pack_generated"),
        new Option("Pack Normal", "pack"),
        new Option("Generated From Height", "generated"),
        new Option("Detail Only", "detail_only")
    };
    private static final Option[] METAL_SOURCES = {
        new Option("Pack LabPBR", "pack"),
        new Option("Flat Scalar", "flat"),
        new Option("Measured Preset", "radser_measured"),
        new Option("LabPBR Metal Code", "labpbr_code"),
        new Option("Generated Mask", "generated_mask")
    };
    private static final Option[] EMISSION_MODES = {
        new Option("None", "none"),
        new Option("Spec Alpha", "spec_alpha"),
        new Option("Albedo Red", "albedo_red"),
        new Option("Albedo Green", "albedo_green"),
        new Option("Albedo Blue", "albedo_blue"),
        new Option("Hue", "hue"),
        new Option("Saturation", "saturation"),
        new Option("Value", "value"),
        new Option("Luminance", "luminance"),
        new Option("Whole Texture", "whole_texture"),
        new Option("Flat", "flat")
    };
    private static final Option[] ROUGHNESS_GENERATORS = {
        new Option("Luminance", "luminance"),
        new Option("Inverse Luminance", "inverse_luminance"),
        new Option("Edges", "edges"),
        new Option("Cavity", "cavity"),
        new Option("Saturation", "saturation"),
        new Option("Height Curvature", "height_curvature"),
        new Option("Crack Detail", "crack_detail")
    };
    private static final Option[] HEIGHT_GENERATORS = {
        new Option("Luminance", "luminance"),
        new Option("Inverse Luminance", "inverse_luminance"),
        new Option("Edge / Cavity", "edge_cavity"),
        new Option("Distance / Bevel", "distance_bevel"),
        new Option("Shape From Shading", "shape_from_shading")
    };
    private static final Option[] METAL_MASK_SOURCES = {
        new Option("Flat", "flat"),
        new Option("Pack Code", "pack_code"),
        new Option("Luminance", "luminance"),
        new Option("Saturation", "saturation"),
        new Option("Albedo Red", "albedo_red"),
        new Option("Albedo Green", "albedo_green"),
        new Option("Albedo Blue", "albedo_blue"),
        new Option("Edge Mask", "edge_mask")
    };
    private static final Option[] LABPBR_METAL_CODES = {
        new Option("Iron / Generic Metal 238", "238"),
        new Option("Gold 230", "230"),
        new Option("Copper 231", "231"),
        new Option("Aluminum 232", "232"),
        new Option("Silver 233", "233"),
        new Option("Lead 234", "234"),
        new Option("Platinum 235", "235"),
        new Option("Nickel 236", "236"),
        new Option("Tin 237", "237")
    };
    private static final Option[] POROSITY_MODES = {
        new Option("Preserve", "preserve"),
        new Option("Porosity", "porosity"),
        new Option("SSS", "sss")
    };
    private static final Option[] POROSITY_SOURCES = {
        new Option("Luminance", "luminance"),
        new Option("Inverse Luminance", "inverse_luminance"),
        new Option("Saturation", "saturation"),
        new Option("Cavity", "cavity"),
        new Option("Height Influence", "height_influence")
    };
    private static final Option[] NORMAL_COMBINE_MODES = {
        new Option("Overlay Generated", "overlay_generated"),
        new Option("Replace", "replace"),
        new Option("Add Detail", "add_detail"),
        new Option("Preserve Pack Convention", "preserve_pack")
    };
    private static final Option[] NORMAL_KERNELS = {
        new Option("Sobel", "sobel"),
        new Option("Scharr", "scharr")
    };
    private static final Option[] NORMAL_ORIENTATION = {
        new Option("OpenGL", "opengl"),
        new Option("DirectX", "directx"),
        new Option("Flip Green", "flip_green")
    };
    private static final Option[] VOLUME_MODES = {
        new Option("Solid Volume", "solid_volume"),
        new Option("Thin Glass", "thin_glass")
    };
    private static final Option[] THICKNESS_SOURCES = {
        new Option("Flat", "flat"),
        new Option("Albedo Alpha", "albedo_alpha"),
        new Option("Generated Luminance", "generated_luminance"),
        new Option("Pack Derived", "pack_derived")
    };
    private static final Option[] DIFFUSE_MODELS = {
        new Option("Global", "global"),
        new Option("EON", "eon"),
        new Option("VMF", "vmf")
    };

    private final Screen parent;
    private Identifier selectedSprite;
    private MaterialRecipe recipe = MaterialRecipe.defaults();
    private MaterialRecipe savedSnapshot = MaterialRecipe.defaults();
    private Channel activeChannel = Channel.ROUGHNESS;
    private String status = "Saved";
    private final ArrayDeque<MaterialRecipe> undo = new ArrayDeque<>();
    private final ArrayDeque<MaterialRecipe> redo = new ArrayDeque<>();
    private int recipeRevision;
    private int cachedRevision = -1;
    private int cachedSpriteId = -1;
    private MaterialBakePlan cachedPlan;
    private MaterialUploadResult lastUpload;

    public MaterialLabScreen(Screen parent) {
        this(parent, null);
    }

    public MaterialLabScreen(Screen parent, Identifier initialSprite) {
        super(Text.literal("Material Lab"));
        this.parent = parent;
        this.selectedSprite = initialSprite;
    }

    @Override
    protected void init() {
        super.init();
        if (selectedSprite == null
            || !TextureArrayBridge.sortedSpriteIds.contains(selectedSprite)
            || !AutoPbrTextureCatalog.isEditableSprite(selectedSprite)) {
            selectedSprite = AutoPbrTextureCatalog.fallbackSprite();
        }
        loadRecipe();
        rebuildWidgets();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void loadRecipe() {
        recipe = MaterialLabStore.loadRecipe(MinecraftClient.getInstance(), selectedSprite);
        savedSnapshot = recipe.copy();
        markPlanDirty();
        lastUpload = null;
        if (recipe.isDefaultIntent()
            && !MaterialLabStore.currentProfileExists(MinecraftClient.getInstance())
            && MaterialLabStore.hasAnyProfiles(MinecraftClient.getInstance())) {
            status = "Profile Review Needed";
        } else {
            status = recipe.isDefaultIntent() ? "Generated/Pack Baseline" : "Loaded";
        }
    }

    private void markPlanDirty() {
        recipeRevision++;
        cachedPlan = null;
        cachedRevision = -1;
        cachedSpriteId = -1;
    }

    private MaterialBakePlan currentPlan(int spriteId) {
        if (cachedPlan == null || cachedSpriteId != spriteId || cachedRevision != recipeRevision) {
            cachedPlan = MaterialRecipeCompiler.evaluate(spriteId, recipe);
            cachedSpriteId = spriteId;
            cachedRevision = recipeRevision;
        }
        return cachedPlan;
    }

    private void rebuildWidgets() {
        SelectionDropdownWidget.clearInstances();
        this.clearChildren();
        addOpacitySlider();

        int leftX = PAD;
        int leftBottom = this.height - BOTTOM_STRIP_HEIGHT - PAD;
        addButton(leftX + 10, leftBottom - 72, 88, CONTROL_HEIGHT, "Prev", () -> stepSprite(-1));
        addButton(leftX + 106, leftBottom - 72, 88, CONTROL_HEIGHT, "Next", () -> stepSprite(1));
        addButton(leftX + 10, leftBottom - 44, LEFT_WIDTH - 20, CONTROL_HEIGHT, "Pick Crosshair",
            () -> selectSprite(AutoPbrTexturePicker.pick(MinecraftClient.getInstance())));

        int mainX = mainX();
        int mainW = mainW();
        int actionY = this.height - BOTTOM_STRIP_HEIGHT - 32;
        int actionW = Math.max(72, (mainW - ROW_GAP * 4) / 5);
        addButton(mainX, actionY, actionW, CONTROL_HEIGHT, "Apply", this::applyPreview);
        addButton(mainX + (actionW + ROW_GAP), actionY, actionW, CONTROL_HEIGHT, "Save", this::saveRecipe);
        addButton(mainX + (actionW + ROW_GAP) * 2, actionY, actionW, CONTROL_HEIGHT, "Revert", this::revertRecipe);
        addButton(mainX + (actionW + ROW_GAP) * 3, actionY, actionW, CONTROL_HEIGHT, "Reset Channel",
            this::resetActiveChannel);
        addButton(mainX + (actionW + ROW_GAP) * 4, actionY, actionW, CONTROL_HEIGHT, "Reset Sprite",
            this::resetSprite);

        int controlsX = mainX + 10;
        int controlsY = controlsTop();
        int controlsW = Math.max(120, mainW - 20);
        switch (activeChannel) {
            case ALBEDO -> addAlbedoControls(controlsX, controlsY, controlsW);
            case ROUGHNESS -> addRoughnessControls(controlsX, controlsY, controlsW);
            case METAL -> addMetalControls(controlsX, controlsY, controlsW);
            case POROSITY -> addPorosityControls(controlsX, controlsY, controlsW);
            case HEIGHT -> addHeightControls(controlsX, controlsY, controlsW);
            case NORMAL -> addNormalControls(controlsX, controlsY, controlsW);
            case EMISSION -> addEmissionControls(controlsX, controlsY, controlsW);
            case TRANSMISSION -> addTransmissionControls(controlsX, controlsY, controlsW);
            case ADVANCED -> addAdvancedControls(controlsX, controlsY, controlsW);
        }
    }

    private void addAlbedoControls(int x, int y, int w) {
        addButton(x, y, Math.min(180, w), CONTROL_HEIGHT, "Pick Crosshair",
            () -> selectSprite(AutoPbrTexturePicker.pick(MinecraftClient.getInstance())));
        addButton(x + Math.min(188, w / 2), y, Math.min(180, w), CONTROL_HEIGHT, "Use Pack Baseline",
            this::usePackBaseline);
    }

    private void addRoughnessControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addOptionDropdown(x, y, colW * 2 + ROW_GAP, "Source", BLEND_SOURCES, recipe.roughnessSource, option ->
            mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessSource = option.value();
                r.generateRoughness = option.value().contains("generated");
            }, true, true));
        addButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT,
            recipe.roughnessInvert ? "Invert On" : "Invert Off",
            () -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessInvert = !r.roughnessInvert;
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addOptionDropdown(x, y, colW * 2 + ROW_GAP, "Generator", ROUGHNESS_GENERATORS,
            recipe.roughnessGeneratorMode, option -> mutate(r -> {
                r.roughnessOverride = true;
                r.generateRoughness = true;
                r.roughnessGeneratorMode = option.value();
            }, true, true));
        addButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT,
            recipe.roughnessAutoRange ? "Auto Range On" : "Auto Range Off",
            () -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessAutoRange = !r.roughnessAutoRange;
                if (r.roughnessAutoRange) {
                    r.roughnessBlackPoint = 0.04f;
                    r.roughnessWhitePoint = 0.96f;
                    r.roughnessMidpoint = 0.50f;
                }
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Flat Rough", 0, 100, recipe.roughness, 0.55f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughness = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Generated", 0, 100, recipe.roughnessGeneratedBlend, 0.65f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.generateRoughness = true;
                r.roughnessGeneratedBlend = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Flat Blend", 0, 100, recipe.roughnessFlatBlend, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessFlatBlend = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Min", 0, 100, recipe.roughnessMin, 0.04f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessMin = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Max", 0, 100, recipe.roughnessMax, 0.96f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessMax = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Gamma", 20, 400, recipe.roughnessGamma, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessGamma = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Contrast", 0, 200, recipe.roughnessContrast, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessContrast = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Edge Influence", 0, 200, recipe.roughnessEdge, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.generateRoughness = true;
                r.roughnessEdge = v;
            }, false, false));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "Smoothing", 0, 4, recipe.roughnessSmoothing, 0,
            value -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessSmoothing = value;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Black Point", 0, 100, recipe.roughnessBlackPoint, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessBlackPoint = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Midpoint", 5, 95, recipe.roughnessMidpoint, 0.5f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessMidpoint = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "White Point", 0, 100, recipe.roughnessWhitePoint, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessWhitePoint = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Broad Detail", 0, 200, recipe.roughnessBroadDetail, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessBroadDetail = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Mid Detail", 0, 200, recipe.roughnessMidDetail, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessMidDetail = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Fine Detail", 0, 200, recipe.roughnessFineDetail, 1.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessFineDetail = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "Blur Radius", 0, 4, recipe.roughnessBlurRadius, 0,
            value -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessBlurRadius = value;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Sharpen", 0, 200, recipe.roughnessSharpen, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessSharpen = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Denoise", 0, 100, recipe.roughnessDenoise, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessDenoise = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Wear", 0, 100, recipe.roughnessWear, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessWear = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Polish", 0, 100, recipe.roughnessPolish, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessPolish = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Wetness", 0, 100, recipe.roughnessWetness, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessWetness = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Edge Rough", 0, 100, recipe.roughnessEdgeRoughness, 0.0f,
            v -> mutate(r -> {
                r.roughnessOverride = true;
                r.roughnessEdgeRoughness = v;
            }, false, false));
    }

    private void addMetalControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addOptionDropdown(x, y, colW, "Source", METAL_SOURCES, recipe.metalMode, option ->
            mutate(r -> applyMetalSource(r, option.value()), true, true));
        List<MaterialPresetCatalog.Preset> metals = MaterialPresetCatalog.metals();
        addPresetDropdown(x + colW + ROW_GAP, y, colW * 2 + ROW_GAP, "Measured Metal", metals,
            recipe.metalPreset, preset -> mutate(r -> applyMetalPreset(r, preset), true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addOptionDropdown(x, y, colW, "Mask Source", METAL_MASK_SOURCES, recipe.metalMaskSource, option ->
            mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskSource = option.value();
                if ("generated_mask".equals(r.metalMode)) r.metallic = Math.max(r.metallic, 1.0f);
            }, true, true));
        addOptionDropdown(x + colW + ROW_GAP, y, colW * 2 + ROW_GAP, "LabPBR Code", LABPBR_METAL_CODES,
            Integer.toString(recipe.labPbrMetalCode >= 230 ? recipe.labPbrMetalCode : 238),
            option -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMode = "labpbr_code";
                r.labPbrMetalCode = Integer.parseInt(option.value());
                r.metallic = 1.0f;
                r.f0Override = true;
                r.f0 = Math.max(r.f0, 0.5f);
                r.conductorF0RgbOverride = false;
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Metallic", 0, 100, recipe.metallic, 0.0f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMode = "flat";
                r.metallic = v;
                if (v < 0.5f) r.conductorF0RgbOverride = false;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Scalar F0", 0, 99, recipe.f0, 0.04f,
            v -> mutate(r -> {
                r.f0Override = true;
                r.f0 = v;
                r.conductorF0RgbOverride = false;
            }, false, false));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "LabPBR Code", 230, 255,
            recipe.labPbrMetalCode >= 230 ? recipe.labPbrMetalCode : 238, 238,
            value -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMode = "labpbr_code";
                r.metalMaskSource = "flat";
                r.labPbrMetalCode = value;
                r.metallic = 1.0f;
                r.conductorF0RgbOverride = false;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Mask Threshold", 0, 100, recipe.metalMaskThreshold, 0.5f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskThreshold = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Softness", 0, 100, recipe.metalMaskSoftness, 0.1f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskSoftness = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Clamp Max", 0, 100, recipe.metalMaskClampMax, 1.0f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskClampMax = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "Dilate", 0, 4, recipe.metalMaskDilate, 0,
            value -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskDilate = value;
            }, false, false));
        addIntSlider(x + colW + ROW_GAP, y, colW, "Erode", 0, 4, recipe.metalMaskErode, 0,
            value -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskErode = value;
            }, false, false));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "Despeckle", 0, 4, recipe.metalMaskDespeckle, 0,
            value -> mutate(r -> {
                r.metallicOverride = true;
                r.metalMaskDespeckle = value;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "F0 Red", 0, 99, recipe.conductorF0R, 0.04f,
            v -> mutate(r -> {
                r.conductorF0RgbOverride = true;
                r.f0Override = true;
                r.metallicOverride = true;
                r.metallic = 1.0f;
                r.conductorF0R = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "F0 Green", 0, 99, recipe.conductorF0G, 0.04f,
            v -> mutate(r -> {
                r.conductorF0RgbOverride = true;
                r.f0Override = true;
                r.metallicOverride = true;
                r.metallic = 1.0f;
                r.conductorF0G = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "F0 Blue", 0, 99, recipe.conductorF0B, 0.04f,
            v -> mutate(r -> {
                r.conductorF0RgbOverride = true;
                r.f0Override = true;
                r.metallicOverride = true;
                r.metallic = 1.0f;
                r.conductorF0B = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Oxide", 0, 100, recipe.oxideAmount, 0.0f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.oxideAmount = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Tarnish Rough", 0, 100, recipe.oxideRoughnessInfluence, 0.0f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.oxideRoughnessInfluence = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Patina Bias", 0, 100, recipe.oxideColorBias, 0.0f,
            v -> mutate(r -> {
                r.metallicOverride = true;
                r.oxideColorBias = v;
            }, false, false));
    }

    private void addPorosityControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addOptionDropdown(x, y, colW, "Mode", POROSITY_MODES, recipe.porosityMode, option ->
            mutate(r -> {
                r.porosityMode = option.value();
                r.porosityOverride = !"preserve".equals(option.value());
            }, true, true));
        addOptionDropdown(x + colW + ROW_GAP, y, colW, "Generator", POROSITY_SOURCES,
            recipe.porositySource, option -> mutate(r -> {
                r.porosityOverride = !"preserve".equals(r.porosityMode);
                r.porositySource = option.value();
            }, true, true));
        addButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT,
            recipe.porosityInvert ? "Invert On" : "Invert Off",
            () -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityInvert = !r.porosityInvert;
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Porosity Amt", 0, 100, recipe.porosityAmount, 0.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                if ("preserve".equals(r.porosityMode)) r.porosityMode = "porosity";
                r.porosityAmount = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Cavity", 0, 100, recipe.porosityCavityInfluence, 0.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                if ("preserve".equals(r.porosityMode)) r.porosityMode = "porosity";
                r.porosityCavityInfluence = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Wet Coupling", 0, 100, recipe.porosityWetnessCoupling, 0.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                if ("preserve".equals(r.porosityMode)) r.porosityMode = "porosity";
                r.porosityWetnessCoupling = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Black Point", 0, 100, recipe.porosityBlackPoint, 0.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityBlackPoint = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Midpoint", 5, 95, recipe.porosityMidpoint, 0.5f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityMidpoint = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "White Point", 0, 100, recipe.porosityWhitePoint, 1.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityWhitePoint = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "SSS Strength", 0, 100, recipe.porositySssStrength, 0.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityMode = "sss";
                r.porositySssStrength = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "SSS Radius", 0, 100, recipe.porositySssRadius, 0.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityMode = "sss";
                r.porositySssRadius = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Thickness", 0, 100, recipe.porositySssThickness, 0.5f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porosityMode = "sss";
                r.porositySssThickness = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Tint R", 0, 100, recipe.porositySssTintR, 1.0f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porositySssTintR = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Tint G", 0, 100, recipe.porositySssTintG, 0.75f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porositySssTintG = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Tint B", 0, 100, recipe.porositySssTintB, 0.55f,
            v -> mutate(r -> {
                r.porosityOverride = true;
                r.porositySssTintB = v;
            }, false, false));
    }

    private void addHeightControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addOptionDropdown(x, y, colW * 2 + ROW_GAP, "Source", BLEND_SOURCES, recipe.heightSource, option ->
            mutate(r -> {
                r.heightOverride = true;
                r.heightSource = option.value();
                r.generateHeight = option.value().contains("generated");
            }, true, true));
        addButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT,
            recipe.invertHeight ? "Invert On" : "Invert Off",
            () -> mutate(r -> {
                r.heightOverride = true;
                r.invertHeight = !r.invertHeight;
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addOptionDropdown(x, y, colW * 2 + ROW_GAP, "Generator", HEIGHT_GENERATORS,
            recipe.heightGeneratorMode, option -> mutate(r -> {
                r.heightOverride = true;
                r.generateHeight = true;
                r.heightGeneratorMode = option.value();
            }, true, true));
        addButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT,
            recipe.heightAutoRange ? "Auto Norm On" : "Auto Norm Off",
            () -> mutate(r -> {
                r.heightOverride = true;
                r.heightAutoRange = !r.heightAutoRange;
                if (r.heightAutoRange) {
                    r.heightBlackPoint = 0.03f;
                    r.heightWhitePoint = 0.97f;
                    r.heightMidpoint = 0.50f;
                }
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Scale", 0, 100, recipe.heightScale, 0.25f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightScale = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Generated", 0, 100, recipe.heightGeneratedBlend, 0.75f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.generateHeight = true;
                r.heightGeneratedBlend = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Flat Blend", 0, 100, recipe.heightFlatBlend, 0.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightFlatBlend = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Flat Height", 0, 100, recipe.heightFlat, 0.5f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightFlat = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Gamma", 20, 400, recipe.heightGamma, 1.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightGamma = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Offset", -100, 100, recipe.heightOffset, 0.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightOffset = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Min", 0, 100, recipe.heightMin, 0.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightMin = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Max", 0, 100, recipe.heightMax, 1.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightMax = v;
            }, false, false));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "Smoothing", 0, 4, recipe.heightSmoothing, 0,
            value -> mutate(r -> {
                r.heightOverride = true;
                r.heightSmoothing = value;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Black Point", 0, 100, recipe.heightBlackPoint, 0.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightBlackPoint = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Midpoint", 5, 95, recipe.heightMidpoint, 0.5f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightMidpoint = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "White Point", 0, 100, recipe.heightWhitePoint, 1.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightWhitePoint = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "Blur Radius", 0, 4, recipe.heightBlurRadius, 0,
            value -> mutate(r -> {
                r.heightOverride = true;
                r.heightBlurRadius = value;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Sharpen", 0, 200, recipe.heightSharpen, 0.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightSharpen = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Denoise", 0, 100, recipe.heightDenoise, 0.0f,
            v -> mutate(r -> {
                r.heightOverride = true;
                r.heightDenoise = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "Erode", 0, 4, recipe.heightErode, 0,
            value -> mutate(r -> {
                r.heightOverride = true;
                r.heightErode = value;
            }, false, false));
        addIntSlider(x + colW + ROW_GAP, y, colW, "Dilate", 0, 4, recipe.heightDilate, 0,
            value -> mutate(r -> {
                r.heightOverride = true;
                r.heightDilate = value;
            }, false, false));
        addDisabledButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT,
            "Displace Cap " + Options.displacementDepthCapPercent + "%");
    }

    private void addNormalControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addOptionDropdown(x, y, colW * 2 + ROW_GAP, "Source", NORMAL_SOURCES, recipe.normalSource, option ->
            mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalSource = option.value();
                r.generateNormal = option.value().equals("generated");
            }, true, true));
        addOptionDropdown(x + (colW + ROW_GAP) * 2, y, colW, "Orientation",
            NORMAL_ORIENTATION, recipe.normalOrientation, option -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalOrientation = option.value();
                r.flipGreen = "flip_green".equals(option.value()) || "directx".equals(option.value());
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addOptionDropdown(x, y, colW, "Combine", NORMAL_COMBINE_MODES, recipe.normalCombineMode, option ->
            mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalCombineMode = option.value();
            }, true, true));
        addOptionDropdown(x + colW + ROW_GAP, y, colW, "Kernel", NORMAL_KERNELS,
            recipe.normalKernelMode, option -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalKernelMode = option.value();
                r.generateNormal = true;
            }, true, true));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "Radius", 1, 4,
            recipe.normalGeneratorRadius, 1,
            value -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.generateNormal = true;
                r.normalGeneratorRadius = value;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Overall", 0, 300, recipe.normalStrength, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalStrength = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Pack Strength", 0, 300, recipe.normalPackStrength, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalPackStrength = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Generated Strength", 0, 300,
            recipe.normalGeneratedStrength, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.generateNormal = true;
                r.normalGeneratedStrength = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Generated Blend", 0, 100, recipe.normalGeneratedBlend, 0.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.generateNormal = v > 0.0f;
                r.normalGeneratedBlend = v;
            }, false, false));
        addIntSlider(x + colW + ROW_GAP, y, colW, "Smoothing", 0, 4, recipe.normalSmoothing, 0,
            value -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalSmoothing = value;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Detail", 0, 300,
            recipe.detailNormalStrength, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.detailNormalStrength = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "X Strength", 0, 300, recipe.normalXStrength, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalXStrength = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Y Strength", 0, 300, recipe.normalYStrength, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalYStrength = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Detail Freq", 0, 400,
            recipe.normalDetailFrequency, 1.0f,
            v -> mutate(r -> {
                r.normalStrengthOverride = true;
                r.normalDetailFrequency = v;
            }, false, false));
    }

    private void addEmissionControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addOptionDropdown(x, y, colW, "Mask", EMISSION_MODES, recipe.emissionMode, option ->
            mutate(r -> {
                r.emissionMode = option.value();
                r.emissionOverride = !"none".equals(option.value());
            }, true, true));
        addButton(x + colW + ROW_GAP, y, colW, CONTROL_HEIGHT,
            recipe.emissionInvert ? "Invert On" : "Invert Off",
            () -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionInvert = !r.emissionInvert;
            }, true, true));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "Nits", 0, 4000,
            Math.round(recipe.emissionNits), 0,
            value -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionNits = value;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Gain", 0, 100, recipe.emissionGain, 0.0f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionGain = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Low", 0, 100, recipe.emissionThresholdLow, 0.75f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionThresholdLow = v;
                r.emissionThreshold = (r.emissionThresholdLow + r.emissionThresholdHigh) * 0.5f;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "High", 0, 100, recipe.emissionThresholdHigh, 0.90f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionThresholdHigh = v;
                r.emissionThreshold = (r.emissionThresholdLow + r.emissionThresholdHigh) * 0.5f;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Softness", 0, 100, recipe.emissionSoftness, 0.08f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionSoftness = v;
            }, false, false));
        addIntSlider(x + colW + ROW_GAP, y, colW, "Dilate", 0, 4, recipe.emissionDilate, 0,
            value -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionDilate = value;
            }, false, false));
        addIntSlider(x + (colW + ROW_GAP) * 2, y, colW, "Erode", 0, 4, recipe.emissionErode, 0,
            value -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionErode = value;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "Despeckle", 0, 4, recipe.emissionDespeckle, 0,
            value -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionDespeckle = value;
            }, false, false));
        addIntSlider(x + colW + ROW_GAP, y, colW, "Blur Radius", 0, 4, recipe.emissionBlurRadius, 0,
            value -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionBlurRadius = value;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Tint R", 0, 100, recipe.emissionTintR, 1.0f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionTintR = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Tint G", 0, 100, recipe.emissionTintG, 0.75f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionTintG = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Tint B", 0, 100, recipe.emissionTintB, 0.45f,
            v -> mutate(r -> {
                r.emissionOverride = true;
                r.emissionTintB = v;
            }, false, false));
        addDisabledButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT, "Pulse/Flicker Roadmap");
    }

    private void addTransmissionControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        List<MaterialPresetCatalog.Preset> dielectrics = MaterialPresetCatalog.dielectrics();
        addPresetDropdown(x, y, colW * 2 + ROW_GAP, "Dielectric Preset", dielectrics,
            recipe.dielectricPreset, preset -> mutate(r -> applyDielectricPreset(r, preset), true, true));
        addButton(x + (colW + ROW_GAP) * 2, y, colW, CONTROL_HEIGHT, "Use Opaque Pack",
            () -> mutate(r -> {
                r.iorOverride = false;
                r.transmissionOverride = false;
                r.transmission = 0.0f;
                r.dielectricPreset = "";
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "IOR", 100, 300, recipe.ior, 1.45f,
            v -> mutate(r -> {
                r.iorOverride = true;
                r.ior = v;
                if (!r.conductorF0RgbOverride) {
                    r.f0Override = true;
                    r.f0 = MaterialRecipe.f0FromIor(r.ior);
                }
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Transmission", 0, 100, recipe.transmission, 0.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.transmission = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Refract Rough", 0, 100, recipe.refractionRoughness, 0.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.refractionRoughness = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Absorb R", 0, 100, recipe.absorptionR, 0.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.absorptionR = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Absorb G", 0, 100, recipe.absorptionG, 0.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.absorptionG = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Absorb B", 0, 100, recipe.absorptionB, 0.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.absorptionB = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "Absorb Dist", 1, 64, Math.round(recipe.absorptionDistance), 16,
            value -> mutate(r -> {
                r.transmissionOverride = true;
                r.absorptionDistance = value;
            }, false, false));
        addOptionDropdown(x + colW + ROW_GAP, y, colW, "Volume", VOLUME_MODES,
            recipe.transmissionVolumeMode, option -> mutate(r -> {
                r.transmissionOverride = true;
                r.transmissionVolumeMode = option.value();
            }, true, true));
        addOptionDropdown(x + (colW + ROW_GAP) * 2, y, colW, "Thickness", THICKNESS_SOURCES,
            recipe.thicknessSource, option -> mutate(r -> {
                r.transmissionOverride = true;
                r.thicknessSource = option.value();
            }, true, true));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Thick Amt", 0, 100, recipe.thicknessAmount, 0.5f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.thicknessAmount = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Thick Min", 0, 100, recipe.thicknessMin, 0.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.thicknessMin = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Thick Max", 0, 100, recipe.thicknessMax, 1.0f,
            v -> mutate(r -> {
                r.transmissionOverride = true;
                r.thicknessMax = v;
            }, false, false));
    }

    private void addAdvancedControls(int x, int y, int w) {
        int colW = columnWidth(w, 3);
        addSlider(x, y, colW, "Anisotropic", 0, 100, recipe.anisotropic, 0.0f,
            v -> mutate(r -> {
                r.anisotropicOverride = true;
                r.anisotropic = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Coat", 0, 100, recipe.coatWeight, 0.0f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatWeight = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Coat Rough", 0, 100, recipe.coatRoughness, 0.03f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatRoughness = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Sheen", 0, 100, recipe.sheenWeight, 0.0f,
            v -> mutate(r -> {
                r.sheenOverride = true;
                r.sheenWeight = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Sheen Tint", 0, 100, recipe.sheenTint, 0.0f,
            v -> mutate(r -> {
                r.sheenOverride = true;
                r.sheenTint = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Sheen Rough", 0, 100, recipe.sheenRoughness, 0.5f,
            v -> mutate(r -> {
                r.sheenOverride = true;
                r.sheenRoughness = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Aniso Rot", 0, 100, recipe.anisotropicRotation, 0.0f,
            v -> mutate(r -> {
                r.anisotropicOverride = true;
                r.anisotropicRotation = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Coat IOR", 100, 300, recipe.coatIor, 1.5f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatIor = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Coat Mask", 0, 100, recipe.coatMask, 1.0f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatMask = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Coat Tint R", 0, 100, recipe.coatTintR, 1.0f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatTintR = v;
            }, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Coat Tint G", 0, 100, recipe.coatTintG, 1.0f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatTintG = v;
            }, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Coat Tint B", 0, 100, recipe.coatTintB, 1.0f,
            v -> mutate(r -> {
                r.coatOverride = true;
                r.coatTintB = v;
            }, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addIntSlider(x, y, colW, "UV Scale", 1, 800, Math.round(recipe.uvScale * 100.0f), 100,
            value -> mutate(r -> r.uvScale = value / 100.0f, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "UV Offset", -100, 100, recipe.uvOffset, 0.0f,
            v -> mutate(r -> r.uvOffset = v, false, false));
        addSlider(x + (colW + ROW_GAP) * 2, y, colW, "Filter Rad", 0, 100, recipe.filterRadius / 16.0f, 0.0f,
            v -> mutate(r -> r.filterRadius = v * 16.0f, false, false));
        y += CONTROL_HEIGHT + ROW_GAP;
        addSlider(x, y, colW, "Mip Bias", -100, 100, recipe.mipBias / 8.0f, 0.0f,
            v -> mutate(r -> r.mipBias = v * 8.0f, false, false));
        addSlider(x + colW + ROW_GAP, y, colW, "Displace", 0, 400, recipe.displacementScale, 1.0f,
            v -> mutate(r -> {
                r.displacementOverride = true;
                r.displacementScale = v;
            }, false, false));
        addOptionDropdown(x + (colW + ROW_GAP) * 2, y, colW, "Diffuse", DIFFUSE_MODELS,
            recipe.diffuseModel, option -> mutate(r -> r.diffuseModel = option.value(), true, true));
    }

    private void addButton(int x, int y, int w, int h, String label, Runnable action) {
        this.addDrawableChild(new MaterialLabButtonWidget(x, y, w, h, Text.literal(label), action));
    }

    private void addDisabledButton(int x, int y, int w, int h, String label) {
        MaterialLabButtonWidget button = new MaterialLabButtonWidget(x, y, w, h,
            Text.literal("Locked: " + label), () -> status = "Roadmap: " + label);
        button.active = false;
        this.addDrawableChild(button);
    }

    private void addOpacitySlider() {
        int sliderW = opacitySliderWidth();
        int x = opacitySliderX();
        int y = (HEADER_HEIGHT - CONTROL_HEIGHT) / 2;
        ResettableSliderWidget slider = new ResettableSliderWidget(x, y, sliderW, CONTROL_HEIGHT, 35, 100,
            Options.materialLabOpacityPercent, 82,
            v -> getGenericValueText(Text.literal("Menu Opacity"),
                Text.literal(String.format(Locale.ROOT, "%.2f", v / 100.0f))),
            v -> Options.setMaterialLabOpacityPercent(v, false));
        slider.setOnRelease(() -> Options.setMaterialLabOpacityPercent(Options.materialLabOpacityPercent, true));
        this.addDrawableChild(slider);
    }

    private void addSlider(int x, int y, int w, String label, int min, int max, float value, float stockDefault,
                           Consumer<Float> onChange) {
        int current = Math.round(value * 100.0f);
        int stock = Math.round(stockDefault * 100.0f);
        ResettableSliderWidget slider = new ResettableSliderWidget(x, y, w, CONTROL_HEIGHT, min, max,
            MathHelper.clamp(current, min, max), MathHelper.clamp(stock, min, max),
            v -> getGenericValueText(Text.literal(label), Text.literal(formatSliderValue(v, min, max))),
            v -> onChange.accept(v / 100.0f));
        slider.setOnRelease(this::applyPreview);
        this.addDrawableChild(slider);
    }

    private void addIntSlider(int x, int y, int w, String label, int min, int max, int value, int stockDefault,
                              IntConsumer onChange) {
        ResettableSliderWidget slider = new ResettableSliderWidget(x, y, w, CONTROL_HEIGHT, min, max,
            MathHelper.clamp(value, min, max), MathHelper.clamp(stockDefault, min, max),
            v -> getGenericValueText(Text.literal(label), Text.literal(Integer.toString(v))),
            onChange::accept);
        slider.setOnRelease(this::applyPreview);
        this.addDrawableChild(slider);
    }

    private void addOptionDropdown(int x, int y, int w, String label, Option[] options, String current,
                                   Consumer<Option> onSelect) {
        int index = optionIndex(options, current);
        String[] labels = new String[options.length];
        for (int i = 0; i < options.length; i++) labels[i] = options[i].label();
        this.addDrawableChild(new SelectionDropdownWidget(x, y, w, CONTROL_HEIGHT, label, labels, index,
            i -> onSelect.accept(options[MathHelper.clamp(i, 0, options.length - 1)]),
            SelectionDropdownWidget.OpenDirection.PREFER_UP));
    }

    private void addPresetDropdown(int x, int y, int w, String label, List<MaterialPresetCatalog.Preset> presets,
                                   String current, Consumer<MaterialPresetCatalog.Preset> onSelect) {
        if (presets.isEmpty()) {
            addButton(x, y, w, CONTROL_HEIGHT, label + ": Missing", () -> status = "Preset catalog missing");
            return;
        }
        String[] labels = presets.stream().map(MaterialPresetCatalog.Preset::display).toArray(String[]::new);
        int index = indexOfPreset(presets, current);
        this.addDrawableChild(new SelectionDropdownWidget(x, y, w, CONTROL_HEIGHT, label, labels, index,
            i -> onSelect.accept(presets.get(MathHelper.clamp(i, 0, presets.size() - 1))),
            SelectionDropdownWidget.OpenDirection.PREFER_UP));
    }

    private String formatSliderValue(int value, int min, int max) {
        if (max <= 4) return Integer.toString(value);
        return String.format(Locale.ROOT, "%.2f", value / 100.0f);
    }

    private int optionIndex(Option[] options, String current) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].value().equals(current)) return i;
        }
        return 0;
    }

    private int indexOfPreset(List<MaterialPresetCatalog.Preset> presets, String id) {
        for (int i = 0; i < presets.size(); i++) {
            if (presets.get(i).id().equals(id)) return i;
        }
        return 0;
    }

    private int columnWidth(int totalWidth, int columns) {
        return Math.max(72, (totalWidth - ROW_GAP * (columns - 1)) / columns);
    }

    private void mutate(Consumer<MaterialRecipe> mutation, boolean rebuild, boolean applyNow) {
        undo.push(recipe.copy());
        while (undo.size() > 64) undo.removeLast();
        redo.clear();
        mutation.accept(recipe);
        markPlanDirty();
        status = "Dirty";
        if (applyNow) applyPreview();
        if (rebuild && RadianceTheme.activeSlider == null) rebuildWidgets();
    }

    private void applyMetalSource(MaterialRecipe r, String source) {
        r.metalMode = source;
        r.metalMaskSource = "flat";
        if ("pack".equals(source)) {
            r.metallicOverride = false;
            r.f0Override = false;
            r.conductorF0RgbOverride = false;
            r.labPbrMetalCode = 0;
            r.metalPreset = "";
        } else if ("labpbr_code".equals(source)) {
            r.metallicOverride = true;
            r.metallic = 1.0f;
            if (r.labPbrMetalCode < 230) r.labPbrMetalCode = 238;
            r.f0Override = true;
            r.f0 = Math.max(r.f0, 0.5f);
            r.conductorF0RgbOverride = false;
        } else if ("flat".equals(source)) {
            r.metallicOverride = true;
            r.conductorF0RgbOverride = false;
            r.labPbrMetalCode = 0;
        } else if ("generated_mask".equals(source)) {
            r.metallicOverride = true;
            r.metalMaskSource = "luminance";
            r.metallic = Math.max(r.metallic, 1.0f);
            r.labPbrMetalCode = 238;
            r.conductorF0RgbOverride = false;
        } else if ("radser_measured".equals(source)) {
            r.metallicOverride = true;
            r.metallic = 1.0f;
            if (r.labPbrMetalCode < 230) r.labPbrMetalCode = 238;
            r.f0Override = true;
            r.f0 = Math.max(r.f0, 0.5f);
        }
    }

    private void applyMetalPreset(MaterialRecipe r, MaterialPresetCatalog.Preset preset) {
        if (preset == null) return;
        r.metallicOverride = true;
        r.metalMode = "radser_measured";
        r.metalMaskSource = "flat";
        r.labPbrMetalCode = preset.labPbrMetalCode();
        r.metallic = 1.0f;
        r.f0Override = true;
        r.f0 = preset.f0();
        r.conductorF0RgbOverride = preset.hasConductorF0Rgb();
        r.conductorF0R = preset.conductorF0R();
        r.conductorF0G = preset.conductorF0G();
        r.conductorF0B = preset.conductorF0B();
        r.metalPreset = preset.id();
        r.transmissionOverride = true;
        r.transmission = 0.0f;
    }

    private void applyDielectricPreset(MaterialRecipe r, MaterialPresetCatalog.Preset preset) {
        if (preset == null) return;
        r.iorOverride = true;
        r.ior = preset.ior();
        r.transmissionOverride = preset.transmission() > 0.0f;
        r.transmission = preset.transmission();
        if (!r.conductorF0RgbOverride) {
            r.f0Override = true;
            r.f0 = MaterialRecipe.f0FromIor(r.ior);
        }
        r.dielectricPreset = preset.id();
    }

    private void applyPreview() {
        if (!AutoPbrTextureCatalog.isEditableSprite(selectedSprite)) {
            lastUpload = null;
            status = "Physical water is shader controlled";
            return;
        }
        int spriteId = AutoPbrTextureCatalog.spriteIndex(selectedSprite);
        lastUpload = MaterialRecipeCompiler.compileAndUpload(spriteId, recipe);
        cachedPlan = lastUpload.plan;
        cachedSpriteId = spriteId;
        cachedRevision = recipeRevision;
        status = lastUpload.statusText;
    }

    private void saveRecipe() {
        if (!AutoPbrTextureCatalog.isEditableSprite(selectedSprite)) {
            status = "Physical water is shader controlled";
            return;
        }
        applyPreview();
        boolean ok = MaterialLabStore.saveRecipe(MinecraftClient.getInstance(), selectedSprite, recipe);
        if (ok) {
            savedSnapshot = recipe.copy();
            if (lastUpload != null && !lastUpload.uploadOk) {
                status = "Saved / " + lastUpload.statusText;
            } else {
                status = recipe.isDefaultIntent() ? "Baseline Saved" : "Saved";
            }
        } else {
            status = "Save Error";
        }
        rebuildWidgets();
    }

    private void revertRecipe() {
        undo.push(recipe.copy());
        recipe = savedSnapshot.copy();
        markPlanDirty();
        redo.clear();
        applyPreview();
        if (lastUpload != null && lastUpload.uploadOk) status = "Reverted";
        rebuildWidgets();
    }

    private void resetSprite() {
        undo.push(recipe.copy());
        redo.clear();
        recipe = MaterialRecipe.defaults();
        markPlanDirty();
        applyPreview();
        if (lastUpload != null && lastUpload.uploadOk) status = "Sprite Reset";
        rebuildWidgets();
    }

    private void usePackBaseline() {
        undo.push(recipe.copy());
        redo.clear();
        recipe = MaterialRecipe.defaults();
        markPlanDirty();
        applyPreview();
        if (lastUpload != null && lastUpload.uploadOk) status = "Baseline Restored";
        rebuildWidgets();
    }

    private void resetActiveChannel() {
        mutate(r -> {
            MaterialRecipe d = MaterialRecipe.defaults();
            switch (activeChannel) {
                case ALBEDO -> {
                }
                case ROUGHNESS -> {
                    r.generateRoughness = d.generateRoughness;
                    r.roughnessOverride = d.roughnessOverride;
                    r.roughnessSource = d.roughnessSource;
                    r.roughnessGeneratorMode = d.roughnessGeneratorMode;
                    r.roughness = d.roughness;
                    r.roughnessGeneratedBlend = d.roughnessGeneratedBlend;
                    r.roughnessFlatBlend = d.roughnessFlatBlend;
                    r.roughnessBlackPoint = d.roughnessBlackPoint;
                    r.roughnessMidpoint = d.roughnessMidpoint;
                    r.roughnessWhitePoint = d.roughnessWhitePoint;
                    r.roughnessMin = d.roughnessMin;
                    r.roughnessMax = d.roughnessMax;
                    r.roughnessGamma = d.roughnessGamma;
                    r.roughnessContrast = d.roughnessContrast;
                    r.roughnessEdge = d.roughnessEdge;
                    r.roughnessBroadDetail = d.roughnessBroadDetail;
                    r.roughnessMidDetail = d.roughnessMidDetail;
                    r.roughnessFineDetail = d.roughnessFineDetail;
                    r.roughnessSharpen = d.roughnessSharpen;
                    r.roughnessDenoise = d.roughnessDenoise;
                    r.roughnessWear = d.roughnessWear;
                    r.roughnessPolish = d.roughnessPolish;
                    r.roughnessWetness = d.roughnessWetness;
                    r.roughnessEdgeRoughness = d.roughnessEdgeRoughness;
                    r.roughnessBlurRadius = d.roughnessBlurRadius;
                    r.roughnessSmoothing = d.roughnessSmoothing;
                    r.roughnessAutoRange = d.roughnessAutoRange;
                    r.roughnessInvert = d.roughnessInvert;
                }
                case METAL -> {
                    r.metallicOverride = d.metallicOverride;
                    r.metalMode = d.metalMode;
                    r.metalMaskSource = d.metalMaskSource;
                    r.labPbrMetalCode = d.labPbrMetalCode;
                    r.metallic = d.metallic;
                    r.metalMaskThreshold = d.metalMaskThreshold;
                    r.metalMaskSoftness = d.metalMaskSoftness;
                    r.metalMaskDilate = d.metalMaskDilate;
                    r.metalMaskErode = d.metalMaskErode;
                    r.metalMaskDespeckle = d.metalMaskDespeckle;
                    r.metalMaskClampMin = d.metalMaskClampMin;
                    r.metalMaskClampMax = d.metalMaskClampMax;
                    r.oxideAmount = d.oxideAmount;
                    r.oxideRoughnessInfluence = d.oxideRoughnessInfluence;
                    r.oxideColorBias = d.oxideColorBias;
                    r.metalPreset = d.metalPreset;
                    r.conductorF0RgbOverride = d.conductorF0RgbOverride;
                    r.conductorF0R = d.conductorF0R;
                    r.conductorF0G = d.conductorF0G;
                    r.conductorF0B = d.conductorF0B;
                    r.f0Override = d.f0Override;
                    r.f0 = d.f0;
                }
                case POROSITY -> {
                    r.porosityOverride = d.porosityOverride;
                    r.porosityMode = d.porosityMode;
                    r.porositySource = d.porositySource;
                    r.porosityAmount = d.porosityAmount;
                    r.porosityBlackPoint = d.porosityBlackPoint;
                    r.porosityMidpoint = d.porosityMidpoint;
                    r.porosityWhitePoint = d.porosityWhitePoint;
                    r.porosityCavityInfluence = d.porosityCavityInfluence;
                    r.porosityWetnessCoupling = d.porosityWetnessCoupling;
                    r.porositySssStrength = d.porositySssStrength;
                    r.porositySssRadius = d.porositySssRadius;
                    r.porositySssTintR = d.porositySssTintR;
                    r.porositySssTintG = d.porositySssTintG;
                    r.porositySssTintB = d.porositySssTintB;
                    r.porositySssThickness = d.porositySssThickness;
                    r.porosityInvert = d.porosityInvert;
                }
                case HEIGHT -> {
                    r.generateHeight = d.generateHeight;
                    r.heightOverride = d.heightOverride;
                    r.heightSource = d.heightSource;
                    r.heightGeneratorMode = d.heightGeneratorMode;
                    r.heightGeneratedBlend = d.heightGeneratedBlend;
                    r.heightFlatBlend = d.heightFlatBlend;
                    r.heightFlat = d.heightFlat;
                    r.heightBlackPoint = d.heightBlackPoint;
                    r.heightMidpoint = d.heightMidpoint;
                    r.heightWhitePoint = d.heightWhitePoint;
                    r.heightMin = d.heightMin;
                    r.heightMax = d.heightMax;
                    r.heightOffset = d.heightOffset;
                    r.heightSmoothing = d.heightSmoothing;
                    r.heightBlurRadius = d.heightBlurRadius;
                    r.heightSharpen = d.heightSharpen;
                    r.heightErode = d.heightErode;
                    r.heightDilate = d.heightDilate;
                    r.heightDenoise = d.heightDenoise;
                    r.heightScale = d.heightScale;
                    r.heightGamma = d.heightGamma;
                    r.heightAutoRange = d.heightAutoRange;
                    r.invertHeight = d.invertHeight;
                }
                case NORMAL -> {
                    r.generateNormal = d.generateNormal;
                    r.normalStrengthOverride = d.normalStrengthOverride;
                    r.normalSource = d.normalSource;
                    r.normalCombineMode = d.normalCombineMode;
                    r.normalKernelMode = d.normalKernelMode;
                    r.normalOrientation = d.normalOrientation;
                    r.normalGeneratedBlend = d.normalGeneratedBlend;
                    r.normalPackStrength = d.normalPackStrength;
                    r.normalGeneratedStrength = d.normalGeneratedStrength;
                    r.normalXStrength = d.normalXStrength;
                    r.normalYStrength = d.normalYStrength;
                    r.normalDetailFrequency = d.normalDetailFrequency;
                    r.normalDetailBlendMode = d.normalDetailBlendMode;
                    r.normalGeneratorRadius = d.normalGeneratorRadius;
                    r.normalSmoothing = d.normalSmoothing;
                    r.normalStrength = d.normalStrength;
                    r.detailNormalStrength = d.detailNormalStrength;
                    r.flipGreen = d.flipGreen;
                }
                case EMISSION -> {
                    r.emissionOverride = d.emissionOverride;
                    r.emissionMode = d.emissionMode;
                    r.emissionGain = d.emissionGain;
                    r.emissionThreshold = d.emissionThreshold;
                    r.emissionThresholdLow = d.emissionThresholdLow;
                    r.emissionThresholdHigh = d.emissionThresholdHigh;
                    r.emissionSoftness = d.emissionSoftness;
                    r.emissionDilate = d.emissionDilate;
                    r.emissionErode = d.emissionErode;
                    r.emissionDespeckle = d.emissionDespeckle;
                    r.emissionBlurRadius = d.emissionBlurRadius;
                    r.emissionTintR = d.emissionTintR;
                    r.emissionTintG = d.emissionTintG;
                    r.emissionTintB = d.emissionTintB;
                    r.emissionNits = d.emissionNits;
                    r.emissionAnimationMode = d.emissionAnimationMode;
                    r.emissionAnimationSpeed = d.emissionAnimationSpeed;
                    r.emissionAnimationPhase = d.emissionAnimationPhase;
                    r.emissionInvert = d.emissionInvert;
                }
                case TRANSMISSION -> {
                    r.iorOverride = d.iorOverride;
                    r.ior = d.ior;
                    r.dielectricPreset = d.dielectricPreset;
                    r.transmissionOverride = d.transmissionOverride;
                    r.transmission = d.transmission;
                    r.absorptionR = d.absorptionR;
                    r.absorptionG = d.absorptionG;
                    r.absorptionB = d.absorptionB;
                    r.absorptionDistance = d.absorptionDistance;
                    r.transmissionVolumeMode = d.transmissionVolumeMode;
                    r.refractionRoughness = d.refractionRoughness;
                    r.thicknessSource = d.thicknessSource;
                    r.thicknessAmount = d.thicknessAmount;
                    r.thicknessMin = d.thicknessMin;
                    r.thicknessMax = d.thicknessMax;
                    r.thicknessGamma = d.thicknessGamma;
                }
                case ADVANCED -> {
                    r.anisotropicOverride = d.anisotropicOverride;
                    r.anisotropic = d.anisotropic;
                    r.anisotropicRotation = d.anisotropicRotation;
                    r.sheenOverride = d.sheenOverride;
                    r.sheenWeight = d.sheenWeight;
                    r.sheenTint = d.sheenTint;
                    r.sheenRoughness = d.sheenRoughness;
                    r.diffuseModel = d.diffuseModel;
                    r.coatOverride = d.coatOverride;
                    r.coatWeight = d.coatWeight;
                    r.coatRoughness = d.coatRoughness;
                    r.coatIor = d.coatIor;
                    r.coatTintR = d.coatTintR;
                    r.coatTintG = d.coatTintG;
                    r.coatTintB = d.coatTintB;
                    r.coatMask = d.coatMask;
                    r.coatMaskSource = d.coatMaskSource;
                    r.displacementOverride = d.displacementOverride;
                    r.displacementScale = d.displacementScale;
                    r.detailNormalStrength = d.detailNormalStrength;
                    r.anisotropicDirectionMode = d.anisotropicDirectionMode;
                    r.filterRadius = d.filterRadius;
                    r.mipBias = d.mipBias;
                    r.uvScale = d.uvScale;
                    r.uvOffset = d.uvOffset;
                }
            }
        }, true, true);
        if (lastUpload != null && lastUpload.uploadOk) status = activeChannel.label() + " Reset";
    }

    private void selectSprite(Identifier sprite) {
        if (sprite == null
            || !TextureArrayBridge.sortedSpriteIds.contains(sprite)
            || !AutoPbrTextureCatalog.isEditableSprite(sprite)) {
            return;
        }
        selectedSprite = sprite;
        undo.clear();
        redo.clear();
        loadRecipe();
        rebuildWidgets();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (RadianceTheme.peekActive) return;
        renderFrame(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        SelectionDropdownWidget.renderAllOverlays(context, mouseX, mouseY);
    }

    private void renderFrame(DrawContext context, int mouseX, int mouseY) {
        int spriteId = AutoPbrTextureCatalog.spriteIndex(selectedSprite);
        MaterialBakePlan plan = currentPlan(spriteId);
        renderHeader(context);
        renderLeftPanel(context, spriteId);
        renderMainPanel(context, spriteId, plan);
        renderInspector(context, spriteId, plan);
        renderChannelStrip(context, spriteId, plan, mouseX, mouseY);
    }

    private void renderHeader(DrawContext context) {
        context.fill(0, 0, width, HEADER_HEIGHT, labBg(RadianceTheme.unifiedHeaderBg));
        int sliderX = opacitySliderX();
        int statusMax = Math.max(70, Math.min(210, sliderX - PAD * 2));
        int statusX = Math.max(PAD, sliderX - statusMax - 14);
        int titleMax = Math.max(80, statusX - PAD * 2);
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal("Material Lab > " + selectedSprite),
                titleMax),
            PAD, (HEADER_HEIGHT - 8) / 2, RadianceTheme.textAccent);
        Text statusText = Text.literal(status);
        int statusColor = "Saved".equals(status) || "Baseline Saved".equals(status)
            || "Applied".equals(status) || "Baseline Restored".equals(status)
            ? RadianceTheme.TEXT_SUCCESS
            : status.contains("Error") || status.contains("Failed") ? RadianceTheme.TEXT_ERROR
            : RadianceTheme.textSecondary;
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, statusText, statusMax),
            statusX, (HEADER_HEIGHT - 8) / 2, statusColor);
    }

    private void renderLeftPanel(DrawContext context, int spriteId) {
        int x = PAD;
        int y = HEADER_HEIGHT + PAD;
        int h = height - HEADER_HEIGHT - BOTTOM_STRIP_HEIGHT - PAD * 2;
        context.fill(x, y, x + LEFT_WIDTH, y + h, labBg(RadianceTheme.unifiedTreeBg));
        context.drawBorder(x, y, LEFT_WIDTH, h, RadianceTheme.borderDefault);
        RadianceTheme.drawOutlinedText(context, textRenderer, Text.literal("Texture"), x + 10, y + 10,
            RadianceTheme.textAccent);
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal(selectedSprite.toString()), LEFT_WIDTH - 20),
            x + 10, y + 28, RadianceTheme.textPrimary);
        int preview = Math.min(96, LEFT_WIDTH - 40);
        renderImagePreview(context, AutoPbrTextureCatalog.albedo(spriteId), x + 10, y + 48, preview);
        int lineY = y + 58 + preview;
        AutoPbrTextureCatalog.SpriteProvenance provenance = AutoPbrTextureCatalog.spriteProvenance(spriteId);
        drawSmallLine(context, x + 10, lineY, "id " + spriteId); lineY += 12;
        drawSmallLine(context, x + 10, lineY, "spec " + provenance.specularSource()); lineY += 12;
        drawSmallLine(context, x + 10, lineY, "normal " + provenance.normalSource()); lineY += 12;
        drawSmallLine(context, x + 10, lineY, "height " + AutoPbrTextureCatalog.heightAlphaRangeLabel(spriteId));
        lineY += 12;
        drawSmallLine(context, x + 10, lineY, "ao " + AutoPbrTextureCatalog.aoAvailability(spriteId)); lineY += 12;
        drawSmallLine(context, x + 10, lineY, "emission " + AutoPbrTextureCatalog.emissionAvailability(spriteId));
        lineY += 12;
        drawSmallLine(context, x + 10, lineY, "usage " + usage(selectedSprite));
    }

    private void renderMainPanel(DrawContext context, int spriteId, MaterialBakePlan plan) {
        int x = mainX();
        int y = HEADER_HEIGHT + PAD;
        int w = mainW();
        int h = height - HEADER_HEIGHT - BOTTOM_STRIP_HEIGHT - PAD * 2;
        context.fill(x, y, x + w, y + h, labBg(RadianceTheme.unifiedContentBg));
        context.drawBorder(x, y, w, h, RadianceTheme.borderDefault);
        RadianceTheme.drawOutlinedText(context, textRenderer, Text.literal(activeChannel.label()),
            x + 10, y + 10, RadianceTheme.textAccent);
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal(activeChannel.detail()), w - 20),
            x + 10, y + 26, RadianceTheme.textSecondary);
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal("Dirty: " + dirtySummary()), w - 20),
            x + 10, y + 42, RadianceTheme.textPrimary);
        renderPreviewLane(context, x + 10, previewLaneY(), w - 20, previewLaneHeight(), spriteId, plan);
        renderHistogramLane(context, x + 10, histogramY(), w - 20, HISTOGRAM_HEIGHT, spriteId, plan);
    }

    private void renderChannelNotes(DrawContext context, int x, int y, int w) {
        List<String> lines = switch (activeChannel) {
            case ALBEDO -> List.of(
                "Albedo stays resource-pack truth in this pass.",
                "Alpha remains Minecraft coverage or opacity, not transmission.");
            case ROUGHNESS -> List.of(
                "UI edits roughness; compiler packs LabPBR _s.R as smoothness.",
                "Generated roughness is generic luminance and edge analysis only.");
            case METAL -> List.of(
                "Measured metals are manual presets, never sprite-name guesses.",
                "Pack LabPBR metal codes are preserved until you enable an explicit metal recipe.");
            case POROSITY -> List.of(
                "Porosity/SSS edits LabPBR _s.B only when mode is not Preserve.",
                "SSS radius, thickness, and tint also write a modest shader rule when SSS mode is active.");
            case HEIGHT -> List.of(
                "Height writes LabPBR _n.A and keeps generated/runtime images rebuildable.",
                "Scale is per-material intent, still bounded by global displacement settings.");
            case NORMAL -> List.of(
                "Generated normals derive from the current height candidate.",
                "Flip green changes normal orientation once; no hidden second flip.",
                "AO is preserved as packed _n.B data, but it is not a visible editing channel.");
            case EMISSION -> List.of(
                "Emission is manual-mask only. No hidden light classifier.",
                "Tint and physical nits are backed texture-rule fields; pulse/flicker remains roadmap-only.");
            case TRANSMISSION -> List.of(
                "Transmission and IOR are RadSER material rules.",
                "Thickness source, min, max, and gamma shape the scalar rule before upload.");
            case ADVANCED -> List.of(
                "Enabled: anisotropy, coat, sheen, UV transform, filter radius, mip bias, and displacement rules.",
                "Coat IOR/tint/mask and sheen roughness are shader-backed scalar extensions.");
        };
        int lineY = y;
        for (String line : lines) {
            RadianceTheme.drawOutlinedText(context, textRenderer,
                RadianceTheme.trimText(textRenderer, Text.literal(line), w),
                x, lineY, RadianceTheme.textSecondary);
            lineY += 13;
        }
    }

    private void renderInspector(DrawContext context, int spriteId, MaterialBakePlan plan) {
        int x = inspectorX();
        int y = HEADER_HEIGHT + PAD;
        int w = inspectorW();
        int h = height - HEADER_HEIGHT - BOTTOM_STRIP_HEIGHT - PAD * 2;
        context.fill(x, y, x + w, y + h, labBg(RadianceTheme.unifiedTreeBg));
        context.drawBorder(x, y, w, h, RadianceTheme.borderDefault);
        RadianceTheme.drawOutlinedText(context, textRenderer, Text.literal("Inspector"), x + 10, y + 10,
            RadianceTheme.textAccent);
        int previewSize = Math.min(w - 24, Math.max(88, Math.min(208, h - 148)));
        int previewX = x + (w - previewSize) / 2;
        int previewY = y + 34;
        context.fill(previewX - 2, previewY - 2, previewX + previewSize + 2, previewY + previewSize + 2,
            RadianceTheme.widgetBg);
        context.drawBorder(previewX - 2, previewY - 2, previewSize + 4, previewSize + 4,
            RadianceTheme.borderFocused);
        renderChannelPreview(context, previewX, previewY, previewSize, activeChannel, spriteId, plan);

        int lineY = previewY + previewSize + 12;
        drawInspectorLine(context, x + 10, lineY, "Source", channelBadge(activeChannel, spriteId, plan)); lineY += 13;
        drawInspectorLine(context, x + 10, lineY, "Bake", plan != null && plan.ok ? "ok" : "failed"); lineY += 13;
        drawInspectorLine(context, x + 10, lineY, "Upload", status); lineY += 13;
        drawInspectorLine(context, x + 10, lineY, "Recipe", recipe.isDefaultIntent() ? "default removed on save" : "stored modifier");
        lineY += 13;
        if (activeChannel == Channel.METAL) {
            drawInspectorLine(context, x + 10, lineY, "Mode", recipe.metalMode); lineY += 13;
            if (recipe.conductorF0RgbOverride) {
                drawInspectorLine(context, x + 10, lineY, "F0 RGB",
                    fmt(recipe.conductorF0R) + ", " + fmt(recipe.conductorF0G) + ", " + fmt(recipe.conductorF0B));
                lineY += 13;
            }
            if (!recipe.metalPreset.isBlank()) {
                MaterialPresetCatalog.Preset preset = MaterialPresetCatalog.byId(recipe.metalPreset);
                lineY = drawWrappedSmallLine(context, x + 10, lineY, preset.measuredSummary(), w - 20, 2);
            }
        }
        if (activeChannel == Channel.POROSITY) {
            drawInspectorLine(context, x + 10, lineY, "_s.B Mode", recipe.porosityMode); lineY += 13;
            lineY = drawWrappedSmallLine(context, x + 10, lineY,
                "Preserve keeps pack _s.B; Porosity/SSS bakes only this channel.", w - 20, 2);
        }
        if (activeChannel == Channel.ALBEDO || activeChannel == Channel.TRANSMISSION) {
            lineY = drawWrappedSmallLine(context, x + 10, lineY,
                "Alpha is coverage/opacity, not transmission.", w - 20, 2);
        }
        if (activeChannel == Channel.EMISSION) {
            lineY = drawWrappedSmallLine(context, x + 10, lineY,
                "Emission mask does not create block light.", w - 20, 2);
        }
        lineY = drawWrappedSmallLine(context, x + 10, lineY,
            "Reset clears: " + resetFields(activeChannel), w - 20, 2);
        if (plan != null && !plan.diagnostics.isEmpty()) {
            RadianceTheme.drawOutlinedText(context, textRenderer,
                RadianceTheme.trimText(textRenderer, Text.literal(plan.diagnostics.get(0)), w - 20),
                x + 10, h + y - 20, RadianceTheme.TEXT_ERROR);
        }
    }

    private void renderChannelStrip(DrawContext context, int spriteId, MaterialBakePlan plan, int mouseX, int mouseY) {
        int y = height - BOTTOM_STRIP_HEIGHT;
        context.fill(0, y, width, height, labBg(RadianceTheme.unifiedContentBg));
        context.drawBorder(0, y, width, BOTTOM_STRIP_HEIGHT, RadianceTheme.borderDefault);
        int tileW = stripTileWidth();
        int tileH = BOTTOM_STRIP_HEIGHT - 18;
        int gap = ROW_GAP;
        int x = stripStartX();
        for (Channel channel : Channel.values()) {
            boolean active = channel == activeChannel;
            boolean hovered = hover(mouseX, mouseY, x, y + 8, tileW, tileH);
            int bg = active ? RadianceTheme.widgetBgActive : hovered ? RadianceTheme.widgetBgHover : RadianceTheme.widgetBg;
            context.fill(x, y + 8, x + tileW, y + 8 + tileH, labBg(bg));
            context.drawBorder(x, y + 8, tileW, tileH, active ? RadianceTheme.borderFocused : RadianceTheme.borderDefault);
            int preview = Math.min(tileW - 12, 34);
            renderChannelPreview(context, x + (tileW - preview) / 2, y + 14, preview, channel, spriteId, plan);
            RadianceTheme.drawCenteredOutlinedText(context, textRenderer,
                RadianceTheme.trimText(textRenderer, Text.literal(channel.shortLabel()), tileW - 6),
                x + tileW / 2, y + 50, active ? RadianceTheme.textAccent : RadianceTheme.textSecondary);
            String badge = channelBadge(channel, spriteId, plan);
            if (!badge.isBlank()) {
                RadianceTheme.drawCenteredOutlinedText(context, textRenderer,
                    RadianceTheme.trimText(textRenderer, Text.literal(badge), tileW - 6),
                    x + tileW / 2, y + 64, RadianceTheme.textSecondary);
            }
            x += tileW + gap;
        }
    }

    private void renderPreviewLane(DrawContext context, int x, int y, int w, int h,
                                   int spriteId, MaterialBakePlan plan) {
        String[] labels = previewLabels(activeChannel);
        int count = labels.length;
        int gap = 6;
        int tileW = Math.max(76, (w - gap * (count - 1)) / count);
        int preview = Math.max(44, Math.min(78, h - 22));
        for (int i = 0; i < count; i++) {
            int tx = x + i * (tileW + gap);
            context.fill(tx, y, tx + tileW, y + h, labBg(RadianceTheme.widgetBg));
            context.drawBorder(tx, y, tileW, h, RadianceTheme.borderDefault);
            renderPreviewByLabel(context, labels[i], tx + (tileW - preview) / 2, y + 8, preview, spriteId, plan);
            RadianceTheme.drawCenteredOutlinedText(context, textRenderer,
                RadianceTheme.trimText(textRenderer, Text.literal(labels[i]), tileW - 6),
                tx + tileW / 2, y + h - 13, RadianceTheme.textSecondary);
        }
    }

    private String[] previewLabels(Channel channel) {
        return switch (channel) {
            case ALBEDO -> new String[]{"Albedo", "Alpha", "Luminance", "Coverage"};
            case ROUGHNESS -> new String[]{"Pack Smooth", "Generated", "Flat", "Final Rough"};
            case METAL -> new String[]{"Pack _s.G", "Metal Mask", "F0 / Code", "BRDF"};
            case POROSITY -> new String[]{"Pack _s.B", "Generated", "SSS Rule", "Final _s.B"};
            case HEIGHT -> new String[]{"Pack Height", "Generated", "Flat", "Final Height", "Relief"};
            case NORMAL -> new String[]{"Pack Normal", "Generated", "Detail", "Final Normal", "Tile"};
            case EMISSION -> new String[]{"Source", "Threshold", "Cleaned", "Final"};
            case TRANSMISSION -> new String[]{"Alpha", "Transmit", "Thickness", "Rule"};
            case ADVANCED -> new String[]{"Aniso", "Coat", "Sheen", "Sampler"};
        };
    }

    private void renderPreviewByLabel(DrawContext context, String label, int x, int y, int size,
                                      int spriteId, MaterialBakePlan plan) {
        PreviewLane lane = previewLaneForLabel(activeChannel, label);
        if (plan != null && plan.preview != null) {
            int sourceSize = Math.max(1, plan.preview.size);
            int[] colors = plan.preview.color(lane.key());
            if (colors != null) {
                renderColorPreview(context, colors, sourceSize, x, y, size);
                return;
            }
            float[] values = plan.preview.scalar(lane.key());
            if (values != null) {
                renderScalarPreview(context, values, sourceSize, x, y, size, lane.tintRgb());
                return;
            }
        }
        renderChannelPreview(context, x, y, size, activeChannel, spriteId, plan);
    }

    private void renderHistogramLane(DrawContext context, int x, int y, int w, int h,
                                     int spriteId, MaterialBakePlan plan) {
        context.fill(x, y, x + w, y + h, labBg(RadianceTheme.widgetBg));
        context.drawBorder(x, y, w, h, RadianceTheme.borderDefault);

        int plotX = x + 10;
        int plotY = y + 20;
        int plotW = Math.max(16, w - 20);
        int plotH = Math.max(8, h - 30);
        MaterialHistogram.Stats histogram = MaterialHistogram.build(valuesForHistogram(spriteId, plan), plotW);

        String label = histogramLabel();
        String stats = histogram.count == 0
            ? label + " histogram: unavailable"
            : String.format(Locale.ROOT, "%s histogram  min %.2f  avg %.2f  max %.2f",
                label, histogram.min, histogram.avg, histogram.max);
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal(stats), w - 14),
            x + 6, y + 5, RadianceTheme.textSecondary);

        drawHistogramGrid(context, plotX, plotY, plotW, plotH);
        if (histogram.count > 0) {
            renderHistogramDensity(context, plotX, plotY, plotW, plotH, histogram);
            drawHistogramAverage(context, plotX, plotY, plotW, plotH, histogram.avg);
        }
        drawHistogramHandle(context, plotX, plotY, plotW, plotH, levelBlack(activeChannel), 0xFF707070);
        drawHistogramHandle(context, plotX, plotY, plotW, plotH, levelMid(activeChannel), RadianceTheme.TEXT_LINK);
        drawHistogramHandle(context, plotX, plotY, plotW, plotH, levelWhite(activeChannel), RadianceTheme.textAccent);
    }

    private void drawHistogramGrid(DrawContext context, int x, int y, int w, int h) {
        int grid = RadianceTheme.withAlpha(0x808080, 0.22f);
        int faint = RadianceTheme.withAlpha(0x808080, 0.12f);
        int baseline = RadianceTheme.withAlpha(0xB0B0B0, 0.35f);
        context.fill(x, y + h, x + w, y + h + 1, baseline);
        context.fill(x, y + h / 2, x + w, y + h / 2 + 1, faint);
        for (int i = 0; i <= 4; i++) {
            int tx = x + Math.round((w - 1) * (i / 4.0f));
            context.fill(tx, y, tx + 1, y + h + 1, i == 0 || i == 4 ? grid : faint);
            Text tick = Text.literal(String.format(Locale.ROOT, "%.2f", i / 4.0f));
            RadianceTheme.drawOutlinedText(context, textRenderer, tick,
                MathHelper.clamp(tx - textRenderer.getWidth(tick) / 2, x, x + w - textRenderer.getWidth(tick)),
                y + h + 2, RadianceTheme.textSecondary);
        }
    }

    private void renderHistogramDensity(DrawContext context, int x, int y, int w, int h,
                                        MaterialHistogram.Stats histogram) {
        if (histogram.maxDensity <= 0.0f) return;
        float logMax = (float) Math.log1p(histogram.maxDensity);
        for (int col = 0; col < w; col++) {
            float u = w <= 1 ? 0.0f : col / (float) (w - 1);
            float density = histogram.sample(u);
            float normalized = logMax <= 0.0f ? 0.0f : (float) (Math.log1p(density) / logMax);
            if (normalized <= 0.005f) continue;

            int height = Math.max(1, Math.round(h * normalized));
            int rgb = histogramDensityColor(u, normalized);
            float alpha = MathHelper.clamp(0.18f + normalized * 0.58f, 0.18f, 0.76f);
            context.fill(x + col, y + h - height, x + col + 1, y + h,
                RadianceTheme.withAlpha(rgb, alpha));
        }
    }

    private void drawHistogramAverage(DrawContext context, int x, int y, int w, int h, float value) {
        int ax = x + Math.round(MathHelper.clamp(value, 0.0f, 1.0f) * Math.max(1, w - 1));
        int color = RadianceTheme.withAlpha(0x2AB5A0, 0.55f);
        context.fill(ax, y, ax + 1, y + h + 1, color);
    }

    private float[] valuesForHistogram(int spriteId, MaterialBakePlan plan) {
        if (plan == null || plan.preview == null) return null;
        return switch (activeChannel) {
            case ALBEDO -> plan.preview.scalar("luminance");
            case ROUGHNESS -> plan.preview.scalar("final_roughness");
            case METAL -> plan.preview.scalar("metal_mask");
            case POROSITY -> plan.preview.scalar("final_sss");
            case HEIGHT -> plan.preview.scalar("final_height");
            case NORMAL -> normalMagnitude(plan.preview.color("final_normal"), Math.max(1, plan.preview.size));
            case EMISSION -> plan.preview.scalar("emission_final");
            case TRANSMISSION -> plan.preview.scalar("transmission");
            default -> null;
        };
    }

    private PreviewLane previewLaneForLabel(Channel channel, String label) {
        return switch (channel) {
            case ALBEDO -> switch (label) {
                case "Alpha" -> new PreviewLane("alpha", 0xFFFFFF);
                case "Luminance" -> new PreviewLane("luminance", 0xFFFFFF);
                case "Coverage" -> new PreviewLane("coverage", 0xFFFFFF);
                default -> new PreviewLane("albedo", 0xFFFFFF);
            };
            case ROUGHNESS -> switch (label) {
                case "Pack Smooth" -> new PreviewLane("pack_smoothness", 0xFFFFFF);
                case "Generated" -> new PreviewLane("generated_roughness", 0xFFFFFF);
                case "Flat" -> new PreviewLane("flat_roughness", 0xFFFFFF);
                default -> new PreviewLane("final_roughness", 0xFFFFFF);
            };
            case METAL -> switch (label) {
                case "Pack _s.G" -> new PreviewLane("pack_sg", 0xD8E8FF);
                case "Metal Mask" -> new PreviewLane("metal_mask", 0xFFFFFF);
                case "F0 / Code" -> new PreviewLane("f0_map", 0xD8E8FF);
                default -> new PreviewLane("brdf_swatch", 0xFFFFFF);
            };
            case POROSITY -> switch (label) {
                case "Pack _s.B" -> new PreviewLane("pack_sb", 0xB8E0FF);
                case "Generated" -> new PreviewLane("generated_sss", 0xB8E0FF);
                case "SSS Rule" -> new PreviewLane("sss_rule", 0xFFB8A0);
                default -> new PreviewLane("final_sss", 0xB8E0FF);
            };
            case HEIGHT -> switch (label) {
                case "Pack Height" -> new PreviewLane("pack_height", 0xFFFFFF);
                case "Generated" -> new PreviewLane("generated_height", 0xFFFFFF);
                case "Flat" -> new PreviewLane("flat_height", 0xFFFFFF);
                case "Relief" -> new PreviewLane("relief", 0xFFFFFF);
                default -> new PreviewLane("final_height", 0xFFFFFF);
            };
            case NORMAL -> switch (label) {
                case "Pack Normal" -> new PreviewLane("pack_normal", 0xFFFFFF);
                case "Generated" -> new PreviewLane("generated_normal", 0xFFFFFF);
                case "Detail" -> new PreviewLane("detail_normal", 0xFFFFFF);
                default -> new PreviewLane("final_normal", 0xFFFFFF);
            };
            case EMISSION -> switch (label) {
                case "Source" -> new PreviewLane("emission_source", 0xFF7A1C);
                case "Threshold" -> new PreviewLane("emission_threshold", 0xFF7A1C);
                default -> new PreviewLane("emission_final", 0xFF7A1C);
            };
            case TRANSMISSION -> switch (label) {
                case "Alpha" -> new PreviewLane("alpha", 0xFFFFFF);
                case "Thickness" -> new PreviewLane("thickness", 0x66CCFF);
                case "Rule" -> new PreviewLane("rule", 0x66CCFF);
                default -> new PreviewLane("transmission", 0x66CCFF);
            };
            case ADVANCED -> switch (label) {
                case "Aniso" -> new PreviewLane("anisotropic", 0xB6A3FF);
                case "Coat" -> new PreviewLane("coat", 0xE4F0FF);
                case "Sheen" -> new PreviewLane("sheen", 0xFFB8E6);
                default -> new PreviewLane("sampler", 0x66CCFF);
            };
        };
    }

    private String histogramLabel() {
        return switch (activeChannel) {
            case ALBEDO -> "Luminance";
            case ROUGHNESS -> "Final roughness";
            case METAL -> "Metal mask";
            case POROSITY -> "Final _s.B";
            case HEIGHT -> "Final height";
            case NORMAL -> "Normal strength";
            case EMISSION -> "Final emission";
            case TRANSMISSION -> "Transmission";
            case ADVANCED -> "Advanced scalar";
        };
    }

    private float levelBlack(Channel channel) {
        return switch (channel) {
            case ROUGHNESS -> recipe.roughnessBlackPoint;
            case POROSITY -> recipe.porosityBlackPoint;
            case HEIGHT -> recipe.heightBlackPoint;
            default -> 0.0f;
        };
    }

    private float levelMid(Channel channel) {
        return switch (channel) {
            case ROUGHNESS -> recipe.roughnessMidpoint;
            case POROSITY -> recipe.porosityMidpoint;
            case HEIGHT -> recipe.heightMidpoint;
            default -> 0.5f;
        };
    }

    private float levelWhite(Channel channel) {
        return switch (channel) {
            case ROUGHNESS -> recipe.roughnessWhitePoint;
            case POROSITY -> recipe.porosityWhitePoint;
            case HEIGHT -> recipe.heightWhitePoint;
            default -> 1.0f;
        };
    }

    private void drawHistogramHandle(DrawContext context, int x, int y, int w, int h, float value, int color) {
        int hx = x + Math.round(MathHelper.clamp(value, 0.0f, 1.0f) * Math.max(1, w - 1));
        context.fill(hx, y, hx + 1, y + h + 1, RadianceTheme.scaleAlpha(color, 0.8f));
        int markerX = MathHelper.clamp(hx - 3, x, x + w - 7);
        int markerY = y + h - 5;
        context.fill(markerX, markerY, markerX + 7, markerY + 7, RadianceTheme.withAlpha(0x050505, 0.88f));
        context.drawBorder(markerX, markerY, 7, 7, color);
    }

    private int histogramDensityColor(float value, float density) {
        int base = blendRgb(0x808080, 0xE0E0E0, MathHelper.clamp(density, 0.0f, 1.0f));
        float warm = MathHelper.clamp((value - 0.68f) / 0.32f, 0.0f, 1.0f) * 0.55f;
        float cool = MathHelper.clamp((0.10f - value) / 0.10f, 0.0f, 1.0f) * 0.25f;
        int color = blendRgb(base, 0xE8712A, warm);
        return blendRgb(color, 0x2AB5A0, cool);
    }

    private int blendRgb(int a, int b, float t) {
        float clamped = MathHelper.clamp(t, 0.0f, 1.0f);
        int ar = (a >>> 16) & 0xFF;
        int ag = (a >>> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >>> 16) & 0xFF;
        int bg = (b >>> 8) & 0xFF;
        int bb = b & 0xFF;
        int rr = Math.round(ar + (br - ar) * clamped);
        int rg = Math.round(ag + (bg - ag) * clamped);
        int rb = Math.round(ab + (bb - ab) * clamped);
        return (rr << 16) | (rg << 8) | rb;
    }

    private float[] normalMagnitude(int[] normal, int sourceSize) {
        if (normal == null || normal.length == 0) return null;
        float[] out = new float[normal.length];
        for (int i = 0; i < normal.length; i++) {
            float nx = (((normal[i] >>> 16) & 0xFF) / 255.0f) * 2.0f - 1.0f;
            float ny = (((normal[i] >>> 8) & 0xFF) / 255.0f) * 2.0f - 1.0f;
            out[i] = MathHelper.clamp((float) Math.sqrt(nx * nx + ny * ny), 0.0f, 1.0f);
        }
        return out;
    }

    private void renderChannelPreview(DrawContext context, int x, int y, int size, Channel channel,
                                      int spriteId, MaterialBakePlan plan) {
        context.fill(x, y, x + size, y + size, RadianceTheme.withAlpha(0x050505, 0.9f));
        if (channel == Channel.ALBEDO) {
            renderImagePreview(context, AutoPbrTextureCatalog.albedo(spriteId), x, y, size);
            return;
        }
        if (plan == null || !plan.ok || plan.surface == null) return;
        int sourceSize = Math.max(1, plan.size);
        switch (channel) {
            case ROUGHNESS -> renderScalarPreview(context, plan.surface.roughness, sourceSize, x, y, size, 0xFFFFFF);
            case METAL -> {
                if (plan.surface.conductorF0RgbOverride) {
                    int color = 0xFF000000
                        | (Math.round(plan.surface.conductorF0R * 255.0f) << 16)
                        | (Math.round(plan.surface.conductorF0G * 255.0f) << 8)
                        | Math.round(plan.surface.conductorF0B * 255.0f);
                    context.fill(x, y, x + size, y + size, color);
                } else {
                    renderScalarPreview(context, plan.surface.f0Map, sourceSize, x, y, size, 0xD8E8FF);
                }
            }
            case POROSITY -> renderScalarPreview(context, plan.surface.sssMap, sourceSize, x, y, size, 0xB8E0FF);
            case HEIGHT -> renderScalarPreview(context, plan.surface.heightMap, sourceSize, x, y, size, 0xFFFFFF);
            case NORMAL -> renderColorPreview(context, plan.surface.normal, sourceSize, x, y, size);
            case EMISSION -> renderScalarPreview(context, plan.surface.emissionMap, sourceSize, x, y, size, 0xFF7A1C);
            case TRANSMISSION -> renderTransmissionPreview(context, x, y, size, plan.surface.transmission);
            case ADVANCED -> renderAdvancedPreview(context, x, y, size, plan);
            default -> {
            }
        }
    }

    private void renderTransmissionPreview(DrawContext context, int x, int y, int size, float value) {
        int alpha = MathHelper.clamp(Math.round(MathHelper.clamp(value, 0.0f, 1.0f) * 180.0f), 24, 180);
        context.fill(x, y, x + size, y + size, (alpha << 24) | 0x66CCFF);
        context.drawBorder(x, y, size, size, RadianceTheme.TEXT_LINK);
    }

    private void renderAdvancedPreview(DrawContext context, int x, int y, int size, MaterialBakePlan plan) {
        float[] values = {
            plan.surface.anisotropic,
            plan.surface.coatWeight,
            plan.surface.coatRoughness,
            plan.surface.sheenWeight
        };
        int barW = Math.max(2, (size - 10) / values.length);
        for (int i = 0; i < values.length; i++) {
            int h = Math.round(MathHelper.clamp(values[i], 0.0f, 1.0f) * (size - 8));
            int bx = x + 5 + i * barW;
            context.fill(bx, y + size - 4 - h, bx + Math.max(1, barW - 2), y + size - 4,
                RadianceTheme.SELECTED_BAR);
        }
    }

    private String channelBadge(Channel channel, int spriteId, MaterialBakePlan plan) {
        if (channel == Channel.ALBEDO) return "Pack";
        if (plan == null || !plan.ok || plan.surface == null) return "";
        return switch (channel) {
            case ROUGHNESS -> plan.surface.roughnessOverride ? "Recipe" : sourceLabel(AutoPbrTextureCatalog.specularSource(spriteId));
            case METAL -> (plan.surface.f0Override || plan.surface.metallicOverride || plan.surface.conductorF0RgbOverride)
                ? "Recipe" : sourceLabel(AutoPbrTextureCatalog.specularSource(spriteId));
            case POROSITY -> plan.surface.sssOverride || plan.surface.subSurfaceExtOverride ? "Recipe" : "Preserved";
            case HEIGHT -> plan.surface.heightOverride ? "Recipe" : sourceLabel(AutoPbrTextureCatalog.normalSource(spriteId));
            case NORMAL -> plan.surface.normalOverride ? "Recipe" : sourceLabel(AutoPbrTextureCatalog.normalSource(spriteId));
            case EMISSION -> plan.surface.emissionOverride ? "Recipe" : sourceLabel(AutoPbrTextureCatalog.specularSource(spriteId));
            case TRANSMISSION -> plan.surface.transmissionOverride || plan.surface.iorOverride
                || plan.surface.thicknessOverride ? "Rule" : "Opaque";
            case ADVANCED -> plan.surface.anisotropicOverride || plan.surface.sheenOverride || plan.surface.coatOverride
                || plan.surface.displacementOverride || plan.surface.uvTransformOverride
                || plan.surface.filterRadiusOverride || plan.surface.mipBiasOverride ? "Rule" : "Off";
            default -> "";
        };
    }

    private String sourceLabel(byte source) {
        return AutoPbrTextureCatalog.sourceLabel(source);
    }

    private void renderImagePreview(DrawContext context, NativeImage image, int x, int y, int size) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return;
        int samples = Math.min(64, Math.max(1, Math.min(image.getWidth(), image.getHeight())));
        for (int py = 0; py < samples; py++) {
            for (int px = 0; px < samples; px++) {
                int sx = Math.min(image.getWidth() - 1, px * image.getWidth() / samples);
                int sy = Math.min(image.getHeight() - 1, py * image.getHeight() / samples);
                fillPreviewCell(context, x, y, size, samples, px, py, image.getColorArgb(sx, sy));
            }
        }
    }

    private void renderScalarPreview(DrawContext context, float[] values, int sourceSize, int x, int y, int size,
                                     int tintRgb) {
        if (values == null || values.length == 0) return;
        int samples = Math.min(64, Math.max(1, sourceSize));
        int tr = (tintRgb >>> 16) & 0xFF;
        int tg = (tintRgb >>> 8) & 0xFF;
        int tb = tintRgb & 0xFF;
        for (int py = 0; py < samples; py++) {
            for (int px = 0; px < samples; px++) {
                int sx = Math.min(sourceSize - 1, px * sourceSize / samples);
                int sy = Math.min(sourceSize - 1, py * sourceSize / samples);
                int index = Math.min(values.length - 1, sy * sourceSize + sx);
                float v = Math.max(0.0f, Math.min(1.0f, values[index]));
                int r = Math.round(tr * v);
                int g = Math.round(tg * v);
                int b = Math.round(tb * v);
                fillPreviewCell(context, x, y, size, samples, px, py,
                    0xFF000000 | (r << 16) | (g << 8) | b);
            }
        }
    }

    private void renderColorPreview(DrawContext context, int[] values, int sourceSize, int x, int y, int size) {
        if (values == null || values.length == 0) return;
        int samples = Math.min(64, Math.max(1, sourceSize));
        for (int py = 0; py < samples; py++) {
            for (int px = 0; px < samples; px++) {
                int sx = Math.min(sourceSize - 1, px * sourceSize / samples);
                int sy = Math.min(sourceSize - 1, py * sourceSize / samples);
                int index = Math.min(values.length - 1, sy * sourceSize + sx);
                fillPreviewCell(context, x, y, size, samples, px, py, values[index]);
            }
        }
    }

    private void fillPreviewCell(DrawContext context, int x, int y, int size, int samples,
                                 int px, int py, int color) {
        int x0 = x + px * size / samples;
        int y0 = y + py * size / samples;
        int x1 = x + (px + 1) * size / samples;
        int y1 = y + (py + 1) * size / samples;
        context.fill(x0, y0, Math.max(x0 + 1, x1), Math.max(y0 + 1, y1), color);
    }

    private void drawSmallLine(DrawContext context, int x, int y, String text) {
        drawSmallLine(context, x, y, text, LEFT_WIDTH - 22);
    }

    private void drawSmallLine(DrawContext context, int x, int y, String text, int availableWidth) {
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal(text), Math.max(32, availableWidth)),
            x, y, RadianceTheme.textSecondary);
    }

    private void drawInspectorLine(DrawContext context, int x, int y, String label, String value) {
        RadianceTheme.drawOutlinedText(context, textRenderer, Text.literal(label + ":"),
            x, y, RadianceTheme.textAccent);
        int labelWidth = Math.min(84, Math.max(52, textRenderer.getWidth(label + ":") + 8));
        RadianceTheme.drawOutlinedText(context, textRenderer,
            RadianceTheme.trimText(textRenderer, Text.literal(value), inspectorW() - labelWidth - 22),
            x + labelWidth, y, RadianceTheme.textPrimary);
    }

    private int drawWrappedSmallLine(DrawContext context, int x, int y, String text, int width, int maxLines) {
        String remaining = text == null ? "" : text;
        int line = 0;
        while (!remaining.isBlank() && line < maxLines) {
            int cut = fitTextIndex(remaining, width);
            String part = remaining.substring(0, cut).stripTrailing();
            drawSmallLine(context, x, y + line * 12, part, width);
            remaining = remaining.substring(cut).stripLeading();
            line++;
        }
        return y + Math.max(1, line) * 12;
    }

    private int fitTextIndex(String text, int width) {
        if (textRenderer.getWidth(text) <= width) return text.length();
        int best = 1;
        for (int i = 1; i <= text.length(); i++) {
            if (textRenderer.getWidth(text.substring(0, i)) > width) break;
            if (Character.isWhitespace(text.charAt(i - 1)) || text.charAt(i - 1) == ',' || text.charAt(i - 1) == ';') {
                best = i;
            }
        }
        return Math.max(1, best);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (SelectionDropdownWidget.handleOverlayClick(mouseX, mouseY, button)) return true;
        if (button == 0) {
            Channel channel = channelAt(mouseX, mouseY);
            if (channel != null) {
                activeChannel = channel;
                rebuildWidgets();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Channel channelAt(double mouseX, double mouseY) {
        int y = height - BOTTOM_STRIP_HEIGHT + 8;
        int tileW = stripTileWidth();
        int x = stripStartX();
        for (Channel channel : Channel.values()) {
            if (hover(mouseX, mouseY, x, y, tileW, BOTTOM_STRIP_HEIGHT - 18)) return channel;
            x += tileW + ROW_GAP;
        }
        return null;
    }

    private void stepSprite(int delta) {
        if (TextureArrayBridge.sortedSpriteIds.isEmpty()) return;
        int index = AutoPbrTextureCatalog.spriteIndex(selectedSprite);
        int count = TextureArrayBridge.sortedSpriteIds.size();
        for (int step = 1; step <= count; step++) {
            int next = Math.floorMod(index + delta * step, count);
            Identifier sprite = TextureArrayBridge.sortedSpriteIds.get(next);
            if (AutoPbrTextureCatalog.isEditableSprite(sprite)) {
                selectSprite(sprite);
                return;
            }
        }
    }

    private boolean hover(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            redo();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            RadianceTheme.peekActive = true;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            recipe = savedSnapshot.copy();
            markPlanDirty();
            applyPreview();
            close();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_M || keyCode == GLFW.GLFW_KEY_I) {
            selectSprite(AutoPbrTexturePicker.pick(MinecraftClient.getInstance()));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            activeChannel = Channel.previous(activeChannel);
            rebuildWidgets();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            activeChannel = Channel.next(activeChannel);
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(recipe.copy());
        recipe = undo.pop();
        markPlanDirty();
        status = "Dirty";
        applyPreview();
        rebuildWidgets();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(recipe.copy());
        recipe = redo.pop();
        markPlanDirty();
        status = "Dirty";
        applyPreview();
        rebuildWidgets();
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
        SelectionDropdownWidget.clearInstances();
        RadianceTheme.peekActive = false;
        RadianceTheme.endSliderFocus();
        MinecraftClient.getInstance().setScreen(parent);
    }

    private int mainX() {
        return LEFT_WIDTH + PAD * 2;
    }

    private int mainPanelY() {
        return HEADER_HEIGHT + PAD;
    }

    private int previewLaneY() {
        return mainPanelY() + MAIN_TITLE_HEIGHT;
    }

    private int previewLaneHeight() {
        return PREVIEW_LANE_HEIGHT - 28;
    }

    private int histogramY() {
        return previewLaneY() + previewLaneHeight() + ROW_GAP;
    }

    private int controlsTop() {
        return histogramY() + HISTOGRAM_HEIGHT + ROW_GAP;
    }

    private int inspectorW() {
        if (width < 980) return Math.max(210, width / 4);
        return INSPECTOR_WIDTH;
    }

    private int inspectorX() {
        return width - inspectorW() - PAD;
    }

    private int mainW() {
        return Math.max(220, inspectorX() - mainX() - PAD);
    }

    private int stripTileWidth() {
        int count = Channel.values().length;
        int available = width - PAD * 2 - ROW_GAP * (count - 1);
        return MathHelper.clamp(available / count, 58, 92);
    }

    private int stripStartX() {
        int count = Channel.values().length;
        int total = count * stripTileWidth() + (count - 1) * ROW_GAP;
        return Math.max(PAD, (width - total) / 2);
    }

    private int opacitySliderWidth() {
        return Math.min(210, Math.max(150, width / 9));
    }

    private int opacitySliderX() {
        return Math.max(PAD, width - opacitySliderWidth() - 112);
    }

    private String fmt(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private int labBg(int color) {
        return RadianceTheme.withAlpha(color & 0x00FFFFFF, Options.materialLabOpacityPercent / 100.0f);
    }

    private float luminancePreview(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return r * 0.2126f + g * 0.7152f + b * 0.0722f;
    }

    private String dirtySummary() {
        List<String> channels = new java.util.ArrayList<>();
        if (recipe.roughnessOverride || recipe.generateRoughness) channels.add("Rough");
        if (recipe.metallicOverride || recipe.f0Override || recipe.conductorF0RgbOverride
            || recipe.oxideAmount > 0.0001f || recipe.oxideRoughnessInfluence > 0.0001f) channels.add("Metal/F0");
        if (recipe.porosityOverride) channels.add("Porosity/SSS");
        if (recipe.heightOverride || recipe.generateHeight) channels.add("Height");
        if (recipe.normalStrengthOverride || recipe.generateNormal) channels.add("Normal");
        if (recipe.emissionOverride) channels.add("Emission");
        if (recipe.iorOverride || recipe.transmissionOverride
            || recipe.thicknessMin > 0.0001f || recipe.thicknessMax < 0.9999f
            || Math.abs(recipe.thicknessGamma - 1.0f) > 0.0001f) channels.add("Transmit");
        if (recipe.anisotropicOverride || recipe.sheenOverride || recipe.coatOverride || recipe.displacementOverride
            || Math.abs(recipe.uvScale - 1.0f) > 0.0001f || Math.abs(recipe.uvOffset) > 0.0001f
            || recipe.filterRadius > 0.0001f || Math.abs(recipe.mipBias) > 0.0001f) {
            channels.add("Advanced");
        }
        return channels.isEmpty() ? "none" : String.join(", ", channels);
    }

    private String resetFields(Channel channel) {
        return switch (channel) {
            case ALBEDO -> "no recipe fields";
            case ROUGHNESS -> "source, generator, levels, cleanup, overlays";
            case METAL -> "source, mask, code, F0, conductor RGB, oxide";
            case POROSITY -> "mode, source, levels, porosity, SSS tint/radius";
            case HEIGHT -> "source, generator, levels, cleanup, scale";
            case NORMAL -> "source, combine, kernel, strength, orientation";
            case EMISSION -> "mask source, thresholds, cleanup, gain";
            case TRANSMISSION -> "IOR, transmission, absorption, volume, thickness shape";
            case ADVANCED -> "anisotropy, coat, sheen, UV/filter/mip/displacement rules";
        };
    }

    private String usage(Identifier sprite) {
        int count = AutoPbrUsageIndex.usageCount(MinecraftClient.getInstance(), sprite);
        return count <= 0 ? "unknown" : Integer.toString(count);
    }

    private static final class MaterialLabButtonWidget extends ClickableWidget {
        private final Runnable action;

        private MaterialLabButtonWidget(int x, int y, int width, int height, Text message, Runnable action) {
            super(x, y, width, height, message);
            this.action = action;
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            float alphaMult = RadianceTheme.inactiveFadeFactor();
            if (alphaMult <= 0.0f) return;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            var renderer = MinecraftClient.getInstance().textRenderer;
            boolean hovered = active && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

            if (active) {
                int bg = hovered ? RadianceTheme.buttonHover : RadianceTheme.buttonBg;
                context.fill(x, y, x + w, y + h, RadianceTheme.scaleAlpha(bg, alphaMult));
                context.drawBorder(x, y, w, h, RadianceTheme.scaleAlpha(RadianceTheme.buttonBorder, alphaMult));
                Text drawMessage = RadianceTheme.trimText(renderer, getMessage(), Math.max(16, w - 10));
                int textW = renderer.getWidth(drawMessage);
                RadianceTheme.drawOutlinedText(context, renderer, drawMessage,
                    x + (w - textW) / 2, y + (h - 8) / 2,
                    hovered ? RadianceTheme.textPrimary : RadianceTheme.textSecondary, alphaMult);
            } else {
                context.fill(x, y, x + w, y + h, RadianceTheme.withAlpha(0x1A1A1A, 0.35f * alphaMult));
                context.drawBorder(x, y, w, h, RadianceTheme.withAlpha(0x303030, 0.45f * alphaMult));
                Text drawMessage = RadianceTheme.trimText(renderer, getMessage(), Math.max(16, w - 10));
                int textW = renderer.getWidth(drawMessage);
                RadianceTheme.drawOutlinedText(context, renderer, drawMessage,
                    x + (w - textW) / 2, y + (h - 8) / 2,
                    RadianceTheme.textSecondary, alphaMult * 0.45f);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (active) {
                playDownSound(MinecraftClient.getInstance().getSoundManager());
                action.run();
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private record Option(String label, String value) {
    }

    private record PreviewLane(String key, int tintRgb) {
    }

    private enum Channel {
        ALBEDO("Albedo", "Albedo", "Pack albedo and Minecraft alpha coverage"),
        ROUGHNESS("Rough", "Roughness", "Procedural roughness map controls"),
        METAL("Metal/F0", "Metal / F0", "Measured metals, LabPBR metal codes, and F0"),
        POROSITY("Porosity/SSS", "Porosity / SSS", "Backed LabPBR _s.B packing"),
        HEIGHT("Height", "Height", "LabPBR normal alpha height map controls"),
        NORMAL("Normal", "Normal", "Pack/generated normal blending"),
        EMISSION("Emission", "Emission", "Manual emission mask recipe"),
        TRANSMISSION("Transmit", "Transmission / IOR", "RadSER transmission and IOR rules"),
        ADVANCED("Advanced", "Advanced", "Backed optics and sampler/material rule controls");

        private final String shortLabel;
        private final String label;
        private final String detail;

        Channel(String shortLabel, String label, String detail) {
            this.shortLabel = shortLabel;
            this.label = label;
            this.detail = detail;
        }

        String shortLabel() {
            return shortLabel;
        }

        String label() {
            return label;
        }

        String detail() {
            return detail;
        }

        static Channel previous(Channel current) {
            Channel[] values = values();
            return values[Math.floorMod(current.ordinal() - 1, values.length)];
        }

        static Channel next(Channel current) {
            Channel[] values = values();
            return values[Math.floorMod(current.ordinal() + 1, values.length)];
        }
    }
}
