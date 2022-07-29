package semjon00.beddestroyer.modules;

// TODO: better positions (skill = 100%)
// TODO: fix situations where trough blocks is enabled
// TODO: fix situations where trough entities is disabled
// TODO: always choose the closest angle point, even it is not for the closest target
// TODO: integrate with Blink (that would seriously be OP) - Modules.get().get(Blink.class).isActive()
// TODO: An option to disallow running while breaking (confuses the server)
// TODO: move breakBlockPrima inside the onTickPre to possibly gain an extra tick of time

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import oshi.util.tuples.Triplet;
import semjon00.beddestroyer.MeteorBedDestroyerAddon;
import static net.minecraft.block.Blocks.YELLOW_WOOL;

public class BedDestroyer extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");

    private final Setting<Boolean> vanilaRange = sgGeneral.add(new BoolSetting.Builder()
            .name("vanila-range")
            .description("Whether to use vanilla reach distance.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
            .name("range")
            .description("The break range.")
            .defaultValue(4.5)
            .min(1)
            .max(8)
            .sliderMin(1)
            .sliderMax(8)
            .visible(() -> !vanilaRange.get())
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
//
//    private final Setting<Boolean> troughBlocks = sgGeneral.add(new BoolSetting.Builder()
//            .name("trough-blocks")
//            .description("Should we break trough blocks?")
//            .defaultValue(false)
//            .build()
//    );
//
//    private final Setting<Boolean> troughEntities = sgGeneral.add(new BoolSetting.Builder()
//            .name("trough-entities")
//            .description("Should we break trough entities?")
//            .defaultValue(true)
//            .build()
//    );

    private final Setting<Boolean> swingClientside = sgGeneral.add(new BoolSetting.Builder()
            .name("swing-clientside")
            .description("Should the hand move client-side?")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> breakYellowWool = sgGeneral.add(new BoolSetting.Builder()
            .name("break-yellow-wool")
            .description("Should the module break yellow wool (cuz why not)?")
            .defaultValue(false)
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

    public static BlockPos currentTargetBlock = null;

    @Override
    public void onDeactivate() {
        currentTargetBlock = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Seeing if our block is still reachable
        if (currentTargetBlock != null) {
            Triplet<Double, Double, Direction> ga = goodAngle(currentTargetBlock);
            if (ga == null) {
                // Our target block is no longer reachable! Sad.
                currentTargetBlock = null;
            } else {
                // The target block is still visible! Advance the breaking!
                if (rotationMode.get() == RotationMode.FREELOOK) {
                    Rotations.rotate(ga.getA(), ga.getB(), 10, true, () -> {});
                }
                breakBlockPrima(currentTargetBlock, ga.getC());
            }
        }

        // Searching a target
        if (currentTargetBlock == null) {
            BlockIterator.register((int) Math.ceil(range.get()+1), (int) Math.ceil(range.get()+2), (blockPos, blockState) -> {
                if (!BlockUtils.canBreak(blockPos, blockState)) return;
                if (!allowedTarget(blockState)) return;

                Triplet<Double, Double, Direction> ga = goodAngle(blockPos);
                if (ga != null) {
                    // New target found!
                    currentTargetBlock = blockPos.toImmutable();
                }
            });
        }
        BlockIterator.after(() -> {});
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (currentTargetBlock != null) {
            event.renderer.blockSides(
                    currentTargetBlock.getX(), currentTargetBlock.getY(), currentTargetBlock.getZ(),
                    renderColor.get(), 0);
        }
    }

    private boolean allowedTarget(BlockState targetBlockState) {
        if (targetBlockState.getBlock() instanceof BedBlock) return true;
        if (breakYellowWool.get()) {
            if (targetBlockState.getBlock() == YELLOW_WOOL) return true;
        }
        return false;
    }

    private void breakBlockPrima(BlockPos blockPos, Direction direction) {
        // Adopted from meteordevelopment.meteorclient.utils.world.BlockUtils
        // With this version can set a direction, too.

        BlockPos pos = blockPos instanceof BlockPos.Mutable ? new BlockPos(blockPos) : blockPos;

        if (mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.updateBlockBreakingProgress(pos, direction);
        } else {
            mc.interactionManager.attackBlock(pos, direction);
        }

        if (swingClientside.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    // Positions to check to know if we can reach the bed and the correct angle for it
    // These positions should cover almost all cases where the bed is exposed
    // See net.minecraft.block.BedBlock.getOutlineShape for the bed shape
    // Range: from 0.0 to 16.0
    public double[][] deltaPositionsToCheck = {
            {1, 8, 1}, {15, 8, 1}, {15, 8, 15}, {1, 8, 15}, // Around and slightly inside
            {8, 9, 8}, // Center from above
            {8, 12, 8} // Above the center
    };

    // Calculates an angle so the block can be hit.
    // If many, take the one that is the closest
    // Returns: yaw, pitch, face that will be hit
    public Triplet<Double, Double, Direction> goodAngle(BlockPos pos) {
        for (var dp : deltaPositionsToCheck) {
            Vec3d aimAt = new Vec3d(
                    pos.getX() + dp[0] / 16.0,
                    pos.getY() + dp[1] / 16.0,
                    pos.getZ() + dp[2] / 16.0
                    );

            var aimVector = aimAt.subtract(mc.player.getCameraPosVec(1f / 20f)).normalize();
            BlockHitResult hit = collisionForVector(aimVector);

            if (!hit.getBlockPos().equals(pos)) {
                // We tried the angle, but the target is obstructed. UNACCEPTABLE!
                continue;
            }

            // Let's calculate the direction for the camera
            double yaw = Math.atan2(- aimVector.x, aimVector.z) * 180.0 / Math.PI;
            double pitch = Math.asin(- aimVector.y) * 180.0 / Math.PI;

            return new Triplet(yaw, pitch, hit.getSide());
        }
        // Can not hit. What a pity.
        return null;
    }

    public BlockHitResult collisionForVector(Vec3d direction) {
        Vec3d start = mc.player.getCameraPosVec(1f / 20f);
        Vec3d delta = direction.normalize().multiply(getRange());
        Vec3d end = start.add(delta);
        return mc.world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
    }

    public double getRange() {
        return vanilaRange.get() ? mc.interactionManager.getReachDistance() : range.get();
    }
}
