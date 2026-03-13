package com.radiance.client.gui;

import com.radiance.client.util.MaterialData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Software PBR sphere renderer for material preview icons.
 * Renders Cook-Torrance spheres at configurable sizes, caches as Minecraft textures.
 */
public final class MaterialSphereRenderer {

    private MaterialSphereRenderer() {}

    /** Collision-free cache key using all 13 material properties + render size. */
    private record SphereKey(int f0R, int f0G, int f0B, int roughness, int metallic,
        int transmission, int ior, int subsurface, int anisotropic,
        int sheenWeight, int sheenTint, int coatWeight, int coatRoughness, int size) {

        static SphereKey of(MaterialData d, int size) {
            return new SphereKey(d.f0R, d.f0G, d.f0B, d.roughness, d.metallic,
                d.transmission, d.ior, d.subsurface, d.anisotropic,
                d.sheenWeight, d.sheenTint, d.coatWeight, d.coatRoughness, size);
        }
    }

    private static final ConcurrentHashMap<SphereKey, Identifier> textureCache = new ConcurrentHashMap<>();
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    // 3-point studio lighting (key, fill, rim)
    private static final double[] KEY  = normalize( 0.5,  0.7,  0.5); // upper-right front
    private static final double[] FILL = normalize(-0.6,  0.2,  0.7); // left front, softer
    private static final double[] RIM  = normalize(-0.3, -0.5,  0.4); // lower-left back, cool
    private static final double KEY_INT = 2.5, FILL_INT = 0.8, RIM_INT = 0.5;
    private static final double VX = 0.0, VY = 0.0, VZ = 1.0; // front view

    private static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[]{ x / len, y / len, z / len };
    }

    /** Draw a material sphere icon at the given position and size. */
    public static void drawSphere(DrawContext context, MaterialData data, int x, int y, int size) {
        Identifier id = getOrCreateTexture(data, size);
        if (id != null) {
            context.drawTexture(RenderLayer::getGuiTextured, id, x, y, 0, 0, size, size, size, size);
        }
    }

    /** Get or create a cached texture for the given material data and size. */
    public static Identifier getOrCreateTexture(MaterialData data, int size) {
        SphereKey key = SphereKey.of(data, size);
        return textureCache.computeIfAbsent(key, k -> createTexture(data, size));
    }

    /** Invalidate all cached textures (call when leaving the browser screen). */
    public static void clearCache() {
        var snapshot = new java.util.HashMap<>(textureCache);
        textureCache.clear();
        idCounter.set(0);
        var texManager = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : snapshot.values()) {
            texManager.destroyTexture(id);
        }
    }

    private static Identifier createTexture(MaterialData data, int size) {
        NativeImage image = renderSphereImage(data, size);
        try {
            NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
            Identifier id = Identifier.of("radiance", "mat_preview/" + idCounter.getAndIncrement());
            MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
            return id;
        } catch (Exception e) {
            image.close();
            throw e;
        }
    }

    private static NativeImage renderSphereImage(MaterialData data, int size) {
        NativeImage image = new NativeImage(size, size, true);
        double radius = (size - 2) / 2.0;
        double cx = size / 2.0;
        double cy = size / 2.0;

        // Material parameters in [0,1]
        double f0r = data.f0R / 1000.0;
        double f0g = data.f0G / 1000.0;
        double f0b = data.f0B / 1000.0;
        double rough = Math.max(data.roughness / 100.0, 0.01);
        double met = data.metallic / 1000.0;
        double trans = data.transmission / 1000.0;
        double coat = data.coatWeight / 1000.0;
        double coatRough = Math.max(data.coatRoughness / 100.0, 0.01);
        double alpha = rough * rough;
        double coatAlpha = coatRough * coatRough;

        // Dielectric base F0 from IOR if not metallic
        double dielF0 = 0.04;
        if (data.ior > 0) {
            double n = data.ior / 1000.0;
            double r = (n - 1.0) / (n + 1.0);
            dielF0 = r * r;
        }

        double[][] lights = { KEY, FILL, RIM };
        double[] lightInt = { KEY_INT, FILL_INT, RIM_INT };

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                double nx = (px - cx) / radius;
                double ny = (py - cy) / radius;
                double r2 = nx * nx + ny * ny;

                if (r2 > 1.0) {
                    image.setColorArgb(px, py, 0x00000000); // transparent
                    continue;
                }

                double nz = Math.sqrt(1.0 - r2);
                double NdotV = Math.max(nz, 0.001);

                // Fresnel at normal incidence for diffuse energy conservation
                double fNorm = schlick(dielF0, NdotV);
                double frNorm = fNorm + met * (schlick(f0r, NdotV) - fNorm);

                double outR = 0, outG = 0, outB = 0;

                // Accumulate direct lighting from all 3 studio lights
                for (int li = 0; li < 3; li++) {
                    double lx = lights[li][0], ly = lights[li][1], lz = lights[li][2];
                    double intensity = lightInt[li];
                    double NdotL = Math.max(nx * lx + ny * ly + nz * lz, 0.0);
                    if (NdotL <= 0.0) continue;

                    double hx = lx + VX, hy = ly + VY, hz = lz + VZ;
                    double hLen = Math.sqrt(hx * hx + hy * hy + hz * hz);
                    hx /= hLen; hy /= hLen; hz /= hLen;
                    double NdotH = Math.max(nx * hx + ny * hy + nz * hz, 0.0);
                    double VdotH = Math.max(VX * hx + VY * hy + VZ * hz, 0.001);

                    // GGX specular BRDF
                    double d = ggxD(NdotH, alpha);
                    double g = smithG(NdotV, NdotL, alpha);
                    double fD = schlick(dielF0, VdotH);
                    double fr = fD + met * (schlick(f0r, VdotH) - fD);
                    double fg = fD + met * (schlick(f0g, VdotH) - fD);
                    double fb = fD + met * (schlick(f0b, VdotH) - fD);
                    double denom = Math.max(4.0 * NdotV * NdotL, 0.001);
                    double spec = d * g / denom;

                    outR += fr * spec * NdotL * intensity;
                    outG += fg * spec * NdotL * intensity;
                    outB += fb * spec * NdotL * intensity;

                    // Diffuse (only for non-metals)
                    double diffScale = (1.0 - met) * (1.0 - fr) * NdotL / Math.PI * intensity;
                    outR += 0.7 * diffScale;
                    outG += 0.7 * diffScale;
                    outB += 0.7 * diffScale;

                    // Clearcoat
                    if (coat > 0.01) {
                        double cD = ggxD(NdotH, coatAlpha);
                        double cG = smithG(NdotV, NdotL, coatAlpha);
                        double cF = schlick(0.04, VdotH);
                        outR += cD * cG * cF / denom * NdotL * coat * intensity;
                        outG += cD * cG * cF / denom * NdotL * coat * intensity;
                        outB += cD * cG * cF / denom * NdotL * coat * intensity;
                    }
                }

                // Environment reflection (studio HDRI approximation)
                // Reflection direction for specular environment
                double rdotN2 = 2.0 * NdotV;
                double refX = -VX + rdotN2 * nx;
                double refY = -VY + rdotN2 * ny;
                double refZ = -VZ + rdotN2 * nz;
                // Hemisphere gradient: bright sky above, warm ground below
                double refUp = (-refY + 1.0) * 0.5;
                double envSpecR = 0.35 + refUp * 0.55;  // warm ground to bright sky
                double envSpecG = 0.30 + refUp * 0.60;
                double envSpecB = 0.25 + refUp * 0.75;

                // Roughness-blurred env: lerp toward diffuse hemisphere as roughness increases
                double upN = (-ny + 1.0) * 0.5;
                double envDiffR = 0.25 + upN * 0.35;
                double envDiffG = 0.20 + upN * 0.40;
                double envDiffB = 0.15 + upN * 0.50;
                double envBlur = Math.min(rough * 2.0, 1.0);
                double eR = envSpecR * (1.0 - envBlur) + envDiffR * envBlur;
                double eG = envSpecG * (1.0 - envBlur) + envDiffG * envBlur;
                double eB = envSpecB * (1.0 - envBlur) + envDiffB * envBlur;

                // Specular environment (pre-integrated: F * envBRDF)
                double envF_r = frNorm + (Math.max(1.0 - rough, frNorm) - frNorm) * Math.pow(1.0 - NdotV, 5.0);
                double envF_g = fNorm + met * (schlick(f0g, NdotV) - fNorm)
                    + (Math.max(1.0 - rough, fNorm + met * (f0g - fNorm)) - (fNorm + met * (f0g - fNorm))) * Math.pow(1.0 - NdotV, 5.0);
                double envF_b = fNorm + met * (schlick(f0b, NdotV) - fNorm)
                    + (Math.max(1.0 - rough, fNorm + met * (f0b - fNorm)) - (fNorm + met * (f0b - fNorm))) * Math.pow(1.0 - NdotV, 5.0);
                outR += eR * envF_r * 0.6;
                outG += eG * envF_g * 0.6;
                outB += eB * envF_b * 0.6;

                // Diffuse environment (only for non-metals)
                outR += envDiffR * 0.7 * (1.0 - met) * (1.0 - frNorm) * 0.4;
                outG += envDiffG * 0.7 * (1.0 - met) * (1.0 - frNorm) * 0.4;
                outB += envDiffB * 0.7 * (1.0 - met) * (1.0 - frNorm) * 0.4;

                // Transmission: checker pattern shows through
                if (trans > 0.01) {
                    boolean checker = ((px / 4) + (py / 4)) % 2 == 0;
                    double bgR = checker ? 0.3 : 0.15;
                    double bgG = checker ? 0.3 : 0.15;
                    double bgB = checker ? 0.35 : 0.18;
                    outR = outR * (1.0 - trans * 0.7) + bgR * trans * 0.7;
                    outG = outG * (1.0 - trans * 0.7) + bgG * trans * 0.7;
                    outB = outB * (1.0 - trans * 0.7) + bgB * trans * 0.7;
                }

                // Fresnel rim glow
                double rim = Math.pow(1.0 - NdotV, 4.0) * 0.25;
                outR += rim; outG += rim; outB += rim;

                // Reinhard tonemap
                outR = outR / (outR + 1.0);
                outG = outG / (outG + 1.0);
                outB = outB / (outB + 1.0);

                // Gamma
                outR = Math.pow(outR, 1.0 / 2.2);
                outG = Math.pow(outG, 1.0 / 2.2);
                outB = Math.pow(outB, 1.0 / 2.2);

                int ir = clamp8(outR);
                int ig = clamp8(outG);
                int ib = clamp8(outB);

                // Anti-aliased edge
                int ia = 255;
                double edgeDist = Math.sqrt(r2);
                if (edgeDist > 0.95) {
                    ia = clamp8((1.0 - edgeDist) / 0.05);
                }

                image.setColorArgb(px, py, (ia << 24) | (ir << 16) | (ig << 8) | ib);
            }
        }
        return image;
    }

    // ── PBR math helpers ──

    private static double ggxD(double NdotH, double alpha) {
        double a2 = alpha * alpha;
        double d = NdotH * NdotH * (a2 - 1.0) + 1.0;
        return a2 / (Math.PI * d * d);
    }

    private static double smithG(double NdotV, double NdotL, double alpha) {
        return smithG1(NdotV, alpha) * smithG1(NdotL, alpha);
    }

    private static double smithG1(double NdotX, double alpha) {
        double a2 = alpha * alpha;
        double denom = NdotX + Math.sqrt(a2 + (1.0 - a2) * NdotX * NdotX);
        return 2.0 * NdotX / denom;
    }

    private static double schlick(double f0, double cosTheta) {
        double t = 1.0 - cosTheta;
        double t2 = t * t;
        return f0 + (1.0 - f0) * t2 * t2 * t;
    }

    private static int clamp8(double v) {
        return Math.max(0, Math.min(255, (int) (v * 255.0 + 0.5)));
    }
}
