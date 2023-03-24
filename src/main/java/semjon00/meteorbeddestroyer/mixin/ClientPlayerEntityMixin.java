package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
    // Prevents the player from sprinting in Freelook mode
    // May be useful for avoiding an anticheat
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void setSprinting(CallbackInfo info) {
        var bd = Modules.get().get(BedDestroyer.class);
        if (bd.isBreakingTarget && bd.rotationMode.get() == BedDestroyer.RotationMode.Freelook) {
            // Wait until the addon disables the sprinting
            if (mc.player == null) return;
            if (!mc.player.isSprinting()) {
                info.cancel();
            }
        }
    }
}
