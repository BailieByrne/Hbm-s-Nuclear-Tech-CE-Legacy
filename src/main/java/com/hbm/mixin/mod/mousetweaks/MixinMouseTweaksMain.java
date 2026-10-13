package com.hbm.mixin.mod.mousetweaks;

import com.hbm.inventory.IAutoRoutingContainer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;

@Pseudo
@Mixin(targets = "yalter.mousetweaks.Main", remap = false)
public abstract class MixinMouseTweaksMain {
    @Group(name = "hbm$mouseTweaksRouting", min = 1, max = 1)
    @ModifyExpressionValue(method = "findWheelApplicableSlot", at = @At(value = "INVOKE",
            target = "Lyalter/mousetweaks/IGuiScreenHandler;isIgnored(Lnet/minecraft/inventory/Slot;)Z", remap = false), require = 0)
    private static boolean hbm$originalRouting(boolean ignored, @Local(argsOnly = true) Slot selected,
            @Local(argsOnly = true) boolean push, @Local(index = 10) Slot candidate) {
        return ignored || hbm$rejectAutomaticDestination(selected, candidate, push);
    }

    @Group(name = "hbm$mouseTweaksRouting", min = 1, max = 1)
    @ModifyExpressionValue(method = "findWheelApplicableSlot", at = @At(value = "INVOKE",
            target = "Lyalter/mousetweaks/impl/IGuiScreenHandler;isIgnored(Lnet/minecraft/inventory/Slot;)Z", remap = false), require = 0)
    private static boolean hbm$unofficialRouting(boolean ignored, @Local(argsOnly = true) Slot selected,
            @Local(argsOnly = true) boolean push, @Local(index = 11) Slot candidate) {
        return ignored || hbm$rejectAutomaticDestination(selected, candidate, push);
    }

    @Unique
    private static boolean hbm$rejectAutomaticDestination(Slot selected, Slot candidate, boolean push) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (!(player.openContainer instanceof IAutoRoutingContainer container)) return false;
        Slot source = push ? selected : candidate;
        Slot destination = push ? candidate : selected;
        if (source.inventory != player.inventory || destination.inventory == player.inventory) return false;
        ItemStack stack = source.getStack();
        return stack.isEmpty() || !destination.isItemValid(stack)
                || !container.isAutomaticInsertionAllowed(destination.slotNumber, stack);
    }
}
