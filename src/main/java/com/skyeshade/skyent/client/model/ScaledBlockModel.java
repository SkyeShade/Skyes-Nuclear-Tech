package com.skyeshade.skyent.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.math.Transformation;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.jetbrains.annotations.Nullable;

public class ScaledBlockModel implements IUnbakedGeometry<ScaledBlockModel> {
    private static final float DEFAULT_ORIGIN_X = 8.0F;
    private static final float DEFAULT_ORIGIN_Y = 0.0F;
    private static final float DEFAULT_ORIGIN_Z = 8.0F;

    private final BlockModel baseModel;
    private final float scale;
    private final float originX;
    private final float originY;
    private final float originZ;

    public ScaledBlockModel(BlockModel baseModel, float scale, float originX, float originY, float originZ) {
        this.baseModel = baseModel;
        this.scale = scale;
        this.originX = originX / 16.0F;
        this.originY = originY / 16.0F;
        this.originZ = originZ / 16.0F;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        BakedModel bakedBase = UnbakedGeometryHelper.bake(baseModel, baker, baseModel, spriteGetter, modelState, context.isGui3d());
        return new Baked(bakedBase, new ScaleTransformer(scale, originX, originY, originZ));
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        baseModel.resolveParents(modelGetter);
    }

    private static final class Baked extends BakedModelWrapper<BakedModel> {
        private final IQuadTransformer transformer;

        private Baked(BakedModel originalModel, IQuadTransformer transformer) {
            super(originalModel);
            this.transformer = transformer;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return transformer.process(originalModel.getQuads(state, side, rand));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
            return transformer.process(originalModel.getQuads(state, side, rand, extraData, renderType));
        }
    }

    private record ScaleTransformer(float scale, float originX, float originY, float originZ) implements IQuadTransformer {
        @Override
        public void processInPlace(BakedQuad quad) {
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                float x = Float.intBitsToFloat(vertices[offset]);
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                float z = Float.intBitsToFloat(vertices[offset + 2]);

                vertices[offset] = Float.floatToRawIntBits(originX + (x - originX) * scale);
                vertices[offset + 1] = Float.floatToRawIntBits(originY + (y - originY) * scale);
                vertices[offset + 2] = Float.floatToRawIntBits(originZ + (z - originZ) * scale);
            }
        }
    }

    public static final class Loader implements IGeometryLoader<ScaledBlockModel> {
        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public ScaledBlockModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
            if (!jsonObject.has("base")) {
                throw new JsonParseException("Scaled block model requires a \"base\" model object.");
            }

            BlockModel baseModel = deserializationContext.deserialize(jsonObject.get("base"), BlockModel.class);
            float scale = GsonHelper.getAsFloat(jsonObject, "scale", 1.0F);
            float originX = DEFAULT_ORIGIN_X;
            float originY = DEFAULT_ORIGIN_Y;
            float originZ = DEFAULT_ORIGIN_Z;
            if (jsonObject.has("origin")) {
                var origin = GsonHelper.getAsJsonArray(jsonObject, "origin");
                if (origin.size() != 3) {
                    throw new JsonParseException("Scaled block model origin must contain exactly three numbers.");
                }
                originX = origin.get(0).getAsFloat();
                originY = origin.get(1).getAsFloat();
                originZ = origin.get(2).getAsFloat();
            }

            return new ScaledBlockModel(baseModel, scale, originX, originY, originZ);
        }
    }
}
