package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.model.blockbench.*;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import com.skyeshade.skyent.content.shape.MeshVoxelizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static com.skyeshade.skyent.content.model.blockbench.BlockbenchScene.*;

class MeshGeometryTest {
    private static final MeshMachineDefinition DEF = MeshMachineDefinition.LARGE_STEAM_TURBINE;

    private static void near(Vec3 expected, Vec3 actual) {
        assertEquals(0, expected.distanceTo(actual), 1e-8, expected + " != " + actual);
    }

    private static BlockbenchScene fixture(String elements, String groups, String outliner) {
        return BlockbenchParser.parse(new StringReader("{\"meta\":{\"format_version\":\"5.0\",\"model_format\":\"free\"},\"textures\":[{\"uuid\":\"t\",\"name\":\"test\",\"width\":256,\"height\":256,\"uv_width\":128,\"uv_height\":64}],\"elements\":[" + elements + "],\"groups\":[" + groups + "],\"outliner\":[" + outliner + "]}"));
    }

    private static final String MESH = """
            {"uuid":"mesh","type":"mesh","origin":[8,4,2],"vertices":{"a":[0,0,0],"b":[4,0,0],"c":[0,4,0]},"faces":{"f":{"vertices":["a","b","c"],"texture":0,"uv":{"a":[0,0],"b":[64,0],"c":[0,32]}}}}
            """;

    @Test
    void mixedOriginsAndAuthoredUvDimensions() {
        var scene = fixture(MESH + "," + """
                {"uuid":"cube","type":"cube","origin":[8,4,2],"from":[8,4,2],"to":[12,8,6],"faces":{"north":{"texture":0,"uv":[0,0,64,32]}}}
                """, "", "\"mesh\",\"cube\"");
        var triangles = MeshSceneCompiler.compile(scene, n -> true).triangles();
        near(new Vec3(8, 4, 2), triangles.getFirst().a().position());
        assertEquals(.5, triangles.getFirst().b().uv().u());
        assertEquals(.5, triangles.getFirst().c().uv().v());
        assertTrue(triangles.subList(1, 3).stream().flatMap(t -> t.vertices().stream()).allMatch(v -> v.position().x >= 8 && v.position().x <= 12 && v.position().z == 2));
        near(new Vec3(0, 0, 1), triangles.getFirst().normal());
        near(new Vec3(0, 0, -1), triangles.get(1).normal());
    }

    @Test
    void nestedAbsolutePivotsAndCompoundRotations() {
        var scene = fixture(MESH, """
                {"uuid":"parent","origin":[8,4,2],"rotation":[0,0,90]},
                {"uuid":"child","origin":[10,4,2],"rotation":[0,90,0]}
                """, "{\"uuid\":\"parent\",\"children\":[{\"uuid\":\"child\",\"children\":[\"mesh\"]}]}");
        var compiled = MeshSceneCompiler.compile(scene, n -> true);
        near(new Vec3(8, 6, 4), compiled.triangles().getFirst().a().position());
        near(new Vec3(8, 6, 2), compiled.pivots().get("child"));
        near(new Vec3(0, 0, 1), MeshSceneCompiler.rotate(new Vec3(0, 1, 0), new Vec3(90, 0, 90)));
        near(new Vec3(0, 0, -1), MeshSceneCompiler.rotate(new Vec3(1, 0, 0), new Vec3(90, 90, 0)));
        near(new Vec3(0, 1, 0), MeshSceneCompiler.rotateMesh(new Vec3(1, 0, 0), new Vec3(90, 90, 0)));
    }

    @Test
    void allFacingsUseCellCentersAndPreserveHandedness() {
        var transform = DEF.transform();
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            for (int x = 0; x < DEF.multiblock().sizeX(); x++)
                for (int y = 0; y < 3; y++)
                    for (int z = 0; z < 5; z++) {
                        BlockPos pos = ModelMultiblocks.localToWorld(DEF.multiblock(), BlockPos.ZERO, facing, x, y, z);
                        near(Vec3.atCenterOf(pos), transform.relative(new Vec3(x + .5, y + .5, z + .5), facing));
                    }
            Vec3 a = MachineModelTransform.direction(new Vec3(1, 0, 0), facing), b = MachineModelTransform.direction(new Vec3(0, 1, 0), facing);
            near(MachineModelTransform.direction(new Vec3(0, 0, 1), facing), a.cross(b));
        }
        near(new Vec3(0, 0, 0), transform.footprint(new Vec3(-16, 0, -24)));
        near(new Vec3(4, 3, 5), transform.footprint(new Vec3(16, 24, 16)));
        near(new Vec3(1.5, 1.125, 3.1875), transform.footprint(DEF.shaftPivot()));
    }

    @Test
    void actualTurbineSelectionAndMaterials() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel")) {
            assertNotNull(stream);
            var json = com.google.gson.JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            MeshMetadataTest.removeCollisionMetadata(json);
            var scene = DEF.parse(new StringReader(json.toString()));
            var full = MeshSceneCompiler.compile(scene, n -> true);
            var stat = DEF.compileStatic(scene);
            assertEquals(5, scene.textures().size());
            assertTrue(stat.triangles().size() > 400);
            assertTrue(full.triangles().size() > stat.triangles().size());
            assertFalse(stat.pivots().containsKey(DEF.movingRoots().iterator().next()));
            for (String part : DEF.collisionParts()) assertTrue(stat.pivots().containsKey(part));
            assertEquals(Set.of(0, 1, 2, 3, 4), new HashSet<>(stat.triangles().stream().map(MeshSceneCompiler.Triangle::texture).toList()));
            assertTrue(stat.triangles().stream().noneMatch(t -> t.part().equals("2293f5c5-fa5b-3ff5-3f80-a7e682160dc8")));
        }
    }

    @Test
    void thinTriangleCrossesCellsWithoutVerticesInsideThem() {
        var t = new MeshVoxelizer.Triangle(new Vec3(.1, .5, .1), new Vec3(.9, .5, .9), new Vec3(.9, .5, .89));
        var result = MeshVoxelizer.voxelize(List.of(t), 1, 1, 1, 8, MeshVoxelizer.Policy.OPEN_SURFACE);
        assertTrue(result.boxes().stream().anyMatch(b -> b.contains(new Vec3(.5, .5, .5))));
        assertTrue(result.occupiedCells() < 8 * 8 * 8 / 2);
    }

    @Test
    void closedFillDiffersFromOpenShell() {
        List<MeshVoxelizer.Triangle> triangles = new ArrayList<>();
        var scene = fixture("{\"uuid\":\"cube\",\"type\":\"cube\",\"from\":[0.2,0.2,0.2],\"to\":[0.8,0.8,0.8],\"faces\":{" +
                String.join(",", List.of("north", "south", "east", "west", "up", "down").stream().map(f -> "\"" + f + "\":{\"texture\":0,\"uv\":[0,0,1,1]}").toList()) + "}}", "", "\"cube\"");
        MeshSceneCompiler.compile(scene, n -> true).triangles().forEach(t -> triangles.add(new MeshVoxelizer.Triangle(t.a().position(), t.b().position(), t.c().position())));
        var open = MeshVoxelizer.voxelize(triangles, 1, 1, 1, 16, MeshVoxelizer.Policy.OPEN_SURFACE);
        var closed = MeshVoxelizer.voxelize(triangles, 1, 1, 1, 16, MeshVoxelizer.Policy.CLOSED_SOLID);
        assertTrue(closed.occupiedCells() > open.occupiedCells());
        assertFalse(open.boxes().stream().anyMatch(b -> b.contains(new Vec3(.5, .5, .5))));
        assertTrue(closed.boxes().stream().anyMatch(b -> b.contains(new Vec3(.5, .5, .5))));
        triangles.removeIf(t -> t.a().z == .2 && t.b().z == .2 && t.c().z == .2);
        var unsealed = MeshVoxelizer.voxelize(triangles, 1, 1, 1, 16, MeshVoxelizer.Policy.CLOSED_SOLID);
        assertFalse(unsealed.boxes().stream().anyMatch(b -> b.contains(new Vec3(.5, .5, .5))));
    }

    @Test
    void rejectsUnsupportedVersionAndBrokenReferences() {
        assertThrows(RuntimeException.class, () -> BlockbenchParser.parse(new StringReader("{\"meta\":{\"format_version\":\"4.9\",\"model_format\":\"free\"}}")));
        assertThrows(RuntimeException.class, () -> fixture(MESH, "", "\"missing\""));
        assertThrows(RuntimeException.class, () -> fixture(MESH.replace("\"texture\":0", "\"texture\":7"), "", "\"mesh\""));
    }

    @Test
    void cubeUvRotationUsesBlockbenchCornerOrder() {
        var scene = fixture("""
                {"uuid":"cube","type":"cube","from":[0,0,0],"to":[1,1,1],"faces":{"north":{"texture":0,"uv":[0,0,64,32],"rotation":90}}}
                """, "", "\"cube\"");
        var triangles = MeshSceneCompiler.compile(scene, n -> true).triangles();
        var atUpperRight = triangles.stream().flatMap(t -> t.vertices().stream()).filter(v -> v.position().equals(new Vec3(1, 1, 0))).findFirst().orElseThrow();
        assertEquals(0, atUpperRight.uv().u());
        assertEquals(.5, atUpperRight.uv().v());
    }

    @Test
    void crossedQuadIsReorderedWithoutLosingUvSeams() {
        var scene = fixture("""
                {"uuid":"quad","type":"mesh","vertices":{"a":[0,0,0],"b":[1,0,0],"c":[0,1,0],"d":[1,1,0]},"faces":{"f":{"texture":0,"vertices":["a","b","c","d"],"uv":{"a":[0,0],"b":[128,0],"c":[0,64],"d":[128,64]}}}}
                """, "", "\"quad\"");
        var triangles = MeshSceneCompiler.compile(scene, n -> true).triangles();
        assertEquals(2, triangles.size());
        near(triangles.get(0).normal(), triangles.get(1).normal());
        for (var t : triangles)
            for (var v : t.vertices()) {
                assertEquals(v.position().x, v.uv().u());
                assertEquals(v.position().y, v.uv().v());
            }
    }

    @Test
    void everyStaticVertexAndUvMatchesIndependentBlockbenchCapture() throws Exception {
        try (var modelStream = getClass().getResourceAsStream("/assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel");
             var goldenStream = getClass().getResourceAsStream("/mesh/blockbench-reference.json")) {
            assertNotNull(modelStream);
            assertNotNull(goldenStream);
            // This captured reference predates authored metadata and excludes the legacy moving root.
            var json = com.google.gson.JsonParser.parseReader(new InputStreamReader(modelStream, StandardCharsets.UTF_8)).getAsJsonObject();
            MeshMetadataTest.removeCollisionMetadata(json);
            var scene = DEF.parse(new StringReader(json.toString()));
            var compiled = DEF.compileStatic(scene);
            var golden = com.google.gson.JsonParser.parseReader(new InputStreamReader(goldenStream, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, List<Vertex>> reference = new HashMap<>();
            for (var sample : golden.getAsJsonArray("samples")) {
                var o = sample.getAsJsonObject();
                var p = o.getAsJsonArray("position");
                var uv = o.getAsJsonArray("uv");
                reference.computeIfAbsent(o.get("part").getAsString(), key -> new ArrayList<>()).add(new Vertex(new Vec3(p.get(0).getAsDouble(), p.get(1).getAsDouble(), p.get(2).getAsDouble()), new UV(uv.get(0).getAsDouble(), uv.get(1).getAsDouble())));
            }
            for (var triangle : compiled.triangles())
                for (var vertex : triangle.vertices()) {
                    assertTrue(reference.getOrDefault(triangle.part(), List.of()).stream().anyMatch(v ->
                                    v.position().distanceTo(vertex.position()) < 2e-5 && Math.abs(v.uv().u() - vertex.uv().u()) < 1e-6 && Math.abs(v.uv().v() - vertex.uv().v()) < 1e-6),
                            "Blockbench reference mismatch: " + triangle.path() + " / " + triangle.part() + " / " + triangle.face() + " / " + vertex);
                }
        }
    }
}
