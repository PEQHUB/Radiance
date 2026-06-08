package com.radiance.client.compat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility bridge for the FirstPerson mod (tr7zw).
 * Uses reflection to access FirstPerson state without a compile-time dependency.
 *
 * Radiance cancels WorldRenderer.render() at HEAD, so FirstPerson's
 * WorldRendererMixin.renderEntities() injection never fires. We replicate
 * the essential flow: set isRenderingPlayer + apply position offset during
 * Radiance's entity render, so LivingEntityRendererMixin body-part hiding works.
 *
 * Method targets (from decompilation):
 *   - FirstPersonModelCore: isEnabled(), setRenderingPlayer(boolean), setRenderingPlayerPost(boolean)
 *   - LogicHandler (via getLogicHandler()): shouldApplyThirdPerson(boolean), showVanillaHands(),
 *     updatePositionOffset(Entity, float), getOffset()
 */
public final class FirstPersonCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("Radiance/FirstPersonCompat");

    private static boolean modPresent;
    private static boolean initSuccess;

    private static Object coreInstance;
    private static Object logicHandler;

    // FirstPersonModelCore methods
    private static MethodHandle isEnabledHandle;
    private static MethodHandle setRenderingPlayerHandle;       // instance method, not field
    private static MethodHandle setRenderingPlayerPostHandle;

    // LogicHandler methods
    private static MethodHandle shouldApplyThirdPersonHandle;
    private static MethodHandle showVanillaHandsHandle;
    private static MethodHandle updatePositionOffsetHandle;
    private static MethodHandle getOffsetHandle;

    private FirstPersonCompat() {}

    public static void init() {
        modPresent = FabricLoader.getInstance().isModLoaded("firstperson");
        if (!modPresent) return;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            // Get FirstPersonModelCore.instance (public static)
            Class<?> coreClass = Class.forName("dev.tr7zw.firstperson.FirstPersonModelCore");
            java.lang.reflect.Field instanceField = coreClass.getField("instance");
            coreInstance = instanceField.get(null);

            if (coreInstance == null) {
                LOGGER.warn("FirstPerson mod detected but instance is null (not yet initialized)");
                initSuccess = false;
                return;
            }

            // FirstPersonModelCore methods
            // isEnabled() is on the parent class FirstPersonBase
            Class<?> baseClass = coreClass.getSuperclass(); // FirstPersonBase
            isEnabledHandle = lookup.findVirtual(baseClass, "isEnabled", MethodType.methodType(boolean.class));

            // setRenderingPlayer(boolean) is an instance method overridden in FirstPersonModelCore
            // It sets both the instance field (via super) AND the static field
            setRenderingPlayerHandle = lookup.findVirtual(coreClass, "setRenderingPlayer",
                MethodType.methodType(void.class, boolean.class));

            // setRenderingPlayerPost(boolean)
            setRenderingPlayerPostHandle = lookup.findVirtual(baseClass, "setRenderingPlayerPost",
                MethodType.methodType(void.class, boolean.class));

            // Get LogicHandler via getLogicHandler()
            MethodHandle getLogicHandler = lookup.findVirtual(coreClass, "getLogicHandler",
                MethodType.methodType(Class.forName("dev.tr7zw.firstperson.LogicHandler")));
            logicHandler = getLogicHandler.invoke(coreInstance);

            if (logicHandler == null) {
                LOGGER.warn("FirstPerson LogicHandler is null");
                initSuccess = false;
                return;
            }

            // LogicHandler methods
            Class<?> logicClass = logicHandler.getClass();

            // shouldApplyThirdPerson(boolean) — takes isThirdPerson as param
            shouldApplyThirdPersonHandle = lookup.findVirtual(logicClass, "shouldApplyThirdPerson",
                MethodType.methodType(boolean.class, boolean.class));

            // showVanillaHands() — no-arg, returns boolean
            try {
                showVanillaHandsHandle = lookup.findVirtual(logicClass, "showVanillaHands",
                    MethodType.methodType(boolean.class));
            } catch (NoSuchMethodException e) {
                showVanillaHandsHandle = null;
            }

            // updatePositionOffset(Entity, float) — applies position offset to entity
            try {
                updatePositionOffsetHandle = lookup.findVirtual(logicClass, "updatePositionOffset",
                    MethodType.methodType(void.class, Entity.class, float.class));
            } catch (NoSuchMethodException e) {
                updatePositionOffsetHandle = null;
            }

            // getOffset() — returns Vec3d (the computed offset)
            try {
                getOffsetHandle = lookup.findVirtual(logicClass, "getOffset",
                    MethodType.methodType(net.minecraft.util.math.Vec3d.class));
            } catch (NoSuchMethodException e) {
                getOffsetHandle = null;
            }

            initSuccess = true;
            LOGGER.info("FirstPerson mod compatibility initialized");
        } catch (Throwable e) {
            LOGGER.warn("Failed to initialize FirstPerson mod compatibility: {}", e.getMessage());
            initSuccess = false;
        }
    }

    /** Lazy init: FirstPerson instance may not be ready during our static init. */
    private static void ensureInstance() {
        if (modPresent && !initSuccess && coreInstance == null) {
            init();
        }
    }

    /**
     * Returns true when FirstPerson mod is enabled AND camera is first-person
     * AND no activation handler blocks it.
     */
    public static boolean isActive() {
        ensureInstance();
        if (!initSuccess) return false;
        try {
            boolean enabled = (boolean) isEnabledHandle.invoke(coreInstance);
            if (!enabled) return false;
            // Pass actual camera perspective: true if third-person, false if first-person
            boolean isThirdPerson = !MinecraftClient.getInstance().options.getPerspective().isFirstPerson();
            return (boolean) shouldApplyThirdPersonHandle.invoke(logicHandler, isThirdPerson);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sets isRenderingPlayer via the instance method (sets both instance + static fields,
     * and triggers updatePlayerLayers on certain tick intervals).
     */
    public static void setRenderingPlayer(boolean rendering) {
        if (!initSuccess) return;
        try {
            setRenderingPlayerHandle.invoke(coreInstance, rendering);
        } catch (Throwable t) {
            // ignore
        }
    }

    public static void setRenderingPlayerPost(boolean rendering) {
        if (!initSuccess) return;
        try {
            setRenderingPlayerPostHandle.invoke(coreInstance, rendering);
        } catch (Throwable t) {
            // ignore
        }
    }

    /**
     * Computes and applies the position offset for the player model.
     * The mod shifts the player model slightly forward to avoid camera clipping.
     */
    public static void updatePositionOffset(Entity entity, float tickDelta) {
        if (!initSuccess || updatePositionOffsetHandle == null) return;
        try {
            updatePositionOffsetHandle.invoke(logicHandler, entity, tickDelta);
        } catch (Throwable t) {
            // ignore
        }
    }

    public static net.minecraft.util.math.Vec3d getOffset() {
        if (!initSuccess || getOffsetHandle == null) return null;
        try {
            return (net.minecraft.util.math.Vec3d) getOffsetHandle.invoke(logicHandler);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean showVanillaHands() {
        if (!initSuccess || showVanillaHandsHandle == null) return true;
        try {
            return (boolean) showVanillaHandsHandle.invoke(logicHandler);
        } catch (Throwable t) {
            return true;
        }
    }

    public static boolean isModPresent() {
        return modPresent;
    }
}
