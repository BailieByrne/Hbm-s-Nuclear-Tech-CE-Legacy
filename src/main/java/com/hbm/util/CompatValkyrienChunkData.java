package com.hbm.util;

import net.minecraft.world.chunk.BlockStateContainer;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Field;

import static com.hbm.lib.internal.UnsafeHolder.U;

/** Preserves Valkyrien Skies' section occupancy trees when copying native block data. */
final class CompatValkyrienChunkData {
    static final boolean ACTIVE = Loader.isModLoaded("valkyrienskies");
    private static final Class<?> OCTREE_CLASS;
    private static final Class<?> BITSET_CLASS;
    private static final long SOLID_TREE, LIQUID_TREE, BIT_BUFFER, BIT_DATA;

    static {
        if (ACTIVE) {
            try {
                ClassLoader loader = BlockStateContainer.class.getClassLoader();
                Class<?> octreeApi = Class.forName("org.valkyrienskies.mod.common.util.datastructures.IBitOctree", false, loader);
                Class<?> bitsetApi = Class.forName("org.valkyrienskies.mod.common.util.datastructures.IBitSet", false, loader);
                OCTREE_CLASS = Class.forName("org.valkyrienskies.mod.common.util.datastructures.SimpleBitOctree", false, loader);
                BITSET_CLASS = Class.forName("org.valkyrienskies.mod.common.util.datastructures.SmallBitSet", false, loader);
                Field solid = BlockStateContainer.class.getDeclaredField("solidOctree");
                Field liquid = BlockStateContainer.class.getDeclaredField("liquidOctree");
                Field buffer = OCTREE_CLASS.getDeclaredField("bitbuffer");
                Field data = BITSET_CLASS.getDeclaredField("data");
                if (solid.getType() != octreeApi || liquid.getType() != octreeApi
                        || buffer.getType() != bitsetApi || data.getType() != int[].class) {
                    throw new IllegalStateException("Unsupported Valkyrien Skies section occupancy layout");
                }
                SOLID_TREE = U.objectFieldOffset(solid);
                LIQUID_TREE = U.objectFieldOffset(liquid);
                BIT_BUFFER = U.objectFieldOffset(buffer);
                BIT_DATA = U.objectFieldOffset(data);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot access Valkyrien Skies section occupancy data", failure);
            }
        } else {
            OCTREE_CLASS = BITSET_CLASS = null;
            SOLID_TREE = LIQUID_TREE = BIT_BUFFER = BIT_DATA = 0L;
        }
    }

    private CompatValkyrienChunkData() {}

    static void copy(BlockStateContainer source, BlockStateContainer destination) {
        copyTree(U.getReference(source, SOLID_TREE), U.getReference(destination, SOLID_TREE));
        copyTree(U.getReference(source, LIQUID_TREE), U.getReference(destination, LIQUID_TREE));
    }

    private static void copyTree(Object source, Object destination) {
        if (source.getClass() != OCTREE_CLASS || destination.getClass() != OCTREE_CLASS) {
            throw new IllegalStateException("Unsupported Valkyrien Skies occupancy tree implementation");
        }
        Object sourceBuffer = U.getReference(source, BIT_BUFFER);
        Object destinationBuffer = U.getReference(destination, BIT_BUFFER);
        if (sourceBuffer.getClass() != BITSET_CLASS || destinationBuffer.getClass() != BITSET_CLASS) {
            throw new IllegalStateException("Unsupported Valkyrien Skies occupancy bitset implementation");
        }
        int[] sourceData = (int[]) U.getReference(sourceBuffer, BIT_DATA);
        int[] destinationData = (int[]) U.getReference(destinationBuffer, BIT_DATA);
        if (sourceData == destinationData || sourceData.length != destinationData.length) {
            throw new IllegalStateException("Valkyrien Skies section copies require independent matching occupancy buffers");
        }
        // Copy the leaf and every aggregate level together. Native set() keeps
        // this independent copy current while private sections are carved.
        System.arraycopy(sourceData, 0, destinationData, 0, sourceData.length);
    }
}
