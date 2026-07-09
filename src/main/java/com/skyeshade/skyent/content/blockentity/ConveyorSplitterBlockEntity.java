package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.ConveyorSplitterBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorDirectTransfer;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class ConveyorSplitterBlockEntity extends BlockEntity {
    private static final boolean DEBUG_CONVEYOR_SPLITTER = false;
    private static final int SPLITTER_TRANSFER_TICKS = 12;
    private static final int SPLITTER_BUFFER_CAPACITY = 3;
    private static final String TAG_QUEUE = "Queue";
    private static final String TAG_ITEM = "Item";
    private static final String TAG_REMAINING_TICKS = "RemainingTicks";
    private static final Direction[] OUTPUT_ORDER = new Direction[] {
            Direction.NORTH,
            Direction.WEST,
            Direction.EAST
    };
    private int nextOutputIndex;
    private final Deque<QueuedSplitterItem> queue = new ArrayDeque<>();

    public ConveyorSplitterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONVEYOR_SPLITTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorSplitterBlockEntity splitter) {
        if (level.isClientSide) {
            return;
        }

        if (splitter.tickQueue()) {
            setChanged(level, pos, state);
        }

        List<ConveyorMovingItemEntity> items = level.getEntitiesOfClass(
                ConveyorMovingItemEntity.class,
                new AABB(pos),
                item -> !item.isRemoved() && ConveyorSplitterBlock.isAtSplitPoint(pos, state, item)
        );
        for (ConveyorMovingItemEntity item : items) {
            item.setBlocked(true);
            splitter.debug("captured item {} stack={}", item.getUUID(), item.getItemStack());
            if (splitter.tryRouteItem(item)) {
                return;
            }
        }
    }

    private boolean tryRouteItem(ConveyorMovingItemEntity item) {
        if (level == null || item.getItemStack().isEmpty()) {
            return false;
        }

        if (!canAcceptBufferedItem()) {
            return false;
        }

        insertConveyorItem(item.getItemStack(), false);
        item.discard();
        return true;
    }

    public ItemStack insertConveyorItem(ItemStack stack, boolean simulate) {
        if (level == null || stack.isEmpty()) {
            return stack;
        }

        if (!canAcceptBufferedItem()) {
            return stack;
        }

        if (!simulate) {
            queue.addLast(new QueuedSplitterItem(stack.copy(), SPLITTER_TRANSFER_TICKS));
            setChanged();
        }

        return ItemStack.EMPTY;
    }

    public void dropBufferedItems() {
        if (level == null || level.isClientSide || queue.isEmpty()) {
            queue.clear();
            return;
        }

        for (QueuedSplitterItem queued : queue) {
            ItemEntity itemEntity = new ItemEntity(level, worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, queued.stack.copy());
            itemEntity.setPickUpDelay(10);
            level.addFreshEntity(itemEntity);
        }
        queue.clear();
    }

    private boolean canAcceptBufferedItem() {
        return queue.size() < SPLITTER_BUFFER_CAPACITY;
    }

    private boolean tickQueue() {
        boolean changed = false;
        for (QueuedSplitterItem queued : queue) {
            if (queued.remainingTicks > 0) {
                queued.remainingTicks--;
                changed = true;
            }
        }

        QueuedSplitterItem front = queue.peekFirst();
        if (front != null && front.remainingTicks <= 0 && canRouteStack(front.stack)) {
            routeStack(front.stack);
            queue.removeFirst();
            changed = true;
        }
        return changed;
    }

    private boolean canRouteStack(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }

        Direction facing = ConveyorSplitterBlock.getTravelDirection(getBlockState());
        debug("routing stack={} facing={} outputs=[front={},left={},right={}]", stack, facing, facing, facing.getCounterClockWise(), facing.getClockWise());
        List<OutputTarget> targets = getAvailableTargets(facing, stack);
        if (targets.isEmpty()) {
            debug("no available outputs for stack={}", stack);
            return false;
        }
        debug("found {} available output(s) for stack={}", targets.size(), stack);

        AllocationPlan plan = allocate(stack.getCount(), targets);
        if (plan.allocations().isEmpty()) {
            return false;
        }
        debug("allocation result={} nextIndex={}", plan.allocations(), plan.nextOutputIndex());

        if (!canCreateAllOutputs(stack, plan.allocations(), targets)) {
            debug("simulated insertion failed for allocation={}", plan.allocations());
            return false;
        }
        return true;
    }

    private void routeStack(ItemStack original) {
        Direction facing = ConveyorSplitterBlock.getTravelDirection(getBlockState());
        List<OutputTarget> targets = getAvailableTargets(facing, original);
        AllocationPlan plan = allocate(original.getCount(), targets);
        if (plan.allocations().isEmpty() || !canCreateAllOutputs(original, plan.allocations(), targets)) {
            return;
        }

        for (OutputTarget target : targets) {
            int count = plan.allocations().getOrDefault(target.direction(), 0);
            if (count <= 0) {
                continue;
            }

            ItemStack outputStack = original.copy();
            outputStack.setCount(count);
            insertIntoTarget(target, outputStack, false);
            debug("inserted {} item(s) toward {} at {}", count, target.direction(), target.pos());
        }

        nextOutputIndex = plan.nextOutputIndex();
        setChanged();
    }

    private List<OutputTarget> getAvailableTargets(Direction facing, ItemStack original) {
        List<OutputTarget> targets = new ArrayList<>();
        for (int i = 0; i < OUTPUT_ORDER.length; i++) {
            Direction outputDirection = resolveOutputDirection(facing, OUTPUT_ORDER[i]);
            BlockPos outputPos = worldPosition.relative(outputDirection);
            Direction fromDirection = outputDirection.getOpposite();
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, fromDirection);
            boolean hasDirectAcceptor = level.getBlockState(outputPos).getBlock() instanceof com.skyeshade.skyent.content.conveyor.ConveyorItemAcceptor;
            if (!hasDirectAcceptor && handler == null) {
                debug("output {} target={} insertionSide={} has no item acceptor", outputDirection, outputPos, fromDirection);
                continue;
            }

            ItemStack probe = original.copy();
            probe.setCount(1);
            ItemStack probeRemainder = insertIntoTarget(new OutputTarget(i, outputDirection, outputPos, fromDirection, handler), probe, true);
            if (probeRemainder.isEmpty()) {
                debug("output {} target={} insertionSide={} accepted probe", outputDirection, outputPos, fromDirection);
                targets.add(new OutputTarget(i, outputDirection, outputPos, fromDirection, handler));
            } else {
                debug("output {} target={} insertionSide={} rejected single-item probe remainder={}", outputDirection, outputPos, fromDirection, probeRemainder.getCount());
            }
        }
        return targets;
    }

    private AllocationPlan allocate(int count, List<OutputTarget> targets) {
        EnumMap<Direction, Integer> allocations = new EnumMap<>(Direction.class);
        int cursor = nextOutputIndex;
        for (int item = 0; item < count; item++) {
            OutputTarget target = findNextTarget(targets, cursor);
            if (target == null) {
                return new AllocationPlan(new EnumMap<>(Direction.class), nextOutputIndex);
            }

            allocations.merge(target.direction(), 1, Integer::sum);
            cursor = (target.orderIndex() + 1) % OUTPUT_ORDER.length;
        }

        return new AllocationPlan(allocations, cursor);
    }

    @Nullable
    private static OutputTarget findNextTarget(List<OutputTarget> targets, int cursor) {
        for (int offset = 0; offset < OUTPUT_ORDER.length; offset++) {
            int index = (cursor + offset) % OUTPUT_ORDER.length;
            for (OutputTarget target : targets) {
                if (target.orderIndex() == index) {
                    return target;
                }
            }
        }
        return null;
    }

    private boolean canCreateAllOutputs(ItemStack original, Map<Direction, Integer> allocations, List<OutputTarget> targets) {
        for (OutputTarget target : targets) {
            int count = allocations.getOrDefault(target.direction(), 0);
            if (count <= 0) {
                continue;
            }

            ItemStack stack = original.copy();
            stack.setCount(count);
            ItemStack remainder = insertIntoTarget(target, stack, true);
            if (!remainder.isEmpty()) {
                debug("output {} at {} cannot accept allocated stack count={} remainder={}", target.direction(), target.pos(), count, remainder.getCount());
                return false;
            }
        }
        return true;
    }

    private ItemStack insertIntoTarget(OutputTarget target, ItemStack stack, boolean simulate) {
        if (level == null) {
            return stack;
        }

        var directRemainder = ConveyorDirectTransfer.tryInsert(level, target.pos(), stack, target.fromDirection(), simulate);
        if (directRemainder.isPresent()) {
            return directRemainder.get();
        }

        return target.handler() == null ? stack : ConveyorInsertionUtil.insertIntoHandler(target.handler(), stack, simulate);
    }

    private static Direction resolveOutputDirection(Direction facing, Direction localDirection) {
        if (localDirection == Direction.NORTH) {
            return facing;
        }
        if (localDirection == Direction.WEST) {
            return facing.getCounterClockWise();
        }
        if (localDirection == Direction.EAST) {
            return facing.getClockWise();
        }
        return facing;
    }

    private void debug(String message, Object... args) {
        if (DEBUG_CONVEYOR_SPLITTER) {
            Object[] combined = new Object[args.length + 1];
            combined[0] = worldPosition;
            System.arraycopy(args, 0, combined, 1, args.length);
            SkyesNuclearTech.LOGGER.info("[ConveyorSplitter {}] " + message, combined);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("NextOutputIndex", nextOutputIndex);
        ListTag entries = new ListTag();
        for (QueuedSplitterItem queued : queue) {
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
        nextOutputIndex = Math.floorMod(tag.getInt("NextOutputIndex"), OUTPUT_ORDER.length);
        queue.clear();
        ListTag entries = tag.getList(TAG_QUEUE, CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < entries.size() && queue.size() < SPLITTER_BUFFER_CAPACITY; index++) {
            CompoundTag entry = entries.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound(TAG_ITEM));
            if (!stack.isEmpty()) {
                queue.addLast(new QueuedSplitterItem(stack, Math.max(0, entry.getInt(TAG_REMAINING_TICKS))));
            }
        }
    }

    private record OutputTarget(int orderIndex, Direction direction, BlockPos pos, Direction fromDirection, @Nullable IItemHandler handler) {
    }

    private record AllocationPlan(EnumMap<Direction, Integer> allocations, int nextOutputIndex) {
    }

    private static final class QueuedSplitterItem {
        private final ItemStack stack;
        private int remainingTicks;

        private QueuedSplitterItem(ItemStack stack, int remainingTicks) {
            this.stack = stack;
            this.remainingTicks = remainingTicks;
        }
    }
}
