package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.shape.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.world.phys.AABB;
import com.skyeshade.skyent.content.model.blockbench.MeshSceneCompiler;
import static org.junit.jupiter.api.Assertions.*;

class MeshShapeBudgetTest {
    @Test void turbineCounts() throws Exception {
        var def = MeshMachineDefinition.LARGE_STEAM_TURBINE;
        try (var in = getClass().getResourceAsStream("/assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel")) {
            assertNotNull(in);
            var legacy = com.google.gson.JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            MeshMetadataTest.removeCollisionMetadata(legacy);
            var compiled = def.compileStatic(def.parse(new StringReader(legacy.toString())));
            for (var primitive : compiled.primitives()) {
                if (!def.supportingSheets().contains(primitive.part()) || primitive.polygons().stream().allMatch(p -> p.triangles().stream().allMatch(t -> Math.abs(t.normal().y) > .999))) continue;
                var one = new MeshSceneCompiler.Compiled(List.of(), Map.of(), List.of(), List.of(primitive));
                var r = MeshHybridShapeCompiler.compile(one, def.transform(), 3, 3, 5, 16, (p, f) -> MeshVoxelizer.SurfacePolicy.SHELL);
                if (r.fallback().boxes().isEmpty()) continue;
                var oldSurfaces = primitive.polygons().stream().flatMap(p -> p.triangles().stream()).map(t ->
                        new MeshVoxelizer.Surface(new MeshVoxelizer.Triangle(def.transform().footprint(t.a().position()),
                                def.transform().footprint(t.b().position()),def.transform().footprint(t.c().position())),MeshVoxelizer.SurfacePolicy.SHELL)).toList();
                System.out.println("CANOPY " + primitive.part() + " oldOutline=" + MeshVoxelizer.voxelizeSurfaces(oldSurfaces,3,3,5,16).boxes().size() + " newOutline=" + r.boxes().size());
                assertTrue(r.boxes().size() <= 16, "Each planar glass slope must remain a modest staircase");
            }
            for (boolean collision : new boolean[]{true, false}) {
                var result = MeshHybridShapeCompiler.compile(compiled, def.transform(), 3, 3, 5, collision ? 8 : 16,
                        (p, f) -> !collision || def.collisionParts().contains(p.part()) ? MeshVoxelizer.SurfacePolicy.SHELL
                                : def.supportingSheets().contains(p.part()) && (f.face().equals("up") || f.face().equals("down"))
                                ? MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET : null);
                System.out.println("SHAPE_BUDGET " + (collision ? "collision " : "outline ") + result.counts());
                assertTrue(result.boxes().size() < (collision ? 220 : 2200), "Shape complexity regression");
                assertEquals(collision ? 3 : 32, result.cuboids().size());
                assertEquals(collision ? 56 : 172, result.sheets().size());
                assertTrue(result.fallback().unmerged().size() >= result.fallback().boxes().size());
                // Keep the machine/cell diagnostic contract exercised without starting Minecraft.
                String counts = result.cellCounts(new AABB(0,1,2,1,2,3),0);
                assertTrue(counts.contains("beforeMerge=") && counts.contains("afterMerge=") && counts.contains("stepped="));
            }
        }
    }
}
