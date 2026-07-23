package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.content.block.MediumTankBlock;
import com.skyeshade.skyent.content.blockentity.MediumTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class MediumTankRenderer implements BlockEntityRenderer<MediumTankBlockEntity> {
    private static final double PIXELS_PER_BLOCK = 16.0D;
    private static final double LIQUID_INSET = 3.0D / 16.0D;
    private static final double LIQUID_Y_INSET = 3.0D / 16.0D;
    private static final double LIQUID_MIN_X = LIQUID_INSET;
    private static final double LIQUID_MAX_X = 2.0D - LIQUID_INSET;
    private static final double LIQUID_MIN_Y = LIQUID_INSET + LIQUID_Y_INSET;
    private static final double LIQUID_MAX_Y = 2.0D - LIQUID_INSET - LIQUID_Y_INSET- (1.0D / 16.0D);
    private static final double LIQUID_MIN_Z = -1.0D + LIQUID_INSET;
    private static final double LIQUID_MAX_Z = 3.0D - LIQUID_INSET;
    private static final int MAX_LIQUID_PIXELS = Math.max(1, (int) Math.round((LIQUID_MAX_Y - LIQUID_MIN_Y) * PIXELS_PER_BLOCK));
    private static final int SPRITE_PIXELS = 16;
    private static final float MIN_GAS_ALPHA = 0.12F;
    private static final float MAX_GAS_ALPHA = 0.65F;

    public MediumTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MediumTankBlockEntity tank, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = tank.getLevel();
        if (level == null) {
            return;
        }

        FluidStack fluidStack = tank.getFluidInTank();
        int amount = fluidStack.getAmount();
        int capacity = tank.getFluidCapacity();
        if (fluidStack.isEmpty() || amount <= 0 || capacity <= 0) {
            return;
        }

        Direction facing = tank.getBlockState().hasProperty(MediumTankBlock.FACING)
                ? tank.getBlockState().getValue(MediumTankBlock.FACING)
                : Direction.NORTH;

        Fluid fluid = fluidStack.getFluid();
        double fillFraction = Mth.clamp(amount / (double) capacity, 0.0D, 1.0D);
        boolean gas = isGas(fluidStack);
        boolean full = amount >= capacity;
        int filledPixels = gas || full
                ? MAX_LIQUID_PIXELS
                : Mth.clamp((int) Math.ceil(fillFraction * MAX_LIQUID_PIXELS), 1, MAX_LIQUID_PIXELS);
        boolean renderTopFace = !gas && !full;
        double maxY = LIQUID_MIN_Y + filledPixels / PIXELS_PER_BLOCK;

        IClientFluidTypeExtensions extensions = IClientFluidTypeExtensions.of(fluid);
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(extensions.getStillTexture());
        int tint = extensions.getTintColor();
        int red = tint >> 16 & 0xFF;
        int green = tint >> 8 & 0xFF;
        int blue = tint & 0xFF;
        int alpha = tint >>> 24;
        if (alpha <= 0) {
            alpha = 255;
        }
        if (gas) {
            alpha = gasAlpha(alpha, fillFraction);
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        LiquidBounds bounds = new LiquidBounds(LIQUID_MIN_X, LIQUID_MAX_X, LIQUID_MIN_Y, maxY, LIQUID_MIN_Z, LIQUID_MAX_Z);
        renderLiquid(bounds, renderTopFace, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight);
    }

    @Override
    public boolean shouldRenderOffScreen(MediumTankBlockEntity blockEntity) {
        return true;
    }

    private static void renderLiquid(LiquidBounds bounds, boolean renderTopFace, Direction facing, PoseStack.Pose pose, Matrix4f matrix, VertexConsumer consumer, TextureAtlasSprite sprite, int red, int green, int blue, int alpha, int packedLight) {
        if (renderTopFace) {
            renderTopTiles(bounds, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight);
        }
        renderSideTiles(bounds, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight);
    }

    private static void renderTopTiles(LiquidBounds bounds, Direction facing, PoseStack.Pose pose, Matrix4f matrix, VertexConsumer consumer, TextureAtlasSprite sprite, int red, int green, int blue, int alpha, int packedLight) {
        int widthPixels = blockPixels(bounds.maxX - bounds.minX);
        int depthPixels = blockPixels(bounds.maxZ - bounds.minZ);

        for (int xPixel = 0; xPixel < widthPixels; xPixel += SPRITE_PIXELS) {
            int tileWidth = Math.min(SPRITE_PIXELS, widthPixels - xPixel);
            double x = bounds.minX + xPixel / PIXELS_PER_BLOCK;
            double nextX = bounds.minX + (xPixel + tileWidth) / PIXELS_PER_BLOCK;

            for (int zPixel = 0; zPixel < depthPixels; zPixel += SPRITE_PIXELS) {
                int tileDepth = Math.min(SPRITE_PIXELS, depthPixels - zPixel);
                double z = bounds.minZ + zPixel / PIXELS_PER_BLOCK;
                double nextZ = bounds.minZ + (zPixel + tileDepth) / PIXELS_PER_BLOCK;
                addQuad(
                        consumer, pose, matrix, facing,
                        x, bounds.maxY, z,
                        nextX, bounds.maxY, z,
                        nextX, bounds.maxY, nextZ,
                        x, bounds.maxY, nextZ,
                        0.0D, 1.0D, 0.0D,
                        spriteU(sprite, 0), spriteV(sprite, 0),
                        spriteU(sprite, tileWidth), spriteV(sprite, tileDepth),
                        red, green, blue, alpha, packedLight
                );
            }
        }
    }

    private static void renderSideTiles(LiquidBounds bounds, Direction facing, PoseStack.Pose pose, Matrix4f matrix, VertexConsumer consumer, TextureAtlasSprite sprite, int red, int green, int blue, int alpha, int packedLight) {
        renderSide(bounds, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight, Side.NORTH);
        renderSide(bounds, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight, Side.SOUTH);
        renderSide(bounds, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight, Side.WEST);
        renderSide(bounds, facing, pose, matrix, consumer, sprite, red, green, blue, alpha, packedLight, Side.EAST);
    }

    private static void renderSide(LiquidBounds bounds, Direction facing, PoseStack.Pose pose, Matrix4f matrix, VertexConsumer consumer, TextureAtlasSprite sprite, int red, int green, int blue, int alpha, int packedLight, Side side) {
        double horizontalMin = side.axisX ? bounds.minZ : bounds.minX;
        double horizontalMax = side.axisX ? bounds.maxZ : bounds.maxX;
        int horizontalPixels = blockPixels(horizontalMax - horizontalMin);
        int filledPixels = blockPixels(bounds.maxY - bounds.minY);

        for (int hPixel = 0; hPixel < horizontalPixels; hPixel += SPRITE_PIXELS) {
            int tileWidth = Math.min(SPRITE_PIXELS, horizontalPixels - hPixel);
            double h = horizontalMin + hPixel / PIXELS_PER_BLOCK;
            double nextH = horizontalMin + (hPixel + tileWidth) / PIXELS_PER_BLOCK;

            for (int yPixel = 0; yPixel < filledPixels; yPixel += SPRITE_PIXELS) {
                int tileHeight = Math.min(SPRITE_PIXELS, filledPixels - yPixel);
                double y = bounds.minY + yPixel / PIXELS_PER_BLOCK;
                double nextY = bounds.minY + (yPixel + tileHeight) / PIXELS_PER_BLOCK;
                boolean topPartialBand = yPixel + tileHeight >= filledPixels && tileHeight < SPRITE_PIXELS;
                int vStart = topPartialBand ? SPRITE_PIXELS - tileHeight : 0;
                Face face = side.face(bounds, h, nextH, y, nextY);
                addQuad(
                        consumer, pose, matrix, facing,
                        face.x1, face.y1, face.z1,
                        face.x2, face.y2, face.z2,
                        face.x3, face.y3, face.z3,
                        face.x4, face.y4, face.z4,
                        side.normalX, 0.0D, side.normalZ,
                        spriteU(sprite, 0), spriteV(sprite, vStart),
                        spriteU(sprite, tileWidth), spriteV(sprite, SPRITE_PIXELS),
                        red, green, blue, alpha, packedLight
                );
            }
        }
    }

    private static int blockPixels(double blocks) {
        return Math.max(0, Mth.ceil(blocks * PIXELS_PER_BLOCK));
    }

    private static boolean isGas(FluidStack fluidStack) {
        return fluidStack.getFluid().getFluidType().isLighterThanAir();
    }

    private static int gasAlpha(int baseAlpha, double fillFraction) {
        float densityAlpha = Mth.lerp((float) fillFraction, MIN_GAS_ALPHA, MAX_GAS_ALPHA);
        return Mth.clamp(Math.round(baseAlpha * densityAlpha), 1, 255);
    }

    private static float spriteU(TextureAtlasSprite sprite, int localPixel) {
        // Mirrors FluidGaugeRenderer: convert sprite-local pixel offsets from sprite.getX()
        // into atlas-normalized UVs. Do not pass 16-pixel values to TextureAtlasSprite#getU.
        int atlasWidth = Math.round(sprite.contents().width() / (sprite.getU1() - sprite.getU0()));
        return (sprite.getX() + Mth.clamp(localPixel, 0, SPRITE_PIXELS)) / (float) atlasWidth;
    }

    private static float spriteV(TextureAtlasSprite sprite, int localPixel) {
        // Mirrors FluidGaugeRenderer: convert sprite-local pixel offsets from sprite.getY()
        // into atlas-normalized UVs. Do not pass 16-pixel values to TextureAtlasSprite#getV.
        int atlasHeight = Math.round(sprite.contents().height() / (sprite.getV1() - sprite.getV0()));
        return (sprite.getY() + Mth.clamp(localPixel, 0, SPRITE_PIXELS)) / (float) atlasHeight;
    }

    private static void addQuad(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f matrix, Direction facing, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4, double normalX, double normalY, double normalZ, float u0, float v0, float u1, float v1, int red, int green, int blue, int alpha, int packedLight) {
        Vector3f p1 = transformPoint(x1, y1, z1, facing);
        Vector3f p2 = transformPoint(x2, y2, z2, facing);
        Vector3f p3 = transformPoint(x3, y3, z3, facing);
        Vector3f p4 = transformPoint(x4, y4, z4, facing);
        Vector3f normal = rotateNormal(normalX, normalY, normalZ, facing);
        addVertex(consumer, pose, matrix, p1, u0, v1, normal, red, green, blue, alpha, packedLight);
        addVertex(consumer, pose, matrix, p2, u1, v1, normal, red, green, blue, alpha, packedLight);
        addVertex(consumer, pose, matrix, p3, u1, v0, normal, red, green, blue, alpha, packedLight);
        addVertex(consumer, pose, matrix, p4, u0, v0, normal, red, green, blue, alpha, packedLight);
    }

    private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, Matrix4f matrix, Vector3f point, float u, float v, Vector3f normal, int red, int green, int blue, int alpha, int packedLight) {
        consumer.addVertex(matrix, point.x(), point.y(), point.z())
                .setColor(red, green, blue, alpha)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, normal.x(), normal.y(), normal.z());
    }

    private static Vector3f rotate(double x, double y, double z, Direction facing) {
        return switch (facing) {
            case EAST -> new Vector3f((float) -z, (float) y, (float) x);
            case SOUTH -> new Vector3f((float) -x, (float) y, (float) -z);
            case WEST -> new Vector3f((float) z, (float) y, (float) -x);
            default -> new Vector3f((float) x, (float) y, (float) z);
        };
    }

    private static Vector3f transformPoint(double x, double y, double z, Direction facing) {
        Vector3f point = rotate(x, y, z, facing);
        Vector3f offset = liquidFacingOffset(facing);
        point.add(offset);
        return point;
    }

    private static Vector3f liquidFacingOffset(Direction facing) {
        return switch (facing) {
            case SOUTH -> new Vector3f(1.0F, 0.0F, 1.0F);
            case WEST -> new Vector3f(0.0F, 0.0F, 1.0F);
            case EAST -> new Vector3f(1.0F, 0.0F, 0.0F);
            default -> new Vector3f(0.0F, 0.0F, 0.0F);
        };
    }

    private static Vector3f rotateNormal(double x, double y, double z, Direction facing) {
        return rotate(x, y, z, facing).normalize();
    }

    private enum Side {
        NORTH(false, 0.0D, -1.0D) {
            @Override
            Face face(LiquidBounds bounds, double h, double nextH, double y, double nextY) {
                return new Face(h, y, bounds.minZ, nextH, y, bounds.minZ, nextH, nextY, bounds.minZ, h, nextY, bounds.minZ);
            }
        },
        SOUTH(false, 0.0D, 1.0D) {
            @Override
            Face face(LiquidBounds bounds, double h, double nextH, double y, double nextY) {
                return new Face(nextH, y, bounds.maxZ, h, y, bounds.maxZ, h, nextY, bounds.maxZ, nextH, nextY, bounds.maxZ);
            }
        },
        WEST(true, -1.0D, 0.0D) {
            @Override
            Face face(LiquidBounds bounds, double h, double nextH, double y, double nextY) {
                return new Face(bounds.minX, y, nextH, bounds.minX, y, h, bounds.minX, nextY, h, bounds.minX, nextY, nextH);
            }
        },
        EAST(true, 1.0D, 0.0D) {
            @Override
            Face face(LiquidBounds bounds, double h, double nextH, double y, double nextY) {
                return new Face(bounds.maxX, y, h, bounds.maxX, y, nextH, bounds.maxX, nextY, nextH, bounds.maxX, nextY, h);
            }
        };

        private final boolean axisX;
        private final double normalX;
        private final double normalZ;

        Side(boolean axisX, double normalX, double normalZ) {
            this.axisX = axisX;
            this.normalX = normalX;
            this.normalZ = normalZ;
        }

        abstract Face face(LiquidBounds bounds, double h, double nextH, double y, double nextY);
    }

    private record LiquidBounds(double minX, double maxX, double minY, double maxY, double minZ, double maxZ) {
    }

    private record Face(double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3, double z3, double x4, double y4, double z4) {
    }
}
