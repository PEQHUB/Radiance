package com.radiance.client.gui;

import com.radiance.client.RadianceClient;
import com.radiance.client.util.MaterialData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Async software PBR sphere renderer for material preview icons.
 * Renders Cook-Torrance spheres off the GL thread, caches in memory and on disk.
 */
public final class MaterialSphereRenderer {

    private MaterialSphereRenderer() {}

    // ── Render size constants (render at 2x, display at 1x for supersampling) ──
    public static final int GRID_RENDER_SIZE = 128;
    public static final int SIDEBAR_RENDER_SIZE = 360;

    // ── Cache key ──
    private record SphereKey(int f0R, int f0G, int f0B, int roughness, int metallic,
        int transmission, int ior, int subsurface, int anisotropic,
        int sheenWeight, int sheenTint, int coatWeight, int coatRoughness, int size) {

        static SphereKey of(MaterialData d, int size) {
            return new SphereKey(d.f0R, d.f0G, d.f0B, d.roughness, d.metallic,
                d.transmission, d.ior, d.subsurface, d.anisotropic,
                d.sheenWeight, d.sheenTint, d.coatWeight, d.coatRoughness, size);
        }

        long diskHash() {
            long h = 17;
            h = h * 31 + f0R;   h = h * 31 + f0G;   h = h * 31 + f0B;
            h = h * 31 + roughness;  h = h * 31 + metallic;
            h = h * 31 + transmission;  h = h * 31 + ior;
            h = h * 31 + subsurface;  h = h * 31 + anisotropic;
            h = h * 31 + sheenWeight;  h = h * 31 + sheenTint;
            h = h * 31 + coatWeight;  h = h * 31 + coatRoughness;
            h = h * 31 + size;
            return h;
        }
    }

    // ── Memory cache ──
    private static final ConcurrentHashMap<SphereKey, Identifier> textureCache = new ConcurrentHashMap<>();
    private static final AtomicInteger idCounter = new AtomicInteger(0);

    // ── Async pipeline ──
    private static volatile int cacheGeneration = 0; // bumped on clearCache() to discard stale workers
    private static final ExecutorService workerPool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "radiance-sphere-worker");
        t.setDaemon(true);
        return t;
    });
    private record CompletedRender(SphereKey key, NativeImage image) {}
    private static final ConcurrentLinkedQueue<CompletedRender> completedQueue = new ConcurrentLinkedQueue<>();
    private static final Set<SphereKey> pendingKeys = ConcurrentHashMap.newKeySet();

    // ── Disk cache ──
    private static final String CACHE_DIR = "sphere-cache";
    private static final String CACHE_VERSION_FILE = "cache-version.txt";
    private static final String CACHE_VERSION = "2"; // bump when render algorithm changes

    // ── 3-point studio lighting ──
    private static final double[] KEY  = normalize( 0.5,  0.7,  0.5);
    private static final double[] FILL = normalize(-0.6,  0.2,  0.7);
    private static final double[] RIM  = normalize(-0.3, -0.5,  0.4);
    private static final double KEY_INT = 2.5, FILL_INT = 0.8, RIM_INT = 0.5;

    private static double[] normalize(double x, double y, double z) {
        double len = Math.sqrt(x * x + y * y + z * z);
        return new double[]{ x / len, y / len, z / len };
    }

    // ── Public API ──

    /**
     * Draw a sphere at the given position. Shows placeholder while async render is pending.
     * @param renderSize  resolution to render at (e.g. GRID_RENDER_SIZE=128)
     * @param displaySize size to draw on screen (e.g. 64)
     */
    public static void drawSphere(DrawContext context, MaterialData data,
                                   int x, int y, int renderSize, int displaySize) {
        Identifier id = requestTexture(data, renderSize);
        if (id != null) {
            context.drawTexture(RenderLayer::getGuiTextured, id,
                x, y, 0, 0, displaySize, displaySize, displaySize, displaySize);
        } else {
            drawPlaceholder(context, x, y, displaySize);
        }
    }

    /**
     * Request a texture for the given material. Returns Identifier if cached, null if pending.
     * Non-blocking: submits async render on cache miss.
     */
    public static Identifier requestTexture(MaterialData data, int renderSize) {
        SphereKey key = SphereKey.of(data, renderSize);

        // Memory cache hit
        Identifier cached = textureCache.get(key);
        if (cached != null) return cached;

        // Already queued
        if (!pendingKeys.add(key)) return null;

        // Submit async: check disk cache, then render if needed
        final int gen = cacheGeneration;
        workerPool.submit(() -> {
            NativeImage image = null;
            try {
                image = loadFromDiskCache(key);
                if (image == null) {
                    image = renderSphereImage(data, renderSize);
                    saveToDiskCache(key, image);
                }
                // If cache was cleared while we were working, discard result
                if (gen != cacheGeneration) {
                    image.close();
                    pendingKeys.remove(key);
                    return;
                }
                completedQueue.add(new CompletedRender(key, image));
            } catch (Exception e) {
                if (image != null) image.close();
                pendingKeys.remove(key);
                System.err.println("[Radiance] Sphere render failed: " + e.getMessage());
            }
        });

        return null;
    }

    /**
     * Drain completed async renders and register their textures on the GL thread.
     * Call once per frame from render().
     */
    public static void drainCompleted() {
        CompletedRender item;
        while ((item = completedQueue.poll()) != null) {
            SphereKey key = item.key;
            NativeImage image = item.image;
            try {
                if (textureCache.containsKey(key)) {
                    image.close();
                } else {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(image);
                    Identifier id = Identifier.of("radiance", "mat_preview/" + idCounter.getAndIncrement());
                    MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                    textureCache.put(key, id);
                }
            } catch (Exception e) {
                image.close();
            } finally {
                pendingKeys.remove(key);
            }
        }
    }

    /** Invalidate all cached textures and cancel pending work. */
    public static void clearCache() {
        // Bump generation so in-flight workers discard their results
        cacheGeneration++;

        // Drain pending completions to avoid NativeImage leaks
        CompletedRender item;
        while ((item = completedQueue.poll()) != null) {
            item.image.close();
        }
        pendingKeys.clear();

        // NOTE: Do NOT call texManager.destroyTexture() — Vulkan has no intercept for
        // texture destruction and calling it causes stale descriptors → VK_ERROR_DEVICE_LOST.
        // Just clear the map; old Vulkan textures remain allocated but harmless (~128×128 RGBA each).
        textureCache.clear();
        // Note: idCounter is NOT reset — monotonic IDs prevent Identifier collisions
    }

    /** Validate disk cache version; wipe if stale. Call once when browser opens. */
    public static void validateDiskCache() {
        try {
            Path versionFile = getCacheDir().resolve(CACHE_VERSION_FILE);
            if (Files.exists(versionFile)) {
                String cached = Files.readString(versionFile).trim();
                if (CACHE_VERSION.equals(cached)) return;
            }
            clearDiskCache();
        } catch (Exception e) {
            // Non-fatal
        }
    }

    /** Delete all files in the sphere-cache directory. */
    public static void clearDiskCache() {
        try {
            Path dir = getCacheDir();
            if (Files.exists(dir)) {
                try (var files = Files.list(dir)) {
                    files.forEach(f -> { try { Files.delete(f); } catch (Exception ignored) {} });
                }
            }
        } catch (Exception e) {
            // Non-fatal
        }
    }

    // ── Disk cache internals ──

    private static Path getCacheDir() {
        return RadianceClient.radianceDir.resolve(CACHE_DIR);
    }

    private static String diskFilename(SphereKey key) {
        return "sphere_" + Long.toHexString(key.diskHash()) + ".png";
    }

    private static NativeImage loadFromDiskCache(SphereKey key) {
        try {
            Path file = getCacheDir().resolve(diskFilename(key));
            if (!Files.exists(file)) return null;
            try (InputStream in = Files.newInputStream(file)) {
                return NativeImage.read(in);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveToDiskCache(SphereKey key, NativeImage image) {
        try {
            Path dir = getCacheDir();
            Files.createDirectories(dir);
            Path versionFile = dir.resolve(CACHE_VERSION_FILE);
            if (!Files.exists(versionFile)) {
                Files.writeString(versionFile, CACHE_VERSION);
            }
            image.writeTo(dir.resolve(diskFilename(key)));
        } catch (Exception e) {
            // Non-fatal: disk cache is best-effort
        }
    }

    // ── Placeholder ──

    private static void drawPlaceholder(DrawContext context, int x, int y, int size) {
        // Dark rounded-ish fill as loading indicator
        int inset = size / 8;
        context.fill(x + inset, y + inset, x + size - inset, y + size - inset, 0xFF1A1A1E);
    }

    // ── Scene constants ──
    private static final double SPHERE_R = 0.85;
    private static final double GROUND_Y = -SPHERE_R; // sphere tangent to ground
    private static final double CHECKER_SCALE = 0.5;
    private static final double GROUND_FADE_START = 2.5;
    private static final double GROUND_FADE_END = 5.0;

    // Camera
    private static final double CAM_X = 0.0, CAM_Y = 0.6, CAM_Z = 3.0;
    private static final double LOOK_X = 0.0, LOOK_Y = -0.1, LOOK_Z = 0.0;
    private static final double VFOV = Math.toRadians(30.0);

    // Pre-computed camera basis (forward, right, up)
    private static final double[] CAM_FWD, CAM_RIGHT, CAM_UP;
    static {
        double fx = LOOK_X - CAM_X, fy = LOOK_Y - CAM_Y, fz = LOOK_Z - CAM_Z;
        double fl = Math.sqrt(fx*fx + fy*fy + fz*fz);
        fx /= fl; fy /= fl; fz /= fl;
        CAM_FWD = new double[]{fx, fy, fz};
        // right = forward × world_up(0,1,0)
        double rx = fz, ry = 0, rz = -fx;
        double rl = Math.sqrt(rx*rx + rz*rz);
        rx /= rl; rz /= rl;
        CAM_RIGHT = new double[]{rx, ry, rz};
        // up = right × forward
        double ux = ry*fz - rz*fy, uy = rz*fx - rx*fz, uz = rx*fy - ry*fx;
        CAM_UP = new double[]{ux, uy, uz};
    }

    // ── Scene rendering (pure CPU, thread-safe) ──

    private static NativeImage renderSphereImage(MaterialData data, int size) {
        NativeImage image = new NativeImage(size, size, true);

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

        double dielF0 = 0.04;
        if (data.ior > 0) {
            double n = data.ior / 1000.0;
            double r = (n - 1.0) / (n + 1.0);
            dielF0 = r * r;
        }

        double[][] lights = { KEY, FILL, RIM };
        double[] lightInt = { KEY_INT, FILL_INT, RIM_INT };

        double halfH = Math.tan(VFOV * 0.5);
        double halfW = halfH; // square aspect

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                // NDC → ray direction
                double u = (2.0 * (px + 0.5) / size - 1.0) * halfW;
                double v = (1.0 - 2.0 * (py + 0.5) / size) * halfH; // flip Y
                double dx = CAM_FWD[0] + u * CAM_RIGHT[0] + v * CAM_UP[0];
                double dy = CAM_FWD[1] + u * CAM_RIGHT[1] + v * CAM_UP[1];
                double dz = CAM_FWD[2] + u * CAM_RIGHT[2] + v * CAM_UP[2];
                double dl = Math.sqrt(dx*dx + dy*dy + dz*dz);
                dx /= dl; dy /= dl; dz /= dl;

                // Ray-sphere intersection (sphere at origin, radius SPHERE_R)
                double ox = CAM_X, oy = CAM_Y, oz = CAM_Z;
                double b = ox*dx + oy*dy + oz*dz;
                double c = ox*ox + oy*oy + oz*oz - SPHERE_R * SPHERE_R;
                double disc = b*b - c;

                double outR, outG, outB;
                int ia = 255;

                if (disc > 0) {
                    double t = -b - Math.sqrt(disc);
                    if (t > 0) {
                        // Sphere hit — compute normal
                        double hx = ox + t*dx, hy = oy + t*dy, hz = oz + t*dz;
                        double nx = hx / SPHERE_R, ny = hy / SPHERE_R, nz = hz / SPHERE_R;

                        // View direction = -ray dir
                        double vx = -dx, vy = -dy, vz = -dz;
                        double NdotV = Math.max(nx*vx + ny*vy + nz*vz, 0.001);

                        double[] shading = shadeSphere(nx, ny, nz, vx, vy, vz, NdotV,
                            f0r, f0g, f0b, rough, met, trans, coat, alpha, coatAlpha, dielF0,
                            lights, lightInt);
                        outR = shading[0]; outG = shading[1]; outB = shading[2];

                        // Transmission: show ground through sphere
                        if (trans > 0.01) {
                            double[] groundCol = traceGround(hx, hy, hz, dx, dy, dz, lights, lightInt);
                            outR = outR * (1.0 - trans * 0.7) + groundCol[0] * trans * 0.7;
                            outG = outG * (1.0 - trans * 0.7) + groundCol[1] * trans * 0.7;
                            outB = outB * (1.0 - trans * 0.7) + groundCol[2] * trans * 0.7;
                        }

                        // Tonemap + gamma
                        outR = gammaReinhard(outR);
                        outG = gammaReinhard(outG);
                        outB = gammaReinhard(outB);

                        image.setColorArgb(px, py, (255 << 24) | (clamp8(outR) << 16) | (clamp8(outG) << 8) | clamp8(outB));
                        continue;
                    }
                }

                // Sphere miss — try ground plane
                if (dy < 0) {
                    double tGround = (GROUND_Y - oy) / dy;
                    if (tGround > 0) {
                        double gx = ox + tGround * dx;
                        double gz = oz + tGround * dz;

                        double[] groundCol = shadeGround(gx, gz, lights, lightInt);
                        outR = gammaReinhard(groundCol[0]);
                        outG = gammaReinhard(groundCol[1]);
                        outB = gammaReinhard(groundCol[2]);

                        image.setColorArgb(px, py, (255 << 24) | (clamp8(outR) << 16) | (clamp8(outG) << 8) | clamp8(outB));
                        continue;
                    }
                }

                // Background gradient
                double skyT = Math.max(dy * 0.5 + 0.5, 0.0); // 0=bottom, 1=top
                outR = 0.055 + 0.05 * skyT;
                outG = 0.06 + 0.05 * skyT;
                outB = 0.075 + 0.06 * skyT;

                image.setColorArgb(px, py, (255 << 24) | (clamp8(outR) << 16) | (clamp8(outG) << 8) | clamp8(outB));
            }
        }
        return image;
    }

    /** Shade sphere hit with full PBR (Cook-Torrance + env). */
    private static double[] shadeSphere(double nx, double ny, double nz,
            double vx, double vy, double vz, double NdotV,
            double f0r, double f0g, double f0b,
            double rough, double met, double trans, double coat,
            double alpha, double coatAlpha, double dielF0,
            double[][] lights, double[] lightInt) {

        double fNorm = schlick(dielF0, NdotV);
        double frNorm = fNorm + met * (schlick(f0r, NdotV) - fNorm);

        double outR = 0, outG = 0, outB = 0;

        // Direct lighting from 3 studio lights
        for (int li = 0; li < 3; li++) {
            double lx = lights[li][0], ly = lights[li][1], lz = lights[li][2];
            double intensity = lightInt[li];
            double NdotL = Math.max(nx*lx + ny*ly + nz*lz, 0.0);
            if (NdotL <= 0.0) continue;

            double hx = lx + vx, hy = ly + vy, hz = lz + vz;
            double hLen = Math.sqrt(hx*hx + hy*hy + hz*hz);
            hx /= hLen; hy /= hLen; hz /= hLen;
            double NdotH = Math.max(nx*hx + ny*hy + nz*hz, 0.0);
            double VdotH = Math.max(vx*hx + vy*hy + vz*hz, 0.001);

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

            double diffScale = (1.0 - met) * (1.0 - fr) * NdotL / Math.PI * intensity;
            outR += 0.7 * diffScale;
            outG += 0.7 * diffScale;
            outB += 0.7 * diffScale;

            if (coat > 0.01) {
                double cD = ggxD(NdotH, coatAlpha);
                double cG = smithG(NdotV, NdotL, coatAlpha);
                double cF = schlick(0.04, VdotH);
                outR += cD * cG * cF / denom * NdotL * coat * intensity;
                outG += cD * cG * cF / denom * NdotL * coat * intensity;
                outB += cD * cG * cF / denom * NdotL * coat * intensity;
            }
        }

        // Studio HDRI environment
        double rdotN2 = 2.0 * (nx*vx + ny*vy + nz*vz);
        double refX = -vx + rdotN2 * nx;
        double refY = -vy + rdotN2 * ny;
        double refZ = -vz + rdotN2 * nz;

        double[] envSpec = sampleStudioEnv(refX, refY, refZ);
        double[] envDiff = sampleStudioEnv(nx, ny, nz);

        double envBlur = Math.min(rough * 2.0, 1.0);
        double eR = envSpec[0] * (1.0 - envBlur) + envDiff[0] * envBlur;
        double eG = envSpec[1] * (1.0 - envBlur) + envDiff[1] * envBlur;
        double eB = envSpec[2] * (1.0 - envBlur) + envDiff[2] * envBlur;

        double envF_r = frNorm + (Math.max(1.0 - rough, frNorm) - frNorm) * Math.pow(1.0 - NdotV, 5.0);
        double envF_g = fNorm + met * (schlick(f0g, NdotV) - fNorm)
            + (Math.max(1.0 - rough, fNorm + met * (f0g - fNorm)) - (fNorm + met * (f0g - fNorm))) * Math.pow(1.0 - NdotV, 5.0);
        double envF_b = fNorm + met * (schlick(f0b, NdotV) - fNorm)
            + (Math.max(1.0 - rough, fNorm + met * (f0b - fNorm)) - (fNorm + met * (f0b - fNorm))) * Math.pow(1.0 - NdotV, 5.0);
        outR += eR * envF_r * 0.6;
        outG += eG * envF_g * 0.6;
        outB += eB * envF_b * 0.6;

        outR += envDiff[0] * 0.7 * (1.0 - met) * (1.0 - frNorm) * 0.4;
        outG += envDiff[1] * 0.7 * (1.0 - met) * (1.0 - frNorm) * 0.4;
        outB += envDiff[2] * 0.7 * (1.0 - met) * (1.0 - frNorm) * 0.4;

        // Fresnel rim glow
        double rim = Math.pow(1.0 - NdotV, 4.0) * 0.25;
        outR += rim; outG += rim; outB += rim;

        return new double[]{outR, outG, outB};
    }

    /** Shade a ground plane hit with checkerboard + shadow. */
    private static double[] shadeGround(double gx, double gz,
            double[][] lights, double[] lightInt) {
        // Checkerboard pattern
        int cx = (int) Math.floor(gx / CHECKER_SCALE);
        int cz = (int) Math.floor(gz / CHECKER_SCALE);
        boolean light = ((cx + cz) & 1) == 0;
        double baseR = light ? 0.35 : 0.20;
        double baseG = light ? 0.34 : 0.19;
        double baseB = light ? 0.33 : 0.18;

        // Analytical sphere shadow
        double distXZ = Math.sqrt(gx * gx + gz * gz);
        double shadow = smoothstep(SPHERE_R * 0.85, SPHERE_R * 1.8, distXZ);

        // Lambertian diffuse from studio lights (ground normal = (0,1,0))
        double diffuse = 0.0;
        for (int li = 0; li < 3; li++) {
            double NdotL = Math.max(lights[li][1], 0.0); // dot(up, lightDir)
            diffuse += NdotL * lightInt[li];
        }
        diffuse = diffuse / Math.PI * 0.8 + 0.12; // ambient floor

        double outR = baseR * diffuse * shadow;
        double outG = baseG * diffuse * shadow;
        double outB = baseB * diffuse * shadow;

        // Fade ground to background at distance
        double dist = Math.sqrt(gx * gx + gz * gz);
        double fade = smoothstep(GROUND_FADE_START, GROUND_FADE_END, dist);
        double bgR = 0.06, bgG = 0.065, bgB = 0.08;
        outR = outR * (1.0 - fade) + bgR * fade;
        outG = outG * (1.0 - fade) + bgG * fade;
        outB = outB * (1.0 - fade) + bgB * fade;

        return new double[]{outR, outG, outB};
    }

    /** Trace ground from inside sphere (for transmission). */
    private static double[] traceGround(double ox, double oy, double oz,
            double dx, double dy, double dz,
            double[][] lights, double[] lightInt) {
        if (dy >= 0) return new double[]{0.06, 0.065, 0.08};
        double t = (GROUND_Y - oy) / dy;
        if (t <= 0) return new double[]{0.06, 0.065, 0.08};
        double gx = ox + t * dx;
        double gz = oz + t * dz;
        return shadeGround(gx, gz, lights, lightInt);
    }

    /** Reinhard tonemap + gamma 2.2 */
    private static double gammaReinhard(double v) {
        v = v / (v + 1.0);
        return Math.pow(v, 1.0 / 2.2);
    }

    // ── Studio HDRI environment ──

    /**
     * Procedural studio HDRI: two softbox reflections + ambient + ground bounce.
     * Returns [R, G, B] environment radiance for a given direction.
     */
    private static double[] sampleStudioEnv(double dx, double dy, double dz) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0001) return new double[]{0.15, 0.14, 0.13};
        dx /= len; dy /= len; dz /= len;

        // Dark studio ambient
        double r = 0.08, g = 0.075, b = 0.07;

        // Sky gradient (negative Y = up in screen-space coords)
        if (dy < 0) {
            double skyFactor = Math.min(-dy, 1.0);
            r += 0.12 * skyFactor;
            g += 0.13 * skyFactor;
            b += 0.17 * skyFactor;
        }

        // Key softbox: bright rectangular highlight matching key light direction
        double keyDot = dx * KEY[0] + dy * KEY[1] + dz * KEY[2];
        if (keyDot > 0.85) {
            double softbox = smoothstep(0.85, 0.95, keyDot);
            r += 2.5 * softbox;
            g += 2.4 * softbox;
            b += 2.2 * softbox;
        } else if (keyDot > 0.7) {
            double penumbra = smoothstep(0.7, 0.85, keyDot) * 0.3;
            r += 0.6 * penumbra;
            g += 0.58 * penumbra;
            b += 0.55 * penumbra;
        }

        // Fill softbox: cooler, dimmer highlight matching fill light
        double fillDot = dx * FILL[0] + dy * FILL[1] + dz * FILL[2];
        if (fillDot > 0.8) {
            double softbox = smoothstep(0.8, 0.92, fillDot);
            r += 0.7 * softbox;
            g += 0.72 * softbox;
            b += 0.75 * softbox;
        }

        // Warm ground bounce (positive Y = down)
        if (dy > 0) {
            double groundFactor = Math.min(dy, 1.0);
            double bounce = groundFactor * groundFactor;
            r += 0.15 * bounce;
            g += 0.10 * bounce;
            b += 0.05 * bounce;
        }

        return new double[]{r, g, b};
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
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
