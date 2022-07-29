package semjon00.meteorbeddestroyer.modules;

// TODO: fix situations where trough blocks is enabled
// TODO: fix situations where trough entities is disabled
// TODO: verify that the rotation does not lag behind the position (nor vice versa)
// TODO: integrate with Blink (that would seriously be OP) - Modules.get().get(Blink.class).isActive()
// TODO: An option to disallow running while breaking (confuses the server)
// TODO: force camera mode
// TODO: move breakBlockPrima inside the onTickPre to possibly gain an extra tick of time

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockIterator;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import oshi.util.tuples.Triplet;
import semjon00.meteorbeddestroyer.MeteorBedDestroyerAddon;

import java.util.ArrayList;
import java.util.List;

public class BedDestroyer extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

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

    private final Setting<SettingColor> targetColor = sgRender.add(new ColorSetting.Builder()
            .name("target-color")
            .description("Color to render the block that is being broken.")
            .defaultValue(new SettingColor(255,31,31, 127))
            .build()
    );

    private final Setting<SettingColor> passiveColor = sgRender.add(new ColorSetting.Builder()
            .name("bed-color")
            .description("Color to render all nearby bed blocks.")
            .defaultValue(new SettingColor(234, 234, 31, 31))
            .build()
    );

    public BedDestroyer() {
        super(MeteorBedDestroyerAddon.CATEGORY,
                "bed-destroyer",
                "Fine-tuned bed nuker.");
    }

    public static BlockPos currentTargetBlock = null;
    private final List<BlockPos> candidateTargetBlocks = new ArrayList<>();
    private final List<BlockPos> possibleTargets = new ArrayList<>();

    @Override
    public void onDeactivate() {
        currentTargetBlock = null;
        candidateTargetBlocks.clear();
        possibleTargets.clear();
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Seeing if our target is still reachable
        if (currentTargetBlock != null) {
            if (!allowedTarget(mc.world.getBlockState(currentTargetBlock))) {
                // Something (we, apparently) broke the target block! Yay!
                currentTargetBlock = null;
            }
        }
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

        // Searching a target, potential targets
        candidateTargetBlocks.clear();
        possibleTargets.clear();
        BlockIterator.register((int) Math.ceil(range.get()+1), (int) Math.ceil(range.get()+2), (blockPos, blockState) -> {
            if (!BlockUtils.canBreak(blockPos, blockState)) return;
            if (!allowedTarget(blockState)) return;
            possibleTargets.add(blockPos.toImmutable());

            // The target is sticky - we do not want to change it if we already have one
            if (currentTargetBlock == null) {
                Triplet<Double, Double, Direction> ga = goodAngle(blockPos);
                if (ga != null) {
                    // New target candidate found!
                    candidateTargetBlocks.add(blockPos.toImmutable());
                }
            }
        });
        BlockIterator.after(() -> {
            double bestSqDist = Double.MAX_VALUE;
            for (var c : candidateTargetBlocks) {
                var curSqDist = cameraPosition().squaredDistanceTo(Vec3d.ofCenter(c));
                if (curSqDist < bestSqDist) {
                    bestSqDist = curSqDist;
                    currentTargetBlock = c.toImmutable();
                }
            }
            candidateTargetBlocks.clear();
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (currentTargetBlock != null) {
            highlightBlock(event, currentTargetBlock, targetColor.get(), false);
        }
        for (var b : possibleTargets) {
            if (!b.equals(currentTargetBlock)) highlightBlock(event, b, passiveColor.get(), true);
        }
    }

    private void highlightBlock(Render3DEvent event, BlockPos bp, Color color, boolean allowPartial) {
        BlockState state = mc.world.getBlockState(bp);
        VoxelShape shape = state.getOutlineShape(mc.world, bp);
        if (shape.isEmpty()) return;
        Box box = shape.getBoundingBox();

        byte excludeDir = 0;
        if (allowPartial && state.getBlock() instanceof BedBlock bb) {
            var other = BedBlock.getOppositePartDirection(state);
            if (possibleTargets.contains(bp.offset(other))) {
                excludeDir = Dir.get(other);
            }
        }

        event.renderer.box(
                bp.getX() + box.minX, bp.getY() + box.minY, bp.getZ() + box.minZ,
                bp.getX() + box.maxX, bp.getY() + box.maxY, bp.getZ() + box.maxZ,
                color, color, ShapeMode.Sides, excludeDir);
    }

    private boolean allowedTarget(BlockState targetBlockState) {
        if (targetBlockState.getBlock() instanceof BedBlock) return true;
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
    // Range: from 0.0 to 16.0, last element is penalty
    public double[][] deltaPositionsToCheck = {
            {8, 8.9, 8, 0}, // Upper center
            {0, 8, 0, 1}, {0, 8, 8, 1}, {8, 8, 8, 1}, {8, 8, 0, 1}, // Upper side centers
            {0.1, 8.9, 0.1, 2}, {15.9, 8.9, 0.1, 2}, {15.9, 8.9, 15.9, 2}, {0.1, 8.9, 15.9, 2}, // Upper corners
    };

    // Calculates an angle so the block can be hit.
    // If many, take the one that allows for the shortest distance
    // Returns: yaw, pitch, face that will be hit
    public Triplet<Double, Double, Direction> goodAngle(BlockPos pos) {
        if (pos == null) return null;

        Triplet<Double, Double, Direction> ans = null;
        double bestScore = Double.MAX_VALUE;
        for (var dp : deltaPositionsToCheck) {
            Vec3d aimAt = new Vec3d(
                    pos.getX() + dp[0] / 16.0,
                    pos.getY() + dp[1] / 16.0,
                    pos.getZ() + dp[2] / 16.0
                    );

            var aimVector = aimAt.subtract(cameraPosition()).normalize();
            BlockHitResult hit = collisionForVector(aimVector);

            if (!hit.getBlockPos().equals(pos)) {
                // We tried an angle, but the target is obstructed. UNACCEPTABLE!
                continue;
            }

            var hitScore = cameraPosition().squaredDistanceTo(hit.getPos()) + dp[3] * 100;

            if (hitScore < bestScore) {
                bestScore = hitScore;

                // Let's calculate the direction for the camera
                double yaw = Math.atan2(- aimVector.x, aimVector.z) * 180.0 / Math.PI;
                double pitch = Math.asin(- aimVector.y) * 180.0 / Math.PI;

                ans = new Triplet<>(yaw, pitch, hit.getSide());
            }
        }
        return ans;
    }

    public BlockHitResult collisionForVector(Vec3d direction) {
        Vec3d start = cameraPosition();
        Vec3d delta = direction.normalize().multiply(getRange());
        Vec3d end = start.add(delta);
        var context = new RaycastContext(start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player);
        return mc.world.raycast(context);
    }

    public Vec3d cameraPosition() {
        // Some magic, no idea why tick delta needed here
        return mc.player.getCameraPosVec(1f / 20f);
    }

    public double getRange() {
        return vanilaRange.get() ? mc.interactionManager.getReachDistance() : range.get();
    }
}
