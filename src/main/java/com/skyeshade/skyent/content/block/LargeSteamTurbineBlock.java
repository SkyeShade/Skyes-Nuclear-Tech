package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.LargeSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.model.MeshMachineDefinition;
import com.skyeshade.skyent.content.multiblock.*;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.*;

import java.util.*;

public final class LargeSteamTurbineBlock extends BaseEntityBlock {
    public static final MapCodec<LargeSteamTurbineBlock> CODEC = simpleCodec(LargeSteamTurbineBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final ModelMultiblockDefinition MULTIBLOCK = MeshMachineDefinition.LARGE_STEAM_TURBINE.multiblock();
    private static final ThreadLocal<Boolean> CHANGING = ThreadLocal.withInitial(() -> false);

    public LargeSteamTurbineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LargeSteamTurbineBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        return canPlace(context, facing) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    public static boolean canPlace(BlockPlaceContext context, Direction facing) {
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        // Check availability before reading states: placement must never force a neighboring chunk to load.
        for (int y = 0; y < MULTIBLOCK.sizeY(); y++)
            for (int x = 0; x < MULTIBLOCK.sizeX(); x++)
                for (int z = 0; z < MULTIBLOCK.sizeZ(); z++) {
                    BlockPos pos = ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z);
                    if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)
                            || !level.getBlockState(pos).canBeReplaced(context) || level.getBlockEntity(pos) != null)
                        return false;
                    VoxelShape collision = MeshMachineShapeCache.shape(x, y, z, facing, true);
                    if (!level.isUnobstructed(null, collision.move(pos.getX(), pos.getY(), pos.getZ()))) return false;
                }
        return ModelMultiblocks.canPlace(MULTIBLOCK, context, origin, facing);
    }

    /**
     * Used by the item transaction and GameTests; rollback restores replaceable states if any write is rejected.
     */
    public static boolean place(BlockPlaceContext context, BlockState controller) {
        Direction facing = controller.getValue(FACING);
        if (!canPlace(context, facing)) return false;
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        Map<BlockPos, BlockState> previous = new LinkedHashMap<>();
        CHANGING.set(true);
        try {
            ModelMultiblocks.forEachLocal(MULTIBLOCK, (x, y, z) -> previous.put(ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z), level.getBlockState(ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z))));
            if (!level.setBlock(origin, controller, Block.UPDATE_CLIENTS)) return rollback(level, previous);
            // Existing part factory and local/world mapping; suppress notifications until the volume is complete.
            for (int y = 0; y < MULTIBLOCK.sizeY(); y++)
                for (int x = 0; x < MULTIBLOCK.sizeX(); x++)
                    for (int z = 0; z < MULTIBLOCK.sizeZ(); z++) {
                        if (MULTIBLOCK.isControllerLocal(x, y, z)) continue;
                        BlockPos pos = ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z);
                        if (!level.setBlock(pos, partState(x, y, z, facing), Block.UPDATE_CLIENTS))
                            return rollback(level, previous);
                    }
            previous.keySet().forEach(pos -> level.updateNeighborsAt(pos, level.getBlockState(pos).getBlock()));
            return true;
        } catch (RuntimeException exception) {
            rollback(level, previous);
            throw exception;
        } finally {
            CHANGING.set(false);
        }
    }

    private static boolean rollback(Level level, Map<BlockPos, BlockState> previous) {
        previous.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
        return false;
    }

    public static BlockState partState(int x, int y, int z, Direction facing) {
        return ModBlocks.LARGE_STEAM_TURBINE_PART.get().defaultBlockState().setValue(LargeSteamTurbinePartBlock.FACING, facing)
                .setValue(LargeSteamTurbinePartBlock.PART_X, x).setValue(LargeSteamTurbinePartBlock.PART_Y, y).setValue(LargeSteamTurbinePartBlock.PART_Z, z);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && !level.isClientSide && !CHANGING.get()) {
            CHANGING.set(true);
            try {
                ModelMultiblocks.removeParts(MULTIBLOCK, level, pos, state.getValue(FACING), ModBlocks.LARGE_STEAM_TURBINE_PART.get());
            } finally {
                CHANGING.set(false);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    public static void removeFromPart(Level level, BlockPos pos, BlockState state, boolean drop) {
        if (level.isClientSide || CHANGING.get()) return;
        BlockPos master = masterPos(pos, state);
        // Removal must also clean up across an unloaded chunk edge. Only placement avoids loading chunks.
        BlockState controller = level.getBlockState(master);
        if (controller.is(ModBlocks.LARGE_STEAM_TURBINE.get()) && controller.getValue(FACING) == state.getValue(FACING))
            level.destroyBlock(master, drop);
    }

    public static BlockPos masterPos(BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.LARGE_STEAM_TURBINE.get())) return pos;
        return ModelMultiblocks.masterPosFromLocal(MULTIBLOCK, pos, new BlockPos(state.getValue(LargeSteamTurbinePartBlock.PART_X),
                state.getValue(LargeSteamTurbinePartBlock.PART_Y), state.getValue(LargeSteamTurbinePartBlock.PART_Z)), state.getValue(FACING));
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.LARGE_STEAM_TURBINE_PART.get())) return pos;
        BlockPos master = masterPos(pos, state);
        BlockState controller = level.getBlockState(master);
        return controller.is(ModBlocks.LARGE_STEAM_TURBINE.get()) && controller.getValue(FACING) == state.getValue(FACING) ? master : pos;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return controllerShape(state, false);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return controllerShape(state, true);
    }

    private VoxelShape controllerShape(BlockState state, boolean collision) {
        BlockPos c = MULTIBLOCK.controllerLocal();
        return MeshMachineShapeCache.shape(c.getX(), c.getY(), c.getZ(), state.getValue(FACING), collision);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }
}
