package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.IndustrialPressBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import com.skyeshade.skyent.content.item.WrenchUtil;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IndustrialPressPartBlock extends Block implements ConveyorBeltSurface, ConveyorTravelDirectionProvider, ConveyorVisualFeeder, ConveyorGateSurface {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, 2);
    public static final MapCodec<IndustrialPressPartBlock> CODEC = simpleCodec(IndustrialPressPartBlock::new);

    public IndustrialPressPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_X, 1)
                .setValue(PART_Y, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos masterPos = IndustrialPressBlock.getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.INDUSTRIAL_PRESS.get())) {
                IndustrialPressBlock.removeWholeMachine(level, masterPos, !player.isCreative());
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockPos masterPos = IndustrialPressBlock.getMasterPos(state, pos);
        if (!level.isClientSide && !level.getBlockState(masterPos).is(ModBlocks.INDUSTRIAL_PRESS.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else if (level.getBlockState(masterPos).is(ModBlocks.INDUSTRIAL_PRESS.get())) {
            IndustrialPressBlock.requestSharedLightUpdate(level, masterPos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos masterPos = IndustrialPressBlock.getMasterPos(state, pos);
        return level.getBlockState(masterPos).is(ModBlocks.INDUSTRIAL_PRESS.get()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!WrenchUtil.isWrench(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        BlockPos masterPos = IndustrialPressBlock.getMasterPos(state, pos);
        return IndustrialPressBlock.toggleMode(level, masterPos, player).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return IndustrialPressBlock.shapeForLocal(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return IndustrialPressBlock.shapeForLocal(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(FACING));
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
        return new ItemStack(ModItems.INDUSTRIAL_PRESS.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y);
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        return isInternalConveyorPart(state) && level.getBlockState(IndustrialPressBlock.getMasterPos(state, pos)).is(ModBlocks.INDUSTRIAL_PRESS.get());
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
        return facing;
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        return isInternalConveyorPart(state)
                && state.hasProperty(FACING)
                && state.getValue(FACING) == direction;
    }

    @Override
    public boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        IndustrialPressBlockEntity press = getController(level, pos, state);
        return press != null && press.canInternalConveyorAccept();
    }

    @Override
    public boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state) {
        return isInternalConveyorPart(state);
    }

    @Override
    public boolean skyent$canConveyorItemMove(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        IndustrialPressBlockEntity press = getController(level, pos, state);
        return press != null && press.canInternalConveyorMove(item);
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, Direction outputDirection) {
        return isInternalConveyorPart(state);
    }

    @Override
    public boolean skyent$canConveyorItemOutput(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        IndustrialPressBlockEntity press = getController(level, pos, state);
        return press != null && press.canInternalConveyorOutput(item);
    }

    @Nullable
    @Override
    public Vec3 skyent$getConveyorHoldPosition(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Direction outputDirection) {
        if (!isInternalConveyorPart(state)) {
            return null;
        }
        IndustrialPressBlockEntity press = getController(level, pos, state);
        return press == null ? null : press.getPressHoldPosition();
    }

    private static boolean isInternalConveyorPart(BlockState state) {
        return state.is(ModBlocks.INDUSTRIAL_PRESS_PART.get())
                && state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && IndustrialPressBlock.isInternalConveyorLocalPos(new BlockPos(
                state.getValue(PART_X),
                state.getValue(PART_Y),
                0
        ));
    }

    @Nullable
    private static IndustrialPressBlockEntity getController(Level level, BlockPos pos, BlockState state) {
        BlockPos masterPos = IndustrialPressBlock.getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof IndustrialPressBlockEntity press ? press : null;
    }
}
