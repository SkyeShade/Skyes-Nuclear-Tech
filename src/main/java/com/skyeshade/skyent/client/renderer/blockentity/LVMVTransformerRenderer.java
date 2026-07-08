package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.content.item.LVWireDrumItem;
import com.skyeshade.skyent.event.systems.LVElectricalNetworkSystem;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class LVMVTransformerRenderer implements BlockEntityRenderer<LVMVTransformerBlockEntity> {
    private static final int CABLE_SEGMENTS = LVElectricalNetworkSystem.CABLE_SEGMENTS;
    private static long renderedConnectionFrame = Long.MIN_VALUE;
    private static final Set<ConnectionKey> RENDERED_CONNECTIONS = new HashSet<>();
    private static final RenderType CABLE_RENDER_TYPE = RenderType.create(
            "skyent_transformer_mv_cable_quads",
            DefaultVertexFormat.POSITION_COLOR_LIGHTMAP,
            VertexFormat.Mode.QUADS,
            512,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LEASH_SHADER)
                    .setTextureState(RenderStateShard.NO_TEXTURE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false)
    );

    public LVMVTransformerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LVMVTransformerBlockEntity transformer, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = transformer.getLevel();
        if (level == null) {
            return;
        }

        VertexConsumer buffer = bufferSource.getBuffer(CABLE_RENDER_TYPE);
        Matrix4f pose = poseStack.last().pose();
        BlockPos origin = transformer.getBlockPos();
        beginFrame(Minecraft.getInstance().getFrameTimeNs());
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

            ConnectionKey key = new ConnectionKey(connection.terminalPos(), connection.connectionPos());
            if (!RENDERED_CONNECTIONS.add(key)) {
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
            drawCable(buffer, pose, start, end, camera, connection.wireType(), packedLight);
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

    private static void beginFrame(long frame) {
        if (renderedConnectionFrame != frame) {
            renderedConnectionFrame = frame;
            RENDERED_CONNECTIONS.clear();
        }
    }

    private static void drawCable(VertexConsumer buffer, Matrix4f pose, Vector3f start, Vector3f end, Vector3f camera, LVWireType wireType, int packedLight) {
        Vector3f cable = new Vector3f(end).sub(start);
        if (cable.lengthSquared() <= 0.0001F) {
            return;
        }

        Vector3f[] points = new Vector3f[CABLE_SEGMENTS + 1];
        Vector3f[] left = new Vector3f[CABLE_SEGMENTS + 1];
        Vector3f[] right = new Vector3f[CABLE_SEGMENTS + 1];
        for (int sample = 0; sample <= CABLE_SEGMENTS; sample++) {
            float t = (float) sample / CABLE_SEGMENTS;
            points[sample] = sagPoint(start, end, t);
            Vector3f tangent = sample < CABLE_SEGMENTS
                    ? new Vector3f(sagPoint(start, end, (float) (sample + 1) / CABLE_SEGMENTS)).sub(points[sample])
                    : new Vector3f(points[sample]).sub(points[sample - 1]);
            Vector3f view = new Vector3f(camera).sub(points[sample]);
            Vector3f normal = tangent.cross(view, new Vector3f());
            if (normal.lengthSquared() <= 0.0001F) {
                normal.set(0.0F, 0.0F, 1.0F);
            } else {
                normal.normalize();
            }
            normal.mul(wireType.cableHalfWidth());
            left[sample] = new Vector3f(points[sample]).add(normal);
            right[sample] = new Vector3f(points[sample]).sub(normal);
        }

        for (int sample = 0; sample < CABLE_SEGMENTS; sample++) {
            vertex(buffer, pose, left[sample], wireType, packedLight);
            vertex(buffer, pose, left[sample + 1], wireType, packedLight);
            vertex(buffer, pose, right[sample + 1], wireType, packedLight);
            vertex(buffer, pose, right[sample], wireType, packedLight);
        }
    }

    private static Vector3f sagPoint(Vector3f start, Vector3f end, float t) {
        Vector3f point = new Vector3f(start).lerp(end, t);
        float distance = new Vector3f(end).sub(start).length();
        float sag = Math.min(1.2F, 0.05F + distance * 0.04F);
        point.y -= Math.sin(Math.PI * t) * sag;
        return point;
    }

    private static void vertex(VertexConsumer buffer, Matrix4f pose, Vector3f pos, LVWireType wireType, int packedLight) {
        buffer.addVertex(pose, pos.x(), pos.y(), pos.z())
                .setColor(wireType.red(), wireType.green(), wireType.blue(), 1.0F)
                .setLight(Math.max(packedLight, LightTexture.FULL_BRIGHT));
    }

    private record ConnectionKey(long first, long second) {
        private ConnectionKey(BlockPos a, BlockPos b) {
            this(Math.min(a.asLong(), b.asLong()), Math.max(a.asLong(), b.asLong()));
        }
    }
}
