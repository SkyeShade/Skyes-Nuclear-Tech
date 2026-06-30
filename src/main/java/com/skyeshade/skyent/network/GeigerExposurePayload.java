package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GeigerExposurePayload(double exposureMillisievertsPerSecond, double radiationSickness) implements CustomPacketPayload {
    public static final Type<GeigerExposurePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "geiger_exposure")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, GeigerExposurePayload> STREAM_CODEC = StreamCodec.ofMember(
            GeigerExposurePayload::encode,
            GeigerExposurePayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static GeigerExposurePayload decode(RegistryFriendlyByteBuf buffer) {
        return new GeigerExposurePayload(buffer.readDouble(), buffer.readDouble());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeDouble(exposureMillisievertsPerSecond);
        buffer.writeDouble(radiationSickness);
    }
}
