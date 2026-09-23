package com.skyeshade.skyent.content.model;

import net.minecraft.world.phys.Vec3;

/**
 * Skyent ScaledBlockModel/Centrifuge face factors, continuously interpolated by geometric normal.
 */
public final class MeshDirectionalShade {
    private MeshDirectionalShade() {
    }

    public static float factor(Vec3 normal) {
        double length = normal.lengthSqr();
        if (length < 1e-20) return 1;
        // Squared direction cosines form a partition of unity and preserve every cardinal endpoint.
        return (float) ((.70 * normal.x * normal.x + .82 * normal.z * normal.z
                + (normal.y < 0 ? .55 : 1) * normal.y * normal.y) / length);
    }
}
