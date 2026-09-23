package com.skyeshade.skyent.content.model.blockbench;

import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Immutable authored data. No sprites, render types, resource manager, or client dependencies.
 */
public record BlockbenchScene(Map<String, Node> nodes, List<String> roots, List<Texture> textures,
                              SkyentModelMetadata.Display display, List<String> metadataWarnings) {
    public BlockbenchScene(Map<String, Node> nodes, List<String> roots, List<Texture> textures) {
        this(nodes, roots, textures, SkyentModelMetadata.Display.ABSENT, List.of());
    }
    public CollisionHierarchy collisionHierarchy() { return CollisionHierarchy.of(this); }
    public BlockbenchScene {
        nodes = Map.copyOf(nodes);
        roots = List.copyOf(roots);
        textures = List.copyOf(textures);
        metadataWarnings = List.copyOf(metadataWarnings);
    }

    public enum RenderSides {AUTO, FRONT, DOUBLE}

    public record Texture(String uuid, String name, int uvWidth, int uvHeight, RenderSides renderSides) {
    }

    public record UV(double u, double v) {
    }

    public record Vertex(Vec3 position, UV uv) {
    }

    public record Face(String id, List<Vertex> vertices, int texture) {
        public Face {
            vertices = List.copyOf(vertices);
        }
    }

    /**
     * Face positions are relative to this node's pivot, including for cubes. Pivots are authored absolute positions.
     */
    public record Cube(Vec3 from, Vec3 to) {
        /**
         * Same preview thickness as the renderer; authored zero dimensions remain identifiable.
         */
        public Vec3 previewTo() {
            return new Vec3(to.x == from.x ? to.x + .001 : to.x,
                    to.y == from.y ? to.y + .001 : to.y, to.z == from.z ? to.z + .001 : to.z);
        }

        public int zeroDimensions() {
            return (from.x == to.x ? 1 : 0) + (from.y == to.y ? 1 : 0) + (from.z == to.z ? 1 : 0);
        }
    }

    public record Node(String uuid, String name, String type, Vec3 pivot, Vec3 rotation,
                       boolean visible, boolean export, List<String> children, List<Face> faces, Cube cube,
                       SkyentModelMetadata.Node metadata) {
        public Node(String uuid, String name, String type, Vec3 pivot, Vec3 rotation,
                    boolean visible, boolean export, List<String> children, List<Face> faces, Cube cube) {
            this(uuid,name,type,pivot,rotation,visible,export,children,faces,cube,SkyentModelMetadata.Node.DEFAULT);
        }
        public Node(String uuid, String name, String type, Vec3 pivot, Vec3 rotation,
                    boolean visible, boolean export, List<String> children, List<Face> faces) {
            this(uuid, name, type, pivot, rotation, visible, export, children, faces, null);
        }

        public Node {
            children = List.copyOf(children);
            faces = List.copyOf(faces);
        }
    }
}
