package com.skyeshade.skyent.client.sound;

import com.skyeshade.skyent.content.block.LVCrusherBlock;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class MachineSoundManager {
    private static final Map<MachineLoopKey, PositionedMachineLoopSound> LOOPS = new HashMap<>();

    private MachineSoundManager() {
    }

    public static void startOrUpdateMachineLoop(ClientLevel level, BlockPos pos, SoundEvent sound, SoundSource source, float volume, float pitch) {
        MachineLoopKey key = key(level, pos, sound);
        PositionedMachineLoopSound existing = LOOPS.get(key);
        if (existing != null && !existing.isStopped()) {
            return;
        }

        PositionedMachineLoopSound loop = new PositionedMachineLoopSound(
                sound,
                source,
                pos,
                volume,
                pitch,
                () -> shouldContinue(level, pos)
        );
        LOOPS.put(key, loop);
        Minecraft.getInstance().getSoundManager().play(loop);
    }

    public static void stopMachineLoop(ClientLevel level, BlockPos pos, SoundEvent sound) {
        PositionedMachineLoopSound loop = LOOPS.remove(key(level, pos, sound));
        if (loop != null) {
            loop.stopNow();
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clearAll();
            return;
        }

        Iterator<Map.Entry<MachineLoopKey, PositionedMachineLoopSound>> iterator = LOOPS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<MachineLoopKey, PositionedMachineLoopSound> entry = iterator.next();
            PositionedMachineLoopSound loop = entry.getValue();
            if (loop.isStopped() || !entry.getKey().dimension().equals(minecraft.level.dimension())) {
                loop.stopNow();
                iterator.remove();
            }
        }
    }

    public static void clearAll() {
        for (PositionedMachineLoopSound loop : LOOPS.values()) {
            loop.stopNow();
        }
        LOOPS.clear();
    }

    private static boolean shouldContinue(ClientLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.LV_CRUSHER.get())
                && state.hasProperty(LVCrusherBlock.LIT)
                && state.getValue(LVCrusherBlock.LIT);
    }

    private static MachineLoopKey key(ClientLevel level, BlockPos pos, SoundEvent sound) {
        ResourceLocation soundId = BuiltInRegistries.SOUND_EVENT.getKey(sound);
        return new MachineLoopKey(level.dimension(), pos.immutable(), soundId);
    }

    private record MachineLoopKey(ResourceKey<Level> dimension, BlockPos pos, ResourceLocation soundId) {
    }
}
