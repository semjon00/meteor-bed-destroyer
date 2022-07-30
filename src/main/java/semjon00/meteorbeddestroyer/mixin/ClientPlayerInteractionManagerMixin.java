package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
//	// Debug
//	@Inject(at = @At("HEAD"), method = "sendPlayerAction")
//	private void sendPlayerAction(PlayerActionC2SPacket.Action action, BlockPos pos, Direction direction, CallbackInfo ci) {
//		SimpleDateFormat sdfDate = new SimpleDateFormat("HH:mm:ss.SSS");
//		Date now = new Date();
//		String strDate = sdfDate.format(now);
//
//		MeteorBedDestroyerAddon.LOGGER.info(MessageFormat.format(
//				"[sendPlayerAction] {0} | {1} | {2} | {3}",
//				strDate, pos.toString(), action.name(), direction.getName()
//				));
//	}

	// Prevents Minecraft from cancelling block breaking if we actually have a target that we punch
	// For module: BedDestroyer
	@Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void onCancelBlockBreaking(CallbackInfo info) {
		if (Modules.get().get(BedDestroyer.class).isActive()) {
			if (Modules.get().get(BedDestroyer.class).currentTarget != null) {
				info.cancel();
			}
		}
	}
}
