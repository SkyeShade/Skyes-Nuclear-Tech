package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.network.PlayLocalSoundPayload;
import com.skyeshade.skyent.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class CraftingSoundSystem {
    private static final int CRAFT_SOUND_COOLDOWN_TICKS = 2;
    private static final Map<UUID, Long> LAST_WIRE_SOUND_TICK = new HashMap<>();
    private static final Map<UUID, Long> LAST_HAMMER_SOUND_TICK = new HashMap<>();

    private CraftingSoundSystem() {
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }

        ItemStack crafted = event.getCrafting();
        Container inventory = event.getInventory();
        if (isWireOutput(crafted) && containsItem(inventory, ModItems.WIRE_CUTTERS.get())) {
            playCraftSound(player, LAST_WIRE_SOUND_TICK, SoundEvents.SHEEP_SHEAR, 0.6F, 1.2F);
        } else if (isHammeredBoltOutput(crafted) && containsItem(inventory, ModItems.FORGING_HAMMER.get())) {
            playCraftSound(player, LAST_HAMMER_SOUND_TICK, SoundEvents.ANVIL_PLACE, 0.7F, 1.15F);
        }
    }

    private static boolean isWireOutput(ItemStack stack) {
        return stack.is(ModItems.COPPER_WIRE.get()) || stack.is(ModItems.STEEL_WIRE.get());
    }

    private static boolean isHammeredBoltOutput(ItemStack stack) {
        return stack.is(ModItems.IRON_BOLT.get())
                || stack.is(ModItems.COPPER_BOLT.get())
                || stack.is(ModItems.STEEL_BOLT.get());
    }

    private static boolean containsItem(Container inventory, Item item) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(item)) {
                return true;
            }
        }
        return false;
    }

    private static void playCraftSound(Player player, Map<UUID, Long> lastSoundTick, SoundEvent sound, float volume, float pitch) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        long gameTime = player.level().getGameTime();
        UUID playerId = player.getUUID();
        Long lastTick = lastSoundTick.get(playerId);
        if (lastTick != null && gameTime - lastTick < CRAFT_SOUND_COOLDOWN_TICKS) {
            return;
        }

        lastSoundTick.put(playerId, gameTime);
        PacketDistributor.sendToPlayer(serverPlayer, new PlayLocalSoundPayload(BuiltInRegistries.SOUND_EVENT.getKey(sound), volume, pitch));
    }
}
