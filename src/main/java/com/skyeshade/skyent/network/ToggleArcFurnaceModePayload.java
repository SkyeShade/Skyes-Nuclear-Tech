package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleArcFurnaceModePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ToggleArcFurnaceModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "toggle_arc_furnace_mode")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleArcFurnaceModePayload> STREAM_CODEC = StreamCodec.ofMember(
            ToggleArcFurnaceModePayload::encode,
            ToggleArcFurnaceModePayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ToggleArcFurnaceModePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ToggleArcFurnaceModePayload(buffer.readBlockPos());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }
}
