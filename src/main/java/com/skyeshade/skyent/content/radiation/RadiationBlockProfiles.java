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

    public static Optional<EnvironmentalRadiationRayProfile> getEnvironmentalRayProfile(BlockState state) {
        return Optional.ofNullable(get(state.getBlock()).environmentalRays());
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
        // Environmental ray profiles: low ores are sparse, vitrified tiers scale by heat, and corium keeps the 16 ray / 8 conversion hot-source baseline.
        register(ModBlocks.URANIUM_BLOCK, RadiationBlockProfile.builder()
                .radiation(50.0D, 6, 40)
                .environmentalRays(2, 1, 100, 600, 2)
                .transmission(0.90D)
                .build());
        register(ModBlocks.URANIUM_ORE, RadiationBlockProfile.builder()
                .radiation(2.5D, 3, 40)
                .environmentalRays(1, 1, 240, 1200, 1)
                .transmission(0.90D)
                .build());
        register(ModBlocks.DEEPSLATE_URANIUM_ORE, RadiationBlockProfile.builder()
                .radiation(2.5D, 3, 40)
                .environmentalRays(1, 1, 240, 1200, 1)
                .transmission(0.90D)
                .build());
        register(ModBlocks.CORIUM_BLOCK, RadiationBlockProfile.builder()
                .radiation(25_000.0D, 48, 180)
                .environmentalRays(16, 8, 20, 200, 10)
                .transmission(0.90D)
                .build());
        register(ModBlocks.RADIOACTIVE_SCRAP_METAL, RadiationBlockProfile.builder()
                .radiation(1000.0D, 16, 80)
                .environmentalRays(4, 2, 80, 800, 4)
                .transmission(0.90D)
                .build());
        register(ModBlocks.CONTAMINATED_GRASS_BLOCK, RadiationBlockProfile.builder()
                .radiation(2.5D, 3, 40)
                .transmission(0.90D)
                .build());
        register(ModBlocks.VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(10.0D, 4, 32)
                .transmission(0.90D)
                .build());
        register(ModBlocks.BAKED_VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(25.0D, 5, 36)
                .environmentalRays(1, 1, 220, 1200, 1)
                .transmission(0.90D)
                .build());
        register(ModBlocks.SCORCHED_VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(75.0D, 6, 44)
                .environmentalRays(1, 1, 180, 1000, 1)
                .transmission(0.90D)
                .build());
        register(ModBlocks.IRRADIATED_VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(200.0D, 8, 56)
                .environmentalRays(2, 1, 140, 900, 2)
                .transmission(0.90D)
                .build());
        register(ModBlocks.HOT_VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(750.0D, 12, 72)
                .environmentalRays(3, 2, 90, 700, 3)
                .transmission(0.90D)
                .build());
        register(ModBlocks.RADIANT_VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(1_500.0D, 18, 96)
                .environmentalRays(5, 3, 60, 500, 5)
                .transmission(0.90D)
                .build());
        register(ModBlocks.INFERNAL_VITRIFIED_STONE, RadiationBlockProfile.builder()
                .radiation(5_000.0D, 28, 128)
                .environmentalRays(8, 4, 40, 400, 7)
                .transmission(0.90D)
                .build());
        register(ModBlocks.MOLTEN_CORIUM_BLOCK, RadiationBlockProfile.builder()
                .radiation(25_000.0D, 48, 180)
                .environmentalRays(16, 8, 20, 200, 10)
                .transmission(1.0D)
                .build());
        register(ModBlocks.CRACKED_CONCRETE_BRICKS, RadiationBlockProfile.builder()
                .transmission(0.90D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.CONCRETE_BRICKS, RadiationBlockProfile.builder()
                .transmission(0.85D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.REINFORCED_CONCRETE, RadiationBlockProfile.builder()
                .transmission(0.65D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.REINFORCED_GLASS, RadiationBlockProfile.builder()
                .transmission(0.20D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.TUNGSTEN_REINFORCED_CONCRETE, RadiationBlockProfile.builder()
                .transmission(0.25D)
                .showShieldingTooltip()
                .build());
        register(ModBlocks.PLATED_CONCRETE, RadiationBlockProfile.builder()
                .transmission(0.02D)
                .showShieldingTooltip()
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
