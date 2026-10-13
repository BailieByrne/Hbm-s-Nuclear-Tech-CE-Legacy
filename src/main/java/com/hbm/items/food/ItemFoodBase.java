package com.hbm.items.food;

import com.google.common.collect.ImmutableMap;
import com.hbm.Tags;
import com.hbm.explosion.ExplosionNukeSmall;
import com.hbm.items.ClaimedModelLocationRegistry;
import com.hbm.items.IClaimedModelLocation;
import com.hbm.items.IDynamicModels;
import com.hbm.items.ModItems;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


import static com.hbm.items.ItemEnumMulti.ROOT_PATH;

public class ItemFoodBase extends ItemFood implements IDynamicModels, IClaimedModelLocation {
	String texturePath;

	public ItemFoodBase(int amount, float saturation, boolean isWolfFood, String s){
		super(amount, saturation, isWolfFood);
		this.setTranslationKey(s);
		this.setRegistryName(s);
		this.texturePath = s;
		INSTANCES.add(this);

		ModItems.ALL_ITEMS.add(this);
        ClaimedModelLocationRegistry.register(this);
	}
	public ItemFoodBase(int amount, float saturation, boolean isWolfFood, String s, String texturePath){
		super(amount, saturation, isWolfFood);
		this.setTranslationKey(s);
		this.setRegistryName(s);
		this.texturePath = texturePath;
		INSTANCES.add(this);

		ModItems.ALL_ITEMS.add(this);
        ClaimedModelLocationRegistry.register(this);
	}

	@Override
	public void bakeModel(ModelBakeEvent event) {
	}


	@Override
	public void registerModel() {
		ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(new ResourceLocation(Tags.MODID, ROOT_PATH + texturePath), "inventory"));
	}

	@Override
	public void registerSprite(TextureMap map) {
		map.registerSprite(new ResourceLocation(Tags.MODID, ROOT_PATH + texturePath));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean ownsModelLocation(ModelResourceLocation location) {
		return IClaimedModelLocation.isInventoryLocation(location, new ResourceLocation(Tags.MODID, ROOT_PATH + texturePath));
	}

	@Override
	@SideOnly(Side.CLIENT)
	public IModel loadModel(ModelResourceLocation location) {
		try {
			IModel generated = ModelLoaderRegistry.getModel(new ResourceLocation("item/generated"));
			return generated.retexture(ImmutableMap.of("layer0", new ResourceLocation(Tags.MODID, ROOT_PATH + texturePath).toString()));
		} catch (Exception e) {
			return IClaimedModelLocation.super.loadModel(location);
		}
	}

	@Override
	protected void onFoodEaten(ItemStack stack, World world, EntityPlayer player) {
		if (world.isRemote) return;
		if (this == ModItems.bomb_waffle) {
			ExplosionNukeSmall.explode(world, player.posX, player.posY + 0.5, player.posZ, ExplosionNukeSmall.PARAMS_MEDIUM);
		} else if (this == ModItems.cotton_candy) {
			player.addPotionEffect(new PotionEffect(MobEffects.POISON, 15 * 20, 0));
			player.addPotionEffect(new PotionEffect(MobEffects.WITHER, 5 * 20, 0));
			player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, 25 * 20, 2));
			player.addPotionEffect(new PotionEffect(MobEffects.SPEED, 25 * 20, 2));
			player.addPotionEffect(new PotionEffect(MobEffects.RESISTANCE, 30 * 20, 4));
		} else if (this == ModItems.schnitzel_vegan) {
			player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 10 * 20, 0));
			player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 30 * 20, 0));
			player.addPotionEffect(new PotionEffect(MobEffects.HUNGER, 3 * 60 * 20, 4));
			player.addPotionEffect(new PotionEffect(MobEffects.WITHER, 3 * 20, 0));
			player.setFire(5 * 20);
			player.motionY = 2;
		} else {
			super.onFoodEaten(stack, world, player);
		}
	}
}
