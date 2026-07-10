package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.IndustrialPressLightRefreshTracker;
import com.skyeshade.skyent.client.render.IndustrialPressLighting;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.block.IndustrialPressPartBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.item.HotMetalItems;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class IndustrialPressBlockEntity extends BlockEntity implements RJEnergyInfo {
    private static final int CAPTURE_TICKS = 5;
    private static final int LOWER_TICKS = 25;
    private static final int RAISE_TICKS = 25;
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final float MOVEMENT_SOUND_VOLUME = 0.85F;
    private static final float MOVEMENT_SOUND_PITCH = 0.85F;
    private static final float IMPACT_SOUND_VOLUME = 0.9F;
    private static final float IMPACT_SOUND_PITCH = 0.6F;
    public static final int IMPACT_SOUND_LEAD_TICKS = 5;
    private static final int INDUSTRIAL_PRESS_ENERGY_CAPACITY_RJ = 512_000;
    private static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    private static final double RUNNING_CURRENT_AMPS = 0.5D;
    private static final double MAX_INPUT_CURRENT_AMPS = 2.0D;
    private static final int INDUSTRIAL_PRESS_MV_RJ_PER_TICK =
            (int) Math.round(REQUIRED_TIER.voltage() * RUNNING_CURRENT_AMPS);
    private static final int MAX_INPUT_RJ_PER_TICK =
            (int) Math.round(REQUIRED_TIER.voltage() * MAX_INPUT_CURRENT_AMPS);
    public static final float PRESS_REST_Y_OFFSET = 0.0F;
    public static final float PRESS_BOTTOM_Y_OFFSET = -8.0F / 16.0F;
    public static final float PRESS_TRAVEL_DISTANCE = PRESS_REST_Y_OFFSET - PRESS_BOTTOM_Y_OFFSET;
    private static final String TAG_STATE = "PressState";
    private static final String TAG_STATE_TICKS = "StateTicks";
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_CAPTURED_ITEM = "CapturedItem";
    private static final String TAG_CAPTURED_ITEM_RELEASED = "CapturedItemReleased";
    private static final String TAG_IMPACT_SOUND_PLAYED = "ImpactSoundPlayed";
    private static final String TAG_PRESS_MODE = "PressMode";
    private static final String TAG_ACTIVE_PRESS_MODE = "ActivePressMode";
    private static final int BOLTS_PER_ROD = 4;
    private static final double CAPTURE_FORWARD_MIN = -0.05D;
    private static final double CAPTURE_FORWARD_MAX = 0.08D;
    private static final double CAPTURE_LATERAL_TOLERANCE = 0.22D;
    private static final double EJECTION_FORWARD_MAX = 0.72D;
    private static final double EJECTION_LATERAL_TOLERANCE = 0.35D;
    private static final double EJECTION_VERTICAL_TOLERANCE = 0.35D;
    private static final double EJECTION_SCAN_INFLATE = 0.45D;

    private final RJStorage rjStorage = new RJStorage(INDUSTRIAL_PRESS_ENERGY_CAPACITY_RJ);
    private PressState pressState = PressState.IDLE_INTAKE;
    @Nullable
    private UUID capturedItemId;
    private boolean capturedItemReleased;
    private boolean impactSoundPlayed;
    private PressMode selectedMode = PressMode.PLATE;
    private PressMode activeMode = PressMode.PLATE;
    private int stateTicks;
    private int currentEnergyUsage;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;

    public IndustrialPressBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.INDUSTRIAL_PRESS.get(), pos, blockState);
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
            IndustrialPressLightRefreshTracker.register(worldPosition);
        }
        refreshSharedLight(true);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            IndustrialPressLightRefreshTracker.unregister(worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            IndustrialPressLightRefreshTracker.unregister(worldPosition);
        }
        super.setRemoved();
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, IndustrialPressBlockEntity press) {
        if (!level.isClientSide) {
            return;
        }
        press.tickClientAnimationState();
        press.lightCheckTicks++;
        if (press.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
            press.lightCheckTicks = 0;
            press.refreshSharedLight(false);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IndustrialPressBlockEntity press) {
        if (level.isClientSide) {
            return;
        }

        int previousEnergyUsage = press.currentEnergyUsage;
        press.currentEnergyUsage = 0;
        press.ejectUntrackedItemsPastLockPoint();
        press.pruneCapturedItem();
        press.tickStateMachine();
        if (previousEnergyUsage != press.currentEnergyUsage) {
            press.setChangedAndSync();
        }
    }

    public boolean canInternalConveyorAccept() {
        return pressState == PressState.IDLE_INTAKE && capturedItemId == null;
    }

    public boolean canInternalConveyorMove(ConveyorMovingItemEntity item) {
        if (capturedItemId != null && capturedItemId.equals(item.getUUID())) {
            return pressState == PressState.RELEASING && capturedItemReleased;
        }
        if (isUntrackedPressItemPastLockPoint(item)) {
            return true;
        }
        return pressState == PressState.IDLE_INTAKE;
    }

    public boolean canInternalConveyorOutput(ConveyorMovingItemEntity item) {
        if (capturedItemId == null || !capturedItemId.equals(item.getUUID())) {
            if (isUntrackedPressItemPastLockPoint(item)) {
                return true;
            }
            return pressState == PressState.IDLE_INTAKE;
        }
        return pressState == PressState.RELEASING
                && capturedItemReleased
                && canReleaseToOutput(item);
    }

    public int getAvailableRJCapacity() {
        return Math.min(rjStorage.getAvailableRJCapacity(), MAX_INPUT_RJ_PER_TICK);
    }

    public int receiveRJ(ElectricalTier tier, int maxAmount, boolean simulate) {
        if (tier != REQUIRED_TIER) {
            return 0;
        }

        int received = rjStorage.receiveRJ(Math.min(maxAmount, MAX_INPUT_RJ_PER_TICK), simulate);
        if (received > 0 && !simulate) {
            setChanged();
        }
        return received;
    }

    @Override
    public int getEnergyStoredRJ() {
        return rjStorage.getStoredRJ();
    }

    @Override
    public int getEnergyCapacityRJ() {
        return rjStorage.getCapacityRJ();
    }

    @Override
    public int getCurrentUsageRJPerTick() {
        return currentEnergyUsage;
    }

    @Override
    public String getVoltageTierName() {
        return REQUIRED_TIER.displayName();
    }

    public String getStatusText() {
        if (isPowerRequiredForCurrentState() && rjStorage.getStoredRJ() < INDUSTRIAL_PRESS_MV_RJ_PER_TICK) {
            return "No Power";
        }
        if (pressState == PressState.RELEASING && getCapturedItem() != null && !canReleaseToOutput(getCapturedItem())) {
            return "Output Blocked";
        }
        return switch (pressState) {
            case IDLE_INTAKE -> "Idle";
            case CAPTURING -> "Capturing";
            case LOWERING -> "Lowering";
            case PRESSING -> "Pressing";
            case RAISING -> "Raising";
            case RELEASING -> "Releasing";
        };
    }

    public String getModeDisplayName() {
        return selectedMode.displayName;
    }

    public boolean toggleMode(net.minecraft.world.entity.player.Player player) {
        if (level == null || level.isClientSide) {
            return false;
        }

        selectedMode = selectedMode.next();
        Vec3 center = getMachineCenter();
        level.playSound(null, center.x, center.y, center.z, ModSounds.MECHANICAL_LEVER.get(), SoundSource.BLOCKS, 0.85F, 1.0F);
        player.displayClientMessage(Component.literal("Industrial Press: " + selectedMode.displayName + " Mode"), true);
        setChangedAndSync();
        return true;
    }

    public float getPressTravelBlocks(float partialTick) {
        return switch (pressState) {
            case LOWERING -> PRESS_TRAVEL_DISTANCE * smoothStep((stateTicks + partialTick) / LOWER_TICKS);
            case PRESSING -> PRESS_TRAVEL_DISTANCE;
            case RAISING -> PRESS_TRAVEL_DISTANCE * (1.0F - smoothStep((stateTicks + partialTick) / RAISE_TICKS));
            case IDLE_INTAKE, CAPTURING, RELEASING -> 0.0F;
        };
    }

    public Direction getInternalConveyorDirection() {
        Direction facing = getBlockState().hasProperty(IndustrialPressBlock.FACING)
                ? getBlockState().getValue(IndustrialPressBlock.FACING)
                : Direction.NORTH;
        return facing;
    }

    public Vec3 getPressHoldPosition() {
        BlockPos pressPos = IndustrialPressBlock.localToWorld(worldPosition, getInternalConveyorDirection(), 0, 1, 0);
        return new Vec3(pressPos.getX() + 0.5D, pressPos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET, pressPos.getZ() + 0.5D);
    }

    public void dropHeldItem() {
        if (level == null || level.isClientSide) {
            return;
        }
        ConveyorMovingItemEntity item = getCapturedItem();
        if (item != null) {
            item.dropAsNormalItem(item.position(), Vec3.ZERO);
            item.discard();
        }
        capturedItemId = null;
        capturedItemReleased = false;
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

    private void tickStateMachine() {
        switch (pressState) {
            case IDLE_INTAKE -> captureEligibleItem();
            case CAPTURING -> {
                snapCapturedItemToPress();
                stateTicks++;
                if (stateTicks >= CAPTURE_TICKS) {
                    transitionTo(PressState.LOWERING);
                }
            }
            case LOWERING -> {
                snapCapturedItemToPress();
                if (!consumeEnergyForActiveTick()) {
                    return;
                }
                stateTicks++;
                playImpactSoundIfLeadReached();
                if (stateTicks >= LOWER_TICKS) {
                    transitionTo(PressState.PRESSING);
                }
            }
            case PRESSING -> {
                snapCapturedItemToPress();
                if (!consumeEnergyForActiveTick()) {
                    return;
                }
                if (pressCapturedItem()) {
                    if (!impactSoundPlayed) {
                        playImpactSound();
                        impactSoundPlayed = true;
                    }
                    transitionTo(PressState.RAISING);
                } else {
                    transitionTo(PressState.RELEASING);
                }
            }
            case RAISING -> {
                snapCapturedItemToPress();
                if (!consumeEnergyForActiveTick()) {
                    return;
                }
                stateTicks++;
                if (stateTicks >= RAISE_TICKS) {
                    transitionTo(PressState.RELEASING);
                }
            }
            case RELEASING -> releaseCapturedItemIfPossible();
        }
    }

    private void tickClientAnimationState() {
        if (isPowerRequiredForCurrentState() && currentEnergyUsage <= 0) {
            return;
        }

        switch (pressState) {
            case CAPTURING -> stateTicks = Math.min(stateTicks + 1, CAPTURE_TICKS);
            case LOWERING -> stateTicks = Math.min(stateTicks + 1, LOWER_TICKS);
            case PRESSING -> stateTicks = 0;
            case RAISING -> stateTicks = Math.min(stateTicks + 1, RAISE_TICKS);
            case IDLE_INTAKE, RELEASING -> stateTicks = 0;
        }
    }

    private void captureEligibleItem() {
        if (level == null || capturedItemId != null) {
            return;
        }

        Direction direction = getInternalConveyorDirection();
        Vec3 holdPosition = getPressHoldPosition();
        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            if (!canPress(item.getItemStack(), selectedMode)) {
                continue;
            }
            if (!isCapturePointReached(item, holdPosition, direction)) {
                continue;
            }

            capturedItemId = item.getUUID();
            activeMode = selectedMode;
            capturedItemReleased = false;
            impactSoundPlayed = false;
            item.setBlocked(true);
            snapCapturedItemToPress();
            transitionTo(PressState.CAPTURING);
            return;
        }
    }

    private boolean pressCapturedItem() {
        ConveyorMovingItemEntity item = getCapturedItem();
        if (item == null) {
            clearCapturedItem();
            return false;
        }

        ItemStack output = pressResult(item.getItemStack(), activeMode);
        if (output.isEmpty()) {
            return false;
        }
        item.setItemStack(output);
        item.setBlocked(true);
        setChangedAndSync();
        return true;
    }

    private void releaseCapturedItemIfPossible() {
        ConveyorMovingItemEntity item = getCapturedItem();
        if (item == null) {
            if (capturedItemReleased) {
                clearCapturedItem();
                transitionTo(PressState.IDLE_INTAKE);
            } else {
                clearCapturedItem();
                transitionTo(PressState.IDLE_INTAKE);
            }
            return;
        }

        if (capturedItemReleased) {
            item.setBlocked(false);
            if (!isItemInCaptureLockZone(item)) {
                clearCapturedItem();
                transitionTo(PressState.IDLE_INTAKE);
            }
            return;
        }

        item.setBlocked(true);
        if (!canReleaseToOutput(item)) {
            return;
        }

        capturedItemReleased = true;
        item.setBlocked(false);
        if (!isItemInCaptureLockZone(item)) {
            clearCapturedItem();
            transitionTo(PressState.IDLE_INTAKE);
            return;
        }
        setChangedAndSync();
    }

    private boolean canReleaseToOutput(ConveyorMovingItemEntity item) {
        if (level == null || pressState != PressState.RELEASING) {
            return false;
        }

        Direction direction = getInternalConveyorDirection();
        BlockPos currentPos = BlockPos.containing(item.getX(), item.getY() - 0.05D, item.getZ());
        BlockPos pressPos = getPressConveyorBlockPos();
        if (!currentPos.equals(pressPos)) {
            BlockPos below = currentPos.below();
            if (below.equals(pressPos)) {
                currentPos = below;
            } else {
                return false;
            }
        }

        BlockPos outputPos = currentPos.relative(direction);
        BlockState outputState = level.getBlockState(outputPos);
        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            return canEnterOutputConveyor(outputState, outputPos, direction.getOpposite());
        }

        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, direction.getOpposite());
        if (handler == null) {
            return false;
        }

        ItemStack remainder = item.getItemStack().copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, true);
        }
        return remainder.isEmpty();
    }

    private boolean canEnterOutputConveyor(BlockState outputState, BlockPos outputPos, Direction fromDirection) {
        if (level == null) {
            return false;
        }
        if (outputState.getBlock() instanceof ConveyorTravelDirectionProvider provider) {
            Direction outputDirection = provider.skyent$getConveyorTravelDirection(level, outputPos, outputState);
            if (outputDirection == fromDirection) {
                return false;
            }
        }
        return !(outputState.getBlock() instanceof IndustrialPressPartBlock)
                && (!(outputState.getBlock() instanceof com.skyeshade.skyent.content.conveyor.ConveyorGateSurface gate)
                || gate.skyent$canConveyorItemEnter(level, outputPos, outputState, fromDirection));
    }

    private void snapCapturedItemToPress() {
        ConveyorMovingItemEntity item = getCapturedItem();
        if (item == null) {
            return;
        }
        Vec3 hold = getPressHoldPosition();
        item.setPos(hold.x, hold.y, hold.z);
        item.setBlocked(true);
    }

    private boolean consumeEnergyForActiveTick() {
        if (!isPowerRequiredForCurrentState()) {
            return true;
        }
        if (rjStorage.getStoredRJ() < INDUSTRIAL_PRESS_MV_RJ_PER_TICK) {
            currentEnergyUsage = 0;
            return false;
        }

        rjStorage.consumeRJ(INDUSTRIAL_PRESS_MV_RJ_PER_TICK);
        currentEnergyUsage = INDUSTRIAL_PRESS_MV_RJ_PER_TICK;
        setChanged();
        return true;
    }

    private boolean isPowerRequiredForCurrentState() {
        return pressState == PressState.LOWERING || pressState == PressState.PRESSING || pressState == PressState.RAISING;
    }

    private void pruneCapturedItem() {
        if (capturedItemId == null) {
            return;
        }

        ConveyorMovingItemEntity item = getCapturedItem();
        if (pressState == PressState.RELEASING && capturedItemReleased && item == null) {
            clearCapturedItem();
            transitionTo(PressState.IDLE_INTAKE);
            return;
        }

        if (item == null) {
            clearCapturedItem();
            transitionTo(PressState.IDLE_INTAKE);
            return;
        }

        if (pressResult(item.getItemStack(), activeMode).isEmpty() && canSafelyReleaseStaleCapturedItem()) {
            item.setBlocked(false);
            capturedItemReleased = true;
            if (hasCapturedItemLeftPress()) {
                clearCapturedItem();
                transitionTo(PressState.IDLE_INTAKE);
            } else if (pressState != PressState.RELEASING) {
                transitionTo(PressState.RELEASING);
            } else {
                setChangedAndSync();
            }
        }
    }

    @Nullable
    private ConveyorMovingItemEntity getCapturedItem() {
        if (level == null || capturedItemId == null) {
            return null;
        }
        UUID id = capturedItemId;
        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            if (id.equals(item.getUUID())) {
                return item;
            }
        }
        return null;
    }

    private List<ConveyorMovingItemEntity> getInternalConveyorItems() {
        List<ConveyorMovingItemEntity> items = new ArrayList<>();
        if (level == null) {
            return items;
        }

        BlockPos pressPos = getPressConveyorBlockPos();
        AABB searchBox = new AABB(pressPos).inflate(0.35D, 0.35D, 0.35D);
        for (ConveyorMovingItemEntity item : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved())) {
            if (isItemInsidePressConveyor(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private BlockPos getPressConveyorBlockPos() {
        return IndustrialPressBlock.localToWorld(worldPosition, getInternalConveyorDirection(), 0, 1, 0);
    }

    private boolean isItemInsidePressConveyor(ConveyorMovingItemEntity item) {
        BlockPos current = BlockPos.containing(item.getX(), item.getY() - 0.05D, item.getZ());
        BlockPos pressPos = getPressConveyorBlockPos();
        return current.equals(pressPos) || current.below().equals(pressPos);
    }

    private boolean hasCapturedItemLeftPress() {
        return capturedItemReleased && getCapturedItem() == null;
    }

    private boolean canSafelyReleaseStaleCapturedItem() {
        return pressState == PressState.IDLE_INTAKE
                || pressState == PressState.CAPTURING;
    }

    private void clearCapturedItem() {
        capturedItemId = null;
        capturedItemReleased = false;
        impactSoundPlayed = false;
        activeMode = selectedMode;
    }

    private boolean isCapturePointReached(ConveyorMovingItemEntity item, Vec3 holdPosition, Direction direction) {
        double forwardDelta = (item.getX() - holdPosition.x) * direction.getStepX()
                + (item.getZ() - holdPosition.z) * direction.getStepZ();
        double lateralDelta = direction.getAxis() == Direction.Axis.X
                ? Math.abs(item.getZ() - holdPosition.z)
                : Math.abs(item.getX() - holdPosition.x);
        return forwardDelta >= CAPTURE_FORWARD_MIN
                && forwardDelta <= CAPTURE_FORWARD_MAX
                && lateralDelta <= CAPTURE_LATERAL_TOLERANCE;
    }

    private boolean isItemInCaptureLockZone(ConveyorMovingItemEntity item) {
        return isCapturePointReached(item, getPressHoldPosition(), getInternalConveyorDirection());
    }

    private void ejectUntrackedItemsPastLockPoint() {
        if (level == null) {
            return;
        }

        for (ConveyorMovingItemEntity item : findPressItemsNearInternalConveyor()) {
            if (!isUntrackedPressItemPastLockPoint(item)) {
                continue;
            }
            recoverUntrackedItemPastLockPoint(item);
        }
    }

    private List<ConveyorMovingItemEntity> findPressItemsNearInternalConveyor() {
        List<ConveyorMovingItemEntity> items = new ArrayList<>();
        if (level == null) {
            return items;
        }

        BlockPos pressPos = getPressConveyorBlockPos();
        AABB searchBox = new AABB(pressPos).inflate(EJECTION_SCAN_INFLATE, EJECTION_SCAN_INFLATE, EJECTION_SCAN_INFLATE);
        for (ConveyorMovingItemEntity item : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved())) {
            if (isNearPressConveyorPath(item)) {
                items.add(item);
            }
        }
        return items;
    }

    private boolean isUntrackedPressItemPastLockPoint(ConveyorMovingItemEntity item) {
        return (capturedItemId == null || !capturedItemId.equals(item.getUUID()))
                && isNearPressConveyorPath(item)
                && getForwardDistanceFromHold(item) > CAPTURE_FORWARD_MAX;
    }

    private boolean isNearPressConveyorPath(ConveyorMovingItemEntity item) {
        Vec3 hold = getPressHoldPosition();
        Direction direction = getInternalConveyorDirection();
        double forward = getForwardDistanceFromHold(item);
        double lateral = direction.getAxis() == Direction.Axis.X
                ? Math.abs(item.getZ() - hold.z)
                : Math.abs(item.getX() - hold.x);
        double vertical = Math.abs(item.getY() - hold.y);
        return forward >= CAPTURE_FORWARD_MIN
                && forward <= EJECTION_FORWARD_MAX
                && lateral <= EJECTION_LATERAL_TOLERANCE
                && vertical <= EJECTION_VERTICAL_TOLERANCE;
    }

    private void recoverUntrackedItemPastLockPoint(ConveyorMovingItemEntity item) {
        if (!item.isBlocked() && isItemOnInternalConveyorPath(item)) {
            return;
        }

        item.setBlocked(false);
        if (!isItemOnInternalConveyorPath(item) && !hasItemAheadTooClose(item)) {
            snapItemToInternalConveyorPath(item);
        }
    }

    private boolean isItemOnInternalConveyorPath(ConveyorMovingItemEntity item) {
        Vec3 hold = getPressHoldPosition();
        Direction direction = getInternalConveyorDirection();
        double lateral = direction.getAxis() == Direction.Axis.X
                ? Math.abs(item.getZ() - hold.z)
                : Math.abs(item.getX() - hold.x);
        double vertical = Math.abs(item.getY() - hold.y);
        return lateral <= CAPTURE_LATERAL_TOLERANCE
                && vertical <= 0.05D;
    }

    private void snapItemToInternalConveyorPath(ConveyorMovingItemEntity item) {
        Direction direction = getInternalConveyorDirection();
        Vec3 hold = getPressHoldPosition();
        double forward = Mth.clamp(getForwardDistanceFromHold(item), CAPTURE_FORWARD_MAX, EJECTION_FORWARD_MAX);

        double x = direction.getAxis() == Direction.Axis.X
                ? hold.x + direction.getStepX() * forward
                : hold.x;
        double z = direction.getAxis() == Direction.Axis.Z
                ? hold.z + direction.getStepZ() * forward
                : hold.z;

        item.setPos(x, hold.y, z);
    }

    private boolean hasItemAheadTooClose(ConveyorMovingItemEntity item) {
        if (level == null) {
            return false;
        }

        Direction direction = getInternalConveyorDirection();
        double itemForward = getForwardDistanceFromHold(item);
        AABB searchBox = item.getBoundingBox().inflate(ConveyorMovingItemEntity.ITEM_SPACING_SEARCH_RADIUS);
        for (ConveyorMovingItemEntity other : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> entity != item && !entity.isRemoved())) {
            if (!isNearPressConveyorPath(other)) {
                continue;
            }

            double forwardDistance = getForwardDistanceFromHold(other) - itemForward;
            if (forwardDistance <= 0.0D || forwardDistance >= ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE) {
                continue;
            }

            Vec3 hold = getPressHoldPosition();
            double lateral = direction.getAxis() == Direction.Axis.X
                    ? Math.abs(other.getZ() - hold.z)
                    : Math.abs(other.getX() - hold.x);
            if (lateral <= ConveyorMovingItemEntity.ITEM_SPACING_SEARCH_RADIUS) {
                return true;
            }
        }
        return false;
    }

    private double getForwardDistanceFromHold(ConveyorMovingItemEntity item) {
        Direction direction = getInternalConveyorDirection();
        Vec3 hold = getPressHoldPosition();
        return (item.getX() - hold.x) * direction.getStepX()
                + (item.getZ() - hold.z) * direction.getStepZ();
    }

    private boolean canPress(ItemStack stack, PressMode mode) {
        return !pressResult(stack, mode).isEmpty();
    }

    private ItemStack pressResult(ItemStack stack, PressMode mode) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        Item output = switch (mode) {
            case PLATE -> plateForIngot(stack);
            case ROD -> rodForIngot(stack);
            case BOLT -> boltForRod(stack);
        };
        if (output == null) {
            return ItemStack.EMPTY;
        }
        if (!canPressCold(stack) && !HotItemUtil.isForgeReady(stack)) {
            return ItemStack.EMPTY;
        }
        int outputCount = mode == PressMode.BOLT ? stack.getCount() * BOLTS_PER_ROD : stack.getCount();
        return new ItemStack(output, outputCount);
    }

    @Nullable
    private static Item plateForIngot(ItemStack stack) {
        Item item = HotMetalItems.getLookupItem(stack.getItem());
        if (item == Items.COPPER_INGOT) return ModItems.COPPER_PLATE.get();
        if (item == Items.IRON_INGOT) return ModItems.IRON_PLATE.get();
        if (item == Items.GOLD_INGOT) return ModItems.GOLD_PLATE.get();
        if (item == ModItems.STEEL_INGOT.get()) return ModItems.STEEL_PLATE.get();
        if (item == ModItems.ALUMINUM_INGOT.get()) return ModItems.ALUMINUM_PLATE.get();
        if (item == ModItems.TITANIUM_INGOT.get()) return ModItems.TITANIUM_PLATE.get();
        if (item == ModItems.TUNGSTEN_INGOT.get()) return ModItems.TUNGSTEN_PLATE.get();
        if (item == ModItems.COBALT_INGOT.get()) return ModItems.COBALT_PLATE.get();
        if (item == ModItems.NICKEL_INGOT.get()) return ModItems.NICKEL_PLATE.get();
        if (item == ModItems.LEAD_INGOT.get()) return ModItems.LEAD_PLATE.get();
        if (item == ModItems.COBALT_BRONZE_INGOT.get()) return ModItems.COBALT_BRONZE_PLATE.get();
        return null;
    }

    @Nullable
    private static Item rodForIngot(ItemStack stack) {
        Item item = HotMetalItems.getLookupItem(stack.getItem());
        if (item == Items.COPPER_INGOT) return ModItems.COPPER_ROD.get();
        if (item == Items.IRON_INGOT) return ModItems.IRON_ROD.get();
        if (item == ModItems.STEEL_INGOT.get()) return ModItems.STEEL_ROD.get();
        if (item == ModItems.ALUMINUM_INGOT.get()) return ModItems.ALUMINUM_ROD.get();
        if (item == ModItems.TITANIUM_INGOT.get()) return ModItems.TITANIUM_ROD.get();
        if (item == ModItems.COBALT_INGOT.get()) return ModItems.COBALT_ROD.get();
        if (item == ModItems.TUNGSTEN_INGOT.get()) return ModItems.TUNGSTEN_ROD.get();
        if (item == ModItems.NICKEL_INGOT.get()) return ModItems.NICKEL_ROD.get();
        return null;
    }

    @Nullable
    private static Item boltForRod(ItemStack stack) {
        Item item = HotMetalItems.getLookupItem(stack.getItem());
        if (item == ModItems.COPPER_ROD.get()) return ModItems.COPPER_BOLT.get();
        if (item == ModItems.IRON_ROD.get()) return ModItems.IRON_BOLT.get();
        if (item == ModItems.STEEL_ROD.get()) return ModItems.STEEL_BOLT.get();
        if (item == ModItems.ALUMINUM_ROD.get()) return ModItems.ALUMINUM_BOLT.get();
        if (item == ModItems.TITANIUM_ROD.get()) return ModItems.TITANIUM_BOLT.get();
        if (item == ModItems.COBALT_ROD.get()) return ModItems.COBALT_BOLT.get();
        if (item == ModItems.TUNGSTEN_ROD.get()) return ModItems.TUNGSTEN_BOLT.get();
        if (item == ModItems.NICKEL_ROD.get()) return ModItems.NICKEL_BOLT.get();
        return null;
    }

    private static boolean canPressCold(ItemStack stack) {
        return stack.is(Items.GOLD_INGOT) || stack.is(ModItems.LEAD_INGOT.get());
    }

    private void playImpactSound() {
        if (level == null) {
            return;
        }
        Vec3 hold = getPressHoldPosition();
        level.playSound(null, hold.x, hold.y, hold.z, ModSounds.INDUSTRIAL_PRESS.get(), SoundSource.BLOCKS, IMPACT_SOUND_VOLUME, IMPACT_SOUND_PITCH);
    }

    private void playImpactSoundIfLeadReached() {
        if (impactSoundPlayed) {
            return;
        }

        int leadTicks = Mth.clamp(IMPACT_SOUND_LEAD_TICKS, 0, LOWER_TICKS);
        int triggerTick = LOWER_TICKS - leadTicks;
        if (stateTicks >= triggerTick) {
            playImpactSound();
            impactSoundPlayed = true;
        }
    }

    private void transitionTo(PressState state) {
        if (pressState == state) {
            return;
        }
        if (state == PressState.LOWERING) {
            impactSoundPlayed = false;
            playMovementSound();
        } else if (state == PressState.RAISING) {
            playMovementSound();
        }
        pressState = state;
        if (state == PressState.IDLE_INTAKE) {
            capturedItemReleased = false;
            impactSoundPlayed = false;
        }
        stateTicks = 0;
        setChangedAndSync();
    }

    private void playMovementSound() {
        if (level == null || level.isClientSide) {
            return;
        }

        Vec3 center = getMachineCenter();
        level.playSound(null, center.x, center.y, center.z, ModSounds.HEAVY_MOVING_METAL.get(), SoundSource.BLOCKS, MOVEMENT_SOUND_VOLUME, MOVEMENT_SOUND_PITCH);
    }

    private Vec3 getMachineCenter() {
        Direction facing = getInternalConveyorDirection();
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 1; x++) {
                BlockPos cell = IndustrialPressBlock.localToWorld(worldPosition, facing, x, y, 0);
                sum = sum.add(cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D);
                count++;
            }
        }
        return sum.scale(1.0D / count);
    }

    private int computePackedLight(Level level) {
        Direction facing = getBlockState().hasProperty(IndustrialPressBlock.FACING)
                ? getBlockState().getValue(IndustrialPressBlock.FACING)
                : Direction.NORTH;
        return IndustrialPressLighting.computeMaxPackedLight(level, worldPosition, facing);
    }

    private static float smoothStep(float value) {
        float clamped = Mth.clamp(value, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_STATE, pressState.serializedName);
        tag.putInt(TAG_STATE_TICKS, stateTicks);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.putString(TAG_PRESS_MODE, selectedMode.serializedName);
        tag.putString(TAG_ACTIVE_PRESS_MODE, activeMode.serializedName);
        if (capturedItemId != null) {
            tag.putUUID(TAG_CAPTURED_ITEM, capturedItemId);
        }
        tag.putBoolean(TAG_CAPTURED_ITEM_RELEASED, capturedItemReleased);
        tag.putBoolean(TAG_IMPACT_SOUND_PLAYED, impactSoundPlayed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pressState = PressState.byName(tag.getString(TAG_STATE));
        stateTicks = Math.max(0, tag.getInt(TAG_STATE_TICKS));
        selectedMode = PressMode.byName(tag.getString(TAG_PRESS_MODE));
        activeMode = tag.contains(TAG_ACTIVE_PRESS_MODE)
                ? PressMode.byName(tag.getString(TAG_ACTIVE_PRESS_MODE))
                : selectedMode;
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        capturedItemId = tag.hasUUID(TAG_CAPTURED_ITEM) ? tag.getUUID(TAG_CAPTURED_ITEM) : null;
        capturedItemReleased = tag.getBoolean(TAG_CAPTURED_ITEM_RELEASED);
        impactSoundPlayed = tag.getBoolean(TAG_IMPACT_SOUND_PLAYED);
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

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }

    public enum PressState {
        IDLE_INTAKE("idle_intake"),
        CAPTURING("capturing"),
        LOWERING("lowering"),
        PRESSING("pressing"),
        RAISING("raising"),
        RELEASING("releasing");

        private final String serializedName;

        PressState(String serializedName) {
            this.serializedName = serializedName;
        }

        private static PressState byName(String name) {
            for (PressState state : values()) {
                if (state.serializedName.equals(name)) {
                    return state;
                }
            }
            return IDLE_INTAKE;
        }
    }

    public enum PressMode {
        PLATE("plate", "Plate"),
        ROD("rod", "Rod"),
        BOLT("bolt", "Bolt");

        private final String serializedName;
        private final String displayName;

        PressMode(String serializedName, String displayName) {
            this.serializedName = serializedName;
            this.displayName = displayName;
        }

        private PressMode next() {
            return switch (this) {
                case PLATE -> ROD;
                case ROD -> BOLT;
                case BOLT -> PLATE;
            };
        }

        private static PressMode byName(String name) {
            for (PressMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return PLATE;
        }
    }
}
