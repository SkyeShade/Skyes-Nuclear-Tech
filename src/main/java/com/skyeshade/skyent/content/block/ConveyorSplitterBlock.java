package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.ConveyorSplitterBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorItemAcceptor;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ConveyorSplitterBlock extends BaseEntityBlock implements ConveyorBeltSurface, ConveyorGateSurface, ConveyorTravelDirectionProvider, ConveyorVisualFeeder, ConveyorItemAcceptor {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<ConveyorSplitterBlock> CODEC = simpleCodec(ConveyorSplitterBlock::new);
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    private static final double HOLD_FORWARD_DISTANCE = -0.28D;
    private static final double INPUT_LANE_RADIUS = 0.18D;

    public ConveyorSplitterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ConveyorSplitterBlockEntity splitter) {
            splitter.dropBufferedItems();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConveyorSplitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null : createTickerHelper(blockEntityType, ModBlockEntities.CONVEYOR_SPLITTER.get(), ConveyorSplitterBlockEntity::serverTick);
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        return state.is(this) && isInInputLane(pos, state, itemPos);
    }

    @Override
    public Vec3 getTravelLocation(Level level, BlockPos pos, Vec3 itemPos, double speed) {
        Direction direction = getTravelDirection(level.getBlockState(pos));
        Vec3 hold = getHoldPosition(pos, direction);
        Vec3 snap = getClosestSnappingPosition(level, pos, itemPos);
        Vec3 destination = new Vec3(hold.x, snap.y, hold.z);
        Vec3 motion = destination.subtract(itemPos);
        if (motion.lengthSqr() <= 1.0E-6D) {
            return itemPos;
        }

        return itemPos.add(motion.normalize().scale(Math.min(speed, motion.length())));
    }

    @Override
    public Vec3 getClosestSnappingPosition(Level level, BlockPos pos, Vec3 itemPos) {
        Direction direction = getTravelDirection(level.getBlockState(pos));
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
        return getTravelDirection(state);
    }

    @Override
    public boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        return fromDirection == getTravelDirection(state).getOpposite();
    }

    @Override
    public ItemStack insertConveyorItem(Level level, BlockPos pos, BlockState state, ItemStack stack, Direction fromDirection, boolean simulate) {
        if (level.isClientSide || stack.isEmpty() || fromDirection != getTravelDirection(state).getOpposite()) {
            return stack;
        }

        if (level.getBlockEntity(pos) instanceof ConveyorSplitterBlockEntity splitter) {
            return splitter.insertConveyorItem(stack, simulate);
        }

        return stack;
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        return false;
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        Direction facing = getTravelDirection(state);
        return direction == facing
                || direction == facing.getCounterClockWise()
                || direction == facing.getClockWise();
    }

    @Nullable
    @Override
    public Vec3 skyent$getConveyorHoldPosition(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        return getHoldPosition(pos, getTravelDirection(state));
    }

    public static boolean isAtSplitPoint(BlockPos pos, BlockState state, ConveyorMovingItemEntity item) {
        Direction direction = getTravelDirection(state);
        Vec3 center = pos.getCenter();
        double forwardDistance = (item.getX() - center.x) * direction.getStepX()
                + (item.getZ() - center.z) * direction.getStepZ();
        return isInInputLane(pos, state, item.position())
                && (forwardDistance >= HOLD_FORWARD_DISTANCE - 0.03D || item.isBlocked());
    }

    public static Vec3 getHoldPosition(BlockPos pos, Direction direction) {
        return new Vec3(
                pos.getX() + 0.5D + direction.getStepX() * HOLD_FORWARD_DISTANCE,
                pos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET,
                pos.getZ() + 0.5D + direction.getStepZ() * HOLD_FORWARD_DISTANCE
        );
    }

    public static Direction getTravelDirection(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
    }

    private static boolean isInInputLane(BlockPos pos, BlockState state, Vec3 itemPos) {
        Direction direction = getTravelDirection(state);
        Vec3 center = pos.getCenter();
        double lateralDistance = direction.getAxis() == Direction.Axis.X
                ? Math.abs(itemPos.z - center.z)
                : Math.abs(itemPos.x - center.x);
        if (lateralDistance > INPUT_LANE_RADIUS) {
            return false;
        }

        double forwardDistance = (itemPos.x - center.x) * direction.getStepX()
                + (itemPos.z - center.z) * direction.getStepZ();
        return forwardDistance <= HOLD_FORWARD_DISTANCE + 0.18D || forwardDistance <= 0.0D;
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
        builder.add(FACING);
    }
}
