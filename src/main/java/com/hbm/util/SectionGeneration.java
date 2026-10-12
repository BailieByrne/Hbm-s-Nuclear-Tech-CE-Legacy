package com.hbm.util;

import com.hbm.lib.internal.GenerationTracking;
import com.hbm.lib.internal.UnsafeHolder;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static com.hbm.lib.internal.UnsafeHolder.U;

/** Tracks in-place block changes while private section snapshots are outstanding. */
public final class SectionGeneration {
    private static final long GENERATION = UnsafeHolder.fieldOffset(ExtendedBlockStorage.class, "hbm$generation");
    private static final GenerationTracking TRACKING = new GenerationTracking(advanceHandle());
    private static final MethodHandle CHANGED = TRACKING.invoker();

    private SectionGeneration() {}

    public static void changed(ExtendedBlockStorage section) {
        try {
            CHANGED.invokeExact(section);
        } catch (Throwable failure) {
            U.throwException(failure);
        }
    }

    public static long generation(ExtendedBlockStorage section) {
        return section == null ? 0 : U.getLong(section, GENERATION);
    }

    public static void acquire() { TRACKING.acquire(); }
    public static void release() { TRACKING.release(); }

    private static MethodHandle advanceHandle() {
        try {
            return MethodHandles.lookup().findStatic(SectionGeneration.class, "advance",
                    MethodType.methodType(void.class, ExtendedBlockStorage.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static void advance(ExtendedBlockStorage section) {
        U.putLong(section, GENERATION, U.getLong(section, GENERATION) + 1);
    }
}
