package com.skyeshade.skyent.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.skyeshade.skyent.client.model.MeshBakedModel;
import com.skyeshade.skyent.content.block.LargeSteamTurbineBlock;
import com.skyeshade.skyent.content.blockentity.LargeSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.model.*;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import net.minecraft.client.renderer.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * /skyent_mesh_debug off|bounds|collision|all|part <name or UUID>. Local developer overlay.
 */
public final class MeshMachineDebug {
    private static String mode = "off", selected = "";

    private MeshMachineDebug() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        var command = Commands.literal("skyent_mesh_debug");
        for (String value : new String[]{"off", "bounds", "collision", "outline", "voxels", "cells", "all",
                "hybrid", "hybrid_outline", "exact", "sheets", "fallback", "metadata"})
            command.then(Commands.literal(value).executes(ctx -> {
                mode = value;
                if (value.equals("metadata")) ctx.getSource().sendSuccess(() -> Component.literal(MeshMachineShapeCache.data().metadataAudit()
                        + " | magenta=excluded geometry bounds (visual rendering retained)"),false);
                if (value.equals("hybrid") || value.equals("hybrid_outline") || value.equals("exact") || value.equals("sheets") || value.equals("fallback")) {
                    var data = MeshMachineShapeCache.data();
                    var result = value.equals("hybrid_outline") ? data.outlineHybrid() : data.collisionHybrid();
                    ctx.getSource().sendSuccess(() -> Component.literal((value.equals("hybrid_outline") ? "Outline: " : "Collision: ")
                            + (result == null ? data.audit() : result.counts()) + " | green=exact cyan=thin sheets orange=1/16 steps"), false);
                }
                return 1;
            }));
        command.then(Commands.literal("part").then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> {
            selected = StringArgumentType.getString(ctx, "name");
            mode = "part";
            ctx.getSource().sendSuccess(() -> Component.literal("Highlighting mesh parts matching: " + selected), false);
            return 1;
        })));
        event.getDispatcher().register(command);
    }

    public static void render(LargeSteamTurbineBlockEntity turbine, MeshBakedModel model, PoseStack pose, MultiBufferSource buffers) {
        if (mode.equals("off")) return;
        Direction facing = turbine.getBlockState().getValue(LargeSteamTurbineBlock.FACING);
        var transform = MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        var lines = buffers.getBuffer(RenderType.lines());
        if (mode.equals("metadata")) for (AABB box : MeshMachineShapeCache.data().excludedGeometry().values())
            LevelRenderer.renderLineBox(pose,lines,transform.relativeBounds(box,facing),1,.1f,.8f,.8f);
        if (mode.equals("hybrid") || mode.equals("hybrid_outline") || mode.equals("exact") || mode.equals("sheets") || mode.equals("fallback")) {
            var result = mode.equals("hybrid_outline") ? MeshMachineShapeCache.data().outlineHybrid() : MeshMachineShapeCache.data().collisionHybrid();
            if (result != null) {
                boolean all = mode.startsWith("hybrid");
                if (all || mode.equals("exact")) for (var box : result.cuboids())
                    LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(box.bounds(), facing), .1f, 1, .1f, 1);
                if (all || mode.equals("sheets")) for (var box : result.sheets())
                    LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(box.bounds(), facing), 0, 1, 1, 1);
                if (all || mode.equals("fallback")) for (var box : result.fallback().boxes())
                    LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(box, facing), 1, .45f, .05f, .7f);
            }
        }
        if (mode.equals("bounds") || mode.equals("all")) {
            LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(new AABB(0, 0, 0, 3, 3, 5), facing), 0, 1, 1, 1);
            LevelRenderer.renderLineBox(pose, lines, new AABB(0, 0, 0, 1, 1, 1), 1, 1, 0, 1);
            LevelRenderer.renderLineBox(pose, lines, model.bounds(facing), 1, 0, 1, 1);
            LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(MeshMachineShapeCache.data().modelBounds(), facing), 1, .5f, 0, 1);
            Vec3 origin = transform.relative(Vec3.ZERO, facing);
            axis(pose, lines, origin, origin.add(MachineModelTransform.direction(new Vec3(1, 0, 0), facing)), 1, 0, 0);
            axis(pose, lines, origin, origin.add(0, 1, 0), 0, 1, 0);
            axis(pose, lines, origin, origin.add(MachineModelTransform.direction(new Vec3(0, 0, 1), facing)), 0, 0, 1);
            Vec3 shaft = transform.authoredRelative(MeshMachineDefinition.LARGE_STEAM_TURBINE.shaftPivot(), facing);
            axis(pose, lines, shaft, shaft.add(MachineModelTransform.direction(MeshMachineDefinition.LARGE_STEAM_TURBINE.shaftAxis(), facing)), 1, 1, 1);
        }
        if (mode.equals("collision") || mode.equals("all"))
            for (AABB box : MeshMachineShapeCache.data().collisionBoxes())
                LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(box, facing), 0, 1, 0, 1);
        if (mode.equals("voxels") && MeshMachineShapeCache.data().collisionVoxels() != null)
            MeshMachineShapeCache.data().collisionVoxels().unmerged().forEach(box ->
                    LevelRenderer.renderLineBox(pose, lines, transform.relativeBounds(box, facing), .8f, .3f, 1, .45f));
        if (mode.equals("cells") || mode.equals("all") || mode.equals("outline"))
            com.skyeshade.skyent.content.multiblock.ModelMultiblocks.forEachLocal(LargeSteamTurbineBlock.MULTIBLOCK, (x, y, z) -> {
                var offset = com.skyeshade.skyent.content.multiblock.ModelMultiblocks.rotateLocalOffset(LargeSteamTurbineBlock.MULTIBLOCK,
                        new net.minecraft.core.BlockPos(x, y, z), facing);
                if (!mode.equals("outline"))
                    LevelRenderer.renderLineBox(pose, lines, new AABB(offset), .3f, .7f, 1, .3f);
                // Draw the actual post-clipping, post-facing gameplay lookup, not a second transform approximation.
                var data = MeshMachineShapeCache.data();
                var cells = mode.equals("outline") ? data.outline() : data.collision();
                for (AABB box : cells.boxesForLocal(x, y, z, facing))
                    LevelRenderer.renderLineBox(pose, lines, box.move(offset),
                            mode.equals("outline") ? 1 : 0, 1, mode.equals("outline") ? 0 : 1, .65f);
            });
        if (mode.equals("part")) model.parts(facing).forEach((name, box) -> {
            if (name.contains(selected)) LevelRenderer.renderLineBox(pose, lines, box, 1, .2f, .2f, 1);
        });
    }

    private static void axis(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer lines, Vec3 a, Vec3 b, float r, float g, float blue) {
        Vec3 n = b.subtract(a).normalize();
        lines.addVertex(pose.last(), (float) a.x, (float) a.y, (float) a.z).setColor(r, g, blue, 1).setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
        lines.addVertex(pose.last(), (float) b.x, (float) b.y, (float) b.z).setColor(r, g, blue, 1).setNormal(pose.last(), (float) n.x, (float) n.y, (float) n.z);
    }
}
