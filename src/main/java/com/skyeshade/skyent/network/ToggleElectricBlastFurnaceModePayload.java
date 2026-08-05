package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ToggleElectricBlastFurnaceModePayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ToggleElectricBlastFurnaceModePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "toggle_electric_blast_furnace_mode")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ToggleElectricBlastFurnaceModePayload> STREAM_CODEC = StreamCodec.ofMember(
            ToggleElectricBlastFurnaceModePayload::encode,
            ToggleElectricBlastFurnaceModePayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static ToggleElectricBlastFurnaceModePayload decode(RegistryFriendlyByteBuf buffer) {
        return new ToggleElectricBlastFurnaceModePayload(buffer.readBlockPos());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }
}
