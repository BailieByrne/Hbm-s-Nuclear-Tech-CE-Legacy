package com.hbm.util;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

public class EnchantmentUtil {

	public static void addEnchantment(ItemStack stack, Enchantment enchantment, int level) {

		stack.addEnchantment(enchantment, level);
	}

	public static void removeEnchantment(ItemStack stack, Enchantment enchantment) {
		if(stack.getEnchantmentTagList() == null || !stack.hasTagCompound())
			return;
		
		int i = 0;
		for( ; i < stack.getEnchantmentTagList().tagCount(); i++) {
			if(stack.getEnchantmentTagList().getCompoundTagAt(i).getShort("id") == Enchantment.getEnchantmentID(enchantment))
				break;
		}

		if(i < stack.getEnchantmentTagList().tagCount())
			stack.getEnchantmentTagList().removeTag(i);

		if(stack.getEnchantmentTagList().tagCount() == 0)
			stack.getTagCompound().removeTag("ench");
	}

	public static int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
		if (stack.isEmpty() || enchantment == null)
			return 0;

		NBTTagList list = stack.getEnchantmentTagList();
		if (list == null)
			return 0;

		ResourceLocation targetId = Enchantment.REGISTRY.getNameForObject(enchantment);

		for (int i = 0; i < list.tagCount(); i++) {
			NBTTagCompound tag = list.getCompoundTagAt(i);
			Enchantment ench = Enchantment.getEnchantmentByID(tag.getShort("id"));
			if (ench != null && Enchantment.REGISTRY.getNameForObject(ench).equals(targetId))
				return tag.getShort("lvl");
		}

		return 0;
	}

	/** XP bar size at {@code level}; 1.12 curve (upstream: 1.7 curve). */
	public static int xpBarCap(int level) {
		return level >= 30 ? 112 + (level - 30) * 9 : (level >= 15 ? 37 + (level - 15) * 5 : 7 + level * 2);
	}

    public static int getLevelForExperience(int xp) {

		int level = 0;

		while (true) {

			int xpCap = xpBarCap(level);

			if (xp < xpCap)
				return level;

			xp -= xpCap;
			level++;
		}
	}

    /** {@link EntityPlayer#addExperience} without score increase. */
	public static void addExperience(EntityPlayer player, int xp, boolean silent) {

		int j = Integer.MAX_VALUE - player.experienceTotal;

		if(xp > j) {
			xp = j;
		}

		player.experience += (float) xp / (float) player.xpBarCap();

		for(player.experienceTotal += xp; player.experience >= 1.0F; player.experience /= (float) player.xpBarCap()) {
			player.experience = (player.experience - 1.0F) * (float) player.xpBarCap();

			if(silent)
				addExperienceLevelSilent(player, 1);
			else
				player.addExperienceLevel(1);
		}
	}

	public static void setExperience(EntityPlayer player, int xp) {

		player.experienceLevel = 0;
		player.experience = 0.0F;
		player.experienceTotal = 0;

		addExperience(player, xp, true);
	}

	public static void addExperienceLevelSilent(EntityPlayer player, int level) {
		player.experienceLevel += level;

		if(player.experienceLevel < 0) {
			player.experienceLevel = 0;
			player.experience = 0.0F;
			player.experienceTotal = 0;
		}
	}

	/** Fun fact: experienceTotal lies and has no actual purpose other than misleading people! */
	public static int getTotalExperience(EntityPlayer player) {
		int xp = 0;

		for(int i = 0; i < player.experienceLevel; i++) {
			xp += xpBarCap(i);
		}

		xp += xpBarCap(player.experienceLevel) * player.experience;

		return xp;
	}
}
