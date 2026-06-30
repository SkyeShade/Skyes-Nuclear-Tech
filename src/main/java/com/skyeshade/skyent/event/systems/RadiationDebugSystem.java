package com.skyeshade.skyent.event.systems;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.skyeshade.skyent.content.radiation.RadiationDebugRays;
import com.skyeshade.skyent.network.RadiationRaysDebugPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class RadiationDebugSystem {
    private static final int PERMISSION_LEVEL = 2;

    private RadiationDebugSystem() {
    }

    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyent")
                .then(Commands.literal("radiation_rays")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setRadiationRays(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("radiation_debug")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(context -> setRadiationDebugOverlay(context.getSource(), BoolArgumentType.getBool(context, "enabled"))))));
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RadiationDebugRays.remove(player);
        }
    }

    private static int setRadiationRays(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RadiationDebugRays.setEnabled(player, enabled);
        PacketDistributor.sendToPlayer(player, new RadiationRaysDebugPayload(enabled));
        player.sendSystemMessage(Component.literal(enabled ? "Radiation rays enabled" : "Radiation rays disabled"));
        return Command.SINGLE_SUCCESS;
    }

    private static int setRadiationDebugOverlay(CommandSourceStack source, boolean enabled) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        RadiationExposureSystem.setDebugOverlayEnabled(player, enabled);
        player.sendSystemMessage(Component.literal(enabled ? "Radiation debug overlay enabled" : "Radiation debug overlay disabled"));
        if (enabled) {
            RadiationExposureSystem.sendDebugOverlayEnabled(player);
        } else {
            RadiationExposureSystem.sendDebugOverlayDisabled(player);
        }
        return Command.SINGLE_SUCCESS;
    }
}
