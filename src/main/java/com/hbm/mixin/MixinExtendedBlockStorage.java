package com.hbm.mixin;

import com.hbm.util.SectionGeneration;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ExtendedBlockStorage.class)
public abstract class MixinExtendedBlockStorage {
    @Unique
    public long hbm$generation;

    @Inject(method = {"set", "setData"}, at = @At("RETURN"))
    private void hbm$blockStatesChanged(CallbackInfo ci) {
        SectionGeneration.changed((ExtendedBlockStorage) (Object) this);
    }
}
