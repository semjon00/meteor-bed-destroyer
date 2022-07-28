package semjon00.beddestroyer.modules;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import semjon00.beddestroyer.MeteorBedDestroyerAddon;

import static net.minecraft.block.Blocks.YELLOW_WOOL;

// Must read: https://wiki.vg/Protocol#Player_Action

public class BedDestroyer extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("The break range.")
            .defaultValue(4.5)
            .min(1)
            .max(8)
            .build()
    );

    public enum RotationMode {
        NONE,
        FREELOOK
    }

    private final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
            .name("rotation-mode")
            .description("How should the module fake rotation?")
            .defaultValue(RotationMode.NONE)
            .build()
    );

    private final Setting<Boolean> breakYellowWool = sgGeneral.add(new BoolSetting.Builder()
            .name("break-yellow-wool")
            .description("Should the module break yellow wool (cuz why not)?")
            .defaultValue(false)
            .build());

    private final Setting<Direction> direction = sgGeneral.add(new EnumSetting.Builder<Direction>()
            .name("direction")
            .description("DEBUG")
            .defaultValue(Direction.UP)
            .build()
    );

    private final Setting<SettingColor> renderColor = sgGeneral.add(new ColorSetting.Builder()
            .name("render-color")
            .description("Color to render the block that is being broken.")
            .defaultValue(new SettingColor(255,31,32, 127))
            .build()
    );

    public BedDestroyer() {
        super(MeteorBedDestroyerAddon.CATEGORY,
                "bed-destroyer",
                "Fine-tuned bed nuker.");
    }

    private BlockPos targetBlock = null;
    private BlockPos targetBlockCandidate = null;
    private double targetCandidateSqDistance = Double.MAX_VALUE;
    private final Object lock = new Object(); // Just in case

    @Override
    public void onDeactivate() {
        targetBlock = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Breaking activities
        if (targetBlock != null) {
            if (rotationMode.get() != RotationMode.NONE) {
                /* TODO:
                    Find the best spot for breaking.
                    Consider that it may be beneficial to aim for the closest point.
                    Also, if a block is partially obstructed, ignore points that are obstructed
                    Keep in mind the model of the block
                    Make a setting to avoid entities in the way
                 */
                double yaw = Rotations.getYaw(targetBlock);
                double pitch = Rotations.getPitch(targetBlock);
                Rotations.rotate(yaw, pitch, 10, true, () -> {});
            }
            breakBlockPrima(targetBlock);
        }

        // Picking block activities
        targetBlockCandidate = null;
        targetCandidateSqDistance = Double.MAX_VALUE;

        double rangeSq = Math.pow(range.get(), 2);
        BlockIterator.register((int) Math.ceil(range.get()+1), (int) Math.ceil(range.get()+2), (blockPos, blockState) -> {
            if (!BlockUtils.canBreak(blockPos, blockState)) return;
            if (!allowedTarget(blockState)) return;
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
                if (!allowedTarget(targetBlockState)) targetBlock = null;
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

        double sqDistance = Utils.squaredDistance(
                pX, pYEyes, pZ,
                blockPos.getX() + 0.5, blockPos.getY() + 0.2, blockPos.getZ() + 0.5
        );

        return sqDistance;
    }

    private boolean allowedTarget(BlockState targetBlockState) {
        if (targetBlockState.getBlock() instanceof BedBlock) return true;
        if (breakYellowWool.get()) {
            if (targetBlockState.getBlock() == YELLOW_WOOL) return true;
        }
        return false;
    }

    private void breakBlockPrima(BlockPos blockPos) {
        BlockUtils.breakBlock(blockPos, false);

        // TODO: extract
        // There is meteordevelopment.meteorclient.utils.world.breakBlock method,
        // But it does not use correct block sides
        // BlockUtils.breakBlock(blockPos, false);

//        BlockPos pos = blockPos instanceof BlockPos.Mutable ? new BlockPos(blockPos) : blockPos;
//        if (mc.interactionManager.isBreakingBlock()) {
//            mc.interactionManager.updateBlockBreakingProgress(pos, Direction.UP);
//        } else {
//            mc.interactionManager.attackBlock(pos, Direction.UP);
//        }
//        mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
//        BlockUtils.breaking = true;
    }

    private Direction getRightDirection(Vec3d pos) {
        // TODO
        return direction.get();
    }
}
