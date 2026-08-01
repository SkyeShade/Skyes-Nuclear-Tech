package com.skyeshade.skyent.event;

import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.content.block.CentrifugeBlock;
import com.skyeshade.skyent.content.block.ConveyorChuteBlock;
import com.skyeshade.skyent.content.block.ConveyorElevatorBlock;
import com.skyeshade.skyent.content.block.HeatingChamberPartBlock;
import com.skyeshade.skyent.content.block.MediumTankBlock;
import com.skyeshade.skyent.content.block.MVAssemblerBlock;
import com.skyeshade.skyent.content.block.MVChemicalReactorBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import com.skyeshade.skyent.content.block.SteamForgeHammerPartBlock;
import com.skyeshade.skyent.content.block.WireMillPartBlock;
import com.skyeshade.skyent.content.fluid.SteelFluidBarrelFluidHandler;
import com.skyeshade.skyent.network.CameraShakeS2CPacket;
import com.skyeshade.skyent.network.ClientPayloadHandlers;
import com.skyeshade.skyent.network.GeigerExposurePayload;
import com.skyeshade.skyent.network.NukeDetonationEffectsPayload;
import com.skyeshade.skyent.network.OpenMVAssemblerPayload;
import com.skyeshade.skyent.network.OpenMVAssemblerRecipeSelectPayload;
import com.skyeshade.skyent.network.PlayLocalSoundPayload;
import com.skyeshade.skyent.network.RadiationDebugOverlayPayload;
import com.skyeshade.skyent.network.RadiationRayBatchPayload;
import com.skyeshade.skyent.network.RadiationRaysDebugPayload;
import com.skyeshade.skyent.network.SelectMVAssemblerRecipePayload;
import com.skyeshade.skyent.network.ServerPayloadHandlers;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMultiblockShapes;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class CommonEvents {
    private CommonEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(CommonEvents::onCommonSetup);
        modEventBus.addListener(CommonEvents::onRegisterCapabilities);
        modEventBus.addListener(CommonEvents::onRegisterPayloads);
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        ModMultiblockShapes.registerDefaults();
        BootstrapSystem.onCommonSetup(event);
        event.enqueueWork(CommonEvents::registerDeadLeavesFlammability);
    }

    private static void registerDeadLeavesFlammability() {
        FireBlock fire = (FireBlock) Blocks.FIRE;
        fire.setFlammable(ModBlocks.DEAD_OAK_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_BIRCH_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_SPRUCE_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_JUNGLE_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_ACACIA_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_DARK_OAK_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_MANGROVE_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_CHERRY_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_AZALEA_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_FLOWERING_AZALEA_LEAVES.get(), 30, 60);
        fire.setFlammable(ModBlocks.DEAD_RUBBER_LEAVES.get(), 30, 60);
    }

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.COMBUSTION_GENERATOR.get(),
                (generator, side) -> generator.getAutomationItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.COMBUSTION_GENERATOR.get(),
                (generator, side) -> generator.getAutomationFluidHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.ELECTRIC_FURNACE.get(),
                (furnace, side) -> furnace.getAutomationItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LV_CRUSHER.get(),
                (crusher, side) -> crusher.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MV_ASSEMBLER.get(),
                (assembler, side) -> assembler.getAutomationItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MV_CHEMICAL_REACTOR.get(),
                (reactor, side) -> reactor.getAutomationItemHandler(reactor.getBlockPos(), side)
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.MV_CHEMICAL_REACTOR.get(),
                (reactor, side) -> reactor.getAutomationFluidHandler(reactor.getBlockPos(), side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.CENTRIFUGE.get(),
                (centrifuge, side) -> centrifuge.getAutomationItemHandler(centrifuge.getBlockPos(), side)
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.CENTRIFUGE.get(),
                (centrifuge, side) -> centrifuge.getAutomationFluidHandler(centrifuge.getBlockPos(), side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.BRICK_BLAST_FURNACE.get(),
                (furnace, side) -> furnace.getAutomationItemHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LV_ELECTRIC_PUMP.get(),
                (pump, side) -> pump.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.LV_ELECTRIC_PUMP.get(),
                (pump, side) -> pump.getAutomationFluidHandler()
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MEDIUM_TANK.get(),
                (tank, side) -> tank.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.MEDIUM_TANK.get(),
                (tank, side) -> tank.getAutomationFluidHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MV_INLINE_PUMP.get(),
                (pump, side) -> pump.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.MV_INLINE_PUMP.get(),
                (pump, side) -> pump.getAutomationFluidHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.LV_STEAM_TURBINE.get(),
                (turbine, side) -> turbine.getAutomationItemHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.LV_STEAM_TURBINE.get(),
                (turbine, side) -> turbine.getAutomationFluidHandler()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.BASIC_FLUID_DUCT.get(),
                (duct, side) -> duct.getFluidHandler(side)
        );

        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.BASIC_CONVEYOR_BELT.get(),
                (belt, side) -> belt.getItemHandler(side)
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> HeatingChamberPartBlock.getItemHandler(level, pos, state, side),
                ModBlocks.HEATING_CHAMBER_PART.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> WireMillPartBlock.getItemHandler(level, pos, state, side),
                ModBlocks.WIRE_MILL_PART.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> MVAssemblerBlock.getMasterBlockEntity(level, state, pos)
                        .map(assembler -> assembler.getAutomationItemHandler(pos, side))
                        .orElse(null),
                ModBlocks.MV_ASSEMBLER_PART.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> MVChemicalReactorBlock.getMasterBlockEntity(level, state, pos)
                        .map(reactor -> reactor.getAutomationItemHandler(pos, side))
                        .orElse(null),
                ModBlocks.MV_CHEMICAL_REACTOR_PART.get()
        );

        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> MVChemicalReactorBlock.getMasterBlockEntity(level, state, pos)
                        .map(reactor -> reactor.getAutomationFluidHandler(pos, side))
                        .orElse(null),
                ModBlocks.MV_CHEMICAL_REACTOR_PART.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> CentrifugeBlock.getMasterBlockEntity(level, state, pos)
                        .map(centrifuge -> centrifuge.getAutomationItemHandler(pos, side))
                        .orElse(null),
                ModBlocks.CENTRIFUGE_PART.get()
        );

        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> CentrifugeBlock.getMasterBlockEntity(level, state, pos)
                        .map(centrifuge -> centrifuge.getAutomationFluidHandler(pos, side))
                        .orElse(null),
                ModBlocks.CENTRIFUGE_PART.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> MediumTankBlock.getMasterBlockEntity(level, state, pos)
                        .map(tank -> tank.getAutomationItemHandler())
                        .orElse(null),
                ModBlocks.MEDIUM_TANK_PART.get()
        );

        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> MediumTankBlock.getMasterBlockEntity(level, state, pos)
                        .filter(tank -> MediumTankBlock.isValidPipeConnection(state, side))
                        .map(tank -> tank.getAutomationFluidHandler())
                        .orElse(null),
                ModBlocks.MEDIUM_TANK_PART.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> ConveyorElevatorBlock.getItemHandler(level, pos, state, side),
                ModBlocks.CONVEYOR_ELEVATOR.get()
        );

        event.registerBlock(
                Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> ConveyorChuteBlock.getItemHandler(level, pos, state, side),
                ModBlocks.CONVEYOR_CHUTE.get()
        );

        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.STEAM_FORGE_HAMMER.get(),
                (hammer, side) -> hammer.getAutomationFluidHandler()
        );

        event.registerBlock(
                Capabilities.FluidHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> {
                    Direction facing = state.getValue(SteamForgeHammerPartBlock.FACING);
                    Direction back = facing.getOpposite();
                    if (side != null && side != back) {
                        return null;
                    }

                    return SteamForgeHammerBlock.getMasterBlockEntity(level, state, pos)
                            .map(hammer -> hammer.getAutomationFluidHandler())
                            .orElse(null);
                },
                ModBlocks.STEAM_FORGE_HAMMER_PART.get()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LV_RJ_CONVERTER.get(),
                (converter, side) -> converter.getFEOutput()
        );

        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.LV_FE_CONVERTER.get(),
                (converter, side) -> converter.getFEInput()
        );

        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new SteelFluidBarrelFluidHandler(stack),
                ModItems.STEEL_FLUID_BARREL.get()
        );
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(
                        RadiationRaysDebugPayload.TYPE,
                        RadiationRaysDebugPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleRadiationRaysDebug
                )
                .playToClient(
                        RadiationRayBatchPayload.TYPE,
                        RadiationRayBatchPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleRadiationRayBatch
                )
                .playToClient(
                        GeigerExposurePayload.TYPE,
                        GeigerExposurePayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleGeigerExposure
                )
                .playToClient(
                        RadiationDebugOverlayPayload.TYPE,
                        RadiationDebugOverlayPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleRadiationDebugOverlay
                )
                .playToClient(
                        PlayLocalSoundPayload.TYPE,
                        PlayLocalSoundPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handlePlayLocalSound
                )
                .playToClient(
                        NukeDetonationEffectsPayload.TYPE,
                        NukeDetonationEffectsPayload.STREAM_CODEC,
                        ClientPayloadHandlers::handleNukeDetonationEffects
                )
                .playToClient(
                        CameraShakeS2CPacket.TYPE,
                        CameraShakeS2CPacket.STREAM_CODEC,
                        ClientPayloadHandlers::handleCameraShake
                )
                .playToServer(
                        OpenMVAssemblerPayload.TYPE,
                        OpenMVAssemblerPayload.STREAM_CODEC,
                        ServerPayloadHandlers::handleOpenMVAssembler
                )
                .playToServer(
                        OpenMVAssemblerRecipeSelectPayload.TYPE,
                        OpenMVAssemblerRecipeSelectPayload.STREAM_CODEC,
                        ServerPayloadHandlers::handleOpenMVAssemblerRecipeSelect
                )
                .playToServer(
                        SelectMVAssemblerRecipePayload.TYPE,
                        SelectMVAssemblerRecipePayload.STREAM_CODEC,
                        ServerPayloadHandlers::handleSelectMVAssemblerRecipe
                );
    }
}
