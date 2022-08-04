package semjon00.meteorbeddestroyer.mixin;

import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.utils.misc.text.ColoredText;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

import static meteordevelopment.meteorclient.utils.misc.text.TextUtils.getMostPopularColor;
import static meteordevelopment.meteorclient.utils.misc.text.TextUtils.toColoredTextList;

@Mixin(PlayerUtils.class)
public class PlayerUtilsMixin {
    // Let's try to predict the player's team more aggressively. Helps in some cases.
    @Redirect(method = "getPlayerColor", at = @At(value = "INVOKE", target = "Lmeteordevelopment/meteorclient/utils/misc/text/TextUtils;getMostPopularColor(Lnet/minecraft/text/Text;)Lmeteordevelopment/meteorclient/utils/render/color/Color;"))
    private static Color getPlayearColor(Text text) {
        var fallback = getMostPopularColor(text);

        if (Config.get().useTeamColor.get()) {
            List<ColoredText> cText = toColoredTextList(text);
            if (cText.size() < 2) return fallback;

            if (cText.get(0).getText().contains("⚑") || isMonochrome(fallback)) {
                return cText.get(0).getColor();
            }
        }

        return fallback;
    }

    private static boolean isMonochrome(Color color) {
        return color.r == color.g && color.g == color.b; // Monochrome
    }
}
