package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.CopycatMaterialGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers a Copycats+ material lookup that misses with a copycat base material instead of
 * null, so an empty multistate storage stops crashing the caller. See
 * {@link CopycatMaterialGuard} for the failure this comes from.
 */
@Mixin(targets = "com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage", remap = false)
public abstract class MixinCopycatMaterialItemStorage {

    @Inject(method = "getMaterialItem", at = @At("RETURN"), cancellable = true)
    private void arcadiaPatchCreate$replaceMissingMaterial(
        String property,
        CallbackInfoReturnable<Object> cir
    ) {
        if (cir.getReturnValue() != null) {
            return;
        }

        Object fallback = CopycatMaterialGuard.fallbackItem();
        if (fallback != null) {
            cir.setReturnValue(fallback);
        }
    }
}
