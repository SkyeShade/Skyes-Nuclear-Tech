package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.multiblock.ModelMultiblockDefinition;
import com.skyeshade.skyent.content.multiblock.ModelMultiblockOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The sole authored -> footprint -> controller-relative contract for vertices, pivots, bounds and ports.
 */
public record MachineModelTransform(ModelMultiblockDefinition multiblock) {
    public MachineModelTransform {
        if (multiblock.orientation() != ModelMultiblockOrientation.CARDINAL_ROTATION || multiblock.modelScale() <= 0)
            throw new IllegalArgumentException("Mesh machines require positive scale and cardinal rotation");
    }

    public Vec3 footprint(Vec3 authored) {
        Vec3 origin = multiblock.modelOrigin();
        return origin.add(authored.subtract(origin).scale(multiblock.modelScale())).add(multiblock.modelTranslation()).scale(1.0 / 16);
    }

    public Vec3 relative(Vec3 footprint, Direction facing) {
        Vec3 c = Vec3.atLowerCornerOf(multiblock.controllerLocal());
        return direction(footprint.subtract(c).subtract(.5, 0, .5), facing).add(.5, 0, .5);
    }

    public Vec3 authoredRelative(Vec3 authored, Direction facing) {
        return relative(footprint(authored), facing);
    }

    public static Vec3 direction(Vec3 v, Direction facing) {
        return switch (facing) {
            case NORTH -> v;
            case EAST -> new Vec3(-v.z, v.y, v.x);
            case SOUTH -> new Vec3(-v.x, v.y, -v.z);
            case WEST -> new Vec3(v.z, v.y, -v.x);
            default -> throw new IllegalArgumentException("Horizontal facing required");
        };
    }

    public AABB relativeBounds(AABB box, Direction facing) {
        Vec3 a = relative(new Vec3(box.minX, box.minY, box.minZ), facing);
        Vec3 b = relative(new Vec3(box.maxX, box.maxY, box.maxZ), facing);
        return new AABB(a, b);
    }
}
