package com.skyeshade.skyent.test;

import com.skyeshade.skyent.content.block.*;
import com.skyeshade.skyent.content.blockentity.LargeSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.multiblock.ModelMultiblocks;
import com.skyeshade.skyent.content.shape.MeshMachineShapeCache;
import com.skyeshade.skyent.registry.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("skyent")
@PrefixGameTestTemplate(false)
public final class LargeSteamTurbineGameTests {
    private static final BlockPos ORIGIN = new BlockPos(8, 2, 8);

    @GameTest(template = "mesh_empty")
    public static void north(GameTestHelper h) {
        lifecycle(h, Direction.NORTH);
    }

    @GameTest(template = "mesh_empty")
    public static void east(GameTestHelper h) {
        lifecycle(h, Direction.EAST);
    }

    @GameTest(template = "mesh_empty")
    public static void south(GameTestHelper h) {
        lifecycle(h, Direction.SOUTH);
    }

    @GameTest(template = "mesh_empty")
    public static void west(GameTestHelper h) {
        lifecycle(h, Direction.WEST);
    }

    private static BlockPlaceContext context(GameTestHelper h, Direction facing, ItemStack stack) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(facing.getOpposite().toYRot());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos origin = h.absolutePos(ORIGIN);
        return new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin, false));
    }

    private static void lifecycle(GameTestHelper h, Direction facing) {
        h.assertTrue(!MeshMachineShapeCache.data().audit().startsWith("FALLBACK"), "Canonical shapes compiled");
        var stack = new ItemStack(ModItems.LARGE_STEAM_TURBINE.get(), 2);
        var ctx = context(h, facing, stack);
        h.assertTrue(ModItems.LARGE_STEAM_TURBINE.get().place(ctx).consumesAction(), "Item placement succeeds " + facing);
        h.assertTrue(stack.getCount() == 1, "Exactly one item consumed");
        BlockPos origin = h.absolutePos(ORIGIN);
        h.assertTrue(h.getLevel().getBlockEntity(origin) instanceof LargeSteamTurbineBlockEntity, "Controller entity exists");
        ModelMultiblocks.forEachLocal(LargeSteamTurbineBlock.MULTIBLOCK, (x, y, z) -> {
            BlockPos p = ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin, facing, x, y, z);
            var state = h.getLevel().getBlockState(p);
            h.assertTrue(state.is(LargeSteamTurbineBlock.MULTIBLOCK.isControllerLocal(x, y, z) ? ModBlocks.LARGE_STEAM_TURBINE.get() : ModBlocks.LARGE_STEAM_TURBINE_PART.get()), "Footprint populated " + p);
            h.assertTrue(LargeSteamTurbineBlock.masterPos(p, state).equals(origin), "Cell resolves controller");
            var restored = NbtUtils.readBlockState(h.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK), NbtUtils.writeBlockState(state));
            h.assertTrue(restored.equals(state), "Saved cell state roundtrip");
        });
        BlockEntity entity = h.getLevel().getBlockEntity(origin);
        var saved = entity.saveWithFullMetadata(h.getLevel().registryAccess());
        h.assertTrue(BlockEntity.loadStatic(origin, entity.getBlockState(), saved, h.getLevel().registryAccess()) instanceof LargeSteamTurbineBlockEntity, "Controller NBT roundtrip");
        h.getLevel().destroyBlock(origin, true);
        assertRemoved(h, origin, facing);
        h.assertTrue(dropCount(h, origin) == 1, "Controller destruction drops exactly one item");
        h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8)).forEach(ItemEntity::discard);
        h.assertTrue(ModItems.LARGE_STEAM_TURBINE.get().place(context(h, facing, new ItemStack(ModItems.LARGE_STEAM_TURBINE.get()))).consumesAction(), "Replace succeeds");
        BlockPos part = ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin, facing, 2, 1, 3);
        h.getLevel().destroyBlock(part, true);
        assertRemoved(h, origin, facing);
        h.assertTrue(dropCount(h, origin) == 1, "Part destruction drops exactly one item");
        h.succeed();
    }

    private static int dropCount(GameTestHelper h, BlockPos origin) {
        return h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(8)).stream().filter(e -> e.getItem().is(ModItems.LARGE_STEAM_TURBINE.get())).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void assertRemoved(GameTestHelper h, BlockPos origin, Direction facing) {
        ModelMultiblocks.forEachLocal(LargeSteamTurbineBlock.MULTIBLOCK, (x, y, z) -> {
            BlockPos pos = ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin, facing, x, y, z);
            h.assertTrue(h.getLevel().getBlockState(pos).isAir(), "Whole machine removed at " + pos);
        });
    }

    @GameTest(template = "mesh_empty")
    public static void obstruction(GameTestHelper h) {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos origin = h.absolutePos(ORIGIN);
            BlockPos obstacle = ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin, facing, 2, 1, 3);
            h.getLevel().setBlock(obstacle, Blocks.STONE.defaultBlockState(), 3);
            var stack = new ItemStack(ModItems.LARGE_STEAM_TURBINE.get());
            h.assertTrue(!ModItems.LARGE_STEAM_TURBINE.get().place(context(h, facing, stack)).consumesAction(), "Obstruction rejects " + facing);
            h.assertTrue(stack.getCount() == 1 && h.getLevel().getBlockState(origin).isAir(), "No consumption/partial controller");
            h.getLevel().setBlock(obstacle, Blocks.AIR.defaultBlockState(), 3);
            assertRemoved(h, origin, facing);
        }
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void creativePartBreak(GameTestHelper h) {
        BlockPos origin = h.absolutePos(ORIGIN);
        h.assertTrue(ModItems.LARGE_STEAM_TURBINE.get().place(context(h, Direction.NORTH, new ItemStack(ModItems.LARGE_STEAM_TURBINE.get()))).consumesAction(), "Place");
        BlockPos part = ModelMultiblocks.localToWorld(LargeSteamTurbineBlock.MULTIBLOCK, origin, Direction.NORTH, 2, 1, 3);
        var state = h.getLevel().getBlockState(part);
        state.getBlock().playerWillDestroy(h.getLevel(), part, state, h.makeMockPlayer(GameType.CREATIVE));
        assertRemoved(h, origin, Direction.NORTH);
        h.assertTrue(dropCount(h, origin) == 0, "Creative part break drops nothing");
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void collisionAndRaycast(GameTestHelper h) {
        var definition = com.skyeshade.skyent.content.model.MeshMachineDefinition.LARGE_STEAM_TURBINE;
        BlockPos origin = h.absolutePos(ORIGIN);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            h.assertTrue(ModItems.LARGE_STEAM_TURBINE.get().place(context(h, facing, new ItemStack(ModItems.LARGE_STEAM_TURBINE.get()))).consumesAction(), "Place for shape checks");
            var transform = definition.transform();
            // The center of the open rotor bay is not made solid by canopy, stator blades or decorative internals.
            Vec3 free = transform.relative(new Vec3(1.5, 1.125, 3.1875), facing).add(Vec3.atLowerCornerOf(origin));
            var collision = h.getLevel().getBlockState(BlockPos.containing(free)).getCollisionShape(h.getLevel(), BlockPos.containing(free));
            Vec3 local = free.subtract(Vec3.atLowerCornerOf(BlockPos.containing(free)));
            boolean[] obstructed = {false};
            collision.forAllBoxes((a, b, c, d, e, f) -> {
                if (new AABB(a, b, c, d, e, f).contains(local)) obstructed[0] = true;
            });
            h.assertTrue(!obstructed[0], "Decorative rotor bay is not solid " + facing);
            Vec3 start = transform.relative(new Vec3(1.5, 1, -1), facing).add(Vec3.atLowerCornerOf(origin));
            Vec3 end = transform.relative(new Vec3(1.5, 1, 2), facing).add(Vec3.atLowerCornerOf(origin));
            var ray = h.getLevel().clip(new net.minecraft.world.level.ClipContext(start, end, net.minecraft.world.level.ClipContext.Block.OUTLINE, net.minecraft.world.level.ClipContext.Fluid.NONE, context(h, facing, ItemStack.EMPTY).getPlayer()));
            h.assertTrue(ray.getType() == HitResult.Type.BLOCK, "Static front outline is selectable " + facing);
            h.assertTrue(LargeSteamTurbineBlock.masterPos(ray.getBlockPos(), h.getLevel().getBlockState(ray.getBlockPos())).equals(origin), "Ray hit resolves controller");
            var probe = net.minecraft.world.entity.EntityType.PIG.create(h.getLevel());
            h.assertTrue(probe != null, "Collision probe created");
            probe.setPos(start);
            probe.move(net.minecraft.world.entity.MoverType.SELF,
                    com.skyeshade.skyent.content.model.MachineModelTransform.direction(new Vec3(0, 0, 3), facing));
            h.assertTrue(probe.position().distanceTo(start) < 1, "Entity movement is stopped by the static chassis " + facing);
            probe.discard();
            h.getLevel().destroyBlock(origin, false);
        }
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void buildHeightRejects(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.CREATIVE);
        var origin = new BlockPos(h.absolutePos(ORIGIN).getX(), h.getLevel().getMaxBuildHeight() - 2, h.absolutePos(ORIGIN).getZ());
        var stack = new ItemStack(ModItems.LARGE_STEAM_TURBINE.get());
        var ctx = new BlockPlaceContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(Vec3.atCenterOf(origin), Direction.UP, origin, false));
        h.assertTrue(!ModItems.LARGE_STEAM_TURBINE.get().place(ctx).consumesAction(), "Full height must fit");
        h.assertTrue(h.getLevel().getBlockState(origin).isAir(), "No partial build-height placement");
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void canopySupportsPlayerAndSelectsBothSides(GameTestHelper h) {
        var transform = com.skyeshade.skyent.content.model.MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        BlockPos origin = h.absolutePos(ORIGIN);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            h.assertTrue(ModItems.LARGE_STEAM_TURBINE.get().place(context(h, facing, new ItemStack(ModItems.LARGE_STEAM_TURBINE.get()))).consumesAction(), "Place canopy test");
            var player = h.makeMockPlayer(GameType.SURVIVAL);
            Vec3 top = transform.relative(new Vec3(1.5, 3, 2.3), facing).add(Vec3.atLowerCornerOf(origin));
            player.setPos(top);
            player.move(net.minecraft.world.entity.MoverType.SELF, new Vec3(0, -2, 0));
            double height = player.getY() - origin.getY();
            h.assertTrue(height >= 2.03 && height < 2.13, "Player stands on thin glass, height=" + height + " " + facing);
            for (int i = 0; i < 8; i++) {
                player.move(net.minecraft.world.entity.MoverType.SELF, com.skyeshade.skyent.content.model.MachineModelTransform.direction(new Vec3(0, -.08, .12), facing));
                h.assertTrue(Math.abs(player.getY() - origin.getY() - height) < .02, "Walking does not fall through glass");
            }
            Vec3 center = transform.relative(new Vec3(1.5, 2.03125, 3.1), facing).add(Vec3.atLowerCornerOf(origin));
            for (int sign : new int[]{-1, 1}) {
                var ray = h.getLevel().clip(new ClipContext(center.add(0, sign * .35, 0), center.add(0, -sign * .1, 0), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                h.assertTrue(ray.getType() == HitResult.Type.BLOCK, "Glass selectable from " + sign + " " + facing);
                h.assertTrue(Math.abs(ray.getLocation().y - center.y) < .03, "Glass outline follows authored height");
                h.assertTrue(LargeSteamTurbineBlock.resolveDestroyProgressPos(h.getLevel(), ray.getBlockPos()).equals(origin), "Glass hit maps destroy progress to controller");
            }
            player.discard();
            h.getLevel().destroyBlock(origin, false);
        }
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void cachedCellsMatchSharedFacingTransform(GameTestHelper h) {
        var def = com.skyeshade.skyent.content.model.MeshMachineDefinition.LARGE_STEAM_TURBINE;
        var data = MeshMachineShapeCache.data();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            ModelMultiblocks.forEachLocal(def.multiblock(), (x, y, z) -> {
                var offset = ModelMultiblocks.rotateLocalOffset(def.multiblock(), new BlockPos(x, y, z), facing);
                var boxes = data.collision().shapeForLocal(x, y, z, facing).toAabbs();
                for (AABB b : data.collisionBoxes()) {
                    double ax = Math.max(x, b.minX), ay = Math.max(y, b.minY), az = Math.max(z, b.minZ);
                    double bx = Math.min(x + 1, b.maxX), by = Math.min(y + 1, b.maxY), bz = Math.min(z + 1, b.maxZ);
                    if (bx <= ax || by <= ay || bz <= az) continue;
                    Vec3 expected = def.transform().relative(new Vec3((ax + bx) / 2, (ay + by) / 2, (az + bz) / 2), facing).subtract(Vec3.atLowerCornerOf(offset));
                    h.assertTrue(boxes.stream().anyMatch(box -> box.inflate(1e-8).contains(expected)), "Merged box and cell shape agree " + facing + " " + expected);
                }
                for (AABB local : boxes) {
                    Vec3 world = local.getCenter().add(Vec3.atLowerCornerOf(offset));
                    h.assertTrue(data.collisionBoxes().stream().map(b -> def.transform().relativeBounds(b, facing)).anyMatch(b -> b.inflate(1e-8).contains(world)), "Cell shape contains no shifted boxes " + facing);
                }
            });
        }
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void exactBoxesSplitWithoutSnappingForAllFacings(GameTestHelper h) {
        var transform = com.skyeshade.skyent.content.model.MeshMachineDefinition.LARGE_STEAM_TURBINE.transform();
        AABB exact = new AABB(.137, .219, .347, 2.831, 2.719, 4.613);
        var cells = new com.skyeshade.skyent.content.shape.MeshCellShapes(java.util.List.of(exact), transform);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            double[] volume = {0};
            ModelMultiblocks.forEachLocal(transform.multiblock(), (x, y, z) -> {
                AABB clipped = exact.intersect(new AABB(x, y, z, x + 1, y + 1, z + 1));
                var offset = ModelMultiblocks.rotateLocalOffset(transform.multiblock(), new BlockPos(x, y, z), facing);
                var boxes = cells.shapeForLocal(x, y, z, facing).toAabbs();
                h.assertTrue(boxes.size() == 1, "A split exact solid remains one box " + facing);
                AABB expected = transform.relativeBounds(clipped, facing).move(-offset.getX(), -offset.getY(), -offset.getZ());
                AABB actual = boxes.getFirst();
                h.assertTrue(Math.abs(expected.minX - actual.minX) < 1e-12 && Math.abs(expected.minY - actual.minY) < 1e-12
                                && Math.abs(expected.minZ - actual.minZ) < 1e-12 && Math.abs(expected.maxX - actual.maxX) < 1e-12
                                && Math.abs(expected.maxY - actual.maxY) < 1e-12 && Math.abs(expected.maxZ - actual.maxZ) < 1e-12,
                        "Exact cell bounds and shared transform agree " + facing);
                volume[0] += actual.getXsize() * actual.getYsize() * actual.getZsize();
            });
            h.assertTrue(Math.abs(volume[0] - exact.getXsize() * exact.getYsize() * exact.getZsize()) < 1e-10,
                    "Cell splitting preserves total volume " + facing);
        }
        var data = MeshMachineShapeCache.data();
        h.assertTrue(data.collisionHybrid() != null && data.outlineHybrid() != null, "Hybrid cache initialized without proxy fallback");
        h.assertTrue(data.collisionHybrid().cuboids().size() == 3, "Solid top covers use three exact cuboids");
        h.assertTrue(data.collisionHybrid().sheets().size() > 20, "Chassis rectangles bypass voxelization");
        h.assertTrue(data.outlineHybrid().cuboids().size() > 20 && data.outlineHybrid().fallbackTriangles() > 0,
                "Outline combines exact primitives with sloped geometry");
        h.succeed();
    }

    @GameTest(template = "mesh_empty")
    public static void legacyRegistrations(GameTestHelper h) {
        for (Block block : new Block[]{ModBlocks.LV_STEAM_TURBINE.get(), ModBlocks.ROLLING_MILL.get(), ModBlocks.CENTRIFUGE.get(), ModBlocks.ARC_FURNACE.get()}) {
            BlockPos pos = h.absolutePos(ORIGIN);
            h.getLevel().setBlock(pos, block.defaultBlockState(), 3);
            h.assertTrue(h.getLevel().getBlockEntity(pos) != null, "Legacy controller entity " + block);
            h.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        h.succeed();
    }
}
