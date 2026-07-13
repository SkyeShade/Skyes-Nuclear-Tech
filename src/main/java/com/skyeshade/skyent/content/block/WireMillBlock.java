package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.WireMillBlockEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
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

public class WireMillBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<WireMillBlock> CODEC = simpleCodec(WireMillBlock::new);
    public static final int LENGTH = 6;
    public static final int WIDTH = 3;
    public static final int HEIGHT = 3;
    public static final int CONTROLLER_LOCAL_X = 0;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final int CONTROLLER_LOCAL_Z = 1;
    public static final int PLACEMENT_ANCHOR_LOCAL_X = 0;
    public static final int PLACEMENT_ANCHOR_LOCAL_Y = 0;
    public static final int PLACEMENT_ANCHOR_LOCAL_Z = 1;
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape SHAPE = Shapes.block();

    public WireMillBlock(BlockBehaviour.Properties properties) {
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
        return new WireMillBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.WIRE_MILL.get(), WireMillBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.WIRE_MILL.get(), WireMillBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection();
        BlockPos controllerPos = controllerPosForPlacement(context.getClickedPos(), facing);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < LENGTH; x++) {
                for (int z = 0; z < WIDTH; z++) {
                    if (isPlacementAnchorLocalPos(x, y, z)) {
                        continue;
                    }
                    if (!canPlacePartAt(context, localToWorld(controllerPos, facing, x, y, z))) {
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
        BlockPos controllerPos = controllerPosForPlacement(pos, facing);
        if (!controllerPos.equals(pos)) {
            level.setBlock(controllerPos, state, Block.UPDATE_ALL);
        }
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < LENGTH; x++) {
                for (int z = 0; z < WIDTH; z++) {
                    if (isControllerLocalPos(x, y, z)) {
                        continue;
                    }
                    level.setBlock(localToWorld(controllerPos, facing, x, y, z), ModBlocks.WIRE_MILL_PART.get().defaultBlockState()
                            .setValue(WireMillPartBlock.FACING, facing)
                            .setValue(WireMillPartBlock.PART_X, x)
                            .setValue(WireMillPartBlock.PART_Y, y)
                            .setValue(WireMillPartBlock.PART_Z, z), Block.UPDATE_ALL);
                }
            }
        }
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
        return new ItemStack(ModItems.WIRE_MILL.get());
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
            if (level.getBlockEntity(masterPos) instanceof WireMillBlockEntity wireMill) {
                wireMill.dropInternalContents();
            }
            spawnDestroyParticles(level, masterPos, facing);
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.WIRE_MILL.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.WIRE_MILL.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.WIRE_MILL.get())) {
            return pos;
        }
        if (state.is(ModBlocks.WIRE_MILL_PART.get())
                && state.hasProperty(WireMillPartBlock.FACING)
                && state.hasProperty(WireMillPartBlock.PART_X)
                && state.hasProperty(WireMillPartBlock.PART_Y)
                && state.hasProperty(WireMillPartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(WireMillPartBlock.PART_X),
                    state.getValue(WireMillPartBlock.PART_Y),
                    state.getValue(WireMillPartBlock.PART_Z)
            );
            return pos.subtract(rotateLocalOffset(local, state.getValue(WireMillPartBlock.FACING)));
        }
        return pos;
    }

    public static Optional<WireMillBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof WireMillBlockEntity wireMill
                ? Optional.of(wireMill)
                : Optional.empty();
    }

    public static boolean isConnectorSupportCell(BlockState state) {
        return state.is(ModBlocks.WIRE_MILL.get()) || state.is(ModBlocks.WIRE_MILL_PART.get());
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.WIRE_MILL_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.WIRE_MILL.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(rotateLocalOffset(new BlockPos(x, y, z), facing));
    }

    public static BlockPos controllerPosForPlacement(BlockPos clickedPos, Direction facing) {
        BlockPos anchorOffset = rotateLocalOffset(new BlockPos(
                PLACEMENT_ANCHOR_LOCAL_X,
                PLACEMENT_ANCHOR_LOCAL_Y,
                PLACEMENT_ANCHOR_LOCAL_Z
        ), facing);
        BlockPos controllerOffset = rotateLocalOffset(new BlockPos(
                CONTROLLER_LOCAL_X,
                CONTROLLER_LOCAL_Y,
                CONTROLLER_LOCAL_Z
        ), facing);
        return clickedPos.subtract(anchorOffset).offset(controllerOffset);
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        int length = local.getX() - CONTROLLER_LOCAL_X;
        int y = local.getY() - CONTROLLER_LOCAL_Y;
        int width = local.getZ() - CONTROLLER_LOCAL_Z;
        Direction forward = facing;
        Direction right = facing.getClockWise();
        int worldX = forward.getStepX() * length + right.getStepX() * width;
        int worldZ = forward.getStepZ() * length + right.getStepZ() * width;
        return new BlockPos(worldX, y, worldZ);
    }

    private static boolean isControllerLocalPos(int x, int y, int z) {
        return x == CONTROLLER_LOCAL_X && y == CONTROLLER_LOCAL_Y && z == CONTROLLER_LOCAL_Z;
    }

    private static boolean isPlacementAnchorLocalPos(int x, int y, int z) {
        return x == PLACEMENT_ANCHOR_LOCAL_X && y == PLACEMENT_ANCHOR_LOCAL_Y && z == PLACEMENT_ANCHOR_LOCAL_Z;
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.WIRE_MILL.get().defaultBlockState().setValue(FACING, facing);
        int visualStateId = Block.getId(visualState);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < LENGTH; x++) {
                for (int z = 0; z < WIDTH; z++) {
                    level.levelEvent(2001, localToWorld(masterPos, facing, x, y, z), visualStateId);
                }
            }
        }
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < LENGTH; x++) {
                for (int z = 0; z < WIDTH; z++) {
                    if (isControllerLocalPos(x, y, z)) {
                        continue;
                    }
                    BlockPos partPos = localToWorld(masterPos, facing, x, y, z);
                    if (level.getBlockState(partPos).is(ModBlocks.WIRE_MILL_PART.get())) {
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
