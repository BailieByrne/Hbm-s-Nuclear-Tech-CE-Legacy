package com.hbm.tileentity.network;

import com.hbm.lib.ItemStackHandlerWrapper;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * "Multiple inheritance is bad because...uhhhh...i guess if you do it wrong then it can lead to bad things"
 * ~ genuinely retarded people on StackOverflow
 * like yeah, doing things wrong can lead to bad things
 * no shit
 * just like how java operates already
 * you fucking dork
 *
 * this class has to extend TileEntityRequestNetwork for all the network stuff to work
 * but it also needs slots and all the container boilerplate crap
 * since multiple inheritance is a sin punishable by stoning, i had to cram the entire contents of TileEntityMachineBase into this class
 * is this good code? is this what you wanted? was it worth avoiding those hypothetical scenarios where multiple inheritance is le bad?
 * i believe that neither heaven nor hell awaits me when all is said and done
 * saint peter will send me to southend
 *
 * @author hbm
 */
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public abstract class TileEntityRequestNetworkContainer extends TileEntityRequestNetwork implements ISidedInventory {
    public ItemStackHandler inventory;

    private String customName;
    private final SidedItemHandler[] itemHandlers = new SidedItemHandler[EnumFacing.VALUES.length + 1];

    public TileEntityRequestNetworkContainer(int scount) {
        inventory = new ItemStackHandler(scount) {
            @Override
            protected void onContentsChanged(int slot) {
                markDirty();
            }
        };
    }

    @Override public int getSizeInventory() { return inventory.getSlots(); }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            if (!this.inventory.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override public ItemStack getStackInSlot(int i) { return inventory.getStackInSlot(i); }
    @Override public void openInventory(EntityPlayer player) { }
    @Override public void closeInventory(EntityPlayer player) { }
    @Override public boolean isItemValidForSlot(int slot, ItemStack itemStack) { return false; }
    @Override public int getField(int id) { return 0; }
    @Override public void setField(int id, int value) { }
    @Override public int getFieldCount() { return 0; }

    @Override
    public void clear() {
        for (int i = 0; i < this.inventory.getSlots(); i++) {
            this.inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override public boolean canInsertItem(int slot, ItemStack itemStack, EnumFacing side) { return this.isItemValidForSlot(slot, itemStack); }
    @Override public boolean canExtractItem(int slot, ItemStack itemStack, EnumFacing side) { return false; }
    @Override public int[] getSlotsForFace(EnumFacing side) { return getAccessibleSlotsFromSide(side); }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) return getItemHandler(facing).hasSlots;
        return super.hasCapability(capability, facing);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            SidedItemHandler handler = getItemHandler(facing);
            if (handler.hasSlots) return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(handler);
        }
        return super.getCapability(capability, facing);
    }

    private SidedItemHandler getItemHandler(@Nullable EnumFacing side) {
        int index = side == null ? EnumFacing.VALUES.length : side.ordinal();
        SidedItemHandler handler = itemHandlers[index];
        if (handler == null) {
            handler = new SidedItemHandler(side, getSlotsForFace(side));
            itemHandlers[index] = handler;
        }
        return handler;
    }

    private final class SidedItemHandler extends ItemStackHandlerWrapper {
        private final EnumFacing side;
        private final boolean hasSlots;

        private SidedItemHandler(@Nullable EnumFacing side, int[] slots) {
            super(inventory, slots);
            this.side = side;
            this.hasSlots = slots.length > 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return super.isItemValid(slot, stack) && canInsertItem(slot, stack, side);
        }

        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return canInsertItem(slot, stack, side) ? super.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return canExtractItem(slot, inventory.getStackInSlot(slot), side)
                    ? super.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (!stack.isEmpty() && stack.getMaxStackSize() > this.getInventoryStackLimit()) {
            this.inventory.setStackInSlot(slot, stack.splitStack(this.getInventoryStackLimit()));
        } else {
            this.inventory.setStackInSlot(slot, stack);
        }
    }

    @Override public ITextComponent getDisplayName() { return this.hasCustomName() ? new TextComponentString(this.customName) : new TextComponentString(getName()); }
    public abstract String getName();
    @Override public boolean hasCustomName() { return this.customName != null && !this.customName.isEmpty(); }
    public void setCustomName(String name) { this.customName = name; markDirty(); }
    @Override public int getInventoryStackLimit() { return 64; }

    @Override
    public boolean isUsableByPlayer(EntityPlayer player) {
        if(world.getTileEntity(pos) != this) {
            return false;
        } else {
            return player.getDistanceSq(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 128;
        }
    }

    public int[] getAccessibleSlotsFromSide(EnumFacing e) {
        return new int[]{};
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if(!this.inventory.getStackInSlot(slot).isEmpty()) {
            ItemStack itemstack;

            if (this.inventory.getStackInSlot(slot).getMaxStackSize() <= amount) {
                itemstack = this.inventory.getStackInSlot(slot);
                this.inventory.setStackInSlot(slot, ItemStack.EMPTY);
            } else {
                itemstack = this.inventory.getStackInSlot(slot).splitStack(amount);

                if(this.inventory.getStackInSlot(slot).getMaxStackSize() == 0) {
                    this.inventory.setStackInSlot(slot, ItemStack.EMPTY);
                }

            }
            return itemstack;
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public ItemStack removeStackFromSlot(int index) {
        ItemStack stack = inventory.getStackInSlot(index);
        inventory.setStackInSlot(index, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        NBTTagList list = nbt.getTagList("items", 10);

        for(int i = 0; i < list.tagCount(); i++)
        {
            NBTTagCompound nbt1 = list.getCompoundTagAt(i);
            byte b0 = nbt1.getByte("slot");
            if(b0 >= 0 && b0 < inventory.getSlots())
            {
                inventory.setStackInSlot(b0, new ItemStack(nbt1));
            }
        }

        customName = nbt.getString("name");
    }

    @Override
    public @NotNull NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        NBTTagList list = new NBTTagList();

        for(int i = 0; i < inventory.getSlots(); i++)
        {
            if(!inventory.getStackInSlot(i).isEmpty())
            {
                NBTTagCompound nbt1 = new NBTTagCompound();
                nbt1.setByte("slot", (byte)i);
                inventory.getStackInSlot(i).writeToNBT(nbt1);
                list.appendTag(nbt1);
            }
        }
        nbt.setTag("items", list);

        if (customName != null) {
            nbt.setString("name", customName);
        }
        return nbt;
    }
}
