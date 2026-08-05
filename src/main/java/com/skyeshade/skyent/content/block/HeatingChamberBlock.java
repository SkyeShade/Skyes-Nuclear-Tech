package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.HeatingChamberBlockEntity;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockCollisionMode;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockDefinition;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockOrientation;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockRenderMode;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class HeatingChamberBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<HeatingChamberBlock> CODEC = simpleCodec(HeatingChamberBlock::new);
    public static final int SIZE_X = 2;
    public static final int SIZE_Y = 3;
    public static final int SIZE_Z = 2;
    public static final ModelMultiblockDefinition MULTIBLOCK = new ModelMultiblockDefinition(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "heating_chamber"),
            SIZE_X,
            SIZE_Y,
            SIZE_Z,
            BlockPos.ZERO,
            2.0D,
            Vec3.ZERO,
            Vec3.ZERO,
            ModelMultiblockCollisionMode.SOLID,
            ModelMultiblockRenderMode.CONTROLLER_BLOCK_MODEL,
            ModelMultiblockOrientation.CARDINAL_ROTATION
    );
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    private static final VoxelShape MASTER_SHAPE = Shapes.block();

    public HeatingChamberBlock(BlockBehaviour.Properties properties) {
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
        return new HeatingChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.HEATING_CHAMBER.get(), HeatingChamberBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.HEATING_CHAMBER.get(), HeatingChamberBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getCounterClockWise();
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
                ModBlocks.HEATING_CHAMBER_PART.get().defaultBlockState()
                        .setValue(HeatingChamberPartBlock.FACING, partFacing)
                        .setValue(HeatingChamberPartBlock.PART_X, x)
                        .setValue(HeatingChamberPartBlock.PART_Y, y)
                        .setValue(HeatingChamberPartBlock.PART_Z, z)
                        .setValue(HeatingChamberPartBlock.LIGHT_BLOCKING, shouldPartBlockLight(x, y, z)));
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MASTER_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MASTER_SHAPE;
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
        return new ItemStack(ModItems.HEATING_CHAMBER.get());
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
            if (level.getBlockEntity(masterPos) instanceof HeatingChamberBlockEntity blockEntity) {
                blockEntity.dropInternalConveyorItems();
            }
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.HEATING_CHAMBER.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.HEATING_CHAMBER.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.HEATING_CHAMBER.get())) {
            return pos;
        }
        if (state.is(ModBlocks.HEATING_CHAMBER_PART.get())
                && state.hasProperty(HeatingChamberPartBlock.FACING)
                && state.hasProperty(HeatingChamberPartBlock.PART_X)
                && state.hasProperty(HeatingChamberPartBlock.PART_Y)
                && state.hasProperty(HeatingChamberPartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(HeatingChamberPartBlock.PART_X),
                    state.getValue(HeatingChamberPartBlock.PART_Y),
                    state.getValue(HeatingChamberPartBlock.PART_Z)
            );
            return ModelMultiblocks.masterPosFromLocal(MULTIBLOCK, pos, local, state.getValue(HeatingChamberPartBlock.FACING));
        }
        return pos;
    }

    public static Optional<HeatingChamberBlockEntity> getMasterBlockEntity(LevelAccessor level, BlockState state, BlockPos pos) {
        BlockPos masterPos = getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof HeatingChamberBlockEntity chamber
                ? Optional.of(chamber)
                : Optional.empty();
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.HEATING_CHAMBER_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.HEATING_CHAMBER.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return ModelMultiblocks.localToWorld(MULTIBLOCK, origin, facing, x, y, z);
    }

    public static boolean isInternalConveyorLocalPos(BlockPos local) {
        return local.getX() == 0 && local.getY() == 1 && (local.getZ() == 0 || local.getZ() == 1);
    }

    public static boolean isConnectorSupportCell(BlockState state) {
        return state.is(ModBlocks.HEATING_CHAMBER.get()) || state.is(ModBlocks.HEATING_CHAMBER_PART.get());
    }

    public static BlockPos[] getInternalConveyorWorldPositions(Direction facing, BlockPos controllerPos) {
        return new BlockPos[] {
                localToWorld(controllerPos, facing, 0, 1, 1),
                localToWorld(controllerPos, facing, 0, 1, 0)
        };
    }

    public static Direction getInternalConveyorDirection(Direction machineFacing) {
        return machineFacing;
    }

    public static void requestSharedLightUpdate(Level level, BlockPos masterPos) {
        BlockState state = level.getBlockState(masterPos);
        if (!state.is(ModBlocks.HEATING_CHAMBER.get())) {
            return;
        }
        if (level.getBlockEntity(masterPos) instanceof HeatingChamberBlockEntity blockEntity) {
            blockEntity.requestModelDataUpdate();
            blockEntity.refreshSharedLight(true);
        }
        level.sendBlockUpdated(masterPos, state, state, Block.UPDATE_CLIENTS);
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        return ModelMultiblocks.rotateLocalOffset(MULTIBLOCK, local, facing);
    }

    private static boolean shouldPartBlockLight(int x, int y, int z) {
        return false;
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.HEATING_CHAMBER.get().defaultBlockState().setValue(FACING, facing);
        ModelMultiblocks.spawnDestroyParticles(MULTIBLOCK, level, masterPos, facing, visualState);
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        ModelMultiblocks.removeParts(MULTIBLOCK, level, masterPos, facing, ModBlocks.HEATING_CHAMBER_PART.get());
    }
}
