package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.WireMillBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.recipe.WireMillRecipes;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandler;

public class WireMillPartBlock extends Block implements ConveyorBeltSurface, ConveyorTravelDirectionProvider, ConveyorVisualFeeder, ConveyorGateSurface {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, WireMillBlock.LENGTH - 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, WireMillBlock.HEIGHT - 1);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, WireMillBlock.WIDTH - 1);
    public static final MapCodec<WireMillPartBlock> CODEC = simpleCodec(WireMillPartBlock::new);
    private static final VoxelShape SHAPE = Shapes.block();

    public WireMillPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 0)
                .setValue(PART_Y, 0)
                .setValue(PART_Z, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos masterPos = WireMillBlock.getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.WIRE_MILL.get())) {
                WireMillBlock.removeWholeMachine(level, masterPos, !player.isCreative());
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockPos masterPos = WireMillBlock.getMasterPos(state, pos);
        if (!level.getBlockState(masterPos).is(ModBlocks.WIRE_MILL.get()) && !level.isClientSide) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
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
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.WIRE_MILL.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z);
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        return isInputConveyorPart(state) && level.getBlockState(WireMillBlock.getMasterPos(state, pos)).is(ModBlocks.WIRE_MILL.get());
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
        return isInputConveyorPart(state) ? state.getValue(FACING) : null;
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        if (isInputConveyorPart(state)) {
            return state.getValue(FACING) == direction;
        }
        if (isOutputPortPart(state)) {
            return state.getValue(FACING).getCounterClockWise() == direction;
        }
        return false;
    }

    @Override
    public boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        WireMillBlockEntity wireMill = getController(level, pos, state);
        return isInputConveyorPart(state) && wireMill != null && wireMill.canInputConveyorAccept();
    }

    @Override
    public boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item) {
        WireMillBlockEntity wireMill = getController(level, pos, state);
        return isInputConveyorPart(state) && wireMill != null && wireMill.canInputConveyorMove(item);
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        return false;
    }

    @Nullable
    public static IItemHandler getItemHandler(LevelAccessor level, BlockPos pos, BlockState state, @Nullable Direction side) {
        if (!(level instanceof Level realLevel) || !isInputConveyorPart(state)) {
            return null;
        }
        return new InputConveyorItemHandler(realLevel, pos, state, side);
    }

    private static boolean isInputConveyorPart(BlockState state) {
        return state.is(ModBlocks.WIRE_MILL_PART.get())
                && state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && state.hasProperty(PART_Z)
                && state.getValue(PART_X) == WireMillBlock.PLACEMENT_ANCHOR_LOCAL_X
                && state.getValue(PART_Y) == WireMillBlock.PLACEMENT_ANCHOR_LOCAL_Y + 1
                && state.getValue(PART_Z) == WireMillBlock.PLACEMENT_ANCHOR_LOCAL_Z;
    }

    private static boolean isOutputPortPart(BlockState state) {
        if (!state.is(ModBlocks.WIRE_MILL_PART.get())
                || !state.hasProperty(PART_X)
                || !state.hasProperty(PART_Y)
                || !state.hasProperty(PART_Z)) {
            return false;
        }
        int x = state.getValue(PART_X);
        return state.getValue(PART_Y) == 1
                && state.getValue(PART_Z) == 0
                && x >= 1
                && x <= 4;
    }

    @Nullable
    private static WireMillBlockEntity getController(Level level, BlockPos pos, BlockState state) {
        BlockPos masterPos = WireMillBlock.getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof WireMillBlockEntity wireMill ? wireMill : null;
    }

    private static final class InputConveyorItemHandler implements IItemHandler {
        private static final double INSERT_BACK_OFFSET = 0.45D;
        private static final double INSERT_SIDE_OFFSET = 0.45D;

        private final Level level;
        private final BlockPos pos;
        private final BlockState state;
        @Nullable
        private final Direction insertionSide;

        private InputConveyorItemHandler(Level level, BlockPos pos, BlockState state, @Nullable Direction insertionSide) {
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
            if (slot != 0 || stack.isEmpty() || !WireMillRecipes.isWireInput(stack)) {
                return stack;
            }
            Direction direction = getDirection();
            if (insertionSide != null && insertionSide == direction) {
                return stack;
            }
            WireMillBlockEntity wireMill = getController(level, pos, state);
            if (wireMill == null || !wireMill.canInputConveyorAccept()) {
                return stack;
            }
            int acceptedCount = Math.min(stack.getCount(), wireMill.getFreeInputQueueSlots());
            if (acceptedCount <= 0) {
                return stack;
            }

            Vec3 position = getInsertionPosition(direction);
            if (!hasRoomAt(position)) {
                return stack;
            }

            if (!simulate) {
                ConveyorMovingItemEntity entity = new ConveyorMovingItemEntity(level, position.x, position.y, position.z, stack.copyWithCount(acceptedCount));
                level.addFreshEntity(entity);
            }
            if (acceptedCount >= stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(acceptedCount);
            return remainder;
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
            return slot == 0 && WireMillRecipes.isWireInput(stack);
        }

        private Direction getDirection() {
            return state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
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
    }
}
