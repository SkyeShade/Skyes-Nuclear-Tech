package com.skyeshade.skyent.client.render;

import net.minecraft.core.BlockPos;

/**
 * Read the engine-owned destroy stages; no parallel timer or stale overlay cache.
 */
public interface MeshDestroyProgress {
    int skyent$meshDestroyStage(BlockPos controller);
}
