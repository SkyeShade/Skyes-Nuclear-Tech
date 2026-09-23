package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.model.blockbench.*;
import com.skyeshade.skyent.content.shape.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.*;

import static com.skyeshade.skyent.content.model.blockbench.BlockbenchScene.*;
import static org.junit.jupiter.api.Assertions.*;

class MeshHybridShapeTest {
    private static final MachineModelTransform TRANSFORM = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
    private static final Vec3 FROM = new Vec3(-8.37, 6.23, -12.19), TO = new Vec3(-4.24, 10.14, -3.90);

    private static MeshSceneCompiler.Compiled cube(Vec3 rotation, boolean sheet, String parent) {
        String faces = String.join(",", List.of("north", "south", "east", "west", "up", "down").stream()
                .map(f -> "\"" + f + "\":{\"texture\":0,\"uv\":[0,0,16,16]}").toList());
        String json = """
                {"meta":{"format_version":"5.0","model_format":"free"},
                "textures":[{"uuid":"t","name":"test","uv_width":16,"uv_height":16}],
                "elements":[{"uuid":"cube","type":"cube","origin":[-6.305,8.185,-8.045],
                "from":[-8.37,6.23,-12.19],"to":[-4.24,%s,-3.90],"rotation":[%s,%s,%s],"faces":{%s}}],
                "groups":[%s],"outliner":[%s]}
                """.formatted(sheet ? "6.23" : "10.14", rotation.x, rotation.y, rotation.z, faces, parent,
                parent.isEmpty() ? "\"cube\"" : "{\"uuid\":\"parent\",\"children\":[\"cube\"]}");
        return MeshSceneCompiler.compile(BlockbenchParser.parse(new StringReader(json)), n -> true);
    }

    private static MeshHybridShapeCompiler.Result shapes(MeshSceneCompiler.Compiled compiled, int resolution, MeshVoxelizer.SurfacePolicy policy) {
        return MeshHybridShapeCompiler.compile(compiled, TRANSFORM, 3, 3, 5, resolution, (p, f) -> policy);
    }

    private static void near(AABB expected, AABB actual, double epsilon) {
        assertEquals(expected.minX, actual.minX, epsilon);
        assertEquals(expected.minY, actual.minY, epsilon);
        assertEquals(expected.minZ, actual.minZ, epsilon);
        assertEquals(expected.maxX, actual.maxX, epsilon);
        assertEquals(expected.maxY, actual.maxY, epsilon);
        assertEquals(expected.maxZ, actual.maxZ, epsilon);
    }

    @Test
    void unrotatedCubeBypassesGridWithExactNonGridBounds() {
        var c = cube(Vec3.ZERO, false, "");
        for (int resolution : List.of(8, 16)) {
            var r = shapes(c, resolution, MeshVoxelizer.SurfacePolicy.SHELL);
            assertEquals(1, r.cuboids().size());
            assertEquals(0, r.sheets().size());
            assertEquals(0, r.fallbackTriangles());
            assertTrue(r.fallback().unmerged().isEmpty());
            near(new AABB(TRANSFORM.footprint(FROM), TRANSFORM.footprint(TO)), r.boxes().getFirst(), 1e-12);
        }
    }

    @Test
    void quarterTurnCubeHasExactBounds() {
        var r = shapes(cube(new Vec3(0, 90, 0), false, ""), 8, MeshVoxelizer.SurfacePolicy.SHELL);
        assertEquals(1, r.cuboids().size());
        Vec3 center = TRANSFORM.footprint(FROM.add(TO).scale(.5));
        Vec3 half = new Vec3((TO.z - FROM.z) / 16, (TO.y - FROM.y) / 16, (TO.x - FROM.x) / 16);
        near(new AABB(center.subtract(half), center.add(half)), r.boxes().getFirst(), 1e-12);
    }

    @Test
    void tinyQuarterTurnNoiseIsToleratedButSmallRealAnglesAreNot() {
        var exact = shapes(cube(new Vec3(0, 90, 0), false, ""), 8, MeshVoxelizer.SurfacePolicy.SHELL);
        var noise = shapes(cube(new Vec3(0, 90.000001, 0), false, ""), 8, MeshVoxelizer.SurfacePolicy.SHELL);
        assertEquals(1, noise.cuboids().size());
        near(exact.boxes().getFirst(), noise.boxes().getFirst(), 2e-8);
        assertTrue(shapes(cube(new Vec3(0, 90.001, 0), false, ""), 8, MeshVoxelizer.SurfacePolicy.SHELL).cuboids().isEmpty());
    }

    @Test
    void arbitraryAndCompoundRotationUseFallback() {
        for (Vec3 angle : List.of(new Vec3(0, 22.5, 0), new Vec3(17, 31, 9))) {
            var r = shapes(cube(angle, false, ""), 8, MeshVoxelizer.SurfacePolicy.SHELL);
            assertTrue(r.cuboids().isEmpty());
            assertTrue(r.sheets().isEmpty());
            assertEquals(12, r.fallbackTriangles());
            assertEquals(1, r.fallbackPrimitives());
            assertFalse(r.fallback().boxes().isEmpty());
        }
    }

    @Test
    void classificationOccursAfterNestedHierarchyNotLocalRotation() {
        var r = shapes(cube(new Vec3(0, 22.5, 0), false,
                "{\"uuid\":\"parent\",\"origin\":[-6.305,8.185,-8.045],\"rotation\":[0,-22.5,0]}"), 8, MeshVoxelizer.SurfacePolicy.SHELL);
        assertEquals(1, r.cuboids().size());
        near(new AABB(TRANSFORM.footprint(FROM), TRANSFORM.footprint(TO)), r.boxes().getFirst(), 1e-12);
    }

    @Test
    void planarCubePreservesTangentialExtentsAndRenderedTop() {
        for (int resolution : List.of(8, 16)) {
            var r = shapes(cube(Vec3.ZERO, true, ""), resolution, MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET);
            assertEquals(1, r.sheets().size());
            assertTrue(r.cuboids().isEmpty());
            assertEquals(0, r.fallbackTriangles());
            var b = r.boxes().getFirst();
            var from = TRANSFORM.footprint(FROM);
            var to = TRANSFORM.footprint(TO);
            assertEquals(from.x, b.minX, 1e-12);
            assertEquals(to.x, b.maxX, 1e-12);
            assertEquals(from.z, b.minZ, 1e-12);
            assertEquals(to.z, b.maxZ, 1e-12);
            assertEquals(MeshStaircase.SHEET_DEPTH, b.getYsize(), 1e-12);
            assertEquals(from.y + .001 / 8, b.maxY, 1e-12);
        }
    }

    private static MeshSceneCompiler.Compiled plane(Vec3 rotation, Vec3... points) {
        var face = new Face("face", Arrays.stream(points).map(p -> new Vertex(p, new UV(0, 0))).toList(), 0);
        var node = new Node("plane", "plane", "mesh", new Vec3(-8, 8, -12), rotation, true, true, List.of(), List.of(face));
        return MeshSceneCompiler.compile(new BlockbenchScene(Map.of("plane", node), List.of("plane"),
                List.of(new Texture("t", "t", 16, 16, RenderSides.AUTO))), n -> true);
    }

    private static final Vec3[] PLANE = {new Vec3(.13, 0, .29), new Vec3(4.43, 0, .29), new Vec3(4.43, 0, 7.81), new Vec3(.13, 0, 7.81)};

    @Test
    void axisAlignedMeshSheetOnlyExpandsNormalInEveryAxis() {
        for (Vec3 rotation : List.of(Vec3.ZERO, new Vec3(90, 0, 0), new Vec3(0, 0, 90))) {
            var c = plane(rotation, PLANE);
            var r = shapes(c, 8, MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET);
            assertEquals(1, r.sheets().size());
            assertEquals(0, r.fallbackTriangles());
            var vertices = c.primitives().getFirst().polygons().getFirst().vertices().stream().map(TRANSFORM::footprint).toList();
            AABB bounds = new AABB(vertices.get(0), vertices.get(2));
            Vec3 n = c.triangles().getFirst().normal();
            var box = r.boxes().getFirst();
            if (Math.abs(n.x) < .5) { assertEquals(bounds.minX, box.minX, 1e-12); assertEquals(bounds.maxX, box.maxX, 1e-12); }
            if (Math.abs(n.y) < .5) { assertEquals(bounds.minY, box.minY, 1e-12); assertEquals(bounds.maxY, box.maxY, 1e-12); }
            if (Math.abs(n.z) < .5) { assertEquals(bounds.minZ, box.minZ, 1e-12); assertEquals(bounds.maxZ, box.maxZ, 1e-12); }
            if (Math.abs(n.y) > .5) assertEquals(bounds.maxY, box.maxY, 1e-12);
            assertEquals(MeshStaircase.SHEET_DEPTH, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())), 1e-12);
        }
    }

    @Test
    void slopedPlaneAndNonRectangularAxisAlignedFacesStayFallback() {
        for (var c : List.of(plane(new Vec3(0, 0, 22.5), PLANE),
                plane(Vec3.ZERO, PLANE[0], PLANE[1], PLANE[2]),
                plane(Vec3.ZERO, PLANE[0], PLANE[1], new Vec3(3, 0, 7.81), PLANE[3]))) {
            var r = shapes(c, 8, MeshVoxelizer.SurfacePolicy.SHELL);
            assertTrue(r.sheets().isEmpty());
            assertTrue(r.fallbackTriangles() > 0);
        }
    }

    @Test
    void hybridAssemblyRetainsBothKindsWithoutRerasterizingExactGeometry() {
        var a = cube(Vec3.ZERO, false, "");
        var b = plane(new Vec3(0, 0, 22.5), PLANE);
        List<MeshSceneCompiler.Primitive> primitives = new ArrayList<>(a.primitives());
        primitives.addAll(b.primitives());
        var c = new MeshSceneCompiler.Compiled(List.of(), Map.of(), List.of(), primitives);
        var r = shapes(c, 8, MeshVoxelizer.SurfacePolicy.SHELL);
        assertEquals(1, r.cuboids().size());
        assertEquals(2, r.fallbackTriangles());
        assertEquals(1 + r.fallback().boxes().size(), r.boxes().size());
        assertEquals(shapes(b, 8, MeshVoxelizer.SurfacePolicy.SHELL).fallback().boxes(), r.fallback().boxes());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB box = r.cuboids().getFirst().bounds();
            near(new AABB(TRANSFORM.relative(new Vec3(box.minX, box.minY, box.minZ), facing),
                    TRANSFORM.relative(new Vec3(box.maxX, box.maxY, box.maxZ), facing)), TRANSFORM.relativeBounds(box, facing), 1e-12);
        }
    }

    @Test
    void excludedGeometryDoesNotAcquireCollision() {
        var r = MeshHybridShapeCompiler.compile(cube(Vec3.ZERO, false, ""), TRANSFORM, 3, 3, 5, 8, (p, f) -> null);
        assertTrue(r.boxes().isEmpty());
    }

    @Test
    void flatTopKeepsItsExactNonGridHeightWithoutOutwardThickness() {
        for (double y : new double[]{10.5, 10.5173}) {
            var compiled = plane(Vec3.ZERO, new Vec3(.13,y,.29), new Vec3(4.43,y,.29),
                    new Vec3(4.43,y,7.81), new Vec3(.13,y,7.81));
            var result = shapes(compiled, 16, MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET);
            double rendered = compiled.primitives().getFirst().polygons().getFirst().vertices().stream()
                    .map(TRANSFORM::footprint).mapToDouble(p -> p.y).max().orElseThrow();
            assertEquals(rendered, result.boxes().getFirst().maxY, 1e-12);
            assertEquals(0, result.fallback().boxes().size());
        }
    }

    @Test
    void closedMeshShellDoesNotExpandItsExternalBounds() {
        var cube = cube(Vec3.ZERO, false, "");
        var primitive = cube.primitives().getFirst();
        var mesh = new MeshSceneCompiler.Primitive("mesh", "mesh", "mesh", List.of(), 0, -1, primitive.polygons());
        var compiled = new MeshSceneCompiler.Compiled(cube.triangles(), Map.of(), List.of(), List.of(mesh));
        var result = shapes(compiled, 8, MeshVoxelizer.SurfacePolicy.SHELL);
        assertEquals(6, result.sheets().size());
        AABB bounds = result.boxes().getFirst();
        for (AABB box : result.boxes()) bounds = bounds.minmax(box);
        near(new AABB(TRANSFORM.footprint(FROM), TRANSFORM.footprint(TO)), bounds, 1e-12);
    }

    @Test
    void turbineHousingRailAndCanopyHaveExactTangentialBounds() throws Exception {
        var def = MeshMachineDefinition.LARGE_STEAM_TURBINE;
        try (var input = getClass().getResourceAsStream("/assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel")) {
            assertNotNull(input);
            var compiled = def.compileStatic(def.parse(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)));
            var result = shapes(compiled, 16, MeshVoxelizer.SurfacePolicy.SHELL);
            for (String face : List.of("kmBo6ivq", "0H3vAdJ2", "vy7ZeJ6j", "rnIuLWhN", "qNnWj42M")) {
                var box = result.sheets().stream().filter(b -> b.face().equals(face)).findFirst().orElseThrow().bounds();
                var t = compiled.triangles().stream().filter(triangle -> triangle.face().equals(face)).findFirst().orElseThrow();
                var polygon = compiled.primitives().stream().flatMap(p -> p.polygons().stream()).filter(p -> p.face().equals(face)).findFirst().orElseThrow();
                var v = polygon.vertices().stream().map(TRANSFORM::footprint).toList();
                AABB authored = new AABB(v.get(0),v.get(2));
                if (Math.abs(t.normal().x) < .5) { assertEquals(authored.minX,box.minX,1e-12); assertEquals(authored.maxX,box.maxX,1e-12); }
                if (Math.abs(t.normal().y) < .5) { assertEquals(authored.minY,box.minY,1e-12); assertEquals(authored.maxY,box.maxY,1e-12); }
                if (Math.abs(t.normal().z) < .5) { assertEquals(authored.minZ,box.minZ,1e-12); assertEquals(authored.maxZ,box.maxZ,1e-12); }
            }
            var glass = result.sheets().stream().filter(b -> b.part().equals("df0c753c-46f2-a345-e2fb-fb4782f65dd4")).findFirst().orElseThrow().bounds();
            assertEquals(.96875,glass.minX,1e-12); assertEquals(1.96875,glass.maxX,1e-12);
            assertEquals(1.875,glass.minZ,1e-12); assertEquals(4.5,glass.maxZ,1e-12);
        }
    }
}
