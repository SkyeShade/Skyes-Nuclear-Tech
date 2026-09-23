package com.skyeshade.skyent.content.shape;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Conservative triangle/voxel SAT, optional closed-component fill, deterministic greedy AABB merging.
 */
public final class MeshVoxelizer {
    public enum Policy {OPEN_SURFACE, CLOSED_SOLID}

    /**
     * Only selected load-bearing sheets use a full voxel of normal thickness.
     */
    public enum SurfacePolicy {SHELL, SUPPORTING_SHEET}

    /**
     * Shared physical thickness for exact sheets and voxel fallback, measured in blocks.
     */
    public static double sheetThickness(SurfacePolicy policy, int resolution) {
        return (policy == SurfacePolicy.SUPPORTING_SHEET ? 1.0 : .25) / resolution;
    }

    public record Surface(Triangle triangle, SurfacePolicy policy) {
    }

    public record Triangle(Vec3 a, Vec3 b, Vec3 c) {
    }

    public record Result(List<AABB> boxes, int occupiedCells) {
        public Result {
            boxes = List.copyOf(boxes);
        }
    }

    public record SurfaceResult(List<AABB> boxes, BitSet occupancy, int nx, int ny, int nz, int resolution) {
        public SurfaceResult {
            boxes = List.copyOf(boxes);
            occupancy = (BitSet) occupancy.clone();
        }

        @Override
        public BitSet occupancy() {
            return (BitSet) occupancy.clone();
        }

        public void forEachOccupied(java.util.function.Consumer<AABB> consumer) {
            for (int i = occupancy.nextSetBit(0); i >= 0; i = occupancy.nextSetBit(i + 1)) {
                int z = i % nz, x = (i / nz) % nx, y = i / (nx * nz);
                consumer.accept(new AABB((double) x / resolution, (double) y / resolution, (double) z / resolution,
                        (double) (x + 1) / resolution, (double) (y + 1) / resolution, (double) (z + 1) / resolution));
            }
        }
    }

    /**
     * Conservative surface occupancy with subvoxel bounds. Clip the triangle against a cell expanded
     * by its normal-projected thickness, then bound the clipped sheet and intersect the original cell.
     * This contains the extruded triangle without expanding every boundary to an entire grid cell.
     */
    public static SurfaceResult voxelizeSurfaces(List<Surface> surfaces, int sx, int sy, int sz, int resolution) {
        if (resolution < 1 || resolution > 32 || (long) sx * sy * sz * resolution * resolution * resolution > 2_000_000)
            throw new IllegalArgumentException("Invalid surface grid");
        int nx = sx * resolution, ny = sy * resolution, nz = sz * resolution;
        AABB[] cells = new AABB[nx * ny * nz];
        BitSet occupancy = new BitSet(cells.length);
        for (Surface surface : surfaces) {
            Triangle t = surface.triangle();
            Vec3 normal = t.b().subtract(t.a()).cross(t.c().subtract(t.a())).normalize();
            if (normal.lengthSqr() < .5) continue;
            double half = sheetThickness(surface.policy(), resolution) / 2;
            Vec3 pad = new Vec3(Math.abs(normal.x) * half, Math.abs(normal.y) * half, Math.abs(normal.z) * half);
            AABB bounds = new AABB(t.a(), t.b()).minmax(new AABB(t.c(), t.c())).inflate(pad.x, pad.y, pad.z);
            for (int y = low(bounds.minY, resolution, ny); y <= high(bounds.maxY, resolution, ny); y++)
                for (int x = low(bounds.minX, resolution, nx); x <= high(bounds.maxX, resolution, nx); x++)
                    for (int z = low(bounds.minZ, resolution, nz); z <= high(bounds.maxZ, resolution, nz); z++) {
                        AABB cell = new AABB((double) x / resolution, (double) y / resolution, (double) z / resolution,
                                (double) (x + 1) / resolution, (double) (y + 1) / resolution, (double) (z + 1) / resolution);
                        AABB expanded = cell.inflate(pad.x, pad.y, pad.z);
                        List<Vec3> polygon = new ArrayList<>(List.of(t.a(), t.b(), t.c()));
                        for (int axis = 0; axis < 3 && !polygon.isEmpty(); axis++) {
                            polygon = clip(polygon, axis, min(expanded, axis), true);
                            polygon = clip(polygon, axis, max(expanded, axis), false);
                        }
                        if (polygon.isEmpty()) continue;
                        AABB box = new AABB(polygon.getFirst(), polygon.getFirst());
                        for (Vec3 p : polygon) box = box.minmax(new AABB(p, p));
                        box = box.inflate(pad.x, pad.y, pad.z).intersect(cell);
                        // Bound coordinate complexity for Minecraft's compressed VoxelShape grid. Outward
                        // quantization to 1/8 voxel is conservative and cannot introduce a machine offset.
                        int sub = resolution * 8;
                        box = new AABB(down(box.minX, sub), down(box.minY, sub), down(box.minZ, sub),
                                up(box.maxX, sub), up(box.maxY, sub), up(box.maxZ, sub));
                        if (box.getXsize() < 1e-9 || box.getYsize() < 1e-9 || box.getZsize() < 1e-9) continue;
                        int i = index(x, y, z, nx, nz);
                        cells[i] = cells[i] == null ? box : cells[i].minmax(box);
                        occupancy.set(i);
                    }
        }
        List<AABB> boxes = new ArrayList<>();
        for (AABB box : cells) if (box != null) boxes.add(box);
        // Merge only when the other two intervals match: never undo the subvoxel clipping.
        for (int pass = 0; pass < 2; pass++) for (int axis = 0; axis < 3; axis++) boxes = merge(boxes, axis);
        return new SurfaceResult(boxes, occupancy, nx, ny, nz, resolution);
    }

    private static double down(double v, int r) {
        return Math.floor(v * r + 1e-8) / r;
    }

    private static double up(double v, int r) {
        return Math.ceil(v * r - 1e-8) / r;
    }

    private static double coord(Vec3 p, int axis) {
        return axis == 0 ? p.x : axis == 1 ? p.y : p.z;
    }

    private static double min(AABB b, int axis) {
        return axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ;
    }

    private static double max(AABB b, int axis) {
        return axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ;
    }

    private static List<Vec3> clip(List<Vec3> polygon, int axis, double plane, boolean greater) {
        if (polygon.isEmpty()) return polygon;
        List<Vec3> result = new ArrayList<>();
        Vec3 a = polygon.getLast();
        double da = (coord(a, axis) - plane) * (greater ? 1 : -1);
        for (Vec3 b : polygon) {
            double db = (coord(b, axis) - plane) * (greater ? 1 : -1);
            if ((da >= 0) != (db >= 0)) result.add(a.lerp(b, da / (da - db)));
            if (db >= 0) result.add(b);
            a = b;
            da = db;
        }
        return result;
    }

    private static List<AABB> merge(List<AABB> boxes, int axis) {
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        boxes.sort(Comparator.comparingDouble((AABB b) -> min(b, u)).thenComparingDouble(b -> max(b, u))
                .thenComparingDouble(b -> min(b, v)).thenComparingDouble(b -> max(b, v)).thenComparingDouble(b -> min(b, axis)));
        List<AABB> result = new ArrayList<>();
        AABB last = null;
        for (AABB box : boxes) {
            if (last != null && min(last, u) == min(box, u) && max(last, u) == max(box, u)
                    && min(last, v) == min(box, v) && max(last, v) == max(box, v) && min(box, axis) <= max(last, axis) + 1e-9) {
                last = last.minmax(box);
                result.set(result.size() - 1, last);
            } else {
                result.add(box);
                last = box;
            }
        }
        return result;
    }

    private static final Vec3[] AXES = {new Vec3(1, 0, 0), new Vec3(0, 1, 0), new Vec3(0, 0, 1)};

    private MeshVoxelizer() {
    }

    public static Result voxelize(List<Triangle> triangles, int sx, int sy, int sz, int resolution, Policy policy) {
        if (resolution < 1 || resolution > 32 || (long) sx * sy * sz * resolution * resolution * resolution > 2_000_000)
            throw new IllegalArgumentException("Voxel grid too large or invalid resolution");
        int nx = sx * resolution, ny = sy * resolution, nz = sz * resolution;
        BitSet grid = new BitSet(nx * ny * nz);
        double half = .5 / resolution;
        for (Triangle triangle : triangles) {
            Vec3 a = triangle.a(), b = triangle.b(), c = triangle.c();
            int x0 = low(Math.min(a.x, Math.min(b.x, c.x)), resolution, nx), x1 = high(Math.max(a.x, Math.max(b.x, c.x)), resolution, nx);
            int y0 = low(Math.min(a.y, Math.min(b.y, c.y)), resolution, ny), y1 = high(Math.max(a.y, Math.max(b.y, c.y)), resolution, ny);
            int z0 = low(Math.min(a.z, Math.min(b.z, c.z)), resolution, nz), z1 = high(Math.max(a.z, Math.max(b.z, c.z)), resolution, nz);
            Vec3[] edges = {b.subtract(a), c.subtract(b), a.subtract(c)};
            List<Vec3> axes = new ArrayList<>(List.of(AXES));
            axes.add(edges[0].cross(edges[1]));
            for (Vec3 edge : edges) for (Vec3 axis : AXES) axes.add(edge.cross(axis));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    for (int z = z0; z <= z1; z++) {
                        Vec3 center = new Vec3((x + .5) / resolution, (y + .5) / resolution, (z + .5) / resolution);
                        if (overlap(a.subtract(center), b.subtract(center), c.subtract(center), axes, half))
                            grid.set(index(x, y, z, nx, nz));
                    }
        }
        if (policy == Policy.CLOSED_SOLID) fill(grid, nx, ny, nz);
        int count = grid.cardinality();
        List<AABB> boxes = new ArrayList<>();
        for (int y = 0; y < ny; y++)
            for (int x = 0; x < nx; x++)
                for (int z = 0; z < nz; z++) {
                    if (!grid.get(index(x, y, z, nx, nz))) continue;
                    int ex = x + 1, ey = y + 1, ez = z + 1;
                    while (ez < nz && grid.get(index(x, y, ez, nx, nz))) ez++;
                    while (ex < nx && full(grid, ex, ex + 1, y, y + 1, z, ez, nx, nz)) ex++;
                    while (ey < ny && full(grid, x, ex, ey, ey + 1, z, ez, nx, nz)) ey++;
                    for (int yy = y; yy < ey; yy++)
                        for (int xx = x; xx < ex; xx++)
                            grid.clear(index(xx, yy, z, nx, nz), index(xx, yy, ez - 1, nx, nz) + 1);
                    boxes.add(new AABB((double) x / resolution, (double) y / resolution, (double) z / resolution, (double) ex / resolution, (double) ey / resolution, (double) ez / resolution));
                }
        return new Result(boxes, count);
    }

    private static int low(double p, int r, int size) {
        return Math.max(0, Math.min(size - 1, (int) Math.floor(p * r - 1e-7)));
    }

    private static int high(double p, int r, int size) {
        return Math.max(0, Math.min(size - 1, (int) Math.floor(p * r + 1e-7)));
    }

    private static boolean overlap(Vec3 a, Vec3 b, Vec3 c, List<Vec3> axes, double half) {
        for (Vec3 axis : axes) {
            if (axis.lengthSqr() < 1e-20) continue;
            double pa = a.dot(axis), pb = b.dot(axis), pc = c.dot(axis);
            double radius = half * (Math.abs(axis.x) + Math.abs(axis.y) + Math.abs(axis.z));
            if (Math.min(pa, Math.min(pb, pc)) > radius + 1e-9 || Math.max(pa, Math.max(pb, pc)) < -radius - 1e-9)
                return false;
        }
        return true;
    }

    private static int index(int x, int y, int z, int nx, int nz) {
        return (y * nx + x) * nz + z;
    }

    private static boolean full(BitSet bits, int x, int ex, int y, int ey, int z, int ez, int nx, int nz) {
        for (int yy = y; yy < ey; yy++)
            for (int xx = x; xx < ex; xx++)
                for (int zz = z; zz < ez; zz++) if (!bits.get(index(xx, yy, zz, nx, nz))) return false;
        return true;
    }

    /**
     * Flood outside only when the caller explicitly declares a closed component. Open panels are never filled.
     */
    private static void fill(BitSet grid, int nx, int ny, int nz) {
        BitSet outside = new BitSet(nx * ny * nz);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < ny; y++)
            for (int x = 0; x < nx; x++)
                for (int z = 0; z < nz; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == nx - 1 || y == ny - 1 || z == nz - 1)
                        seed(index(x, y, z, nx, nz), grid, outside, queue);
                }
        while (!queue.isEmpty()) {
            int i = queue.removeFirst(), z = i % nz, x = (i / nz) % nx, y = i / (nx * nz);
            if (x > 0) seed(i - nz, grid, outside, queue);
            if (x + 1 < nx) seed(i + nz, grid, outside, queue);
            if (y > 0) seed(i - nx * nz, grid, outside, queue);
            if (y + 1 < ny) seed(i + nx * nz, grid, outside, queue);
            if (z > 0) seed(i - 1, grid, outside, queue);
            if (z + 1 < nz) seed(i + 1, grid, outside, queue);
        }
        outside.flip(0, nx * ny * nz);
        grid.or(outside);
    }

    private static void seed(int i, BitSet grid, BitSet outside, ArrayDeque<Integer> queue) {
        if (!grid.get(i) && !outside.get(i)) {
            outside.set(i);
            queue.addLast(i);
        }
    }
}
