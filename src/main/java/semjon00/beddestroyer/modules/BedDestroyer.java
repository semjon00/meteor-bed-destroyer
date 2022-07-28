package semjon00.beddestroyer.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import oshi.util.tuples.Triplet;

import java.text.MessageFormat;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static semjon00.beddestroyer.MeteorBedDestroyerAddon.CATEGORY;
import static semjon00.beddestroyer.MeteorBedDestroyerAddon.LOGGER;

public class BedDestroyer extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("The break range.")
            .defaultValue(4)
            .min(1)
            .max(8)
            .build()
    );

    private final Setting<SettingColor> renderColor = sgGeneral.add(new ColorSetting.Builder()
            .name("render-color")
            .description("Color to render the block that is being broken.")
            .defaultValue(new SettingColor(255,31,32, 127))
            .build()
    );

    public BedDestroyer() {
        super(CATEGORY, "beddestroyer", "Fine-tuned bed nuker.");
    }

    private BlockPos targetBlock = null;
    private BlockPos targetBlockCandidate = null;
    private double targetCandidateSqDistance = Double.MAX_VALUE;
    private final Object lock = new Object(); // Just in case

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        targetBlockCandidate = null;
        targetCandidateSqDistance = Double.MAX_VALUE;

        double rangeSq = Math.pow(range.get(), 2);
        BlockIterator.register((int) Math.ceil(range.get()+1), (int) Math.ceil(range.get()+2), (blockPos, blockState) -> {
            if (!BlockUtils.canBreak(blockPos, blockState)) return;
            if (!(blockState.getBlock() instanceof BedBlock)) return;
            double distanceSq = distanceToPlayerSq(blockPos);
            if (distanceSq > rangeSq) return;

            synchronized (lock) {
                if (targetCandidateSqDistance > distanceSq) {
                    targetBlockCandidate = blockPos.toImmutable();
                    targetCandidateSqDistance = distanceSq;
                }
            }
        });

        BlockIterator.after(() -> {
            if (targetBlock != null) {
                BlockState targetBlockState = mc.world.getBlockState(targetBlock);
                if (!(targetBlockState.getBlock() instanceof BedBlock)) targetBlock = null;
            }
            if (targetBlock != null) {
                if (distanceToPlayerSq(targetBlock) > rangeSq) targetBlock = null;
            }
            if (targetBlock == null) {
                targetBlock = targetBlockCandidate;
            }
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (targetBlock != null) {
            event.renderer.blockSides(
                    targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(),
                    renderColor.get(), 0);
        }
    }

    private double distanceToPlayerSq(BlockPos blockPos) {
        double pX = mc.player.getX();
        double pyFeet = mc.player.getY();
        double pYEyes = pyFeet + mc.player.getEyeHeight(mc.player.getPose());
        double pZ = mc.player.getZ();

        // TODO: More points to check if we can break a block
        // Block, this.getCollisionShape(world, pos, ShapeContext.of(entity))
        // Find the closest point?
        // Multiple points?
        double sqDistance = Utils.squaredDistance(
                pX, pYEyes, pZ,
                blockPos.getX() + 0.5, blockPos.getY() + 0.1, blockPos.getZ() + 0.5
        );

        return sqDistance;
    }
}
