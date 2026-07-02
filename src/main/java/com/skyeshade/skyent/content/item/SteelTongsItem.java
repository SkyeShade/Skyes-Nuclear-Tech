package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.blockentity.ForgingAnvilBlockEntity;
import com.skyeshade.skyent.content.radiation.RadiationItemValues;
import com.skyeshade.skyent.registry.ModItems;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class SteelTongsItem extends Item {
    private static final String HELD_STACK_TAG = "HeldStack";
    private static final DecimalFormat RADIATION_DECIMAL_FORMAT;

    static {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.ROOT);
        RADIATION_DECIMAL_FORMAT = new DecimalFormat("#.##", symbols);
        RADIATION_DECIMAL_FORMAT.setRoundingMode(RoundingMode.HALF_UP);
    }

    public SteelTongsItem(Properties properties) {
        super(properties);
    }

    public static boolean isTongs(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.STEEL_TONGS.get());
    }

    public static boolean isSteelTongs(ItemStack stack) {
        return isTongs(stack);
    }

    public static boolean hasHeldStack(ItemStack tongsStack) {
        CustomData customData = tongsStack.get(DataComponents.CUSTOM_DATA);
        return customData != null && customData.copyTag().contains(HELD_STACK_TAG);
    }

    public static ItemStack getHeldStack(ItemStack tongsStack, HolderLookup.Provider registries) {
        CustomData customData = tongsStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return ItemStack.EMPTY;
        }
        CompoundTag tag = customData.copyTag();
        return tag.contains(HELD_STACK_TAG) ? ItemStack.parseOptional(registries, tag.getCompound(HELD_STACK_TAG)) : ItemStack.EMPTY;
    }

    public static void setHeldStack(ItemStack tongsStack, ItemStack held, HolderLookup.Provider registries) {
        if (!isTongs(tongsStack)) {
            return;
        }
        if (held.isEmpty()) {
            clearHeldStack(tongsStack);
            return;
        }
        if (isTongs(held)) {
            return;
        }

        CompoundTag tag = getOrCreateCustomTag(tongsStack);
        tag.put(HELD_STACK_TAG, held.save(registries));
        tongsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static ItemStack removeHeldStack(ItemStack tongsStack, HolderLookup.Provider registries) {
        ItemStack held = getHeldStack(tongsStack, registries);
        clearHeldStack(tongsStack);
        return held;
    }

    public static boolean canAcceptHeldStack(ItemStack tongsStack, ItemStack incoming, HolderLookup.Provider registries) {
        if (!isTongs(tongsStack) || incoming.isEmpty() || isTongs(incoming)) {
            return false;
        }

        ItemStack held = getHeldStack(tongsStack, registries);
        if (held.isEmpty()) {
            return true;
        }

        return ItemStack.isSameItemSameComponents(held, incoming) && held.getCount() < held.getMaxStackSize();
    }

    public static ItemStack insertIntoTongs(ItemStack tongsStack, ItemStack incoming, HolderLookup.Provider registries) {
        if (!canAcceptHeldStack(tongsStack, incoming, registries)) {
            return incoming.copy();
        }

        ItemStack remainder = incoming.copy();
        ItemStack held = getHeldStack(tongsStack, registries);
        if (held.isEmpty()) {
            setHeldStack(tongsStack, remainder, registries);
            return ItemStack.EMPTY;
        }

        int room = held.getMaxStackSize() - held.getCount();
        int moved = Math.min(room, remainder.getCount());
        if (moved <= 0) {
            return remainder;
        }

        held.grow(moved);
        remainder.shrink(moved);
        setHeldStack(tongsStack, held, registries);
        return remainder;
    }

    public static void clearHeldStack(ItemStack tongsStack) {
        CustomData customData = tongsStack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }
        CompoundTag tag = customData.copyTag();
        tag.remove(HELD_STACK_TAG);
        tongsStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof ForgingAnvilBlockEntity anvil)) {
            return InteractionResult.PASS;
        }

        ItemStack tongs = context.getItemInHand();
        HolderLookup.Provider registries = level.registryAccess();
        if (hasHeldStack(tongs)) {
            ItemStack held = getHeldStack(tongs, registries);
            if (held.isEmpty() || anvil.hasInput() || !ForgingAnvilRecipes.isAnvilInput(held)) {
                return InteractionResult.PASS;
            }

            if (!level.isClientSide) {
                ItemStack placed = held.copyWithCount(1);
                anvil.setInput(placed);
                held.shrink(1);
                setHeldStack(tongs, held, registries);
                level.playSound(null, context.getClickedPos(), SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!anvil.hasInput()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            setHeldStack(tongs, anvil.removeInput(), registries);
            level.playSound(null, context.getClickedPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        ItemStack held = getHeldStack(stack, context.registries());
        if (held.isEmpty()) {
            tooltipComponents.add(Component.translatable("tooltip.skyent.steel_tongs.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltipComponents.add(Component.translatable(
                "tooltip.skyent.steel_tongs.holding",
                held.getHoverName(),
                held.getCount()
        ).withStyle(ChatFormatting.GRAY));
        appendHandledRadiationTooltip(held, tooltipComponents);
    }

    private static void appendHandledRadiationTooltip(ItemStack held, List<Component> tooltipComponents) {
        double rawStackRadiation = RadiationItemValues.getStackRadiation(held);
        if (rawStackRadiation <= 0.0D) {
            return;
        }

        double handledRadiation = Math.sqrt(rawStackRadiation);
        tooltipComponents.add(Component.translatable(
                "tooltip.skyent.steel_tongs.handled_radiation",
                formatTongsRadiation(handledRadiation)
        ).withStyle(ChatFormatting.YELLOW));
        tooltipComponents.add(Component.translatable(
                "tooltip.skyent.steel_tongs.environmental_emission",
                formatTongsRadiation(rawStackRadiation)
        ).withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String formatTongsRadiation(double value) {
        return RADIATION_DECIMAL_FORMAT.format(value);
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }
}
