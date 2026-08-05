package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.ArcFurnaceBlockEntity;
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
import net.minecraft.util.Mth;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ArcFurnaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<ArcFurnaceBlock> CODEC = simpleCodec(ArcFurnaceBlock::new);
    public static final int SIZE_X = 3;
    public static final int SIZE_Y = 4;
    public static final int SIZE_Z = 3;
    public static final int CONTROLLER_LOCAL_X = 1;
    public static final int CONTROLLER_LOCAL_Y = 0;
    public static final int CONTROLLER_LOCAL_Z = 0;
    public static final ModelMultiblockDefinition MULTIBLOCK = new ModelMultiblockDefinition(
            ModMultiblockShapes.ARC_FURNACE,
            SIZE_X,
            SIZE_Y,
            SIZE_Z,
            new BlockPos(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z),
            2.0D,
            Vec3.ZERO,
            new Vec3(0.0D, 0.0D, 16.0D),
            ModelMultiblockCollisionMode.CUSTOM,
            ModelMultiblockRenderMode.BER_FULL_MODEL
    );
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape SOLID_SHAPE = Shapes.block();
    private static final double EXTERNAL_LADDER_THICKNESS = 0.35D;
    private static final double EXTERNAL_LADDER_BLOCK_OVERLAP = 0.0625D;

    public ArcFurnaceBlock(Properties properties) {
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
        return new ArcFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.ARC_FURNACE.get(), ArcFurnaceBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.ARC_FURNACE.get(), ArcFurnaceBlockEntity::serverTick);
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
                ModBlocks.ARC_FURNACE_PART.get().defaultBlockState()
                        .setValue(ArcFurnacePartBlock.FACING, partFacing)
                        .setValue(ArcFurnacePartBlock.PART_X, x)
                        .setValue(ArcFurnacePartBlock.PART_Y, y)
                        .setValue(ArcFurnacePartBlock.PART_Z, z));
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
        return shapeForLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z, state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (level.getBlockEntity(pos) instanceof ArcFurnaceBlockEntity furnace && player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(furnace, pos);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return isLadderLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z, state.getValue(FACING), pos, entity);
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
        return new ItemStack(ModItems.ARC_FURNACE.get());
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

    public static VoxelShape shapeForLocal(int x, int y, int z, Direction facing) {
        if (y == 1 || y == 2) {
            return SOLID_SHAPE;
        }
        return ModelMultiblocks.generatedShapeForLocal(MULTIBLOCK, facing, x, y, z, (fallbackFacing, fallbackX, fallbackY, fallbackZ) -> SOLID_SHAPE);
    }

    public static boolean isLadderLocal(int x, int y, int z, Direction facing, BlockPos pos, @Nullable LivingEntity entity) {
        if (x != 0 || z != 1 || y < 0 || y >= SIZE_Y) {
            return false;
        }
        return entity == null || isTouchingLocalWestSide(pos, entity, localWestSide(facing));
    }

    private static Direction localWestSide(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static boolean isTouchingLocalWestSide(BlockPos pos, LivingEntity entity, Direction worldSide) {
        return externalLadderZone(pos, worldSide).intersects(entity.getBoundingBox());
    }

    public static void tickExternalLadder(LivingEntity entity) {
        if (entity.isSpectator()) {
            return;
        }

        AABB searchBounds = entity.getBoundingBox().inflate(EXTERNAL_LADDER_THICKNESS + 0.1D);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = Mth.floor(searchBounds.minY); y <= Mth.floor(searchBounds.maxY); y++) {
            for (int x = Mth.floor(searchBounds.minX); x <= Mth.floor(searchBounds.maxX); x++) {
                for (int z = Mth.floor(searchBounds.minZ); z <= Mth.floor(searchBounds.maxZ); z++) {
                    cursor.set(x, y, z);
                    BlockState state = entity.level().getBlockState(cursor);
                    if (state.is(ModBlocks.ARC_FURNACE.get())) {
                        if (isLadderLocal(CONTROLLER_LOCAL_X, CONTROLLER_LOCAL_Y, CONTROLLER_LOCAL_Z, state.getValue(FACING), cursor, entity)) {
                            applyExternalLadderMotion(entity);
                            return;
                        }
                    } else if (state.is(ModBlocks.ARC_FURNACE_PART.get())) {
                        if (isLadderLocal(
                                state.getValue(ArcFurnacePartBlock.PART_X),
                                state.getValue(ArcFurnacePartBlock.PART_Y),
                                state.getValue(ArcFurnacePartBlock.PART_Z),
                                state.getValue(ArcFurnacePartBlock.FACING),
                                cursor,
                                entity
                        )) {
                            applyExternalLadderMotion(entity);
                            return;
                        }
                    }
                }
            }
        }
    }

    private static AABB externalLadderZone(BlockPos pos, Direction worldSide) {
        return switch (worldSide) {
            case WEST -> new AABB(
                    pos.getX() - EXTERNAL_LADDER_THICKNESS,
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + EXTERNAL_LADDER_BLOCK_OVERLAP,
                    pos.getY() + 1.0D,
                    pos.getZ() + 1.0D
            );
            case EAST -> new AABB(
                    pos.getX() + 1.0D - EXTERNAL_LADDER_BLOCK_OVERLAP,
                    pos.getY(),
                    pos.getZ(),
                    pos.getX() + 1.0D + EXTERNAL_LADDER_THICKNESS,
                    pos.getY() + 1.0D,
                    pos.getZ() + 1.0D
            );
            case NORTH -> new AABB(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ() - EXTERNAL_LADDER_THICKNESS,
                    pos.getX() + 1.0D,
                    pos.getY() + 1.0D,
                    pos.getZ() + EXTERNAL_LADDER_BLOCK_OVERLAP
            );
            case SOUTH -> new AABB(
                    pos.getX(),
                    pos.getY(),
                    pos.getZ() + 1.0D - EXTERNAL_LADDER_BLOCK_OVERLAP,
                    pos.getX() + 1.0D,
                    pos.getY() + 1.0D,
                    pos.getZ() + 1.0D + EXTERNAL_LADDER_THICKNESS
            );
            default -> new AABB(pos);
        };
    }

    private static void applyExternalLadderMotion(LivingEntity entity) {
        entity.resetFallDistance();
        Vec3 movement = entity.getDeltaMovement();
        double x = Mth.clamp(movement.x, -0.15D, 0.15D);
        double z = Mth.clamp(movement.z, -0.15D, 0.15D);
        double y = Math.max(movement.y, -0.15D);
        if (y < 0.0D && entity.isSuppressingSlidingDownLadder() && entity instanceof Player) {
            y = 0.0D;
        }
        if (entity.horizontalCollision) {
            y = Math.max(y, 0.2D);
        }
        entity.setDeltaMovement(x, y, z);
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
            if (level.getBlockEntity(masterPos) instanceof ArcFurnaceBlockEntity furnace) {
                furnace.dropContents(level, masterPos);
            }
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.ARC_FURNACE.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.ARC_FURNACE.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.ARC_FURNACE.get())) {
            return pos;
        }
        if (state.is(ModBlocks.ARC_FURNACE_PART.get())
                && state.hasProperty(ArcFurnacePartBlock.FACING)
                && state.hasProperty(ArcFurnacePartBlock.PART_X)
                && state.hasProperty(ArcFurnacePartBlock.PART_Y)
                && state.hasProperty(ArcFurnacePartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(ArcFurnacePartBlock.PART_X),
                    state.getValue(ArcFurnacePartBlock.PART_Y),
                    state.getValue(ArcFurnacePartBlock.PART_Z)
            );
            return ModelMultiblocks.masterPosFromLocal(MULTIBLOCK, pos, local, state.getValue(ArcFurnacePartBlock.FACING));
        }
        return pos;
    }

    public static Optional<ArcFurnaceBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof ArcFurnaceBlockEntity furnace
                ? Optional.of(furnace)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.ARC_FURNACE_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.ARC_FURNACE.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z);
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        return ModelMultiblocks.rotateLocalOffset(MULTIBLOCK, local, facing);
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.ARC_FURNACE.get().defaultBlockState().setValue(FACING, facing);
        ModelMultiblocks.spawnDestroyParticles(MULTIBLOCK, level, masterPos, facing, visualState);
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        ModelMultiblocks.removeParts(MULTIBLOCK, level, masterPos, facing, ModBlocks.ARC_FURNACE_PART.get());
    }
}
