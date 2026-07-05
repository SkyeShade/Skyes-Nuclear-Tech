package com.skyeshade.skyent.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.geometry.UnbakedGeometryHelper;
import org.jetbrains.annotations.Nullable;

public class SlicedScaledBlockModel implements IUnbakedGeometry<SlicedScaledBlockModel> {
    private static final float DEFAULT_ORIGIN_X = 8.0F;
    private static final float DEFAULT_ORIGIN_Y = 0.0F;
    private static final float DEFAULT_ORIGIN_Z = 8.0F;

    private final BlockModel baseModel;
    private final float scale;
    private final float originX;
    private final float originY;
    private final float originZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final String sliceXProperty;
    private final String sliceYProperty;
    private final String sliceZProperty;
    private final String facingProperty;
    @Nullable
    private final Boolean ambientOcclusion;
    private final SharedLightingMode sharedLightingMode;
    private final boolean disableDiffuseShading;
    private final boolean forceUniformLight;

    public SlicedScaledBlockModel(BlockModel baseModel, float scale, float originX, float originY, float originZ, int sizeX, int sizeY, int sizeZ, String sliceXProperty, String sliceYProperty, String sliceZProperty, String facingProperty, @Nullable Boolean ambientOcclusion, SharedLightingMode sharedLightingMode, boolean disableDiffuseShading, boolean forceUniformLight) {
        this.baseModel = baseModel;
        this.scale = scale;
        this.originX = originX / 16.0F;
        this.originY = originY / 16.0F;
        this.originZ = originZ / 16.0F;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.sliceXProperty = sliceXProperty;
        this.sliceYProperty = sliceYProperty;
        this.sliceZProperty = sliceZProperty;
        this.facingProperty = facingProperty;
        this.ambientOcclusion = ambientOcclusion;
        this.sharedLightingMode = sharedLightingMode;
        this.disableDiffuseShading = disableDiffuseShading;
        this.forceUniformLight = forceUniformLight;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        BakedModel bakedBase = UnbakedGeometryHelper.bake(baseModel, baker, baseModel, spriteGetter, modelState, context.isGui3d());
        return new Baked(bakedBase, scale, originX, originY, originZ, sizeX, sizeY, sizeZ, sliceXProperty, sliceYProperty, sliceZProperty, facingProperty, ambientOcclusion, sharedLightingMode, disableDiffuseShading, forceUniformLight);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        baseModel.resolveParents(modelGetter);
    }

    private static final class Baked extends BakedModelWrapper<BakedModel> implements SharedLightingBakedModel {
        private final float scale;
        private final float originX;
        private final float originY;
        private final float originZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final String sliceXProperty;
        private final String sliceYProperty;
        private final String sliceZProperty;
        private final String facingProperty;
        @Nullable
        private final Boolean ambientOcclusion;
        private final SharedLightingMode sharedLightingMode;
        private final boolean disableDiffuseShading;
        private final boolean forceUniformLight;

        private Baked(BakedModel originalModel, float scale, float originX, float originY, float originZ, int sizeX, int sizeY, int sizeZ, String sliceXProperty, String sliceYProperty, String sliceZProperty, String facingProperty, @Nullable Boolean ambientOcclusion, SharedLightingMode sharedLightingMode, boolean disableDiffuseShading, boolean forceUniformLight) {
            super(originalModel);
            this.scale = scale;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.sliceXProperty = sliceXProperty;
            this.sliceYProperty = sliceYProperty;
            this.sliceZProperty = sliceZProperty;
            this.facingProperty = facingProperty;
            this.ambientOcclusion = ambientOcclusion;
            this.sharedLightingMode = sharedLightingMode;
            this.disableDiffuseShading = disableDiffuseShading;
            this.forceUniformLight = forceUniformLight;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion != null ? ambientOcclusion : originalModel.useAmbientOcclusion();
        }

        @Override
        public boolean skyent$usesSharedLighting() {
            return sharedLightingMode != SharedLightingMode.PER_BLOCK;
        }

        @Override
        public int skyent$getSharedLight(BlockAndTintGetter level, BlockState state, BlockPos pos, @Nullable Direction direction) {
            if (sharedLightingMode == SharedLightingMode.PER_BLOCK) {
                return SharedLightingBakedModel.NO_SHARED_LIGHT;
            }

            int sliceX = getIntStateValue(state, sliceXProperty, 0);
            int sliceY = getIntStateValue(state, sliceYProperty, 0);
            int sliceZ = getIntStateValue(state, sliceZProperty, 0);
            Direction facing = getDirectionStateValue(state, facingProperty, Direction.NORTH);
            BlockPos controllerPos = pos.subtract(rotateLocalOffset(new BlockPos(sliceX, sliceY, sliceZ), facing));
            if (sharedLightingMode == SharedLightingMode.CONTROLLER) {
                return sampleLight(level, controllerPos);
            }
            if (sharedLightingMode == SharedLightingMode.DIRECTIONAL_MAX) {
                return direction == null ? SharedLightingBakedModel.NO_SHARED_LIGHT : sampleDirectionalMaxLight(level, controllerPos, facing, direction);
            }

            int sky = 0;
            int block = 0;
            int count = sizeX * sizeY * sizeZ;
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        int light = sampleCellLight(level, controllerPos.offset(rotateLocalOffset(new BlockPos(x, y, z), facing)));
                        int sampleSky = LightTexture.sky(light);
                        int sampleBlock = LightTexture.block(light);
                        if (sharedLightingMode == SharedLightingMode.MAX) {
                            sky = Math.max(sky, sampleSky);
                            block = Math.max(block, sampleBlock);
                        } else {
                            sky += sampleSky;
                            block += sampleBlock;
                        }
                    }
                }
            }
            if (sharedLightingMode == SharedLightingMode.AVERAGE) {
                sky = Math.round((float) sky / count);
                block = Math.round((float) block / count);
            }
            return LightTexture.pack(block, sky);
        }

        private int sampleDirectionalMaxLight(BlockAndTintGetter level, BlockPos controllerPos, Direction facing, Direction direction) {
            int sky = 0;
            int block = 0;
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        BlockPos cellPos = controllerPos.offset(rotateLocalOffset(new BlockPos(x, y, z), facing));
                        int light = sampleLight(level, cellPos.relative(direction));
                        sky = Math.max(sky, LightTexture.sky(light));
                        block = Math.max(block, LightTexture.block(light));
                    }
                }
            }
            return LightTexture.pack(block, sky);
        }

        private static int sampleLight(BlockAndTintGetter level, BlockPos pos) {
            return LevelRenderer.getLightColor(level, level.getBlockState(pos), pos);
        }

        private static int sampleCellLight(BlockAndTintGetter level, BlockPos pos) {
            int centerLight = sampleLight(level, pos);
            int sky = LightTexture.sky(centerLight);
            int block = LightTexture.block(centerLight);
            for (Direction direction : Direction.values()) {
                int light = sampleLight(level, pos.relative(direction));
                sky = Math.max(sky, LightTexture.sky(light));
                block = Math.max(block, LightTexture.block(light));
            }
            return LightTexture.pack(block, sky);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return sliceQuads(originalModel.getQuads(state, null, rand), state, side);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
            return sliceQuads(originalModel.getQuads(state, null, rand, extraData, renderType), state, side);
        }

        private List<BakedQuad> sliceQuads(List<BakedQuad> quads, @Nullable BlockState state, @Nullable Direction side) {
            int sliceX = getIntStateValue(state, sliceXProperty, 0);
            int sliceY = getIntStateValue(state, sliceYProperty, 0);
            int sliceZ = getIntStateValue(state, sliceZProperty, 0);
            Direction facing = getDirectionStateValue(state, facingProperty, Direction.NORTH);
            List<BakedQuad> sliced = new ArrayList<>();
            for (BakedQuad quad : quads) {
                for (BakedQuad transformed : transformQuad(quad, sliceX, sliceY, sliceZ, facing)) {
                    if (side == null || transformed.getDirection() == side) {
                        sliced.add(transformed);
                    }
                }
            }
            return sliced;
        }

        private List<BakedQuad> transformQuad(BakedQuad quad, int sliceX, int sliceY, int sliceZ, Direction facing) {
            if (sliceX < 0 || sliceX >= sizeX || sliceY < 0 || sliceY >= sizeY || sliceZ < 0 || sliceZ >= sizeZ) {
                return List.of();
            }

            List<Vertex> polygon = new ArrayList<>(4);
            int[] sourceVertices = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                polygon.add(Vertex.from(sourceVertices, vertex, scale, originX, originY, originZ));
            }

            polygon = clipPolygon(polygon, Axis.X, sliceX, true);
            polygon = clipPolygon(polygon, Axis.X, sliceX + 1.0F, false);
            polygon = clipPolygon(polygon, Axis.Y, sliceY, true);
            polygon = clipPolygon(polygon, Axis.Y, sliceY + 1.0F, false);
            polygon = clipPolygon(polygon, Axis.Z, sliceZ, true);
            polygon = clipPolygon(polygon, Axis.Z, sliceZ + 1.0F, false);
            if (polygon.size() < 3) {
                return List.of();
            }

            List<Vertex> localPolygon = new ArrayList<>(polygon.size());
            for (Vertex vertex : polygon) {
                localPolygon.add(vertex.rebasedAndRotated(sliceX, sliceY, sliceZ, facing));
            }

            List<BakedQuad> result = new ArrayList<>();
            if (localPolygon.size() == 4) {
                result.add(createQuad(localPolygon.get(0), localPolygon.get(1), localPolygon.get(2), localPolygon.get(3), quad, facing));
            } else {
                for (int index = 1; index < localPolygon.size() - 1; index++) {
                    Vertex first = localPolygon.get(0);
                    Vertex second = localPolygon.get(index);
                    Vertex third = localPolygon.get(index + 1);
                    result.add(createQuad(first, second, third, third, quad, facing));
                }
            }
            return result;
        }

        private static List<Vertex> clipPolygon(List<Vertex> polygon, Axis axis, float plane, boolean keepGreater) {
            if (polygon.isEmpty()) {
                return polygon;
            }

            List<Vertex> clipped = new ArrayList<>();
            Vertex previous = polygon.get(polygon.size() - 1);
            float previousDistance = distance(previous, axis, plane, keepGreater);
            boolean previousInside = previousDistance >= -1.0E-5F;
            for (Vertex current : polygon) {
                float currentDistance = distance(current, axis, plane, keepGreater);
                boolean currentInside = currentDistance >= -1.0E-5F;
                if (currentInside != previousInside) {
                    float t = previousDistance / (previousDistance - currentDistance);
                    clipped.add(Vertex.interpolate(previous, current, t));
                }
                if (currentInside) {
                    clipped.add(current);
                }
                previous = current;
                previousDistance = currentDistance;
                previousInside = currentInside;
            }
            return clipped;
        }

        private static float distance(Vertex vertex, Axis axis, float plane, boolean keepGreater) {
            float value = vertex.get(axis);
            return keepGreater ? value - plane : plane - value;
        }

        private BakedQuad createQuad(Vertex first, Vertex second, Vertex third, Vertex fourth, BakedQuad source, Direction facing) {
            int[] vertices = new int[IQuadTransformer.STRIDE * 4];
            first.copyTo(vertices, 0);
            second.copyTo(vertices, 1);
            third.copyTo(vertices, 2);
            fourth.copyTo(vertices, 3);
            if (forceUniformLight) {
                sanitizeVertexLighting(vertices);
            }
            Direction direction = calculateDirection(first, second, third, rotateDirection(source.getDirection(), facing));
            return new BakedQuad(vertices, source.getTintIndex(), direction, source.getSprite(), !disableDiffuseShading && source.isShade(), ambientOcclusion != null ? ambientOcclusion : source.hasAmbientOcclusion());
        }

        private static void sanitizeVertexLighting(int[] vertices) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE;
                vertices[offset + IQuadTransformer.COLOR] = 0xFFFFFFFF;
                vertices[offset + IQuadTransformer.UV2] = 0;
                vertices[offset + IQuadTransformer.NORMAL] = 0;
            }
        }

        private static Direction calculateDirection(Vertex first, Vertex second, Vertex third, Direction fallback) {
            float ax = second.x() - first.x();
            float ay = second.y() - first.y();
            float az = second.z() - first.z();
            float bx = third.x() - first.x();
            float by = third.y() - first.y();
            float bz = third.z() - first.z();
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            float absX = Math.abs(nx);
            float absY = Math.abs(ny);
            float absZ = Math.abs(nz);
            if (absX < 1.0E-6F && absY < 1.0E-6F && absZ < 1.0E-6F) {
                return fallback;
            }
            if (absX >= absY && absX >= absZ) {
                return nx >= 0.0F ? Direction.EAST : Direction.WEST;
            }
            if (absY >= absZ) {
                return ny >= 0.0F ? Direction.UP : Direction.DOWN;
            }
            return nz >= 0.0F ? Direction.SOUTH : Direction.NORTH;
        }

        private static Direction rotateDirection(Direction direction, Direction facing) {
            if (direction.getAxis().isVertical()) {
                return direction;
            }
            return switch (facing) {
                case EAST -> direction.getClockWise();
                case SOUTH -> direction.getOpposite();
                case WEST -> direction.getCounterClockWise();
                default -> direction;
            };
        }

        private static float rotateX(float x, float z, Direction facing) {
            float centeredX = x - 0.5F;
            float centeredZ = z - 0.5F;
            return switch (facing) {
                case EAST -> 0.5F - centeredZ;
                case SOUTH -> 0.5F - centeredX;
                case WEST -> 0.5F + centeredZ;
                default -> x;
            };
        }

        private static float rotateZ(float x, float z, Direction facing) {
            float centeredX = x - 0.5F;
            float centeredZ = z - 0.5F;
            return switch (facing) {
                case EAST -> 0.5F + centeredX;
                case SOUTH -> 0.5F - centeredZ;
                case WEST -> 0.5F - centeredX;
                default -> z;
            };
        }

        private static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
            int x = local.getX();
            int y = local.getY();
            int z = local.getZ();
            return switch (facing) {
                case NORTH -> new BlockPos(x, y, z);
                case EAST -> new BlockPos(-z, y, x);
                case SOUTH -> new BlockPos(-x, y, -z);
                case WEST -> new BlockPos(z, y, -x);
                default -> local;
            };
        }

        private enum Axis {
            X,
            Y,
            Z
        }

        private static final class Vertex {
            private static final int UV_U = 4;
            private static final int UV_V = 5;

            private final int[] data;

            private Vertex(int[] data) {
                this.data = data;
            }

            private static Vertex from(int[] source, int vertex, float scale, float originX, float originY, float originZ) {
                int[] data = new int[IQuadTransformer.STRIDE];
                System.arraycopy(source, vertex * IQuadTransformer.STRIDE, data, 0, IQuadTransformer.STRIDE);
                float x = Float.intBitsToFloat(data[IQuadTransformer.POSITION]);
                float y = Float.intBitsToFloat(data[IQuadTransformer.POSITION + 1]);
                float z = Float.intBitsToFloat(data[IQuadTransformer.POSITION + 2]);
                data[IQuadTransformer.POSITION] = Float.floatToRawIntBits(originX + (x - originX) * scale);
                data[IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(originY + (y - originY) * scale);
                data[IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(originZ + (z - originZ) * scale);
                return new Vertex(data);
            }

            private static Vertex interpolate(Vertex first, Vertex second, float t) {
                int[] data = new int[IQuadTransformer.STRIDE];
                for (int index = 0; index < data.length; index++) {
                    if (index >= IQuadTransformer.POSITION && index <= IQuadTransformer.POSITION + 2) {
                        data[index] = Float.floatToRawIntBits(lerp(Float.intBitsToFloat(first.data[index]), Float.intBitsToFloat(second.data[index]), t));
                    } else if (index == UV_U || index == UV_V) {
                        data[index] = Float.floatToRawIntBits(lerp(Float.intBitsToFloat(first.data[index]), Float.intBitsToFloat(second.data[index]), t));
                    } else {
                        data[index] = t < 0.5F ? first.data[index] : second.data[index];
                    }
                }
                return new Vertex(data);
            }

            private Vertex rebasedAndRotated(int sliceX, int sliceY, int sliceZ, Direction facing) {
                int[] rebased = data.clone();
                float localX = x() - sliceX;
                float localY = y() - sliceY;
                float localZ = z() - sliceZ;
                rebased[IQuadTransformer.POSITION] = Float.floatToRawIntBits(rotateX(localX, localZ, facing));
                rebased[IQuadTransformer.POSITION + 1] = Float.floatToRawIntBits(localY);
                rebased[IQuadTransformer.POSITION + 2] = Float.floatToRawIntBits(rotateZ(localX, localZ, facing));
                return new Vertex(rebased);
            }

            private void copyTo(int[] target, int vertex) {
                System.arraycopy(data, 0, target, vertex * IQuadTransformer.STRIDE, IQuadTransformer.STRIDE);
            }

            private float get(Axis axis) {
                return switch (axis) {
                    case X -> x();
                    case Y -> y();
                    case Z -> z();
                };
            }

            private float x() {
                return Float.intBitsToFloat(data[IQuadTransformer.POSITION]);
            }

            private float y() {
                return Float.intBitsToFloat(data[IQuadTransformer.POSITION + 1]);
            }

            private float z() {
                return Float.intBitsToFloat(data[IQuadTransformer.POSITION + 2]);
            }

            private static float lerp(float first, float second, float t) {
                return first + (second - first) * t;
            }
        }

        private static int getIntStateValue(@Nullable BlockState state, String propertyName, int fallback) {
            if (state == null) {
                return fallback;
            }
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(propertyName)) {
                    return parseIntStateValue(state, property, fallback);
                }
            }
            return fallback;
        }

        private static <T extends Comparable<T>> int parseIntStateValue(BlockState state, Property<T> property, int fallback) {
            T value = state.getValue(property);
            if (value instanceof Number number) {
                return number.intValue();
            }
            try {
                return Integer.parseInt(property.getName(value));
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }

        private static Direction getDirectionStateValue(@Nullable BlockState state, String propertyName, Direction fallback) {
            if (state == null) {
                return fallback;
            }
            for (Property<?> property : state.getProperties()) {
                if (property.getName().equals(propertyName)) {
                    return parseDirectionStateValue(state, property, fallback);
                }
            }
            return fallback;
        }

        private static <T extends Comparable<T>> Direction parseDirectionStateValue(BlockState state, Property<T> property, Direction fallback) {
            T value = state.getValue(property);
            if (value instanceof Direction direction) {
                return direction;
            }
            try {
                return Direction.byName(property.getName(value));
            } catch (IllegalArgumentException exception) {
                return fallback;
            }
        }
    }

    public enum SharedLightingMode {
        PER_BLOCK,
        MAX,
        AVERAGE,
        CONTROLLER,
        DIRECTIONAL_MAX;

        private static SharedLightingMode fromJson(JsonObject jsonObject) {
            String value = GsonHelper.getAsString(jsonObject, "shared_lighting", "per_block");
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "per_block" -> PER_BLOCK;
                case "max" -> MAX;
                case "average" -> AVERAGE;
                case "controller" -> CONTROLLER;
                case "directional_max" -> DIRECTIONAL_MAX;
                default -> throw new JsonParseException("Unknown sliced scaled block model shared_lighting mode: " + value);
            };
        }
    }

    public static final class Loader implements IGeometryLoader<SlicedScaledBlockModel> {
        public static final Loader INSTANCE = new Loader();

        private Loader() {
        }

        @Override
        public SlicedScaledBlockModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
            if (!jsonObject.has("base")) {
                throw new JsonParseException("Sliced scaled block model requires a \"base\" model object.");
            }

            BlockModel baseModel = deserializationContext.deserialize(jsonObject.get("base"), BlockModel.class);
            float scale = GsonHelper.getAsFloat(jsonObject, "scale", 1.0F);
            float originX = DEFAULT_ORIGIN_X;
            float originY = DEFAULT_ORIGIN_Y;
            float originZ = DEFAULT_ORIGIN_Z;
            if (jsonObject.has("origin")) {
                var origin = GsonHelper.getAsJsonArray(jsonObject, "origin");
                if (origin.size() != 3) {
                    throw new JsonParseException("Sliced scaled block model origin must contain exactly three numbers.");
                }
                originX = origin.get(0).getAsFloat();
                originY = origin.get(1).getAsFloat();
                originZ = origin.get(2).getAsFloat();
            }

            var size = GsonHelper.getAsJsonArray(jsonObject, "size");
            if (size.size() != 3) {
                throw new JsonParseException("Sliced scaled block model size must contain exactly three integers.");
            }
            var sliceProperties = GsonHelper.getAsJsonArray(jsonObject, "slice_properties");
            if (sliceProperties.size() != 3) {
                throw new JsonParseException("Sliced scaled block model slice_properties must contain exactly three property names.");
            }

            int sizeX = size.get(0).getAsInt();
            int sizeY = size.get(1).getAsInt();
            int sizeZ = size.get(2).getAsInt();
            if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                throw new JsonParseException("Sliced scaled block model size values must be positive.");
            }
            Boolean ambientOcclusion = null;
            if (jsonObject.has("ambient_occlusion")) {
                ambientOcclusion = GsonHelper.getAsBoolean(jsonObject, "ambient_occlusion");
            } else if (jsonObject.has("use_ambient_occlusion")) {
                ambientOcclusion = GsonHelper.getAsBoolean(jsonObject, "use_ambient_occlusion");
            }
            boolean disableDiffuseShading = GsonHelper.getAsBoolean(jsonObject, "disable_diffuse_shading", false);
            boolean forceUniformLight = GsonHelper.getAsBoolean(jsonObject, "force_uniform_light", false);

            return new SlicedScaledBlockModel(
                    baseModel,
                    scale,
                    originX,
                    originY,
                    originZ,
                    sizeX,
                    sizeY,
                    sizeZ,
                    sliceProperties.get(0).getAsString(),
                    sliceProperties.get(1).getAsString(),
                    sliceProperties.get(2).getAsString(),
                    GsonHelper.getAsString(jsonObject, "facing_property", "facing"),
                    ambientOcclusion,
                    SharedLightingMode.fromJson(jsonObject),
                    disableDiffuseShading,
                    forceUniformLight
            );
        }
    }
}
