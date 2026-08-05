package com.skyeshade.skyent.content.recipe;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum ArcFurnaceMode {
    SMELTING(0, "smelting"),
    ALLOYING(1, "alloying");

    public static final Codec<ArcFurnaceMode> CODEC = Codec.STRING.xmap(ArcFurnaceMode::bySerializedName, ArcFurnaceMode::serializedName);
    public static final StreamCodec<io.netty.buffer.ByteBuf, ArcFurnaceMode> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ArcFurnaceMode::byCode, ArcFurnaceMode::code);

    private final int code;
    private final String serializedName;

    ArcFurnaceMode(int code, String serializedName) {
        this.code = code;
        this.serializedName = serializedName;
    }

    public int code() {
        return code;
    }

    public String serializedName() {
        return serializedName;
    }

    public ArcFurnaceMode toggled() {
        return this == SMELTING ? ALLOYING : SMELTING;
    }

    public static ArcFurnaceMode byCode(int code) {
        for (ArcFurnaceMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return SMELTING;
    }

    public static ArcFurnaceMode bySerializedName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (ArcFurnaceMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown Arc Furnace mode: " + name);
    }
}
