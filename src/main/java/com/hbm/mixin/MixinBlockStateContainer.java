package com.hbm.mixin;

import com.hbm.util.SectionPalette;
import net.minecraft.world.chunk.BlockStateContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockStateContainer.class)
public abstract class MixinBlockStateContainer {
    @Unique
    public volatile SectionPalette.Data hbm$paletteData;

    @Inject(method = "setBits(IZ)V", at = @At("RETURN"), remap = false, require = 1)
    private void hbm$publishPaletteGeneration(CallbackInfo ci) {
        SectionPalette.resized((BlockStateContainer) (Object) this);
    }
}
