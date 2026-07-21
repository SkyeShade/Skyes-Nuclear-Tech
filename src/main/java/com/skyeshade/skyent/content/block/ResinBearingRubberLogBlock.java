package com.skyeshade.skyent.content.block;

import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jetbrains.annotations.Nullable;

public class ResinBearingRubberLogBlock extends RotatedPillarBlock {
    public static final DirectionProperty RESIN_FACE = HorizontalDirectionalBlock.FACING;

    public ResinBearingRubberLogBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(RESIN_FACE, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) {
            return null;
        }
        RandomSource random = context.getLevel().getRandom();
        Direction resinFace = context.getHorizontalDirection().getOpposite();
        if (!resinFace.getAxis().isHorizontal()) {
            resinFace = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        }
        return state.setValue(RESIN_FACE, resinFace);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RESIN_FACE);
    }
}
