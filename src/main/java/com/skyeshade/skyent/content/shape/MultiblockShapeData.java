package com.skyeshade.skyent.content.shape;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MultiblockShapeData {
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final VoxelShape[][] shapes;

    public MultiblockShapeData(int sizeX, int sizeY, int sizeZ, VoxelShape[] northShapes) {
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.shapes = new VoxelShape[northShapes.length][];
        for (int i = 0; i < northShapes.length; i++) {
            this.shapes[i] = shapesByFacing(northShapes[i]);
        }
    }

    public VoxelShape shapeForLocal(int x, int y, int z, Direction facing) {
        int clampedX = Math.max(0, Math.min(sizeX - 1, x));
        int clampedY = Math.max(0, Math.min(sizeY - 1, y));
        int clampedZ = Math.max(0, Math.min(sizeZ - 1, z));
        int index = (clampedY * sizeX + clampedX) * sizeZ + clampedZ;
        return shapeForFacing(shapes[index], facing);
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
