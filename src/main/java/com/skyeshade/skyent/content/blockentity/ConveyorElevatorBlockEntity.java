package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.ConveyorElevatorBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorDirectTransfer;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class ConveyorElevatorBlockEntity extends BlockEntity {
    private static final boolean DEBUG_CONVEYOR_ELEVATOR = false;
    private static final int BASE_ELEVATOR_TRAVEL_TICKS = 8;
    private static final int ELEVATOR_TRAVEL_TICKS_PER_BLOCK = 6;
    private static final int ELEVATOR_ENTRIES_PER_BLOCK = 2;
    private static final String TAG_QUEUE = "Queue";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_REMAINING_TICKS = "RemainingTicks";
    private final Deque<QueuedElevatorItem> queue = new ArrayDeque<>();

    public ConveyorElevatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONVEYOR_ELEVATOR.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorElevatorBlockEntity elevator) {
        if (level.isClientSide || !ConveyorElevatorBlock.isInputSegment(state)) {
            return;
        }

        boolean changed = false;
        for (QueuedElevatorItem queued : elevator.queue) {
            if (queued.remainingTicks > 0) {
                queued.remainingTicks--;
                changed = true;
            }
        }

        QueuedElevatorItem front = elevator.queue.peekFirst();
        if (front != null && front.remainingTicks <= 0 && elevator.tryOutput(front.stack)) {
            elevator.queue.removeFirst();
            changed = true;
        }

        if (changed) {
            setChanged(level, pos, state);
        }
    }

    public boolean enqueue(ItemStack stack) {
        if (level == null || stack.isEmpty() || !ConveyorElevatorBlock.isInputSegment(getBlockState())) {
            return false;
        }
        int capacity = capacity();
        if (queue.size() >= capacity) {
            debug("rejected input because full queueSize={} capacity={} stack={}", queue.size(), capacity, stack);
            return false;
        }

        queue.addLast(new QueuedElevatorItem(stack.copy(), travelTicks()));
        debug("accepted entry queueSize={} capacity={} stack={}", queue.size(), capacity, stack);
        setChanged();
        return true;
    }

    public boolean canAcceptEntry() {
        return ConveyorElevatorBlock.isInputSegment(getBlockState()) && queue.size() < capacity();
    }

    public void dropStoredItems() {
        if (level == null || level.isClientSide || queue.isEmpty()) {
            queue.clear();
            return;
        }

        Vec3 dropPos = worldPosition.getCenter();
        for (QueuedElevatorItem queued : queue) {
            ItemEntity itemEntity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, queued.stack.copy());
            itemEntity.setPickUpDelay(10);
            level.addFreshEntity(itemEntity);
        }
        queue.clear();
    }

    private int travelTicks() {
        return BASE_ELEVATOR_TRAVEL_TICKS + Math.max(1, stackHeight()) * ELEVATOR_TRAVEL_TICKS_PER_BLOCK;
    }

    private int stackHeight() {
        if (level == null) {
            return 1;
        }

        int height = 1;
        BlockPos cursor = worldPosition.above();
        while (level.getBlockState(cursor).getBlock() instanceof ConveyorElevatorBlock) {
            height++;
            cursor = cursor.above();
        }
        return height;
    }

    private int capacity() {
        return Math.max(0, stackHeight() * ELEVATOR_ENTRIES_PER_BLOCK);
    }

    private boolean tryOutput(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }

        BlockPos topPos = findTopPos();
        BlockState topState = level.getBlockState(topPos);
        if (!(topState.getBlock() instanceof ConveyorElevatorBlock) || !ConveyorElevatorBlock.isHorizontalOutputSegment(topState)) {
            return false;
        }

        Direction outputFacing = ConveyorElevatorBlock.getFacing(topState);
        BlockPos outputPos = topPos.relative(outputFacing);
        BlockState outputState = level.getBlockState(outputPos);
        var directRemainder = ConveyorDirectTransfer.tryInsert(level, outputPos, stack, outputFacing.getOpposite(), false);
        if (directRemainder.isPresent()) {
            if (directRemainder.get().isEmpty()) {
                debug("output entry directly into conveyor acceptor at {} stack={}", outputPos, stack);
                return true;
            }
            return false;
        }

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, outputFacing.getOpposite());
        if (handler != null) {
            ItemStack remainder = ConveyorInsertionUtil.insertIntoHandler(handler, stack, false);
            if (remainder.isEmpty()) {
                debug("output entry into handler at {} stack={}", outputPos, stack);
                return true;
            }
            stack.setCount(remainder.getCount());
            debug("partial handler output at {} remainder={}", outputPos, stack);
            return false;
        }

        if (outputState.getBlock() instanceof ConveyorBeltSurface surface) {
            if (outputState.getBlock() instanceof ConveyorGateSurface gate
                    && !gate.skyent$canConveyorItemEnter(level, outputPos, outputState, outputFacing.getOpposite())) {
                return false;
            }

            Vec3 outputStart = new Vec3(
                    outputPos.getX() + 0.5D - outputFacing.getStepX() * 0.45D,
                    outputPos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET,
                    outputPos.getZ() + 0.5D - outputFacing.getStepZ() * 0.45D
            );
            Vec3 spawnPos = surface.getClosestSnappingPosition(level, outputPos, outputStart);
            if (!hasRoomAt(spawnPos)) {
                return false;
            }

            ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, spawnPos.x, spawnPos.y, spawnPos.z, stack.copy());
            level.addFreshEntity(entity);
            debug("output entry onto conveyor at {} stack={}", outputPos, stack);
            return true;
        }

        if (outputState.isAir()) {
            Vec3 dropPos = new Vec3(
                    topPos.getX() + 0.5D + outputFacing.getStepX() * 0.65D,
                    topPos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET,
                    topPos.getZ() + 0.5D + outputFacing.getStepZ() * 0.65D
            );
            ItemEntity itemEntity = new ItemEntity(level, dropPos.x, dropPos.y, dropPos.z, stack.copy());
            itemEntity.setDeltaMovement(outputFacing.getStepX() * 0.08D, 0.04D, outputFacing.getStepZ() * 0.08D);
            itemEntity.setPickUpDelay(10);
            level.addFreshEntity(itemEntity);
            debug("output entry into air at {} stack={}", outputPos, stack);
            return true;
        }

        return false;
    }

    private BlockPos findTopPos() {
        if (level == null) {
            return worldPosition;
        }

        BlockPos top = worldPosition;
        while (level.getBlockState(top.above()).getBlock() instanceof ConveyorElevatorBlock) {
            top = top.above();
        }
        return top;
    }

    private boolean hasRoomAt(Vec3 position) {
        if (level == null) {
            return false;
        }

        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
    }

    @Nullable
    public IItemHandler getItemHandler(@Nullable Direction side) {
        if (!ConveyorElevatorBlock.isInputSegment(getBlockState())) {
            return null;
        }
        if (side != null && side != ConveyorElevatorBlock.getFacing(getBlockState())) {
            return null;
        }
        return new ElevatorItemHandler();
    }

    private void debug(String message, Object... args) {
        if (DEBUG_CONVEYOR_ELEVATOR) {
            Object[] combined = new Object[args.length + 1];
            combined[0] = worldPosition;
            System.arraycopy(args, 0, combined, 1, args.length);
            com.skyeshade.skyent.SkyesNuclearTech.LOGGER.info("[ConveyorElevator {}] " + message, combined);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag entries = new ListTag();
        for (QueuedElevatorItem queued : queue) {
            CompoundTag entry = new CompoundTag();
            entry.put(TAG_ITEM, queued.stack.save(registries));
            entry.putInt(TAG_REMAINING_TICKS, queued.remainingTicks);
            entries.add(entry);
        }
        tag.put(TAG_QUEUE, entries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        queue.clear();
        ListTag entries = tag.getList(TAG_QUEUE, CompoundTag.TAG_COMPOUND);
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound(TAG_ITEM));
            if (!stack.isEmpty()) {
                queue.addLast(new QueuedElevatorItem(stack, entry.getInt(TAG_REMAINING_TICKS)));
            }
        }
    }

    private final class ElevatorItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !ConveyorElevatorBlock.isInputSegment(getBlockState())) {
                return stack;
            }
            if (queue.size() >= capacity()) {
                debug("rejected handler input because full queueSize={} capacity={} stack={}", queue.size(), capacity(), stack);
                return stack;
            }
            if (!simulate && !enqueue(stack)) {
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty();
        }
    }

    private static final class QueuedElevatorItem {
        private final ItemStack stack;
        private int remainingTicks;

        private QueuedElevatorItem(ItemStack stack, int remainingTicks) {
            this.stack = stack;
            this.remainingTicks = remainingTicks;
        }
    }
}
