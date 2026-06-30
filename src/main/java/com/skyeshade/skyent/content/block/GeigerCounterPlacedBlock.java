package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.GeigerCounterPlacedBlockEntity;
import com.skyeshade.skyent.content.item.GeigerCounterItem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GeigerCounterPlacedBlock extends BaseEntityBlock {
    public static final DirectionProperty ATTACHED_FACE = DirectionProperty.create(
            "attached_face",
            direction -> direction == Direction.UP || direction.getAxis().isHorizontal()
    );
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<GeigerCounterPlacedBlock> CODEC = simpleCodec(GeigerCounterPlacedBlock::new);

    private static final VoxelShape SHAPE_NORTH = Block.box(5.0D, 0.0D, 2.125D, 11.0D, 2.0D, 13.125D);
    private static final VoxelShape SHAPE_SOUTH = Block.box(5.0D, 0.0D, 2.875D, 11.0D, 2.0D, 13.875D);
    private static final VoxelShape SHAPE_EAST = Block.box(2.875D, 0.0D, 5.0D, 13.875D, 2.0D, 11.0D);
    private static final VoxelShape SHAPE_WEST = Block.box(2.125D, 0.0D, 5.0D, 13.125D, 2.0D, 11.0D);
    private static final VoxelShape WALL_SHAPE_NORTH = Block.box(5.0D, 2.875D, 14.0D, 11.0D, 13.875D, 16.0D);
    private static final VoxelShape WALL_SHAPE_SOUTH = Block.box(5.0D, 2.875D, 0.0D, 11.0D, 13.875D, 2.0D);
    private static final VoxelShape WALL_SHAPE_EAST = Block.box(0.0D, 2.875D, 5.0D, 2.0D, 13.875D, 11.0D);
    private static final VoxelShape WALL_SHAPE_WEST = Block.box(14.0D, 2.875D, 5.0D, 16.0D, 13.875D, 11.0D);

    public GeigerCounterPlacedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(ATTACHED_FACE, Direction.UP)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        if (clickedFace == Direction.DOWN) {
            return null;
        }

        Direction facing = clickedFace == Direction.UP ? context.getHorizontalDirection() : clickedFace;
        return defaultBlockState()
                .setValue(ATTACHED_FACE, clickedFace)
                .setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForState(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForState(state);
    }

    private static VoxelShape shapeForState(BlockState state) {
        Direction attachedFace = state.getValue(ATTACHED_FACE);
        if (attachedFace == Direction.UP) {
            return floorShapeForFacing(state.getValue(FACING));
        }

        return wallShapeForAttachedFace(attachedFace);
    }

    private static VoxelShape floorShapeForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    private static VoxelShape wallShapeForAttachedFace(Direction attachedFace) {
        return switch (attachedFace) {
            case EAST -> WALL_SHAPE_EAST;
            case SOUTH -> WALL_SHAPE_SOUTH;
            case WEST -> WALL_SHAPE_WEST;
            default -> WALL_SHAPE_NORTH;
        };
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attachedFace = state.getValue(ATTACHED_FACE);
        if (attachedFace == Direction.UP) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        BlockPos supportPos = pos.relative(attachedFace.getOpposite());
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, attachedFace);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && !canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }

        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (player.isShiftKeyDown()) {
            pickUp(level, pos, player);
            return InteractionResult.CONSUME;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof GeigerCounterPlacedBlockEntity geiger) {
            boolean enabled = geiger.toggleAudio();
            player.displayClientMessage(Component.literal(enabled ? "Geiger audio: On" : "Geiger audio: Off"), true);
            level.playSound(null, pos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.0F);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    private static void pickUp(Level level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(ModItems.GEIGER_COUNTER.get());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof GeigerCounterPlacedBlockEntity geiger) {
            GeigerCounterItem.setAudioEnabled(stack, geiger.isAudioEnabled());
        }

        level.removeBlock(pos, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F, 1.0F);
        if (!player.isCreative()) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeigerCounterPlacedBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.GEIGER_COUNTER_PLACED.get(),
                level.isClientSide ? GeigerCounterPlacedBlockEntity::clientTick : GeigerCounterPlacedBlockEntity::serverTick
        );
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction attachedFace = state.getValue(ATTACHED_FACE);
        if (attachedFace == Direction.UP) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        return state.setValue(ATTACHED_FACE, rotation.rotate(attachedFace))
                .setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction attachedFace = state.getValue(ATTACHED_FACE);
        Direction rotationReference = attachedFace == Direction.UP ? state.getValue(FACING) : attachedFace;
        return state.rotate(mirror.getRotation(rotationReference));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ATTACHED_FACE, FACING);
    }
}
