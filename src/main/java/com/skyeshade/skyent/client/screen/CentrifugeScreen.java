package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.CentrifugeMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class CentrifugeScreen extends AbstractContainerScreen<CentrifugeMenu> {
    private static final int BASE_GUI_WIDTH = 176;
    private static final int GUI_WIDTH = 204;
    private static final int GUI_HEIGHT = 224;
    private static final int GUI_TEXTURE_WIDTH = 256;
    private static final int GUI_TEXTURE_HEIGHT = 256;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 9;
    private static final int INVENTORY_LABEL_Y = 131;
    private static final int TITLE_COLOR = 0x404040;

    private static final int INPUT_GAUGE_X = 21;
    private static final int OUTPUT_GAUGE_X = 129;
    private static final int GAUGE_Y = 19;
    private static final int GAUGE_WIDTH = 27;
    private static final int GAUGE_HEIGHT = 40;
    private static final int GAUGE_OVERLAY_U = 208;
    private static final int GAUGE_OVERLAY_V = 90;

    private static final int ENERGY_BAR_X = 184;
    private static final int ENERGY_BAR_Y = 29;
    private static final int ENERGY_BAR_WIDTH = 16;
    private static final int ENERGY_BAR_HEIGHT = 57;
    private static final int ENERGY_BAR_U = 205;
    private static final int ENERGY_BAR_V = 29;

    private static final int PROGRESS_ARROW_X = 66;
    private static final int PROGRESS_ARROW_Y = 38;
    private static final int PROGRESS_ARROW_U = 206;
    private static final int PROGRESS_ARROW_V = 133;
    private static final int PROGRESS_ARROW_WIDTH = 46;
    private static final int PROGRESS_ARROW_HEIGHT = 62;

    // Reserved texture slot for future battery/recharge item support.
    private static final int FUTURE_BATTERY_SLOT_X = 184;
    private static final int FUTURE_BATTERY_SLOT_Y = 90;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/centrifuge.png"
    );

    public CentrifugeScreen(CentrifugeMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        leftPos = (width - BASE_GUI_WIDTH) / 2;
        topPos = (height - imageHeight) / 2;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        int hoveredTank = hoveredTank(mouseX, mouseY);
        if (hoveredTank >= 0) {
            guiGraphics.renderComponentTooltip(font, fluidTooltip(hoveredTank), mouseX, mouseY);
        } else if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, energyTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderTank(guiGraphics, 0, INPUT_GAUGE_X);
        renderTank(guiGraphics, 1, OUTPUT_GAUGE_X);
        renderProgressArrow(guiGraphics);
        renderEnergyBar(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, TITLE_X, TITLE_Y, TITLE_COLOR, false);
        guiGraphics.drawString(font, playerInventoryTitle, INVENTORY_LABEL_X, INVENTORY_LABEL_Y, TITLE_COLOR, false);
    }

    private void renderTank(GuiGraphics guiGraphics, int tankIndex, int x) {
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                menu.getFluid(tankIndex),
                menu.getFluidAmount(tankIndex),
                menu.getFluidCapacity(tankIndex),
                leftPos + x,
                topPos + GAUGE_Y,
                GAUGE_WIDTH,
                GAUGE_HEIGHT
        );
        guiGraphics.blit(
                TEXTURE,
                leftPos + x,
                topPos + GAUGE_Y,
                GAUGE_OVERLAY_U,
                GAUGE_OVERLAY_V,
                GAUGE_WIDTH,
                GAUGE_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderProgressArrow(GuiGraphics guiGraphics) {
        int maxProgress = menu.getMaxProgress();
        if (maxProgress <= 0) {
            return;
        }

        int filledWidth = Math.min(PROGRESS_ARROW_WIDTH, menu.getProgress() * PROGRESS_ARROW_WIDTH / maxProgress);
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

    private void renderEnergyBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }

        int maxEnergy = Math.max(1, menu.getMaxEnergyStoredRJ());
        int filledHeight = Math.min(ENERGY_BAR_HEIGHT, stored * ENERGY_BAR_HEIGHT / maxEnergy);
        if (filledHeight <= 0) {
            return;
        }

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

    private int hoveredTank(int mouseX, int mouseY) {
        if (isHovering(INPUT_GAUGE_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            return 0;
        }
        return isHovering(OUTPUT_GAUGE_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY) ? 1 : -1;
    }

    private List<Component> fluidTooltip(int tankIndex) {
        return List.of(
                FluidGaugeRenderer.fluidDisplayName(menu.getFluid(tankIndex), menu.getFluidAmount(tankIndex)),
                Component.literal(menu.getFluidAmount(tankIndex) + " / " + menu.getFluidCapacity(tankIndex) + " mB")
        );
    }

    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStoredRJ() + " / " + menu.getMaxEnergyStoredRJ() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
                Component.literal("Usage: " + menu.getCurrentEnergyUsage() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED)
        );
    }
}
