package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;


@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
	// Prevents Minecraft from cancelling block breaking if we actually have a target that we punch
	@Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void onCancelBlockBreaking(CallbackInfo info) {
		if (Modules.get().get(BedDestroyer.class).isBreakingTarget) info.cancel();
	}
}
