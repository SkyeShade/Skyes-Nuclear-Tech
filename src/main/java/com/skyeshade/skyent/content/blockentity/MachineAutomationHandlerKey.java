package com.skyeshade.skyent.content.blockentity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

record MachineAutomationHandlerKey(BlockPos queriedPos, @Nullable Direction side) {
}
