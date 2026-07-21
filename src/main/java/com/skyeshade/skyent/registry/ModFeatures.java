package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.worldgen.RubberTreeDecorator;
import com.skyeshade.skyent.content.worldgen.RubberTreeFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(
            Registries.FEATURE,
            SkyesNuclearTech.MOD_ID
    );
    private static final DeferredRegister<TreeDecoratorType<?>> TREE_DECORATORS = DeferredRegister.create(
            Registries.TREE_DECORATOR_TYPE,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<Feature<?>, RubberTreeFeature> RUBBER_TREE = FEATURES.register(
            "rubber_tree",
            () -> new RubberTreeFeature(NoneFeatureConfiguration.CODEC)
    );
    public static final DeferredHolder<TreeDecoratorType<?>, TreeDecoratorType<RubberTreeDecorator>> RUBBER_TREE_DECORATOR = TREE_DECORATORS.register(
            "rubber_tree_decorator",
            () -> new TreeDecoratorType<>(RubberTreeDecorator.CODEC)
    );

    private ModFeatures() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
        TREE_DECORATORS.register(modEventBus);
    }
}
