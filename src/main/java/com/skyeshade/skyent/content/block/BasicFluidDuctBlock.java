package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.BasicFluidDuctBlockEntity;
import com.skyeshade.skyent.content.item.WrenchUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

public class BasicFluidDuctBlock extends BaseEntityBlock {
    public static final BooleanProperty NORTH = BooleanProperty.create("north");
    public static final BooleanProperty SOUTH = BooleanProperty.create("south");
    public static final BooleanProperty EAST = BooleanProperty.create("east");
    public static final BooleanProperty WEST = BooleanProperty.create("west");
    public static final BooleanProperty UP = BooleanProperty.create("up");
    public static final BooleanProperty DOWN = BooleanProperty.create("down");
    public static final BooleanProperty DISABLED_NORTH = BooleanProperty.create("disabled_north");
    public static final BooleanProperty DISABLED_SOUTH = BooleanProperty.create("disabled_south");
    public static final BooleanProperty DISABLED_EAST = BooleanProperty.create("disabled_east");
    public static final BooleanProperty DISABLED_WEST = BooleanProperty.create("disabled_west");
    public static final BooleanProperty DISABLED_UP = BooleanProperty.create("disabled_up");
    public static final BooleanProperty DISABLED_DOWN = BooleanProperty.create("disabled_down");
    public static final MapCodec<BasicFluidDuctBlock> CODEC = simpleCodec(BasicFluidDuctBlock::new);

    private static final VoxelShape CORE_SHAPE = Block.box(5.0D, 5.0D, 5.0D, 11.0D, 11.0D, 11.0D);
    private static final VoxelShape NORTH_ARM_SHAPE = Block.box(5.0D, 5.0D, 0.0D, 11.0D, 11.0D, 5.0D);
    private static final VoxelShape SOUTH_ARM_SHAPE = Block.box(5.0D, 5.0D, 11.0D, 11.0D, 11.0D, 16.0D);
    private static final VoxelShape WEST_ARM_SHAPE = Block.box(0.0D, 5.0D, 5.0D, 5.0D, 11.0D, 11.0D);
    private static final VoxelShape EAST_ARM_SHAPE = Block.box(11.0D, 5.0D, 5.0D, 16.0D, 11.0D, 11.0D);
    private static final VoxelShape DOWN_ARM_SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 5.0D, 11.0D);
    private static final VoxelShape UP_ARM_SHAPE = Block.box(5.0D, 11.0D, 5.0D, 11.0D, 16.0D, 11.0D);
    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);
    private static final Map<Direction, BooleanProperty> DISABLED_PROPERTY_BY_DIRECTION = new EnumMap<>(Direction.class);
    private static final Map<Direction, VoxelShape> SHAPE_BY_DIRECTION = new EnumMap<>(Direction.class);

    static {
        PROPERTY_BY_DIRECTION.put(Direction.NORTH, NORTH);
        PROPERTY_BY_DIRECTION.put(Direction.SOUTH, SOUTH);
        PROPERTY_BY_DIRECTION.put(Direction.EAST, EAST);
        PROPERTY_BY_DIRECTION.put(Direction.WEST, WEST);
        PROPERTY_BY_DIRECTION.put(Direction.UP, UP);
        PROPERTY_BY_DIRECTION.put(Direction.DOWN, DOWN);

        DISABLED_PROPERTY_BY_DIRECTION.put(Direction.NORTH, DISABLED_NORTH);
        DISABLED_PROPERTY_BY_DIRECTION.put(Direction.SOUTH, DISABLED_SOUTH);
        DISABLED_PROPERTY_BY_DIRECTION.put(Direction.EAST, DISABLED_EAST);
        DISABLED_PROPERTY_BY_DIRECTION.put(Direction.WEST, DISABLED_WEST);
        DISABLED_PROPERTY_BY_DIRECTION.put(Direction.UP, DISABLED_UP);
        DISABLED_PROPERTY_BY_DIRECTION.put(Direction.DOWN, DISABLED_DOWN);

        SHAPE_BY_DIRECTION.put(Direction.NORTH, NORTH_ARM_SHAPE);
        SHAPE_BY_DIRECTION.put(Direction.SOUTH, SOUTH_ARM_SHAPE);
        SHAPE_BY_DIRECTION.put(Direction.EAST, EAST_ARM_SHAPE);
        SHAPE_BY_DIRECTION.put(Direction.WEST, WEST_ARM_SHAPE);
        SHAPE_BY_DIRECTION.put(Direction.UP, UP_ARM_SHAPE);
        SHAPE_BY_DIRECTION.put(Direction.DOWN, DOWN_ARM_SHAPE);
    }

    public BasicFluidDuctBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(SOUTH, false)
                .setValue(EAST, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(DISABLED_NORTH, false)
                .setValue(DISABLED_SOUTH, false)
                .setValue(DISABLED_EAST, false)
                .setValue(DISABLED_WEST, false)
                .setValue(DISABLED_UP, false)
                .setValue(DISABLED_DOWN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (level instanceof Level realLevel) {
            return state.setValue(propertyFor(direction), canConnect(state, realLevel, pos, direction));
        }

        return state.setValue(propertyFor(direction), !isSideDisabled(state, direction) && neighborState.is(ModBlocks.BASIC_FLUID_DUCT.get()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            BlockState updatedState = updateConnections(state, level, pos);
            if (updatedState != state) {
                level.setBlock(pos, updatedState, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!WrenchUtil.isWrench(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) {
            return pickupWithWrench(level, pos, player).consumesAction()
                    ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                    : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        return useWrench(state, level, pos, player, hitResult).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    public static InteractionResult useWrench(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        DuctHitPart hitPart = getHitPart(state, pos, hitResult);
        if (hitPart.direction != null && isArmVisible(state, hitPart.direction)) {
            return setConnectionDisabledSymmetric(level, pos, state, hitPart.direction, true);
        }

        if (hitPart.coreHit) {
            Direction direction = hitResult.getDirection();
            if (isSideDisabled(state, direction) || isOppositeAdjacentDuctSideDisabled(level, pos, direction)) {
                return setConnectionDisabledSymmetric(level, pos, state, direction, false);
            }
        }

        return InteractionResult.PASS;
    }

    public static InteractionResult pickupWithWrench(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = new ItemStack(ModBlocks.BASIC_FLUID_DUCT.get().asItem());
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.updateNeighborsAt(pos, Blocks.AIR);
        if (!player.getAbilities().instabuild) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BasicFluidDuctBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(
                NORTH,
                SOUTH,
                EAST,
                WEST,
                UP,
                DOWN,
                DISABLED_NORTH,
                DISABLED_SOUTH,
                DISABLED_EAST,
                DISABLED_WEST,
                DISABLED_UP,
                DISABLED_DOWN
        );
    }

    private static BlockState updateConnections(BlockState state, Level level, BlockPos pos) {
        BlockState updatedState = state;
        for (Direction direction : Direction.values()) {
            updatedState = updatedState.setValue(propertyFor(direction), canConnect(updatedState, level, pos, direction));
        }

        return updatedState;
    }

    private static boolean canConnect(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (isSideDisabled(state, direction)) {
            return false;
        }

        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.is(ModBlocks.BASIC_FLUID_DUCT.get())) {
            return !isSideDisabled(neighborState, direction.getOpposite());
        }

        return level.getCapability(Capabilities.FluidHandler.BLOCK, neighborPos, direction.getOpposite()) != null;
    }

    public static boolean canUseSide(BlockState state, Direction direction) {
        return state.is(ModBlocks.BASIC_FLUID_DUCT.get()) && !isSideDisabled(state, direction);
    }

    public static boolean canConnectDucts(BlockState state, Direction direction, BlockState neighborState) {
        return state.is(ModBlocks.BASIC_FLUID_DUCT.get())
                && neighborState.is(ModBlocks.BASIC_FLUID_DUCT.get())
                && !isSideDisabled(state, direction)
                && !isSideDisabled(neighborState, direction.getOpposite());
    }

    public static boolean isSideDisabled(BlockState state, Direction direction) {
        return state.hasProperty(disabledPropertyFor(direction)) && state.getValue(disabledPropertyFor(direction));
    }

    private static BlockState setSideDisabled(BlockState state, Direction direction, boolean disabled) {
        return state
                .setValue(disabledPropertyFor(direction), disabled)
                .setValue(propertyFor(direction), false);
    }

    private static VoxelShape shapeFor(BlockState state) {
        VoxelShape shape = CORE_SHAPE;
        for (Direction direction : Direction.values()) {
            if (isArmVisible(state, direction)) {
                shape = Shapes.or(shape, SHAPE_BY_DIRECTION.get(direction));
            }
        }

        return shape;
    }

    private static InteractionResult setConnectionDisabledSymmetric(Level level, BlockPos pos, BlockState state, Direction direction, boolean disabled) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockState updatedState = setSideDisabled(state, direction, disabled);
        if (!disabled) {
            updatedState = updateConnections(updatedState, level, pos);
        }

        level.setBlock(pos, updatedState, Block.UPDATE_ALL);

        BlockPos neighborPos = pos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);
        if (neighborState.is(ModBlocks.BASIC_FLUID_DUCT.get())) {
            BlockState updatedNeighborState = setSideDisabled(neighborState, direction.getOpposite(), disabled);
            if (!disabled) {
                updatedNeighborState = updateConnections(updatedNeighborState, level, neighborPos);
            }
            level.setBlock(neighborPos, updatedNeighborState, Block.UPDATE_ALL);
            level.updateNeighborsAt(neighborPos, updatedNeighborState.getBlock());
        } else {
            level.neighborChanged(neighborPos, updatedState.getBlock(), pos);
        }

        level.updateNeighborsAt(pos, updatedState.getBlock());
        level.playSound(null, pos, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.BLOCKS, 0.45F, disabled ? 0.75F : 1.1F);
        return InteractionResult.CONSUME;
    }

    private static boolean isOppositeAdjacentDuctSideDisabled(Level level, BlockPos pos, Direction direction) {
        BlockState neighborState = level.getBlockState(pos.relative(direction));
        return neighborState.is(ModBlocks.BASIC_FLUID_DUCT.get()) && isSideDisabled(neighborState, direction.getOpposite());
    }

    private static DuctHitPart getHitPart(BlockState state, BlockPos pos, BlockHitResult hitResult) {
        Vec3 local = hitResult.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Direction direction : Direction.values()) {
            if (isArmVisible(state, direction) && contains(SHAPE_BY_DIRECTION.get(direction), local)) {
                return DuctHitPart.arm(direction);
            }
        }

        if (contains(CORE_SHAPE, local)) {
            return DuctHitPart.core();
        }

        return DuctHitPart.none();
    }

    private static boolean contains(VoxelShape shape, Vec3 local) {
        double epsilon = 1.0E-5D;
        for (AABB box : shape.toAabbs()) {
            if (local.x >= box.minX - epsilon && local.x <= box.maxX + epsilon
                    && local.y >= box.minY - epsilon && local.y <= box.maxY + epsilon
                    && local.z >= box.minZ - epsilon && local.z <= box.maxZ + epsilon) {
                return true;
            }
        }

        return false;
    }

    private static BooleanProperty propertyFor(Direction direction) {
        return PROPERTY_BY_DIRECTION.get(direction);
    }

    private static boolean isArmVisible(BlockState state, Direction direction) {
        return state.hasProperty(propertyFor(direction)) && state.getValue(propertyFor(direction));
    }

    private static BooleanProperty disabledPropertyFor(Direction direction) {
        return DISABLED_PROPERTY_BY_DIRECTION.get(direction);
    }

    private record DuctHitPart(boolean coreHit, @Nullable Direction direction) {
        private static DuctHitPart core() {
            return new DuctHitPart(true, null);
        }

        private static DuctHitPart arm(Direction direction) {
            return new DuctHitPart(false, direction);
        }

        private static DuctHitPart none() {
            return new DuctHitPart(false, null);
        }
    }
}
