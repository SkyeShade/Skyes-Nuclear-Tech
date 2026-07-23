package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.MVInlinePumpBlockEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.phys.BlockHitResult;

public class MVInlinePumpBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<MVInlinePumpBlock> CODEC = simpleCodec(MVInlinePumpBlock::new);

    public MVInlinePumpBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MVInlinePumpBlockEntity pump && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(pump, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof MVInlinePumpBlockEntity pump) {
                pump.dropContents(level, pos);
                level.updateNeighbourForOutputSignal(pos, this);
            }
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MVInlinePumpBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.MV_INLINE_PUMP.get(),
                level.isClientSide ? MVInlinePumpBlockEntity::clientTick : MVInlinePumpBlockEntity::serverTick
        );
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MVInlinePumpBlockEntity pump ? pump.getRedstoneSignal() : 0;
    }

    public static boolean isFluidInputSide(BlockState state, @Nullable Direction side) {
        return side != null && side == worldDirectionForLocalSide(Direction.SOUTH, facing(state));
    }

    public static boolean isFluidOutputSide(BlockState state, @Nullable Direction side) {
        return side != null && side == worldDirectionForLocalSide(Direction.NORTH, facing(state));
    }

    public static boolean isValidFluidConnection(BlockState state, @Nullable Direction side) {
        return isFluidInputSide(state, side) || isFluidOutputSide(state, side);
    }

    public static boolean isValidEnergyConnection(BlockState state, @Nullable Direction side) {
        if (side == null) {
            return false;
        }

        Direction facing = facing(state);
        return side == worldDirectionForLocalSide(Direction.EAST, facing)
                || side == worldDirectionForLocalSide(Direction.WEST, facing);
    }

    public static Direction worldDirectionForLocalSide(Direction localSide, Direction facing) {
        if (localSide.getAxis().isVertical()) {
            return localSide;
        }

        return switch (facing) {
            case NORTH -> localSide;
            case EAST -> localSide.getClockWise();
            case SOUTH -> localSide.getOpposite();
            case WEST -> localSide.getCounterClockWise();
            default -> localSide;
        };
    }

    private static Direction facing(BlockState state) {
        return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
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
