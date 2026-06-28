package com.skyeshade.skyent.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.menu.LVElectricPumpMenu;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public class LVElectricPumpScreen extends AbstractContainerScreen<LVElectricPumpMenu> {
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 166;
    private static final int FLUID_TEXTURE_SIZE = 16;

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
        int amount = menu.getFluidAmount();
        Fluid fluid = menu.getFluid();
        if (amount <= 0 || fluid == Fluids.EMPTY) {
            return;
        }

        int capacity = Math.max(1, menu.getFluidCapacity());
        int filledHeight = amount * FLUID_GAUGE_HEIGHT / capacity;
        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(extensions.getStillTexture());
        int tint = extensions.getTintColor();
        float red = ((tint >> 16) & 0xFF) / 255.0F;
        float green = ((tint >> 8) & 0xFF) / 255.0F;
        float blue = (tint & 0xFF) / 255.0F;
        float alpha = ((tint >> 24) & 0xFF) / 255.0F;
        if (alpha <= 0.0F) {
            alpha = 1.0F;
        }

        RenderSystem.setShaderColor(red, green, blue, alpha);
        tileFluidSprite(guiGraphics, sprite, leftPos + FLUID_GAUGE_X, topPos + FLUID_GAUGE_Y + FLUID_GAUGE_HEIGHT - filledHeight, FLUID_GAUGE_WIDTH, filledHeight);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
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
                Component.literal(fluidName(menu.getFluid())),
                Component.literal(menu.getFluidAmount() + " / " + menu.getFluidCapacity() + " mB")
        );
    }

    private static String fluidName(Fluid fluid) {
        if (fluid == Fluids.EMPTY) {
            return "Empty";
        }

        ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
        return key == null ? "Fluid" : key.toString();
    }
}
