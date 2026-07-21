package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public final class ModTreeGrowers {
    public static final ResourceKey<ConfiguredFeature<?, ?>> RUBBER_TREE_SAPLING = ResourceKey.create(
            Registries.CONFIGURED_FEATURE,
            ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "rubber_tree_sapling")
    );

    public static final TreeGrower RUBBER = new TreeGrower(
            "rubber",
            Optional.empty(),
            Optional.of(RUBBER_TREE_SAPLING),
            Optional.empty()
    );

    private ModTreeGrowers() {
    }
}
