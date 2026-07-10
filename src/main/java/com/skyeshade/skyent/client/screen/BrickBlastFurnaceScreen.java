package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.BrickBlastFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;

public class BrickBlastFurnaceScreen extends AbstractContainerScreen<BrickBlastFurnaceMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int TITLE_Y = 5;
    private static final int TITLE_COLOR = 0x404040;
    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;

    public static final int FUEL_GAUGE_X = 39;
    public static final int FUEL_GAUGE_Y = 17;
    public static final int FUEL_GAUGE_WIDTH = 27;
    public static final int FUEL_GAUGE_HEIGHT = 51;
    public static final int FUEL_GAUGE_OVERLAY_U = 229;
    public static final int FUEL_GAUGE_OVERLAY_V = 0;
    public static final int FUEL_GAUGE_OVERLAY_WIDTH = 27;
    public static final int FUEL_GAUGE_OVERLAY_HEIGHT = 51;
    public static final int FUEL_FILL_X = 39;
    public static final int FUEL_FILL_Y = 17;
    public static final int FUEL_FILL_WIDTH = 27;
    public static final int FUEL_FILL_HEIGHT = 52;

    public static final int FIRE_X = 84;
    public static final int FIRE_Y = 36;
    public static final int FIRE_U = 176;
    public static final int FIRE_V = 0;
    public static final int FIRE_WIDTH = 14;
    public static final int FIRE_HEIGHT = 14;

    public static final int PROGRESS_ARROW_X = 103;
    public static final int PROGRESS_ARROW_Y = 35;
    public static final int PROGRESS_ARROW_U = 177;
    public static final int PROGRESS_ARROW_V = 14;
    public static final int PROGRESS_ARROW_WIDTH = 22;
    public static final int PROGRESS_ARROW_HEIGHT = 16;

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/blast_furnace.png"
    );

    public BrickBlastFurnaceScreen(BrickBlastFurnaceMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderFuelGauge(guiGraphics);
        renderFuelGaugeOverlay(guiGraphics);
        renderFire(guiGraphics);
        renderProgressArrow(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int titleX = (imageWidth - font.width(title)) / 2;
        guiGraphics.drawString(font, title, titleX, TITLE_Y, TITLE_COLOR, false);
    }

    private void renderFuelGauge(GuiGraphics guiGraphics) {
        int fuelHeat = menu.getFuelHeat();
        if (fuelHeat <= 0) {
            return;
        }

        int maxFuelHeat = Math.max(1, menu.getMaxFuelHeat());
        int drawHeight = getFuelGaugeDrawHeight(fuelHeat, maxFuelHeat);

        FluidGaugeRenderer.renderMaskedTiledFluid(
                guiGraphics,
                Fluids.LAVA,
                drawHeight,
                FUEL_FILL_HEIGHT,
                leftPos + FUEL_FILL_X,
                topPos + FUEL_FILL_Y,
                FUEL_FILL_WIDTH,
                FUEL_FILL_HEIGHT
        );
    }

    private static int getFuelGaugeDrawHeight(int fuelHeat, int maxFuelHeat) {
        if (fuelHeat >= maxFuelHeat) {
            return FUEL_FILL_HEIGHT;
        }

        if (fuelHeat > 0) {
            int filledHeight = Mth.ceil(fuelHeat / (float) maxFuelHeat * FUEL_FILL_HEIGHT);
            return Mth.clamp(filledHeight, 1, FUEL_FILL_HEIGHT - 1);
        }

        return 0;
    }

    private void renderFuelGaugeOverlay(GuiGraphics guiGraphics) {
        guiGraphics.blit(
                TEXTURE,
                leftPos + FUEL_GAUGE_X,
                topPos + FUEL_GAUGE_Y,
                FUEL_GAUGE_OVERLAY_U,
                FUEL_GAUGE_OVERLAY_V,
                FUEL_GAUGE_OVERLAY_WIDTH,
                FUEL_GAUGE_OVERLAY_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderFire(GuiGraphics guiGraphics) {
        if (!menu.isActive()) {
            return;
        }

        guiGraphics.blit(
                TEXTURE,
                leftPos + FIRE_X,
                topPos + FIRE_Y,
                FIRE_U,
                FIRE_V,
                FIRE_WIDTH,
                FIRE_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderProgressArrow(GuiGraphics guiGraphics) {
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
                topPos + PROGRESS_ARROW_Y,
                PROGRESS_ARROW_U,
                PROGRESS_ARROW_V,
                filledWidth,
                PROGRESS_ARROW_HEIGHT,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

}
