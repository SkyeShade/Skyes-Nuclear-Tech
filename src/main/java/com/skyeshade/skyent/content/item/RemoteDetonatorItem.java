package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.block.NuclearChargeBlock;
import com.skyeshade.skyent.content.entity.NuclearExplosionEntity;
import com.skyeshade.skyent.content.entity.NuclearExplosionChunkLoading;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class RemoteDetonatorItem extends Item {
    private static final String TARGET_DIMENSION_TAG = "TargetDimension";
    private static final String TARGET_X_TAG = "TargetX";
    private static final String TARGET_Y_TAG = "TargetY";
    private static final String TARGET_Z_TAG = "TargetZ";

    public RemoteDetonatorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            bind(context.getItemInHand(), context.getLevel(), context.getClickedPos(), player);
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        return trigger(context.getLevel(), player, context.getHand(), context.getItemInHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        return trigger(level, player, usedHand, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Target target = readTarget(stack);
        if (target == null) {
            tooltipComponents.add(Component.literal("Not linked").withStyle(ChatFormatting.GRAY));
            return;
        }

        tooltipComponents.add(Component.literal("Linked: "
                + target.pos().getX() + " "
                + target.pos().getY() + " "
                + target.pos().getZ() + " in "
                + target.dimension().location()).withStyle(ChatFormatting.GRAY));
    }

    private static void bind(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            CompoundTag tag = getOrCreateCustomTag(stack);
            tag.putString(TARGET_DIMENSION_TAG, level.dimension().location().toString());
            tag.putInt(TARGET_X_TAG, pos.getX());
            tag.putInt(TARGET_Y_TAG, pos.getY());
            tag.putInt(TARGET_Z_TAG, pos.getZ());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            player.displayClientMessage(Component.literal("Linked target: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()), true);
        }
    }

    private static InteractionResultHolder<ItemStack> trigger(Level level, Player player, InteractionHand hand, ItemStack stack) {
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        Target target = readTarget(stack);
        if (target == null) {
            player.displayClientMessage(Component.literal("No target linked."), true);
            return InteractionResultHolder.success(stack);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }

        ServerLevel targetLevel = serverPlayer.server.getLevel(target.dimension());
        if (targetLevel == null) {
            player.displayClientMessage(Component.literal("Target dimension is not loaded."), true);
            return InteractionResultHolder.success(stack);
        }

        UUID ticketOwner = UUID.randomUUID();
        ChunkPos targetChunk = new ChunkPos(target.pos());

        NuclearExplosionChunkLoading.NuclearExplosionChunkLease lease = NuclearExplosionChunkLoading.forceTemporaryDetonationChunk(
                targetLevel,
                targetChunk,
                ticketOwner
        );
        String releaseReason = "invalid_target";
        try {
            targetLevel.getChunkAt(target.pos());

            BlockState state = targetLevel.getBlockState(target.pos());
            boolean validCharge = state.is(ModBlocks.NUCLEAR_CHARGE.get());
            if (!validCharge) {
                player.displayClientMessage(Component.literal("Target is not a valid receiver."), true);
                return InteractionResultHolder.success(stack);
            }

            releaseReason = NuclearChargeBlock.detonate(targetLevel, target.pos(), player) ? "detonated" : "detonation_failed";
            return InteractionResultHolder.success(stack);
        } finally {
            NuclearExplosionChunkLoading.unforceExplosionChunks(targetLevel, lease.ownerUuid(), lease.chunks());
        }
    }

    private static Target readTarget(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        CompoundTag tag = customData.copyTag();
        if (!tag.contains(TARGET_DIMENSION_TAG)
                || !tag.contains(TARGET_X_TAG)
                || !tag.contains(TARGET_Y_TAG)
                || !tag.contains(TARGET_Z_TAG)) {
            return null;
        }

        ResourceLocation dimensionId = ResourceLocation.tryParse(tag.getString(TARGET_DIMENSION_TAG));
        if (dimensionId == null) {
            return null;
        }

        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        BlockPos pos = new BlockPos(tag.getInt(TARGET_X_TAG), tag.getInt(TARGET_Y_TAG), tag.getInt(TARGET_Z_TAG));
        return new Target(dimension, pos);
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }

    private record Target(ResourceKey<Level> dimension, BlockPos pos) {
    }
}
