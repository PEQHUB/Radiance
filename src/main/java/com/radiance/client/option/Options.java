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
    public static final String CATEGORY_PIPELINE = "options.video.category.pipeline";

    public static final String KEY_RADIANCE_SETTINGS = "key.radiance.settings";
    public static final String KEY_CATEGORY_RADIANCE = "key.category.radiance";

    // Tonemapping
    public static final String TONEMAP_MODE_KEY = "options.video.tonemap_mode";
    public static final String TONEMAP_MODE_PBR_NEUTRAL = "options.video.tonemap_mode.pbr_neutral";
    public static final String TONEMAP_MODE_REINHARD_EXTENDED = "options.video.tonemap_mode.reinhard_extended";
    public static final String TONEMAP_MODE_ACES = "options.video.tonemap_mode.aces";
    public static final String TONEMAP_MODE_AGX = "options.video.tonemap_mode.agx";
    public static final String TONEMAP_MODE_LOTTES = "options.video.tonemap_mode.lottes";
    public static final String TONEMAP_MODE_FROSTBITE = "options.video.tonemap_mode.frostbite";
    public static final String TONEMAP_MODE_UNCHARTED2 = "options.video.tonemap_mode.uncharted2";
    public static final String MAX_EXPOSURE_KEY = "options.video.max_exposure";

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
    public static int maxExposure = 2;
    public static int upscalerPreset = 5; // DLSS: 4=D, 5=E (default). Generic for future upscalers.

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

            setMaxExposure(Integer.parseInt(props.getProperty("maxExposure", String.valueOf(maxExposure))), false);

            upscalerPreset = Integer.parseInt(props.getProperty("upscalerPreset",
                props.getProperty("dlssPreset", String.valueOf(upscalerPreset))));
            nativeSetDlssPreset(upscalerPreset, false);

            rayBounces = Integer.parseInt(props.getProperty("rayBounces", String.valueOf(rayBounces)));
            nativeSetRayBounces(rayBounces, false);

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
        props.setProperty("maxExposure", String.valueOf(maxExposure));
        props.setProperty("upscalerPreset", String.valueOf(upscalerPreset));

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
}
