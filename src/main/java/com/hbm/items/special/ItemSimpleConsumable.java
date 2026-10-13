package com.hbm.items.special;

import com.hbm.config.VersatileConfig;
import com.hbm.items.ModItems;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.lib.ModDamageSource;
import com.hbm.main.MainRegistry;
import com.hbm.potion.HbmPotion;
import com.hbm.util.Tuple.Pair;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraft.world.World;

import java.util.function.BiConsumer;

public class ItemSimpleConsumable extends ItemCustomLore {
	
	//if java is giving me the power of generics and delegates then i'm going to use them, damn it!
	private BiConsumer<ItemStack, EntityPlayer> useAction;
	private BiConsumer<ItemStack, EntityPlayer> useActionServer;
	private BiConsumer<ItemStack, Pair<EntityLivingBase, EntityLivingBase>> hitAction;
	private BiConsumer<ItemStack, Pair<EntityLivingBase, EntityLivingBase>> hitActionServer;

	public ItemSimpleConsumable(String s){
		super(s);
		this.setCreativeTab(MainRegistry.consumableTab);
	}

	@Override
	public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {

		ItemStack stack = player.getHeldItem(hand);
		if(this.useAction != null)
			this.useAction.accept(stack, player);
		
		if(!world.isRemote && this.useActionServer != null)
			this.useActionServer.accept(stack, player);
		
		return ActionResult.<ItemStack> newResult(EnumActionResult.SUCCESS, player.getHeldItem(hand));
	}

	@Override
	public boolean hitEntity(ItemStack stack, EntityLivingBase entity, EntityLivingBase entityPlayer) {
		
		if(this.hitAction != null)
			this.hitAction.accept(stack, new Pair(entity, entityPlayer));
		
		if(!entity.world.isRemote && this.hitActionServer != null)
			this.hitActionServer.accept(stack, new Pair(entity, entityPlayer));
		
		return false;
	}
	
	public static void giveSoundAndDecrement(ItemStack stack, EntityLivingBase entity, SoundEvent sound, ItemStack container) {
		// Insert before shrink: emptied hand slot may take container; attack path then clears hand => container lost.
		ItemSimpleConsumable.tryAddItem(entity, container);
		stack.shrink(1);
		entity.world.playSound(null, entity.posX, entity.posY, entity.posZ, sound, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
	
	public static void addPotionEffect(EntityLivingBase entity, Potion effect, int duration, int level) {
		
		if(!entity.isPotionActive(effect)) {
			entity.addPotionEffect(new PotionEffect(effect, duration, level));
		} else {
			int d = duration + entity.getActivePotionEffect(effect).getDuration();
			entity.addPotionEffect(new PotionEffect(effect, d, level));
		}
	}
	
	public static void tryAddItem(EntityLivingBase entity, ItemStack stack) {
		if(entity instanceof EntityPlayer) {
			EntityPlayer player = (EntityPlayer) entity;
			if(!player.inventory.addItemStackToInventory(stack)) {
				player.dropItem(stack, false);
			}
		}
	}
	
	//this formatting style probably already has a name but i will call it "the greg"
	public ItemSimpleConsumable setUseAction(		BiConsumer<ItemStack, EntityPlayer> delegate) {								this.useAction = delegate;			return this; }
	public ItemSimpleConsumable setUseActionServer(	BiConsumer<ItemStack, EntityPlayer> delegate) {								this.useActionServer = delegate;	return this; }
	public ItemSimpleConsumable setHitAction(		BiConsumer<ItemStack, Pair<EntityLivingBase, EntityLivingBase>> delegate) {	this.hitAction = delegate;			return this; }
	public ItemSimpleConsumable setHitActionServer(	BiConsumer<ItemStack, Pair<EntityLivingBase, EntityLivingBase>> delegate) {	this.hitActionServer = delegate;	return this; }

    public static void doRadaway(ItemStack stack, EntityPlayer user, int duration) {
        giveSoundAndDecrement(stack, user, HBMSoundHandler.radawayUse, new ItemStack(ModItems.iv_empty));
        addPotionEffect(user, HbmPotion.radaway, duration, 0);
    }

    public static void effectAntidote(ItemStack stack, EntityLivingBase affected, EntityLivingBase source) {
        if (VersatileConfig.hasPotionSickness(affected)) return;
        affected.clearActivePotions();
        giveSoundAndDecrement(stack, source, HBMSoundHandler.syringeUse, new ItemStack(ModItems.syringe_empty));
        VersatileConfig.applyPotionSickness(affected, 5);
    }

    public static void effectPoison(ItemStack stack, EntityLivingBase affected, EntityLivingBase source) {
        if (affected == source)
            affected.attackEntityFrom(affected.getRNG().nextBoolean() ? ModDamageSource.euthanizedSelf : ModDamageSource.euthanizedSelf2, 30);
        else affected.attackEntityFrom(ModDamageSource.euthanized(source, source), 30);
        giveSoundAndDecrement(stack, source, HBMSoundHandler.syringeUse, new ItemStack(ModItems.syringe_empty));
    }

    public static void effectAwesome(ItemStack stack, EntityLivingBase affected, EntityLivingBase source) {
        if (VersatileConfig.hasPotionSickness(affected)) return;
        giveSoundAndDecrement(stack, source, HBMSoundHandler.syringeUse, new ItemStack(ModItems.syringe_empty));
        affected.addPotionEffect(new PotionEffect(MobEffects.REGENERATION, 50 * 20, 9));
        affected.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 50 * 20, 9));
        affected.addPotionEffect(new PotionEffect(MobEffects.FIRE_RESISTANCE, 50 * 20, 0));
        affected.addPotionEffect(new PotionEffect(MobEffects.STRENGTH, 50 * 20, 24));
        affected.addPotionEffect(new PotionEffect(MobEffects.HASTE, 50 * 20, 9));
        affected.addPotionEffect(new PotionEffect(MobEffects.SPEED, 50 * 20, 6));
        affected.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 50 * 20, 9));
        affected.addPotionEffect(new PotionEffect(MobEffects.HEALTH_BOOST, 50 * 20, 9));
        affected.addPotionEffect(new PotionEffect(MobEffects.ABSORPTION, 50 * 20, 4));
        affected.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 5 * 20, 4));
        affected.addPotionEffect(new PotionEffect(HbmPotion.radx, 50 * 20, 9));
        VersatileConfig.applyPotionSickness(affected, 5);
    }
}
