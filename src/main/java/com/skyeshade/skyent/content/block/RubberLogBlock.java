package com.skyeshade.skyent.content.block;

import com.skyeshade.skyent.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

public class RubberLogBlock extends RotatedPillarBlock {
    private static final int RESIN_CHANCE = 30;
    private static final int LEAF_SCAN_RADIUS = 4;

    public RubberLogBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(RESIN_CHANCE) != 0 || !hasNearbyRubberLeaves(level, pos)) {
            return;
        }

        Direction resinFace = Direction.Plane.HORIZONTAL.getRandomDirection(random);
        level.setBlock(
                pos,
                ModBlocks.RESIN_BEARING_RUBBER_LOG.get()
                        .defaultBlockState()
                        .setValue(AXIS, state.getValue(AXIS))
                        .setValue(ResinBearingRubberLogBlock.RESIN_FACE, resinFace),
                UPDATE_ALL
        );
    }

    private static boolean hasNearbyRubberLeaves(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos scanPos = new BlockPos.MutableBlockPos();
        for (int x = -LEAF_SCAN_RADIUS; x <= LEAF_SCAN_RADIUS; x++) {
            for (int y = -LEAF_SCAN_RADIUS; y <= LEAF_SCAN_RADIUS; y++) {
                for (int z = -LEAF_SCAN_RADIUS; z <= LEAF_SCAN_RADIUS; z++) {
                    scanPos.set(pos.getX() + x, pos.getY() + y, pos.getZ() + z);
                    if (level.getBlockState(scanPos).is(ModBlocks.RUBBER_LEAVES.get())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
