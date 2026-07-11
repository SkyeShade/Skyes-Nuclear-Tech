package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.recipe.RollingMillRecipes;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class RollingMillBlockEntity extends BlockEntity implements RJEnergyInfo {
    private static final int ENERGY_CAPACITY_RJ = 512_000;
    private static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    private static final double RUNNING_CURRENT_AMPS = 0.5D;
    private static final double MAX_INPUT_CURRENT_AMPS = 2.0D;
    private static final int ROLLING_MILL_MV_RJ_PER_TICK =
            (int) Math.round(REQUIRED_TIER.voltage() * RUNNING_CURRENT_AMPS);
    private static final int MAX_INPUT_RJ_PER_TICK =
            (int) Math.round(REQUIRED_TIER.voltage() * MAX_INPUT_CURRENT_AMPS);
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_RUNNING = "Running";
    private static final double BASE_TRANSFORM_DISTANCE_BLOCKS = 1.0D;
    private static final double TRANSFORM_OFFSET_BLOCKS = 6.0D / 16.0D;
    private static final double TRANSFORM_DISTANCE_BLOCKS = BASE_TRANSFORM_DISTANCE_BLOCKS - TRANSFORM_OFFSET_BLOCKS;
    private static final float MOTOR_LOOP_VOLUME = 0.95F;
    private static final float MOTOR_LOOP_PITCH = 0.8F;

    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private int currentEnergyUsage;
    private boolean poweredThisTick;
    private boolean hasRelevantItemInside;
    private boolean movementSinceLastServerTick;
    private boolean blockedThisTick;
    private boolean running;

    public RollingMillBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.ROLLING_MILL.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RollingMillBlockEntity rollingMill) {
        int previousEnergyUsage = rollingMill.currentEnergyUsage;
        boolean previousRunning = rollingMill.running;
        boolean movedSinceLastServerTick = rollingMill.movementSinceLastServerTick;
        rollingMill.movementSinceLastServerTick = false;
        rollingMill.currentEnergyUsage = 0;
        rollingMill.updatePowerAvailability();
        boolean processedThisTick = rollingMill.transformEligibleItems();
        rollingMill.updateRunningState(movedSinceLastServerTick || processedThisTick);
        if (previousEnergyUsage != rollingMill.currentEnergyUsage || previousRunning != rollingMill.running) {
            rollingMill.setChangedAndSync();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, RollingMillBlockEntity rollingMill) {
        if (!level.isClientSide) {
            return;
        }
        rollingMill.tickClientMotorLoop();
    }

    public boolean canInternalConveyorAccept() {
        return true;
    }

    public boolean canInternalConveyorMove(ConveyorMovingItemEntity item) {
        return !isRelevantRollingItem(item.getItemStack()) || poweredThisTick;
    }

    public boolean canInternalConveyorOutput(ConveyorMovingItemEntity item) {
        return canInternalConveyorMove(item);
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
        if (hasRelevantItemInside && !poweredThisTick) {
            return "Needs MV Power";
        }
        if (running) {
            return "Rolling";
        }
        if (blockedThisTick) {
            return "Blocked";
        }
        return "Idle";
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            stopClientMotorLoop(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            stopClientMotorLoop(level, worldPosition);
        }
        super.setRemoved();
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

    public void markInternalItemMoved(ConveyorMovingItemEntity item) {
        if (isRelevantRollingItem(item.getItemStack())) {
            movementSinceLastServerTick = true;
        }
    }

    private boolean transformEligibleItems() {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (!poweredThisTick) {
            return false;
        }

        boolean transformed = false;
        Direction direction = getInternalConveyorDirection();
        Vec3 inputCenter = getInputCenter();
        for (ConveyorMovingItemEntity item : getInternalConveyorItems()) {
            if (progressFromInput(item.position(), inputCenter, direction) < TRANSFORM_DISTANCE_BLOCKS) {
                continue;
            }

            ItemStack result = RollingMillRecipes.getRollingResult(item.getItemStack());
            if (!result.isEmpty()) {
                item.setItemStack(result);
                transformed = true;
            }
        }
        return transformed;
    }

    private void updatePowerAvailability() {
        hasRelevantItemInside = getInternalConveyorItems().stream().anyMatch(item -> isRelevantRollingItem(item.getItemStack()));
        if (!hasRelevantItemInside) {
            poweredThisTick = true;
            blockedThisTick = false;
            return;
        }
        if (rjStorage.getStoredRJ() < ROLLING_MILL_MV_RJ_PER_TICK) {
            poweredThisTick = false;
            return;
        }

        poweredThisTick = true;
    }

    private void updateRunningState(boolean activeThisTick) {
        if (!hasRelevantItemInside) {
            blockedThisTick = false;
            running = false;
            return;
        }
        if (!poweredThisTick) {
            blockedThisTick = false;
            currentEnergyUsage = 0;
            running = false;
            return;
        }
        if (!activeThisTick) {
            blockedThisTick = true;
            currentEnergyUsage = 0;
            running = false;
            return;
        }

        rjStorage.consumeRJ(ROLLING_MILL_MV_RJ_PER_TICK);
        currentEnergyUsage = ROLLING_MILL_MV_RJ_PER_TICK;
        blockedThisTick = false;
        running = true;
        setChanged();
    }

    private static boolean isRelevantRollingItem(ItemStack stack) {
        return RollingMillRecipes.isRollingInput(stack) || RollingMillRecipes.isRollingOutput(stack);
    }

    private List<ConveyorMovingItemEntity> getInternalConveyorItems() {
        List<ConveyorMovingItemEntity> items = new ArrayList<>();
        if (level == null) {
            return items;
        }

        Direction facing = getBlockState().hasProperty(RollingMillBlock.FACING)
                ? getBlockState().getValue(RollingMillBlock.FACING)
                : Direction.NORTH;
        for (int z = 0; z <= 1; z++) {
            BlockPos conveyorPos = RollingMillBlock.localToWorld(worldPosition, facing, 1, 1, z);
            AABB searchBox = new AABB(conveyorPos).inflate(0.15D, 0.55D, 0.15D);
            for (ConveyorMovingItemEntity item : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved())) {
                if (!items.contains(item)) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private Vec3 getInputCenter() {
        Direction facing = getBlockState().hasProperty(RollingMillBlock.FACING)
                ? getBlockState().getValue(RollingMillBlock.FACING)
                : Direction.NORTH;
        return RollingMillBlock.localToWorld(
                worldPosition,
                facing,
                RollingMillBlock.getInternalConveyorInputLocalPos().getX(),
                RollingMillBlock.getInternalConveyorInputLocalPos().getY(),
                RollingMillBlock.getInternalConveyorInputLocalPos().getZ()
        ).getCenter();
    }

    private Direction getInternalConveyorDirection() {
        Direction facing = getBlockState().hasProperty(RollingMillBlock.FACING)
                ? getBlockState().getValue(RollingMillBlock.FACING)
                : Direction.NORTH;
        return RollingMillBlock.getInternalConveyorDirection(facing);
    }

    private static double progressFromInput(Vec3 position, Vec3 inputCenter, Direction direction) {
        return (position.x - inputCenter.x) * direction.getStepX()
                + (position.z - inputCenter.z) * direction.getStepZ();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.putBoolean(TAG_RUNNING, running);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        running = tag.getBoolean(TAG_RUNNING);
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

    private Vec3 getMachineCenter() {
        Direction facing = getBlockState().hasProperty(RollingMillBlock.FACING)
                ? getBlockState().getValue(RollingMillBlock.FACING)
                : Direction.NORTH;
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int y = 0; y < RollingMillBlock.SIZE_Y; y++) {
            for (int x = 0; x < RollingMillBlock.SIZE_X; x++) {
                for (int z = 0; z < RollingMillBlock.SIZE_Z; z++) {
                    sum = sum.add(RollingMillBlock.localToWorld(worldPosition, facing, x, y, z).getCenter());
                    count++;
                }
            }
        }
        return sum.scale(1.0D / Math.max(1, count));
    }

    private void tickClientMotorLoop() {
        if (level == null || !level.isClientSide) {
            return;
        }
        if (running) {
            startClientMotorLoop(level, worldPosition, getMachineCenter());
        } else {
            stopClientMotorLoop(level, worldPosition);
        }
    }

    private static void startClientMotorLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientLoopMethod("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientMotorLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static String motorLoopKey(BlockPos pos) {
        return "rolling_mill_motor:" + pos.asLong();
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
                        Supplier.class,
                        float.class,
                        float.class,
                        BooleanSupplier.class
                );
                method.invoke(
                        null,
                        level,
                        motorLoopKey(pos),
                        ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get(),
                        SoundSource.BLOCKS,
                        (Supplier<Vec3>) () -> center,
                        MOTOR_LOOP_VOLUME,
                        MOTOR_LOOP_PITCH,
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof RollingMillBlockEntity rollingMill && rollingMill.isRunning()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, motorLoopKey(pos), ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Rolling Mill client loop sound", exception);
        }
    }
}
