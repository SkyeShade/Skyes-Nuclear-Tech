package com.skyeshade.skyent.client.sound;

import com.skyeshade.skyent.network.PlayLocalSoundPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class LocalSoundClient {
    private LocalSoundClient() {
    }

    public static void handlePayload(PlayLocalSoundPayload payload) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(payload.soundId());
        if (sound == null) {
            return;
        }

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, payload.pitch(), payload.volume()));
    }
}
