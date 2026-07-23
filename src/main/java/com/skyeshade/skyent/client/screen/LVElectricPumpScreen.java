package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.LVElectricPumpMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LVElectricPumpScreen extends AbstractContainerScreen<LVElectricPumpMenu> {
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

    public static final int FLUID_GAUGE_X = 69;
    public static final int FLUID_GAUGE_Y = 9;
    public static final int FLUID_GAUGE_WIDTH = 37;
    public static final int FLUID_GAUGE_HEIGHT = 48;
    public static final int FLUID_GAUGE_OVERLAY_U = 208;
    public static final int FLUID_GAUGE_OVERLAY_V = 5;
    public static final int FLUID_GAUGE_OVERLAY_WIDTH = 37;
    public static final int FLUID_GAUGE_OVERLAY_HEIGHT = 48;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/lv_pump_gui.png"
    );

    public LVElectricPumpScreen(LVElectricPumpMenu menu, Inventory playerInventory, Component title) {
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
        } else if (isHovering(FLUID_GAUGE_X, FLUID_GAUGE_Y, FLUID_GAUGE_WIDTH, FLUID_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, fluidTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderFluidGauge(guiGraphics);
        renderFluidGaugeOverlay(guiGraphics);
        renderEnergyBar(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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
                Component.literal("Required: " + LVElectricPumpBlockEntity.REQUIRED_TIER.displayName()).withStyle(ChatFormatting.RED),
                Component.literal("Max input: " + LVElectricPumpBlockEntity.MAX_INPUT_AMPS + " A").withStyle(ChatFormatting.RED)
        );
    }

    private List<Component> fluidTooltip() {
        return List.of(
                FluidGaugeRenderer.fluidDisplayName(menu.getFluid(), menu.getFluidAmount()),
                Component.literal(menu.getFluidAmount() + " / " + menu.getFluidCapacity() + " mB")
        );
    }
}
