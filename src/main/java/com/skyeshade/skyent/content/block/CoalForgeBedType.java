package com.skyeshade.skyent.content.block;

import net.minecraft.util.StringRepresentable;

public enum CoalForgeBedType implements StringRepresentable {
    EMPTY("empty"),
    COAL("coal"),
    ASH("ash");

    private final String serializedName;

    CoalForgeBedType(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
