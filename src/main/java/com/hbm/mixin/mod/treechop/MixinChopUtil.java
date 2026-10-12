package com.hbm.mixin.mod.treechop;

import com.hbm.items.tool.ItemToolAbility;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "ht.treechop.common.util.ChopUtil", remap = false)
public abstract class MixinChopUtil {
    @Redirect(method = "canChangeBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/item/Item;onBlockStartBreak(Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;)Z"),
            require = 1, remap = false)
    private static boolean hbm$allowOrdinaryChop(Item item, ItemStack stack, BlockPos pos, EntityPlayer player) {
        // TreeChop calls this hook while checking permission for its partial
        // replacement. Ordinary tool harvesting here would remove the log first.
        // Active abilities retain ownership; successful chops use TreeChop's
        // native onBlockDestroyed callback for durability and energy consumption.
        if (item instanceof ItemToolAbility) {
            ItemToolAbility tool = (ItemToolAbility) item;
            if (tool.canOperate(stack) && tool.getConfiguration(stack).getActivePreset().isNone()) return false;
        }
        return item.onBlockStartBreak(stack, pos, player);
    }
}
