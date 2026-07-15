package com.skyeshade.skyent.event.systems;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.skyeshade.skyent.content.explosion.NuclearExplosionTuning;
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
                                .executes(context -> setRadiationDebugOverlay(context.getSource(), BoolArgumentType.getBool(context, "enabled")))))
                .then(Commands.literal("nuke_tuning")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.literal("get")
                                .then(Commands.literal("nuclearChargeRadius")
                                        .executes(context -> getNuclearChargeRadius(context.getSource()))))
                        .then(Commands.literal("set")
                                .then(Commands.literal("nuclearChargeRadius")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(
                                                        NuclearExplosionTuning.MIN_NUCLEAR_CHARGE_RADIUS,
                                                        NuclearExplosionTuning.MAX_NUCLEAR_CHARGE_RADIUS
                                                ))
                                                .executes(context -> setNuclearChargeRadius(
                                                        context.getSource(),
                                                        DoubleArgumentType.getDouble(context, "value")
                                                ))))))
                .then(Commands.literal("nuke_config")
                        .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                        .then(Commands.literal("get")
                                .then(Commands.literal("radius")
                                        .executes(context -> getNuclearChargeRadius(context.getSource()))))
                        .then(Commands.literal("set")
                                .then(Commands.literal("radius")
                                        .then(Commands.argument("value", DoubleArgumentType.doubleArg(
                                                        NuclearExplosionTuning.MIN_NUCLEAR_CHARGE_RADIUS,
                                                        NuclearExplosionTuning.MAX_NUCLEAR_CHARGE_RADIUS
                                                ))
                                                .executes(context -> setNuclearChargeRadius(
                                                        context.getSource(),
                                                        DoubleArgumentType.getDouble(context, "value")
                                                )))))));
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

    private static int getNuclearChargeRadius(CommandSourceStack source) {
        source.sendSuccess(
                () -> Component.literal("Nuclear Charge radius is " + formatRadius(NuclearExplosionTuning.nuclearChargeRadius) + " blocks"),
                false
        );
        return Command.SINGLE_SUCCESS;
    }

    private static int setNuclearChargeRadius(CommandSourceStack source, double radius) {
        double clampedRadius = NuclearExplosionTuning.setNuclearChargeRadius(radius);
        source.sendSuccess(
                () -> Component.literal("Nuclear Charge radius set to " + formatRadius(clampedRadius) + " blocks"),
                true
        );
        return Command.SINGLE_SUCCESS;
    }

    private static String formatRadius(double radius) {
        if (Math.rint(radius) == radius) {
            return Long.toString(Math.round(radius));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", radius);
    }
}
