package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadiationRaysDebugPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<RadiationRaysDebugPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "radiation_rays_debug")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationRaysDebugPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RadiationRaysDebugPayload::enabled,
            RadiationRaysDebugPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
