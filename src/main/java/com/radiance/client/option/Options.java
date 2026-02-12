package com.radiance.client.option;

import com.radiance.client.RadianceClient;
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

public class Options {

    public static final String OPTION_PROPERTIES = "options.properties";

    public static final String CATEGORY_GAMEPLAY = "options.video.category.gameplay";
    public static final String CATEGORY_WINDOW = "options.video.category.window";
    public static final String CATEGORY_RAY_TRACING = "options.video.category.ray_tracing";
    public static final String CATEGORY_UPSCALER = "options.video.category.upscaler";
    public static final String CATEGORY_TONEMAPPING = "options.video.category.tonemapping";
    public static final String CATEGORY_TERRAIN = "options.video.category.terrain";
    public static final String CATEGORY_HDR = "options.video.category.hdr";
    public static final String CATEGORY_PIPELINE = "options.video.category.pipeline";

    public static final String KEY_RADIANCE_SETTINGS = "key.radiance.settings";
    public static final String KEY_CATEGORY_RADIANCE = "key.category.radiance";

    public static final String CATEGORY_EMISSION = "options.video.category.emission";

    // Tonemapping
    public static final String TONEMAP_MODE_KEY = "options.video.tonemap_mode";
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
    public static final String MIDDLE_GREY_KEY = "options.video.middle_grey";
    public static final String LWHITE_KEY = "options.video.lwhite";
    public static final String SATURATION_KEY = "options.video.saturation";

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

    // Ray Tracing
    public static final String RAY_BOUNCES_KEY = "options.video.ray_bounces";

    // Terrain
    public static final String CHUNK_BUILDING_BATCH_SIZE_KEY = "options.video.chunk_building_batch_size";
    public static final String CHUNK_BUILDING_TOTAL_BATCHES_KEY = "options.video.chunk_building_total_batches";

    // Pipeline
    public static final String PIPELINE_SETUP_KEY = "options.video.pipeline_setup";

    // Fields
    public static int maxFps = 260;
    public static int inactivityFpsLimit = 260;
    public static boolean vsync = true;
    public static int upscalerMode = 0; // 0=Off, 1=FSR3, 2=DLSS SR
    public static int upscalerQuality = 2;  // 0=Performance, 1=Balanced, 2=Quality, 3=Native/DLAA, 4=Custom
    public static int upscalerResOverride = 100; // 33-100%
    public static int rayBounces = 4;
    public static int chunkBuildingBatchSize = 2;
    public static int chunkBuildingTotalBatches = 4;
    public static int tonemappingMode = 1; // default: Reinhard Extended
    public static int minExposureTenK = 1;    // ten-thousandths: 1-10000 → 0.0001 to 1.0
    public static int maxExposure = 2;
    public static int exposureCompensation = 0; // tenths of EV: -30 to +30 → -3.0 to +3.0
    public static int middleGreyPercent = 18;   // 1-50 → 0.01 to 0.50
    public static int LwhiteTenths = 40;        // 10-200 → 1.0 to 20.0
    public static int saturationPercent = 100;  // 0-200 → 0.0 to 2.0 (default 1.0)
    public static int upscalerPreset = 5; // DLSS: 4=D, 5=E (default). Generic for future upscalers.

    // HDR10 output (default: disabled, pure SDR)
    public static boolean hdrEnabled = false;
    public static int hdrPeakNits = 1000;          // 400–10000 nits
    public static int hdrPaperWhiteNits = 203;     // 80–500 nits, ITU-R BT.2408 reference white
    public static int hdrUiBrightnessNits = 100;   // 50–300 nits, UI brightness in HDR mode

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

    // Debounce for DLSS quality changes (500ms)
    private static ScheduledFuture<?> dlssRebuildTask;
    private static final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "radiance-dlss-debounce");
            t.setDaemon(true);
            return t;
        });

    public static void readOptions() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        if (!Files.exists(path)) {
            overwriteConfig();
            return;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);

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
            setTonemappingMode(
                Integer.parseInt(props.getProperty("tonemappingMode",
                    String.valueOf(tonemappingMode))), false);

            upscalerMode = Integer.parseInt(props.getProperty("upscalerMode", String.valueOf(upscalerMode)));

            // Push to native directly on startup (no debounce, write=false)
            // Support both old "dlss*" keys and new "upscaler*" keys for backwards compatibility
            upscalerResOverride = Integer.parseInt(props.getProperty("upscalerResOverride",
                props.getProperty("dlssResOverride", String.valueOf(upscalerResOverride))));
            nativeSetDlssResOverride(upscalerResOverride, false);

            upscalerQuality = Integer.parseInt(props.getProperty("upscalerQuality",
                props.getProperty("dlssQuality", String.valueOf(upscalerQuality))));
            nativeSetDlssQuality(upscalerQuality, false);

            setMinExposure(Integer.parseInt(props.getProperty("minExposureTenK", String.valueOf(minExposureTenK))), false);
            setMaxExposure(Integer.parseInt(props.getProperty("maxExposure", String.valueOf(maxExposure))), false);

            upscalerPreset = Integer.parseInt(props.getProperty("upscalerPreset",
                props.getProperty("dlssPreset", String.valueOf(upscalerPreset))));
            nativeSetDlssPreset(upscalerPreset, false);

            rayBounces = Integer.parseInt(props.getProperty("rayBounces", String.valueOf(rayBounces)));
            nativeSetRayBounces(rayBounces, false);

            exposureCompensation = Integer.parseInt(props.getProperty(
                "exposureCompensation", String.valueOf(exposureCompensation)));
            middleGreyPercent = Integer.parseInt(props.getProperty(
                "middleGreyPercent", String.valueOf(middleGreyPercent)));
            LwhiteTenths = Integer.parseInt(props.getProperty(
                "LwhiteTenths", String.valueOf(LwhiteTenths)));
            saturationPercent = Integer.parseInt(props.getProperty(
                "saturationPercent", String.valueOf(saturationPercent)));
            nativeSetExposureCompensation(exposureCompensation / 10.0f, false);
            nativeSetMiddleGrey(middleGreyPercent / 100.0f, false);
            nativeSetLwhite(LwhiteTenths / 10.0f, false);
            nativeSetSaturation(saturationPercent / 100.0f, false);

            // HDR10
            hdrEnabled = Boolean.parseBoolean(props.getProperty("hdrEnabled", String.valueOf(hdrEnabled)));
            hdrPeakNits = Integer.parseInt(props.getProperty("hdrPeakNits", String.valueOf(hdrPeakNits)));
            hdrPaperWhiteNits = Integer.parseInt(props.getProperty("hdrPaperWhiteNits", String.valueOf(hdrPaperWhiteNits)));
            nativeSetHdrEnabled(hdrEnabled, false);
            nativeSetHdrPeakNits(hdrPeakNits, false);
            nativeSetHdrPaperWhiteNits(hdrPaperWhiteNits, false);

            hdrUiBrightnessNits = Integer.parseInt(props.getProperty("hdrUiBrightnessNits", String.valueOf(hdrUiBrightnessNits)));
            nativeSetHdrUiBrightnessNits(hdrUiBrightnessNits, false);

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

            overwriteConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void overwriteConfig() {
        Path path = RadianceClient.radianceDir.resolve(OPTION_PROPERTIES);
        Properties props = new Properties();
        props.setProperty("maxFps", String.valueOf(maxFps));
        props.setProperty("inactivityFpsLimit", String.valueOf(inactivityFpsLimit));
        props.setProperty("vsync", String.valueOf(vsync));
        props.setProperty("upscalerMode", String.valueOf(upscalerMode));
        props.setProperty("upscalerQuality", String.valueOf(upscalerQuality));
        props.setProperty("upscalerResOverride", String.valueOf(upscalerResOverride));
        props.setProperty("rayBounces", String.valueOf(rayBounces));
        props.setProperty("chunkBuildingBatchSize", String.valueOf(chunkBuildingBatchSize));
        props.setProperty("chunkBuildingTotalBatches", String.valueOf(chunkBuildingTotalBatches));
        props.setProperty("tonemappingMode", String.valueOf(tonemappingMode));
        props.setProperty("minExposureTenK", String.valueOf(minExposureTenK));
        props.setProperty("maxExposure", String.valueOf(maxExposure));
        props.setProperty("exposureCompensation", String.valueOf(exposureCompensation));
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

    public static void setTonemappingMode(int mode, boolean write) {
        Options.tonemappingMode = mode;
        nativeSetTonemappingMode(mode, write);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetDlssQuality(int quality, boolean write);

    public static void setUpscalerQuality(int quality, boolean write) {
        Options.upscalerQuality = quality;
        if (dlssRebuildTask != null) dlssRebuildTask.cancel(false);
        dlssRebuildTask = scheduler.schedule(() -> nativeSetDlssQuality(quality, write),
            500, TimeUnit.MILLISECONDS);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetDlssResOverride(int resOverride, boolean write);

    private static ScheduledFuture<?> dlssResOverrideTask;

    public static void setUpscalerResOverride(int resOverride, boolean write) {
        Options.upscalerResOverride = resOverride;
        if (dlssResOverrideTask != null) dlssResOverrideTask.cancel(false);
        dlssResOverrideTask = scheduler.schedule(() -> nativeSetDlssResOverride(resOverride, write),
            500, TimeUnit.MILLISECONDS);
        if (write) {
            overwriteConfig();
        }
    }

    public native static void nativeSetRayBounces(int bounces, boolean write);

    public static void setRayBounces(int bounces, boolean write) {
        Options.rayBounces = bounces;
        nativeSetRayBounces(bounces, write);
        if (write) {
            overwriteConfig();
        }
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
        if (upscalerPresetTask != null) upscalerPresetTask.cancel(false);
        upscalerPresetTask = scheduler.schedule(() -> nativeSetDlssPreset(preset, write),
            500, TimeUnit.MILLISECONDS);
        if (write) {
            overwriteConfig();
        }
    }

    // --- Exposure Compensation (float EV offset) ---
    public native static void nativeSetExposureCompensation(float ec, boolean write);

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
        Options.hdrEnabled = enabled;
        nativeSetHdrEnabled(enabled, write);
        if (write) {
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

    public native static void nativeRebuildChunks();
}
