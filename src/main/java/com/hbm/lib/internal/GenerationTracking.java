package com.hbm.lib.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.MethodType;

public final class GenerationTracking {
    private final MethodHandle inactive;
    private final MutableCallSite site;
    private final MutableCallSite[] sites;
    private final MethodHandle invoker;
    private final MethodHandle active;
    private int users;

    public GenerationTracking(MethodHandle active) {
        assert active.type().returnType() == void.class;
        this.active = active;
        try {
            MethodHandle noop = MethodHandles.lookup().findStatic(GenerationTracking.class, "noop",
                    MethodType.methodType(void.class));
            this.inactive = MethodHandles.dropArguments(noop, 0, active.type().parameterList());
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
        this.site = new MutableCallSite(inactive);
        this.sites = new MutableCallSite[]{site};
        this.invoker = site.dynamicInvoker();
    }

    public MethodHandle invoker() {
        return invoker;
    }

    private static void noop() {}

    public synchronized void acquire() {
        if (users++ == 0) {
            site.setTarget(active);
            MutableCallSite.syncAll(sites);
        }
    }

    // Release only after every result protected by this acquisition has been committed or rejected.
    public synchronized void release() {
        assert users > 0;
        if (--users == 0) {
            site.setTarget(inactive);
            MutableCallSite.syncAll(sites);
        }
    }

}
