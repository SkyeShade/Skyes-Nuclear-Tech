package com.skyeshade.skyent.content.shape;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.model.MeshMachineDefinition;
import com.skyeshade.skyent.content.model.blockbench.BlockbenchParser;
import com.skyeshade.skyent.content.model.blockbench.MeshSceneCompiler;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Canonical classpath-only gameplay cache, built during common setup. Visual resource packs cannot replace it.
 */
public final class MeshMachineShapeCache {
    private static volatile Data turbine;

    public record Data(MeshCellShapes collision, MeshCellShapes outline, List<AABB> collisionBoxes,
                       AABB modelBounds, String audit, MeshStaircase.Result collisionVoxels,
                       MeshHybridShapeCompiler.Result collisionHybrid, MeshHybridShapeCompiler.Result outlineHybrid,
                       Map<String, AABB> excludedGeometry, String metadataAudit) {
    }

    private MeshMachineShapeCache() {
    }

    public static synchronized void initialize() {
        if (turbine != null) return;
        MeshMachineDefinition def = MeshMachineDefinition.LARGE_STEAM_TURBINE;
        long start = System.nanoTime();
        try (InputStream stream = MeshMachineShapeCache.class.getResourceAsStream("/assets/" + def.source().getNamespace() + "/" + def.source().getPath())) {
            if (stream == null) throw new FileNotFoundException(def.source().toString());
            byte[] bytes = stream.readAllBytes();
            var scene = def.parse(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            var compiled = def.compileStatic(scene);
            for (String uuid : compiled.authoredCollision() ? Set.<String>of() : def.collisionParts())
                if (!compiled.pivots().containsKey(uuid))
                    throw new IllegalArgumentException("Missing collision UUID " + uuid);
            for (String uuid : compiled.authoredCollision() ? Set.<String>of() : def.supportingSheets())
                if (!compiled.pivots().containsKey(uuid))
                    throw new IllegalArgumentException("Missing supporting sheet " + uuid);
            AABB bounds = null;
            for (var t : compiled.triangles()) {
                var triangle = new MeshVoxelizer.Triangle(def.transform().footprint(t.a().position()), def.transform().footprint(t.b().position()), def.transform().footprint(t.c().position()));
                AABB box = new AABB(triangle.a(), triangle.b()).minmax(new AABB(triangle.c(), triangle.c()));
                bounds = bounds == null ? box : bounds.minmax(box);
            }
            if (bounds == null || bounds.minX < -1e-5 || bounds.minY < -1e-5 || bounds.minZ < -1e-5
                    || bounds.maxX > 3.00001 || bounds.maxY > 3.00001 || bounds.maxZ > 5.00001)
                throw new IllegalArgumentException("Static geometry outside reserved footprint: " + bounds);
            var c = MeshHybridShapeCompiler.compile(compiled, def.transform(), 3, 3, 5, 16,
                    (primitive, polygon) -> def.collisionPolicy(compiled,primitive,polygon));
            var o = MeshHybridShapeCompiler.compile(compiled, def.transform(), 3, 3, 5, 16,
                    // Eligibility belongs to the outline policy, allowing future independent overrides.
                    (primitive, polygon) -> def.outlinePolicy(compiled, primitive, polygon), false);
            String metadataAudit = scene.collisionHierarchy().audit(scene)
                    + " compiledCollisionGeometry=" + compiled.primitives().stream().filter(p -> p.polygons().stream().anyMatch(f -> def.collisionPolicy(compiled,p,f) != null)).count()
                    + " outlinePolicy=" + def.outlinePolicyName(compiled) + " displayAuthored=" + scene.display().present();
            Map<String,AABB> excluded = new LinkedHashMap<>();
            for (var primitive : compiled.primitives()) if (!primitive.collision().effective()) {
                AABB box = null;
                for (var polygon : primitive.polygons()) for (var vertex : polygon.vertices()) {
                    var point = def.transform().footprint(vertex);
                    box = box == null ? new AABB(point,point) : box.minmax(new AABB(point,point));
                }
                if (box != null) excluded.put(primitive.path() + " [" + primitive.part() + "] inherited=" + primitive.collision().excludedAncestors(),box);
            }
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String audit = "source=" + hash + " compiler=" + MeshSceneCompiler.VERSION + " transform=" + def.multiblock()
                    + " " + metadataAudit + " legacyListsActive=" + !compiled.authoredCollision()
                    + " triangles=" + compiled.triangles().size() + " parts=" + compiled.pivots().size()
                    + " bounds=" + bounds + " shapes=pixel-staircase-1 collision@16=" + c.counts()
                    + " outline@16=" + o.counts() + " diagnostics=" + compiled.diagnostics();
            turbine = new Data(split(c.boxes()), split(o.boxes()), c.boxes(), bounds, audit, c.fallback(), c, o, Map.copyOf(excluded),metadataAudit);
            SkyesNuclearTech.LOGGER.info("Large turbine model audit ({} ms): {}", (System.nanoTime() - start) / 1_000_000, audit);
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int z = 0; z < 5; z++) {
                AABB cell = new AABB(x, y, z, x + 1, y + 1, z + 1);
                SkyesNuclearTech.LOGGER.info("Large turbine cell [{},{},{}] collision: {} outline: {}", x, y, z,
                        c.cellCounts(cell, turbine.collision().boxesForLocal(x, y, z, Direction.NORTH).size()),
                        o.cellCounts(cell, turbine.outline().boxesForLocal(x, y, z, Direction.NORTH).size()));
            }
        } catch (Exception exception) {
            // Deterministic chassis proxy; never retry parsing/voxelization from a shape query.
            List<AABB> boxes = List.of(new AABB(.25, 0, 0, 2.75, .25, 5), new AABB(.25, .25, 0, 2.75, 2.25, 1.75), new AABB(.25, .25, 4.5, 2.75, 2.25, 5));
            turbine = new Data(split(boxes), split(boxes), boxes, new AABB(0, 0, 0, 3, 3, 5), "FALLBACK: " + exception, null, null, null,Map.of(),"unavailable");
            SkyesNuclearTech.LOGGER.error("Large turbine canonical shape compilation failed; using chassis proxy", exception);
        }
    }

    public static Data data() {
        if (turbine == null) throw new IllegalStateException("Mesh shapes must be initialized in common setup");
        return turbine;
    }

    public static VoxelShape shape(int x, int y, int z, Direction facing, boolean collision) {
        Data data = turbine;
        // Registration can query shapes before common setup; dynamicShape prevents engine caching of this placeholder.
        if (data == null) return Shapes.empty();
        return (collision ? data.collision() : data.outline()).shapeForLocal(x, y, z, facing);
    }

    public static MeshCellShapes split(List<AABB> boxes) {
        return new MeshCellShapes(boxes, MeshMachineDefinition.LARGE_STEAM_TURBINE.transform());
    }
}
