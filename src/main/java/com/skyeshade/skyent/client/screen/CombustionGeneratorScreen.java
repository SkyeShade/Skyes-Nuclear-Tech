package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.CombustionGeneratorMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;

public class CombustionGeneratorScreen extends AbstractContainerScreen<CombustionGeneratorMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;

    public static final int ENERGY_BAR_X = 155;
    public static final int ENERGY_BAR_Y = 5;
    public static final int ENERGY_BAR_WIDTH = 16;
    public static final int ENERGY_BAR_HEIGHT = 57;
    public static final int ENERGY_BAR_U = 191;
    public static final int ENERGY_BAR_V = 5;

    public static final int WATER_BAR_X = 26;
    public static final int WATER_BAR_Y = 9;
    public static final int WATER_BAR_WIDTH = 16;
    public static final int WATER_BAR_HEIGHT = 48;
    public static final int WATER_TANK_OVERLAY_U = 208;
    public static final int WATER_TANK_OVERLAY_V = 5;
    public static final int WATER_TANK_OVERLAY_WIDTH = 16;
    public static final int WATER_TANK_OVERLAY_HEIGHT = 48;

    public static final int FLAME_X = 81;
    public static final int FLAME_Y = 19;
    public static final int FLAME_WIDTH = 13;
    public static final int FLAME_HEIGHT = 13;
    public static final int FLAME_U = 176;
    public static final int FLAME_V = 0;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/combustion_generator.png"
    );

    public CombustionGeneratorScreen(CombustionGeneratorMenu menu, Inventory playerInventory, Component title) {
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
        } else if (isHovering(WATER_BAR_X, WATER_BAR_Y, WATER_BAR_WIDTH, WATER_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, waterTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderWaterBar(guiGraphics);
        renderWaterTankOverlay(guiGraphics);
        renderEnergyBar(guiGraphics);
        renderFlame(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderEnergyBar(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }

        int filledHeight = stored * ENERGY_BAR_HEIGHT / menu.getMaxEnergyStoredRJ();
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

    private void renderWaterBar(GuiGraphics guiGraphics) {
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                Fluids.WATER,
                menu.getWaterAmount(),
                menu.getWaterCapacity(),
                leftPos + WATER_BAR_X,
                topPos + WATER_BAR_Y,
                WATER_BAR_WIDTH,
                WATER_BAR_HEIGHT
        );
    }

    private void renderWaterTankOverlay(GuiGraphics guiGraphics) {
        guiGraphics.blit(
                TEXTURE,
                leftPos + WATER_BAR_X,
                topPos + WATER_BAR_Y,
                WATER_TANK_OVERLAY_U,
                WATER_TANK_OVERLAY_V,
                WATER_TANK_OVERLAY_WIDTH,
                WATER_TANK_OVERLAY_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderFlame(GuiGraphics guiGraphics) {
        if (!menu.isBurning()) {
            return;
        }

        int burnTimeTotal = menu.getBurnTimeTotal();
        if (burnTimeTotal <= 0) {
            return;
        }

        int flameHeight = menu.getBurnTime() * FLAME_HEIGHT / burnTimeTotal;
        guiGraphics.blit(
                TEXTURE,
                leftPos + FLAME_X,
                topPos + FLAME_Y + FLAME_HEIGHT - flameHeight,
                FLAME_U,
                FLAME_V + FLAME_HEIGHT - flameHeight,
                FLAME_WIDTH,
                flameHeight,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }


    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStoredRJ() + " / " + menu.getMaxEnergyStoredRJ() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
                Component.literal(menu.getCurrentGenerationRate() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED),
                Component.literal("Output: " + CombustionGeneratorBlockEntity.OUTPUT_TIER.displayName()).withStyle(ChatFormatting.RED),
                Component.literal("Max current: " + CombustionGeneratorBlockEntity.MAX_OUTPUT_CURRENT_AMPS + " A").withStyle(ChatFormatting.RED)
        );
    }

    private List<Component> waterTooltip() {
        return List.of(
                Component.literal("Water"),
                Component.literal(menu.getWaterAmount() + " / " + menu.getWaterCapacity() + " mB")
        );
    }
}
