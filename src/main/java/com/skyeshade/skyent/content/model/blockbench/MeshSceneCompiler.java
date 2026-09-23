package com.skyeshade.skyent.content.model.blockbench;

import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Predicate;

import static com.skyeshade.skyent.content.model.blockbench.BlockbenchScene.*;

public final class MeshSceneCompiler {
    public static final String VERSION = "bb5-free-5-metadata";

    private MeshSceneCompiler() {
    }

    public record Triangle(String part, String path, String face, int texture, Vertex a, Vertex b, Vertex c,
                           Vec3 normal, boolean doubleSided) {
        public List<Vertex> vertices() {
            return List.of(a, b, c);
        }

        public Triangle reverse() {
            return new Triangle(part, path, face, texture, a, c, b, normal.scale(-1), false);
        }
    }

    /**
     * Polygon boundaries and cube corners survive triangulation, in the same authored-world frame.
     */
    public record Polygon(String face, List<Vec3> vertices, List<Triangle> triangles) {
        public Polygon {
            vertices = List.copyOf(vertices);
            triangles = List.copyOf(triangles);
        }
    }

    public record Primitive(String part, String path, String type, List<Vec3> cubeCorners,
                            int zeroDimensions, int sheetAxis, List<Polygon> polygons, CollisionHierarchy.State collision) {
        public Primitive(String part, String path, String type, List<Vec3> cubeCorners,
                         int zeroDimensions, int sheetAxis, List<Polygon> polygons) {
            this(part,path,type,cubeCorners,zeroDimensions,sheetAxis,polygons,CollisionHierarchy.State.DEFAULT);
        }
        public Primitive {
            cubeCorners = List.copyOf(cubeCorners);
            polygons = List.copyOf(polygons);
        }
    }

    public record Compiled(List<Triangle> triangles, Map<String, Vec3> pivots, List<String> diagnostics,
                           List<Primitive> primitives, boolean authoredCollision) {
        public Compiled(List<Triangle> triangles, Map<String, Vec3> pivots, List<String> diagnostics, List<Primitive> primitives) {
            this(triangles,pivots,diagnostics,primitives,false);
        }
        public Compiled {
            triangles = List.copyOf(triangles);
            pivots = Map.copyOf(pivots);
            diagnostics = List.copyOf(diagnostics);
            primitives = List.copyOf(primitives);
        }

        /**
         * Rendering duplicates only explicitly double-sided surfaces. Gameplay sees each surface once.
         */
        public List<Triangle> renderTriangles() {
            List<Triangle> result = new ArrayList<>();
            for (Triangle triangle : triangles) {
                result.add(triangle);
                if (triangle.doubleSided()) result.add(triangle.reverse());
            }
            return List.copyOf(result);
        }
    }

    /**
     * Selection is per node/subtree; excluding rotor preserves its sibling bearing supports.
     */
    public static Compiled compile(BlockbenchScene scene, Predicate<Node> include) {
        List<Triangle> triangles = new ArrayList<>();
        Map<String, Vec3> pivots = new LinkedHashMap<>();
        List<String> diagnostics = new ArrayList<>();
        List<Primitive> primitives = new ArrayList<>();
        var hierarchy = scene.collisionHierarchy();
        for (String root : scene.roots())
            visit(scene, root, "", List.of(), include, triangles, pivots, diagnostics, primitives, hierarchy);
        return new Compiled(triangles, pivots, diagnostics, primitives, hierarchy.authored());
    }

    private static void visit(BlockbenchScene scene, String id, String path, List<Node> ancestors, Predicate<Node> include,
                              List<Triangle> out, Map<String, Vec3> pivots, List<String> diagnostics, List<Primitive> primitives,
                              CollisionHierarchy hierarchy) {
        Node node = scene.nodes().get(id);
        if (!node.visible() || !node.export() || !include.test(node)) return;
        path += "/" + node.name();
        pivots.put(id, ancestors(node.pivot(), ancestors));
        boolean plane = node.type().equals("mesh") && planar(node.faces());
        List<Polygon> polygons = new ArrayList<>();
        for (Face face : node.faces()) {
            List<Vertex> vertices = new ArrayList<>();
            Texture texture = scene.textures().get(face.texture());
            boolean twoSided = (texture.renderSides() == RenderSides.DOUBLE
                    || texture.renderSides() == RenderSides.AUTO && plane) && !hasReverse(face, node.faces());
            for (Vertex v : face.vertices()) {
                Vec3 p = transform(v.position(), node, ancestors);
                vertices.add(new Vertex(p, new UV(v.uv().u() / texture.uvWidth(), v.uv().v() / texture.uvHeight())));
            }
            List<Triangle> faceTriangles = new ArrayList<>();
            for (int i = 1; i < vertices.size() - 1; i++) {
                Vertex a = vertices.get(0), b = vertices.get(i), c = vertices.get(i + 1);
                Vec3 normal = b.position().subtract(a.position()).cross(c.position().subtract(a.position()));
                if (normal.lengthSqr() < 1e-16) {
                    diagnostics.add("Degenerate face " + id + "/" + face.id());
                    continue;
                }
                Triangle triangle = new Triangle(id, path, face.id(), face.texture(), a, b, c, normal.normalize(), twoSided);
                out.add(triangle);
                faceTriangles.add(triangle);
            }
            polygons.add(new Polygon(face.id(), vertices.stream().map(Vertex::position).toList(), faceTriangles));
        }
        List<Vec3> corners = new ArrayList<>();
        if (node.cube() != null) {
            Vec3 a = node.cube().from(), b = node.cube().previewTo();
            for (int i = 0; i < 8; i++)
                corners.add(transform(new Vec3((i & 1) == 0 ? a.x : b.x, (i & 2) == 0 ? a.y : b.y,
                        (i & 4) == 0 ? a.z : b.z), node, ancestors));
        }
        if (!polygons.isEmpty()) primitives.add(new Primitive(id, path, node.type(), corners,
                node.cube() == null ? 0 : node.cube().zeroDimensions(),
                node.cube() == null || node.cube().zeroDimensions() != 1 ? -1 :
                        node.cube().from().x == node.cube().to().x ? 0 : node.cube().from().y == node.cube().to().y ? 1 : 2, polygons,
                hierarchy.states().get(id)));
        List<Node> next = new ArrayList<>(ancestors);
        next.add(node);
        for (String child : node.children())
            visit(scene, child, path, next, include, out, pivots, diagnostics, primitives, hierarchy);
    }

    private static Vec3 transform(Vec3 point, Node node, List<Node> ancestors) {
        Vec3 local = node.type().equals("mesh") ? rotateMesh(point, node.rotation()) : rotate(point, node.rotation());
        return ancestors(local.add(node.pivot()), ancestors);
    }

    // Blockbench free/auto previews both sides. For runtime, infer that only for truly planar meshes;
    // closed chassis keeps culling. Non-planar open sheets can explicitly request texture render_sides=double.
    private static boolean planar(List<Face> faces) {
        Vec3 origin = null, normal = null;
        for (Face face : faces) {
            Vec3 a = face.vertices().getFirst().position();
            for (int i = 1; i + 1 < face.vertices().size(); i++) {
                Vec3 n = face.vertices().get(i).position().subtract(a).cross(face.vertices().get(i + 1).position().subtract(a));
                if (n.lengthSqr() > 1e-16) {
                    origin = a;
                    normal = n.normalize();
                    break;
                }
            }
            if (normal != null) break;
        }
        if (normal == null) return false;
        for (Face face : faces)
            for (Vertex v : face.vertices())
                if (Math.abs(v.position().subtract(origin).dot(normal)) > 1e-7) return false;
        return true;
    }

    private static boolean hasReverse(Face face, List<Face> faces) {
        var points = face.vertices().stream().map(Vertex::position).toList();
        Vec3 n = points.get(1).subtract(points.get(0)).cross(points.get(2).subtract(points.get(0)));
        for (Face other : faces) {
            if (other == face || other.vertices().size() != points.size()) continue;
            var q = other.vertices().stream().map(Vertex::position).toList();
            if (points.containsAll(q) && n.dot(q.get(1).subtract(q.get(0)).cross(q.get(2).subtract(q.get(0)))) < 0)
                return true;
        }
        return false;
    }

    private static Vec3 ancestors(Vec3 point, List<Node> parents) {
        for (int i = parents.size() - 1; i >= 0; i--) {
            Node parent = parents.get(i);
            point = rotate(point.subtract(parent.pivot()), parent.rotation()).add(parent.pivot());
        }
        return point;
    }

    /**
     * Three.js Euler ZYX: apply X then Y then Z. No engine yaw convention leaks into authored space.
     */
    public static Vec3 rotate(Vec3 p, Vec3 degrees) {
        double x = Math.toRadians(degrees.x), y = Math.toRadians(degrees.y), z = Math.toRadians(degrees.z);
        p = new Vec3(p.x, p.y * Math.cos(x) - p.z * Math.sin(x), p.y * Math.sin(x) + p.z * Math.cos(x));
        p = new Vec3(p.x * Math.cos(y) + p.z * Math.sin(y), p.y, -p.x * Math.sin(y) + p.z * Math.cos(y));
        return new Vec3(p.x * Math.cos(z) - p.y * Math.sin(z), p.x * Math.sin(z) + p.y * Math.cos(z), p.z);
    }

    /**
     * Mesh.preview_controller.setup does NOT assign Format.euler_order in Blockbench 5.0.
     * Its THREE.Mesh retains XYZ (apply Z then Y then X), unlike cubes and groups.
     */
    public static Vec3 rotateMesh(Vec3 p, Vec3 degrees) {
        double x = Math.toRadians(degrees.x), y = Math.toRadians(degrees.y), z = Math.toRadians(degrees.z);
        p = new Vec3(p.x * Math.cos(z) - p.y * Math.sin(z), p.x * Math.sin(z) + p.y * Math.cos(z), p.z);
        p = new Vec3(p.x * Math.cos(y) + p.z * Math.sin(y), p.y, -p.x * Math.sin(y) + p.z * Math.cos(y));
        return new Vec3(p.x, p.y * Math.cos(x) - p.z * Math.sin(x), p.y * Math.sin(x) + p.z * Math.cos(x));
    }
}
