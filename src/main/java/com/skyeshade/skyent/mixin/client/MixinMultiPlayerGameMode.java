package com.skyeshade.skyent.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.skyeshade.skyent.content.block.BlastDoorBlock;
import com.skyeshade.skyent.content.block.CentrifugeBlock;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.block.MediumTankBlock;
import com.skyeshade.skyent.content.block.MVAssemblerBlock;
import com.skyeshade.skyent.content.block.MVChemicalReactorBlock;
import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import com.skyeshade.skyent.content.block.WireMillBlock;
import com.skyeshade.skyent.content.block.ZoneGateBlock;
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
        visualPos = IndustrialPressBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = RollingMillBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = WireMillBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = LVMVTransformerBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = MVAssemblerBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = MVChemicalReactorBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = CentrifugeBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = BlastDoorBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = ZoneGateBlock.resolveDestroyProgressPos(level, visualPos);
        visualPos = MediumTankBlock.resolveDestroyProgressPos(level, visualPos);
        original.call(level, breakerId, visualPos, progress);

        if (progress < 0 && !visualPos.equals(pos)) {
            original.call(level, breakerId, pos, progress);
        }
    }
}
