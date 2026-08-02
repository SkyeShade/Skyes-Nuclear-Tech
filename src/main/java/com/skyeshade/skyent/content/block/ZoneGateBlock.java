package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.ZoneGateBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ZoneGateBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<ZoneGateBlock> CODEC = simpleCodec(ZoneGateBlock::new);
    public static final int SIZE_X = 7;
    public static final int SIZE_Y = 4;
    public static final int SIZE_Z = 1;
    // Controller is the bottom-center block of the 7x4x1 gate footprint.
    public static final int CONTROLLER_LOCAL_X = 3;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final int CONTROLLER_LOCAL_Z = 0;
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FALLBACK_CELL_SHAPE = Shapes.block();
    private static final double DOOR_COLLISION_MIN_X = 0.5D;
    private static final double DOOR_COLLISION_MAX_X = 6.5D;
    private static final double DOOR_COLLISION_MIN_Y = -0.0625D;
    private static final double DOOR_COLLISION_MAX_Y = 3.4375D;
    private static final double DOOR_COLLISION_MIN_Z = 0.4375D;
    private static final double DOOR_COLLISION_MAX_Z = 0.5625D;

    public ZoneGateBlock(Properties properties) {
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
        return new ZoneGateBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.ZONE_GATE.get(), ZoneGateBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.ZONE_GATE.get(), ZoneGateBlockEntity::serverTick);
    }

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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
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
                    level.setBlock(localToWorld(pos, facing, x, y, z), ModBlocks.ZONE_GATE_PART.get().defaultBlockState()
                            .setValue(ZoneGatePartBlock.FACING, facing)
                            .setValue(ZoneGatePartBlock.PART_X, x)
                            .setValue(ZoneGatePartBlock.PART_Y, y), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ZoneGateBlockEntity zoneGate) {
            zoneGate.updateRedstonePower();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !REMOVING.get()) {
            removeWholeGate(level, pos, !player.isCreative());
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
        return shapeForLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, state.getValue(FACING), doorPanelProgress(level, state, pos));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
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
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.ZONE_GATE.get());
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

    public static void removeWholeGate(Level level, BlockPos masterPos, boolean drop) {
        if (REMOVING.get()) {
            return;
        }

        REMOVING.set(true);
        try {
            BlockState state = level.getBlockState(masterPos);
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            spawnDestroyParticles(level, masterPos, facing);
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.ZONE_GATE.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.ZONE_GATE.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.ZONE_GATE.get())) {
            return pos;
        }
        if (state.is(ModBlocks.ZONE_GATE_PART.get())
                && state.hasProperty(ZoneGatePartBlock.FACING)
                && state.hasProperty(ZoneGatePartBlock.PART_X)
                && state.hasProperty(ZoneGatePartBlock.PART_Y)) {
            BlockPos local = new BlockPos(
                    state.getValue(ZoneGatePartBlock.PART_X),
                    state.getValue(ZoneGatePartBlock.PART_Y),
                    CONTROLLER_LOCAL_Z
            );
            return pos.subtract(rotateLocalOffset(local, state.getValue(ZoneGatePartBlock.FACING)));
        }
        return pos;
    }

    public static Optional<ZoneGateBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof ZoneGateBlockEntity zoneGate
                ? Optional.of(zoneGate)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.ZONE_GATE_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.ZONE_GATE.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(rotateLocalOffset(new BlockPos(x, y, z), facing));
    }

    public static VoxelShape shapeForLocal(int x, int y, Direction facing, double openProgress) {
        VoxelShape frame = frameShapeForLocal(x, y, facing);
        if (openProgress >= 1.0D) {
            return frame;
        }

        VoxelShape doorPanel = doorPanelShapeForLocal(x, y, facing, openProgress * ZoneGateBlockEntity.ZONE_GATE_DOOR_TRAVEL_Y);
        return doorPanel.isEmpty() ? frame : Shapes.or(frame, doorPanel);
    }

    public static VoxelShape radiationFrameShapeForState(BlockState state) {
        Direction facing = state.hasProperty(FACING)
                ? state.getValue(FACING)
                : state.getValue(ZoneGatePartBlock.FACING);
        int localX = state.hasProperty(ZoneGatePartBlock.PART_X)
                ? state.getValue(ZoneGatePartBlock.PART_X)
                : CONTROLLER_LOCAL_X;
        int localY = state.hasProperty(ZoneGatePartBlock.PART_Y)
                ? state.getValue(ZoneGatePartBlock.PART_Y)
                : CONTROLLER_LOCAL_Y;
        return frameShapeForLocal(localX, localY, facing);
    }

    public static VoxelShape radiationDoorPanelShapeForState(BlockState state, double openProgress) {
        if (openProgress >= 1.0D) {
            return Shapes.empty();
        }

        Direction facing = state.hasProperty(FACING)
                ? state.getValue(FACING)
                : state.getValue(ZoneGatePartBlock.FACING);
        int localX = state.hasProperty(ZoneGatePartBlock.PART_X)
                ? state.getValue(ZoneGatePartBlock.PART_X)
                : CONTROLLER_LOCAL_X;
        int localY = state.hasProperty(ZoneGatePartBlock.PART_Y)
                ? state.getValue(ZoneGatePartBlock.PART_Y)
                : CONTROLLER_LOCAL_Y;
        return doorPanelShapeForLocal(localX, localY, facing, openProgress * ZoneGateBlockEntity.ZONE_GATE_DOOR_TRAVEL_Y);
    }

    public static boolean isPoweredAnywhere(Level level, BlockPos masterPos, Direction facing) {
        for (int y = 0; y < SIZE_Y; y++) {
            for (int x = 0; x < SIZE_X; x++) {
                for (int z = 0; z < SIZE_Z; z++) {
                    if (level.hasNeighborSignal(localToWorld(masterPos, facing, x, y, z))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static double doorPanelProgress(BlockGetter level, BlockState state, BlockPos pos) {
        if (level instanceof LevelAccessor levelAccessor) {
            return getMasterBlockEntity(levelAccessor, state, pos)
                    .map(ZoneGateBlockEntity::getOpenProgress)
                    .orElse(0.0F);
        }
        return 0.0D;
    }

    public static VoxelShape frameShapeForLocal(int x, int y, Direction facing) {
        return MultiblockShapeRegistry.getShape(
                ModMultiblockShapes.ZONE_GATE,
                facing,
                x,
                y,
                CONTROLLER_LOCAL_Z,
                (fallbackFacing, fallbackX, fallbackY, fallbackZ) -> FALLBACK_CELL_SHAPE
        );
    }

    private static VoxelShape doorPanelShapeForLocal(int x, int y, Direction facing, double yOffset) {
        double minX = Math.max(0.0D, DOOR_COLLISION_MIN_X - x);
        double maxX = Math.min(1.0D, DOOR_COLLISION_MAX_X - x);
        double minY = Math.max(0.0D, DOOR_COLLISION_MIN_Y + yOffset - y);
        double maxY = Math.min(1.0D, DOOR_COLLISION_MAX_Y + yOffset - y);
        double minZ = Math.max(0.0D, DOOR_COLLISION_MIN_Z);
        double maxZ = Math.min(1.0D, DOOR_COLLISION_MAX_Z);
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            return Shapes.empty();
        }
        return rotateShape(Shapes.box(minX, minY, minZ, maxX, maxY, maxZ), facing);
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            VoxelShape box = switch (facing) {
                case EAST -> Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX);
                case SOUTH -> Shapes.box(1.0D - maxX, minY, 1.0D - maxZ, 1.0D - minX, maxY, 1.0D - minZ);
                case WEST -> Shapes.box(minZ, minY, 1.0D - maxX, maxZ, maxY, 1.0D - minX);
                default -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
            };
            rotated[0] = Shapes.or(rotated[0], box);
        });
        return rotated[0];
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
        BlockState visualState = ModBlocks.ZONE_GATE.get().defaultBlockState().setValue(FACING, facing);
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
                    if (level.getBlockState(partPos).is(ModBlocks.ZONE_GATE_PART.get())) {
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
