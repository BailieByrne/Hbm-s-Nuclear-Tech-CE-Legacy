package com.hbm.items.special;

import com.hbm.Tags;
import com.hbm.items.IDynamicModels;
import com.hbm.items.ItemBase;
import java.util.List;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemStarmetal extends ItemBase implements IDynamicModels {

    private static final String[] SUFFIXES = {"", "_astra", "_ursa", "_orion"};

    public ItemStarmetal(String name) {
        super(name);
        setHasSubtypes(true);
        IDynamicModels.INSTANCES.add(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModel() {
        ModelResourceLocation[] models = new ModelResourceLocation[4];
        for (int i = 0; i < models.length; i++) {
            models[i] = new ModelResourceLocation(new ResourceLocation(
                    Tags.MODID, getRegistryName().getPath() + SUFFIXES[i]), "inventory");
        }
        ModelBakery.registerItemVariants(this, models);
        ModelLoader.setCustomMeshDefinition(this, stack -> models[Math.abs(stack.getMetadata()) % 4]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerSprite(TextureMap map) {
        for (String suffix : SUFFIXES)
            map.registerSprite(new ResourceLocation(Tags.MODID, "items/" + getRegistryName().getPath() + suffix));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void bakeModel(ModelBakeEvent event) {
    }

    @Override
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        switch (stack.getItemDamage()) {
            case 1: tooltip.add(TextFormatting.ITALIC + "Astra"); break;
            case 2: tooltip.add(TextFormatting.ITALIC + "Ursa"); break;
            case 3: tooltip.add(TextFormatting.ITALIC + "Orion"); break;
        }
    }
}
