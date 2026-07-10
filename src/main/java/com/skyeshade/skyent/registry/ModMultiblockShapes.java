package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.shape.MultiblockShapeDefinition;
import com.skyeshade.skyent.content.shape.MultiblockShapeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ModMultiblockShapes {
    public static final ResourceLocation ROLLING_MILL = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "rolling_mill");
    public static final ResourceLocation INDUSTRIAL_PRESS = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "industrial_press");

    private static boolean registered;

    private ModMultiblockShapes() {
    }

    public static void registerDefaults() {
        if (registered) {
            return;
        }
        registered = true;

        MultiblockShapeRegistry.register(new MultiblockShapeDefinition(
                ROLLING_MILL,
                ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "models/block/rolling_mill.json"),
                2.0D,
                Vec3.ZERO,
                new Vec3(16.0D, 0.0D, 0.0D),
                4,
                3,
                2
        ));

        MultiblockShapeRegistry.register(new MultiblockShapeDefinition(
                INDUSTRIAL_PRESS,
                ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "models/block/industrial_press.json"),
                2.0D,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, -8.0D),
                2,
                3,
                1
        ));
    }
}
