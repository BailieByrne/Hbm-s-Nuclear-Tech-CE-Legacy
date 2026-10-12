package com.hbm.explosion;

import com.hbm.main.MainRegistry;
import com.hbm.saveddata.BlastNotificationData;
import com.hbm.config.BombConfig;
import com.hbm.handler.threading.BombForkJoinPool;
import com.hbm.interfaces.BitMask;
import com.hbm.interfaces.IExplosionRay;
import com.hbm.interfaces.ServerThread;
import com.hbm.lib.Library;
import com.hbm.lib.TLPool;
import com.hbm.lib.maps.NonBlockingHashMapLong;
import com.hbm.lib.queues.MpscUnboundedXaddArrayLongQueue;
import com.hbm.util.ChunkUtil;
import com.hbm.util.BulkSectionUpdates;
import com.hbm.util.SectionSnapshot;
import com.hbm.util.ConcurrentBitSet;
import com.hbm.util.MpscIntArrayListCollector;
import com.hbm.util.OffHeapBitSet;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.util.math.BlockPos;

import net.minecraft.world.WorldServer;
import net.minecraft.util.BitArray;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.World;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.chunk.*;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import com.hbm.util.SectionPalette;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.*;

import static com.hbm.lib.internal.UnsafeHolder.U;
import static com.hbm.lib.internal.UnsafeHolder.fieldOffset;

// Workers trace and carve private section snapshots; the server validates and publishes them.
// Reloads restart from blast metadata. Worker and application state remain transient.
public class ExplosionNukeRayParallelized implements IExplosionRay, BombForkJoinPool.IJobCancellable {

    private static long nextCacheEpoch;
    private final long cacheEpoch = allocateCacheEpoch();

    private static synchronized long allocateCacheEpoch() {
        return ++nextCacheEpoch;
    }

    static final int SUB_MASK_SIZE = 16 * 16 * 16;
    static final float NUKE_RESISTANCE_CUTOFF = 2_000_000F;
    static final float INITIAL_ENERGY_FACTOR = 0.3F;
    static final double RESOLUTION_FACTOR = 1.0;
    static final int LUT_RESISTANCE_BINS = 256;
    static final int LUT_DISTANCE_BINS = 256;
    static final float LUT_MAX_RESISTANCE = 100.0F;
    static final float[][] ENERGY_LOSS_LUT = new float[LUT_RESISTANCE_BINS][LUT_DISTANCE_BINS];
    static final float DAMAGE_PER_BLOCK = 0.50F;
    static final float DAMAGE_THRESHOLD_MULT = 1.00F;
    static final float LOW_R_BOUND = 0.25F;
    static final float LOW_R_PASS_LENGTH_BREAK = 0.75F;
    static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    static final double RAY_DIRECTION_EPSILON = 1e-6;
    static final double PROCESSING_EPSILON = 1e-9;
    static final float MIN_EFFECTIVE_DIST_FOR_ENERGY_CALC = 0.01f;

    static final long EMPTY_LONG = MpscUnboundedXaddArrayLongQueue.EMPTY;
    static final int PAUSED_RAY_WORDS = 6;

    // Native packed storage plus a palette resistance cache.
    static final int EMPTY_CACHE_SHIFT = 7;
    static final ThreadLocal<EmptySubMaskCache> TL_EMPTY_CACHE = ThreadLocal.withInitial(EmptySubMaskCache::new);
    static final int RES_CACHE_SHIFT = 7;
    static final ThreadLocal<DecodedSection[]> TL_RES_CACHE = ThreadLocal.withInitial(
            () -> new DecodedSection[1 << RES_CACHE_SHIFT]);

    static final ThreadLocal<BlockPos.MutableBlockPos> TL_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    static final ThreadLocal<LocalAgg> TL_LOCAL_AGG = ThreadLocal.withInitial(LocalAgg::new);
    static final TLPool<IntDoubleAccumulator> ACC_POOL = new TLPool<>(IntDoubleAccumulator::new,
            IntDoubleAccumulator::clear, 64, 4096);
    static final ThreadLocal<Long2ObjectOpenHashMap<IBlockState>> TL_MODIFIED = ThreadLocal.withInitial(
            () -> new Long2ObjectOpenHashMap<>(16));
    static final ThreadLocal<LongOpenHashSet> TL_EDGES = ThreadLocal.withInitial(() -> new LongOpenHashSet(64));
    static final TLPool<LongArrayList> LONG_LIST_POOL = new TLPool<>(() -> new LongArrayList(64), LongArrayList::clear,
            8, 512);
    static final TLPool<Long2LongOpenHashMap> LONG2LONG_POOL = new TLPool<>(Long2LongOpenHashMap::new,
            Long2LongOpenHashMap::clear, 4, 256);
    static final TLPool<IntArrayList> INT_LIST_POOL = new TLPool<>(() -> new IntArrayList(64), IntArrayList::clear, 16,
            512);
    static final TLPool<Long2ObjectOpenHashMap<IBlockState>> LONG2OBJECT_POOL = new TLPool<>(Long2ObjectOpenHashMap::new,
            Long2ObjectOpenHashMap::clear, 4, 256);

    static final long OFF_MAP_ACQUIRED = fieldOffset(ExplosionNukeRayParallelized.class, "mapAcquired");
    static final long OFF_CONSOLIDATION_STARTED = fieldOffset(ExplosionNukeRayParallelized.class,
            "consolidationStarted");
    static final long OFF_PENDING_RAYS = fieldOffset(ExplosionNukeRayParallelized.class, "pendingRays");
    static final long OFF_ACTIVE_WORKER_TASKS = fieldOffset(ExplosionNukeRayParallelized.class, "activeWorkerTasks");
    private static final long OFF_ADMISSION_PUMP_QUEUED = fieldOffset(ExplosionNukeRayParallelized.class,
            "admissionPumpQueued");
    static final long OFF_FAILURE = fieldOffset(ExplosionNukeRayParallelized.class, "failure");
    static final long OFF_WORKER_CLEANUP = fieldOffset(ExplosionNukeRayParallelized.class, "workerCleanupDone");
    static final long OFF_PENDING_POST_LOAD_WORK = fieldOffset(ExplosionNukeRayParallelized.class,
            "pendingPostLoadWork");
    static final long OFF_FINISH_QUEUED = fieldOffset(ExplosionNukeRayParallelized.class, "finishQueued");
    static final long OFF_COLLECT_FINISHED = fieldOffset(ExplosionNukeRayParallelized.class, "collectFinished");
    static final long OFF_CONSOLIDATION_FINISHED = fieldOffset(ExplosionNukeRayParallelized.class,
            "consolidationFinished");
    static final long OFF_DESTROY_FINISHED = fieldOffset(ExplosionNukeRayParallelized.class, "destroyFinished");
    static final long OFF_POOL_ACQUIRED = fieldOffset(ExplosionNukeRayParallelized.class, "poolAcquired");
    static final long OFF_JOB_REGISTERED = fieldOffset(ExplosionNukeRayParallelized.class, "jobRegistered");
    static {
        for (int r = 0; r < LUT_RESISTANCE_BINS; r++) {
            float resistance = (r / (float) (LUT_RESISTANCE_BINS - 1)) * LUT_MAX_RESISTANCE;
            for (int d = 0; d < LUT_DISTANCE_BINS; d++) {
                float distFrac = d / (float) (LUT_DISTANCE_BINS - 1);
                ENERGY_LOSS_LUT[r][d] = (float) (Math.pow(resistance + 1.0, 3.0 * distFrac) - 1.0);
            }
        }
    }

    final WorldServer world;
    final double explosionX, explosionY, explosionZ;
    final double invRadius, invRayIndexScale;
    final int originX, originY, originZ;
    final int radius;
    final int strength;
    final int worldHeight;
    final int worldMinY;
    final int bitsetSize;
    final int subchunkPerChunk;
    final NonBlockingHashMapLong<ConcurrentBitSet> destructionMap;
    final NonBlockingHashMapLong<ChunkAgg> aggMap;
    final NonBlockingHashMapLong<MpscIntArrayListCollector> waitingRoom;
    final NonBlockingHashMapLong<ConcurrentLinkedQueue<ResumeItem>> postLoadQueues;
    final MpscUnboundedXaddArrayLongQueue chunkLoadQueue;
    final Long2LongOpenHashMap sectionMaskByChunk;
    /** Chunks this blast has already carved and notified. Server thread only, like {@link #chunkFixup}. */
    private final LongOpenHashSet publishedChunks = new LongOpenHashSet();
    /**
     * Edge cells whose notification would wake a fluid in a chunk this blast has not carved yet. Waking it
     * pours that fluid into the crater already cut next to it, and the carve that follows takes its
     * neighbours away through {@code casSectionAt} - which never schedules a fluid tick - so what poured in
     * stands frozen at the chunk seam. Deferred to {@link #secondPass}, never dropped: by then both sides
     * are air and the notification is either a no-op or correct.
     */
    private final LongArrayList deferredFluidWakes = new LongArrayList();
    private final Long2ObjectOpenHashMap<CompletableFuture<?>> lightingByChunk = new Long2ObjectOpenHashMap<>();
    final int algorithm;
    final int rayCount;
    /**
     * Outstanding async chunk requests; server thread only.
     */
    private final Long2ObjectOpenHashMap<CompletableFuture<Chunk>> chunksInFlight =
            new Long2ObjectOpenHashMap<>();
    /**
     * Ticketed, awaiting the distance-manager flush.
     */
    private final LongArrayList ticketsPendingFlush = new LongArrayList();
    // Trace continuations use only this blast's leased chunks. Quiescent trace windows and completed
    // publication neighborhoods release their leases instead of retaining the whole blast footprint.
    private final LongOpenHashSet ticketedChunks = new LongOpenHashSet();
    private final NonBlockingHashMapLong<Chunk> admittedChunks = new NonBlockingHashMapLong<>(64);
    private final ConcurrentLinkedQueue<NukeCarveWork> publicationQueue = new ConcurrentLinkedQueue<>();
    private @Nullable NukeCarveWork currentPublication;
    private long @Nullable [] publicationNeighborhood;
    private boolean publicationSubmitted;
    private boolean publicationPhase;
    private @Nullable CompletableFuture<?> publicationLighting;
    private boolean finalNotified;
    private final Long2ObjectOpenHashMap<LongArrayList> finalWakes = new Long2ObjectOpenHashMap<>();
    private long[] finalChunks;
    private int finalChunkIndex;
    private final LongOpenHashSet readinessLoads = new LongOpenHashSet();
    private boolean notificationTicketsPendingFlush;
    private int notificationAdmissionVersion;
    int @Nullable [] rayOrder;
    private volatile long @Nullable [] pausedRays;
    @Nullable ForkJoinPool pool;
    private @Nullable BulkSectionUpdates sectionUpdates;
    private volatile @Nullable Throwable failure;
    private volatile int activeWorkerTasks;
    private volatile long admissionDeadline;
    private volatile int admissionPumpQueued;
    private volatile boolean cancelling;
    private boolean mainCleanupDone;
    private volatile int workerCleanupDone;
    @Nullable NonBlockingHashMapLong<Chunk> mirror;
    volatile @Nullable UUID detonator;
    @SuppressWarnings("unused")
    volatile int mapAcquired, consolidationStarted, finishQueued, pendingRays,
            pendingPostLoadWork, collectFinished, consolidationFinished, destroyFinished, poolAcquired, jobRegistered;
    volatile boolean isContained = true;

    public ExplosionNukeRayParallelized(World world, double x, double y, double z, int strength, int radius,
                                        int algorithm) {
        if (!(world instanceof WorldServer server)) {
            throw new IllegalArgumentException("ExplosionNukeRayParallelized requires a WorldServer");
        }
        this.world = server;
        this.algorithm = algorithm;
        explosionX = x;
        explosionY = y;
        explosionZ = z;
        originX = (int) Math.floor(x);
        originY = (int) Math.floor(y);
        originZ = (int) Math.floor(z);
        this.strength = strength;
        this.radius = radius;
        invRadius = radius > 0 ? 1.0 / radius : 0.0;

        // Native 1.12 chunks use a zero-based, fixed world height.
        this.worldMinY = 0;
        int rawHeight = server.getHeight();
        this.worldHeight = (rawHeight + 15) & ~15;
        this.subchunkPerChunk = worldHeight >> 4;
        this.bitsetSize = 16 * worldHeight * 16;

        rayCount = Math.max(0, (int) (2.5 * Math.PI * strength * strength * RESOLUTION_FACTOR));
        invRayIndexScale = rayCount > 1 ? 1.0 / (rayCount - 1) : 0.0;
        pendingRays = rayCount;

        int estimatedChunkCount = Math.max(16, count(false));
        int chunkCap = capFor(estimatedChunkCount * 2);
        int subChunkCap = capFor(Math.max(16, count(true)));

        destructionMap = new NonBlockingHashMapLong<>(chunkCap);
        aggMap = new NonBlockingHashMapLong<>(chunkCap);
        waitingRoom = new NonBlockingHashMapLong<>(subChunkCap);
        postLoadQueues = new NonBlockingHashMapLong<>(subChunkCap);
        sectionMaskByChunk = new Long2LongOpenHashMap(estimatedChunkCount);
        sectionMaskByChunk.defaultReturnValue(0L);

        final int pooledLong = 4;
        int csLoad = chooseChunkSizeForSegments(Math.max(subChunkCap, 1024), 32, 1024, 8192);
        chunkLoadQueue = new MpscUnboundedXaddArrayLongQueue(csLoad, pooledLong);

        initializeAndStartWorkers();
    }

    private static int chunkX(long packed) { return (int) packed; }
    private static int chunkZ(long packed) { return (int) (packed >>> 32); }
    private static boolean validChunk(int x, int z) {
        return x >= -1875000 && z >= -1875000 && x < 1875000 && z < 1875000;
    }
    private static boolean emptySection(ExtendedBlockStorage section) {
        return section == Chunk.NULL_BLOCK_STORAGE || section.isEmpty();
    }

    static int capFor(int n) {
        int c = 1;
        while (c < n) c <<= 1;
        return Math.max(16, c);
    }

    static int ceilPow2(int x) {
        if (x <= 1) return 1;
        int hb = Integer.highestOneBit(x - 1);
        int r = hb << 1;
        return r > 0 ? r : (1 << 30);
    }

    static int chooseChunkSizeForSegments(int peakDepth, int targetSegments, int minPow2, int maxPow2) {
        if (peakDepth <= 0) return minPow2;
        int want = (peakDepth + targetSegments - 1) / targetSegments;
        int cs = ceilPow2(want);
        if (cs < minPow2) cs = minPow2;
        if (cs > maxPow2) cs = maxPow2;
        return cs;
    }

    static float getNukeResistance(IBlockState state) {
        return ExplosionNukeRayBatched.getNukeResistance(state);
    }

    static double getEnergyLossFactor(float resistance, double distFrac) {
        if (resistance >= NUKE_RESISTANCE_CUTOFF) return resistance;
        if (resistance <= 0) return 0.0;
        if (resistance > LUT_MAX_RESISTANCE) return Math.pow(resistance + 1.0, 3.0 * distFrac) - 1.0;
        int rBin = (int) (resistance * (LUT_RESISTANCE_BINS - 1) / LUT_MAX_RESISTANCE);
        if (rBin == 0 && resistance > 0) return Math.pow(resistance + 1.0, 3.0 * distFrac) - 1.0;
        int dBin = (int) (distFrac * (LUT_DISTANCE_BINS - 1));
        return ENERGY_LOSS_LUT[rBin][dBin];
    }

    private static int rayKeyCubeMorton(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        int face;
        double u, v, inv; //@formatter:off
        if (ax >= ay && ax >= az) {
            if (x >= 0) { face = 0; inv = 1.0 / ax; u = (-z * inv + 1.0) * 0.5; }
            else        { face = 1; inv = 1.0 / ax; u = ( z * inv + 1.0) * 0.5; }
            v = (-y * inv + 1.0) * 0.5;
        } else if (ay >= az) {
            if (y >= 0) { face = 2; inv = 1.0 / ay; u = ( x * inv + 1.0) * 0.5; v = ( z * inv + 1.0) * 0.5; }
            else        { face = 3; inv = 1.0 / ay; u = ( x * inv + 1.0) * 0.5; v = (-z * inv + 1.0) * 0.5; }
        } else {
            if (z >= 0) { face = 4; inv = 1.0 / az; u = ( x * inv + 1.0) * 0.5; }
            else        { face = 5; inv = 1.0 / az; u = (-x * inv + 1.0) * 0.5; }
            v = (-y * inv + 1.0) * 0.5;
        } //@formatter:on
        int uq = (int) (u * 1023.0);
        int vq = (int) (v * 1023.0);
        if (uq < 0) uq = 0;
        else if (uq > 1023) uq = 1023;
        if (vq < 0) vq = 0;
        else if (vq > 1023) vq = 1023;
        return (face << 20) | morton10(uq, vq);
    }

    private static int morton10(int x, int y) {
        return (part1By1_10(x) << 1) | part1By1_10(y);
    }

    private static int part1By1_10(int x) {
        x &= 0x3FF;
        x = (x | (x << 8)) & 0x00FF00FF;
        x = (x | (x << 4)) & 0x0F0F0F0F;
        x = (x | (x << 2)) & 0x33333333;
        x = (x | (x << 1)) & 0x55555555;
        return x;
    }

    // Scoped to one blast by prepareRayCaches; live terrain changes during tracing remain weakly consistent.
    static long emptySubMask(EmptySubMaskCache cache, ExtendedBlockStorage[] sections) {
        int slot = (System.identityHashCode(sections) * 0x9E3775CD) >>> (32 - EMPTY_CACHE_SHIFT);
        if (cache.arrays[slot] == sections) return cache.masks[slot];
        long mask = 0;
        int counted = Math.min(sections.length, Long.SIZE);
        for (int i = 0; i < counted; i++) {
            if (emptySection(sections[i])) mask |= (1L << i);
        }
        cache.arrays[slot] = sections;
        cache.masks[slot] = mask;
        return mask;
    }

    static final class EmptySubMaskCache {
        long epoch;
        final ExtendedBlockStorage[][] arrays = new ExtendedBlockStorage[1 << EMPTY_CACHE_SHIFT][];
        final long[] masks = new long[1 << EMPTY_CACHE_SHIFT];
    }

    static DecodedSection resolveDecoded(DecodedSection[] cache, ExtendedBlockStorage section) {
        int slot = (System.identityHashCode(section) * 0x9E3775CD) >>> (32 - RES_CACHE_SHIFT);
        DecodedSection ds = cache[slot];
        if (ds == null) {
            ds = new DecodedSection();
            cache[slot] = ds;
        }
        if (ds.section != section) ds.decode(section);
        return ds;
    }

    @Contract(pure = true)
    int count(boolean perSubchunk) {
        int cr = (radius + 15) >> 4;
        int minCX = (originX >> 4) - cr;
        int maxCX = (originX >> 4) + cr;
        int minCZ = (originZ >> 4) - cr;
        int maxCZ = (originZ >> 4) + cr;
        int minSubY = Math.max(0, ((originY - radius) - worldMinY) >> 4);
        int maxSubY = Math.min(subchunkPerChunk - 1, ((originY + radius) - worldMinY) >> 4);
        if (minSubY > maxSubY) return 0;
        double R2 = (radius + 14) * (radius + 14);
        int total = 0;
        for (int cx = minCX; cx <= maxCX; cx++) {
            double dx = ((cx << 4) + 8) - explosionX;
            double dx2 = dx * dx;
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                double dz = ((cz << 4) + 8) - explosionZ;
                double base = dx2 + dz * dz;
                if (base > R2) continue;
                double ry = Math.sqrt(R2 - base);
                int firstY = (int) Math.ceil(((explosionY - ry) - worldMinY - 8.0) / 16.0);
                int lastY = (int) Math.floor(((explosionY + ry) - worldMinY - 8.0) / 16.0);
                if (firstY < minSubY) firstY = minSubY;
                if (lastY > maxSubY) lastY = maxSubY;
                if (perSubchunk) {
                    int countY = lastY - firstY + 1;
                    if (countY > 0) total += countY;
                } else if (lastY >= firstY) total++;
            }
        }
        return total;
    }

    void initializeAndStartWorkers() {
        if (rayCount <= 0) {
            U.putIntRelease(this, OFF_COLLECT_FINISHED, 1);
            U.putIntRelease(this, OFF_CONSOLIDATION_FINISHED, 1);
            U.putIntRelease(this, OFF_DESTROY_FINISHED, 1);
            return;
        }
        if (U.compareAndSetInt(this, OFF_POOL_ACQUIRED, 0, 1)) {
            pool = BombForkJoinPool.acquire();
        }
        registerJobIfNeeded();
        mirror = ChunkUtil.acquireMirrorMap(world);
        U.putIntRelease(this, OFF_MAP_ACQUIRED, 1);
        ForkJoinPool p = pool;
        sectionUpdates = new BulkSectionUpdates(world, p, this::fail);
        submitWorker(p, () -> {
            if (destroyFinished != 0) return;
            buildRayOrder();
            int minRayGrain = (rayOrder != null) ? 4096 : 512;
            new RayTracerTask(0, rayCount, computeTaskGrain(rayCount, minRayGrain)).invoke();
        });
    }

    private boolean submitWorker(ForkJoinPool executor, Runnable work) {
        U.getAndAddInt(this, OFF_ACTIVE_WORKER_TASKS, 1);
        if (cancelling) {
            workerFinished();
            return false;
        }
        try {
            executor.submit(() -> {
                try {
                    work.run();
                } catch (Throwable cause) {
                    fail(cause);
                } finally {
                    workerFinished();
                }
            });
            return true;
        } catch (RuntimeException | Error cause) {
            workerFinished();
            fail(cause);
            return false;
        }
    }

    private void workerFinished() {
        if (U.getAndAddInt(this, OFF_ACTIVE_WORKER_TASKS, -1) == 1) {
            if (cancelling) finishCancellation();
            else {
                if (pendingRays == 0) onAllRaysFinished();
                requestChunkPump();
            }
        }
    }

    private void fail(Throwable cause) {
        if (U.compareAndSetReference(this, OFF_FAILURE, null, cause)) {
            world.addScheduledTask(() -> {
                MainRegistry.logger.error("Nuclear terrain work failed in {} at {}, {}, {}",
                        world.provider.getDimension(), originX, originY, originZ, cause);
                cancel();
            });
        }
    }

    void releasePoolIfHeld() {
        unregisterJobIfNeeded();
        if (U.getAndSetInt(this, OFF_POOL_ACQUIRED, 0) != 0) {
            BombForkJoinPool.release(pool);
            pool = null;
        }
    }

    void registerJobIfNeeded() {
        if (U.compareAndSetInt(this, OFF_JOB_REGISTERED, 0, 1)) {
            BombForkJoinPool.register(pool, world.provider.getDimension(), this);
        }
    }

    void unregisterJobIfNeeded() {
        if (U.getAndSetInt(this, OFF_JOB_REGISTERED, 0) != 0) {
            BombForkJoinPool.unregister(pool, world.provider.getDimension(), this);
        }
    }

    private void buildRayOrder() {
        int n = rayCount;
        if (n <= 0) return;
        if (n < 200_000) return;
        long[] packed = new long[n];
        ForkJoinPool p = pool;
        int par = (p == null) ? 1 : p.getParallelism();
        boolean doParallelFill = par > 1 && n >= 512_000;
        if (doParallelFill) {
            int grain = computeTaskGrain(n, 16384);
            p.invoke(new BuildPackedTask(packed, 0, n, grain, invRayIndexScale));
        } else {
            for (int i = 0; i < n; i++) {
                double y = 1.0 - (i * invRayIndexScale) * 2.0;
                double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                double t = GOLDEN_ANGLE * i;
                double x = Math.cos(t) * r;
                double z = Math.sin(t) * r;
                int key = rayKeyCubeMorton(x, y, z);
                packed[i] = (((long) key) << 32) | (i & 0xFFFF_FFFFL);
            }
        }
        if (n >= 1_000_000) LongArrays.parallelRadixSort(packed);
        else LongArrays.radixSort(packed);

        int[] order = new int[n];
        if (par > 1 && n >= 1_000_000) {
            int grain = computeTaskGrain(n, 32768);
            p.invoke(new UnpackOrderTask(packed, order, 0, n, grain));
        } else {
            for (int i = 0; i < n; i++) order[i] = (int) packed[i];
        }
        rayOrder = order;
    }

    private int computeTaskGrain(int totalRays, int minGrain) {
        int p = pool == null ? 1 : pool.getParallelism();
        int targetTasks = Math.max(1, p << 2);
        int batch = (totalRays + targetTasks - 1) / targetTasks;
        batch = ceilPow2(batch);
        if (batch < minGrain) batch = minGrain;
        if (batch > totalRays && totalRays > 0) batch = totalRays;
        return batch;
    }

    private int computeKeyTaskGrain(int totalKeys) {
        int p = pool == null ? 1 : pool.getParallelism();
        int targetTasks = Math.max(1, p << 2);
        int batch = (totalKeys + targetTasks - 1) / targetTasks;
        batch = ceilPow2(batch);
        if (batch < 64) batch = 64;
        if (batch > totalKeys && totalKeys > 0) batch = totalKeys;
        return batch;
    }

    void onAllRaysFinished() {
        // A resumed ray can finish before its earlier worker has merged the prefix contributions.
        if (activeWorkerTasks != 0 || collectFinished != 0 || destroyFinished != 0 || failure != null) return;
        U.putIntRelease(this, OFF_COLLECT_FINISHED, 1);
        rayOrder = null;
        pausedRays = null;
        if (U.compareAndSetInt(this, OFF_CONSOLIDATION_STARTED, 0, 1)) {
            ForkJoinPool p = pool;
            if (p != null && !p.isShutdown()) submitWorker(p, this::runConsolidation);
            else runConsolidation();
        }
    }

    void queueChunkLoad(long chunkPos) {
        chunkLoadQueue.offer(chunkPos);
        requestChunkPump();
    }

    private void requestChunkPump() {
        if (destroyFinished != 0 || failure != null || System.nanoTime() >= admissionDeadline) return;
        if (!U.compareAndSetInt(this, OFF_ADMISSION_PUMP_QUEUED, 0, 1)) return;
        // addScheduledTask would run inline and reenter the request iterator.
        ChunkUtil.queueServerTask(world, this::pumpChunkAdmissions);
    }

    @ServerThread
    private void pumpChunkAdmissions() {
        U.putIntRelease(this, OFF_ADMISSION_PUMP_QUEUED, 0);
        long deadline = admissionDeadline;
        if (destroyFinished != 0 || failure != null || System.nanoTime() >= deadline) return;
        try {
            advanceWork(deadline);
        } catch (Throwable cause) {
            fail(cause);
        }
    }

    @ServerThread
    void trackChunkFuture(long chunkPos, CompletableFuture<Chunk> future) {
        chunksInFlight.put(chunkPos, future);
        if (!future.isDone()) future.whenComplete((result, cause) -> requestChunkPump());
    }

    @ServerThread
    void loadMissingChunksUntil(long deadline) {
        releaseQuiescentTraceChunks();
        while (System.nanoTime() < deadline
                && chunksInFlight.size() + ticketsPendingFlush.size() < BombConfig.chunksInFlight()) {
            long ck = chunkLoadQueue.relaxedPoll();
            if (ck == EMPTY_LONG) break;
            if (chunksInFlight.containsKey(ck) || ticketsPendingFlush.contains(ck)) continue;
            if (admittedChunks.containsKey(ck)) {
                processChunkLoadRequest(ck, admittedChunks.get(ck));
                continue;
            }
            if (!acquireChunkTicket(ck)) {
                chunkLoadQueue.offer(ck);
                break;
            }
            ticketsPendingFlush.add(ck);
        }
        if ((!ticketsPendingFlush.isEmpty() || notificationTicketsPendingFlush) && System.nanoTime() < deadline) {
            ChunkUtil.flushChunkTickets(world);
            notificationTicketsPendingFlush = false;
            for (int i = 0; i < ticketsPendingFlush.size(); i++) {
                long ck = ticketsPendingFlush.getLong(i);
                CompletableFuture<Chunk> future = ChunkUtil.blastChunkFuture(world, ck);
                // No holder after flush: ticket lost; re-queue.
                if (future != null) trackChunkFuture(ck, future);
                else queueChunkLoad(ck);
            }
            ticketsPendingFlush.clear();
        }
        harvestLoadedChunks(deadline);
    }

    @ServerThread
    private boolean acquireChunkTicket(long chunkPos) {
        if (ticketedChunks.contains(chunkPos)) return true;
        if (!ChunkUtil.tryAddBlastTicket(world, chunkPos)) return false;
        ticketedChunks.add(chunkPos);
        return true;
    }

    @ServerThread
    private void releaseQuiescentTraceChunks() {
        if (activeWorkerTasks != 0 || publicationPhase) return;
        // All queued and running trace jobs are counted. Paused rays retain their voxel/energy state,
        // not a reference to these chunks; a subsequent visit must pass admission again.
        for (long cp : admittedChunks.keySetLong()) {
            admittedChunks.remove(cp);
            if (ticketedChunks.remove(cp)) ChunkUtil.releaseChunkAsync(world, cp);
        }
    }

    /**
     * Consume completed requests only; the budget bounds work, not waits.
     */
    @ServerThread
    private void harvestLoadedChunks(long deadlineNanos) {
        if (chunksInFlight.isEmpty()) return;
        var it = chunksInFlight.long2ObjectEntrySet().fastIterator();
        while (it.hasNext() && System.nanoTime() < deadlineNanos) {
            var entry = it.next();
            CompletableFuture<Chunk> future = entry.getValue();
            if (!future.isDone()) continue;
            long ck = entry.getLongKey();
            it.remove();
            Chunk chunk = future.join();
            if (chunk != null) chunk = ChunkUtil.liveChunkNow(world, ck);
            if (chunk == null) {
                queueChunkLoad(ck);
                continue;
            }
            processChunkLoadRequest(ck, chunk);
            // Completion can clear chunksInFlight from the request callback.
            if (destroyFinished != 0 || failure != null || System.nanoTime() >= deadlineNanos) return;
        }
    }

    @ServerThread
    void releaseChunkTickets() {
        for (CompletableFuture<Chunk> future : chunksInFlight.values()) future.cancel(false);
        admittedChunks.clear();
        for (long ck : ticketedChunks) ChunkUtil.releaseChunkAsync(world, ck);
        ticketedChunks.clear();
        ticketsPendingFlush.clear();
        readinessLoads.clear();
        notificationTicketsPendingFlush = false;
        chunksInFlight.clear();
    }

    @ServerThread
    void processChunkLoadRequest(long chunkPos, @Nullable Chunk chunk) {
        if (chunk == null) return;
        admittedChunks.put(chunkPos, chunk);
        readinessLoads.remove(chunkPos);
        // Load event fires only at EMPTY->FULL.
        if (mirror != null) mirror.put(chunkPos, chunk);

        ForkJoinPool p = pool;
        boolean poolActive = (p != null && !p.isShutdown());
        MpscIntArrayListCollector waiters = waitingRoom.remove(chunkPos);
        if (waiters != null) {
            if (!poolActive) {
                ensureCollectorRegistered(chunkPos, waiters);
                queueChunkLoad(chunkPos);
            } else {
                IntArrayList batch = waiters.drain();
                if (!batch.isEmpty()) {
                    submitWorker(p, () -> new ResumeBatchTask(batch, 0, batch.size(),
                            computeTaskGrain(batch.size(), rayOrder == null ? 512 : 4096)).invoke());
                }
            }
        }
        // Keep completion blocked across the remove-to-submit gap.
        U.getAndAddInt(this, OFF_PENDING_POST_LOAD_WORK, 1);
        try {
            ConcurrentLinkedQueue<ResumeItem> q = postLoadQueues.remove(chunkPos);
            if (q != null) {
                if (!poolActive) {
                    ConcurrentLinkedQueue<ResumeItem> prev = postLoadQueues.putIfAbsent(chunkPos, q);
                    if (prev != null && prev != q) {
                        ResumeItem it;
                        while ((it = q.poll()) != null) prev.offer(it);
                    }
                    queueChunkLoad(chunkPos);
                    return;
                }
                ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
                ResumeItem item;
                while ((item = q.poll()) != null) {
                    switch (item.kind) {
                        case ResumeItem.APPLY_MASKS -> {
                            Int2ObjectOpenHashMap<BitMask> masks = item.masks;
                            submitPostLoadWork(p, () -> applyCarve(chunkPos, masks), () -> freeMasks(masks));
                        }
                        case ResumeItem.APPLY_AGG -> {
                            ChunkAgg agg = item.agg;
                            submitPostLoadWork(p, () -> applyAggregate(chunkPos, agg), agg::clear);
                        }
                    }
                }
            }
        } finally {
            finishPostLoadWork();
        }
    }

    void submitPostLoadWork(ForkJoinPool p, Runnable work, Runnable rejectedCleanup) {
        U.getAndAddInt(this, OFF_PENDING_POST_LOAD_WORK, 1);
        boolean accepted = submitWorker(p, () -> {
            try {
                work.run();
            } finally {
                finishPostLoadWork();
            }
        });
        if (!accepted) {
            try {
                rejectedCleanup.run();
            } finally {
                finishPostLoadWork();
            }
        }
    }

    void finishPostLoadWork() {
        int prev = U.getAndAddInt(this, OFF_PENDING_POST_LOAD_WORK, -1);
        if (prev - 1 == 0) maybeFinish();
    }

    /** Whether notifying {@code pos} would reach a fluid in a chunk this blast has yet to carve. */
    @ServerThread
    private boolean wakesUncarvedFluid(BlockPos pos, BlockPos.MutableBlockPos scratch) {
        for (EnumFacing dir : EnumFacing.VALUES) {
            scratch.setPos(pos).move(dir);
            long cp = ChunkPos.asLong(scratch.getX() >> 4, scratch.getZ() >> 4);
            if (publishedChunks.contains(cp)) continue;
            Chunk chunk = ChunkUtil.liveChunkNow(world, cp);
            if (chunk != null && chunk.getBlockState(scratch).getMaterial().isLiquid()) return true;
        }
        return false;
    }

    @ServerThread
    void flushDeferredFluidWakes() {
        publishedChunks.clear();
        for (LongArrayList wakes : finalWakes.values()) deferredFluidWakes.addAll(wakes);
        finalWakes.clear();
        // Canceled publications have already changed terrain. Their accepted fluid notifications
        // survive independently and replay under the world's finite native ticket allowance.
        if (!deferredFluidWakes.isEmpty()) BlastNotificationData.enqueue(world, deferredFluidWakes);
        deferredFluidWakes.clear();
    }

    @ServerThread
    void secondPass() {
        flushDeferredFluidWakes();
        if (!ticketedChunks.isEmpty()) flushNeighborhoodUpdates(ticketedChunks.toLongArray());
        sectionMaskByChunk.clear();
        lightingByChunk.clear();
    }

    @Override
    public boolean isComplete() {
        return failure == null && collectFinished != 0 && consolidationFinished != 0 && destroyFinished != 0;
    }

    @Override
    public boolean hasFailed() {
        return failure != null;
    }

    @Override
    public boolean isContained() {
        return isContained;
    }

    Int2ObjectOpenHashMap<BitMask> splitBySubchunk(@NotNull ConcurrentBitSet chunkMask) {
        Int2ObjectOpenHashMap<BitMask> m = new Int2ObjectOpenHashMap<>();
        try {
            int bit = chunkMask.nextSetBit(0);
            while (bit >= 0) {
                int yGlobal = worldHeight - 1 - (bit >>> 8);
                int subY = yGlobal >>> 4;
                int xLocal = (bit >>> 4) & 0xF;
                int zLocal = bit & 0xF;
                int yLocal = yGlobal & 0xF;
                int localIndex = Library.packLocal(xLocal, yLocal, zLocal);
                m.computeIfAbsent(subY, k -> new OffHeapBitSet(SUB_MASK_SIZE)).set(localIndex);
                bit = chunkMask.nextSetBit(bit + 1);
            }
            return m;
        } catch (RuntimeException | Error cause) {
            freeMasks(m);
            throw cause;
        }
    }

    Int2ObjectOpenHashMap<BitMask> buildMasksFromAgg(long cpLong, @NotNull ChunkAgg agg,
                                                     ExtendedBlockStorage @NotNull [] sections) {
        Int2ObjectOpenHashMap<BitMask> sub = new Int2ObjectOpenHashMap<>();
        try {
            Int2DoubleOpenHashMap dmg = agg.damage;
            Int2DoubleOpenHashMap len = agg.passLen;

            if (!dmg.isEmpty()) {
                ObjectIterator<Int2DoubleMap.Entry> it = dmg.int2DoubleEntrySet().fastIterator();
                while (it.hasNext()) {
                    Int2DoubleMap.Entry e = it.next();
                    int bitIndex = e.getIntKey();
                    int yGlobal = worldHeight - 1 - (bitIndex >>> 8);
                    int subY = yGlobal >>> 4;
                    int xLocal = (bitIndex >>> 4) & 0xF;
                    int zLocal = bitIndex & 0xF;
                    int yLocal = yGlobal & 0xF;
                    int localIndex = Library.packLocal(xLocal, yLocal, zLocal);
                    if (shouldDestroy(cpLong, bitIndex, sections, e.getDoubleValue(), len.get(bitIndex))) {
                        sub.computeIfAbsent(subY, k -> new OffHeapBitSet(SUB_MASK_SIZE)).set(localIndex);
                    }
                }
            }
            if (!len.isEmpty()) {
                ObjectIterator<Int2DoubleMap.Entry> it = len.int2DoubleEntrySet().fastIterator();
                while (it.hasNext()) {
                    Int2DoubleMap.Entry e = it.next();
                    int bitIndex = e.getIntKey();
                    if (dmg.containsKey(bitIndex)) continue;
                    int yGlobal = worldHeight - 1 - (bitIndex >>> 8);
                    int subY = yGlobal >>> 4;
                    int xLocal = (bitIndex >>> 4) & 0xF;
                    int zLocal = bitIndex & 0xF;
                    int yLocal = yGlobal & 0xF;
                    int localIndex = Library.packLocal(xLocal, yLocal, zLocal);
                    if (shouldDestroy(cpLong, bitIndex, sections, 0.0, e.getDoubleValue())) {
                        sub.computeIfAbsent(subY, k -> new OffHeapBitSet(SUB_MASK_SIZE)).set(localIndex);
                    }
                }
            }
            return sub;
        } catch (RuntimeException | Error cause) {
            freeMasks(sub);
            throw cause;
        }
    }

    private static void freeMasks(Int2ObjectOpenHashMap<BitMask> masks) {
        for (BitMask mask : masks.values()) mask.free();
        masks.clear();
    }

    boolean shouldDestroy(long cpLong, int bitIndex, ExtendedBlockStorage[] sections, double accumulatedDamage,
                          double passLen) {
        int yGlobal = worldHeight - 1 - (bitIndex >>> 8);
        int subY = yGlobal >>> 4;
        if (subY < 0 || subY >= sections.length) return false;
        ExtendedBlockStorage s = sections[subY];
        if (emptySection(s)) return false;
        int xLocal = (bitIndex >>> 4) & 0xF;
        int zLocal = bitIndex & 0xF;
        int yLocal = yGlobal & 0xF;
        IBlockState st = s.get(xLocal, yLocal, zLocal);
        if (st.getMaterial() == Material.AIR) return false;
        float resistance = getNukeResistance(st);
        if (accumulatedDamage >= (resistance * DAMAGE_THRESHOLD_MULT)) return true;
        return resistance <= LOW_R_BOUND && passLen >= LOW_R_PASS_LENGTH_BREAK;
    }

    @Override
    public void setDetonator(UUID detonator) {
        this.detonator = detonator;
    }

    @Override
    public void update(long msBudget) {
        if (destroyFinished != 0 || failure != null) return;
        try {
            long deadline = System.nanoTime() + msBudget * 1_000_000L;
            admissionDeadline = deadline;
            advanceWork(deadline);
        } catch (Throwable cause) {
            fail(cause);
        }
    }

    @ServerThread
    private void advanceWork(long deadline) {
        if (!publicationPhase) {
            loadMissingChunksUntil(deadline);
            if (failure != null || destroyFinished != 0) return;
            if (collectFinished == 0 || consolidationFinished == 0 || activeWorkerTasks != 0) return;
            // Consolidation retains only masks/damage, so no live trace chunk is needed from here on.
            releaseChunkTickets();
            chunkLoadQueue.clear();
            publicationPhase = true;
        }
        drainPublications(deadline);
    }

    @ServerThread
    private void drainPublications(long deadline) {
        while (System.nanoTime() < deadline && failure == null && destroyFinished == 0) {
            if (currentPublication == null) {
                currentPublication = publicationQueue.poll();
                if (currentPublication == null) {
                    if (drainFinalNotifications(deadline)) completeBlast();
                    return;
                }
                publicationNeighborhood = neighborhood(currentPublication.chunkPos);
            }
            if (!admitNeighborhood(publicationNeighborhood, deadline)) return;
            if (!publicationSubmitted) {
                publicationSubmitted = true;
                sectionUpdates.submit(currentPublication);
            }
            sectionUpdates.drain(deadline);
            if (failure != null || destroyFinished != 0 || sectionUpdates.hasPending()) return;
            if (publicationLighting == null) publicationLighting = flushNeighborhoodUpdates(publicationNeighborhood);
            if (!publicationLighting.isDone()) return;
            publicationLighting.join();
            releaseChunkTickets();
            publicationNeighborhood = null;
            publicationLighting = null;
            publicationSubmitted = false;
            currentPublication = null;
        }
    }

    private static long[] neighborhood(long chunkPos) {
        int cx = chunkX(chunkPos), cz = chunkZ(chunkPos), count = 0;
        long[] chunks = new long[9];
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (validChunk(cx + dx, cz + dz)) chunks[count++] = ChunkPos.asLong(cx + dx, cz + dz);
        }
        return count == chunks.length ? chunks : Arrays.copyOf(chunks, count);
    }

    @ServerThread
    private boolean admitNeighborhood(long[] chunks, long deadline) {
        if (ticketedChunks.isEmpty()) {
            // Reserve the entire group atomically: two blasts must not each retain half a group
            // while waiting for the other half of the same finite Forge allowance.
            if (!ChunkUtil.tryAddBlastTickets(world, chunks)) return false;
            for (long cp : chunks) ticketedChunks.add(cp);
            ChunkUtil.flushChunkTickets(world);
        }
        for (long cp : chunks) {
            if (admittedChunks.containsKey(cp) && ChunkUtil.liveChunkNow(world, cp) == admittedChunks.get(cp)) continue;
            admittedChunks.remove(cp);
            if (chunksInFlight.containsKey(cp)) continue;
            trackChunkFuture(cp, ChunkUtil.blastChunkFuture(world, cp));
        }
        harvestLoadedChunks(deadline);
        for (long cp : chunks) if (!admittedChunks.containsKey(cp)) return false;
        return true;
    }

    @ServerThread
    private CompletableFuture<?> flushNeighborhoodUpdates(long[] chunks) {
        java.util.ArrayList<CompletableFuture<?>> futures = new java.util.ArrayList<>();
        for (long cp : chunks) {
            long mask = sectionMaskByChunk.remove(cp);
            CompletableFuture<?> carved = lightingByChunk.remove(cp);
            if (mask == 0 && carved == null) continue;
            Chunk chunk = ChunkUtil.liveChunkNow(world, cp);
            CompletableFuture<?> lighting = ChunkUtil.updateLight(world, chunk, LongLists.EMPTY_LIST, mask);
            if (carved != null && carved != lighting) lighting = CompletableFuture.allOf(carved, lighting);
            ChunkUtil.broadcastWholesaleResend(world, cp, lighting);
            futures.add(lighting);
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0]));
    }

    @ServerThread
    private boolean drainFinalNotifications(long deadline) {
        if (finalChunks == null) {
            LongOpenHashSet chunks = new LongOpenHashSet();
            for (int i = 0; i < deferredFluidWakes.size(); i++) {
                long pos = deferredFluidWakes.getLong(i);
                long cp = ChunkPos.asLong(Library.getBlockPosX(pos) >> 4, Library.getBlockPosZ(pos) >> 4);
                finalWakes.computeIfAbsent(cp, ignored -> new LongArrayList()).add(pos);
                chunks.add(cp);
            }
            deferredFluidWakes.clear();
            publishedChunks.clear();
            chunks.addAll(sectionMaskByChunk.keySet());
            chunks.addAll(lightingByChunk.keySet());
            finalChunks = chunks.toLongArray();
        }
        while (finalChunkIndex < finalChunks.length && System.nanoTime() < deadline) {
            long cp = finalChunks[finalChunkIndex];
            if (publicationNeighborhood == null) publicationNeighborhood = neighborhood(cp);
            if (!admitNeighborhood(publicationNeighborhood, deadline)) return false;
            if (!finalNotified) {
                LongArrayList wakes = finalWakes.remove(cp);
                if (wakes != null) {
                    BlockPos.MutableBlockPos pos = TL_POS.get();
                    for (int i = 0; i < wakes.size(); i++) {
                        long packed = wakes.getLong(i);
                        pos.setPos(Library.getBlockPosX(packed), Library.getBlockPosY(packed), Library.getBlockPosZ(packed));
                        world.notifyNeighborsOfStateChange(pos, Blocks.AIR, true);
                    }
                }
                publicationLighting = flushNeighborhoodUpdates(publicationNeighborhood);
                finalNotified = true;
            }
            if (!publicationLighting.isDone()) return false;
            publicationLighting.join();
            releaseChunkTickets();
            publicationNeighborhood = null;
            publicationLighting = null;
            finalNotified = false;
            finalChunkIndex++;
        }
        return finalChunkIndex == finalChunks.length;
    }

    @ServerThread
    private void completeBlast() {
        U.putIntRelease(this, OFF_DESTROY_FINISHED, 1);
        sectionUpdates.close();
        releasePoolIfHeld();
        if (U.getAndSetInt(this, OFF_MAP_ACQUIRED, 0) != 0) ChunkUtil.releaseMirrorMap(world);
        releaseChunkTickets();
    }

    @Override
    public void cancel() {
        assert world.getMinecraftServer().isCallingFromMinecraftThread();
        cancelling = true;
        U.putIntRelease(this, OFF_COLLECT_FINISHED, 1);
        U.putIntRelease(this, OFF_CONSOLIDATION_FINISHED, 1);
        U.putIntRelease(this, OFF_DESTROY_FINISHED, 1);
        if (sectionUpdates != null) sectionUpdates.close();
        if (!mainCleanupDone) {
            mainCleanupDone = true;
            try {
                if (currentPublication != null && !publicationSubmitted) currentPublication.discard();
                currentPublication = null;
                // Cancellation rejects new work, but accepted terrain still needs its client update.
                secondPass();
            } finally {
                sectionMaskByChunk.clear();
                lightingByChunk.clear();
                releasePoolIfHeld();
                if (U.getAndSetInt(this, OFF_MAP_ACQUIRED, 0) != 0) ChunkUtil.releaseMirrorMap(world);
                releaseChunkTickets();
                if (activeWorkerTasks == 0) finishCancellation();
            }
        }
        if (activeWorkerTasks == 0) finishCancellation();
    }

    private void finishCancellation() {
        // The last worker can release these after server shutdown, when its task queue no longer runs.
        if (!U.compareAndSetInt(this, OFF_WORKER_CLEANUP, 0, 1)) return;
        NukeCarveWork work;
        while ((work = publicationQueue.poll()) != null) work.discard();
        if (waitingRoom != null) waitingRoom.clear();
        if (postLoadQueues != null) {
            for (long cp : postLoadQueues.keySetLong()) {
                ConcurrentLinkedQueue<ResumeItem> q = postLoadQueues.remove(cp);
                if (q != null) {
                    ResumeItem item;
                    while ((item = q.poll()) != null) {
                        switch (item.kind) {
                            case ResumeItem.APPLY_MASKS -> {
                                if (item.masks != null) {
                                    ObjectIterator<Int2ObjectMap.Entry<BitMask>> it = item.masks.int2ObjectEntrySet()
                                                                                                .fastIterator();
                                    while (it.hasNext()) it.next().getValue().free();
                                    item.masks.clear();
                                }
                            }
                            case ResumeItem.APPLY_AGG -> {
                                if (item.agg != null) item.agg.clear();
                            }
                        }
                    }
                }
            }
            postLoadQueues.clear();
        }

        rayOrder = null;
        pausedRays = null;
        if (destructionMap != null) destructionMap.clear();
        if (aggMap != null) aggMap.clear();
        if (chunkLoadQueue != null) chunkLoadQueue.clear();
    }

    @Override
    public void cancelJob() {
        cancel();
    }

    void runConsolidation() {
        if (algorithm == 2) {
            long[] keys = aggMap.keySetLong();
            ChunkAgg[] aggregates = new ChunkAgg[keys.length];
            for (int i = 0; i < keys.length; i++) {
                aggregates[i] = aggMap.remove(keys[i]);
            }
            if (keys.length != 0) {
                int thresh = computeKeyTaskGrain(keys.length);
                new ConsolidateAggTask(keys, aggregates, 0, keys.length, thresh).invoke();
            }
        } else {
            long[] keys = destructionMap.keySetLong();
            if (keys.length != 0) {
                int thresh = computeKeyTaskGrain(keys.length);
                new ConsolidateMaskTask(keys, 0, keys.length, thresh).invoke();
            }
        }
        U.putIntRelease(this, OFF_CONSOLIDATION_FINISHED, 1);
        maybeFinish();
    }

    void maybeFinish() {
        requestChunkPump();
    }

    void applyCarve(long chunkPos, Int2ObjectOpenHashMap<BitMask> masks) {
        if (masks.isEmpty()) return;
        enqueuePublication(new NukeCarveWork(chunkPos, masks));
    }

    void applyAggregate(long chunkPos, ChunkAgg aggregate) {
        enqueuePublication(new NukeCarveWork(chunkPos, aggregate));
    }

    private void enqueuePublication(NukeCarveWork work) {
        if (cancelling) {
            work.discard();
            return;
        }
        publicationQueue.offer(work);
        if (cancelling && publicationQueue.remove(work)) work.discard();
    }

    private final class NukeCarveWork extends BulkSectionUpdates.Work {
        private final long chunkPos;
        private final @Nullable ChunkAgg aggregate;
        private @Nullable Int2ObjectOpenHashMap<BitMask> masks;
        private final long observed;
        private final Long2ObjectOpenHashMap<IBlockState> modified = LONG2OBJECT_POOL.borrow();
        private final LongArrayList neighbors = LONG_LIST_POOL.borrow();
        private final Long2LongOpenHashMap neighborMask = LONG2LONG_POOL.borrow();
        private long selfMask;
        private boolean transferred;

        NukeCarveWork(long chunkPos, Int2ObjectOpenHashMap<BitMask> masks) {
            super(chunkPos);
            this.chunkPos = chunkPos;
            this.aggregate = null;
            this.masks = masks;
            long observed = 0;
            for (var iterator = masks.keySet().iterator(); iterator.hasNext();) observed |= 1L << iterator.nextInt();
            this.observed = observed;
        }

        NukeCarveWork(long chunkPos, ChunkAgg aggregate) {
            super(chunkPos);
            this.chunkPos = chunkPos;
            this.aggregate = aggregate;
            long observed = 0;
            for (var iterator = aggregate.damage.keySet().iterator(); iterator.hasNext();) {
                observed |= 1L << ((worldHeight - 1 - (iterator.nextInt() >>> 8)) >>> 4);
            }
            for (var iterator = aggregate.passLen.keySet().iterator(); iterator.hasNext();) {
                observed |= 1L << ((worldHeight - 1 - (iterator.nextInt() >>> 8)) >>> 4);
            }
            this.observed = observed;
        }

        @Override
        protected long observedSections() {
            return observed;
        }

        @Override
        protected void calculate(SectionSnapshot snapshot) {
            if (aggregate != null) masks = buildMasksFromAgg(chunkPos, aggregate, snapshot.sections);
            int cx = chunkX(chunkPos);
            int cz = chunkZ(chunkPos);
            Long2ObjectOpenHashMap<IBlockState> local = TL_MODIFIED.get();
            LongOpenHashSet edges = TL_EDGES.get();
            for (var iterator = masks.int2ObjectEntrySet().fastIterator(); iterator.hasNext();) {
                var entry = iterator.next();
                int subY = entry.getIntKey();
                local.clear();
                edges.clear();
                boolean carved = ChunkUtil.carveSnapshot(world, cx, cz, subY, snapshot, entry.getValue(), edges, local);
                if (carved || !local.isEmpty() || emptySection(snapshot.sections[subY])) selfMask |= 1L << subY;
                modified.putAll(local);
                for (LongIterator it = edges.iterator(); it.hasNext();) {
                    long pos = it.nextLong();
                    neighbors.add(pos);
                    long neighbor = ChunkPos.asLong(Library.getBlockPosX(pos) >> 4, Library.getBlockPosZ(pos) >> 4);
                    neighborMask.put(neighbor,
                            neighborMask.get(neighbor) | (1L << ((Library.getBlockPosY(pos) - worldMinY) >>> 4)));
                }
            }
        }

        @Override
        protected void published(Chunk chunk) {
            if (selfMask != 0) chunkFixup(chunkPos, modified, neighbors, selfMask, neighborMask);
        }

        @Override
        protected boolean readyToPublish(Chunk chunk) {
            return (modified.isEmpty() && neighbors.isEmpty()) || notificationNeighborhoodReady(chunkPos);
        }

        @Override
        protected void missing() {
            if (aggregate != null) applyAggregate(chunkPos, aggregate);
            else applyCarve(chunkPos, masks);
            transferred = true;
        }

        @Override
        protected void clearAttempt() {
            modified.clear();
            neighbors.clear();
            neighborMask.clear();
            selfMask = 0;
            if (aggregate != null && masks != null) {
                freeMasks(masks);
                masks = null;
            }
        }

        @Override
        protected void discard() {
            clearAttempt();
            if (!transferred) {
                if (aggregate != null) aggregate.clear();
                else if (masks != null) {
                    freeMasks(masks);
                }
            }
            LONG2OBJECT_POOL.recycle(modified);
            LONG_LIST_POOL.recycle(neighbors);
            LONG2LONG_POOL.recycle(neighborMask);
        }
    }

    boolean notificationNeighborhoodReady(long chunkPos) {
        for (long cp : publicationNeighborhood) {
            if (!ticketedChunks.contains(cp) || ChunkUtil.liveChunkNow(world, cp) != admittedChunks.get(cp)) return false;
        }
        return true;
    }

    void chunkFixup(long cpLong, Long2ObjectOpenHashMap<IBlockState> modified, LongArrayList neighborNotifies,
                    long selfMask,
                    Long2LongOpenHashMap neighborMask) {
        sectionMaskByChunk.put(cpLong, sectionMaskByChunk.get(cpLong) | selfMask);
        ObjectIterator<Long2LongMap.Entry> iterator = neighborMask.long2LongEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2LongMap.Entry e = iterator.next();
            sectionMaskByChunk.put(e.getLongKey(), sectionMaskByChunk.get(e.getLongKey()) | e.getLongValue());
        }
        BlockPos.MutableBlockPos p = TL_POS.get();
        IBlockState air = Blocks.AIR.getDefaultState();
        LongArrayList lightChanges = new LongArrayList();
        Chunk selfChunk = ChunkUtil.liveChunkNow(world, cpLong);
        if (selfChunk != null) {
            ObjectIterator<Long2ObjectMap.Entry<IBlockState>> modIter = modified.long2ObjectEntrySet().fastIterator();
            while (modIter.hasNext()) {
                Long2ObjectMap.Entry<IBlockState> entry = modIter.next();
                long lp = entry.getLongKey();
                int x = Library.getBlockPosX(lp);
                int y = Library.getBlockPosY(lp);
                int z = Library.getBlockPosZ(lp);
                IBlockState oldState = entry.getValue();
                p.setPos(x, y, z);
                if (oldState.getBlock().hasTileEntity(oldState)) {
                    ChunkUtil.replaceEmptied(world, p, air, 3);
                } else {
                    if (ChunkUtil.postCarveBlockUpdate(world, selfChunk, p, oldState, air)) lightChanges.add(lp);
                }
            }
        }
        publishedChunks.add(cpLong);
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (int i = 0, n = neighborNotifies.size(); i < n; i++) {
            long lp = neighborNotifies.getLong(i);
            p.setPos(Library.getBlockPosX(lp), Library.getBlockPosY(lp), Library.getBlockPosZ(lp));
            if (wakesUncarvedFluid(p, neighbour)) {
                deferredFluidWakes.add(lp);
                continue;
            }
            world.notifyNeighborsOfStateChange(p, air.getBlock(), true);
            // Native neighborChanged also handles support-breaking in 1.12.

        }
        if (selfChunk != null) {
            CompletableFuture<?> lighting = ChunkUtil.updateLight(world, selfChunk, lightChanges, selfMask);
            CompletableFuture<?> earlier = lightingByChunk.put(cpLong, lighting);
            if (earlier != null && earlier != lighting) {
                lightingByChunk.put(cpLong, CompletableFuture.allOf(earlier, lighting));
            }
        }
    }

    void handleMissingChunk(LocalAgg agg, long chunkPos, int dirIndex) {
        MpscIntArrayListCollector group = waitingRoom.get(chunkPos);
        if (group == null) {
            MpscIntArrayListCollector created = new MpscIntArrayListCollector();
            MpscIntArrayListCollector prev = waitingRoom.putIfAbsent(chunkPos, created);
            group = (prev != null) ? prev : created;
            if (prev == null) {
                group.push(dirIndex);
                ensureCollectorRegistered(chunkPos, group);
                queueChunkLoad(chunkPos);
                return;
            }
        }
        agg.deferMissing(chunkPos, dirIndex);
    }

    void flushDeferredMissing(LocalAgg agg) {
        if (!agg.hasDeferredMissing()) return;
        Long2ObjectOpenHashMap<IntArrayList> map = agg.missingChunks();
        ObjectIterator<Long2ObjectMap.Entry<IntArrayList>> iterator = map.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2ObjectMap.Entry<IntArrayList> entry = iterator.next();
            IntArrayList list = entry.getValue();
            if (list == null || list.isEmpty()) continue;
            long chunkPos = entry.getLongKey();
            MpscIntArrayListCollector collector = waitingRoom.get(chunkPos);
            boolean amFirst = false;
            if (collector == null) {
                MpscIntArrayListCollector created = new MpscIntArrayListCollector();
                MpscIntArrayListCollector prev = waitingRoom.putIfAbsent(chunkPos, created);
                if (prev == null) {
                    collector = created;
                    amFirst = true;
                } else collector = prev;
            }
            collector.pushBatch(list);
            boolean requeued = ensureCollectorRegistered(chunkPos, collector);
            if (amFirst || requeued) queueChunkLoad(chunkPos);
            list.clear();
            INT_LIST_POOL.recycle(list);
        }
        map.clear();
    }

    boolean ensureCollectorRegistered(long chunkPos, MpscIntArrayListCollector collector) {
        while (true) {
            MpscIntArrayListCollector current = waitingRoom.get(chunkPos);
            if (current == collector) return false;
            if (current == null) {
                if (waitingRoom.putIfAbsent(chunkPos, collector) == null) return true;
                continue;
            }
            IntArrayList drained = collector.drain();
            if (!drained.isEmpty()) {
                for (int i = drained.size() - 1; i >= 0; i--) current.push(drained.getInt(i));
            }
            collector = current;
        }
    }

    void enqueueForMissingChunk(long chunkPos, ResumeItem item) {
        ConcurrentLinkedQueue<ResumeItem> q = postLoadQueues.get(chunkPos);
        if (q == null) {
            ConcurrentLinkedQueue<ResumeItem> created = new ConcurrentLinkedQueue<>();
            created.offer(item);
            ConcurrentLinkedQueue<ResumeItem> prev = postLoadQueues.putIfAbsent(chunkPos, created);
            if (prev == null) {
                queueChunkLoad(chunkPos);
                return;
            }
            q = created;
        } else {
            q.offer(item);
        }
        if (ensurePostLoadQueueRegistered(chunkPos, q)) queueChunkLoad(chunkPos);
    }

    boolean ensurePostLoadQueueRegistered(long chunkPos, ConcurrentLinkedQueue<ResumeItem> queue) {
        while (true) {
            ConcurrentLinkedQueue<ResumeItem> current = postLoadQueues.get(chunkPos);
            if (current == queue) return false;
            if (current == null) {
                if (postLoadQueues.putIfAbsent(chunkPos, queue) == null) return true;
                continue;
            }
            ResumeItem item;
            while ((item = queue.poll()) != null) current.offer(item);
            queue = current;
        }
    }

    private long[] pausedRays() {
        long[] states = pausedRays;
        if (states != null) return states;
        synchronized (this) {
            if (pausedRays == null) pausedRays = new long[Math.multiplyExact(rayCount, PAUSED_RAY_WORDS)];
            return pausedRays;
        }
    }

    private void pauseRay(int index, int x, int y, int z, double energy, double position,
                          double maxX, double maxY, double maxZ) {
        long[] states = pausedRays();
        int offset = index * PAUSED_RAY_WORDS;
        states[offset] = Double.doubleToRawLongBits(energy);
        states[offset + 1] = Library.blockPosToLong(x, y, z);
        states[offset + 2] = Double.doubleToRawLongBits(position);
        states[offset + 3] = Double.doubleToRawLongBits(maxX);
        states[offset + 4] = Double.doubleToRawLongBits(maxY);
        states[offset + 5] = Double.doubleToRawLongBits(maxZ);
    }

    boolean traceSingle(int dirIndex, LocalAgg agg) {
        double energy = strength * INITIAL_ENERGY_FACTOR;
        double px = explosionX, py = explosionY, pz = explosionZ;
        int x = originX, y = originY, z = originZ;
        double dirY = 1.0 - (dirIndex * invRayIndexScale) * 2.0;
        double r = Math.sqrt(Math.max(0.0, 1.0 - dirY * dirY));
        double t = GOLDEN_ANGLE * dirIndex;
        double dirX = Math.cos(t) * r;
        double dirZ = Math.sin(t) * r;
        double currentRayPosition = 0.0;
        double absDirX = Math.abs(dirX);
        int stepX = (absDirX < RAY_DIRECTION_EPSILON) ? 0 : (dirX > 0 ? 1 : -1);
        double tDeltaX = (stepX == 0) ? Double.POSITIVE_INFINITY : 1.0 / absDirX;

        double absDirY = Math.abs(dirY);
        int stepY = (absDirY < RAY_DIRECTION_EPSILON) ? 0 : (dirY > 0 ? 1 : -1);
        double tDeltaY = (stepY == 0) ? Double.POSITIVE_INFINITY : 1.0 / absDirY;

        double absDirZ = Math.abs(dirZ);
        int stepZ = (absDirZ < RAY_DIRECTION_EPSILON) ? 0 : (dirZ > 0 ? 1 : -1);
        double tDeltaZ = (stepZ == 0) ? Double.POSITIVE_INFINITY : 1.0 / absDirZ;

        double radiusLimit = radius - PROCESSING_EPSILON;
        boolean useAggDamage = (algorithm == 2);

        double tMaxX = (stepX == 0) ? Double.POSITIVE_INFINITY : ((stepX > 0 ? (x + 1 - px) : (px - x)) * tDeltaX);
        double tMaxY = (stepY == 0) ? Double.POSITIVE_INFINITY : ((stepY > 0 ? (y + 1 - py) : (py - y)) * tDeltaY);
        double tMaxZ = (stepZ == 0) ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? (z + 1 - pz) : (pz - z)) * tDeltaZ);

        long[] states = pausedRays;
        int stateOffset = dirIndex * PAUSED_RAY_WORDS;
        // Positive energy marks a saved continuation; the waiting-room handoff publishes all six words.
        if (states != null && states[stateOffset] != 0) {
            energy = Double.longBitsToDouble(states[stateOffset]);
            long packed = states[stateOffset + 1];
            x = Library.getBlockPosX(packed);
            y = Library.getBlockPosY(packed);
            z = Library.getBlockPosZ(packed);
            currentRayPosition = Double.longBitsToDouble(states[stateOffset + 2]);
            tMaxX = Double.longBitsToDouble(states[stateOffset + 3]);
            tMaxY = Double.longBitsToDouble(states[stateOffset + 4]);
            tMaxZ = Double.longBitsToDouble(states[stateOffset + 5]);
            states[stateOffset] = 0;
        }

        long cachedCPLong = 0L;
        ExtendedBlockStorage[] sections = null;
        int lastCX = Integer.MIN_VALUE, lastCZ = Integer.MIN_VALUE;
        int chunkMinX = 0, chunkMaxX = 0, chunkMinZ = 0, chunkMaxZ = 0;
        long emptySubMask = 0;
        long bitsetCPLong = Long.MIN_VALUE;
        ConcurrentBitSet currentBits = null;
        int loopCount = 0;
        int maxY = worldMinY + worldHeight;
        DecodedSection[] resCache = TL_RES_CACHE.get();
        EmptySubMaskCache emptyCache = TL_EMPTY_CACHE.get();
        prepareRayCaches(resCache, emptyCache);
        ExtendedBlockStorage lastStorage = null;
        DecodedSection decoded = null;

        try {
            if (energy <= 0) return true;
            while (energy > 0) {
                if ((loopCount++ & 0x3FF) == 0) {
                    if (destroyFinished != 0 || y < worldMinY || y >= maxY || Thread.currentThread().isInterrupted())
                        break;
                    if (currentRayPosition >= radiusLimit) break;
                } else {
                    if (currentRayPosition >= radiusLimit) break;
                    if (y < worldMinY || y >= maxY) break;
                }
                int cx = x >> 4;
                int cz = z >> 4;

                if (cx != lastCX || cz != lastCZ) {
                    cachedCPLong = ChunkPos.asLong(cx, cz);
                    sections = ChunkUtil.getLoadedSections(admittedChunks, cachedCPLong);
                    if (sections == null) {
                        pauseRay(dirIndex, x, y, z, energy, currentRayPosition, tMaxX, tMaxY, tMaxZ);
                        handleMissingChunk(agg, cachedCPLong, dirIndex);
                        return false;
                    }
                    lastCX = cx;
                    lastCZ = cz;
                    chunkMinX = cx << 4;
                    chunkMaxX = chunkMinX + 16;
                    chunkMinZ = cz << 4;
                    chunkMaxZ = chunkMinZ + 16;
                    emptySubMask = emptySubMask(emptyCache, sections);
                    if (!useAggDamage && bitsetCPLong != cachedCPLong) {
                        currentBits = getOrCreateDestructionBitSet(cachedCPLong);
                        bitsetCPLong = cachedCPLong;
                        }
                }

                int subY = (y - worldMinY) >> 4;
                if (subY < 0 || subY >= sections.length) break;
                boolean subIsEmpty = ((emptySubMask >>> subY) & 1) != 0;
                if (subIsEmpty) {
                    double minY = (subY << 4) + worldMinY;
                    double maxYd = minY + 16.0;

                    double exitX = stepX > 0 ? (chunkMaxX - px) * tDeltaX
                            : stepX < 0 ? (px - chunkMinX) * tDeltaX : Double.POSITIVE_INFINITY;
                    double exitY = stepY > 0 ? (maxYd - py) * tDeltaY
                            : stepY < 0 ? (py - minY) * tDeltaY : Double.POSITIVE_INFINITY;
                    double exitZ = stepZ > 0 ? (chunkMaxZ - pz) * tDeltaZ
                            : stepZ < 0 ? (pz - chunkMinZ) * tDeltaZ : Double.POSITIVE_INFINITY;
                    double tExitAbs = Math.min(exitX, Math.min(exitY, exitZ));
                    currentRayPosition = Math.min(tExitAbs, radius);
                    if (currentRayPosition >= radiusLimit) break;

                    x = (int) Math.floor(px + dirX * currentRayPosition);
                    y = (int) Math.floor(py + dirY * currentRayPosition);
                    z = (int) Math.floor(pz + dirZ * currentRayPosition);
                    // Select the cell beyond every crossed face at the same geometric distance.
                    // Staying on the empty side and then advancing a whole voxel skips a thin wall.
                    if (exitX == tExitAbs) x = stepX > 0 ? chunkMaxX : chunkMinX - 1;
                    if (exitY == tExitAbs) y = stepY > 0 ? (int) maxYd : (int) minY - 1;
                    if (exitZ == tExitAbs) z = stepZ > 0 ? chunkMaxZ : chunkMinZ - 1;

                    tMaxX = (stepX == 0) ? Double.POSITIVE_INFINITY : ((stepX > 0 ? (x + 1 - px) : (px - x)) * tDeltaX);
                    tMaxY = (stepY == 0) ? Double.POSITIVE_INFINITY : ((stepY > 0 ? (y + 1 - py) : (py - y)) * tDeltaY);
                    tMaxZ = (stepZ == 0) ? Double.POSITIVE_INFINITY : ((stepZ > 0 ? (z + 1 - pz) : (pz - z)) * tDeltaZ);
                    continue;
                }
                ExtendedBlockStorage storage = sections[subY];
                if (!emptySection(storage) && storage != lastStorage) {
                    decoded = resolveDecoded(resCache, storage);
                    lastStorage = storage;
                }

                int xLocal = x & 0xF, yLocal = y & 0xF, zLocal = z & 0xF;
                float resistance = emptySection(storage) ? -1F : decoded.resistanceAt(xLocal, yLocal, zLocal);

                double tExitVoxel = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
                double segLen = tExitVoxel - currentRayPosition;
                double remaining = radius - currentRayPosition;
                if (remaining <= PROCESSING_EPSILON) break;
                boolean clipAtRadius = segLen > remaining - 1e-12;
                if (clipAtRadius) segLen = remaining;

                if (resistance >= 0 && segLen > PROCESSING_EPSILON) {
                    if (resistance >= NUKE_RESISTANCE_CUTOFF) {
                        energy = 0;
                    } else {
                        double distFrac = Math.max(currentRayPosition, MIN_EFFECTIVE_DIST_FOR_ENERGY_CALC) * invRadius;
                        double energyLoss = getEnergyLossFactor(resistance, distFrac) * segLen;
                        energy -= energyLoss;

                        if (useAggDamage) {
                            int bitIndex = ((worldHeight - 1 - (y - worldMinY)) << 8) | ((x & 0xF) << 4) | (z & 0xF);
                            double damageInc = Math.max(DAMAGE_PER_BLOCK * segLen, energyLoss) * INITIAL_ENERGY_FACTOR;
                            agg.recordHit(cachedCPLong, bitIndex, damageInc, segLen);
                        } else if (energy > 0) {
                            // 1.7: destroy non-air cells crossed.
                            int bitIndex = ((worldHeight - 1 - (y - worldMinY)) << 8) | ((x & 0xF) << 4) | (z & 0xF);
                            currentBits.set(bitIndex);
                        }
                    }
                }

                currentRayPosition = tExitVoxel;
                if (energy <= 0.0 || clipAtRadius) break;
                if (tMaxX < tMaxY) {
                    if (tMaxX < tMaxZ) {
                        x += stepX;
                        tMaxX += tDeltaX;
                    } else {
                        z += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                } else {
                    if (tMaxY < tMaxZ) {
                        y += stepY;
                        tMaxY += tDeltaY;
                    } else {
                        z += stepZ;
                        tMaxZ += tDeltaZ;
                    }
                }
            }
            if (energy > 0) isContained = false;
            return true;
        } catch (Exception e) {
            fail(e);
            return true;
        }
    }

    void mergeLocalAgg(LocalAgg agg) {
        if (algorithm != 2) return;
        ObjectIterator<Long2ObjectMap.Entry<IntDoubleAccumulator>> dIter = agg.localDamage.long2ObjectEntrySet()
                                                                                          .fastIterator();
        while (dIter.hasNext()) {
            Long2ObjectMap.Entry<IntDoubleAccumulator> entry = dIter.next();
            getOrCreateChunkAgg(entry.getLongKey()).merge(entry.getValue(), true);
        }
        ObjectIterator<Long2ObjectMap.Entry<IntDoubleAccumulator>> lIter = agg.localLen.long2ObjectEntrySet()
                                                                                       .fastIterator();
        while (lIter.hasNext()) {
            Long2ObjectMap.Entry<IntDoubleAccumulator> entry = lIter.next();
            getOrCreateChunkAgg(entry.getLongKey()).merge(entry.getValue(), false);
        }
        agg.localDamage.clear();
        agg.localLen.clear();
        agg.clear();
    }

    ChunkAgg getOrCreateChunkAgg(long chunkPos) {
        return aggMap.computeIfAbsent(chunkPos, ignored -> new ChunkAgg());
    }

    ConcurrentBitSet getOrCreateDestructionBitSet(long chunkPos) {
        ConcurrentBitSet bits = destructionMap.get(chunkPos);
        if (bits != null) return bits;
        ConcurrentBitSet created = new ConcurrentBitSet(bitsetSize);
        ConcurrentBitSet previous = destructionMap.putIfAbsent(chunkPos, created);
        return previous != null ? previous : created;
    }

    static final class BuildPackedTask extends RecursiveAction {
        final long[] packed;
        final int lo, hi, threshold;
        final double invRayIndexScale;

        BuildPackedTask(long[] packed, int lo, int hi, int threshold, double invRayIndexScale) {
            this.packed = packed;
            this.lo = lo;
            this.hi = hi;
            this.threshold = Math.max(1, threshold);
            this.invRayIndexScale = invRayIndexScale;
        }

        @Override
        protected void compute() {
            int len = hi - lo;
            if (len <= threshold) {
                for (int i = lo; i < hi; i++) {
                    double y = 1.0 - (i * invRayIndexScale) * 2.0;
                    double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
                    double t = GOLDEN_ANGLE * i;
                    double x = Math.cos(t) * r;
                    double z = Math.sin(t) * r;
                    int key = rayKeyCubeMorton(x, y, z);
                    packed[i] = (((long) key) << 32) | (i & 0xFFFF_FFFFL);
                }
                return;
            }
            int mid = lo + (len >>> 1);
            invokeAll(new BuildPackedTask(packed, lo, mid, threshold, invRayIndexScale),
                    new BuildPackedTask(packed, mid, hi, threshold, invRayIndexScale));
        }
    }

    static final class UnpackOrderTask extends RecursiveAction {
        final long[] packed;
        final int[] order;
        final int lo, hi, threshold;

        UnpackOrderTask(long[] packed, int[] order, int lo, int hi, int threshold) {
            this.packed = packed;
            this.order = order;
            this.lo = lo;
            this.hi = hi;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void compute() {
            int len = hi - lo;
            if (len <= threshold) {
                for (int i = lo; i < hi; i++) order[i] = (int) packed[i];
                return;
            }
            int mid = lo + (len >>> 1);
            invokeAll(new UnpackOrderTask(packed, order, lo, mid, threshold),
                    new UnpackOrderTask(packed, order, mid, hi, threshold));
        }
    }

    private void prepareRayCaches(DecodedSection[] resistance, EmptySubMaskCache empty) {
        if (empty.epoch == cacheEpoch) return;
        empty.epoch = cacheEpoch;
        // A reused worker can see new content without either the section or its array being replaced.
        Arrays.fill(empty.arrays, null);
        for (DecodedSection decoded : resistance) {
            if (decoded != null) decoded.section = null;
        }
    }

    static final class DecodedSection {
        static final int MAX_LUT = 4096;
        @Nullable ExtendedBlockStorage section;
        @Nullable BitArray storage;
        @Nullable IBlockStatePalette pal;
        float @Nullable [] lut;
        boolean direct;

        static float resistanceOf(IBlockState state) {
            if (state.getMaterial() == Material.AIR) return -1f;
            return getNukeResistance(state);
        }

        void decode(ExtendedBlockStorage sec) {
            SectionPalette.Data data = SectionPalette.data(sec.getData());
            BitArray bits = data.storage;
            IBlockStatePalette palette = data.palette;
            int paletteSize = palette == BlockStateContainer.REGISTRY_BASED_PALETTE
                    ? Block.BLOCK_STATE_IDS.size() : 1 << data.bits;
            direct = paletteSize > MAX_LUT;
            if (!direct) {
                int capacity = Math.max(1 << data.bits, paletteSize);
                if (lut == null || lut.length < capacity) lut = new float[Math.max(capacity, 64)];
                Arrays.fill(lut, 0, capacity, Float.NaN);
            }
            storage = bits;
            pal = palette;
            section = sec;
        }

        float resistanceAt(int xLocal, int yLocal, int zLocal) {
            int pi = storage.getAt(Library.packLocal(xLocal, yLocal, zLocal));
            if (direct) return resistanceOf(pal.getBlockState(pi));
            float r = lut[pi];
            if (Float.isNaN(r)) {
                r = resistanceOf(pal.getBlockState(pi));
                lut[pi] = r;
            }
            return r;
        }

        IBlockState stateAt(int xLocal, int yLocal, int zLocal) {
            return pal.getBlockState(storage.getAt(Library.packLocal(xLocal, yLocal, zLocal)));
        }
    }

    record ResumeItem(int kind, Int2ObjectOpenHashMap<BitMask> masks, ChunkAgg agg) {
        static final int APPLY_MASKS = 0;
        static final int APPLY_AGG = 1;

        ResumeItem(Int2ObjectOpenHashMap<BitMask> masks) {
            this(APPLY_MASKS, masks, null);
        }

        ResumeItem(ChunkAgg agg) {
            this(APPLY_AGG, null, agg);
        }
    }

    static final class IntDoubleAccumulator {
        // Sparse retention avoids allocating a dense damage table for every section crossed by a ray.
        static final int EMPTY = Integer.MIN_VALUE;
        static final int BASE_CAPACITY = 128;
        static final int MAX_RETAINED_CAPACITY = 4096;
        static final double LOAD = 0.50;
        static final int HASH_MUL = 0xC2B2AE35;

        int[] keys;
        double[] vals;
        int mask;
        int shift;
        int size;
        int resizeThreshold;

        IntDoubleAccumulator() {
            this(BASE_CAPACITY);
        }

        IntDoubleAccumulator(int expected) {
            init(capFor(expected));
        }

        void init(int capacity) {
            keys = new int[capacity];
            Arrays.fill(keys, EMPTY);
            vals = new double[capacity];
            mask = capacity - 1;
            shift = 32 - Integer.numberOfTrailingZeros(capacity);
            size = 0;
            resizeThreshold = (int) (capacity * LOAD);
        }

        void clear() {
            if (keys.length > MAX_RETAINED_CAPACITY) {
                init(BASE_CAPACITY);
                return;
            }
            Arrays.fill(keys, EMPTY);
            size = 0;
        }

        void add(int key, double delta) {
            int[] ks = keys;
            double[] vs = vals;
            int m = mask;
            int idx = (key * HASH_MUL) >>> shift;
            while (true) {
                int k = ks[idx];
                if (k == EMPTY) {
                    ks[idx] = key;
                    vs[idx] = delta;
                    if (++size >= resizeThreshold) rehash();
                    return;
                }
                if (k == key) {
                    vs[idx] += delta;
                    return;
                }
                idx = (idx + 1) & m;
            }
        }

        int size() {
            return size;
        }

        void accumulateTo(Int2DoubleOpenHashMap map) {
            int[] ks = keys;
            double[] vs = vals;
            for (int i = 0; i < ks.length; i++) {
                int k = ks[i];
                if (k != EMPTY) map.addTo(k, vs[i]);
            }
        }

        void rehash() {
            int[] oldK = keys;
            double[] oldV = vals;
            int newCap = oldK.length << 1;
            int[] newK = new int[newCap];
            Arrays.fill(newK, EMPTY);
            double[] newV = new double[newCap];
            int newMask = newCap - 1;
            int newShift = 32 - Integer.numberOfTrailingZeros(newCap);
            int newThreshold = (int) (newCap * LOAD);
            int newSize = 0;
            for (int i = 0; i < oldK.length; i++) {
                int k = oldK[i];
                if (k == EMPTY) continue;
                int idx = (k * HASH_MUL) >>> newShift;
                while (newK[idx] != EMPTY) idx = (idx + 1) & newMask;
                newK[idx] = k;
                newV[idx] = oldV[i];
                newSize++;
            }
            keys = newK;
            vals = newV;
            mask = newMask;
            shift = newShift;
            resizeThreshold = newThreshold;
            size = newSize;
        }
    }

    static final class LocalAgg {
        final Long2ObjectOpenHashMap<IntDoubleAccumulator> localDamage = new Long2ObjectOpenHashMap<>(16);
        final Long2ObjectOpenHashMap<IntDoubleAccumulator> localLen = new Long2ObjectOpenHashMap<>(16);
        final Long2ObjectOpenHashMap<IntArrayList> deferredMissing = new Long2ObjectOpenHashMap<>(4);
        long lastCp = Long.MIN_VALUE;
        @Nullable IntDoubleAccumulator lastDmg;
        @Nullable IntDoubleAccumulator lastLen;

        void clear() {
            if (!localDamage.isEmpty()) {
                ObjectIterator<Long2ObjectMap.Entry<IntDoubleAccumulator>> it = localDamage.long2ObjectEntrySet()
                                                                                           .fastIterator();
                while (it.hasNext()) ACC_POOL.recycle(it.next().getValue());
                localDamage.clear();
            }
            if (!localLen.isEmpty()) {
                ObjectIterator<Long2ObjectMap.Entry<IntDoubleAccumulator>> it = localLen.long2ObjectEntrySet()
                                                                                        .fastIterator();
                while (it.hasNext()) ACC_POOL.recycle(it.next().getValue());
                localLen.clear();
            }
            lastCp = Long.MIN_VALUE;
            lastDmg = null;
            lastLen = null;
            if (!deferredMissing.isEmpty()) {
                for (IntArrayList list : deferredMissing.values()) {
                    list.clear();
                    INT_LIST_POOL.recycle(list);
                }
                deferredMissing.clear();
            }
        }

        void deferMissing(long chunkPos, int dirIndex) {
            IntArrayList list = deferredMissing.get(chunkPos);
            if (list == null) {
                list = INT_LIST_POOL.borrow();
                deferredMissing.put(chunkPos, list);
            }
            list.add(dirIndex);
        }

        boolean hasDeferredMissing() {
            return !deferredMissing.isEmpty();
        }

        Long2ObjectOpenHashMap<IntArrayList> missingChunks() {
            return deferredMissing;
        }

        void recordHit(long chunkPos, int bitIndex, double damageInc, double segLen) {
            if (damageInc <= 0.0 && segLen <= 0.0) return;
            if (chunkPos != lastCp) {
                lastCp = chunkPos;
                lastDmg = localDamage.get(chunkPos);
                lastLen = localLen.get(chunkPos);
            }
            if (damageInc > 0.0) {
                IntDoubleAccumulator acc = lastDmg;
                if (acc == null) {
                    acc = ACC_POOL.borrow();
                    localDamage.put(chunkPos, acc);
                    lastDmg = acc;
                }
                acc.add(bitIndex, damageInc);
            }
            if (segLen > 0.0) {
                IntDoubleAccumulator acc = lastLen;
                if (acc == null) {
                    acc = ACC_POOL.borrow();
                    localLen.put(chunkPos, acc);
                    lastLen = acc;
                }
                acc.add(bitIndex, segLen);
            }
        }
    }

    static final class ChunkAgg {
        final Int2DoubleOpenHashMap damage = new Int2DoubleOpenHashMap(64);
        final Int2DoubleOpenHashMap passLen = new Int2DoubleOpenHashMap(64);
        final ConcurrentLinkedQueue<IntDoubleAccumulator> qDmg = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<IntDoubleAccumulator> qLen = new ConcurrentLinkedQueue<>();

        void merge(@NotNull IntDoubleAccumulator accumulator, boolean isDamage) {
            int sz = accumulator.size();
            if (sz == 0) {
                ACC_POOL.recycle(accumulator);
                return;
            }
            if (isDamage) qDmg.offer(accumulator);
            else qLen.offer(accumulator);
        }

        void drainUnlocked() {
            IntDoubleAccumulator a;
            while ((a = qDmg.poll()) != null) {
                if (a.size() > 0) a.accumulateTo(damage);
                ACC_POOL.recycle(a);
            }
            while ((a = qLen.poll()) != null) {
                if (a.size() > 0) a.accumulateTo(passLen);
                ACC_POOL.recycle(a);
            }
        }

        void clear() {
            damage.clear();
            passLen.clear();
            IntDoubleAccumulator a;
            while ((a = qDmg.poll()) != null) ACC_POOL.recycle(a);
            while ((a = qLen.poll()) != null) ACC_POOL.recycle(a);
        }
    }

    private abstract class BlastTask extends RecursiveAction {
        @Override
        protected final void compute() {
            try {
                computeBlast();
            } catch (Throwable cause) {
                fail(cause);
            }
        }

        protected abstract void computeBlast();
    }

    final class ConsolidateAggTask extends BlastTask {
        final long[] keys;
        final ChunkAgg[] aggregates;
        final int start, end, threshold;

        ConsolidateAggTask(long[] keys, ChunkAgg[] aggregates, int start, int end, int threshold) {
            this.keys = keys;
            this.aggregates = aggregates;
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                for (int i = start; i < end; i++) {
                    if (destroyFinished != 0) {
                        for (; i < end; i++) {
                            ChunkAgg remaining = aggregates[i];
                            if (remaining != null) remaining.clear();
                        }
                        return;
                    }
                    long cpLong = keys[i];
                    ChunkAgg agg = aggregates[i];
                    if (agg == null) continue;
                    agg.drainUnlocked();
                    applyAggregate(cpLong, agg);
                }
            } else {
                int mid = start + (len >>> 1);
                invokeAll(new ConsolidateAggTask(keys, aggregates, start, mid, threshold),
                        new ConsolidateAggTask(keys, aggregates, mid, end, threshold));
            }
        }
    }

    final class ConsolidateMaskTask extends BlastTask {
        final long[] keys;
        final int start, end, threshold;

        ConsolidateMaskTask(long[] keys, int start, int end, int threshold) {
            this.keys = keys;
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                for (int i = start; i < end; i++) {
                    long cpLong = keys[i];
                    ConcurrentBitSet chunkBitSet = destructionMap.get(cpLong);
                    if (chunkBitSet == null || chunkBitSet.isEmpty()) {
                        destructionMap.remove(cpLong);
                        continue;
                    }
                    Int2ObjectOpenHashMap<BitMask> masks = splitBySubchunk(chunkBitSet);
                    boolean transferred = false;
                    try {
                        applyCarve(cpLong, masks);
                        transferred = true;
                    } finally {
                        if (!transferred) freeMasks(masks);
                    }
                    destructionMap.remove(cpLong);
                }
            } else {
                int mid = start + (len >>> 1);
                invokeAll(new ConsolidateMaskTask(keys, start, mid, threshold),
                        new ConsolidateMaskTask(keys, mid, end, threshold));
            }
        }
    }

    class RayTracerTask extends BlastTask {
        final int start, end, threshold;

        RayTracerTask(int start, int end, int threshold) {
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                LocalAgg agg = TL_LOCAL_AGG.get();
                agg.clear();
                int completed = 0;
                for (int i = start; i < end; i++) {
                    if (Thread.currentThread().isInterrupted() || destroyFinished != 0) break;
                    int[] order = rayOrder;
                    int dirIndex = (order != null) ? order[i] : i;
                    if (traceSingle(dirIndex, agg)) completed++;
                }
                flushDeferredMissing(agg);
                mergeLocalAgg(agg);
                if (completed > 0) {
                    int prev = U.getAndAddInt(ExplosionNukeRayParallelized.this, OFF_PENDING_RAYS, -completed);
                    if (prev - completed == 0) onAllRaysFinished();
                }
            } else {
                int mid = start + (len >>> 1);
                invokeAll(new RayTracerTask(start, mid, threshold), new RayTracerTask(mid, end, threshold));
            }
        }
    }

    class ResumeBatchTask extends BlastTask {
        final IntArrayList indices;
        final int start, end, threshold;

        ResumeBatchTask(IntArrayList indices, int start, int end, int threshold) {
            this.indices = indices;
            this.start = start;
            this.end = end;
            this.threshold = Math.max(1, threshold);
        }

        @Override
        protected void computeBlast() {
            int len = end - start;
            if (len <= threshold) {
                LocalAgg agg = TL_LOCAL_AGG.get();
                agg.clear();
                int completed = 0;
                for (int i = start; i < end; i++) {
                    int dirIndex = indices.getInt(i);
                    if (Thread.currentThread().isInterrupted() || destroyFinished != 0) break;
                    if (traceSingle(dirIndex, agg)) completed++;
                }
                flushDeferredMissing(agg);
                mergeLocalAgg(agg);
                if (completed > 0) {
                    int prev = U.getAndAddInt(ExplosionNukeRayParallelized.this, OFF_PENDING_RAYS, -completed);
                    if (prev - completed == 0) onAllRaysFinished();
                }
            } else {
                int mid = start + (len >>> 1);
                invokeAll(new ResumeBatchTask(indices, start, mid, threshold),
                        new ResumeBatchTask(indices, mid, end, threshold));
            }
        }
    }
}
