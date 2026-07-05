package com.skyeshade.skyent.client.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.skyeshade.skyent.client.render.HeatingChamberRenderDebug;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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
import net.minecraft.util.Mth;
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
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

public class ScaledBlockModel implements IUnbakedGeometry<ScaledBlockModel> {
    private static final AtomicInteger HEATING_CHAMBER_GET_QUADS_LOGS = new AtomicInteger();
    private static final AtomicInteger HEATING_CHAMBER_RUNTIME_LIGHT_LOGS = new AtomicInteger();
    private static final float DEFAULT_ORIGIN_X = 8.0F;
    private static final float DEFAULT_ORIGIN_Y = 0.0F;
    private static final float DEFAULT_ORIGIN_Z = 8.0F;

    private final BlockModel baseModel;
    private final float scale;
    private final float originX;
    private final float originY;
    private final float originZ;
    private final float translateX;
    private final float translateY;
    private final float translateZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final String facingProperty;
    private final SharedLightingMode sharedLightingMode;
    @Nullable
    private final Boolean ambientOcclusion;
    private final boolean disableDiffuseShading;
    private final boolean forceUniformLight;
    private final boolean ignoreNeighborShading;
    private final boolean ignoreCullface;
    private final boolean forceGeneralQuads;
    private final boolean debugForceWhiteFullbright;
    private final boolean bakeStaticFullbright;
    private final boolean runtimeSharedLight;
    private final int sharedLightReduction;
    private final float renderBrightnessMultiplier;
    private final float renderBrightnessFloor;

    public ScaledBlockModel(BlockModel baseModel, float scale, float originX, float originY, float originZ, float translateX, float translateY, float translateZ, int sizeX, int sizeY, int sizeZ, String facingProperty, SharedLightingMode sharedLightingMode, @Nullable Boolean ambientOcclusion, boolean disableDiffuseShading, boolean forceUniformLight, boolean ignoreNeighborShading, boolean ignoreCullface, boolean forceGeneralQuads, boolean debugForceWhiteFullbright, boolean bakeStaticFullbright, boolean runtimeSharedLight, int sharedLightReduction, float renderBrightnessMultiplier, float renderBrightnessFloor) {
        this.baseModel = baseModel;
        this.scale = scale;
        this.originX = originX / 16.0F;
        this.originY = originY / 16.0F;
        this.originZ = originZ / 16.0F;
        this.translateX = translateX / 16.0F;
        this.translateY = translateY / 16.0F;
        this.translateZ = translateZ / 16.0F;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.facingProperty = facingProperty;
        this.sharedLightingMode = sharedLightingMode;
        this.ambientOcclusion = ambientOcclusion;
        this.disableDiffuseShading = disableDiffuseShading;
        this.forceUniformLight = forceUniformLight;
        this.ignoreNeighborShading = ignoreNeighborShading;
        this.ignoreCullface = ignoreCullface;
        this.forceGeneralQuads = forceGeneralQuads;
        this.debugForceWhiteFullbright = debugForceWhiteFullbright;
        this.bakeStaticFullbright = bakeStaticFullbright;
        this.runtimeSharedLight = runtimeSharedLight;
        this.sharedLightReduction = sharedLightReduction;
        this.renderBrightnessMultiplier = renderBrightnessMultiplier;
        this.renderBrightnessFloor = renderBrightnessFloor;
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState, ItemOverrides overrides) {
        if (context.getModelName().contains("heating_chamber")) {
            HeatingChamberRenderDebug.log(
                    "bake modelName={} geometry=ScaledBlockModel base={} shared_lighting={} ambient_occlusion={} ignore_neighbor_shading={} ignore_cullface={} force_general_quads={} debug_force_white_fullbright={} bake_static_fullbright={} runtime_shared_light={} shared_light_reduction={} render_brightness_multiplier={} render_brightness_floor={}",
                    context.getModelName(),
                    baseModel.name,
                    sharedLightingMode,
                    ambientOcclusion,
                    ignoreNeighborShading,
                    ignoreCullface,
                    forceGeneralQuads,
                    debugForceWhiteFullbright,
                    bakeStaticFullbright,
                    runtimeSharedLight,
                    sharedLightReduction,
                    renderBrightnessMultiplier,
                    renderBrightnessFloor
            );
        }
        BakedModel bakedBase = UnbakedGeometryHelper.bake(baseModel, baker, baseModel, spriteGetter, modelState, context.isGui3d());
        return new Baked(bakedBase, new ScaleTransformer(scale, originX, originY, originZ, translateX, translateY, translateZ), sizeX, sizeY, sizeZ, facingProperty, sharedLightingMode, ambientOcclusion, disableDiffuseShading, forceUniformLight, ignoreNeighborShading, ignoreCullface, forceGeneralQuads, debugForceWhiteFullbright, bakeStaticFullbright, runtimeSharedLight, sharedLightReduction, renderBrightnessMultiplier, renderBrightnessFloor);
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        baseModel.resolveParents(modelGetter);
    }

    private static final class Baked extends BakedModelWrapper<BakedModel> implements SharedLightingBakedModel {
        private final IQuadTransformer transformer;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final String facingProperty;
        private final SharedLightingMode sharedLightingMode;
        @Nullable
        private final Boolean ambientOcclusion;
        private final boolean disableDiffuseShading;
        private final boolean forceUniformLight;
        private final boolean ignoreNeighborShading;
        private final boolean ignoreCullface;
        private final boolean forceGeneralQuads;
        private final boolean debugForceWhiteFullbright;
        private final boolean bakeStaticFullbright;
        private final boolean runtimeSharedLight;
        private final int sharedLightReduction;
        private final float renderBrightnessMultiplier;
        private final float renderBrightnessFloor;

        private Baked(BakedModel originalModel, IQuadTransformer transformer, int sizeX, int sizeY, int sizeZ, String facingProperty, SharedLightingMode sharedLightingMode, @Nullable Boolean ambientOcclusion, boolean disableDiffuseShading, boolean forceUniformLight, boolean ignoreNeighborShading, boolean ignoreCullface, boolean forceGeneralQuads, boolean debugForceWhiteFullbright, boolean bakeStaticFullbright, boolean runtimeSharedLight, int sharedLightReduction, float renderBrightnessMultiplier, float renderBrightnessFloor) {
            super(originalModel);
            this.transformer = transformer;
            this.sizeX = sizeX;
            this.sizeY = sizeY;
            this.sizeZ = sizeZ;
            this.facingProperty = facingProperty;
            this.sharedLightingMode = sharedLightingMode;
            this.ambientOcclusion = ambientOcclusion;
            this.disableDiffuseShading = disableDiffuseShading;
            this.forceUniformLight = forceUniformLight;
            this.ignoreNeighborShading = ignoreNeighborShading;
            this.ignoreCullface = ignoreCullface;
            this.forceGeneralQuads = forceGeneralQuads;
            this.debugForceWhiteFullbright = debugForceWhiteFullbright;
            this.bakeStaticFullbright = bakeStaticFullbright;
            this.runtimeSharedLight = runtimeSharedLight;
            this.sharedLightReduction = sharedLightReduction;
            this.renderBrightnessMultiplier = renderBrightnessMultiplier;
            this.renderBrightnessFloor = renderBrightnessFloor;
        }

        @Override
        public boolean useAmbientOcclusion() {
            return ambientOcclusion != null ? ambientOcclusion : originalModel.useAmbientOcclusion();
        }

        @Override
        public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
            if (ambientOcclusion != null) {
                return ambientOcclusion ? TriState.TRUE : TriState.FALSE;
            }
            return originalModel.useAmbientOcclusion(state, data, renderType);
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
            Direction facing = getDirectionStateValue(state, facingProperty, Direction.NORTH);
            if (sharedLightingMode == SharedLightingMode.CONTROLLER || sharedLightingMode == SharedLightingMode.CONTROLLER_RUNTIME) {
                return sampleLight(level, pos);
            }
            if (sharedLightingMode == SharedLightingMode.DIRECTIONAL_MAX) {
                return direction == null ? SharedLightingBakedModel.NO_SHARED_LIGHT : sampleDirectionalMaxLight(level, pos, facing, direction);
            }

            int sky = 0;
            int block = 0;
            int count = sizeX * sizeY * sizeZ;
            for (int y = 0; y < sizeY; y++) {
                for (int x = 0; x < sizeX; x++) {
                    for (int z = 0; z < sizeZ; z++) {
                        int light = sampleCellLight(level, pos.offset(rotateLocalOffset(new BlockPos(x, y, z), facing)));
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

        @Override
        public boolean skyent$ignoresNeighborShading() {
            return ignoreNeighborShading;
        }

        @Override
        public boolean skyent$debugForceWhiteFullbright() {
            return debugForceWhiteFullbright;
        }

        @Override
        public String skyent$getDebugDescription() {
            return "ScaledBlockModel{"
                    + "shared_lighting=" + sharedLightingMode
                    + ", ambient_occlusion=" + ambientOcclusion
                    + ", ignore_neighbor_shading=" + ignoreNeighborShading
                    + ", ignore_cullface=" + ignoreCullface
                    + ", force_general_quads=" + forceGeneralQuads
                    + ", debug_force_white_fullbright=" + debugForceWhiteFullbright
                    + ", bake_static_fullbright=" + bakeStaticFullbright
                    + ", runtime_shared_light=" + runtimeSharedLight
                    + ", shared_light_reduction=" + sharedLightReduction
                    + ", render_brightness_multiplier=" + renderBrightnessMultiplier
                    + ", render_brightness_floor=" + renderBrightnessFloor
                    + ", size=" + sizeX + "x" + sizeY + "x" + sizeZ
                    + '}';
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

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            logGetQuads(state, side, null);
            if (ignoreCullface || forceGeneralQuads) {
                return getUnculledQuads(state, side, rand, ModelData.EMPTY, null);
            }
            return transformQuads(originalModel.getQuads(state, side, rand), null);
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
            logGetQuads(state, side, renderType);
            if (ignoreCullface || forceGeneralQuads) {
                return getUnculledQuads(state, side, rand, extraData, renderType);
            }
            return transformQuads(originalModel.getQuads(state, side, rand, extraData, renderType), getRuntimePackedLight(extraData));
        }

        private void logGetQuads(@Nullable BlockState state, @Nullable Direction side, @Nullable RenderType renderType) {
            if (HeatingChamberRenderDebug.ENABLED
                    && state != null
                    && state.is(ModBlocks.HEATING_CHAMBER.get())
                    && HEATING_CHAMBER_GET_QUADS_LOGS.getAndIncrement() < 200) {
                HeatingChamberRenderDebug.log(
                        "getQuads state={} side={} renderType={} description={}",
                        state,
                        side,
                        renderType,
                        skyent$getDebugDescription()
                );
            }
        }

        private List<BakedQuad> getUnculledQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
            if (side != null) {
                return List.of();
            }

            List<BakedQuad> quads = new ArrayList<>(originalModel.getQuads(state, null, rand, extraData, renderType));
            for (Direction direction : Direction.values()) {
                quads.addAll(originalModel.getQuads(state, direction, rand, extraData, renderType));
            }
            return transformQuads(quads, getRuntimePackedLight(extraData));
        }

        @Nullable
        private RuntimePackedLight getRuntimePackedLight(ModelData extraData) {
            if (!runtimeSharedLight) {
                return null;
            }
            Integer packedLight = extraData.get(SkyentModelData.SHARED_PACKED_LIGHT);
            int originalPackedLight = packedLight == null ? 0 : packedLight;
            int reducedPackedLight = reducePackedLight(originalPackedLight, sharedLightReduction);
            logRuntimeLight(originalPackedLight, reducedPackedLight);
            return new RuntimePackedLight(originalPackedLight, reducedPackedLight);
        }

        private void logRuntimeLight(int originalPackedLight, int reducedPackedLight) {
            if (HeatingChamberRenderDebug.ENABLED && HEATING_CHAMBER_RUNTIME_LIGHT_LOGS.getAndIncrement() < 100) {
                HeatingChamberRenderDebug.log(
                        "runtime light shared_light_reduction={} originalPacked={} originalBlock={} originalSky={} reducedPacked={} reducedBlock={} reducedSky={} writesUv2=true renderBrightnessScale={}",
                        sharedLightReduction,
                        originalPackedLight,
                        LightTexture.block(originalPackedLight),
                        LightTexture.sky(originalPackedLight),
                        reducedPackedLight,
                        LightTexture.block(reducedPackedLight),
                        LightTexture.sky(reducedPackedLight),
                        renderBrightnessScale(renderBrightnessMultiplier, renderBrightnessFloor)
                );
            }
        }

        private List<BakedQuad> transformQuads(List<BakedQuad> quads, @Nullable RuntimePackedLight runtimePackedLight) {
            List<BakedQuad> transformed = transformer.process(quads);
            boolean shouldForceUniformLight = forceUniformLight && !runtimeSharedLight;
            if (!disableDiffuseShading && !shouldForceUniformLight && !debugForceWhiteFullbright && !bakeStaticFullbright && runtimePackedLight == null && ambientOcclusion == null) {
                return transformed;
            }

            List<BakedQuad> wrapped = new ArrayList<>(transformed.size());
            for (BakedQuad quad : transformed) {
                int[] vertices = quad.getVertices().clone();
                if (debugForceWhiteFullbright) {
                    forceDebugMagentaFullbright(vertices);
                } else if (runtimePackedLight != null) {
                    bakePackedLight(vertices, runtimePackedLight.reducedPackedLight());
                    applyRenderBrightnessMultiplier(vertices, renderBrightnessMultiplier, renderBrightnessFloor);
                } else if (bakeStaticFullbright) {
                    bakeFullbright(vertices);
                } else if (shouldForceUniformLight) {
                    sanitizeVertexLighting(vertices);
                }
                wrapped.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), !disableDiffuseShading && quad.isShade(), ambientOcclusion != null ? ambientOcclusion : quad.hasAmbientOcclusion()));
            }
            return wrapped;
        }

        private static void forceDebugMagentaFullbright(int[] vertices) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE;
                vertices[offset + IQuadTransformer.COLOR] = 0xFFFF00FF;
                vertices[offset + IQuadTransformer.UV2] = LightTexture.FULL_BRIGHT;
                vertices[offset + IQuadTransformer.NORMAL] = 0;
            }
        }

        private static void bakeFullbright(int[] vertices) {
            bakePackedLight(vertices, LightTexture.FULL_BRIGHT);
        }

        private static void bakePackedLight(int[] vertices, int packedLight) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE;
                vertices[offset + IQuadTransformer.UV2] = packedLight;
                vertices[offset + IQuadTransformer.NORMAL] = 0;
            }
        }

        private static int reducePackedLight(int packedLight, int reduction) {
            int safeReduction = Math.max(0, reduction);
            if (safeReduction == 0) {
                return packedLight;
            }
            int block = Mth.clamp(Mth.clamp(LightTexture.block(packedLight), 0, 15) - safeReduction, 0, 15);
            int sky = Mth.clamp(Mth.clamp(LightTexture.sky(packedLight), 0, 15) - safeReduction, 0, 15);
            return LightTexture.pack(block, sky);
        }

        private static void applyRenderBrightnessMultiplier(int[] vertices, float multiplier, float floor) {
            float scale = renderBrightnessScale(multiplier, floor);
            if (scale >= 0.999F) {
                return;
            }

            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR;
                vertices[offset] = scaleColor(vertices[offset], scale);
            }
        }

        private static float renderBrightnessScale(float multiplier, float floor) {
            float safeMultiplier = Mth.clamp(multiplier, 0.0F, 1.0F);
            float safeFloor = Mth.clamp(floor, 0.0F, 1.0F);
            return Math.max(safeMultiplier, safeFloor);
        }

        private static int scaleColor(int color, float scale) {
            int red = Mth.clamp(Math.round((color & 0xFF) * scale), 0, 255);
            int green = Mth.clamp(Math.round(((color >> 8) & 0xFF) * scale), 0, 255);
            int blue = Mth.clamp(Math.round(((color >> 16) & 0xFF) * scale), 0, 255);
            int alpha = (color >> 24) & 0xFF;
            return red | green << 8 | blue << 16 | alpha << 24;
        }

        private static void sanitizeVertexLighting(int[] vertices) {
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE;
                vertices[offset + IQuadTransformer.COLOR] = 0xFFFFFFFF;
                vertices[offset + IQuadTransformer.UV2] = 0;
                vertices[offset + IQuadTransformer.NORMAL] = 0;
            }
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

        private record RuntimePackedLight(int originalPackedLight, int reducedPackedLight) {
        }
    }

    public enum SharedLightingMode {
        PER_BLOCK,
        MAX,
        AVERAGE,
        CONTROLLER,
        CONTROLLER_RUNTIME,
        DIRECTIONAL_MAX;

        private static SharedLightingMode fromJson(JsonObject jsonObject) {
            String value = GsonHelper.getAsString(jsonObject, "shared_lighting", "per_block");
            return switch (value.toLowerCase(java.util.Locale.ROOT)) {
                case "per_block" -> PER_BLOCK;
                case "max" -> MAX;
                case "average" -> AVERAGE;
                case "controller" -> CONTROLLER;
                case "controller_runtime" -> CONTROLLER_RUNTIME;
                case "directional_max" -> DIRECTIONAL_MAX;
                default -> throw new JsonParseException("Unknown scaled block model shared_lighting mode: " + value);
            };
        }
    }

    private record ScaleTransformer(float scale, float originX, float originY, float originZ, float translateX, float translateY, float translateZ) implements IQuadTransformer {
        @Override
        public void processInPlace(BakedQuad quad) {
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
                float x = Float.intBitsToFloat(vertices[offset]);
                float y = Float.intBitsToFloat(vertices[offset + 1]);
                float z = Float.intBitsToFloat(vertices[offset + 2]);

                vertices[offset] = Float.floatToRawIntBits(originX + (x - originX) * scale + translateX);
                vertices[offset + 1] = Float.floatToRawIntBits(originY + (y - originY) * scale + translateY);
                vertices[offset + 2] = Float.floatToRawIntBits(originZ + (z - originZ) * scale + translateZ);
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
            float translateX = 0.0F;
            float translateY = 0.0F;
            float translateZ = 0.0F;
            int sizeX = 1;
            int sizeY = 1;
            int sizeZ = 1;
            if (jsonObject.has("origin")) {
                var origin = GsonHelper.getAsJsonArray(jsonObject, "origin");
                if (origin.size() != 3) {
                    throw new JsonParseException("Scaled block model origin must contain exactly three numbers.");
                }
                originX = origin.get(0).getAsFloat();
                originY = origin.get(1).getAsFloat();
                originZ = origin.get(2).getAsFloat();
            }
            if (jsonObject.has("translation")) {
                var translation = GsonHelper.getAsJsonArray(jsonObject, "translation");
                if (translation.size() != 3) {
                    throw new JsonParseException("Scaled block model translation must contain exactly three numbers.");
                }
                translateX = translation.get(0).getAsFloat();
                translateY = translation.get(1).getAsFloat();
                translateZ = translation.get(2).getAsFloat();
            }
            if (jsonObject.has("size")) {
                var size = GsonHelper.getAsJsonArray(jsonObject, "size");
                if (size.size() != 3) {
                    throw new JsonParseException("Scaled block model size must contain exactly three integers.");
                }
                sizeX = size.get(0).getAsInt();
                sizeY = size.get(1).getAsInt();
                sizeZ = size.get(2).getAsInt();
                if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0) {
                    throw new JsonParseException("Scaled block model size values must be positive.");
                }
            }
            Boolean ambientOcclusion = null;
            if (jsonObject.has("ambient_occlusion")) {
                ambientOcclusion = GsonHelper.getAsBoolean(jsonObject, "ambient_occlusion");
            } else if (jsonObject.has("use_ambient_occlusion")) {
                ambientOcclusion = GsonHelper.getAsBoolean(jsonObject, "use_ambient_occlusion");
            }

            return new ScaledBlockModel(
                    baseModel,
                    scale,
                    originX,
                    originY,
                    originZ,
                    translateX,
                    translateY,
                    translateZ,
                    sizeX,
                    sizeY,
                    sizeZ,
                    GsonHelper.getAsString(jsonObject, "facing_property", "facing"),
                    SharedLightingMode.fromJson(jsonObject),
                    ambientOcclusion,
                    GsonHelper.getAsBoolean(jsonObject, "disable_diffuse_shading", false),
                    GsonHelper.getAsBoolean(jsonObject, "force_uniform_light", false),
                    GsonHelper.getAsBoolean(jsonObject, "ignore_neighbor_shading", false),
                    GsonHelper.getAsBoolean(jsonObject, "ignore_cullface", false),
                    GsonHelper.getAsBoolean(jsonObject, "force_general_quads", false),
                    GsonHelper.getAsBoolean(jsonObject, "debug_force_white_fullbright", false),
                    GsonHelper.getAsBoolean(jsonObject, "bake_static_fullbright", false),
                    GsonHelper.getAsBoolean(jsonObject, "runtime_shared_light", false),
                    Math.max(0, GsonHelper.getAsInt(jsonObject, "shared_light_reduction", 0)),
                    GsonHelper.getAsFloat(jsonObject, "render_brightness_multiplier", 1.0F),
                    GsonHelper.getAsFloat(jsonObject, "render_brightness_floor", 0.0F)
            );
        }
    }
}
