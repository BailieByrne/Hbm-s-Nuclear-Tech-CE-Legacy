package com.hbm.render.entity.missile;

import org.lwjgl.opengl.GL11;
import com.hbm.interfaces.AutoRegister;
import com.hbm.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraftforge.fml.client.registry.IRenderFactory;


import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.missile.EntityMissileTier0.*;
import com.hbm.main.ResourceManager;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

@AutoRegister(entity = EntityMissileTest.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileMicro.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileBHole.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileSchrabidium.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileTaint.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileEMP.class, factory = "FACTORY")
public class RenderMissileTaint extends Render<Entity> {
    public static final IRenderFactory<Entity> FACTORY = RenderMissileTaint::new;
	
	public RenderMissileTaint(RenderManager manager) { super(manager); }

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

		GlStateManager.enableCull();
		bindTexture(getEntityTexture(entity));
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		ResourceManager.missileMicro.renderAll();
		GlStateManager.shadeModel(previousShade);
        if (!previousLighting) GlStateManager.disableLighting();
		GlStateManager.popMatrix();
	}

	@Override
	protected ResourceLocation getEntityTexture(Entity p_110775_1_) {
		if(p_110775_1_ instanceof EntityMissileTaint)
			return ResourceManager.missileTaint_tex;
		if(p_110775_1_ instanceof EntityMissileBHole)
			return ResourceManager.missileMicroBHole_tex;
		if(p_110775_1_ instanceof EntityMissileSchrabidium)
			return ResourceManager.missileMicroSchrab_tex;
		if(p_110775_1_ instanceof EntityMissileEMP)
			return ResourceManager.missileMicroEMP_tex;
		if(p_110775_1_ instanceof EntityMissileTest)
			return ResourceManager.missileMicroTest_tex;

		return ResourceManager.missileMicro_tex;
	}
}
