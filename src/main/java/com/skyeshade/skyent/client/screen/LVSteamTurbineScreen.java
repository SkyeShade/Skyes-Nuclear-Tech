package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.LVSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.LVSteamTurbineMenu;
import com.skyeshade.skyent.registry.ModFluids;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class LVSteamTurbineScreen extends AbstractContainerScreen<LVSteamTurbineMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int GUI_TEXTURE_WIDTH = 256;
    private static final int GUI_TEXTURE_HEIGHT = 256;

    private static final int ENERGY_BAR_X = LVElectricPumpScreen.ENERGY_BAR_X;
    private static final int ENERGY_BAR_Y = LVElectricPumpScreen.ENERGY_BAR_Y;
    private static final int ENERGY_BAR_WIDTH = LVElectricPumpScreen.ENERGY_BAR_WIDTH;
    private static final int ENERGY_BAR_HEIGHT = LVElectricPumpScreen.ENERGY_BAR_HEIGHT;
    private static final int ENERGY_BAR_U = LVElectricPumpScreen.ENERGY_BAR_U;
    private static final int ENERGY_BAR_V = LVElectricPumpScreen.ENERGY_BAR_V;

    private static final int STEAM_GAUGE_X = 69;
    private static final int STEAM_GAUGE_Y = 9;
    private static final int STEAM_GAUGE_WIDTH = 37;
    private static final int STEAM_GAUGE_HEIGHT = 48;
    private static final int STEAM_GAUGE_OVERLAY_U = 208;
    private static final int STEAM_GAUGE_OVERLAY_V = 5;
    private static final int STEAM_GAUGE_OVERLAY_WIDTH = 37;
    private static final int STEAM_GAUGE_OVERLAY_HEIGHT = 48;

    private static final int RPM_GAUGE_X = 118;
    private static final int RPM_GAUGE_Y = 21;
    private static final int RPM_GAUGE_WIDTH = 23;
    private static final int RPM_GAUGE_HEIGHT = 24;
    private static final int RPM_GAUGE_OVERLAY_U = 187;
    private static final int RPM_GAUGE_OVERLAY_V = 66;
    private static final int RPM_GAUGE_OVERLAY_WIDTH = 23;
    private static final int RPM_GAUGE_OVERLAY_HEIGHT = 24;
    private static final int RPM_GAUGE_FILL_U = 187;
    private static final int RPM_GAUGE_FILL_V = 66;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/lv_steam_turbine.png"
    );

    public LVSteamTurbineScreen(LVSteamTurbineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (isHovering(STEAM_GAUGE_X, STEAM_GAUGE_Y, STEAM_GAUGE_WIDTH, STEAM_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, steamTooltip(), mouseX, mouseY);
        } else if (isHovering(ENERGY_BAR_X, ENERGY_BAR_Y, ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, energyTooltip(), mouseX, mouseY);
        } else if (isHovering(RPM_GAUGE_X, RPM_GAUGE_Y, RPM_GAUGE_WIDTH, RPM_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, rpmTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderSteamGauge(guiGraphics);
        renderSteamGaugeOverlay(guiGraphics);
        renderEnergyBar(guiGraphics);
        renderRpmGauge(guiGraphics);

    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderSteamGauge(GuiGraphics guiGraphics) {
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                ModFluids.STEAM.get(),
                menu.getSteamAmount(),
                menu.getSteamCapacity(),
                leftPos + STEAM_GAUGE_X,
                topPos + STEAM_GAUGE_Y,
                STEAM_GAUGE_WIDTH,
                STEAM_GAUGE_HEIGHT
        );
    }

    private void renderSteamGaugeOverlay(GuiGraphics guiGraphics) {
        guiGraphics.blit(
                TEXTURE,
                leftPos + STEAM_GAUGE_X,
                topPos + STEAM_GAUGE_Y,
                STEAM_GAUGE_OVERLAY_U,
                STEAM_GAUGE_OVERLAY_V,
                STEAM_GAUGE_OVERLAY_WIDTH,
                STEAM_GAUGE_OVERLAY_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderEnergyBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }

        int filledHeight = stored * ENERGY_BAR_HEIGHT / Math.max(1, menu.getMaxEnergyStoredRJ());
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

    private void renderRpmGauge(GuiGraphics guiGraphics) {
        int maxRpm = Math.max(1, menu.getMaxRpm());
        int rpm = Math.max(0, Math.min(menu.getRpm(), maxRpm));
        int filledHeight = Math.max(0, Math.min(RPM_GAUGE_HEIGHT, rpm * RPM_GAUGE_HEIGHT / maxRpm));
        if (filledHeight <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                leftPos + RPM_GAUGE_X,
                topPos + RPM_GAUGE_Y + RPM_GAUGE_HEIGHT - filledHeight,
                RPM_GAUGE_FILL_U,
                RPM_GAUGE_FILL_V + RPM_GAUGE_HEIGHT - filledHeight,
                RPM_GAUGE_WIDTH,
                filledHeight,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }



    private List<Component> steamTooltip() {
        return List.of(
                Component.literal("Steam"),
                Component.literal(menu.getSteamAmount() + " / " + menu.getSteamCapacity() + " mB")
        );
    }

    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStoredRJ() + " / " + menu.getMaxEnergyStoredRJ() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
                Component.literal("Output: " + menu.getCurrentOutput() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED),
                Component.literal("Tier: " + LVSteamTurbineBlockEntity.OUTPUT_TIER.displayName()).withStyle(ChatFormatting.RED),
                Component.literal("Max output: " + LVSteamTurbineBlockEntity.MAX_OUTPUT_CURRENT_AMPS + " A").withStyle(ChatFormatting.RED)
        );
    }

    private List<Component> rpmTooltip() {
        return List.of(
                Component.literal("RPM: " + menu.getRpm() + " / " + menu.getMaxRpm()).withStyle(ChatFormatting.AQUA),
                Component.literal("Steam: " + menu.getCurrentSteamUsage() + " mB/t").withStyle(ChatFormatting.AQUA)
        );
    }
}
