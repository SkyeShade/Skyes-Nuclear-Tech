package com.skyeshade.skyent.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.skyeshade.skyent.client.model.MeshBakedModel;
import com.skyeshade.skyent.content.block.LargeSteamTurbineBlock;
import com.skyeshade.skyent.content.blockentity.LargeSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.model.MeshMachineDefinition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public final class LargeSteamTurbineRenderer implements BlockEntityRenderer<LargeSteamTurbineBlockEntity> {
    private static final float[] BRIGHTNESS = {1, 1, 1, 1};
    public static final ModelResourceLocation STATIC_MODEL = ModelResourceLocation.standalone(MeshMachineDefinition.id("block/large_steam_turbine_static"));

    public LargeSteamTurbineRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static MeshBakedModel model() {
        var model = Minecraft.getInstance().getModelManager().getModel(STATIC_MODEL);
        return model instanceof MeshBakedModel mesh ? mesh : null;
    }

    @Override
    public void render(LargeSteamTurbineBlockEntity turbine, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        MeshBakedModel model = model();
        if (model == null) return;
        Direction facing = turbine.getBlockState().getValue(LargeSteamTurbineBlock.FACING);
        var consumer = buffers.getBuffer(RenderType.cutout());
        // Scene light supplied by the dispatcher at the controller. The machine is non-occluding.
        int[] lightmap = {light, light, light, light};
        var quads = model.quads(facing);
        for (int i = 0; i < quads.size(); i++) {
            float shade = model.shade(facing, i);
            consumer.putBulkData(pose.last(), quads.get(i), BRIGHTNESS, shade, shade, shade, 1, lightmap, overlay, true);
        }
        com.skyeshade.skyent.client.debug.MeshMachineDebug.render(turbine, model, pose, buffers);
    }

    @Override
    public AABB getRenderBoundingBox(LargeSteamTurbineBlockEntity turbine) {
        Direction facing = turbine.getBlockState().getValue(LargeSteamTurbineBlock.FACING);
        MeshBakedModel model = model();
        AABB bounds = model == null ? MeshMachineDefinition.LARGE_STEAM_TURBINE.transform().relativeBounds(new AABB(0, 0, 0, 3, 3, 5), facing) : model.bounds(facing);
        return bounds.move(turbine.getBlockPos());
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    // Opt out of controller-section culling, then let NeoForge cull against the finite whole-model bounds above.
    @Override
    public boolean shouldRenderOffScreen(LargeSteamTurbineBlockEntity turbine) {
        return true;
    }
}
