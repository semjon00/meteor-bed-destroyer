package semjon00.meteorbeddestroyer.modules;

// TODO: add toggle for situations where trough blocks is desireable
// TODO: add toggle for situations where trough entities is undesireable
// TODO: verify that the rotation does not lag behind the position (nor vice versa)
// TODO: move breakBlockPrima inside the onTickPre to possibly gain an extra tick of time
// TODO: Accurate mode: update the direction at every frame (especially useful for FORCED rotation mode)
// TODO: the breaking angle should be sticky (FORCED rotation mode improvement and performance optimization)
// TODO: fix vanilla reach being slightly off
// TODO: fix blink integration sometimes produces one frame with red coloring

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Blink;
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
import oshi.util.tuples.Pair;
import oshi.util.tuples.Triplet;
import semjon00.meteorbeddestroyer.MeteorBedDestroyerAddon;

import java.util.ArrayList;
import java.util.List;

public class BedDestroyer extends Module {
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> vanilaReach = sgGeneral.add(new BoolSetting.Builder()
            .name("vanila-reach")
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
            .visible(() -> !vanilaReach.get())
            .build()
    );

    public enum RotationMode {
        NONE,
        FREELOOK,
        LOCKED
    }

    public final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
            .name("rotation-mode")
            .description("How should the module fake rotation?")
            .defaultValue(RotationMode.NONE)
            .build()
    );

    public final Setting<Boolean> restrictSprinting = sgGeneral.add(new BoolSetting.Builder()
            .name("restrict-sprinting")
            .description("Sprinting may not be possible in all directions. Restrict it?")
            .defaultValue(true)
            .visible(() -> rotationMode.get() == RotationMode.FREELOOK)
            .build()
    );

    private final Setting<Boolean> restoreRotation = sgGeneral.add(new BoolSetting.Builder()
            .name("restore-rotation")
            .description("Should the previous rotation be restored after the block is broken?")
            .defaultValue(false)
            .visible(() -> rotationMode.get() == RotationMode.LOCKED)
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

    private final Setting<Boolean> blinkIntegration = sgGeneral.add(new BoolSetting.Builder()
            .name("blink-integration")
            .description("Should prevent the breaking while in Blink state?")
            .defaultValue(true)
            .build()
    );

    private final Setting<SettingColor> activeTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("target-color")
            .description("Color to render the block that is being broken.")
            .defaultValue(new SettingColor(255,31,31, 127))
            .build()
    );

    private final Setting<SettingColor> passiveTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("target-color")
            .description("Color to render the block that we aim at, but do not yet break.")
            .defaultValue(new SettingColor(245,133,31, 79))
            .build()
    );

    private final Setting<SettingColor> candidateColor = sgRender.add(new ColorSetting.Builder()
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

    public boolean isBreakingThisTick = false;
    public BlockPos currentTargetBlock = null;
    private final List<BlockPos> candidateTargetBlocks = new ArrayList<>();
    private final List<BlockPos> possibleTargets = new ArrayList<>();
    private Pair<Float, Float> savedYawPitch = null;

    @Override
    public void onDeactivate() {
        isBreakingThisTick = false;
        currentTargetBlock = null;
        candidateTargetBlocks.clear();
        possibleTargets.clear();
        savedYawPitch = null;
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        // Seeing if our target is still reachable
        if (currentTargetBlock != null) {
            if (!allowedTarget(mc.world.getBlockState(currentTargetBlock))) {
                // Something (we, apparently) broke the target block! Yay!
                currentTargetBlock = null;
                isBreakingThisTick = false;
            }
        }
        if (currentTargetBlock != null) {
            Triplet<Float, Float, Direction> ga = goodAngle(currentTargetBlock);
            if (ga == null) {
                // Our target block is no longer reachable! Sad.
                currentTargetBlock = null;
                mc.interactionManager.cancelBlockBreaking();
            } else {
                // The target block is visible!
                isBreakingThisTick = true;
                if (blinkIntegration.get() && Modules.get().get(Blink.class).isActive()) isBreakingThisTick = false;
                if (isBreakingThisTick) {
                    if (rotationMode.get() == RotationMode.LOCKED) {
                        if (savedYawPitch == null) {
                            savedYawPitch = new Pair<>(mc.player.getYaw(), mc.player.getPitch());
                        }
                        // clientSide parameter sets head rotation, but not the rotation of the camera
                        Rotations.rotate(ga.getA(), ga.getB(), 10, true, () -> {});
                        mc.player.setYaw(ga.getA());
                        mc.player.setPitch(ga.getB());
                    }
                    if (rotationMode.get() == RotationMode.FREELOOK) {
                        if (restrictSprinting.get()) {
                            if (mc.player.isSprinting()) {
                                mc.player.setSprinting(false);
                            }
                        }
                        Rotations.rotate(ga.getA(), ga.getB(), 10, true, () -> {});
                    }
                    breakBlockPrima(currentTargetBlock, ga.getC());
                }
            }
        }
        if (currentTargetBlock == null && savedYawPitch != null) {
            if (restoreRotation.get()) {
                mc.player.setYaw(savedYawPitch.getA());
                mc.player.setPitch(savedYawPitch.getB());
            }
            savedYawPitch = null;
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
                Triplet<Float, Float, Direction> ga = goodAngle(blockPos);
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
            var color = isBreakingThisTick ? activeTargetColor.get() : passiveTargetColor.get();
            highlightBlock(event, currentTargetBlock, color,false);
        }
        for (var b : possibleTargets) {
            if (!b.equals(currentTargetBlock)) highlightBlock(event, b, candidateColor.get(), true);
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

    // Positions to check to know if we can reach the bed and to calculate the correct angle for breaking it
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
    public Triplet<Float, Float, Direction> goodAngle(BlockPos pos) {
        if (pos == null) return null;

        Triplet<Float, Float, Direction> ans = null;
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
                float yaw = (float) (Math.atan2(- aimVector.x, aimVector.z) * 180.0 / Math.PI);
                float pitch = (float) (Math.asin(- aimVector.y) * 180.0 / Math.PI);

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
        // Some magic, no idea why tick delta is needed here
        return mc.player.getCameraPosVec(1f / 20f);
    }

    public double getRange() {
        return vanilaReach.get() ? mc.interactionManager.getReachDistance() : range.get();
    }
}
