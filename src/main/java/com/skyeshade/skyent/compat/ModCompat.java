package com.skyeshade.skyent.compat;

import net.neoforged.fml.ModList;

public final class ModCompat {
    public static final String JADE_MOD_ID = "jade";

    private ModCompat() {
    }

    public static boolean isJadeLoaded() {
        return ModList.get().isLoaded(JADE_MOD_ID);
    }
}
