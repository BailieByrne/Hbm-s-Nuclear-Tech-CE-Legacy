package com.hbm.util;

import com.hbm.lib.internal.UnsafeHolder;
import net.minecraft.util.BitArray;
import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraft.world.chunk.IBlockStatePalette;

import static com.hbm.lib.internal.UnsafeHolder.U;

/** Publishes 1.12's separately assigned palette/storage fields as one consistent resize generation. */
public final class SectionPalette {
    private static final long DATA = UnsafeHolder.fieldOffset(BlockStateContainer.class, "hbm$paletteData");

    private SectionPalette() {}

    public static Data data(BlockStateContainer container) {
        return (Data) U.getReferenceVolatile(container, DATA);
    }

    public static void resized(BlockStateContainer container) {
        U.putReferenceVolatile(container, DATA, new Data(container.storage, container.palette, container.bits));
    }

    public static final class Data {
        public final BitArray storage;
        public final IBlockStatePalette palette;
        public final int bits;

        private Data(BitArray storage, IBlockStatePalette palette, int bits) {
            this.storage = storage;
            this.palette = palette;
            this.bits = bits;
        }
    }
}
