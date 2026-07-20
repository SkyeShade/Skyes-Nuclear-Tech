package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMVAssemblerRecipeSelectPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenMVAssemblerRecipeSelectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "open_mv_assembler_recipe_select")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMVAssemblerRecipeSelectPayload> STREAM_CODEC = StreamCodec.ofMember(
            OpenMVAssemblerRecipeSelectPayload::encode,
            OpenMVAssemblerRecipeSelectPayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static OpenMVAssemblerRecipeSelectPayload decode(RegistryFriendlyByteBuf buffer) {
        return new OpenMVAssemblerRecipeSelectPayload(buffer.readBlockPos());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
    }
}
