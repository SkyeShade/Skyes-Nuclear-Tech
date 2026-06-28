package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.CombustionGeneratorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public class CombustionGeneratorScreen extends AbstractContainerScreen<CombustionGeneratorMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int FLUID_TEXTURE_SIZE = 16;

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
        int stored = menu.getEnergyStored();
        if (stored <= 0) {
            return;
        }

        int filledHeight = stored * ENERGY_BAR_HEIGHT / menu.getMaxEnergyStored();
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
        int amount = menu.getWaterAmount();
        if (amount <= 0) {
            return;
        }

        int filledHeight = amount * WATER_BAR_HEIGHT / menu.getWaterCapacity();
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(Fluids.WATER);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(extensions.getStillTexture());
        int tint = extensions.getTintColor();
        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = ((tint >> 24) & 0xFF) / 255.0F;

        RenderSystem.setShaderColor(red, green, blue, alpha);
        tileFluidSprite(guiGraphics, sprite, leftPos + WATER_BAR_X, topPos + WATER_BAR_Y + WATER_BAR_HEIGHT - filledHeight, WATER_BAR_WIDTH, filledHeight);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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

    private void tileFluidSprite(GuiGraphics guiGraphics, TextureAtlasSprite sprite, int x, int y, int width, int height) {
        int atlasWidth = Math.round(sprite.contents().width() / (sprite.getU1() - sprite.getU0()));
        int atlasHeight = Math.round(sprite.contents().height() / (sprite.getV1() - sprite.getV0()));

        for (int tileY = 0; tileY < height; tileY += FLUID_TEXTURE_SIZE) {
            int tileHeight = Math.min(FLUID_TEXTURE_SIZE, height - tileY);
            for (int tileX = 0; tileX < width; tileX += FLUID_TEXTURE_SIZE) {
                int tileWidth = Math.min(FLUID_TEXTURE_SIZE, width - tileX);
                guiGraphics.blit(
                        sprite.atlasLocation(),
                        x + tileX,
                        y + tileY,
                        sprite.getX(),
                        sprite.getY() + FLUID_TEXTURE_SIZE - tileHeight,
                        tileWidth,
                        tileHeight,
                        atlasWidth,
                        atlasHeight
                );
            }
        }
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
                Component.literal(menu.getEnergyStored() + " / " + menu.getMaxEnergyStored() + " " + EnergyUnits.UNIT).withStyle(ChatFormatting.RED),
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
