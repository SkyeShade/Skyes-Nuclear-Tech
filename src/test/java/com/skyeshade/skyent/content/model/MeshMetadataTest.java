package com.skyeshade.skyent.content.model;

import com.google.gson.*;
import com.skyeshade.skyent.content.model.blockbench.*;
import com.skyeshade.skyent.content.shape.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeshMetadataTest {
    private static final MeshMachineDefinition DEF = MeshMachineDefinition.LARGE_STEAM_TURBINE;
    static JsonObject smallModel() {
        return JsonParser.parseString("""
                {"meta":{"format_version":"5.0","model_format":"free"},
                 "textures":[{"uuid":"t","name":"t","uv_width":16,"uv_height":16}],
                 "groups":[{"uuid":"g"},{"uuid":"nested"}],
                 "elements":[{"uuid":"cube","type":"cube","from":[0,0,0],"to":[2,2,2],
                 "faces":{"north":{"texture":0,"uv":[0,0,2,2]}}},
                 {"uuid":"mesh","type":"mesh","vertices":{"a":[0,0,3],"b":[2,0,3],"c":[2,2,3]},
                 "faces":{"f":{"texture":0,"vertices":["a","b","c"],"uv":{"a":[0,0],"b":[2,0],"c":[2,2]}}}}],
                 "outliner":[{"uuid":"g","children":[{"uuid":"nested","children":["cube","mesh"]}]}]}
                """).getAsJsonObject();
    }
    static BlockbenchScene parse(JsonObject json) { return BlockbenchParser.parse(new StringReader(json.toString())); }
    private static JsonObject node(JsonObject j,String id) {
        for (String key : List.of("groups","elements")) for (var e : j.getAsJsonArray(key))
            if (e.getAsJsonObject().get("uuid").getAsString().equals(id)) return e.getAsJsonObject();
        throw new IllegalArgumentException(id);
    }
    private static MeshHybridShapeCompiler.Result shapes(MeshSceneCompiler.Compiled c, boolean respect) {
        return MeshHybridShapeCompiler.compile(c,DEF.transform(),3,3,5,16,(p,f)->MeshVoxelizer.SurfacePolicy.SHELL,respect);
    }
    @Test void missingMetadataDefaultsToCollidableAndKeepsParents() {
        var scene = parse(smallModel()); var h = scene.collisionHierarchy();
        assertFalse(h.authored());
        assertTrue(h.states().values().stream().allMatch(CollisionHierarchy.State::effective));
        assertEquals("nested",h.states().get("cube").parent());
        assertNull(scene.nodes().get("cube").metadata().collidable());
    }
    @Test void cubeAndMeshExplicitFalseAreIndependentOfRendering() {
        for (String id : List.of("cube","mesh")) {
            var json=smallModel(); node(json,id).addProperty("skyent_collidable",false);
            var c=MeshSceneCompiler.compile(parse(json),n->true);
            assertEquals(2,c.primitives().size()); assertEquals(3,c.triangles().size());
            assertFalse(c.primitives().stream().filter(p->p.part().equals(id)).findFirst().orElseThrow().collision().effective());
            var included=new MeshSceneCompiler.Compiled(List.of(),Map.of(),List.of(),c.primitives().stream().filter(p->!p.part().equals(id)).toList());
            assertEquals(shapes(included,true).boxes(),shapes(c,true).boxes());
            assertTrue(shapes(c,false).boxes().size()>shapes(c,true).boxes().size());
        }
    }
    @Test void excludedGroupPropagatesThroughNestedGroupsAndCannotBeOverridden() {
        for (Boolean explicit : Arrays.asList(null,true,false)) {
            var json=smallModel(); node(json,"g").addProperty("skyent_collidable",false);
            node(json,"nested").addProperty("skyent_collidable",true);
            if(explicit!=null)node(json,"cube").addProperty("skyent_collidable",explicit);
            var scene=parse(json); var h=scene.collisionHierarchy();
            assertFalse(h.states().get("cube").effective()); assertFalse(h.states().get("mesh").effective());
            assertEquals(List.of("g"),h.states().get("cube").excludedAncestors());
            var c=MeshSceneCompiler.compile(scene,n->true);
            assertFalse(c.renderTriangles().isEmpty()); assertTrue(shapes(c,true).boxes().isEmpty());
            assertFalse(shapes(c,false).boxes().isEmpty());
            assertTrue(h.audit(scene).contains("inheritedExcludedGeometry=2"));
        }
    }
    @Test void allExcludedAncestorsRemainAvailableForDebugging() {
        var json=smallModel(); node(json,"g").addProperty("skyent_collidable",false); node(json,"nested").addProperty("skyent_collidable",false);
        assertEquals(List.of("g","nested"),parse(json).collisionHierarchy().states().get("mesh").excludedAncestors());
    }
    @Test void savedParsedMetadataMatchesValuesAndReparseReplacesOldState() {
        var json=smallModel(); node(json,"cube").addProperty("skyent_collidable",false); node(json,"mesh").addProperty("skyent_collidable",true);
        var first=parse(json); var loaded=parse(JsonParser.parseString(json.toString()).getAsJsonObject());
        assertEquals(first,loaded); assertEquals(Boolean.FALSE,loaded.nodes().get("cube").metadata().collidable());
        node(json,"cube").remove("skyent_collidable");
        assertTrue(parse(json).collisionHierarchy().states().get("cube").effective());
        assertFalse(first.collisionHierarchy().states().get("cube").effective());
    }
    @Test void malformedCollisionMetadataWarnsAndKeepsLegacyMode() {
        var json=smallModel(); node(json,"g").addProperty("skyent_collidable","false");
        var scene=parse(json); assertFalse(scene.collisionHierarchy().authored()); assertFalse(scene.metadataWarnings().isEmpty());
    }
    static JsonObject turbine() throws IOException {
        try(var in=MeshMetadataTest.class.getResourceAsStream("/assets/skyent/models/block/mv_steam_turbine_mesh.bbmodel")) {
            return JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    static void removeCollisionMetadata(JsonObject json) {
        for(String key:List.of("groups","elements")) for(var n:json.getAsJsonArray(key))n.getAsJsonObject().remove("skyent_collidable");
    }
    @Test void legacyTurbineRetainsItsPreviousCollisionAndVisualPolicy() throws Exception {
        var json=turbine(); removeCollisionMetadata(json);
        var scene=DEF.parse(new StringReader(json.toString())); var c=DEF.compileStatic(scene);
        var collision=MeshHybridShapeCompiler.compile(c,DEF.transform(),3,3,5,16,(p,f)->DEF.collisionPolicy(c,p,f));
        assertFalse(c.authoredCollision()); assertEquals(191,collision.boxes().size());
        assertEquals(1048,c.triangles().size());
    }
    @Test void authoredTurbineUsesHierarchyInsteadOfLegacyUuidAllowlists() throws Exception {
        var json=turbine(); var scene=DEF.parse(new StringReader(json.toString())); var c=DEF.compileStatic(scene);
        assertTrue(c.authoredCollision());
        var collision=MeshHybridShapeCompiler.compile(c,DEF.transform(),3,3,5,16,(p,f)->DEF.collisionPolicy(c,p,f));
        var all=shapes(c,false);
        var excluded=c.primitives().stream().filter(p->!p.collision().effective()).toList();
        assertFalse(excluded.isEmpty());
        assertTrue(excluded.stream().anyMatch(p->p.path().contains("rotor_assembly")));
        assertTrue(excluded.stream().anyMatch(p->p.path().contains("staters")));
        assertTrue(c.triangles().stream().anyMatch(t->t.path().contains("/rotor/")));
        var isolated=new MeshSceneCompiler.Compiled(List.of(),Map.of(),List.of(),excluded,true);
        assertTrue(shapes(isolated,true).boxes().isEmpty()); assertFalse(shapes(isolated,false).boxes().isEmpty());
        assertTrue(collision.sheets().stream().anyMatch(b->b.part().equals("4caa3237-734e-b197-4dd9-73818e56c5d2")));
        assertTrue(collision.boxes().size()<all.boxes().size());
        System.out.println("AUTHORED_TURBINE " + scene.collisionHierarchy().audit(scene) + " renderedTriangles=" + c.triangles().size()
                + " collision=" + collision.counts() + " allVisibleOutline=" + all.counts());
        // Metadata mode must not require the old turbine UUID lists to exist.
        var noLists=new MeshMachineDefinition(DEF.multiblock(),DEF.source(),DEF.materials(),Set.of(),Set.of(),Set.of(),DEF.shaftPivot(),DEF.shaftAxis());
        assertEquals(collision.boxes(),MeshHybridShapeCompiler.compile(c,DEF.transform(),3,3,5,16,(p,f)->noLists.collisionPolicy(c,p,f)).boxes());
    }
}
