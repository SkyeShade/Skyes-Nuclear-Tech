package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.HeatingChamberLighting;
import com.skyeshade.skyent.client.render.WireMillLightRefreshTracker;
import com.skyeshade.skyent.content.block.WireMillBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorDirectTransfer;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.content.recipe.WireMillRecipes;
import com.skyeshade.skyent.content.recipe.WireMillRecipes.WireMillRecipe;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModSounds;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class WireMillBlockEntity extends BlockEntity implements RJEnergyInfo {
    private static final int LIGHT_CHECK_INTERVAL_TICKS = 40;
    private static final int ENERGY_CAPACITY_RJ = 512_000;
    private static final int ENERGY_USAGE_RJ_PER_TICK = 64;
    private static final int MAX_INPUT_RJ_PER_TICK = 256;
    private static final ElectricalTier REQUIRED_TIER = ElectricalTier.MV;
    private static final int PROCESS_TIME_TICKS = 100;
    private static final int INPUT_QUEUE_SIZE = 9;
    private static final int MAX_OUTPUT_QUEUE_SIZE = 36;
    private static final int INPUT_LOCAL_X = 0;
    private static final int INPUT_LOCAL_Y = 1;
    private static final int INPUT_LOCAL_Z = 1;
    private static final int OUTPUT_LOCAL_Y = 1;
    private static final int OUTPUT_LOCAL_Z = 0;
    private static final int[] OUTPUT_LOCAL_X = {1, 2, 3, 4};
    private static final double CAPTURE_FORWARD_DISTANCE = 0.12D;
    private static final String TAG_PENDING_INPUT = "PendingInput";
    private static final String TAG_INPUT_QUEUE = "InputQueue";
    private static final String TAG_PROGRESS = "Progress";
    private static final String TAG_OUTPUT_QUEUE = "OutputQueue";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_CURRENT_ENERGY_USAGE = "CurrentEnergyUsage";
    private static final String TAG_RUNNING = "Running";
    private static final float MOTOR_LOOP_VOLUME = 0.95F;
    private static final float MOTOR_LOOP_PITCH = 0.8F;

    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private final ItemStack[] inputQueue = new ItemStack[INPUT_QUEUE_SIZE];
    private int progress;
    private int currentEnergyUsage;
    private boolean running;
    private int cachedSharedPackedLight = -1;
    private int lightCheckTicks;
    private final Deque<ItemStack> outputQueue = new ArrayDeque<>();

    public WireMillBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.WIRE_MILL.get(), pos, blockState);
        clearInputQueue();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WireMillBlockEntity wireMill) {
        if (level.isClientSide) {
            return;
        }

        int previousUsage = wireMill.currentEnergyUsage;
        boolean previousRunning = wireMill.running;
        wireMill.currentEnergyUsage = 0;
        wireMill.running = false;
        boolean changed = wireMill.tryCaptureInputItems();
        if (!wireMill.canProcessFrontInput() && wireMill.progress != 0) {
            wireMill.progress = 0;
            changed = true;
        }
        if (wireMill.canProcessFrontInput() && wireMill.canAdvanceProcessing() && wireMill.consumeEnergyForProcessingTick()) {
            wireMill.progress++;
            wireMill.running = true;
            changed = true;
            if (wireMill.progress >= PROCESS_TIME_TICKS) {
                wireMill.finishProcessing();
                changed = true;
            }
        }

        if (wireMill.tryEjectOutputs()) {
            changed = true;
        }

        if (previousUsage != wireMill.currentEnergyUsage || previousRunning != wireMill.running) {
            changed = true;
        }
        if (changed) {
            wireMill.setChangedAndSync();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, WireMillBlockEntity wireMill) {
        if (level.isClientSide) {
            wireMill.tickClientMotorLoop();
            wireMill.lightCheckTicks++;
            if (wireMill.lightCheckTicks >= LIGHT_CHECK_INTERVAL_TICKS) {
                wireMill.lightCheckTicks = 0;
                wireMill.refreshSharedLight(false);
            }
        }
    }

    public boolean canInputConveyorAccept() {
        return getFreeInputQueueSlots() > 0;
    }

    public boolean canInputConveyorMove(ConveyorMovingItemEntity item) {
        return !WireMillRecipes.isWireInput(item.getItemStack()) || canInputConveyorAccept();
    }

    public void dropInternalContents() {
        if (level == null || level.isClientSide) {
            return;
        }

        for (int i = 0; i < inputQueue.length; i++) {
            if (!inputQueue[i].isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, inputQueue[i]);
                inputQueue[i] = ItemStack.EMPTY;
            }
        }
        while (!outputQueue.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, outputQueue.removeFirst());
        }
        for (ConveyorMovingItemEntity item : getInputConveyorItems()) {
            item.dropAsNormalItem(item.position(), Vec3.ZERO);
            item.discard();
        }
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

    public boolean isRunning() {
        return running;
    }

    public int getProgress() {
        return progress;
    }

    public int getProcessTime() {
        return PROCESS_TIME_TICKS;
    }

    public String getStatusText() {
        if (running) {
            return "Running (" + Math.min(100, Math.round(progress * 100.0F / PROCESS_TIME_TICKS)) + "%)";
        }
        if (hasQueuedInput() && rjStorage.getStoredRJ() < ENERGY_USAGE_RJ_PER_TICK) {
            return "Waiting for Power";
        }
        if (hasQueuedInput() && !canAdvanceProcessing()) {
            return "Output Full";
        }
        if (hasQueuedInput()) {
            return "Paused";
        }
        return "Idle";
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
            WireMillLightRefreshTracker.register(worldPosition);
        }
        refreshSharedLight(true);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            WireMillLightRefreshTracker.unregister(worldPosition);
            stopClientMotorLoop(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            WireMillLightRefreshTracker.unregister(worldPosition);
            stopClientMotorLoop(level, worldPosition);
        }
        super.setRemoved();
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
        Direction facing = getFacing();
        return HeatingChamberLighting.computeMaxPackedLight(
                level,
                worldPosition,
                facing,
                WireMillBlock.LENGTH,
                WireMillBlock.HEIGHT,
                WireMillBlock.WIDTH,
                WireMillBlock::localToWorld
        );
    }

    public int getFreeInputQueueSlots() {
        return INPUT_QUEUE_SIZE - getInputQueueUsed();
    }

    public int getInputQueueUsed() {
        int used = 0;
        for (ItemStack stack : inputQueue) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public int getOutputQueueUsed() {
        return outputQueue.size();
    }

    public int getMaxOutputQueueSize() {
        return MAX_OUTPUT_QUEUE_SIZE;
    }

    private boolean tryCaptureInputItems() {
        if (level == null || getFreeInputQueueSlots() <= 0) {
            return false;
        }

        Direction facing = getFacing();
        BlockPos inputPos = getInputPos(facing);
        Vec3 inputCenter = inputPos.getCenter();
        for (ConveyorMovingItemEntity item : getInputConveyorItems()) {
            if (!WireMillRecipes.isWireInput(item.getItemStack())) {
                continue;
            }
            if (!isCapturePointReached(item, inputCenter, facing)) {
                continue;
            }

            ItemStack stack = item.getItemStack();
            int accepted = enqueueInputItems(stack, getFreeInputQueueSlots());
            if (accepted <= 0) {
                continue;
            }

            if (stack.getCount() <= accepted) {
                item.discard();
            } else {
                ItemStack remainder = stack.copy();
                remainder.shrink(accepted);
                item.setItemStack(remainder);
                item.setBlocked(true);
            }
            return true;
        }
        return false;
    }

    private void finishProcessing() {
        ItemStack input = getFrontInput();
        WireMillRecipe recipe = WireMillRecipes.find(input);
        if (recipe != null) {
            ItemStack result = recipe.outputStack();
            for (int i = 0; i < recipe.outputCount(); i++) {
                outputQueue.addLast(result.copyWithCount(1));
            }
        }
        removeFrontInput();
        progress = 0;
    }

    private boolean canProcessFrontInput() {
        return WireMillRecipes.find(getFrontInput()) != null;
    }

    private boolean canAdvanceProcessing() {
        WireMillRecipe recipe = WireMillRecipes.find(getFrontInput());
        if (recipe == null) {
            return false;
        }
        return progress < PROCESS_TIME_TICKS - 1 || outputQueue.size() + recipe.outputCount() <= MAX_OUTPUT_QUEUE_SIZE;
    }

    private int enqueueInputItems(ItemStack stack, int limit) {
        if (stack.isEmpty() || !WireMillRecipes.isWireInput(stack) || limit <= 0) {
            return 0;
        }

        int accepted = Math.min(stack.getCount(), Math.min(limit, getFreeInputQueueSlots()));
        for (int i = 0; i < accepted; i++) {
            int slot = firstFreeInputQueueSlot();
            if (slot < 0) {
                return i;
            }
            inputQueue[slot] = stack.copyWithCount(1);
        }
        return accepted;
    }

    private int firstFreeInputQueueSlot() {
        for (int i = 0; i < inputQueue.length; i++) {
            if (inputQueue[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack getFrontInput() {
        return inputQueue[0];
    }

    private boolean hasQueuedInput() {
        return !getFrontInput().isEmpty();
    }

    private void removeFrontInput() {
        for (int i = 0; i < inputQueue.length - 1; i++) {
            inputQueue[i] = inputQueue[i + 1];
        }
        inputQueue[inputQueue.length - 1] = ItemStack.EMPTY;
    }

    private void compactInputQueue() {
        ItemStack[] compacted = new ItemStack[INPUT_QUEUE_SIZE];
        for (int i = 0; i < compacted.length; i++) {
            compacted[i] = ItemStack.EMPTY;
        }
        int next = 0;
        for (ItemStack stack : inputQueue) {
            if (!stack.isEmpty() && next < compacted.length) {
                compacted[next++] = stack.copyWithCount(1);
            }
        }
        System.arraycopy(compacted, 0, inputQueue, 0, inputQueue.length);
    }

    private void clearInputQueue() {
        for (int i = 0; i < inputQueue.length; i++) {
            inputQueue[i] = ItemStack.EMPTY;
        }
    }

    private boolean consumeEnergyForProcessingTick() {
        if (rjStorage.getStoredRJ() < ENERGY_USAGE_RJ_PER_TICK) {
            currentEnergyUsage = 0;
            return false;
        }
        rjStorage.consumeRJ(ENERGY_USAGE_RJ_PER_TICK);
        currentEnergyUsage = ENERGY_USAGE_RJ_PER_TICK;
        setChanged();
        return true;
    }

    private boolean tryEjectOutputs() {
        if (level == null || outputQueue.isEmpty()) {
            return false;
        }

        boolean ejected = false;
        Direction facing = getFacing();
        Direction outputDirection = facing.getCounterClockWise();
        for (int outputX : OUTPUT_LOCAL_X) {
            if (outputQueue.isEmpty()) {
                break;
            }
            ItemStack next = outputQueue.peekFirst();
            if (tryEjectOutput(next, facing, outputDirection, outputX)) {
                outputQueue.removeFirst();
                ejected = true;
            }
        }
        return ejected;
    }

    private boolean tryEjectOutput(ItemStack stack, Direction facing, Direction outputDirection, int outputX) {
        BlockPos portPos = WireMillBlock.localToWorld(worldPosition, facing, outputX, OUTPUT_LOCAL_Y, OUTPUT_LOCAL_Z);
        BlockPos outputPos = portPos.relative(outputDirection);
        ItemStack single = stack.copyWithCount(1);

        var directRemainder = ConveyorDirectTransfer.tryInsert(level, outputPos, single, outputDirection.getOpposite(), true);
        if (directRemainder.isPresent()) {
            if (!directRemainder.get().isEmpty()) {
                return false;
            }
            ConveyorDirectTransfer.tryInsert(level, outputPos, single, outputDirection.getOpposite(), false);
            return true;
        }

        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, outputDirection.getOpposite());
        if (handler != null) {
            if (!ConveyorInsertionUtil.insertIntoHandler(handler, single, true).isEmpty()) {
                return false;
            }
            ConveyorInsertionUtil.insertIntoHandler(handler, single, false);
            return true;
        }

        BlockState outputState = level.getBlockState(outputPos);
        if (!(outputState.getBlock() instanceof ConveyorBeltSurface surface)) {
            return false;
        }
        if (outputState.getBlock() instanceof ConveyorGateSurface gate
                && !gate.skyent$canConveyorItemEnter(level, outputPos, outputState, outputDirection.getOpposite())) {
            return false;
        }

        Vec3 outputStart = new Vec3(
                outputPos.getX() + 0.5D - outputDirection.getStepX() * 0.45D,
                outputPos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET,
                outputPos.getZ() + 0.5D - outputDirection.getStepZ() * 0.45D
        );
        Vec3 spawnPos = surface.getClosestSnappingPosition(level, outputPos, outputStart);
        if (!hasRoomAt(spawnPos)) {
            return false;
        }

        ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, single);
        level.addFreshEntity(entity);
        return true;
    }

    private List<ConveyorMovingItemEntity> getInputConveyorItems() {
        List<ConveyorMovingItemEntity> items = new ArrayList<>();
        if (level == null) {
            return items;
        }

        BlockPos inputPos = getInputPos(getFacing());
        AABB searchBox = new AABB(inputPos).inflate(0.2D, 0.55D, 0.2D);
        for (ConveyorMovingItemEntity item : level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved())) {
            items.add(item);
        }
        return items;
    }

    private BlockPos getInputPos(Direction facing) {
        return WireMillBlock.localToWorld(worldPosition, facing, INPUT_LOCAL_X, INPUT_LOCAL_Y, INPUT_LOCAL_Z);
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(WireMillBlock.FACING) ? state.getValue(WireMillBlock.FACING) : Direction.NORTH;
    }

    private boolean hasRoomAt(Vec3 position) {
        if (level == null) {
            return false;
        }
        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
    }

    private static boolean isCapturePointReached(ConveyorMovingItemEntity item, Vec3 inputCenter, Direction direction) {
        double forwardDistance = (item.getX() - inputCenter.x) * direction.getStepX()
                + (item.getZ() - inputCenter.z) * direction.getStepZ();
        return item.isBlocked() || forwardDistance >= CAPTURE_FORWARD_DISTANCE;
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    private Vec3 getMachineCenter() {
        Direction facing = getFacing();
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int y = 0; y < WireMillBlock.HEIGHT; y++) {
            for (int x = 0; x < WireMillBlock.LENGTH; x++) {
                for (int z = 0; z < WireMillBlock.WIDTH; z++) {
                    sum = sum.add(WireMillBlock.localToWorld(worldPosition, facing, x, y, z).getCenter());
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
        return "wire_mill_running:" + pos.asLong();
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
                        (BooleanSupplier) () -> level.getBlockEntity(pos) instanceof WireMillBlockEntity wireMill && wireMill.isRunning()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, motorLoopKey(pos), ModSounds.HEAVY_ELECTRIC_MOTOR_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update Wire Mill client loop sound", exception);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag inputEntries = new ListTag();
        for (int i = 0; i < inputQueue.length; i++) {
            if (!inputQueue[i].isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putInt(TAG_SLOT, i);
                entry.put(TAG_ITEM, inputQueue[i].save(registries));
                inputEntries.add(entry);
            }
        }
        tag.put(TAG_INPUT_QUEUE, inputEntries);
        tag.putInt(TAG_PROGRESS, progress);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_CURRENT_ENERGY_USAGE, currentEnergyUsage);
        tag.putBoolean(TAG_RUNNING, running);
        ListTag outputEntries = new ListTag();
        for (ItemStack stack : outputQueue) {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_ITEM, stack.save(registries));
            outputEntries.add(entry);
        }
        tag.put(TAG_OUTPUT_QUEUE, outputEntries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        clearInputQueue();
        if (tag.contains(TAG_INPUT_QUEUE)) {
            ListTag inputEntries = tag.getList(TAG_INPUT_QUEUE, net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < inputEntries.size(); i++) {
                CompoundTag entry = inputEntries.getCompound(i);
                int slot = entry.getInt(TAG_SLOT);
                if (slot >= 0 && slot < inputQueue.length) {
                    ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound(TAG_ITEM));
                    inputQueue[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
                }
            }
        } else if (tag.contains(TAG_PENDING_INPUT)) {
            ItemStack legacyPendingInput = ItemStack.parseOptional(registries, tag.getCompound(TAG_PENDING_INPUT));
            if (!legacyPendingInput.isEmpty()) {
                inputQueue[0] = legacyPendingInput.copyWithCount(1);
            }
        }
        compactInputQueue();
        progress = Math.max(0, tag.getInt(TAG_PROGRESS));
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        currentEnergyUsage = Math.max(0, tag.getInt(TAG_CURRENT_ENERGY_USAGE));
        running = tag.getBoolean(TAG_RUNNING);
        outputQueue.clear();
        ListTag outputEntries = tag.getList(TAG_OUTPUT_QUEUE, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < outputEntries.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, outputEntries.getCompound(i).getCompound(TAG_ITEM));
            if (!stack.isEmpty() && outputQueue.size() < MAX_OUTPUT_QUEUE_SIZE) {
                outputQueue.addLast(stack.copyWithCount(1));
            }
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

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }
}
