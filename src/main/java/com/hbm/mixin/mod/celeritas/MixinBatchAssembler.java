package com.hbm.mixin.mod.celeritas;

import com.hbm.render.chunk.CeleritasCameraTransformAccess;
import org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BatchAssembler.class, remap = false)
public abstract class MixinBatchAssembler {

    @Dynamic
    @Redirect(method = {"createCachedBatch", "uniformCullMask", "fillPerSectionRuns"},
            at = @At(value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;intX:I"),
            require = 4)
    private static int hbm$useUnsafeIntX(CameraTransform camera) {
        return CeleritasCameraTransformAccess.getIntX(camera);
    }

    @Dynamic
    @Redirect(method = {"createCachedBatch", "uniformCullMask", "fillPerSectionRuns"},
            at = @At(value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;intY:I"),
            require = 4)
    private static int hbm$useUnsafeIntY(CameraTransform camera) {
        return CeleritasCameraTransformAccess.getIntY(camera);
    }

    @Dynamic
    @Redirect(method = {"createCachedBatch", "uniformCullMask", "fillPerSectionRuns"},
            at = @At(value = "FIELD",
                    opcode = Opcodes.GETFIELD,
                    target = "Lorg/embeddedt/embeddium/impl/render/viewport/CameraTransform;intZ:I"),
            require = 4)
    private static int hbm$useUnsafeIntZ(CameraTransform camera) {
        return CeleritasCameraTransformAccess.getIntZ(camera);
    }
}
