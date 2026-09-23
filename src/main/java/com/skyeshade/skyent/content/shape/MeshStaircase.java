package com.skyeshade.skyent.content.shape;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Pixel-sized surface steps. Polygons, not their triangulation diagonals, define the boundary. */
public final class MeshStaircase {
    public static final double STEP = 1.0 / 16;
    public static final double SHEET_DEPTH = 1.0 / 1024;
    private static final double EPS = 1e-9;

    public record Result(List<AABB> boxes, List<AABB> unmerged) {
        public Result { boxes = List.copyOf(boxes); unmerged = List.copyOf(unmerged); }
    }

    private MeshStaircase() {}

    public static List<AABB> surface(List<Vec3> polygon, boolean closed, AABB footprint) {
        if (polygon.size() < 3) return List.of();
        Vec3 n = polygon.get(1).subtract(polygon.getFirst()).cross(polygon.get(2).subtract(polygon.getFirst())).normalize();
        if (n.lengthSqr() < .5) return List.of();
        // Project on the dominant plane: neither remaining derivative exceeds one.
        int axis = Math.abs(n.y) + EPS >= Math.abs(n.x) && Math.abs(n.y) + EPS >= Math.abs(n.z) ? 1
                : Math.abs(n.x) >= Math.abs(n.z) ? 0 : 2;
        int u = (axis + 1) % 3, v = (axis + 2) % 3;
        AABB bounds = bounds(polygon);
        if (Math.abs(coord(n, u)) < EPS && (Math.abs(coord(n, v)) >= EPS
                || max(bounds, v) - min(bounds, v) < max(bounds, u) - min(bounds, u))) {
            int swap = u; u = v; v = swap;
        }
        double du = -coord(n, u) / coord(n, axis), dv = -coord(n, v) / coord(n, axis);
        if (Math.abs(du) < EPS) du = 0;
        if (Math.abs(dv) < EPS) dv = 0;
        Vec3 origin = polygon.getFirst();
        double intercept = coord(origin, axis) - du * coord(origin, u) - dv * coord(origin, v);
        // Open sheets have no reliable winding. Horizontal support always lies below the face.
        boolean positive = closed ? coord(n, axis) > 0 : axis == 1 || coord(n, axis) > 0;
        if (dv == 0 && convex(polygon, axis))
            return strips(polygon, bounds, footprint, axis, u, v, du, intercept, positive);
        List<AABB> out = new ArrayList<>();
        for (int i = (int)Math.floor(min(bounds, u) / STEP); i * STEP < max(bounds, u) - EPS; i++)
            for (int j = (int)Math.floor(min(bounds, v) / STEP); j * STEP < max(bounds, v) - EPS; j++) {
                List<Vec3> tile = clip(polygon, u, i * STEP, true);
                tile = clip(tile, u, (i + 1) * STEP, false);
                tile = clip(tile, v, j * STEP, true);
                tile = clip(tile, v, (j + 1) * STEP, false);
                if (tile.size() < 3) continue;
                AABB b = bounds(tile);
                if (max(b, u) - min(b, u) < EPS || max(b, v) - min(b, v) < EPS) continue;
                double lo = intercept + du * (du >= 0 ? min(b, u) : max(b, u)) + dv * (dv >= 0 ? min(b, v) : max(b, v));
                double hi = intercept + du * (du >= 0 ? max(b, u) : min(b, u)) + dv * (dv >= 0 ? max(b, v) : min(b, v));
                // Entire exposed rectangle stays inside the plane. One pixel of backing connects
                // neighboring steps without extruding outwards or filling the machine's open bay.
                double depth = Math.max(SHEET_DEPTH, Math.min(STEP, Math.max(max(bounds, u) - min(bounds, u), max(bounds, v) - min(bounds, v))));
                depth = Math.max(depth, hi - lo);
                if (hi - lo < EPS) depth = SHEET_DEPTH;
                b = interval(b, axis, positive ? lo - depth : hi, positive ? lo : hi + depth);
                b = clipBounds(b, footprint);
                if (b != null) out.add(b);
            }
        return out;
    }

    /** A planar extrusion needs only one rectangle across its invariant direction per step. */
    private static List<AABB> strips(List<Vec3> polygon, AABB bounds, AABB footprint,
                                     int axis, int u, int v, double gradient, double intercept, boolean positive) {
        List<AABB> out = new ArrayList<>();
        for (int i = (int)Math.floor(min(bounds, u) / STEP); i * STEP < max(bounds, u) - EPS; i++) {
            double from = Math.max(min(bounds, u), i * STEP), to = Math.min(max(bounds, u), (i + 1) * STEP);
            double[] start = span(polygon, u, v, from), end = span(polygon, u, v, to);
            double a = Math.max(start[0], end[0]), b = Math.min(start[1], end[1]);
            if (b - a <= EPS) {
                // A terminal tip narrower than a pixel still gets a support/selection surface.
                double[] middle = span(polygon, u, v, (from + to) / 2);
                a = middle[0]; b = middle[1];
            }
            if (b - a <= EPS) continue;
            double low = intercept + gradient * (gradient >= 0 ? from : to);
            double high = intercept + gradient * (gradient >= 0 ? to : from);
            double depth = gradient == 0 ? SHEET_DEPTH : Math.min(STEP, max(bounds, u) - min(bounds, u));
            AABB box = interval(interval(bounds, u, from, to), v, a, b);
            box = clipBounds(interval(box, axis, positive ? low - depth : high, positive ? low : high + depth), footprint);
            if (box != null) out.add(box);
        }
        return out;
    }

    private static double[] span(List<Vec3> polygon, int u, int v, double at) {
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        Vec3 a = polygon.getLast();
        for (Vec3 b : polygon) {
            double x = coord(a, u), y = coord(b, u);
            if (at >= Math.min(x, y) - EPS && at <= Math.max(x, y) + EPS) {
                if (Math.abs(x - y) < EPS) {
                    lo = Math.min(lo, Math.min(coord(a, v), coord(b, v)));
                    hi = Math.max(hi, Math.max(coord(a, v), coord(b, v)));
                } else {
                    double value = coord(a, v) + (coord(b, v) - coord(a, v)) * Math.clamp((at - x) / (y - x), 0, 1);
                    lo = Math.min(lo, value); hi = Math.max(hi, value);
                }
            }
            a = b;
        }
        return new double[]{lo, hi};
    }

    private static boolean convex(List<Vec3> polygon, int axis) {
        double sign = 0;
        for (int i = 0; i < polygon.size(); i++) {
            Vec3 a = polygon.get(i), b = polygon.get((i + 1) % polygon.size()), c = polygon.get((i + 2) % polygon.size());
            double turn = coord(b.subtract(a).cross(c.subtract(b)), axis);
            if (Math.abs(turn) < EPS) continue;
            if (sign * turn < 0) return false;
            sign = turn;
        }
        return true;
    }

    public static Result finish(List<AABB> boxes) { return new Result(merge(boxes), boxes); }

    /** Merge whole rectangular runs to a fixed point; never enlarge a slope into a bounding volume. */
    public static List<AABB> merge(List<AABB> input) {
        List<AABB> boxes = new ArrayList<>(new LinkedHashSet<>(input));
        int before;
        do {
            before = boxes.size();
            for (int axis = 0; axis < 3; axis++) {
                final int a = axis, u = (axis + 1) % 3, v = (axis + 2) % 3;
                boxes.sort(Comparator.comparingLong((AABB b) -> key(min(b, u))).thenComparingLong(b -> key(max(b, u)))
                        .thenComparingLong(b -> key(min(b, v))).thenComparingLong(b -> key(max(b, v))).thenComparingDouble(b -> min(b, a)));
                List<AABB> merged = new ArrayList<>();
                for (AABB b : boxes) {
                    AABB last = merged.isEmpty() ? null : merged.getLast();
                    if (last != null && key(min(last, u)) == key(min(b, u)) && key(max(last, u)) == key(max(b, u))
                            && key(min(last, v)) == key(min(b, v)) && key(max(last, v)) == key(max(b, v))
                            && min(b, a) <= max(last, a) + EPS) merged.set(merged.size() - 1, last.minmax(b));
                    else merged.add(b);
                }
                boxes = merged;
            }
        } while (boxes.size() < before);
        return List.copyOf(boxes);
    }

    private static long key(double d) { return Math.round(d * 1e9); }
    private static List<Vec3> clip(List<Vec3> polygon, int axis, double plane, boolean greater) {
        if (polygon.isEmpty()) return polygon;
        List<Vec3> out = new ArrayList<>();
        Vec3 a = polygon.getLast();
        double da = (coord(a, axis) - plane) * (greater ? 1 : -1);
        for (Vec3 b : polygon) {
            double db = (coord(b, axis) - plane) * (greater ? 1 : -1);
            if ((da >= 0) != (db >= 0)) out.add(a.lerp(b, da / (da - db)));
            if (db >= 0) out.add(b);
            a = b; da = db;
        }
        return out;
    }
    private static AABB bounds(List<Vec3> points) {
        AABB b = new AABB(points.getFirst(), points.getFirst());
        for (Vec3 p : points) b = b.minmax(new AABB(p, p));
        return b;
    }
    private static AABB clipBounds(AABB b, AABB limit) {
        double ax = Math.max(b.minX, limit.minX), ay = Math.max(b.minY, limit.minY), az = Math.max(b.minZ, limit.minZ);
        double bx = Math.min(b.maxX, limit.maxX), by = Math.min(b.maxY, limit.maxY), bz = Math.min(b.maxZ, limit.maxZ);
        // AABB normalizes reversed coordinates; reject disjoint intervals before constructing it.
        return bx - ax > EPS && by - ay > EPS && bz - az > EPS ? new AABB(ax, ay, az, bx, by, bz) : null;
    }
    private static double coord(Vec3 p, int a) { return a == 0 ? p.x : a == 1 ? p.y : p.z; }
    private static double min(AABB b, int a) { return a == 0 ? b.minX : a == 1 ? b.minY : b.minZ; }
    private static double max(AABB b, int a) { return a == 0 ? b.maxX : a == 1 ? b.maxY : b.maxZ; }
    private static AABB interval(AABB b, int a, double lo, double hi) {
        return new AABB(a == 0 ? lo : b.minX, a == 1 ? lo : b.minY, a == 2 ? lo : b.minZ,
                a == 0 ? hi : b.maxX, a == 1 ? hi : b.maxY, a == 2 ? hi : b.maxZ);
    }
}
