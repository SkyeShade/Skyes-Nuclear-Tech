package com.skyeshade.skyent.test;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.model.MeshMachineDefinition;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandHandler;

import static com.skyeshade.skyent.test.MeshVisualTest.*;

/**
 * Close, repeatable actual-game comparisons of the requested flat and sloped surfaces. Development only.
 */
final class MeshHybridValidation {
    private record View(String name, Vec3 eye, Vec3 target) {
    }

    private static final View[] VIEWS = {
            new View("housing", new Vec3(5, 3.1, .7), new Vec3(2.5, 1.2, .8)),
            new View("electrical-panel", new Vec3(1.5, 2.4, -3), new Vec3(1.5, 1.1, .2)),
            new View("top-slabs", new Vec3(1.5, 5, -.3), new Vec3(1.5, 2.3, 1)),
            new View("base-rails", new Vec3(5, .9, 4), new Vec3(2.65, .15, 3)),
            new View("canopy-glass", new Vec3(1.5, 5, 3), new Vec3(1.5, 2.03, 3)),
            new View("angled-frame", new Vec3(5, 3.5, 5.5), new Vec3(2.4, 1.8, 3.5))
    };
    private static final String[] MODES = {"off", "hybrid_outline", "hybrid"};

    static boolean tick(Minecraft mc, int tick) {
        int shot = (tick - 1) / 30, step = (tick - 1) % 30;
        if (shot < 18) {
            var view = VIEWS[shot / 3];
            String mode = MODES[shot % 3];
            if (step == 0) {
                ClientCommandHandler.runCommand("skyent_mesh_debug " + mode);
                look(mc, view.eye, view.target);
            }
            if (step == 25) capture(mc, "hybrid-" + view.name + "-" + mode);
        } else if (shot < 26) {
            int facing = (shot - 18) / 2;
            String mode = shot % 2 == 0 ? "hybrid" : "hybrid_outline";
            if (step == 0) {
                ClientCommandHandler.runCommand("skyent_mesh_debug " + mode);
                camera(mc, facing);
            }
            if (step == 25) capture(mc, "hybrid-facing-" + facing + "-" + mode);
        } else {
            ClientCommandHandler.runCommand("skyent_mesh_debug off");
            var data = MeshMachineShapeCache.data();
            SkyesNuclearTech.LOGGER.info("MESH_HYBRID: COMPLETE collision={} outline={}", data.collisionHybrid().counts(), data.outlineHybrid().counts());
            return true;
        }
        return false;
    }

    private static void look(Minecraft mc, Vec3 eye, Vec3 target) {
        var transform = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        Vec3 worldEye = transform.relative(eye, Direction.NORTH).add(Vec3.atLowerCornerOf(origin(0)));
        Vec3 d = target.subtract(eye);
        teleport(mc, worldEye.add(0, -mc.player.getEyeHeight(), 0), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }
}
