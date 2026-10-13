package com.hbm.entity.logic;

import net.minecraftforge.common.ForgeChunkManager.Ticket;

/**
 * Contract for entity-backed Forge chunk loaders.
 *
 * <p>Implementation notes:
 *
 * <p>1. {@link #init(Ticket) init(Ticket)} is used for both fresh tickets and tickets restored by Forge's
 * forced-chunk-loading callback. Treat it as a bind/refresh hook, not as the place where you
 * decide whether the entity should chunkload.
 *
 * <p>2. Do <strong>not</strong> request tickets from {@code entityInit()}. That old 1.7 pattern runs
 * during deserialization as well, so persisted entities can request a new ticket before Forge has a
 * chance to hand back the restored one.
 *
 * <p>3. Bootstrap fresh tickets after successful server admission through
 * {@code Entity.onAddedToWorld()}. The first {@code onUpdate()} can be withheld until a ticket
 * already forces the current chunk. Keep update-time retries and explicit opt-in methods such as
 * {@code setChunkLoading()}. If the entity was read from NBT, give the restore callback a short
 * grace period before requesting a new ticket.
 *
 * <p>4. If {@link #init(Ticket) init(Ticket)} receives a duplicate ticket while one is already bound, release the
 * new ticket immediately instead of silently leaking it.
 *
 * <p>5. On shutdown, death, or failed spawn, implementations must unforce all chunks and release the
 * ticket. Unforcing chunks alone is not enough; leaked tickets eventually exhaust the mod's ticket
 * budget and make later chunkloaders fail silently.
 *
 * <p>6. Persist only the state that says whether chunkloading should be enabled. Never persist or
 * reconstruct the ticket itself from NBT.
 */
public interface IChunkLoader {

	/** Whether an admitted server entity needs a fresh ticket, honoring restore grace and opt-in state. */
	boolean shouldBootstrapChunkLoader();

	void init(Ticket ticket);
	void loadNeighboringChunks(int newChunkX, int newChunkZ);
}
