package com.hbm.entity.projectile;

import com.hbm.config.BombConfig;
import com.hbm.entity.effect.EntityMist;
import com.hbm.entity.logic.EntityNukeExplosionMK5;
import com.hbm.interfaces.AutoRegister;
import com.hbm.interfaces.IConstantRenderer;
import com.hbm.lib.HBMSoundHandler;
import com.hbm.explosion.vanillant.ExplosionVNT;
import com.hbm.explosion.vanillant.standard.BlockAllocatorStandard;
import com.hbm.explosion.vanillant.standard.BlockMutatorFire;
import com.hbm.explosion.vanillant.standard.BlockProcessorStandard;
import com.hbm.explosion.vanillant.standard.EntityProcessorCrossSmooth;
import com.hbm.explosion.vanillant.standard.ExplosionEffectWeapon;
import com.hbm.explosion.vanillant.standard.PlayerProcessorStandard;
import com.hbm.handler.threading.PacketThreading;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.packet.toclient.AuxParticlePacketNT;

import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

@AutoRegister(name = "entity_zeta", trackingRange = 1000)
public class EntityBombletZeta extends EntityThrowable implements IConstantRenderer {

	public int type = 0;

	public EntityBombletZeta(World p_i1582_1_) {
		super(p_i1582_1_);
		this.ignoreFrustumCheck = true;
	}

	@Override
	public void onUpdate() {

		this.lastTickPosX = this.prevPosX = posX;
		this.lastTickPosY = this.prevPosY = posY;
		this.lastTickPosZ = this.prevPosZ = posZ;
		this.setPosition(posX + this.motionX, posY + this.motionY, posZ + this.motionZ);

		this.motionX *= 0.99;
		this.motionZ *= 0.99;
		this.motionY -= 0.05D;

		this.rotation();

		if(this.world.getBlockState(new BlockPos((int) this.posX, (int) this.posY, (int) this.posZ)).getBlock() != Blocks.AIR) {
			if(!this.world.isRemote) {
				if(type == 0) {
					ExplosionVNT vnt = new ExplosionVNT(world, this.posX + 0.5F, this.posY + 1.5F, this.posZ + 0.5F, 4F);
					vnt.setBlockAllocator(new BlockAllocatorStandard());
					vnt.setBlockProcessor(new BlockProcessorStandard());
					vnt.setEntityProcessor(new EntityProcessorCrossSmooth(1, 100));
					vnt.setPlayerProcessor(new PlayerProcessorStandard());
					vnt.setSFX(new ExplosionEffectWeapon(15, 3.5F, 1.25F));
					vnt.explode();
				}
				if(type == 1) {

					ExplosionVNT vnt = new ExplosionVNT(world, this.posX + 0.5F, this.posY + 1.5F, this.posZ + 0.5F, 4F);
					vnt.setBlockAllocator(new BlockAllocatorStandard());
					vnt.setBlockProcessor(new BlockProcessorStandard().withBlockEffect(new BlockMutatorFire()));
					vnt.setEntityProcessor(new EntityProcessorCrossSmooth(1, 100));
					vnt.setPlayerProcessor(new PlayerProcessorStandard());
					vnt.setSFX(new ExplosionEffectWeapon(15, 5F, 1.75F));
					vnt.explode();

				}
				if(type == 2) {
					world.playSound(null, posX + 0.5F, posY + 0.5F, posZ + 0.5F, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 5.0F, 2.6F + (rand.nextFloat() - rand.nextFloat()) * 0.8F);
					EntityMist mist = new EntityMist(world);
					mist.setType(Fluids.CHLORINE);
					mist.setPosition(this.posX - motionX, this.posY - motionY, this.posZ - motionZ);
					mist.setArea(15, 7.5F);
					world.spawnEntity(mist);
				}
				if(type == 4) {
					world.spawnEntity(EntityNukeExplosionMK5.statFac(world, (int) (BombConfig.fatmanRadius * 1.5), posX, posY, posZ).setDetonator(getThrower()));

					NBTTagCompound data = new NBTTagCompound();
					data.setString("type", "muke");
					if(rand.nextInt(100) == 0)
						data.setBoolean("balefire", true);
					PacketThreading.createAllAroundThreadedPacket(new AuxParticlePacketNT(data, posX, posY + 0.5, posZ), new TargetPoint(dimension, posX, posY, posZ, 250));
					world.playSound(null, posX, posY, posZ, HBMSoundHandler.mukeExplosion, SoundCategory.HOSTILE, 15.0F, 1.0F);
				}
				this.setDead();
			}
		}
	}

	public void rotation() {
        float f2 = MathHelper.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.rotationYaw = (float)(Math.atan2(this.motionX, this.motionZ) * 180.0D / Math.PI);

        for (this.rotationPitch = (float)(Math.atan2(this.motionY, f2) * 180.0D / Math.PI) - 90; this.rotationPitch - this.prevRotationPitch < -180.0F; this.prevRotationPitch -= 360.0F)
        {
            ;
        }

        while (this.rotationPitch - this.prevRotationPitch >= 180.0F)
        {
            this.prevRotationPitch += 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw < -180.0F)
        {
            this.prevRotationYaw -= 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw >= 180.0F)
        {
            this.prevRotationYaw += 360.0F;
        }
	}

	@Override
	protected void onImpact(RayTraceResult p_70184_1_) {
	}

    @Override
	@SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double distance)
    {
        return distance < 25000;
    }
}
