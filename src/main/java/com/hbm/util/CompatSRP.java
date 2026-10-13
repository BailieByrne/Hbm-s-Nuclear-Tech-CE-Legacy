package com.hbm.util;

import com.hbm.explosion.vanillant.ExplosionVNT;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/** Optional Scape and Run: Parasites compatibility for NTM explosions. */
public final class CompatSRP {
    private static final ResourceLocation SCARY_ORB = new ResourceLocation("srparasites", "orbscary");

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getExplosion() instanceof ExplosionVNT.CompatExplosion)) return;
        List<Entity> affected = event.getAffectedEntities();
        for (int i = affected.size() - 1; i >= 0; i--) {
            if (SCARY_ORB.equals(EntityList.getKey(affected.get(i)))) affected.remove(i);
        }
    }
}
