package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.event.systems.LVElectricalNetworkSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LVConnectorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final MapCodec<LVConnectorBlock> CODEC = simpleCodec(LVConnectorBlock::new);

    private static final VoxelShape UP_SHAPE = Shapes.or(
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.0D, 11.0D),
            Block.box(6.0D, 2.0D, 6.0D, 10.0D, 3.0D, 10.0D),
            Block.box(5.0D, 3.0D, 5.0D, 11.0D, 5.0D, 11.0D),
            Block.box(6.0D, 5.0D, 6.0D, 10.0D, 6.0D, 10.0D),
            Block.box(6.5D, 6.0D, 6.5D, 9.5D, 7.0D, 9.5D)
    );
    private static final VoxelShape DOWN_SHAPE = Shapes.or(
            Block.box(5.0D, 14.0D, 5.0D, 11.0D, 16.0D, 11.0D),
            Block.box(6.0D, 13.0D, 6.0D, 10.0D, 14.0D, 10.0D),
            Block.box(5.0D, 11.0D, 5.0D, 11.0D, 13.0D, 11.0D),
            Block.box(6.0D, 10.0D, 6.0D, 10.0D, 11.0D, 10.0D),
            Block.box(6.5D, 9.0D, 6.5D, 9.5D, 10.0D, 9.5D)
    );
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(5.0D, 5.0D, 14.0D, 11.0D, 11.0D, 16.0D),
            Block.box(6.0D, 6.0D, 13.0D, 10.0D, 10.0D, 14.0D),
            Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 13.0D),
            Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 11.0D),
            Block.box(6.5D, 6.5D, 9.0D, 9.5D, 9.5D, 10.0D)
    );
    private static final VoxelShape SOUTH_SHAPE = Shapes.or(
            Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 2.0D),
            Block.box(6.0D, 6.0D, 2.0D, 10.0D, 10.0D, 3.0D),
            Block.box(5.0D, 5.0D, 3.0D, 11.0D, 11.0D, 5.0D),
            Block.box(6.0D, 6.0D, 5.0D, 10.0D, 10.0D, 6.0D),
            Block.box(6.5D, 6.5D, 6.0D, 9.5D, 9.5D, 7.0D)
    );
    private static final VoxelShape EAST_SHAPE = Shapes.or(
            Block.box(0.0D, 5.0D, 5.0D, 2.0D, 11.0D, 11.0D),
            Block.box(2.0D, 6.0D, 6.0D, 3.0D, 10.0D, 10.0D),
            Block.box(3.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D),
            Block.box(5.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D),
            Block.box(6.0D, 6.5D, 6.5D, 7.0D, 9.5D, 9.5D)
    );
    private static final VoxelShape WEST_SHAPE = Shapes.or(
            Block.box(14.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D),
            Block.box(13.0D, 6.0D, 6.0D, 14.0D, 10.0D, 10.0D),
            Block.box(11.0D, 5.0D, 5.0D, 13.0D, 11.0D, 11.0D),
            Block.box(10.0D, 6.0D, 6.0D, 11.0D, 10.0D, 10.0D),
            Block.box(9.0D, 6.5D, 6.5D, 10.0D, 9.5D, 9.5D)
    );
    private static final VoxelShape MV_UP_SHAPE = Shapes.or(
            Block.box(5.0D, 0.0D, 5.0D, 11.0D, 2.0D, 11.0D),
            Block.box(6.0D, 2.0D, 6.0D, 10.0D, 8.0D, 10.0D),
            Block.box(5.0D, 3.0D, 5.0D, 11.0D, 5.0D, 11.0D),
            Block.box(5.0D, 6.0D, 5.0D, 11.0D, 7.0D, 11.0D),
            Block.box(6.5D, 8.0D, 6.5D, 9.5D, 9.0D, 9.5D)
    );
    private static final VoxelShape MV_DOWN_SHAPE = Shapes.or(
            Block.box(5.0D, 14.0D, 5.0D, 11.0D, 16.0D, 11.0D),
            Block.box(6.0D, 8.0D, 6.0D, 10.0D, 14.0D, 10.0D),
            Block.box(5.0D, 11.0D, 5.0D, 11.0D, 13.0D, 11.0D),
            Block.box(5.0D, 9.0D, 5.0D, 11.0D, 10.0D, 11.0D),
            Block.box(6.5D, 7.0D, 6.5D, 9.5D, 8.0D, 9.5D)
    );
    private static final VoxelShape MV_NORTH_SHAPE = Shapes.or(
            Block.box(5.0D, 5.0D, 14.0D, 11.0D, 11.0D, 16.0D),
            Block.box(6.0D, 6.0D, 8.0D, 10.0D, 10.0D, 14.0D),
            Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 13.0D),
            Block.box(5.0D, 5.0D, 9.0D, 11.0D, 11.0D, 10.0D),
            Block.box(6.5D, 6.5D, 7.0D, 9.5D, 9.5D, 8.0D)
    );
    private static final VoxelShape MV_SOUTH_SHAPE = Shapes.or(
            Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 2.0D),
            Block.box(6.0D, 6.0D, 2.0D, 10.0D, 10.0D, 8.0D),
            Block.box(5.0D, 5.0D, 3.0D, 11.0D, 11.0D, 5.0D),
            Block.box(5.0D, 5.0D, 6.0D, 11.0D, 11.0D, 7.0D),
            Block.box(6.5D, 6.5D, 8.0D, 9.5D, 9.5D, 9.0D)
    );
    private static final VoxelShape MV_EAST_SHAPE = Shapes.or(
            Block.box(0.0D, 5.0D, 5.0D, 2.0D, 11.0D, 11.0D),
            Block.box(2.0D, 6.0D, 6.0D, 8.0D, 10.0D, 10.0D),
            Block.box(3.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D),
            Block.box(6.0D, 5.0D, 5.0D, 7.0D, 11.0D, 11.0D),
            Block.box(8.0D, 6.5D, 6.5D, 9.0D, 9.5D, 9.5D)
    );
    private static final VoxelShape MV_WEST_SHAPE = Shapes.or(
            Block.box(14.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D),
            Block.box(8.0D, 6.0D, 6.0D, 14.0D, 10.0D, 10.0D),
            Block.box(11.0D, 5.0D, 5.0D, 13.0D, 11.0D, 11.0D),
            Block.box(9.0D, 5.0D, 5.0D, 10.0D, 11.0D, 11.0D),
            Block.box(7.0D, 6.5D, 6.5D, 8.0D, 9.5D, 9.5D)
    );

    public LVConnectorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getClickedFace());
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos supportPos = pos.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(supportPos);
        if (supportState.is(ModBlocks.MV_INLINE_PUMP.get())) {
            return MVInlinePumpBlock.isValidEnergyConnection(supportState, facing);
        }

        return supportState.isFaceSturdy(level, supportPos, facing)
                || LVMVTransformerBlock.isConnectorSupportCell(supportState)
                || HeatingChamberBlock.isConnectorSupportCell(supportState)
                || IndustrialPressBlock.isConnectorSupportCell(supportState)
                || RollingMillBlock.isConnectorSupportCell(level, supportState, supportPos, facing)
                || WireMillBlock.isConnectorSupportCell(supportState);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !canSurvive(state, level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }

        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && !canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LVConnectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }

        // MV connectors currently share the LV connector transport/network implementation.
        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.LV_CONNECTOR.get(),
                (tickerLevel, pos, tickerState, connector) -> LVElectricalNetworkSystem.onConnectorTick(connector)
        );
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LVConnectorBlockEntity connector) {
            connector.removeAllConnections();
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape shapeFor(BlockState state) {
        Direction facing = state.getValue(FACING);
        if (state.is(ModBlocks.MV_CONNECTOR.get())) {
            return switch (facing) {
                case DOWN -> MV_DOWN_SHAPE;
                case NORTH -> MV_NORTH_SHAPE;
                case SOUTH -> MV_SOUTH_SHAPE;
                case EAST -> MV_EAST_SHAPE;
                case WEST -> MV_WEST_SHAPE;
                case UP -> MV_UP_SHAPE;
            };
        }

        return switch (facing) {
            case DOWN -> DOWN_SHAPE;
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            case UP -> UP_SHAPE;
        };
    }
}
