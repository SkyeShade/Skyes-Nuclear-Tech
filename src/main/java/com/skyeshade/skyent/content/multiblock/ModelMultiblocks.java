package com.skyeshade.skyent.content.multiblock;

import com.skyeshade.skyent.content.shape.MultiblockShapeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ModelMultiblocks {
    private ModelMultiblocks() {
    }

    public static BlockPos localToWorld(ModelMultiblockDefinition definition, BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(rotateLocalOffset(definition, new BlockPos(x, y, z), facing));
    }

    public static BlockPos rotateLocalOffset(ModelMultiblockDefinition definition, BlockPos local, Direction facing) {
        int rightOffset = local.getX() - definition.controllerLocal().getX();
        int y = local.getY() - definition.controllerLocal().getY();
        int forwardOffset = local.getZ() - definition.controllerLocal().getZ();

        if (definition.orientation() == ModelMultiblockOrientation.CARDINAL_ROTATION) {
            return switch (facing) {
                case NORTH -> new BlockPos(rightOffset, y, forwardOffset);
                case EAST -> new BlockPos(-forwardOffset, y, rightOffset);
                case SOUTH -> new BlockPos(-rightOffset, y, -forwardOffset);
                case WEST -> new BlockPos(forwardOffset, y, -rightOffset);
                default -> new BlockPos(rightOffset, y, forwardOffset);
            };
        }

        Direction right = facing.getClockWise();
        int worldX = facing.getStepX() * forwardOffset + right.getStepX() * rightOffset;
        int worldZ = facing.getStepZ() * forwardOffset + right.getStepZ() * rightOffset;
        return new BlockPos(worldX, y, worldZ);
    }

    public static BlockPos masterPosFromLocal(ModelMultiblockDefinition definition, BlockPos partPos, BlockPos local, Direction facing) {
        return partPos.subtract(rotateLocalOffset(definition, local, facing));
    }

    public static boolean canPlace(ModelMultiblockDefinition definition, BlockPlaceContext context, BlockPos origin, Direction facing) {
        for (int y = 0; y < definition.sizeY(); y++) {
            for (int x = 0; x < definition.sizeX(); x++) {
                for (int z = 0; z < definition.sizeZ(); z++) {
                    if (definition.isControllerLocal(x, y, z)) {
                        continue;
                    }
                    BlockPos pos = localToWorld(definition, origin, facing, x, y, z);
                    if (!context.getLevel().getBlockState(pos).canBeReplaced(context)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void placeParts(ModelMultiblockDefinition definition, Level level, BlockPos origin, Direction facing, PartStateFactory partStateFactory) {
        forEachPart(definition, (x, y, z) -> {
            BlockPos partPos = localToWorld(definition, origin, facing, x, y, z);
            level.setBlock(partPos, partStateFactory.create(x, y, z, facing), Block.UPDATE_ALL);
        });
    }

    public static void removeParts(ModelMultiblockDefinition definition, Level level, BlockPos masterPos, Direction facing, Block partBlock) {
        forEachPart(definition, (x, y, z) -> {
            BlockPos partPos = localToWorld(definition, masterPos, facing, x, y, z);
            if (level.getBlockState(partPos).is(partBlock)) {
                level.setBlock(partPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
    }

    public static void spawnDestroyParticles(ModelMultiblockDefinition definition, Level level, BlockPos masterPos, Direction facing, BlockState visualState) {
        int visualStateId = Block.getId(visualState);
        forEachLocal(definition, (x, y, z) -> level.levelEvent(2001, localToWorld(definition, masterPos, facing, x, y, z), visualStateId));
    }

    public static VoxelShape generatedShapeForLocal(
            ModelMultiblockDefinition definition,
            Direction facing,
            int partX,
            int partY,
            int partZ,
            MultiblockShapeRegistry.ShapeFallback fallback
    ) {
        return MultiblockShapeRegistry.getShape(definition.id(), facing, partX, partY, partZ, fallback);
    }

    public static void forEachPart(ModelMultiblockDefinition definition, LocalConsumer consumer) {
        forEachLocal(definition, (x, y, z) -> {
            if (!definition.isControllerLocal(x, y, z)) {
                consumer.accept(x, y, z);
            }
        });
    }

    public static void forEachLocal(ModelMultiblockDefinition definition, LocalConsumer consumer) {
        for (int y = 0; y < definition.sizeY(); y++) {
            for (int x = 0; x < definition.sizeX(); x++) {
                for (int z = 0; z < definition.sizeZ(); z++) {
                    consumer.accept(x, y, z);
                }
            }
        }
    }

    @FunctionalInterface
    public interface LocalConsumer {
        void accept(int x, int y, int z);
    }

    @FunctionalInterface
    public interface PartStateFactory {
        BlockState create(int x, int y, int z, Direction facing);
    }
}
