package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayLocalSoundPayload(ResourceLocation soundId, float volume, float pitch) implements CustomPacketPayload {
    public static final Type<PlayLocalSoundPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "play_local_sound")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayLocalSoundPayload> STREAM_CODEC = StreamCodec.ofMember(
            PlayLocalSoundPayload::encode,
            PlayLocalSoundPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static PlayLocalSoundPayload decode(RegistryFriendlyByteBuf buffer) {
        return new PlayLocalSoundPayload(buffer.readResourceLocation(), buffer.readFloat(), buffer.readFloat());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(soundId);
        buffer.writeFloat(volume);
        buffer.writeFloat(pitch);
    }
}
