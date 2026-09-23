package com.skyeshade.skyent.content.shape;

import com.skyeshade.skyent.content.model.MachineModelTransform;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import net.minecraft.core.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.*;

import java.util.*;

/**
 * Mesh-only shape cache. Both boxes and render vertices use MachineModelTransform for every facing.
 */
public final class MeshCellShapes {
    private final Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);
    private final Map<Direction, List<List<AABB>>> debugBoxes = new EnumMap<>(Direction.class);
    private final int sx, sy, sz;

    public MeshCellShapes(List<AABB> boxes, MachineModelTransform transform) {
        var def = transform.multiblock();
        sx = def.sizeX();
        sy = def.sizeY();
        sz = def.sizeZ();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] cells = new VoxelShape[sx * sy * sz];
            for (int y = 0; y < sy; y++)
                for (int x = 0; x < sx; x++)
                    for (int z = 0; z < sz; z++) {
                        List<AABB> clipped = new ArrayList<>();
                        BlockPos offset = ModelMultiblocks.rotateLocalOffset(def, new BlockPos(x, y, z), facing);
                        for (AABB b : boxes) {
                            double ax = Math.max(x, b.minX), ay = Math.max(y, b.minY), az = Math.max(z, b.minZ);
                            double bx = Math.min(x + 1, b.maxX), by = Math.min(y + 1, b.maxY), bz = Math.min(z + 1, b.maxZ);
                            if (bx > ax && by > ay && bz > az)
                                clipped.add(transform.relativeBounds(new AABB(ax, ay, az, bx, by, bz), facing)
                                        .move(-offset.getX(), -offset.getY(), -offset.getZ()));
                        }
                        cells[(y * sx + x) * sz + z] = clipped.isEmpty() ? Shapes.empty() : new BoxUnion(clipped);
                    }
            shapes.put(facing, cells);
            // Vanilla decomposition scans the compressed grid. Never repeat it every overlay frame.
            debugBoxes.put(facing, Arrays.stream(cells).map(s -> List.copyOf(s.toAabbs())).toList());
        }
    }

    public VoxelShape shapeForLocal(int x, int y, int z, Direction facing) {
        if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) return Shapes.empty();
        return shapes.get(facing)[(y * sx + x) * sz + z];
    }

    public List<AABB> boxesForLocal(int x, int y, int z, Direction facing) {
        if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) return List.of();
        return debugBoxes.get(facing).get((y * sx + x) * sz + z);
    }

    /**
     * Build the compressed coordinate grid once, instead of repeatedly unioning/optimizing thousands
     * of partial voxel boxes. Uses vanilla collision/raycast implementation without any entity hooks.
     */
    private static final class BoxUnion extends ArrayVoxelShape {
        BoxUnion(List<AABB> boxes) {
            this(boxes, coords(boxes, 0), coords(boxes, 1), coords(boxes, 2));
        }

        private BoxUnion(List<AABB> boxes, double[] xs, double[] ys, double[] zs) {
            super(grid(boxes, xs, ys, zs), xs, ys, zs);
        }

        private static double[] coords(List<AABB> boxes, int axis) {
            TreeSet<Double> result = new TreeSet<>();
            for (AABB b : boxes) {
                result.add(axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ);
                result.add(axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ);
            }
            return result.stream().mapToDouble(Double::doubleValue).toArray();
        }

        private static BitSetDiscreteVoxelShape grid(List<AABB> boxes, double[] xs, double[] ys, double[] zs) {
            var grid = new BitSetDiscreteVoxelShape(xs.length - 1, ys.length - 1, zs.length - 1);
            for (AABB b : boxes) {
                int ax = Arrays.binarySearch(xs, b.minX), bx = Arrays.binarySearch(xs, b.maxX);
                int ay = Arrays.binarySearch(ys, b.minY), by = Arrays.binarySearch(ys, b.maxY);
                int az = Arrays.binarySearch(zs, b.minZ), bz = Arrays.binarySearch(zs, b.maxZ);
                for (int x = ax; x < bx; x++)
                    for (int y = ay; y < by; y++) for (int z = az; z < bz; z++) grid.fill(x, y, z);
            }
            return grid;
        }
    }
}
