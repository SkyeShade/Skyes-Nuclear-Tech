package com.skyeshade.skyent.network;

import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.content.menu.ElectricBlastFurnaceMenu;
import com.skyeshade.skyent.content.menu.MVAssemblerMenu;
import com.skyeshade.skyent.content.menu.MVAssemblerRecipeSelectMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ServerPayloadHandlers {
    private ServerPayloadHandlers() {
    }

    public static void handleOpenMVAssemblerRecipeSelect(OpenMVAssemblerRecipeSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof MVAssemblerMenu menu
                    && menu.getBlockPos().equals(payload.pos())) {
                openRecipeSelectMenu(serverPlayer, menu.getBlockEntity());
            }
        });
    }

    public static void handleOpenMVAssembler(OpenMVAssemblerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof MVAssemblerRecipeSelectMenu menu
                    && menu.getBlockPos().equals(payload.pos())) {
                openAssemblerMenu(serverPlayer, menu.getBlockEntity());
            }
        });
    }

    public static void handleSelectMVAssemblerRecipe(SelectMVAssemblerRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MVAssemblerMenu menu
                    && menu.getBlockPos().equals(payload.pos())) {
                menu.getBlockEntity().selectRecipe(payload.recipeId());
            } else if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.containerMenu instanceof MVAssemblerRecipeSelectMenu menu
                    && menu.getBlockPos().equals(payload.pos())) {
                menu.getBlockEntity().selectRecipe(payload.recipeId());
                openAssemblerMenu(serverPlayer, menu.getBlockEntity());
            }
        });
    }

    public static void handleToggleElectricBlastFurnaceMode(ToggleElectricBlastFurnaceModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof ElectricBlastFurnaceMenu menu
                    && menu.getBlockPos().equals(payload.pos())) {
                menu.getBlockEntity().toggleMode();
            }
        });
    }

    private static void openRecipeSelectMenu(ServerPlayer player, MVAssemblerBlockEntity assembler) {
        player.openMenu(
                new SimpleMenuProvider(
                        (containerId, inventory, ignored) -> new MVAssemblerRecipeSelectMenu(containerId, inventory, assembler),
                        Component.translatable("container.skyent.assembler_recipe_select")
                ),
                buffer -> buffer.writeBlockPos(assembler.getBlockPos())
        );
    }

    private static void openAssemblerMenu(ServerPlayer player, MVAssemblerBlockEntity assembler) {
        player.openMenu(assembler, assembler.getBlockPos());
    }
}
