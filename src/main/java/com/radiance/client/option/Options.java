package com.radiance.client.option;

import com.radiance.client.RadianceClient;
import com.radiance.client.pipeline.Pipeline;
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
    public static final int CURRENT_OPTIONS_VERSION = 13;
    public static final int SDR_TONEMAPPING_DEFAULT_MODE = 1;
    public static final int SATURATION_DEFAULT_PERCENT = 130;

    // SDR transfer function
    public static final int SDR_TRANSFER_FUNCTION_GAMMA_22 = 0;
    public static final int SDR_TRANSFER_FUNCTION_SRGB = 1;

    public static final String CATEGORY_GAMEPLAY = "options.video.category.gameplay";
    public static final String CATEGORY_WINDOW = "options.video.category.window";
    public static final String CATEGORY_RAY_TRACING = "options.video.category.ray_tracing";
    public static final String CATEGORY_UPSCALER = "options.video.category.upscaler";
    public static final String CATEGORY_TONEMAPPING = "options.video.category.tonemapping";
    public static final String CATEGORY_CAMERA_CONTROLS = "options.video.category.camera_controls";
    public static final String CATEGORY_POST_PROCESSING = "options.video.category.post_processing";
    public static final String CATEGORY_TERRAIN = "options.video.category.terrain";
    public static final String CATEGORY_HDR = "options.video.category.hdr";
    public static final String CATEGORY_PIPELINE = "options.video.category.pipeline";
    public static final String CATEGORY_ENVIRONMENT = "options.video.category.environment";

    public static final String KEY_RADIANCE_SETTINGS = "key.radiance.settings";
    public static final String KEY_CATEGORY_RADIANCE = "key.category.radiance";

    public static final String CATEGORY_EMISSION = "options.video.category.emission";

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
    public static final int MOON_INTENSITY_DEFAULT_OVERWORLD_PERCENT = 10;
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
    public static final String LEGACY_EXPOSURE_KEY = "options.video.legacy_exposure";
    public static final String EXPOSURE_UP_SPEED_KEY = "options.video.exposure_up_speed";
    public static final String EXPOSURE_DOWN_SPEED_KEY = "options.video.exposure_down_speed";
    public static final String EXPOSURE_BRIGHT_ADAPT_BOOST_KEY = "options.video.exposure_bright_adapt_boost";
    public static final String EXPOSURE_HIGHLIGHT_PROTECTION_KEY = "options.video.exposure_highlight_protection";
    public static final String EXPOSURE_HIGHLIGHT_PERCENTILE_KEY = "options.video.exposure_highlight_percentile";
    public static final String EXPOSURE_HIGHLIGHT_SMOOTH_SPEED_KEY = "options.video.exposure_highlight_smooth_speed";
    public static final String EXPOSURE_LOG2_MAX_KEY = "options.video.exposure_log2_max";
    public static final String MIDDLE_GREY_KEY = "options.video.middle_grey";
    public static final String LWHITE_KEY = "options.video.lwhite";
    public static final String SATURATION_KEY = "options.video.saturation";
    public static final String CAS_ENABLED_KEY = "options.video.cas_enabled";
    public static final String CAS_SHARPNESS_KEY = "options.video.cas_sharpness";

    // HDR10
    public static final String HDR_ENABLED_KEY = "options.video.hdr_enabled";
    public static final String HDR_PEAK_NITS_KEY = "options.video.hdr_peak_nits";
    public static final String HDR_PAPER_WHITE_NITS_KEY = "options.video.hdr_paper_white_nits";

    // Upscaler (Off / FSR3 / DLSS SR)
    public static final String UPSCALER_MODE_KEY = "options.video.upscaler_mode";
    public static final String UPSCALER_MODE_OFF = "options.video.upscaler_mode.off";
    public static final String UPSCALER_MODE_FSR3 = "options.video.upscaler_mode.fsr3";
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

    // NVIDIA Reflex
    public static final String REFLEX_ENABLED_KEY = "options.video.reflex_enabled";
    public static final String REFLEX_BOOST_KEY = "options.video.reflex_boost";
    public static final String VRR_MODE_KEY = "options.video.vrr_mode";

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
    public static final String OMM_ENABLED_KEY = "options.video.omm_enabled";
    public static final String OMM_BAKER_LEVEL_KEY = "options.video.omm_baker_level";
    public static final String SIMPLIFIED_INDIRECT_KEY = "options.video.simplified_indirect";

    // Terrain
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";

    // Pipeline
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";

    // Fields
    public static int optionsVersion = CURRENT_OPTIONS_VERSION;
    public static int maxFps = 260;
    public static int inactivityFpsLimit = 260;
    public static boolean vsync = true;
    // Upscaler selection (menu-facing)
    // 0 = Off
    // 1 = DLSS (Ray Reconstruction)
    public static int upscalerMode = 1;
    public static int upscalerQuality = 2;  // 0=Performance, 1=Balanced, 2=Quality, 3=Native/DLAA, 4=Custom
    public static int upscalerResOverride = 100; // 33-100%
    public static boolean dlssDEnabled = true;
    public static int rayBounces = 4;
    public static boolean ommEnabled = false;
    public static int ommBakerLevel = 4;
    public static boolean simplifiedIndirect = false;
    public static boolean outputScale2x = false;
    public static boolean reflexEnabled = false;
    public static boolean reflexBoost = false;
    public static boolean vrrMode = false;
    public static int chunkBuildingBatchSize = 2;
    public static int chunkBuildingTotalBatches = 4;
    public static int tonemappingMode = SDR_TONEMAPPING_DEFAULT_MODE;
    public static int sdrTonemappingMode = SDR_TONEMAPPING_DEFAULT_MODE;
    public static int sdrTransferFunction = SDR_TRANSFER_FUNCTION_GAMMA_22;
    public static int minExposureTenK = 1;    // ten-thousandths: 1-10000 → 0.0001 to 1.0
    public static int maxExposure = 8;          // raised from 2 — allows ~3 EV boost for dark scenes
    public static int exposureCompensation = 0; // tenths of EV: -30 to +30 → -3.0 to +3.0
    public static boolean manualExposureEnabled = false;
    public static int manualExposureHundredths = 100; // hundredths: 1-2000 -> 0.01 to 20.00
    public static boolean legacyExposure = false;
    public static boolean casEnabled = false;
    public static int casSharpnessPercent = 50;
    // Treat speeds as max EV change per second (rate-limited adaptation).
    // Defaults are tuned to avoid sun-induced pulsing while still reacting to bright terrain.
    public static int exposureUpSpeedTenths = 10;             // 1-200 → 0.1 to 20.0
    public static int exposureDownSpeedTenths = 10;           // 1-200 → 0.1 to 20.0
    public static int exposureBrightAdaptBoostTenths = 10;    // 10-80 → 1.0 to 8.0
    public static int exposureHighlightProtectionPercent = 100; // 0-100 → 0.0 to 1.0
    public static int exposureHighlightPercentileTenK = 9500; // 9000-9999 → 0.9000 to 0.9999 (was 9850, now 95th percentile)
    public static int exposureHighlightSmoothSpeedTenths = 100; // 0-300 → 0.0 to 30.0
    public static int exposureLog2Max = 14;                   // 8-16, improved mode only
    public static int middleGreyPercent = 18;   // 1-50 → 0.01 to 0.50
    public static int LwhiteTenths = 40;        // 10-200 → 1.0 to 20.0
    public static int saturationPercent = SATURATION_DEFAULT_PERCENT;  // 0-200 → 0.0 to 2.0
    public static int upscalerPreset = 5; // DLSS: 4=D, 5=E (default). Generic for future upscalers.

    // HDR10 output (default: disabled, pure SDR)
    public static boolean hdrEnabled = false;
    public static int hdrPeakNits = 1000;          // 400–10000 nits
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

    public static final int SUN_PATH_MODE_DEFAULT = 1;
    public static final int SUN_INCLINATION_DEFAULT = 23;
    public static final int SUN_AZIMUTH_OFFSET_DEFAULT = 0;
    public static final int MOON_INCLINATION_DEFAULT = 23;
    public static final int MOON_AZIMUTH_OFFSET_DEFAULT = 0;

    // Emission
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

    // Environmental settings (per dimension: overworld/nether/end)
    public static int environmentEditingDimension = DIM_OVERWORLD;
    public static final int[] skyBrightnessPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] rainBlendPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudBrightnessPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudAlphaPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
    public static final int[] cloudHeightOffset = new int[]{0, 0, 0};

    // Volumetric cloud tuning (Fancy layout)
    public static final int[] cloudPuffinessPercent = new int[]{PERCENT_DEFAULT, PERCENT_DEFAULT, PERCENT_DEFAULT};
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
    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "radiance-dlss-debounce");
            t.setDaemon(true);
            return t;
        });

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
            tonemappingMode = clampTonemappingMode(Integer.parseInt(props.getProperty(
                "tonemappingMode", String.valueOf(tonemappingMode))));
            sdrTonemappingMode = clampTonemappingMode(Integer.parseInt(props.getProperty(
                "sdrTonemappingMode", String.valueOf(tonemappingMode))));
            nativeSetTonemappingMode(tonemappingMode, false);

            sdrTransferFunction = Math.max(0, Math.min(1, Integer.parseInt(props.getProperty(
                "sdrTransferFunction", String.valueOf(sdrTransferFunction)))));
            nativeSetSdrTransferFunction(sdrTransferFunction, false);

            upscalerMode = Integer.parseInt(props.getProperty("upscalerMode", String.valueOf(upscalerMode)));

            // Push to native directly on startup (no debounce, write=false)
            // Support both old "dlss*" keys and new "upscaler*" keys for backwards compatibility
            upscalerResOverride = Integer.parseInt(props.getProperty("upscalerResOverride",
                props.getProperty("dlssResOverride", String.valueOf(upscalerResOverride))));
            nativeSetDlssResOverride(upscalerResOverride, false);

            upscalerQuality = Integer.parseInt(props.getProperty("upscalerQuality",
                props.getProperty("dlssQuality", String.valueOf(upscalerQuality))));
            nativeSetDlssQuality(upscalerQuality, false);

            dlssDEnabled = Boolean.parseBoolean(props.getProperty("dlssDEnabled", String.valueOf(dlssDEnabled)));

            // Migration / consistency:
            // - legacy upscalerMode values: 0=Off, 1=FSR3, 2=DLSS
            // - new upscalerMode values:    0=Off, 1=DLSS
            if (upscalerMode >= 2) {
                upscalerMode = 1;
            }
            if (dlssDEnabled) {
                upscalerMode = 1;
            } else {
                upscalerMode = 0;
            }

            setMinExposure(Integer.parseInt(props.getProperty("minExposureTenK", String.valueOf(minExposureTenK))), false);
            setMaxExposure(Integer.parseInt(props.getProperty("maxExposure", String.valueOf(maxExposure))), false);

            upscalerPreset = Integer.parseInt(props.getProperty("upscalerPreset",
                props.getProperty("dlssPreset", String.valueOf(upscalerPreset))));
            nativeSetDlssPreset(upscalerPreset, false);

            rayBounces = Integer.parseInt(props.getProperty("rayBounces", String.valueOf(rayBounces)));
            nativeSetRayBounces(rayBounces, false);

            ommEnabled = Boolean.parseBoolean(props.getProperty("ommEnabled", String.valueOf(ommEnabled)));
            nativeSetOMMEnabled(ommEnabled, false);

            ommBakerLevel = clamp(Integer.parseInt(props.getProperty("ommBakerLevel", String.valueOf(ommBakerLevel))), 1, 8);
            nativeSetOMMBakerLevel(ommBakerLevel, false);

            simplifiedIndirect = Boolean.parseBoolean(props.getProperty("simplifiedIndirect", String.valueOf(simplifiedIndirect)));
            nativeSetSimplifiedIndirect(simplifiedIndirect, false);

            outputScale2x = Boolean.parseBoolean(props.getProperty("outputScale2x", String.valueOf(outputScale2x)));
            nativeSetOutputScale2x(outputScale2x, false);

            reflexEnabled = Boolean.parseBoolean(props.getProperty("reflexEnabled", String.valueOf(reflexEnabled)));
            nativeSetReflexEnabled(reflexEnabled, false);
            reflexBoost = Boolean.parseBoolean(props.getProperty("reflexBoost", String.valueOf(reflexBoost)));
            nativeSetReflexBoost(reflexBoost, false);
            vrrMode = Boolean.parseBoolean(props.getProperty("vrrMode", String.valueOf(vrrMode)));
            nativeSetVrrMode(vrrMode, false);

            exposureCompensation = Integer.parseInt(props.getProperty(
                "exposureCompensation", String.valueOf(exposureCompensation)));
            manualExposureEnabled = Boolean.parseBoolean(props.getProperty(
                "manualExposureEnabled", String.valueOf(manualExposureEnabled)));
            manualExposureHundredths = Integer.parseInt(props.getProperty(
                "manualExposureHundredths", String.valueOf(manualExposureHundredths)));
            manualExposureHundredths = clamp(manualExposureHundredths, 1, 2000);
            casEnabled = Boolean.parseBoolean(props.getProperty(
                "casEnabled", String.valueOf(casEnabled)));
            casSharpnessPercent = clamp(Integer.parseInt(props.getProperty(
                "casSharpnessPercent", String.valueOf(casSharpnessPercent))), 0, 100);
            middleGreyPercent = Integer.parseInt(props.getProperty(
                "middleGreyPercent", String.valueOf(middleGreyPercent)));
            LwhiteTenths = Integer.parseInt(props.getProperty(
                "LwhiteTenths", String.valueOf(LwhiteTenths)));
            saturationPercent = Integer.parseInt(props.getProperty(
                "saturationPercent", String.valueOf(saturationPercent)));
            legacyExposure = Boolean.parseBoolean(props.getProperty(
                "legacyExposure", String.valueOf(legacyExposure)));

            exposureUpSpeedTenths = Integer.parseInt(props.getProperty(
                "exposureUpSpeedTenths", String.valueOf(exposureUpSpeedTenths)));
            exposureDownSpeedTenths = Integer.parseInt(props.getProperty(
                "exposureDownSpeedTenths", String.valueOf(exposureDownSpeedTenths)));
            exposureBrightAdaptBoostTenths = Integer.parseInt(props.getProperty(
                "exposureBrightAdaptBoostTenths", String.valueOf(exposureBrightAdaptBoostTenths)));
            exposureHighlightProtectionPercent = Integer.parseInt(props.getProperty(
                "exposureHighlightProtectionPercent", String.valueOf(exposureHighlightProtectionPercent)));
            exposureHighlightPercentileTenK = Integer.parseInt(props.getProperty(
                "exposureHighlightPercentileTenK", String.valueOf(exposureHighlightPercentileTenK)));
            exposureHighlightSmoothSpeedTenths = Integer.parseInt(props.getProperty(
                "exposureHighlightSmoothSpeedTenths", String.valueOf(exposureHighlightSmoothSpeedTenths)));
            exposureLog2Max = Integer.parseInt(props.getProperty(
                "exposureLog2Max", String.valueOf(exposureLog2Max)));

            exposureUpSpeedTenths = clamp(exposureUpSpeedTenths, 1, 200);
            exposureDownSpeedTenths = clamp(exposureDownSpeedTenths, 1, 200);
            exposureBrightAdaptBoostTenths = clamp(exposureBrightAdaptBoostTenths, 10, 80);
            exposureHighlightProtectionPercent = clamp(exposureHighlightProtectionPercent, 0, 100);
            exposureHighlightPercentileTenK = clamp(exposureHighlightPercentileTenK, 9000, 9999);
            exposureHighlightSmoothSpeedTenths = clamp(exposureHighlightSmoothSpeedTenths, 0, 300);
            exposureLog2Max = clamp(exposureLog2Max, 8, 16);

            nativeSetExposureCompensation(exposureCompensation / 10.0f, false);
            nativeSetManualExposureEnabled(manualExposureEnabled, false);
            nativeSetManualExposure(manualExposureHundredths / 100.0f, false);
            nativeSetCasEnabled(casEnabled, false);
            nativeSetCasSharpness(casSharpnessPercent / 100.0f, false);
            nativeSetMiddleGrey(middleGreyPercent / 100.0f, false);
            nativeSetLwhite(LwhiteTenths / 10.0f, false);
            nativeSetSaturation(saturationPercent / 100.0f, false);
            nativeSetLegacyExposure(legacyExposure, false);
            nativeSetExposureUpSpeed(exposureUpSpeedTenths / 10.0f, false);
            nativeSetExposureDownSpeed(exposureDownSpeedTenths / 10.0f, false);
            nativeSetExposureBrightAdaptBoost(exposureBrightAdaptBoostTenths / 10.0f, false);
            nativeSetExposureHighlightProtection(exposureHighlightProtectionPercent / 100.0f, false);
            nativeSetExposureHighlightPercentile(exposureHighlightPercentileTenK / 10000.0f, false);
            nativeSetExposureHighlightSmoothingSpeed(exposureHighlightSmoothSpeedTenths / 10.0f, false);
            nativeSetExposureLog2MaxImproved((float) exposureLog2Max, false);

            // HDR10
            hdrEnabled = Boolean.parseBoolean(props.getProperty("hdrEnabled", String.valueOf(hdrEnabled)));
            hdrPeakNits = Integer.parseInt(props.getProperty("hdrPeakNits", String.valueOf(hdrPeakNits)));
            hdrPaperWhiteNits = Integer.parseInt(props.getProperty("hdrPaperWhiteNits", String.valueOf(hdrPaperWhiteNits)));
            nativeSetHdrEnabled(hdrEnabled, false);
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
            }

            if (loadedOptionsVersion < 13) {
                // Reset auto-exposure parameters to industry-standard defaults.
                // Histogram trim changed to 10%/90% (was ~0.5%/99.9%) and highlight
                // cap moved to 95th percentile (was 98.5th) to prevent blown highlights.
                // maxExposure raised to 8 (was 2) for proper dark-scene brightening.
                maxExposure = 8;
                nativeSetMaxExposure(maxExposure, false);
                exposureHighlightPercentileTenK = 9500;
                nativeSetExposureHighlightPercentile(exposureHighlightPercentileTenK / 10000.0f, false);
            }

            optionsVersion = CURRENT_OPTIONS_VERSION;

            // Emission
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
        props.setProperty("ommEnabled", String.valueOf(ommEnabled));
        props.setProperty("ommBakerLevel", String.valueOf(ommBakerLevel));
        props.setProperty("simplifiedIndirect", String.valueOf(simplifiedIndirect));
        props.setProperty("outputScale2x", String.valueOf(outputScale2x));
        props.setProperty("reflexEnabled", String.valueOf(reflexEnabled));
        props.setProperty("reflexBoost", String.valueOf(reflexBoost));
        props.setProperty("vrrMode", String.valueOf(vrrMode));
        props.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        props.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));
        props.setProperty("tonemappingMode", String.valueOf(tonemappingMode));
        props.setProperty("sdrTonemappingMode", String.valueOf(sdrTonemappingMode));
        props.setProperty("sdrTransferFunction", String.valueOf(sdrTransferFunction));
        props.setProperty("minExposureTenK", String.valueOf(minExposureTenK));
        props.setProperty("maxExposure", String.valueOf(maxExposure));
        props.setProperty("exposureCompensation", String.valueOf(exposureCompensation));
        props.setProperty("manualExposureEnabled", String.valueOf(manualExposureEnabled));
        props.setProperty("manualExposureHundredths", String.valueOf(manualExposureHundredths));
        props.setProperty("casEnabled", String.valueOf(casEnabled));
        props.setProperty("casSharpnessPercent", String.valueOf(casSharpnessPercent));
        props.setProperty("legacyExposure", String.valueOf(legacyExposure));
        props.setProperty("exposureUpSpeedTenths", String.valueOf(exposureUpSpeedTenths));
        props.setProperty("exposureDownSpeedTenths", String.valueOf(exposureDownSpeedTenths));
        props.setProperty("exposureBrightAdaptBoostTenths", String.valueOf(exposureBrightAdaptBoostTenths));
        props.setProperty("exposureHighlightProtectionPercent", String.valueOf(exposureHighlightProtectionPercent));
        props.setProperty("exposureHighlightPercentileTenK", String.valueOf(exposureHighlightPercentileTenK));
        props.setProperty("exposureHighlightSmoothSpeedTenths", String.valueOf(exposureHighlightSmoothSpeedTenths));
        props.setProperty("exposureLog2Max", String.valueOf(exposureLog2Max));
        props.setProperty("middleGreyPercent", String.valueOf(middleGreyPercent));
        props.setProperty("LwhiteTenths", String.valueOf(LwhiteTenths));
        props.setProperty("saturationPercent", String.valueOf(saturationPercent));
        props.setProperty("upscalerPreset", String.valueOf(upscalerPreset));
        props.setProperty("hdrEnabled", String.valueOf(hdrEnabled));
        props.setProperty("hdrPeakNits", String.valueOf(hdrPeakNits));
        props.setProperty("hdrPaperWhiteNits", String.valueOf(hdrPaperWhiteNits));
        props.setProperty("hdrUiBrightnessNits", String.valueOf(hdrUiBrightnessNits));
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
                    props.getProperty("env.cloudPuffinessPercent." + dim, String.valueOf(PERCENT_DEFAULT))));
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
                cloudPuffinessPercent[dim] = PERCENT_DEFAULT;
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
    }

    private static void setEnvironmentDefaults() {
        environmentEditingDimension = DIM_OVERWORLD;
        for (int dim = 0; dim < DIM_COUNT; dim++) {
            skyBrightnessPercent[dim] = PERCENT_DEFAULT;
            rainBlendPercent[dim] = PERCENT_DEFAULT;
            cloudBrightnessPercent[dim] = PERCENT_DEFAULT;
            cloudAlphaPercent[dim] = PERCENT_DEFAULT;
            cloudHeightOffset[dim] = 0;
            cloudPuffinessPercent[dim] = PERCENT_DEFAULT;
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

        // Sun/Moon orbit defaults
        sunPathMode = SUN_PATH_MODE_DEFAULT;
        sunInclinationDeg = SUN_INCLINATION_DEFAULT;
        sunAzimuthOffsetDeg = SUN_AZIMUTH_OFFSET_DEFAULT;
        moonFollowSun = true;
        moonInclinationDeg = MOON_INCLINATION_DEFAULT;
        moonAzimuthOffsetDeg = MOON_AZIMUTH_OFFSET_DEFAULT;
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
        // Locked to 3% — higher values cause visible corner artifacts at concave cloud edges.
        return 0.03f;
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
        rayBounces = 4;
        ommEnabled = false;
        ommBakerLevel = 4;
        simplifiedIndirect = false;
        outputScale2x = false;
        reflexEnabled = false;
        reflexBoost = false;
        vrrMode = false;
        chunkBuildingBatchSize = 2;
        chunkBuildingTotalBatches = 4;
        sdrTransferFunction = SDR_TRANSFER_FUNCTION_GAMMA_22;
        saturationPercent = SATURATION_DEFAULT_PERCENT;
        upscalerQuality = 2;
        upscalerResOverride = 100;
        upscalerPreset = 5;
        hdrEnabled = false;
        hdrPeakNits = 1000;
        hdrPaperWhiteNits = 203;
        hdrUiBrightnessNits = 100;
        minExposureTenK = 1;
        maxExposure = 2;
        exposureCompensation = 0;
        manualExposureEnabled = false;
        manualExposureHundredths = 100;
        legacyExposure = false;
        casEnabled = false;
        casSharpnessPercent = 50;
        exposureUpSpeedTenths = 10;
        exposureDownSpeedTenths = 10;
        exposureBrightAdaptBoostTenths = 10;
        exposureHighlightProtectionPercent = 100;
        exposureHighlightPercentileTenK = 9850;
        exposureHighlightSmoothSpeedTenths = 100;
        exposureLog2Max = 14;
        middleGreyPercent = 18;
        LwhiteTenths = 40;
        // Environment + orbit
        setEnvironmentDefaults();
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

    public native static void nativeSetTonemappingMode(int mode, boolean write);

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
        if (write) {
            overwriteConfig();
        }
    }

    public static void setUpscalerMode(int mode, boolean write) {
        int clamped = Math.max(0, Math.min(1, mode));

        if (clamped == 1) {
            try {
                if (!Pipeline.isNativeModuleAvailable("render_pipeline.module.dlss.name")) {
                    RadianceClient.LOGGER.warn(
                        "DLSS requested but DLSS module is not available; disabling.");
                    clamped = 0;
                }
            } catch (UnsatisfiedLinkError ignored) {
                // Native not loaded yet; keep requested value.
            }
        }

        Options.upscalerMode = clamped;
        Options.dlssDEnabled = (clamped == 1);

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

    private static ScheduledFuture<?> dlssResOverrideTask;

    public static void setUpscalerResOverride(int resOverride, boolean write) {
        Options.upscalerResOverride = resOverride;
        if (Options.dlssDEnabled) {
            if (dlssResOverrideTask != null) dlssResOverrideTask.cancel(false);
            dlssResOverrideTask = scheduler.schedule(
                () -> runOnClientThread(() -> nativeSetDlssResOverride(resOverride, write)),
                500,
                TimeUnit.MILLISECONDS);
        }
        if (write) {
            overwriteConfig();
        }
    }

    public static void setDlssDEnabled(boolean enabled, boolean write) {
        setUpscalerMode(enabled ? 1 : 0, write);
    }

    public native static void nativeSetRayBounces(int bounces, boolean write);

    public static void setRayBounces(int bounces, boolean write) {
        Options.rayBounces = bounces;
        nativeSetRayBounces(bounces, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- OMM ---
    public native static void nativeSetOMMEnabled(boolean enabled, boolean write);

    public static void setOMMEnabled(boolean enabled, boolean write) {
        Options.ommEnabled = enabled;
        nativeSetOMMEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- OMM Baker Level ---
    public native static void nativeSetOMMBakerLevel(int level, boolean write);

    public static void setOMMBakerLevel(int level, boolean write) {
        Options.ommBakerLevel = Math.max(1, Math.min(8, level));
        nativeSetOMMBakerLevel(Options.ommBakerLevel, write);
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

    // --- Output Scale 2x ---
    public native static void nativeSetOutputScale2x(boolean enabled, boolean write);

    public static void setOutputScale2x(boolean enabled, boolean write) {
        Options.outputScale2x = enabled;
        nativeSetOutputScale2x(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- NVIDIA Reflex ---
    public native static void nativeSetReflexEnabled(boolean enabled, boolean write);
    public native static void nativeSetReflexBoost(boolean enabled, boolean write);
    public native static boolean nativeIsReflexSupported();

    public static boolean isReflexSupported() {
        try { return nativeIsReflexSupported(); }
        catch (UnsatisfiedLinkError e) { return false; }
    }

    public static void setReflexEnabled(boolean enabled, boolean write) {
        Options.reflexEnabled = enabled;
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

    // --- VRR Mode (Reflex frame cap) ---
    public native static void nativeSetVrrMode(boolean enabled, boolean write);
    public native static int nativeGetDisplayRefreshRate();

    public static void setVrrMode(boolean enabled, boolean write) {
        Options.vrrMode = enabled;
        nativeSetVrrMode(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static int getDisplayRefreshRate() {
        try { return nativeGetDisplayRefreshRate(); }
        catch (UnsatisfiedLinkError e) { return 0; }
    }

    // --- Min Exposure ---
    public native static void nativeSetMinExposure(float minExposure, boolean write);

    public static void setMinExposure(int tenK, boolean write) {
        Options.minExposureTenK = tenK;
        nativeSetMinExposure(tenK / 10000.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetMaxExposure(int maxExposure, boolean write);

    public static void setMaxExposure(int maxExposure, boolean write) {
        Options.maxExposure = maxExposure;
        nativeSetMaxExposure(maxExposure, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetDlssPreset(int preset, boolean write);

    private static ScheduledFuture<?> upscalerPresetTask;

    public static void setUpscalerPreset(int preset, boolean write) {
        Options.upscalerPreset = preset;
        if (Options.dlssDEnabled) {
            if (upscalerPresetTask != null) upscalerPresetTask.cancel(false);
            upscalerPresetTask = scheduler.schedule(
                () -> runOnClientThread(() -> nativeSetDlssPreset(preset, write)),
                500,
                TimeUnit.MILLISECONDS);
        }
        if (write) {
            overwriteConfig();
        }
    }

    // --- Exposure Compensation (float EV offset) ---
    public native static void nativeSetExposureCompensation(float ec, boolean write);

    public native static void nativeSetManualExposureEnabled(boolean enabled, boolean write);

    public static void setManualExposureEnabled(boolean enabled, boolean write) {
        Options.manualExposureEnabled = enabled;
        nativeSetManualExposureEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetManualExposure(float exposure, boolean write);

    public static void setManualExposureHundredths(int hundredths, boolean write) {
        hundredths = clamp(hundredths, 1, 2000);
        Options.manualExposureHundredths = hundredths;
        nativeSetManualExposure(hundredths / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetCasEnabled(boolean enabled, boolean write);

    public static void setCasEnabled(boolean enabled, boolean write) {
        Options.casEnabled = enabled;
        nativeSetCasEnabled(enabled, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetCasSharpness(float sharpness, boolean write);

    public static void setCasSharpnessPercent(int percent, boolean write) {
        percent = clamp(percent, 0, 100);
        Options.casSharpnessPercent = percent;
        nativeSetCasSharpness(percent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    public static void setExposureCompensation(int tenths, boolean write) {
        Options.exposureCompensation = tenths;
        nativeSetExposureCompensation(tenths / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Middle Grey ---
    public native static void nativeSetMiddleGrey(float mg, boolean write);

    public static void setMiddleGrey(int percent, boolean write) {
        Options.middleGreyPercent = percent;
        nativeSetMiddleGrey(percent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Legacy Exposure ---
    public native static void nativeSetLegacyExposure(boolean legacyExposure, boolean write);

    public static void setLegacyExposure(boolean legacyExposure, boolean write) {
        Options.legacyExposure = legacyExposure;
        nativeSetLegacyExposure(legacyExposure, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Exposure Speeds ---
    public native static void nativeSetExposureUpSpeed(float speed, boolean write);
    public native static void nativeSetExposureDownSpeed(float speed, boolean write);
    public native static void nativeSetExposureBrightAdaptBoost(float boost, boolean write);
    public native static void nativeSetExposureHighlightProtection(float protection, boolean write);
    public native static void nativeSetExposureHighlightPercentile(float percentile, boolean write);
    public native static void nativeSetExposureHighlightSmoothingSpeed(float speed, boolean write);
    public native static void nativeSetExposureLog2MaxImproved(float log2Max, boolean write);

    public static void setExposureUpSpeedTenths(int tenths, boolean write) {
        tenths = clamp(tenths, 1, 200);
        Options.exposureUpSpeedTenths = tenths;
        nativeSetExposureUpSpeed(tenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setExposureDownSpeedTenths(int tenths, boolean write) {
        tenths = clamp(tenths, 1, 200);
        Options.exposureDownSpeedTenths = tenths;
        nativeSetExposureDownSpeed(tenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setExposureBrightAdaptBoostTenths(int tenths, boolean write) {
        tenths = clamp(tenths, 10, 80);
        Options.exposureBrightAdaptBoostTenths = tenths;
        nativeSetExposureBrightAdaptBoost(tenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setExposureHighlightProtectionPercent(int percent, boolean write) {
        percent = clamp(percent, 0, 100);
        Options.exposureHighlightProtectionPercent = percent;
        nativeSetExposureHighlightProtection(percent / 100.0f, write);
        if (write) overwriteConfig();
    }

    public static void setExposureHighlightPercentileTenK(int tenK, boolean write) {
        tenK = clamp(tenK, 9000, 9999);
        Options.exposureHighlightPercentileTenK = tenK;
        nativeSetExposureHighlightPercentile(tenK / 10000.0f, write);
        if (write) overwriteConfig();
    }

    public static void setExposureHighlightSmoothSpeedTenths(int tenths, boolean write) {
        tenths = clamp(tenths, 0, 300);
        Options.exposureHighlightSmoothSpeedTenths = tenths;
        nativeSetExposureHighlightSmoothingSpeed(tenths / 10.0f, write);
        if (write) overwriteConfig();
    }

    public static void setExposureLog2Max(int ev, boolean write) {
        ev = clamp(ev, 8, 16);
        Options.exposureLog2Max = ev;
        nativeSetExposureLog2MaxImproved((float) ev, write);
        if (write) overwriteConfig();
    }

    // --- Lwhite (Reinhard white point) ---
    public native static void nativeSetLwhite(float lw, boolean write);

    public static void setLwhite(int tenths, boolean write) {
        Options.LwhiteTenths = tenths;
        nativeSetLwhite(tenths / 10.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Saturation ---
    public native static void nativeSetSaturation(float saturation, boolean write);

    public static void setSaturation(int percent, boolean write) {
        Options.saturationPercent = percent;
        nativeSetSaturation(percent / 100.0f, write);
        if (write) {
            overwriteConfig();
        }
    }

    // --- HDR10 Output ---
    public native static void nativeSetHdrEnabled(boolean enabled, boolean write);

    public static void setHdrEnabled(boolean enabled, boolean write) {
        if (enabled) {
            sdrTonemappingMode = clampTonemappingMode(tonemappingMode);
        }

        Options.hdrEnabled = enabled;
        nativeSetHdrEnabled(enabled, false);

        if (!enabled) {
            tonemappingMode = clampTonemappingMode(sdrTonemappingMode);
            nativeSetTonemappingMode(tonemappingMode, false);
        }

        if (write) {
            try {
                Pipeline.loadPipeline();
                Pipeline.build();
            } catch (Exception e) {
                RadianceClient.LOGGER.error("Failed to rebuild pipeline after HDR toggle.",
                    e);
            }

            nativeSetHdrEnabled(enabled, true);
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrPeakNits(int nits, boolean write);

    public static void setHdrPeakNits(int nits, boolean write) {
        Options.hdrPeakNits = nits;
        nativeSetHdrPeakNits(nits, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrPaperWhiteNits(int nits, boolean write);

    public static void setHdrPaperWhiteNits(int nits, boolean write) {
        Options.hdrPaperWhiteNits = nits;
        nativeSetHdrPaperWhiteNits(nits, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetHdrUiBrightnessNits(int nits, boolean write);

    public static void setHdrUiBrightnessNits(int nits, boolean write) {
        Options.hdrUiBrightnessNits = nits;
        nativeSetHdrUiBrightnessNits(nits, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static boolean nativeIsHdrActive();

    public native static boolean nativeIsHdrSupported();

    public static boolean isHdrActive() {
        if (!hdrEnabled) {
            return false;
        }

        try {
            return nativeIsHdrActive();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public static boolean isHdrSupported() {
        try {
            return nativeIsHdrSupported();
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    public native static void nativeRebuildChunks();

    private static int clampTonemappingMode(int mode) {
        return Math.max(0, Math.min(7, mode));
    }
}
