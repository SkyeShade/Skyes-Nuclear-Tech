package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.BasicConveyorBeltBlock;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

public class BasicConveyorBeltBlockEntity extends BlockEntity {
    private static final double INSERT_BACK_OFFSET = 0.45D;
    private final IItemHandler itemHandler = new BeltItemHandler();

    public BasicConveyorBeltBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.BASIC_CONVEYOR_BELT.get(), pos, blockState);
    }

    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    private Direction getFacing() {
        BlockState state = getBlockState();
        return state.hasProperty(BasicConveyorBeltBlock.FACING) ? state.getValue(BasicConveyorBeltBlock.FACING) : Direction.NORTH;
    }

    private Vec3 getInsertionPosition() {
        Direction facing = getFacing();
        return new Vec3(
                worldPosition.getX() + 0.5D - facing.getStepX() * INSERT_BACK_OFFSET,
                worldPosition.getY() + BasicConveyorBeltBlock.BELT_ITEM_Y,
                worldPosition.getZ() + 0.5D - facing.getStepZ() * INSERT_BACK_OFFSET
        );
    }

    private boolean hasRoomAt(Vec3 position) {
        if (level == null) {
            return false;
        }

        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
    }

    private boolean spawnMovingItem(ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) {
            return false;
        }

        Vec3 position = getInsertionPosition();
        if (!hasRoomAt(position)) {
            return false;
        }

        ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, position.x, position.y, position.z, stack.copy());
        level.addFreshEntity(entity);
        return true;
    }

    private final class BeltItemHandler implements IItemHandler {
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
            if (slot != 0 || stack.isEmpty()) {
                return stack;
            }

            Vec3 position = getInsertionPosition();
            if (!hasRoomAt(position)) {
                return stack;
            }

            if (!simulate && !spawnMovingItem(stack)) {
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
}
