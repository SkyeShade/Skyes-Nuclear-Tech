package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MVAssemblerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<MVAssemblerBlock> CODEC = simpleCodec(MVAssemblerBlock::new);
    public static final int SIZE_X = 3;
    public static final int SIZE_Y = 2;
    public static final int SIZE_Z = 3;
    // The controller is the front-center bottom cell of the 3x3x2 footprint.
    // Local X is right/left across the machine, local Z is forward from the controller.
    public static final int CONTROLLER_LOCAL_X = 1;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final int CONTROLLER_LOCAL_Z = 0;
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape SHAPE = Shapes.block();

    public MVAssemblerBlock(BlockBehaviour.Properties properties) {
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
        return new MVAssemblerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.MV_ASSEMBLER.get(), MVAssemblerBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.MV_ASSEMBLER.get(), MVAssemblerBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos origin = context.getClickedPos();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    if (isControllerLocalPos(x, y, z)) {
                        continue;
                    }
                    if (!canPlacePartAt(context, localToWorld(origin, facing, x, y, z))) {
                        return null;
                    }
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    if (isControllerLocalPos(x, y, z)) {
                        continue;
                    }
                    level.setBlock(localToWorld(pos, facing, x, y, z), ModBlocks.MV_ASSEMBLER_PART.get().defaultBlockState()
                            .setValue(MVAssemblerPartBlock.FACING, facing)
                            .setValue(MVAssemblerPartBlock.PART_X, x)
                            .setValue(MVAssemblerPartBlock.PART_Y, y)
                            .setValue(MVAssemblerPartBlock.PART_Z, z), Block.UPDATE_ALL);
                }
            }
        }
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

        if (level.getBlockEntity(pos) instanceof MVAssemblerBlockEntity assembler && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(assembler, pos);
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
        return new ItemStack(ModItems.MV_ASSEMBLER.get());
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
            if (level.getBlockEntity(masterPos) instanceof MVAssemblerBlockEntity assembler) {
                assembler.dropContents(level, masterPos);
            }
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.MV_ASSEMBLER.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.MV_ASSEMBLER.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.MV_ASSEMBLER.get())) {
            return pos;
        }
        if (state.is(ModBlocks.MV_ASSEMBLER_PART.get())
                && state.hasProperty(MVAssemblerPartBlock.FACING)
                && state.hasProperty(MVAssemblerPartBlock.PART_X)
                && state.hasProperty(MVAssemblerPartBlock.PART_Y)
                && state.hasProperty(MVAssemblerPartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(MVAssemblerPartBlock.PART_X),
                    state.getValue(MVAssemblerPartBlock.PART_Y),
                    state.getValue(MVAssemblerPartBlock.PART_Z)
            );
            return pos.subtract(rotateLocalOffset(local, state.getValue(MVAssemblerPartBlock.FACING)));
        }
        return pos;
    }

    public static Optional<MVAssemblerBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof MVAssemblerBlockEntity assembler
                ? Optional.of(assembler)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.MV_ASSEMBLER_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.MV_ASSEMBLER.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(rotateLocalOffset(new BlockPos(x, y, z), facing));
    }

    public static void requestSharedLightUpdate(Level level, BlockPos masterPos) {
        BlockState state = level.getBlockState(masterPos);
        if (!state.is(ModBlocks.MV_ASSEMBLER.get())) {
            return;
        }
        if (level.getBlockEntity(masterPos) instanceof MVAssemblerBlockEntity blockEntity) {
            blockEntity.requestModelDataUpdate();
            blockEntity.refreshSharedLight(true);
        }
        level.sendBlockUpdated(masterPos, state, state, Block.UPDATE_CLIENTS);
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        int rightOffset = local.getX() - CONTROLLER_LOCAL_X;
        int y = local.getY() - CONTROLLER_LOCAL_Y;
        int forwardOffset = local.getZ() - CONTROLLER_LOCAL_Z;
        Direction right = facing.getClockWise();
        int worldX = facing.getStepX() * forwardOffset + right.getStepX() * rightOffset;
        int worldZ = facing.getStepZ() * forwardOffset + right.getStepZ() * rightOffset;
        return new BlockPos(worldX, y, worldZ);
    }

    private static boolean isControllerLocalPos(int x, int y, int z) {
        return x == CONTROLLER_LOCAL_X && y == CONTROLLER_LOCAL_Y && z == CONTROLLER_LOCAL_Z;
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.MV_ASSEMBLER.get().defaultBlockState().setValue(FACING, facing);
        int visualStateId = Block.getId(visualState);
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    level.levelEvent(2001, localToWorld(masterPos, facing, x, y, z), visualStateId);
                }
            }
        }
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    if (isControllerLocalPos(x, y, z)) {
                        continue;
                    }
                    BlockPos partPos = localToWorld(masterPos, facing, x, y, z);
                    if (level.getBlockState(partPos).is(ModBlocks.MV_ASSEMBLER_PART.get())) {
                        level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static boolean canPlacePartAt(BlockPlaceContext context, BlockPos pos) {
        BlockState state = context.getLevel().getBlockState(pos);
        return state.canBeReplaced(context);
    }
}
