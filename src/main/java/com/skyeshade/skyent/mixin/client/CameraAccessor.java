package com.skyeshade.skyent.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Invoker("setRotation")
    void skyent$setRotation(float yaw, float pitch);

    @Invoker("setPosition")
    void skyent$setPosition(Vec3 position);
}
