package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectMVAssemblerRecipePayload(BlockPos pos, ResourceLocation recipeId) implements CustomPacketPayload {
    public static final Type<SelectMVAssemblerRecipePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "select_mv_assembler_recipe")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectMVAssemblerRecipePayload> STREAM_CODEC = StreamCodec.ofMember(
            SelectMVAssemblerRecipePayload::encode,
            SelectMVAssemblerRecipePayload::decode
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static SelectMVAssemblerRecipePayload decode(RegistryFriendlyByteBuf buffer) {
        return new SelectMVAssemblerRecipePayload(buffer.readBlockPos(), buffer.readResourceLocation());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(pos);
        buffer.writeResourceLocation(recipeId);
    }
}
