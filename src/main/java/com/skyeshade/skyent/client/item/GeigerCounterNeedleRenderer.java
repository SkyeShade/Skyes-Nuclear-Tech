package com.skyeshade.skyent.client.item;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skyeshade.skyent.content.item.GeigerNeedleUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;

public final class GeigerCounterNeedleRenderer {
    private static final RenderType NEEDLE_RENDER_TYPE = RenderType.create(
            "skyent_geiger_counter_needle",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            256,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false)
    );

    private static final boolean DEBUG_SWEEP_NEEDLE = false;
    private static final float DEBUG_SWEEP_MIN_ANGLE = -90.0F;
    private static final float DEBUG_SWEEP_MAX_ANGLE = 90.0F;
    private static final float DEBUG_SWEEP_SPEED = 0.08F;

    // Temporary tuning constants for aligning the overlay to the Blockbench handheld model.
    private static final boolean RENDER_FIRST_PERSON = true;
    private static final boolean RENDER_THIRD_PERSON = true;
    private static final HandheldOverlayTransform FIRST_PERSON_RIGHT = new HandheldOverlayTransform(
            -0.75F / 16.0F,
            4.25F / 16.0F,
            1.25F / 16.0F,
            -6.15F,
            -31.49F,
            -1.61F,
            1.0F
    );
    private static final HandheldOverlayTransform FIRST_PERSON_LEFT = new HandheldOverlayTransform(
            0.75F / 16.0F,
            4.25F / 16.0F,
            1.25F / 16.0F,
            -6.15F,
            31.49F,
            1.61F,
            1.0F
    );
    private static final HandheldOverlayTransform THIRD_PERSON_RIGHT = new HandheldOverlayTransform(
            0.0F,
            2.0F / 16.0F,
            0.75F / 16.0F,
            0.0F,
            0.0F,
            0.0F,
            0.5F
    );
    private static final HandheldOverlayTransform THIRD_PERSON_LEFT = THIRD_PERSON_RIGHT;
    private static final float NEEDLE_TRANSLATE_X = -0.012F / 16.0F;
    private static final float NEEDLE_TRANSLATE_Y = 0.05F / 16.0F;
    private static final float NEEDLE_TRANSLATE_Z = 0.80F / 16.0F;
    private static final float NEEDLE_SCALE = 1.0F;
    private static final float NEEDLE_LENGTH = 0.12F;
    private static final float NEEDLE_WIDTH = 0.012F;


    private GeigerCounterNeedleRenderer() {
    }

    public static void renderNeedle(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, ItemDisplayContext displayContext, boolean leftHand) {
        if (!isHandheld(displayContext)) {
            return;
        }

        float angle = needleAngleDegrees();

        poseStack.pushPose();
        applyHandheldModelOverlayTransform(poseStack, displayContext);
        poseStack.translate(NEEDLE_TRANSLATE_X, NEEDLE_TRANSLATE_Y, NEEDLE_TRANSLATE_Z);
        poseStack.scale(NEEDLE_SCALE, NEEDLE_SCALE, NEEDLE_SCALE);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer buffer = bufferSource.getBuffer(NEEDLE_RENDER_TYPE);
        float halfWidth = NEEDLE_WIDTH * 0.5F;

        addVertex(buffer, pose, -halfWidth, 0.0F, 0.0F);
        addVertex(buffer, pose, halfWidth, 0.0F, 0.0F);
        addVertex(buffer, pose, halfWidth, NEEDLE_LENGTH, 0.0F);
        addVertex(buffer, pose, -halfWidth, NEEDLE_LENGTH, 0.0F);

        poseStack.popPose();
    }

    private static boolean isHandheld(ItemDisplayContext displayContext) {
        return RENDER_FIRST_PERSON
                && (displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                || RENDER_THIRD_PERSON
                && (displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }

    private static float needleAngleDegrees() {
        if (DEBUG_SWEEP_NEEDLE) {
            Minecraft minecraft = Minecraft.getInstance();
            float ticks = minecraft.player == null ? 0.0F : minecraft.player.tickCount;
            float wave = (Mth.sin(ticks * DEBUG_SWEEP_SPEED) + 1.0F) * 0.5F;
            return Mth.lerp(wave, DEBUG_SWEEP_MIN_ANGLE, DEBUG_SWEEP_MAX_ANGLE);
        }

        float gauge = GeigerCounterClientState.getNeedleValue();
        Minecraft minecraft = Minecraft.getInstance();
        float ticks = minecraft.player == null ? 0.0F : minecraft.player.tickCount;
        return GeigerNeedleUtil.valueToRenderedNeedleDegrees(gauge, ticks);
    }

    private static void applyHandheldModelOverlayTransform(PoseStack poseStack, ItemDisplayContext displayContext) {
        HandheldOverlayTransform transform = switch (displayContext) {
            case FIRST_PERSON_LEFT_HAND -> FIRST_PERSON_LEFT;
            case FIRST_PERSON_RIGHT_HAND -> FIRST_PERSON_RIGHT;
            case THIRD_PERSON_LEFT_HAND -> THIRD_PERSON_LEFT;
            case THIRD_PERSON_RIGHT_HAND -> THIRD_PERSON_RIGHT;
            default -> null;
        };

        if (transform != null) {
            transform.apply(poseStack);
        }
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f pose, float x, float y, float z) {
        buffer.addVertex(pose, x, y, z)
                .setColor(0, 0, 0, 255)
                .setLight(LightTexture.FULL_BRIGHT);
    }

    private record HandheldOverlayTransform(
            float translateX,
            float translateY,
            float translateZ,
            float rotateX,
            float rotateY,
            float rotateZ,
            float scale
    ) {
        private void apply(PoseStack poseStack) {
            poseStack.translate(translateX, translateY, translateZ);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(rotateX));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(rotateY));
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotateZ));
            poseStack.scale(scale, scale, scale);
        }
    }
}
