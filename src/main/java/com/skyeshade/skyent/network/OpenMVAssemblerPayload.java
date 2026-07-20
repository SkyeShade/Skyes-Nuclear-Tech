package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMVAssemblerPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenMVAssemblerPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "open_mv_assembler")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMVAssemblerPayload> STREAM_CODEC = StreamCodec.ofMember(
            OpenMVAssemblerPayload::encode,
            OpenMVAssemblerPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static OpenMVAssemblerPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenMVAssemblerPayload(buffer.readBlockPos());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }
}
