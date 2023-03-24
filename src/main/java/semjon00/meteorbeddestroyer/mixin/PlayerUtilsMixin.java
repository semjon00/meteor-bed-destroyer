package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.text.ColoredText;
import meteordevelopment.meteorclient.utils.misc.text.TextUtils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import semjon00.meteorbeddestroyer.modules.BedDestroyer;

import java.util.List;

import static meteordevelopment.meteorclient.utils.misc.text.TextUtils.toColoredTextList;

@Mixin(PlayerUtils.class)
public class PlayerUtilsMixin {
    // Let's try to predict the player's team more aggressively.
    @Inject(method = "getPlayerColor", at = @At("HEAD"), cancellable = true)
    private static void getPlayearColor(PlayerEntity entity, Color defaultColor, CallbackInfoReturnable<Color> cir) {
        var bd = Modules.get().get(BedDestroyer.class);
        if (!bd.alternativeTeamsColoring.get()) return;
        // if (!Config.get().useTeamColor.get()) return;

        List<ColoredText> cTexts = toColoredTextList(entity.getDisplayName());
        Color ret = Color.BLACK;
        int priority = 0;
        for (var cText : cTexts) {
            var color = cText.getColor();
            if (cText.getText().contains("⚑")) {
                if (!isMonochrome(color) && priority < 3) {
                    priority = 3;
                    ret = color;
                } else if (priority < 2) {
                    priority = 2;
                    ret = color;
                }
            } else {
                if (!isMonochrome(color) && priority < 2) {
                    if (priority == 1) {
                        // We already had a colored text that does not contain ⚑
                        return;
                    }
                    priority = 1;
                    ret = color;
                }
            }
        }
        if (priority > 1) cir.setReturnValue(ret);
        if (priority == 1) {
            cir.setReturnValue(mix(ret, TextUtils.getMostPopularColor(entity.getDisplayName()), 0.5f));
        }
    }

    private static boolean isMonochrome(Color color) {
        return color.r == color.g && color.g == color.b; // Monochrome
    }

    private static Color mix(Color f, Color s, float percentageA) {
        var percentageB = 1 - percentageA;
        int r = Math.round(f.r * percentageA + s.r * percentageB);
        int g = Math.round(f.g * percentageA + s.g * percentageB);
        int b = Math.round(f.b * percentageA + s.b * percentageB);
        int a = Math.round(f.a * percentageA + s.a * percentageB);
        return new Color(r, g, b, a);
    }
}
