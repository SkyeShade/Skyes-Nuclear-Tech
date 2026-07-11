package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.RollingMillBlockEntity;
import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorGateSurface;
import com.skyeshade.skyent.content.conveyor.ConveyorLogicConstants;
import com.skyeshade.skyent.content.conveyor.ConveyorTravelDirectionProvider;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RollingMillPartBlock extends Block implements ConveyorBeltSurface, ConveyorTravelDirectionProvider, ConveyorVisualFeeder, ConveyorGateSurface {
    private static final double ROLLING_ITEM_SPEED = 0.015D;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, RollingMillBlock.SIZE_X - 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, RollingMillBlock.SIZE_Y - 1);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, RollingMillBlock.SIZE_Z - 1);
    public static final BooleanProperty LIGHT_BLOCKING = BooleanProperty.create("light_blocking");
    public static final MapCodec<RollingMillPartBlock> CODEC = simpleCodec(RollingMillPartBlock::new);

    public RollingMillPartBlock(BlockBehaviour.Properties properties) {
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
            BlockPos masterPos = RollingMillBlock.getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.ROLLING_MILL.get())) {
                RollingMillBlock.removeWholeMachine(level, masterPos, !player.isCreative());
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockPos masterPos = RollingMillBlock.getMasterPos(state, pos);
        if (!level.getBlockState(masterPos).is(ModBlocks.ROLLING_MILL.get()) && !level.isClientSide) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return RollingMillBlock.shapeForLocal(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(PART_Z), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return RollingMillBlock.shapeForLocal(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(PART_Z), state.getValue(FACING));
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
        return new ItemStack(ModItems.ROLLING_MILL.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z, LIGHT_BLOCKING);
    }

    @Override
    public boolean canItemStay(Level level, BlockPos pos, Vec3 itemPos) {
        BlockState state = level.getBlockState(pos);
        return isInternalConveyorPart(state) && level.getBlockState(RollingMillBlock.getMasterPos(state, pos)).is(ModBlocks.ROLLING_MILL.get());
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
        double travelSpeed = Math.min(speed, ROLLING_ITEM_SPEED);
        return itemPos.add(motion.normalize().scale(Math.min(travelSpeed, motion.length())));
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
        return RollingMillBlock.getInternalConveyorDirection(facing);
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        return RollingMillBlock.getInternalConveyorDirection(facing) == direction;
    }

    @Override
    public boolean skyent$canConveyorItemEnter(Level level, BlockPos pos, BlockState state, Direction fromDirection) {
        if (!isInternalConveyorPart(state)) {
            return false;
        }
        RollingMillBlockEntity rollingMill = getController(level, pos, state);
        return rollingMill != null && rollingMill.canInternalConveyorAccept();
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
        RollingMillBlockEntity rollingMill = getController(level, pos, state);
        return rollingMill != null && rollingMill.canInternalConveyorMove(item);
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
        RollingMillBlockEntity rollingMill = getController(level, pos, state);
        return rollingMill != null && rollingMill.canInternalConveyorOutput(item);
    }

    @Override
    public void skyent$onConveyorItemMoved(Level level, BlockPos pos, BlockState state, ConveyorMovingItemEntity item, Vec3 from, Vec3 to) {
        if (!isInternalConveyorPart(state)) {
            return;
        }
        RollingMillBlockEntity rollingMill = getController(level, pos, state);
        if (rollingMill != null) {
            rollingMill.markInternalItemMoved(item);
        }
    }

    private static boolean isInternalConveyorPart(BlockState state) {
        return state.is(ModBlocks.ROLLING_MILL_PART.get())
                && state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && state.hasProperty(PART_Z)
                && RollingMillBlock.isInternalConveyorLocalPos(new BlockPos(
                state.getValue(PART_X),
                state.getValue(PART_Y),
                state.getValue(PART_Z)
        ));
    }

    @Nullable
    private static RollingMillBlockEntity getController(Level level, BlockPos pos, BlockState state) {
        BlockPos masterPos = RollingMillBlock.getMasterPos(state, pos);
        return level.getBlockEntity(masterPos) instanceof RollingMillBlockEntity rollingMill ? rollingMill : null;
    }
}
