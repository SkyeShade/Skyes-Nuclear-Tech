package com.skyeshade.skyent.content.shape;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.skyeshade.skyent.SkyesNuclearTech;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class MultiblockModelShapeLoader {
    private static final double EPSILON = 1.0E-6D;

    private MultiblockModelShapeLoader() {
    }

    public static MultiblockShapeData load(MultiblockShapeDefinition definition, ResourceManager resourceManager) throws IOException {
        ResourceLocation resourceId = modelToResourcePath(definition.model());
        java.util.Optional<Resource> resource = resourceManager.getResource(resourceId);
        if (resource.isEmpty()) {
            return null;
        }

        try (Reader reader = resource.get().openAsReader()) {
            return load(definition, reader);
        }
    }

    public static MultiblockShapeData loadFromClasspath(MultiblockShapeDefinition definition) throws IOException {
        try (Reader reader = openClasspathReader(definition)) {
            return load(definition, reader);
        }
    }

    private static MultiblockShapeData load(MultiblockShapeDefinition definition, Reader reader) {
        JsonObject model = JsonParser.parseReader(reader).getAsJsonObject();
        JsonArray elements = GsonHelper.getAsJsonArray(model, "elements", new JsonArray());
        List<VoxelShape>[] cells = createCellLists(definition);

        int elementIndex = 0;
        for (JsonElement elementJson : elements) {
            JsonObject element = elementJson.getAsJsonObject();
            warnIfRotated(definition, element, elementIndex);

            double[] mins = new double[3];
            double[] maxs = new double[3];
            JsonArray from = GsonHelper.getAsJsonArray(element, "from");
            JsonArray to = GsonHelper.getAsJsonArray(element, "to");
            for (int axis = 0; axis < 3; axis++) {
                double origin = component(definition.origin(), axis);
                double translation = component(definition.translation(), axis);
                double a = origin + (from.get(axis).getAsDouble() - origin) * definition.scale() + translation;
                double b = origin + (to.get(axis).getAsDouble() - origin) * definition.scale() + translation;
                mins[axis] = Math.min(a, b);
                maxs[axis] = Math.max(a, b);
            }

            clipElementToCells(definition, cells, mins, maxs);
            elementIndex++;
        }

        VoxelShape[] northShapes = new VoxelShape[cells.length];
        int totalBoxes = 0;
        for (int i = 0; i < cells.length; i++) {
            northShapes[i] = combine(cells[i]);
            totalBoxes += cells[i].size();
        }
        SkyesNuclearTech.LOGGER.info(
                "Generated multiblock shapes for {} from {}: {} cells, {} boxes",
                definition.id(),
                definition.model(),
                northShapes.length,
                totalBoxes
        );
        return new MultiblockShapeData(definition.sizeX(), definition.sizeY(), definition.sizeZ(), northShapes);
    }

    @SuppressWarnings("unchecked")
    private static List<VoxelShape>[] createCellLists(MultiblockShapeDefinition definition) {
        int size = definition.sizeX() * definition.sizeY() * definition.sizeZ();
        List<VoxelShape>[] cells = new List[size];
        for (int i = 0; i < size; i++) {
            cells[i] = new ArrayList<>();
        }
        return cells;
    }

    private static void clipElementToCells(MultiblockShapeDefinition definition, List<VoxelShape>[] cells, double[] mins, double[] maxs) {
        for (int y = 0; y < definition.sizeY(); y++) {
            for (int x = 0; x < definition.sizeX(); x++) {
                for (int z = 0; z < definition.sizeZ(); z++) {
                    double clipMinX = Math.max(mins[0], 16.0D * x);
                    double clipMaxX = Math.min(maxs[0], 16.0D * (x + 1));
                    double clipMinY = Math.max(mins[1], 16.0D * y);
                    double clipMaxY = Math.min(maxs[1], 16.0D * (y + 1));
                    double clipMinZ = Math.max(mins[2], 16.0D * z);
                    double clipMaxZ = Math.min(maxs[2], 16.0D * (z + 1));

                    if (clipMaxX - clipMinX <= EPSILON || clipMaxY - clipMinY <= EPSILON || clipMaxZ - clipMinZ <= EPSILON) {
                        continue;
                    }

                    cells[index(definition, x, y, z)].add(Block.box(
                            clipMinX - 16.0D * x,
                            clipMinY - 16.0D * y,
                            clipMinZ - 16.0D * z,
                            clipMaxX - 16.0D * x,
                            clipMaxY - 16.0D * y,
                            clipMaxZ - 16.0D * z
                    ));
                }
            }
        }
    }

    private static VoxelShape combine(List<VoxelShape> boxes) {
        if (boxes.isEmpty()) {
            return Shapes.empty();
        }
        VoxelShape shape = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) {
            shape = Shapes.or(shape, boxes.get(i));
        }
        return shape;
    }

    private static void warnIfRotated(MultiblockShapeDefinition definition, JsonObject element, int elementIndex) {
        if (!element.has("rotation")) {
            return;
        }
        JsonObject rotation = GsonHelper.getAsJsonObject(element, "rotation");
        double angle = GsonHelper.getAsDouble(rotation, "angle", 0.0D);
        if (Math.abs(angle) > EPSILON) {
            SkyesNuclearTech.LOGGER.warn(
                    "Multiblock shape {} model {} element {} has unsupported non-zero rotation {}; using from/to bounds",
                    definition.id(),
                    definition.model(),
                    elementIndex,
                    angle
            );
        }
    }

    private static Reader openClasspathReader(MultiblockShapeDefinition definition) throws IOException {
        ResourceLocation model = definition.model();
        String path = "assets/" + model.getNamespace() + "/" + model.getPath();
        InputStream stream = MultiblockModelShapeLoader.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Classpath resource not found: " + path);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }

    private static ResourceLocation modelToResourcePath(ResourceLocation model) {
        return ResourceLocation.fromNamespaceAndPath(model.getNamespace(), model.getPath());
    }

    private static int index(MultiblockShapeDefinition definition, int x, int y, int z) {
        return (y * definition.sizeX() + x) * definition.sizeZ() + z;
    }

    private static double component(net.minecraft.world.phys.Vec3 vec, int axis) {
        return switch (axis) {
            case 0 -> vec.x;
            case 1 -> vec.y;
            default -> vec.z;
        };
    }
}
