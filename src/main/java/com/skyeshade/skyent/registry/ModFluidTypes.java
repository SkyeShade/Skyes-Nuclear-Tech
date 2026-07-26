package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Consumer;

public final class ModFluidTypes {
    private static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(
            NeoForgeRegistries.FLUID_TYPES,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<FluidType, FluidType> MOLTEN_CORIUM = FLUID_TYPES.register(
            "molten_corium",
            () -> new FluidType(FluidType.Properties.create()
                    .lightLevel(15)
                    .density(3000)
                    .viscosity(9000)
                    .temperature(3000)
                    .canSwim(false)
                    .canDrown(false)
                    .canExtinguish(false)
                    .supportsBoating(false)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)) {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/fluid/molten_corium_still");
                        private static final ResourceLocation FLOW = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/fluid/molten_corium_flow");

                        @Override
                        public ResourceLocation getStillTexture() {
                            return STILL;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return FLOW;
                        }

                        @Override
                        public int getTintColor() {
                            return 0xFFFFFFFF;
                        }
                    });
                }
            }
    );

    public static final DeferredHolder<FluidType, FluidType> STEAM = FLUID_TYPES.register(
            "steam",
            () -> new FluidType(FluidType.Properties.create()
                    .density(-100)
                    .viscosity(100)
                    .temperature(373)
                    .canSwim(false)
                    .canDrown(false)
                    .supportsBoating(false)) {
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        private static final ResourceLocation STILL = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/fluid/steam_still");
                        private static final ResourceLocation FLOW = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "block/fluid/steam_flow");

                        @Override
                        public ResourceLocation getStillTexture() {
                            return STILL;
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return FLOW;
                        }

                        @Override
                        public int getTintColor() {
                            return 0xFFFFFFFF;
                        }
                    });
                }
            }
    );

    public static final DeferredHolder<FluidType, FluidType> SULFURIC_ACID = registerTintedFluidType(
            "sulfuric_acid",
            "sulfuric_acid",
            0xFFF7E783,
            FluidType.Properties.create()
                    .density(1300)
                    .viscosity(1300)
                    .temperature(295)
                    .supportsBoating(false)
    );

    public static final DeferredHolder<FluidType, FluidType> DILUTED_SULFURIC_ACID = registerTintedFluidType(
            "diluted_sulfuric_acid",
            "diluted_sulfuric_acid",
            0xFFCFE6A0,
            FluidType.Properties.create()
                    .density(1100)
                    .viscosity(1000)
                    .temperature(295)
                    .supportsBoating(false)
    );

    public static final DeferredHolder<FluidType, FluidType> MINERAL_SLURRY = registerTintedFluidType(
            "mineral_slurry",
            "mineral_slurry",
            0xFF8A7E68,
            FluidType.Properties.create()
                    .density(1600)
                    .viscosity(2500)
                    .temperature(295)
                    .supportsBoating(false)
    );

    public static final DeferredHolder<FluidType, FluidType> BRINE = registerTintedFluidType(
            "brine",
            "brine",
            0xFFB9D5E5,
            FluidType.Properties.create()
                    .density(1100)
                    .viscosity(1000)
                    .temperature(295)
    );

    public static final DeferredHolder<FluidType, FluidType> DEMINERALISED_WATER = registerTintedFluidType(
            "demineralised_water",
            "demineralised_water",
            0xFF3F76E4,
            FluidType.Properties.create()
                    .density(1000)
                    .viscosity(1000)
                    .temperature(295)
    );

    private ModFluidTypes() {
    }

    private static DeferredHolder<FluidType, FluidType> registerTintedFluidType(
            String id,
            String textureName,
            int tintColor,
            FluidType.Properties properties
    ) {
        return FLUID_TYPES.register(id, () -> new FluidType(properties) {
            @Override
            public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                consumer.accept(new IClientFluidTypeExtensions() {
                    private final ResourceLocation still = ResourceLocation.fromNamespaceAndPath(
                            SkyesNuclearTech.MOD_ID,
                            "block/fluid/" + textureName + "_still"
                    );
                    private final ResourceLocation flow = ResourceLocation.fromNamespaceAndPath(
                            SkyesNuclearTech.MOD_ID,
                            "block/fluid/" + textureName + "_flow"
                    );

                    @Override
                    public ResourceLocation getStillTexture() {
                        return still;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flow;
                    }

                    @Override
                    public int getTintColor() {
                        return tintColor;
                    }
                });
            }
        });
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
    }
}
