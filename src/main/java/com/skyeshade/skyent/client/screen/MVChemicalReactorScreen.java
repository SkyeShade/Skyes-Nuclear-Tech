package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.MVChemicalReactorBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.MVChemicalReactorMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MVChemicalReactorScreen extends AbstractContainerScreen<MVChemicalReactorMenu> {
    private static final int GUI_WIDTH = 224;
    private static final int GUI_HEIGHT = 197;
    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;
    private static final int GUI_BACKGROUND_U = 32;
    private static final int GUI_BACKGROUND_V = 0;
    private static final int GUI_BACKGROUND_X = 32;
    private static final int GUI_BACKGROUND_WIDTH = 176;
    private static final int CONTENT_Y_OFFSET = 3;
    private static final int TITLE_X = 40;
    private static final int TITLE_Y = 5;
    private static final int INVENTORY_LABEL_X = 40;
    private static final int INVENTORY_LABEL_Y = 101 + CONTENT_Y_OFFSET;
    private static final int TITLE_COLOR = 0x404040;

    private static final int INPUT_GAUGE_1_X = 41;
    private static final int INPUT_GAUGE_2_X = 78;
    private static final int OUTPUT_GAUGE_1_X = 124;
    private static final int OUTPUT_GAUGE_2_X = 161;
    private static final int GAUGE_Y = 14 + CONTENT_Y_OFFSET;
    private static final int GAUGE_WIDTH = 13;
    private static final int GAUGE_HEIGHT = 52;
    private static final int GAUGE_OVERLAY_U = 232;
    private static final int GAUGE_OVERLAY_V = 14;

    private static final int ENERGY_BAR_X = 186;
    private static final int ENERGY_BAR_Y = 8 + CONTENT_Y_OFFSET;
    private static final int ENERGY_BAR_WIDTH = 16;
    private static final int ENERGY_BAR_HEIGHT = 57;
    private static final int ENERGY_BAR_U = 211;
    private static final int ENERGY_BAR_V = 7;

    private static final int PROGRESS_ARROW_X = 96;
    private static final int PROGRESS_ARROW_1_Y = 15 + CONTENT_Y_OFFSET;
    private static final int PROGRESS_ARROW_2_Y = 70 + CONTENT_Y_OFFSET;
    private static final int PROGRESS_ARROW_U = 211;
    private static final int PROGRESS_ARROW_V = 70;
    private static final int PROGRESS_ARROW_WIDTH = 22;
    private static final int PROGRESS_ARROW_HEIGHT = 16;

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/chemical_reactor.png"
    );

    public MVChemicalReactorScreen(MVChemicalReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
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
        guiGraphics.blit(TEXTURE, leftPos + GUI_BACKGROUND_X, topPos, GUI_BACKGROUND_U, GUI_BACKGROUND_V, GUI_BACKGROUND_WIDTH, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderTank(guiGraphics, 0, INPUT_GAUGE_1_X);
        renderTank(guiGraphics, 1, INPUT_GAUGE_2_X);
        renderTank(guiGraphics, 2, OUTPUT_GAUGE_1_X);
        renderTank(guiGraphics, 3, OUTPUT_GAUGE_2_X);
        renderEnergyBar(guiGraphics);
        renderProgressArrow(guiGraphics, PROGRESS_ARROW_1_Y);
        renderProgressArrow(guiGraphics, PROGRESS_ARROW_2_Y);
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

    private void renderEnergyBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }

        int maxEnergy = Math.max(1, menu.getMaxEnergyStoredRJ());
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

    private void renderProgressArrow(GuiGraphics guiGraphics, int y) {
        int maxProgress = menu.getMaxProgress();
        if (maxProgress <= 0) {
            return;
        }

        int filledWidth = menu.getProgress() * PROGRESS_ARROW_WIDTH / maxProgress;
        if (filledWidth <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                leftPos + PROGRESS_ARROW_X,
                topPos + y,
                PROGRESS_ARROW_U,
                PROGRESS_ARROW_V,
                filledWidth,
                PROGRESS_ARROW_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private int hoveredTank(int mouseX, int mouseY) {
        if (isHovering(INPUT_GAUGE_1_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            return 0;
        }
        if (isHovering(INPUT_GAUGE_2_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            return 1;
        }
        if (isHovering(OUTPUT_GAUGE_1_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY)) {
            return 2;
        }
        return isHovering(OUTPUT_GAUGE_2_X, GAUGE_Y, GAUGE_WIDTH, GAUGE_HEIGHT, mouseX, mouseY) ? 3 : -1;
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
                Component.literal("Usage: " + menu.getCurrentEnergyUsage() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED),
                Component.literal("Required: " + MVChemicalReactorBlockEntity.REQUIRED_TIER.displayName()).withStyle(ChatFormatting.RED),
                Component.literal("Current: " + MVChemicalReactorBlockEntity.RUNNING_CURRENT_AMPS + " A").withStyle(ChatFormatting.RED)
        );
    }
}
