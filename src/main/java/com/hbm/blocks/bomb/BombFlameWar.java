package com.hbm.blocks.bomb;

import com.hbm.blocks.ModBlocks;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.BlockAllocatorStandard;
import com.hbm.explosion.vanillant.standard.BlockProcessorStandard;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import com.hbm.explosion.vanillant.standard.ExplosionEffectTiny;
import com.hbm.explosion.vanillant.standard.PlayerProcessorStandard;
import com.hbm.interfaces.IBomb;
import com.hbm.particle.helper.ExplosionCreator;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BombFlameWar extends Block implements IBomb {
    public BombFlameWar(Material material, String name) {
        super(material);
        setTranslationKey(name);
        setRegistryName(name);
        ModBlocks.ALL_BLOCKS.add(this);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block block, BlockPos fromPos) {
        if (world.isBlockPowered(pos)) explode(world, pos, null);
    }

    @Override
    public BombReturnCode explode(World world, BlockPos pos, Entity detonator) {
        if (!world.isRemote) {
            world.destroyBlock(pos, false);
            for (int i = 0; i < 150; i++) {
                ExplosionVNT vnt = new ExplosionVNT(world,
                        pos.getX() + world.rand.nextInt(51) - 25,
                        pos.getY() + world.rand.nextInt(11) - 5,
                        pos.getZ() + world.rand.nextInt(51) - 25, 4, null);
                vnt.setEntityProcessor(new EntityProcessorCrossSmooth(1, 25));
                vnt.setPlayerProcessor(new PlayerProcessorStandard());
                vnt.setSFX(new ExplosionEffectTiny());
                vnt.explode();
            }
            ExplosionVNT vnt = new ExplosionVNT(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15F);
            vnt.setBlockAllocator(new BlockAllocatorStandard(32));
            vnt.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
            vnt.setEntityProcessor(new EntityProcessorCrossSmooth(2, 200));
            vnt.setPlayerProcessor(new PlayerProcessorStandard());
            vnt.explode();
            ExplosionCreator.composeEffectSmall(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
        return BombReturnCode.DETONATED;
    }
}
