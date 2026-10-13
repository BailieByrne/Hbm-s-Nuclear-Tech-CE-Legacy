package com.hbm.blocks.network;

import com.hbm.api.conveyor.IConveyorBelt;
import com.hbm.api.conveyor.IEnterableBlock;
import com.hbm.blocks.ModBlocks;
import com.hbm.items.ModItems;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockConveyorLift extends BlockConveyorBase {
    public static final PropertyInteger TYPE = BlockConveyorChute.TYPE;
    private static final AxisAlignedBB TOP_AABB = new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);

    public BlockConveyorLift(Material material, String name) {
        super(material, name);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH).withProperty(TYPE, 0));
    }

    private static int liftType(IBlockAccess world, BlockPos pos) {
        if (!(world.getBlockState(pos.down()).getBlock() instanceof IConveyorBelt)) return 0;
        Block above = world.getBlockState(pos.up()).getBlock();
        return above instanceof IConveyorBelt || above instanceof IEnterableBlock ? 1 : 2;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.withProperty(TYPE, liftType(world, pos));
    }

    @Override
    public boolean shouldSideBeRendered(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }
    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return liftType(world, pos) == 2 ? TOP_AABB : FULL_BLOCK_AABB;
    }

    @Override
    public EnumFacing getInputDirection(World world, BlockPos pos) {
        return EnumFacing.DOWN;
    }

    @Override
    public EnumFacing getOutputDirection(World world, BlockPos pos) {
        return EnumFacing.UP;
    }

    @Override
    public EnumFacing getTravelDirection(World world, BlockPos pos, Vec3d itemPos) {
        return liftType(world, pos) == 2 ? world.getBlockState(pos).getValue(FACING) : EnumFacing.DOWN;
    }

    @Override
    public Vec3d getClosestSnappingPosition(World world, BlockPos pos, Vec3d itemPos) {
        return liftType(world, pos) == 2 ? super.getClosestSnappingPosition(world, pos, itemPos)
                : new Vec3d(pos.getX() + 0.5, itemPos.y, pos.getZ() + 0.5);
    }

    @Override
    public boolean onScrew(World world, EntityPlayer player, int x, int y, int z, EnumFacing side,
                           float hitX, float hitY, float hitZ, EnumHand hand, ToolType tool) {
        if (tool != ToolType.SCREWDRIVER) return false;
        BlockPos pos = new BlockPos(x, y, z);
        IBlockState state = world.getBlockState(pos);
        if (!player.isSneaking()) {
            world.setBlockState(pos, state.withRotation(Rotation.CLOCKWISE_90), 3);
        } else {
            BlockConveyorChute chute = (BlockConveyorChute) ModBlocks.conveyor_chute;
            EnumFacing facing = state.getValue(FACING);
            world.setBlockState(pos, chute.getDefaultState().withProperty(FACING, facing)
                    .withProperty(TYPE, chute.getUpdatedType(world, pos, facing)), 3);
        }
        return true;
    }

    public int getUpdatedType(World world, BlockPos pos) {
        return liftType(world, pos);
    }

    public int getUpdatedType(World world, BlockPos pos, EnumFacing facing) {
        return liftType(world, pos);
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        world.setBlockState(pos, state.withProperty(TYPE, liftType(world, pos)), 3);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block changed, BlockPos fromPos) {
        world.setBlockState(pos, state.withProperty(TYPE, liftType(world, pos)), 3);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite()).withProperty(TYPE, liftType(world, pos));
    }

    @Override
    public Item getItemDropped(IBlockState state, Random random, int fortune) {
        return ModItems.conveyor_wand;
    }

    @Override
    public boolean doesSideBlockRendering(IBlockState state, IBlockAccess world, BlockPos pos, EnumFacing face) {
        return false;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, TYPE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex() + (state.getValue(TYPE) << 2);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta & 3)).withProperty(TYPE, Math.min((meta >> 2) & 3, 2));
    }
}