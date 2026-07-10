package com.skyeshade.skyent.content.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// Generated from rolling_mill.json with the scaled-block transform. Do not edit by hand.
public final class RollingMillShapes {
    private static final int SIZE_X = 4;
    private static final int SIZE_Y = 3;
    private static final int SIZE_Z = 2;

    private static final VoxelShape[] NORTH_SHAPES = new VoxelShape[] {
            // local x=0, y=0, z=0, boxes=2
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    Block.box(6.0D, 15.5D, 5.0D, 8.0D, 16.0D, 11.0D)
            )
            ,
            // local x=0, y=0, z=1, boxes=2
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    Block.box(6.0D, 15.5D, 5.0D, 8.0D, 16.0D, 11.0D)
            )
            ,
            // local x=1, y=0, z=0, boxes=1
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
            ,
            // local x=1, y=0, z=1, boxes=1
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
            ,
            // local x=2, y=0, z=0, boxes=1
            Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D)
            ,
            // local x=2, y=0, z=1, boxes=1
            Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D)
            ,
            // local x=3, y=0, z=0, boxes=4
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    Block.box(0.0D, 14.0D, 14.0D, 8.0D, 16.0D, 16.0D),
                    Block.box(0.0D, 14.0D, 0.0D, 8.0D, 16.0D, 2.0D),
                    Block.box(0.0D, 0.0D, 2.0D, 8.0D, 16.0D, 14.0D)
            )
            ,
            // local x=3, y=0, z=1, boxes=4
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 2.0D, 8.0D, 16.0D, 14.0D),
                    Block.box(0.0D, 14.0D, 14.0D, 8.0D, 16.0D, 16.0D),
                    Block.box(0.0D, 14.0D, 0.0D, 8.0D, 16.0D, 2.0D)
            )
            ,
            // local x=0, y=1, z=0, boxes=3
            Shapes.or(
                    Block.box(6.0D, 6.5D, 5.0D, 8.0D, 12.5D, 11.0D),
                    Block.box(6.0D, 0.0D, 5.0D, 8.0D, 5.5D, 11.0D),
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
            )
            ,
            // local x=0, y=1, z=1, boxes=3
            Shapes.or(
                    Block.box(6.0D, 0.0D, 5.0D, 8.0D, 5.5D, 11.0D),
                    Block.box(6.0D, 6.5D, 5.0D, 8.0D, 12.5D, 11.0D),
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D)
            )
            ,
            // local x=1, y=1, z=0, boxes=4
            Shapes.or(
                    Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D),
                    Block.box(1.0D, 14.0D, 0.0D, 15.0D, 16.0D, 16.0D),
                    Block.box(1.0D, 0.0D, 0.0D, 15.0D, 4.0D, 16.0D)
            )
            ,
            // local x=1, y=1, z=1, boxes=4
            Shapes.or(
                    Block.box(15.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 0.0D, 1.0D, 16.0D, 16.0D),
                    Block.box(1.0D, 14.0D, 0.0D, 15.0D, 16.0D, 16.0D),
                    Block.box(1.0D, 0.0D, 0.0D, 15.0D, 4.0D, 16.0D)
            )
            ,
            // local x=2, y=1, z=0, boxes=1
            Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D)
            ,
            // local x=2, y=1, z=1, boxes=1
            Block.box(0.0D, 0.0D, 0.0D, 8.0D, 16.0D, 16.0D)
            ,
            // local x=3, y=1, z=0, boxes=5
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D),
                    Block.box(8.0D, 14.0D, 2.0D, 16.0D, 16.0D, 14.0D),
                    Block.box(0.0D, 0.0D, 14.0D, 8.0D, 14.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 0.0D, 8.0D, 14.0D, 2.0D),
                    Block.box(0.0D, 0.0D, 2.0D, 8.0D, 16.0D, 14.0D)
            )
            ,
            // local x=3, y=1, z=1, boxes=5
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 14.0D, 16.0D),
                    Block.box(8.0D, 14.0D, 2.0D, 16.0D, 16.0D, 14.0D),
                    Block.box(0.0D, 0.0D, 2.0D, 8.0D, 16.0D, 14.0D),
                    Block.box(0.0D, 0.0D, 14.0D, 8.0D, 14.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 0.0D, 8.0D, 14.0D, 2.0D)
            )
            ,
            // local x=0, y=2, z=0, boxes=3
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
                    Block.box(8.0D, 4.0D, 0.0D, 16.0D, 8.0D, 16.0D),
                    Block.box(8.0D, 8.0D, 4.0D, 16.0D, 12.0D, 16.0D)
            )
            ,
            // local x=0, y=2, z=1, boxes=3
            Shapes.or(
                    Block.box(8.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
                    Block.box(8.0D, 4.0D, 0.0D, 16.0D, 8.0D, 16.0D),
                    Block.box(8.0D, 8.0D, 0.0D, 16.0D, 12.0D, 12.0D)
            )
            ,
            // local x=1, y=2, z=0, boxes=4
            Shapes.or(
                    Block.box(15.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 0.0D, 1.0D, 4.0D, 16.0D),
                    Block.box(1.0D, 0.0D, 0.0D, 15.0D, 4.0D, 16.0D),
                    Block.box(0.0D, 4.0D, 4.0D, 16.0D, 8.0D, 16.0D)
            )
            ,
            // local x=1, y=2, z=1, boxes=4
            Shapes.or(
                    Block.box(15.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
                    Block.box(0.0D, 0.0D, 0.0D, 1.0D, 4.0D, 16.0D),
                    Block.box(1.0D, 0.0D, 0.0D, 15.0D, 4.0D, 16.0D),
                    Block.box(0.0D, 4.0D, 0.0D, 16.0D, 8.0D, 12.0D)
            )
            ,
            // local x=2, y=2, z=0, boxes=3
            Shapes.or(
                    Block.box(0.0D, 0.0D, 0.0D, 8.0D, 4.0D, 16.0D),
                    Block.box(0.0D, 4.0D, 0.0D, 8.0D, 8.0D, 16.0D),
                    Block.box(0.0D, 8.0D, 4.0D, 8.0D, 12.0D, 16.0D)
            )
            ,
            // local x=2, y=2, z=1, boxes=3
            Shapes.or(
                    Block.box(0.0D, 0.0D, 0.0D, 8.0D, 4.0D, 16.0D),
                    Block.box(0.0D, 4.0D, 0.0D, 8.0D, 8.0D, 16.0D),
                    Block.box(0.0D, 8.0D, 0.0D, 8.0D, 12.0D, 12.0D)
            )
            ,
            // local x=3, y=2, z=0, boxes=2
            Shapes.or(
                    Block.box(8.0D, 0.0D, 2.0D, 16.0D, 4.0D, 14.0D),
                    Block.box(0.0D, 0.0D, 2.0D, 8.0D, 4.0D, 14.0D)
            )
            ,
            // local x=3, y=2, z=1, boxes=2
            Shapes.or(
                    Block.box(8.0D, 0.0D, 2.0D, 16.0D, 4.0D, 14.0D),
                    Block.box(0.0D, 0.0D, 2.0D, 8.0D, 4.0D, 14.0D)
            )
    };

    private static final VoxelShape[][] LOCAL_SHAPES = new VoxelShape[NORTH_SHAPES.length][];

    static {
        for (int i = 0; i < NORTH_SHAPES.length; i++) {
            LOCAL_SHAPES[i] = shapesByFacing(NORTH_SHAPES[i]);
        }
    }

    private RollingMillShapes() {
    }

    public static VoxelShape shapeForLocal(int x, int y, int z, Direction facing) {
        int clampedX = Math.max(0, Math.min(SIZE_X - 1, x));
        int clampedY = Math.max(0, Math.min(SIZE_Y - 1, y));
        int clampedZ = Math.max(0, Math.min(SIZE_Z - 1, z));
        int index = (clampedY * SIZE_X + clampedX) * SIZE_Z + clampedZ;
        return shapeForFacing(LOCAL_SHAPES[index], facing);
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
