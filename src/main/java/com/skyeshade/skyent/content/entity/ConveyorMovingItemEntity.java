package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.content.block.ConveyorChuteBlock;
import com.skyeshade.skyent.content.block.ConveyorElevatorBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.radiation.RadioactiveCarrierEntity;
import com.skyeshade.skyent.registry.ModEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;

public class ConveyorMovingItemEntity extends Entity implements RadioactiveCarrierEntity {
    private static final boolean DEBUG_CONVEYOR_ITEM_SYNC = false;
    private static final boolean DEBUG_CONVEYOR_MERGE = false;
    private static final int MIN_CLIENT_LERP_STEPS = 3;
    private static final double CLIENT_SNAP_DISTANCE_SQR = 4.0D;
    public static final double BELT_ITEM_SPEED = 0.062D;
    public static final double OUTPUT_EDGE_DISTANCE = 0.52D;
    public static final double BLOCKED_EDGE_DISTANCE = 0.32D;
    public static final double STRAIGHT_ITEM_SPACING = 0.32D;
    public static final double MERGE_ITEM_SPACING = 0.36D;
    public static final double MERGE_SPACING_DISTANCE_FROM_CENTER = 0.55D;
    public static final int MERGE_SPACING_TICKS = 12;
    public static final int MERGE_GHOST_TICKS = 10;
    public static final double MERGE_RESERVE_FRONT_GAP = 0.56D;
    public static final double MERGE_RESERVE_REAR_GAP = 0.56D;
    public static final double MERGE_ENTRY_CLEARANCE = 0.55D;
    public static final double MERGE_ANTICIPATION_DISTANCE = 0.02D;
    public static final double MERGE_COMMIT_DISTANCE_FROM_CENTERLINE = 0.10D;
    public static final double SAME_LANE_SPACING_DISTANCE_FROM_CENTERLINE = 0.30D;
    public static final double ITEM_SPACING_DISTANCE = STRAIGHT_ITEM_SPACING;
    public static final double ITEM_SPACING_SEARCH_RADIUS = 0.75D;
    private static final double PATH_SPACING_HIT_RADIUS = 0.16D;
    private static final double PATH_SPACING_SAMPLE_STEP = BELT_ITEM_SPEED;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(
            ConveyorMovingItemEntity.class,
            EntityDataSerializers.ITEM_STACK
    );
    private BlockPos lastBeltPos;
    private Direction lastBeltFacing;
    private int mergeSpacingTicks;
    private int spacingSuppressionTicks;
    private boolean mergeGhost;
    private int mergeGhostTicks;
    private BlockPos mergeDestinationPos;
    private Direction mergeDestinationFacing;
    private double mergeReservedProgress;
    private double clientTargetX;
    private double clientTargetY;
    private double clientTargetZ;
    private int clientLerpSteps;
    private static final EntityDataAccessor<Boolean> DATA_BLOCKED = SynchedEntityData.defineId(
            ConveyorMovingItemEntity.class,
            EntityDataSerializers.BOOLEAN
    );

    public ConveyorMovingItemEntity(EntityType<ConveyorMovingItemEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public ConveyorMovingItemEntity(Level level, double x, double y, double z, ItemStack stack) {
        this(ModEntities.CONVEYOR_MOVING_ITEM.get(), level);
        setPos(x, y, z);
        setItemStack(stack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_BLOCKED, false);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);

        if (getItemStack().isEmpty()) {
            discard();
            return;
        }

        if (level().isClientSide) {
            tickClientInterpolation();
            return;
        }

        tickServerMovement();
    }

    private void tickServerMovement() {
        BeltContext belt = findCurrentBelt();

        if (belt == null && lastBeltPos != null) {
            belt = beltAt(lastBeltPos);
            if (belt != null) {
                tryHandleOutput(belt, true);
                tickMergeSpacing();
                return;
            }
        }

        if (belt == null || !belt.surface().canItemStay(level(), belt.pos(), position())) {
            dropAsNormalItem(position(), Vec3.ZERO);
            discard();
            return;
        }

        updateBeltTracking(belt);
        updateMergeGhostState(belt);

        BlockState currentState = level().getBlockState(belt.pos());
        if (currentState.getBlock() instanceof ConveyorElevatorBlock) {
            if (ConveyorElevatorBlock.tryCaptureMovingItem(level(), belt.pos(), currentState, this)) {
                tickMergeSpacing();
                return;
            }
        }
        if (currentState.getBlock() instanceof ConveyorChuteBlock) {
            if (ConveyorChuteBlock.tryCaptureMovingItem(level(), belt.pos(), currentState, this)) {
                tickMergeSpacing();
                return;
            }
        }

        if (isBlocked()) {
            if (!canMoveOnBelt(belt)) {
                tickMergeSpacing();
                return;
            }
            setPos(blockedPosition(belt));
            tryHandleOutput(belt, true);
            tickMergeSpacing();
            return;
        }

        if (!canMoveOnBelt(belt)) {
            tickMergeSpacing();
            return;
        }

        if (usesHorizontalOutput(belt) && tryHandleOutput(belt, false)) {
            tickMergeSpacing();
            return;
        }

        Vec3 next = belt.surface().getTravelLocation(level(), belt.pos(), position(), BELT_ITEM_SPEED);

        if (usesHorizontalOutput(belt) && wouldReachOutputEdge(belt, next) && shouldPreHandleOutput(belt)) {
            setPos(canOutputFromBelt(belt) ? clampedFrontPosition(belt) : blockedPosition(belt));
            tryHandleOutput(belt, true);
            tickMergeSpacing();
            return;
        }

        if (isItemAheadTooClose(belt, next)) {
            tickMergeSpacing();
            return;
        }

        setBlocked(false);
        setPos(next.x, next.y, next.z);
        tickMergeSpacing();
    }
    private boolean shouldPreHandleOutput(BeltContext belt) {
        if (!canOutputFromBelt(belt)) {
            return true;
        }

        BlockPos outputPos = belt.pos().relative(belt.facing());
        BlockState outputState = level().getBlockState(outputPos);


        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            return false;
        }


        return true;
    }
    private boolean wouldReachOutputEdge(BeltContext belt, Vec3 pos) {
        Vec3 center = belt.pos().getCenter();
        double forwardDistance =
                (pos.x - center.x) * belt.facing().getStepX()
                        + (pos.z - center.z) * belt.facing().getStepZ();

        return forwardDistance >= OUTPUT_EDGE_DISTANCE;
    }

    private void tickClientInterpolation() {
        if (clientLerpSteps <= 0) {
            tickMergeSpacing();
            return;
        }

        double nextX = getX() + (clientTargetX - getX()) / clientLerpSteps;
        double nextY = getY() + (clientTargetY - getY()) / clientLerpSteps;
        double nextZ = getZ() + (clientTargetZ - getZ()) / clientLerpSteps;
        setPos(nextX, nextY, nextZ);
        clientLerpSteps--;
        tickMergeSpacing();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yRot, xRot, steps);
            return;
        }

        double distanceSqr = distanceToSqr(x, y, z);
        if (distanceSqr > CLIENT_SNAP_DISTANCE_SQR) {
            setPos(x, y, z);
            clientLerpSteps = 0;
            debugSync("client snap to {},{},{} distanceSqr={}", x, y, z, distanceSqr);
            return;
        }

        clientTargetX = x;
        clientTargetY = y;
        clientTargetZ = z;
        clientLerpSteps = Math.max(steps, MIN_CLIENT_LERP_STEPS);
        debugSync("client lerp target {},{},{} steps={} distanceSqr={}", x, y, z, clientLerpSteps, distanceSqr);
    }

    private boolean tryHandleOutput(BeltContext belt, boolean force) {
        Vec3 center = belt.pos().getCenter();
        Vec3 position = position();
        double forwardDistance = (position.x - center.x) * belt.facing().getStepX()
                + (position.z - center.z) * belt.facing().getStepZ();
        if (!force && forwardDistance < OUTPUT_EDGE_DISTANCE) {
            return false;
        }

        if (!canOutputFromBelt(belt)) {
            setBlocked(true);
            setPos(blockedPosition(belt));
            return true;
        }

        BlockPos outputPos = belt.pos().relative(belt.facing());
        BlockState outputState = level().getBlockState(outputPos);
        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            if (outputState.getBlock() instanceof ConveyorElevatorBlock) {
                if (!ConveyorElevatorBlock.canAcceptHorizontalInput(level(), outputPos, outputState, belt.facing().getOpposite())) {
                    setBlocked(true);
                    setPos(clampedFrontPosition(belt));
                    return true;
                }
                setBlocked(false);
                return false;
            }
            if (outputState.getBlock() instanceof ConveyorChuteBlock) {
                if (!ConveyorChuteBlock.canAcceptHorizontalInput(level(), outputPos, outputState, belt.facing().getOpposite())) {
                    setBlocked(true);
                    setPos(clampedFrontPosition(belt));
                    return true;
                }
                setBlocked(false);
                return false;
            }

            Direction outputFacing = getConveyorTravelDirection(outputState, outputPos);
            if (outputFacing == belt.facing().getOpposite()) {
                setBlocked(true);
                setPos(clampedFrontPosition(belt));
                return true;
            }
            if (!canEnterOutputBelt(outputState, outputPos, belt.facing().getOpposite())) {
                setBlocked(true);
                setPos(clampedFrontPosition(belt));
                return true;
            }
            if (outputFacing != belt.facing()) {
                MergeReservation reservation = reserveMergeGapIntoOutputBelt(belt, outputPos, outputFacing);
                if (!reservation.allowed()) {
                    setBlocked(true);
                    setPos(clampedFrontPosition(belt));
                    return true;
                }
                startMergeGhost(outputPos, outputFacing, reservation.reservedProgress());
            }
            setBlocked(false);
            return false;
        }

        var handler = level().getCapability(
                Capabilities.ItemHandler.BLOCK,
                outputPos,
                belt.facing().getOpposite()
        );
        if (handler != null) {
            ItemStack remainder = insertIntoHandler(handler, getItemStack());
            if (remainder.isEmpty()) {
                discard();
                return true;
            }
            if (remainder.getCount() != getItemStack().getCount()) {
                setItemStack(remainder);
            }
            setBlocked(true);
            setPos(clampedFrontPosition(belt));
            return true;
        }

        if (outputState.isAir()) {
            Vec3 dropPos = position.add(belt.facing().getStepX() * 0.15D, 0.02D, belt.facing().getStepZ() * 0.15D);
            dropAsNormalItem(dropPos, new Vec3(belt.facing().getStepX() * 0.08D, 0.04D, belt.facing().getStepZ() * 0.08D));
            discard();
            return true;
        }

        setBlocked(true);
        setPos(clampedFrontPosition(belt));
        return true;
    }

    private boolean canMoveOnBelt(BeltContext belt) {
        BlockState state = level().getBlockState(belt.pos());
        return !(belt.surface() instanceof ConveyorGateSurface gate)
                || gate.skyent$canConveyorItemMove(level(), belt.pos(), state, this);
    }

    private boolean canOutputFromBelt(BeltContext belt) {
        BlockState state = level().getBlockState(belt.pos());
        return !(belt.surface() instanceof ConveyorGateSurface gate)
                || gate.skyent$canConveyorItemOutput(level(), belt.pos(), state, this, belt.facing());
    }

    private boolean canEnterOutputBelt(BlockState outputState, BlockPos outputPos, Direction fromDirection) {
        return !(outputState.getBlock() instanceof ConveyorGateSurface gate)
                || gate.skyent$canConveyorItemEnter(level(), outputPos, outputState, fromDirection);
    }

    private MergeReservation reserveMergeGapIntoOutputBelt(BeltContext sourceBelt, BlockPos outputPos, Direction outputFacing) {
        Vec3 mergePosition = mergeEntryPosition(outputPos, outputFacing, sourceBelt.facing());
        AABB searchBox = new AABB(mergePosition, mergePosition).inflate(ITEM_SPACING_SEARCH_RADIUS + MERGE_ITEM_SPACING);
        ConveyorMovingItemEntity aheadItem = null;
        ConveyorMovingItemEntity behindItem = null;
        ConveyorMovingItemEntity ghostConflict = null;
        ConveyorMovingItemEntity clearanceBlocker = null;
        double nearestAhead = Double.MAX_VALUE;
        double nearestBehind = Double.MAX_VALUE;
        double reservedProgress = progressAlong(outputFacing, mergePosition);

        for (ConveyorMovingItemEntity other : level().getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> entity != this && !entity.isRemoved())) {
            if (other.isUncommittedMergeGhostFor(outputPos, outputFacing)) {
                ghostConflict = other;
                break;
            }
            if (!isOnDestinationLane(other.position(), outputPos, outputFacing)) {
                continue;
            }

            double alongLane = projectionAlong(other.position().subtract(mergePosition), outputFacing);
            double distanceFromEntry = Math.abs(alongLane);
            if (distanceFromEntry < MERGE_ENTRY_CLEARANCE) {
                clearanceBlocker = other;
            }
            if (alongLane > 0.0D && alongLane < nearestAhead) {
                nearestAhead = alongLane;
                aheadItem = other;
            } else if (alongLane < 0.0D && -alongLane < nearestBehind) {
                nearestBehind = -alongLane;
                behindItem = other;
            }
        }

        boolean frontGap = aheadItem == null || nearestAhead >= MERGE_RESERVE_FRONT_GAP;
        boolean rearGap = behindItem == null || nearestBehind >= MERGE_RESERVE_REAR_GAP;
        boolean gapWideEnough = aheadItem == null || behindItem == null
                || nearestAhead + nearestBehind >= MERGE_RESERVE_FRONT_GAP + MERGE_RESERVE_REAR_GAP;
        boolean allowed = ghostConflict == null && clearanceBlocker == null && frontGap && rearGap && gapWideEnough;
        debugMerge(
                "reserve {}@{} -> {}@{} entry={} entryProgress={} allowed={} ahead={} aheadGap={} behind={} behindGap={} ghostConflict={} clearanceBlocker={} anticipation={} gapWideEnough={}",
                sourceBelt.facing(),
                sourceBelt.pos(),
                outputFacing,
                outputPos,
                mergePosition,
                reservedProgress,
                allowed,
                aheadItem == null ? null : aheadItem.getUUID(),
                aheadItem == null ? -1.0D : nearestAhead,
                behindItem == null ? null : behindItem.getUUID(),
                behindItem == null ? -1.0D : nearestBehind,
                ghostConflict == null ? null : ghostConflict.getUUID(),
                clearanceBlocker == null ? null : clearanceBlocker.getUUID(),
                MERGE_ANTICIPATION_DISTANCE,
                gapWideEnough
        );
        return new MergeReservation(allowed, reservedProgress);
    }

    private static Vec3 mergeEntryPosition(BlockPos outputPos, Direction outputFacing, Direction incomingDirection) {
        double x = outputPos.getX() + 0.5D;
        double y = outputPos.getY() + com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants.ITEM_PATH_Y_OFFSET;
        double z = outputPos.getZ() + 0.5D;

        if (incomingDirection == outputFacing.getOpposite()) {
            x -= outputFacing.getStepX() * BLOCKED_EDGE_DISTANCE;
            z -= outputFacing.getStepZ() * BLOCKED_EDGE_DISTANCE;
        } else {
            x -= incomingDirection.getStepX() * BLOCKED_EDGE_DISTANCE;
            z -= incomingDirection.getStepZ() * BLOCKED_EDGE_DISTANCE;
        }

        return new Vec3(x, y, z);
    }

    private static boolean isOnDestinationLane(Vec3 position, BlockPos beltPos, Direction beltFacing) {
        return distanceFromLaneCenterline(beltPos, beltFacing, position) <= SAME_LANE_SPACING_DISTANCE_FROM_CENTERLINE;
    }

    private static double distanceFromLaneCenterline(BlockPos beltPos, Direction beltFacing, Vec3 position) {
        Vec3 center = beltPos.getCenter();
        return beltFacing.getAxis() == Direction.Axis.X
                ? Math.abs(position.z - center.z)
                : Math.abs(position.x - center.x);
    }

    private static double projectionAlong(Vec3 delta, Direction direction) {
        return delta.x * direction.getStepX() + delta.z * direction.getStepZ();
    }

    private static double progressAlong(Direction direction, Vec3 position) {
        return position.x * direction.getStepX() + position.z * direction.getStepZ();
    }

    private void startMergeGhost(BlockPos destinationPos, Direction destinationFacing, double reservedProgress) {
        mergeGhost = true;
        mergeGhostTicks = MERGE_GHOST_TICKS;
        mergeDestinationPos = destinationPos;
        mergeDestinationFacing = destinationFacing;
        mergeReservedProgress = reservedProgress;
        mergeSpacingTicks = Math.max(mergeSpacingTicks, MERGE_SPACING_TICKS);
        debugMerge("ghost start destination={} facing={} reservedProgress={}", destinationPos, destinationFacing, reservedProgress);
    }

    private void updateMergeGhostState(BeltContext belt) {
        if (!mergeGhost) {
            return;
        }

        if (shouldCommitMergeGhost(belt)) {
            debugMerge("ghost commit on {} facing={} pos={}", belt.pos(), belt.facing(), position());
            clearMergeGhost();
            return;
        }

        if (mergeDestinationPos != null && mergeDestinationPos.equals(belt.pos()) && mergeDestinationFacing == belt.facing()) {
            mergeGhostTicks--;
            if (mergeGhostTicks <= 0) {
                mergeGhostTicks = 1;
                debugMerge(
                        "ghost timeout extended on {} facing={} pos={} lateralDistance={}",
                        belt.pos(),
                        belt.facing(),
                        position(),
                        distanceFromLaneCenterline(belt.pos(), belt.facing(), position())
                );
            }
        }
    }

    private boolean shouldCommitMergeGhost(BeltContext belt) {
        if (mergeDestinationPos == null || mergeDestinationFacing == null) {
            return true;
        }
        if (!mergeDestinationPos.equals(belt.pos()) || mergeDestinationFacing != belt.facing()) {
            return false;
        }

        double lateralDistance = distanceFromLaneCenterline(belt.pos(), belt.facing(), position());
        double currentProgress = progressAlong(belt.facing(), position());
        return lateralDistance <= MERGE_COMMIT_DISTANCE_FROM_CENTERLINE
                && currentProgress >= mergeReservedProgress - MERGE_ANTICIPATION_DISTANCE;
    }

    private void clearMergeGhost() {
        mergeGhost = false;
        mergeGhostTicks = 0;
        mergeDestinationPos = null;
        mergeDestinationFacing = null;
        mergeReservedProgress = 0.0D;
    }

    private boolean isUncommittedMergeGhostFor(BeltContext belt) {
        return isUncommittedMergeGhostFor(belt.pos(), belt.facing());
    }

    private boolean isUncommittedMergeGhostFor(BlockPos beltPos, Direction beltFacing) {
        return mergeGhost
                && mergeDestinationPos != null
                && mergeDestinationPos.equals(beltPos)
                && mergeDestinationFacing == beltFacing;
    }

    private Vec3 clampedFrontPosition(BeltContext belt) {
        Vec3 snap = belt.surface().getClosestSnappingPosition(level(), belt.pos(), position());
        Vec3 center = belt.pos().getCenter();
        return new Vec3(
                center.x + belt.facing().getStepX() * BLOCKED_EDGE_DISTANCE,
                snap.y,
                center.z + belt.facing().getStepZ() * BLOCKED_EDGE_DISTANCE
        );
    }

    private Vec3 blockedPosition(BeltContext belt) {
        BlockState state = level().getBlockState(belt.pos());
        if (belt.surface() instanceof ConveyorGateSurface gate) {
            Vec3 holdPosition = gate.skyent$getConveyorHoldPosition(level(), belt.pos(), state, this, belt.facing());
            if (holdPosition != null) {
                return holdPosition;
            }
        }
        return clampedFrontPosition(belt);
    }

    private boolean isItemAheadTooClose(BeltContext belt, Vec3 nextPosition) {
        if (spacingSuppressionTicks > 0) {
            return false;
        }

        if (isUncommittedMergeGhostFor(belt)) {
            return false;
        }

        AABB searchBox = getBoundingBox().inflate(ITEM_SPACING_SEARCH_RADIUS);
        for (ConveyorMovingItemEntity other : level().getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> entity != this && !entity.isRemoved())) {
            if (other.spacingSuppressionTicks > 0) {
                continue;
            }
            if (other.isUncommittedMergeGhostFor(belt)) {
                debugMerge("ignored ghost {} in spacing check on {} facing={}", other.getUUID(), belt.pos(), belt.facing());
                continue;
            }

            double requiredSpacing = Math.max(getCurrentSpacingDistance(), other.getCurrentSpacingDistance());
            if (isVerticalElevatorSpacingBlocked(belt, nextPosition, other, requiredSpacing)) {
                return true;
            }

            if (isOnSameBeltLane(belt, other, other.findCurrentBelt())) {
                Vec3 delta = other.position().subtract(nextPosition);
                double ahead = delta.x * belt.facing().getStepX() + delta.z * belt.facing().getStepZ();
                if (ahead > 0.0D && ahead < requiredSpacing) {
                    return true;
                }
                continue;
            }

            if (isOtherOnFuturePathWithinSpacing(belt, nextPosition, other, requiredSpacing)) {
                return true;
            }
        }
        return false;
    }

    private boolean usesHorizontalOutput(BeltContext belt) {
        BlockState state = level().getBlockState(belt.pos());
        if (state.getBlock() instanceof ConveyorElevatorBlock) {
            return ConveyorElevatorBlock.isHorizontalOutputSegment(state);
        }
        if (state.getBlock() instanceof ConveyorChuteBlock) {
            return ConveyorChuteBlock.isHorizontalOutputSegment(state);
        }
        return true;
    }

    private boolean isVerticalElevatorSpacingBlocked(BeltContext belt, Vec3 nextPosition, ConveyorMovingItemEntity other, double requiredSpacing) {
        BlockState state = level().getBlockState(belt.pos());
        if (!ConveyorElevatorBlock.isVerticalTravelSegment(state) && !ConveyorChuteBlock.isVerticalTravelSegment(state)) {
            return false;
        }

        BeltContext otherBelt = other.findCurrentBelt();
        if (otherBelt != null && !otherBelt.pos().equals(belt.pos()) && !otherBelt.pos().equals(belt.pos().above())) {
            return false;
        }
        if (otherBelt == null && other.lastBeltPos != null && !other.lastBeltPos.equals(belt.pos()) && !other.lastBeltPos.equals(belt.pos().above())) {
            return false;
        }

        double dx = other.getX() - nextPosition.x;
        double dz = other.getZ() - nextPosition.z;
        if (dx * dx + dz * dz > SAME_LANE_SPACING_DISTANCE_FROM_CENTERLINE * SAME_LANE_SPACING_DISTANCE_FROM_CENTERLINE) {
            return false;
        }

        double ahead = other.getY() - nextPosition.y;
        return ahead > 0.0D && ahead < requiredSpacing;
    }

    private boolean isOnSameBeltLane(BeltContext belt, ConveyorMovingItemEntity other, @Nullable BeltContext otherBelt) {
        if (otherBelt != null && (!otherBelt.pos().equals(belt.pos()) || otherBelt.facing() != belt.facing())) {
            return false;
        }
        if (otherBelt == null && other.lastBeltPos != null && (!other.lastBeltPos.equals(belt.pos()) || other.lastBeltFacing != belt.facing())) {
            return false;
        }
        if (otherBelt == null && other.lastBeltPos == null) {
            return false;
        }

        double lateralDistance = distanceFromLaneCenterline(belt.pos(), belt.facing(), other.position());
        if (lateralDistance > SAME_LANE_SPACING_DISTANCE_FROM_CENTERLINE) {
            debugMerge(
                    "ignored off-lane item {} in spacing check on {} facing={} lateralDistance={}",
                    other.getUUID(),
                    belt.pos(),
                    belt.facing(),
                    lateralDistance
            );
            return false;
        }
        return true;
    }

    private boolean isOtherOnFuturePathWithinSpacing(BeltContext startBelt, Vec3 startPosition, ConveyorMovingItemEntity other, double maxDistance) {
        Vec3 probe = startPosition;
        BeltContext probeBelt = startBelt;
        double traveled = 0.0D;
        double hitRadiusSqr = PATH_SPACING_HIT_RADIUS * PATH_SPACING_HIT_RADIUS;

        for (int i = 0; i < 16 && traveled < maxDistance; i++) {
            if (!isRelevantPathSpacingItem(startBelt, probeBelt, other)) {
                return false;
            }

            if (horizontalDistanceSqr(probe, other.position()) <= hitRadiusSqr) {
                return traveled > 0.0D;
            }

            Vec3 nextProbe = probeBelt.surface().getTravelLocation(level(), probeBelt.pos(), probe, PATH_SPACING_SAMPLE_STEP);
            double stepDistance = horizontalDistance(probe, nextProbe);
            if (stepDistance <= 1.0E-6D) {
                break;
            }
            traveled += stepDistance;
            probe = nextProbe;

            if (wouldReachOutputEdge(probeBelt, probe)) {
                BlockPos outputPos = probeBelt.pos().relative(probeBelt.facing());
                BeltContext nextBelt = beltAt(outputPos);
                if (nextBelt == null || nextBelt.facing() == probeBelt.facing().getOpposite()) {
                    break;
                }
                probeBelt = nextBelt;
            }
        }

        return false;
    }

    private boolean isRelevantPathSpacingItem(BeltContext startBelt, BeltContext probeBelt, ConveyorMovingItemEntity other) {
        BlockPos directOutputPos = startBelt.pos().relative(startBelt.facing());
        BeltContext otherBelt = other.findCurrentBelt();

        if (otherBelt != null) {
            return otherBelt.pos().equals(startBelt.pos())
                    || otherBelt.pos().equals(probeBelt.pos())
                    || otherBelt.pos().equals(directOutputPos);
        }

        if (other.lastBeltPos == null) {
            return false;
        }

        return other.lastBeltPos.equals(startBelt.pos())
                || other.lastBeltPos.equals(probeBelt.pos())
                || other.lastBeltPos.equals(directOutputPos);
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        return Math.sqrt(horizontalDistanceSqr(a, b));
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private void updateBeltTracking(BeltContext belt) {
        if (lastBeltPos != null && !lastBeltPos.equals(belt.pos()) && lastBeltFacing != null && lastBeltFacing != belt.facing()) {
            mergeSpacingTicks = MERGE_SPACING_TICKS;
        }
        lastBeltPos = belt.pos();
        lastBeltFacing = belt.facing();
    }

    private void tickMergeSpacing() {
        if (mergeSpacingTicks > 0) {
            mergeSpacingTicks--;
        }
        if (spacingSuppressionTicks > 0) {
            spacingSuppressionTicks--;
        }
    }

    private boolean isUsingMergeSpacing() {
        return mergeSpacingTicks > 0 || isNearBeltCenter();
    }

    private boolean isNearBeltCenter() {
        BeltContext belt = findCurrentBelt();
        if (belt == null) {
            return false;
        }

        Vec3 center = belt.pos().getCenter();
        double dx = getX() - center.x;
        double dz = getZ() - center.z;
        return Math.sqrt(dx * dx + dz * dz) < MERGE_SPACING_DISTANCE_FROM_CENTER && mergeSpacingTicks > 0;
    }

    private double getCurrentSpacingDistance() {
        if (mergeGhost) {
            return STRAIGHT_ITEM_SPACING;
        }
        return isUsingMergeSpacing() ? MERGE_ITEM_SPACING : STRAIGHT_ITEM_SPACING;
    }

    private BeltContext findCurrentBelt() {
        BlockPos current = BlockPos.containing(getX(), getY() - 0.05D, getZ());
        BeltContext context = beltAt(current);
        if (context != null) {
            return context;
        }
        return beltAt(current.below());
    }

    private BeltContext beltAt(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (!(state.getBlock() instanceof ConveyorBeltSurface surface)) {
            return null;
        }
        Direction facing = getConveyorTravelDirection(state, pos);
        if (facing == null) {
            return null;
        }
        return new BeltContext(pos, surface, facing);
    }

    @Nullable
    private Direction getConveyorTravelDirection(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof ConveyorTravelDirectionProvider provider) {
            return provider.skyent$getConveyorTravelDirection(level(), pos, state);
        }
        if (state.hasProperty(com.skyeshade.skyent.content.block.BasicConveyorBeltBlock.FACING)) {
            return state.getValue(com.skyeshade.skyent.content.block.BasicConveyorBeltBlock.FACING);
        }
        return null;
    }

    private static ItemStack insertIntoHandler(net.neoforged.neoforge.items.IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    public ItemStack getItemStack() {
        return entityData.get(DATA_ITEM);
    }

    @Override
    public ItemStack skyent$getRadiationStack() {
        return getItemStack();
    }

    @Override
    public Vec3 skyent$getRadiationPosition() {
        return position();
    }

    public void setItemStack(ItemStack stack) {
        entityData.set(DATA_ITEM, stack.copy());
    }

    public boolean isBlocked() {
        return entityData.get(DATA_BLOCKED);
    }

    public void setBlocked(boolean blocked) {
        entityData.set(DATA_BLOCKED, blocked);
    }

    public void suppressSpacingFor(int ticks) {
        spacingSuppressionTicks = Math.max(spacingSuppressionTicks, ticks);
    }

    private void debugSync(String message, Object... args) {
        if (DEBUG_CONVEYOR_ITEM_SYNC) {
            Object[] combined = new Object[args.length + 1];
            combined[0] = getUUID();
            System.arraycopy(args, 0, combined, 1, args.length);
            com.skyeshade.skyent.SkyesNuclearTech.LOGGER.info("[ConveyorMovingItem {}] " + message, combined);
        }
    }

    private void debugMerge(String message, Object... args) {
        if (DEBUG_CONVEYOR_MERGE) {
            Object[] combined = new Object[args.length + 1];
            combined[0] = getUUID();
            System.arraycopy(args, 0, combined, 1, args.length);
            com.skyeshade.skyent.SkyesNuclearTech.LOGGER.info("[ConveyorMerge {}] " + message, combined);
        }
    }

    public void dropAsNormalItem(Vec3 position, Vec3 velocity) {
        if (level().isClientSide || getItemStack().isEmpty()) {
            return;
        }

        ItemEntity itemEntity = new ItemEntity(level(), position.x, position.y, position.z, getItemStack().copy());
        itemEntity.setDeltaMovement(velocity);
        itemEntity.setPickUpDelay(10);
        level().addFreshEntity(itemEntity);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        setItemStack(ItemStack.parseOptional(level().registryAccess(), compound.getCompound("Item")));
        setBlocked(compound.getBoolean("Blocked"));
        mergeSpacingTicks = compound.getInt("MergeSpacingTicks");
        spacingSuppressionTicks = compound.getInt("SpacingSuppressionTicks");
        clearMergeGhost();
        if (compound.contains("LastBeltX")) {
            lastBeltPos = new BlockPos(compound.getInt("LastBeltX"), compound.getInt("LastBeltY"), compound.getInt("LastBeltZ"));
        }
        Direction loadedFacing = Direction.byName(compound.getString("LastBeltFacing"));
        if (loadedFacing != null) {
            lastBeltFacing = loadedFacing;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (!getItemStack().isEmpty()) {
            compound.put("Item", getItemStack().save(level().registryAccess()));
        }
        compound.putBoolean("Blocked", isBlocked());
        compound.putInt("MergeSpacingTicks", mergeSpacingTicks);
        compound.putInt("SpacingSuppressionTicks", spacingSuppressionTicks);
        if (lastBeltPos != null) {
            compound.putInt("LastBeltX", lastBeltPos.getX());
            compound.putInt("LastBeltY", lastBeltPos.getY());
            compound.putInt("LastBeltZ", lastBeltPos.getZ());
        }
        if (lastBeltFacing != null) {
            compound.putString("LastBeltFacing", lastBeltFacing.getName());
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = getItemStack();
        if (stack.isEmpty()) {
            discard();
            return InteractionResult.CONSUME;
        }

        ItemStack remainder = stack.copy();
        int before = remainder.getCount();
        boolean added = player.getInventory().add(remainder);
        if (added || remainder.getCount() != before) {
            if (remainder.isEmpty()) {
                discard();
            } else {
                setItemStack(remainder);
            }
            level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private record MergeReservation(boolean allowed, double reservedProgress) {
    }

    private record BeltContext(BlockPos pos, ConveyorBeltSurface surface, net.minecraft.core.Direction facing) {
    }
}
