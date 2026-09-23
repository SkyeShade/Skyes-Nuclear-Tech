package com.skyeshade.skyent.client.model;

import com.google.gson.*;
import com.skyeshade.skyent.content.model.*;
import com.skyeshade.skyent.content.model.blockbench.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.geometry.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;

/**
 * Explicit opt-in model loader; each bake owns all its sprites and four immutable facing batches.
 */
public record BlockbenchMeshGeometry(
        MeshMachineDefinition definition) implements IUnbakedGeometry<BlockbenchMeshGeometry> {
    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
                           ModelState state, ItemOverrides overrides) {
        if (!state.getRotation().isIdentity() || !context.getRootTransform().isIdentity())
            throw new IllegalArgumentException("Mesh machine orientation belongs to MachineModelTransform, not blockstate rotations");
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(definition.source())) {
            var scene = definition.parse(reader);
            var compiled = definition.compileStatic(scene);
            TextureAtlasSprite[] materials = scene.textures().stream()
                    .map(t -> sprites.apply(new Material(TextureAtlas.LOCATION_BLOCKS, definition.materials().get(t.uuid()))))
                    .toArray(TextureAtlasSprite[]::new);
            Map<Direction, List<BakedQuad>> faces = new EnumMap<>(Direction.class);
            Map<Direction, AABB> bounds = new EnumMap<>(Direction.class);
            Map<Direction, Map<String, AABB>> parts = new EnumMap<>(Direction.class);
            Map<Direction, List<Float>> shades = new EnumMap<>(Direction.class);
            List<BakedQuad> itemQuads = new ArrayList<>();
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                List<BakedQuad> quads = new ArrayList<>();
                List<Float> faceShades = new ArrayList<>();
                Map<String, AABB> partBounds = new LinkedHashMap<>();
                AABB box = null;
                for (var triangle : compiled.renderTriangles()) {
                    Vec3 normal = MachineModelTransform.direction(triangle.normal(), facing);
                    faceShades.add(MeshDirectionalShade.factor(normal));
                    TextureAtlasSprite sprite = materials[triangle.texture()];
                    int[] data = new int[4 * IQuadTransformer.STRIDE];
                    var vertices = List.of(triangle.a(), triangle.b(), triangle.c(), triangle.c());
                    for (int i = 0; i < 4; i++) {
                        var vertex = vertices.get(i);
                        Vec3 p = definition.transform().authoredRelative(vertex.position(), facing);
                        AABB point = new AABB(p, p);
                        box = box == null ? point : box.minmax(point);
                        partBounds.merge(triangle.path() + " [" + triangle.part() + "]", point, AABB::minmax);
                        int base = i * IQuadTransformer.STRIDE;
                        data[base + IQuadTransformer.POSITION] = Float.floatToRawIntBits((float) p.x);
                        data[base + IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits((float) p.y);
                        data[base + IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits((float) p.z);
                        data[base + IQuadTransformer.COLOR] = 0xFFFFFFFF;
                        data[base + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU((float) vertex.uv().u()));
                        data[base + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV((float) vertex.uv().v()));
                        data[base + IQuadTransformer.NORMAL] = packNormal(normal);
                    }
                    quads.add(new BakedQuad(data, -1, Direction.getNearest(normal.x, normal.y, normal.z), sprite, true));
                    if (facing == Direction.NORTH) {
                        int[] itemData = data.clone();
                        for (int i = 0; i < 4; i++) {
                            Vec3 p = MeshItemPresentation.position(vertices.get(i).position(),definition.transform(),scene.display().present());
                            int base = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                            itemData[base] = Float.floatToRawIntBits((float)p.x);
                            itemData[base+1] = Float.floatToRawIntBits((float)p.y);
                            itemData[base+2] = Float.floatToRawIntBits((float)p.z);
                        }
                        itemQuads.add(new BakedQuad(itemData,-1,Direction.getNearest(normal.x,normal.y,normal.z),sprite,true));
                    }
                }
                if (box == null) throw new IllegalArgumentException("Empty static mesh");
                faces.put(facing, List.copyOf(quads));
                shades.put(facing, List.copyOf(faceShades));
                bounds.put(facing, box.inflate(.05));
                parts.put(facing, Map.copyOf(partBounds));
            }
            return new MeshBakedModel(faces, bounds, parts, shades, materials[0], MeshItemPresentation.transforms(scene.display(),context.getTransforms()), overrides,
                    context.getRenderType(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cutout")), itemQuads);
        } catch (IOException exception) {
            throw new JsonParseException("Cannot load mesh " + definition.source(), exception);
        }
    }

    private static int packNormal(Vec3 normal) {
        return ((byte) Math.round(normal.x * 127) & 255) | (((byte) Math.round(normal.y * 127) & 255) << 8) | (((byte) Math.round(normal.z * 127) & 255) << 16);
    }

    public static final class Loader implements IGeometryLoader<BlockbenchMeshGeometry> {
        public static final Loader INSTANCE = new Loader();

        @Override
        public BlockbenchMeshGeometry read(JsonObject json, JsonDeserializationContext context) {
            if (!json.has("machine") || !"skyent:large_steam_turbine".equals(json.get("machine").getAsString()))
                throw new JsonParseException("Unknown mesh machine definition");
            return new BlockbenchMeshGeometry(MeshMachineDefinition.LARGE_STEAM_TURBINE);
        }
    }
}
