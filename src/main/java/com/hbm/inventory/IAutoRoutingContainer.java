package com.hbm.inventory;

import net.minecraft.item.ItemStack;

/** Destination roles for automatic GUI item transfers. */
public interface IAutoRoutingContainer {
    boolean isAutomaticInsertionAllowed(int slot, ItemStack stack);
}
