package com.radiance.client.proxy.world;

import net.minecraft.util.math.Vec3d;

public class PlayerProxy {

    public static native void setCameraPos(double x, double y, double z);

    public static void setCameraPos(Vec3d cameraPos) {
        setCameraPos(cameraPos.x, cameraPos.y, cameraPos.z);
    }
}
