package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.content.toxicity.ToxicItemValues;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class ToxicitySystem {
    public static final int TOXICITY_EFFECT_INTERVAL_TICKS = 60;
    public static final int TOXICITY_EFFECT_DURATION_TICKS = 100;

    private ToxicitySystem() {
    }

    public static void tickLivingEntity(LivingEntity entity) {
        if (!entity.isAlive() || entity.isRemoved()) {
            return;
        }
        if (entity instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return;
        }
        if (entity.tickCount % TOXICITY_EFFECT_INTERVAL_TICKS != 0) {
            return;
        }

        double toxicity = ToxicItemValues.calculateInventoryToxicity(entity);
        if (toxicity <= 0.0D) {
            return;
        }

        if (toxicity >= 1000.0D) {
            applyExtremeEffects(entity);
        } else if (toxicity >= 100.0D) {
            applyHighEffects(entity);
        } else if (toxicity >= 10.0D) {
            applyMediumEffects(entity);
        } else {
            applyLowEffects(entity);
        }
    }

    private static void applyLowEffects(LivingEntity entity) {
        addEffect(entity, MobEffects.HUNGER, 0);
    }

    private static void applyMediumEffects(LivingEntity entity) {
        addEffect(entity, MobEffects.HUNGER, 1);
        addEffect(entity, MobEffects.WEAKNESS, 3);
    }

    private static void applyHighEffects(LivingEntity entity) {
        addEffect(entity, MobEffects.HUNGER, 2);
        addEffect(entity, MobEffects.WEAKNESS, 4);
        addEffect(entity, MobEffects.POISON, 0);
        addEffect(entity, MobEffects.CONFUSION, 0);
    }

    private static void applyExtremeEffects(LivingEntity entity) {
        addEffect(entity, MobEffects.HUNGER, 2);
        addEffect(entity, MobEffects.WEAKNESS, 4);
        addEffect(entity, MobEffects.POISON, 2);
        addEffect(entity, MobEffects.CONFUSION, 1);
        addEffect(entity, MobEffects.WITHER, 1);
    }

    private static void addEffect(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
        entity.addEffect(new MobEffectInstance(
                effect,
                TOXICITY_EFFECT_DURATION_TICKS,
                amplifier,
                false,
                false,
                true
        ));
    }
}
