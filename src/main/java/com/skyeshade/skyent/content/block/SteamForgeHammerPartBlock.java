package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SteamForgeHammerPartBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART_Y = IntegerProperty.create("part_y", 1, 2);
    public static final MapCodec<SteamForgeHammerPartBlock> CODEC = simpleCodec(SteamForgeHammerPartBlock::new);
    // Steam Forge Hammer model is authored at half scale and rendered at 2x.
    // Shape coordinates below describe the final rendered size in normal 0..16 block units.
    private static final VoxelShape MIDDLE_NORTH_SHAPE = Block.box(0.0D, 0.0D, 10.0D, 16.0D, 16.0D, 16.0D);
    private static final VoxelShape TOP_PANEL_NORTH_SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 6.0D, 16.0D);
    private static final VoxelShape TOP_STACK_1_NORTH_SHAPE = Block.box(4.0D, 6.0D, 2.0D, 12.0D, 16.0D, 10.0D);
    private static final VoxelShape TOP_STACK_2_NORTH_SHAPE = Block.box(4.0D, 6.0D, 10.0D, 12.0D, 12.0D, 16.0D);
    private static final VoxelShape TOP_NORTH_SHAPE = Shapes.or(
            TOP_PANEL_NORTH_SHAPE,
            TOP_STACK_1_NORTH_SHAPE,
            TOP_STACK_2_NORTH_SHAPE);
    private static final VoxelShape[] MIDDLE_SHAPES = shapesByFacing(MIDDLE_NORTH_SHAPE);
    private static final VoxelShape[] TOP_SHAPES = shapesByFacing(TOP_NORTH_SHAPE);

    public SteamForgeHammerPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART_Y, 1));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos masterPos = getMasterPos(pos, state);
            if (level.getBlockState(masterPos).is(ModBlocks.STEAM_FORGE_HAMMER.get())) {
                SteamForgeHammerBlock.removeWholeMachine(level, masterPos, !player.isCreative(), player);
            } else {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide && !level.getBlockState(getMasterPos(pos, state)).is(ModBlocks.STEAM_FORGE_HAMMER.get())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos masterPos = getMasterPos(pos, state);
        return level.getBlockState(masterPos).is(ModBlocks.STEAM_FORGE_HAMMER.get()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(PART_Y)) {
            case 1 -> shapeForFacing(MIDDLE_SHAPES, facing);
            case 2 -> shapeForFacing(TOP_SHAPES, facing);
            default -> Shapes.block();
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.STEAM_FORGE_HAMMER.get());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART_Y);
    }

    private static BlockPos getMasterPos(BlockPos pos, BlockState state) {
        return pos.below(state.getValue(PART_Y));
    }

    private static VoxelShape[] shapesByFacing(VoxelShape northShape) {
        return new VoxelShape[] {
                northShape,
                rotateShape(northShape, Direction.EAST),
                rotateShape(northShape, Direction.SOUTH),
                rotateShape(northShape, Direction.WEST)
        };
    }

    private static VoxelShape shapeForFacing(VoxelShape[] shapes, Direction facing) {
        return switch (facing) {
            case EAST -> shapes[1];
            case SOUTH -> shapes[2];
            case WEST -> shapes[3];
            default -> shapes[0];
        };
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
}
