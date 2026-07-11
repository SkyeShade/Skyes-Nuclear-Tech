package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.RollingMillBlockEntity;
import com.skyeshade.skyent.content.shape.MultiblockShapeRegistry;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMultiblockShapes;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RollingMillBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<RollingMillBlock> CODEC = simpleCodec(RollingMillBlock::new);
    public static final int SIZE_X = 4;
    public static final int SIZE_Y = 3;
    public static final int SIZE_Z = 2;
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public RollingMillBlock(BlockBehaviour.Properties properties) {
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
        return new RollingMillBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.ROLLING_MILL.get(), RollingMillBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.ROLLING_MILL.get(), RollingMillBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getCounterClockWise();
        BlockPos origin = context.getClickedPos();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    if (x == 0 && y == 0 && z == 0) {
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
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    level.setBlock(localToWorld(pos, facing, x, y, z), ModBlocks.ROLLING_MILL_PART.get().defaultBlockState()
                            .setValue(RollingMillPartBlock.FACING, facing)
                            .setValue(RollingMillPartBlock.PART_X, x)
                            .setValue(RollingMillPartBlock.PART_Y, y)
                            .setValue(RollingMillPartBlock.PART_Z, z)
                            .setValue(RollingMillPartBlock.LIGHT_BLOCKING, shouldPartBlockLight(x, y, z)), Block.UPDATE_ALL);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForLocal(0, 0, 0, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForLocal(0, 0, 0, state.getValue(FACING));
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
    protected boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.ROLLING_MILL.get());
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
            if (level.getBlockEntity(masterPos) instanceof RollingMillBlockEntity rollingMill) {
                rollingMill.dropInternalConveyorItems();
            }
            spawnDestroyParticles(level, masterPos, facing);
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.ROLLING_MILL.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.ROLLING_MILL.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.ROLLING_MILL.get())) {
            return pos;
        }
        if (state.is(ModBlocks.ROLLING_MILL_PART.get())
                && state.hasProperty(RollingMillPartBlock.FACING)
                && state.hasProperty(RollingMillPartBlock.PART_X)
                && state.hasProperty(RollingMillPartBlock.PART_Y)
                && state.hasProperty(RollingMillPartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(RollingMillPartBlock.PART_X),
                    state.getValue(RollingMillPartBlock.PART_Y),
                    state.getValue(RollingMillPartBlock.PART_Z)
            );
            return pos.subtract(rotateLocalOffset(local, state.getValue(RollingMillPartBlock.FACING)));
        }
        return pos;
    }

    public static Optional<RollingMillBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof RollingMillBlockEntity rollingMill
                ? Optional.of(rollingMill)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.ROLLING_MILL_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.ROLLING_MILL.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(rotateLocalOffset(new BlockPos(x, y, z), facing));
    }

    public static VoxelShape shapeForLocal(int x, int y, int z, Direction facing) {
        return MultiblockShapeRegistry.getShape(
                ModMultiblockShapes.ROLLING_MILL,
                facing,
                x,
                y,
                z,
                (fallbackFacing, fallbackX, fallbackY, fallbackZ) -> RollingMillShapes.shapeForLocal(fallbackX, fallbackY, fallbackZ, fallbackFacing)
        );
    }

    public static boolean isInternalConveyorLocalPos(BlockPos local) {
        return local.getX() == 1 && local.getY() == 1 && (local.getZ() == 0 || local.getZ() == 1);
    }

    public static BlockPos getInternalConveyorInputLocalPos() {
        return new BlockPos(1, 1, 1);
    }

    public static Direction getInternalConveyorDirection(Direction machineFacing) {
        return machineFacing;
    }

    public static boolean isConnectorSupportCell(LevelReader level, BlockState state, BlockPos pos, Direction clickedFace) {
        Direction facing;
        BlockPos local;
        if (state.is(ModBlocks.ROLLING_MILL.get()) && state.hasProperty(FACING)) {
            facing = state.getValue(FACING);
            local = BlockPos.ZERO;
        } else if (state.is(ModBlocks.ROLLING_MILL_PART.get())
                && state.hasProperty(RollingMillPartBlock.FACING)
                && state.hasProperty(RollingMillPartBlock.PART_X)
                && state.hasProperty(RollingMillPartBlock.PART_Y)
                && state.hasProperty(RollingMillPartBlock.PART_Z)) {
            facing = state.getValue(RollingMillPartBlock.FACING);
            local = new BlockPos(
                    state.getValue(RollingMillPartBlock.PART_X),
                    state.getValue(RollingMillPartBlock.PART_Y),
                    state.getValue(RollingMillPartBlock.PART_Z)
            );
        } else {
            return false;
        }

        return level.getBlockState(getMasterPos(state, pos)).is(ModBlocks.ROLLING_MILL.get())
                && local.getX() == SIZE_X - 1
                && local.getY() <= 1
                && local.getZ() >= 0
                && local.getZ() < SIZE_Z
                && clickedFace == facing.getClockWise();
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        int x = local.getX();
        int y = local.getY();
        int z = local.getZ();
        return switch (facing) {
            case NORTH -> new BlockPos(x, y, z);
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> local;
        };
    }

    private static boolean shouldPartBlockLight(int x, int y, int z) {
        return false;
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.ROLLING_MILL.get().defaultBlockState().setValue(FACING, facing);
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
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos partPos = localToWorld(masterPos, facing, x, y, z);
                    if (level.getBlockState(partPos).is(ModBlocks.ROLLING_MILL_PART.get())) {
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
