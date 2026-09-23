package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.model.blockbench.BlockbenchScene;
import com.skyeshade.skyent.content.model.blockbench.MeshSceneCompiler;
import com.skyeshade.skyent.content.multiblock.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Set;

/**
 * Opt-in sidecar. UUID identities survive renaming; variants never infer their own origin from bounds.
 */
public record MeshMachineDefinition(ModelMultiblockDefinition multiblock, ResourceLocation source,
                                    Map<String, ResourceLocation> materials, Set<String> movingRoots,
                                    Set<String> collisionParts, Set<String> supportingSheets, Vec3 shaftPivot,
                                    Vec3 shaftAxis) {
    public static final MeshMachineDefinition LARGE_STEAM_TURBINE = new MeshMachineDefinition(
            new ModelMultiblockDefinition(id("large_steam_turbine"), 3, 3, 5, new BlockPos(1, 0, 0),
                    2, Vec3.ZERO, new Vec3(32, 0, 48), ModelMultiblockCollisionMode.GENERATED_MODEL_SHAPES,
                    ModelMultiblockRenderMode.BER_FULL_MODEL, ModelMultiblockOrientation.CARDINAL_ROTATION),
            id("models/block/mv_steam_turbine_mesh.bbmodel"),
            Map.of("1669b220-ca5f-c415-f901-eda5f88237da", id("machine/large_steam_turbine_main"),
                    "09a8bd5e-efd4-4c42-6ead-a06d284111ef", id("block/i_o"),
                    "0aad749f-7bb3-a15e-33fb-dc743e3e8215", id("machine/large_steam_details"),
                    "47e192a8-6035-b623-2f9e-5229ccb16c16", id("machine/large_steam_canopy"),
                    "7383c370-e825-71e1-282c-75af3920ef5f", id("machine/large_steam_rotor")),
            Set.of("1db2ced4-6c2c-0030-c870-8e3d5ae388e5"),
            // Chassis shell, two stationary bearing supports, and three solid top covers. Never fill the open rotor bay.
            Set.of("4caa3237-734e-b197-4dd9-73818e56c5d2", "319e3270-5123-2832-9210-9a90463758bf", "53d2f7b6-440d-19dd-fe9c-b0d40e0fe4d7",
                    // Solid top covers; decorative ribs, glass trim and internals remain excluded.
                    "a1d7d4c2-7aae-f8ab-1835-867a06efd807", "ff254c79-f413-702b-18f7-dfefa641d0ca", "00793013-627b-74d5-663c-3cc7a5373d1f"),
            // Explicit canopy glass panels; frames, trim, stators and moving blades are not supporting sheets.
            Set.of("df0c753c-46f2-a345-e2fb-fb4782f65dd4", "6a9bce78-8c30-e384-7de6-b1c9c91bdfa3", "f0d05ff7-eef2-114b-270c-76e34b416943"),
            new Vec3(-4, 9, 1.5), new Vec3(0, 0, 1));

    public MeshMachineDefinition {
        materials = Map.copyOf(materials);
        movingRoots = Set.copyOf(movingRoots);
        collisionParts = Set.copyOf(collisionParts);
        supportingSheets = Set.copyOf(supportingSheets);
    }

    public MachineModelTransform transform() {
        return new MachineModelTransform(multiblock);
    }

    /** Compatibility-only: authored collision metadata replaces this allowlist. */
    @Deprecated(forRemoval = false)
    public Set<String> collisionParts() { return collisionParts; }

    /** Compatibility-only: authored sheets use geometric classification rather than UUIDs. */
    @Deprecated(forRemoval = false)
    public Set<String> supportingSheets() { return supportingSheets; }

    /** Legacy static-subtree suppression. Authored models keep all visible geometry; no animation is inferred. */
    @Deprecated(forRemoval = false)
    public Set<String> movingRoots() { return movingRoots; }

    public BlockbenchScene parse(java.io.Reader reader) {
        var json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        // Two current I/O undersides have stale UVs and no material. Repair only those known omissions,
        // using the matching upper cap. Keep the user's editor asset untouched; explicit authored fixes win.
        for (var entry : json.getAsJsonArray("elements")) {
            var element = entry.getAsJsonObject();
            if (!Set.of("65a6fc3b-b7d9-1f1e-b49a-59df51e4d991", "64724ece-31e2-d3df-3334-0aef2f105c17").contains(element.get("uuid").getAsString()))
                continue;
            var faces = element.getAsJsonObject("faces");
            if (!faces.getAsJsonObject("down").has("texture"))
                faces.add("down", faces.getAsJsonObject("up").deepCopy());
        }
        return com.skyeshade.skyent.content.model.blockbench.BlockbenchParser.parse(new java.io.StringReader(json.toString()));
    }

    public MeshSceneCompiler.Compiled compileStatic(BlockbenchScene scene) {
        boolean authored = scene.collisionHierarchy().authored();
        for (String uuid : authored ? Set.<String>of() : movingRoots)
            if (!scene.nodes().containsKey(uuid))
                throw new IllegalArgumentException("Missing moving-part UUID " + uuid);
        for (BlockbenchScene.Texture t : scene.textures())
            if (!materials.containsKey(t.uuid()))
                throw new IllegalArgumentException("Unmapped material " + t.name() + " / " + t.uuid());
        // Legacy moving-root suppression predates authored metadata. Collision flags never hide visuals.
        return MeshSceneCompiler.compile(scene, node -> authored || !movingRoots.contains(node.uuid()));
    }

    /** UUID allowlists are compatibility-only for scenes without any authored collision property. */
    public com.skyeshade.skyent.content.shape.MeshVoxelizer.SurfacePolicy collisionPolicy(
            MeshSceneCompiler.Compiled compiled, MeshSceneCompiler.Primitive primitive, MeshSceneCompiler.Polygon polygon) {
        if (!primitive.collision().effective()) return null;
        if (compiled.authoredCollision() || collisionParts.contains(primitive.part()))
            return com.skyeshade.skyent.content.shape.MeshVoxelizer.SurfacePolicy.SHELL;
        if (supportingSheets.contains(primitive.part()) && (polygon.face().equals("up") || polygon.face().equals("down")))
            return com.skyeshade.skyent.content.shape.MeshVoxelizer.SurfacePolicy.SUPPORTING_SHEET;
        return null;
    }

    /**
     * Separate selection-policy boundary for future outline metadata. Until such metadata exists,
     * authored outlines follow effective collision eligibility; legacy outlines include all visuals.
     */
    public com.skyeshade.skyent.content.shape.MeshVoxelizer.SurfacePolicy outlinePolicy(
            MeshSceneCompiler.Compiled compiled, MeshSceneCompiler.Primitive primitive, MeshSceneCompiler.Polygon polygon) {
        if (compiled.authoredCollision() && !primitive.collision().effective()) return null;
        return com.skyeshade.skyent.content.shape.MeshVoxelizer.SurfacePolicy.SHELL;
    }

    public String outlinePolicyName(MeshSceneCompiler.Compiled compiled) {
        return compiled.authoredCollision() ? "authored-collision" : "all-visible";
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("skyent", path);
    }
}
