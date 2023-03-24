package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;

@Mixin(Mouse.class)
public class MouseMixin {
//    // Makes the player unable to rotate using their mouse if there is a target
//    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
//    public void updateMouseHead(CallbackInfo ci) {
//        var bd = Modules.get().get(BedDestroyer.class);
//        if (!bd.isActive()) return;
//        if (bd.rotationMode.get() == BedDestroyer.RotationMode.Locked && bd.isBreakingTarget) {
//            ci.cancel();
//        }
//    }

    // Makes the player unable to rotate using their mouse if there is a target
    // Also, updates free rotation if the mouse moved (just in case)
    @Inject(method = "updateMouse", at = @At("TAIL"))
    public void updateMouseTail(CallbackInfo ci) {
        Modules.get().get(BedDestroyer.class).updateNoTick();
    }
}
