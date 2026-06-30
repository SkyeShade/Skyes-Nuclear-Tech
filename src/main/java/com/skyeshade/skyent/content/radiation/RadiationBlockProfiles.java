package com.skyeshade.skyent.content.radiation;

import com.skyeshade.skyent.registry.ModBlocks;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class RadiationBlockProfiles {
    private static final RadiationBlockProfile EMPTY = RadiationBlockProfile.builder().build();
    private static final Map<Block, RadiationBlockProfile> PROFILES = new IdentityHashMap<>();
    private static boolean initialized;

    private RadiationBlockProfiles() {
    }

    public static void register(Block block, RadiationBlockProfile profile) {
        PROFILES.put(block, profile);
    }

    public static void register(Supplier<? extends Block> block, RadiationBlockProfile profile) {
        register(block.get(), profile);
    }

    public static RadiationBlockProfile get(Block block) {
        ensureInitialized();
        return PROFILES.getOrDefault(block, EMPTY);
    }

    public static Optional<RadiationBlockProfile> getOptional(Block block) {
        ensureInitialized();
        return Optional.ofNullable(PROFILES.get(block));
    }

    public static boolean isRadioactive(BlockState state) {
        return get(state.getBlock()).radioactive();
    }

    public static double getRadiationStrength(BlockState state) {
        return getRadiationStrength(state.getBlock());
    }

    public static double getRadiationStrength(Block block) {
        return get(block).radiationStrength();
    }

    public static int getEnvironmentalRange(BlockState state) {
        return getEnvironmentalRange(state.getBlock());
    }

    public static int getEnvironmentalRange(Block block) {
        return get(block).environmentalRange();
    }

    public static int getEntityRange(BlockState state) {
        return getEntityRange(state.getBlock());
    }

    public static int getEntityRange(Block block) {
        return get(block).entityRange();
    }

    public static OptionalDouble getCustomTransmission(BlockState state) {
        RadiationBlockProfile profile = get(state.getBlock());
        return profile.hasCustomTransmission() ? OptionalDouble.of(profile.transmission()) : OptionalDouble.empty();
    }

    public static double coriumStrength() {
        return getRadiationStrength(ModBlocks.CORIUM_BLOCK.get());
    }

    public static int coriumEnvironmentalRange() {
        return getEnvironmentalRange(ModBlocks.CORIUM_BLOCK.get());
    }

    private static void ensureInitialized() {
        if (initialized) {
            return;
        }

        initialized = true;
        registerDefaults();
    }

    private static void registerDefaults() {
        register(ModBlocks.URANIUM_BLOCK, RadiationBlockProfile.builder()
                .radiation(50.0D, 6, 40)
                .transmission(0.90D)
                .build());
        register(ModBlocks.URANIUM_ORE, RadiationBlockProfile.builder()
                .radiation(2.5D, 3, 40)
                .transmission(0.90D)
                .build());
        register(ModBlocks.DEEPSLATE_URANIUM_ORE, RadiationBlockProfile.builder()
                .radiation(2.5D, 3, 40)
                .transmission(0.90D)
                .build());
        register(ModBlocks.CORIUM_BLOCK, RadiationBlockProfile.builder()
                .radiation(25_000.0D, 48, 180)
                .transmission(0.90D)
                .build());
        register(ModBlocks.MOLTEN_CORIUM_BLOCK, RadiationBlockProfile.builder()
                .radiation(25_000.0D, 48, 180)
                .transmission(1.0D)
                .build());
        register(ModBlocks.ALUMINUM_BLOCK, RadiationBlockProfile.builder()
                .transmission(0.65D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.TITANIUM_BLOCK, RadiationBlockProfile.builder()
                .transmission(0.40D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.TUNGSTEN_BLOCK, RadiationBlockProfile.builder()
                .transmission(0.25D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.STEEL_BLOCK, RadiationBlockProfile.builder()
                .transmission(0.45D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.LEAD_BLOCK, RadiationBlockProfile.builder()
                .transmission(0.02D)
                .showShieldingTooltip()
                .build());
    }
}
