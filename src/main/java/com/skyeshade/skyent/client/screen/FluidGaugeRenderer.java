package com.skyeshade.skyent.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

public final class FluidGaugeRenderer {
    private static final int FLUID_TEXTURE_SIZE = 16;

    private FluidGaugeRenderer() {
    }

    public static void renderMaskedTiledFluid(
            GuiGraphics guiGraphics,
            Fluid fluid,
            int amount,
            int capacity,
            int x,
            int y,
            int width,
            int height
    ) {
        if (amount <= 0 || capacity <= 0 || fluid == Fluids.EMPTY) {
            return;
        }

        int filledHeight = amount * height / Math.max(1, capacity);
        if (filledHeight <= 0) {
            return;
        }
        if (filledHeight > height) {
            filledHeight = height;
        }

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

        int visibleY = y + height - filledHeight;
        RenderSystem.setShaderColor(red, green, blue, alpha);
        tileFluidSpriteMasked(
                guiGraphics,
                sprite,
                x,
                y,
                width,
                height,
                x,
                visibleY,
                width,
                filledHeight
        );
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void tileFluidSpriteMasked(
            GuiGraphics guiGraphics,
            TextureAtlasSprite sprite,
            int tileOriginX,
            int tileOriginY,
            int tiledWidth,
            int tiledHeight,
            int maskX,
            int maskY,
            int maskWidth,
            int maskHeight
    ) {
        int atlasWidth = Math.round(sprite.contents().width() / (sprite.getU1() - sprite.getU0()));
        int atlasHeight = Math.round(sprite.contents().height() / (sprite.getV1() - sprite.getV0()));

        int maskRight = maskX + maskWidth;
        int maskBottom = maskY + maskHeight;

        for (int tileY = 0; tileY < tiledHeight; tileY += FLUID_TEXTURE_SIZE) {
            int tileHeight = Math.min(FLUID_TEXTURE_SIZE, tiledHeight - tileY);
            int tileAbsY = tileOriginY + tileY;

            for (int tileX = 0; tileX < tiledWidth; tileX += FLUID_TEXTURE_SIZE) {
                int tileWidth = Math.min(FLUID_TEXTURE_SIZE, tiledWidth - tileX);
                int tileAbsX = tileOriginX + tileX;

                int drawX = Math.max(tileAbsX, maskX);
                int drawY = Math.max(tileAbsY, maskY);
                int drawRight = Math.min(tileAbsX + tileWidth, maskRight);
                int drawBottom = Math.min(tileAbsY + tileHeight, maskBottom);

                if (drawRight <= drawX || drawBottom <= drawY) {
                    continue;
                }

                int drawWidth = drawRight - drawX;
                int drawHeight = drawBottom - drawY;
                int spriteU = sprite.getX() + (drawX - tileAbsX);
                int spriteV = sprite.getY() + (drawY - tileAbsY);

                guiGraphics.blit(
                        sprite.atlasLocation(),
                        drawX,
                        drawY,
                        spriteU,
                        spriteV,
                        drawWidth,
                        drawHeight,
                        atlasWidth,
                        atlasHeight
                );
            }
        }
    }
}
