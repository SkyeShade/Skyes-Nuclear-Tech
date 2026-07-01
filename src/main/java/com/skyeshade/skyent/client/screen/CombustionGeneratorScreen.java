package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.menu.CombustionGeneratorMenu;
import com.skyeshade.skyent.registry.ModFluids;
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

    // Shared legacy constants used by other machine screens.
    public static final int ENERGY_BAR_X = 155;
    public static final int ENERGY_BAR_Y = 5;
    public static final int ENERGY_BAR_WIDTH = 16;
    public static final int ENERGY_BAR_HEIGHT = 57;
    public static final int ENERGY_BAR_U = 191;
    public static final int ENERGY_BAR_V = 5;

    public static final int INPUT_GAUGE_X = 49;
    public static final int INPUT_GAUGE_Y = 9;
    public static final int OUTPUT_GAUGE_X = 111;
    public static final int OUTPUT_GAUGE_Y = 9;
    public static final int LIQUID_GAUGE_WIDTH = 16;
    public static final int LIQUID_GAUGE_HEIGHT = 48;
    public static final int LIQUID_TANK_OVERLAY_U = 208;
    public static final int LIQUID_TANK_OVERLAY_V = 5;
    public static final int LIQUID_TANK_OVERLAY_WIDTH = 16;
    public static final int LIQUID_TANK_OVERLAY_HEIGHT = 48;

    public static final int FLAME_X = 81;
    public static final int FLAME_Y = 25;
    public static final int FLAME_WIDTH = 14;
    public static final int FLAME_HEIGHT = 14;
    public static final int FLAME_U = 176;
    public static final int FLAME_V = 0;

    public static final int HEAT_BAR_X = 85;
    public static final int HEAT_BAR_Y = 8;
    public static final int HEAT_BAR_WIDTH = 6;
    public static final int HEAT_BAR_HEIGHT = 16;
    public static final int HEAT_BAR_U = 180;
    public static final int HEAT_BAR_V = 15;

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

        if (isHovering(INPUT_GAUGE_X, INPUT_GAUGE_Y, LIQUID_GAUGE_WIDTH, LIQUID_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, waterTooltip(), mouseX, mouseY);
        } else if (isHovering(OUTPUT_GAUGE_X, OUTPUT_GAUGE_Y, LIQUID_GAUGE_WIDTH, LIQUID_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, steamTooltip(), mouseX, mouseY);
        } else if (isHovering(HEAT_BAR_X, HEAT_BAR_Y, HEAT_BAR_WIDTH, HEAT_BAR_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, heatTooltip(), mouseX, mouseY);
        } else if (isHovering(FLAME_X, FLAME_Y, FLAME_WIDTH, FLAME_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, flameTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderInputGauge(guiGraphics);
        renderOutputGauge(guiGraphics);
        renderGaugeOverlay(guiGraphics, INPUT_GAUGE_X, INPUT_GAUGE_Y);
        renderGaugeOverlay(guiGraphics, OUTPUT_GAUGE_X, OUTPUT_GAUGE_Y);
        renderHeatBar(guiGraphics);
        renderFlame(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    private void renderInputGauge(GuiGraphics guiGraphics) {
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                Fluids.WATER,
                menu.getWaterAmount(),
                menu.getWaterCapacity(),
                leftPos + INPUT_GAUGE_X,
                topPos + INPUT_GAUGE_Y,
                LIQUID_GAUGE_WIDTH,
                LIQUID_GAUGE_HEIGHT
        );
    }

    private void renderOutputGauge(GuiGraphics guiGraphics) {
        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                ModFluids.STEAM.get(),
                menu.getSteamAmount(),
                menu.getSteamCapacity(),
                leftPos + OUTPUT_GAUGE_X,
                topPos + OUTPUT_GAUGE_Y,
                LIQUID_GAUGE_WIDTH,
                LIQUID_GAUGE_HEIGHT
        );
    }

    private void renderGaugeOverlay(GuiGraphics guiGraphics, int x, int y) {
        guiGraphics.blit(
                TEXTURE,
                leftPos + x,
                topPos + y,
                LIQUID_TANK_OVERLAY_U,
                LIQUID_TANK_OVERLAY_V,
                LIQUID_TANK_OVERLAY_WIDTH,
                LIQUID_TANK_OVERLAY_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderHeatBar(GuiGraphics guiGraphics) {
        double progress = (menu.getTemperatureC() - CombustionGeneratorBlockEntity.MIN_TEMPERATURE_C)
                / (CombustionGeneratorBlockEntity.MAX_TEMPERATURE_C - CombustionGeneratorBlockEntity.MIN_TEMPERATURE_C);
        int filledHeight = clampHeight(progress, HEAT_BAR_HEIGHT);
        if (filledHeight <= 0) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                leftPos + HEAT_BAR_X,
                topPos + HEAT_BAR_Y + HEAT_BAR_HEIGHT - filledHeight,
                HEAT_BAR_U,
                HEAT_BAR_V + HEAT_BAR_HEIGHT - filledHeight,
                HEAT_BAR_WIDTH,
                filledHeight,
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

        int flameHeight = Math.max(1, menu.getBurnTime() * FLAME_HEIGHT / burnTimeTotal);
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

    private static int clampHeight(double progress, int height) {
        if (progress <= 0.0D) {
            return 0;
        }
        if (progress >= 1.0D) {
            return height;
        }
        return Math.max(1, (int) Math.floor(progress * height));
    }

    private List<Component> waterTooltip() {
        return List.of(
                Component.literal("Water"),
                Component.literal(menu.getWaterAmount() + " / " + menu.getWaterCapacity() + " mB")
        );
    }

    private List<Component> steamTooltip() {
        return List.of(
                Component.literal("Steam"),
                Component.literal(menu.getSteamAmount() + " / " + menu.getSteamCapacity() + " mB")
        );
    }

    private List<Component> heatTooltip() {
        return List.of(Component.literal("Temperature: " + Math.round(menu.getTemperatureC()) + "\u00B0C").withStyle(ChatFormatting.GOLD));
    }

    private List<Component> flameTooltip() {
        return List.of(Component.literal("Burn time: " + menu.getBurnTime() + " ticks").withStyle(ChatFormatting.GOLD));
    }
}
