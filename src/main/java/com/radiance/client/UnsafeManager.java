package com.radiance.client;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

public enum UnsafeManager {
    INSTANCE;

    private final Unsafe unsafe;

    UnsafeManager() {
        this.unsafe = initUnsafe();
    }

    private static Unsafe initUnsafe() {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            return (Unsafe) f.get(null);
        } catch (Throwable t) {
            throw new IllegalStateException(
                "Cannot access sun.misc.Unsafe. On Java 17+, add JVM arg: "
                    + "--add-opens=java.base/sun.misc=ALL-UNNAMED", t);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T allocateInstance(Class<T> cls) {
        try {
            return (T) unsafe.allocateInstance(cls);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    public Unsafe raw() {
        return unsafe;
    }
}
