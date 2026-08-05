package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.BlastDoorBlockEntity;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlastDoorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty FRAME_ONLY = BooleanProperty.create("frame_only");
    public static final MapCodec<BlastDoorBlock> CODEC = simpleCodec(BlastDoorBlock::new);
    public static final int SIZE_X = 3;
    public static final int SIZE_Y = 3;
    public static final int SIZE_Z = 1;
    // Controller is the bottom-center block of the 3x3x1 door footprint.
    public static final int CONTROLLER_LOCAL_X = 1;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final int CONTROLLER_LOCAL_Z = 0;
    public static final ModelMultiblockDefinition MULTIBLOCK = new ModelMultiblockDefinition(
            ModMultiblockShapes.BLAST_DOOR_FRAME,
            SIZE_X,
            SIZE_Y,
            SIZE_Z,
            new BlockPos(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z),
            2.0D,
            Vec3.ZERO,
            new Vec3(0.0D, 0.0D, -8.0D),
            ModelMultiblockCollisionMode.GENERATED_FRAME_PLUS_DYNAMIC_DOOR,
            ModelMultiblockRenderMode.CONTROLLER_FRAME_AND_BER_DYNAMIC_PARTS
    );
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape FULL_CELL_SHAPE = Shapes.block();
    private static final VoxelShape EMPTY_FRAME_CELL_SHAPE = Shapes.empty();
    // Tuneable closed-panel bounds in 3x3x1 multiblock-local block units.
    // The panel moves upward by DOOR_COLLISION_TRAVEL_Y * openProgress.
    // Keep this in sync with BlastDoorRenderer's BER panel yOffset.
    private static final double DOOR_COLLISION_MIN_X = 0.0D;
    private static final double DOOR_COLLISION_MAX_X = 3.0D;
    private static final double DOOR_COLLISION_MIN_Y = 0.0D;
    private static final double DOOR_COLLISION_MAX_Y = 3.0D;
    private static final double DOOR_COLLISION_MIN_Z = 0.375D;
    private static final double DOOR_COLLISION_MAX_Z = 0.625D;
    private static final double DOOR_COLLISION_TRAVEL_Y = 2.7D;

    public BlastDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(FRAME_ONLY, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastDoorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.BLAST_DOOR.get(), BlastDoorBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.BLAST_DOOR.get(), BlastDoorBlockEntity::serverTick);
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
                ModBlocks.BLAST_DOOR_PART.get().defaultBlockState()
                        .setValue(BlastDoorPartBlock.FACING, partFacing)
                        .setValue(BlastDoorPartBlock.PART_X, x)
                        .setValue(BlastDoorPartBlock.PART_Y, y));
        if (level.getBlockEntity(pos) instanceof BlastDoorBlockEntity blastDoor) {
            blastDoor.updateRedstonePower();
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof BlastDoorBlockEntity blastDoor) {
            blastDoor.updateRedstonePower();
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !REMOVING.get()) {
            removeWholeDoor(level, pos, !player.isCreative());
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
        return shapeForLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, state.getValue(FACING), doorPanelProgress(level, state, pos));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, state.getValue(FACING), doorPanelProgress(level, state, pos));
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
        return new ItemStack(ModItems.BLAST_DOOR.get());
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
        builder.add(FACING, FRAME_ONLY);
    }

    public static void removeWholeDoor(Level level, BlockPos masterPos, boolean drop) {
        if (REMOVING.get()) {
            return;
        }

        REMOVING.set(true);
        try {
            BlockState state = level.getBlockState(masterPos);
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            spawnDestroyParticles(level, masterPos, facing);
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.BLAST_DOOR.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.BLAST_DOOR.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.BLAST_DOOR.get())) {
            return pos;
        }
        if (state.is(ModBlocks.BLAST_DOOR_PART.get())
                && state.hasProperty(BlastDoorPartBlock.FACING)
                && state.hasProperty(BlastDoorPartBlock.PART_X)
                && state.hasProperty(BlastDoorPartBlock.PART_Y)) {
            BlockPos local = new BlockPos(
                    state.getValue(BlastDoorPartBlock.PART_X),
                    state.getValue(BlastDoorPartBlock.PART_Y),
                    CONTROLLER_LOCAL_Z
            );
            return ModelMultiblocks.masterPosFromLocal(MULTIBLOCK, pos, local, state.getValue(BlastDoorPartBlock.FACING));
        }
        return pos;
    }

    public static Optional<BlastDoorBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof BlastDoorBlockEntity blastDoor
                ? Optional.of(blastDoor)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.BLAST_DOOR_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.BLAST_DOOR.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z);
    }

    public static VoxelShape shapeForLocal(int x, int y, Direction facing, double openProgress) {
        VoxelShape frame = frameShapeForLocal(x, y, facing);
        if (openProgress >= 1.0D) {
            return frame;
        }

        VoxelShape panel = doorPanelShapeForLocal(x, y, facing, openProgress * DOOR_COLLISION_TRAVEL_Y);
        return panel.isEmpty() ? frame : Shapes.or(frame, panel);
    }

    public static VoxelShape radiationDoorPanelShapeForState(BlockState state, double openProgress) {
        if (openProgress >= 1.0D) {
            return Shapes.empty();
        }

        Direction facing = state.hasProperty(FACING)
                ? state.getValue(FACING)
                : state.getValue(BlastDoorPartBlock.FACING);
        int localX = state.hasProperty(BlastDoorPartBlock.PART_X)
                ? state.getValue(BlastDoorPartBlock.PART_X)
                : CONTROLLER_LOCAL_X;
        int localY = state.hasProperty(BlastDoorPartBlock.PART_Y)
                ? state.getValue(BlastDoorPartBlock.PART_Y)
                : CONTROLLER_LOCAL_Y;
        return doorPanelShapeForLocal(localX, localY, facing, openProgress * DOOR_COLLISION_TRAVEL_Y);
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

    public static boolean isOpenPassageLocal(int localX, int localY) {
        return localX == CONTROLLER_LOCAL_X && localY < SIZE_Y - 1;
    }

    public static double doorPanelProgress(BlockGetter level, BlockState state, BlockPos pos) {
        if (level instanceof LevelAccessor levelAccessor) {
            return getMasterBlockEntity(levelAccessor, state, pos)
                    .map(BlastDoorBlockEntity::getOpenProgress)
                    .orElse(0.0F);
        }
        return 0.0D;
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        return ModelMultiblocks.rotateLocalOffset(MULTIBLOCK, local, facing);
    }

    private static boolean isControllerLocalPos(int x, int y, int z) {
        return MULTIBLOCK.isControllerLocal(x, y, z);
    }

    private static VoxelShape frameShapeForLocal(int x, int y, Direction facing) {
        return ModelMultiblocks.generatedShapeForLocal(
                MULTIBLOCK,
                facing,
                x,
                y,
                CONTROLLER_LOCAL_Z,
                (fallbackFacing, fallbackX, fallbackY, fallbackZ) -> isOpenPassageLocal(fallbackX, fallbackY) ? EMPTY_FRAME_CELL_SHAPE : FULL_CELL_SHAPE
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

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.BLAST_DOOR.get().defaultBlockState().setValue(FACING, facing);
        ModelMultiblocks.spawnDestroyParticles(MULTIBLOCK, level, masterPos, facing, visualState);
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        ModelMultiblocks.removeParts(MULTIBLOCK, level, masterPos, facing, ModBlocks.BLAST_DOOR_PART.get());
    }
}
