package com.radiance.v2.bridge;

import com.radiance.client.option.Options;

/**
 * Java-side bridge for the V2 engine config system.
 * Native implementations are auto-generated in config_bridge.cpp (gen_config.py).
 *
 * Each nativeSet*() posts a CmdConfigPatch to the engine's BridgeService,
 * which applies the change on the engine thread and fires ConfigKey subscribers.
 *
 * The {@code write} parameter on each native call is reserved for future use
 * (C++ side persistence). For Java-originated changes it is always {@code false}
 * since Options.java owns persistence via overwriteConfig().
 */
public class ConfigBridge {

    private ConfigBridge() {} // static-only

    // ========================== Native declarations ==========================
    // Signatures must match config_bridge.cpp (jni_name, param types).
    // Grouped by config_service.cpp category for readability.

    // --- display ---
    private static native void nativeSetVsync(boolean value, boolean write);
    private static native void nativeSetMaxFps(int value, boolean write);
    private static native void nativeSetInactivityFpsLimit(int value, boolean write);
    private static native void nativeSetOutputScale2x(boolean value, boolean write);

    // --- upscaler ---
    private static native void nativeSetUpscalerMode(int value, boolean write);
    private static native void nativeSetUpscalerQuality(int value, boolean write);
    private static native void nativeSetUpscalerResOverride(int value, boolean write);
    // dlssQuality is an alias for upscalerQuality in V2 — not a separate JNI entry

    // --- rayTracing ---
    private static native void nativeSetRayBounces(int value, boolean write);
    private static native void nativeSetSimplifiedIndirect(boolean value, boolean write);
    private static native void nativeSetSerEnabled(boolean value, boolean write);
    private static native void nativeSetSerHintsEnabled(boolean value, boolean write);
    private static native void nativeSetNoiseLOD(boolean value, boolean write);
    private static native void nativeSetMultiScatterGGX(boolean value, boolean write);
    private static native void nativeSetEonDiffuse(boolean value, boolean write);
    private static native void nativeSetGreedyMeshingEnabled(boolean value, boolean write);
    private static native void nativeSetOmmEnabled(boolean value, boolean write);
    private static native void nativeSetOmmBakerLevel(int value, boolean write);
    private static native void nativeSetAreaLightsEnabled(boolean value, boolean write);
    private static native void nativeSetShadowSoftness(float value, boolean write);
    private static native void nativeSetRestirEnabled(boolean value, boolean write);
    private static native void nativeSetRestirBounceEnabled(boolean value, boolean write);
    private static native void nativeSetRestirSimplifiedBRDF(boolean value, boolean write);
    private static native void nativeSetRestirCandidates(int value, boolean write);
    private static native void nativeSetRestirTemporalMClamp(int value, boolean write);
    private static native void nativeSetRestirWClamp(int value, boolean write);
    private static native void nativeSetRestirSpatialTaps(int value, boolean write);
    private static native void nativeSetRestirSpatialRadius(int value, boolean write);
    private static native void nativeSetPomEnabled(boolean value, boolean write);
    private static native void nativeSetPomHeightScale(float value, boolean write);
    private static native void nativeSetPomSteps(int value, boolean write);
    private static native void nativeSetPomRefinement(int value, boolean write);
    private static native void nativeSetPomFadeDistance(float value, boolean write);
    private static native void nativeSetSharcEnabled(boolean value, boolean write);
    private static native void nativeSetSharcSceneScale(float value, boolean write);
    private static native void nativeSetSharcRoughnessThreshold(float value, boolean write);

    // --- exposure ---
    // Note: these take raw Java ints; C++ config_bridge.cpp divides by 10 or 100.
    private static native void nativeSetExposureCompensation(int value, boolean write);
    private static native void nativeSetManualExposureEnabled(boolean value, boolean write);
    private static native void nativeSetManualExposure(float value, boolean write);
    private static native void nativeSetBrightAdaptSpeed(int value, boolean write);
    private static native void nativeSetDarkAdaptSpeed(int value, boolean write);
    private static native void nativeSetSceneChangeThreshold(int value, boolean write);
    private static native void nativeSetCenterWeightStrength(int value, boolean write);
    private static native void nativeSetHighlightWeight(int value, boolean write);

    // --- toneMapping ---
    private static native void nativeSetTonemappingMode(int value, boolean write);
    private static native void nativeSetPsychoEnabled(boolean value, boolean write);
    private static native void nativeSetSaturation(int value, boolean write);
    private static native void nativeSetSaturationAdaptive(boolean value, boolean write);
    private static native void nativeSetLwhite(int value, boolean write);
    private static native void nativeSetColorExpansion(int value, boolean write);
    private static native void nativeSetSharpenerMode(int value, boolean write);
    private static native void nativeSetCasSharpness(int value, boolean write);
    private static native void nativeSetPsychoPeakSDR(int value, boolean write);

    // --- bloom (V2-only, no Options.java fields yet) ---
    // nativeSetBloomEnabled, nativeSetBloomIntensity, nativeSetBloomThreshold
    // omitted — no Java-side fields to source from.

    // --- chunks ---
    private static native void nativeSetChunkBuildingBatchSize(int value, boolean write);
    private static native void nativeSetChunkBuildingTotalBatches(int value, boolean write);
    private static native void nativeSetChunkCullDistance(float value, boolean write);
    private static native void nativeSetChunkLodDistance(float value, boolean write);

    // --- debug ---
    private static native void nativeSetDiagLevel(int value, boolean write);
    private static native void nativeSetDiagFlags(int value, boolean write);
    private static native void nativeSetGpuDiagnostics(boolean value, boolean write);
    private static native void nativeSetValidationLayers(boolean value, boolean write);

    // --- frameGen ---
    private static native void nativeSetFrameGenEnabled(boolean value, boolean write);
    private static native void nativeSetFrameGenMultiplier(int value, boolean write);

    // --- reflex ---
    private static native void nativeSetReflexEnabled(boolean value, boolean write);
    private static native void nativeSetReflexBoost(boolean value, boolean write);
    private static native void nativeSetMaxFpsLimit(int value, boolean write);

    // --- clouds ---
    private static native void nativeSetCloudQuality(int value, boolean write);
    private static native void nativeSetCloudDensity(float value, boolean write);
    private static native void nativeSetCloudCoverage(float value, boolean write);
    private static native void nativeSetCloudType(float value, boolean write);
    private static native void nativeSetCloudSpeed(float value, boolean write);
    private static native void nativeSetCloudAltitude(float value, boolean write);
    private static native void nativeSetCloudThickness(float value, boolean write);
    private static native void nativeSetCloudDetailStrength(float value, boolean write);
    private static native void nativeSetCloudScatterOctaves(int value, boolean write);
    private static native void nativeSetCloudAmbientStrength(float value, boolean write);
    private static native void nativeSetCloudTemporalBlend(float value, boolean write);
    private static native void nativeSetCloudNoiseScale(float value, boolean write);
    private static native void nativeSetCloudCellFrequency(float value, boolean write);
    private static native void nativeSetCloudAtmosphereFadeDist(float value, boolean write);
    private static native void nativeSetCloudDebugMode(int value, boolean write);
    private static native void nativeSetCloudWindAngle(float value, boolean write);

    // --- offlineAccum ---
    private static native void nativeSetOfflineAccumEnabled(boolean value, boolean write);
    private static native void nativeSetOfflineBounces(int value, boolean write);
    private static native void nativeSetOfflineDenoised(boolean value, boolean write);
    private static native void nativeSetOfflineAperture(float value, boolean write);
    private static native void nativeSetOfflineFocalDistance(float value, boolean write);
    private static native void nativeSetOfflineDlssEpochLength(int value, boolean write);

    // --- displacement ---
    private static native void nativeSetDisplacementQuality(int value, boolean write);
    private static native void nativeSetTessMaxLevel(int value, boolean write);
    private static native void nativeSetTessNearDist(float value, boolean write);
    private static native void nativeSetTessMidDist(float value, boolean write);
    private static native void nativeSetTessFarDist(float value, boolean write);

    // ========================== Public API ==========================

    /**
     * Push every Options field to the V2 engine config.
     * Call once after {@link EngineBridge#initFromWindow} succeeds and
     * {@link Options#readOptions()} has populated the Java fields.
     *
     * All values are sent with {@code write=false} since Java owns persistence.
     */
    public static void syncAllSettings() {
        if (!EngineBridge.isV2Active()) return;

        try {
            // display
            nativeSetVsync(Options.vsync, false);
            nativeSetMaxFps(Options.maxFps, false);
            nativeSetInactivityFpsLimit(Options.inactivityFpsLimit, false);
            nativeSetOutputScale2x(Options.outputScale2x, false);

            // upscaler
            nativeSetUpscalerMode(Options.upscalerMode, false);
            nativeSetUpscalerQuality(Options.upscalerQuality, false);
            nativeSetUpscalerResOverride(Options.upscalerResOverride, false);

            // rayTracing
            nativeSetRayBounces(Options.rayBounces, false);
            nativeSetSimplifiedIndirect(Options.simplifiedIndirect, false);
            nativeSetSerEnabled(Options.serEnabled, false);
            nativeSetSerHintsEnabled(Options.serHintsEnabled, false);
            nativeSetNoiseLOD(Options.noiseLOD, false);
            nativeSetMultiScatterGGX(Options.multiScatterGGX, false);
            nativeSetEonDiffuse(Options.eonDiffuse, false);
            nativeSetGreedyMeshingEnabled(Options.greedyMeshingEnabled, false);
            nativeSetOmmEnabled(Options.ommEnabled, false);
            nativeSetOmmBakerLevel(Options.ommBakerLevel, false);
            nativeSetAreaLightsEnabled(Options.areaLightsEnabled, false);
            nativeSetShadowSoftness(Options.shadowSoftnessPercent / 100.0f, false);
            nativeSetRestirEnabled(Options.restirEnabled, false);
            nativeSetRestirBounceEnabled(Options.restirBounceEnabled, false);
            nativeSetRestirSimplifiedBRDF(Options.restirSimplifiedBRDF, false);
            nativeSetRestirCandidates(Options.restirCandidates, false);
            nativeSetRestirTemporalMClamp(Options.restirTemporalMClamp, false);
            nativeSetRestirWClamp(Options.restirWClamp, false);
            nativeSetRestirSpatialTaps(Options.restirSpatialTaps, false);
            nativeSetRestirSpatialRadius(Options.restirSpatialRadius, false);
            nativeSetPomEnabled(Options.pomEnabled, false);
            nativeSetPomHeightScale(Options.pomHeightScalePercent / 100.0f, false);
            nativeSetPomSteps(Options.pomSteps, false);
            nativeSetPomRefinement(Options.pomRefinement, false);
            nativeSetPomFadeDistance((float) Options.pomFadeDistance, false);
            nativeSetSharcEnabled(Options.sharcEnabled, false);
            nativeSetSharcSceneScale(Options.sharcSceneScaleTenths / 10.0f, false);
            nativeSetSharcRoughnessThreshold(Options.sharcRoughnessThresholdPercent / 100.0f, false);

            // exposure (raw ints — C++ config_bridge divides by 10/100)
            nativeSetExposureCompensation(Options.exposureCompensation, false);
            nativeSetManualExposureEnabled(Options.manualExposureEnabled, false);
            nativeSetManualExposure(Options.ev100ToLinearExposure(Options.manualExposureEV100Tenths), false);
            nativeSetBrightAdaptSpeed(Options.brightAdaptSpeedTenths, false);
            nativeSetDarkAdaptSpeed(Options.darkAdaptSpeedTenths, false);
            nativeSetSceneChangeThreshold(Options.sceneChangeThresholdTenths, false);
            nativeSetCenterWeightStrength(Options.centerWeightPercent, false);
            nativeSetHighlightWeight(Options.highlightWeightPercent, false);

            // toneMapping
            nativeSetTonemappingMode(Options.tonemappingMode, false);
            nativeSetPsychoEnabled(Options.psychoEnabled, false);
            nativeSetSaturation(Options.saturationPercent, false);
            nativeSetSaturationAdaptive(Options.saturationAdaptive, false);
            nativeSetLwhite(Options.LwhiteTenths, false);
            nativeSetColorExpansion(Options.colorExpansionPercent, false);
            nativeSetSharpenerMode(Options.sharpenerMode, false);
            nativeSetCasSharpness(Options.casSharpnessPercent, false);
            nativeSetPsychoPeakSDR(Options.psychoPeakSDRTenths, false);

            // chunks
            nativeSetChunkBuildingBatchSize(Options.chunkBuildingBatchSize, false);
            nativeSetChunkBuildingTotalBatches(Options.chunkBuildingTotalBatches, false);
            nativeSetChunkCullDistance((float) Options.chunkCullDistance, false);
            nativeSetChunkLodDistance((float) Options.chunkLodDistance, false);

            // debug
            nativeSetDiagLevel(Options.diagLevel, false);
            nativeSetDiagFlags(Options.diagFlags, false);
            nativeSetValidationLayers(Options.validationLayers, false);

            // frameGen — frameGenMode: 0=Off, 1=On, 2=Auto
            nativeSetFrameGenEnabled(Options.frameGenMode != 0, false);
            nativeSetFrameGenMultiplier(Options.frameGenMultiplier, false);

            // reflex
            nativeSetReflexEnabled(Options.reflexEnabled, false);
            nativeSetReflexBoost(Options.reflexBoost, false);

            // offlineAccum — offlineState: 0=NORMAL, 1=FREE, 2=ACCUMULATING
            nativeSetOfflineAccumEnabled(Options.offlineState > 0, false);
            nativeSetOfflineBounces(Options.offlineBounces, false);
            nativeSetOfflineAperture(Options.computeApertureRadius(), false);
            nativeSetOfflineFocalDistance(Options.offlineFocalDistance, false);

            // displacement
            nativeSetDisplacementQuality(Options.displacementQuality, false);
            nativeSetTessMaxLevel(Options.tessMaxLevel, false);
            nativeSetTessNearDist((float) Options.tessNearDist, false);
            nativeSetTessMidDist((float) Options.tessMidDist, false);
            nativeSetTessFarDist((float) Options.tessFarDist, false);

            System.out.println("[Radiance] ConfigBridge: synced all settings to V2 engine");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[Radiance] ConfigBridge: JNI link error during sync — " + e.getMessage());
        }
    }

    /**
     * Route a single option change to the V2 engine.
     * Call from Options setters when V2 is active.
     *
     * @param key   the Options property key (matches options.properties names)
     * @param value the new value (Boolean, Integer, or Float)
     */
    public static void onOptionChanged(String key, Object value) {
        if (!EngineBridge.isV2Active()) return;

        try {
            switch (key) {
                // display
                case "vsync" -> nativeSetVsync((Boolean) value, false);
                case "maxFps" -> nativeSetMaxFps((Integer) value, false);
                case "inactivityFpsLimit" -> nativeSetInactivityFpsLimit((Integer) value, false);
                case "outputScale2x" -> nativeSetOutputScale2x((Boolean) value, false);

                // upscaler
                case "upscalerMode" -> nativeSetUpscalerMode((Integer) value, false);
                case "upscalerQuality", "dlssQuality" -> nativeSetUpscalerQuality((Integer) value, false);
                case "upscalerResOverride" -> nativeSetUpscalerResOverride((Integer) value, false);

                // rayTracing
                case "rayBounces" -> nativeSetRayBounces((Integer) value, false);
                case "simplifiedIndirect" -> nativeSetSimplifiedIndirect((Boolean) value, false);
                case "serEnabled" -> nativeSetSerEnabled((Boolean) value, false);
                case "serHintsEnabled" -> nativeSetSerHintsEnabled((Boolean) value, false);
                case "noiseLOD" -> nativeSetNoiseLOD((Boolean) value, false);
                case "multiScatterGGX" -> nativeSetMultiScatterGGX((Boolean) value, false);
                case "eonDiffuse" -> nativeSetEonDiffuse((Boolean) value, false);
                case "greedyMeshingEnabled" -> nativeSetGreedyMeshingEnabled((Boolean) value, false);
                case "ommEnabled" -> nativeSetOmmEnabled((Boolean) value, false);
                case "ommBakerLevel" -> nativeSetOmmBakerLevel((Integer) value, false);
                case "areaLightsEnabled" -> nativeSetAreaLightsEnabled((Boolean) value, false);
                case "shadowSoftnessPercent" -> nativeSetShadowSoftness((Integer) value / 100.0f, false);
                case "restirEnabled" -> nativeSetRestirEnabled((Boolean) value, false);
                case "restirBounceEnabled" -> nativeSetRestirBounceEnabled((Boolean) value, false);
                case "restirSimplifiedBRDF" -> nativeSetRestirSimplifiedBRDF((Boolean) value, false);
                case "restirCandidates" -> nativeSetRestirCandidates((Integer) value, false);
                case "restirTemporalMClamp" -> nativeSetRestirTemporalMClamp((Integer) value, false);
                case "restirWClamp" -> nativeSetRestirWClamp((Integer) value, false);
                case "restirSpatialTaps" -> nativeSetRestirSpatialTaps((Integer) value, false);
                case "restirSpatialRadius" -> nativeSetRestirSpatialRadius((Integer) value, false);
                case "pomEnabled" -> nativeSetPomEnabled((Boolean) value, false);
                case "pomHeightScalePercent" -> nativeSetPomHeightScale((Integer) value / 100.0f, false);
                case "pomSteps" -> nativeSetPomSteps((Integer) value, false);
                case "pomRefinement" -> nativeSetPomRefinement((Integer) value, false);
                case "pomFadeDistance" -> nativeSetPomFadeDistance(((Integer) value).floatValue(), false);
                case "sharcEnabled" -> nativeSetSharcEnabled((Boolean) value, false);
                case "sharcSceneScaleTenths" -> nativeSetSharcSceneScale((Integer) value / 10.0f, false);
                case "sharcRoughnessThresholdPercent" -> nativeSetSharcRoughnessThreshold((Integer) value / 100.0f, false);

                // exposure (raw ints — C++ does /10 or /100)
                case "exposureCompensation" -> nativeSetExposureCompensation((Integer) value, false);
                case "manualExposureEnabled" -> nativeSetManualExposureEnabled((Boolean) value, false);
                case "manualExposureEV100Tenths" -> nativeSetManualExposure(
                        Options.ev100ToLinearExposure((Integer) value), false);
                case "brightAdaptSpeedTenths" -> nativeSetBrightAdaptSpeed((Integer) value, false);
                case "darkAdaptSpeedTenths" -> nativeSetDarkAdaptSpeed((Integer) value, false);
                case "sceneChangeThresholdTenths" -> nativeSetSceneChangeThreshold((Integer) value, false);
                case "centerWeightPercent" -> nativeSetCenterWeightStrength((Integer) value, false);
                case "highlightWeightPercent" -> nativeSetHighlightWeight((Integer) value, false);

                // toneMapping
                case "tonemappingMode" -> nativeSetTonemappingMode((Integer) value, false);
                case "psychoEnabled" -> nativeSetPsychoEnabled((Boolean) value, false);
                case "saturationPercent" -> nativeSetSaturation((Integer) value, false);
                case "saturationAdaptive" -> nativeSetSaturationAdaptive((Boolean) value, false);
                case "LwhiteTenths" -> nativeSetLwhite((Integer) value, false);
                case "colorExpansionPercent" -> nativeSetColorExpansion((Integer) value, false);
                case "sharpenerMode" -> nativeSetSharpenerMode((Integer) value, false);
                case "casSharpnessPercent" -> nativeSetCasSharpness((Integer) value, false);
                case "psychoPeakSDRTenths" -> nativeSetPsychoPeakSDR((Integer) value, false);

                // chunks
                case "chunkBuildingBatchSize" -> nativeSetChunkBuildingBatchSize((Integer) value, false);
                case "chunkBuildingTotalBatches" -> nativeSetChunkBuildingTotalBatches((Integer) value, false);
                case "chunkCullDistance" -> nativeSetChunkCullDistance(((Integer) value).floatValue(), false);
                case "chunkLodDistance" -> nativeSetChunkLodDistance(((Integer) value).floatValue(), false);

                // debug
                case "diagLevel" -> nativeSetDiagLevel((Integer) value, false);
                case "diagFlags" -> nativeSetDiagFlags((Integer) value, false);
                case "validationLayers" -> nativeSetValidationLayers((Boolean) value, false);

                // frameGen
                case "frameGenMode" -> nativeSetFrameGenEnabled((Integer) value != 0, false);
                case "frameGenMultiplier" -> nativeSetFrameGenMultiplier((Integer) value, false);

                // reflex
                case "reflexEnabled" -> nativeSetReflexEnabled((Boolean) value, false);
                case "reflexBoost" -> nativeSetReflexBoost((Boolean) value, false);

                // offlineAccum — offlineState drives enabled; aperture is computed
                case "offlineState" -> nativeSetOfflineAccumEnabled((Integer) value > 0, false);
                case "offlineBounces" -> nativeSetOfflineBounces((Integer) value, false);
                case "offlineAperture" -> nativeSetOfflineAperture((Float) value, false);
                case "offlineFocalDistance" -> nativeSetOfflineFocalDistance((Float) value, false);
                case "fStop", "focalLengthMM" -> nativeSetOfflineAperture(Options.computeApertureRadius(), false);

                // displacement
                case "displacementQuality" -> nativeSetDisplacementQuality((Integer) value, false);
                case "tessMaxLevel" -> nativeSetTessMaxLevel((Integer) value, false);
                case "tessNearDist" -> nativeSetTessNearDist(((Integer) value).floatValue(), false);
                case "tessMidDist" -> nativeSetTessMidDist(((Integer) value).floatValue(), false);
                case "tessFarDist" -> nativeSetTessFarDist(((Integer) value).floatValue(), false);

                // Unrecognized key — no-op (V1-only or not yet wired)
                default -> {}
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println("[Radiance] ConfigBridge: JNI link error for key '" + key + "' — " + e.getMessage());
        }
    }
}
