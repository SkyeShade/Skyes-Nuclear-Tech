package com.skyeshade.skyent.content.model;

import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MeshModelAuditCommand {
    private MeshModelAuditCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("skyent_mesh_audit").requires(source -> source.hasPermission(2)).executes(ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal(MeshMachineShapeCache.data().audit()), false);
            return MeshMachineShapeCache.data().audit().startsWith("FALLBACK") ? 0 : 1;
        }));
    }
}
