package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.MVInlinePumpBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.MVInlinePumpMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MVInlinePumpScreen extends AbstractContainerScreen<MVInlinePumpMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;

    public static final int FLUID_GAUGE_X = MediumTankScreen.FLUID_GAUGE_X;
    public static final int FLUID_GAUGE_Y = MediumTankScreen.FLUID_GAUGE_Y;
    public static final int FLUID_GAUGE_WIDTH = MediumTankScreen.FLUID_GAUGE_WIDTH;
    public static final int FLUID_GAUGE_HEIGHT = MediumTankScreen.FLUID_GAUGE_HEIGHT;
    public static final int FLUID_GAUGE_OVERLAY_U = MediumTankScreen.FLUID_GAUGE_OVERLAY_U;
    public static final int FLUID_GAUGE_OVERLAY_V = MediumTankScreen.FLUID_GAUGE_OVERLAY_V;
    public static final int FLUID_GAUGE_OVERLAY_WIDTH = MediumTankScreen.FLUID_GAUGE_OVERLAY_WIDTH;
    public static final int FLUID_GAUGE_OVERLAY_HEIGHT = MediumTankScreen.FLUID_GAUGE_OVERLAY_HEIGHT;

    private static final int POWER_BAR_X = 155;
    private static final int POWER_BAR_Y = 14;
    private static final int POWER_BAR_WIDTH = 16;
    private static final int POWER_BAR_HEIGHT = 57;
    private static final int POWER_BAR_U = 176;
    private static final int POWER_BAR_V = 14;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/inline_pump.png"
    );

    public MVInlinePumpScreen(MVInlinePumpMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (isHovering(POWER_BAR_X, POWER_BAR_Y, POWER_BAR_WIDTH, POWER_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, energyTooltip(), mouseX, mouseY);
        } else if (isHovering(FLUID_GAUGE_X, FLUID_GAUGE_Y, FLUID_GAUGE_WIDTH, FLUID_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, fluidTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderFluidGauge(guiGraphics);
        renderFluidGaugeOverlay(guiGraphics);
        renderPowerBar(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, 8, 6, 0x404040, false);
    }

    private void renderPowerBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }

        int maxEnergy = Math.max(1, menu.getMaxEnergyStoredRJ());
        int filledHeight = stored * POWER_BAR_HEIGHT / maxEnergy;
        guiGraphics.blit(
                TEXTURE,
                leftPos + POWER_BAR_X,
                topPos + POWER_BAR_Y + POWER_BAR_HEIGHT - filledHeight,
                POWER_BAR_U,
                POWER_BAR_V + POWER_BAR_HEIGHT - filledHeight,
                POWER_BAR_WIDTH,
                filledHeight,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderFluidGauge(GuiGraphics guiGraphics) {
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                menu.getFluid(),
                menu.getFluidAmount(),
                menu.getFluidCapacity(),
                leftPos + FLUID_GAUGE_X,
                topPos + FLUID_GAUGE_Y,
                FLUID_GAUGE_WIDTH,
                FLUID_GAUGE_HEIGHT
        );
    }

    private void renderFluidGaugeOverlay(GuiGraphics guiGraphics) {
        guiGraphics.blit(
                TEXTURE,
                leftPos + FLUID_GAUGE_X,
                topPos + FLUID_GAUGE_Y,
                FLUID_GAUGE_OVERLAY_U,
                FLUID_GAUGE_OVERLAY_V,
                FLUID_GAUGE_OVERLAY_WIDTH,
                FLUID_GAUGE_OVERLAY_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStoredRJ() + " / " + menu.getMaxEnergyStoredRJ() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
                Component.literal("Usage: " + menu.getCurrentEnergyUsage() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED),
                Component.literal("Required: " + MVInlinePumpBlockEntity.REQUIRED_TIER.displayName()).withStyle(ChatFormatting.RED),
                Component.literal("Max input: " + MVInlinePumpBlockEntity.MAX_INPUT_AMPS + " A").withStyle(ChatFormatting.RED)
        );
    }

    private List<Component> fluidTooltip() {
        return List.of(
                FluidGaugeRenderer.fluidDisplayName(menu.getFluid(), menu.getFluidAmount()),
                Component.literal("Transfer: " + menu.getCurrentTransferRateMbPerSecond() + " mB/s"),
                Component.literal(menu.getFluidAmount() + " / " + menu.getFluidCapacity() + " mB")
        );
    }
}
