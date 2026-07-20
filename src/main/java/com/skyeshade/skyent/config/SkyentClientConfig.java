package com.skyeshade.skyent.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class SkyentClientConfig {
    public static final String FILE_NAME = "skyent/client.toml";

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue ENABLE_RADIATION_NOISE_OVERLAY;
    private static final ModConfigSpec.BooleanValue ONLY_SHOW_RADIATION_NOISE_OVERLAY_IN_SURVIVAL;

    static {
        BUILDER.comment("Skyes Nuclear Tech client-only visual settings.");

        BUILDER.push("visuals");
        ENABLE_RADIATION_NOISE_OVERLAY = BUILDER
                .comment("Whether radiation static/noise is rendered on the screen when exposed to radiation.")
                .define("enableRadiationNoiseOverlay", true);
        ONLY_SHOW_RADIATION_NOISE_OVERLAY_IN_SURVIVAL = BUILDER
                .comment("Only render the radiation noise/static overlay in survival/adventure mode. Requires enableRadiationNoiseOverlay to also be true. When enabled, the overlay is hidden in creative and spectator.")
                .define("onlyShowRadiationNoiseOverlayInSurvival", true);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    private SkyentClientConfig() {
    }

    public static boolean enableRadiationNoiseOverlay() {
        return ENABLE_RADIATION_NOISE_OVERLAY.get();
    }

    public static boolean onlyShowRadiationNoiseOverlayInSurvival() {
        return ONLY_SHOW_RADIATION_NOISE_OVERLAY_IN_SURVIVAL.get();
    }

}
