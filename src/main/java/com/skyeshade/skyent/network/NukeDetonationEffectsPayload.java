package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record NukeDetonationEffectsPayload(
        double x,
        double y,
        double z,
        boolean spawnCloud,
        boolean flashSky,
        boolean spawnBeams,
        long seed
) implements CustomPacketPayload {
    public static final Type<NukeDetonationEffectsPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "nuke_detonation_effects")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, NukeDetonationEffectsPayload> STREAM_CODEC = StreamCodec.ofMember(
            NukeDetonationEffectsPayload::encode,
            NukeDetonationEffectsPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static NukeDetonationEffectsPayload decode(RegistryFriendlyByteBuf buffer) {
        return new NukeDetonationEffectsPayload(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readLong()
        );
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeBoolean(spawnCloud);
        buffer.writeBoolean(flashSky);
        buffer.writeBoolean(spawnBeams);
        buffer.writeLong(seed);
    }
}
