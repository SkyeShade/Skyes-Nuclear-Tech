package com.skyeshade.skyent.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode {
    @WrapOperation(
            method = {
                    "startDestroyBlock",
                    "continueDestroyBlock",
                    "stopDestroyBlock"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V"
            ),
            require = 0
    )
    private void skyent$remapLocalSteamForgeHammerPartDestroyProgress(
            ClientLevel level,
            int breakerId,
            BlockPos pos,
            int progress,
            Operation<Void> original
    ) {
        BlockPos visualPos = SteamForgeHammerBlock.resolveDestroyProgressPos(level, pos);
        visualPos = HeatingChamberBlock.resolveDestroyProgressPos(level, visualPos);
        original.call(level, breakerId, visualPos, progress);

        if (progress < 0 && !visualPos.equals(pos)) {
            original.call(level, breakerId, pos, progress);
        }
    }
}
