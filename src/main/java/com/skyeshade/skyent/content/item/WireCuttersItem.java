package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public class WireCuttersItem extends Item {
    public WireCuttersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null || !(level.getBlockEntity(context.getClickedPos()) instanceof LVConnectorBlockEntity connector)) {
            return InteractionResult.PASS;
        }

        if (connector.getConnections().isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            connector.removeAllConnections();
            context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            level.playSound(null, context.getClickedPos(), SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.1F);
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
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
