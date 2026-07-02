package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.SteamForgeHammerBlockEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BaseEntityBlock;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteamForgeHammerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<SteamForgeHammerBlock> CODEC = simpleCodec(SteamForgeHammerBlock::new);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    // Steam Forge Hammer model is authored at half scale and rendered at 2x.
    // Shape coordinates below describe the final rendered size in normal 0..16 block units.
    private static final VoxelShape MASTER_SHAPE = Shapes.block();

    public SteamForgeHammerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SteamForgeHammerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        if (!canPlacePartAt(context, pos.above()) || !canPlacePartAt(context, pos.above(2))) {
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        level.setBlock(pos.above(), ModBlocks.STEAM_FORGE_HAMMER_PART.get().defaultBlockState()
                .setValue(SteamForgeHammerPartBlock.FACING, facing)
                .setValue(SteamForgeHammerPartBlock.PART_Y, 1), Block.UPDATE_ALL);
        level.setBlock(pos.above(2), ModBlocks.STEAM_FORGE_HAMMER_PART.get().defaultBlockState()
                .setValue(SteamForgeHammerPartBlock.FACING, facing)
                .setValue(SteamForgeHammerPartBlock.PART_Y, 2), Block.UPDATE_ALL);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !REMOVING.get()) {
            removeWholeMachine(level, pos, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !REMOVING.get()) {
            removeParts(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MASTER_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MASTER_SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.STEAM_FORGE_HAMMER.get());
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

    public static void removeWholeMachine(Level level, BlockPos masterPos, boolean drop, @Nullable Player player) {
        if (REMOVING.get()) {
            return;
        }

        REMOVING.set(true);
        try {
            removeParts(level, masterPos);
            if (level.getBlockState(masterPos).is(ModBlocks.STEAM_FORGE_HAMMER.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.STEAM_FORGE_HAMMER.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    private static void removeParts(Level level, BlockPos masterPos) {
        removePart(level, masterPos.above());
        removePart(level, masterPos.above(2));
    }

    private static void removePart(Level level, BlockPos partPos) {
        if (level.getBlockState(partPos).is(ModBlocks.STEAM_FORGE_HAMMER_PART.get())) {
            level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static boolean canPlacePartAt(BlockPlaceContext context, BlockPos pos) {
        BlockState state = context.getLevel().getBlockState(pos);
        return state.canBeReplaced(context);
    }
}
