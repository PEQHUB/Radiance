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

    // Fixed lighting setup (classic 3-point-ish for material preview)
    private static final double LX = 0.4082, LY = 0.8165, LZ = 0.4082; // normalized (1,2,1)
    private static final double VX = 0.0, VY = 0.0, VZ = 1.0; // front view

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

                // Dot products
                double NdotL = Math.max(nx * LX + ny * LY + nz * LZ, 0.0);
                double NdotV = Math.max(nz, 0.001);
                double hx = LX + VX, hy = LY + VY, hz = LZ + VZ;
                double hLen = Math.sqrt(hx * hx + hy * hy + hz * hz);
                hx /= hLen; hy /= hLen; hz /= hLen;
                double NdotH = Math.max(nx * hx + ny * hy + nz * hz, 0.0);
                double VdotH = Math.max(VX * hx + VY * hy + VZ * hz, 0.001);

                // GGX Distribution
                double d = ggxD(NdotH, alpha);
                double g = smithG(NdotV, NdotL, alpha);

                // Fresnel (Schlick) — continuous metallic blend
                double fDiel = schlick(dielF0, VdotH);
                double fr = fDiel + met * (schlick(f0r, VdotH) - fDiel);
                double fg = fDiel + met * (schlick(f0g, VdotH) - fDiel);
                double fb = fDiel + met * (schlick(f0b, VdotH) - fDiel);

                // Specular
                double denom = Math.max(4.0 * NdotV * NdotL, 0.001);
                double specScale = d * g / denom;
                double specR = fr * specScale * NdotL;
                double specG = fg * specScale * NdotL;
                double specB = fb * specScale * NdotL;

                // Diffuse (Lambertian) — (1-met) already suppresses for metals
                double baseAlbedo = 0.7;
                double diffScale = (1.0 - met) * (1.0 - fr) * NdotL / Math.PI;
                double diffR = baseAlbedo * diffScale;
                double diffG = baseAlbedo * diffScale;
                double diffB = baseAlbedo * diffScale;

                double outR = specR + diffR;
                double outG = specG + diffG;
                double outB = specB + diffB;

                // Transmission: checker pattern shows through
                if (trans > 0.01) {
                    boolean checker = ((px / 4) + (py / 4)) % 2 == 0;
                    double bgR = checker ? 0.22 : 0.12;
                    double bgG = checker ? 0.22 : 0.12;
                    double bgB = checker ? 0.25 : 0.14;
                    outR = outR * (1.0 - trans * 0.7) + bgR * trans * 0.7;
                    outG = outG * (1.0 - trans * 0.7) + bgG * trans * 0.7;
                    outB = outB * (1.0 - trans * 0.7) + bgB * trans * 0.7;
                }

                // Clearcoat layer
                if (coat > 0.01) {
                    double coatD = ggxD(NdotH, coatAlpha);
                    double coatG = smithG(NdotV, NdotL, coatAlpha);
                    double coatF = schlick(0.04, VdotH);
                    double coatSpec = coatD * coatG * coatF / denom * NdotL;
                    outR += coatSpec * coat;
                    outG += coatSpec * coat;
                    outB += coatSpec * coat;
                }

                // Hemisphere ambient (cool sky above, warm ground below)
                double upFactor = (-ny + 1.0) * 0.5;
                double envR = 0.10 + upFactor * 0.25;
                double envG = 0.07 + upFactor * 0.38;
                double envB = 0.04 + upFactor * 0.56;
                double ambScale = 0.10;
                double tintR = baseAlbedo + met * (f0r - baseAlbedo);
                double tintG = baseAlbedo + met * (f0g - baseAlbedo);
                double tintB = baseAlbedo + met * (f0b - baseAlbedo);
                outR += envR * ambScale * tintR;
                outG += envG * ambScale * tintG;
                outB += envB * ambScale * tintB;

                // Fresnel rim (subtle edge glow)
                double rim = Math.pow(1.0 - NdotV, 4.0) * 0.15;
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
