package com.hbm.util;

import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/** Server-captured private block sections with optimistic, all-section validation. */
public final class SectionSnapshot {
    private final Chunk chunk;
    private final ExtendedBlockStorage[] original;
    private final long[] generations;
    private final long observed;
    public final ExtendedBlockStorage[] sections;
    private long changed;

    private SectionSnapshot(Chunk chunk, long observed) {
        this.chunk = chunk;
        this.observed = observed;
        ExtendedBlockStorage[] live = chunk.getBlockStorageArray();
        original = new ExtendedBlockStorage[live.length];
        generations = new long[live.length];
        sections = new ExtendedBlockStorage[live.length];
        for (long mask = observed; mask != 0; mask &= mask - 1) {
            int index = Long.numberOfTrailingZeros(mask);
            ExtendedBlockStorage source = live[index];
            original[index] = source;
            generations[index] = SectionGeneration.generation(source);
            // 1.12 represents an absent section with null.
            sections[index] = source == null ? null : ChunkUtil.copyOf(source);
        }
    }

    static long representable(int sectionCount) {
        if (sectionCount > Long.SIZE) throw new IllegalStateException("dimension has " + sectionCount
                + " sections; bulk section edits span " + Long.SIZE);
        return sectionCount == Long.SIZE ? -1L : (1L << sectionCount) - 1;
    }

    public static SectionSnapshot capture(Chunk chunk, long observed) {
        assert ((WorldServer) chunk.getWorld()).getMinecraftServer().isCallingFromMinecraftThread();
        return new SectionSnapshot(chunk, observed & representable(chunk.getBlockStorageArray().length));
    }

    public void changed(int index) {
        assert (observed & (1L << index)) != 0;
        changed |= 1L << index;
    }

    public Chunk chunk() { return chunk; }

    public boolean publish() {
        WorldServer world = (WorldServer) chunk.getWorld();
        assert world.getMinecraftServer().isCallingFromMinecraftThread();
        if (ChunkUtil.liveChunkNow(world, net.minecraft.util.math.ChunkPos.asLong(chunk.x, chunk.z)) != chunk) return false;
        ExtendedBlockStorage[] live = chunk.getBlockStorageArray();
        for (long mask = observed; mask != 0; mask &= mask - 1) {
            int index = Long.numberOfTrailingZeros(mask);
            if (live[index] != original[index] || SectionGeneration.generation(live[index]) != generations[index]) return false;
        }
        // Validate every observed section before publishing any result or invoking callbacks.
        for (long mask = changed; mask != 0; mask &= mask - 1) {
            int index = Long.numberOfTrailingZeros(mask);
            ExtendedBlockStorage replacement = sections[index];
            ExtendedBlockStorage before = original[index];
            if (replacement != null && before != null) {
                // 1.12 stores lighting beside states; retain lighting published since capture.
                replacement.blockLight = before.blockLight;
                replacement.skyLight = before.skyLight;
            }
            boolean replaced = ChunkUtil.casEbsAt(before, replacement, live, index);
            assert replaced;
        }
        if (changed != 0) chunk.markDirty();
        return true;
    }
}
