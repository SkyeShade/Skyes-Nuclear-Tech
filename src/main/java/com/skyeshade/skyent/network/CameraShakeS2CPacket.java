package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CameraShakeS2CPacket(float strength, int duration) implements CustomPacketPayload {
    public static final Type<CameraShakeS2CPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "camera_shake")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CameraShakeS2CPacket> STREAM_CODEC = StreamCodec.ofMember(
            CameraShakeS2CPacket::encode,
            CameraShakeS2CPacket::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static CameraShakeS2CPacket decode(RegistryFriendlyByteBuf buffer) {
        return new CameraShakeS2CPacket(buffer.readFloat(), buffer.readVarInt());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeFloat(strength);
        buffer.writeVarInt(duration);
    }
}
