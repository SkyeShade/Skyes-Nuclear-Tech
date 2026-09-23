package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.core.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.*;

/**
 * Lightweight, persisted local-cell address. No block entity or independent drops.
 */
public final class LargeSteamTurbinePartBlock extends Block {
    public static final MapCodec<LargeSteamTurbinePartBlock> CODEC = simpleCodec(LargeSteamTurbinePartBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 2), PART_Y = IntegerProperty.create("part_y", 0, 2), PART_Z = IntegerProperty.create("part_z", 0, 4);

    public LargeSteamTurbinePartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART_X, 0).setValue(PART_Y, 0).setValue(PART_Z, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PART_X, PART_Y, PART_Z);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        LargeSteamTurbineBlock.removeFromPart(level, pos, state, !player.isCreative() && player.hasCorrectToolForDrops(state));
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock())) LargeSteamTurbineBlock.removeFromPart(level, pos, state, true);
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.LARGE_STEAM_TURBINE.get());
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return shape(s, false);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return shape(s, true);
    }

    private VoxelShape shape(BlockState s, boolean collision) {
        return MeshMachineShapeCache.shape(s.getValue(PART_X), s.getValue(PART_Y), s.getValue(PART_Z), s.getValue(FACING), collision);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState s, BlockGetter l, BlockPos p) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState s, BlockGetter l, BlockPos p) {
        return 0;
    }
}
