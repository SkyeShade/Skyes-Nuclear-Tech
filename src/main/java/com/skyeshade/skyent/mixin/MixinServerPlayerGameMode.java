package com.skyeshade.skyent.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerPlayerGameMode.class)
public abstract class MixinServerPlayerGameMode {
    @Shadow
    protected ServerLevel level;

    @WrapOperation(
            method = "handleBlockBreakAction",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;destroyBlockProgress(ILnet/minecraft/core/BlockPos;I)V"
            )
    )
    private void skyent$remapSteamForgeHammerPartDestroyProgress(
            ServerLevel level,
            int breakerId,
            BlockPos pos,
            int progress,
            Operation<Void> original
    ) {
        BlockPos visualPos = SteamForgeHammerBlock.resolveDestroyProgressPos(this.level, pos);
        visualPos = HeatingChamberBlock.resolveDestroyProgressPos(this.level, visualPos);
        visualPos = IndustrialPressBlock.resolveDestroyProgressPos(this.level, visualPos);
        visualPos = RollingMillBlock.resolveDestroyProgressPos(this.level, visualPos);
        visualPos = LVMVTransformerBlock.resolveDestroyProgressPos(this.level, visualPos);
        original.call(level, breakerId, visualPos, progress);

        if (progress < 0 && !visualPos.equals(pos)) {
            original.call(level, breakerId, pos, progress);
        }
    }
}
