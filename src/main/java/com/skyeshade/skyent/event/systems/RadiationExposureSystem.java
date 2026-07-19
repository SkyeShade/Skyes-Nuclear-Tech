package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.config.SkyentRadiationConfig;
import com.skyeshade.skyent.content.radiation.CarriedRadiationUtil;
import com.skyeshade.skyent.content.radiation.ModDamageSources;
import com.skyeshade.skyent.content.radiation.RadiationExposureData;
import com.skyeshade.skyent.content.radiation.RadiationExposureUtil;
import com.skyeshade.skyent.content.radiation.RadiationItemValues;
import com.skyeshade.skyent.network.RadiationDebugOverlayPayload;
import com.skyeshade.skyent.network.GeigerExposurePayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class RadiationExposureSystem {
    public static final double MAX_RADIATION_SICKNESS = 1000.0D;
    public static final double IRREVERSIBLE_THRESHOLD = 800.0D;
    public static final double MAX_HEALTH_LOSS_START = 400.0D;
    public static final double LETHAL_THRESHOLD = 1000.0D;

    public static final double EXPOSURE_TO_SICKNESS_SCALE = 0.08D;
    public static final double RECOVERY_PER_SECOND = 0.25D;
    public static final double IRREVERSIBLE_WORSENING_PER_SECOND = 0.25D;
    public static final double NETHER_AMBIENT_RADIATION_MSV_PER_SECOND = 3.0D;

    private static final int SYMPTOM_INTERVAL_TICKS = 100;
    private static final String PERSISTED_TAG = SkyesNuclearTech.MOD_ID + ":radiation_sickness";
    private static final String SICKNESS_TAG = "sickness";
    private static final ResourceLocation RADIATION_MAX_HEALTH_LOSS_ID = ResourceLocation.fromNamespaceAndPath(
            SkyesNuclearTech.MOD_ID,
            "radiation_max_health_loss"
    );
    private static final boolean FORCE_GEIGER_ACTIONBAR_DEBUG = false;
    private static final boolean DEBUG_RADIATION_ACTIONBAR_LOGS = false;

    private static final Map<UUID, RadiationExposureData> ENTITY_DATA = new HashMap<>();
    private static final Set<UUID> PLAYERS_WITH_DEBUG_OVERLAY = new HashSet<>();

    private RadiationExposureSystem() {
    }


    public static void tickPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        RadiationExposureData data = getOrLoadData(player);
        int updateInterval = SkyentRadiationConfig.exposurePlayerUpdateIntervalTicks();
        if (gameTime - data.getLastExposureUpdateTick() < updateInterval) {
            return;
        }

        Vec3 samplePos = player.getEyePosition();
        RadiationExposureUtil.ExposureScanResult scan = RadiationExposureUtil.scanEnvironmentalExposure(
                level,
                samplePos,
                SkyentRadiationConfig.exposureRadioactiveBlockScanRadius(),
                player
        );
        double exposure = scan.exposureMillisievertsPerSecond() + getAmbientRadiationMillisievertsPerSecond(player);
        double inventoryExposure = RadiationItemValues.calculateInventoryRadiation(player);
        emitCarriedEnvironmentalRadiation(level, player, data, gameTime);
        data.setCurrentEnvironmentalExposureMillisievertsPerSecond(exposure);
        data.setCurrentInventoryExposureMillisievertsPerSecond(inventoryExposure);
        data.setCurrentTotalExposureMillisievertsPerSecond(exposure + inventoryExposure);
        data.setLastExposureUpdateTick(gameTime);
        updateRadiationSickness(player, data, gameTime, updateInterval, Integer.MAX_VALUE);
        saveData(player, data);
        PacketDistributor.sendToPlayer(player, new GeigerExposurePayload(data.getCurrentTotalExposureMillisievertsPerSecond(), data.getRadiationSickness()));
        sendPeriodicDebugOverlay(player, scan, data, gameTime);
    }

    public static void tickLivingEntity(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            tickPlayer(player);
            return;
        }

        RadiationEntityUpdateScheduler.enqueueIfDue(entity);
    }

    static void tickScheduledNonPlayerEntity(LivingEntity entity, int elapsedTicks) {
        if (entity instanceof ServerPlayer player) {
            tickPlayer(player);
            return;
        }

        if (!entity.isAlive() || entity.isRemoved() || !(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        RadiationExposureData data = getOrLoadData(entity);

        Vec3 samplePos = entity.getEyePosition();
        RadiationExposureUtil.ExposureScanResult scan = RadiationExposureUtil.scanEnvironmentalExposure(
                level,
                samplePos,
                SkyentRadiationConfig.exposureRadioactiveBlockScanRadius(),
                entity
        );
        double environmentalExposure = scan.exposureMillisievertsPerSecond() + getAmbientRadiationMillisievertsPerSecond(entity);
        double inventoryExposure = RadiationItemValues.calculateInventoryRadiation(entity);
        emitCarriedEnvironmentalRadiation(level, entity, data, gameTime);
        data.setCurrentEnvironmentalExposureMillisievertsPerSecond(environmentalExposure);
        data.setCurrentInventoryExposureMillisievertsPerSecond(inventoryExposure);
        data.setCurrentTotalExposureMillisievertsPerSecond(environmentalExposure + inventoryExposure);
        data.setLastExposureUpdateTick(gameTime);
        updateRadiationSickness(entity, data, gameTime, elapsedTicks, elapsedTicks);
        saveData(entity, data);
    }

    static long lastExposureUpdateTick(LivingEntity entity) {
        return getOrLoadData(entity).getLastExposureUpdateTick();
    }

    public static void applyDirectEnvironmentalExposure(LivingEntity entity, double exposureMillisievertsPerSecond, int exposureTicks) {
        if (exposureMillisievertsPerSecond <= 0.0D || exposureTicks <= 0 || !entity.isAlive() || entity.isRemoved()) {
            return;
        }
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        long gameTime = level.getGameTime();
        RadiationExposureData data = getOrLoadData(entity);
        double inventoryExposure = data.getCurrentInventoryExposureMillisievertsPerSecond();
        data.setCurrentEnvironmentalExposureMillisievertsPerSecond(Math.max(
                data.getCurrentEnvironmentalExposureMillisievertsPerSecond(),
                exposureMillisievertsPerSecond
        ));
        data.setCurrentTotalExposureMillisievertsPerSecond(Math.max(
                data.getCurrentTotalExposureMillisievertsPerSecond(),
                exposureMillisievertsPerSecond + inventoryExposure
        ));
        data.setLastExposureUpdateTick(gameTime);

        if (isRadiationImmune(entity)) {
            removeRadiationMaxHealthModifier(entity);
            data.setLastSicknessUpdateTick(gameTime);
            syncDirectPlayerExposure(entity, data);
            return;
        }

        double elapsedSeconds = exposureTicks / 20.0D;
        double sickness = data.getRadiationSickness();
        sickness += exposureMillisievertsPerSecond * EXPOSURE_TO_SICKNESS_SCALE * elapsedSeconds;
        data.setRadiationSickness(Mth.clamp(sickness, 0.0D, MAX_RADIATION_SICKNESS));
        data.setLastSicknessUpdateTick(gameTime);
        applyRadiationMaxHealthModifier(entity, data.getRadiationSickness());
        applyRadiationSymptoms(entity, data, gameTime);

        if (data.getRadiationSickness() >= LETHAL_THRESHOLD && entity.isAlive()) {
            entity.invulnerableTime = 0;
            entity.hurtTime = 0;
            entity.hurt(ModDamageSources.radiation(level), 1.0E9F);
        }

        saveData(entity, data);
        syncDirectPlayerExposure(entity, data);
    }

    public static PointSourceTickResult tickPointSource(
            ServerLevel level,
            Vec3 sourceCenter,
            double sourceMillisievertsPerSecond,
            double radius,
            int exposureTicks,
            boolean collectDebug
    ) {
        if (sourceMillisievertsPerSecond <= 0.0D || radius <= 0.0D || exposureTicks <= 0) {
            return PointSourceTickResult.EMPTY;
        }

        double radiusSqr = radius * radius;
        AABB bounds = new AABB(sourceCenter, sourceCenter).inflate(radius);
        int checked = 0;
        int exposed = 0;
        int playersChecked = 0;
        int playersExposed = 0;
        int immunePlayersSkippedDamage = 0;
        double maxEntityExposureMillisievertsPerSecond = 0.0D;
        double maxPlayerExposureMillisievertsPerSecond = 0.0D;
        double nearestPlayerDistance = Double.NaN;
        double nearestPlayerExposureMillisievertsPerSecond = 0.0D;
        double nearestPlayerDoseMillisievertsThisTick = 0.0D;
        double nearestPlayerTransmission = 0.0D;
        boolean nearestPlayerImmune = false;
        String nearestPlayerName = "";
        StringBuilder playerDetails = collectDebug ? new StringBuilder() : null;

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, bounds, entity -> entity.isAlive() && !entity.isRemoved())) {
            Vec3 samplePos = entity.getEyePosition();
            if (samplePos.distanceToSqr(sourceCenter) > radiusSqr) {
                continue;
            }

            checked++;
            RadiationExposureUtil.PointSourceExposure exposure = RadiationExposureUtil.calculatePointSourceExposure(
                    level,
                    sourceCenter,
                    samplePos,
                    sourceMillisievertsPerSecond,
                    radius
            );
            double exposureMillisievertsPerSecond = exposure.exposureMillisievertsPerSecond();
            if (entity instanceof ServerPlayer player) {
                playersChecked++;
                boolean immune = isRadiationImmune(player);
                if (immune) {
                    immunePlayersSkippedDamage++;
                }
                if (Double.isNaN(nearestPlayerDistance) || exposure.distance() < nearestPlayerDistance) {
                    nearestPlayerDistance = exposure.distance();
                    nearestPlayerExposureMillisievertsPerSecond = exposureMillisievertsPerSecond;
                    // Exposure is an mSv/s rate; direct dose for one tick is rate / 20.
                    nearestPlayerDoseMillisievertsThisTick = exposureMillisievertsPerSecond * exposureTicks / 20.0D;
                    nearestPlayerTransmission = exposure.transmission();
                    nearestPlayerImmune = immune;
                    nearestPlayerName = player.getGameProfile().getName();
                }
            }
            if (exposureMillisievertsPerSecond <= 0.0D) {
                continue;
            }

            applyDirectEnvironmentalExposure(entity, exposureMillisievertsPerSecond, exposureTicks);
            exposed++;
            maxEntityExposureMillisievertsPerSecond = Math.max(maxEntityExposureMillisievertsPerSecond, exposureMillisievertsPerSecond);
            if (entity instanceof ServerPlayer player) {
                playersExposed++;
                maxPlayerExposureMillisievertsPerSecond = Math.max(maxPlayerExposureMillisievertsPerSecond, exposureMillisievertsPerSecond);
                if (collectDebug && playersExposed <= 4) {
                    if (playerDetails.length() > 0) {
                        playerDetails.append("; ");
                    }
                    playerDetails.append(player.getGameProfile().getName())
                            .append("=")
                            .append(String.format("%.3f", exposureMillisievertsPerSecond))
                            .append("mSv/s d=")
                            .append(String.format("%.1f", exposure.distance()))
                            .append(" t=")
                            .append(String.format("%.3f", exposure.transmission()))
                            .append(isRadiationImmune(player) ? " immune" : "");
                }
            }
        }

        return new PointSourceTickResult(
                checked,
                exposed,
                playersChecked,
                playersExposed,
                immunePlayersSkippedDamage,
                maxEntityExposureMillisievertsPerSecond,
                maxPlayerExposureMillisievertsPerSecond,
                nearestPlayerDistance,
                nearestPlayerExposureMillisievertsPerSecond,
                nearestPlayerDoseMillisievertsThisTick,
                nearestPlayerTransmission,
                nearestPlayerImmune,
                nearestPlayerName,
                playerDetails == null ? "" : playerDetails.toString()
        );
    }

    public static boolean isImmuneToRadiationEffects(LivingEntity entity) {
        return isRadiationImmune(entity);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ENTITY_DATA.remove(event.getEntity().getUUID());
        PLAYERS_WITH_DEBUG_OVERLAY.remove(event.getEntity().getUUID());
    }

    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer) || !(event.getOriginal() instanceof ServerPlayer oldPlayer)) {
            return;
        }

        RadiationExposureData data = new RadiationExposureData();
        if (!event.isWasDeath()) {
            RadiationExposureData oldData = ENTITY_DATA.get(oldPlayer.getUUID());
            if (oldData == null) {
                oldData = loadData(oldPlayer);
            }
            data.setRadiationSickness(oldData.getRadiationSickness());
            data.setCurrentEnvironmentalExposureMillisievertsPerSecond(oldData.getCurrentEnvironmentalExposureMillisievertsPerSecond());
            data.setCurrentInventoryExposureMillisievertsPerSecond(oldData.getCurrentInventoryExposureMillisievertsPerSecond());
            data.setCurrentTotalExposureMillisievertsPerSecond(oldData.getCurrentTotalExposureMillisievertsPerSecond());
        }

        ENTITY_DATA.put(newPlayer.getUUID(), data);
        saveData(newPlayer, data);
        if (event.isWasDeath()) {
            removeRadiationMaxHealthModifier(newPlayer);
        }
    }

    public static void setDebugOverlayEnabled(ServerPlayer player, boolean enabled) {
        if (enabled) {
            PLAYERS_WITH_DEBUG_OVERLAY.add(player.getUUID());
        } else {
            PLAYERS_WITH_DEBUG_OVERLAY.remove(player.getUUID());
        }

        if (DEBUG_RADIATION_ACTIONBAR_LOGS) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation debug overlay {} for {}",
                    enabled ? "enabled" : "disabled",
                    player.getGameProfile().getName()
            );
        }
    }

    public static boolean isDebugOverlayEnabled(ServerPlayer player) {
        return FORCE_GEIGER_ACTIONBAR_DEBUG || PLAYERS_WITH_DEBUG_OVERLAY.contains(player.getUUID());
    }

    public static void sendDebugOverlayEnabled(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new RadiationDebugOverlayPayload(
                true,
                "Radiation debug enabled | waiting for exposure update",
                ""
        ));
    }

    public static void sendDebugOverlayDisabled(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, RadiationDebugOverlayPayload.disabled());
    }

    private static void sendPeriodicDebugOverlay(
            ServerPlayer player,
            RadiationExposureUtil.ExposureScanResult scan,
            RadiationExposureData data,
            long gameTime
    ) {
        if (!isDebugOverlayEnabled(player)) {
            return;
        }

        if (gameTime - data.getLastDebugOverlayTick() < 20L) {
            return;
        }

        data.setLastDebugOverlayTick(gameTime);
        RadiationDebugOverlayPayload payload = scan == null
                ? new RadiationDebugOverlayPayload(true, "Radiation debug enabled | no scan data", "")
                : formatDebugOverlayPayload(scan, data, player);

        if (DEBUG_RADIATION_ACTIONBAR_LOGS) {
            SkyesNuclearTech.LOGGER.info(
                    "Radiation debug overlay periodic send: player={}, uuid={}, gameTime={}, line1={}, line2={}",
                    player.getGameProfile().getName(),
                    player.getUUID(),
                    gameTime,
                    payload.line1(),
                    payload.line2()
            );
        }

        PacketDistributor.sendToPlayer(player, payload);
    }

    private static void syncDirectPlayerExposure(LivingEntity entity, RadiationExposureData data) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }

        PacketDistributor.sendToPlayer(player, new GeigerExposurePayload(data.getCurrentTotalExposureMillisievertsPerSecond(), data.getRadiationSickness()));
        sendDirectExposureDebugOverlay(player, data, player.level().getGameTime());
    }

    private static void sendDirectExposureDebugOverlay(ServerPlayer player, RadiationExposureData data, long gameTime) {
        if (!isDebugOverlayEnabled(player)) {
            return;
        }

        if (gameTime - data.getLastDebugOverlayTick() < 10L) {
            return;
        }

        data.setLastDebugOverlayTick(gameTime);
        int hpLossPercent = radiationHealthLossPercent(data.getRadiationSickness(), player);
        String line1 = String.format(
                "Nuke burst: %.1f mSv/s | Total: %.1f mSv/s | Sick: %.0f/1000 | HP loss: %d%%",
                data.getCurrentEnvironmentalExposureMillisievertsPerSecond(),
                data.getCurrentTotalExposureMillisievertsPerSecond(),
                data.getRadiationSickness(),
                hpLossPercent
        );
        String line2 = isRadiationImmune(player)
                ? "Nuke burst exposure visible | sickness/damage skipped by immunity"
                : "Nuke burst exposure visible | shielding applied before dose";
        PacketDistributor.sendToPlayer(player, new RadiationDebugOverlayPayload(true, line1, line2));
    }

    private static RadiationExposureData getOrLoadData(LivingEntity entity) {
        return ENTITY_DATA.computeIfAbsent(entity.getUUID(), ignored -> loadData(entity));
    }

    private static RadiationExposureData loadData(LivingEntity entity) {
        RadiationExposureData data = new RadiationExposureData();
        CompoundTag persisted = entity.getPersistentData();
        if (persisted.contains(PERSISTED_TAG)) {
            CompoundTag tag = persisted.getCompound(PERSISTED_TAG);
            data.setRadiationSickness(Mth.clamp(tag.getDouble(SICKNESS_TAG), 0.0D, MAX_RADIATION_SICKNESS));
        }
        return data;
    }

    private static void saveData(LivingEntity entity, RadiationExposureData data) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble(SICKNESS_TAG, Mth.clamp(data.getRadiationSickness(), 0.0D, MAX_RADIATION_SICKNESS));
        entity.getPersistentData().put(PERSISTED_TAG, tag);
    }

    private static void updateRadiationSickness(
            LivingEntity entity,
            RadiationExposureData data,
            long gameTime,
            int exposureUpdateInterval,
            int maxElapsedTicks
    ) {
        if (isRadiationImmune(entity)) {
            removeRadiationMaxHealthModifier(entity);
            data.setLastSicknessUpdateTick(gameTime);
            return;
        }

        long lastUpdate = data.getLastSicknessUpdateTick();
        long rawElapsedTicks = lastUpdate == 0L ? exposureUpdateInterval : Math.max(1L, gameTime - lastUpdate);
        long elapsedTicks = Math.min(rawElapsedTicks, Math.max(1, maxElapsedTicks));
        double elapsedSeconds = elapsedTicks / 20.0D;
        double sickness = data.getRadiationSickness();
        sickness += data.getCurrentTotalExposureMillisievertsPerSecond() * EXPOSURE_TO_SICKNESS_SCALE * elapsedSeconds;

        if (sickness > 0.0D && sickness < IRREVERSIBLE_THRESHOLD) {
            sickness = Math.max(0.0D, sickness - RECOVERY_PER_SECOND * elapsedSeconds);
        } else if (sickness >= IRREVERSIBLE_THRESHOLD && sickness < MAX_RADIATION_SICKNESS) {
            sickness = Math.min(MAX_RADIATION_SICKNESS, sickness + IRREVERSIBLE_WORSENING_PER_SECOND * elapsedSeconds);
        }

        data.setRadiationSickness(Mth.clamp(sickness, 0.0D, MAX_RADIATION_SICKNESS));
        data.setLastSicknessUpdateTick(gameTime);
        applyRadiationMaxHealthModifier(entity, data.getRadiationSickness());
        applyRadiationSymptoms(entity, data, gameTime);
        // TODO: Suppress only natural regeneration at high sickness without blocking potions or modded healing.

        if (data.getRadiationSickness() >= LETHAL_THRESHOLD && entity.isAlive() && entity.level() instanceof ServerLevel level) {
            entity.invulnerableTime = 0;
            entity.hurtTime = 0;
            entity.hurt(ModDamageSources.radiation(level), 1.0E9F);
        }
    }

    private static void emitCarriedEnvironmentalRadiation(ServerLevel level, LivingEntity entity, RadiationExposureData data, long gameTime) {
        if (gameTime - data.getLastCarriedEnvironmentalRayTick() < 20L) {
            return;
        }

        data.setLastCarriedEnvironmentalRayTick(gameTime);
        double strength = CarriedRadiationUtil.carriedRadiationStrength(entity);
        CarriedRadiationUtil.emitEnvironmentalRays(level, entity, strength);
    }

    private static double getAmbientRadiationMillisievertsPerSecond(LivingEntity entity) {
        return entity.level().dimension() == Level.NETHER ? NETHER_AMBIENT_RADIATION_MSV_PER_SECOND : 0.0D;
    }

    private static boolean isRadiationImmune(LivingEntity entity) {
        return entity instanceof Player player && (player.isCreative() || player.isSpectator());
    }

    private static void applyRadiationMaxHealthModifier(LivingEntity entity, double radiationSickness) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }

        maxHealth.removeModifier(RADIATION_MAX_HEALTH_LOSS_ID);
        if (radiationSickness < MAX_HEALTH_LOSS_START) {
            return;
        }

        double maxHealthWithoutRadiation = maxHealth.getValue();
        double t = Mth.clamp(
                (radiationSickness - MAX_HEALTH_LOSS_START) / (MAX_RADIATION_SICKNESS - MAX_HEALTH_LOSS_START),
                0.0D,
                1.0D
        );
        double maxLoss = Math.max(0.0D, maxHealthWithoutRadiation - 2.0D);
        double healthLoss = Math.min(maxLoss, maxHealthWithoutRadiation * t);
        if (healthLoss <= 0.0D) {
            return;
        }

        maxHealth.addTransientModifier(new AttributeModifier(
                RADIATION_MAX_HEALTH_LOSS_ID,
                -healthLoss,
                AttributeModifier.Operation.ADD_VALUE
        ));
        if (entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }
    }

    private static void removeRadiationMaxHealthModifier(LivingEntity entity) {
        AttributeInstance maxHealth = entity.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.removeModifier(RADIATION_MAX_HEALTH_LOSS_ID);
        }
    }

    private static void applyRadiationSymptoms(LivingEntity entity, RadiationExposureData data, long gameTime) {
        double sickness = data.getRadiationSickness();
        if (sickness < 100.0D || gameTime - data.getLastSymptomTick() < SYMPTOM_INTERVAL_TICKS) {
            return;
        }

        data.setLastSymptomTick(gameTime);
        if (sickness >= 100.0D && entity.getRandom().nextDouble() < symptomChance(sickness, 0.10D, 0.30D)) {
            entity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 180, 0, true, true));
        }
        if (entity instanceof Player && sickness >= 250.0D && entity.getRandom().nextDouble() < symptomChance(sickness, 0.20D, 0.45D)) {
            entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 120, sickness >= 800.0D ? 1 : 0, true, true));
        }
        if (sickness >= 250.0D && entity.getRandom().nextDouble() < symptomChance(sickness, 0.15D, 0.40D)) {
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, sickness >= 800.0D ? 1 : 0, true, true));
        }
        if (sickness >= 500.0D && entity.getRandom().nextDouble() < symptomChance(sickness, 0.15D, 0.35D)) {
            entity.addEffect(new MobEffectInstance(MobEffects.POISON, 60, sickness >= 800.0D ? 1 : 0, true, true));
        }
        if (sickness >= 700.0D && entity.getRandom().nextDouble() < symptomChance(sickness, 0.10D, 0.30D)) {
            entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, sickness >= 900.0D ? 1 : 0, true, true));
        }

    }

    private static double symptomChance(double sickness, double lowChance, double highChance) {
        double t = Mth.clamp((sickness - 100.0D) / (MAX_RADIATION_SICKNESS - 100.0D), 0.0D, 1.0D);
        return Mth.lerp(t, lowChance, highChance);
    }

    private static RadiationDebugOverlayPayload formatDebugOverlayPayload(RadiationExposureUtil.ExposureScanResult scan, RadiationExposureData data, ServerPlayer player) {
        String nearest = Double.isNaN(scan.nearestSourceDistance()) ? "--" : String.format("%.1fm", scan.nearestSourceDistance());
        int hpLossPercent = radiationHealthLossPercent(data.getRadiationSickness(), player);
        String line1 = String.format(
                "Env: %.1f | Inv: %.1f | Total: %.1f mSv/s | Sick: %.0f/1000 | HP loss: %d%%",
                scan.exposureMillisievertsPerSecond(),
                data.getCurrentInventoryExposureMillisievertsPerSecond(),
                data.getCurrentTotalExposureMillisievertsPerSecond(),
                data.getRadiationSickness(),
                hpLossPercent
        );
        String line2 = String.format(
                "Sources: %d/%d | Nearest: %s | Strongest: %.1f mSv/s | Registry: %d/%d",
                scan.contributingSources(),
                scan.sourcesFound(),
                nearest,
                scan.strongestSourceContribution(),
                scan.registryCandidates(),
                scan.registeredSources()
        );
        return new RadiationDebugOverlayPayload(true, line1, line2);
    }

    private static int radiationHealthLossPercent(double radiationSickness, ServerPlayer player) {
        if (radiationSickness < MAX_HEALTH_LOSS_START || isRadiationImmune(player)) {
            return 0;
        }

        double t = Mth.clamp(
                (radiationSickness - MAX_HEALTH_LOSS_START) / (MAX_RADIATION_SICKNESS - MAX_HEALTH_LOSS_START),
                0.0D,
                1.0D
        );
        return Mth.floor(t * 100.0D);
    }

    public record PointSourceTickResult(
            int checkedEntities,
            int exposedEntities,
            int checkedPlayers,
            int exposedPlayers,
            int immunePlayersSkippedDamage,
            double maxEntityExposureMillisievertsPerSecond,
            double maxPlayerExposureMillisievertsPerSecond,
            double nearestPlayerDistance,
            double nearestPlayerExposureMillisievertsPerSecond,
            double nearestPlayerDoseMillisievertsThisTick,
            double nearestPlayerTransmission,
            boolean nearestPlayerImmune,
            String nearestPlayerName,
            String playerDetails
    ) {
        private static final PointSourceTickResult EMPTY = new PointSourceTickResult(
                0,
                0,
                0,
                0,
                0,
                0.0D,
                0.0D,
                Double.NaN,
                0.0D,
                0.0D,
                0.0D,
                false,
                "",
                ""
        );
    }
}
