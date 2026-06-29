package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
            BuiltInRegistries.SOUND_EVENT,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_1 = register("geiger1");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_2 = register("geiger2");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_3 = register("geiger3");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_4 = register("geiger4");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_5 = register("geiger5");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_6 = register("geiger6");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_7 = register("geiger7");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEIGER_8 = register("geiger8");

    private ModSounds() {
    }

    public static void register(IEventBus modEventBus) {
        SOUND_EVENTS.register(modEventBus);
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
