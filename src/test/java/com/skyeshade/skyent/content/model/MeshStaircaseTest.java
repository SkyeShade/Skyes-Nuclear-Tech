package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.shape.*;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MeshStaircaseTest {
    private static final AABB FOOTPRINT = new AABB(0, 0, 0, 3, 3, 5);
    private static List<Vec3> slope(double angle, double end) {
        double rise = Math.tan(Math.toRadians(angle)) * end;
        return List.of(new Vec3(0, 1, 0), new Vec3(0, 1, 2), new Vec3(end, 1 + rise, 2), new Vec3(end, 1 + rise, 0));
    }

    @Test void pixelStepsMergeToOneRunAcrossWidthWithoutFloating() {
        for (double angle : new double[]{22.5, 45}) {
            var raw = MeshStaircase.surface(slope(angle, 1), false, FOOTPRINT);
            var boxes = MeshStaircase.finish(raw).boxes();
            assertEquals(16, raw.size(), "Invariant width must never be voxelized");
            assertEquals(16, boxes.size(), "One rectangular run per pixel step");
            double gradient = Math.tan(Math.toRadians(angle));
            for (AABB b : boxes) {
                assertEquals(1.0 / 16, b.getXsize(), 1e-10, "No subpixel subdivisions along slope");
                assertEquals(2, b.getZsize(), 1e-10);
                assertEquals(1 + b.minX * gradient, b.maxY, 1e-10, "Top is inside the rendered plane");
                assertTrue(1 + b.maxX * gradient - b.maxY <= 1.0 / 16 + 1e-10);
            }
        }
    }

    @Test void finalPartialStepRetainsAuthoredBoundary() {
        var boxes = MeshStaircase.finish(MeshStaircase.surface(slope(22.5, .91), false, FOOTPRINT)).boxes();
        assertEquals(15, boxes.size());
        var last = boxes.stream().max(Comparator.comparingDouble(b -> b.maxX)).orElseThrow();
        assertEquals(.91, last.maxX, 1e-12);
        assertEquals(.91 - 14.0 / 16, last.getXsize(), 1e-12);
        assertEquals(14, boxes.stream().filter(b -> Math.abs(b.getXsize() - 1.0 / 16) < 1e-10).count());
    }

    @Test void verySmallSlopedSheetSurvives() {
        var boxes = MeshStaircase.finish(MeshStaircase.surface(slope(45, .01), false, FOOTPRINT)).boxes();
        assertEquals(1, boxes.size());
        assertEquals(.01, boxes.getFirst().getXsize(), 1e-12);
    }

    @Test void footprintClippingDoesNotReflectDisjointBoundsBackIntoTheMachine() {
        assertTrue(MeshStaircase.surface(slope(22.5, 1).stream().map(p -> p.add(0, 5, 0)).toList(), false, FOOTPRINT).isEmpty());
    }

    @Test void neighboringCellsMergeWithoutChangingTheStaircase() {
        var strips = MeshStaircase.surface(slope(22.5, 1), false, FOOTPRINT);
        List<AABB> cells = new ArrayList<>();
        for (AABB b : strips) for (int i = 0; i < 32; i++)
            cells.add(new AABB(b.minX, b.minY, i / 16.0, b.maxX, b.maxY, (i + 1) / 16.0));
        var merged = MeshStaircase.merge(cells);
        assertEquals(16, merged.size());
        assertEquals(new HashSet<>(strips), new HashSet<>(merged));
    }

    @Test void slopesKeepTheirRelationshipForEveryFacingAndCellBoundary() {
        var transform = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        var boxes = MeshStaircase.surface(slope(22.5, 1.13), false, FOOTPRINT);
        var cells = new MeshCellShapes(boxes, transform);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 5; z++) {
                var offset = ModelMultiblocks.rotateLocalOffset(transform.multiblock(), new BlockPos(x,y,z), facing);
                var cell = new AABB(x,y,z,x+1,y+1,z+1);
                for (AABB box : boxes) if (box.intersects(cell)) {
                    var expected = transform.relativeBounds(box.intersect(cell), facing).move(-offset.getX(),-offset.getY(),-offset.getZ());
                    assertTrue(cells.boxesForLocal(x,y,z,facing).stream().anyMatch(b -> b.inflate(1e-10).contains(expected.getCenter())));
                }
            }
        }
    }

    @Test void exactCellSplitsAndAllFacingsPreserveOuterBoundsAndVolume() {
        var transform = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        AABB source = new AABB(.137, .219, .347, 2.831, 2.3125, 4.613);
        var cells = new MeshCellShapes(List.of(source), transform);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            AABB union = null;
            double volume = 0;
            for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 5; z++) {
                var offset = ModelMultiblocks.rotateLocalOffset(transform.multiblock(), new BlockPos(x, y, z), facing);
                for (AABB b : cells.boxesForLocal(x, y, z, facing)) {
                    var world = b.move(offset);
                    union = union == null ? world : union.minmax(world);
                    volume += b.getXsize() * b.getYsize() * b.getZsize();
                }
            }
            assertEquals(transform.relativeBounds(source, facing), union);
            assertEquals(source.getXsize() * source.getYsize() * source.getZsize(), volume, 1e-10);
            assertEquals(2.3125, union.maxY, 1e-12);
        }
    }
}
