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

    private ModFluidTypes() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
    }
}
