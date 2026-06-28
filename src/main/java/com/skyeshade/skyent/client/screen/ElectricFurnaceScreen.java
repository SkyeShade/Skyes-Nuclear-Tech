package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.ElectricFurnaceMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;

    public static final int ENERGY_BAR_X = CombustionGeneratorScreen.ENERGY_BAR_X;
    public static final int ENERGY_BAR_Y = CombustionGeneratorScreen.ENERGY_BAR_Y;
    public static final int ENERGY_BAR_WIDTH = CombustionGeneratorScreen.ENERGY_BAR_WIDTH;
    public static final int ENERGY_BAR_HEIGHT = CombustionGeneratorScreen.ENERGY_BAR_HEIGHT;
    public static final int ENERGY_BAR_U = CombustionGeneratorScreen.ENERGY_BAR_U;
    public static final int ENERGY_BAR_V = CombustionGeneratorScreen.ENERGY_BAR_V;

    public static final int PROGRESS_ARROW_X = 70;
    public static final int PROGRESS_ARROW_Y = 26;
    public static final int PROGRESS_ARROW_U = 234;
    public static final int PROGRESS_ARROW_V = 0;
    public static final int PROGRESS_ARROW_WIDTH = 22;
    public static final int PROGRESS_ARROW_HEIGHT = 16;

    public static final int RUNNING_OVERLAY_X = 42;
    public static final int RUNNING_OVERLAY_Y = 14;
    public static final int RUNNING_OVERLAY_U = 209;
    public static final int RUNNING_OVERLAY_V = 5;
    public static final int RUNNING_OVERLAY_WIDTH = 23;
    public static final int RUNNING_OVERLAY_HEIGHT = 40;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/electric_furnace.png"
    );

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, energyTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderEnergyBar(guiGraphics);
        renderProgressArrow(guiGraphics);
        renderRunningOverlay(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderEnergyBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStored();
        if (stored <= 0) {
            return;
        }

        int maxEnergy = Math.max(1, menu.getMaxEnergyStored());
        int filledHeight = stored * ENERGY_BAR_HEIGHT / maxEnergy;
        guiGraphics.blit(
                TEXTURE,
                leftPos + ENERGY_BAR_X,
                topPos + ENERGY_BAR_Y + ENERGY_BAR_HEIGHT - filledHeight,
                ENERGY_BAR_U,
                ENERGY_BAR_V + ENERGY_BAR_HEIGHT - filledHeight,
                ENERGY_BAR_WIDTH,
                filledHeight,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderProgressArrow(GuiGraphics guiGraphics) {
        int maxProgress = menu.getMaxCookProgress();
        if (maxProgress <= 0) {
            return;
        }

        int filledWidth = menu.getCookProgress() * PROGRESS_ARROW_WIDTH / maxProgress;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                leftPos + PROGRESS_ARROW_X,
                topPos + PROGRESS_ARROW_Y,
                PROGRESS_ARROW_U,
                PROGRESS_ARROW_V,
                filledWidth,
                PROGRESS_ARROW_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderRunningOverlay(GuiGraphics guiGraphics) {
        if (!menu.isActivelyCooking()) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                leftPos + RUNNING_OVERLAY_X,
                topPos + RUNNING_OVERLAY_Y,
                RUNNING_OVERLAY_U,
                RUNNING_OVERLAY_V,
                RUNNING_OVERLAY_WIDTH,
                RUNNING_OVERLAY_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStored() + " / " + menu.getMaxEnergyStored() + " FE").withStyle(ChatFormatting.RED),
                Component.literal("Usage: " + menu.getCurrentEnergyUsage() + " FE/t").withStyle(ChatFormatting.RED)
        );
    }
}
