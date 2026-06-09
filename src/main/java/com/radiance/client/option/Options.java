package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.util.EmissiveBlock;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

public class Options {

    public static final String OPTION_PROPERTIES = "options.properties";
    public static final int CURRENT_OPTIONS_VERSION = 24;
    public static final int SDR_TONEMAPPING_DEFAULT_MODE = 0;
    public static final int SATURATION_DEFAULT_PERCENT = 100;

    // SDR transfer function
    public static final int SDR_TRANSFER_FUNCTION_GAMMA_22 = 0;
    public static final int SDR_TRANSFER_FUNCTION_SRGB = 1;

    public static final String CATEGORY_GAMEPLAY = "options.video.category.gameplay";
    public static final String CATEGORY_WINDOW = "options.video.category.window";
    public static final String CATEGORY_RAY_TRACING = "options.video.category.ray_tracing";
    public static final String CATEGORY_SHADER_PACKS = "options.video.category.shader_packs";
    public static final String CATEGORY_UPSCALER = "options.video.category.upscaler";
    public static final String CATEGORY_TONEMAPPING = "options.video.category.tonemapping";
    public static final String CATEGORY_CAMERA_CONTROLS = "options.video.category.camera_controls";
    public static final String CATEGORY_POST_PROCESSING = "options.video.category.post_processing";
    public static final String CATEGORY_TERRAIN = "options.video.category.terrain";
    public static final String CATEGORY_HDR = "options.video.category.hdr";
    public static final String CATEGORY_PIPELINE = "options.video.category.pipeline";
    public static final String CATEGORY_ENVIRONMENT = "options.video.category.environment";

    public static final String KEY_RADIANCE_SETTINGS = "key.radiance.settings";
    public static final String KEY_AUTOPBR_PICKER = "key.radiance.autopbr_picker";
    public static final String KEY_CATEGORY_RADIANCE = "key.category.radiance";

    public static final int FALLBACK_AUTOPBR_ORDINAL = 255;

    public static final String CATEGORY_FPV = "options.video.category.fpv";
    public static final String FPV_ENABLED_KEY = "options.video.fpv_enabled";
    public static final String FPV_OFFSET_FORWARD_KEY = "options.video.fpv_offset_forward";
    public static final String FPV_OFFSET_VERTICAL_KEY = "options.video.fpv_offset_vertical";
    public static final String FPV_OFFSET_LATERAL_KEY = "options.video.fpv_offset_lateral";

    public static final String CATEGORY_EMISSION = "options.video.category.emission";
    public static final String CATEGORY_LIGHTING = "options.video.category.lighting";
    public static final String EXPOSURE_SETTINGS_KEY = "options.video.exposure_settings";
    public static final String POST_PROCESSING_SETTINGS_KEY = "options.video.post_processing_settings";

    public static final String ENVIRONMENT_SETTINGS_KEY = "options.video.environment_settings";
    public static final String ENVIRONMENT_DIMENSION_KEY = "options.video.environment.dimension";
    public static final String ENVIRONMENT_DIMENSION_OVERWORLD = "options.video.environment.dimension.overworld";
    public static final String ENVIRONMENT_DIMENSION_NETHER = "options.video.environment.dimension.nether";
    public static final String ENVIRONMENT_DIMENSION_END = "options.video.environment.dimension.end";

    public static final int DIM_OVERWORLD = 0;
    public static final int DIM_NETHER = 1;
    public static final int DIM_END = 2;
    public static final int DIM_COUNT = 3;

    public static final int PERCENT_DEFAULT = 100;
    public static final int MOON_INTENSITY_DEFAULT_OVERWORLD_PERCENT = 100;  // physical moon value (0.1 lux) already accounts for dimness
    public static final int WATER_TINT_R_DEFAULT = 0;
    public static final int WATER_TINT_G_DEFAULT = 48;
    public static final int WATER_TINT_B_DEFAULT = 65;

    // Clouds
    // Defaults are intentionally non-neutral so volumetric clouds have visible structure out of the box.
    public static final int CLOUD_DETAIL_SCALE_DEFAULT_PERCENT = 100;
    public static final int CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT = 100;

    // Tonemapping
    public static final String TONEMAP_MODE_KEY = "options.video.tonemap_mode";
    public static final String SDR_TRANSFER_FUNCTION_KEY = "options.video.sdr_transfer_function";
    public static final String SDR_TRANSFER_FUNCTION_GAMMA_22_KEY = "options.video.sdr_transfer_function.gamma22";
    public static final String SDR_TRANSFER_FUNCTION_SRGB_KEY = "options.video.sdr_transfer_function.srgb";
    public static final String TONEMAP_MODE_PBR_NEUTRAL = "options.video.tonemap_mode.pbr_neutral";
    public static final String TONEMAP_MODE_REINHARD_EXTENDED = "options.video.tonemap_mode.reinhard_extended";
    public static final String TONEMAP_MODE_ACES = "options.video.tonemap_mode.aces";
    public static final String TONEMAP_MODE_AGX = "options.video.tonemap_mode.agx";
    public static final String TONEMAP_MODE_LOTTES = "options.video.tonemap_mode.lottes";
    public static final String TONEMAP_MODE_FROSTBITE = "options.video.tonemap_mode.frostbite";
    public static final String TONEMAP_MODE_UNCHARTED2 = "options.video.tonemap_mode.uncharted2";
    public static final String TONEMAP_MODE_GT = "options.video.tonemap_mode.gt";
    public static final String TONEMAP_MODE_PSYCHOVISUAL = "options.video.tonemap_mode.psychovisual";
    // HDR tonemappers
    public static final String HDR_TONEMAP_MODE_KEY = "options.video.hdr_tonemap_mode";
    public static final String HDR_TONEMAP_PSYCHOVISUAL = "options.video.hdr_tonemap_mode.psychovisual";
    public static final String HDR_TONEMAP_BT2390 = "options.video.hdr_tonemap_mode.bt2390";
    // HDR tonemappers (shown when hdrEnabled=true)
    public static final String TONEMAP_MODE_HDR_HERMITE_REINHARD = "options.video.tonemap_mode.hdr_hermite_reinhard";
    public static final String TONEMAP_MODE_HDR_REINHARD_EXTENDED = "options.video.tonemap_mode.hdr_reinhard_extended";
    public static final String TONEMAP_MODE_HDR_BT2390 = "options.video.tonemap_mode.hdr_bt2390";
    public static final String TONEMAP_MODE_HDR_FROSTBITE = "options.video.tonemap_mode.hdr_frostbite";
    public static final String MIN_EXPOSURE_KEY = "options.video.min_exposure";
    public static final String MAX_EXPOSURE_KEY = "options.video.max_exposure";
    public static final String EXPOSURE_COMPENSATION_KEY = "options.video.exposure_compensation";
    public static final String MANUAL_EXPOSURE_ENABLED_KEY = "options.video.manual_exposure_enabled";
    public static final String MANUAL_EXPOSURE_KEY = "options.video.manual_exposure";
    public static final String BRIGHT_ADAPT_SPEED_KEY = "options.video.bright_adapt_speed";
    public static final String DARK_ADAPT_SPEED_KEY = "options.video.dark_adapt_speed";
    public static final String SCENE_CHANGE_THRESHOLD_KEY = "options.video.scene_change_threshold";
    public static final String CENTER_WEIGHT_STRENGTH_KEY = "options.video.center_weight_strength";
    public static final String HIGHLIGHT_WEIGHT_KEY = "options.video.highlight_weight";
    public static final String MIDDLE_GREY_KEY = "options.video.middle_grey";
    public static final String LWHITE_KEY = "options.video.lwhite";
    public static final String SATURATION_KEY = "options.video.saturation";
    public static final String SHARPENER_MODE_KEY = "options.video.sharpener_mode";
    public static final String CAS_SHARPNESS_KEY = "options.video.cas_sharpness";

    // PsychoV tonemapper
    public static final String PSYCHO_ENABLED_KEY = "options.video.psycho.enabled";
    public static final String PSYCHO_HIGHLIGHTS_KEY = "options.video.psycho.highlights";
    public static final String PSYCHO_SHADOWS_KEY = "options.video.psycho.shadows";
    public static final String PSYCHO_CONTRAST_KEY = "options.video.psycho.contrast";
    public static final String PSYCHO_PURITY_KEY = "options.video.psycho.purity";
    public static final String PSYCHO_BLEACHING_KEY = "options.video.psycho.bleaching";
    public static final String PSYCHO_CLIP_POINT_KEY = "options.video.psycho.clip_point";
    public static final String PSYCHO_HUE_RESTORE_KEY = "options.video.psycho.hue_restore";
    public static final String PSYCHO_ADAPT_CONTRAST_KEY = "options.video.psycho.adapt_contrast";
    public static final String PSYCHO_WHITE_CURVE_KEY = "options.video.psycho.white_curve";
    public static final String PSYCHO_CONE_EXPONENT_KEY = "options.video.psycho.cone_exponent";
    public static final String PSYCHO_PEAK_SDR_KEY = "options.video.psycho.peak_sdr";
    public static final String CATEGORY_PSYCHO = "options.video.psycho.category";

    // HDR10
    public static final String HDR_ENABLED_KEY = "options.video.hdr_enabled";
    public static final String HDR_PEAK_NITS_KEY = "options.video.hdr_peak_nits";
    public static final String HDR_PAPER_WHITE_NITS_KEY = "options.video.hdr_paper_white_nits";

    // Upscaler (Off / DLSS SR)
    public static final String UPSCALER_MODE_KEY = "options.video.upscaler_mode";
    public static final String UPSCALER_MODE_OFF = "options.video.upscaler_mode.off";
    public static final String UPSCALER_MODE_DLSS_SR = "options.video.upscaler_mode.dlss_sr";

    // Upscaler Quality (applies to DLSS, FSR, and future upscalers)
    public static final String UPSCALER_QUALITY_KEY = "options.video.upscaler_quality";
    public static final String UPSCALER_QUALITY_PERFORMANCE = "options.video.upscaler_quality.performance";
    public static final String UPSCALER_QUALITY_BALANCED = "options.video.upscaler_quality.balanced";
    public static final String UPSCALER_QUALITY_QUALITY = "options.video.upscaler_quality.quality";
    public static final String UPSCALER_QUALITY_NATIVE = "options.video.upscaler_quality.native";
    public static final String UPSCALER_QUALITY_CUSTOM = "options.video.upscaler_quality.custom";
    public static final String UPSCALER_RES_OVERRIDE_KEY = "options.video.upscaler_res_override";
    public static final String UPSCALER_PRESET_KEY = "options.video.upscaler_preset";
    public static final String OUTPUT_SCALE_2X_KEY = "options.video.output_scale_2x";

    // VSync + NVIDIA Reflex
    public static final String VSYNC_KEY = "options.video.radiance_vsync";
    public static final String REFLEX_ENABLED_KEY = "options.video.reflex_enabled";
    public static final String REFLEX_BOOST_KEY = "options.video.reflex_boost";
    public static final String VRR_MODE_KEY = "options.video.vrr_mode";
    public static final String MAX_FPS_KEY = "options.video.max_fps";
    public static final String INACTIVITY_FPS_KEY = "options.video.inactivity_fps_limit";

    // Frame Generation (DLSS-G)
    public static final String FRAME_GEN_MODE_KEY = "options.video.frame_gen_mode";
    public static final String FRAME_GEN_MULTIPLIER_KEY = "options.video.frame_gen_multiplier";

    // DLSS-D (Ray Reconstruction)
    public static final String DLSS_D_ENABLED_KEY = "options.video.dlss_d_enabled";

    // Sun/Moon orbit
    public static final String SUN_PATH_MODE_KEY = "options.video.environment.sun_path_mode";
    public static final String SUN_PATH_MODE_LEGACY = "options.video.environment.sun_path_mode.legacy";
    public static final String SUN_PATH_MODE_PHYSICAL = "options.video.environment.sun_path_mode.physical";
    public static final String SUN_INCLINATION_KEY = "options.video.environment.sun_inclination";
    public static final String SUN_AZIMUTH_OFFSET_KEY = "options.video.environment.sun_azimuth_offset";
    public static final String MOON_FOLLOW_SUN_KEY = "options.video.environment.moon_follow_sun";
    public static final String MOON_INCLINATION_KEY = "options.video.environment.moon_inclination";
    public static final String MOON_AZIMUTH_OFFSET_KEY = "options.video.environment.moon_azimuth_offset";

    // Ray Tracing
    public static final String RAY_BOUNCES_KEY = "options.video.ray_bounces";
    public static final String SIMPLIFIED_INDIRECT_KEY = "options.video.simplified_indirect";
    public static final String MULTI_SCATTER_GGX_KEY = "options.video.multi_scatter_ggx";
    public static final String EON_DIFFUSE_KEY = "options.video.eon_diffuse";
    public static final String DIFFUSE_MODEL_KEY = "options.video.diffuse_model";
    public static final String SHARC_ENABLED_KEY = "options.video.sharc_enabled";
    public static final String SHARC_SCENE_SCALE_KEY = "options.video.sharc_scene_scale";
    public static final String SHARC_ROUGHNESS_THRESHOLD_KEY = "options.video.sharc_roughness_threshold";
    public static final String SHARC_ACCUMULATION_FRAMES_KEY = "options.video.sharc_accumulation_frames";
    public static final String SHARC_STALE_FRAMES_KEY = "options.video.sharc_stale_frames";
    public static final String SHARC_DOWNSCALE_KEY = "options.video.sharc_downscale";
    public static final String SHARC_UPDATE_BLOCK_SIZE_KEY = "options.video.sharc_update_block_size";
    public static final String SHARC_UPDATE_BOUNCES_KEY = "options.video.sharc_update_bounces";
    public static final String SHARC_CAPACITY_EXPONENT_KEY = "options.video.sharc_capacity_exponent";
    public static final String SHARC_QUALITY_PRESET_KEY = "options.video.sharc_quality_preset";
    public static final String SHARC_QUERY_MODE_KEY = "options.video.sharc_query_mode";
    public static final String CATEGORY_SHARC = "options.video.category.sharc";
    public static final String SHADER_PACK_KEY = "options.video.shader_pack";
    public static final String SHADER_PACK_STATUS_KEY = "options.video.shader_pack.status";
    // Terrain
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";
    public static final String CHUNK_CULL_DISTANCE_KEY = "options.video.chunk_cull_distance";

    // Pipeline
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";

    // Fields
    public static int optionsVersion = CURRENT_OPTIONS_VERSION;
    public static int maxFps = 260;
    public static int inactivityFpsLimit = 260;
    public static boolean vsync = true;
    // Upscaler selection (menu-facing)
    // 0 = DLSS-RR (Ray Reconstruction)
    // 2 = Off
    public static int upscalerMode = 0;
    public static int upscalerQuality = 2;  // 0=Performance, 1=Balanced, 2=Quality, 3=Native/DLAA, 4=Custom
    public static int upscalerResOverride = 99; // 1-100%
    public static boolean dlssDEnabled = true;
    public static int rayBounces = 2;
    public static boolean simplifiedIndirect = false;
    public static boolean multiScatterGGX = true;  // Kulla-Conty multi-scatter GGX energy compensation
    public static boolean eonDiffuse = true;        // EON energy-preserving rough diffuse BRDF
    public static final int DIFFUSE_MODEL_EON = 0;
    public static final int DIFFUSE_MODEL_VMF = 1;
    public static final int DIFFUSE_MODEL_LEGACY = 2;
    public static int diffuseModel = DIFFUSE_MODEL_EON;
    public static String shaderPackPath = Pipeline.VANILLA_RAY_TRACING_SHADER_PACK_PATH;

    // Window persistence
    public static int windowPosX = -1;  // -1 = not set (use OS default)
    public static int windowPosY = -1;
    public static int windowWidth = -1;
    public static int windowHeight = -1;
    public static boolean windowRestoreSucceeded = false;
    public static int pendingWindowX = -1, pendingWindowY = -1; // deferred position set (OS needs time after resize)
    public static boolean suppressResizeCallback = false;

    // Diagnostics
    public static boolean loggingEnabled = false;
    public static native void nativeSetLoggingEnabled(boolean enabled, boolean write);
    public static boolean gpuDebugLabels = false;
    public static native void nativeSetGpuDebugLabels(boolean enabled, boolean write);

    // Cube-block height-field geometry displacement. Legacy POM keys are load aliases only.
    public static final int DISPLACEMENT_QUALITY_LOW = 1;
    public static final int DISPLACEMENT_QUALITY_BALANCED = 2;
    public static final int DISPLACEMENT_QUALITY_HIGH = 3;
    public static final int DISPLACEMENT_QUALITY_ULTRA = 4;
    public static boolean displacementEnabled = false;
    public static int displacementDepthCapPercent = 5;  // 1-50, /100 = 0.01-0.50 blocks
    public static int displacementQuality = DISPLACEMENT_QUALITY_BALANCED;
    public static int displacementFadeDistance = 64;    // 8-256 blocks
    public static int displacementSteps = 32;
    public static int displacementRefinement = 4;
    public static int tessMaxLevel = 16;
    public static int tessNearDist = 32;
    public static int tessMidDist = 96;
    public static int tessFarDist = 192;
    // Legacy runtime aliases. Keep in sync for old callers/configs.
    public static boolean pomEnabled = false;
    public static int pomHeightScalePercent = displacementDepthCapPercent;
    public static int pomSteps = displacementSteps;
    public static int pomRefinement = displacementRefinement;
    public static int pomFadeDistance = displacementFadeDistance;
    // SER: Shader Execution Reordering — groups threads by material for cache coherence
    // V2 engine: use new EngineApp path instead of legacy Renderer (clear-screen only for now)
    public static boolean useV2Engine = false;

    // serEnabled: toggle hit-object reordering (requires VK_EXT_ray_tracing_invocation_reorder)
    // serHints: explicit geometry-based coherence hints (additional 10-20% on top of basic SER)
    public static boolean serEnabled = true;
    public static boolean serHintsEnabled = true;
    public static boolean sharcEnabled = true;
    public static int sharcSceneScaleTenths = 200;          // 10-200 → 1.0-20.0
    public static int sharcRoughnessThresholdPercent = 70;  // 0-100 → 0.0-1.0
    public static int sharcAccumulationFrames = 32;         // 4-256
    public static int sharcStaleFrames = 16;                // 4-128
    public static int sharcDownscale = 1;                   // 1-8
    public static int sharcUpdateBlockSize = 2;             // 2-8 (NxN sparse block)
    public static int sharcUpdateBounces = 4;                // 2-8 (max bounces in update pass)
    public static int sharcCapacityExponent = 21;           // 18-24 (2^N entries)
    public static int sharcQualityPreset = 5;               // 0=Low, 1=Medium, 2=High, 3=Ultra, 4=Overkill, 5=Custom
    public static int sharcQueryMode = 0;                    // 0=Off, 1=Observe, 2=Active

    // ── Overall Quality Preset (simple mode master slider) ──
    public static int overallQualityPreset = 4;              // 0=Low, 1=Medium, 2=High, 3=Ultra, 4=Custom
    public static final String[] OVERALL_QUALITY_NAMES = {"Low", "Medium", "High", "Ultra", "Custom"};
    public static final String OVERALL_QUALITY_KEY = "radiance.settings.overall_quality";

    /**
     * Apply master quality preset. Sets ray bounces, SHARC preset, and upscaler quality together.
     * Custom (4) does nothing.
     */
    public static void applyOverallPreset(int preset, boolean write) {
        overallQualityPreset = preset;
        if (preset == 4) return; // Custom — don't change anything
        // {rayBounces, sharcPreset, upscalerQuality}
        int[][] presets = {
            {1, 0, 0},  // Low:    1 bounce, SHARC Low, Performance
            {2, 1, 1},  // Medium: 2 bounces, SHARC Medium, Balanced
            {4, 2, 2},  // High:   4 bounces, SHARC High, Quality
            {8, 3, 3},  // Ultra:  8 bounces, SHARC Ultra, Native
        };
        int[] p = presets[preset];
        setRayBounces(p[0], false);
        applySharcPreset(p[1], false);
        setUpscalerQuality(p[2], false);
        if (write) overwriteConfig();
    }

    /**
     * Detect which overall quality preset matches the current settings.
     * Returns 4 (Custom) if no preset matches.
     */
    public static int detectOverallPreset() {
        int[][] presets = {
            {1, 0, 0}, {2, 1, 1}, {4, 2, 2}, {8, 3, 3},
        };
        for (int i = 0; i < presets.length; i++) {
            if (rayBounces == presets[i][0]
                    && sharcQualityPreset == presets[i][1]
                    && upscalerQuality == presets[i][2]) {
                return i;
            }
        }
        return 4; // Custom
    }

    /**
     * Count how many advanced-only settings are non-default.
     * Used by simple mode to show a "N advanced settings modified" badge.
     */
    public static int countNonDefaultAdvancedSettings() {
        int count = 0;
        // SHARC individual params (only if preset is Custom)
        if (sharcQualityPreset == 5) {
            if (sharcSceneScaleTenths != 40) count++;
            if (sharcRoughnessThresholdPercent != 25) count++;
            if (sharcAccumulationFrames != 32) count++;
            if (sharcStaleFrames != 16) count++;
            if (sharcUpdateBlockSize != 5) count++;
            if (sharcUpdateBounces != 4) count++;
            if (sharcCapacityExponent != 21) count++;
        }
        // PsychoV (all non-default values)
        if (psychoHighlightsPercent != 100) count++;
        if (psychoShadowsPercent != 100) count++;
        if (psychoContrastPercent != 100) count++;
        if (psychoPurityPercent != 100) count++;
        if (psychoBleachingPercent != 0) count++;
        if (psychoHueRestorePercent != 0) count++;
        // Exposure tuning
        if (brightAdaptSpeedTenths != 5) count++;
        if (darkAdaptSpeedTenths != 20) count++;
        if (sceneChangeThresholdTenths != 50) count++;
        if (centerWeightPercent != 0) count++;
        // Multi-scatter / diffuse model (defaults: multi-scatter on, EON diffuse)
        if (!multiScatterGGX) count++;
        if (diffuseModel != DIFFUSE_MODEL_EON) count++;
        // Material-owned shader displacement (default disabled)
        if (displacementEnabled) count++;
        // SER (default enabled)
        if (!serEnabled) count++;
        if (!serHintsEnabled) count++;
        return count;
    }

    /** SHARC quality preset definitions. Each row: {sceneScaleTenths, roughnessPercent, accumFrames,
     *  staleFrames, downscale, updateBlockSize, updateBounces, capacityExponent} */
    public static final int[][] SHARC_PRESETS = {
        // Low:      coarse grid, sparse updates, small cache
        {60, 30, 24, 12, 1, 7, 3, 20},
        // Medium:   balanced (current defaults)
        {40, 25, 32, 16, 1, 5, 4, 21},
        // High:     finer grid, denser updates, larger cache
        {30, 20, 40, 20, 1, 4, 5, 22},
        // Ultra:    fine grid, dense updates, 8M entries
        {20, 15, 48, 24, 1, 3, 6, 23},
        // Overkill: finest grid, very dense updates, 16M entries
        {15, 10, 64, 32, 1, 2, 8, 24},
    };
    public static final String[] SHARC_PRESET_NAMES = {"Low", "Medium", "High", "Ultra", "Overkill", "Custom"};
    public static final String[] SHARC_QUERY_MODE_NAMES = {"Off", "Observe", "Active"};
    // Offline accumulation mode (transient runtime state — NOT persisted except preferences)
    public static int offlineState = 0;          // 0=NORMAL, 1=FREE, 2=ACCUMULATING
    public static long frozenDayTimeTicks = -1;  // captured on F7
    public static double frozenCamX, frozenCamY, frozenCamZ;
    public static float frozenCamYaw, frozenCamPitch;
    // Offline preferences (persisted)
    public static boolean offlineGroundTruth = false; // unbiased path tracing mode
    // Individual shader quality toggles (controlled by Ground Truth preset or independently)
    public static boolean beerLawShadows = true;
    public static boolean noEmissionClamp = false;
    public static boolean physicalSunDisk = true;
    public static boolean noHandAmbient = false;
    public static boolean entityNormalsEnabled = true;
    public static void setEntityNormalsEnabled(boolean enabled, boolean write) {
        entityNormalsEnabled = enabled;
        nativeSetEntityNormalsEnabled(enabled, false);
        if (write) overwriteConfig();
    }
    public static int offlineBounces = 16;           // 1-128
    public static boolean offlineDisableRR = false;
    public static boolean offlineDisableClamp = false;
    public static float offlineFocalDistance = 10.0f;  // 0.5-256 blocks (1/16th precision)
    public static boolean offlineNativeRes = false;  // force render-res = display-res
    public static int offlineDenoised = 0;           // 0=Raw Fast (RR on), 1=Raw Accurate (RR off), 2=Denoised (epoch-based)
    public static int dlssEpochLength = 16;          // frames per Denoised epoch (4-64)
    // Camera model (persisted) — industry standard f-stop / focal length / sensor
    public static int sensorPreset = 0;              // 0=FF, 1=APS-C, 2=M4/3, 3=MF
    public static float sensorWidthMM = 36.0f;       // sensor width in mm
    public static float sensorHeightMM = 24.0f;      // sensor height in mm
    public static int focalLengthMM = 50;            // 14-200mm
    public static float fStop = 5.6f;                // f/1.4 - f/22
    public static int dofStrengthPercent = 100;      // 100-2000 (1.0x-20.0x artistic DOF multiplier)
    // First-person view (renders third-person body model in first person)
    public static boolean fpvEnabled = true;          // show body in first person
    public static int fpvOffsetForward = -20;          // forward offset in centimetres (-30 to 30)
    public static int fpvOffsetVertical = 0;          // vertical offset in centimetres (-30 to 30, positive = down)
    public static int fpvOffsetLateral = 0;           // lateral offset in centimetres (-20 to 20, positive = right)
    // Freecam (persisted preferences)
    public static boolean freecamEnabled = true;     // true=freecam, false=stick to player
    public static float freecamSpeed = 10.0f;        // 0.1-50.0 movement speed multiplier
    public static boolean freecamShowPlayer = false;  // show player model in freecam
    // Freecam transient state — use Options.freecam.x/y/z/yaw/pitch instead
    // HUD transient state
    public static boolean suppressHudOverlay = false; // suppress HUD for one frame (screenshot)
    public static long accumStartTimeNanos = 0;       // System.nanoTime() when accumulation started
    public static int focusMode = 0;                    // 0=MF, 1=AF-S, 2=AF-C
    public static final String[] FOCUS_MODE_NAMES = {"MF", "AF-S", "AF-C"};
    public static String focusToastMessage = null;       // transient toast text (null = hidden)
    public static long focusToastExpireMs = 0;           // System.currentTimeMillis() when toast expires
    public static int focusToastColor = 0xFFFFFF;        // toast text color

    /** Show a temporary toast notification on the HUD. Visible in all game states. */
    public static void setFocusToast(String message, int color, int durationMs) {
        focusToastMessage = message;
        focusToastColor = color;
        focusToastExpireMs = System.currentTimeMillis() + durationMs;
    }

    // Freecam instance (transient)
    public static com.radiance.client.input.FreecamState freecam = new com.radiance.client.input.FreecamState();

    // Sensor preset data: {widthMM, heightMM}
    public static final float[][] SENSOR_PRESETS = {
        {36.0f, 24.0f},   // Full Frame
        {23.6f, 15.6f},   // APS-C
        {17.3f, 13.0f},   // Micro 4/3
        {44.0f, 33.0f},   // Medium Format
    };
    public static final String[] SENSOR_PRESET_NAMES = {"Full Frame", "APS-C", "Micro 4/3", "Medium Format"};

    /** Compute aperture radius in blocks from focal length and f-stop. */
    public static float computeApertureRadius() {
        if (fStop >= 22.0f) return 0.0f; // pinhole
        return (float) focalLengthMM / (2.0f * fStop * 1000.0f);
    }

    /** Compute vertical FOV in radians from sensor height and focal length. */
    public static float computeFovVerticalRad() {
        return 2.0f * (float) Math.atan(sensorHeightMM / (2.0 * focalLengthMM));
    }

    /** Compute vertical FOV in degrees. */
    public static float computeFovVerticalDeg() {
        return (float) Math.toDegrees(computeFovVerticalRad());
    }

    /** Apply sensor preset by index. */
    public static void applySensorPreset(int index) {
        if (index >= 0 && index < SENSOR_PRESETS.length) {
            sensorPreset = index;
            sensorWidthMM = SENSOR_PRESETS[index][0];
            sensorHeightMM = SENSOR_PRESETS[index][1];
        }
    }

    /** Sync computed aperture to C++ via existing JNI. */
    public static void syncApertureToNative() {
        try {
            nativeSetOfflineAperture(computeApertureRadius(), false);
        } catch (UnsatisfiedLinkError ignored) {}
    }

    public static void setSensorPreset(int index, boolean write) {
        applySensorPreset(index);
        syncApertureToNative();
        if (write) overwriteConfig();
    }

    public static void setFocalLengthMM(int value, boolean write) {
        focalLengthMM = clamp(value, 14, 200);
        syncApertureToNative();
        if (write) overwriteConfig();
    }

    public static void setFStop(float value, boolean write) {
        fStop = Math.max(1.4f, Math.min(22.0f, value));
        syncApertureToNative();
        if (write) overwriteConfig();
    }

    public static void setFocusMode(int mode, boolean write) {
        focusMode = clamp(mode, 0, 2);
        if (write) overwriteConfig();
    }

    public static void setOfflineFocalDistance(float dist, boolean write) {
        offlineFocalDistance = Math.max(0.5f, Math.min(256.0f, dist));
        try {
            nativeSetOfflineFocalDistance(offlineFocalDistance, write);
        } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static boolean outputScale2x = false;
    public static boolean reflexEnabled = false;
    public static boolean reflexBoost = false;
    public static boolean vrrMode = false;
    private static boolean reflexExplicitlyConfigured = false; // true if user saved a preference

    // Frame Generation (DLSS-G)
    public static int frameGenMode = 0;          // 0=Off, 1=On, 2=Auto (dynamic MFG)
    public static int frameGenMultiplier = 1;    // 1=2x, 2=3x, 3=4x
    public static boolean dlssgQueueParallelism = false; // Disabled until DLSS-G inputs use an independent resource ring
    public static int chunkBuildingBatchSize = 6;
    public static int chunkBuildingTotalBatches = 6;
    public static int chunkCullDistance = 24;  // 0-512 chunks, chunks beyond excluded from TLAS (0=unlimited)
    public static int tonemappingMode = SDR_TONEMAPPING_DEFAULT_MODE;
    public static int sdrTonemappingMode = SDR_TONEMAPPING_DEFAULT_MODE;
    public static int sdrTransferFunction = SDR_TRANSFER_FUNCTION_SRGB;
    public static int minExposureTenK = 1;    // 1-10000 → 1e-7 to 1e-3 (physical light range)
    public static int maxExposure = 20;            // Tenths: 20 = 2.0 exposure. Range 1-100 (0.1-10.0)
    public static int exposureCompensation = 0; // tenths of EV: -30 to +30 → -3.0 to +3.0
    public static boolean manualExposureEnabled = false;  // auto exposure on by default (required for physical luminance range)
    public static int manualExposureEV100Tenths = 150; // EV100 in tenths: -40 to 200 -> EV -4.0 to EV 20.0 (default EV 15 = sunny day)
    public static int sharpenerMode = 0; // 0=None, 1=CAS, 2=RCAS
    public static int casSharpnessPercent = 50;
    // Exponential-decay auto-exposure parameters (v21)
    public static int brightAdaptSpeedTenths = 5;              // 1-50 → 0.1 to 5.0 seconds (tau for bright adapt)
    public static int darkAdaptSpeedTenths = 20;               // 5-100 → 0.5 to 10.0 seconds (tau for dark adapt)
    public static int sceneChangeThresholdTenths = 50;         // 20-100 → 2.0 to 10.0 EV (instant snap threshold)
    public static int centerWeightPercent = 0;                 // 0-100 → 0.0 to 1.0 (center-weighted metering strength, 0=uniform)
    public static int highlightWeightPercent = 50;             // 0-100 → 0.0 to 1.0 (highlight-weighted metering, 0=uniform)
    public static int middleGreyPercent = 18;   // 1-50 → 0.01 to 0.50
    public static int LwhiteTenths = 40;        // 10-200 → 1.0 to 20.0
    public static int saturationPercent = SATURATION_DEFAULT_PERCENT;  // 0-200 → 0.0 to 2.0
    public static boolean saturationAdaptive = false;  // Adaptive saturation: brightness+chroma-dependent

    // Per-tonemapper params: [mode][paramIndex], float values sent directly to native
    public static float[][] tonemapParams = new float[9][8];

    static {
        initTonemapDefaults();
    }

    public static void initTonemapDefaults() {
        for (int m = 0; m < 9; m++) for (int p = 0; p < 8; p++) tonemapParams[m][p] = 0.0f;
        // Mode 0: PBR Neutral
        tonemapParams[0][0] = 0.76f;   // startCompression
        tonemapParams[0][1] = 0.15f;   // desaturation
        // Mode 1: Reinhard
        tonemapParams[1][0] = 4.0f;    // Lwhite
        // Mode 2: ACES
        tonemapParams[2][0] = 1.0f;    // pre-exposure (1.0 = neutral)
        // Mode 3: AgX Eary Chroma (P0 = look preset, P1 = extra contrast, P2 = extra saturation)
        tonemapParams[3][0] = 0.0f;    // look: 0 = Base, 1 = Punchy, 2 = Golden
        tonemapParams[3][1] = 1.0f;    // extra contrast (1.0 = neutral)
        tonemapParams[3][2] = 1.0f;    // extra saturation (1.0 = neutral)
        // Mode 4: Lottes (GDC 2016 canonical — AMD GPUOpen)
        tonemapParams[4][0] = 2.0f;    // contrast (a)
        tonemapParams[4][1] = 1.0f;    // shoulder (d)
        tonemapParams[4][2] = 16.0f;   // hdrMax
        tonemapParams[4][3] = 0.18f;   // midIn (18% grey)
        tonemapParams[4][4] = 0.18f;   // midOut (preserves 18% grey exactly)
        // Mode 5: Frostbite (custom piecewise — no canonical source)
        tonemapParams[5][0] = 0.25f;   // linearEnd
        tonemapParams[5][1] = 2.0f;    // shoulderStrength
        // Mode 6: Uncharted 2 (Hable GDC 2010 canonical)
        tonemapParams[6][0] = 0.15f;   // A shoulder strength
        tonemapParams[6][1] = 0.50f;   // B linear strength
        tonemapParams[6][2] = 0.10f;   // C linear angle
        tonemapParams[6][3] = 0.20f;   // D toe strength
        tonemapParams[6][4] = 0.02f;   // E toe numerator
        tonemapParams[6][5] = 0.30f;   // F toe denominator
        tonemapParams[6][6] = 11.2f;   // W white point
        // Mode 7: GT
        tonemapParams[7][0] = 1.0f;    // contrast
        tonemapParams[7][1] = 0.22f;   // linearStart
        tonemapParams[7][2] = 0.4f;    // linearLength
        tonemapParams[7][3] = 1.33f;   // blackCurve
        tonemapParams[7][4] = 0.0f;    // blackLift (0 is valid)
    }

    public static int upscalerPreset = 4; // DLSS: 4=D (default), 5=E. Generic for future upscalers.

    // HDR tonemapper mode: 0 = PsychoVisual, 1 = BT.2390 EETF
    public static int hdrTonemapMode = 0;

    // PsychoV tonemapper (stored as integer percent/tenths for slider UI)
    public static boolean psychoEnabled = true;  // kept for SDR mode 8 visibility
    public static int psychoHighlightsPercent = 100;     // 0-300 → 0.0 to 3.0
    public static int psychoShadowsPercent = 100;        // 0-300 → 0.0 to 3.0
    public static int psychoContrastPercent = 100;       // 0-300 → 0.0 to 3.0
    public static int psychoPurityPercent = 100;         // 0-300 → 0.0 to 3.0
    public static int psychoBleachingPercent = 0;        // 0-100 → 0.0 to 1.0
    public static int psychoClipPointTenths = 1000;      // 10-5000 → 1.0 to 500.0
    public static int psychoHueRestorePercent = 0;       // 0-100 → 0.0 to 1.0
    public static int psychoAdaptContrastPercent = 100;  // 0-300 → 0.0 to 3.0
    public static int psychoWhiteCurve = 1;              // 0 = Neutwo, 1 = Naka-Rushton
    public static int psychoConeExponentPercent = 100;   // 10-300 → 0.1 to 3.0
    public static int psychoPeakSDRTenths = 20;          // 5-80 → 0.5 to 8.0 (SDR PsychoV peak)

    // HDR10 output (default: disabled, pure SDR)
    public static boolean hdrEnabled = false;
    public static boolean hdrScrgbMode = false;    // false = HDR10 (default, DLSS-FG compat), true = scRGB
    public static int hdrPeakNits = 1070;          // 100–10000 nits
    public static int hdrPaperWhiteNits = 203;     // 80–500 nits, ITU-R BT.2408 reference white
    public static int hdrUiBrightnessNits = 100;   // 50–300 nits, UI brightness in HDR mode

    // Sun/Moon orbit (Overworld-only, not per-dimension)
    public static int sunPathMode = 1;         // 0=Legacy, 1=Physical
    public static int sunInclinationDeg = 23;  // 0–90 degrees (Earth-like tilt)
    public static int sunAzimuthOffsetDeg = 0; // -180 to +180 degrees
    public static boolean moonFollowSun = true;
    public static int moonInclinationDeg = 23;
    public static int moonAzimuthOffsetDeg = 0;

    // Persistent UI state (not reset by Reset to Defaults)
    public static boolean showWelcomeMessage = true;
    public static boolean useUnifiedUI = true;           // true = new unified panel UI, false = legacy screens
    public static boolean advancedMode = false;            // false = AAA simple menu, true = full tweaker menu
    public static int uiGlobalAlphaPercent = 55;         // 0-100, controls menu transparency
    public static int materialLabOpacityPercent = 82;    // 35-100, Material Lab panel opacity only
    public static boolean uiAdaptiveDimming = false;     // auto-adjust alpha based on scene brightness

    public static final int SUN_PATH_MODE_DEFAULT = 1;
    public static final int SUN_INCLINATION_DEFAULT = 23;
    public static final int SUN_AZIMUTH_OFFSET_DEFAULT = 0;
    public static final int MOON_INCLINATION_DEFAULT = 23;
    public static final int MOON_AZIMUTH_OFFSET_DEFAULT = 0;

    // Emission — per-block temperatures (Celsius, keyed by EmissiveBlock id)
    public static final java.util.Map<String, Integer> blockTemperatures = new java.util.HashMap<>();
    // Flame colorant: per-block wavelength (nm, 0=off) and purity (0-100%)
    public static final java.util.Map<String, Integer> blockWavelengths = new java.util.HashMap<>();
    public static final java.util.Map<String, Integer> blockPurities = new java.util.HashMap<>();
    // Per-emissive-block gamut boost (0-200, 100 = 1.0× neutral)
    public static final java.util.Map<String, Integer> blockGamutBoosts = new java.util.HashMap<>();
    public static boolean lavaTextureEmissionEnabled = true; // Kill-switch for lava texture emission (diagnostic)
    public static float emissionLava = 1.0f;
    public static float emissionFire = 1.0f;
    public static float emissionSoulFire = 1.0f;
    public static float emissionTorch = 1.0f;
    public static float emissionSoulTorch = 1.0f;
    public static float emissionLantern = 1.0f;
    public static float emissionSoulLantern = 1.0f;
    public static float emissionCampfire = 1.0f;
    public static float emissionSoulCampfire = 1.0f;
    public static float emissionGlowstone = 1.0f;
    public static float emissionShroomlight = 1.0f;
    public static float emissionSeaLantern = 1.0f;
    public static float emissionFroglight = 1.0f;
    public static float emissionMagmaBlock = 1.0f;
    public static float emissionBeacon = 1.0f;
    public static float emissionEndRod = 1.0f;
    public static float emissionJackOLantern = 1.0f;
    public static float emissionNetherPortal = 1.0f;
    public static float emissionCryingObsidian = 0.8f;
    public static float emissionRespawnAnchor = 1.0f;
    public static float emissionConduit = 1.0f;
    public static float emissionAmethystCluster = 0.5f;
    public static float emissionSculkSensor = 0.5f;
    public static float emissionSculkCatalyst = 0.5f;
    public static float emissionSculkVein = 0.3f;
    public static float emissionSculk = 0.2f;
    public static float emissionSculkShrieker = 0.5f;
    public static float emissionBrewingStand = 0.5f;
    public static float emissionEndPortal = 1.0f;
    // New: emission fields for formerly area-light-only blocks
    public static float emissionRedstoneTorch = 0.5f;
    public static float emissionRedstoneLamp = 1.0f;
    public static float emissionCandle = 0.5f;
    public static float emissionCaveVines = 0.8f;
    public static float emissionGlowLichen = 0.3f;
    public static float emissionFurnace = 0.7f;
    public static float emissionBlastFurnace = 0.7f;
    public static float emissionSmoker = 0.7f;
    public static float emissionEnderChest = 0.5f;
    public static float emissionCopperBulb = 1.0f;
    public static float emissionEnchantingTable = 0.3f;
    public static float emissionCalibratedSculkSensor = 1.0f;
    public static float emissionSeaPickle = 1.0f;
    public static float emissionEndGateway = 1.0f;
    public static float emissionTrialSpawner = 1.0f;
    public static float emissionVault = 1.0f;

    // Firework particle emission in nits (cd/m²), same scale as EmissiveBlock.surfaceNits
    // Sparks: 20 nits (subtle glow), Flash: 2000 nits (bright burst like lava at 2300)
    public static float fireworkSparkEmission = 20.0f;
    public static float fireworkFlashEmission = 2000.0f;
    // Slider storage: integer nits
    public static int fireworkSparkEmissionNits = 20;
    public static int fireworkFlashEmissionNits = 2000;

    // Glowing particle emission in nits
    public static float flameParticleEmission = 800.0f;      // Torch/furnace flames
    public static float lavaParticleEmission = 400.0f;       // Lava drips/embers
    public static float portalParticleEmission = 1500.0f;    // Nether portal particles
    public static float endRodParticleEmission = 200.0f;     // End rod particles
    public static float glowParticleEmission = 30.0f;        // Glow squid ink, glow berries
    public static float soulFireFlameParticleEmission = 500.0f; // Soul fire flame particles
    public static float candleFlameParticleEmission = 300.0f;   // Candle flame particles
    public static float enchantingParticleEmission = 300.0f;    // Enchanting table particles
    public static float sculkParticleEmission = 175.0f;         // Sculk charge/pop particles
    public static float totemParticleEmission = 500.0f;          // Totem of undying particles
    public static float dragonBreathParticleEmission = 500.0f;   // Dragon breath particles
    public static float lavaDripParticleEmission = 200.0f;       // Lava drip particles

    // Per-particle spectral color parameters (indexed by particle type ordinal)
    // Order: flame, lavaEmber, fireworkSpark, fireworkFlash, portal, endRod, glow,
    //        soulFlame, candleFlame, enchanting, sculk, totem, dragonBreath, lavaDrip
    public static final int PARTICLE_TYPE_COUNT = 14;
    public static int[] particleTemperatures = { 1527, 1050, 2000, 2000, 1500, 1800, 1200, 1727, 1527, 1000, 900, 1400, 1600, 1050 };
    public static final int[] PARTICLE_TEMP_DEFAULTS = { 1527, 1050, 2000, 2000, 1500, 1800, 1200, 1727, 1527, 1000, 900, 1400, 1600, 1050 };
    public static int[] particleWavelengths = { 0, 0, 0, 0, 420, 0, 510, 460, 0, 530, 480, 530, 450, 0 };
    public static final int[] PARTICLE_WL_DEFAULTS = { 0, 0, 0, 0, 420, 0, 510, 460, 0, 530, 480, 530, 450, 0 };
    public static int[] particlePurities = { 0, 0, 0, 0, 90, 0, 20, 80, 0, 30, 50, 50, 60, 0 };
    public static final int[] PARTICLE_PUR_DEFAULTS = { 0, 0, 0, 0, 90, 0, 20, 80, 0, 30, 50, 50, 60, 0 };
    public static final String[] PARTICLE_TYPE_NAMES = {
        "Flame", "Lava Ember", "Firework Spark", "Firework Flash", "Portal", "End Rod", "Glow",
        "Soul Fire", "Candle Flame", "Enchanting", "Sculk", "Totem", "Dragon Breath", "Lava Drip"
    };

    // Per-dye-color firework emission properties, indexed by DyeColor ordinal
    // Order: white, orange, magenta, light_blue, yellow, lime, pink, gray,
    //        light_gray, cyan, purple, blue, brown, green, red, black
    public static final int FIREWORK_COLOR_COUNT = 16;
    public static int[] fireworkColorTemperatures = new int[] {
        6500, 2200, 5000, 8000, 3500, 4000, 3500, 4500,
        5000, 10000, 6000, 15000, 1400, 3000, 1200, 1000
    };
    public static final int[] FIREWORK_COLOR_TEMP_DEFAULTS = new int[] {
        6500, 2200, 5000, 8000, 3500, 4000, 3500, 4500,
        5000, 10000, 6000, 15000, 1400, 3000, 1200, 1000
    };
    // Per-color wavelength (nm, 0 = pure blackbody) and purity (0-100%)
    public static int[] fireworkColorWavelength = new int[] {
        0, 0, 500, 0, 0, 530, 500, 0,
        0, 490, 430, 470, 0, 530, 620, 0
    };
    public static final int[] FIREWORK_WAVELENGTH_DEFAULTS = new int[] {
        0, 0, 500, 0, 0, 530, 500, 0,
        0, 490, 430, 470, 0, 530, 620, 0
    };
    public static int[] fireworkColorPurity = new int[] {
        0, 0, 60, 0, 0, 50, 40, 0,
        0, 50, 70, 50, 0, 60, 30, 0
    };
    public static final int[] FIREWORK_PURITY_DEFAULTS = new int[] {
        0, 0, 60, 0, 0, 50, 40, 0,
        0, 50, 70, 50, 0, 60, 30, 0
    };
    public static final String[] FIREWORK_COLOR_NAMES = new String[] {
        "White", "Orange", "Magenta", "Light Blue", "Yellow", "Lime",
        "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue",
        "Brown", "Green", "Red", "Black"
    };

    public static void setFireworkSparkEmission(int nits, boolean save) {
        fireworkSparkEmissionNits = nits;
        fireworkSparkEmission = (float) nits;
        if (save) overwriteConfig();
    }

    public static void setFireworkFlashEmission(int nits, boolean save) {
        fireworkFlashEmissionNits = nits;
        fireworkFlashEmission = (float) nits;
        if (save) overwriteConfig();
    }

    public static void setFireworkColorTemperature(int index, int kelvin, boolean save) {
        fireworkColorTemperatures[index] = kelvin;
        if (save) overwriteConfig();
    }

    public static void setFireworkColorWavelength(int index, int nm, boolean save) {
        fireworkColorWavelength[index] = nm;
        if (save) overwriteConfig();
    }

    public static void setFireworkColorPurity(int index, int pct, boolean save) {
        fireworkColorPurity[index] = pct;
        if (save) overwriteConfig();
    }

    // Vanilla DyeColor.getFireworkColor() RGB values, indexed by DyeColor ordinal
    // Used to match particle RGB → dye color → blackbody temperature
    private static final int[] DYE_FIREWORK_COLORS = {
        0xF9FFFE, // 0  White
        0xF9801D, // 1  Orange
        0xC74EBD, // 2  Magenta
        0x3AB3DA, // 3  Light Blue
        0xFED83D, // 4  Yellow
        0x80C71F, // 5  Lime
        0xF38BAA, // 6  Pink
        0x474F52, // 7  Gray
        0x9D9D97, // 8  Light Gray
        0x169C9C, // 9  Cyan
        0x8932B8, // 10 Purple
        0x3C44AA, // 11 Blue
        0x835432, // 12 Brown
        0x5E7C16, // 13 Green
        0xB02E26, // 14 Red
        0x1D1D21, // 15 Black
    };

    /**
     * Match a particle's normalized RGB (0-1) to the closest vanilla DyeColor firework color index.
     */
    public static int lookupFireworkColorIndex(float r, float g, float b) {
        int pr = Math.round(r * 255);
        int pg = Math.round(g * 255);
        int pb = Math.round(b * 255);
        int bestIdx = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < FIREWORK_COLOR_COUNT; i++) {
            int c = DYE_FIREWORK_COLORS[i];
            int dr = pr - ((c >> 16) & 0xFF);
            int dg = pg - ((c >> 8) & 0xFF);
            int db = pb - (c & 0xFF);
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    public static boolean autoPBREnabled = true;
    public static boolean materialCompatEnabled = true;
    public static boolean materialCompatCtmEnabled = true;
    public static boolean materialCompatRandomEnabled = true;
    public static boolean materialCompatNaturalEnabled = true;
    public static boolean materialCompatColorsEnabled = true;
    public static boolean materialCompatOverlaysEnabled = true;
    public static boolean materialCompatLegacyMcPatcherEnabled = true;
    public static boolean materialCompatPhysicalEmissiveEnabled = true;
    public static int materialCompatCtmAtlasAdmissionLimit = 0;
    public static boolean textureStreamingV2 = true;
    public static boolean textureTieredArrays = true;
    public static boolean vanillaBlockAtlasBypass = true;
    public static boolean sparseAuxUpload = true;
    public static boolean ctmDemandResidency = true;
    public static boolean materialTableDirtyUpdates = true;
    public static boolean eagerSpriteImageCache = false;
    public static boolean materialCompatFullPreloadDiagnostic = false;

    public static void setAutoPBREnabled(boolean enabled, boolean write) {
        com.radiance.client.debug.CrashContext.recordChange("autoPBREnabled=" + enabled);
        Options.autoPBREnabled = enabled;
        if (!enabled) {
            com.radiance.client.autopbr.AutoPbrTextureRules.disableAll();
        } else {
            com.radiance.client.autopbr.AutoPbrRuntime.rehydrateSavedSidecars(
                net.minecraft.client.MinecraftClient.getInstance());
        }
        if (write) { overwriteConfig(); }
    }

    private static boolean legacyMaterialCompatAllDisabled(Properties props) {
        return !Boolean.parseBoolean(props.getProperty("materialCompatEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatCtmEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatRandomEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatNaturalEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatColorsEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatOverlaysEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatLegacyMcPatcherEnabled", "false"))
            && !Boolean.parseBoolean(props.getProperty("materialCompatPhysicalEmissiveEnabled", "false"));
    }

    public static boolean materialCompatDefaultsMigrationAppliesForTest(int loadedOptionsVersion,
        Properties props) {
        return loadedOptionsVersion < 24 && legacyMaterialCompatAllDisabled(props);
    }

    public static void applyMaterialCompatDefaultsMigrationForTest(int loadedOptionsVersion,
        Properties props) {
        if (materialCompatDefaultsMigrationAppliesForTest(loadedOptionsVersion, props)) {
            enableMaterialCompatDefaults();
        }
    }

    private static void enableMaterialCompatDefaults() {
        materialCompatEnabled = true;
        materialCompatCtmEnabled = true;
        materialCompatRandomEnabled = true;
        materialCompatNaturalEnabled = true;
        materialCompatColorsEnabled = true;
        materialCompatOverlaysEnabled = true;
        materialCompatLegacyMcPatcherEnabled = true;
        materialCompatPhysicalEmissiveEnabled = true;
    }

    // Environmental settings (per dimension: overworld/nether/end)
    public static int environmentEditingDimension = DIM_OVERWORLD;
    public static final int[] skyBrightnessPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] rainBlendPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudBrightnessPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudAlphaPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudHeightOffset = new int[]{0, 0, 0};

    // Volumetric cloud tuning (Fancy layout)
    public static final int[] cloudPuffinessPercent = new int[]{3, 3, 3};
    public static final int[] cloudDetailScalePercent = new int[]{
        CLOUD_DETAIL_SCALE_DEFAULT_PERCENT, CLOUD_DETAIL_SCALE_DEFAULT_PERCENT, CLOUD_DETAIL_SCALE_DEFAULT_PERCENT};
    public static final int[] cloudDetailStrengthPercent = new int[]{
        CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT, CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT, CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT};
    // Cloud anisotropy is intentionally forced off (hidden setting).
    public static final int[] cloudAnisotropyPercent = new int[]{0, 0, 0}; // HG g, 0-95
    public static final int[] cloudShadowStrengthPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudThicknessBlocks = new int[]{4, 4, 4};
    public static final int[] cloudDensityPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    // Default: enable mottled cloud shadows in the overworld only.
    public static final int[] cloudNoiseAffectsShadows = new int[]{1, 0, 0};

    // Retired fork CloudModule settings retained for native ABI compatibility.
    public static int volCloudQuality = 0;            // Forced off for the 0.1.5 visual-only lane.
    public static int volCloudDensityTenths = 10;     // 1-30 → 0.1-3.0
    public static int volCloudCoveragePercent = 35;   // 0-100 → 0.0-1.0
    public static int volCloudTypePercent = 67;       // 0-100 → 0.0-1.0 (0=Stratus, 33=Sc, 67=Cumulus, 100=Cb)
    public static int volCloudSpeedTenths = 50;       // 0-300 tenths of m/s (÷50 for internal multiplier)
    public static int volCloudAltitude = 192;         // 128-320 blocks
    public static int volCloudThickness = 64;         // 32-128 blocks
    public static int volCloudDetailStrengthPercent = 100; // 0-200 → 0.0-2.0
    public static int volCloudScatterOctaves = 3;     // 1-4
    public static int volCloudAmbientPercent = 100;   // 0-200 → 0.0-2.0 (ambient occlusion)
    public static int volCloudTemporalPercent = 0;    // 0=auto, 50-99 → 0.50-0.99
    public static int volCloudNoiseScale = 1000;       // 64-4096 blocks (noise texture period)
    public static int volCloudCellFrequencyTenths = 40; // 10-320 → 1.0-32.0 cells
    public static int volCloudAtmosphereFadeDist = 800; // 200-2000 blocks (visual fade only, not march clamp)
    public static int volCloudDebugMode = 0;           // 0=normal, 1-8=debug visualization modes
    public static int volCloudWindAngleDegrees = 0;  // 0-360 degrees
    public static int volCloudMarchSteps = 0;          // 0=use preset, 32-256
    public static int volCloudLightSteps = 0;          // 0=use preset, 1-12
    public static int volCloudResDivisor = 0;          // 0=use preset, 1-4
    public static int volCloudNoiseRes = 128;          // 128 (8MB), 256 (64MB), 512 (512MB)
    public static int wetSurfaceStrengthPercent = 100; // 0-200 → 0.0-2.0

    public static boolean isVolumetricCloudModuleEnabled() {
        return false;
    }

    public static final int[] waterTintR = new int[]{WATER_TINT_R_DEFAULT, WATER_TINT_R_DEFAULT, WATER_TINT_R_DEFAULT};
    public static final int[] waterTintG = new int[]{WATER_TINT_G_DEFAULT, WATER_TINT_G_DEFAULT, WATER_TINT_G_DEFAULT};
    public static final int[] waterTintB = new int[]{WATER_TINT_B_DEFAULT, WATER_TINT_B_DEFAULT, WATER_TINT_B_DEFAULT};
    public static final int[] waterFogStrengthPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] sunSizePercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] sunIntensityPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] moonSizePercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] moonIntensityPercent = new int[]{
        MOON_INTENSITY_DEFAULT_OVERWORLD_PERCENT, PERCENT_DEFAULT, PERCENT_DEFAULT};

    // Debounce for DLSS quality changes (500ms)
    private static ScheduledFuture<?> dlssRebuildTask;
    private static ScheduledFuture<?> upscalerPresetTask;
    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "radiance-settings-debounce");
            t.setDaemon(true);
            return t;
        });

    // Debounce for Minecraft-side chunk reload (worldRenderer.reload)
    private static ScheduledFuture<?> chunkReloadTask;

    public static void debouncedChunkReload() {
        if (chunkReloadTask != null) chunkReloadTask.cancel(false);
        chunkReloadTask = scheduler.schedule(
            () -> runOnClientThread(() -> {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc != null && mc.worldRenderer != null) {
                    mc.worldRenderer.reload();
                }
            }),
            500,
            TimeUnit.MILLISECONDS);
    }

    public static boolean isDevLoggingEnabled() {
        try {
            String env = System.getenv("RADIANCE_DEV_LOG");
            if (env != null) {
                if ("0".equals(env)) return false;
                if ("false".equalsIgnoreCase(env)) return false;
                return true;
            }
        } catch (Throwable ignored) {
        }
        return Boolean.getBoolean("radiance.devLog");
    }

    private static void runOnClientThread(Runnable task) {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null) {
                mc.execute(task);
                return;
            }
        } catch (Throwable ignored) {
        }
        task.run();
    }

    public static void readOptions() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        if (!Files.exists(path)) {
            overwriteConfig();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);

            int loadedOptionsVersion = Integer.parseInt(
                props.getProperty("optionsVersion", "0"));
            optionsVersion = loadedOptionsVersion;

            setMaxFps(Integer.parseInt(props.getProperty("maxFps", String.valueOf(maxFps))), false);
            setInactivityFpsLimit(Integer.parseInt(
                    props.getProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit))),
                false);
            setVsync(Boolean.parseBoolean(props.getProperty("vsync", String.valueOf(vsync))),
                false);
            setChunkBuildingBatchSize(Integer.parseInt(props.getProperty("chunkBuildingBatchSize",
                    String.valueOf(chunkBuildingBatchSize))),
                false);
            setChunkBuildingTotalBatches(
                Integer.parseInt(props.getProperty("chunkBuildingTotalBatches",
                    String.valueOf(chunkBuildingTotalBatches))), false);
            setChunkCullDistance(
                Integer.parseInt(props.getProperty("chunkCullDistance",
                    String.valueOf(chunkCullDistance))), false);
            tonemappingMode = clampTonemappingMode(Integer.parseInt(props.getProperty(
                "tonemappingMode", String.valueOf(tonemappingMode))));
            sdrTonemappingMode = clampTonemappingMode(Integer.parseInt(props.getProperty(
                "sdrTonemappingMode", String.valueOf(tonemappingMode))));
            nativeSetTonemappingMode(tonemappingMode, false);

            // Load per-tonemapper params
            for (int m = 0; m < 9; m++) {
                for (int p = 0; p < 8; p++) {
                    String key = "tmParam_" + m + "_" + p;
                    String stored = props.getProperty(key);
                    if (stored != null) {
                        try { tonemapParams[m][p] = Float.parseFloat(stored); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            pushActiveTonemapParams();

            sdrTransferFunction = Math.max(0, Math.min(1, Integer.parseInt(props.getProperty(
                "sdrTransferFunction", String.valueOf(sdrTransferFunction)))));
            nativeSetSdrTransferFunction(sdrTransferFunction, false);

            upscalerMode = Integer.parseInt(props.getProperty("upscalerMode", String.valueOf(upscalerMode)));

            // Push to native directly on startup (no debounce, write=false)
            // Support both old "dlss*" keys and new "upscaler*" keys for backwards compatibility
            upscalerResOverride = clampPercent(Integer.parseInt(props.getProperty("upscalerResOverride",
                props.getProperty("dlssResOverride", String.valueOf(upscalerResOverride)))));
            nativeSetDlssResOverride(upscalerResOverride, false);

            upscalerQuality = Integer.parseInt(props.getProperty("upscalerQuality",
                props.getProperty("dlssQuality", String.valueOf(upscalerQuality))));
            nativeSetDlssQuality(upscalerQuality, false);

            dlssDEnabled = Boolean.parseBoolean(props.getProperty("dlssDEnabled", String.valueOf(dlssDEnabled)));

            // Migration / consistency:
            // Current upscalerMode: 0=DLSS-RR, 2=Off. Old 1=FSR3 configs migrate to Off.
            // Old configs had dlssDEnabled as the primary flag.
            if (dlssDEnabled) {
                upscalerMode = 0; // DLSS-RR
            } else {
                upscalerMode = 2; // Off
            }

            setMinExposure(Integer.parseInt(props.getProperty("minExposureTenK", String.valueOf(minExposureTenK))), false);
            setMaxExposure(Integer.parseInt(props.getProperty("maxExposure", String.valueOf(maxExposure))), false);

            upscalerPreset = Integer.parseInt(props.getProperty("upscalerPreset",
                props.getProperty("dlssPreset", String.valueOf(upscalerPreset))));
            nativeSetDlssPreset(upscalerPreset, false);

            rayBounces = Integer.parseInt(props.getProperty("rayBounces", String.valueOf(rayBounces)));
            nativeSetRayBounces(rayBounces, false);

            simplifiedIndirect = Boolean.parseBoolean(props.getProperty("simplifiedIndirect", String.valueOf(simplifiedIndirect)));
            nativeSetSimplifiedIndirect(simplifiedIndirect, false);
            multiScatterGGX = Boolean.parseBoolean(props.getProperty("multiScatterGGX", String.valueOf(multiScatterGGX)));
            try { nativeSetMultiScatterGGX(multiScatterGGX, false); } catch (UnsatisfiedLinkError ignored) {}
            eonDiffuse = Boolean.parseBoolean(props.getProperty("eonDiffuse", String.valueOf(eonDiffuse)));
            diffuseModel = diffuseModelFromProperties(props, diffuseModel, eonDiffuse);
            eonDiffuse = diffuseModel != DIFFUSE_MODEL_LEGACY;
            try { nativeSetDiffuseModel(diffuseModel, false); } catch (UnsatisfiedLinkError ignored) {}
            shaderPackPath = props.getProperty("shaderPackPath", shaderPackPath);

            windowPosX = Integer.parseInt(props.getProperty("windowPosX", String.valueOf(windowPosX)));
            windowPosY = Integer.parseInt(props.getProperty("windowPosY", String.valueOf(windowPosY)));
            windowWidth = Integer.parseInt(props.getProperty("windowWidth", String.valueOf(windowWidth)));
            windowHeight = Integer.parseInt(props.getProperty("windowHeight", String.valueOf(windowHeight)));

            loggingEnabled = Boolean.parseBoolean(props.getProperty("loggingEnabled", String.valueOf(loggingEnabled)));
            gpuDebugLabels = Boolean.parseBoolean(props.getProperty("gpuDebugLabels", String.valueOf(gpuDebugLabels)));
            nativeSetGpuDebugLabels(gpuDebugLabels, false);
            // Full file logging waits for renderer init, but Vulkan debug labels are safe to arm now.

            boolean hasCanonicalDisplacement = props.containsKey("displacementEnabled");
            boolean hasDisplacementQualityKey = props.containsKey("displacementQuality");
            int loadedQuality = clamp(Integer.parseInt(props.getProperty("displacementQuality", String.valueOf(displacementQuality))),
                DISPLACEMENT_QUALITY_LOW, DISPLACEMENT_QUALITY_ULTRA);
            applyDisplacementQualityPreset(hasDisplacementQualityKey ? loadedQuality : displacementQuality);
            displacementEnabled = Boolean.parseBoolean(props.getProperty(
                "displacementEnabled", props.getProperty("pomEnabled", String.valueOf(displacementEnabled))));
            displacementDepthCapPercent = clamp(Integer.parseInt(props.getProperty(
                "displacementDepthCapPercent", props.getProperty("pomHeightScalePercent", String.valueOf(displacementDepthCapPercent)))), 1, 50);
            displacementFadeDistance = clamp(Integer.parseInt(props.getProperty(
                "displacementFadeDistance", props.getProperty("pomFadeDistance", String.valueOf(displacementFadeDistance)))), 8, 256);
            syncDisplacementNative(false);

            useV2Engine = Boolean.parseBoolean(props.getProperty("useV2Engine", String.valueOf(useV2Engine)));

            serEnabled = Boolean.parseBoolean(props.getProperty("serEnabled", String.valueOf(serEnabled)));
            nativeSetSEREnabled(serEnabled, false);
            serHintsEnabled = Boolean.parseBoolean(props.getProperty("serHintsEnabled", String.valueOf(serHintsEnabled)));
            nativeSetSERHintsEnabled(serHintsEnabled, false);

            sharcEnabled = Boolean.parseBoolean(props.getProperty("sharcEnabled", String.valueOf(sharcEnabled)));
            nativeSetSharcEnabled(sharcEnabled, false);

            sharcSceneScaleTenths = clamp(Integer.parseInt(props.getProperty("sharcSceneScaleTenths", String.valueOf(sharcSceneScaleTenths))), 10, 200);
            nativeSetSharcSceneScale(sharcSceneScaleTenths / 10.0f, false);

            sharcRoughnessThresholdPercent = clamp(Integer.parseInt(props.getProperty("sharcRoughnessThresholdPercent", String.valueOf(sharcRoughnessThresholdPercent))), 0, 100);
            nativeSetSharcRoughnessThreshold(sharcRoughnessThresholdPercent / 100.0f, false);

            sharcAccumulationFrames = clamp(Integer.parseInt(props.getProperty("sharcAccumulationFrames", String.valueOf(sharcAccumulationFrames))), 4, 256);
            nativeSetSharcAccumulationFrames(sharcAccumulationFrames, false);

            sharcStaleFrames = clamp(Integer.parseInt(props.getProperty("sharcStaleFrames", String.valueOf(sharcStaleFrames))), 4, 128);
            nativeSetSharcStaleFrames(sharcStaleFrames, false);

            sharcDownscale = clamp(Integer.parseInt(props.getProperty("sharcDownscale", String.valueOf(sharcDownscale))), 1, 8);
            nativeSetSharcDownscale(sharcDownscale, false);

            sharcUpdateBlockSize = clamp(Integer.parseInt(props.getProperty("sharcUpdateBlockSize", String.valueOf(sharcUpdateBlockSize))), 1, 8);
            nativeSetSharcUpdateBlockSize(sharcUpdateBlockSize, false);

            sharcUpdateBounces = clamp(Integer.parseInt(props.getProperty("sharcUpdateBounces", String.valueOf(sharcUpdateBounces))), 2, 8);
            nativeSetSharcUpdateBounces(sharcUpdateBounces, false);

            sharcCapacityExponent = clamp(Integer.parseInt(props.getProperty("sharcCapacityExponent", String.valueOf(sharcCapacityExponent))), 18, 24);
            nativeSetSharcCapacityExponent(sharcCapacityExponent, false);

            sharcQueryMode = clamp(Integer.parseInt(props.getProperty("sharcQueryMode", String.valueOf(sharcQueryMode))), 0, 2);
            nativeSetSharcQueryMode(sharcQueryMode, false);

            sharcQualityPreset = clamp(Integer.parseInt(props.getProperty("sharcQualityPreset", String.valueOf(sharcQualityPreset))), 0, 5);

            beerLawShadows = Boolean.parseBoolean(props.getProperty("beerLawShadows", String.valueOf(beerLawShadows)));
            noEmissionClamp = Boolean.parseBoolean(props.getProperty("noEmissionClamp", String.valueOf(noEmissionClamp)));
            physicalSunDisk = Boolean.parseBoolean(props.getProperty("physicalSunDisk", String.valueOf(physicalSunDisk)));
            noHandAmbient = Boolean.parseBoolean(props.getProperty("noHandAmbient", String.valueOf(noHandAmbient)));
            entityNormalsEnabled = Boolean.parseBoolean(props.getProperty("entityNormalsEnabled", String.valueOf(entityNormalsEnabled)));

            // Offline accumulation preferences (ground truth is session-only, not persisted)
            offlineBounces = Integer.parseInt(props.getProperty("offlineBounces", String.valueOf(offlineBounces)));
            offlineDisableRR = Boolean.parseBoolean(props.getProperty("offlineDisableRR", String.valueOf(offlineDisableRR)));
            offlineDisableClamp = Boolean.parseBoolean(props.getProperty("offlineDisableClamp", String.valueOf(offlineDisableClamp)));
            offlineFocalDistance = Float.parseFloat(props.getProperty("offlineFocalDistance", String.valueOf(offlineFocalDistance)));
            offlineNativeRes = Boolean.parseBoolean(props.getProperty("offlineNativeRes", String.valueOf(offlineNativeRes)));
            offlineDenoised = Integer.parseInt(props.getProperty("offlineDenoised", String.valueOf(offlineDenoised)));
            dlssEpochLength = Integer.parseInt(props.getProperty("dlssEpochLength", String.valueOf(dlssEpochLength)));
            // Camera model
            sensorPreset = Integer.parseInt(props.getProperty("sensorPreset", String.valueOf(sensorPreset)));
            sensorWidthMM = Float.parseFloat(props.getProperty("sensorWidthMM", String.valueOf(sensorWidthMM)));
            sensorHeightMM = Float.parseFloat(props.getProperty("sensorHeightMM", String.valueOf(sensorHeightMM)));
            focalLengthMM = Integer.parseInt(props.getProperty("focalLengthMM", String.valueOf(focalLengthMM)));
            fStop = Float.parseFloat(props.getProperty("fStop", String.valueOf(fStop)));
            dofStrengthPercent = clamp(Integer.parseInt(props.getProperty(
                "dofStrengthPercent", String.valueOf(dofStrengthPercent))), 100, 2000);
            // First-person view
            fpvEnabled = Boolean.parseBoolean(props.getProperty("fpvEnabled", String.valueOf(fpvEnabled)));
            fpvOffsetForward = clamp(Integer.parseInt(props.getProperty("fpvOffsetForward",
                props.getProperty("fpvCameraOffset", String.valueOf(fpvOffsetForward)))), -30, 30);
            fpvOffsetVertical = clamp(Integer.parseInt(props.getProperty("fpvOffsetVertical", String.valueOf(fpvOffsetVertical))), -30, 30);
            fpvOffsetLateral = clamp(Integer.parseInt(props.getProperty("fpvOffsetLateral", String.valueOf(fpvOffsetLateral))), -20, 20);
            syncFpvSettings();
            // Freecam preferences
            freecamEnabled = Boolean.parseBoolean(props.getProperty("freecamEnabled", String.valueOf(freecamEnabled)));
            freecamSpeed = Float.parseFloat(props.getProperty("freecamSpeed", String.valueOf(freecamSpeed)));
            freecamShowPlayer = Boolean.parseBoolean(props.getProperty("freecamShowPlayer", String.valueOf(freecamShowPlayer)));
            try {
                nativeSetOfflineBounces(offlineBounces, false);
                nativeSetOfflineDisableRR(offlineDisableRR, false);
                nativeSetOfflineDisableClamp(offlineDisableClamp, false);
                nativeSetOfflineAperture(computeApertureRadius(), false);
                nativeSetOfflineFocalDistance(offlineFocalDistance, false);
                nativeSetDofStrength(dofStrengthPercent / 100.0f, false);
                nativeSetOfflineNativeRes(offlineNativeRes, false);
                nativeSetOfflineDenoised(offlineDenoised, false);
                nativeSetDlssEpochLength(dlssEpochLength, false);
            } catch (UnsatisfiedLinkError ignored) {
                // Offline accumulation JNI not available in this core.dll build
            }

            nativeSetBeerLawShadows(beerLawShadows, false);
            nativeSetNoEmissionClamp(noEmissionClamp, false);
            nativeSetPhysicalSunDisk(physicalSunDisk, false);
            nativeSetNoHandAmbient(noHandAmbient, false);
            nativeSetEntityNormalsEnabled(entityNormalsEnabled, false);

            autoPBREnabled = Boolean.parseBoolean(props.getProperty("autoPBREnabled", String.valueOf(autoPBREnabled)));
            materialCompatEnabled = Boolean.parseBoolean(props.getProperty("materialCompatEnabled", String.valueOf(materialCompatEnabled)));
            materialCompatCtmEnabled = Boolean.parseBoolean(props.getProperty("materialCompatCtmEnabled", String.valueOf(materialCompatCtmEnabled)));
            materialCompatRandomEnabled = Boolean.parseBoolean(props.getProperty("materialCompatRandomEnabled", String.valueOf(materialCompatRandomEnabled)));
            materialCompatNaturalEnabled = Boolean.parseBoolean(props.getProperty("materialCompatNaturalEnabled", String.valueOf(materialCompatNaturalEnabled)));
            materialCompatColorsEnabled = Boolean.parseBoolean(props.getProperty("materialCompatColorsEnabled", String.valueOf(materialCompatColorsEnabled)));
            materialCompatOverlaysEnabled = Boolean.parseBoolean(props.getProperty("materialCompatOverlaysEnabled", String.valueOf(materialCompatOverlaysEnabled)));
            materialCompatLegacyMcPatcherEnabled = Boolean.parseBoolean(props.getProperty("materialCompatLegacyMcPatcherEnabled", String.valueOf(materialCompatLegacyMcPatcherEnabled)));
            materialCompatPhysicalEmissiveEnabled = Boolean.parseBoolean(props.getProperty("materialCompatPhysicalEmissiveEnabled", String.valueOf(materialCompatPhysicalEmissiveEnabled)));
            materialCompatCtmAtlasAdmissionLimit = Math.max(0, Integer.parseInt(props.getProperty("materialCompatCtmAtlasAdmissionLimit", String.valueOf(materialCompatCtmAtlasAdmissionLimit))));
            textureStreamingV2 = Boolean.parseBoolean(props.getProperty("textureStreamingV2", String.valueOf(textureStreamingV2)));
            textureTieredArrays = Boolean.parseBoolean(props.getProperty("textureTieredArrays", String.valueOf(textureTieredArrays)));
            vanillaBlockAtlasBypass = Boolean.parseBoolean(props.getProperty("vanillaBlockAtlasBypass", String.valueOf(vanillaBlockAtlasBypass)));
            sparseAuxUpload = Boolean.parseBoolean(props.getProperty("sparseAuxUpload", String.valueOf(sparseAuxUpload)));
            ctmDemandResidency = Boolean.parseBoolean(props.getProperty("ctmDemandResidency", String.valueOf(ctmDemandResidency)));
            materialTableDirtyUpdates = Boolean.parseBoolean(props.getProperty("materialTableDirtyUpdates", String.valueOf(materialTableDirtyUpdates)));
            eagerSpriteImageCache = Boolean.parseBoolean(props.getProperty("eagerSpriteImageCache", String.valueOf(eagerSpriteImageCache)));
            materialCompatFullPreloadDiagnostic = Boolean.parseBoolean(props.getProperty("materialCompatFullPreloadDiagnostic", String.valueOf(materialCompatFullPreloadDiagnostic)));
            if (loadedOptionsVersion < 24 && legacyMaterialCompatAllDisabled(props)) {
                enableMaterialCompatDefaults();
            }
            outputScale2x = Boolean.parseBoolean(props.getProperty("outputScale2x", String.valueOf(outputScale2x)));
            nativeSetOutputScale2x(outputScale2x, false);

            reflexExplicitlyConfigured = props.containsKey("reflexEnabled") || props.containsKey("vrrMode");
            reflexEnabled = Boolean.parseBoolean(props.getProperty("reflexEnabled", String.valueOf(reflexEnabled)));
            nativeSetReflexEnabled(reflexEnabled, false);
            reflexBoost = Boolean.parseBoolean(props.getProperty("reflexBoost", String.valueOf(reflexBoost)));
            nativeSetReflexBoost(reflexBoost, false);
            vrrMode = Boolean.parseBoolean(props.getProperty("vrrMode", String.valueOf(vrrMode)));
            nativeSetVrrMode(vrrMode, false);

            // Frame Generation
            frameGenMode = Integer.parseInt(props.getProperty("frameGenMode", String.valueOf(frameGenMode)));
            nativeSetFrameGenMode(frameGenMode, false);
            frameGenMultiplier = Integer.parseInt(props.getProperty("frameGenMultiplier", String.valueOf(frameGenMultiplier)));
            nativeSetFrameGenMultiplier(frameGenMultiplier, false);
            dlssgQueueParallelism = false;
            nativeSetDlssgQueueParallelism(false, false);

            exposureCompensation = Integer.parseInt(props.getProperty(
                "exposureCompensation", String.valueOf(exposureCompensation)));
            manualExposureEnabled = Boolean.parseBoolean(props.getProperty(
                "manualExposureEnabled", String.valueOf(manualExposureEnabled)));
            manualExposureEV100Tenths = Integer.parseInt(props.getProperty(
                "manualExposureEV100Tenths", String.valueOf(manualExposureEV100Tenths)));
            manualExposureEV100Tenths = clamp(manualExposureEV100Tenths, -40, 200);
            // Backward compat: casEnabled=true → sharpenerMode=1
            if (props.containsKey("casEnabled") && !props.containsKey("sharpenerMode")) {
                sharpenerMode = Boolean.parseBoolean(props.getProperty("casEnabled", "false")) ? 1 : 0;
            } else {
                sharpenerMode = clamp(Integer.parseInt(props.getProperty(
                    "sharpenerMode", String.valueOf(sharpenerMode))), 0, 2);
            }
            casSharpnessPercent = clamp(Integer.parseInt(props.getProperty(
                "casSharpnessPercent", String.valueOf(casSharpnessPercent))), 0, 100);
            middleGreyPercent = Integer.parseInt(props.getProperty(
                "middleGreyPercent", String.valueOf(middleGreyPercent)));
            LwhiteTenths = Integer.parseInt(props.getProperty(
                "LwhiteTenths", String.valueOf(LwhiteTenths)));
            saturationPercent = Integer.parseInt(props.getProperty(
                "saturationPercent", String.valueOf(saturationPercent)));
            saturationAdaptive = Boolean.parseBoolean(props.getProperty(
                "saturationAdaptive", String.valueOf(saturationAdaptive)));

            // HDR tonemapper mode
            hdrTonemapMode = Integer.parseInt(props.getProperty("hdrTonemapMode", "0"));
            nativeSetHdrTonemapMode(hdrTonemapMode, false);

            // PsychoV tonemapper
            psychoEnabled = Boolean.parseBoolean(props.getProperty("psychoEnabled", "true"));
            nativeSetPsychoEnabled(psychoEnabled, false);
            psychoHighlightsPercent = Integer.parseInt(props.getProperty("psychoHighlightsPercent", "100"));
            nativeSetPsychoHighlights(psychoHighlightsPercent / 100.0f, false);
            psychoShadowsPercent = Integer.parseInt(props.getProperty("psychoShadowsPercent", "100"));
            nativeSetPsychoShadows(psychoShadowsPercent / 100.0f, false);
            psychoContrastPercent = Integer.parseInt(props.getProperty("psychoContrastPercent", "100"));
            nativeSetPsychoContrast(psychoContrastPercent / 100.0f, false);
            psychoPurityPercent = Integer.parseInt(props.getProperty("psychoPurityPercent", "105"));
            nativeSetPsychoPurity(psychoPurityPercent / 100.0f, false);
            psychoBleachingPercent = Integer.parseInt(props.getProperty("psychoBleachingPercent", "0"));
            nativeSetPsychoBleaching(psychoBleachingPercent / 100.0f, false);
            psychoClipPointTenths = Integer.parseInt(props.getProperty("psychoClipPointTenths", "1000"));
            nativeSetPsychoClipPoint(psychoClipPointTenths / 10.0f, false);
            psychoHueRestorePercent = Integer.parseInt(props.getProperty("psychoHueRestorePercent", "0"));
            nativeSetPsychoHueRestore(psychoHueRestorePercent / 100.0f, false);
            psychoAdaptContrastPercent = Integer.parseInt(props.getProperty("psychoAdaptContrastPercent", "100"));
            nativeSetPsychoAdaptContrast(psychoAdaptContrastPercent / 100.0f, false);
            psychoWhiteCurve = Integer.parseInt(props.getProperty("psychoWhiteCurve", "1"));
            nativeSetPsychoWhiteCurve(psychoWhiteCurve, false);
            psychoConeExponentPercent = Integer.parseInt(props.getProperty("psychoConeExponentPercent", "100"));
            nativeSetPsychoConeExponent(psychoConeExponentPercent / 100.0f, false);
            psychoPeakSDRTenths = Integer.parseInt(props.getProperty("psychoPeakSDRTenths", "20"));
            psychoPeakSDRTenths = clamp(psychoPeakSDRTenths, 5, 80);
            nativeSetPsychoPeakSDR(psychoPeakSDRTenths / 10.0f, false);

            brightAdaptSpeedTenths = Integer.parseInt(props.getProperty(
                "brightAdaptSpeedTenths", String.valueOf(brightAdaptSpeedTenths)));
            darkAdaptSpeedTenths = Integer.parseInt(props.getProperty(
                "darkAdaptSpeedTenths", String.valueOf(darkAdaptSpeedTenths)));
            sceneChangeThresholdTenths = Integer.parseInt(props.getProperty(
                "sceneChangeThresholdTenths", String.valueOf(sceneChangeThresholdTenths)));
            centerWeightPercent = Integer.parseInt(props.getProperty(
                "centerWeightPercent", String.valueOf(centerWeightPercent)));
            highlightWeightPercent = Integer.parseInt(props.getProperty(
                "highlightWeightPercent", String.valueOf(highlightWeightPercent)));

            brightAdaptSpeedTenths = clamp(brightAdaptSpeedTenths, 1, 50);
            darkAdaptSpeedTenths = clamp(darkAdaptSpeedTenths, 5, 100);
            sceneChangeThresholdTenths = clamp(sceneChangeThresholdTenths, 20, 100);
            centerWeightPercent = clamp(centerWeightPercent, 0, 100);
            highlightWeightPercent = clamp(highlightWeightPercent, 0, 100);

            nativeSetExposureCompensation(exposureCompensation / 10.0f, false);
            nativeSetManualExposureEnabled(manualExposureEnabled, false);
            nativeSetManualExposure(ev100ToLinearExposure(manualExposureEV100Tenths), false);
            nativeSetSharpenerMode(sharpenerMode, false);
            nativeSetCasSharpness(casSharpnessPercent / 100.0f, false);
            nativeSetMiddleGrey(middleGreyPercent / 100.0f, false);
            nativeSetLwhite(LwhiteTenths / 10.0f, false);
            nativeSetSaturation(saturationPercent / 100.0f, false);
            try { nativeSetSaturationAdaptive(saturationAdaptive, false); } catch (UnsatisfiedLinkError ignored) {}
            nativeSetBrightAdaptSpeed(brightAdaptSpeedTenths / 10.0f, false);
            nativeSetDarkAdaptSpeed(darkAdaptSpeedTenths / 10.0f, false);
            nativeSetSceneChangeThreshold(sceneChangeThresholdTenths / 10.0f, false);
            nativeSetCenterWeightStrength(centerWeightPercent / 100.0f, false);
            nativeSetHighlightWeight(highlightWeightPercent / 100.0f, false);

            // HDR
            hdrEnabled = Boolean.parseBoolean(props.getProperty("hdrEnabled", String.valueOf(hdrEnabled)));
            hdrScrgbMode = Boolean.parseBoolean(props.getProperty("hdrScrgbMode", String.valueOf(hdrScrgbMode)));
            hdrPeakNits = Integer.parseInt(props.getProperty("hdrPeakNits", String.valueOf(hdrPeakNits)));
            hdrPaperWhiteNits = Integer.parseInt(props.getProperty("hdrPaperWhiteNits", String.valueOf(hdrPaperWhiteNits)));
            nativeSetHdrEnabled(hdrEnabled, false);
            nativeSetHdrScrgbMode(hdrScrgbMode, false);
            nativeSetHdrPeakNits(hdrPeakNits, false);
            nativeSetHdrPaperWhiteNits(hdrPaperWhiteNits, false);

            hdrUiBrightnessNits = Integer.parseInt(props.getProperty("hdrUiBrightnessNits", String.valueOf(hdrUiBrightnessNits)));
            nativeSetHdrUiBrightnessNits(hdrUiBrightnessNits, false);

            if (loadedOptionsVersion < 2) {
                saturationPercent = SATURATION_DEFAULT_PERCENT;
                nativeSetSaturation(saturationPercent / 100.0f, false);
            }

            if (loadedOptionsVersion < 3) {
                if (hdrEnabled && !props.containsKey("sdrTonemappingMode")) {
                    sdrTonemappingMode = SDR_TONEMAPPING_DEFAULT_MODE;
                }

                tonemappingMode = clampTonemappingMode(sdrTonemappingMode);
                nativeSetTonemappingMode(tonemappingMode, false);
                pushActiveTonemapParams();
            }

            if (loadedOptionsVersion < 21) {
                // v21: Auto-exposure system rebuild — exponential decay + scene-cut detection.
                // Reset all exposure parameters to new defaults (old fields no longer exist).
                brightAdaptSpeedTenths = 5;
                darkAdaptSpeedTenths = 20;
                sceneChangeThresholdTenths = 50;
                centerWeightPercent = 0;
                middleGreyPercent = 18;
                exposureCompensation = 0;
                nativeSetBrightAdaptSpeed(brightAdaptSpeedTenths / 10.0f, false);
                nativeSetDarkAdaptSpeed(darkAdaptSpeedTenths / 10.0f, false);
                nativeSetSceneChangeThreshold(sceneChangeThresholdTenths / 10.0f, false);
                nativeSetCenterWeightStrength(centerWeightPercent / 100.0f, false);
                nativeSetMiddleGrey(middleGreyPercent / 100.0f, false);
                nativeSetExposureCompensation(exposureCompensation / 10.0f, false);
            }

            if (loadedOptionsVersion < 22) {
                // v22: Fix stale exposure defaults from v21 intermediate builds.
                centerWeightPercent = 0;
                nativeSetCenterWeightStrength(0.0f, false);
                brightAdaptSpeedTenths = 5;
                nativeSetBrightAdaptSpeed(0.5f, false);
                darkAdaptSpeedTenths = 20;
                nativeSetDarkAdaptSpeed(2.0f, false);
                sceneChangeThresholdTenths = 50;
                nativeSetSceneChangeThreshold(5.0f, false);
            }

            optionsVersion = CURRENT_OPTIONS_VERSION;

            // Emission — per-block temperatures
            // Migrate legacy lavaTemperatureCelsius if present
            String legacyLavaTemp = props.getProperty("lavaTemperatureCelsius");
            for (EmissiveBlock b : EmissiveBlock.values()) {
                if (!b.isThermal()) continue;
                String key = "blockTemp_" + b.getId();
                int defaultTemp = b.getDefaultTemperatureCelsius();
                // Legacy migration: use old lavaTemperatureCelsius for lava if new key absent
                String fallback = String.valueOf(defaultTemp);
                if (b == EmissiveBlock.LAVA && legacyLavaTemp != null && !props.containsKey(key)) {
                    fallback = legacyLavaTemp;
                }
                int temp = clamp(Integer.parseInt(props.getProperty(key, fallback)), 500, 4000);
                blockTemperatures.put(b.getId(), temp);
                // Apply temperature to surfaceNits: BB(T) × emissivity
                float kelvin = temp + 273.15f;
                b.setSurfaceNits(EmissiveBlock.blackbodyLuminance(kelvin) * b.getEmissivity());
            }
            // Flame colorant: per-block wavelength and purity
            for (EmissiveBlock b : EmissiveBlock.values()) {
                if (!b.isThermal()) continue;
                int wl = clamp(Integer.parseInt(props.getProperty("blockWavelength_" + b.getId(),
                    String.valueOf(b.getDefaultWavelengthNm()))), 0, 780);
                blockWavelengths.put(b.getId(), wl);
                int pur = clamp(Integer.parseInt(props.getProperty("blockPurity_" + b.getId(),
                    String.valueOf(b.getDefaultPurityPercent()))), 0, 100);
                blockPurities.put(b.getId(), pur);
            }
            // Per-emissive-block gamut boost
            for (EmissiveBlock b : EmissiveBlock.values()) {
                int gbDefault = b.getId().equals("lava") ? 200 : 100;
                int gb = clamp(Integer.parseInt(props.getProperty("blockGamut_" + b.getId(), String.valueOf(gbDefault))), 0, 200);
                blockGamutBoosts.put(b.getId(), gb);
            }
            lavaTextureEmissionEnabled = Boolean.parseBoolean(props.getProperty("lavaTextureEmissionEnabled", String.valueOf(lavaTextureEmissionEnabled)));
            emissionLava = Float.parseFloat(props.getProperty("emissionLava", String.valueOf(emissionLava)));
            emissionFire = Float.parseFloat(props.getProperty("emissionFire", String.valueOf(emissionFire)));
            emissionSoulFire = Float.parseFloat(props.getProperty("emissionSoulFire", String.valueOf(emissionSoulFire)));
            emissionTorch = Float.parseFloat(props.getProperty("emissionTorch", String.valueOf(emissionTorch)));
            emissionSoulTorch = Float.parseFloat(props.getProperty("emissionSoulTorch", String.valueOf(emissionSoulTorch)));
            emissionLantern = Float.parseFloat(props.getProperty("emissionLantern", String.valueOf(emissionLantern)));
            emissionSoulLantern = Float.parseFloat(props.getProperty("emissionSoulLantern", String.valueOf(emissionSoulLantern)));
            emissionCampfire = Float.parseFloat(props.getProperty("emissionCampfire", String.valueOf(emissionCampfire)));
            emissionSoulCampfire = Float.parseFloat(props.getProperty("emissionSoulCampfire", String.valueOf(emissionSoulCampfire)));
            emissionGlowstone = Float.parseFloat(props.getProperty("emissionGlowstone", String.valueOf(emissionGlowstone)));
            emissionShroomlight = Float.parseFloat(props.getProperty("emissionShroomlight", String.valueOf(emissionShroomlight)));
            emissionSeaLantern = Float.parseFloat(props.getProperty("emissionSeaLantern", String.valueOf(emissionSeaLantern)));
            emissionFroglight = Float.parseFloat(props.getProperty("emissionFroglight", String.valueOf(emissionFroglight)));
            emissionMagmaBlock = Float.parseFloat(props.getProperty("emissionMagmaBlock", String.valueOf(emissionMagmaBlock)));
            emissionBeacon = Float.parseFloat(props.getProperty("emissionBeacon", String.valueOf(emissionBeacon)));
            emissionEndRod = Float.parseFloat(props.getProperty("emissionEndRod", String.valueOf(emissionEndRod)));
            emissionJackOLantern = Float.parseFloat(props.getProperty("emissionJackOLantern", String.valueOf(emissionJackOLantern)));
            emissionNetherPortal = Float.parseFloat(props.getProperty("emissionNetherPortal", String.valueOf(emissionNetherPortal)));
            emissionCryingObsidian = Float.parseFloat(props.getProperty("emissionCryingObsidian", String.valueOf(emissionCryingObsidian)));
            emissionRespawnAnchor = Float.parseFloat(props.getProperty("emissionRespawnAnchor", String.valueOf(emissionRespawnAnchor)));
            emissionConduit = Float.parseFloat(props.getProperty("emissionConduit", String.valueOf(emissionConduit)));
            emissionAmethystCluster = Float.parseFloat(props.getProperty("emissionAmethystCluster", String.valueOf(emissionAmethystCluster)));
            emissionSculkSensor = Float.parseFloat(props.getProperty("emissionSculkSensor", String.valueOf(emissionSculkSensor)));
            emissionSculkCatalyst = Float.parseFloat(props.getProperty("emissionSculkCatalyst", String.valueOf(emissionSculkCatalyst)));
            emissionSculkVein = Float.parseFloat(props.getProperty("emissionSculkVein", String.valueOf(emissionSculkVein)));
            emissionSculk = Float.parseFloat(props.getProperty("emissionSculk", String.valueOf(emissionSculk)));
            emissionSculkShrieker = Float.parseFloat(props.getProperty("emissionSculkShrieker", String.valueOf(emissionSculkShrieker)));
            emissionBrewingStand = Float.parseFloat(props.getProperty("emissionBrewingStand", String.valueOf(emissionBrewingStand)));
            emissionEndPortal = Float.parseFloat(props.getProperty("emissionEndPortal", String.valueOf(emissionEndPortal)));
            emissionRedstoneTorch = Float.parseFloat(props.getProperty("emissionRedstoneTorch", String.valueOf(emissionRedstoneTorch)));
            emissionRedstoneLamp = Float.parseFloat(props.getProperty("emissionRedstoneLamp", String.valueOf(emissionRedstoneLamp)));
            emissionCandle = Float.parseFloat(props.getProperty("emissionCandle", String.valueOf(emissionCandle)));
            emissionCaveVines = Float.parseFloat(props.getProperty("emissionCaveVines", String.valueOf(emissionCaveVines)));
            emissionGlowLichen = Float.parseFloat(props.getProperty("emissionGlowLichen", String.valueOf(emissionGlowLichen)));
            emissionFurnace = Float.parseFloat(props.getProperty("emissionFurnace", String.valueOf(emissionFurnace)));
            emissionBlastFurnace = Float.parseFloat(props.getProperty("emissionBlastFurnace", String.valueOf(emissionBlastFurnace)));
            emissionSmoker = Float.parseFloat(props.getProperty("emissionSmoker", String.valueOf(emissionSmoker)));
            emissionEnderChest = Float.parseFloat(props.getProperty("emissionEnderChest", String.valueOf(emissionEnderChest)));
            emissionCopperBulb = Float.parseFloat(props.getProperty("emissionCopperBulb", String.valueOf(emissionCopperBulb)));
            emissionEnchantingTable = Float.parseFloat(props.getProperty("emissionEnchantingTable", String.valueOf(emissionEnchantingTable)));
            emissionCalibratedSculkSensor = Float.parseFloat(props.getProperty("emissionCalibratedSculkSensor", String.valueOf(emissionCalibratedSculkSensor)));
            emissionSeaPickle = Float.parseFloat(props.getProperty("emissionSeaPickle", String.valueOf(emissionSeaPickle)));
            emissionEndGateway = Float.parseFloat(props.getProperty("emissionEndGateway", String.valueOf(emissionEndGateway)));
            emissionTrialSpawner = Float.parseFloat(props.getProperty("emissionTrialSpawner", String.valueOf(emissionTrialSpawner)));
            emissionVault = Float.parseFloat(props.getProperty("emissionVault", String.valueOf(emissionVault)));

            // Firework emission
            fireworkSparkEmissionNits = Integer.parseInt(props.getProperty("fireworkSparkNits", String.valueOf(fireworkSparkEmissionNits)));
            fireworkSparkEmission = (float) fireworkSparkEmissionNits;
            fireworkFlashEmissionNits = Integer.parseInt(props.getProperty("fireworkFlashNits", String.valueOf(fireworkFlashEmissionNits)));
            fireworkFlashEmission = (float) fireworkFlashEmissionNits;

            // Glowing particle emission
            flameParticleEmission = Float.parseFloat(props.getProperty("flameParticleEmission", String.valueOf(flameParticleEmission)));
            lavaParticleEmission = Float.parseFloat(props.getProperty("lavaParticleEmission", String.valueOf(lavaParticleEmission)));
            portalParticleEmission = Float.parseFloat(props.getProperty("portalParticleEmission", String.valueOf(portalParticleEmission)));
            endRodParticleEmission = Float.parseFloat(props.getProperty("endRodParticleEmission", String.valueOf(endRodParticleEmission)));
            glowParticleEmission = Float.parseFloat(props.getProperty("glowParticleEmission", String.valueOf(glowParticleEmission)));
            soulFireFlameParticleEmission = Float.parseFloat(props.getProperty("soulFireFlameParticleEmission", String.valueOf(soulFireFlameParticleEmission)));
            candleFlameParticleEmission = Float.parseFloat(props.getProperty("candleFlameParticleEmission", String.valueOf(candleFlameParticleEmission)));
            enchantingParticleEmission = Float.parseFloat(props.getProperty("enchantingParticleEmission", String.valueOf(enchantingParticleEmission)));
            sculkParticleEmission = Float.parseFloat(props.getProperty("sculkParticleEmission", String.valueOf(sculkParticleEmission)));
            totemParticleEmission = Float.parseFloat(props.getProperty("totemParticleEmission", String.valueOf(totemParticleEmission)));
            dragonBreathParticleEmission = Float.parseFloat(props.getProperty("dragonBreathParticleEmission", String.valueOf(dragonBreathParticleEmission)));
            lavaDripParticleEmission = Float.parseFloat(props.getProperty("lavaDripParticleEmission", String.valueOf(lavaDripParticleEmission)));

            // Per-particle spectral color parameters
            for (int i = 0; i < PARTICLE_TYPE_COUNT; i++) {
                particleTemperatures[i] = Integer.parseInt(props.getProperty("particleTemp." + i, String.valueOf(particleTemperatures[i])));
                particleWavelengths[i] = Integer.parseInt(props.getProperty("particleWL." + i, String.valueOf(particleWavelengths[i])));
                particlePurities[i] = Integer.parseInt(props.getProperty("particlePur." + i, String.valueOf(particlePurities[i])));
            }

            // Per-block uniform glow overrides (only stored when different from default)
            for (EmissiveBlock blk : EmissiveBlock.values()) {
                String key = "uniformGlow_" + blk.getId();
                String val = props.getProperty(key);
                if (val != null) {
                    blk.setUniformGlow(Boolean.parseBoolean(val), false);
                }
            }

            for (int i = 0; i < FIREWORK_COLOR_COUNT; i++) {
                fireworkColorTemperatures[i] = Integer.parseInt(props.getProperty("fireworkColorTemp." + i, String.valueOf(fireworkColorTemperatures[i])));
                fireworkColorWavelength[i] = Integer.parseInt(props.getProperty("fireworkColorWL." + i, String.valueOf(fireworkColorWavelength[i])));
                fireworkColorPurity[i] = Integer.parseInt(props.getProperty("fireworkColorPur." + i, String.valueOf(fireworkColorPurity[i])));
            }

            readEnvironmentSettings(props, loadedOptionsVersion);

            // Migrate config forward after reading.
            optionsVersion = CURRENT_OPTIONS_VERSION;
            overwriteConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void overwriteConfig() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        Properties props = new Properties();
        props.setProperty("maxFps", String.valueOf(maxFps));
        props.setProperty("optionsVersion", String.valueOf(optionsVersion));
        props.setProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit));
        props.setProperty("vsync", String.valueOf(vsync));
        props.setProperty("upscalerMode", String.valueOf(upscalerMode));
        props.setProperty("upscalerQuality", String.valueOf(upscalerQuality));
        props.setProperty("upscalerResOverride", String.valueOf(upscalerResOverride));
        props.setProperty("dlssDEnabled", String.valueOf(dlssDEnabled));
        props.setProperty("rayBounces", String.valueOf(rayBounces));
        props.setProperty("simplifiedIndirect", String.valueOf(simplifiedIndirect));
        props.setProperty("multiScatterGGX", String.valueOf(multiScatterGGX));
        props.setProperty("eonDiffuse", String.valueOf(eonDiffuse));
        props.setProperty("diffuseModel", String.valueOf(diffuseModel));
        props.setProperty("shaderPackPath", String.valueOf(shaderPackPath));
        // Save current window position/size via LWJGL — only if restore succeeded
        // (prevents PrismLauncher's default centered position from overwriting saved values after a crash)
        if (windowRestoreSucceeded) {
            try {
                long handle = net.minecraft.client.MinecraftClient.getInstance().getWindow().getHandle();
                int[] xBuf = new int[1], yBuf = new int[1], wBuf = new int[1], hBuf = new int[1];
                org.lwjgl.glfw.GLFW.glfwGetWindowPos(handle, xBuf, yBuf);
                org.lwjgl.glfw.GLFW.glfwGetWindowSize(handle, wBuf, hBuf);
                if (wBuf[0] > 0 && hBuf[0] > 0) {
                    windowPosX = xBuf[0];
                    windowPosY = yBuf[0];
                    windowWidth = wBuf[0];
                    windowHeight = hBuf[0];
                }
            } catch (Exception ignored) {}
        }
        props.setProperty("windowPosX", String.valueOf(windowPosX));
        props.setProperty("windowPosY", String.valueOf(windowPosY));
        props.setProperty("windowWidth", String.valueOf(windowWidth));
        props.setProperty("windowHeight", String.valueOf(windowHeight));
        props.setProperty("loggingEnabled", String.valueOf(loggingEnabled));
        props.setProperty("gpuDebugLabels", String.valueOf(gpuDebugLabels));
        props.setProperty("displacementEnabled", String.valueOf(displacementEnabled));
        props.setProperty("displacementDepthCapPercent", String.valueOf(displacementDepthCapPercent));
        props.setProperty("displacementQuality", String.valueOf(displacementQuality));
        props.setProperty("tessMaxLevel", String.valueOf(tessMaxLevel));
        props.setProperty("tessNearDist", String.valueOf(tessNearDist));
        props.setProperty("tessMidDist", String.valueOf(tessMidDist));
        props.setProperty("tessFarDist", String.valueOf(tessFarDist));
        props.setProperty("useV2Engine", String.valueOf(useV2Engine));
        props.setProperty("displacementFadeDistance", String.valueOf(displacementFadeDistance));
        props.setProperty("serEnabled", String.valueOf(serEnabled));
        props.setProperty("serHintsEnabled", String.valueOf(serHintsEnabled));
        props.setProperty("sharcEnabled", String.valueOf(sharcEnabled));
        props.setProperty("sharcSceneScaleTenths", String.valueOf(sharcSceneScaleTenths));
        props.setProperty("sharcRoughnessThresholdPercent", String.valueOf(sharcRoughnessThresholdPercent));
        props.setProperty("sharcAccumulationFrames", String.valueOf(sharcAccumulationFrames));
        props.setProperty("sharcStaleFrames", String.valueOf(sharcStaleFrames));
        props.setProperty("sharcDownscale", String.valueOf(sharcDownscale));
        props.setProperty("sharcUpdateBlockSize", String.valueOf(sharcUpdateBlockSize));
        props.setProperty("sharcUpdateBounces", String.valueOf(sharcUpdateBounces));
        props.setProperty("sharcCapacityExponent", String.valueOf(sharcCapacityExponent));
        props.setProperty("sharcQueryMode", String.valueOf(sharcQueryMode));
        props.setProperty("sharcQualityPreset", String.valueOf(sharcQualityPreset));
        props.setProperty("autoPBREnabled", String.valueOf(autoPBREnabled));
        props.setProperty("materialCompatEnabled", String.valueOf(materialCompatEnabled));
        props.setProperty("materialCompatCtmEnabled", String.valueOf(materialCompatCtmEnabled));
        props.setProperty("materialCompatRandomEnabled", String.valueOf(materialCompatRandomEnabled));
        props.setProperty("materialCompatNaturalEnabled", String.valueOf(materialCompatNaturalEnabled));
        props.setProperty("materialCompatColorsEnabled", String.valueOf(materialCompatColorsEnabled));
        props.setProperty("materialCompatOverlaysEnabled", String.valueOf(materialCompatOverlaysEnabled));
        props.setProperty("materialCompatLegacyMcPatcherEnabled", String.valueOf(materialCompatLegacyMcPatcherEnabled));
        props.setProperty("materialCompatPhysicalEmissiveEnabled", String.valueOf(materialCompatPhysicalEmissiveEnabled));
        props.setProperty("materialCompatCtmAtlasAdmissionLimit", String.valueOf(materialCompatCtmAtlasAdmissionLimit));
        props.setProperty("textureStreamingV2", String.valueOf(textureStreamingV2));
        props.setProperty("textureTieredArrays", String.valueOf(textureTieredArrays));
        props.setProperty("vanillaBlockAtlasBypass", String.valueOf(vanillaBlockAtlasBypass));
        props.setProperty("sparseAuxUpload", String.valueOf(sparseAuxUpload));
        props.setProperty("ctmDemandResidency", String.valueOf(ctmDemandResidency));
        props.setProperty("materialTableDirtyUpdates", String.valueOf(materialTableDirtyUpdates));
        props.setProperty("eagerSpriteImageCache", String.valueOf(eagerSpriteImageCache));
        props.setProperty("materialCompatFullPreloadDiagnostic", String.valueOf(materialCompatFullPreloadDiagnostic));
        props.setProperty("outputScale2x", String.valueOf(outputScale2x));
        props.setProperty("reflexEnabled", String.valueOf(reflexEnabled));
        props.setProperty("reflexBoost", String.valueOf(reflexBoost));
        props.setProperty("vrrMode", String.valueOf(vrrMode));
        props.setProperty("frameGenMode", String.valueOf(frameGenMode));
        props.setProperty("frameGenMultiplier", String.valueOf(frameGenMultiplier));
        props.setProperty("dlssgQueueParallelism", String.valueOf(dlssgQueueParallelism));
        props.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        props.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));
        props.setProperty("chunkCullDistance", String.valueOf(chunkCullDistance));
        props.setProperty("tonemappingMode", String.valueOf(tonemappingMode));
        props.setProperty("sdrTonemappingMode", String.valueOf(sdrTonemappingMode));
        // Save per-tonemapper params
        for (int m = 0; m < 9; m++) {
            for (int p = 0; p < 8; p++) {
                props.setProperty("tmParam_" + m + "_" + p, String.valueOf(tonemapParams[m][p]));
            }
        }
        props.setProperty("sdrTransferFunction", String.valueOf(sdrTransferFunction));
        props.setProperty("minExposureTenK", String.valueOf(minExposureTenK));
        props.setProperty("maxExposure", String.valueOf(maxExposure));
        props.setProperty("exposureCompensation", String.valueOf(exposureCompensation));
        props.setProperty("manualExposureEnabled", String.valueOf(manualExposureEnabled));
        props.setProperty("manualExposureEV100Tenths", String.valueOf(manualExposureEV100Tenths));
        props.setProperty("sharpenerMode", String.valueOf(sharpenerMode));
        props.setProperty("casSharpnessPercent", String.valueOf(casSharpnessPercent));
        props.setProperty("brightAdaptSpeedTenths", String.valueOf(brightAdaptSpeedTenths));
        props.setProperty("darkAdaptSpeedTenths", String.valueOf(darkAdaptSpeedTenths));
        props.setProperty("sceneChangeThresholdTenths", String.valueOf(sceneChangeThresholdTenths));
        props.setProperty("centerWeightPercent", String.valueOf(centerWeightPercent));
        props.setProperty("highlightWeightPercent", String.valueOf(highlightWeightPercent));
        props.setProperty("middleGreyPercent", String.valueOf(middleGreyPercent));
        props.setProperty("LwhiteTenths", String.valueOf(LwhiteTenths));
        props.setProperty("saturationPercent", String.valueOf(saturationPercent));
        props.setProperty("saturationAdaptive", String.valueOf(saturationAdaptive));
        // HDR tonemapper + PsychoV
        props.setProperty("hdrTonemapMode", String.valueOf(hdrTonemapMode));
        props.setProperty("psychoEnabled", String.valueOf(psychoEnabled));
        props.setProperty("psychoHighlightsPercent", String.valueOf(psychoHighlightsPercent));
        props.setProperty("psychoShadowsPercent", String.valueOf(psychoShadowsPercent));
        props.setProperty("psychoContrastPercent", String.valueOf(psychoContrastPercent));
        props.setProperty("psychoPurityPercent", String.valueOf(psychoPurityPercent));
        props.setProperty("psychoBleachingPercent", String.valueOf(psychoBleachingPercent));
        props.setProperty("psychoClipPointTenths", String.valueOf(psychoClipPointTenths));
        props.setProperty("psychoHueRestorePercent", String.valueOf(psychoHueRestorePercent));
        props.setProperty("psychoAdaptContrastPercent", String.valueOf(psychoAdaptContrastPercent));
        props.setProperty("psychoWhiteCurve", String.valueOf(psychoWhiteCurve));
        props.setProperty("psychoConeExponentPercent", String.valueOf(psychoConeExponentPercent));
        props.setProperty("psychoPeakSDRTenths", String.valueOf(psychoPeakSDRTenths));
        props.setProperty("upscalerPreset", String.valueOf(upscalerPreset));
        props.setProperty("hdrEnabled", String.valueOf(hdrEnabled));
        props.setProperty("hdrScrgbMode", String.valueOf(hdrScrgbMode));
        props.setProperty("hdrPeakNits", String.valueOf(hdrPeakNits));
        props.setProperty("hdrPaperWhiteNits", String.valueOf(hdrPaperWhiteNits));
        props.setProperty("hdrUiBrightnessNits", String.valueOf(hdrUiBrightnessNits));
        for (var entry : blockTemperatures.entrySet()) {
            props.setProperty("blockTemp_" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (var entry : blockWavelengths.entrySet()) {
            props.setProperty("blockWavelength_" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (var entry : blockPurities.entrySet()) {
            props.setProperty("blockPurity_" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        for (var entry : blockGamutBoosts.entrySet()) {
            props.setProperty("blockGamut_" + entry.getKey(), String.valueOf(entry.getValue()));
        }
        props.setProperty("lavaTextureEmissionEnabled", String.valueOf(lavaTextureEmissionEnabled));
        props.setProperty("emissionLava", String.valueOf(emissionLava));
        props.setProperty("emissionFire", String.valueOf(emissionFire));
        props.setProperty("emissionSoulFire", String.valueOf(emissionSoulFire));
        props.setProperty("emissionTorch", String.valueOf(emissionTorch));
        props.setProperty("emissionSoulTorch", String.valueOf(emissionSoulTorch));
        props.setProperty("emissionLantern", String.valueOf(emissionLantern));
        props.setProperty("emissionSoulLantern", String.valueOf(emissionSoulLantern));
        props.setProperty("emissionCampfire", String.valueOf(emissionCampfire));
        props.setProperty("emissionSoulCampfire", String.valueOf(emissionSoulCampfire));
        props.setProperty("emissionGlowstone", String.valueOf(emissionGlowstone));
        props.setProperty("emissionShroomlight", String.valueOf(emissionShroomlight));
        props.setProperty("emissionSeaLantern", String.valueOf(emissionSeaLantern));
        props.setProperty("emissionFroglight", String.valueOf(emissionFroglight));
        props.setProperty("emissionMagmaBlock", String.valueOf(emissionMagmaBlock));
        props.setProperty("emissionBeacon", String.valueOf(emissionBeacon));
        props.setProperty("emissionEndRod", String.valueOf(emissionEndRod));
        props.setProperty("emissionJackOLantern", String.valueOf(emissionJackOLantern));
        props.setProperty("emissionNetherPortal", String.valueOf(emissionNetherPortal));
        props.setProperty("emissionCryingObsidian", String.valueOf(emissionCryingObsidian));
        props.setProperty("emissionRespawnAnchor", String.valueOf(emissionRespawnAnchor));
        props.setProperty("emissionConduit", String.valueOf(emissionConduit));
        props.setProperty("emissionAmethystCluster", String.valueOf(emissionAmethystCluster));
        props.setProperty("emissionSculkSensor", String.valueOf(emissionSculkSensor));
        props.setProperty("emissionSculkCatalyst", String.valueOf(emissionSculkCatalyst));
        props.setProperty("emissionSculkVein", String.valueOf(emissionSculkVein));
        props.setProperty("emissionSculk", String.valueOf(emissionSculk));
        props.setProperty("emissionSculkShrieker", String.valueOf(emissionSculkShrieker));
        props.setProperty("emissionBrewingStand", String.valueOf(emissionBrewingStand));
        props.setProperty("emissionEndPortal", String.valueOf(emissionEndPortal));
        props.setProperty("emissionRedstoneTorch", String.valueOf(emissionRedstoneTorch));
        props.setProperty("emissionRedstoneLamp", String.valueOf(emissionRedstoneLamp));
        props.setProperty("emissionCandle", String.valueOf(emissionCandle));
        props.setProperty("emissionCaveVines", String.valueOf(emissionCaveVines));
        props.setProperty("emissionGlowLichen", String.valueOf(emissionGlowLichen));
        props.setProperty("emissionFurnace", String.valueOf(emissionFurnace));
        props.setProperty("emissionBlastFurnace", String.valueOf(emissionBlastFurnace));
        props.setProperty("emissionSmoker", String.valueOf(emissionSmoker));
        props.setProperty("emissionEnderChest", String.valueOf(emissionEnderChest));
        props.setProperty("emissionCopperBulb", String.valueOf(emissionCopperBulb));
        props.setProperty("emissionEnchantingTable", String.valueOf(emissionEnchantingTable));
        props.setProperty("emissionCalibratedSculkSensor", String.valueOf(emissionCalibratedSculkSensor));
        props.setProperty("emissionSeaPickle", String.valueOf(emissionSeaPickle));
        props.setProperty("emissionEndGateway", String.valueOf(emissionEndGateway));
        props.setProperty("emissionTrialSpawner", String.valueOf(emissionTrialSpawner));
        props.setProperty("emissionVault", String.valueOf(emissionVault));
        // Firework emission
        props.setProperty("fireworkSparkNits", String.valueOf(fireworkSparkEmissionNits));
        props.setProperty("fireworkFlashNits", String.valueOf(fireworkFlashEmissionNits));
        // Glowing particle emission
        props.setProperty("flameParticleEmission", String.valueOf(flameParticleEmission));
        props.setProperty("lavaParticleEmission", String.valueOf(lavaParticleEmission));
        props.setProperty("portalParticleEmission", String.valueOf(portalParticleEmission));
        props.setProperty("endRodParticleEmission", String.valueOf(endRodParticleEmission));
        props.setProperty("glowParticleEmission", String.valueOf(glowParticleEmission));
        props.setProperty("soulFireFlameParticleEmission", String.valueOf(soulFireFlameParticleEmission));
        props.setProperty("candleFlameParticleEmission", String.valueOf(candleFlameParticleEmission));
        props.setProperty("enchantingParticleEmission", String.valueOf(enchantingParticleEmission));
        props.setProperty("sculkParticleEmission", String.valueOf(sculkParticleEmission));
        props.setProperty("totemParticleEmission", String.valueOf(totemParticleEmission));
        props.setProperty("dragonBreathParticleEmission", String.valueOf(dragonBreathParticleEmission));
        props.setProperty("lavaDripParticleEmission", String.valueOf(lavaDripParticleEmission));
        // Per-particle spectral color parameters
        for (int i = 0; i < PARTICLE_TYPE_COUNT; i++) {
            props.setProperty("particleTemp." + i, String.valueOf(particleTemperatures[i]));
            props.setProperty("particleWL." + i, String.valueOf(particleWavelengths[i]));
            props.setProperty("particlePur." + i, String.valueOf(particlePurities[i]));
        }
        // Per-block uniform glow overrides (only save when different from default)
        for (EmissiveBlock blk : EmissiveBlock.values()) {
            if (blk.isUniformGlow() != blk.getDefaultUniformGlow()) {
                props.setProperty("uniformGlow_" + blk.getId(), String.valueOf(blk.isUniformGlow()));
            }
        }
        for (int i = 0; i < FIREWORK_COLOR_COUNT; i++) {
            props.setProperty("fireworkColorTemp." + i, String.valueOf(fireworkColorTemperatures[i]));
            props.setProperty("fireworkColorWL." + i, String.valueOf(fireworkColorWavelength[i]));
            props.setProperty("fireworkColorPur." + i, String.valueOf(fireworkColorPurity[i]));
        }
        props.setProperty("environmentEditingDimension", String.valueOf(environmentEditingDimension));
        for (int dim = 0; dim < DIM_COUNT; dim++) {
            props.setProperty("env.skyBrightnessPercent." + dim, String.valueOf(skyBrightnessPercent[dim]));
            props.setProperty("env.rainBlendPercent." + dim, String.valueOf(rainBlendPercent[dim]));
            props.setProperty("env.cloudBrightnessPercent." + dim, String.valueOf(cloudBrightnessPercent[dim]));
            props.setProperty("env.cloudAlphaPercent." + dim, String.valueOf(cloudAlphaPercent[dim]));
            props.setProperty("env.cloudHeightOffset." + dim, String.valueOf(cloudHeightOffset[dim]));
            props.setProperty("env.cloudPuffinessPercent." + dim, String.valueOf(cloudPuffinessPercent[dim]));
            props.setProperty("env.cloudDetailScalePercent." + dim, String.valueOf(cloudDetailScalePercent[dim]));
            props.setProperty("env.cloudDetailStrengthPercent." + dim, String.valueOf(cloudDetailStrengthPercent[dim]));
            props.setProperty("env.cloudAnisotropyPercent." + dim, String.valueOf(cloudAnisotropyPercent[dim]));
            props.setProperty("env.cloudShadowStrengthPercent." + dim, String.valueOf(cloudShadowStrengthPercent[dim]));
            props.setProperty("env.cloudThicknessBlocks." + dim, String.valueOf(cloudThicknessBlocks[dim]));
            props.setProperty("env.cloudDensityPercent." + dim, String.valueOf(cloudDensityPercent[dim]));
            props.setProperty("env.cloudNoiseAffectsShadows." + dim, String.valueOf(cloudNoiseAffectsShadows[dim]));
        }
        props.setProperty("wetSurfaceStrengthPercent", String.valueOf(wetSurfaceStrengthPercent));
        for (int dim = 0; dim < DIM_COUNT; dim++) {
            props.setProperty("env.waterTintR." + dim, String.valueOf(waterTintR[dim]));
            props.setProperty("env.waterTintG." + dim, String.valueOf(waterTintG[dim]));
            props.setProperty("env.waterTintB." + dim, String.valueOf(waterTintB[dim]));
            props.setProperty("env.waterFogStrengthPercent." + dim, String.valueOf(waterFogStrengthPercent[dim]));
            props.setProperty("env.sunSizePercent." + dim, String.valueOf(sunSizePercent[dim]));
            props.setProperty("env.sunIntensityPercent." + dim, String.valueOf(sunIntensityPercent[dim]));
            props.setProperty("env.moonSizePercent." + dim, String.valueOf(moonSizePercent[dim]));
            props.setProperty("env.moonIntensityPercent." + dim, String.valueOf(moonIntensityPercent[dim]));
        }
        // Sun/Moon orbit (Overworld-only)
        props.setProperty("sunPathMode", String.valueOf(sunPathMode));
        props.setProperty("sunInclinationDeg", String.valueOf(sunInclinationDeg));
        props.setProperty("sunAzimuthOffsetDeg", String.valueOf(sunAzimuthOffsetDeg));
        props.setProperty("moonFollowSun", String.valueOf(moonFollowSun));
        props.setProperty("moonInclinationDeg", String.valueOf(moonInclinationDeg));
        props.setProperty("moonAzimuthOffsetDeg", String.valueOf(moonAzimuthOffsetDeg));

        // Persistent UI state
        props.setProperty("showWelcomeMessage", String.valueOf(showWelcomeMessage));
        props.setProperty("uiGlobalAlphaPercent", String.valueOf(uiGlobalAlphaPercent));
        props.setProperty("materialLabOpacityPercent", String.valueOf(materialLabOpacityPercent));
        props.setProperty("uiAdaptiveDimming", String.valueOf(uiAdaptiveDimming));
        props.setProperty("advancedMode", String.valueOf(advancedMode));

        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (OutputStream out = Files.newOutputStream(path)) {
            props.store(out, "Options");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void readEnvironmentSettings(Properties props, int loadedOptionsVersion) {
        if (loadedOptionsVersion < 4) {
            setEnvironmentDefaults();
            return;
        }

        environmentEditingDimension = clampDimIndex(Integer.parseInt(
            props.getProperty("environmentEditingDimension", String.valueOf(environmentEditingDimension))));

        for (int dim = 0; dim < DIM_COUNT; dim++) {
            skyBrightnessPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.skyBrightnessPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            rainBlendPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.rainBlendPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            cloudBrightnessPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.cloudBrightnessPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            cloudAlphaPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.cloudAlphaPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            cloudHeightOffset[dim] = Math.max(-64, Math.min(64, Integer.parseInt(
                props.getProperty("env.cloudHeightOffset." + dim, "0"))));

            if (loadedOptionsVersion >= 5) {
                cloudPuffinessPercent[dim] = clampPercent(Integer.parseInt(
                    props.getProperty("env.cloudPuffinessPercent." + dim, "3")));
                cloudDetailScalePercent[dim] = clampPercent(Integer.parseInt(
                    props.getProperty("env.cloudDetailScalePercent." + dim,
                        String.valueOf(CLOUD_DETAIL_SCALE_DEFAULT_PERCENT))));
                cloudDetailStrengthPercent[dim] = clampPercent(Integer.parseInt(
                    props.getProperty("env.cloudDetailStrengthPercent." + dim,
                        String.valueOf(CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT))));
                cloudAnisotropyPercent[dim] = clampAnisotropyPercent(Integer.parseInt(
                    props.getProperty("env.cloudAnisotropyPercent." + dim, "80")));
                cloudShadowStrengthPercent[dim] = clampPercent(Integer.parseInt(
                    props.getProperty("env.cloudShadowStrengthPercent." + dim, String.valueOf(PERCENT_DEFAULT))));

                // Hidden setting: force anisotropy off.
                cloudAnisotropyPercent[dim] = 0;

                // Upgrade defaults: older configs defaulted to 100% (almost no visible breakup).
                // If the user never touched these, bump to new defaults.
                if (loadedOptionsVersion < 12) {
                    if (cloudDetailScalePercent[dim] == PERCENT_DEFAULT) {
                        cloudDetailScalePercent[dim] = CLOUD_DETAIL_SCALE_DEFAULT_PERCENT;
                    }
                    if (cloudDetailStrengthPercent[dim] == PERCENT_DEFAULT) {
                        cloudDetailStrengthPercent[dim] = CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT;
                    }
                }
            } else {
                cloudPuffinessPercent[dim] = 3;
                cloudDetailScalePercent[dim] = CLOUD_DETAIL_SCALE_DEFAULT_PERCENT;
                cloudDetailStrengthPercent[dim] = CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT;
                cloudAnisotropyPercent[dim] = 0;
                cloudShadowStrengthPercent[dim] = PERCENT_DEFAULT;
            }

            if (loadedOptionsVersion >= 6) {
                cloudThicknessBlocks[dim] = Math.max(1, Math.min(16, Integer.parseInt(
                    props.getProperty("env.cloudThicknessBlocks." + dim, "4"))));
            } else {
                cloudThicknessBlocks[dim] = 4;
            }

            if (loadedOptionsVersion >= 9) {
                cloudDensityPercent[dim] = clampPercent(Integer.parseInt(
                    props.getProperty("env.cloudDensityPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            } else {
                cloudDensityPercent[dim] = PERCENT_DEFAULT;
            }

            if (loadedOptionsVersion >= 11) {
                cloudNoiseAffectsShadows[dim] = Math.max(0, Math.min(1, Integer.parseInt(
                    props.getProperty("env.cloudNoiseAffectsShadows." + dim,
                        String.valueOf(dim == DIM_OVERWORLD ? 1 : 0)))));
            } else {
                cloudNoiseAffectsShadows[dim] = dim == DIM_OVERWORLD ? 1 : 0;
            }
        }

        if (loadedOptionsVersion >= 20) {
            wetSurfaceStrengthPercent = clamp(Integer.parseInt(
                props.getProperty("wetSurfaceStrengthPercent", "100")), 0, 200);
        }

        disableVolumetricCloudModule();
        try {
            nativeSetCloudQuality(0, false);
            nativeSetWetSurfaceStrength(wetSurfaceStrengthPercent / 100.0f, false);
        } catch (UnsatisfiedLinkError ignored) {}

        for (int dim = 0; dim < DIM_COUNT; dim++) {
            waterTintR[dim] = clampColorChannel(Integer.parseInt(
                props.getProperty("env.waterTintR." + dim, String.valueOf(WATER_TINT_R_DEFAULT))));
            waterTintG[dim] = clampColorChannel(Integer.parseInt(
                props.getProperty("env.waterTintG." + dim, String.valueOf(WATER_TINT_G_DEFAULT))));
            waterTintB[dim] = clampColorChannel(Integer.parseInt(
                props.getProperty("env.waterTintB." + dim, String.valueOf(WATER_TINT_B_DEFAULT))));
            waterFogStrengthPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.waterFogStrengthPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            sunSizePercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.sunSizePercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            sunIntensityPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.sunIntensityPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            moonSizePercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.moonSizePercent." + dim, String.valueOf(PERCENT_DEFAULT))));
            moonIntensityPercent[dim] = clampPercent(Integer.parseInt(
                props.getProperty("env.moonIntensityPercent." + dim,
                    String.valueOf(dim == DIM_OVERWORLD ? MOON_INTENSITY_DEFAULT_OVERWORLD_PERCENT : PERCENT_DEFAULT))));
        }

        // Sun/Moon orbit (Overworld-only, not per-dimension)
        sunPathMode = Math.max(0, Math.min(1, Integer.parseInt(
            props.getProperty("sunPathMode", String.valueOf(SUN_PATH_MODE_DEFAULT)))));
        sunInclinationDeg = Math.max(0, Math.min(90, Integer.parseInt(
            props.getProperty("sunInclinationDeg", String.valueOf(SUN_INCLINATION_DEFAULT)))));
        sunAzimuthOffsetDeg = Math.max(-180, Math.min(180, Integer.parseInt(
            props.getProperty("sunAzimuthOffsetDeg", String.valueOf(SUN_AZIMUTH_OFFSET_DEFAULT)))));
        moonFollowSun = Boolean.parseBoolean(
            props.getProperty("moonFollowSun", "true"));
        moonInclinationDeg = Math.max(0, Math.min(90, Integer.parseInt(
            props.getProperty("moonInclinationDeg", String.valueOf(MOON_INCLINATION_DEFAULT)))));
        moonAzimuthOffsetDeg = Math.max(-180, Math.min(180, Integer.parseInt(
            props.getProperty("moonAzimuthOffsetDeg", String.valueOf(MOON_AZIMUTH_OFFSET_DEFAULT)))));

        // Persistent UI state
        showWelcomeMessage = Boolean.parseBoolean(
            props.getProperty("showWelcomeMessage", "true"));
        uiGlobalAlphaPercent = Math.max(0, Math.min(100, Integer.parseInt(
            props.getProperty("uiGlobalAlphaPercent", "55"))));
        materialLabOpacityPercent = clamp(Integer.parseInt(
            props.getProperty("materialLabOpacityPercent", "82")), 35, 100);
        uiAdaptiveDimming = Boolean.parseBoolean(
            props.getProperty("uiAdaptiveDimming", "false"));
        advancedMode = Boolean.parseBoolean(
            props.getProperty("advancedMode", "false"));

        // Apply UI theme from loaded values
        com.radiance.client.gui.RadianceTheme.setGlobalAlpha(uiGlobalAlphaPercent / 100f);
        com.radiance.client.gui.RadianceTheme.setAdaptiveDimmingEnabled(uiAdaptiveDimming);
    }

    private static void setEnvironmentDefaults() {
        environmentEditingDimension = DIM_OVERWORLD;
        for (int dim = 0; dim < DIM_COUNT; dim++) {
            skyBrightnessPercent[dim] = PERCENT_DEFAULT;
            rainBlendPercent[dim] = PERCENT_DEFAULT;
            cloudBrightnessPercent[dim] = PERCENT_DEFAULT;
            cloudAlphaPercent[dim] = PERCENT_DEFAULT;
            cloudHeightOffset[dim] = 0;
            cloudPuffinessPercent[dim] = 3;
            cloudDetailScalePercent[dim] = CLOUD_DETAIL_SCALE_DEFAULT_PERCENT;
            cloudDetailStrengthPercent[dim] = CLOUD_DETAIL_STRENGTH_DEFAULT_PERCENT;
            cloudAnisotropyPercent[dim] = 0;
            cloudShadowStrengthPercent[dim] = PERCENT_DEFAULT;
            cloudThicknessBlocks[dim] = 4;
            cloudDensityPercent[dim] = PERCENT_DEFAULT;
            cloudNoiseAffectsShadows[dim] = dim == DIM_OVERWORLD ? 1 : 0;
            waterTintR[dim] = WATER_TINT_R_DEFAULT;
            waterTintG[dim] = WATER_TINT_G_DEFAULT;
            waterTintB[dim] = WATER_TINT_B_DEFAULT;
            waterFogStrengthPercent[dim] = PERCENT_DEFAULT;
            sunSizePercent[dim] = PERCENT_DEFAULT;
            sunIntensityPercent[dim] = PERCENT_DEFAULT;
            moonSizePercent[dim] = PERCENT_DEFAULT;
            moonIntensityPercent[dim] = dim == DIM_OVERWORLD ? MOON_INTENSITY_DEFAULT_OVERWORLD_PERCENT : PERCENT_DEFAULT;
        }

        disableVolumetricCloudModule();

        // Sun/Moon orbit defaults
        sunPathMode = SUN_PATH_MODE_DEFAULT;
        sunInclinationDeg = SUN_INCLINATION_DEFAULT;
        sunAzimuthOffsetDeg = SUN_AZIMUTH_OFFSET_DEFAULT;
        moonFollowSun = true;
        moonInclinationDeg = MOON_INCLINATION_DEFAULT;
        moonAzimuthOffsetDeg = MOON_AZIMUTH_OFFSET_DEFAULT;
    }

    private static void disableVolumetricCloudModule() {
        volCloudQuality = 0;
        volCloudDensityTenths = 10;
        volCloudCoveragePercent = 35;
        volCloudTypePercent = 67;
        volCloudSpeedTenths = 50;
        volCloudAltitude = 192;
        volCloudThickness = 64;
        volCloudDetailStrengthPercent = 100;
        volCloudScatterOctaves = 3;
        volCloudAmbientPercent = 100;
        volCloudTemporalPercent = 0;
        volCloudNoiseScale = 1000;
        volCloudCellFrequencyTenths = 40;
        volCloudAtmosphereFadeDist = 800;
        volCloudDebugMode = 0;
        volCloudWindAngleDegrees = 0;
        volCloudMarchSteps = 0;
        volCloudLightSteps = 0;
        volCloudResDivisor = 0;
        volCloudNoiseRes = 128;
    }

    public static void setFpvEnabled(boolean value, boolean write) {
        fpvEnabled = value;
        syncFpvSettings();
        if (write) overwriteConfig();
    }

    public static void setFpvOffsetForward(int cm, boolean write) {
        fpvOffsetForward = clamp(cm, -30, 30);
        syncFpvSettings();
        if (write) overwriteConfig();
    }

    public static void setFpvOffsetVertical(int cm, boolean write) {
        fpvOffsetVertical = clamp(cm, -30, 30);
        syncFpvSettings();
        if (write) overwriteConfig();
    }

    public static void setFpvOffsetLateral(int cm, boolean write) {
        fpvOffsetLateral = clamp(cm, -20, 20);
        syncFpvSettings();
        if (write) overwriteConfig();
    }

    /** Sync FPV option values to the FirstPersonView runtime fields. */
    public static void syncFpvSettings() {
        com.radiance.client.fpv.FirstPersonView.enabled = fpvEnabled;
        com.radiance.client.fpv.FirstPersonView.offsetForward = fpvOffsetForward / 100.0f;
        com.radiance.client.fpv.FirstPersonView.offsetVertical = fpvOffsetVertical / 100.0f;
        com.radiance.client.fpv.FirstPersonView.offsetLateral = fpvOffsetLateral / 100.0f;
    }

    private static int clampDimIndex(int dim) {
        return Math.max(0, Math.min(DIM_COUNT - 1, dim));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static int clampPercent(int value) {
        return Math.max(0, Math.min(300, value));
    }

    private static int clampAnisotropyPercent(int value) {
        return Math.max(0, Math.min(95, value));
    }

    private static int clampColorChannel(int value) {
        return Math.max(0, Math.min(100, value));
    }

    public static int getEnvironmentDimensionIndex(ClientWorld world) {
        if (world == null || world.getRegistryKey() == null || world.getRegistryKey().getValue() == null) {
            return DIM_OVERWORLD;
        }

        String path = world.getRegistryKey().getValue().getPath();
        if ("the_nether".equals(path)) {
            return DIM_NETHER;
        }
        if ("the_end".equals(path)) {
            return DIM_END;
        }
        return DIM_OVERWORLD;
    }

    public static int getEnvironmentEditingDimension() {
        return environmentEditingDimension;
    }

    public static void setEnvironmentEditingDimension(int dim, boolean write) {
        environmentEditingDimension = clampDimIndex(dim);
        if (write) {
            overwriteConfig();
        }
    }

    public static int[] getDimensionValues(int[] values) {
        return values;
    }

    public static float getSkyBrightness(int dim) {
        return skyBrightnessPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setSkyBrightnessPercent(int dim, int value, boolean write) {
        skyBrightnessPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getRainBlendStrength(int dim) {
        return rainBlendPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setRainBlendPercent(int dim, int value, boolean write) {
        rainBlendPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudBrightness(int dim) {
        return cloudBrightnessPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudBrightnessPercent(int dim, int value, boolean write) {
        cloudBrightnessPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudAlpha(int dim) {
        return cloudAlphaPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudAlphaPercent(int dim, int value, boolean write) {
        cloudAlphaPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static int getCloudHeightOffset(int dim) {
        return cloudHeightOffset[clampDimIndex(dim)];
    }

    public static void setCloudHeightOffset(int dim, int value, boolean write) {
        cloudHeightOffset[clampDimIndex(dim)] = Math.max(-64, Math.min(64, value));
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudPuffiness(int dim) {
        return cloudPuffinessPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudPuffinessPercent(int dim, int value, boolean write) {
        cloudPuffinessPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudDetailScale(int dim) {
        return cloudDetailScalePercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudDetailScalePercent(int dim, int value, boolean write) {
        cloudDetailScalePercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudDetailStrength(int dim) {
        return cloudDetailStrengthPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudDetailStrengthPercent(int dim, int value, boolean write) {
        cloudDetailStrengthPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudAnisotropy(int dim) {
        return 0.0f;
    }

    public static void setCloudAnisotropyPercent(int dim, int value, boolean write) {
        // Hidden setting: always force off.
        cloudAnisotropyPercent[clampDimIndex(dim)] = 0;
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudShadowStrength(int dim) {
        return cloudShadowStrengthPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudShadowStrengthPercent(int dim, int value, boolean write) {
        cloudShadowStrengthPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getCloudDensity(int dim) {
        return cloudDensityPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setCloudDensityPercent(int dim, int value, boolean write) {
        cloudDensityPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static boolean getCloudNoiseAffectsShadows(int dim) {
        return cloudNoiseAffectsShadows[clampDimIndex(dim)] != 0;
    }

    public static void setCloudNoiseAffectsShadows(int dim, boolean enabled, boolean write) {
        cloudNoiseAffectsShadows[clampDimIndex(dim)] = enabled ? 1 : 0;
        if (write) {
            overwriteConfig();
        }
    }


    public static int getCloudThicknessBlocks(int dim) {
        return cloudThicknessBlocks[clampDimIndex(dim)];
    }

    public static void setCloudThicknessBlocks(int dim, int value, boolean write) {
        cloudThicknessBlocks[clampDimIndex(dim)] = Math.max(1, Math.min(16, value));
        if (write) {
            overwriteConfig();
        }
    }

    // ── Volumetric Cloud Module setters (global) ──

    public static void setVolCloudQuality(int quality, boolean write) {
        disableVolumetricCloudModule();
        try { nativeSetCloudQuality(0, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudDensityTenths(int tenths, boolean write) {
        volCloudDensityTenths = clamp(tenths, 1, 30);
        try { nativeSetCloudDensity(volCloudDensityTenths / 10.0f, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudCoveragePercent(int percent, boolean write) {
        volCloudCoveragePercent = clamp(percent, 0, 100);
        try { nativeSetCloudCoverage(volCloudCoveragePercent / 100.0f, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudTypePercent(int percent, boolean write) {
        volCloudTypePercent = clamp(percent, 0, 100);
        try { nativeSetCloudType(volCloudTypePercent / 100.0f, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudSpeedTenths(int tenths, boolean write) {
        volCloudSpeedTenths = clamp(tenths, 0, 300);
        try { nativeSetCloudSpeed(volCloudSpeedTenths / 50.0f, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudAltitude(int altitude, boolean write) {
        volCloudAltitude = clamp(altitude, 128, 320);
        try { nativeSetCloudAltitude((float) volCloudAltitude, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudThickness(int thickness, boolean write) {
        volCloudThickness = clamp(thickness, 32, 128);
        try { nativeSetCloudThicknessVol((float) volCloudThickness, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudDetailStrengthPercent(int percent, boolean write) {
        volCloudDetailStrengthPercent = clamp(percent, 0, 200);
        try { nativeSetCloudDetailStrength(volCloudDetailStrengthPercent / 100.0f, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static void setVolCloudScatterOctaves(int octaves, boolean write) {
        volCloudScatterOctaves = clamp(octaves, 1, 4);
        try { nativeSetCloudScatterOctaves(volCloudScatterOctaves, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) overwriteConfig();
    }

    public static float getWaterTintR(int dim) {
        return waterTintR[clampDimIndex(dim)] / 100.0f;
    }

    public static float getWaterTintG(int dim) {
        return waterTintG[clampDimIndex(dim)] / 100.0f;
    }

    public static float getWaterTintB(int dim) {
        return waterTintB[clampDimIndex(dim)] / 100.0f;
    }

    public static void setWaterTintRPercent(int dim, int value, boolean write) {
        waterTintR[clampDimIndex(dim)] = clampColorChannel(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setWaterTintGPercent(int dim, int value, boolean write) {
        waterTintG[clampDimIndex(dim)] = clampColorChannel(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setWaterTintBPercent(int dim, int value, boolean write) {
        waterTintB[clampDimIndex(dim)] = clampColorChannel(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getWaterFogStrength(int dim) {
        return waterFogStrengthPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setWaterFogStrengthPercent(int dim, int value, boolean write) {
        waterFogStrengthPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getSunSizeMultiplier(int dim) {
        return sunSizePercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setSunSizePercent(int dim, int value, boolean write) {
        sunSizePercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getSunIntensityMultiplier(int dim) {
        return sunIntensityPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setSunIntensityPercent(int dim, int value, boolean write) {
        sunIntensityPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getMoonSizeMultiplier(int dim) {
        return moonSizePercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setMoonSizePercent(int dim, int value, boolean write) {
        moonSizePercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    public static float getMoonIntensityMultiplier(int dim) {
        return moonIntensityPercent[clampDimIndex(dim)] / 100.0f;
    }

    public static void setMoonIntensityPercent(int dim, int value, boolean write) {
        moonIntensityPercent[clampDimIndex(dim)] = clampPercent(value);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Reset all options to defaults ---

    public static void resetAllToDefaults() {
        // Global options
        maxFps = 260;
        vsync = true;
        dlssDEnabled = true;
        rayBounces = 2;
        simplifiedIndirect = false;
        multiScatterGGX = true;
        eonDiffuse = true;
        diffuseModel = DIFFUSE_MODEL_EON;
        sharcEnabled = true;
        sharcSceneScaleTenths = 40;
        sharcRoughnessThresholdPercent = 25;
        sharcAccumulationFrames = 32;
        sharcStaleFrames = 16;
        sharcDownscale = 1;
        sharcUpdateBlockSize = 5;
        sharcUpdateBounces = 4;
        sharcCapacityExponent = 21;
        sharcQueryMode = 0;
        sharcQualityPreset = 1;
        // All per-block overrides baked into LIGHT_DEFS — sliders start neutral
        // Emission defaults
        emissionLava = 1.0f;
        emissionFire = 1.0f;
        emissionSoulFire = 1.0f;
        emissionTorch = 1.0f;
        emissionSoulTorch = 1.0f;
        emissionLantern = 1.0f;
        emissionSoulLantern = 1.0f;
        emissionCampfire = 1.0f;
        emissionSoulCampfire = 1.0f;
        emissionGlowstone = 1.0f;
        emissionShroomlight = 1.0f;
        emissionSeaLantern = 1.0f;
        emissionFroglight = 1.0f;
        emissionMagmaBlock = 1.0f;
        emissionBeacon = 1.0f;
        emissionEndRod = 1.0f;
        emissionJackOLantern = 1.0f;
        emissionNetherPortal = 1.0f;
        emissionCryingObsidian = 0.8f;
        emissionRespawnAnchor = 1.0f;
        emissionConduit = 1.0f;
        emissionAmethystCluster = 0.5f;
        emissionSculkSensor = 0.5f;
        emissionSculkCatalyst = 0.5f;
        emissionSculkVein = 0.3f;
        emissionSculk = 0.2f;
        emissionSculkShrieker = 0.5f;
        emissionBrewingStand = 0.5f;
        emissionEndPortal = 1.0f;
        emissionCalibratedSculkSensor = 1.0f;
        emissionSeaPickle = 1.0f;
        emissionEndGateway = 1.0f;
        emissionTrialSpawner = 1.0f;
        emissionVault = 1.0f;
        emissionRedstoneTorch = 0.5f;
        emissionRedstoneLamp = 1.0f;
        emissionCandle = 0.5f;
        emissionCaveVines = 0.8f;
        emissionGlowLichen = 0.3f;
        emissionFurnace = 0.7f;
        emissionBlastFurnace = 0.7f;
        emissionSmoker = 0.7f;
        emissionEnderChest = 0.5f;
        emissionCopperBulb = 1.0f;
        emissionEnchantingTable = 0.3f;
        // Firework emission defaults
        fireworkSparkEmissionNits = 20;
        fireworkSparkEmission = 20.0f;
        fireworkFlashEmissionNits = 2000;
        fireworkFlashEmission = 2000.0f;
        System.arraycopy(FIREWORK_COLOR_TEMP_DEFAULTS, 0, fireworkColorTemperatures, 0, FIREWORK_COLOR_COUNT);
        System.arraycopy(FIREWORK_WAVELENGTH_DEFAULTS, 0, fireworkColorWavelength, 0, FIREWORK_COLOR_COUNT);
        System.arraycopy(FIREWORK_PURITY_DEFAULTS, 0, fireworkColorPurity, 0, FIREWORK_COLOR_COUNT);
        outputScale2x = false;
        reflexEnabled = false;
        reflexBoost = false;
        vrrMode = false;
        dlssgQueueParallelism = false;
        chunkBuildingBatchSize = 6;
        chunkBuildingTotalBatches = 6;
        chunkCullDistance = 24;
        sdrTransferFunction = SDR_TRANSFER_FUNCTION_SRGB;
        saturationPercent = SATURATION_DEFAULT_PERCENT;
        upscalerQuality = 2;
        upscalerResOverride = 100;
        upscalerPreset = 4;
        hdrEnabled = false;
        hdrPeakNits = 1000;
        hdrPaperWhiteNits = 203;
        hdrUiBrightnessNits = 100;
        minExposureTenK = 1;
        maxExposure = 20;
        exposureCompensation = 0;
        manualExposureEnabled = false;
        manualExposureEV100Tenths = 150;
        sharpenerMode = 0;
        casSharpnessPercent = 50;
        brightAdaptSpeedTenths = 5;
        darkAdaptSpeedTenths = 20;
        sceneChangeThresholdTenths = 50;
        centerWeightPercent = 0;
        middleGreyPercent = 18;
        LwhiteTenths = 40;
        // HDR tonemapper + PsychoV defaults
        hdrTonemapMode = 0;  // 0 = PsychoVisual
        psychoEnabled = true;
        psychoHighlightsPercent = 100;
        psychoShadowsPercent = 100;
        psychoContrastPercent = 100;
        psychoPurityPercent = 105;
        psychoBleachingPercent = 0;
        psychoClipPointTenths = 1000;
        psychoHueRestorePercent = 0;
        psychoAdaptContrastPercent = 100;
        psychoWhiteCurve = 1;
        psychoConeExponentPercent = 100;
        // Environment + orbit
        setEnvironmentDefaults();

        // Push all values to native C++ renderer
        nativeSetMaxFps(maxFps, false);
        nativeSetVsync(vsync, false);
        nativeSetRayBounces(rayBounces, false);
        nativeSetSimplifiedIndirect(simplifiedIndirect, false);
        try { nativeSetMultiScatterGGX(multiScatterGGX, false); } catch (UnsatisfiedLinkError ignored) {}
        try { nativeSetDiffuseModel(diffuseModel, false); } catch (UnsatisfiedLinkError ignored) {}
        nativeSetSharcEnabled(sharcEnabled, false);
        nativeSetSharcSceneScale(sharcSceneScaleTenths / 10.0f, false);
        nativeSetSharcRoughnessThreshold(sharcRoughnessThresholdPercent / 100.0f, false);
        nativeSetSharcAccumulationFrames(sharcAccumulationFrames, false);
        nativeSetSharcStaleFrames(sharcStaleFrames, false);
        nativeSetSharcDownscale(sharcDownscale, false);
        nativeSetSharcUpdateBlockSize(sharcUpdateBlockSize, false);
        nativeSetSharcUpdateBounces(sharcUpdateBounces, false);
        nativeSetSharcCapacityExponent(sharcCapacityExponent, false);
        nativeSetSharcQueryMode(sharcQueryMode, false);
        for (EmissiveBlock b : EmissiveBlock.values()) {
            if (b.isThermal() && b.getLightTypeId() >= 0) {
                int temp = blockTemperatures.getOrDefault(b.getId(), b.getDefaultTemperatureCelsius());
                nativeSetBlockTemperature(b.getLightTypeId(), temp + 273.15f, false);
                updateBlockEmissionColor(b);
            }
        }
        nativeSetOutputScale2x(outputScale2x, false);
        nativeSetReflexEnabled(reflexEnabled, false);
        nativeSetReflexBoost(reflexBoost, false);
        nativeSetVrrMode(vrrMode, false);
        nativeSetDlssgQueueParallelism(dlssgQueueParallelism, false);
        nativeSetChunkBuildingBatchSize(chunkBuildingBatchSize, false);
        nativeSetChunkBuildingTotalBatches(chunkBuildingTotalBatches, false);
        nativeSetChunkCullDistance(chunkCullDistance, false);
        nativeSetSdrTransferFunction(sdrTransferFunction, false);
        nativeSetSaturation(saturationPercent / 100.0f, false);
        nativeSetDlssQuality(upscalerQuality, false);
        nativeSetDlssResOverride(upscalerResOverride, false);
        nativeSetDlssPreset(upscalerPreset, false);
        nativeSetHdrEnabled(hdrEnabled, false);
        nativeSetHdrPeakNits(hdrPeakNits, false);
        nativeSetHdrPaperWhiteNits(hdrPaperWhiteNits, false);
        nativeSetHdrUiBrightnessNits(hdrUiBrightnessNits, false);
        nativeSetMinExposure(minExposureTenK * 1e-7f, false);  // 1-10000 → 1e-7 to 1e-3
        nativeSetMaxExposure(maxExposure, false);
        nativeSetExposureCompensation(exposureCompensation, false);
        nativeSetManualExposureEnabled(manualExposureEnabled, false);
        nativeSetManualExposure(ev100ToLinearExposure(manualExposureEV100Tenths), false);
        nativeSetSharpenerMode(sharpenerMode, false);
        nativeSetCasSharpness(casSharpnessPercent / 100.0f, false);
        nativeSetBrightAdaptSpeed(brightAdaptSpeedTenths / 10.0f, false);
        nativeSetDarkAdaptSpeed(darkAdaptSpeedTenths / 10.0f, false);
        nativeSetSceneChangeThreshold(sceneChangeThresholdTenths / 10.0f, false);
        nativeSetCenterWeightStrength(centerWeightPercent / 100.0f, false);
        nativeSetHighlightWeight(highlightWeightPercent / 100.0f, false);
        nativeSetMiddleGrey(middleGreyPercent / 100.0f, false);
        nativeSetLwhite(LwhiteTenths / 10.0f, false);
        // HDR tonemapper + PsychoV
        nativeSetHdrTonemapMode(hdrTonemapMode, false);
        nativeSetPsychoEnabled(psychoEnabled, false);
        nativeSetPsychoHighlights(psychoHighlightsPercent / 100.0f, false);
        nativeSetPsychoShadows(psychoShadowsPercent / 100.0f, false);
        nativeSetPsychoContrast(psychoContrastPercent / 100.0f, false);
        nativeSetPsychoPurity(psychoPurityPercent / 100.0f, false);
        nativeSetPsychoBleaching(psychoBleachingPercent / 100.0f, false);
        nativeSetPsychoClipPoint(psychoClipPointTenths / 10.0f, false);
        nativeSetPsychoHueRestore(psychoHueRestorePercent / 100.0f, false);
        nativeSetPsychoAdaptContrast(psychoAdaptContrastPercent / 100.0f, false);
        nativeSetPsychoWhiteCurve(psychoWhiteCurve, false);
        nativeSetPsychoConeExponent(psychoConeExponentPercent / 100.0f, false);
        nativeSetPsychoPeakSDR(psychoPeakSDRTenths / 10.0f, false);
        initTonemapDefaults();
        pushActiveTonemapParams();
        Pipeline.resetRayTracingShaderPackAttributesToDefaults();
        Pipeline.build();
        overwriteConfig();
    }

    // --- Sun/Moon orbit setters ---

    public static void setSunPathMode(int mode, boolean write) {
        sunPathMode = Math.max(0, Math.min(1, mode));
        if (write) overwriteConfig();
    }

    public static void setSunInclinationDeg(int deg, boolean write) {
        sunInclinationDeg = Math.max(0, Math.min(90, deg));
        if (write) overwriteConfig();
    }

    public static void setSunAzimuthOffsetDeg(int deg, boolean write) {
        sunAzimuthOffsetDeg = Math.max(-180, Math.min(180, deg));
        if (write) overwriteConfig();
    }

    public static void setMoonFollowSun(boolean follow, boolean write) {
        moonFollowSun = follow;
        if (write) overwriteConfig();
    }

    public static void setMoonInclinationDeg(int deg, boolean write) {
        moonInclinationDeg = Math.max(0, Math.min(90, deg));
        if (write) overwriteConfig();
    }

    public static void setMoonAzimuthOffsetDeg(int deg, boolean write) {
        moonAzimuthOffsetDeg = Math.max(-180, Math.min(180, deg));
        if (write) overwriteConfig();
    }

    // === Native methods ===

    public native static void nativeSetMaxFps(int maxFps, boolean write);

    public static void setMaxFps(int maxFps, boolean write) {
        Options.maxFps = maxFps;
        nativeSetMaxFps(maxFps, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetInactivityFpsLimit(int inactivityFpsLimit, boolean write);

    public static void setInactivityFpsLimit(int inactivityFpsLimit, boolean write) {
        Options.inactivityFpsLimit = inactivityFpsLimit;
        nativeSetInactivityFpsLimit(inactivityFpsLimit, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetVsync(boolean vsync, boolean write);

    public static void setVsync(boolean vsync, boolean write) {
        Options.vsync = vsync;
        nativeSetVsync(vsync, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkBuildingBatchSize(int chunkBuildingBatchSize,
        boolean write);

    public static void setChunkBuildingBatchSize(int chunkBuildingBatchSize, boolean write) {
        Options.chunkBuildingBatchSize = chunkBuildingBatchSize;
        nativeSetChunkBuildingBatchSize(chunkBuildingBatchSize, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkBuildingTotalBatches(int chunkBuildingTotalBatches,
        boolean write);

    public static void setChunkBuildingTotalBatches(int chunkBuildingTotalBatches, boolean write) {
        Options.chunkBuildingTotalBatches = chunkBuildingTotalBatches;
        nativeSetChunkBuildingTotalBatches(chunkBuildingTotalBatches, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetChunkCullDistance(int distance, boolean write);

    public static void setChunkCullDistance(int distance, boolean write) {
        Options.chunkCullDistance = clamp(distance, 0, 512);
        nativeSetChunkCullDistance(Options.chunkCullDistance, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetTonemappingMode(int mode, boolean write);

    public native static void nativeSetTonemapParam(int index, float value, boolean write);

    public native static void nativeSetSdrTransferFunction(int mode, boolean write);

    public static void setSdrTransferFunction(int mode, boolean write) {
        int clamped = Math.max(0, Math.min(1, mode));
        Options.sdrTransferFunction = clamped;
        nativeSetSdrTransferFunction(clamped, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setTonemappingMode(int mode, boolean write) {
        int clampedMode = clampTonemappingMode(mode);
        Options.tonemappingMode = clampedMode;
        Options.sdrTonemappingMode = clampedMode;
        nativeSetTonemappingMode(clampedMode, write);
        pushActiveTonemapParams();
        if (write) {
            overwriteConfig();
        }
    }

    public static void setTonemapParam(int mode, int paramIndex, float value, boolean write) {
        if (mode < 0 || mode > 8 || paramIndex < 0 || paramIndex > 7) return;
        tonemapParams[mode][paramIndex] = value;
        if (mode == tonemappingMode) {
            nativeSetTonemapParam(paramIndex, value, write);
        }
        if (write) overwriteConfig();
    }

    public static void pushActiveTonemapParams() {
        for (int i = 0; i < 8; i++) {
            nativeSetTonemapParam(i, tonemapParams[tonemappingMode][i], false);
        }
    }

    public native static void nativeSetMinExposure(float minExposure, boolean write);
    public native static void nativeSetMaxExposure(int maxExposure, boolean write);
    public native static void nativeSetExposureCompensation(float compensation, boolean write);
    public native static void nativeSetManualExposureEnabled(boolean enabled, boolean write);
    public native static void nativeSetManualExposure(float exposure, boolean write);
    public native static void nativeSetBrightAdaptSpeed(float speed, boolean write);
    public native static void nativeSetDarkAdaptSpeed(float speed, boolean write);
    public native static void nativeSetSceneChangeThreshold(float threshold, boolean write);
    public native static void nativeSetCenterWeightStrength(float strength, boolean write);
    public native static void nativeSetHighlightWeight(float weight, boolean write);
    public native static void nativeSetMiddleGrey(float middleGrey, boolean write);
    public native static void nativeSetLwhite(float lwhite, boolean write);
    public native static void nativeSetSaturation(float saturation, boolean write);
    public native static void nativeSetSaturationAdaptive(boolean enabled, boolean write);
    public native static void nativeSetSharpenerMode(int mode, boolean write);
    public native static void nativeSetCasSharpness(float sharpness, boolean write);
    public native static void nativeResetExposureAdaptation();

    public static void setMinExposure(int value, boolean write) {
        Options.minExposureTenK = clamp(value, 1, 10000);
        nativeSetMinExposure(Options.minExposureTenK * 1e-7f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setMaxExposure(int value, boolean write) {
        Options.maxExposure = clamp(value, 1, 100);
        nativeSetMaxExposure(Options.maxExposure, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setManualExposureEnabled(boolean enabled, boolean write) {
        Options.manualExposureEnabled = enabled;
        nativeSetManualExposureEnabled(enabled, write);
        nativeResetExposureAdaptation();
        if (write) {
            overwriteConfig();
        }
    }

    public static void setManualExposureEV100Tenths(int ev100Tenths, boolean write) {
        Options.manualExposureEV100Tenths = clamp(ev100Tenths, -40, 200);
        nativeSetManualExposure(ev100ToLinearExposure(Options.manualExposureEV100Tenths), write);
        if (write) {
            overwriteConfig();
        }
    }

    public static float ev100ToLinearExposure(int ev100Tenths) {
        float ev = ev100Tenths / 10.0f;
        return 1.0f / (1.2f * (float) Math.pow(2.0, ev));
    }

    public static void setBrightAdaptSpeedTenths(int tenths, boolean write) {
        Options.brightAdaptSpeedTenths = clamp(tenths, 1, 50);
        nativeSetBrightAdaptSpeed(Options.brightAdaptSpeedTenths / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setDarkAdaptSpeedTenths(int tenths, boolean write) {
        Options.darkAdaptSpeedTenths = clamp(tenths, 5, 100);
        nativeSetDarkAdaptSpeed(Options.darkAdaptSpeedTenths / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setSceneChangeThresholdTenths(int tenths, boolean write) {
        Options.sceneChangeThresholdTenths = clamp(tenths, 20, 100);
        nativeSetSceneChangeThreshold(Options.sceneChangeThresholdTenths / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setCenterWeightPercent(int percent, boolean write) {
        Options.centerWeightPercent = clamp(percent, 0, 100);
        nativeSetCenterWeightStrength(Options.centerWeightPercent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setHighlightWeight(int percent, boolean write) {
        Options.highlightWeightPercent = clamp(percent, 0, 100);
        nativeSetHighlightWeight(Options.highlightWeightPercent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setExposureCompensation(int tenths, boolean write) {
        Options.exposureCompensation = clamp(tenths, -30, 30);
        nativeSetExposureCompensation(Options.exposureCompensation / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setMiddleGrey(int percent, boolean write) {
        Options.middleGreyPercent = clamp(percent, 1, 50);
        nativeSetMiddleGrey(Options.middleGreyPercent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setLwhiteTenths(int tenths, boolean write) {
        Options.LwhiteTenths = clamp(tenths, 10, 200);
        nativeSetLwhite(Options.LwhiteTenths / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setSaturation(int percent, boolean write) {
        Options.saturationPercent = clamp(percent, 0, 400);
        nativeSetSaturation(Options.saturationPercent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setSaturationAdaptive(boolean enabled, boolean write) {
        Options.saturationAdaptive = enabled;
        try {
            nativeSetSaturationAdaptive(enabled, write);
        } catch (UnsatisfiedLinkError ignored) {
        }
        if (write) {
            overwriteConfig();
        }
    }

    public static void setSharpenerMode(int mode, boolean write) {
        Options.sharpenerMode = clamp(mode, 0, 2);
        nativeSetSharpenerMode(Options.sharpenerMode, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setCasSharpnessPercent(int percent, boolean write) {
        Options.casSharpnessPercent = clamp(percent, 0, 100);
        nativeSetCasSharpness(Options.casSharpnessPercent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrEnabled(boolean enabled, boolean write);
    public native static void nativeSetHdrScrgbMode(boolean scrgb, boolean write);
    public native static void nativeSetHdrTonemapMode(int mode, boolean write);
    public native static void nativeSetHdrPeakNits(int nits, boolean write);
    public native static void nativeSetHdrPaperWhiteNits(int nits, boolean write);
    public native static void nativeSetHdrUiBrightnessNits(int nits, boolean write);
    public native static boolean nativeIsHdrSupported();
    public native static boolean nativeIsHdrActive();

    public static boolean isHdrSupported() {
        try {
            return nativeIsHdrSupported();
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    public static boolean isHdrActive() {
        try {
            return nativeIsHdrActive();
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    public static void setHdrEnabled(boolean enabled, boolean write) {
        Options.hdrEnabled = enabled;
        nativeSetHdrEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setHdrScrgbMode(boolean enabled, boolean write) {
        Options.hdrScrgbMode = enabled;
        nativeSetHdrScrgbMode(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setHdrTonemapMode(int mode, boolean write) {
        Options.hdrTonemapMode = clamp(mode, 0, 1);
        nativeSetHdrTonemapMode(Options.hdrTonemapMode, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setHdrPeakNits(int nits, boolean write) {
        Options.hdrPeakNits = clamp(nits, 100, 10000);
        nativeSetHdrPeakNits(Options.hdrPeakNits, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setHdrPaperWhiteNits(int nits, boolean write) {
        Options.hdrPaperWhiteNits = clamp(nits, 80, 500);
        nativeSetHdrPaperWhiteNits(Options.hdrPaperWhiteNits, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setHdrUiBrightnessNits(int nits, boolean write) {
        Options.hdrUiBrightnessNits = clamp(nits, 50, 300);
        nativeSetHdrUiBrightnessNits(Options.hdrUiBrightnessNits, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetPsychoEnabled(boolean enabled, boolean write);
    public native static void nativeSetPsychoHighlights(float value, boolean write);
    public native static void nativeSetPsychoShadows(float value, boolean write);
    public native static void nativeSetPsychoContrast(float value, boolean write);
    public native static void nativeSetPsychoPurity(float value, boolean write);
    public native static void nativeSetPsychoBleaching(float value, boolean write);
    public native static void nativeSetPsychoClipPoint(float value, boolean write);
    public native static void nativeSetPsychoHueRestore(float value, boolean write);
    public native static void nativeSetPsychoAdaptContrast(float value, boolean write);
    public native static void nativeSetPsychoWhiteCurve(int value, boolean write);
    public native static void nativeSetPsychoConeExponent(float value, boolean write);
    public native static void nativeSetPsychoPeakSDR(float value, boolean write);

    public static void setPsychoHighlights(int percent, boolean write) {
        Options.psychoHighlightsPercent = clamp(percent, 0, 300);
        nativeSetPsychoHighlights(Options.psychoHighlightsPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoShadows(int percent, boolean write) {
        Options.psychoShadowsPercent = clamp(percent, 0, 300);
        nativeSetPsychoShadows(Options.psychoShadowsPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoContrast(int percent, boolean write) {
        Options.psychoContrastPercent = clamp(percent, 0, 300);
        nativeSetPsychoContrast(Options.psychoContrastPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoPurity(int percent, boolean write) {
        Options.psychoPurityPercent = clamp(percent, 0, 300);
        nativeSetPsychoPurity(Options.psychoPurityPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoBleaching(int percent, boolean write) {
        Options.psychoBleachingPercent = clamp(percent, 0, 100);
        nativeSetPsychoBleaching(Options.psychoBleachingPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoClipPoint(int tenths, boolean write) {
        Options.psychoClipPointTenths = clamp(tenths, 10, 5000);
        nativeSetPsychoClipPoint(Options.psychoClipPointTenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoHueRestore(int percent, boolean write) {
        Options.psychoHueRestorePercent = clamp(percent, 0, 100);
        nativeSetPsychoHueRestore(Options.psychoHueRestorePercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoAdaptContrast(int percent, boolean write) {
        Options.psychoAdaptContrastPercent = clamp(percent, 0, 300);
        nativeSetPsychoAdaptContrast(Options.psychoAdaptContrastPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoWhiteCurve(int value, boolean write) {
        Options.psychoWhiteCurve = clamp(value, 0, 1);
        nativeSetPsychoWhiteCurve(Options.psychoWhiteCurve, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoConeExponent(int percent, boolean write) {
        Options.psychoConeExponentPercent = clamp(percent, 10, 300);
        nativeSetPsychoConeExponent(Options.psychoConeExponentPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setPsychoPeakSDR(int tenths, boolean write) {
        Options.psychoPeakSDRTenths = clamp(tenths, 5, 80);
        nativeSetPsychoPeakSDR(Options.psychoPeakSDRTenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    // Upscaler modes: 0=DLSS-RR, 2=Off. Accept old 1=FSR3 as Off.
    public static void setUpscalerMode(int mode, boolean write) {
        int clamped = Math.max(0, Math.min(2, mode));
        if (clamped == 1) {
            clamped = 2;
        }

        if (clamped == 0) {
            try {
                if (!Pipeline.isNativeModuleAvailable("render_pipeline.module.dlss.name")) {
                    RadianceClient.LOGGER.warn(
                        "DLSS requested but DLSS module is not available; disabling upscaling.");
                    clamped = 2;
                }
            } catch (UnsatisfiedLinkError ignored) {
                // Native not loaded yet; keep requested value.
            }
        }

        Options.upscalerMode = clamped;
        Options.dlssDEnabled = (clamped == 0);

        if (isDevLoggingEnabled()) {
            RadianceClient.LOGGER.info("Upscaler mode set to {} (dlssDEnabled={})", Options.upscalerMode,
                Options.dlssDEnabled);
        }

        if (dlssRebuildTask != null) dlssRebuildTask.cancel(false);
        if (dlssResOverrideTask != null) dlssResOverrideTask.cancel(false);
        if (upscalerPresetTask != null) upscalerPresetTask.cancel(false);

        if (write) {
            try {
                Pipeline.assembleDefault();
                Pipeline.build();
            } catch (Exception e) {
                RadianceClient.LOGGER.error("Failed to rebuild pipeline after upscaler toggle.", e);
            }
            overwriteConfig();
        }
    }

    public native static void nativeSetDlssQuality(int quality, boolean write);

    public static void setUpscalerQuality(int quality, boolean write) {
        Options.upscalerQuality = quality;

        if (isDevLoggingEnabled()) {
            RadianceClient.LOGGER.info("Upscaler quality set to {} (mode={})", quality, Options.upscalerMode);
        }

        // DLSS: handled via native setters. When DLSS is disabled, keep the value stored.
        if (Options.dlssDEnabled) {
            if (dlssRebuildTask != null) dlssRebuildTask.cancel(false);
            dlssRebuildTask = scheduler.schedule(
                () -> runOnClientThread(() -> nativeSetDlssQuality(quality, write)),
                500,
                TimeUnit.MILLISECONDS);
        }
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetDlssResOverride(int resOverride, boolean write);
    public native static void nativeSetDlssPreset(int preset, boolean write);
    public native static void nativeSetOutputScale2x(boolean enabled, boolean write);
    public native static void nativeSetReflexEnabled(boolean enabled, boolean write);
    public native static void nativeSetReflexBoost(boolean enabled, boolean write);
    public native static void nativeSetVrrMode(boolean enabled, boolean write);
    public native static void nativeApplyReflexSettings();
    public native static boolean nativeIsReflexSupported();
    public native static int nativeGetDisplayRefreshRate();
    public native static void nativeSetFrameGenMode(int mode, boolean write);
    public native static void nativeSetFrameGenMultiplier(int multiplier, boolean write);
    public native static void nativeSetDlssgQueueParallelism(boolean enabled, boolean write);
    public native static boolean nativeIsFrameGenSupported();
    public native static int nativeGetFrameGenMaxMultiplier();

    private static ScheduledFuture<?> dlssResOverrideTask;

    public static void setUpscalerResOverride(int resOverride, boolean write) {
        final int clampedResOverride = clampPercent(resOverride);
        Options.upscalerResOverride = clampedResOverride;
        if (Options.dlssDEnabled) {
            if (dlssResOverrideTask != null) dlssResOverrideTask.cancel(false);
            dlssResOverrideTask = scheduler.schedule(
                () -> runOnClientThread(() -> nativeSetDlssResOverride(clampedResOverride, write)),
                500,
                TimeUnit.MILLISECONDS);
        }
        if (write) {
            overwriteConfig();
        }
    }

    public static void setDlssDEnabled(boolean enabled, boolean write) {
        setUpscalerMode(enabled ? 0 : 2, write);
    }

    public static void setUpscalerPreset(int preset, boolean write) {
        Options.upscalerPreset = clamp(preset, 0, 6);
        nativeSetDlssPreset(Options.upscalerPreset, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setOutputScale2x(boolean enabled, boolean write) {
        Options.outputScale2x = enabled;
        nativeSetOutputScale2x(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static boolean isReflexSupported() {
        try {
            return nativeIsReflexSupported();
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    public static void setReflexEnabled(boolean enabled, boolean write) {
        Options.reflexEnabled = enabled;
        Options.reflexExplicitlyConfigured = true;
        nativeSetReflexEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setReflexBoost(boolean enabled, boolean write) {
        Options.reflexBoost = enabled;
        nativeSetReflexBoost(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setVrrMode(boolean enabled, boolean write) {
        Options.vrrMode = enabled;
        Options.reflexExplicitlyConfigured = true;
        nativeSetVrrMode(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static boolean isFrameGenSupported() {
        try {
            return nativeIsFrameGenSupported();
        } catch (UnsatisfiedLinkError ignored) {
            return false;
        }
    }

    public static int getFrameGenMaxMultiplier() {
        try {
            return Math.max(1, nativeGetFrameGenMaxMultiplier());
        } catch (UnsatisfiedLinkError ignored) {
            return 1;
        }
    }

    public static void setFrameGenMode(int mode, boolean write) {
        Options.frameGenMode = clamp(mode, 0, 2);
        nativeSetFrameGenMode(Options.frameGenMode, write);
        if (Options.frameGenMode != 0 && Options.isReflexSupported()) {
            Options.reflexEnabled = true;
        }
        if (write) {
            overwriteConfig();
        }
    }

    public static void setFrameGenMultiplier(int multiplier, boolean write) {
        Options.frameGenMultiplier = clamp(multiplier, 1, Math.max(1, getFrameGenMaxMultiplier()));
        nativeSetFrameGenMultiplier(Options.frameGenMultiplier, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetRayBounces(int bounces, boolean write);

    public static void setRayBounces(int bounces, boolean write) {
        com.radiance.client.debug.CrashContext.recordChange("rayBounces=" + bounces);
        Options.rayBounces = bounces;
        nativeSetRayBounces(bounces, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Simplified Indirect ---
    public native static void nativeSetSimplifiedIndirect(boolean enabled, boolean write);

    public static void setSimplifiedIndirect(boolean enabled, boolean write) {
        Options.simplifiedIndirect = enabled;
        nativeSetSimplifiedIndirect(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Multi-Scatter GGX ---
    public native static void nativeSetMultiScatterGGX(boolean enabled, boolean write);

    public static void setMultiScatterGGX(boolean enabled, boolean write) {
        Options.multiScatterGGX = enabled;
        try { nativeSetMultiScatterGGX(enabled, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) {
            overwriteConfig();
        }
    }

    // --- EON Diffuse ---
    public native static void nativeSetEonDiffuse(boolean enabled, boolean write);
    public native static void nativeSetDiffuseModel(int model, boolean write);

    public static void setEonDiffuse(boolean enabled, boolean write) {
        Options.eonDiffuse = enabled;
        Options.diffuseModel = enabled ? DIFFUSE_MODEL_EON : DIFFUSE_MODEL_LEGACY;
        try { nativeSetDiffuseModel(Options.diffuseModel, write); } catch (UnsatisfiedLinkError ignored) {}
        try { nativeSetEonDiffuse(enabled, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) {
            overwriteConfig();
        }
    }

    public static void setDiffuseModel(int model, boolean write) {
        Options.diffuseModel = clampDiffuseModel(model);
        Options.eonDiffuse = Options.diffuseModel != DIFFUSE_MODEL_LEGACY;
        try { nativeSetDiffuseModel(Options.diffuseModel, write); } catch (UnsatisfiedLinkError ignored) {}
        if (write) {
            overwriteConfig();
        }
    }

    private static int clampDiffuseModel(int model) {
        return clamp(model, DIFFUSE_MODEL_EON, DIFFUSE_MODEL_LEGACY);
    }

    public static int diffuseModelFromProperties(Properties props, int fallbackDiffuseModel, boolean fallbackEonDiffuse) {
        if (props.containsKey("diffuseModel")) {
            return clampDiffuseModel(Integer.parseInt(props.getProperty("diffuseModel", String.valueOf(fallbackDiffuseModel))));
        }
        boolean enabled = Boolean.parseBoolean(props.getProperty("eonDiffuse", String.valueOf(fallbackEonDiffuse)));
        return enabled ? DIFFUSE_MODEL_EON : DIFFUSE_MODEL_LEGACY;
    }

    public native static void nativeSetSEREnabled(boolean enabled, boolean write);
    public native static void nativeSetSERHintsEnabled(boolean enabled, boolean write);
    public native static void nativeSetSharcEnabled(boolean enabled, boolean write);
    public native static void nativeSetSharcSceneScale(float scale, boolean write);
    public native static void nativeSetSharcRoughnessThreshold(float threshold, boolean write);
    public native static void nativeSetSharcAccumulationFrames(int frames, boolean write);
    public native static void nativeSetSharcStaleFrames(int frames, boolean write);
    public native static void nativeSetSharcDownscale(int downscale, boolean write);
    public native static void nativeSetSharcUpdateBlockSize(int blockSize, boolean write);
    public native static void nativeSetSharcUpdateBounces(int bounces, boolean write);
    public native static void nativeSetSharcCapacityExponent(int exponent, boolean write);
    public native static void nativeSetSharcQueryMode(int mode, boolean write);

    public static void setSEREnabled(boolean enabled, boolean write) {
        Options.serEnabled = enabled;
        nativeSetSEREnabled(enabled, write);
        if (write) overwriteConfig();
    }

    public static void setSERHintsEnabled(boolean enabled, boolean write) {
        Options.serHintsEnabled = enabled;
        nativeSetSERHintsEnabled(enabled, write);
        if (write) overwriteConfig();
    }

    public static void setSharcEnabled(boolean enabled, boolean write) {
        Options.sharcEnabled = enabled;
        nativeSetSharcEnabled(enabled, write);
        if (write) overwriteConfig();
    }

    public static void setSharcSceneScaleTenths(int tenths, boolean write) {
        Options.sharcSceneScaleTenths = clamp(tenths, 10, 200);
        nativeSetSharcSceneScale(Options.sharcSceneScaleTenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setSharcRoughnessThresholdPercent(int percent, boolean write) {
        Options.sharcRoughnessThresholdPercent = clamp(percent, 0, 100);
        nativeSetSharcRoughnessThreshold(Options.sharcRoughnessThresholdPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setSharcAccumulationFrames(int frames, boolean write) {
        Options.sharcAccumulationFrames = clamp(frames, 4, 256);
        nativeSetSharcAccumulationFrames(Options.sharcAccumulationFrames, write);
        if (write) overwriteConfig();
    }

    public static void setSharcStaleFrames(int frames, boolean write) {
        Options.sharcStaleFrames = clamp(frames, 4, 128);
        nativeSetSharcStaleFrames(Options.sharcStaleFrames, write);
        if (write) overwriteConfig();
    }

    public static void setSharcDownscale(int downscale, boolean write) {
        Options.sharcDownscale = clamp(downscale, 1, 8);
        nativeSetSharcDownscale(Options.sharcDownscale, write);
        if (write) overwriteConfig();
    }

    public static void setSharcUpdateBlockSize(int blockSize, boolean write) {
        Options.sharcUpdateBlockSize = clamp(blockSize, 1, 8);
        nativeSetSharcUpdateBlockSize(Options.sharcUpdateBlockSize, write);
        if (write) overwriteConfig();
    }

    public static void setSharcUpdateBounces(int bounces, boolean write) {
        Options.sharcUpdateBounces = clamp(bounces, 2, 8);
        nativeSetSharcUpdateBounces(Options.sharcUpdateBounces, write);
        if (write) overwriteConfig();
    }

    public static void setSharcCapacityExponent(int exponent, boolean write) {
        Options.sharcCapacityExponent = clamp(exponent, 18, 24);
        nativeSetSharcCapacityExponent(Options.sharcCapacityExponent, write);
        if (write) overwriteConfig();
    }

    public static void setSharcQueryMode(int mode, boolean write) {
        Options.sharcQueryMode = clamp(mode, 0, 2);
        nativeSetSharcQueryMode(Options.sharcQueryMode, write);
        if (write) overwriteConfig();
    }

    public static void applySharcPreset(int preset, boolean write) {
        Options.sharcQualityPreset = clamp(preset, 0, 5);
        if (Options.sharcQualityPreset < SHARC_PRESETS.length) {
            int[] p = SHARC_PRESETS[Options.sharcQualityPreset];
            setSharcSceneScaleTenths(p[0], false);
            setSharcRoughnessThresholdPercent(p[1], false);
            setSharcAccumulationFrames(p[2], false);
            setSharcStaleFrames(p[3], false);
            setSharcDownscale(p[4], false);
            setSharcUpdateBlockSize(p[5], false);
            setSharcUpdateBounces(p[6], false);
            setSharcCapacityExponent(p[7], false);
        }
        if (write) {
            overwriteConfig();
        }
    }

    public static int detectSharcPreset() {
        for (int i = 0; i < SHARC_PRESETS.length; i++) {
            int[] p = SHARC_PRESETS[i];
            if (Options.sharcSceneScaleTenths == p[0]
                    && Options.sharcRoughnessThresholdPercent == p[1]
                    && Options.sharcAccumulationFrames == p[2]
                    && Options.sharcStaleFrames == p[3]
                    && Options.sharcDownscale == p[4]
                    && Options.sharcUpdateBlockSize == p[5]
                    && Options.sharcUpdateBounces == p[6]
                    && Options.sharcCapacityExponent == p[7]) {
                return i;
            }
        }
        return 5;
    }

    // --- Cube-block height-field geometry displacement ---
    public native static void nativeSetGeometryDisplacementEnabled(boolean enabled, boolean write);
    public native static void nativeSetGeometryDisplacementDepthScale(float scale, boolean write);
    public native static void nativeSetGeometryDisplacementPrimarySteps(int steps, boolean write);
    public native static void nativeSetGeometryDisplacementRefinementSteps(int refinement, boolean write);
    public native static void nativeSetGeometryDisplacementFadeDistance(float distance, boolean write);
    public native static void nativeSetPOMEnabled(boolean enabled, boolean write);
    public native static void nativeSetPOMHeightScale(float scale, boolean write);
    public native static void nativeSetPOMSteps(int steps, boolean write);
    public native static void nativeSetPOMRefinement(int refinement, boolean write);
    public native static void nativeSetPOMFadeDistance(float distance, boolean write);
    public native static void nativeSetDisplacementQuality(int quality, boolean write);
    private static void applyDisplacementQualityPreset(int quality) {
        Options.displacementQuality = clamp(quality, DISPLACEMENT_QUALITY_LOW, DISPLACEMENT_QUALITY_ULTRA);
        switch (Options.displacementQuality) {
            case DISPLACEMENT_QUALITY_LOW -> {
                Options.displacementSteps = 16;
                Options.displacementRefinement = 2;
            }
            case DISPLACEMENT_QUALITY_HIGH -> {
                Options.displacementSteps = 64;
                Options.displacementRefinement = 5;
            }
            case DISPLACEMENT_QUALITY_ULTRA -> {
                Options.displacementSteps = 96;
                Options.displacementRefinement = 6;
            }
            default -> {
                Options.displacementQuality = DISPLACEMENT_QUALITY_BALANCED;
                Options.displacementSteps = 32;
                Options.displacementRefinement = 4;
            }
        }
        Options.pomSteps = Options.displacementSteps;
        Options.pomRefinement = Options.displacementRefinement;
    }

    public static String displacementQualityName(int quality) {
        return switch (clamp(quality, DISPLACEMENT_QUALITY_LOW, DISPLACEMENT_QUALITY_ULTRA)) {
            case DISPLACEMENT_QUALITY_LOW -> "Low";
            case DISPLACEMENT_QUALITY_HIGH -> "High";
            case DISPLACEMENT_QUALITY_ULTRA -> "Ultra";
            default -> "Balanced";
        };
    }

    private static void syncDisplacementNative(boolean write) {
        Options.pomEnabled = Options.displacementEnabled;
        Options.pomHeightScalePercent = Options.displacementDepthCapPercent;
        Options.pomFadeDistance = Options.displacementFadeDistance;
        nativeSetGeometryDisplacementEnabled(Options.displacementEnabled, write);
        nativeSetGeometryDisplacementDepthScale(Options.displacementDepthCapPercent / 100.0f, false);
        nativeSetGeometryDisplacementPrimarySteps(Options.displacementSteps, false);
        nativeSetGeometryDisplacementRefinementSteps(Options.displacementRefinement, false);
        nativeSetGeometryDisplacementFadeDistance((float) Options.displacementFadeDistance, false);
        nativeSetDisplacementQuality(Options.displacementQuality, false);
    }

    public static void setDisplacementEnabled(boolean enabled, boolean write) {
        com.radiance.client.debug.CrashContext.recordChange("displacementEnabled=" + enabled);
        Options.displacementEnabled = enabled;
        syncDisplacementNative(write);
        if (write) { overwriteConfig(); }
    }

    public static void setDisplacementDepthCapPercent(int percent, boolean write) {
        Options.displacementDepthCapPercent = clamp(percent, 1, 50);
        com.radiance.client.debug.CrashContext.recordChange("displacementDepthCapPercent=" + Options.displacementDepthCapPercent);
        syncDisplacementNative(false);
        if (write) { overwriteConfig(); }
    }

    public static void setDisplacementQuality(int quality, boolean write) {
        applyDisplacementQualityPreset(quality);
        com.radiance.client.debug.CrashContext.recordChange("displacementQuality=" + Options.displacementQuality);
        syncDisplacementNative(false);
        if (write) { overwriteConfig(); }
    }

    public static void setDisplacementFadeDistance(int distance, boolean write) {
        Options.displacementFadeDistance = clamp(distance, 8, 256);
        com.radiance.client.debug.CrashContext.recordChange("displacementFadeDistance=" + Options.displacementFadeDistance);
        syncDisplacementNative(false);
        if (write) { overwriteConfig(); }
    }

    public static void setPOMEnabled(boolean enabled, boolean write) {
        setDisplacementEnabled(enabled, write);
    }

    public static void setPOMHeightScalePercent(int percent, boolean write) {
        setDisplacementDepthCapPercent(percent, write);
    }

    public static void setPOMSteps(int steps, boolean write) {
        Options.displacementSteps = clamp(steps, 8, 512);
        Options.pomSteps = Options.displacementSteps;
        nativeSetGeometryDisplacementPrimarySteps(Options.displacementSteps, write);
        if (write) { overwriteConfig(); }
    }

    public static void setPOMRefinement(int refinement, boolean write) {
        Options.displacementRefinement = clamp(refinement, 0, 8);
        Options.pomRefinement = Options.displacementRefinement;
        nativeSetGeometryDisplacementRefinementSteps(Options.displacementRefinement, write);
        if (write) { overwriteConfig(); }
    }

    public static void setPOMFadeDistance(int distance, boolean write) {
        setDisplacementFadeDistance(distance, write);
    }

    public static void setLoggingEnabled(boolean enabled, boolean write) {
        Options.loggingEnabled = enabled;
        nativeSetLoggingEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setGpuDebugLabels(boolean enabled, boolean write) {
        Options.gpuDebugLabels = enabled;
        nativeSetGpuDebugLabels(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void applyDeferredSettings() {
        if (loggingEnabled) {
            nativeSetLoggingEnabled(true, false);
            com.radiance.client.debug.RadianceLogger.setEnabled(true);
        }
        if (gpuDebugLabels) {
            nativeSetGpuDebugLabels(true, false);
        }
    }

    public static void restoreWindow() {
        try {
            var window = MinecraftClient.getInstance().getWindow();
            if (window.isFullscreen()) {
                return;
            }
            long handle = window.getHandle();
            int[] mx = new int[1], my = new int[1], mw = new int[1], mh = new int[1];
            long monitor = org.lwjgl.glfw.GLFW.glfwGetPrimaryMonitor();
            if (monitor == 0) {
                return;
            }
            org.lwjgl.glfw.GLFW.glfwGetMonitorWorkarea(monitor, mx, my, mw, mh);

            int w = windowWidth;
            int h = windowHeight;
            int x = windowPosX;
            int y = windowPosY;
            if (w > 0 && h > 0) {
                w = Math.min(w, mw[0]);
                h = Math.min(h, mh[0]);
            }
            if (x != -1 && y != -1 && w > 0 && h > 0) {
                x = Math.max(mx[0], Math.min(x, mx[0] + mw[0] - w));
                y = Math.max(my[0], Math.min(y, my[0] + mh[0] - h));
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, w, h);
                org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, x, y);
                pendingWindowX = x;
                pendingWindowY = y;
                windowRestoreSucceeded = true;
            }
        } catch (Exception e) {
            RadianceClient.LOGGER.warn("Failed to restore saved window bounds.", e);
        }
    }

    public static void autoDetectReflexVrr() {
        if (reflexExplicitlyConfigured) {
            return;
        }
        if (isReflexSupported()) {
            setReflexEnabled(true, true);
            setVrrMode(true, true);
        }
    }

    public native static void nativeRebuildChunks();
    public native static void nativeSetBlockTemperature(int lightTypeId, float kelvin, boolean write);
    public native static void nativeSetOfflineGroundTruth(boolean enabled, boolean write);
    public native static void nativeSetBeerLawShadows(boolean enabled, boolean write);
    public native static void nativeSetNoEmissionClamp(boolean enabled, boolean write);
    public native static void nativeSetPhysicalSunDisk(boolean enabled, boolean write);
    public native static void nativeSetNoHandAmbient(boolean enabled, boolean write);

    private static void updateBlockEmissionColor(EmissiveBlock block) {
        if (block == null || !block.isThermal()) {
            return;
        }
        int tempC = getBlockTemperature(block);
        float kelvin = tempC + 273.15f;
        block.setSurfaceNits(EmissiveBlock.blackbodyLuminance(kelvin) * block.getEmissivity());
    }

    public static int getBlockTemperature(EmissiveBlock block) {
        return blockTemperatures.getOrDefault(block.getId(), block.getDefaultTemperatureCelsius());
    }

    public static void setBlockTemperature(EmissiveBlock block, int celsius, boolean write) {
        if (block == null || !block.isThermal()) {
            return;
        }
        int clamped = clamp(celsius, 500, 4000);
        blockTemperatures.put(block.getId(), clamped);
        updateBlockEmissionColor(block);
        if (block.getLightTypeId() >= 0) {
            nativeSetBlockTemperature(block.getLightTypeId(), clamped + 273.15f, write);
        }
        if (write) {
            overwriteConfig();
        }
    }

    public static int getBlockWavelength(EmissiveBlock block) {
        return blockWavelengths.getOrDefault(block.getId(), block.getDefaultWavelengthNm());
    }

    public static void setBlockWavelength(EmissiveBlock block, int wavelengthNm, boolean write) {
        if (block == null) {
            return;
        }
        blockWavelengths.put(block.getId(), clamp(wavelengthNm, 0, 780));
        if (write) {
            overwriteConfig();
        }
    }

    public static int getBlockPurity(EmissiveBlock block) {
        return blockPurities.getOrDefault(block.getId(), block.getDefaultPurityPercent());
    }

    public static void setBlockPurity(EmissiveBlock block, int purityPercent, boolean write) {
        if (block == null) {
            return;
        }
        blockPurities.put(block.getId(), clamp(purityPercent, 0, 100));
        if (write) {
            overwriteConfig();
        }
    }

    public static int getBlockGamutBoost(EmissiveBlock block) {
        int defaultBoost = block != null && "lava".equals(block.getId()) ? 200 : 100;
        return block == null ? defaultBoost : blockGamutBoosts.getOrDefault(block.getId(), defaultBoost);
    }

    public static void setBlockGamutBoost(EmissiveBlock block, int boostPercent, boolean write) {
        if (block == null) {
            return;
        }
        blockGamutBoosts.put(block.getId(), clamp(boostPercent, 0, 200));
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetEntityNormalsEnabled(boolean enabled, boolean write);
    public native static void nativeSetOfflineState(int state, boolean write);
    public native static void nativeSetOfflineBounces(int bounces, boolean write);
    public native static void nativeSetOfflineDisableRR(boolean disable, boolean write);
    public native static void nativeSetOfflineDisableClamp(boolean disable, boolean write);
    public native static void nativeSetOfflineAperture(float aperture, boolean write);
    public native static void nativeSetOfflineFocalDistance(float dist, boolean write);
    public native static void nativeSetDofStrength(float strength, boolean write);

    public static void setDofStrength(int percent, boolean write) {
        dofStrengthPercent = clamp(percent, 100, 2000);
        nativeSetDofStrength(percent / 100.0f, write);
    }

    public static void setShaderPackPath(String path, boolean write) {
        String value = path == null || path.isBlank()
            ? Pipeline.VANILLA_RAY_TRACING_SHADER_PACK_PATH
            : path.trim();
        if (!Pipeline.setRayTracingShaderPackPath(value, true)) {
            return;
        }
        shaderPackPath = value;
        if (write) {
            overwriteConfig();
        }
    }
    public native static void nativeSetOfflineNativeRes(boolean enabled, boolean write);
    public native static void nativeSetOfflineDenoised(int mode, boolean write);
    public native static void nativeSetDlssEpochLength(int length, boolean write);
    public native static void nativeResetAccumulation();
    public native static int nativeGetAccumFrameCount();
    public native static int nativeGetDlssEpochCount();

    // ── Volumetric Cloud Module native methods ──
    public native static void nativeSetCloudQuality(int quality, boolean write);
    public native static void nativeSetCloudDensity(float density, boolean write);
    public native static void nativeSetCloudCoverage(float coverage, boolean write);
    public native static void nativeSetCloudType(float type, boolean write);
    public native static void nativeSetCloudSpeed(float speed, boolean write);
    public native static void nativeSetCloudAltitude(float altitude, boolean write);
    public native static void nativeSetCloudThicknessVol(float thickness, boolean write);
    public native static void nativeSetCloudDetailStrength(float strength, boolean write);
    public native static void nativeSetCloudScatterOctaves(int octaves, boolean write);
    public native static void nativeSetCloudAmbientStrength(float strength, boolean write);
    public native static void nativeSetCloudTemporalBlend(float blend, boolean write);
    public native static void nativeSetCloudNoiseScale(float scale, boolean write);
    public native static void nativeSetCloudCellFrequency(float freq, boolean write);
    public native static void nativeSetCloudAtmosphereFadeDist(float dist, boolean write);
    public native static void nativeSetCloudDebugMode(int mode, boolean write);
    public native static void nativeSetCloudWindAngle(float angle, boolean write);
    public native static void nativeSetCloudMarchSteps(int steps, boolean write);
    public native static void nativeSetCloudLightSteps(int steps, boolean write);
    public native static void nativeSetCloudResDivisor(int div, boolean needRecreate);
    public native static void nativeSetCloudNoiseRes(int res, boolean write);

    private static int clampTonemappingMode(int mode) {
        return Math.max(0, Math.min(8, mode));  // 0-7 = standard, 8 = PsychoVisual
    }

    // ── UI Theme setters ──

    public static void setUiGlobalAlphaPercent(int percent, boolean write) {
        uiGlobalAlphaPercent = Math.max(0, Math.min(100, percent));
        com.radiance.client.gui.RadianceTheme.setGlobalAlpha(uiGlobalAlphaPercent / 100f);
        if (write) overwriteConfig();
    }

    public static void setMaterialLabOpacityPercent(int percent, boolean write) {
        materialLabOpacityPercent = clamp(percent, 35, 100);
        if (write) overwriteConfig();
    }

    public static void setUiAdaptiveDimming(boolean enabled, boolean write) {
        uiAdaptiveDimming = enabled;
        com.radiance.client.gui.RadianceTheme.setAdaptiveDimmingEnabled(enabled);
        if (write) overwriteConfig();
    }

    public static void setVolCloudAmbientPercent(int percent, boolean write) {
        volCloudAmbientPercent = clamp(percent, 0, 200);
        nativeSetCloudAmbientStrength(volCloudAmbientPercent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudTemporalPercent(int percent, boolean write) {
        volCloudTemporalPercent = clamp(percent, 0, 99);
        float blend = percent == 0 ? -1.0f : percent / 100.0f;
        nativeSetCloudTemporalBlend(blend, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudNoiseScale(int scale, boolean write) {
        volCloudNoiseScale = clamp(scale, 64, 2048);
        nativeSetCloudNoiseScale((float) volCloudNoiseScale, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudCellFrequencyTenths(int tenths, boolean write) {
        volCloudCellFrequencyTenths = clamp(tenths, 10, 320);
        nativeSetCloudCellFrequency(volCloudCellFrequencyTenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudAtmosphereFadeDist(int dist, boolean write) {
        volCloudAtmosphereFadeDist = clamp(dist, 100, 4000);
        nativeSetCloudAtmosphereFadeDist((float) volCloudAtmosphereFadeDist, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudDebugMode(int mode, boolean write) {
        volCloudDebugMode = clamp(mode, 0, 8);
        nativeSetCloudDebugMode(volCloudDebugMode, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudWindAngleDegrees(int degrees, boolean write) {
        volCloudWindAngleDegrees = clamp(degrees, 0, 360);
        nativeSetCloudWindAngle((float)(volCloudWindAngleDegrees * Math.PI / 180.0), write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudMarchSteps(int steps, boolean write) {
        volCloudMarchSteps = clamp(steps, 0, 512);
        nativeSetCloudMarchSteps(volCloudMarchSteps, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudLightSteps(int steps, boolean write) {
        volCloudLightSteps = clamp(steps, 0, 16);
        nativeSetCloudLightSteps(volCloudLightSteps, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudResDivisor(int div, boolean write) {
        volCloudResDivisor = clamp(div, 0, 4);
        nativeSetCloudResDivisor(volCloudResDivisor, write);
        if (write) overwriteConfig();
    }

    public static void setVolCloudNoiseRes(int res, boolean write) {
        // Snap to nearest power of 2: 128, 256, 512
        if (res <= 192) volCloudNoiseRes = 128;
        else if (res <= 384) volCloudNoiseRes = 256;
        else volCloudNoiseRes = 512;
        nativeSetCloudNoiseRes(volCloudNoiseRes, write);
        if (write) overwriteConfig();
    }

    // --- Wet Surfaces ---
    public native static void nativeSetWetSurfaceStrength(float strength, boolean write);

    public static void setWetSurfaceStrengthPercent(int percent, boolean write) {
        wetSurfaceStrengthPercent = clamp(percent, 0, 200);
        nativeSetWetSurfaceStrength(wetSurfaceStrengthPercent / 100.0f, write);
        if (write) overwriteConfig();
    }
}
