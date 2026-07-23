package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.menu.LVElectricPumpMenu;
import com.skyeshade.skyent.content.menu.MediumTankMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MediumTankScreen extends AbstractContainerScreen<MediumTankMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;

    public static final int GUI_TEXTURE_WIDTH = 256;
    public static final int GUI_TEXTURE_HEIGHT = 256;

    public static final int FLUID_GAUGE_X = LVElectricPumpScreen.FLUID_GAUGE_X + (MediumTankMenu.DUMP_INPUT_SLOT_X - LVElectricPumpMenu.DUMP_INPUT_SLOT_X);
    public static final int FLUID_GAUGE_Y = LVElectricPumpScreen.FLUID_GAUGE_Y + (MediumTankMenu.DUMP_INPUT_SLOT_Y - LVElectricPumpMenu.DUMP_INPUT_SLOT_Y);
    public static final int FLUID_GAUGE_WIDTH = LVElectricPumpScreen.FLUID_GAUGE_WIDTH;
    public static final int FLUID_GAUGE_HEIGHT = LVElectricPumpScreen.FLUID_GAUGE_HEIGHT;
    public static final int FLUID_GAUGE_OVERLAY_U = LVElectricPumpScreen.FLUID_GAUGE_OVERLAY_U;
    public static final int FLUID_GAUGE_OVERLAY_V = LVElectricPumpScreen.FLUID_GAUGE_OVERLAY_V;
    public static final int FLUID_GAUGE_OVERLAY_WIDTH = LVElectricPumpScreen.FLUID_GAUGE_OVERLAY_WIDTH;
    public static final int FLUID_GAUGE_OVERLAY_HEIGHT = LVElectricPumpScreen.FLUID_GAUGE_OVERLAY_HEIGHT;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/medium_tank_gui.png"
    );

    public MediumTankScreen(MediumTankMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        imageWidth = GUI_WIDTH;
        imageHeight = GUI_HEIGHT;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        if (isHovering(FLUID_GAUGE_X, FLUID_GAUGE_Y, FLUID_GAUGE_WIDTH, FLUID_GAUGE_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, fluidTooltip(), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderFluidGauge(guiGraphics);
        renderFluidGaugeOverlay(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
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

    private List<Component> fluidTooltip() {
        return List.of(
                FluidGaugeRenderer.fluidDisplayName(menu.getFluid(), menu.getFluidAmount()),
                Component.literal(menu.getFluidAmount() + " / " + menu.getFluidCapacity() + " mB")
        );
    }
}
