package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RadiationDebugOverlayPayload(boolean enabled, String line1, String line2) implements CustomPacketPayload {
    public static final Type<RadiationDebugOverlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "radiation_debug_overlay")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationDebugOverlayPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            RadiationDebugOverlayPayload::enabled,
            ByteBufCodecs.stringUtf8(256),
            RadiationDebugOverlayPayload::line1,
            ByteBufCodecs.stringUtf8(256),
            RadiationDebugOverlayPayload::line2,
            RadiationDebugOverlayPayload::new
    );

    public static RadiationDebugOverlayPayload disabled() {
        return new RadiationDebugOverlayPayload(false, "", "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
