package com.skyeshade.skyent.test;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.render.MeshDestroyProgress;
import com.skyeshade.skyent.content.block.LargeSteamTurbineBlock;
import com.skyeshade.skyent.content.model.*;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientCommandHandler;

import static com.skyeshade.skyent.test.MeshVisualTest.*;

/**
 * Actual client render, real player movement and native destroy-progress reproduction.
 */
final class MeshPolishValidation {
    private static volatile String failure;
    private static Vec3 bladeCenter, bladeNormal;
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    static boolean tick(Minecraft mc, int tick) throws Exception {
        if (failure != null) throw new IllegalStateException(failure);
        if (tick == 1) {
            var def = MeshMachineDefinition.LARGE_STEAM_TURBINE;
            try (var reader = mc.getResourceManager().openAsReader(def.source())) {
                var mesh = def.compileStatic(def.parse(reader));
                var blade = mesh.triangles().stream().filter(t -> t.doubleSided() && t.path().contains("staters")).findFirst().orElseThrow();
                bladeCenter = def.transform().authoredRelative(blade.a().position().add(blade.b().position()).add(blade.c().position()).scale(1.0 / 3), Direction.NORTH).add(Vec3.atLowerCornerOf(origin(0)));
                bladeNormal = blade.normal();
                SkyesNuclearTech.LOGGER.info("MESH_POLISH: {} geometric triangles, {} submitted with reverse faces, blade {} normal {}", mesh.triangles().size(), mesh.renderTriangles().size(), bladeCenter, bladeNormal);
            }
            look(mc, bladeCenter.add(bladeNormal.scale(.7)), bladeCenter);
            mc.options.fov().set(45);
        }
        if (tick == 35) capture(mc, "blade-front");
        if (tick == 40) look(mc, bladeCenter.add(bladeNormal.scale(-.7)), bladeCenter);
        if (tick == 75) capture(mc, "blade-back");
        if (tick == 80) {
            mc.options.fov().set(60);
            mc.getSingleplayerServer().execute(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                p.setGameMode(GameType.SURVIVAL);
                Vec3 start = footprint(0, new Vec3(1.5, 3.5, 2.3));
                p.teleportTo(mc.getSingleplayerServer().overworld(), start.x, start.y, start.z, 0, 15);
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (tick == 130) {
            assertStanding(mc);
            capture(mc, "canopy-standing");
        }
        if (tick == 135) mc.options.keyUp.setDown(true);
        if (tick == 143) mc.options.keyUp.setDown(false);
        if (tick == 160) {
            assertStanding(mc);
            capture(mc, "canopy-walking");
        }
        if (tick == 170) {
            mc.getSingleplayerServer().execute(() -> mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).setGameMode(GameType.SPECTATOR));
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            camera(mc, 0);
        }
        // All ten native stages, alternating controller and part targets.
        if (tick >= 210 && tick < 410 && (tick - 210) % 20 == 0) {
            int stage = (tick - 210) / 20;
            BlockPos target = stage % 2 == 0 ? origin(0) : ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin(0), Direction.NORTH, 1, 2, 3);
            mc.level.destroyBlockProgress(98761, target, stage);
            check(((MeshDestroyProgress) mc.levelRenderer).skyent$meshDestroyStage(origin(0)) == stage, "Native destroy stage " + stage);
        }
        if (tick >= 220 && tick < 420 && (tick - 220) % 20 == 0) {
            int stage = (tick - 220) / 20;
            check(((MeshDestroyProgress) mc.levelRenderer).skyent$meshDestroyStage(origin(0)) == stage, "Destroy stage remains active at capture " + stage);
            capture(mc, "cracks-" + stage);
        }
        if (tick == 420) {
            mc.level.destroyBlockProgress(98761, origin(0), -1);
            check(((MeshDestroyProgress) mc.levelRenderer).skyent$meshDestroyStage(origin(0)) == -1, "Cancel clears crack stage");
        }
        if (tick == 435) capture(mc, "cracks-cleared");
        if (tick == 440) {
            mc.level.destroyBlockProgress(98761, origin(0), 2);
            mc.level.destroyBlockProgress(98762, origin(0), 7);
            check(((MeshDestroyProgress) mc.levelRenderer).skyent$meshDestroyStage(origin(0)) == 7, "Highest concurrent destroy stage");
            mc.level.destroyBlockProgress(98762, origin(0), -1);
            check(((MeshDestroyProgress) mc.levelRenderer).skyent$meshDestroyStage(origin(0)) == 2, "Other miner persists after cancel");
            mc.level.destroyBlockProgress(98761, origin(0), -1);
            MeshShadingFixture.enabled = true;
            look(mc, new Vec3(8, 208, -16), new Vec3(2.5, 203.3, -8.5));
        }
        if (tick == 480) capture(mc, "shading-above");
        if (tick == 485) look(mc, new Vec3(-3, 201, -14), new Vec3(2.5, 203.3, -8.5));
        if (tick == 525) capture(mc, "shading-below");
        if (tick == 530) {
            // Stand inside the reference cube: its outward faces must remain culled.
            look(mc, new Vec3(.5, 203.5, -9.5), new Vec3(.5, 203.5, -11));
        }
        if (tick == 550) capture(mc, "closed-backfaces");
        if (tick == 555) {
            MeshShadingFixture.enabled = false;
            camera(mc, 0);
            ClientCommandHandler.runCommand("skyent_mesh_debug all");
        }
        for (int i = 0; i < 4; i++) {
            if (tick == 560 + i * 40) camera(mc, i);
            if (tick == 590 + i * 40) capture(mc, "aligned-" + FACINGS[i].getName());
        }
        if (tick == 720) {
            camera(mc, 0);
            ClientCommandHandler.runCommand("skyent_mesh_debug voxels");
        }
        if (tick == 750) capture(mc, "occupancy");
        if (tick == 755) {
            ClientCommandHandler.runCommand("skyent_mesh_debug outline");
            look(mc, footprint(0, new Vec3(1.5, 4, 3)), footprint(0, new Vec3(1.5, 2, 3)));
        }
        if (tick == 790) capture(mc, "canopy-outline-above");
        if (tick == 795) look(mc, footprint(0, new Vec3(1.5, 1.6, 3)), footprint(0, new Vec3(1.5, 2.1, 3)));
        if (tick == 825) capture(mc, "canopy-outline-below");
        if (tick == 830) {
            ClientCommandHandler.runCommand("skyent_mesh_debug off");
            SkyesNuclearTech.LOGGER.info("MESH_POLISH: COMPLETE - sides, real player canopy, all destroy stages/cancel, shading fixture, four-facing overlays");
            return true;
        }
        return false;
    }

    private static Vec3 footprint(int index, Vec3 p) {
        return MeshMachineDefinition.LARGE_STEAM_TURBINE.transform().relative(p, FACINGS[index]).add(Vec3.atLowerCornerOf(origin(index)));
    }

    private static void look(Minecraft mc, Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        teleport(mc, eye.add(0, -mc.player.getEyeHeight(), 0), (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }

    private static void assertStanding(Minecraft mc) {
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            double y = player.getY() - 200;
            if (y < 2.03 || y > 2.14 || !player.onGround())
                failure = "Real player canopy support failed: y=" + y + " ground=" + player.onGround();
            SkyesNuclearTech.LOGGER.info("MESH_POLISH: real player canopy y={} onGround={}", y, player.onGround());
        });
    }
}
