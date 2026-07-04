package com.skyeshade.skyent.content.radiation;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public interface RadioactiveCarrierEntity {
    ItemStack skyent$getRadiationStack();

    Vec3 skyent$getRadiationPosition();
}
