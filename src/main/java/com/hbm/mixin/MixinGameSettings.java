package com.hbm.mixin;

import com.hbm.main.client.StaticDecoBakedModels;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameSettings.class)
public abstract class MixinGameSettings {
    @Inject(method = "setOptionValue", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderGlobal;loadRenderers()V", shift = At.Shift.AFTER), require = 1)
    private void hbm$refreshGraphicsModels(GameSettings.Options option, int value, CallbackInfo ci) {
        if (option == GameSettings.Options.GRAPHICS) StaticDecoBakedModels.graphicsChanged();
    }
}
