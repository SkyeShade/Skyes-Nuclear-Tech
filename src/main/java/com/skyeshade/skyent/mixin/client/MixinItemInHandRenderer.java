package com.skyeshade.skyent.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyent.client.item.GeigerCounterNeedleRenderer;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {
    @Inject(method = "renderItem", at = @At("TAIL"))
    private void skyent$renderGeigerNeedle(
            LivingEntity entity,
            ItemStack stack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!stack.is(ModItems.GEIGER_COUNTER.get())) {
            return;
        }

        GeigerCounterNeedleRenderer.renderNeedle(poseStack, buffer, packedLight, displayContext, leftHand);
    }
}
