package com.hbm.mixin;

import com.hbm.entity.logic.IChunkLoader;
import com.hbm.main.MainRegistry;
import net.minecraft.entity.Entity;
import net.minecraftforge.common.ForgeChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class MixinEntityChunkLoader {

    @Inject(method = "onAddedToWorld", at = @At("RETURN"), remap = false, require = 1)
    private void hbm$bootstrapAdmittedChunkLoader(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.world.isRemote || entity.isDead || !(entity instanceof IChunkLoader)) return;
        IChunkLoader loader = (IChunkLoader) entity;
        // Admission and join-event cancellation have finished. Bootstrap before
        // Forge's chunk-residency check can withhold this entity's first update.
        if (loader.shouldBootstrapChunkLoader()) {
            loader.init(ForgeChunkManager.requestTicket(MainRegistry.instance, entity.world, ForgeChunkManager.Type.ENTITY));
        }
    }
}
