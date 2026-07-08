package com.skyeshade.skyent.content.block;

import net.minecraft.util.StringRepresentable;

public enum ConveyorChuteSegment implements StringRepresentable {
    BOTTOM("bottom"),
    MIDDLE("middle"),
    TOP("top");

    private final String serializedName;

    ConveyorChuteSegment(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
