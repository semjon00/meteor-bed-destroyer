package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(Entity.class)
public abstract class EntityMixin {
    // This makes it inevitable that the player will look at the target after they walk
    @Inject(method = "move", at = @At("TAIL"))
    private void onMove(MovementType type, Vec3d movement, CallbackInfo info) {
        var bd = Modules.get().get(BedDestroyer.class);
        if (!bd.isActive()) return;

        if ((Object) this == mc.player) {
            bd.updateNoTick();
        }
    }
}
