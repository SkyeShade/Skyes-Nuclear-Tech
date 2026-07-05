package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
import com.skyeshade.skyent.content.blockentity.HeatingChamberBlockEntity;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;

public class HeatingChamberPartBlock extends Block implements ConveyorBeltSurface, ConveyorTravelDirectionProvider, ConveyorVisualFeeder, ConveyorGateSurface {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, 1);
    public static final BooleanProperty LIGHT_BLOCKING = BooleanProperty.create("light_blocking");
    public static final MapCodec<HeatingChamberPartBlock> CODEC = simpleCodec(HeatingChamberPartBlock::new);
    private static final VoxelShape PART_SHAPE = Shapes.block();

    public HeatingChamberPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 0)
                .setValue(PART_Y, 0)
                .setValue(PART_Z, 1)
                .setValue(LIGHT_BLOCKING, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos masterPos = HeatingChamberBlock.getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.HEATING_CHAMBER.get())) {
                HeatingChamberBlock.removeWholeMachine(level, masterPos, !player.isCreative());
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockPos masterPos = HeatingChamberBlock.getMasterPos(state, pos);
        if (level.getBlockState(masterPos).is(ModBlocks.HEATING_CHAMBER.get())) {
            HeatingChamberBlock.requestSharedLightUpdate(level, masterPos);
        } else if (!level.isClientSide) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos masterPos = HeatingChamberBlock.getMasterPos(state, pos);
        return level.getBlockState(masterPos).is(ModBlocks.HEATING_CHAMBER.get()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PART_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PART_SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return shouldBlockLight(state) ? PART_SHAPE : Shapes.empty();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !shouldBlockLight(state);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return shouldBlockLight(state) ? 15 : 0;
    }

    public static boolean shouldBlockLight(BlockState state) {
        return state.hasProperty(LIGHT_BLOCKING) && state.getValue(LIGHT_BLOCKING);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentBlockState, Direction side) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.HEATING_CHAMBER.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z, LIGHT_BLOCKING);
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        return isInternalConveyorPart(state) && level.getBlockState(HeatingChamberBlock.getMasterPos(state, pos)).is(ModBlocks.HEATING_CHAMBER.get());
    }

    @Override
    public Vec3 getTravelLocation(Level level, BlockPos pos, Vec3 itemPos, double speed) {
        Direction direction = skyent$getConveyorTravelDirection(level, pos, level.getBlockState(pos));
        if (direction == null) {
            return itemPos;
        }

        Vec3 snap = getClosestSnappingPosition(level, pos, itemPos);
        Vec3 destination = snap.add(direction.getStepX() * 0.5D, 0.0D, direction.getStepZ() * 0.5D);
        Vec3 motion = destination.subtract(itemPos);
        if (motion.lengthSqr() <= 1.0E-6D) {
            return itemPos;
        }
        return itemPos.add(motion.normalize().scale(Math.min(speed, motion.length())));
    }

    @Override
    public Vec3 getClosestSnappingPosition(Level level, BlockPos pos, Vec3 itemPos) {
        Direction direction = skyent$getConveyorTravelDirection(level, pos, level.getBlockState(pos));
        if (direction == null) {
            return itemPos;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET;
        double z = pos.getZ() + 0.5D;

        if (direction.getAxis() == Direction.Axis.X) {
            x = Mth.clamp(itemPos.x, pos.getX(), pos.getX() + 1.0D);
            z = Mth.lerp(BasicConveyorBeltBlock.ITEM_CENTER_PULL, itemPos.z, z);
        } else {
            x = Mth.lerp(BasicConveyorBeltBlock.ITEM_CENTER_PULL, itemPos.x, x);
            z = Mth.clamp(itemPos.z, pos.getZ(), pos.getZ() + 1.0D);
        }

        return new Vec3(x, y, z);
    }

    @Nullable
    @Override
    public Direction skyent$getConveyorTravelDirection(Level level, BlockPos pos, BlockState state) {
        if (!isInternalConveyorPart(state)) {
            return null;
        }
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        return HeatingChamberBlock.getInternalConveyorDirection(facing);
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        return HeatingChamberBlock.getInternalConveyorDirection(facing) == direction;
    }

    @Override
    public boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        HeatingChamberBlockEntity chamber = getController(level, pos, state);
        if (chamber == null) {
            return false;
        }

        BlockPos sourcePos = pos.relative(fromDirection);
        BlockState sourceState = level.getBlockState(sourcePos);
        if (isInternalConveyorPart(sourceState)
                && HeatingChamberBlock.getMasterPos(sourceState, sourcePos).equals(HeatingChamberBlock.getMasterPos(state, pos))) {
            return chamber.canInternalConveyorMove();
        }

        return chamber.canInternalConveyorAccept();
    }

    @Override
    public boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        HeatingChamberBlockEntity chamber = getController(level, pos, state);
        return chamber != null && chamber.canInternalConveyorMove();
    }

    @Override
    public boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        HeatingChamberBlockEntity chamber = getController(level, pos, state);
        return chamber != null && chamber.canInternalConveyorMove(item);
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, Direction outputDirection) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        HeatingChamberBlockEntity chamber = getController(level, pos, state);
        if (chamber == null) {
            return false;
        }

        BlockPos outputPos = pos.relative(outputDirection);
        BlockState outputState = level.getBlockState(outputPos);
        if (isInternalConveyorPart(outputState)
                && HeatingChamberBlock.getMasterPos(outputState, outputPos).equals(HeatingChamberBlock.getMasterPos(state, pos))) {
            return chamber.canInternalConveyorMove();
        }
        if (!chamber.canInternalConveyorOutput()) {
            return false;
        }
        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            return true;
        }

        return level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                outputPos,
                outputDirection.getOpposite()
        ) != null;
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        HeatingChamberBlockEntity chamber = getController(level, pos, state);
        if (chamber == null) {
            return false;
        }

        BlockPos outputPos = pos.relative(outputDirection);
        BlockState outputState = level.getBlockState(outputPos);
        if (isInternalConveyorPart(outputState)
                && HeatingChamberBlock.getMasterPos(outputState, outputPos).equals(HeatingChamberBlock.getMasterPos(state, pos))) {
            return chamber.canInternalConveyorMove(item);
        }
        if (!chamber.canInternalConveyorOutput(item)) {
            return false;
        }
        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            return true;
        }

        return level.getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                outputPos,
                outputDirection.getOpposite()
        ) != null;
    }

    @Nullable
    @Override
    public Vec3 skyent$getConveyorHoldPosition(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        if (!isInternalConveyorPart(state)) {
            return null;
        }

        BlockPos outputPos = pos.relative(outputDirection);
        BlockState outputState = level.getBlockState(outputPos);
        if (isInternalConveyorPart(outputState)
                && HeatingChamberBlock.getMasterPos(outputState, outputPos).equals(HeatingChamberBlock.getMasterPos(state, pos))) {
            return null;
        }

        return item.position();
    }

    @Nullable
    public static IItemHandler getItemHandler(LevelAccessor level, BlockPos pos, BlockState state, @Nullable Direction side) {
        if (!(level instanceof Level realLevel) || !isInternalConveyorPart(state)) {
            return null;
        }
        return new InternalConveyorItemHandler(realLevel, pos, state, side);
    }

    private static boolean isInternalConveyorPart(BlockState state) {
        return state.is(ModBlocks.HEATING_CHAMBER_PART.get())
                && state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && state.hasProperty(PART_Z)
                && HeatingChamberBlock.isInternalConveyorLocalPos(new BlockPos(
                state.getValue(PART_X),
                state.getValue(PART_Y),
                state.getValue(PART_Z)
        ));
    }

    @Nullable
    private static HeatingChamberBlockEntity getController(Level level, BlockPos pos, BlockState state) {
        BlockPos masterPos = HeatingChamberBlock.getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof HeatingChamberBlockEntity chamber ? chamber : null;
    }

    private static final class InternalConveyorItemHandler implements IItemHandler {
        private static final double INSERT_BACK_OFFSET = 0.45D;
        private static final double INSERT_SIDE_OFFSET = 0.45D;

        private final Level level;
        private final BlockPos pos;
        private final BlockState state;
        @Nullable
        private final Direction insertionSide;

        private InternalConveyorItemHandler(Level level, BlockPos pos, BlockState state, @Nullable Direction insertionSide) {
            this.level = level;
            this.pos = pos;
            this.state = state;
            this.insertionSide = insertionSide;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) {
                return stack;
            }

            Direction direction = getDirection();
            if (insertionSide != null && insertionSide == direction) {
                return stack;
            }
            HeatingChamberBlockEntity chamber = getController(level, pos, state);
            if (chamber == null || !chamber.canInternalConveyorAccept()) {
                return stack;
            }

            Vec3 position = getInsertionPosition(direction);
            if (!hasRoomAt(position)) {
                return stack;
            }

            if (!simulate && !spawnMovingItem(stack, position)) {
                return stack;
            }
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && !stack.isEmpty();
        }

        private Direction getDirection() {
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            return HeatingChamberBlock.getInternalConveyorDirection(facing);
        }

        private Vec3 getInsertionPosition(Direction direction) {
            Direction side = insertionSide == null ? direction.getOpposite() : insertionSide;
            double x = pos.getX() + 0.5D;
            double y = pos.getY() + ConveyorLogicConstants.ITEM_PATH_Y_OFFSET;
            double z = pos.getZ() + 0.5D;

            if (side == direction.getOpposite()) {
                x -= direction.getStepX() * INSERT_BACK_OFFSET;
                z -= direction.getStepZ() * INSERT_BACK_OFFSET;
            } else if (side == direction.getClockWise() || side == direction.getCounterClockWise()) {
                x += side.getStepX() * INSERT_SIDE_OFFSET;
                z += side.getStepZ() * INSERT_SIDE_OFFSET;
            }

            return new Vec3(x, y, z);
        }

        private boolean hasRoomAt(Vec3 position) {
            AABB searchBox = new AABB(position, position).inflate(ConveyorMovingItemEntity.ITEM_SPACING_DISTANCE);
            return level.getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> !entity.isRemoved()).isEmpty();
        }

        private boolean spawnMovingItem(ItemStack stack, Vec3 position) {
            if (level.isClientSide || stack.isEmpty()) {
                return false;
            }

            ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, position.x, position.y, position.z, stack.copy());
            level.addFreshEntity(entity);
            return true;
        }
    }
}
