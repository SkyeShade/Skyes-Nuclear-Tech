package com.skyeshade.skyent.content.entity;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class NukePerformanceBudget {
    private static final double TARGET_MSPT = 50.0D;
    private static final double SOFT_THROTTLE_MSPT = 65.0D;
    private static final double HARD_THROTTLE_MSPT = 100.0D;
    private static final double MIN_WORK_SCALE = 0.15D;
    private static long tickStartNs;
    private static double averageMspt = TARGET_MSPT;

    private NukePerformanceBudget() {
    }

    public static void onServerTickPre(ServerTickEvent.Pre event) {
        tickStartNs = System.nanoTime();
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (tickStartNs == 0L) {
            return;
        }
        double tickMs = (System.nanoTime() - tickStartNs) / 1_000_000.0D;
        averageMspt += (tickMs - averageMspt) * 0.08D;
    }

    public static double averageMspt(MinecraftServer server) {
        return Mth.clamp(averageMspt, 1.0D, 1000.0D);
    }

    public static double currentWorkScale(MinecraftServer server) {
        double mspt = averageMspt(server);
        if (mspt <= SOFT_THROTTLE_MSPT) {
            return 1.0D;
        }
        if (mspt >= HARD_THROTTLE_MSPT) {
            return MIN_WORK_SCALE;
        }
        double t = (mspt - SOFT_THROTTLE_MSPT) / (HARD_THROTTLE_MSPT - SOFT_THROTTLE_MSPT);
        return Mth.lerp(t, 1.0D, MIN_WORK_SCALE);
    }

    public static int scaleInt(int base, int min, MinecraftServer server) {
        return Math.max(min, Mth.floor(base * currentWorkScale(server)));
    }

    public static double scaleMilliseconds(double base, double min, MinecraftServer server) {
        return Math.max(min, base * currentWorkScale(server));
    }

    public static void logIfEnabled(int entityId, int tick, String phase, int mutationBlocksBudget, int mutationSectionsBudget, int workUnitsBudget, int columnsBudget) {
    }
}
