package com.skyeshade.skyent.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.skyeshade.skyent.content.entity.ConveyorMovingItemEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class ConveyorMovingItemRenderer extends EntityRenderer<ConveyorMovingItemEntity> {
    private static final float ITEM_RENDER_SCALE = 0.50F;
    private static final float ITEM_RENDER_Y_OFFSET = 0.0F;
    private static final float ITEM_RENDER_X_OFFSET = 0.0F;
    private static final float ITEM_RENDER_Z_OFFSET = 0.0F;
    private static final float ITEM_ROT_X = 90.0F;
    private static final float ITEM_ROT_Y = 0.0F;
    private static final float ITEM_ROT_Z = 0.0F;
    private final ItemRenderer itemRenderer;

    public ConveyorMovingItemRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ConveyorMovingItemEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        ItemStack stack = entity.getItemStack();
        if (stack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(ITEM_RENDER_X_OFFSET, ITEM_RENDER_Y_OFFSET + (stack.getItem() instanceof BlockItem ? 0.08F : 0.0F), ITEM_RENDER_Z_OFFSET);
        if (!(stack.getItem() instanceof BlockItem)) {
            poseStack.mulPose(Axis.XP.rotationDegrees(ITEM_ROT_X));
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(ITEM_ROT_Y));
        poseStack.mulPose(Axis.ZP.rotationDegrees(ITEM_ROT_Z));
        poseStack.scale(ITEM_RENDER_SCALE, ITEM_RENDER_SCALE, ITEM_RENDER_SCALE);
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                0
        );
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ConveyorMovingItemEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
