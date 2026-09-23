package fr.arcadia.arcadiapatchcreate.mixin;

import fr.arcadia.arcadiapatchcreate.runtime.IronsCauldronGuard;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Answers common ink for a scroll that carries no spell, instead of letting the cauldron
 * dereference a missing spell container. See {@link IronsCauldronGuard}.
 */
@Mixin(targets = "io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile", remap = false)
public abstract class MixinIronsAlchemistCauldron {

    @Inject(method = "getInkFromScroll", at = @At("HEAD"), cancellable = true)
    private static void arcadiaPatchCreate$recycleBlankScroll(
        ItemStack scrollStack,
        CallbackInfoReturnable<Object> cir
    ) {
        if (!IronsCauldronGuard.isBlankScroll(scrollStack)) {
            return;
        }

        Object ink = IronsCauldronGuard.commonInk();
        if (ink != null) {
            cir.setReturnValue(ink);
        }
    }
}
