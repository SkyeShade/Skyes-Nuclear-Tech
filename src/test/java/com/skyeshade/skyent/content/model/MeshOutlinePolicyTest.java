package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.model.blockbench.MeshSceneCompiler;
import com.skyeshade.skyent.content.shape.*;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MeshOutlinePolicyTest {
    private static final MeshMachineDefinition DEF = MeshMachineDefinition.LARGE_STEAM_TURBINE;

    private static MeshHybridShapeCompiler.Result outline(MeshSceneCompiler.Compiled compiled) {
        return MeshHybridShapeCompiler.compile(compiled, DEF.transform(), 3, 3, 5, 16,
                (p, f) -> DEF.outlinePolicy(compiled, p, f), false);
    }

    @Test void explicitCubeAndMeshExclusionsAlsoExcludeOutlinesWithoutRemovingRenderTriangles() {
        for (int excludedIndex : List.of(0, 1)) {
            var json = MeshMetadataTest.smallModel();
            json.getAsJsonArray("elements").get(excludedIndex).getAsJsonObject().addProperty("skyent_collidable", false);
            var compiled = MeshSceneCompiler.compile(MeshMetadataTest.parse(json), n -> true);
            var eligible = compiled.primitives().stream().filter(p -> p.collision().effective()).toList();
            var expected = new MeshSceneCompiler.Compiled(List.of(), Map.of(), List.of(), eligible, true);
            assertEquals(outline(expected).boxes(), outline(compiled).boxes());
            assertFalse(outline(compiled).boxes().isEmpty());
            assertEquals(3, compiled.triangles().size());
            assertEquals("authored-collision", DEF.outlinePolicyName(compiled));
        }
    }

    @Test void inheritedGroupExclusionCannotBeOverriddenByChildTrueOrMissingProperty() {
        for (Boolean childValue : Arrays.asList(null, true, false)) {
            var json = MeshMetadataTest.smallModel();
            json.getAsJsonArray("groups").get(0).getAsJsonObject().addProperty("skyent_collidable", false);
            json.getAsJsonArray("groups").get(1).getAsJsonObject().addProperty("skyent_collidable", true);
            if (childValue != null)
                json.getAsJsonArray("elements").get(0).getAsJsonObject().addProperty("skyent_collidable", childValue);
            var compiled = MeshSceneCompiler.compile(MeshMetadataTest.parse(json), n -> true);
            assertTrue(outline(compiled).boxes().isEmpty());
            assertEquals(3, compiled.triangles().size());
        }
    }

    @Test void legacyTurbineKeepsAllVisibleOutlinePolicy() throws Exception {
        var json = MeshMetadataTest.turbine();
        MeshMetadataTest.removeCollisionMetadata(json);
        var compiled = DEF.compileStatic(DEF.parse(new StringReader(json.toString())));
        var previous = MeshHybridShapeCompiler.compile(compiled, DEF.transform(), 3, 3, 5, 16,
                (p, f) -> MeshVoxelizer.SurfacePolicy.SHELL, false);
        assertEquals("all-visible", DEF.outlinePolicyName(compiled));
        assertEquals(previous.boxes(), outline(compiled).boxes());
        assertEquals(1884, outline(compiled).boxes().size());
    }

    @Test void authoredTurbineDropsExcludedRotorAndStatorOutlinesAndPreservesCollisionAndRendering() throws Exception {
        var scene = DEF.parse(new StringReader(MeshMetadataTest.turbine().toString()));
        var compiled = DEF.compileStatic(scene);
        var renderBefore = List.copyOf(compiled.triangles());
        var collision = MeshHybridShapeCompiler.compile(compiled, DEF.transform(), 3, 3, 5, 16,
                (p, f) -> DEF.collisionPolicy(compiled, p, f));
        var previous = MeshHybridShapeCompiler.compile(compiled, DEF.transform(), 3, 3, 5, 16,
                (p, f) -> MeshVoxelizer.SurfacePolicy.SHELL, false);
        var selected = outline(compiled);
        assertEquals(3846, previous.boxes().size());
        assertEquals(384, selected.boxes().size());
        assertEquals(collision.boxes(), selected.boxes());
        assertEquals(32, selected.cuboids().size());
        assertEquals(112, selected.sheets().size());
        assertEquals(240, selected.fallback().boxes().size());
        for (String group : List.of("rotor_assembly", "staters")) {
            var excluded = compiled.primitives().stream().filter(p -> p.path().contains(group)).toList();
            assertFalse(excluded.isEmpty());
            assertTrue(excluded.stream().noneMatch(p -> p.collision().effective()));
            assertTrue(outline(new MeshSceneCompiler.Compiled(List.of(), Map.of(), List.of(), excluded, true)).boxes().isEmpty());
            assertTrue(compiled.triangles().stream().anyMatch(t -> t.path().contains(group)));
        }
        assertEquals(renderBefore, compiled.triangles());
        assertEquals(MeshSceneCompiler.compile(scene, n -> true).triangles(), compiled.triangles());
        assertEquals(1176, compiled.triangles().size());
        System.out.println("TURBINE_OUTLINE before=" + previous.counts() + " after=" + selected.counts()
                + " collision=" + collision.counts() + " renderedTriangles=" + compiled.triangles().size());
    }

    @Test void actualCacheUsesAuthoredPolicyAndSelectionMatchesCollisionInAllFourFacings() {
        MeshMachineShapeCache.initialize();
        var data = MeshMachineShapeCache.data();
        assertTrue(data.metadataAudit().contains("outlinePolicy=authored-collision"), data.audit());
        assertEquals(384, data.outlineHybrid().boxes().size());
        assertEquals(data.collisionBoxes(), data.outlineHybrid().boxes());
        for (Direction facing : Direction.Plane.HORIZONTAL)
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 5; z++) {
                String cell = facing + " " + x + "," + y + "," + z;
                assertEquals(data.collision().boxesForLocal(x, y, z, facing),
                        data.outline().boxesForLocal(x, y, z, facing), cell);
                assertEquals(MeshMachineShapeCache.shape(x, y, z, facing, true).toAabbs(),
                        MeshMachineShapeCache.shape(x, y, z, facing, false).toAabbs(), cell);
            }
    }
}
