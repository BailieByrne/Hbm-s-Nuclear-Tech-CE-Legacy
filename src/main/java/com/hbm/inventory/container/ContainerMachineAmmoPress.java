package com.hbm.inventory.container;

import com.hbm.inventory.IAutoRoutingContainer;
import com.hbm.inventory.TransferStrategy;
import com.hbm.inventory.slot.SlotFiltered;
import com.hbm.inventory.recipes.AmmoPressRecipes;
import com.hbm.tileentity.machine.TileEntityMachineAmmoPress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class ContainerMachineAmmoPress extends Container implements IAutoRoutingContainer {

    private final TileEntityMachineAmmoPress press;
    private final TransferStrategy autoRoutingStrategy;

    public ContainerMachineAmmoPress(InventoryPlayer playerInv, TileEntityMachineAmmoPress tile) {
        press = tile;
        TransferStrategy.Builder routing = TransferStrategy.builder(10);
        for (int i = 0; i < 9; i++) {
            final int input = i;
            routing.rule(i, i + 1, stack -> press.selectedRecipe >= 0 && press.selectedRecipe < AmmoPressRecipes.recipes.size()
                    && AmmoPressRecipes.recipes.get(press.selectedRecipe).input[input] != null
                    && AmmoPressRecipes.recipes.get(press.selectedRecipe).input[input].matchesRecipe(stack, true));
        }
        autoRoutingStrategy = routing.build();

        //Inputs
        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 3; j++) {
                this.addSlotToContainer(new SlotItemHandler(tile.inventory, i * 3 + j, 116 + j * 18, 18 + i * 18));
            }
        }
        // Output
        this.addSlotToContainer(SlotFiltered.takeOnly(tile.inventory, 9, 134, 72));

        for(int i = 0; i < 3; i++) {
            for(int j = 0; j < 9; j++) {
                this.addSlotToContainer(new Slot(playerInv, j + i * 9 + 9, 8 + j * 18, 118 + i * 18));
            }
        }
        for(int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(playerInv, i, 8 + i * 18, 176));
        }
    }

    @Override
    public boolean isAutomaticInsertionAllowed(int slot, ItemStack stack) {
        return autoRoutingStrategy.isAutomaticInsertionAllowed(this.inventorySlots, slot, stack);
    }

    @Override
    public @NotNull ItemStack transferStackInSlot(@NotNull EntityPlayer player, int index) {
        ItemStack rStack = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if(slot != null && slot.getHasStack()) {
            ItemStack stack = slot.getStack();
            rStack = stack.copy();

            if(index <= 9) {
                if(!this.mergeItemStack(stack, 10, this.inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {

                if(press.selectedRecipe < 0 || press.selectedRecipe >= AmmoPressRecipes.recipes.size()) return ItemStack.EMPTY;
                AmmoPressRecipes.AmmoPressRecipe recipe = AmmoPressRecipes.recipes.get(press.selectedRecipe);

                for(int i = 0; i < 9; i++) {
                    if(recipe.input[i] == null) continue;
                    if(recipe.input[i].matchesRecipe(stack, true)) {
                        if(!this.mergeItemStack(stack, i, i + 1, false)) {
                            return ItemStack.EMPTY;
                        }
                    }
                }

                return ItemStack.EMPTY;
            }

            if(stack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
        }

        return rStack;
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer player) {
        return press.isUseableByPlayer(player);
    }
}
