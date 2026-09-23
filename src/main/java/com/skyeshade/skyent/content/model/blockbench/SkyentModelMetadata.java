package com.skyeshade.skyent.content.model.blockbench;

import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Consumer;

/** Common-side authored metadata. Client adapters alone convert this into Minecraft rendering types. */
public final class SkyentModelMetadata {
    private static final String COLLIDABLE = "skyent_collidable";
    public record Node(Boolean collidable) {
        public static final Node DEFAULT = new Node(null);
        public boolean explicitlyExcluded() { return Boolean.FALSE.equals(collidable); }
    }
    public enum Context {
        GUI("gui"), GROUND("ground"), FIXED("fixed"), FIRST_PERSON_RIGHT_HAND("firstperson_righthand"),
        FIRST_PERSON_LEFT_HAND("firstperson_lefthand"), THIRD_PERSON_RIGHT_HAND("thirdperson_righthand"),
        THIRD_PERSON_LEFT_HAND("thirdperson_lefthand"), HEAD("head");
        public final String key;
        Context(String key) { this.key = key; }
    }
    /** Degrees, Java model pixels, unitless scale. No world-machine scale is applied here. */
    public record Transform(Vec3 rotation, Vec3 translation, Vec3 scale) {
        public static final Transform IDENTITY = new Transform(Vec3.ZERO, Vec3.ZERO, new Vec3(1,1,1));
    }
    public record Display(boolean present, Map<Context, Transform> contexts) {
        public static final Display ABSENT = new Display(false, Map.of());
        public Display { contexts = Map.copyOf(contexts); }
    }

    private SkyentModelMetadata() {}

    public static Node node(JsonObject json, String id, Consumer<String> warning) {
        if (!json.has(COLLIDABLE)) return Node.DEFAULT;
        JsonElement value = json.get(COLLIDABLE);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) return new Node(value.getAsBoolean());
        warning.accept("Node " + id + ": " + COLLIDABLE + " must be boolean; using missing/default state");
        return Node.DEFAULT;
    }

    public static Display display(JsonObject root, Consumer<String> warning) {
        if (!root.has("skyent")) return Display.ABSENT;
        if (!root.get("skyent").isJsonObject()) { warning.accept("skyent must be an object; ignoring metadata"); return Display.ABSENT; }
        JsonObject skyent = root.getAsJsonObject("skyent");
        if (!skyent.has("display")) return Display.ABSENT;
        if (!skyent.get("display").isJsonObject()) { warning.accept("skyent.display must be an object; retaining legacy item transforms"); return Display.ABSENT; }
        Map<Context, Transform> result = new EnumMap<>(Context.class);
        JsonObject display = skyent.getAsJsonObject("display");
        for (Context context : Context.values()) {
            if (!display.has(context.key)) continue;
            String path = "skyent.display." + context.key;
            if (!display.get(context.key).isJsonObject()) { warning.accept(path + " must be an object; using default context"); continue; }
            JsonObject entry = display.getAsJsonObject(context.key);
            result.put(context, new Transform(vector(entry, "rotation", Vec3.ZERO, path, warning),
                    vector(entry, "translation", Vec3.ZERO, path, warning),
                    vector(entry, "scale", new Vec3(1,1,1), path, warning)));
        }
        for (String key : display.keySet()) if (Arrays.stream(Context.values()).noneMatch(c -> c.key.equals(key)))
            warning.accept("Unknown skyent.display context " + key + "; ignored");
        return new Display(true, result);
    }

    private static Vec3 vector(JsonObject entry, String key, Vec3 fallback, String path, Consumer<String> warning) {
        if (!entry.has(key)) return fallback;
        JsonElement value = entry.get(key);
        if (value.isJsonArray() && value.getAsJsonArray().size() == 3) {
            double[] v = new double[3];
            boolean valid = true;
            for (int i = 0; i < 3; i++) {
                JsonElement component = value.getAsJsonArray().get(i);
                if (!component.isJsonPrimitive() || !component.getAsJsonPrimitive().isNumber()) { valid = false; break; }
                v[i] = component.getAsDouble();
                if (!Double.isFinite(v[i]) || Math.abs(v[i]) > Float.MAX_VALUE) { valid = false; break; }
            }
            if (valid) return new Vec3(v[0],v[1],v[2]);
        }
        warning.accept(path + "." + key + " requires three finite numbers; using component default " + fallback);
        return fallback;
    }
}
