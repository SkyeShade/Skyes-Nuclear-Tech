package com.skyeshade.skyent.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.energy.CopperWireConstants;
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

public class LVConnectorRenderer implements BlockEntityRenderer<LVConnectorBlockEntity> {
    private static final int CABLE_SEGMENTS = LVElectricalNetworkSystem.CABLE_SEGMENTS;

    private static final float CABLE_ALPHA = 1.0F;
    private static final boolean DEBUG_RENDERED_CONNECTIONS = false;
    private static long renderedConnectionFrame = Long.MIN_VALUE;
    private static final Set<ConnectionKey> RENDERED_CONNECTIONS = new HashSet<>();
    private static final RenderType CABLE_RENDER_TYPE = RenderType.create(
            "skyent_lv_cable_quads",
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
    private static final ColorStop[] HEAT_COLOR_STOPS = {
            new ColorStop(0.00F, new CableColor(0.72F, 0.36F, 0.16F)),
            new ColorStop(0.35F, new CableColor(0.85F, 0.05F, 0.02F)),
            new ColorStop(0.55F, new CableColor(1.00F, 0.10F, 0.02F)),
            new ColorStop(0.72F, new CableColor(1.00F, 0.45F, 0.02F)),
            new ColorStop(0.88F, new CableColor(1.00F, 0.75F, 0.20F)),
            new ColorStop(1.00F, new CableColor(1.00F, 0.95F, 0.75F))
    };

    public LVConnectorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(LVConnectorBlockEntity connector, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        VertexConsumer buffer = bufferSource.getBuffer(CABLE_RENDER_TYPE);
        Matrix4f pose = poseStack.last().pose();
        BlockPos origin = connector.getBlockPos();
        Level level = connector.getLevel();
        beginFrame(Minecraft.getInstance().getFrameTimeNs());
        Vec3 cameraWorld = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vector3f camera = new Vector3f(
                (float) (cameraWorld.x - origin.getX()),
                (float) (cameraWorld.y - origin.getY()),
                (float) (cameraWorld.z - origin.getZ())
        );
        Vec3 startAnchor = anchor(level, connector.getBlockState(), origin);
        Vector3f start = new Vector3f(
                (float) (startAnchor.x - origin.getX()),
                (float) (startAnchor.y - origin.getY()),
                (float) (startAnchor.z - origin.getZ())
        );

        for (BlockPos connection : connector.getConnections()) {
            ConnectionKey key = new ConnectionKey(origin, connection);
            if (!RENDERED_CONNECTIONS.add(key)) {
                continue;
            }

            Vec3 endAnchor = level == null
                    ? new Vec3(LVConnectorBlockEntity.anchorX(connection), LVConnectorBlockEntity.anchorY(connection), LVConnectorBlockEntity.anchorZ(connection))
                    : anchor(level, level.getBlockState(connection), connection);
            Vector3f end = new Vector3f(
                    (float) (endAnchor.x - origin.getX()),
                    (float) (endAnchor.y - origin.getY()),
                    (float) (endAnchor.z - origin.getZ())
            );
            double heat = connector.getConnectionHeat(connection);
            LVWireType wireType = connector.getConnectionWireType(connection);
            debugRenderedConnection(origin, connection, key, heat);
            CableColor color = getCableHeatColor(wireType, heat).withAlpha(1.0F);
            drawCable(buffer, pose, start, end, camera, color, getCableLight(packedLight, heat), wireType.cableHalfWidth());
        }
    }

    @Override
    public boolean shouldRenderOffScreen(LVConnectorBlockEntity blockEntity) {
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

    private static Vec3 anchor(Level level, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        if (LVMVTransformerBlock.isMVTerminal(state)) {
            return LVMVTransformerBlock.mvTerminalAnchor(pos);
        }
        return level == null
                ? new Vec3(LVConnectorBlockEntity.anchorX(pos), LVConnectorBlockEntity.anchorY(pos), LVConnectorBlockEntity.anchorZ(pos))
                : LVConnectorBlockEntity.anchor(state, pos);
    }

    private static void debugRenderedConnection(BlockPos origin, BlockPos connection, ConnectionKey key, double heat) {
        if (DEBUG_RENDERED_CONNECTIONS) {
            SkyesNuclearTech.LOGGER.info("LV cable render origin={} target={} key={}:{} heat={}", origin, connection, key.first, key.second, heat);
        }
    }

    private static void drawCable(VertexConsumer buffer, Matrix4f pose, Vector3f start, Vector3f end, Vector3f camera, CableColor color, int packedLight, float halfWidth) {
        Vector3f cable = new Vector3f(end).sub(start);
        if (cable.lengthSquared() <= 0.0001F) {
            return;
        }

        Vector3f[] points = new Vector3f[CABLE_SEGMENTS + 1];
        Vector3f[] left = new Vector3f[CABLE_SEGMENTS + 1];
        Vector3f[] right = new Vector3f[CABLE_SEGMENTS + 1];
        for (int sample = 0; sample <= CABLE_SEGMENTS; sample++) {
            points[sample] = sagPoint(start, end, (float) sample / CABLE_SEGMENTS);
        }

        for (int sample = 0; sample <= CABLE_SEGMENTS; sample++) {
            Vector3f tangent = tangentAt(points, sample);
            Vector3f side = sideAt(points[sample], tangent, camera, halfWidth);
            left[sample] = new Vector3f(points[sample]).sub(side);
            right[sample] = new Vector3f(points[sample]).add(side);
        }

        for (int segment = 0; segment < CABLE_SEGMENTS; segment++) {
            addVertex(buffer, pose, left[segment], color, packedLight);
            addVertex(buffer, pose, right[segment], color, packedLight);
            addVertex(buffer, pose, right[segment + 1], color, packedLight);
            addVertex(buffer, pose, left[segment + 1], color, packedLight);
        }
    }

    private static Vector3f tangentAt(Vector3f[] points, int index) {
        Vector3f tangent;
        if (index == 0) {
            tangent = new Vector3f(points[1]).sub(points[0]);
        } else if (index == points.length - 1) {
            tangent = new Vector3f(points[index]).sub(points[index - 1]);
        } else {
            tangent = new Vector3f(points[index + 1]).sub(points[index - 1]);
        }

        if (tangent.lengthSquared() <= 0.0001F) {
            return new Vector3f(1.0F, 0.0F, 0.0F);
        }

        return tangent.normalize();
    }

    private static Vector3f sideAt(Vector3f point, Vector3f tangent, Vector3f camera, float halfWidth) {
        Vector3f viewDirection = new Vector3f(camera).sub(point);
        if (viewDirection.lengthSquared() <= 0.0001F) {
            viewDirection.set(0.0F, 1.0F, 0.0F);
        } else {
            viewDirection.normalize();
        }

        Vector3f side = new Vector3f(tangent).cross(viewDirection);
        if (side.lengthSquared() <= 0.0001F) {
            Vector3f fallback = Math.abs(tangent.y()) > 0.95F ? new Vector3f(1.0F, 0.0F, 0.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
            side = new Vector3f(tangent).cross(fallback);
        }

        return side.normalize().mul(halfWidth);
    }

    private static Vector3f sagPoint(Vector3f start, Vector3f end, float amount) {
        double distance = new Vector3f(end).sub(start).length();
        double sag = Math.min(
                LVElectricalNetworkSystem.CABLE_MAX_SAG,
                LVElectricalNetworkSystem.CABLE_BASE_SAG + distance * LVElectricalNetworkSystem.CABLE_SAG_PER_BLOCK
        );
        return new Vector3f(start).lerp(end, amount).sub(0.0F, (float) (Math.sin(Math.PI * amount) * sag), 0.0F);
    }

    private static CableColor getCableHeatColor(LVWireType wireType, double heat) {
        float heatProgress = clamp((float) (heat / CopperWireConstants.COPPER_BURNOUT_HEAT));
        if (heatProgress <= HEAT_COLOR_STOPS[1].position) {
            CableColor baseColor = new CableColor(wireType.red(), wireType.green(), wireType.blue());
            return lerpColor(baseColor, HEAT_COLOR_STOPS[1].color, inverseLerp(HEAT_COLOR_STOPS[0].position, HEAT_COLOR_STOPS[1].position, heatProgress));
        }

        for (int index = 0; index < HEAT_COLOR_STOPS.length - 1; index++) {
            ColorStop lower = HEAT_COLOR_STOPS[index];
            ColorStop upper = HEAT_COLOR_STOPS[index + 1];
            if (heatProgress <= upper.position) {
                return lerpColor(lower.color, upper.color, inverseLerp(lower.position, upper.position, heatProgress));
            }
        }

        return HEAT_COLOR_STOPS[HEAT_COLOR_STOPS.length - 1].color;
    }

    private static CableColor lerpColor(CableColor start, CableColor end, float amount) {
        return new CableColor(
                start.red + (end.red - start.red) * amount,
                start.green + (end.green - start.green) * amount,
                start.blue + (end.blue - start.blue) * amount
        );
    }

    private static float inverseLerp(float start, float end, float value) {
        if (Math.abs(end - start) <= 0.0001F) {
            return 0.0F;
        }

        return clamp((value - start) / (end - start));
    }

    private static int getCableLight(int packedLight, double heat) {
        float glowT = inverseLerp(
                (float) CopperWireConstants.COPPER_GLOW_RED_HEAT,
                (float) CopperWireConstants.COPPER_FULLBRIGHT_HEAT,
                (float) heat
        );
        glowT = smoothstep(glowT);

        int currentBlock = LightTexture.block(packedLight);
        int currentSky = LightTexture.sky(packedLight);
        int fullBlock = LightTexture.block(LightTexture.FULL_BRIGHT);
        int fullSky = LightTexture.sky(LightTexture.FULL_BRIGHT);

        int block = lerpInt(currentBlock, fullBlock, glowT);
        int sky = lerpInt(currentSky, fullSky, glowT);
        return LightTexture.pack(block, sky);
    }

    private static float smoothstep(float value) {
        float clamped = clamp(value);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    private static int lerpInt(int start, int end, float amount) {
        return Math.round(start + (end - start) * amount);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void addVertex(VertexConsumer buffer, Matrix4f pose, Vector3f position, CableColor color, int packedLight) {
        buffer.addVertex(pose, position.x(), position.y(), position.z())
                .setColor(color.red, color.green, color.blue, color.alpha)
                .setLight(packedLight);
    }

    private record CableColor(float red, float green, float blue, float alpha) {
        private CableColor(float red, float green, float blue) {
            this(red, green, blue, CABLE_ALPHA);
        }

        private CableColor withAlpha(float alpha) {
            return new CableColor(red, green, blue, alpha);
        }
    }

    private record ColorStop(float position, CableColor color) {
    }

    private record ConnectionKey(long first, long second) {
        private ConnectionKey(BlockPos first, BlockPos second) {
            this(Math.min(first.asLong(), second.asLong()), Math.max(first.asLong(), second.asLong()));
        }
    }
}
