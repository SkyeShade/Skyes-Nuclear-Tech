package com.skyeshade.skyent.content.worldgen;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.block.ResinBearingRubberLogBlock;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecorator;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;

import java.util.Comparator;
import java.util.List;

public final class RubberTreeDecorator extends TreeDecorator {
    public static final RubberTreeDecorator INSTANCE = new RubberTreeDecorator();
    public static final MapCodec<RubberTreeDecorator> CODEC = MapCodec.unit(INSTANCE);

    private RubberTreeDecorator() {
    }

    @Override
    protected TreeDecoratorType<?> type() {
        return ModFeatures.RUBBER_TREE_DECORATOR.get();
    }

    @Override
    public void place(Context context) {
        placeGuaranteedResinLog(context);
        placeLeafSpire(context);
    }

    private static void placeGuaranteedResinLog(Context context) {
        List<BlockPos> logs = context.logs();
        if (logs.isEmpty()) {
            return;
        }

        int minY = logs.stream().mapToInt(BlockPos::getY).min().orElse(logs.getFirst().getY());
        int maxY = logs.stream().mapToInt(BlockPos::getY).max().orElse(minY);
        int resinY = Math.min(maxY, minY + 1 + context.random().nextInt(Math.max(1, Math.min(2, maxY - minY) + 1)));
        BlockPos resinPos = logs.stream()
                .filter(pos -> pos.getY() == resinY)
                .min(Comparator.comparingInt(pos -> Math.abs(pos.getX() - logs.getFirst().getX()) + Math.abs(pos.getZ() - logs.getFirst().getZ())))
                .orElse(logs.get(Math.min(1, logs.size() - 1)));

        Direction resinFace = Direction.Plane.HORIZONTAL.getRandomDirection(context.random());
        BlockState resinLog = ModBlocks.RESIN_BEARING_RUBBER_LOG.get()
                .defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y)
                .setValue(ResinBearingRubberLogBlock.RESIN_FACE, resinFace);
        context.setBlock(resinPos, resinLog);
    }

    private static void placeLeafSpire(Context context) {
        List<BlockPos> leaves = context.leaves();
        List<BlockPos> logs = context.logs();
        if (logs.isEmpty()) {
            return;
        }

        BlockPos base = logs.stream()
                .min(Comparator.comparingInt(BlockPos::getY))
                .orElse(logs.getFirst());
        int topY = leaves.stream()
                .mapToInt(BlockPos::getY)
                .max()
                .orElseGet(() -> logs.stream().mapToInt(BlockPos::getY).max().orElse(base.getY()));
        BlockState leafState = ModBlocks.RUBBER_LEAVES.get()
                .defaultBlockState()
                .setValue(LeavesBlock.DISTANCE, 7)
                .setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.WATERLOGGED, false);

        for (int offset = 1; offset <= 2; offset++) {
            BlockPos pos = new BlockPos(base.getX(), topY + offset, base.getZ());
            if (context.isAir(pos)) {
                context.setBlock(pos, leafState);
            }
        }
    }
}
