package com.hbm.handler.threading;

import com.hbm.config.BombConfig;
import com.hbm.main.MainRegistry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

public final class BombForkJoinPool {

    private static ForkJoinPool pool;
    private static int refs;
    private static final Int2ReferenceOpenHashMap<ReferenceOpenHashSet<IJobCancellable>> JOBS_BY_DIM = new Int2ReferenceOpenHashMap<>();
    private static final Object LOCK = new Object();

    private BombForkJoinPool() {
    }

    public interface IJobCancellable {
        void cancelJob();
    }

    public static ForkJoinPool acquire() {
        synchronized (LOCK) {
            if (pool == null || pool.isShutdown()) {
                int workers = computeWorkers();
                pool = new ForkJoinPool(workers, ForkJoinPool.defaultForkJoinWorkerThreadFactory,
                        BombForkJoinPool::logWorkerFailure, false);
            }
            refs++;
            return pool;
        }
    }

    public static void release(ForkJoinPool acquiredPool) {
        ForkJoinPool toShutdown;
        synchronized (LOCK) {
            if (acquiredPool != pool) return;
            if (refs <= 0) throw new IllegalStateException("BombForkJoinPool reference underflow");
            refs--;
            toShutdown = maybeShutdownLocked();
        }
        if (toShutdown != null) {
            toShutdown.shutdown();
        }
    }

    public static void register(ForkJoinPool acquiredPool, int dimensionId, IJobCancellable job) {
        if (job == null || dimensionId == Integer.MIN_VALUE || acquiredPool == null) return;
        synchronized (LOCK) {
            if (acquiredPool != pool) return;
            ReferenceOpenHashSet<IJobCancellable> set = JOBS_BY_DIM.get(dimensionId);
            if (set == null) {
                set = new ReferenceOpenHashSet<>();
                JOBS_BY_DIM.put(dimensionId, set);
            }
            set.add(job);
        }
    }

    public static void unregister(ForkJoinPool acquiredPool, int dimensionId, IJobCancellable job) {
        if (job == null || dimensionId == Integer.MIN_VALUE || acquiredPool == null) return;
        ForkJoinPool toShutdown = null;
        synchronized (LOCK) {
            if (acquiredPool != pool) return;
            ReferenceOpenHashSet<IJobCancellable> set = JOBS_BY_DIM.get(dimensionId);
            if (set == null) return;
            set.remove(job);
            if (set.isEmpty()) {
                JOBS_BY_DIM.remove(dimensionId);
                toShutdown = maybeShutdownLocked();
            }
        }
        if (toShutdown != null) {
            toShutdown.shutdown();
        }
    }

    public static void onWorldUnload(World world) {
        if (world == null || world.isRemote) return;
        onWorldUnload(world.provider.getDimension());
    }

    public static void onWorldUnload(int dimensionId) {
        List<IJobCancellable> jobs = null;
        synchronized (LOCK) {
            ReferenceOpenHashSet<IJobCancellable> set = JOBS_BY_DIM.remove(dimensionId);
            if (set != null && !set.isEmpty()) {
                jobs = new ArrayList<>(set.size());
                jobs.addAll(set);
                set.clear();
            }
        }
        if (jobs != null) {
            for (IJobCancellable job : jobs) {
                try {
                    job.cancelJob();
                } catch (Throwable t) {
                    MainRegistry.logger.error("Failed to cancel bomb job on dimension unload {}", dimensionId, t);
                }
            }
        }
        ForkJoinPool toShutdown;
        synchronized (LOCK) {
            toShutdown = maybeShutdownLocked();
        }
        if (toShutdown != null) {
            toShutdown.shutdown();
        }
    }

    public static void onServerStopped() {
        List<IJobCancellable> jobs = null;
        ForkJoinPool toStop;
        synchronized (LOCK) {
            ReferenceCollection<ReferenceOpenHashSet<IJobCancellable>> values = JOBS_BY_DIM.values();
            if (!values.isEmpty()) {
                int approx = 0;
                for (ReferenceOpenHashSet<IJobCancellable> set : values) approx += set.size();
                jobs = new ArrayList<>(Math.max(approx, 16));
                for (ReferenceOpenHashSet<IJobCancellable> set : values) {
                    jobs.addAll(set);
                    set.clear();
                }
                JOBS_BY_DIM.clear();
            }
            refs = 0;
            toStop = pool;
            pool = null;
        }
        if (jobs != null) {
            for (IJobCancellable job : jobs) {
                try {
                    job.cancelJob();
                } catch (Throwable t) {
                    MainRegistry.logger.error("Failed to cancel bomb job on server stop", t);
                }
            }
        }
        if (toStop != null && !toStop.isShutdown()) {
            // Jobs already reject publication; queued tasks must still run their resource-release paths.
            toStop.shutdown();
        }
    }

    /** Waits for deterministic blast work; workers park chunk requests rather than waiting on the server. */
    public static boolean awaitQuiet(long timeoutMs) {
        ForkJoinPool current;
        synchronized (LOCK) {
            current = pool;
        }
        return current == null || current.awaitQuiescence(timeoutMs, TimeUnit.MILLISECONDS);
    }

    private static ForkJoinPool maybeShutdownLocked() {
        if (pool == null) return null;
        if (refs != 0) return null;
        if (!JOBS_BY_DIM.isEmpty()) return null;
        ForkJoinPool p = pool;
        pool = null;
        return p;
    }

    private static int computeWorkers() {
        int processors = Runtime.getRuntime().availableProcessors();
        int workers = BombConfig.maxThreads <= 0 ? Math.max(1, processors + BombConfig.maxThreads) : Math.min(BombConfig.maxThreads, processors);
        return Math.max(1, workers);
    }

    private static void logWorkerFailure(Thread thread, Throwable error) {
        MainRegistry.logger.error("Bomb ForkJoinPool worker crashed in {}", thread.getName(), error);
    }
}
