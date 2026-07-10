package com.skyeshade.skyent.client.screen;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.resources.ResourceLocation;

public final class CoalForgeGuiLayout {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "textures/gui/jei/coal_forge_jei.png"
    );

    public static final int CROP_X = 35;
    public static final int CROP_Y = 21;
    public static final int CROP_WIDTH = 107;
    public static final int CROP_HEIGHT = 42;

    public static final int SLOT_1_X = 40;
    public static final int SLOT_1_Y = 27;
    public static final int SLOT_2_X = 56;
    public static final int SLOT_2_Y = 27;
    public static final int SLOT_3_X = 40;
    public static final int SLOT_3_Y = 43;
    public static final int SLOT_4_X = 56;
    public static final int SLOT_4_Y = 43;
    public static final int HEATING_OVERLAY_X = 42;
    public static final int HEATING_OVERLAY_Y = 29;
    public static final int PROGRESS_ARROW_X = 80;
    public static final int PROGRESS_ARROW_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    public static final int HEATING_OVERLAY_1_U = 180;
    public static final int HEATING_OVERLAY_1_V = 38;
    public static final int HEATING_OVERLAY_2_U = 180;
    public static final int HEATING_OVERLAY_2_V = 67;
    public static final int HEATING_OVERLAY_SIZE = 28;

    public static final int PROGRESS_ARROW_U = 200;
    public static final int PROGRESS_ARROW_V = 4;
    public static final int PROGRESS_ARROW_WIDTH = 22;
    public static final int PROGRESS_ARROW_HEIGHT = 16;

    public static final int TEXTURE_WIDTH = 256;
    public static final int TEXTURE_HEIGHT = 256;

    private CoalForgeGuiLayout() {
    }

    public static int localX(int fullX) {
        return fullX - CROP_X;
    }

    public static int localY(int fullY) {
        return fullY - CROP_Y;
    }
}
