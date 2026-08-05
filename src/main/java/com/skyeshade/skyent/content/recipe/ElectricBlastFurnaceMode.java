package com.skyeshade.skyent.content.recipe;

import com.mojang.serialization.Codec;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public enum ElectricBlastFurnaceMode {
    SMELTING(0, "smelting"),
    ALLOYING(1, "alloying");

    public static final Codec<ElectricBlastFurnaceMode> CODEC = Codec.STRING.xmap(ElectricBlastFurnaceMode::bySerializedName, ElectricBlastFurnaceMode::serializedName);
    public static final StreamCodec<io.netty.buffer.ByteBuf, ElectricBlastFurnaceMode> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(ElectricBlastFurnaceMode::byCode, ElectricBlastFurnaceMode::code);

    private final int code;
    private final String serializedName;

    ElectricBlastFurnaceMode(int code, String serializedName) {
        this.code = code;
        this.serializedName = serializedName;
    }

    public int code() {
        return code;
    }

    public String serializedName() {
        return serializedName;
    }

    public ElectricBlastFurnaceMode toggled() {
        return this == SMELTING ? ALLOYING : SMELTING;
    }

    public static ElectricBlastFurnaceMode byCode(int code) {
        for (ElectricBlastFurnaceMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        return SMELTING;
    }

    public static ElectricBlastFurnaceMode bySerializedName(String name) {
        String normalized = name == null ? "" : name.toLowerCase(Locale.ROOT);
        for (ElectricBlastFurnaceMode mode : values()) {
            if (mode.serializedName.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown Electric Blast Furnace mode: " + name);
    }
}
