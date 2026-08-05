package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.BasicConveyorBeltBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BasicConveyorBeltBlock extends BaseEntityBlock implements ConveyorBeltSurface {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty BACK_CONNECTED = BooleanProperty.create("back_connected");
    public static final BooleanProperty LEFT_CONNECTED = BooleanProperty.create("left_connected");
    public static final BooleanProperty RIGHT_CONNECTED = BooleanProperty.create("right_connected");
    public static final MapCodec<BasicConveyorBeltBlock> CODEC = simpleCodec(BasicConveyorBeltBlock::new);
    public static final double ITEM_CENTER_PULL = 4.0D;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);
    private final double speedMultiplier;

    public BasicConveyorBeltBlock(BlockBehaviour.Properties properties) {
        this(properties, 1.0D);
    }

    public BasicConveyorBeltBlock(BlockBehaviour.Properties properties, double speedMultiplier) {
        super(properties);
        this.speedMultiplier = speedMultiplier;
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BACK_CONNECTED, false)
                .setValue(LEFT_CONNECTED, false)
                .setValue(RIGHT_CONNECTED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        return updateConnections(state, context.getLevel(), context.getClickedPos());
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
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ItemEntity itemEntity) || itemEntity.tickCount <= 10 || itemEntity.isRemoved()) {
            return;
        }

        if (itemEntity.getItem().isEmpty() || !hasRoomAt(level, itemEntity.position())) {
            return;
        }

        Vec3 snap = getClosestSnappingPosition(level, pos, itemEntity.position());
        ConveyorMovingItemEntity movingItem = new ConveyorMovingItemEntity(level, snap.x, snap.y, snap.z, itemEntity.getItem().copy());
        level.addFreshEntity(movingItem);
        itemEntity.discard();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BasicConveyorBeltBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return updateConnections(state, level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockState updatedState = updateConnections(state, level, pos);
        if (!updatedState.equals(state)) {
            level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        return level.getBlockState(pos).is(this);
    }

    @Override
    public Vec3 getTravelLocation(Level level, BlockPos pos, Vec3 itemPos, double speed) {
        Direction direction = getTravelDirection(level.getBlockState(pos));
        Vec3 snap = getClosestSnappingPosition(level, pos, itemPos);
        Vec3 destination = snap.add(direction.getStepX() * 0.5D, 0.0D, direction.getStepZ() * 0.5D);
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
            z = Mth.lerp(ITEM_CENTER_PULL, itemPos.z, z);
        } else {
            x = Mth.lerp(ITEM_CENTER_PULL, itemPos.x, x);
            z = Mth.clamp(itemPos.z, pos.getZ(), pos.getZ() + 1.0D);
        }

        return new Vec3(x, y, z);
    }

    @Override
    public double speedMultiplier(Level level, BlockPos pos) {
        return speedMultiplier;
    }

    private static Direction getTravelDirection(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
    }

    private static boolean hasRoomAt(Level level, Vec3 position) {
        AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
        return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, movingItem -> !movingItem.isRemoved()).isEmpty();
    }

    private BlockState updateConnections(BlockState state, LevelAccessor level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        Direction back = facing.getOpposite();
        Direction left = facing.getCounterClockWise();
        Direction right = facing.getClockWise();

        return state
                .setValue(BACK_CONNECTED, isFeedingConveyor(level, pos.relative(back), facing))
                .setValue(LEFT_CONNECTED, isFeedingConveyor(level, pos.relative(left), right))
                .setValue(RIGHT_CONNECTED, isFeedingConveyor(level, pos.relative(right), left));
    }

    private static boolean isFeedingConveyor(LevelAccessor level, BlockPos neighborPos, Direction requiredFacing) {
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.getBlock() instanceof ConveyorVisualFeeder feeder) {
            return feeder.skyent$feedsConveyorToward(neighborState, requiredFacing);
        }

        return neighborState.getBlock() instanceof ConveyorBeltSurface
                && neighborState.hasProperty(FACING)
                && neighborState.getValue(FACING) == requiredFacing;
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
        builder.add(FACING, BACK_CONNECTED, LEFT_CONNECTED, RIGHT_CONNECTED);
    }
}
