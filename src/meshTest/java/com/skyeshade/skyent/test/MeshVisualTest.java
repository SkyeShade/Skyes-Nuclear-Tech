package com.skyeshade.skyent.test;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.model.MeshBakedModel;
import com.skyeshade.skyent.client.renderer.blockentity.*;
import com.skyeshade.skyent.content.block.*;
import com.skyeshade.skyent.content.blockentity.LargeSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.model.*;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import com.skyeshade.skyent.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.nio.file.*;
import java.util.*;

/**
 * Repeatable isolated visual/reload/save-load harness, present only in development runs.
 */
@EventBusSubscriber(modid = "skyent", value = Dist.CLIENT)
public final class MeshVisualTest {
    private static int ticks, phase, worldTicks;
    private static volatile boolean prepared, reloaded;
    private static boolean opened;
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    static BlockPos origin(int i) {
        return new BlockPos(i * 16, 200, 0);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("skyent.meshVisualTest")) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        try {
            if (!opened && ticks > 80 && mc.getOverlay() == null) {
                opened = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.fov().set(60);
                mc.options.renderDistance().set(6);
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/mesh-validation/level.dat")))
                    mc.createWorldOpenFlows().openWorld("mesh-validation", () -> {
                        throw new IllegalStateException("Cannot open validation world");
                    });
                else mc.createWorldOpenFlows().createFreshLevel("mesh-validation",
                        new LevelSettings("Mesh validation", GameType.SPECTATOR, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234L, false, false), access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null)
                return;
            worldTicks++;
            if (phase == 0) {
                phase = 1;
                var uuid = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var server = mc.getSingleplayerServer();
                        var level = server.overworld();
                        var player = server.getPlayerList().getPlayer(uuid);
                        level.setDayTime(6000);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
                        player.setGameMode(GameType.SPECTATOR);
                        level.setBlock(new BlockPos(4, 203, -10), Blocks.WHITE_CONCRETE.defaultBlockState(), 3);
                        for (int x = -16; x < 72; x++)
                            for (int z = -16; z < 30; z++)
                                level.setBlock(new BlockPos(x, 199, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3);
                        for (int i = 0; i < 4; i++) {
                            BlockPos p = origin(i);
                            if (level.getBlockState(p).is(ModBlocks.LARGE_STEAM_TURBINE.get()))
                                level.destroyBlock(p, false);
                            player.setYRot(FACINGS[i].getOpposite().toYRot());
                            ItemStack stack = new ItemStack(ModItems.LARGE_STEAM_TURBINE.get());
                            var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(p), Direction.UP, p, false));
                            check(ModItems.LARGE_STEAM_TURBINE.get().place(context).consumesAction(), "Visual placement " + FACINGS[i]);
                        }
                        // Populate real legacy multiblocks in the same world for renderer regression screenshots.
                        BlockPos rolling = new BlockPos(0, 200, 16);
                        level.setBlock(rolling, ModBlocks.ROLLING_MILL.get().defaultBlockState(), 3);
                        ModBlocks.ROLLING_MILL.get().setPlacedBy(level, rolling, level.getBlockState(rolling), player, new ItemStack(ModItems.ROLLING_MILL.get()));
                        BlockPos centrifuge = new BlockPos(10, 200, 16);
                        level.setBlock(centrifuge, ModBlocks.CENTRIFUGE.get().defaultBlockState(), 3);
                        ModBlocks.CENTRIFUGE.get().setPlacedBy(level, centrifuge, level.getBlockState(centrifuge), player, new ItemStack(ModItems.CENTRIFUGE.get()));
                        BlockPos arc = new BlockPos(20, 200, 16);
                        level.setBlock(arc, ModBlocks.ARC_FURNACE.get().defaultBlockState(), 3);
                        ModBlocks.ARC_FURNACE.get().setPlacedBy(level, arc, level.getBlockState(arc), player, new ItemStack(ModItems.ARC_FURNACE.get()));
                        level.setBlock(new BlockPos(7, 200, 16), ModBlocks.LV_STEAM_TURBINE.get().defaultBlockState(), 3);
                        prepared = true;
                    } catch (Exception e) {
                        fail(e);
                    }
                });
            }
            if (!prepared) return;
            if (phase == 1) {
                validateModels(mc);
                worldTicks = 0;
                phase = 2;
                camera(mc, 0);
            }
            if (phase == 2) {
                if (worldTicks == 100) capture(mc, "north");
                if (worldTicks == 102)
                    net.neoforged.neoforge.client.ClientCommandHandler.runCommand("skyent_mesh_debug all");
                if (worldTicks == 115) capture(mc, "debug");
                if (worldTicks == 117)
                    net.neoforged.neoforge.client.ClientCommandHandler.runCommand("skyent_mesh_debug off");
                if (worldTicks == 120) camera(mc, 1);
                if (worldTicks == 200) capture(mc, "east");
                if (worldTicks == 220) camera(mc, 2);
                if (worldTicks == 300) capture(mc, "south");
                if (worldTicks == 320) camera(mc, 3);
                if (worldTicks == 400) capture(mc, "west");
                if (worldTicks == 420) {
                    mc.options.fov().set(30);
                    teleport(mc, new Vec3(5, 200.8, 4.25), 90, 10);
                }
                if (worldTicks == 470) capture(mc, "culling");
                if (worldTicks == 490) {
                    var old = LargeSteamTurbineRenderer.model();
                    mc.reloadResourcePacks().thenRun(() -> {
                        check(old != LargeSteamTurbineRenderer.model(), "Resource reload replaced baked geometry");
                        validateModels(mc);
                        reloaded = true;
                    });
                }
                if (worldTicks > 530 && reloaded) {
                    capture(mc, "reloaded");
                    mc.options.fov().set(60);
                    phase = 7;
                    worldTicks = 0;
                }
            } else if (phase == 7) {
                if (MeshPolishValidation.tick(mc, worldTicks)) {
                    phase = 8;
                    worldTicks = 0;
                }
            } else if (phase == 8) {
                if (MeshHybridValidation.tick(mc, worldTicks)) {
                    phase = 3;
                    worldTicks = 0;
                    teleport(mc, new Vec3(13, 207, 7), 0, 25);
                }
            } else if (phase == 3 && worldTicks == 100) {
                capture(mc, "legacy");
                mc.getSingleplayerServer().execute(() -> {
                    mc.getSingleplayerServer().saveEverything(false, true, true);
                });
            } else if (phase == 3 && worldTicks == 140) {
                phase = 4;
                ticks = 0;
                mc.level.disconnect();
                mc.disconnect();
                mc.setScreen(new TitleScreen());
            } else if (phase == 4 && worldTicks > 10) { /* re-entered world */ }
            if (phase == 5 && worldTicks == 80) {
                var server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try {
                        for (int i = 0; i < 4; i++) {
                            int index = i;
                            BlockPos origin = origin(i);
                            var level = server.overworld();
                            check(level.getBlockEntity(origin) instanceof LargeSteamTurbineBlockEntity, "Saved controller restored");
                            ModelMultiblocks.forEachLocal(LargeSteamTurbineBlock.MULTIBLOCK, (x, y, z) -> {
                                var pos = ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin, FACINGS[index], x, y, z);
                                var state = level.getBlockState(pos);
                                check(state.is(ModBlocks.LARGE_STEAM_TURBINE.get()) || state.is(ModBlocks.LARGE_STEAM_TURBINE_PART.get()), "Saved footprint restored");
                                check(LargeSteamTurbineBlock.masterPos(pos, state).equals(origin), "Saved controller relationship");
                            });
                        }
                        SkyesNuclearTech.LOGGER.info("MESH_VISUAL_TEST: save/reopen passed");
                    } catch (Exception e) {
                        fail(e);
                    }
                });
                camera(mc, 0);
            }
            if (phase == 5 && worldTicks == 160) {
                capture(mc, "north-reopened");
                SkyesNuclearTech.LOGGER.info("MESH_VISUAL_TEST: COMPLETE");
                phase = 6;
            }
            if (phase == 6 && worldTicks > 200) mc.stop();
        } catch (Exception e) {
            fail(e);
            mc.stop();
        }
    }

    @SubscribeEvent
    public static void reopen(ClientTickEvent.Pre event) {
        if (Boolean.getBoolean("skyent.meshVisualTest") && phase == 4 && ticks > 60) {
            phase = 5;
            worldTicks = 0;
            Minecraft.getInstance().createWorldOpenFlows().openWorld("mesh-validation", () -> {
                throw new IllegalStateException("Reopen failed");
            });
        }
    }

    static void camera(Minecraft mc, int index) {
        Direction facing = FACINGS[index];
        var transform = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        Vec3 eye = transform.relative(new Vec3(7, 5, -6), facing).add(Vec3.atLowerCornerOf(origin(index)));
        Vec3 target = transform.relative(new Vec3(1.5, 1.1, 2.5), facing).add(Vec3.atLowerCornerOf(origin(index)));
        Vec3 d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        teleport(mc, eye.add(0, -mc.player.getEyeHeight(), 0), yaw, pitch);
    }

    static void teleport(Minecraft mc, Vec3 pos, float yaw, float pitch) {
        var uuid = mc.player.getUUID();
        mc.getSingleplayerServer().execute(() -> {
            var player = mc.getSingleplayerServer().getPlayerList().getPlayer(uuid);
            player.teleportTo(mc.getSingleplayerServer().overworld(), pos.x, pos.y, pos.z, yaw, pitch);
        });
    }

    static void capture(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, "mesh-" + name + ".png", mc.getMainRenderTarget(), message -> SkyesNuclearTech.LOGGER.info("MESH_VISUAL_TEST: {}", message.getString()));
    }

    private static void validateModels(Minecraft mc) {
        MeshBakedModel model = LargeSteamTurbineRenderer.model();
        check(model != null, "Mesh baked model available");
        Set<String> materials = new HashSet<>();
        for (Direction facing : FACINGS) {
            check(model.quads(facing).size() > 1048, "Static geometry includes plane reverse faces");
            for (var quad : model.quads(facing)) {
                check(!quad.getSprite().contents().name().equals(MissingTextureAtlasSprite.getLocation()), "Resolved material");
                materials.add(quad.getSprite().contents().name().toString());
            }
        }
        check(materials.size() == 5, "All five materials are baked");
        check(mc.getModelManager().getModel(RollingMillRenderer.ROLLERS_MODEL) != mc.getModelManager().getMissingModel(), "Rolling mill mesh resolves");
        check(mc.getModelManager().getModel(ArcFurnaceRenderer.BASE_MODEL) != mc.getModelManager().getMissingModel(), "Arc furnace model resolves");
        SkyesNuclearTech.LOGGER.info("MESH_VISUAL_TEST: baking/materials passed {}", materials);
    }

    static void check(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }

    private static void fail(Exception error) {
        SkyesNuclearTech.LOGGER.error("MESH_VISUAL_TEST: FAILED", error);
    }
}
