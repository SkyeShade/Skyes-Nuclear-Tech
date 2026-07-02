package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.content.item.TungstenReachersUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class HotItemSystem {
    private static final int TICK_INTERVAL = 20;
    private static final double INVENTORY_PARTIAL_COOLING_PER_SECOND = 50.0D;
    private static final double STEAM_TEMPERATURE_C = 100.0D;
    private static final float HOT_ITEM_BURN_DAMAGE = 1.0F;
    private static final int MAX_QUENCH_PARTICLES = 16;

    private HotItemSystem() {
    }

    public static void tickLivingEntity(LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }

        if (isSubmergedDeeperThanOneBlock(entity)) {
            QuenchResult quenchResult = quenchHeldItems(entity);
            if (quenchResult.steam()) {
                spawnQuenchFeedback(entity.level(), entity.getX(), entity.getY() + entity.getBbHeight() * 0.5D, entity.getZ(), quenchResult.quenchedStacks());
            }
        }

        if (entity.tickCount % TICK_INTERVAL != 0) {
            return;
        }

        boolean carriesBurningHotItem = false;
        if (entity instanceof Player player) {
            int selectedSlot = player.getInventory().selected;
            for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
                ItemStack stack = player.getInventory().items.get(slot);
                carriesBurningHotItem |= tickInventoryStack(stack, true, slot == selectedSlot
                        && !TungstenReachersUtil.isOppositeHandProtected(player, InteractionHand.MAIN_HAND));
            }
            carriesBurningHotItem |= tickInventoryStack(player.getOffhandItem(), true,
                    !TungstenReachersUtil.isOppositeHandProtected(player, InteractionHand.OFF_HAND));
            carriesBurningHotItem |= tickInventoryStacks(player.getInventory().armor, true);
        } else {
            carriesBurningHotItem |= tickHeldStack(entity, InteractionHand.MAIN_HAND);
            carriesBurningHotItem |= tickHeldStack(entity, InteractionHand.OFF_HAND);
            carriesBurningHotItem |= tickInventoryStacks(entity.getArmorSlots(), true);
        }

        if (carriesBurningHotItem && canBurn(entity)) {
            entity.hurt(entity.level().damageSources().onFire(), HOT_ITEM_BURN_DAMAGE);
        }
    }

    public static void tickItemEntity(ItemEntity itemEntity) {
        if (itemEntity.level().isClientSide || !isInWater(itemEntity)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        ItemStack cooledStack = stack.copy();
        if (quenchStack(itemEntity.level(), itemEntity.position(), cooledStack)) {
            itemEntity.setItem(cooledStack);
        }
    }

    public static boolean quenchStack(Level level, Vec3 pos, ItemStack stack) {
        if (!HotItemUtil.hasTemperature(stack)) {
            return false;
        }

        double temperature = HotItemUtil.getTemperature(stack);
        HotItemUtil.clearTemperature(stack);
        if (temperature > STEAM_TEMPERATURE_C) {
            spawnQuenchFeedback(level, pos.x(), pos.y(), pos.z(), 1);
        }
        return true;
    }

    private static boolean tickInventoryStacks(Iterable<ItemStack> stacks, boolean coolPartialHeat) {
        boolean carriesBurningHotItem = false;
        for (ItemStack stack : stacks) {
            carriesBurningHotItem |= tickInventoryStack(stack, coolPartialHeat, true);
        }
        return carriesBurningHotItem;
    }

    private static boolean tickHeldStack(LivingEntity entity, InteractionHand hand) {
        return tickInventoryStack(entity.getItemInHand(hand), true, !TungstenReachersUtil.isOppositeHandProtected(entity, hand));
    }

    private static boolean tickInventoryStack(ItemStack stack, boolean coolPartialHeat, boolean canBurnCarrier) {
        if (!HotItemUtil.hasTemperature(stack)) {
            return false;
        }

        if (coolPartialHeat) {
            coolInventoryStack(stack);
        }

        return canBurnCarrier && HotItemUtil.hasTemperature(stack) && HotItemUtil.getTemperature(stack) > STEAM_TEMPERATURE_C;
    }

    private static void coolInventoryStack(ItemStack stack) {
        double temperature = HotItemUtil.getTemperature(stack);
        double forgingTemperature = HotItemUtil.getForgingTemperature(stack);
        if (temperature <= HotItemUtil.AMBIENT_TEMPERATURE_C) {
            HotItemUtil.clearTemperature(stack);
        } else if (temperature < forgingTemperature) {
            double cooledTemperature = temperature - INVENTORY_PARTIAL_COOLING_PER_SECOND;
            if (cooledTemperature <= HotItemUtil.AMBIENT_TEMPERATURE_C) {
                HotItemUtil.clearTemperature(stack);
            } else {
                HotItemUtil.setTemperature(stack, cooledTemperature);
            }
        } else if (Double.isFinite(forgingTemperature)) {
            HotItemUtil.setTemperature(stack, forgingTemperature);
        }
    }

    private static QuenchResult quenchHeldItems(LivingEntity entity) {
        QuenchAccumulator accumulator = new QuenchAccumulator();
        quenchHeldStack(entity, InteractionHand.MAIN_HAND, accumulator);
        quenchHeldStack(entity, InteractionHand.OFF_HAND, accumulator);
        return accumulator.quenchedStacks > 0
                ? new QuenchResult(accumulator.maxTemperature > STEAM_TEMPERATURE_C, accumulator.quenchedStacks)
                : QuenchResult.NONE;
    }

    private static void quenchHeldStack(LivingEntity entity, InteractionHand hand, QuenchAccumulator accumulator) {
        ItemStack stack = entity.getItemInHand(hand);
        if (!HotItemUtil.hasTemperature(stack)) {
            return;
        }

        ItemStack cooledStack = stack.copy();
        accumulator.quenchedStacks++;
        accumulator.maxTemperature = Math.max(accumulator.maxTemperature, HotItemUtil.getTemperature(cooledStack));
        HotItemUtil.clearTemperature(cooledStack);
        entity.setItemInHand(hand, cooledStack);
    }

    private static void spawnQuenchFeedback(Level level, double x, double y, double z, int quenchedStacks) {
        level.playSound(null, x, y, z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            int particleCount = Math.min(MAX_QUENCH_PARTICLES, 4 + quenchedStacks * 3);
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, particleCount, 0.25D, 0.25D, 0.25D, 0.02D);
        }
    }

    private static boolean isInWater(Entity entity) {
        return entity.isInWaterOrBubble();
    }

    private static boolean isSubmergedDeeperThanOneBlock(LivingEntity entity) {
        Level level = entity.level();
        BlockPos feet = entity.blockPosition();
        return level.getFluidState(feet).is(FluidTags.WATER)
                && level.getFluidState(feet.above()).is(FluidTags.WATER);
    }

    private static boolean canBurn(LivingEntity entity) {
        return !(entity instanceof Player player) || !player.isCreative() && !player.isSpectator();
    }

    private record QuenchResult(boolean steam, int quenchedStacks) {
        private static final QuenchResult NONE = new QuenchResult(false, 0);
    }

    private static final class QuenchAccumulator {
        private int quenchedStacks;
        private double maxTemperature = HotItemUtil.AMBIENT_TEMPERATURE_C;
    }
}
