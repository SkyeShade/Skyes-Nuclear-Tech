package com.skyeshade.skyent.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.List;

public class GeigerCounterItem extends Item {
    private static final String AUDIO_ENABLED_TAG = "AudioEnabled";

    public GeigerCounterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide) {
            boolean enabled = toggleAudio(stack);
            player.displayClientMessage(
                    Component.literal(enabled ? "Audio Enabled" : "Audio Disabled"),
                    true
            );
            level.playSound(null, player.blockPosition(), SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON, SoundSource.PLAYERS, 0.4F, 1.0F);
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.literal("Audio: " + (isAudioEnabled(stack) ? "On" : "Off")).withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.literal("Right-click to toggle audio").withStyle(ChatFormatting.DARK_GRAY));
    }

    public static boolean isAudioEnabled(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return true;
        }

        CompoundTag tag = customData.copyTag();
        return !tag.contains(AUDIO_ENABLED_TAG) || tag.getBoolean(AUDIO_ENABLED_TAG);
    }

    public static void setAudioEnabled(ItemStack stack, boolean enabled) {
        CompoundTag tag = getOrCreateCustomTag(stack);
        tag.putBoolean(AUDIO_ENABLED_TAG, enabled);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean toggleAudio(ItemStack stack) {
        boolean enabled = !isAudioEnabled(stack);
        setAudioEnabled(stack, enabled);
        return enabled;
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }
}
