package com.skyeshade.skyent.content.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class ForgingHammerItem extends Item {
    public ForgingHammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack remainder = stack.copy();
        remainder.setCount(1);
        int nextDamage = remainder.getDamageValue() + 1;
        if (nextDamage >= remainder.getMaxDamage()) {
            return ItemStack.EMPTY;
        }
        remainder.setDamageValue(nextDamage);
        return remainder;
    }
}
