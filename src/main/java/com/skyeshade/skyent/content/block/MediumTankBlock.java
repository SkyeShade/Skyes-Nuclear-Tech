package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.MediumTankBlockEntity;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.BlockHitResult;

public class MediumTankBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<MediumTankBlock> CODEC = simpleCodec(MediumTankBlock::new);
    public static final int SIZE_X = 2;
    public static final int SIZE_Y = 2;
    public static final int SIZE_Z = 4;
    private static final BlockPos CONTROLLER_LOCAL_POS = new BlockPos(0, 0, 1);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);

    public MediumTankBlock(BlockBehaviour.Properties properties) {
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
        return new MediumTankBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite().getClockWise();
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
                    level.setBlock(localToWorld(pos, facing, x, y, z), ModBlocks.MEDIUM_TANK_PART.get().defaultBlockState()
                            .setValue(MediumTankPartBlock.FACING, facing)
                            .setValue(MediumTankPartBlock.PART_X, x)
                            .setValue(MediumTankPartBlock.PART_Y, y)
                            .setValue(MediumTankPartBlock.PART_Z, z), Block.UPDATE_ALL);
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
            if (level.getBlockEntity(pos) instanceof MediumTankBlockEntity tank) {
                tank.dropContents(level, pos);
            }
            removeParts(level, pos, state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof MediumTankBlockEntity tank && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(tank, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(
                blockEntityType,
                ModBlockEntities.MEDIUM_TANK.get(),
                MediumTankBlockEntity::serverTick
        );
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MediumTankBlockEntity tank ? tank.getRedstoneSignal() : 0;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.MEDIUM_TANK.get());
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
            if (drop && level.getBlockEntity(masterPos) instanceof MediumTankBlockEntity tank) {
                tank.dropContents(level, masterPos);
            }
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.MEDIUM_TANK.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.MEDIUM_TANK.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.MEDIUM_TANK.get())) {
            return pos;
        }
        if (state.is(ModBlocks.MEDIUM_TANK_PART.get())
                && state.hasProperty(MediumTankPartBlock.FACING)
                && state.hasProperty(MediumTankPartBlock.PART_X)
                && state.hasProperty(MediumTankPartBlock.PART_Y)
                && state.hasProperty(MediumTankPartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(MediumTankPartBlock.PART_X),
                    state.getValue(MediumTankPartBlock.PART_Y),
                    state.getValue(MediumTankPartBlock.PART_Z)
            );
            return pos.subtract(localPartOffset(local, state.getValue(MediumTankPartBlock.FACING)));
        }
        return pos;
    }

    public static Optional<MediumTankBlockEntity> getMasterBlockEntity(Level level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof MediumTankBlockEntity tank ? Optional.of(tank) : Optional.empty();
    }

    public static boolean isValidPipeConnection(BlockState state, Direction side) {
        if (side == null) {
            return false;
        }

        Direction facing = state.hasProperty(FACING)
                ? state.getValue(FACING)
                : state.hasProperty(MediumTankPartBlock.FACING)
                ? state.getValue(MediumTankPartBlock.FACING)
                : Direction.NORTH;
        BlockPos local = localPartCoordinates(state);
        return isValidPipeConnection(local, facing, side);
    }

    public static boolean isValidPipeConnection(BlockPos local, Direction facing, Direction side) {
        if (side == Direction.UP || side == Direction.DOWN) {
            return false;
        }
        if (local.getY() != 0) {
            return false;
        }
        if (local.getZ() != 0 && local.getZ() != SIZE_Z - 1) {
            return false;
        }
        if (local.getX() != 0 && local.getX() != SIZE_X - 1) {
            return false;
        }

        Direction expectedSide = worldDirectionForLocalSide(local.getX() == 0 ? Direction.WEST : Direction.EAST, facing);
        return side == expectedSide;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(localPartOffset(new BlockPos(x, y, z), facing));
    }

    public static BlockPos getControllerLocalPos() {
        return CONTROLLER_LOCAL_POS;
    }

    public static boolean isControllerLocalPos(int x, int y, int z) {
        return CONTROLLER_LOCAL_POS.getX() == x && CONTROLLER_LOCAL_POS.getY() == y && CONTROLLER_LOCAL_POS.getZ() == z;
    }

    private static BlockPos localPartCoordinates(BlockState state) {
        if (state.is(ModBlocks.MEDIUM_TANK_PART.get())
                && state.hasProperty(MediumTankPartBlock.PART_X)
                && state.hasProperty(MediumTankPartBlock.PART_Y)
                && state.hasProperty(MediumTankPartBlock.PART_Z)) {
            return new BlockPos(
                    state.getValue(MediumTankPartBlock.PART_X),
                    state.getValue(MediumTankPartBlock.PART_Y),
                    state.getValue(MediumTankPartBlock.PART_Z)
            );
        }
        return CONTROLLER_LOCAL_POS;
    }

    private static Direction worldDirectionForLocalSide(Direction localSide, Direction facing) {
        BlockPos rotated = rotateLocalOffset(new BlockPos(localSide.getStepX(), localSide.getStepY(), localSide.getStepZ()), facing);
        return Direction.fromDelta(rotated.getX(), rotated.getY(), rotated.getZ());
    }

    private static BlockPos localPartOffset(BlockPos local, Direction facing) {
        return rotateLocalOffset(local.subtract(CONTROLLER_LOCAL_POS), facing);
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

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.MEDIUM_TANK.get().defaultBlockState().setValue(FACING, facing);
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
                    if (level.getBlockState(partPos).is(ModBlocks.MEDIUM_TANK_PART.get())) {
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
