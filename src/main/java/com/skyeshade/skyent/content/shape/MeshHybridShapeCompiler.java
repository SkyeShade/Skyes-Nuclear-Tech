package com.skyeshade.skyent.content.shape;

import com.skyeshade.skyent.content.model.MachineModelTransform;
import com.skyeshade.skyent.content.model.blockbench.MeshSceneCompiler;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.BiFunction;

import static com.skyeshade.skyent.content.model.blockbench.MeshSceneCompiler.*;
import static com.skyeshade.skyent.content.shape.MeshVoxelizer.SurfacePolicy;

/**
 * Exact primitives and rectangular sheets bypass rasterization entirely. All positions use the render transform.
 */
public final class MeshHybridShapeCompiler {
    // Both angular error (unit vector) and displacement (blocks) must pass. No rounding to a voxel lattice.
    private static final double EPS = 1e-7;

    public record ExactBox(AABB bounds, String part, String face) {
    }

    public record Result(List<ExactBox> cuboids, List<ExactBox> sheets, MeshStaircase.Result fallback,
                         int fallbackPrimitives, int fallbackTriangles) {
        public Result {
            cuboids = List.copyOf(cuboids);
            sheets = List.copyOf(sheets);
        }

        public List<AABB> boxes() {
            List<AABB> boxes = new ArrayList<>();
            cuboids.forEach(b -> boxes.add(b.bounds()));
            sheets.forEach(b -> boxes.add(b.bounds()));
            boxes.addAll(fallback.boxes());
            return List.copyOf(boxes);
        }

        public String counts() {
            return "exact=" + cuboids.size() + " sheets=" + sheets.size() + " fallbackPrimitives=" + fallbackPrimitives
                    + " fallbackTriangles=" + fallbackTriangles + " stepped=" + fallback.boxes().size()
                    + " beforeMerge=" + (cuboids.size() + sheets.size() + fallback.unmerged().size())
                    + " afterMerge=" + boxes().size();
        }

        public String cellCounts(AABB cell, int actualUnionBoxes) {
            long exact = cuboids.stream().filter(b -> b.bounds().intersects(cell)).count();
            long thin = sheets.stream().filter(b -> b.bounds().intersects(cell)).count();
            long stepped = fallback.boxes().stream().filter(b -> b.intersects(cell)).count();
            long raw = fallback.unmerged().stream().filter(b -> b.intersects(cell)).count();
            return "exact=" + exact + " thinSheets=" + thin + " stepped=" + stepped
                    + " beforeMerge=" + (exact + thin + raw) + " afterMerge=" + (exact + thin + stepped)
                    + " unionBoxes=" + actualUnionBoxes;
        }
    }

    private MeshHybridShapeCompiler() {
    }

    /**
     * A null policy excludes a polygon. Collision and outline supply independent selections.
     * The legacy resolution argument is retained for callers; all approximations now use 1/16 steps.
     */
    public static Result compile(MeshSceneCompiler.Compiled compiled, MachineModelTransform transform,
                                 int sx, int sy, int sz, int legacyResolution,
                                 BiFunction<Primitive, Polygon, SurfacePolicy> selection) {
        return compile(compiled,transform,sx,sy,sz,legacyResolution,selection,true);
    }

    /** Outline may deliberately include non-collidable geometry for selection. */
    public static Result compile(MeshSceneCompiler.Compiled compiled, MachineModelTransform transform,
                                 int sx, int sy, int sz, int legacyResolution,
                                 BiFunction<Primitive, Polygon, SurfacePolicy> selection, boolean respectCollisionMetadata) {
        List<ExactBox> cuboids = new ArrayList<>(), sheets = new ArrayList<>();
        List<AABB> fallback = new ArrayList<>();
        int fallbackPrimitives = 0, fallbackTriangles = 0;
        AABB footprint = new AABB(0, 0, 0, sx, sy, sz);
        for (Primitive primitive : compiled.primitives()) {
            if (respectCollisionMetadata && !primitive.collision().effective()) continue;
            List<Polygon> selected = primitive.polygons().stream().filter(p -> selection.apply(primitive, p) != null).toList();
            if (selected.isEmpty()) continue;
            List<Vec3> corners = primitive.cubeCorners().stream().map(transform::footprint).toList();
            int[] axes = cubeAxes(corners);
            if (axes != null && primitive.zeroDimensions() == 0 && primitive.polygons().size() == 6 && selected.size() == 6) {
                add(cuboids, bounds(corners), footprint, primitive.part(), "cube");
                continue;
            }
            if (axes != null && primitive.sheetAxis() >= 0) {
                int axis = axes[primitive.sheetAxis()];
                // At least one broad cap must be selected; edge-only selections must not fill a sheet.
                boolean cap = selected.stream().anyMatch(p -> rectangleAxis(p.vertices().stream().map(transform::footprint).toList()) == axis);
                if (cap) {
                    AABB box = bounds(corners);
                    // Blockbench's tiny preview cap already supplies the exact rendered upper face.
                    add(sheets, interval(box, axis, max(box, axis) - Math.max(MeshStaircase.SHEET_DEPTH,
                            max(box, axis) - min(box, axis)), max(box, axis)), footprint, primitive.part(), "sheet");
                    continue;
                }
            }
            boolean usedFallback = false;
            if (primitive.sheetAxis() >= 0 && axes == null) {
                // The renderer's 0.001-unit preview extrusion is not six independent walls.
                // Approximate the broad cap once, preserving its rendered support height.
                Polygon cap = selected.stream().max(Comparator.comparingDouble(p -> p.triangles().stream()
                        .mapToDouble(t -> t.b().position().subtract(t.a().position()).cross(t.c().position().subtract(t.a().position())).length()).sum())).orElseThrow();
                fallback.addAll(MeshStaircase.surface(cap.vertices().stream().map(transform::footprint).toList(), false, footprint));
                fallbackTriangles += cap.triangles().size();
                fallbackPrimitives++;
                continue;
            }
            // Closed meshes use inward shell thickness, so their external planar boundary stays exact.
            // Open sheets use one-sided thickness; horizontal sheets always support at authored Y.
            boolean closed = closedOutward(primitive.polygons());
            for (Polygon polygon : selected) {
                if (polygon.triangles().isEmpty()) continue;
                List<Vec3> vertices = polygon.vertices().stream().map(transform::footprint).toList();
                int axis = rectangleAxis(vertices);
                SurfacePolicy policy = selection.apply(primitive, polygon);
                if (axis >= 0) {
                    AABB box = bounds(vertices);
                    double lo = min(box, axis), hi = max(box, axis), depth = MeshStaircase.SHEET_DEPTH;
                    Vec3 normal = vertices.get(1).subtract(vertices.get(0)).cross(vertices.get(2).subtract(vertices.get(0)));
                    if (closed && policy == SurfacePolicy.SHELL) {
                        box = coord(normal, axis) > 0 ? interval(box, axis, lo - depth, hi) : interval(box, axis, lo, hi + depth);
                    } else box = axis == 1 || coord(normal, axis) > 0
                            ? interval(box, axis, lo - depth, hi) : interval(box, axis, lo, hi + depth);
                    if (max(box, axis) <= min(footprint, axis)) box = interval(box, axis, lo, hi + depth);
                    if (min(box, axis) >= max(footprint, axis)) box = interval(box, axis, lo - depth, hi);
                    add(sheets, box, footprint, primitive.part(), polygon.face());
                } else {
                    usedFallback = true;
                    fallbackTriangles += polygon.triangles().size();
                    fallback.addAll(MeshStaircase.surface(vertices, closed, footprint));
                }
            }
            if (usedFallback) fallbackPrimitives++;
        }
        return new Result(cuboids, sheets, MeshStaircase.finish(fallback), fallbackPrimitives, fallbackTriangles);
    }

    private static void add(List<ExactBox> out, AABB box, AABB footprint, String part, String face) {
        double ax = Math.max(box.minX, footprint.minX), ay = Math.max(box.minY, footprint.minY), az = Math.max(box.minZ, footprint.minZ);
        double bx = Math.min(box.maxX, footprint.maxX), by = Math.min(box.maxY, footprint.maxY), bz = Math.min(box.maxZ, footprint.maxZ);
        if (bx - ax > 1e-10 && by - ay > 1e-10 && bz - az > 1e-10)
            out.add(new ExactBox(new AABB(ax, ay, az, bx, by, bz), part, face));
    }

    /**
     * Eight corners in x/y/z bit order, with three nonzero, mutually distinct cardinal edges.
     */
    private static int[] cubeAxes(List<Vec3> corners) {
        if (corners.size() != 8) return null;
        int[] axes = new int[3];
        for (int i = 0; i < 3; i++) {
            Vec3 edge = corners.get(1 << i).subtract(corners.getFirst());
            axes[i] = cardinal(edge);
            if (axes[i] < 0) return null;
            for (int j = 0; j < i; j++) if (axes[j] == axes[i]) return null;
        }
        return axes;
    }

    /**
     * Reject trapezoids, triangles, crossed quads and tilted faces, even when their AABB looks rectangular.
     */
    private static int rectangleAxis(List<Vec3> vertices) {
        if (vertices.size() != 4) return -1;
        int[] axes = new int[4];
        for (int i = 0; i < 4; i++) {
            axes[i] = cardinal(vertices.get((i + 1) % 4).subtract(vertices.get(i)));
            if (axes[i] < 0) return -1;
        }
        if (axes[0] == axes[1] || axes[0] != axes[2] || axes[1] != axes[3]) return -1;
        return 3 - axes[0] - axes[1];
    }

    private static int cardinal(Vec3 edge) {
        double length = edge.length();
        if (length < 1e-10) return -1;
        int axis = Math.abs(edge.x) >= Math.abs(edge.y) ? 0 : 1;
        if (Math.abs(edge.z) > Math.abs(coord(edge, axis))) axis = 2;
        for (int i = 0; i < 3; i++)
            if (i != axis && (Math.abs(coord(edge, i)) > EPS || Math.abs(coord(edge, i)) / length > EPS)) return -1;
        return axis;
    }

    private record Edge(Vec3 a, Vec3 b) {
    }

    private static boolean closedOutward(List<Polygon> polygons) {
        Map<Edge, Integer> edges = new HashMap<>();
        double volume = 0;
        for (Polygon polygon : polygons) {
            var v = polygon.vertices();
            for (int i = 0; i < v.size(); i++)
                edges.merge(new Edge(v.get(i), v.get((i + 1) % v.size())), 1, Integer::sum);
            for (Triangle t : polygon.triangles())
                volume += t.a().position().dot(t.b().position().cross(t.c().position()));
        }
        return volume > 1e-10 && edges.entrySet().stream().allMatch(e -> e.getValue() == 1
                && edges.getOrDefault(new Edge(e.getKey().b(), e.getKey().a()), 0) == 1);
    }

    private static AABB bounds(List<Vec3> points) {
        AABB b = new AABB(points.getFirst(), points.getFirst());
        for (Vec3 p : points) b = b.minmax(new AABB(p, p));
        return b;
    }

    private static double coord(Vec3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }

    private static double min(AABB b, int axis) {
        return axis == 0 ? b.minX : axis == 1 ? b.minY : b.minZ;
    }

    private static double max(AABB b, int axis) {
        return axis == 0 ? b.maxX : axis == 1 ? b.maxY : b.maxZ;
    }

    private static AABB interval(AABB b, int axis, double lo, double hi) {
        return new AABB(axis == 0 ? lo : b.minX, axis == 1 ? lo : b.minY, axis == 2 ? lo : b.minZ,
                axis == 0 ? hi : b.maxX, axis == 1 ? hi : b.maxY, axis == 2 ? hi : b.maxZ);
    }
}
