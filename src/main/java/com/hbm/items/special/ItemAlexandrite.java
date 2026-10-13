package com.hbm.items.special;

import com.hbm.Tags;
import com.hbm.items.IDynamicModels;
import com.hbm.items.ItemBase;
import com.hbm.main.MainRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumSkyBlock;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ItemAlexandrite extends ItemBase implements IDynamicModels {

    public ItemAlexandrite(String name) {
        super(name);
        IDynamicModels.INSTANCES.add(this);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModel() {
        ModelLoader.setCustomModelResourceLocation(this, 0, new ModelResourceLocation(getRegistryName(), "inventory"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void bakeModel(ModelBakeEvent event) {
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerSprite(TextureMap map) {
        map.setTextureEntry(new TextureAlexandrite(new ResourceLocation(
                Tags.MODID, "items/" + getRegistryName().getPath()).toString()));
    }

    @SideOnly(Side.CLIENT)
    public static class TextureAlexandrite extends TextureAtlasSprite {
        private int lastLight;

        protected TextureAlexandrite(String texture) {
            super(texture);
        }

        @Override
        public void updateAnimation() {
            if (Minecraft.getMinecraft().world == null) return;
            EntityPlayer player = MainRegistry.proxy.me();
            if (player == null) return;

            int light = player.world.getLightFor(EnumSkyBlock.BLOCK, player.getPosition());
            if (light != lastLight) {
                lastLight = light;
                frameCounter = light;
                TextureUtil.uploadTextureMipmap(framesTextureData.get(frameCounter),
                        width, height, originX, originY, false, false);
            }
        }
    }
}
