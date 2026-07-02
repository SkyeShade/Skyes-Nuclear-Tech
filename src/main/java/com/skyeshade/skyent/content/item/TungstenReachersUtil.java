package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class TungstenReachersUtil {
    private TungstenReachersUtil() {
    }

    public static boolean isReachers(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.TUNGSTEN_REACHERS.get());
    }

    public static boolean protectsOppositeHand(Player player, InteractionHand itemHand) {
        return isOppositeHandProtected(player, itemHand);
    }

    public static boolean isOppositeHandProtected(LivingEntity entity, InteractionHand hotOrRadioactiveHand) {
        InteractionHand oppositeHand = hotOrRadioactiveHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        return isReachers(entity.getItemInHand(oppositeHand));
    }

    public static double reduceOppositeHandRadiation(double radiation) {
        return Math.sqrt(Math.max(0.0D, radiation));
    }
}
