package com.hbm.render.entity.projectile;

import com.hbm.Tags;
import com.hbm.entity.projectile.EntityBulletBaseNT;
import com.hbm.handler.BulletConfiguration;
import com.hbm.interfaces.AutoRegister;
import com.hbm.items.ModItems;
import com.hbm.main.ResourceManager;
import com.hbm.render.RenderSparks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import org.lwjgl.opengl.GL11;

import java.util.Random;

@Deprecated
@AutoRegister(factory = "FACTORY")
public class RenderBullet extends Render<EntityBulletBaseNT> {
    public static final IRenderFactory<EntityBulletBaseNT> FACTORY = RenderBullet::new;

    protected RenderBullet(RenderManager manager) {
        super(manager);
    }

    @Override
    public void doRender(EntityBulletBaseNT bullet, double x, double y, double z, float entityYaw, float partialTicks) {
        GlStateManager.pushMatrix();
        GlStateManager.translate((float) x, (float) y, (float) z);
        GlStateManager.rotate(bullet.prevRotationYaw + (bullet.rotationYaw - bullet.prevRotationYaw) * partialTicks - 90F, 0F, 1F, 0F);
        GlStateManager.rotate(bullet.prevRotationPitch + (bullet.rotationPitch - bullet.prevRotationPitch) * partialTicks + 180F, 0F, 0F, 1F);
        GlStateManager.scale(1.5F, 1.5F, 1.5F);

        int style = bullet.getDataManager().get(EntityBulletBaseNT.STYLE);
        int trail = bullet.getDataManager().get(EntityBulletBaseNT.TRAIL);
        if (style != BulletConfiguration.STYLE_BLADE)
            GlStateManager.rotate(new Random(bullet.getEntityId()).nextInt(90) - 45, 1F, 0F, 0F);
        GlStateManager.enableCull();
        switch (style) {
            case BulletConfiguration.STYLE_NORMAL: renderBullet(trail); break;
            case BulletConfiguration.STYLE_BOLT: renderDart(trail, bullet.getEntityId()); break;
            case BulletConfiguration.STYLE_FLECHETTE: renderFlechette(); break;
            case BulletConfiguration.STYLE_ROCKET: renderRocket(trail); break;
            case BulletConfiguration.STYLE_GRENADE: renderGrenade(trail); break;
            case BulletConfiguration.STYLE_ORB: renderOrb(trail); break;
            case BulletConfiguration.STYLE_METEOR: renderMeteor(trail); break;
            case BulletConfiguration.STYLE_BLADE: renderBlade(); break;
            default: renderBullet(trail); break;
        }
        GlStateManager.popMatrix();
    }
	private void renderBullet(int type) {

		GlStateManager.scale(0.5, 0.5, 0.5);
		GlStateManager.rotate(90, 0, 0, 1);
		GlStateManager.rotate(90, 0, 1, 0);

		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		bindTexture(ResourceManager.bullet_rifle_tex);
		ResourceManager.projectiles.renderPart("BulletRifle");
		GlStateManager.shadeModel(GL11.GL_FLAT);
	}

	private void renderRocket(int type) {

		GlStateManager.scale(0.5, 0.5, 0.5);
		GlStateManager.rotate(90, 0, 0, 1);
		GlStateManager.rotate(90, 0, 1, 0);

		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		bindTexture(ResourceManager.rocket_tex);
		ResourceManager.projectiles.renderPart("Rocket");
		GlStateManager.shadeModel(GL11.GL_FLAT);
	}

	private void renderGrenade(int type) {

		GlStateManager.scale(0.25F, 0.25F, 0.25F);
		GlStateManager.rotate(90, 0, 0, 1);
		GlStateManager.rotate(90, 0, 1, 0);

		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		bindTexture(ResourceManager.grenade_tex);
		ResourceManager.projectiles.renderPart("Grenade");
		GlStateManager.shadeModel(GL11.GL_FLAT);
	}

	private void renderOrb(int type) {

        GlStateManager.enableCull();
        GlStateManager.disableLighting();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);

		switch(type) {
		case 0:
			bindTexture(ResourceManager.tom_flame_tex);
			ResourceManager.sphere_uv.renderAll();
			GlStateManager.scale(0.3F, 0.3F, 0.3F);
			ResourceManager.sphere_uv.renderAll();
			GlStateManager.scale(1F/0.3F, 1F/0.3F, 1F/0.3F);
			for(int i = 0; i < 5; i++)
				RenderSparks.renderSpark((int) (System.currentTimeMillis() / 100 + 100 * i), 0, 0, 0, 0.5F, 2, 2, 0x8080FF, 0xFFFFFF);
			break;

		case 1:
			GlStateManager.scale(0.5F, 0.5F, 0.5F);
			GlStateManager.disableTexture2D();
			GlStateManager.color(0.5F, 0.0F, 0.0F, 0.5F);
			ResourceManager.sphere_uv.renderAll();
			GlStateManager.scale(0.75F, 0.75F, 0.75F);
			ResourceManager.sphere_uv.renderAll();
			GlStateManager.scale(1F/0.75F, 1F/0.75F, 1F/0.75F);
			GlStateManager.enableTexture2D();
			for(int i = 0; i < 3; i++)
				RenderSparks.renderSpark((int) (System.currentTimeMillis() / 100 + 100 * i), 0, 0, 0, 1F, 2, 3, 0xFF0000, 0xFF8080);
			break;
		}

		GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);

	}

	private void renderMeteor(int type) {

        GlStateManager.enableCull();
        GlStateManager.disableLighting();

		switch(type) {
		case 0:
			bindTexture(new ResourceLocation(Tags.MODID + ":textures/blocks/block_meteor_molten.png")); break;
		case 1:
			bindTexture(new ResourceLocation("textures/blocks/obsidian.png")); break;
		}

		ResourceManager.meteor.renderAll();

        GlStateManager.enableLighting();
	}

	private void renderFlechette() {

		GlStateManager.scale(0.5, 0.5, 0.5);
		GlStateManager.rotate(90, 0, 0, 1);
		GlStateManager.rotate(90, 0, 1, 0);

		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		bindTexture(ResourceManager.flechette_tex);
		ResourceManager.projectiles.renderPart("Flechette");
		GlStateManager.shadeModel(GL11.GL_FLAT);
	}

	private void renderDart(int style, int eID) {


		float red = 1F;
		float green = 1F;
		float blue = 1F;
		switch(style) {
		case BulletConfiguration.BOLT_LASER: red = 1F; green = 0F; blue = 0F; break;
		case BulletConfiguration.BOLT_NIGHTMARE: red = 1F; green = 1F; blue = 0F; break;
		case BulletConfiguration.BOLT_LACUNAE: red = 0.25F; green = 0F; blue = 0.75F; break;
		case BulletConfiguration.BOLT_WORM: red = 0F; green = 1F; blue = 0F; break;
		}

		GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.disableCull();
        GlStateManager.disableLighting();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GlStateManager.depthMask(false);

        GlStateManager.scale(1F/4F, 1F/8F, 1F/8F);
        GlStateManager.scale(-1, 1, 1);

        GlStateManager.scale(2, 2, 2);

		Tessellator tess = Tessellator.getInstance();
		BufferBuilder buf = tess.getBuffer();

		//front
		buf.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(3, -1, -1).color(red, green, blue, 0).endVertex();
		buf.pos(3, 1, -1).color(red, green, blue, 0).endVertex();

		buf.pos(3, -1, 1).color(red, green, blue, 0).endVertex();
		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(3, 1, 1).color(red, green, blue, 0).endVertex();

		buf.pos(3, -1, -1).color(red, green, blue, 0).endVertex();
		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(3, -1, 1).color(red, green, blue, 0).endVertex();

		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(3, 1, -1).color(red, green, blue, 0).endVertex();
		buf.pos(3, 1, 1).color(red, green, blue, 0).endVertex();

		//mid
		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(4, -0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, -0.5).color(red, green, blue, 1).endVertex();

		buf.pos(4, -0.5, 0.5).color(red, green, blue, 1).endVertex();
		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, 0.5).color(red, green, blue, 1).endVertex();

		buf.pos(4, -0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(4, -0.5, 0.5).color(red, green, blue, 1).endVertex();

		buf.pos(6, 0, 0).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, 0.5).color(red, green, blue, 1).endVertex();

		tess.draw();

		//tail
		buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

		buf.pos(4, 0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, 0.5).color(red, green, blue, 1).endVertex();
		buf.pos(0, 0.5, 0.5).color(red, green, blue, 0).endVertex();
		buf.pos(0, 0.5, -0.5).color(red, green, blue, 0).endVertex();


		buf.pos(4, -0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(4, -0.5, 0.5).color(red, green, blue, 1).endVertex();
		buf.pos(0, -0.5, 0.5).color(red, green, blue, 0).endVertex();
		buf.pos(0, -0.5, -0.5).color(red, green, blue, 0).endVertex();

		buf.pos(4, -0.5, 0.5).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, 0.5).color(red, green, blue, 1).endVertex();
		buf.pos(0, 0.5, 0.5).color(red, green, blue, 0).endVertex();
		buf.pos(0, -0.5, 0.5).color(red, green, blue, 0).endVertex();

		buf.pos(4, -0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(4, 0.5, -0.5).color(red, green, blue, 1).endVertex();
		buf.pos(0, 0.5, -0.5).color(red, green, blue, 0).endVertex();
		buf.pos(0, -0.5, -0.5).color(red, green, blue, 0).endVertex();

		tess.draw();

        GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();
        GlStateManager.enableLighting();
        GlStateManager.enableCull();
        GlStateManager.depthMask(true);

		GlStateManager.popMatrix();
	}

    private void renderBlade() {
        GlStateManager.pushMatrix();
        GlStateManager.rotate(90F, 0F, 0F, 1F);
        GlStateManager.translate(0D, 0.5D, 0D);
        GlStateManager.rotate(System.currentTimeMillis() % 360, 1F, 0F, 0F);
        GlStateManager.translate(0D, -0.5D, 0D);
        GlStateManager.rotate(90F, 0F, 1F, 0F);
        GlStateManager.scale(1F, 2F, 1F);

        // Vanilla 1.7 RenderItem.renderInFrame fixes the spin, scales the icon by
        // 0.5128205 and centers its one-copy extrusion. Native 1.12 item models
        // are already centered; NONE avoids adding a ground or hand transform.
        GlStateManager.translate(0F, 0.1F, 0F);
        GlStateManager.scale(0.5128205F, 0.5128205F, 0.5128205F);
        GlStateManager.translate(0F, -0.05F, 0F);
        GlStateManager.rotate(180F, 0F, 1F, 0F);
        GlStateManager.translate(0F, 0.25F, 0.0109375F);
        Minecraft.getMinecraft().getRenderItem().renderItem(new ItemStack(ModItems.blade_titanium), ItemCameraTransforms.TransformType.NONE);
        GlStateManager.popMatrix();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBulletBaseNT bullet) {
        return new ResourceLocation(Tags.MODID, "textures/models/bullet.png");
    }
}
