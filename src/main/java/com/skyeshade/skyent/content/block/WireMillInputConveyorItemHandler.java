package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.content.blockentity.WireMillBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.recipe.WireMillRecipes;
import com.skyeshade.skyent.registry.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

final class WireMillInputConveyorItemHandler implements IItemHandler {
    private static final double INSERT_BACK_OFFSET = 0.45D;
    private static final double INSERT_SIDE_OFFSET = 0.45D;

    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    @Nullable
    private final Direction insertionSide;

    WireMillInputConveyorItemHandler(Level level, BlockPos pos, BlockState state, @Nullable Direction insertionSide) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.insertionSide = insertionSide;
    }

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
        if (slot != 0 || stack.isEmpty() || !WireMillRecipes.isWireInput(stack)) {
            return stack;
        }
        Direction direction = getDirection();
        if (insertionSide != null && insertionSide == direction) {
            return stack;
        }
        WireMillBlockEntity wireMill = getController();
        if (wireMill == null || !wireMill.canInputConveyorAccept()) {
            return stack;
        }
        int acceptedCount = Math.min(stack.getCount(), wireMill.getFreeInputQueueSlots());
        if (acceptedCount <= 0) {
            return stack;
        }

        Vec3 position = getInsertionPosition(direction);
        if (!hasRoomAt(position)) {
            return stack;
        }

        if (!simulate) {
            ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, position.x, position.y, position.z, stack.copyWithCount(acceptedCount));
            level.addFreshEntity(entity);
        }
        if (acceptedCount >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack.copy();
        remainder.shrink(acceptedCount);
        return remainder;
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
        return slot == 0 && WireMillRecipes.isWireInput(stack);
    }

    private Direction getDirection() {
        return state.hasProperty(WireMillPartBlock.FACING) ? state.getValue(WireMillPartBlock.FACING) : Direction.NORTH;
    }

    private Vec3 getInsertionPosition(Direction direction) {
        Direction side = insertionSide == null ? direction.getOpposite() : insertionSide;
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET;
        double z = pos.getZ() + 0.5D;
        if (side == direction.getOpposite()) {
            x -= direction.getStepX() * INSERT_BACK_OFFSET;
            z -= direction.getStepZ() * INSERT_BACK_OFFSET;
        } else if (side == direction.getClockWise() || side == direction.getCounterClockWise()) {
            x += side.getStepX() * INSERT_SIDE_OFFSET;
            z += side.getStepZ() * INSERT_SIDE_OFFSET;
        }
        return new Vec3(x, y, z);
    }

    private boolean hasRoomAt(Vec3 position) {
        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
    }

    @Nullable
    private WireMillBlockEntity getController() {
        BlockPos masterPos = WireMillBlock.getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof WireMillBlockEntity wireMill
                && level.getBlockState(masterPos).is(ModBlocks.WIRE_MILL.get())
                ? wireMill
                : null;
    }
}
