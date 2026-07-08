package com.skyeshade.skyent.content.block;

import net.minecraft.util.StringRepresentable;

public enum ConveyorElevatorSegment implements StringRepresentable {
    BOTTOM("bottom"),
    MIDDLE("middle"),
    TOP("top");

    private final String serializedName;

    ConveyorElevatorSegment(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
