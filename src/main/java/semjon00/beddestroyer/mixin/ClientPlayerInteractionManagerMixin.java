package semjon00.beddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import semjon00.beddestroyer.MeteorBedDestroyerAddon;
import semjon00.beddestroyer.modules.BedDestroyer;

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

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
	@Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
	private void onCancelBlockBreaking(CallbackInfo info) {
		if (Modules.get().get(BedDestroyer.class).isActive()) {
			if (BedDestroyer.currentTargetBlock != null) {
				info.cancel();
			}
		}
	}
}
