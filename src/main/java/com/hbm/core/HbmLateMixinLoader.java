package com.hbm.core;

import java.util.Arrays;
import java.util.List;
import zone.rong.mixinbooter.Context;
import zone.rong.mixinbooter.ILateMixinLoader;

public final class HbmLateMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        return Arrays.asList("hbm.mousetweaks.mixin.json", "hbm.treechop.mixin.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(Context context) {
        switch (context.mixinConfig()) {
            case "hbm.mousetweaks.mixin.json":
                return context.isModPresent("mousetweaks");
            case "hbm.treechop.mixin.json":
                return context.isModPresent("treechop");
            default:
                throw new IllegalArgumentException("Unknown late mixin configuration: " + context.mixinConfig());
        }
    }
}
