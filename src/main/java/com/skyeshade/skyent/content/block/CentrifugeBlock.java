package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.CentrifugeBlockEntity;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockCollisionMode;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockDefinition;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockRenderMode;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMultiblockShapes;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CentrifugeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);
    public static final int SIZE_X = 3;
    public static final int SIZE_Y = 3;
    public static final int SIZE_Z = 3;
    public static final int CONTROLLER_LOCAL_X = 1;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final int CONTROLLER_LOCAL_Z = 0;
    public static final ModelMultiblockDefinition MULTIBLOCK = new ModelMultiblockDefinition(
            ModMultiblockShapes.CENTRIFUGE,
            SIZE_X,
            SIZE_Y,
            SIZE_Z,
            new BlockPos(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z),
            2.0D,
            Vec3.ZERO,
            new Vec3(0.0D, 0.0D, 16.0D),
            ModelMultiblockCollisionMode.SOLID,
            ModelMultiblockRenderMode.CONTROLLER_BLOCK_MODEL
    );
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape SHAPE = Shapes.block();

    public CentrifugeBlock(Properties properties) {
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
        return new CentrifugeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.CENTRIFUGE.get(), CentrifugeBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.CENTRIFUGE.get(), CentrifugeBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos origin = context.getClickedPos();
        if (!ModelMultiblocks.canPlace(MULTIBLOCK, context, origin, facing)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        ModelMultiblocks.placeParts(MULTIBLOCK, level, pos, facing, (x, y, z, partFacing) ->
                ModBlocks.CENTRIFUGE_PART.get().defaultBlockState()
                        .setValue(CentrifugePartBlock.FACING, partFacing)
                        .setValue(CentrifugePartBlock.PART_X, x)
                        .setValue(CentrifugePartBlock.PART_Y, y)
                        .setValue(CentrifugePartBlock.PART_Z, z));
        requestSharedLightUpdate(level, pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        requestSharedLightUpdate(level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !REMOVING.get()) {
            removeWholeMachine(level, pos, !player.isCreative());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !REMOVING.get()) {
            removeParts(level, pos, state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof CentrifugeBlockEntity centrifuge && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(centrifuge, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
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
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.CENTRIFUGE.get());
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

    public static void removeWholeMachine(Level level, BlockPos masterPos, boolean drop) {
        if (REMOVING.get()) {
            return;
        }

        REMOVING.set(true);
        try {
            BlockState state = level.getBlockState(masterPos);
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            spawnDestroyParticles(level, masterPos, facing);
            if (level.getBlockEntity(masterPos) instanceof CentrifugeBlockEntity centrifuge) {
                centrifuge.dropContents(level, masterPos);
            }
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.CENTRIFUGE.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.CENTRIFUGE.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.CENTRIFUGE.get())) {
            return pos;
        }
        if (state.is(ModBlocks.CENTRIFUGE_PART.get())
                && state.hasProperty(CentrifugePartBlock.FACING)
                && state.hasProperty(CentrifugePartBlock.PART_X)
                && state.hasProperty(CentrifugePartBlock.PART_Y)
                && state.hasProperty(CentrifugePartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(CentrifugePartBlock.PART_X),
                    state.getValue(CentrifugePartBlock.PART_Y),
                    state.getValue(CentrifugePartBlock.PART_Z)
            );
            return ModelMultiblocks.masterPosFromLocal(MULTIBLOCK, pos, local, state.getValue(CentrifugePartBlock.FACING));
        }
        return pos;
    }

    public static Optional<CentrifugeBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof CentrifugeBlockEntity centrifuge
                ? Optional.of(centrifuge)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.CENTRIFUGE_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.CENTRIFUGE.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z);
    }

    public static void requestSharedLightUpdate(Level level, BlockPos masterPos) {
        BlockState state = level.getBlockState(masterPos);
        if (!state.is(ModBlocks.CENTRIFUGE.get())) {
            return;
        }
        if (level.getBlockEntity(masterPos) instanceof CentrifugeBlockEntity blockEntity) {
            blockEntity.requestModelDataUpdate();
            blockEntity.refreshSharedLight(true);
        }
        level.sendBlockUpdated(masterPos, state, state, Block.UPDATE_CLIENTS);
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        return ModelMultiblocks.rotateLocalOffset(MULTIBLOCK, local, facing);
    }

    private static boolean isControllerLocalPos(int x, int y, int z) {
        return MULTIBLOCK.isControllerLocal(x, y, z);
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.CENTRIFUGE.get().defaultBlockState().setValue(FACING, facing);
        ModelMultiblocks.spawnDestroyParticles(MULTIBLOCK, level, masterPos, facing, visualState);
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        ModelMultiblocks.removeParts(MULTIBLOCK, level, masterPos, facing, ModBlocks.CENTRIFUGE_PART.get());
    }
}
