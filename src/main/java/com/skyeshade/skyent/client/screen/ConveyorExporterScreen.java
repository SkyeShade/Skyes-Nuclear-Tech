package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.ConveyorExporterMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ConveyorExporterScreen extends AbstractContainerScreen<ConveyorExporterMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int GUI_TEXTURE_WIDTH = 256;
    private static final int GUI_TEXTURE_HEIGHT = 256;
    private static final int MODE_BUTTON_X = 140;
    private static final int MODE_BUTTON_Y = 15;
    private static final int MODE_BUTTON_WIDTH = 18;
    private static final int MODE_BUTTON_HEIGHT = 18;
    private static final int MODE_ICON_X = 144;
    private static final int MODE_ICON_Y = 18;
    private static final int MODE_ICON_WIDTH = 10;
    private static final int MODE_ICON_HEIGHT = 12;
    private static final int BLACKLIST_ICON_U = 178;
    private static final int BLACKLIST_ICON_V = 18;
    private static final int WHITELIST_ICON_U = 191;
    private static final int WHITELIST_ICON_V = 18;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/conveyor_exporter.png"
    );

    private Button modeButton;

    public ConveyorExporterScreen(ConveyorExporterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        modeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    if (minecraft != null && minecraft.gameMode != null) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ConveyorExporterMenu.MODE_BUTTON_ID);
                    }
                })
                .bounds(leftPos + MODE_BUTTON_X, topPos + MODE_BUTTON_Y, MODE_BUTTON_WIDTH, MODE_BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderModeIcon(guiGraphics);
        renderTooltip(guiGraphics, mouseX, mouseY);
        if (modeButton != null && modeButton.isHovered()) {
            guiGraphics.renderComponentTooltip(font, modeTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderModeIcon(GuiGraphics guiGraphics) {
        int u = menu.isWhitelist() ? WHITELIST_ICON_U : BLACKLIST_ICON_U;
        int v = menu.isWhitelist() ? WHITELIST_ICON_V : BLACKLIST_ICON_V;
        guiGraphics.blit(
                TEXTURE,
                leftPos + MODE_ICON_X,
                topPos + MODE_ICON_Y,
                u,
                v,
                MODE_ICON_WIDTH,
                MODE_ICON_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private List<Component> modeTooltip() {
        return List.of(
                Component.literal(menu.isWhitelist() ? "Whitelist" : "Blacklist").withStyle(ChatFormatting.WHITE),
                Component.literal("Click to toggle filter mode").withStyle(ChatFormatting.GRAY)
        );
    }
}
