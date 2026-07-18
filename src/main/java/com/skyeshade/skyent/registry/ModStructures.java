package com.skyeshade.skyent.registry;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.worldgen.structure.VitrifiedCraterPiece;
import com.skyeshade.skyent.worldgen.structure.VitrifiedCraterStructure;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    private static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES = DeferredRegister.create(
            BuiltInRegistries.STRUCTURE_TYPE,
            SkyesNuclearTech.MOD_ID
    );
    private static final DeferredRegister<StructurePieceType> STRUCTURE_PIECES = DeferredRegister.create(
            BuiltInRegistries.STRUCTURE_PIECE,
            SkyesNuclearTech.MOD_ID
    );

    public static final DeferredHolder<StructureType<?>, StructureType<VitrifiedCraterStructure>> VITRIFIED_CRATER =
            STRUCTURE_TYPES.register("vitrified_crater", () -> () -> VitrifiedCraterStructure.CODEC);

    public static final DeferredHolder<StructurePieceType, StructurePieceType> VITRIFIED_CRATER_PIECE =
            STRUCTURE_PIECES.register("vitrified_crater_piece", () -> VitrifiedCraterPiece::new);

    private ModStructures() {
    }

    public static void register(IEventBus modEventBus) {
        STRUCTURE_TYPES.register(modEventBus);
        STRUCTURE_PIECES.register(modEventBus);
    }
}
