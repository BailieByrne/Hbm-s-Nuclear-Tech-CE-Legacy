package com.hbm.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @WrapOperation(method = "onItemUse", require = 1, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraftforge/common/ForgeHooks;onPlaceItemIntoWorld(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/EnumFacing;FFFLnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;"))
    private EnumActionResult hbm$creditConsumedItemUse(ItemStack stack, EntityPlayer player, World world,
            BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ, EnumHand hand,
            Operation<EnumActionResult> original) {
        Item usedItem = stack.getItem();
        EnumActionResult result = original.call(stack, player, world, pos, side, hitX, hitY, hitZ, hand);
        // Forge looks up the item after consumption, when an exhausted stack
        // resolves to AIR. Retain its completed placement/cancellation handling
        // and account for the original item only at that boundary.
        if (result == EnumActionResult.SUCCESS && stack.isEmpty()) {
            player.addStat(StatList.getObjectUseStats(usedItem));
        }
        return result;
    }
}
