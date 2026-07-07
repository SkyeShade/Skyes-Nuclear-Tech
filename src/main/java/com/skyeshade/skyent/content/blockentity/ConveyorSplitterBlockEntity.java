package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.ConveyorSplitterBlock;
import com.skyeshade.skyent.content.conveyor.ConveyorInsertionUtil;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

public class ConveyorSplitterBlockEntity extends BlockEntity {
    private static final boolean DEBUG_CONVEYOR_SPLITTER = false;
    private static final Direction[] OUTPUT_ORDER = new Direction[] {
            Direction.NORTH,
            Direction.WEST,
            Direction.EAST
    };
    private int nextOutputIndex;

    public ConveyorSplitterBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.CONVEYOR_SPLITTER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ConveyorSplitterBlockEntity splitter) {
        if (level.isClientSide) {
            return;
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

        Direction facing = ConveyorSplitterBlock.getTravelDirection(getBlockState());
        ItemStack original = item.getItemStack();
        debug("routing stack={} facing={} outputs=[front={},left={},right={}]", original, facing, facing, facing.getCounterClockWise(), facing.getClockWise());
        List<OutputTarget> targets = getAvailableTargets(facing, original);
        if (targets.isEmpty()) {
            debug("no available outputs for stack={}", original);
            return false;
        }
        debug("found {} available output(s) for stack={}", targets.size(), original);

        AllocationPlan plan = allocate(original.getCount(), targets);
        if (plan.allocations().isEmpty()) {
            return false;
        }
        debug("allocation result={} nextIndex={}", plan.allocations(), plan.nextOutputIndex());

        if (!canCreateAllOutputs(original, plan.allocations(), targets)) {
            debug("simulated insertion failed for allocation={}", plan.allocations());
            return false;
        }

        for (OutputTarget target : targets) {
            int count = plan.allocations().getOrDefault(target.direction(), 0);
            if (count <= 0) {
                continue;
            }

            ItemStack outputStack = original.copy();
            outputStack.setCount(count);
            ConveyorInsertionUtil.insertIntoHandler(target.handler(), outputStack, false);
            debug("inserted {} item(s) toward {} at {}", count, target.direction(), target.pos());
        }

        nextOutputIndex = plan.nextOutputIndex();
        item.discard();
        setChanged();
        return true;
    }

    private List<OutputTarget> getAvailableTargets(Direction facing, ItemStack original) {
        List<OutputTarget> targets = new ArrayList<>();
        for (int i = 0; i < OUTPUT_ORDER.length; i++) {
            Direction outputDirection = resolveOutputDirection(facing, OUTPUT_ORDER[i]);
            BlockPos outputPos = worldPosition.relative(outputDirection);
            Direction fromDirection = outputDirection.getOpposite();
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, outputPos, fromDirection);
            if (handler == null) {
                debug("output {} target={} insertionSide={} has no item handler", outputDirection, outputPos, fromDirection);
                continue;
            }

            ItemStack probe = original.copy();
            probe.setCount(1);
            ItemStack probeRemainder = ConveyorInsertionUtil.insertIntoHandler(handler, probe, true);
            if (probeRemainder.isEmpty()) {
                debug("output {} target={} insertionSide={} accepted probe", outputDirection, outputPos, fromDirection);
                targets.add(new OutputTarget(i, outputDirection, outputPos, handler));
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
            ItemStack remainder = ConveyorInsertionUtil.insertIntoHandler(target.handler(), stack, true);
            if (!remainder.isEmpty()) {
                debug("output {} at {} cannot accept allocated stack count={} remainder={}", target.direction(), target.pos(), count, remainder.getCount());
                return false;
            }
        }
        return true;
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nextOutputIndex = Math.floorMod(tag.getInt("NextOutputIndex"), OUTPUT_ORDER.length);
    }

    private record OutputTarget(int orderIndex, Direction direction, BlockPos pos, IItemHandler handler) {
    }

    private record AllocationPlan(EnumMap<Direction, Integer> allocations, int nextOutputIndex) {
    }
}
