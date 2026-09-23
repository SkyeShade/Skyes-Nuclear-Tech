package com.skyeshade.skyent.client.model;

import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.RenderTypeGroup;

import java.util.*;

public final class MeshBakedModel extends SimpleBakedModel {
    private final Map<Direction, List<BakedQuad>> facingQuads;
    private final Map<Direction, AABB> bounds;
    private final Map<Direction, Map<String, AABB>> parts;
    private final Map<Direction, List<Float>> shades;

    public MeshBakedModel(Map<Direction, List<BakedQuad>> quads, Map<Direction, AABB> bounds,
                          Map<Direction, Map<String, AABB>> parts, Map<Direction, List<Float>> shades, TextureAtlasSprite particle,
                          ItemTransforms transforms, ItemOverrides overrides, RenderTypeGroup types) {
        this(quads,bounds,parts,shades,particle,transforms,overrides,types,quads.get(Direction.NORTH));
    }

    public MeshBakedModel(Map<Direction, List<BakedQuad>> quads, Map<Direction, AABB> bounds,
                          Map<Direction, Map<String, AABB>> parts, Map<Direction, List<Float>> shades, TextureAtlasSprite particle,
                          ItemTransforms transforms, ItemOverrides overrides, RenderTypeGroup types, List<BakedQuad> itemQuads) {
        super(List.copyOf(itemQuads), emptyFaces(), false, true, true, particle, transforms, overrides, types);
        this.facingQuads = Map.copyOf(quads);
        this.bounds = Map.copyOf(bounds);
        this.parts = Map.copyOf(parts);
        this.shades = Map.copyOf(shades);
    }

    private static Map<Direction, List<BakedQuad>> emptyFaces() {
        Map<Direction, List<BakedQuad>> result = new EnumMap<>(Direction.class);
        for (Direction d : Direction.values()) result.put(d, List.of());
        return result;
    }

    public List<BakedQuad> quads(Direction facing) {
        return facingQuads.get(facing);
    }

    public AABB bounds(Direction facing) {
        return bounds.get(facing);
    }

    public float shade(Direction facing, int triangle) {
        return shades.get(facing).get(triangle);
    }

    public Map<String, AABB> parts(Direction facing) {
        return parts.get(facing);
    }
}
