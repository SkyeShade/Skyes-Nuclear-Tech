package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.skyeshade.skyent.client.renderer.LVConnectorRenderer;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity;
import com.skyeshade.skyent.content.item.LVWireDrumItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LVMVTransformerRenderer implements BlockEntityRenderer<LVMVTransformerBlockEntity> {
    public LVMVTransformerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LVMVTransformerBlockEntity transformer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = transformer.getLevel();
        if (level == null) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(LVConnectorRenderer.cableRenderType());
        Matrix4f pose = poseStack.last().pose();
        BlockPos origin = transformer.getBlockPos();
        Vec3 cameraWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f camera = new Vector3f(
                (float) (cameraWorld.x - origin.getX()),
                (float) (cameraWorld.y - origin.getY()),
                (float) (cameraWorld.z - origin.getZ())
        );

        for (LVMVTransformerBlockEntity.TerminalConnection connection : transformer.terminalConnections()) {
            if (level.getBlockEntity(connection.connectionPos()) instanceof com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity) {
                continue;
            }
            if (!LVMVTransformerBlock.isMVTerminal(level.getBlockState(connection.connectionPos()))) {
                continue;
            }
            if (!shouldRenderFromTerminal(connection.terminalPos(), connection.connectionPos())) {
                continue;
            }

            Vec3 startAnchor = LVMVTransformerBlock.mvTerminalAnchor(connection.terminalPos());
            Vec3 endAnchor = LVMVTransformerBlock.mvTerminalAnchor(connection.connectionPos());
            Vector3f start = new Vector3f(
                    (float) (startAnchor.x - origin.getX()),
                    (float) (startAnchor.y - origin.getY()),
                    (float) (startAnchor.z - origin.getZ())
            );
            Vector3f end = new Vector3f(
                    (float) (endAnchor.x - origin.getX()),
                    (float) (endAnchor.y - origin.getY()),
                    (float) (endAnchor.z - origin.getZ())
            );
            double heat = transformer.getTerminalConnectionHeat(connection.terminalPos(), connection.connectionPos());
            LVConnectorRenderer.renderCable(buffer, pose, start, end, camera, connection.wireType(), packedLight, heat);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(LVMVTransformerBlockEntity blockEntity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return LVWireDrumItem.MAX_CONNECTION_DISTANCE * 2;
    }

    private static boolean shouldRenderFromTerminal(BlockPos terminal, BlockPos connection) {
        return !terminal.equals(connection) && terminal.asLong() < connection.asLong();
    }
}
