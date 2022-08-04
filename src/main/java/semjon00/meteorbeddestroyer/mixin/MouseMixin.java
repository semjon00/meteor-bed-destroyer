package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Mouse;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;

import static semjon00.meteorbeddestroyer.MeteorBedDestroyerAddon.*;
import static semjon00.meteorbeddestroyer.modules.BedDestroyer.*;

@Mixin(Mouse.class)
public class MouseMixin {
    // Accurate rotation for Locked rotation mode
    // Could be done at some other place, but we do it here for a good measure
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    public void updateMouse(CallbackInfo ci) {
        var bd = Modules.get().get(BedDestroyer.class);
        if (!bd.isActive()) return;

        if (bd.rotationMode.get() == BedDestroyer.RotationMode.Locked) {
            var result = bd.checkAimPoint(bd.currentTarget, bd.currentTargetDeltaAim);
            if (result == null) return;
            debugLog("In MouseMixin");

            var deltaAim = result.getA().getA();
            var absAim = deltaAim.add(Vec3d.of(bd.currentTarget));
            var aimVector = absAim.subtract(bd.cameraPosition()).normalize();
            bd.rotatingLogic(getYaw(aimVector), getPitch(aimVector));

            ci.cancel();
        }
    }
}
