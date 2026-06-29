package com.skyeshade.skyent.content.item;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

public record SkyentToolTier(
        int uses,
        float speed,
        float attackDamageBonus,
        int enchantmentValue,
        Ingredient repairIngredient
) implements Tier {
    public static final SkyentToolTier TITANIUM = new SkyentToolTier(1_000, 8.0F, 3.0F, 12, Ingredient.EMPTY);
    public static final SkyentToolTier TUNGSTEN = new SkyentToolTier(1_600, 6.0F, 3.5F, 8, Ingredient.EMPTY);

    @Override
    public int getUses() {
        return uses;
    }

    @Override
    public float getSpeed() {
        return speed;
    }

    @Override
    public float getAttackDamageBonus() {
        return attackDamageBonus;
    }

    @Override
    public TagKey<Block> getIncorrectBlocksForDrops() {
        return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
    }

    @Override
    public int getEnchantmentValue() {
        return enchantmentValue;
    }

    @Override
    public Ingredient getRepairIngredient() {
        return repairIngredient;
    }
}
