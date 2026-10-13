package com.hbm.render.entity.missile;

import org.lwjgl.opengl.GL11;
import com.hbm.interfaces.AutoRegister;
import com.hbm.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.client.registry.IRenderFactory;


import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.missile.EntityMissileTier3.*;
import com.hbm.main.ResourceManager;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@AutoRegister(entity = EntityMissileBurst.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileInferno.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileRain.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileDrill.class, factory = "FACTORY")
public class RenderMissileHuge extends Render<Entity> {
    public static final IRenderFactory<Entity> FACTORY = RenderMissileHuge::new;

	public RenderMissileHuge(RenderManager manager) { super(manager); }

	@Override
	public void doRender(Entity entity, double x, double y, double z, float p_76986_8_, float interp) {

		GlStateManager.pushMatrix();
        boolean previousLighting = RenderUtil.isLightingEnabled();
        int previousShade = RenderUtil.getShadeModel();
        if (!previousLighting) GlStateManager.enableLighting();
		GlStateManager.translate((float) x, (float) y, (float) z);
		GlStateManager.rotate(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * interp - 90.0F, 0.0F, 1.0F, 0.0F);
		GlStateManager.rotate(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * interp, 0.0F, 0.0F, 1.0F);
		GlStateManager.rotate(entity.prevRotationYaw + (entity.rotationYaw - entity.prevRotationYaw) * interp - 90.0F, 0.0F, -1.0F, 0.0F);

		if(entity instanceof EntityMissileBaseNT) switch(entity.getDataManager().get(EntityMissileBaseNT.ROT_IDX)) {
		case 2: GlStateManager.rotate(90, 0F, 1F, 0F); break;
		case 4: GlStateManager.rotate(180, 0F, 1F, 0F); break;
		case 3: GlStateManager.rotate(270, 0F, 1F, 0F); break;
		case 5: GlStateManager.rotate(0, 0F, 1F, 0F); break;
		}

		if(entity instanceof EntityMissileBurst) bindTexture(ResourceManager.missileHuge_HE_tex);
		if(entity instanceof EntityMissileInferno) bindTexture(ResourceManager.missileHuge_IN_tex);
		if(entity instanceof EntityMissileRain) bindTexture(ResourceManager.missileHuge_CL_tex);
		if(entity instanceof EntityMissileDrill) bindTexture(ResourceManager.missileHuge_BU_tex);
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		ResourceManager.missileHuge.renderAll();
		GlStateManager.shadeModel(previousShade);
        if (!previousLighting) GlStateManager.disableLighting();
		GlStateManager.popMatrix();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity p_110775_1_) {
		return ResourceManager.missileHuge_HE_tex;
	}
}
