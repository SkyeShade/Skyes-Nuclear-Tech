package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.ConveyorElevatorBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;

public class ConveyorElevatorBlock extends BaseEntityBlock implements ConveyorBeltSurface, ConveyorGateSurface, ConveyorTravelDirectionProvider, ConveyorVisualFeeder {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<ConveyorElevatorSegment> SEGMENT = EnumProperty.create("segment", ConveyorElevatorSegment.class);
    public static final MapCodec<ConveyorElevatorBlock> CODEC = simpleCodec(ConveyorElevatorBlock::new);
    private static final VoxelShape SHAPE = Shapes.block();

    public ConveyorElevatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SEGMENT, ConveyorElevatorSegment.TOP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock())) {
            recomputeStack(level, pos);
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!newState.is(state.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ConveyorElevatorBlockEntity elevator) {
                elevator.dropStoredItems();
            }
            recomputeStack(level, pos.below());
            recomputeStack(level, pos.above());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (fromPos.getX() == pos.getX() && fromPos.getZ() == pos.getZ()) {
            recomputeStack(level, pos);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ItemEntity itemEntity) || itemEntity.tickCount <= 10 || itemEntity.isRemoved()) {
            return;
        }
        if (!isInputSegment(state) || itemEntity.getItem().isEmpty()) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof ConveyorElevatorBlockEntity elevator && elevator.enqueue(itemEntity.getItem())) {
            itemEntity.discard();
        }
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) {
            return false;
        }
        return isInputSegment(state) || isHorizontalOutputSegment(state);
    }

    @Override
    public Vec3 getTravelLocation(Level level, BlockPos pos, Vec3 itemPos, double speed) {
        BlockState state = level.getBlockState(pos);
        if (isHorizontalOutputSegment(state)) {
            Direction direction = getFacing(state);
            Vec3 snap = getClosestSnappingPosition(level, pos, itemPos);
            Vec3 destination = snap.add(direction.getStepX() * 0.5D, 0.0D, direction.getStepZ() * 0.5D);
            return moveToward(itemPos, destination, speed);
        }

        return itemPos;
    }

    @Override
    public Vec3 getClosestSnappingPosition(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        if (!isHorizontalOutputSegment(state)) {
            return new Vec3(pos.getX() + 0.5D, pos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET, pos.getZ() + 0.5D);
        }

        Direction direction = getFacing(state);
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET;
        double z = pos.getZ() + 0.5D;
        if (direction.getAxis() == Direction.Axis.X) {
            x = Mth.clamp(itemPos.x, pos.getX(), pos.getX() + 1.0D);
            z = Mth.lerp(BasicConveyorBeltBlock.ITEM_CENTER_PULL, itemPos.z, z);
        } else {
            x = Mth.lerp(BasicConveyorBeltBlock.ITEM_CENTER_PULL, itemPos.x, x);
            z = Mth.clamp(itemPos.z, pos.getZ(), pos.getZ() + 1.0D);
        }
        return new Vec3(x, y, z);
    }

    @Nullable
    @Override
    public Direction skyent$getConveyorTravelDirection(Level level, BlockPos pos, BlockState state) {
        return getFacing(state);
    }

    @Override
    public boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        return isInputSegment(state) && fromDirection == getFacing(state);
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        return isHorizontalOutputSegment(state) && outputDirection == getFacing(state);
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        return isHorizontalOutputSegment(state) && getFacing(state) == direction;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SEGMENT);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorElevatorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, ModBlockEntities.CONVEYOR_ELEVATOR.get(), ConveyorElevatorBlockEntity::serverTick);
    }

    @Nullable
    public static IItemHandler getItemHandler(LevelAccessor level, BlockPos pos, BlockState state, @Nullable Direction side) {
        if (!(level instanceof Level realLevel) || !(state.getBlock() instanceof ConveyorElevatorBlock) || !isInputSegment(state)) {
            return null;
        }
        return realLevel.getBlockEntity(pos) instanceof ConveyorElevatorBlockEntity elevator ? elevator.getItemHandler(side) : null;
    }

    public static boolean isInputSegment(BlockState state) {
        return state.hasProperty(SEGMENT) && state.getValue(SEGMENT) == ConveyorElevatorSegment.BOTTOM;
    }

    public static boolean isHorizontalOutputSegment(BlockState state) {
        return state.hasProperty(SEGMENT) && state.getValue(SEGMENT) == ConveyorElevatorSegment.TOP;
    }

    public static boolean isVerticalTravelSegment(BlockState state) {
        return state.getBlock() instanceof ConveyorElevatorBlock && !isHorizontalOutputSegment(state);
    }

    public static boolean canAcceptHorizontalInput(BlockState state, Direction fromDirection) {
        return state.getBlock() instanceof ConveyorElevatorBlock && isInputSegment(state) && fromDirection == getFacing(state);
    }

    public static boolean canAcceptHorizontalInput(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        return canAcceptHorizontalInput(state, fromDirection)
                && level.getBlockEntity(pos) instanceof ConveyorElevatorBlockEntity elevator
                && elevator.canAcceptEntry();
    }

    public static boolean tryCaptureMovingItem(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item) {
        if (level.isClientSide || !isInputSegment(state) || item.getItemStack().isEmpty()) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof ConveyorElevatorBlockEntity elevator && elevator.enqueue(item.getItemStack())) {
            item.discard();
            return true;
        }
        return false;
    }

    public static Direction getFacing(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
    }

    private static void recomputeStack(LevelAccessor level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof ConveyorElevatorBlock)) {
            return;
        }

        BlockPos lowest = pos;
        while (level.getBlockState(lowest.below()).getBlock() instanceof ConveyorElevatorBlock) {
            lowest = lowest.below();
        }

        BlockPos highest = pos;
        while (level.getBlockState(highest.above()).getBlock() instanceof ConveyorElevatorBlock) {
            highest = highest.above();
        }

        for (BlockPos cursor = lowest; cursor.getY() <= highest.getY(); cursor = cursor.above()) {
            BlockState current = level.getBlockState(cursor);
            if (!(current.getBlock() instanceof ConveyorElevatorBlock)) {
                continue;
            }

            ConveyorElevatorSegment segment;
            if (lowest.equals(highest) || cursor.equals(highest)) {
                segment = ConveyorElevatorSegment.TOP;
            } else if (cursor.equals(lowest)) {
                segment = ConveyorElevatorSegment.BOTTOM;
            } else {
                segment = ConveyorElevatorSegment.MIDDLE;
            }

            BlockState updated = current.setValue(SEGMENT, segment);
            if (!updated.equals(current)) {
                if (current.getValue(SEGMENT) == ConveyorElevatorSegment.BOTTOM
                        && segment != ConveyorElevatorSegment.BOTTOM
                        && level.getBlockEntity(cursor) instanceof ConveyorElevatorBlockEntity elevator) {
                    elevator.dropStoredItems();
                }
                level.setBlock(cursor, updated, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static Vec3 moveToward(Vec3 position, Vec3 destination, double speed) {
        Vec3 motion = destination.subtract(position);
        if (motion.lengthSqr() <= 1.0E-6D) {
            return position;
        }
        return position.add(motion.normalize().scale(Math.min(speed, motion.length())));
    }
}
