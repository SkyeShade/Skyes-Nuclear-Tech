package com.skyeshade.skyent.test;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.LargeSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.model.MeshMachineDefinition;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import com.skyeshade.skyent.registry.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.*;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientCommandHandler;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.nio.file.Files;
import static com.skyeshade.skyent.test.MeshVisualTest.*;

/** Only shape overlays, placement/NBT load and canopy support. No renderer regression sequence. */
@EventBusSubscriber(modid = "skyent", value = Dist.CLIENT)
public final class MeshShapeValidation {
    private static int ticks, viewTick;
    private static boolean opened, preparing;
    private static volatile boolean prepared;
    private static volatile Throwable failure;
    private static final String[] MODES = {"off", "hybrid", "hybrid_outline", "outline"};

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("skyent.shapeVisualTest")) return;
        var mc = Minecraft.getInstance();
        try {
            if (failure != null) throw new IllegalStateException("Shape smoke test failed", failure);
            if (!opened && ++ticks > 60 && mc.getOverlay() == null) {
                opened = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.hideGui = true;
                mc.options.renderDistance().set(4);
                mc.options.fov().set(60);
                mc.options.enableVsync().set(false);
                mc.options.framerateLimit().set(260);
                if (Files.exists(mc.gameDirectory.toPath().resolve("saves/shape-validation/level.dat")))
                    mc.createWorldOpenFlows().openWorld("shape-validation", () -> { throw new IllegalStateException("Open failed"); });
                else mc.createWorldOpenFlows().createFreshLevel("shape-validation",
                        new LevelSettings("Shape validation", GameType.SPECTATOR, false, Difficulty.PEACEFUL, true,
                                new GameRules(), WorldDataConfiguration.DEFAULT), new WorldOptions(1234L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
            }
            if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
            if (!preparing) {
                preparing = true;
                var uuid = mc.player.getUUID();
                mc.getSingleplayerServer().execute(() -> {
                    try {
                        var server = mc.getSingleplayerServer();
                        var level = server.overworld();
                        var player = server.getPlayerList().getPlayer(uuid);
                        level.setDayTime(6000);
                        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                        for (int x = -8; x < 9; x++) for (int z = -8; z < 12; z++)
                            level.setBlock(new BlockPos(x,199,z), Blocks.SMOOTH_STONE.defaultBlockState(),3);
                        var origin = origin(0);
                        level.destroyBlock(origin, false);
                        player.setGameMode(GameType.SPECTATOR);
                        player.setYRot(Direction.SOUTH.toYRot());
                        var stack = new ItemStack(ModItems.LARGE_STEAM_TURBINE.get());
                        var context = new BlockPlaceContext(level,player,InteractionHand.MAIN_HAND,stack,
                                new BlockHitResult(Vec3.atCenterOf(origin),Direction.UP,origin,false));
                        check(ModItems.LARGE_STEAM_TURBINE.get().place(context).consumesAction(),"Shape placement");
                        var entity = level.getBlockEntity(origin);
                        check(entity instanceof LargeSteamTurbineBlockEntity,"Controller exists");
                        check(BlockEntity.loadStatic(origin,entity.getBlockState(),entity.saveWithFullMetadata(level.registryAccess()),
                                level.registryAccess()) instanceof LargeSteamTurbineBlockEntity,"Controller NBT load");
                        check(!MeshMachineShapeCache.data().audit().startsWith("FALLBACK"),"Shapes loaded");
                        var transform = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
                        player.setGameMode(GameType.SURVIVAL);
                        player.noPhysics = false;
                        player.setPos(transform.relative(new Vec3(1.5,3,3),Direction.NORTH).add(Vec3.atLowerCornerOf(origin)));
                        player.move(MoverType.SELF,new Vec3(0,-2,0));
                        double y = player.getY() - origin.getY();
                        check(Math.abs(y - 2.031375) < .002,"Glass supports at rendered Y: " + y);
                        player.setGameMode(GameType.SPECTATOR);
                        SkyesNuclearTech.LOGGER.info("SHAPE_SMOKE PASS placement, NBT load, canopy standing y={}",y);
                        prepared = true;
                    } catch (Throwable e) { failure = e; }
                });
            }
            if (!prepared) return;
            if (Boolean.getBoolean("skyent.metadataVisualTest")) {
                MeshMetadataValidation.tick(mc,viewTick++);
                return;
            }
            int tick = viewTick++, shot = tick / 100, phase = tick % 100;
            if (shot >= 8) {
                ClientCommandHandler.runCommand("skyent_mesh_debug off");
                SkyesNuclearTech.LOGGER.info("SHAPE_VISUAL COMPLETE");
                mc.stop();
                return;
            }
            String mode = MODES[shot % 4];
            if (phase == 0) {
                ClientCommandHandler.runCommand("skyent_mesh_debug " + mode);
                Vec3 eye = shot < 4 ? new Vec3(5,3.5,5.5) : new Vec3(1.5,5,-.3);
                Vec3 target = shot < 4 ? new Vec3(2.0,1.8,3.5) : new Vec3(1.5,2.3,1);
                Vec3 delta = target.subtract(eye);
                Vec3 world = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform().relative(eye,Direction.NORTH).add(Vec3.atLowerCornerOf(origin(0)));
                teleport(mc,world.add(0,-mc.player.getEyeHeight(),0),(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),
                        (float)-Math.toDegrees(Math.atan2(delta.y,Math.sqrt(delta.x*delta.x+delta.z*delta.z))));
            }
            if (phase == 85) {
                capture(mc,"shape-" + (shot < 4 ? "canopy-" : "top-") + mode);
                SkyesNuclearTech.LOGGER.info("SHAPE_VIEW {} {} fps={}",shot < 4 ? "canopy" : "top",mode,mc.getFps());
            }
        } catch (Throwable e) {
            SkyesNuclearTech.LOGGER.error("SHAPE_VISUAL FAILED",e);
            mc.stop();
        }
    }
}
