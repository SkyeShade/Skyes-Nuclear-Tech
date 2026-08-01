package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.shape.MultiblockShapeDefinition;
import com.skyeshade.skyent.content.shape.MultiblockShapeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public final class ModMultiblockShapes {
    public static final ResourceLocation ROLLING_MILL = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "rolling_mill");
    public static final ResourceLocation INDUSTRIAL_PRESS = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "industrial_press");
    public static final ResourceLocation MEDIUM_TANK = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "medium_tank");
    public static final ResourceLocation BLAST_DOOR_FRAME = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "blast_door_frame");
    public static final ResourceLocation ZONE_GATE = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "zone_gate");
    public static final ResourceLocation CENTRIFUGE = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "centrifuge");

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

        MultiblockShapeRegistry.register(new MultiblockShapeDefinition(
                MEDIUM_TANK,
                ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "models/block/medium_tank.json"),
                2.0D,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 16.0D),
                2,
                2,
                4
        ));

        MultiblockShapeRegistry.register(new MultiblockShapeDefinition(
                BLAST_DOOR_FRAME,
                ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "models/block/blast_door_frame.json"),
                2.0D,
                Vec3.ZERO,
                // Rendering is centered on the bottom-center controller and translates X by -16px.
                // The shape slicer indexes the 3-wide footprint from local X 0..48, so compensate
                // that controller-origin offset here instead of using the visual X translation.
                new Vec3(0.0D, 0.0D, -8.0D),
                3,
                3,
                1
        ));

        MultiblockShapeRegistry.register(new MultiblockShapeDefinition(
                ZONE_GATE,
                ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "models/block/zone_gate_frame.json"),
                4.0D,
                Vec3.ZERO,
                new Vec3(24.0D, 0.0D, -24.0D),
                7,
                4,
                1
        ));

        MultiblockShapeRegistry.register(new MultiblockShapeDefinition(
                CENTRIFUGE,
                ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "models/block/centrifuge_placed.json"),
                2.0D,
                Vec3.ZERO,
                new Vec3(0.0D, 0.0D, 16.0D),
                3,
                3,
                3
        ));
    }
}
