package com.hbm.saveddata;

import com.hbm.Tags;
import com.hbm.lib.Library;
import com.hbm.util.ChunkUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLists;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Accepted blast fluid updates outlive their originating blast and its temporary chunk leases. */
@Mod.EventBusSubscriber(modid = Tags.MODID)
public final class BlastNotificationData extends WorldSavedData {
    public static final String KEY = "hbmBlastNotifications";

    private final Long2ObjectLinkedOpenHashMap<LongOpenHashSet> pending = new Long2ObjectLinkedOpenHashMap<>();
    private LongArrayList activePositions;
    private long activeChunk;
    private long[] leasedChunks;
    private CompletableFuture<?>[] chunkFutures;
    private CompletableFuture<?> lighting;
    private boolean closing;

    public BlastNotificationData(String name) {
        super(name);
    }

    /** Copies accepted positions without requesting chunks or taking over the caller's leases. */
    public static void enqueue(WorldServer world, LongCollection positions) {
        assert world.getMinecraftServer().isCallingFromMinecraftThread();
        if (positions.isEmpty()) return;
        BlastNotificationData data = getData(world);
        LongIterator iterator = positions.iterator();
        while (iterator.hasNext()) data.add(iterator.nextLong());
        data.markDirty();
    }

    private void add(long position) {
        long chunk = ChunkPos.asLong(Library.getBlockPosX(position) >> 4, Library.getBlockPosZ(position) >> 4);
        pending.computeIfAbsent(chunk, ignored -> new LongOpenHashSet()).add(position);
    }

    private static BlastNotificationData getData(WorldServer world) {
        MapStorage storage = world.getPerWorldStorage();
        BlastNotificationData data = (BlastNotificationData) storage.getOrLoadData(BlastNotificationData.class, KEY);
        if (data == null) {
            data = new BlastNotificationData(KEY);
            storage.setData(KEY, data);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        pending.clear();
        int[] positions = nbt.getIntArray("positions");
        if ((positions.length & 1) != 0) throw new IllegalArgumentException("Incomplete packed blast notification");
        for (int i = 0; i < positions.length; i += 2)
            add(((long) positions[i] << 32) | (positions[i + 1] & 0xFFFFFFFFL));
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        int count = activePositions == null ? 0 : activePositions.size();
        for (LongOpenHashSet positions : pending.values()) count = Math.addExact(count, positions.size());
        // 1.12 exposes int-array reads; each pair retains one complete packed block position.
        int[] packed = new int[Math.multiplyExact(count, 2)];
        int index = 0;
        if (activePositions != null) {
            for (int i = 0; i < activePositions.size(); i++) {
                long position = activePositions.getLong(i);
                packed[index++] = (int) (position >>> 32);
                packed[index++] = (int) position;
            }
        }
        for (LongOpenHashSet positions : pending.values()) {
            LongIterator iterator = positions.iterator();
            while (iterator.hasNext()) {
                long position = iterator.nextLong();
                packed[index++] = (int) (position >>> 32);
                packed[index++] = (int) position;
            }
        }
        nbt.setIntArray("positions", packed);
        return nbt;
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote) return;
        WorldServer world = (WorldServer) event.world;
        getData(world).tick(world);
    }

    private void tick(WorldServer world) {
        if (closing) return;
        try {
            if (leasedChunks == null) {
                if (pending.isEmpty()) return;
                activeChunk = pending.firstLongKey();
                int x = Library.getChunkPosX(activeChunk), z = Library.getChunkPosZ(activeChunk), index = 0;
                long[] neighborhood = new long[9];
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                    neighborhood[index++] = ChunkPos.asLong(x + dx, z + dz);
                if (!ChunkUtil.tryAddBlastTickets(world, neighborhood)) return;
                leasedChunks = neighborhood;
                activePositions = new LongArrayList(pending.remove(activeChunk));
                chunkFutures = new CompletableFuture<?>[neighborhood.length];
                for (int i = 0; i < neighborhood.length; i++)
                    chunkFutures[i] = ChunkUtil.blastChunkFuture(world, neighborhood[i]);
            }
            for (int i = 0; i < chunkFutures.length; i++) {
                if (!chunkFutures[i].isDone()) return;
                Chunk chunk = (Chunk) chunkFutures[i].join();
                if (ChunkUtil.liveChunkNow(world, leasedChunks[i]) != chunk)
                    throw new IllegalStateException("Leased blast notification chunk was replaced");
            }
            if (lighting == null) {
                BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
                for (int i = 0; i < activePositions.size(); i++) {
                    long packed = activePositions.getLong(i);
                    position.setPos(Library.getBlockPosX(packed), Library.getBlockPosY(packed), Library.getBlockPosZ(packed));
                    world.notifyNeighborsOfStateChange(position, Blocks.AIR, true);
                }
                CompletableFuture<?>[] updates = new CompletableFuture<?>[leasedChunks.length];
                for (int i = 0; i < leasedChunks.length; i++) {
                    long chunk = leasedChunks[i];
                    updates[i] = ChunkUtil.updateLight(world, ChunkUtil.liveChunkNow(world, chunk),
                            chunk == activeChunk ? activePositions : LongLists.EMPTY_LIST, 0xFFFFL);
                }
                lighting = CompletableFuture.allOf(updates);
            }
            if (!lighting.isDone()) return;
            lighting.join();
            for (long chunk : leasedChunks) ChunkUtil.broadcastWholesaleResend(world, chunk, lighting);
            releaseTransient(world);
            activePositions = null;
            markDirty();
        } catch (RuntimeException | Error failure) {
            requeueActive();
            try { releaseTransient(world); }
            catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void requeueActive() {
        if (activePositions == null) return;
        for (int i = 0; i < activePositions.size(); i++) add(activePositions.getLong(i));
        activePositions = null;
        markDirty();
    }

    private void releaseTransient(WorldServer world) {
        if (chunkFutures != null) {
            for (CompletableFuture<?> future : chunkFutures) if (future != null) future.cancel(false);
            chunkFutures = null;
        }
        lighting = null;
        long[] releasing = leasedChunks;
        leasedChunks = null;
        if (releasing == null) return;
        Throwable failure = null;
        for (long chunk : releasing) {
            try { ChunkUtil.releaseChunkAsync(world, chunk); }
            catch (RuntimeException | Error cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    // Engine cancellation runs at HIGHEST; release before ChunkUtil's NORMAL ticket cleanup.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        WorldServer world = (WorldServer) event.getWorld();
        BlastNotificationData data = getData(world);
        data.closing = true;
        data.requeueActive();
        data.releaseTransient(world);
    }

    // Vanilla may already have saved the world before late cancellation handed over accepted work.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void saveOnWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        WorldServer world = (WorldServer) event.getWorld();
        getData(world).markDirty();
        world.getPerWorldStorage().saveAllData();
    }
}
