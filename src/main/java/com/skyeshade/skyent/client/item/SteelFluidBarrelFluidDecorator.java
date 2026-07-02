package com.skyeshade.skyent.client.item;

import com.mojang.blaze3d.systems.RenderSystem;
import com.skyeshade.skyent.content.item.SteelFluidBarrelItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

public final class SteelFluidBarrelFluidDecorator implements IItemDecorator {
    private static final int WINDOW_X = 7;
    private static final int WINDOW_Y = 7;
    private static final int WINDOW_WIDTH = 2;
    private static final int WINDOW_HEIGHT = 6;

    @Override
    public boolean render(GuiGraphics guiGraphics, Font font, ItemStack stack, int xOffset, int yOffset) {
        FluidStack fluidStack = SteelFluidBarrelItem.getContainedFluid(stack);
        if (fluidStack.isEmpty() || fluidStack.getFluid() == Fluids.EMPTY) {
            return false;
        }

        int filledHeight = fluidStack.getAmount() * WINDOW_HEIGHT / SteelFluidBarrelItem.CAPACITY_MB;
        if (filledHeight <= 0) {
            filledHeight = 1;
        } else if (filledHeight > WINDOW_HEIGHT) {
            filledHeight = WINDOW_HEIGHT;
        }

        Fluid fluid = fluidStack.getFluid();
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

        int atlasWidth = Math.round(sprite.contents().width() / (sprite.getU1() - sprite.getU0()));
        int atlasHeight = Math.round(sprite.contents().height() / (sprite.getV1() - sprite.getV0()));
        int x = xOffset + WINDOW_X;
        int y = yOffset + WINDOW_Y + WINDOW_HEIGHT - filledHeight;
        int spriteU = sprite.getX();
        int spriteV = sprite.getY() + WINDOW_HEIGHT - filledHeight;

        RenderSystem.setShaderColor(red, green, blue, alpha);
        guiGraphics.blit(
                sprite.atlasLocation(),
                x,
                y,
                spriteU,
                spriteV,
                WINDOW_WIDTH,
                filledHeight,
                atlasWidth,
                atlasHeight
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        return false;
    }
}
