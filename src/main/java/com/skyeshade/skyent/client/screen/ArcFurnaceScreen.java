package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.ArcFurnaceMenu;
import com.skyeshade.skyent.content.recipe.ArcFurnaceMode;
import com.skyeshade.skyent.network.ToggleArcFurnaceModePayload;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class ArcFurnaceScreen extends AbstractContainerScreen<ArcFurnaceMenu> {
    private static final int BASE_GUI_WIDTH = 176;
    private static final int GUI_WIDTH = 204;
    private static final int GUI_HEIGHT = 207;
    private static final int GUI_TEXTURE_WIDTH = 256;
    private static final int GUI_TEXTURE_HEIGHT = 256;
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_X = 8;
    private static final int INVENTORY_LABEL_Y = 114;
    private static final int TITLE_COLOR = 0x404040;
    private static final int PROGRESS_X = 78;
    private static final int PROGRESS_Y = 56;
    private static final int PROGRESS_U = 224;
    private static final int PROGRESS_V = 22;
    private static final int PROGRESS_WIDTH = 26;
    private static final int PROGRESS_HEIGHT = 35;
    private static final int ENERGY_X = 183;
    private static final int ENERGY_Y = 17;
    private static final int ENERGY_U = 204;
    private static final int ENERGY_V = 17;
    private static final int ENERGY_WIDTH = 16;
    private static final int ENERGY_HEIGHT = 57;
    private static final int MODE_BUTTON_X = 139;
    private static final int MODE_BUTTON_Y = 10;
    private static final int MODE_BUTTON_SIZE = 26;
    private static final int MODE_ICON_X = 144;
    private static final int MODE_ICON_Y = 15;
    private static final int MODE_ICON_SIZE = 16;
    private static final int ALLOYING_ICON_U = 229;
    private static final int ALLOYING_ICON_V = 59;
    private static final int SMELTING_ICON_U = 229;
    private static final int SMELTING_ICON_V = 78;

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/arc_furnace.png"
    );

    public ArcFurnaceScreen(ArcFurnaceMenu menu, Inventory playerInventory, Component title) {
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

        if (isHovering(ENERGY_X, ENERGY_Y, ENERGY_WIDTH, ENERGY_HEIGHT, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, energyTooltip(), mouseX, mouseY);
        } else if (isPointIn(MODE_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, List.of(modeTooltip()), mouseX, mouseY);
        } else if (isPointIn(ArcFurnaceMenu.POWER_ITEM_SLOT_X, ArcFurnaceMenu.POWER_ITEM_SLOT_Y, 16, 16, mouseX, mouseY)) {
            guiGraphics.renderComponentTooltip(font, List.of(Component.literal("Battery slot TODO").withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, GUI_WIDTH, GUI_HEIGHT, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
        renderProgress(guiGraphics);
        renderEnergy(guiGraphics);
        renderModeIcon(guiGraphics);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, TITLE_X, TITLE_Y, TITLE_COLOR, false);
        guiGraphics.drawString(font, playerInventoryTitle, INVENTORY_LABEL_X, INVENTORY_LABEL_Y, TITLE_COLOR, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isPointIn(MODE_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE, mouseX, mouseY)) {
            if (minecraft != null) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            PacketDistributor.sendToServer(new ToggleArcFurnaceModePayload(menu.getBlockPos()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderProgress(GuiGraphics guiGraphics) {
        int maxProgress = menu.getMaxProgress();
        if (maxProgress <= 0) {
            return;
        }
        int filledWidth = Math.min(PROGRESS_WIDTH, menu.getProgress() * PROGRESS_WIDTH / maxProgress);
        if (filledWidth <= 0) {
            return;
        }
        guiGraphics.blit(TEXTURE, leftPos + PROGRESS_X, topPos + PROGRESS_Y, PROGRESS_U, PROGRESS_V, filledWidth, PROGRESS_HEIGHT, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
    }

    private void renderEnergy(GuiGraphics guiGraphics) {
        int stored = menu.getEnergyStoredRJ();
        if (stored <= 0) {
            return;
        }
        int filledHeight = Math.min(ENERGY_HEIGHT, stored * ENERGY_HEIGHT / Math.max(1, menu.getMaxEnergyStoredRJ()));
        if (filledHeight <= 0) {
            return;
        }
        guiGraphics.blit(
                TEXTURE,
                leftPos + ENERGY_X,
                topPos + ENERGY_Y + ENERGY_HEIGHT - filledHeight,
                ENERGY_U,
                ENERGY_V + ENERGY_HEIGHT - filledHeight,
                ENERGY_WIDTH,
                filledHeight,
                GUI_TEXTURE_WIDTH,
                GUI_TEXTURE_HEIGHT
        );
    }

    private void renderModeIcon(GuiGraphics guiGraphics) {
        ArcFurnaceMode mode = ArcFurnaceMode.byCode(menu.getModeCode());
        int u = mode == ArcFurnaceMode.SMELTING ? SMELTING_ICON_U : ALLOYING_ICON_U;
        int v = mode == ArcFurnaceMode.SMELTING ? SMELTING_ICON_V : ALLOYING_ICON_V;
        guiGraphics.blit(TEXTURE, leftPos + MODE_ICON_X, topPos + MODE_ICON_Y, u, v, MODE_ICON_SIZE, MODE_ICON_SIZE, GUI_TEXTURE_WIDTH, GUI_TEXTURE_HEIGHT);
    }

    private Component modeTooltip() {
        return ArcFurnaceMode.byCode(menu.getModeCode()) == ArcFurnaceMode.ALLOYING
                ? Component.literal("Alloying")
                : Component.literal("Smelting");
    }

    private List<Component> energyTooltip() {
        return List.of(
                Component.literal(menu.getEnergyStoredRJ() + " / " + menu.getMaxEnergyStoredRJ() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
                Component.literal("Usage: " + menu.getCurrentEnergyUsage() + " " + EnergyUnits.UNIT_PER_TICK).withStyle(ChatFormatting.RED),
                Component.literal("Required: " + com.skyeshade.skyent.content.blockentity.ArcFurnaceBlockEntity.REQUIRED_TIER.displayName()).withStyle(ChatFormatting.RED)
        );
    }

    private boolean isPointIn(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width
                && mouseY >= topPos + y && mouseY < topPos + y + height;
    }
}
