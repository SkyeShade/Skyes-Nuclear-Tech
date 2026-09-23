package com.skyeshade.skyent.client.model;

import com.google.gson.*;
import com.skyeshade.skyent.content.model.MachineModelTransform;
import com.skyeshade.skyent.content.model.blockbench.SkyentModelMetadata;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Native Java display semantics over the free-model display origin, independent of placed-machine scale. */
public final class MeshItemPresentation {
    private static final Gson VANILLA = new GsonBuilder()
            .registerTypeAdapter(ItemTransform.class,new ItemTransform.Deserializer())
            .registerTypeAdapter(ItemTransforms.class,new ItemTransforms.Deserializer()).create();
    private MeshItemPresentation() {}

    public static ItemTransforms transforms(SkyentModelMetadata.Display display, ItemTransforms legacy) {
        if (!display.present()) return legacy;
        JsonObject json = new JsonObject();
        display.contexts().forEach((context, transform) -> {
            JsonObject entry = new JsonObject();
            entry.add("rotation",vector(transform.rotation()));
            entry.add("translation",vector(transform.translation()));
            entry.add("scale",vector(transform.scale()));
            json.add(context.key,entry);
        });
        // Vanilla handles pixel translation, clamps, defaults, right-to-left fallback and hand mirroring.
        return VANILLA.fromJson(json,ItemTransforms.class);
    }

    public static Vec3 position(Vec3 authored, MachineModelTransform world, boolean authoredDisplay) {
        // Blockbench free display centers X/Z at 0 and Y at 8. ItemRenderer later subtracts (0.5,0.5,0.5).
        // Neither the controller offset nor the placed multiblock's 2x scale belongs to this preview.
        return authoredDisplay ? authored.scale(1.0 / 16).add(.5,0,.5) : world.authoredRelative(authored,Direction.NORTH);
    }

    private static JsonArray vector(Vec3 v) {
        JsonArray a = new JsonArray(); a.add(v.x); a.add(v.y); a.add(v.z); return a;
    }
}
