package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.model.blockbench.*;
import com.skyeshade.skyent.content.shape.MeshVoxelizer;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.skyeshade.skyent.content.model.blockbench.BlockbenchScene.*;
import static org.junit.jupiter.api.Assertions.*;

class MeshSurfaceTest {
    private static final Vec3 A = Vec3.ZERO, B = new Vec3(1, 0, 0), C = new Vec3(0, 1, 0), D = new Vec3(0, 0, 1);

    private static Face face(String name, Vec3... p) {
        List<Vertex> vertices = new ArrayList<>();
        for (int i = 0; i < p.length; i++) vertices.add(new Vertex(p[i], new UV(i * 16, i * 8)));
        return new Face(name, vertices, 0);
    }

    private static MeshSceneCompiler.Compiled compile(List<Face> faces, Vec3 rotation, RenderSides sides) {
        Node node = new Node("plane", "plane", "mesh", new Vec3(2, 3, 4), rotation, true, true, List.of(), faces);
        return MeshSceneCompiler.compile(new BlockbenchScene(Map.of("plane", node), List.of("plane"),
                List.of(new Texture("t", "sheet", 64, 32, sides))), n -> true);
    }

    @Test
    void closedGeometryKeepsBackfaceCulling() {
        var c = compile(List.of(face("base", A, C, B), face("side1", A, B, D), face("side2", A, D, C), face("side3", B, C, D)), Vec3.ZERO, RenderSides.AUTO);
        assertEquals(4, c.renderTriangles().size());
        assertTrue(c.triangles().stream().noneMatch(MeshSceneCompiler.Triangle::doubleSided));
    }

    @Test
    void doubleSidedPlanePreservesUvAndOpposesWinding() {
        checkPlane(Vec3.ZERO);
    }

    @Test
    void rotatedDoubleSidedPlane() {
        checkPlane(new Vec3(0, 90, 0));
    }

    @Test
    void compoundRotatedDoubleSidedPlane() {
        checkPlane(new Vec3(27, -41, 63));
    }

    private static void checkPlane(Vec3 rotation) {
        var c = compile(List.of(face("plane", A, B, C)), rotation, RenderSides.AUTO);
        assertEquals(1, c.triangles().size());
        var both = c.renderTriangles();
        assertEquals(2, both.size());
        var front = both.get(0);
        var back = both.get(1);
        assertEquals(front.a(), back.a());
        assertEquals(front.b(), back.c());
        assertEquals(front.c(), back.b());
        assertEquals(-1, front.normal().dot(back.normal()), 1e-10);
        for (var t : both) {
            Vec3 normal = t.b().position().subtract(t.a().position()).cross(t.c().position().subtract(t.a().position())).normalize();
            assertEquals(0, normal.distanceTo(t.normal()), 1e-10);
        }
    }

    @Test
    void explicitFrontAndAlreadyAuthoredBackFacesAreRespected() {
        assertEquals(1, compile(List.of(face("f", A, B, C)), Vec3.ZERO, RenderSides.FRONT).renderTriangles().size());
        assertEquals(2, compile(List.of(face("f", A, B, C), face("back", A, C, B)), Vec3.ZERO, RenderSides.DOUBLE).renderTriangles().size());
        assertEquals(4, compile(List.of(face("f", A, B, C), face("bend", A, D, B)), Vec3.ZERO, RenderSides.DOUBLE).renderTriangles().size());
    }

    @Test
    void supportingSheetHasDeterministicNormalThicknessAndNoCellSizedOffset() {
        double y = .4375;
        var t = new MeshVoxelizer.Triangle(new Vec3(.1, y, .1), new Vec3(.9, y, .1), new Vec3(.1, y, .9));
        for (int resolution : List.of(8, 16)) {
            var r = MeshVoxelizer.voxelizeSurfaces(List.of(new MeshVoxelizer.Surface(t, MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET)), 1, 1, 1, resolution);
            assertFalse(r.boxes().isEmpty());
            assertEquals(y + .5 / resolution, r.boxes().stream().mapToDouble(b -> b.maxY).max().orElseThrow(), 1e-9);
            assertEquals(y - .5 / resolution, r.boxes().stream().mapToDouble(b -> b.minY).min().orElseThrow(), 1e-9);
            assertTrue(r.boxes().stream().anyMatch(b -> b.contains(new Vec3(.25, y, .25))));
        }
        var shell = MeshVoxelizer.voxelizeSurfaces(List.of(new MeshVoxelizer.Surface(t, MeshVoxelizer.SurfacePolicy.SHELL)), 1, 1, 1, 8);
        assertEquals(y + .125 / 8, shell.boxes().stream().mapToDouble(b -> b.maxY).max().orElseThrow(), 1e-9);
    }

    @Test
    void slopedSheetOccupancyContainsSurfaceSamplesWithoutFillingTheBay() {
        var t = new MeshVoxelizer.Triangle(new Vec3(.1, .2, .1), new Vec3(.9, .7, .1), new Vec3(.1, .2, .9));
        var r = MeshVoxelizer.voxelizeSurfaces(List.of(new MeshVoxelizer.Surface(t, MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET)), 1, 1, 1, 8);
        for (int i = 1; i < 8; i++)
            for (int j = 1; j < 8 - i; j++) {
                var p = t.a().add(t.b().subtract(t.a()).scale(i / 8.0)).add(t.c().subtract(t.a()).scale(j / 8.0));
                assertTrue(r.boxes().stream().anyMatch(b -> b.inflate(1e-8).contains(p)), "Uncovered sheet sample " + p);
            }
        assertFalse(r.boxes().stream().anyMatch(b -> b.contains(new Vec3(.5, .05, .5))));
    }

    @Test
    void directionalShadingMatchesSkyentAndInterpolatesSlopes() {
        assertEquals(1, MeshDirectionalShade.factor(new Vec3(0, 1, 0)), 1e-6);
        assertEquals(.55, MeshDirectionalShade.factor(new Vec3(0, -1, 0)), 1e-6);
        for (int sign : List.of(-1, 1)) {
            assertEquals(.70, MeshDirectionalShade.factor(new Vec3(sign, 0, 0)), 1e-6);
            assertEquals(.82, MeshDirectionalShade.factor(new Vec3(0, 0, sign)), 1e-6);
            assertEquals(.85, MeshDirectionalShade.factor(new Vec3(sign, 1, 0)), 1e-6);
            assertEquals(.91, MeshDirectionalShade.factor(new Vec3(0, 1, sign)), 1e-6);
        }
        assertEquals(.625, MeshDirectionalShade.factor(new Vec3(1, -1, 0)), 1e-6);
    }

    @Test
    void turbineOnlyDuplicatesPlanarBladesAndRetainsExplicitMaterialPolicy() throws Exception {
        var def = MeshMachineDefinition.LARGE_STEAM_TURBINE;
        try (var input = getClass().getResourceAsStream("/assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel")) {
            assertNotNull(input);
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var compiled = def.compileStatic(def.parse(new java.io.StringReader(json.toString())));
            assertEquals(64, compiled.triangles().stream().filter(MeshSceneCompiler.Triangle::doubleSided).count());
            assertTrue(compiled.triangles().stream().filter(t -> def.collisionParts().contains(t.part())).noneMatch(MeshSceneCompiler.Triangle::doubleSided));
            json.getAsJsonArray("textures").get(4).getAsJsonObject().addProperty("render_sides", "front");
            var front = def.compileStatic(def.parse(new java.io.StringReader(json.toString())));
            assertEquals(0, front.triangles().stream().filter(MeshSceneCompiler.Triangle::doubleSided).count());
            assertEquals(compiled.triangles().size(), front.renderTriangles().size());
        }
    }
}
