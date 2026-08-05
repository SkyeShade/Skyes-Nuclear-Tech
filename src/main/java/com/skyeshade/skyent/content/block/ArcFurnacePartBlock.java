package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.conveyor.ConveyorVisualFeeder;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ArcFurnacePartBlock extends Block implements ConveyorVisualFeeder {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_X = IntegerProperty.create("part_x", 0, ArcFurnaceBlock.SIZE_X - 1);
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 0, ArcFurnaceBlock.SIZE_Y - 1);
    public static final IntegerProperty PART_Z = IntegerProperty.create("part_z", 0, ArcFurnaceBlock.SIZE_Z - 1);
    public static final MapCodec<ArcFurnacePartBlock> CODEC = simpleCodec(ArcFurnacePartBlock::new);

    public ArcFurnacePartBlock(Properties properties) {
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
            BlockPos masterPos = ArcFurnaceBlock.getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.ARC_FURNACE.get())) {
                ArcFurnaceBlock.removeWholeMachine(level, masterPos, !player.isCreative());
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockPos masterPos = ArcFurnaceBlock.getMasterPos(state, pos);
        if (!level.getBlockState(masterPos).is(ModBlocks.ARC_FURNACE.get()) && !level.isClientSide) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ArcFurnaceBlock.shapeForLocal(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(PART_Z), state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ArcFurnaceBlock.shapeForLocal(state.getValue(PART_X), state.getValue(PART_Y), state.getValue(PART_Z), state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos masterPos = ArcFurnaceBlock.getMasterPos(state, pos);
        return ArcFurnaceBlock.getMasterBlockEntity(level, state, pos)
                .map(furnace -> {
                    if (player instanceof ServerPlayer serverPlayer) {
                        serverPlayer.openMenu(furnace, masterPos);
                    }
                    return InteractionResult.CONSUME;
                })
                .orElse(InteractionResult.PASS);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return ArcFurnaceBlock.isLadderLocal(
                state.getValue(PART_X),
                state.getValue(PART_Y),
                state.getValue(PART_Z),
                state.getValue(FACING),
                pos,
                entity
        );
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_X, PART_Y, PART_Z);
    }

    @Override
    public boolean skyent$feedsConveyorToward(BlockState state, Direction direction) {
        return isOutputItemPortPart(state) && state.getValue(FACING) == direction;
    }

    private static boolean isOutputItemPortPart(BlockState state) {
        return state.is(ModBlocks.ARC_FURNACE_PART.get())
                && state.hasProperty(PART_X)
                && state.hasProperty(PART_Y)
                && state.hasProperty(PART_Z)
                && state.getValue(PART_X) == 1
                && state.getValue(PART_Y) == 0
                && state.getValue(PART_Z) == 2;
    }
}
