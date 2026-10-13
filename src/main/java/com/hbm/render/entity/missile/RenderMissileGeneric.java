package com.hbm.render.entity.missile;

import org.lwjgl.opengl.GL11;
import com.hbm.interfaces.AutoRegister;
import com.hbm.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.client.registry.IRenderFactory;


import com.hbm.entity.missile.EntityMissileAntiBallistic;
import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.missile.EntityMissileStealth;
import com.hbm.entity.missile.EntityMissileTier1.*;
import com.hbm.main.ResourceManager;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@AutoRegister(entity = EntityMissileGeneric.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileIncendiary.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileCluster.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileBunkerBuster.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileDecoy.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileAntiBallistic.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileStealth.class, factory = "FACTORY")
public class RenderMissileGeneric extends Render<Entity> {
    public static final IRenderFactory<Entity> FACTORY = RenderMissileGeneric::new;

	public RenderMissileGeneric(RenderManager manager) { super(manager); }

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

		if(entity instanceof EntityMissileAntiBallistic) {
			bindTexture(ResourceManager.missileAA_tex);
			GlStateManager.shadeModel(GL11.GL_SMOOTH);
			ResourceManager.missileABM.renderAll();
			GlStateManager.shadeModel(previousShade);
		} else if(entity instanceof EntityMissileStealth) {
			bindTexture(ResourceManager.missileStealth_tex);
			ResourceManager.missileStealth.renderAll();
		} else {

			if(entity instanceof EntityMissileGeneric)
				bindTexture(ResourceManager.missileV2_HE_tex);
			if(entity instanceof EntityMissileIncendiary)
				bindTexture(ResourceManager.missileV2_IN_tex);
			if(entity instanceof EntityMissileCluster)
				bindTexture(ResourceManager.missileV2_CL_tex);
			if(entity instanceof EntityMissileBunkerBuster)
				bindTexture(ResourceManager.missileV2_BU_tex);
			if(entity instanceof EntityMissileDecoy)
				bindTexture(ResourceManager.missileV2_decoy_tex);

			GlStateManager.shadeModel(GL11.GL_SMOOTH);
			ResourceManager.missileV2.renderAll();
			GlStateManager.shadeModel(previousShade);
		}
        if (!previousLighting) GlStateManager.disableLighting();
		GlStateManager.popMatrix();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity p_110775_1_) {
		return ResourceManager.missileV2_HE_tex;
	}

}
