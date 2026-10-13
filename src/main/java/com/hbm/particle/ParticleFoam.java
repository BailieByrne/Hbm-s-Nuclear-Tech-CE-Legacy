package com.hbm.particle;

import com.hbm.main.client.NTMClientRegistry;
import com.hbm.render.util.NTMBufferBuilder;
import net.minecraft.block.material.Material;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.Random;

@SideOnly(Side.CLIENT)
public class ParticleFoam extends Particle {

    private static int seedSequence;
    // Particles no longer inherit an entity ID in 1.12.
    private final int id = seedSequence++;
    private int age;
    public int maxAge;
    private float baseScale = 1.0F;
    private float maxScale = 1.5F;
    private double[] trail = new double[15 * 3];
    private int trailSize;
    private int trailLength = 15;
    private float buoyancy = 0.05F;
    private final float jitter = 0.15F;
    private final float drag = 0.96F;
    private int explosionPhase;

    public ParticleFoam(World world, double x, double y, double z) {
        super(world, x, y, z);
        setParticleTexture(NTMClientRegistry.particle_base);
        maxAge = 60 + rand.nextInt(60);
        particleGravity = 0.005F + rand.nextFloat() * 0.015F;
        motionY = 2.0F + rand.nextFloat() * 3.0F;
        double angle = rand.nextDouble() * Math.PI * 2;
        double strength = rand.nextDouble() * 0.5;
        motionX = Math.cos(angle) * strength;
        motionZ = Math.sin(angle) * strength;
        explosionPhase = 0;
        particleScale = 0.3F + rand.nextFloat() * 0.7F;
    }

    public void setBaseScale(float scale) { baseScale = scale; }
    public void setMaxScale(float scale) { maxScale = scale; }
    public void setTrailLength(int length) {
        if (length < 0) throw new IllegalArgumentException("Negative trail length: " + length);
        trailLength = length;
    }
    public void setBuoyancy(float buoyancy) { this.buoyancy = buoyancy; }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        int size = trailLength == 0 ? 0 : Math.min(trailSize, trailLength - 1) + 1;
        if (size > 0) {
            if (size > trail.length / 3) {
                int capacity = Math.max(size, trail.length / 2);
                trail = Arrays.copyOf(trail, Math.multiplyExact(capacity, 3));
            }
            System.arraycopy(trail, 0, trail, 3, (size - 1) * 3);
            trail[0] = posX;
            trail[1] = posY;
            trail[2] = posZ;
        }
        trailSize = size;

        ++age;
        if (age == maxAge) setExpired();

        float phaseRatio = (float) age / (float) maxAge;
        if (phaseRatio < 0.3F) {
            explosionPhase = 0;
            if (phaseRatio < 0.15F) motionY += buoyancy * 6.0F;
            else motionY += buoyancy * (1.0F - (phaseRatio / 0.3F)) * 2.0F;
            particleScale = baseScale + (maxScale - baseScale) * (phaseRatio / 0.3F);
        } else if (phaseRatio < 0.6F) {
            explosionPhase = 1;
            motionY *= 0.98F;
            particleScale = maxScale;
        } else {
            explosionPhase = 2;
            motionY -= particleGravity;
            particleScale = maxScale * (1.0F - ((phaseRatio - 0.6F) / 0.4F) * 0.7F);
        }

        particleAlpha = 0.8F * (1.0F - phaseRatio * phaseRatio);
        motionX += (rand.nextFloat() - 0.5F) * jitter;
        motionZ += (rand.nextFloat() - 0.5F) * jitter;
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        move(motionX, motionY, motionZ);

        if (onGround) {
            setExpired();
            return;
        }
        // EntityFX inherited Entity.isInWeb; 1.12 Particle has no equivalent.
        AxisAlignedBB bounds = getBoundingBox().shrink(0.001D);
        for (BlockPos pos : BlockPos.getAllInBox(new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (world.getBlockState(pos).getMaterial() == Material.WEB) {
                setExpired();
                return;
            }
        }
    }

    @Override
    public int getFXLayer() { return 1; }

    @Override
    public void renderParticle(BufferBuilder buffer, Entity entity, float partialTicks, float rotationX,
            float rotationZ, float rotationYZ, float rotationXY, float rotationXZ) {
        NTMBufferBuilder fastBuffer = (NTMBufferBuilder) buffer;
        int light = getBrightnessForRender(partialTicks);
        int packedLight = NTMBufferBuilder.packLightmap(light >> 16 & 65535, light & 65535);
        renderFoamBubbles(fastBuffer, packedLight, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ,
                posX, posY, posZ, particleScale, particleAlpha);
        for (int i = 1; i < trailSize; i++) {
            int point = i * 3;
            float trailScale = particleScale * (1.0F - (float) i / trailLength);
            float trailAlpha = particleAlpha * (1.0F - (float) i / trailLength) * 0.7F;
            renderFoamBubbles(fastBuffer, packedLight, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ,
                    trail[point], trail[point + 1], trail[point + 2], trailScale, trailAlpha);
        }
    }

    private void renderFoamBubbles(NTMBufferBuilder buffer, int packedLight, float rotationX, float rotationZ,
            float rotationYZ, float rotationXY, float rotationXZ, double x, double y, double z, float scale, float alpha) {
        Random urandom = new Random(id + (long) (x * 100) + (long) (y * 10) + (long) z);
        int bubbleCount = explosionPhase == 0 ? 8 : (explosionPhase == 1 ? 6 : 4);
        for (int i = 0; i < bubbleCount; i++) {
            float whiteness = 0.9F + urandom.nextFloat() * 0.1F;
            int packedColor = NTMBufferBuilder.packColor(whiteness, whiteness, whiteness, alpha);
            float bubbleScale = scale * (urandom.nextFloat() * 0.5F + 0.75F);
            float offset = explosionPhase == 0 ? 0.4F : (explosionPhase == 1 ? 0.6F : 0.9F);
            float pX = (float) ((x - interpPosX) + urandom.nextGaussian() * offset);
            float pY = (float) ((y - interpPosY) + urandom.nextGaussian() * offset * 0.7F);
            float pZ = (float) ((z - interpPosZ) + urandom.nextGaussian() * offset);

            buffer.appendParticlePositionTexColorLmap(pX - rotationX * bubbleScale - rotationXY * bubbleScale,
                    pY - rotationZ * bubbleScale, pZ - rotationYZ * bubbleScale - rotationXZ * bubbleScale,
                    particleTexture.getMaxU(), particleTexture.getMaxV(), packedColor, packedLight);
            buffer.appendParticlePositionTexColorLmap(pX - rotationX * bubbleScale + rotationXY * bubbleScale,
                    pY + rotationZ * bubbleScale, pZ - rotationYZ * bubbleScale + rotationXZ * bubbleScale,
                    particleTexture.getMaxU(), particleTexture.getMinV(), packedColor, packedLight);
            buffer.appendParticlePositionTexColorLmap(pX + rotationX * bubbleScale + rotationXY * bubbleScale,
                    pY + rotationZ * bubbleScale, pZ + rotationYZ * bubbleScale + rotationXZ * bubbleScale,
                    particleTexture.getMinU(), particleTexture.getMinV(), packedColor, packedLight);
            buffer.appendParticlePositionTexColorLmap(pX + rotationX * bubbleScale - rotationXY * bubbleScale,
                    pY - rotationZ * bubbleScale, pZ + rotationYZ * bubbleScale - rotationXZ * bubbleScale,
                    particleTexture.getMinU(), particleTexture.getMaxV(), packedColor, packedLight);
        }
    }
}
