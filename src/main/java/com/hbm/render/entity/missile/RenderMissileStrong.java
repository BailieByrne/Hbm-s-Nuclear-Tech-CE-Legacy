package com.hbm.render.entity.missile;

import org.lwjgl.opengl.GL11;
import com.hbm.interfaces.AutoRegister;
import com.hbm.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.client.registry.IRenderFactory;


import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.missile.EntityMissileTier2.*;
import com.hbm.main.ResourceManager;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@AutoRegister(entity = EntityMissileStrong.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileIncendiaryStrong.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileClusterStrong.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileBusterStrong.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileEMPStrong.class, factory = "FACTORY")
public class RenderMissileStrong extends Render<Entity> {
    public static final IRenderFactory<Entity> FACTORY = RenderMissileStrong::new;
	
	public RenderMissileStrong(RenderManager manager) { super(manager); }

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
		GlStateManager.scale(1.5F, 1.5F, 1.5F);

		if(entity instanceof EntityMissileBaseNT) switch(entity.getDataManager().get(EntityMissileBaseNT.ROT_IDX)) {
		case 2: GlStateManager.rotate(90, 0F, 1F, 0F); break;
		case 4: GlStateManager.rotate(180, 0F, 1F, 0F); break;
		case 3: GlStateManager.rotate(270, 0F, 1F, 0F); break;
		case 5: GlStateManager.rotate(0, 0F, 1F, 0F); break;
		}

		if(entity instanceof EntityMissileStrong) bindTexture(ResourceManager.missileStrong_HE_tex);
		if(entity instanceof EntityMissileIncendiaryStrong) bindTexture(ResourceManager.missileStrong_IN_tex);
		if(entity instanceof EntityMissileClusterStrong) bindTexture(ResourceManager.missileStrong_CL_tex);
		if(entity instanceof EntityMissileBusterStrong) bindTexture(ResourceManager.missileStrong_BU_tex);
		if(entity instanceof EntityMissileEMPStrong) bindTexture(ResourceManager.missileStrong_EMP_tex);
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		ResourceManager.missileStrong.renderAll();
		GlStateManager.shadeModel(previousShade);
        if (!previousLighting) GlStateManager.disableLighting();
		GlStateManager.popMatrix();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity p_110775_1_) {
		return ResourceManager.missileStrong_HE_tex;
	}

}
