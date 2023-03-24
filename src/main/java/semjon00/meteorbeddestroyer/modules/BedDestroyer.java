package semjon00.meteorbeddestroyer.modules;

// TODO: add toggle for situations where trough blocks is desirable
// TODO: add toggle for situations where trough entities is undesirable
// TODO: make an option to restrict punching while breaking the target
// TODO: fix radical arm movement when going trough -180 180 yaw border
// TODO: fix sneaking rotation de-sync
// TODO: home bed fix
// TODO: anticheat triggers fix

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
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.RaycastContext;
import oshi.util.tuples.Pair;
import semjon00.meteorbeddestroyer.MeteorBedDestroyerAddon;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BedDestroyer extends Module {
    public final SettingGroup sgGeneral = settings.createGroup("General");
    public final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<RotationMode> rotationMode = sgGeneral.add(new EnumSetting.Builder<RotationMode>()
            .name("rotation-mode")
            .description("Mode of applying rotation.")
            .defaultValue(RotationMode.Freelook)
            .build()
    );

    public final Setting<Boolean> disableBreaking = sgGeneral.add(new BoolSetting.Builder()
            .name("disable-breaking")
            .description("Disables the destroying of a bed.")
            .defaultValue(false)
            .build()
    );

    public final Setting<Boolean> vanillaReach = sgGeneral.add(new BoolSetting.Builder()
            .name("vanilla-reach")
            .description("Uses reach distance that matches the vanilla reach distance.")
            .defaultValue(false)
            .build()
    );

    public final Setting<Double> reach = sgGeneral.add(new DoubleSetting.Builder()
            .name("reach")
            .description("The reach of breaking.")
            .defaultValue(4.0)
            .min(1)
            .max(8)
            .sliderMin(1)
            .sliderMax(8)
            .visible(() -> !vanillaReach.get())
            .build()
    );

    public enum RotationMode {
        None,
        Freelook,
        Locked
    }

    public final Setting<Boolean> restrictSprinting = sgGeneral.add(new BoolSetting.Builder()
            .name("restrict-sprinting")
            .description("Restricts sprinting.")
            .defaultValue(true)
            .visible(() -> rotationMode.get() == RotationMode.Freelook)
            .build()
    );

    public final Setting<Boolean> restoreRotation = sgGeneral.add(new BoolSetting.Builder()
            .name("restore-rotation")
            .description("Restores rotation after target disappears.")
            .defaultValue(false)
            .visible(() -> rotationMode.get() == RotationMode.Locked)
            .build()
    );

    public final Setting<Boolean> targetPointSticky = sgGeneral.add(new BoolSetting.Builder()
            .name("target-point-sticky")
            .description("Aims for the same point on a target as long as possible.")
            .defaultValue(false)
            .visible(() -> rotationMode.get() != RotationMode.None || !disableBreaking.get())
            .build()
    );

    public final Setting<Boolean> swingClientside = sgGeneral.add(new BoolSetting.Builder()
            .name("swing-clientside")
            .description("Visibly swings hand.")
            .defaultValue(false)
            .visible(() -> !disableBreaking.get())
            .build()
    );

    public final Setting<Boolean> blinkIntegration = sgGeneral.add(new BoolSetting.Builder()
            .name("blink-integration")
            .description("Prevents breaking as long as Blink is activated.")
            .defaultValue(true)
            .visible(() -> !disableBreaking.get())
            .build()
    );

    public final Setting<SettingColor> activeTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("active-target-color")
            .description("Color used to render the block that is being broken.")
            .defaultValue(new SettingColor(255,31,31, 127))
            .visible(() -> !disableBreaking.get())
            .build()
    );

    public final Setting<SettingColor> suspendedTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("suspended-target-color")
            .description("Color used to render the target that is not being broken.")
            .defaultValue(new SettingColor(245,133,31, 79))
            .visible(() -> blinkIntegration.get() || disableBreaking.get())
            .build()
    );

    public final Setting<SettingColor> possibleTargetColor = sgRender.add(new ColorSetting.Builder()
            .name("possible-target-color")
            .description("Color to render all nearby bed blocks.")
            .defaultValue(new SettingColor(234, 234, 31, 31))
            .build()
    );

    public final Setting<Boolean> alternativeTeamsColoring = sgRender.add(new BoolSetting.Builder()
            .name("alternative-teams-coloring")
            .description("Use alternative way of assigning colors to players - may be useful for ESP.")
            .defaultValue(false)
            .build()
    );

    public BedDestroyer() {
        super(MeteorBedDestroyerAddon.CATEGORY,
                "bed-destroyer",
                "Fine-tuned bed nuker.");
    }

    public boolean isBreakingTarget = false;
    public BlockPos currentTarget = null;
    public Vec3d currentTargetDeltaAim = null;
    public boolean interactionManagerNotObstruct = false;
    public final List<BlockPos> possibleTargets = new ArrayList<>();
    public Pair<Float, Float> savedYawPitch = null;

    @Override
    public void onDeactivate() {
        isBreakingTarget = false;
        currentTarget = null;
        currentTargetDeltaAim = null;
        possibleTargets.clear();
        savedYawPitch = null;
    }

    @EventHandler
    public void updateWithTick(TickEvent.Pre event) {
        updateDeltaAim();
        updateTarget();
    }

    public void updateNoTick() {
        updateDeltaAim();
        advanceTarget(false);
    }

    public void updateDeltaAim() {
        if (mc.interactionManager == null || mc.player == null) return;

        if (currentTarget != null && (checkAimPoint(currentTarget, currentTargetDeltaAim) == null || !targetPointSticky.get())) {
            var deltaAim = goodAimPoint(currentTarget);
            if (deltaAim == null) {
                // Releasing target
                isBreakingTarget = false;
                currentTarget = null;
                currentTargetDeltaAim = null;
                mc.interactionManager.cancelBlockBreaking();
            } else {
                // New deltaAim point
                currentTargetDeltaAim = deltaAim.getA();
            }
        }
    }

    public void updateTarget() {
        // Searching possible targets, candidate targets
        // Please note that the BlockIterator is deferred
        final List<BlockPos> candidateTargets = new ArrayList<>();
        possibleTargets.clear();
        BlockIterator.register((int) Math.ceil(reach.get() + 2), (int) Math.ceil(reach.get() + 2), (blockPos, blockState) -> {
            if (!BlockUtils.canBreak(blockPos, blockState)) return;
            if (!isAllowedTarget(blockState)) return;
            possibleTargets.add(blockPos.toImmutable());

            // The target is sticky - we do not want to change it if we already have one
            // We will only have candidates if we do not have the old target anymore
            if (currentTarget == null) {
                if (goodAimPoint(blockPos) != null) {
                    candidateTargets.add(blockPos.toImmutable());
                }
            }
        });

        BlockIterator.after(() -> {
            // Picking the best candidate target as a new target (no candidates = no target change)
            double bestSqDist = Double.MAX_VALUE;
            for (var c : candidateTargets) {
                var curSqDist = cameraPosition().squaredDistanceTo(Vec3d.ofCenter(c));
                if (curSqDist < bestSqDist) {
                    bestSqDist = curSqDist;
                    currentTarget = c.toImmutable();
                }
            }
            candidateTargets.clear();

            // Putting deltaAim if necessary
            if (currentTarget != null && currentTargetDeltaAim == null) {
                var deltaAim = goodAimPoint(currentTarget);
                if (deltaAim != null) {
                    currentTargetDeltaAim = deltaAim.getA();
                }
                if (currentTargetDeltaAim == null) currentTarget = null; // Should never happen
            }

            advanceTarget(true);
        });
    }

    public void advanceTarget(boolean isWithTick) {
        if (mc.player == null) return;

        if (currentTarget != null && currentTargetDeltaAim != null) {
            var absAim = currentTargetDeltaAim.add(Vec3d.of(currentTarget));
            var aimVector = absAim.subtract(cameraPosition()).normalize();
            rotatingLogic(getYaw(aimVector), getPitch(aimVector));

            isBreakingTarget = true;
            if (blinkIntegration.get() && Modules.get().get(Blink.class).isActive()) isBreakingTarget = false;
            if (disableBreaking.get()) isBreakingTarget = false;
            if (isBreakingTarget && isWithTick) {
                var deltaAim = checkAimPoint(currentTarget, currentTargetDeltaAim);
                if (deltaAim != null) breakingLogic(currentTarget, deltaAim.getA().getB());
            }
        } else {
            isBreakingTarget = false;
            // Restoring yaw and pitch if necessary
            if (savedYawPitch != null) {
                // By entering Blink state you would assume that the position will be forgotten
                if (restoreRotation.get() && !Modules.get().get(Blink.class).isActive()) {
                    mc.player.setYaw(savedYawPitch.getA());
                    mc.player.setPitch(savedYawPitch.getB());
                }
                savedYawPitch = null;
            }
        }
    }

    public void rotatingLogic(float yaw, float pitch) {
        if (mc.player == null) return;

        if (rotationMode.get() == RotationMode.Locked) {
            if (savedYawPitch == null) {
                savedYawPitch = new Pair<>(mc.player.getYaw(), mc.player.getPitch());
            }
            // clientSide parameter sets head rotation, but not the rotation of the camera
            Rotations.rotate(yaw, pitch, 10, true, () -> {});
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
        }
        if (rotationMode.get() == RotationMode.Freelook) {
            if (restrictSprinting.get()) {
                if (mc.player.isSprinting()) {
                    mc.player.setSprinting(false);
                }
            }
            Rotations.rotate(yaw, pitch, 10, true, () -> {});
        }
    }

    @EventHandler
    public void onRender(Render3DEvent event) {
        if (currentTarget != null) {
            var color = isBreakingTarget ? activeTargetColor.get() : suspendedTargetColor.get();
            highlightBlock(event, currentTarget, color,false);
        }
        for (var b : possibleTargets) {
            if (!b.equals(currentTarget)) highlightBlock(event, b, possibleTargetColor.get(), true);
        }
    }

    public void highlightBlock(Render3DEvent event, BlockPos bp, Color color, boolean allowPartial) {
        if (mc.world == null) return;

        BlockState state = mc.world.getBlockState(bp);
        VoxelShape shape = state.getOutlineShape(mc.world, bp);
        if (shape.isEmpty()) return;
        Box box = shape.getBoundingBox();

        byte excludeDir = 0;
        if (allowPartial && state.getBlock() instanceof BedBlock) {
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

    public boolean isAllowedTarget(BlockPos pos) {
        if (mc.world == null) return false;
        return isAllowedTarget(mc.world.getBlockState(pos));
    }

    public boolean isAllowedTarget(BlockState targetBlockState) {
        return targetBlockState.getBlock() instanceof BedBlock;
    }

    public void breakingLogic(BlockPos blockPos, Direction direction) {
        // Adopted from meteordevelopment.meteorclient.utils.world.BlockUtils
        // This version can set a correct direction, too.

        if (mc.interactionManager == null || mc.player == null) return;

        BlockPos pos = blockPos instanceof BlockPos.Mutable ? new BlockPos(blockPos) : blockPos;

        interactionManagerNotObstruct = true;
        if (mc.interactionManager.isBreakingBlock()) {
            mc.interactionManager.updateBlockBreakingProgress(pos, direction);
        } else {
            mc.interactionManager.attackBlock(pos, direction);
        }
        interactionManagerNotObstruct = false;

        if (swingClientside.get()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        } else {
            mc.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
    }

    // Positions to check to know if we can reach the bed and to calculate the correct angle for breaking it
    // These positions should cover almost all cases where the bed is exposed
    // See net.minecraft.block.BedBlock.getOutlineShape for the bed shape
    public final List<Pair<Vec3d, Integer>> deltaAimPoints = initDeltaAimPoints();
    public List<Pair<Vec3d, Integer>> initDeltaAimPoints() {
        // Range: from 0.0 to 16.0, last element of each quadruple is the priority
        double[][] temp = {
            {8, 9, 8, 10}, // Upper center
            {8, 8, 0, 3}, {0, 8, 8, 3}, {8, 8, 16, 3}, {16, 8, 8, 3}, // Upper side centers
            {0.1, 8.9, 0.1, 1}, {15.9, 8.9, 0.1, 1}, {15.9, 8.9, 15.9, 1}, {0.1, 8.9, 15.9, 1}, // Upper corners
            {0.1, 0.1, 0.1, 0}, {15.9, 0.1, 0.1, 0}, {15.9, 0.1, 15.9, 0}, {0.1, 0.1, 15.9, 0}, // Legs
        };
        List<Pair<Vec3d, Integer>> ans = new ArrayList<>();
        for (var a : temp) ans.add(new Pair<>(
                new Vec3d(a[0] / 16.0, a[1] / 16.0, a[2] / 16.0), (int) Math.round(a[3]))
        );
        return ans;
    }

    // Finds a good deltaAim point such that the block can be hit.
    // Takes a point with the highest priority (if multiple, the one allowing the shortest distance among them)
    // Returns: deltaAim, block face that will be hit
    // Null if no collision possible
    @Nullable
    public Pair<Vec3d, Direction> goodAimPoint(BlockPos pos) {
        Pair<Vec3d, Direction> ans = null;
        double bestScore = -10000;
        for (var ap : deltaAimPoints) {
            var result = checkAimPoint(pos, ap.getA());
            if (result != null) {
                var score = ap.getB() * 10000 - result.getB();
                if (score > bestScore) {
                    bestScore = score;
                    ans = result.getA();
                }
            }
        }
        return ans;
    }

    // Block position, deltaAim -> (deltaAim, face), score
    // The output partially contains the input - needs even more refactoring
    @Nullable
    public Pair<Pair<Vec3d, Direction>, Double> checkAimPoint(BlockPos pos, Vec3d deltaAim) {
        if (pos == null || deltaAim == null) return null;
        if (!isAllowedTarget(pos)) return null; // This will be called a lot...

        Vec3d absAim = deltaAim.add(Vec3d.of(pos));
        var aimVector = absAim.subtract(cameraPosition()).normalize();
        BlockHitResult hit = collisionForVector(aimVector);
        if (hit.getType() == HitResult.Type.MISS || !hit.getBlockPos().equals(pos)) {
            return null;
        }
        var sqDist = cameraPosition().squaredDistanceTo(hit.getPos());
        return new Pair<>(new Pair<>(deltaAim, hit.getSide()), sqDist);
    }

    public BlockHitResult collisionForVector(Vec3d aimVector) {
        if (mc.player == null || mc.world == null) return BlockHitResult.createMissed(null, null, null);

        Vec3d start = cameraPosition();
        Vec3d delta = aimVector.normalize().multiply(getReach());
        Vec3d end = start.add(delta);
        var context = new RaycastContext(
                start, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player
        );
        return mc.world.raycast(context);
    }

    public Vec3d cameraPosition() {
        if (mc.player == null) return Vec3d.ZERO;
        return mc.player.getCameraPosVec(mc.getTickDelta());
    }

    public double getReach() {
        if (mc.interactionManager == null) return 0.0;
        return vanillaReach.get() ? mc.interactionManager.getReachDistance() : reach.get();
    }

    // Vec3d.fromPolar(pitch, yaw).normalize() ??
    public static float getYaw(Vec3d vector) {
        return (float) (Math.atan2(- vector.x, vector.z) * 180.0 / Math.PI);
    }

    public static float getPitch(Vec3d vector) {
        return (float) (Math.asin(- vector.y) * 180.0 / Math.PI);
    }
}
