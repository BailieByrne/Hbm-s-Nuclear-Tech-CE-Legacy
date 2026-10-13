package com.hbm.blocks.generic;

import java.util.Collections;
import java.util.List;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public class BlockSlag extends BlockMeta {
    public BlockSlag(Material material, String name) {
        super(material, SoundType.STONE, name, "block_slag", "block_slag_broken");
    }

    @Override
    public List<ItemStack> getDrops(IBlockAccess world, BlockPos pos, IBlockState state, int fortune) {
        return Collections.singletonList(new ItemStack(this));
    }

    @Override
    public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
        return new ItemStack(this);
    }

    @Override
    public boolean isBeaconBase(IBlockAccess world, BlockPos pos, BlockPos beacon) {
        return true;
    }

    @Override
    public void registerItem() {
        ItemBlock item = new ItemBlock(this);
        item.setRegistryName(getRegistryName());
        ForgeRegistries.ITEMS.register(item);
    }
}
