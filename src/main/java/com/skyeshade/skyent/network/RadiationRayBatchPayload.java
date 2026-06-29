package com.skyeshade.skyent.network;

import com.skyeshade.skyent.SkyesNuclearTech;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record RadiationRayBatchPayload(List<Ray> rays) implements CustomPacketPayload {
    public static final int MAX_RAYS_PER_BATCH = 64;
    public static final Type<RadiationRayBatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "radiation_ray_batch")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RadiationRayBatchPayload> STREAM_CODEC = StreamCodec.ofMember(
            RadiationRayBatchPayload::encode,
            RadiationRayBatchPayload::decode
    );

    public RadiationRayBatchPayload {
        rays = List.copyOf(rays);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static RadiationRayBatchPayload decode(RegistryFriendlyByteBuf buffer) {
        int count = Math.min(buffer.readVarInt(), MAX_RAYS_PER_BATCH);
        List<Ray> rays = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            rays.add(new Ray(
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
                    buffer.readBlockPos(),
                    buffer.readBlockPos(),
                    buffer.readDouble(),
                    buffer.readVarInt(),
                    buffer.readDouble(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readVarInt(),
                    buffer.readVarInt()
            ));
        }

        return new RadiationRayBatchPayload(rays);
    }

    private static void encode(RadiationRayBatchPayload payload, RegistryFriendlyByteBuf buffer) {
        int count = Math.min(payload.rays.size(), MAX_RAYS_PER_BATCH);
        buffer.writeVarInt(count);
        for (int index = 0; index < count; index++) {
            Ray ray = payload.rays.get(index);
            buffer.writeDouble(ray.start.x);
            buffer.writeDouble(ray.start.y);
            buffer.writeDouble(ray.start.z);
            buffer.writeDouble(ray.end.x);
            buffer.writeDouble(ray.end.y);
            buffer.writeDouble(ray.end.z);
            buffer.writeBlockPos(ray.sourcePos);
            buffer.writeBlockPos(ray.targetPos);
            buffer.writeDouble(ray.strength);
            buffer.writeVarInt(ray.range);
            buffer.writeDouble(ray.finalChance);
            buffer.writeBoolean(ray.blocked);
            buffer.writeBoolean(ray.validTarget);
            buffer.writeBoolean(ray.affectedBlock);
            buffer.writeVarInt(ray.convertibleHits);
            buffer.writeVarInt(ray.convertedCount);
        }
    }

    public record Ray(Vec3 start, Vec3 end, BlockPos sourcePos, BlockPos targetPos, double strength, int range, double finalChance, boolean blocked, boolean validTarget, boolean affectedBlock, int convertibleHits, int convertedCount) {
    }
}
