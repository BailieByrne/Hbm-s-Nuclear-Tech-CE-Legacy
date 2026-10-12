package com.hbm.entity.logic;

import com.hbm.config.BombConfig;
import com.hbm.config.CompatibilityConfig;
import com.hbm.config.GeneralConfig;
import com.hbm.entity.effect.EntityFalloutRain;
import com.hbm.explosion.ExplosionNukeGeneric;
import com.hbm.explosion.ExplosionNukeRayBatched;
import com.hbm.explosion.ExplosionNukeRayParallelized;
import com.hbm.handler.threading.BombForkJoinPool;
import com.hbm.interfaces.AutoRegister;
import com.hbm.interfaces.IExplosionRay;
import com.hbm.main.AdvancementManager;
import com.hbm.main.MainRegistry;
import com.hbm.saveddata.satellites.SatelliteDetector;
import com.hbm.saveddata.satellites.SatelliteDetector.BurstIntensity;
import com.hbm.util.ContaminationUtil;
import com.hbm.util.ContaminationUtil.ContaminationType;
import com.hbm.util.ContaminationUtil.HazardType;
import com.hbm.util.SectionGeneration;
import com.hbm.world.WorldUtil;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ForkJoinPool;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

@AutoRegister(name = "entity_nuke_mk5", trackingRange = 1000)
public class EntityNukeExplosionMK5 extends EntityExplosionChunkloading implements BombForkJoinPool.IJobCancellable {
    private int strength;
    private int radius;
    private int algorithm;
    private boolean fallout = true;
    private int falloutAdd;
    private IExplosionRay explosion;
    private boolean initialized;
    private boolean generationHeld;
    private ForkJoinPool generationPool;
    private long explosionStart;
    public UUID detonator;

    public EntityNukeExplosionMK5(World world) { super(world); }

    public EntityNukeExplosionMK5(World world, int strength, int speed, int radius) {
        this(world);
        this.strength = strength;
        this.radius = radius;
        this.algorithm = BombConfig.explosionAlgorithm;
    }

    public static EntityNukeExplosionMK5 statFac(World world, int radius, double x, double y, double z) {
        if (!world.isRemote && GeneralConfig.enableExtendedLogging) {
            MainRegistry.logger.info("[NUKE] Initialized explosion at {} / {} / {} with strength {}!", x, y, z, radius);
        }
        if (radius == 0) radius = 25;
        EntityNukeExplosionMK5 blast = new EntityNukeExplosionMK5(world);
        blast.strength = 2 * radius;
        blast.radius = radius;
        blast.algorithm = BombConfig.explosionAlgorithm;
        blast.setPosition(x, y, z);
        if (BombConfig.disableNuclear) blast.fallout = false;
        return blast;
    }

    public int getRadius() { return radius; }

    public static EntityNukeExplosionMK5 statFacNoRad(World world, int radius, double x, double y, double z) {
        EntityNukeExplosionMK5 blast = statFac(world, radius, x, y, z);
        blast.fallout = false;
        return blast;
    }

    public EntityNukeExplosionMK5 setDetonator(Entity source) {
        if (source instanceof EntityPlayer) detonator = source.getUniqueID();
        return this;
    }

    public EntityNukeExplosionMK5 moreFallout(int additional) {
        falloutAdd = additional;
        return this;
    }

    public EntityNukeExplosionMK5 forceSpawn() {
        forceSpawn = true;
        return this;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) { setDead(); return; }
        if (strength == 0 || !CompatibilityConfig.isWarDim(world)) { setDead(); return; }

        for (EntityPlayer player : world.playerEntities) {
            AdvancementManager.grantAchievement(player, AdvancementManager.achManhattan);
        }
        double range = radius * 2D;
        ExplosionNukeGeneric.dealDamage(world, WorldUtil.getEntitiesInRadius(world, posX, posY, posZ, range), posX, posY, posZ, range);
        if (initialized && fallout && ticksExisted < 10 && strength >= 75) {
            radiateYoungBlast(range, 2_500_000F / (ticksExisted * 5 + 1));
        }

        if (!initialized) {
            explosionStart = System.currentTimeMillis();
            if (algorithm > 0) {
                generationPool = BombForkJoinPool.acquire();
                BombForkJoinPool.register(generationPool, world.provider.getDimension(), this);
                try {
                    SectionGeneration.acquire();
                    generationHeld = true;
                    explosion = new ExplosionNukeRayParallelized(world, posX, posY, posZ, strength, radius, algorithm);
                } catch (RuntimeException | Error failure) {
                    setDead();
                    throw failure;
                }
            } else {
                explosion = new ExplosionNukeRayBatched(world, MathHelper.floor(posX), MathHelper.floor(posY), MathHelper.floor(posZ), strength, radius);
            }
            SatelliteDetector.reportEvent(world, SatelliteDetector.DURATION_HIGH, BurstIntensity.HIGH, posX, posZ);
            initialized = true;
        }
        if (explosion.hasFailed()) {
            setDead();
        } else if (!explosion.isComplete()) {
            explosion.update(BombConfig.mk5);
        } else {
            if (GeneralConfig.enableExtendedLogging) {
                MainRegistry.logger.info("[NUKE] Explosion complete. Time elapsed: {}ms", System.currentTimeMillis() - explosionStart);
            }
            spawnFallout();
            setDead();
        }
    }

    private void radiateYoungBlast(double range, double radiation) {
        List<EntityLivingBase> living = WorldUtil.getEntitiesWithinAABB((WorldServer) world, EntityLivingBase.class,
                new AxisAlignedBB(posX - range, posY - range, posZ - range, posX + range, posY + range, posZ + range));
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (EntityLivingBase entity : living) {
            if (ContaminationUtil.isRadImmune(entity)) continue;
            double dx = entity.posX - posX;
            double dy = entity.posY + entity.getEyeHeight() - posY;
            double dz = entity.posZ - posZ;
            double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double inverse = 1D / Math.max(length, 1.0E-4D);
            double resistance = 0D;
            for (int step = 1; step < length; step++) {
                position.setPos(MathHelper.floor(posX + dx * inverse * step), MathHelper.floor(posY + dy * inverse * step), MathHelper.floor(posZ + dz * inverse * step));
                resistance += world.getBlockState(position).getBlock().getExplosionResistance(null);
            }
            if (resistance < 1D) resistance = 1D;
            ContaminationUtil.contaminate(entity, HazardType.RADIATION, ContaminationType.RAD_BYPASS, radiation / resistance / (length * length));
        }
    }

    private void spawnFallout() {
        if (!fallout) return;
        int scale = Math.max(1, (int) (radius * 2.5D + falloutAdd) * BombConfig.falloutRange / 100);
        EntityFalloutRain rain = new EntityFalloutRain(world);
        rain.setPosition(posX, posY, posZ);
        rain.setScale(scale);
        if (world.spawnEntity(rain) && !rain.isDead) rain.holdGenerationTracking();
    }

    private void releaseRuntime() {
        try {
            if (explosion != null) explosion.cancel();
        } finally {
            if (generationHeld) {
                generationHeld = false;
                SectionGeneration.release();
            }
            if (generationPool != null) {
                BombForkJoinPool.unregister(generationPool, world.provider.getDimension(), this);
                BombForkJoinPool.release(generationPool);
                generationPool = null;
            }
        }
    }

    @Override public void setDead() {
        try { releaseRuntime(); }
        finally {
            try { clearChunkLoader(); }
            finally { super.setDead(); }
        }
    }

    @Override public void onRemovedFromWorld() {
        try { releaseRuntime(); }
        finally {
            try { clearChunkLoader(); }
            finally { super.onRemovedFromWorld(); }
        }
    }

    @Override public void cancelJob() {
        if (world.getMinecraftServer().isCallingFromMinecraftThread()) setDead();
        else ((WorldServer) world).addScheduledTask(this::setDead);
    }

    @Override public boolean attackEntityFrom(DamageSource source, float amount) { return false; }

    @Override public void readEntityFromNBT(NBTTagCompound tag) {
        markChunkLoaderRestoredFromNBT();
        releaseRuntime();
        explosion = null;
        initialized = false;
        explosionStart = 0L;
        radius = tag.getInteger("radius");
        strength = tag.getInteger("strength");
        algorithm = tag.hasKey("algorithm") ? tag.getInteger("algorithm") : BombConfig.explosionAlgorithm;
        fallout = !tag.hasKey("fallout") || tag.getBoolean("fallout");
        falloutAdd = tag.getInteger("falloutAdd");
        detonator = tag.hasKey("detonator") ? tag.getUniqueId("detonator") : null;
    }

    @Override public void writeEntityToNBT(NBTTagCompound tag) {
        tag.setInteger("radius", radius);
        tag.setInteger("strength", strength);
        tag.setInteger("algorithm", algorithm);
        tag.setBoolean("fallout", fallout);
        tag.setInteger("falloutAdd", falloutAdd);
        if (detonator != null) tag.setUniqueId("detonator", detonator);
    }
}
