package com.radiance.client.materiallab;

import com.radiance.client.autopbr.AutoPbrTextureCatalog;
import com.radiance.client.autopbr.AutoPbrTextureRules;
import com.radiance.client.autopbr.AutoPbrValue;
import com.radiance.client.proxy.vulkan.TextureArrayBridge;
import com.radiance.client.texture.TextureTracker;
import com.radiance.mixin_related.extensions.vulkan_render_integration.INativeImageExt;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MaterialRecipeCompiler {
    private static final Logger LOGGER = LoggerFactory.getLogger("MaterialLab");

    private MaterialRecipeCompiler() {
    }

    public static MaterialBakePlan evaluate(int spriteId, MaterialRecipe recipe) {
        int size = AutoPbrTextureCatalog.spriteSize(spriteId);
        if (!AutoPbrTextureCatalog.validSpriteId(spriteId) || size <= 0) {
            return MaterialBakePlan.failed(Math.max(16, size), "invalid sprite");
        }
        MaterialRecipe r = recipe == null ? MaterialRecipe.defaults() : recipe;
        AutoPbrValue.Surface surface = new AutoPbrValue.Surface();
        surface.roughness = roughness(spriteId, r, size);
        surface.heightMap = height(spriteId, r, size);
        surface.normal = normal(spriteId, surface.heightMap, r, size);
        surface.aoMap = ao(spriteId, surface.heightMap, r, size);
        surface.sssMap = sss(spriteId, surface.heightMap, r, size);
        surface.emissionMap = emission(spriteId, r, size);
        surface.metallicMap = metallic(spriteId, r, size);
        surface.f0Map = constant(size, r.f0Override ? r.f0 : 0.04f);
        surface.roughnessOverride = roughnessTouched(r);
        surface.heightOverride = heightTouched(r);
        surface.normalOverride = normalTouched(r);
        surface.aoOverride = aoTouched(r);
        surface.sssOverride = sssTouched(r);
        surface.emissionOverride = r.emissionOverride && !"none".equals(r.emissionMode);
        surface.metallicOverride = r.metallicOverride;
        surface.f0Override = r.f0Override;
        surface.conductorF0RgbOverride = r.conductorF0RgbOverride;
        surface.iorOverride = r.iorOverride || r.transmissionOverride;
        surface.transmissionOverride = r.transmissionOverride && r.transmission > 0.0001f;
        surface.metallic = MathHelper.clamp(r.metallic, 0.0f, 1.0f);
        surface.labPbrMetalCode = r.labPbrMetalCode;
        surface.f0 = MathHelper.clamp(r.f0, 0.0f, 0.99f);
        surface.conductorF0R = MathHelper.clamp(r.conductorF0R, 0.0f, 0.99f);
        surface.conductorF0G = MathHelper.clamp(r.conductorF0G, 0.0f, 0.99f);
        surface.conductorF0B = MathHelper.clamp(r.conductorF0B, 0.0f, 0.99f);
        surface.ior = MathHelper.clamp(r.ior, 1.0f, 3.0f);
        surface.transmission = MathHelper.clamp(r.transmission, 0.0f, 1.0f);
        surface.absorptionR = MathHelper.clamp(r.absorptionR, 0.0f, 1.0f);
        surface.absorptionG = MathHelper.clamp(r.absorptionG, 0.0f, 1.0f);
        surface.absorptionB = MathHelper.clamp(r.absorptionB, 0.0f, 1.0f);
        surface.absorptionDistance = Math.max(0.01f, r.absorptionDistance);
        surface.refractionRoughness = MathHelper.clamp(r.refractionRoughness, 0.0f, 1.0f);
        surface.thicknessAmount = thicknessAmount(spriteId, r, size);
        surface.textureRuleModeFlags = modeFlags(r);
        surface.diffuseModelOverride = diffuseModelMode(r) != 0;
        surface.emissionTintR = MathHelper.clamp(r.emissionTintR, 0.0f, 1.0f);
        surface.emissionTintG = MathHelper.clamp(r.emissionTintG, 0.0f, 1.0f);
        surface.emissionTintB = MathHelper.clamp(r.emissionTintB, 0.0f, 1.0f);
        surface.emissionNits = Math.max(0.0f, r.emissionNits);
        surface.emission = MathHelper.clamp(r.emissionGain, 0.0f, 1.0f);
        surface.ao = MathHelper.clamp(r.aoStrength, 0.0f, 1.0f);
        surface.normalStrength = MathHelper.clamp(r.normalStrength, 0.0f, 4.0f);
        surface.anisotropic = MathHelper.clamp(r.anisotropic, 0.0f, 1.0f);
        surface.anisotropicRotation = MathHelper.clamp(r.anisotropicRotation, 0.0f, 1.0f);
        surface.sheenWeight = MathHelper.clamp(r.sheenWeight, 0.0f, 1.0f);
        surface.sheenTint = MathHelper.clamp(r.sheenTint, 0.0f, 1.0f);
        surface.sheenRoughness = MathHelper.clamp(r.sheenRoughness, 0.0f, 1.0f);
        surface.subSurfaceRadius = MathHelper.clamp(r.porositySssRadius, 0.0f, 1.0f);
        surface.subSurfaceThickness = MathHelper.clamp(r.porositySssThickness, 0.0f, 1.0f);
        surface.subSurfaceTintR = MathHelper.clamp(r.porositySssTintR, 0.0f, 1.0f);
        surface.subSurfaceTintG = MathHelper.clamp(r.porositySssTintG, 0.0f, 1.0f);
        surface.subSurfaceTintB = MathHelper.clamp(r.porositySssTintB, 0.0f, 1.0f);
        surface.coatWeight = MathHelper.clamp(r.coatWeight, 0.0f, 1.0f);
        surface.coatRoughness = MathHelper.clamp(r.coatRoughness, 0.0f, 1.0f);
        surface.coatIor = MathHelper.clamp(r.coatIor, 1.0f, 3.0f);
        surface.coatTintR = MathHelper.clamp(r.coatTintR, 0.0f, 1.0f);
        surface.coatTintG = MathHelper.clamp(r.coatTintG, 0.0f, 1.0f);
        surface.coatTintB = MathHelper.clamp(r.coatTintB, 0.0f, 1.0f);
        surface.coatMask = MathHelper.clamp(r.coatMask, 0.0f, 1.0f);
        surface.uvScaleU = MathHelper.clamp(r.uvScale, 0.01f, 16.0f);
        surface.uvScaleV = MathHelper.clamp(r.uvScale, 0.01f, 16.0f);
        surface.uvOffsetU = r.uvOffset;
        surface.uvOffsetV = r.uvOffset;
        surface.filterRadius = MathHelper.clamp(r.filterRadius, 0.0f, 16.0f);
        surface.mipBias = MathHelper.clamp(r.mipBias, -8.0f, 8.0f);
        surface.displacementScale = MathHelper.clamp(r.displacementScale, 0.0f, 4.0f);
        surface.anisotropicOverride = r.anisotropicOverride;
        surface.sheenOverride = r.sheenOverride;
        surface.coatOverride = r.coatOverride;
        surface.displacementOverride = r.displacementOverride;
        surface.absorptionOverride = r.transmissionOverride && (r.absorptionR > 0.0f || r.absorptionG > 0.0f
            || r.absorptionB > 0.0f || differs(r.absorptionDistance, 16.0f));
        surface.thicknessOverride = r.transmissionOverride && (differs(surface.thicknessAmount, 0.5f)
            || !"flat".equals(safe(r.thicknessSource, "flat"))
            || differs(r.thicknessMin, 0.0f) || differs(r.thicknessMax, 1.0f)
            || differs(r.thicknessGamma, 1.0f));
        surface.volumeModeOverride = r.transmissionOverride && !"solid_volume".equals(safe(r.transmissionVolumeMode, "solid_volume"));
        surface.refractionRoughnessOverride = r.transmissionOverride && r.refractionRoughness > 0.0001f;
        surface.emissionPhysicalOverride = r.emissionOverride && (r.emissionNits > 0.0f
            || differs(r.emissionTintR, 1.0f) || differs(r.emissionTintG, 0.75f) || differs(r.emissionTintB, 0.45f));
        surface.anisotropicRotationOverride = r.anisotropicOverride && r.anisotropic > 0.0f;
        surface.sheenRoughnessOverride = r.sheenOverride && differs(r.sheenRoughness, 0.5f);
        surface.subSurfaceExtOverride = r.porosityOverride && "sss".equals(safe(r.porosityMode, "preserve"))
            && (differs(r.porositySssRadius, 0.0f)
            || differs(r.porositySssThickness, 0.5f)
            || differs(r.porositySssTintR, 1.0f)
            || differs(r.porositySssTintG, 0.75f)
            || differs(r.porositySssTintB, 0.55f));
        surface.coatExtOverride = r.coatOverride && (differs(r.coatIor, 1.5f)
            || differs(r.coatTintR, 1.0f) || differs(r.coatTintG, 1.0f) || differs(r.coatTintB, 1.0f)
            || differs(r.coatMask, 1.0f)
            || !"flat".equals(safe(r.coatMaskSource, "flat")));
        surface.uvTransformOverride = differs(r.uvScale, 1.0f) || differs(r.uvOffset, 0.0f);
        surface.filterRadiusOverride = r.filterRadius > 0.0001f;
        surface.mipBiasOverride = differs(r.mipBias, 0.0f);
        List<String> diagnostics = new ArrayList<>();
        if (r.emissionOverride && "none".equals(r.emissionMode)) {
            diagnostics.add("emission override uses no emission source");
        }
        MaterialPreviewData preview = buildPreview(spriteId, r, size, surface);
        return new MaterialBakePlan(true, size, surface, diagnostics, preview);
    }

    private static MaterialPreviewData buildPreview(int spriteId, MaterialRecipe recipe, int size,
                                                    AutoPbrValue.Surface surface) {
        MaterialPreviewData.Builder builder = MaterialPreviewData.builder(size);
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        builder.color("albedo", imageColor(albedo, size));
        builder.scalar("alpha", imageAlpha(albedo, size));
        builder.scalar("coverage", imageCoverage(albedo, size));
        builder.scalar("luminance", imageLuminance(albedo, size));

        builder.scalar("pack_smoothness", imageChannel(spec, size, ChannelComponent.RED));
        builder.scalar("pack_roughness", packRoughness(spriteId, size));
        builder.scalar("pack_sg", imageChannel(spec, size, ChannelComponent.GREEN));
        builder.scalar("pack_sb", packSss(spriteId, size));
        builder.scalar("pack_sa", packEmission(spriteId, size));
        builder.scalar("final_smoothness", finalSmoothness(surface.roughness, size));
        builder.scalar("final_sg", finalSpecularChannel(spriteId, surface, size, SpecularChannel.F0_METAL));
        builder.scalar("final_sb", finalSpecularChannel(spriteId, surface, size, SpecularChannel.SSS));
        builder.scalar("final_sa", finalSpecularChannel(spriteId, surface, size, SpecularChannel.EMISSION));

        builder.scalar("generated_roughness", generatedRoughness(spriteId, recipe, size));
        builder.scalar("flat_roughness", constant(size, recipe.roughness));
        builder.scalar("final_roughness", surface.roughness);

        builder.scalar("metal_mask", surface.metallicMap);
        builder.scalar("f0_map", surface.f0Map);
        builder.color("brdf_swatch", brdfSwatch(surface, size));

        builder.scalar("pack_height", packHeight(spriteId, size));
        builder.scalar("generated_height", generatedHeight(spriteId, recipe, size));
        builder.scalar("flat_height", constant(size, recipe.heightFlat));
        builder.scalar("final_height", surface.heightMap);
        builder.scalar("relief", reliefPreview(surface.heightMap, size));

        builder.color("pack_normal", packNormal(spriteId, size));
        builder.color("generated_normal", normalFromHeight(surface.heightMap, recipe, size,
            recipe.normalGeneratedStrength));
        builder.color("detail_normal", normalFromHeight(surface.heightMap, recipe, size,
            recipe.detailNormalStrength));
        builder.color("final_normal", surface.normal);

        builder.scalar("pack_ao", packAo(spriteId, size));
        builder.scalar("generated_sss", generatedSssPreview(spriteId, surface.heightMap, recipe, size));
        builder.scalar("sss_rule", constant(size, surface.subSurfaceExtOverride
            ? (surface.subSurfaceRadius + surface.subSurfaceThickness) * 0.5f : 0.0f));
        builder.scalar("final_sss", surface.sssMap);

        builder.scalar("emission_source", emissionSource(spriteId, recipe, size));
        builder.scalar("emission_threshold", emissionThreshold(spriteId, recipe, size));
        builder.scalar("emission_final", surface.emissionMap);

        builder.scalar("transmission", constant(size, surface.transmission));
        builder.scalar("thickness", constant(size, surface.thicknessAmount));
        builder.scalar("rule", constant(size, surface.transmissionOverride || surface.iorOverride ? 1.0f : 0.0f));
        builder.scalar("anisotropic", constant(size, surface.anisotropic));
        builder.scalar("coat", constant(size, surface.coatWeight));
        builder.scalar("sheen", constant(size, surface.sheenWeight));
        float samplerRule = Math.max(Math.max(
            surface.uvTransformOverride ? Math.abs(surface.uvScaleU - 1.0f) / 4.0f : 0.0f,
            surface.filterRadiusOverride ? surface.filterRadius / 16.0f : 0.0f), Math.max(
            surface.mipBiasOverride ? Math.abs(surface.mipBias) / 8.0f : 0.0f,
            surface.displacementOverride ? surface.displacementScale / 4.0f : 0.0f));
        builder.scalar("sampler", constant(size, samplerRule));
        return builder.build();
    }

    private static int[] imageColor(NativeImage image, int size) {
        int[] out = new int[size * size];
        java.util.Arrays.fill(out, 0xFF050505);
        if (image == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = sample(image, x, y, size);
            }
        }
        return out;
    }

    private static float[] imageAlpha(NativeImage image, int size) {
        float[] out = constant(size, 1.0f);
        if (image == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = ((sample(image, x, y, size) >>> 24) & 0xFF) / 255.0f;
            }
        }
        return out;
    }

    private static float[] imageCoverage(NativeImage image, int size) {
        float[] alpha = imageAlpha(image, size);
        for (int i = 0; i < alpha.length; i++) {
            alpha[i] = alpha[i] > 0.0f ? 1.0f : 0.0f;
        }
        return alpha;
    }

    private static float[] imageLuminance(NativeImage image, int size) {
        float[] out = constant(size, 0.0f);
        if (image == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = luminance(sample(image, x, y, size));
            }
        }
        return out;
    }

    private static float[] imageChannel(NativeImage image, int size, ChannelComponent component) {
        float[] out = constant(size, 0.0f);
        if (image == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = sample(image, x, y, size);
                int value = switch (component) {
                    case ALPHA -> (argb >>> 24) & 0xFF;
                    case RED -> (argb >>> 16) & 0xFF;
                    case GREEN -> (argb >>> 8) & 0xFF;
                    case BLUE -> argb & 0xFF;
                };
                out[y * size + x] = value / 255.0f;
            }
        }
        return out;
    }

    private static float[] packEmission(int spriteId, int size) {
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        float[] out = constant(size, 0.0f);
        if (spec == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int a = (sample(spec, x, y, size) >>> 24) & 0xFF;
                out[y * size + x] = a >= 255 ? 0.0f : a / 254.0f;
            }
        }
        return out;
    }

    private static float[] finalSmoothness(float[] roughness, int size) {
        float[] out = constant(size, 0.0f);
        for (int i = 0; i < out.length; i++) {
            float value = value(roughness, i, 1.0f);
            out[i] = 1.0f - (float) Math.sqrt(MathHelper.clamp(value, 0.02f, 0.98f));
        }
        return out;
    }

    private static float[] finalSpecularChannel(int spriteId, AutoPbrValue.Surface surface, int size,
                                                SpecularChannel channel) {
        NativeImage existing = AutoPbrTextureCatalog.specular(spriteId);
        float[] out = constant(size, 0.0f);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int src = existing == null ? 0 : sample(existing, x, y, size);
                int srcA = (src >>> 24) & 0xFF;
                int srcF0 = (src >>> 8) & 0xFF;
                int srcSss = src & 0xFF;
                int byteValue = switch (channel) {
                    case EMISSION -> surface.emissionOverride
                        ? MathHelper.clamp(Math.round(value(surface.emissionMap, index, 0.0f) * 254.0f), 0, 254)
                        : (existing == null ? 255 : srcA);
                    case F0_METAL -> {
                        int f0 = MathHelper.clamp(Math.round(value(surface.f0Map, index, 0.04f) * 255.0f), 0, 229);
                        int metallic = MathHelper.clamp(Math.round(value(surface.metallicMap, index, 0.0f)), 0, 1);
                        int metalCode = surface.labPbrMetalCode >= 230 && surface.labPbrMetalCode <= 255
                            ? surface.labPbrMetalCode
                            : 238;
                        yield (surface.f0Override || surface.metallicOverride)
                            ? (surface.metallicOverride && metallic > 0 ? metalCode : f0)
                            : (existing == null ? 0 : srcF0);
                    }
                    case SSS -> surface.sssOverride
                        ? MathHelper.clamp(Math.round(value(surface.sssMap, index, 0.0f) * 255.0f), 0, 255)
                        : srcSss;
                };
                out[index] = byteValue / 255.0f;
            }
        }
        return out;
    }

    private static int[] brdfSwatch(AutoPbrValue.Surface surface, int size) {
        int[] out = new int[size * size];
        int r;
        int g;
        int b;
        if (surface.conductorF0RgbOverride) {
            r = MathHelper.clamp(Math.round(surface.conductorF0R * 255.0f), 0, 255);
            g = MathHelper.clamp(Math.round(surface.conductorF0G * 255.0f), 0, 255);
            b = MathHelper.clamp(Math.round(surface.conductorF0B * 255.0f), 0, 255);
        } else {
            int v = MathHelper.clamp(Math.round(Math.max(surface.f0, 0.04f) * 255.0f), 12, 255);
            r = v;
            g = v;
            b = v;
        }
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;
        java.util.Arrays.fill(out, color);
        return out;
    }

    private static float[] reliefPreview(float[] height, int size) {
        float[] out = constant(size, 0.0f);
        if (height == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = edgeMagnitudeFromValues(height, x, y, size);
            }
        }
        return out;
    }

    private static float[] generatedSssPreview(int spriteId, float[] height, MaterialRecipe recipe, int size) {
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        float[] out = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int argb = sample(albedo, x, y, size);
                float lum = luminance(argb);
                float cavity = height == null ? edgeMagnitude(albedo, x, y, size)
                    : MathHelper.clamp(1.0f - height[index] + edgeMagnitudeFromValues(height, x, y, size),
                        0.0f, 1.0f);
                float value = switch (safe(recipe.porositySource, "luminance")) {
                    case "inverse_luminance" -> 1.0f - lum;
                    case "saturation" -> saturation(argb);
                    case "cavity" -> cavity;
                    case "height_influence" -> height == null ? lum : height[index];
                    default -> lum;
                };
                out[index] = recipe.porosityInvert ? 1.0f - value : value;
            }
        }
        return out;
    }

    private static float[] emissionSource(int spriteId, MaterialRecipe recipe, int size) {
        float[] out = constant(size, 0.0f);
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int argb = sample(albedo, x, y, size);
                out[index] = switch (safe(recipe.emissionMode, "none")) {
                    case "spec_alpha" -> {
                        if (spec == null) yield 0.0f;
                        int a = (sample(spec, x, y, size) >>> 24) & 0xFF;
                        yield a >= 255 ? 0.0f : a / 254.0f;
                    }
                    case "albedo_red" -> ((argb >>> 16) & 0xFF) / 255.0f;
                    case "albedo_green" -> ((argb >>> 8) & 0xFF) / 255.0f;
                    case "albedo_blue" -> (argb & 0xFF) / 255.0f;
                    case "hue" -> hue(argb);
                    case "saturation" -> saturation(argb);
                    case "value" -> valueChannel(argb);
                    case "luminance", "luminance_mask" -> luminance(argb);
                    case "whole_texture", "flat" -> 1.0f;
                    default -> 0.0f;
                };
            }
        }
        return out;
    }

    private static float[] emissionThreshold(int spriteId, MaterialRecipe recipe, int size) {
        float[] source = emissionSource(spriteId, recipe, size);
        float[] out = new float[source.length];
        for (int i = 0; i < source.length; i++) {
            float value = source[i];
            if (!"whole_texture".equals(recipe.emissionMode) && !"flat".equals(recipe.emissionMode)
                && !"spec_alpha".equals(recipe.emissionMode)) {
                float low = Math.min(recipe.emissionThresholdLow, recipe.emissionThreshold)
                    - recipe.emissionSoftness * 0.5f;
                float high = Math.max(recipe.emissionThresholdHigh, recipe.emissionThreshold)
                    + recipe.emissionSoftness * 0.5f;
                value = smoothstep(low, high, value);
            }
            out[i] = recipe.emissionInvert ? 1.0f - value : value;
        }
        return out;
    }

    private record NormalUpload(boolean layerOk, boolean heightOk) {
    }

    private enum ChannelComponent {
        ALPHA,
        RED,
        GREEN,
        BLUE
    }

    private enum SpecularChannel {
        EMISSION,
        F0_METAL,
        SSS
    }

    public static MaterialUploadResult compileAndUpload(int spriteId, MaterialRecipe recipe) {
        MaterialBakePlan plan = evaluate(spriteId, recipe);
        if (!plan.ok || plan.surface == null || spriteId < 0 || plan.size <= 0) {
            return MaterialUploadResult.bakeFailed(plan);
        }
        List<String> diagnostics = new ArrayList<>(plan.diagnostics);
        boolean specOk = true;
        boolean normalOk = true;
        boolean heightOk = true;
        boolean rulesOk = false;
        try {
            AutoPbrValue.Surface surface = plan.surface;
            int size = plan.size;
            long gen = TextureArrayBridge.getActiveTextureGeneration();
            boolean specTouched = surface.roughnessOverride || surface.f0Override || surface.sssOverride
                || surface.metallicOverride || surface.emissionOverride;
            boolean normalTouched = surface.normalOverride || surface.aoOverride || surface.heightOverride;
            if (specTouched) {
                specOk = uploadCustomSpecular(spriteId, surface, size, gen, diagnostics);
            } else {
                specOk = restoreSpecularBaseline(spriteId, size, gen, diagnostics);
            }
            if (normalTouched) {
                NormalUpload normalUpload = uploadCustomNormal(spriteId, surface, size, gen, diagnostics);
                normalOk = normalUpload.layerOk();
                heightOk = normalUpload.heightOk();
            } else {
                NormalUpload normalUpload = restoreNormalBaseline(spriteId, size, gen, diagnostics);
                normalOk = normalUpload.layerOk();
                heightOk = normalUpload.heightOk();
            }
            rulesOk = AutoPbrTextureRules.update(spriteId, plan);
            if (!rulesOk) diagnostics.add("texture rules upload failed or pending");
        } catch (UnsatisfiedLinkError e) {
            LOGGER.debug("[MaterialLab] Native live upload skipped", e);
            specOk = false;
            normalOk = false;
            heightOk = false;
            rulesOk = false;
            diagnostics.add("native link unavailable");
        } catch (Exception e) {
            LOGGER.warn("[MaterialLab] Recipe compile/upload failed for sprite {}", spriteId, e);
            specOk = false;
            normalOk = false;
            heightOk = false;
            rulesOk = false;
            diagnostics.add("upload exception: " + e.getClass().getSimpleName());
        }
        return new MaterialUploadResult(plan, specOk, normalOk, heightOk, rulesOk, diagnostics);
    }

    private static boolean uploadCustomSpecular(int spriteId, AutoPbrValue.Surface surface, int size,
                                                long generation, List<String> diagnostics) {
        int bytes = size * size * 4;
        try (NativeImage spec = new NativeImage(NativeImage.Format.RGBA, size, size, false)) {
            bakeSpecular(spriteId, surface, spec);
            boolean ok = TextureArrayBridge.nativeUpdateSpecularLayer(spriteId,
                ((INativeImageExt) (Object) spec).neoVoxelRT$getPointer(), bytes, generation);
            if (!ok) {
                diagnostics.add("specular layer upload failed");
                return false;
            }
            TextureTracker.spriteSpecularSource[spriteId] = TextureTracker.SOURCE_USER_CUSTOM;
            cacheImage(TextureTracker.spriteSpecularCache, spriteId, spec);
            return true;
        }
    }

    private static NormalUpload uploadCustomNormal(int spriteId, AutoPbrValue.Surface surface, int size,
                                                   long generation, List<String> diagnostics) {
        int bytes = size * size * 4;
        try (NativeImage normal = new NativeImage(NativeImage.Format.RGBA, size, size, false)) {
            bakeNormal(spriteId, surface, normal);
            boolean layerOk = TextureArrayBridge.nativeUpdateNormalLayer(spriteId,
                ((INativeImageExt) (Object) normal).neoVoxelRT$getPointer(), bytes, generation);
            if (!layerOk) {
                diagnostics.add("normal layer upload failed");
                return new NormalUpload(false, false);
            }
            TextureTracker.spriteNormalSource[spriteId] = TextureTracker.SOURCE_USER_CUSTOM;
            cacheImage(TextureTracker.spriteNormalCache, spriteId, normal);
            boolean heightOk = refreshHeightMetadata(spriteId, normal, generation, diagnostics);
            return new NormalUpload(true, heightOk);
        }
    }

    private static boolean restoreSpecularBaseline(int spriteId, int size, long generation, List<String> diagnostics) {
        if (!validTextureIndex(spriteId) || TextureTracker.spriteSpecularSource[spriteId] != TextureTracker.SOURCE_USER_CUSTOM) {
            return true;
        }
        NativeImage baseline = TextureTracker.spriteBaselineSpecularCache.get(spriteId);
        try (NativeImage fallback = baseline == null ? defaultSpecular(size) : null) {
            NativeImage source = baseline == null ? fallback : baseline;
            boolean ok = TextureArrayBridge.nativeUpdateSpecularLayer(spriteId,
                ((INativeImageExt) (Object) source).neoVoxelRT$getPointer(), size * size * 4, generation);
            if (!ok) {
                diagnostics.add("specular baseline restore failed");
                return false;
            }
            TextureTracker.spriteSpecularSource[spriteId] = TextureTracker.spriteBaselineSpecularSource[spriteId];
            if (baseline == null) {
                removeCachedImage(TextureTracker.spriteSpecularCache, spriteId);
            } else {
                cacheImage(TextureTracker.spriteSpecularCache, spriteId, baseline);
            }
            return true;
        }
    }

    private static NormalUpload restoreNormalBaseline(int spriteId, int size, long generation, List<String> diagnostics) {
        if (!validTextureIndex(spriteId) || TextureTracker.spriteNormalSource[spriteId] != TextureTracker.SOURCE_USER_CUSTOM) {
            return new NormalUpload(true, true);
        }
        NativeImage baseline = TextureTracker.spriteBaselineNormalCache.get(spriteId);
        try (NativeImage fallback = baseline == null ? defaultNormal(size) : null) {
            NativeImage source = baseline == null ? fallback : baseline;
            boolean ok = TextureArrayBridge.nativeUpdateNormalLayer(spriteId,
                ((INativeImageExt) (Object) source).neoVoxelRT$getPointer(), size * size * 4, generation);
            if (!ok) {
                diagnostics.add("normal baseline restore failed");
                return new NormalUpload(false, false);
            }
            TextureTracker.spriteNormalSource[spriteId] = TextureTracker.spriteBaselineNormalSource[spriteId];
            if (baseline == null) {
                removeCachedImage(TextureTracker.spriteNormalCache, spriteId);
            } else {
                cacheImage(TextureTracker.spriteNormalCache, spriteId, baseline);
            }
            boolean heightOk = refreshHeightMetadata(spriteId, source, generation, diagnostics);
            return new NormalUpload(true, heightOk);
        }
    }

    private static NativeImage defaultSpecular(int size) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setColorArgb(x, y, 0);
            }
        }
        return image;
    }

    private static NativeImage defaultNormal(int size) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, size, size, false);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                image.setColorArgb(x, y, 0xFF8080FF);
            }
        }
        return image;
    }

    private static boolean validTextureIndex(int spriteId) {
        return spriteId >= 0 && spriteId < TextureTracker.MAX_TEXTURES;
    }

    private static float[] roughness(int spriteId, MaterialRecipe recipe, int size) {
        float[] pack = packRoughness(spriteId, size);
        float[] generated = generatedRoughness(spriteId, recipe, size);
        float[] flat = constant(size, recipe.roughness);
        float[] out = new float[size * size];
        String source = safe(recipe.roughnessSource, "pack_generated_flat");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                float value = switch (source) {
                    case "pack" -> pack[index];
                    case "generated" -> generated[index];
                    case "flat" -> flat[index];
                    default -> pack[index] >= 0.0f ? pack[index] : generated[index];
                };
                if ("pack_generated_flat".equals(source)) {
                    value = mix(value, generated[index], MathHelper.clamp(recipe.roughnessGeneratedBlend, 0.0f, 1.0f));
                    value = mix(value, flat[index], MathHelper.clamp(recipe.roughnessFlatBlend, 0.0f, 1.0f));
                }
                if (recipe.roughnessInvert) value = 1.0f - value;
                value = levels(value, recipe.roughnessBlackPoint, recipe.roughnessMidpoint, recipe.roughnessWhitePoint);
                value = curve(value, recipe.roughnessGamma, recipe.roughnessContrast);
                value = mix(value, MathHelper.clamp(value + edgeMagnitude(AutoPbrTextureCatalog.albedo(spriteId), x, y, size)
                    * recipe.roughnessEdgeRoughness, 0.0f, 1.0f), recipe.roughnessEdgeRoughness);
                float oxide = oxideMask(spriteId, recipe, x, y, size, 1.0f);
                value = MathHelper.clamp(value + oxide * MathHelper.clamp(recipe.oxideRoughnessInfluence, 0.0f, 1.0f) * 0.45f, 0.0f, 1.0f);
                value = MathHelper.clamp(value + recipe.roughnessWear * 0.10f - recipe.roughnessPolish * 0.18f
                    - recipe.roughnessWetness * 0.25f, 0.0f, 1.0f);
                out[index] = remap(value, recipe.roughnessMin, recipe.roughnessMax);
            }
        }
        out = cleanup(out, size, recipe.roughnessBlurRadius, recipe.roughnessSharpen,
            recipe.roughnessDenoise, 0, 0, 0);
        return smooth(out, size, recipe.roughnessSmoothing);
    }

    private static float[] height(int spriteId, MaterialRecipe recipe, int size) {
        float[] pack = packHeight(spriteId, size);
        float[] generated = generatedHeight(spriteId, recipe, size);
        float[] flat = constant(size, recipe.heightFlat);
        float gamma = MathHelper.clamp(recipe.heightGamma, 0.2f, 4.0f);
        float scale = MathHelper.clamp(recipe.heightScale, 0.0f, 1.0f);
        float[] out = new float[size * size];
        String source = safe(recipe.heightSource, "pack_generated_flat");
        for (int i = 0; i < out.length; i++) {
            float value = switch (source) {
                case "pack" -> pack[i];
                case "generated" -> generated[i];
                case "flat" -> flat[i];
                default -> pack[i] >= 0.0f ? pack[i] : generated[i];
            };
            if ("pack_generated_flat".equals(source)) {
                value = mix(value, generated[i], MathHelper.clamp(recipe.heightGeneratedBlend, 0.0f, 1.0f));
                value = mix(value, flat[i], MathHelper.clamp(recipe.heightFlatBlend, 0.0f, 1.0f));
            }
            value = levels(value, recipe.heightBlackPoint, recipe.heightMidpoint, recipe.heightWhitePoint);
            value = (float) Math.pow(MathHelper.clamp(value, 0.0f, 1.0f), gamma);
            if (recipe.invertHeight) value = 1.0f - value;
            value = remap(value, recipe.heightMin, recipe.heightMax);
            value = MathHelper.clamp(value + recipe.heightOffset, 0.0f, 1.0f);
            out[i] = MathHelper.clamp(0.5f + (value - 0.5f) * scale, 0.0f, 1.0f);
        }
        out = cleanup(out, size, recipe.heightBlurRadius, recipe.heightSharpen,
            recipe.heightDenoise, recipe.heightDilate, recipe.heightErode, 0);
        return smooth(out, size, recipe.heightSmoothing);
    }

    private static int[] normal(int spriteId, float[] height, MaterialRecipe recipe, int size) {
        int[] pack = packNormal(spriteId, size);
        int[] generated = normalFromHeight(height, recipe, size, recipe.normalGeneratedStrength);
        int[] out = new int[size * size];
        String source = safe(recipe.normalSource, "pack_generated");
        float generatedBlend = MathHelper.clamp(recipe.generateNormal ? 1.0f : recipe.normalGeneratedBlend, 0.0f, 1.0f);
        if ("generated".equals(source)) generatedBlend = 1.0f;
        if ("detail_only".equals(source)) generatedBlend = 1.0f;
        if ("pack".equals(source)) generatedBlend = 0.0f;
        for (int i = 0; i < out.length; i++) {
            int base = strengthenNormal(pack[i], recipe.normalPackStrength);
            float blend = "replace".equals(recipe.normalCombineMode) ? (generatedBlend > 0.0f ? 1.0f : 0.0f) : generatedBlend;
            out[i] = blendNormal(base, generated[i], blend, shouldFlipGreen(recipe));
        }
        return smoothNormals(out, size, recipe.normalSmoothing);
    }

    private static int[] normalFromHeight(float[] height, MaterialRecipe recipe, int size, float normalStrength) {
        int[] out = new int[size * size];
        float strength = MathHelper.clamp(normalStrength * recipe.normalStrength
            * MathHelper.clamp(recipe.detailNormalStrength, 0.0f, 4.0f), 0.0f, 4.0f);
        int radius = MathHelper.clamp(recipe.normalGeneratorRadius, 1, 4);
        radius = MathHelper.clamp(Math.round(radius * MathHelper.clamp(recipe.normalDetailFrequency, 0.25f, 4.0f)), 1, 4);
        float kernelScale = "scharr".equals(safe(recipe.normalKernelMode, "sobel")) ? 1.35f : 1.0f;
        float sx = MathHelper.clamp(recipe.normalXStrength, 0.0f, 4.0f);
        float syScale = MathHelper.clamp(recipe.normalYStrength, 0.0f, 4.0f);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float left = height[y * size + Math.max(0, x - radius)];
                float right = height[y * size + Math.min(size - 1, x + radius)];
                float up = height[Math.max(0, y - radius) * size + x];
                float down = height[Math.min(size - 1, y + radius) * size + x];
                float nx = MathHelper.clamp((left - right) * strength * sx * kernelScale * 0.5f + 0.5f, 0.0f, 1.0f);
                float ny = MathHelper.clamp((up - down) * strength * syScale * kernelScale * 0.5f + 0.5f, 0.0f, 1.0f);
                out[y * size + x] = 0xFF000000
                    | (Math.round(nx * 255.0f) << 16)
                    | (Math.round(ny * 255.0f) << 8)
                    | 0x80;
            }
        }
        return out;
    }

    private static float[] ao(int spriteId, float[] height, MaterialRecipe recipe, int size) {
        float[] pack = packAo(spriteId, size);
        float[] generated = aoFromHeight(height, recipe, size);
        float[] flat = constant(size, recipe.aoFlat);
        float[] out = new float[size * size];
        String source = safe(recipe.aoSource, "pack_generated_flat");
        for (int i = 0; i < out.length; i++) {
            float value = switch (source) {
                case "pack" -> pack[i];
                case "generated" -> generated[i];
                case "flat" -> flat[i];
                default -> pack[i] >= 0.0f ? pack[i] : generated[i];
            };
            if ("pack_generated_flat".equals(source)) {
                value = mix(value, generated[i], MathHelper.clamp(recipe.generateAo ? 1.0f : recipe.aoGeneratedBlend, 0.0f, 1.0f));
                value = mix(value, flat[i], 1.0f - MathHelper.clamp(recipe.aoStrength, 0.0f, 1.0f));
            }
            if (recipe.aoInvert) value = 1.0f - value;
            out[i] = remap(value, recipe.aoMin, recipe.aoMax);
        }
        return out;
    }

    private static float[] aoFromHeight(float[] height, MaterialRecipe recipe, int size) {
        float[] out = new float[size * size];
        float strength = recipe.generateAo || recipe.aoOverride ? MathHelper.clamp(recipe.aoStrength, 0.0f, 1.0f) : 1.0f;
        for (int i = 0; i < out.length; i++) {
            out[i] = MathHelper.clamp(1.0f - (1.0f - height[i]) * 0.35f * strength, 0.0f, 1.0f);
        }
        return out;
    }

    private static float[] sss(int spriteId, float[] height, MaterialRecipe recipe, int size) {
        float[] pack = packSss(spriteId, size);
        if (!sssTouched(recipe)) return pack;
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        float[] generated = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int argb = sample(albedo, x, y, size);
                float lum = luminance(argb);
                float sat = saturation(argb);
                float cavity = height == null ? edgeMagnitude(albedo, x, y, size)
                    : MathHelper.clamp(1.0f - height[index] + edgeMagnitudeFromValues(height, x, y, size), 0.0f, 1.0f);
                float value = switch (safe(recipe.porositySource, "luminance")) {
                    case "inverse_luminance" -> 1.0f - lum;
                    case "saturation" -> sat;
                    case "cavity" -> cavity;
                    case "height_influence" -> height == null ? lum : height[index];
                    default -> lum;
                };
                value = levels(value, recipe.porosityBlackPoint, recipe.porosityMidpoint, recipe.porosityWhitePoint);
                if (recipe.porosityInvert) value = 1.0f - value;
                if ("sss".equals(safe(recipe.porosityMode, "preserve"))) {
                    float thickness = MathHelper.clamp(recipe.porositySssThickness, 0.0f, 1.0f);
                    float radius = MathHelper.clamp(recipe.porositySssRadius, 0.0f, 1.0f);
                    value = MathHelper.clamp(value * recipe.porositySssStrength
                        * mix(0.80f, 1.25f, thickness) * mix(1.0f, 1.15f, radius), 0.0f, 1.0f);
                } else {
                    value = MathHelper.clamp(value * recipe.porosityAmount
                        + cavity * recipe.porosityCavityInfluence
                        - recipe.porosityWetnessCoupling * 0.25f, 0.0f, 1.0f);
                }
                generated[index] = value;
            }
        }
        return generated;
    }

    private static float[] metallic(int spriteId, MaterialRecipe recipe, int size) {
        if (!recipe.metallicOverride) return constant(size, 0.0f);
        if ("flat".equals(safe(recipe.metalMaskSource, "flat")) || "radser_measured".equals(recipe.metalMode)
            || "labpbr_code".equals(recipe.metalMode)) {
            return applyOxideToMetallic(spriteId, recipe, size, constant(size, recipe.metallic));
        }
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        float[] out = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int argb = sample(albedo, x, y, size);
                float value = switch (safe(recipe.metalMaskSource, "flat")) {
                    case "pack_code" -> spec == null ? 0.0f : ((((sample(spec, x, y, size) >>> 8) & 0xFF) >= 230) ? 1.0f : 0.0f);
                    case "luminance" -> luminance(argb);
                    case "saturation" -> saturation(argb);
                    case "albedo_red" -> ((argb >>> 16) & 0xFF) / 255.0f;
                    case "albedo_green" -> ((argb >>> 8) & 0xFF) / 255.0f;
                    case "albedo_blue" -> (argb & 0xFF) / 255.0f;
                    case "edge_mask" -> edgeMagnitude(albedo, x, y, size);
                    default -> recipe.metallic;
                };
                value = smoothstep(recipe.metalMaskThreshold - recipe.metalMaskSoftness,
                    recipe.metalMaskThreshold + recipe.metalMaskSoftness, value);
                value = MathHelper.clamp(value * recipe.metallic, recipe.metalMaskClampMin, recipe.metalMaskClampMax);
                float oxide = oxideMask(spriteId, recipe, x, y, size, value);
                value = MathHelper.clamp(value * (1.0f - oxide), 0.0f, 1.0f);
                out[index] = value;
            }
        }
        return cleanup(out, size, 0, 0.0f, 0.0f, recipe.metalMaskDilate, recipe.metalMaskErode,
            recipe.metalMaskDespeckle);
    }

    private static float[] emission(int spriteId, MaterialRecipe recipe, int size) {
        float[] out = constant(size, 0.0f);
        if (!recipe.emissionOverride || "none".equals(recipe.emissionMode)) return out;
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                float value = 0.0f;
                int argb = sample(albedo, x, y, size);
                value = switch (safe(recipe.emissionMode, "none")) {
                    case "spec_alpha" -> {
                        if (spec == null) yield 0.0f;
                        int a = (sample(spec, x, y, size) >>> 24) & 0xFF;
                        yield a >= 255 ? 0.0f : a / 254.0f;
                    }
                    case "albedo_red" -> ((argb >>> 16) & 0xFF) / 255.0f;
                    case "albedo_green" -> ((argb >>> 8) & 0xFF) / 255.0f;
                    case "albedo_blue" -> (argb & 0xFF) / 255.0f;
                    case "hue" -> hue(argb);
                    case "saturation" -> saturation(argb);
                    case "value" -> valueChannel(argb);
                    case "luminance", "luminance_mask" -> luminance(argb);
                    case "whole_texture", "flat" -> 1.0f;
                    default -> 0.0f;
                };
                if (!"whole_texture".equals(recipe.emissionMode) && !"flat".equals(recipe.emissionMode)
                    && !"spec_alpha".equals(recipe.emissionMode)) {
                    float low = Math.min(recipe.emissionThresholdLow, recipe.emissionThreshold) - recipe.emissionSoftness * 0.5f;
                    float high = Math.max(recipe.emissionThresholdHigh, recipe.emissionThreshold) + recipe.emissionSoftness * 0.5f;
                    value = smoothstep(low, high, value);
                }
                if (recipe.emissionInvert) value = 1.0f - value;
                out[index] = MathHelper.clamp(value * recipe.emissionGain, 0.0f, 1.0f);
            }
        }
        return cleanup(out, size, recipe.emissionBlurRadius, 0.0f, 0.0f,
            recipe.emissionDilate, recipe.emissionErode, recipe.emissionDespeckle);
    }

    private static void bakeSpecular(int spriteId, AutoPbrValue.Surface surface, NativeImage out) {
        NativeImage existing = AutoPbrTextureCatalog.specular(spriteId);
        int size = out.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int src = existing == null ? 0 : sample(existing, x, y, size);
                int srcA = (src >>> 24) & 0xFF;
                int srcSmoothness = (src >>> 16) & 0xFF;
                int srcF0 = (src >>> 8) & 0xFF;
                int srcSss = src & 0xFF;
                float roughness = value(surface.roughness, index, 1.0f);
                int smoothness = MathHelper.clamp(
                    Math.round((1.0f - (float) Math.sqrt(MathHelper.clamp(roughness, 0.02f, 0.98f))) * 255.0f),
                    0, 255);
                int emission = MathHelper.clamp(Math.round(value(surface.emissionMap, index, 0.0f) * 254.0f), 0, 254);
                int f0 = MathHelper.clamp(Math.round(value(surface.f0Map, index, 0.04f) * 255.0f), 0, 229);
                int metallic = MathHelper.clamp(Math.round(value(surface.metallicMap, index, 0.0f)), 0, 1);
                int metalCode = surface.labPbrMetalCode >= 230 && surface.labPbrMetalCode <= 255
                    ? surface.labPbrMetalCode
                    : 238;
                int encodedF0Metal = surface.metallicOverride && metallic > 0 ? metalCode : f0;
                int outA = surface.emissionOverride ? emission : (existing == null ? 255 : srcA);
                int outSmooth = surface.roughnessOverride ? smoothness : (existing == null ? 0 : srcSmoothness);
                int outF0 = (surface.f0Override || surface.metallicOverride) ? encodedF0Metal : (existing == null ? 0 : srcF0);
                int outSss = surface.sssOverride
                    ? MathHelper.clamp(Math.round(value(surface.sssMap, index, 0.0f) * 255.0f), 0, 255)
                    : srcSss;
                out.setColorArgb(x, y, (outA << 24) | (outSmooth << 16) | (outF0 << 8) | outSss);
            }
        }
    }

    private static void bakeNormal(int spriteId, AutoPbrValue.Surface surface, NativeImage out) {
        NativeImage existing = AutoPbrTextureCatalog.normal(spriteId);
        int size = out.getWidth();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                int src = existing == null ? 0xFFFF8080 : sample(existing, x, y, size);
                int evaluated = surface.normal == null || index >= surface.normal.length ? src : surface.normal[index];
                int a = surface.heightOverride
                    ? MathHelper.clamp(Math.round(value(surface.heightMap, index, 0.5f) * 255.0f), 0, 255)
                    : ((src >>> 24) & 0xFF);
                int r = surface.normalOverride ? ((evaluated >>> 16) & 0xFF) : ((src >>> 16) & 0xFF);
                int g = surface.normalOverride ? ((evaluated >>> 8) & 0xFF) : ((src >>> 8) & 0xFF);
                int b = surface.aoOverride
                    ? MathHelper.clamp(Math.round(value(surface.aoMap, index, 1.0f) * 255.0f), 0, 255)
                    : (src & 0xFF);
                out.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static void cacheImage(java.util.Map<Integer, NativeImage> cache, int spriteId, NativeImage image) {
        if (cache == null || spriteId < 0 || spriteId >= TextureTracker.MAX_TEXTURES || image == null) return;
        NativeImage copy = new NativeImage(NativeImage.Format.RGBA, image.getWidth(), image.getHeight(), false);
        copy.copyFrom(image);
        NativeImage previous = cache.put(spriteId, copy);
        if (previous != null && previous != copy) {
            try {
                previous.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void removeCachedImage(java.util.Map<Integer, NativeImage> cache, int spriteId) {
        if (cache == null) return;
        NativeImage previous = cache.remove(spriteId);
        if (previous != null) {
            try {
                previous.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static boolean refreshHeightMetadata(int spriteId, NativeImage normal, long generation,
                                                 List<String> diagnostics) {
        int heightRange = heightAlphaRangePackedForTest(normal, AutoPbrTextureCatalog.albedo(spriteId));
        int flags = AutoPbrTextureCatalog.spriteSourceFlags(spriteId);
        try {
            boolean ok = TextureArrayBridge.nativeUpdateSpriteHeightMetadata(spriteId, flags, heightRange, generation);
            if (!ok && diagnostics != null) diagnostics.add("height metadata upload failed");
            return ok;
        } catch (UnsatisfiedLinkError e) {
            LOGGER.debug("[MaterialLab] Native height metadata refresh skipped", e);
            if (diagnostics != null) diagnostics.add("native link unavailable");
            return false;
        }
    }

    public static int heightAlphaRangePackedForTest(NativeImage normal, NativeImage albedo) {
        if (normal == null || normal.getWidth() <= 0 || normal.getHeight() <= 0) return -1;
        int min = 255;
        int max = 0;
        boolean foundOpaquePixel = false;
        for (int y = 0; y < normal.getHeight(); y++) {
            for (int x = 0; x < normal.getWidth(); x++) {
                if (albedo != null) {
                    int ax = MathHelper.clamp(x * albedo.getWidth() / normal.getWidth(), 0, albedo.getWidth() - 1);
                    int ay = MathHelper.clamp(y * albedo.getHeight() / normal.getHeight(), 0, albedo.getHeight() - 1);
                    if (((albedo.getColorArgb(ax, ay) >>> 24) & 0xFF) == 0) continue;
                }
                int alpha = (normal.getColorArgb(x, y) >>> 24) & 0xFF;
                min = Math.min(min, alpha);
                max = Math.max(max, alpha);
                foundOpaquePixel = true;
            }
        }
        if (!foundOpaquePixel || max <= min) return -1;
        return min | (max << 8);
    }

    private static boolean roughnessTouched(MaterialRecipe recipe) {
        return recipe.roughnessOverride || recipe.generateRoughness || recipe.roughnessInvert
            || (recipe.oxideAmount > 0.0001f && recipe.oxideRoughnessInfluence > 0.0001f);
    }

    private static boolean heightTouched(MaterialRecipe recipe) {
        return recipe.heightOverride || recipe.generateHeight || recipe.invertHeight;
    }

    private static boolean normalTouched(MaterialRecipe recipe) {
        return recipe.normalStrengthOverride || recipe.generateNormal || recipe.flipGreen;
    }

    private static boolean aoTouched(MaterialRecipe recipe) {
        return recipe.aoOverride || recipe.generateAo || recipe.aoInvert;
    }

    private static boolean sssTouched(MaterialRecipe recipe) {
        return recipe.porosityOverride && !"preserve".equals(safe(recipe.porosityMode, "preserve"));
    }

    private static int modeFlags(MaterialRecipe recipe) {
        int volumeMode = "thin_glass".equals(safe(recipe.transmissionVolumeMode, "solid_volume")) ? 1 : 0;
        int thicknessMode = switch (safe(recipe.thicknessSource, "flat")) {
            case "albedo_alpha" -> 1;
            case "generated_luminance" -> 2;
            case "pack_derived" -> 3;
            default -> 0;
        };
        int coatMaskMode = "flat".equals(safe(recipe.coatMaskSource, "flat")) ? 0 : 1;
        int diffuseModel = diffuseModelMode(recipe);
        return (volumeMode & 0x3) | ((thicknessMode & 0x3) << 2) | ((coatMaskMode & 0x3) << 4)
            | ((diffuseModel & 0x3) << 6);
    }

    private static int diffuseModelMode(MaterialRecipe recipe) {
        return switch (safe(recipe.diffuseModel, "global")) {
            case "eon" -> 1;
            case "vmf" -> 2;
            case "legacy" -> 3;
            default -> 0;
        };
    }

    private static float thicknessAmount(int spriteId, MaterialRecipe recipe, int size) {
        float base = switch (safe(recipe.thicknessSource, "flat")) {
            case "albedo_alpha" -> averageAlpha(AutoPbrTextureCatalog.albedo(spriteId), size, recipe.thicknessAmount);
            case "generated_luminance" -> averageLuminance(AutoPbrTextureCatalog.albedo(spriteId), size, recipe.thicknessAmount);
            case "pack_derived" -> average(packHeight(spriteId, size), recipe.thicknessAmount);
            default -> recipe.thicknessAmount;
        };
        float gamma = MathHelper.clamp(recipe.thicknessGamma, 0.2f, 4.0f);
        float shaped = (float) Math.pow(MathHelper.clamp(base, 0.0f, 1.0f), gamma);
        float min = MathHelper.clamp(Math.min(recipe.thicknessMin, recipe.thicknessMax), 0.0f, 1.0f);
        float max = MathHelper.clamp(Math.max(recipe.thicknessMin, recipe.thicknessMax), 0.0f, 1.0f);
        return MathHelper.clamp(mix(min, max, shaped), 0.0f, 1.0f);
    }

    private static float average(float[] values, float fallback) {
        if (values == null || values.length == 0) return MathHelper.clamp(fallback, 0.0f, 1.0f);
        double sum = 0.0;
        int count = 0;
        for (float value : values) {
            if (!Float.isFinite(value) || value < 0.0f) continue;
            sum += MathHelper.clamp(value, 0.0f, 1.0f);
            count++;
        }
        return count == 0 ? MathHelper.clamp(fallback, 0.0f, 1.0f) : (float) (sum / count);
    }

    private static float averageAlpha(NativeImage image, int size, float fallback) {
        if (image == null) return MathHelper.clamp(fallback, 0.0f, 1.0f);
        double sum = 0.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                sum += ((sample(image, x, y, size) >>> 24) & 0xFF) / 255.0f;
            }
        }
        return (float) (sum / Math.max(1, size * size));
    }

    private static float averageLuminance(NativeImage image, int size, float fallback) {
        if (image == null) return MathHelper.clamp(fallback, 0.0f, 1.0f);
        double sum = 0.0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                sum += luminance(sample(image, x, y, size));
            }
        }
        return (float) (sum / Math.max(1, size * size));
    }

    private static float[] applyOxideToMetallic(int spriteId, MaterialRecipe recipe, int size, float[] values) {
        if (values == null || recipe.oxideAmount <= 0.0001f) return values;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int index = y * size + x;
                float metal = MathHelper.clamp(values[index], 0.0f, 1.0f);
                values[index] = MathHelper.clamp(metal * (1.0f - oxideMask(spriteId, recipe, x, y, size, metal)), 0.0f, 1.0f);
            }
        }
        return values;
    }

    private static float oxideMask(int spriteId, MaterialRecipe recipe, int x, int y, int size, float metalCoverage) {
        float amount = MathHelper.clamp(recipe.oxideAmount, 0.0f, 1.0f);
        if (amount <= 0.0001f) return 0.0f;
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        int argb = sample(albedo, x, y, size);
        float lum = luminance(argb);
        float sat = saturation(argb);
        float edge = edgeMagnitude(albedo, x, y, size);
        float cavity = MathHelper.clamp(1.0f - lum + edge * 0.5f, 0.0f, 1.0f);
        float patinaBias = MathHelper.clamp(recipe.oxideColorBias, 0.0f, 1.0f);
        float chemical = mix(cavity, sat * 0.75f + edge * 0.35f, patinaBias);
        return MathHelper.clamp(chemical * amount * MathHelper.clamp(metalCoverage, 0.0f, 1.0f), 0.0f, 1.0f);
    }

    private static boolean differs(float value, float baseline) {
        return Math.abs(value - baseline) > 0.0001f;
    }

    private static float[] packRoughness(int spriteId, int size) {
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        float[] out = constant(size, -1.0f);
        if (spec == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int smoothByte = (sample(spec, x, y, size) >>> 16) & 0xFF;
                float smoothness = smoothByte / 255.0f;
                out[y * size + x] = MathHelper.clamp((1.0f - smoothness) * (1.0f - smoothness), 0.0f, 1.0f);
            }
        }
        return out;
    }

    private static float[] generatedRoughness(int spriteId, MaterialRecipe recipe, int size) {
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        float edgeScale = MathHelper.clamp(recipe.roughnessEdge, 0.0f, 2.0f);
        String mode = safe(recipe.roughnessGeneratorMode, "luminance");
        float[] heightCurvature = "height_curvature".equals(mode) ? generatedHeight(spriteId, recipe, size) : null;
        float[] out = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = sample(albedo, x, y, size);
                float lum = luminance(argb);
                float edge = edgeMagnitude(albedo, x, y, size);
                float sat = saturation(argb);
                float cavity = MathHelper.clamp(1.0f - lum + edge * 0.5f, 0.0f, 1.0f);
                float value = switch (mode) {
                    case "inverse_luminance" -> 1.0f - lum;
                    case "edges" -> edge;
                    case "cavity" -> cavity;
                    case "saturation" -> sat;
                    case "height_curvature" -> edgeMagnitudeFromValues(heightCurvature, x, y, size);
                    case "crack_detail" -> MathHelper.clamp(cavity * 0.65f + edge * 0.65f, 0.0f, 1.0f);
                    default -> lum;
                };
                float detail = recipe.roughnessBroadDetail * 0.12f + recipe.roughnessMidDetail * 0.10f
                    + recipe.roughnessFineDetail * edge * 0.18f;
                out[y * size + x] = MathHelper.clamp(0.42f + value * 0.40f + edge * 0.22f * edgeScale
                    + detail - 0.22f, 0.0f, 1.0f);
            }
        }
        return out;
    }

    private static float[] packHeight(int spriteId, int size) {
        NativeImage normal = AutoPbrTextureCatalog.normal(spriteId);
        float[] out = constant(size, -1.0f);
        if (normal == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = ((sample(normal, x, y, size) >>> 24) & 0xFF) / 255.0f;
            }
        }
        return out;
    }

    private static float[] generatedHeight(int spriteId, MaterialRecipe recipe, int size) {
        NativeImage albedo = AutoPbrTextureCatalog.albedo(spriteId);
        float[] out = new float[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int argb = sample(albedo, x, y, size);
                float lum = luminance(argb);
                float edge = edgeMagnitude(albedo, x, y, size);
                float value = switch (safe(recipe.heightGeneratorMode, "luminance")) {
                    case "inverse_luminance" -> 1.0f - lum;
                    case "edge_cavity" -> MathHelper.clamp((1.0f - lum) * 0.55f + edge * 0.65f, 0.0f, 1.0f);
                    case "distance_bevel" -> MathHelper.clamp(lum * 0.65f + (1.0f - edge) * 0.25f, 0.0f, 1.0f);
                    case "shape_from_shading" -> MathHelper.clamp(0.5f + (lum - 0.5f) * 1.4f - edge * 0.15f, 0.0f, 1.0f);
                    default -> lum;
                };
                out[y * size + x] = value;
            }
        }
        return out;
    }

    private static int[] packNormal(int spriteId, int size) {
        NativeImage normal = AutoPbrTextureCatalog.normal(spriteId);
        int[] out = new int[size * size];
        java.util.Arrays.fill(out, 0xFF8080FF);
        if (normal == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int src = sample(normal, x, y, size);
                out[y * size + x] = 0xFF000000 | (src & 0x00FFFF00) | 0xFF;
            }
        }
        return out;
    }

    private static float[] packAo(int spriteId, int size) {
        NativeImage normal = AutoPbrTextureCatalog.normal(spriteId);
        float[] out = constant(size, -1.0f);
        if (normal == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = (sample(normal, x, y, size) & 0xFF) / 255.0f;
            }
        }
        return out;
    }

    private static float[] packSss(int spriteId, int size) {
        NativeImage spec = AutoPbrTextureCatalog.specular(spriteId);
        float[] out = constant(size, -1.0f);
        if (spec == null) return out;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                out[y * size + x] = (sample(spec, x, y, size) & 0xFF) / 255.0f;
            }
        }
        return out;
    }

    private static int strengthenNormal(int argb, float strength) {
        float s = MathHelper.clamp(strength, 0.0f, 4.0f);
        float nx = ((((argb >>> 16) & 0xFF) / 255.0f) - 0.5f) * s + 0.5f;
        float ny = ((((argb >>> 8) & 0xFF) / 255.0f) - 0.5f) * s + 0.5f;
        return 0xFF000000
            | (MathHelper.clamp(Math.round(nx * 255.0f), 0, 255) << 16)
            | (MathHelper.clamp(Math.round(ny * 255.0f), 0, 255) << 8)
            | 0xFF;
    }

    private static int blendNormal(int a, int b, float t, boolean flipGreen) {
        float blend = MathHelper.clamp(t, 0.0f, 1.0f);
        float ax = (((a >>> 16) & 0xFF) / 255.0f) * 2.0f - 1.0f;
        float ay = (((a >>> 8) & 0xFF) / 255.0f) * 2.0f - 1.0f;
        float bx = (((b >>> 16) & 0xFF) / 255.0f) * 2.0f - 1.0f;
        float by = (((b >>> 8) & 0xFF) / 255.0f) * 2.0f - 1.0f;
        float nx = mix(ax, bx, blend);
        float ny = mix(ay, by, blend);
        float len = (float) Math.sqrt(Math.max(0.0001f, nx * nx + ny * ny));
        if (len > 1.0f) {
            nx /= len;
            ny /= len;
        }
        float encX = nx * 0.5f + 0.5f;
        float encY = ny * 0.5f + 0.5f;
        if (flipGreen) encY = 1.0f - encY;
        return 0xFF000000
            | (MathHelper.clamp(Math.round(encX * 255.0f), 0, 255) << 16)
            | (MathHelper.clamp(Math.round(encY * 255.0f), 0, 255) << 8)
            | 0xFF;
    }

    private static float[] smooth(float[] values, int size, int passes) {
        int count = MathHelper.clamp(passes, 0, 4);
        float[] current = values;
        for (int pass = 0; pass < count; pass++) {
            float[] next = new float[current.length];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float sum = 0.0f;
                    int samples = 0;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            int sx = MathHelper.clamp(x + ox, 0, size - 1);
                            int sy = MathHelper.clamp(y + oy, 0, size - 1);
                            sum += current[sy * size + sx];
                            samples++;
                        }
                    }
                    next[y * size + x] = sum / samples;
                }
            }
            current = next;
        }
        return current;
    }

    private static float[] cleanup(float[] values, int size, int blurRadius, float sharpen, float denoise,
                                   int dilate, int erode, int despeckle) {
        float[] out = values;
        float[] original = values;
        if (blurRadius > 0) out = smooth(out, size, MathHelper.clamp(blurRadius, 0, 4));
        if (sharpen > 0.001f) {
            float[] blurred = smooth(out, size, 1);
            float amount = MathHelper.clamp(sharpen, 0.0f, 2.0f);
            float[] sharpened = new float[out.length];
            for (int i = 0; i < out.length; i++) {
                sharpened[i] = MathHelper.clamp(out[i] + (out[i] - blurred[i]) * amount, 0.0f, 1.0f);
            }
            out = sharpened;
        }
        if (denoise > 0.001f) {
            float[] blurred = smooth(out, size, 1);
            float amount = MathHelper.clamp(denoise, 0.0f, 1.0f);
            for (int i = 0; i < out.length; i++) out[i] = mix(out[i], blurred[i], amount);
        }
        for (int i = 0; i < MathHelper.clamp(dilate, 0, 4); i++) out = maxFilter(out, size);
        for (int i = 0; i < MathHelper.clamp(erode, 0, 4); i++) out = minFilter(out, size);
        if (despeckle > 0) {
            float[] blurred = smooth(out, size, 1);
            float threshold = 0.08f * MathHelper.clamp(despeckle, 1, 4);
            for (int i = 0; i < out.length; i++) {
                if (Math.abs(out[i] - blurred[i]) > threshold) out[i] = blurred[i];
            }
        }
        if (original == null) return out;
        return out;
    }

    private static float[] maxFilter(float[] values, int size) {
        float[] out = new float[values.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float best = 0.0f;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int sx = MathHelper.clamp(x + ox, 0, size - 1);
                        int sy = MathHelper.clamp(y + oy, 0, size - 1);
                        best = Math.max(best, values[sy * size + sx]);
                    }
                }
                out[y * size + x] = best;
            }
        }
        return out;
    }

    private static float[] minFilter(float[] values, int size) {
        float[] out = new float[values.length];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float best = 1.0f;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        int sx = MathHelper.clamp(x + ox, 0, size - 1);
                        int sy = MathHelper.clamp(y + oy, 0, size - 1);
                        best = Math.min(best, values[sy * size + sx]);
                    }
                }
                out[y * size + x] = best;
            }
        }
        return out;
    }

    private static int[] smoothNormals(int[] values, int size, int passes) {
        int count = MathHelper.clamp(passes, 0, 4);
        int[] current = values;
        for (int pass = 0; pass < count; pass++) {
            int[] next = new int[current.length];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float sx = 0.0f;
                    float sy = 0.0f;
                    int samples = 0;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            int px = MathHelper.clamp(x + ox, 0, size - 1);
                            int py = MathHelper.clamp(y + oy, 0, size - 1);
                            int n = current[py * size + px];
                            sx += ((n >>> 16) & 0xFF) / 255.0f;
                            sy += ((n >>> 8) & 0xFF) / 255.0f;
                            samples++;
                        }
                    }
                    int rx = MathHelper.clamp(Math.round(sx / samples * 255.0f), 0, 255);
                    int ry = MathHelper.clamp(Math.round(sy / samples * 255.0f), 0, 255);
                    next[y * size + x] = 0xFF000000 | (rx << 16) | (ry << 8) | 0xFF;
                }
            }
            current = next;
        }
        return current;
    }

    private static float remap(float value, float min, float max) {
        float lo = MathHelper.clamp(Math.min(min, max), 0.0f, 1.0f);
        float hi = MathHelper.clamp(Math.max(min, max), 0.0f, 1.0f);
        return MathHelper.clamp(lo + MathHelper.clamp(value, 0.0f, 1.0f) * (hi - lo), 0.0f, 1.0f);
    }

    private static float levels(float value, float black, float midpoint, float white) {
        float lo = MathHelper.clamp(Math.min(black, white - 0.01f), 0.0f, 0.99f);
        float hi = MathHelper.clamp(Math.max(white, lo + 0.01f), 0.01f, 1.0f);
        float t = MathHelper.clamp((value - lo) / (hi - lo), 0.0f, 1.0f);
        float mid = MathHelper.clamp(midpoint, 0.05f, 0.95f);
        float gamma = (float) (Math.log(0.5) / Math.log(mid));
        return (float) Math.pow(t, gamma);
    }

    private static float curve(float value, float gamma, float contrast) {
        float g = MathHelper.clamp(gamma, 0.2f, 4.0f);
        float c = MathHelper.clamp(contrast, 0.0f, 2.0f);
        float v = (float) Math.pow(MathHelper.clamp(value, 0.0f, 1.0f), g);
        return MathHelper.clamp((v - 0.5f) * c + 0.5f, 0.0f, 1.0f);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float lo = Math.min(edge0, edge1);
        float hi = Math.max(edge0, edge1);
        if (hi <= lo + 0.0001f) return x >= hi ? 1.0f : 0.0f;
        float t = MathHelper.clamp((x - lo) / (hi - lo), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    private static float mix(float a, float b, float t) {
        float blend = MathHelper.clamp(t, 0.0f, 1.0f);
        return a + (b - a) * blend;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static float[] constant(int size, float value) {
        float[] out = new float[size * size];
        java.util.Arrays.fill(out, value < 0.0f ? value : MathHelper.clamp(value, 0.0f, 1.0f));
        return out;
    }

    private static int sample(NativeImage image, int x, int y, int targetSize) {
        if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) return 0xFFFFFFFF;
        int sx = MathHelper.clamp(x * image.getWidth() / targetSize, 0, image.getWidth() - 1);
        int sy = MathHelper.clamp(y * image.getHeight() / targetSize, 0, image.getHeight() - 1);
        return image.getColorArgb(sx, sy);
    }

    private static float luminance(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return r * 0.2126f + g * 0.7152f + b * 0.0722f;
    }

    private static float saturation(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        return max <= 0.0001f ? 0.0f : (max - min) / max;
    }

    private static float valueChannel(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        return Math.max(r, Math.max(g, b));
    }

    private static float hue(int argb) {
        float r = ((argb >>> 16) & 0xFF) / 255.0f;
        float g = ((argb >>> 8) & 0xFF) / 255.0f;
        float b = (argb & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        if (delta <= 0.0001f) return 0.0f;
        float h;
        if (max == r) h = ((g - b) / delta) % 6.0f;
        else if (max == g) h = (b - r) / delta + 2.0f;
        else h = (r - g) / delta + 4.0f;
        return MathHelper.clamp((h / 6.0f + 1.0f) % 1.0f, 0.0f, 1.0f);
    }

    private static float edgeMagnitude(NativeImage image, int x, int y, int size) {
        float left = luminance(sample(image, Math.max(0, x - 1), y, size));
        float right = luminance(sample(image, Math.min(size - 1, x + 1), y, size));
        float up = luminance(sample(image, x, Math.max(0, y - 1), size));
        float down = luminance(sample(image, x, Math.min(size - 1, y + 1), size));
        return MathHelper.clamp(Math.abs(left - right) + Math.abs(up - down), 0.0f, 1.0f);
    }

    private static float edgeMagnitudeFromValues(float[] values, int x, int y, int size) {
        if (values == null || values.length == 0) return 0.0f;
        float left = values[y * size + Math.max(0, x - 1)];
        float right = values[y * size + Math.min(size - 1, x + 1)];
        float up = values[Math.max(0, y - 1) * size + x];
        float down = values[Math.min(size - 1, y + 1) * size + x];
        return MathHelper.clamp(Math.abs(left - right) + Math.abs(up - down), 0.0f, 1.0f);
    }

    private static boolean shouldFlipGreen(MaterialRecipe recipe) {
        return recipe.flipGreen || "directx".equals(safe(recipe.normalOrientation, "opengl"))
            || "flip_green".equals(safe(recipe.normalOrientation, "opengl"));
    }

    private static float value(float[] values, int index, float fallback) {
        if (values == null || index < 0 || index >= values.length) return fallback;
        return values[index];
    }
}
