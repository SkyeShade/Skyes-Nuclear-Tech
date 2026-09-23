package com.skyeshade.skyent.content.model.blockbench;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;

import java.io.Reader;
import java.util.*;

import static com.skyeshade.skyent.content.model.blockbench.BlockbenchScene.*;

/**
 * Bounded adapter for Blockbench 5.0 free (XYZ meshes, ZYX cubes/groups, absolute group pivots).
 */
public final class BlockbenchParser {
    private BlockbenchParser() {
    }

    public static BlockbenchScene parse(Reader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        List<String> warnings = new ArrayList<>();
        var display = SkyentModelMetadata.display(json, warnings::add);
        JsonObject meta = json.getAsJsonObject("meta");
        require(meta != null && "5.0".equals(string(meta, "format_version", ""))
                && "free".equals(string(meta, "model_format", "")), "Expected Blockbench 5.0 free model");
        List<Texture> textures = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray("textures")) {
            JsonObject t = entry.getAsJsonObject();
            int w = t.get("uv_width").getAsInt(), h = t.get("uv_height").getAsInt();
            require(w > 0 && h > 0, "Texture requires positive authored UV dimensions");
            textures.add(new Texture(string(t, "uuid", ""), string(t, "name", ""), w, h,
                    RenderSides.valueOf(string(t, "render_sides", "auto").toUpperCase(Locale.ROOT))));
        }
        Map<String, JsonObject> source = new LinkedHashMap<>();
        for (String section : List.of("groups", "elements")) {
            if (!json.has(section)) continue;
            for (JsonElement entry : json.getAsJsonArray(section)) {
                JsonObject n = entry.getAsJsonObject();
                String id = n.get("uuid").getAsString();
                require(source.putIfAbsent(id, n) == null, "Duplicate UUID " + id);
            }
        }
        Map<String, List<String>> children = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<String> roots = hierarchy(json.getAsJsonArray("outliner"), source, children, seen, 0);
        require(seen.size() == source.size(), "Unreferenced nodes in outliner");
        Map<String, Node> nodes = new LinkedHashMap<>();
        for (var entry : source.entrySet()) {
            String id = entry.getKey();
            JsonObject n = entry.getValue();
            String type = string(n, "type", "group");
            require(Set.of("group", "cube", "mesh").contains(type), "Unsupported node type " + type + " at " + id);
            require(!n.has("scale") || vec(n.get("scale")).equals(new Vec3(1, 1, 1)), "Node scale unsupported at " + id);
            require(!bool(n, "rescale", false), "Cube rescale unsupported at " + id);
            require(!n.has("stretch") || vec(n.get("stretch")).equals(new Vec3(1, 1, 1)), "Cube stretch unsupported at " + id);
            require(!n.has("inflate") || n.get("inflate").getAsDouble() == 0, "Cube inflate unsupported at " + id);
            require("flat".equals(string(n, "shading", "flat")), "Only flat shading is supported at " + id);
            Vec3 pivot = vector(n, "origin");
            boolean visible = bool(n, "visibility", true), export = bool(n, "export", true);
            // Hidden editor guides may intentionally have no material. They retain metadata but no render faces.
            List<Face> faces = !visible || !export || type.equals("group") ? List.of()
                    : type.equals("mesh") ? mesh(n, textures.size()) : cube(n, pivot, textures.size(), bool(meta, "box_uv", false));
            nodes.put(id, new Node(id, string(n, "name", id), type, pivot, vector(n, "rotation"),
                    visible, export, children.getOrDefault(id, List.of()), faces,
                    type.equals("cube") ? new Cube(vec(n.get("from")).subtract(pivot), vec(n.get("to")).subtract(pivot)) : null,
                    SkyentModelMetadata.node(n, id, warnings::add)));
        }
        warnings.forEach(w -> com.mojang.logging.LogUtils.getLogger().warn("Blockbench metadata: {}", w));
        return new BlockbenchScene(nodes, roots, textures, display, warnings);
    }

    private static List<String> hierarchy(JsonArray array, Map<String, JsonObject> source,
                                          Map<String, List<String>> children, Set<String> seen, int depth) {
        require(depth < 128, "Outliner exceeds maximum depth");
        List<String> result = new ArrayList<>();
        for (JsonElement e : array) {
            String id = e.isJsonPrimitive() ? e.getAsString() : e.getAsJsonObject().get("uuid").getAsString();
            require(source.containsKey(id) && seen.add(id), "Missing, duplicate or cyclic outliner UUID " + id);
            result.add(id);
            if (e.isJsonObject())
                children.put(id, hierarchy(e.getAsJsonObject().getAsJsonArray("children"), source, children, seen, depth + 1));
        }
        return result;
    }

    private static List<Face> mesh(JsonObject n, int textureCount) {
        Map<String, Vec3> positions = new HashMap<>();
        n.getAsJsonObject("vertices").entrySet().forEach(e -> positions.put(e.getKey(), vec(e.getValue())));
        List<Face> faces = new ArrayList<>();
        for (var entry : n.getAsJsonObject("faces").entrySet()) {
            JsonObject f = entry.getValue().getAsJsonObject();
            if (f.has("texture") && f.get("texture").isJsonNull()) continue;
            List<String> ids = new ArrayList<>();
            for (JsonElement vertex : f.getAsJsonArray("vertices")) ids.add(vertex.getAsString());
            require(ids.size() == 3 || ids.size() == 4, "Only triangles/quads supported: " + entry.getKey());
            for (String id : ids) require(positions.containsKey(id), "Missing vertex " + id);
            // Blockbench MeshFace.getSortedVertices: preserves winding and repairs serialized crossed quads.
            if (ids.size() == 4) {
                Vec3 a = positions.get(ids.get(0)), b = positions.get(ids.get(1));
                Vec3 c = positions.get(ids.get(2)), d = positions.get(ids.get(3));
                if (opposite(b, c, a, d)) ids = List.of(ids.get(2), ids.get(0), ids.get(1), ids.get(3));
                else if (opposite(a, b, c, d)) ids = List.of(ids.get(0), ids.get(2), ids.get(1), ids.get(3));
            }
            List<Vertex> vertices = new ArrayList<>();
            for (String id : ids) {
                JsonArray uv = f.getAsJsonObject("uv").getAsJsonArray(id);
                require(uv != null && uv.size() == 2, "Missing UV for " + id);
                vertices.add(new Vertex(positions.get(id), new UV(number(uv.get(0)), number(uv.get(1)))));
            }
            faces.add(new Face(entry.getKey(), vertices, texture(f, textureCount)));
        }
        return faces;
    }

    private static boolean opposite(Vec3 a, Vec3 b, Vec3 top, Vec3 check) {
        Vec3 edge = b.subtract(a);
        if (edge.lengthSqr() < 1e-20) return false;
        Vec3 normal = a.add(edge.scale(top.subtract(a).dot(edge) / edge.lengthSqr())).subtract(top);
        return normal.dot(check.subtract(b)) > 1e-10;
    }

    private static List<Face> cube(JsonObject n, Vec3 pivot, int textureCount, boolean projectBoxUv) {
        Vec3 from = vec(n.get("from")), to = vec(n.get("to"));
        require(to.x >= from.x && to.y >= from.y && to.z >= from.z, "Inverted cube bounds unsupported");
        // Match Blockbench's 0.001 authored-unit thickness for planar cubes (glass panels).
        to = new Cube(from, to).previewTo();
        double x = from.x, y = from.y, z = from.z, X = to.x, Y = to.y, Z = to.z;
        Map<String, List<Vec3>> corners = Map.of(
                "east", List.of(new Vec3(X, Y, Z), new Vec3(X, Y, z), new Vec3(X, y, Z), new Vec3(X, y, z)),
                "west", List.of(new Vec3(x, Y, z), new Vec3(x, Y, Z), new Vec3(x, y, z), new Vec3(x, y, Z)),
                "up", List.of(new Vec3(x, Y, z), new Vec3(X, Y, z), new Vec3(x, Y, Z), new Vec3(X, Y, Z)),
                "down", List.of(new Vec3(x, y, Z), new Vec3(X, y, Z), new Vec3(x, y, z), new Vec3(X, y, z)),
                "south", List.of(new Vec3(x, Y, Z), new Vec3(X, Y, Z), new Vec3(x, y, Z), new Vec3(X, y, Z)),
                "north", List.of(new Vec3(X, Y, z), new Vec3(x, Y, z), new Vec3(X, y, z), new Vec3(x, y, z)));
        List<Face> faces = new ArrayList<>();
        for (String side : List.of("east", "west", "up", "down", "south", "north")) {
            JsonObject f = n.getAsJsonObject("faces").getAsJsonObject(side);
            if (f == null || (f.has("texture") && f.get("texture").isJsonNull())) continue;
            double[] rect;
            if (bool(n, "box_uv", projectBoxUv)) rect = boxUV(n, side);
            else {
                JsonArray uv = f.getAsJsonArray("uv");
                rect = new double[]{number(uv.get(0)), number(uv.get(1)), number(uv.get(2)), number(uv.get(3))};
            }
            UV[] uv = {new UV(rect[0], rect[1]), new UV(rect[2], rect[1]), new UV(rect[0], rect[3]), new UV(rect[2], rect[3])};
            int rotation = f.has("rotation") ? f.get("rotation").getAsInt() : 0;
            require(rotation >= 0 && rotation < 360 && rotation % 90 == 0, "Invalid cube UV rotation");
            for (int r = 0; r < rotation; r += 90) uv = new UV[]{uv[2], uv[0], uv[3], uv[1]};
            List<Vertex> vertices = new ArrayList<>();
            // Cube diagonal and winding match Blockbench's indices 0,2,1 / 2,3,1.
            for (int i : new int[]{2, 3, 1, 0})
                vertices.add(new Vertex(corners.get(side).get(i).subtract(pivot), uv[i]));
            faces.add(new Face(side, vertices, texture(f, textureCount)));
        }
        return faces;
    }

    private static double[] boxUV(JsonObject n, String side) {
        Vec3 size = vec(n.get("to")).subtract(vec(n.get("from")));
        double x = Math.floor(size.x), y = Math.floor(size.y), z = Math.floor(size.z);
        boolean mirror = bool(n, "mirror_uv", false);
        if (mirror && side.equals("east")) side = "west";
        else if (mirror && side.equals("west")) side = "east";
        double[] r = switch (side) {
            case "east" -> new double[]{0, z, z, z + y};
            case "west" -> new double[]{z + x, z, 2 * z + x, z + y};
            case "up" -> new double[]{z + x, z, z, 0};
            case "down" -> new double[]{z + 2 * x, 0, z + x, z};
            case "south" -> new double[]{2 * z + x, z, 2 * z + 2 * x, z + y};
            default -> new double[]{z, z, z + x, z + y};
        };
        if (mirror) {
            double temp = r[0];
            r[0] = r[2];
            r[2] = temp;
        }
        JsonArray offset = n.getAsJsonArray("uv_offset");
        for (int i = 0; i < 4; i++) r[i] += number(offset.get(i % 2));
        return r;
    }

    private static int texture(JsonObject f, int count) {
        require(f.has("texture") && f.get("texture").isJsonPrimitive()
                && f.getAsJsonPrimitive("texture").isNumber(), "Visible face has no texture index");
        int index = f.get("texture").getAsInt();
        require(index >= 0 && index < count, "Unresolved texture index " + index);
        return index;
    }

    private static String string(JsonObject n, String key, String fallback) {
        return n.has(key) ? n.get(key).getAsString() : fallback;
    }

    private static boolean bool(JsonObject n, String key, boolean fallback) {
        return n.has(key) ? n.get(key).getAsBoolean() : fallback;
    }

    private static Vec3 vector(JsonObject n, String key) {
        return n.has(key) ? vec(n.get(key)) : Vec3.ZERO;
    }

    private static Vec3 vec(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        require(a.size() == 3, "Expected 3-vector");
        return new Vec3(number(a.get(0)), number(a.get(1)), number(a.get(2)));
    }

    private static double number(JsonElement e) {
        double d = e.getAsDouble();
        require(Double.isFinite(d), "Non-finite coordinate");
        return d;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new JsonParseException(message);
    }
}
