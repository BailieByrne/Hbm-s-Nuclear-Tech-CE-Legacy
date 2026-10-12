package com.hbm.inventory.container;

import com.hbm.inventory.IAutoRoutingContainer;
import com.hbm.inventory.TransferStrategy;

import com.hbm.inventory.slot.SlotBattery;
import com.hbm.inventory.slot.SlotFiltered;
import com.hbm.inventory.slot.SlotUpgrade;
import com.hbm.items.machine.ItemMachineUpgrade;
import com.hbm.items.machine.ItemStamp;
import com.hbm.lib.Library;
import com.hbm.tileentity.machine.TileEntityMachineEPress;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.NotNull;

public class ContainerMachineEPress extends Container implements IAutoRoutingContainer {

    private static final TransferStrategy AUTO_ROUTING_STRATEGY = TransferStrategy.builder(5)
            .rule(0, 1, Library::isBattery)
            .rule(4, 5, stack -> stack.getItem() instanceof ItemMachineUpgrade)
            .rule(1, 2, stack -> stack.getItem() instanceof ItemStamp)
            .genericMachineRange(2, 3)
            .build();

    private final TileEntityMachineEPress ePress;

    private int progress;

    public ContainerMachineEPress(InventoryPlayer invPlayer, @NotNull TileEntityMachineEPress ePress) {

        this.ePress = ePress;

        // Battery
        this.addSlotToContainer(new SlotBattery(ePress.inventory, 0, 152, 54));
        //Stamp
        this.addSlotToContainer(new SlotItemHandler(ePress.inventory, 1, 19, 15));
        //Input
        this.addSlotToContainer(new SlotItemHandler(ePress.inventory, 2, 19, 51));
        //Output
        this.addSlotToContainer(SlotFiltered.takeOnly(ePress.inventory, 3, 79, 33));
        //Upgrade
        this.addSlotToContainer(new SlotUpgrade(ePress.inventory, 4, 111, 32));

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlotToContainer(new Slot(invPlayer, j + i * 9 + 9, 8 + j * 18, 104 + i * 18));
            }
        }

        for (int i = 0; i < 9; i++) {
            this.addSlotToContainer(new Slot(invPlayer, i, 8 + i * 18, 162));
        }
    }

    @NotNull
    @Override
    public ItemStack transferStackInSlot(@NotNull EntityPlayer player, int index) {
        ItemStack stackCopy = ItemStack.EMPTY;
        Slot slot = this.inventorySlots.get(index);

        if (slot != null && slot.getHasStack()) {
            ItemStack originalStack = slot.getStack();
            stackCopy = originalStack.copy();
            if (index <= 4) {
                if (!this.mergeItemStack(originalStack, 5, this.inventorySlots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            }
            else {
                if (Library.isBattery(stackCopy)) {
                    if (!this.mergeItemStack(originalStack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                else if (stackCopy.getItem() instanceof ItemMachineUpgrade) {
                    if (!this.mergeItemStack(originalStack, 4, 5, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                else if (stackCopy.getItem() instanceof ItemStamp) {
                    if (!this.mergeItemStack(originalStack, 1, 2, false)) {
                        return ItemStack.EMPTY;
                    }
                }
                else {
                    if (!this.mergeItemStack(originalStack, 2, 3, false)) {
                        if (index < 32) {
                            if (!this.mergeItemStack(originalStack, 32, 41, false)) {
                                return ItemStack.EMPTY;
                            }
                        } else if (index < 41) {
                            if (!this.mergeItemStack(originalStack, 5, 32, false)) {
                                return ItemStack.EMPTY;
                            }
                        }
                    }
                }
            }

            if (originalStack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }

            if (originalStack.getCount() == stackCopy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, originalStack);
        }

        return stackCopy;
    }

    @Override
    public boolean canInteractWith(@NotNull EntityPlayer player) {
        return ePress.isUseableByPlayer(player);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        for (IContainerListener par1 : this.listeners) {
            if (this.progress != this.ePress.progress) {
                par1.sendWindowProperty(this, 0, this.ePress.progress);
            }
        }
        this.progress = this.ePress.progress;
    }

    @Override
    public void updateProgressBar(int id, int data) {
        if (id == 0) {
            ePress.progress = data;
        }
    }

    @Override
    public boolean isAutomaticInsertionAllowed(int slot, ItemStack stack) {
        return AUTO_ROUTING_STRATEGY.isAutomaticInsertionAllowed(this.inventorySlots, slot, stack);
    }
}
