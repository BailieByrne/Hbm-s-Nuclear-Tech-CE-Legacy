package com.hbm.render.entity.missile;

import com.hbm.entity.missile.EntityMissileBaseNT;
import com.hbm.entity.missile.EntityMissileTier4;
import com.hbm.interfaces.AutoRegister;
import com.hbm.main.ResourceManager;
import org.lwjgl.opengl.GL11;
import com.hbm.util.RenderUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.client.registry.IRenderFactory;
// PRACTICALLY - TIER 4 MISSILES
@AutoRegister(entity = EntityMissileTier4.EntityMissileNuclear.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileTier4.EntityMissileMirv.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileTier4.EntityMissileVolcano.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileTier4.EntityMissileDoomsday.class, factory = "FACTORY")
@AutoRegister(entity = EntityMissileTier4.EntityMissileDoomsdayRusted.class, factory = "FACTORY")
public class RenderMissileNuclear extends Render<EntityMissileBaseNT> {

	public static final IRenderFactory<EntityMissileBaseNT> FACTORY = (RenderManager man) -> {return new RenderMissileNuclear(man);};
	
	protected RenderMissileNuclear(RenderManager renderManager) {
		super(renderManager);
	}
	
	@Override
	public void doRender(EntityMissileBaseNT missile, double x, double y, double z, float entityYaw, float partialTicks) {
		GlStateManager.pushMatrix();
        boolean prevLighting = RenderUtil.isLightingEnabled();
        if (!prevLighting) GlStateManager.enableLighting();
        GlStateManager.translate(x, y, z);
        float yaw = missile.prevRotationYaw + (missile.rotationYaw - missile.prevRotationYaw) * partialTicks - 90F;
        GlStateManager.rotate(yaw, 0F, 1F, 0F);
        GlStateManager.rotate(missile.prevRotationPitch + (missile.rotationPitch - missile.prevRotationPitch) * partialTicks, 0F, 0F, 1F);
        GlStateManager.rotate(yaw, 0F, -1F, 0F);
        switch (missile.getDataManager().get(EntityMissileBaseNT.ROT_IDX)) {
            case 2 -> GlStateManager.rotate(90F, 0F, 1F, 0F);
            case 4 -> GlStateManager.rotate(180F, 0F, 1F, 0F);
            case 3 -> GlStateManager.rotate(270F, 0F, 1F, 0F);
            case 5 -> GlStateManager.rotate(0F, 0F, 1F, 0F);
        }
        if(missile instanceof EntityMissileTier4.EntityMissileVolcano)
			bindTexture(ResourceManager.missileVolcano_tex);
		else if(missile instanceof EntityMissileTier4.EntityMissileNuclear)
			bindTexture(ResourceManager.missileNuclear_tex);
		else if(missile instanceof EntityMissileTier4.EntityMissileMirv)
			bindTexture(ResourceManager.missileMIRV_tex);
		else if(missile instanceof EntityMissileTier4.EntityMissileDoomsdayRusted)
			bindTexture(ResourceManager.missileDoomsdayRusted_tex);
		else if(missile instanceof EntityMissileTier4.EntityMissileDoomsday)
			bindTexture(ResourceManager.missileDoomsday_tex);

        int previousShade = RenderUtil.getShadeModel();
        GlStateManager.shadeModel(GL11.GL_SMOOTH);
        ResourceManager.missileNuclear.renderAll();
        GlStateManager.shadeModel(previousShade);
        if (!prevLighting) GlStateManager.disableLighting();
		GlStateManager.popMatrix();
	}

	@Override
	protected ResourceLocation getEntityTexture(EntityMissileBaseNT entity) {
		return ResourceManager.missileNuclear_tex;
	}

}
