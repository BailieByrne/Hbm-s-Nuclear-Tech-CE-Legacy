package com.hbm.mixin.mod.treechop;

import com.hbm.items.tool.ItemToolAbility;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.atomic.AtomicBoolean;

@Pseudo
@Mixin(targets = "ht.treechop.common.util.ChopResult", remap = false)
public abstract class MixinChopResult {
    @WrapOperation(method = "lambda$apply$1", at = @At(value = "INVOKE",
            target = "Lht/treechop/common/util/ChopUtil;canChangeBlock(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/item/ItemStack;)Z", remap = false),
            require = 1, remap = false)
    private static boolean hbm$invalidateOwnedAttempt(BlockPos pos, EntityPlayer player, ItemStack stack,
            Operation<Boolean> original, @Local(argsOnly = true) AtomicBoolean invalidated) {
        boolean ntmTool = stack.getItem() instanceof ItemToolAbility;
        boolean allowed = original.call(pos, player, stack);
        // This query returns false only when the item hook has claimed the
        // block. Abort the now-owned plan before TreeChop applies replacements
        // or reports success and charges that same tool a second time.
        if (ntmTool && !allowed) invalidated.set(true);
        return allowed;
    }
}
