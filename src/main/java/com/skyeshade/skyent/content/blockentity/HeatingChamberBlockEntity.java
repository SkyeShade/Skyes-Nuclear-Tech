package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLightRefreshTracker;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.content.block.BasicConveyorBeltBlock;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.content.particle.StreakParticleOptions;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class HeatingChamberBlockEntity extends BlockEntity {
    private static final boolean DEBUG_HEATING_CHAMBER_BATCH = false;
    private static final int BATCH_SIZE = 4;
    private static final int MAX_INSIDE_ITEMS = 4;
    private static final int COLLECT_TICKS = 5;
    private static final int CLOSE_TICKS = 25;
    private static final int MIN_HEAT_TICKS = 100;
    private static final int MAX_HEAT_TICKS = 1000;
    private static final int MAX_BATCH_ITEMS = BATCH_SIZE * 64;
    private static final int OPEN_TICKS = 25;
    private static final float MAX_CHAMBER_TRAVEL_BLOCKS = 10.0F / 16.0F;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final float MOVEMENT_SOUND_VOLUME = 0.85F;
    private static final float MOVEMENT_SOUND_PITCH = 0.85F;
    private static final float HEATING_LOOP_VOLUME = 1.55F;
    private static final float HEATING_LOOP_PITCH = 0.85F;
    private static final double CAPTURE_FORWARD_DISTANCE = 0.16D;
    private static final double[] CAPTURE_HOLDING_SLOT_LOCAL_Z_FRONT_TO_BACK = {0.25D, 0.75D, 1.25D, 1.75D};
    private static final int HEATING_SPARK_INTERVAL = 3;
    private static final int OPENING_SMOKE_WINDOW_TICKS = 8;

    private HeatingState heatingState = HeatingState.IDLE_INTAKE;
    private final Set<UUID> insideItemIds = new HashSet<>();
    private final Set<UUID> capturedItemIds = new HashSet<>();
    private final Set<UUID> releasedCapturedItemIds = new HashSet<>();
    private int stateTicks;
    private int activeHeatingDuration = MIN_HEAT_TICKS;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public HeatingChamberBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HEATING_CHAMBER.get(), pos, blockState);
    }

    @Override
    public ModelData getModelData() {
        if (level == null && cachedSharedPackedLight < 0) {
            return ModelData.EMPTY;
        }

        int packedLight = cachedSharedPackedLight >= 0 ? cachedSharedPackedLight : computePackedLight(level);
        return ModelData.of(SkyentModelData.SHARED_PACKED_LIGHT, packedLight);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            HeatingChamberLightRefreshTracker.register(worldPosition);
        }
        refreshSharedLight(true);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            HeatingChamberLightRefreshTracker.unregister(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            HeatingChamberLightRefreshTracker.unregister(worldPosition);
            stopClientHeatingLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, HeatingChamberBlockEntity chamber) {
        if (!level.isClientSide) {
            return;
        }
        chamber.tickClientAnimationState();
        chamber.tickClientHeatingLoop();
        chamber.tickClientParticles();
        chamber.lightCheckTicks++;
        if (chamber.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            chamber.lightCheckTicks = 0;
            chamber.refreshSharedLight(false);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HeatingChamberBlockEntity chamber) {
        if (level.isClientSide) {
            return;
        }

        chamber.updateInsideTracking();
        chamber.captureEligibleItems();
        chamber.pruneCapturedItems();
        chamber.snapCapturedItemsToHoldingSlots();

        switch (chamber.heatingState) {
            case IDLE_INTAKE -> {
                if (!chamber.capturedItemIds.isEmpty()) {
                    chamber.transitionTo(HeatingState.COLLECTING);
                }
            }
            case COLLECTING -> {
                if (chamber.capturedItemIds.size() >= BATCH_SIZE) {
                    chamber.transitionTo(HeatingState.CLOSING);
                    return;
                }

                chamber.stateTicks++;
                if (chamber.stateTicks >= COLLECT_TICKS && !chamber.capturedItemIds.isEmpty()) {
                    chamber.debug("collect timer expired with {} captured item(s)", chamber.capturedItemIds.size());
                    chamber.transitionTo(HeatingState.CLOSING);
                }
            }
            case CLOSING -> {
                chamber.stateTicks++;
                if (chamber.stateTicks >= CLOSE_TICKS) {
                    chamber.transitionTo(HeatingState.HEATING);
                }
            }
            case HEATING -> {
                chamber.stateTicks++;
                if (chamber.stateTicks >= chamber.activeHeatingDuration) {
                    chamber.heatCapturedItems();
                    chamber.transitionTo(HeatingState.OPENING);
                }
            }
            case OPENING -> {
                chamber.stateTicks++;
                if (chamber.stateTicks >= OPEN_TICKS) {
                    chamber.transitionTo(HeatingState.RELEASING);
                }
            }
            case RELEASING -> {
                chamber.releaseNextCapturedItemIfPossible();
                if (chamber.getCapturedItemsInside().isEmpty()) {
                    chamber.debug("batch fully released");
                    chamber.capturedItemIds.clear();
                    chamber.releasedCapturedItemIds.clear();
                    chamber.transitionTo(HeatingState.IDLE_INTAKE);
                }
            }
        }
    }

    public boolean canInternalConveyorAccept() {
        return (heatingState == HeatingState.IDLE_INTAKE
                || heatingState == HeatingState.COLLECTING && stateTicks < COLLECT_TICKS)
                && capturedItemIds.size() < BATCH_SIZE
                && insideItemIds.size() < MAX_INSIDE_ITEMS;
    }

    public boolean canInternalConveyorMove() {
        return heatingState == HeatingState.IDLE_INTAKE
                || heatingState == HeatingState.COLLECTING
                || heatingState == HeatingState.RELEASING;
    }

    public boolean canInternalConveyorMove(ConveyorMovingItemEntity item) {
        if (capturedItemIds.contains(item.getUUID())) {
            return heatingState == HeatingState.RELEASING && releasedCapturedItemIds.contains(item.getUUID());
        }
        return canInternalConveyorMove();
    }

    public boolean canInternalConveyorOutput() {
        return heatingState == HeatingState.RELEASING;
    }

    public boolean canInternalConveyorOutput(ConveyorMovingItemEntity item) {
        if (capturedItemIds.contains(item.getUUID())) {
            return heatingState == HeatingState.RELEASING
                    && releasedCapturedItemIds.contains(item.getUUID())
                    && canReleaseToOutput(item);
        }
        return canInternalConveyorOutput() && canReleaseToOutput(item);
    }

    public boolean isHeating() {
        return heatingState == HeatingState.HEATING;
    }

    public float getChamberTravelBlocks(float partialTick) {
        return switch (heatingState) {
            case CLOSING -> MAX_CHAMBER_TRAVEL_BLOCKS * smoothStep((stateTicks + partialTick) / CLOSE_TICKS);
            case HEATING -> MAX_CHAMBER_TRAVEL_BLOCKS;
            case OPENING -> MAX_CHAMBER_TRAVEL_BLOCKS * (1.0F - smoothStep((stateTicks + partialTick) / OPEN_TICKS));
            case IDLE_INTAKE, COLLECTING, RELEASING -> 0.0F;
        };
    }

    public void dropInternalConveyorItems() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            item.dropAsNormalItem(item.position(), Vec3.ZERO);
            item.discard();
        }
    }

    public void refreshSharedLight(boolean forceRenderUpdate) {
        if (level == null || !level.isClientSide) {
            return;
        }

        int packedLight = computePackedLight(level);
        if (!forceRenderUpdate && packedLight == cachedSharedPackedLight) {
            return;
        }

        cachedSharedPackedLight = packedLight;
        requestModelDataUpdate();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private int computePackedLight(Level level) {
        return HeatingChamberLighting.computeControllerPackedLight(level, worldPosition);
    }

    private void tickClientAnimationState() {
        switch (heatingState) {
            case CLOSING -> stateTicks = Math.min(stateTicks + 1, CLOSE_TICKS);
            case HEATING -> stateTicks = Math.min(stateTicks + 1, Math.max(MIN_HEAT_TICKS, activeHeatingDuration));
            case OPENING -> stateTicks = Math.min(stateTicks + 1, OPEN_TICKS);
            case COLLECTING -> stateTicks = Math.min(stateTicks + 1, COLLECT_TICKS);
            case IDLE_INTAKE, RELEASING -> stateTicks = 0;
        }
    }

    private void transitionTo(HeatingState state) {
        if (heatingState == state) {
            return;
        }

        if (state == HeatingState.CLOSING) {
            finalizeCapturedBatch();
            activeHeatingDuration = computeHeatingDuration();
            playMovementSound();
        } else if (state == HeatingState.OPENING) {
            playMovementSound();
        } else if (state == HeatingState.RELEASING) {
            prepareCapturedItemsForRelease();
        } else if (state == HeatingState.IDLE_INTAKE) {
            activeHeatingDuration = MIN_HEAT_TICKS;
            releasedCapturedItemIds.clear();
        }

        heatingState = state;
        stateTicks = 0;
        debug("transitioned to {} with {} captured item(s), heatDuration={}", state, capturedItemIds.size(), activeHeatingDuration);
        setChangedAndSync();
    }

    private void finalizeCapturedBatch() {
        if (level == null) {
            return;
        }

        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            if (capturedItemIds.size() >= BATCH_SIZE) {
                return;
            }
            if (capturedItemIds.add(item.getUUID())) {
                insideItemIds.add(item.getUUID());
                item.setBlocked(true);
                debug("finalized inside item {} into batch count={}", item.getUUID(), capturedItemIds.size());
            }
        }
        snapCapturedItemsToHoldingSlots();
    }

    private void prepareCapturedItemsForRelease() {
        releasedCapturedItemIds.clear();
        for (ConveyorMovingItemEntity item : getCapturedItemsInside()) {
            item.setBlocked(true);
            debug("captured item remains blocked for staged release {}", item.getUUID());
        }
    }

    private void snapCapturedItemsToHoldingSlots() {
        if (level == null || heatingState == HeatingState.IDLE_INTAKE || heatingState == HeatingState.RELEASING || capturedItemIds.isEmpty()) {
            return;
        }

        Direction facing = getBlockState().hasProperty(HeatingChamberBlock.FACING)
                ? getBlockState().getValue(HeatingChamberBlock.FACING)
                : Direction.NORTH;
        List<ConveyorMovingItemEntity> items = getCapturedItemsSortedFrontFirst();
        int slotCount = Math.min(items.size(), CAPTURE_HOLDING_SLOT_LOCAL_Z_FRONT_TO_BACK.length);
        for (int index = 0; index < slotCount; index++) {
            ConveyorMovingItemEntity item = items.get(index);
            if (releasedCapturedItemIds.contains(item.getUUID())) {
                continue;
            }

            Vec3 slot = capturedHoldingSlotPosition(facing, index);
            item.setBlocked(true);
            item.setPos(slot.x, slot.y, slot.z);
        }
    }

    private Vec3 capturedHoldingSlotPosition(Direction facing, int frontToBackIndex) {
        double localZ = CAPTURE_HOLDING_SLOT_LOCAL_Z_FRONT_TO_BACK[Mth.clamp(frontToBackIndex, 0, CAPTURE_HOLDING_SLOT_LOCAL_Z_FRONT_TO_BACK.length - 1)];
        return localToWorld(new Vec3(
                0.5D,
                1.0D + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET,
                localZ
        ), facing);
    }

    private void releaseNextCapturedItemIfPossible() {
        if (level == null || heatingState != HeatingState.RELEASING) {
            return;
        }

        for (ConveyorMovingItemEntity item : getCapturedItemsSortedFrontFirst()) {
            if (releasedCapturedItemIds.contains(item.getUUID())) {
                continue;
            }

            item.setBlocked(true);
            if (!canReleaseToOutput(item)) {
                debug("release waiting: output blocked for item {}", item.getUUID());
                return;
            }
            ConveyorMovingItemEntity blocker = findReleasedCapturedItemAhead(item);
            if (blocker != null) {
                debug("release waiting: spacing blocked by item {} ahead of {}", blocker.getUUID(), item.getUUID());
                return;
            }

            releasedCapturedItemIds.add(item.getUUID());
            item.setBlocked(false);
            debug("released captured item {} at {}", item.getUUID(), item.position());
            return;
        }
    }

    private List<ConveyorMovingItemEntity> getCapturedItemsSortedFrontFirst() {
        List<ConveyorMovingItemEntity> items = getCapturedItemsInside();
        Direction direction = getInternalConveyorDirection();
        items.sort(Comparator.comparingDouble((ConveyorMovingItemEntity item) -> getForwardProgressAlongConveyor(item, direction)).reversed());
        return items;
    }

    private static double getForwardProgressAlongConveyor(ConveyorMovingItemEntity item, Direction direction) {
        return item.getX() * direction.getStepX() + item.getZ() * direction.getStepZ();
    }

    @Nullable
    private ConveyorMovingItemEntity findReleasedCapturedItemAhead(ConveyorMovingItemEntity item) {
        Direction direction = getInternalConveyorDirection();
        double itemProgress = getForwardProgressAlongConveyor(item, direction);
        AABB searchBox = item.getBoundingBox().inflate(ConveyorMovingItemEntity.ITEM_SPACING_SEARCH_RADIUS + ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);

        ConveyorMovingItemEntity nearestBlocker = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ConveyorMovingItemEntity other : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> entity != item && !entity.isRemoved())) {
            if (!releasedCapturedItemIds.contains(other.getUUID())) {
                continue;
            }
            double forwardDistance = getForwardProgressAlongConveyor(other, direction) - itemProgress;
            if (forwardDistance <= 0.0D || forwardDistance >= ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE) {
                continue;
            }
            if (!isNearConveyorLane(item, other, direction)) {
                continue;
            }
            if (forwardDistance < nearestDistance) {
                nearestDistance = forwardDistance;
                nearestBlocker = other;
            }
        }

        return nearestBlocker;
    }

    private static boolean isNearConveyorLane(ConveyorMovingItemEntity item, ConveyorMovingItemEntity other, Direction direction) {
        double lateralDistance = direction.getAxis() == Direction.Axis.X
                ? Math.abs(other.getZ() - item.getZ())
                : Math.abs(other.getX() - item.getX());
        return lateralDistance <= ConveyorMovingItemEntity.ITEM_SPACING_SEARCH_RADIUS;
    }

    private boolean canReleaseToOutput(ConveyorMovingItemEntity item) {
        if (level == null || heatingState != HeatingState.RELEASING) {
            return false;
        }

        Direction direction = getInternalConveyorDirection();
        BlockPos currentPos = getMovingItemBlockPos(item);
        BlockState currentState = level.getBlockState(currentPos);
        if (!isOwnInternalConveyorPart(currentState, currentPos)) {
            BlockPos below = currentPos.below();
            BlockState belowState = level.getBlockState(below);
            if (isOwnInternalConveyorPart(belowState, below)) {
                currentPos = below;
            } else {
                return false;
            }
        }

        BlockPos outputPos = currentPos.relative(direction);
        BlockState outputState = level.getBlockState(outputPos);
        if (isOwnInternalConveyorPart(outputState, outputPos)) {
            return true;
        }

        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            boolean canEnter = canEnterOutputConveyor(outputState, outputPos, direction.getOpposite());
            if (!canEnter) {
                debug("output blocked for item {} at {} canEnter={}", item.getUUID(), outputPos, canEnter);
            }
            return canEnter;
        }

        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, direction.getOpposite());
        if (handler == null) {
            debug("output blocked for item {} at {} because no conveyor or handler exists", item.getUUID(), outputPos);
            return false;
        }

        ItemStack remainder = item.getItemStack().copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, true);
        }

        boolean accepted = remainder.isEmpty();
        if (!accepted) {
            debug("output handler blocked item {} at {} remainder={}", item.getUUID(), outputPos, remainder.getCount());
        }
        return accepted;
    }

    private boolean isOwnInternalConveyorPart(BlockState state, BlockPos pos) {
        return state.is(ModBlocks.HEATING_CHAMBER_PART.get())
                && HeatingChamberBlock.isInternalConveyorLocalPos(new BlockPos(
                state.getValue(com.skyeshade.skyent.content.block.HeatingChamberPartBlock.PART_X),
                state.getValue(com.skyeshade.skyent.content.block.HeatingChamberPartBlock.PART_Y),
                state.getValue(com.skyeshade.skyent.content.block.HeatingChamberPartBlock.PART_Z)
        ))
                && HeatingChamberBlock.getMasterPos(state, pos).equals(worldPosition);
    }

    private static BlockPos getMovingItemBlockPos(ConveyorMovingItemEntity item) {
        return BlockPos.containing(item.getX(), item.getY() - 0.05D, item.getZ());
    }

    private boolean canEnterOutputConveyor(BlockState outputState, BlockPos outputPos, Direction fromDirection) {
        if (outputState.getBlock() instanceof ConveyorTravelDirectionProvider provider) {
            Direction outputDirection = provider.skyent$getConveyorTravelDirection(level, outputPos, outputState);
            if (outputDirection == fromDirection) {
                return false;
            }
        } else if (outputState.hasProperty(BasicConveyorBeltBlock.FACING) && outputState.getValue(BasicConveyorBeltBlock.FACING) == fromDirection) {
            return false;
        }

        return !(outputState.getBlock() instanceof ConveyorGateSurface gate)
                || gate.skyent$canConveyorItemEnter(level, outputPos, outputState, fromDirection);
    }

    private int computeHeatingDuration() {
        int totalItems = 0;
        for (ConveyorMovingItemEntity item : getCapturedItemsInside()) {
            totalItems += item.getItemStack().getCount();
        }

        float progress = Mth.clamp((float) totalItems / (float) MAX_BATCH_ITEMS, 0.0F, 1.0F);
        int duration = Math.round(Mth.lerp(progress, MIN_HEAT_TICKS, MAX_HEAT_TICKS));
        debug("computed heating duration {} from {} captured item(s)", duration, totalItems);
        return Mth.clamp(duration, MIN_HEAT_TICKS, MAX_HEAT_TICKS);
    }

    private void playMovementSound() {
        if (level == null || level.isClientSide) {
            return;
        }

        Vec3 center = getMachineCenter();
        level.playSound(null, center.x, center.y, center.z, ModSounds.HEAVY_MOVING_METAL.get(), SoundSource.BLOCKS, MOVEMENT_SOUND_VOLUME, MOVEMENT_SOUND_PITCH);
    }

    private void tickClientHeatingLoop() {
        if (level == null || !level.isClientSide) {
            return;
        }

        if (heatingState == HeatingState.HEATING) {
            startClientHeatingLoop(level, worldPosition, getMachineCenter());
        } else {
            stopClientHeatingLoop(level, worldPosition);
        }
    }

    private void tickClientParticles() {
        if (level == null || !level.isClientSide) {
            return;
        }

        Direction facing = getBlockState().hasProperty(HeatingChamberBlock.FACING)
                ? getBlockState().getValue(HeatingChamberBlock.FACING)
                : Direction.NORTH;

        if (heatingState == HeatingState.HEATING) {
            if (stateTicks % HEATING_SPARK_INTERVAL == 0) {
                spawnHeatingSparks(facing, 1 + level.random.nextInt(3));
            }
        } else if (heatingState == HeatingState.OPENING && stateTicks <= OPENING_SMOKE_WINDOW_TICKS) {
            spawnHeatingSmoke(facing, stateTicks <= 1 ? 10 : 5);
        }
    }

    private void spawnHeatingSmoke(Direction facing, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 local = new Vec3(
                    0.25D + level.random.nextDouble() * 0.65D,
                    1.0D + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET + 0.18D + level.random.nextDouble() * 0.45D,
                    0.3D + level.random.nextDouble() * 1.4D
            );
            Vec3 world = localToWorld(local, facing);
            Vec3 localDirection = randomHeatEjectionDirectionLocal();
            Vec3 smokeLocalDirection = new Vec3(localDirection.x, localDirection.y * 0.25D, localDirection.z).normalize();
            Vec3 worldDirection = rotateLocalVec(smokeLocalDirection, facing).normalize();
            Vec3 worldVelocity = worldDirection
                    .scale(randomBetween(0.01D, 0.035D))
                    .add(0.0D, randomBetween(0.015D, 0.04D), 0.0D);
            level.addParticle(
                    level.random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    world.x,
                    world.y,
                    world.z,
                    worldVelocity.x,
                    worldVelocity.y,
                    worldVelocity.z
            );
        }
    }

    private void spawnHeatingSparks(Direction facing, int count) {
        for (int i = 0; i < count; i++) {
            Vec3 local = new Vec3(
                    0.25D + level.random.nextDouble() * 0.65D,
                    1.0D + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET + 0.1D + level.random.nextDouble() * 0.25D,
                    0.35D + level.random.nextDouble() * 1.3D
            );
            Vec3 world = localToWorld(local, facing);
            Vec3 localDirection = randomHeatEjectionDirectionLocal();
            Vec3 worldDirection = rotateLocalVec(localDirection, facing).normalize();
            double speed = 0.03D + level.random.nextDouble() * 0.09D;
            StreakParticleOptions options = new StreakParticleOptions(
                    worldDirection,
                    0.55F + level.random.nextFloat() * 0.3F,
                    0.125F + level.random.nextFloat() * 0.025F,
                    6 + level.random.nextInt(9),
                    1.0F,
                    0.74F + level.random.nextFloat() * 0.2F,
                    0.16F,
                    0.85F + level.random.nextFloat() * 0.15F,
                    0.85F,
                    0.16F,
                    0.03F,
                    level.random.nextFloat() * 360.0F,
                    0.85F,
                    0.02F + level.random.nextFloat() * 0.04F
            );
            level.addParticle(options, world.x, world.y, world.z, worldDirection.x * speed, worldDirection.y * speed, worldDirection.z * speed);
        }
    }

    private Vec3 randomHeatEjectionDirectionLocal() {
        Vec3 localDirection;
        do {
            localDirection = new Vec3(
                    randomBetween(-1.0D, 0.6D),
                    randomBetween(-0.9D, -0.2D),
                    randomBetween(-1.0D, 1.0D)
            ).normalize();
        } while (localDirection.x > 0.35D);
        return localDirection;
    }

    private double randomBetween(double min, double max) {
        return min + level.random.nextDouble() * (max - min);
    }

    private void updateInsideTracking() {
        if (level == null) {
            return;
        }

        Set<UUID> currentInside = new HashSet<>();
        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            currentInside.add(item.getUUID());
            if (insideItemIds.add(item.getUUID())) {
                debug("item entered {} inside={} captured={}", item.getUUID(), insideItemIds.size(), capturedItemIds.size());
            }
        }

        insideItemIds.removeIf(id -> {
            boolean left = !currentInside.contains(id);
            if (left) {
                debug("item left {} inside={} captured={}", id, insideItemIds.size() - 1, capturedItemIds.size());
            }
            return left;
        });
    }

    private void captureEligibleItems() {
        if (level == null || heatingState == HeatingState.CLOSING || heatingState == HeatingState.HEATING
                || heatingState == HeatingState.OPENING || heatingState == HeatingState.RELEASING) {
            return;
        }

        BlockPos holdingPos = getHoldingBlockPos();
        Direction direction = getInternalConveyorDirection();
        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            if (capturedItemIds.size() >= BATCH_SIZE) {
                return;
            }
            if (capturedItemIds.contains(item.getUUID())) {
                item.setBlocked(true);
                continue;
            }
            if (!isCapturePointReached(item, holdingPos, direction)) {
                continue;
            }

            capturedItemIds.add(item.getUUID());
            insideItemIds.add(item.getUUID());
            item.setBlocked(true);
            debug("captured item {} at {} count={}", item.getUUID(), item.position(), capturedItemIds.size());
            setChangedAndSync();
        }
    }

    private void pruneCapturedItems() {
        if (capturedItemIds.isEmpty()) {
            return;
        }

        Set<UUID> present = new HashSet<>();
        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            present.add(item.getUUID());
        }
        capturedItemIds.removeIf(id -> heatingState != HeatingState.RELEASING && !present.contains(id));
    }

    private void heatCapturedItems() {
        for (ConveyorMovingItemEntity item : getCapturedItemsInside()) {
            ItemStack heated = heatItem(item.getItemStack());
            if (!ItemStack.matches(item.getItemStack(), heated)) {
                debug("heated captured stack {} x{}", heated.getItem(), heated.getCount());
                item.setItemStack(heated);
            }
        }
    }

    private ItemStack heatItem(ItemStack stack) {
        if (stack.isEmpty() || !HotItemUtil.isForgeableIngot(stack)) {
            return stack;
        }

        ItemStack heated = stack.copy();
        HotItemUtil.setTemperature(heated, HotItemUtil.getForgingTemperature(heated));
        return heated;
    }

    private List<ConveyorMovingItemEntity> getCapturedItemsInside() {
        List<ConveyorMovingItemEntity> items = new ArrayList<>();
        if (capturedItemIds.isEmpty()) {
            return items;
        }

        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            if (capturedItemIds.contains(item.getUUID())) {
                items.add(item);
            }
        }
        return items;
    }

    private List<ConveyorMovingItemEntity> getInternalConveyorItems() {
        List<ConveyorMovingItemEntity> items = new ArrayList<>();
        if (level == null) {
            return items;
        }

        Set<BlockPos> footprintPositions = getFootprintPositionSet();
        if (footprintPositions.isEmpty()) {
            return items;
        }

        AABB searchBox = getInternalConveyorBounds(footprintPositions).inflate(0.1D);
        for (ConveyorMovingItemEntity item : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved())) {
            BlockPos current = BlockPos.containing(item.getX(), item.getY() - 0.05D, item.getZ());
            if (footprintPositions.contains(current) || footprintPositions.contains(current.below())) {
                items.add(item);
            }
        }
        return items;
    }

    private Set<BlockPos> getFootprintPositionSet() {
        Set<BlockPos> positions = new HashSet<>();
        Direction facing = getBlockState().hasProperty(HeatingChamberBlock.FACING)
                ? getBlockState().getValue(HeatingChamberBlock.FACING)
                : Direction.NORTH;
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 1; x++) {
                for (int z = 0; z <= 1; z++) {
                    positions.add(HeatingChamberBlock.localToWorld(worldPosition, facing, x, y, z));
                }
            }
        }
        return positions;
    }

    private Vec3 getMachineCenter() {
        Set<BlockPos> positions = getFootprintPositionSet();
        if (positions.isEmpty()) {
            return worldPosition.getCenter();
        }

        double x = 0.0D;
        double y = 0.0D;
        double z = 0.0D;
        for (BlockPos pos : positions) {
            x += pos.getX() + 0.5D;
            y += pos.getY() + 0.5D;
            z += pos.getZ() + 0.5D;
        }
        double count = positions.size();
        return new Vec3(x / count, y / count, z / count);
    }

    private Vec3 localToWorld(Vec3 local, Direction facing) {
        int blockX = Mth.floor(local.x);
        int blockY = Mth.floor(local.y);
        int blockZ = Mth.floor(local.z);

        double fracX = local.x - blockX;
        double fracY = local.y - blockY;
        double fracZ = local.z - blockZ;

        BlockPos blockPos = HeatingChamberBlock.localToWorld(worldPosition, facing, blockX, blockY, blockZ);

        return switch (facing) {
            case NORTH -> new Vec3(
                    blockPos.getX() + fracX,
                    blockPos.getY() + fracY,
                    blockPos.getZ() + fracZ
            );
            case EAST -> new Vec3(
                    blockPos.getX() + 1.0D - fracZ,
                    blockPos.getY() + fracY,
                    blockPos.getZ() + fracX
            );
            case SOUTH -> new Vec3(
                    blockPos.getX() + 1.0D - fracX,
                    blockPos.getY() + fracY,
                    blockPos.getZ() + 1.0D - fracZ
            );
            case WEST -> new Vec3(
                    blockPos.getX() + fracZ,
                    blockPos.getY() + fracY,
                    blockPos.getZ() + 1.0D - fracX
            );
            default -> new Vec3(
                    blockPos.getX() + fracX,
                    blockPos.getY() + fracY,
                    blockPos.getZ() + fracZ
            );
        };
    }

    private static Vec3 rotateLocalVec(Vec3 local, Direction facing) {
        return switch (facing) {
            case NORTH -> local;
            case EAST -> new Vec3(-local.z, local.y, local.x);
            case SOUTH -> new Vec3(-local.x, local.y, -local.z);
            case WEST -> new Vec3(local.z, local.y, -local.x);
            default -> local;
        };
    }

    private BlockPos getHoldingBlockPos() {
        Direction facing = getBlockState().hasProperty(HeatingChamberBlock.FACING)
                ? getBlockState().getValue(HeatingChamberBlock.FACING)
                : Direction.NORTH;
        return HeatingChamberBlock.localToWorld(worldPosition, facing, 0, 1, 0);
    }

    private Direction getInternalConveyorDirection() {
        Direction facing = getBlockState().hasProperty(HeatingChamberBlock.FACING)
                ? getBlockState().getValue(HeatingChamberBlock.FACING)
                : Direction.NORTH;
        return HeatingChamberBlock.getInternalConveyorDirection(facing);
    }

    private static boolean isCapturePointReached(ConveyorMovingItemEntity item, BlockPos holdingPos, Direction direction) {
        BlockPos current = BlockPos.containing(item.getX(), item.getY() - 0.05D, item.getZ());
        if (current.equals(holdingPos) || current.below().equals(holdingPos)) {
            Vec3 center = holdingPos.getCenter();
            double forwardDistance = (item.getX() - center.x) * direction.getStepX()
                    + (item.getZ() - center.z) * direction.getStepZ();
            return item.isBlocked() || forwardDistance >= CAPTURE_FORWARD_DISTANCE;
        }
        return false;
    }

    private static AABB getInternalConveyorBounds(Set<BlockPos> positions) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX() + 1);
            maxY = Math.max(maxY, pos.getY() + 1);
            maxZ = Math.max(maxZ, pos.getZ() + 1);
        }

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("HeatingState", heatingState.getSerializedName());
        tag.putInt("StateTicks", stateTicks);
        tag.putInt("ActiveHeatingDuration", activeHeatingDuration);
        tag.putLongArray("InsideItemMost", insideItemIds.stream().mapToLong(UUID::getMostSignificantBits).toArray());
        tag.putLongArray("InsideItemLeast", insideItemIds.stream().mapToLong(UUID::getLeastSignificantBits).toArray());
        tag.putLongArray("CapturedItemMost", capturedItemIds.stream().mapToLong(UUID::getMostSignificantBits).toArray());
        tag.putLongArray("CapturedItemLeast", capturedItemIds.stream().mapToLong(UUID::getLeastSignificantBits).toArray());
        tag.putLongArray("ReleasedCapturedItemMost", releasedCapturedItemIds.stream().mapToLong(UUID::getMostSignificantBits).toArray());
        tag.putLongArray("ReleasedCapturedItemLeast", releasedCapturedItemIds.stream().mapToLong(UUID::getLeastSignificantBits).toArray());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heatingState = HeatingState.byName(tag.getString("HeatingState"));
        stateTicks = Math.max(0, tag.getInt("StateTicks"));
        activeHeatingDuration = tag.contains("ActiveHeatingDuration")
                ? Mth.clamp(tag.getInt("ActiveHeatingDuration"), MIN_HEAT_TICKS, MAX_HEAT_TICKS)
                : MIN_HEAT_TICKS;
        insideItemIds.clear();
        long[] insideMost = tag.getLongArray("InsideItemMost");
        long[] insideLeast = tag.getLongArray("InsideItemLeast");
        for (int i = 0; i < insideMost.length && i < insideLeast.length; i++) {
            insideItemIds.add(new UUID(insideMost[i], insideLeast[i]));
        }
        capturedItemIds.clear();
        long[] most = tag.getLongArray("CapturedItemMost");
        long[] least = tag.getLongArray("CapturedItemLeast");
        for (int i = 0; i < most.length && i < least.length; i++) {
            capturedItemIds.add(new UUID(most[i], least[i]));
        }
        releasedCapturedItemIds.clear();
        long[] releasedMost = tag.getLongArray("ReleasedCapturedItemMost");
        long[] releasedLeast = tag.getLongArray("ReleasedCapturedItemLeast");
        for (int i = 0; i < releasedMost.length && i < releasedLeast.length; i++) {
            releasedCapturedItemIds.add(new UUID(releasedMost[i], releasedLeast[i]));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum HeatingState {
        IDLE_INTAKE("idle_intake"),
        COLLECTING("collecting"),
        CLOSING("closing"),
        HEATING("heating"),
        OPENING("opening"),
        RELEASING("releasing");

        private final String serializedName;

        HeatingState(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return serializedName;
        }

        public static HeatingState byName(String name) {
            for (HeatingState state : values()) {
                if (state.serializedName.equals(name)) {
                    return state;
                }
            }
            return IDLE_INTAKE;
        }
    }

    private void debug(String message, Object... args) {
        if (DEBUG_HEATING_CHAMBER_BATCH) {
            Object[] logArgs = new Object[args.length + 1];
            logArgs[0] = worldPosition;
            System.arraycopy(args, 0, logArgs, 1, args.length);
            SkyesNuclearTech.LOGGER.info("[Heating Chamber {}] " + message, logArgs);
        }
    }

    private static void startClientHeatingLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientLoopMethod("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientHeatingLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static String heatingLoopKey(BlockPos pos) {
        return "heating_chamber:" + pos.asLong();
    }

    private static void invokeClientLoopMethod(String methodName, Level level, BlockPos pos, Vec3 center) {
        if (!level.isClientSide) {
            return;
        }

        try {
            Class<?> clientLevelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            if (!clientLevelClass.isInstance(level)) {
                return;
            }

            Class<?> managerClass = Class.forName("com.skyeshade.skyent.client.sound.MachineSoundManager");
            if ("startOrUpdateNamedLoop".equals(methodName)) {
                Method method = managerClass.getMethod(
                        methodName,
                        clientLevelClass,
                        String.class,
                        net.minecraft.sounds.SoundEvent.class,
                        SoundSource.class,
                        java.util.function.Supplier.class,
                        float.class,
                        float.class,
                        java.util.function.BooleanSupplier.class
                );
                method.invoke(
                        null,
                        level,
                        heatingLoopKey(pos),
                        ModSounds.ELECTRIC_FURNACE_LOOP.get(),
                        SoundSource.BLOCKS,
                        (java.util.function.Supplier<Vec3>) () -> center,
                        HEATING_LOOP_VOLUME,
                        HEATING_LOOP_PITCH,
                        (java.util.function.BooleanSupplier) () -> level.getBlockEntity(pos) instanceof HeatingChamberBlockEntity chamber && chamber.isHeating()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, heatingLoopKey(pos), ModSounds.ELECTRIC_FURNACE_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Heating Chamber client loop sound", exception);
        }
    }
}
