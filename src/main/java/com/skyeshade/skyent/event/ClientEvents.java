package com.skyeshade.skyent.event;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.debug.RadiationDebugOverlayClient;
import com.skyeshade.skyent.client.debug.RadiationRayDebugClient;
import com.skyeshade.skyent.client.effect.CameraShakeSystem;
import com.skyeshade.skyent.client.effect.NukeDetonationEffectsClient;
import com.skyeshade.skyent.client.item.GeigerCounterClientState;
import com.skyeshade.skyent.client.item.GeigerCounterSoundManager;
import com.skyeshade.skyent.client.item.PlacedGeigerCounterSoundManager;
import com.skyeshade.skyent.client.item.SteelFluidBarrelFluidDecorator;
import com.skyeshade.skyent.client.item.SteelTongsHeldItemDecorator;
import com.skyeshade.skyent.client.model.ScaledBlockModel;
import com.skyeshade.skyent.client.model.SlicedScaledBlockModel;
import com.skyeshade.skyent.client.particle.NukeCloudParticle;
import com.skyeshade.skyent.client.particle.StreakParticle;
import com.skyeshade.skyent.client.renderer.blockentity.CoalForgeRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.BlastDoorRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.ForgingAnvilRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.GeigerCounterPlacedRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.HeatingChamberRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.IndustrialPressRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.LVMVTransformerRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.MediumTankRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.RollingMillRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.SteamForgeHammerRenderer;
import com.skyeshade.skyent.client.renderer.entity.ConveyorMovingItemRenderer;
import com.skyeshade.skyent.client.renderer.entity.NuclearExplosionRenderer;
import com.skyeshade.skyent.client.renderer.LVConnectorRenderer;
import com.skyeshade.skyent.client.renderer.LVElectricPumpRenderer;
import com.skyeshade.skyent.client.render.RadiationFeedbackClient;
import com.skyeshade.skyent.client.sound.ConveyorSoundHandler;
import com.skyeshade.skyent.client.sound.MachineSoundManager;
import com.skyeshade.skyent.content.shape.MultiblockShapeRegistry;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.content.item.PyrophoricTooltip;
import com.skyeshade.skyent.content.item.RadiationShieldingTooltip;
import com.skyeshade.skyent.content.item.RadioactiveTooltip;
import com.skyeshade.skyent.content.item.ToxicityTooltip;
import com.skyeshade.skyent.client.screen.BrickBlastFurnaceScreen;
import com.skyeshade.skyent.client.screen.CombustionGeneratorScreen;
import com.skyeshade.skyent.client.screen.ConveyorExporterScreen;
import com.skyeshade.skyent.client.screen.ElectricFurnaceScreen;
import com.skyeshade.skyent.client.screen.LVCrusherScreen;
import com.skyeshade.skyent.client.screen.LVElectricPumpScreen;
import com.skyeshade.skyent.client.screen.LVSteamTurbineScreen;
import com.skyeshade.skyent.client.screen.MediumTankScreen;
import com.skyeshade.skyent.client.screen.MVAssemblerRecipeSelectScreen;
import com.skyeshade.skyent.client.screen.MVAssemblerScreen;
import com.skyeshade.skyent.client.screen.MVChemicalReactorScreen;
import com.skyeshade.skyent.client.screen.MVInlinePumpScreen;
import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModEntities;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMenus;
import com.skyeshade.skyent.registry.ModParticles;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

public final class ClientEvents {
    private ClientEvents() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientEvents::onClientSetup);
        modEventBus.addListener(ClientEvents::onRegisterMenuScreens);
        modEventBus.addListener(ClientEvents::onRegisterRenderers);
        modEventBus.addListener(ClientEvents::onRegisterAdditionalModels);
        modEventBus.addListener(ClientEvents::onRegisterGeometryLoaders);
        modEventBus.addListener(ClientEvents::onRegisterItemDecorations);
        modEventBus.addListener(ClientEvents::onRegisterParticleProviders);
        modEventBus.addListener(ClientEvents::onRegisterClientReloadListeners);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderGui);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onItemTooltip);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        BootstrapSystem.onClientSetup(event);
        event.enqueueWork(ClientEvents::registerHotIngotItemProperties);
    }

    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.COMBUSTION_GENERATOR.get(), CombustionGeneratorScreen::new);
        event.register(ModMenus.ELECTRIC_FURNACE.get(), ElectricFurnaceScreen::new);
        event.register(ModMenus.LV_CRUSHER.get(), LVCrusherScreen::new);
        event.register(ModMenus.BRICK_BLAST_FURNACE.get(), BrickBlastFurnaceScreen::new);
        event.register(ModMenus.LV_ELECTRIC_PUMP.get(), LVElectricPumpScreen::new);
        event.register(ModMenus.MEDIUM_TANK.get(), MediumTankScreen::new);
        event.register(ModMenus.LV_STEAM_TURBINE.get(), LVSteamTurbineScreen::new);
        event.register(ModMenus.MV_INLINE_PUMP.get(), MVInlinePumpScreen::new);
        event.register(ModMenus.CONVEYOR_EXPORTER.get(), ConveyorExporterScreen::new);
        event.register(ModMenus.MV_ASSEMBLER.get(), MVAssemblerScreen::new);
        event.register(ModMenus.MV_CHEMICAL_REACTOR.get(), MVChemicalReactorScreen::new);
        event.register(ModMenus.ASSEMBLER_RECIPE_SELECT.get(), MVAssemblerRecipeSelectScreen::new);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CONVEYOR_MOVING_ITEM.get(), ConveyorMovingItemRenderer::new);
        event.registerEntityRenderer(ModEntities.NUCLEAR_EXPLOSION.get(), NuclearExplosionRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LV_CONNECTOR.get(), LVConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LV_ELECTRIC_PUMP.get(), LVElectricPumpRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GEIGER_COUNTER_PLACED.get(), GeigerCounterPlacedRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COAL_FORGE.get(), CoalForgeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FORGING_ANVIL.get(), ForgingAnvilRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STEAM_FORGE_HAMMER.get(), SteamForgeHammerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.HEATING_CHAMBER.get(), HeatingChamberRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.INDUSTRIAL_PRESS.get(), IndustrialPressRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ROLLING_MILL.get(), RollingMillRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LV_MV_TRANSFORMER.get(), LVMVTransformerRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.MEDIUM_TANK.get(), MediumTankRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.BLAST_DOOR.get(), BlastDoorRenderer::new);
    }

    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(SteamForgeHammerRenderer.PISTON_MODEL);
        event.register(HeatingChamberRenderer.CHAMBER_MODEL);
        event.register(IndustrialPressRenderer.PRESS_HEAD_MODEL);
        event.register(RollingMillRenderer.ROLLERS_MODEL);
        event.register(BlastDoorRenderer.DOOR_PANEL_MODEL);
    }

    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "scaled_block_model"), ScaledBlockModel.Loader.INSTANCE);
        event.register(ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "sliced_scaled_block_model"), SlicedScaledBlockModel.Loader.INSTANCE);
    }

    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((barrier, resourceManager, preparationsProfiler, reloadProfiler, backgroundExecutor, gameExecutor) ->
                java.util.concurrent.CompletableFuture.supplyAsync(() -> resourceManager, backgroundExecutor)
                        .thenCompose(barrier::wait)
                        .thenAcceptAsync(MultiblockShapeRegistry::reload, gameExecutor)
        );
    }

    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.STEEL_FLUID_BARREL.get(), new SteelFluidBarrelFluidDecorator());
        event.register(ModItems.STEEL_TONGS.get(), new SteelTongsHeldItemDecorator());
    }

    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SPARK_STREAK.get(), StreakParticle.Factory::new);
        event.registerSpriteSet(ModParticles.NUKE_CLOUD.get(), NukeCloudParticle.Provider::new);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        GeigerCounterClientState.clientTick();
        tickRadiationFeedback();
        GeigerCounterSoundManager.clientTick();
        PlacedGeigerCounterSoundManager.clientTick();
        ConveyorSoundHandler.clientTick();
        MachineSoundManager.tick();
        CameraShakeSystem.onClientTick(event);
        RadiationRayDebugClient.onClientTick(event);
        NukeDetonationEffectsClient.onClientTick(event);
    }

    private static void tickRadiationFeedback() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            RadiationFeedbackClient.clear();
            return;
        }

        RadiationFeedbackClient.setDoseRate((float) GeigerCounterClientState.getTargetExposureMillisievertsPerSecond());
        RadiationFeedbackClient.tick();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        RadiationRayDebugClient.onRenderLevel(event);
        NukeDetonationEffectsClient.onRenderLevel(event);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        RadiationDebugOverlayClient.onRenderGui(event);
        NukeDetonationEffectsClient.onRenderGui(event);
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        HotItemUtil.appendTooltip(stack, event.getToolTip());
        appendBottomMaterialInfoTooltips(stack, event);
    }

    private static void appendBottomMaterialInfoTooltips(ItemStack stack, ItemTooltipEvent event) {
        RadiationShieldingTooltip.append(stack, event.getToolTip());
        RadioactiveTooltip.append(stack, event.getToolTip());
        PyrophoricTooltip.append(stack, event.getToolTip());
        ToxicityTooltip.append(stack, event.getToolTip());
        appendBlastResistanceTooltip(stack, event);
    }

    private static void appendBlastResistanceTooltip(ItemStack stack, ItemTooltipEvent event) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return;
        }

        float resistance = blockItem.getBlock().getExplosionResistance();
        String value = Float.isInfinite(resistance) || resistance >= Float.MAX_VALUE
                ? "Immune"
                : String.format(Locale.ROOT, "%.1f", resistance);
        event.getToolTip().add(Component.translatable("tooltip.skyent.blast_resistance", value).withStyle(ChatFormatting.GOLD));
    }

    private static void registerHotIngotItemProperties() {
        ResourceLocation hotProperty = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "hot");
        registerHotIngotProperty(Items.IRON_INGOT, hotProperty);
        registerHotIngotProperty(Items.COPPER_INGOT, hotProperty);
        registerHotIngotProperty(Items.GOLD_INGOT, hotProperty);
        registerHotIngotProperty(ModItems.STEEL_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.ALUMINUM_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.TITANIUM_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.COBALT_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.NICKEL_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.COBALT_BRONZE_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.CUPRONICKEL_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.LEAD_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.TUNGSTEN_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.URANIUM_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.IRON_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.COPPER_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.STEEL_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.ALUMINUM_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.TITANIUM_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.TUNGSTEN_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.COBALT_ROD.get(), hotProperty);
        registerHotIngotProperty(ModItems.NICKEL_ROD.get(), hotProperty);
    }

    private static void registerHotIngotProperty(Item item, ResourceLocation property) {
        ItemProperties.register(item, property, (stack, level, entity, seed) -> HotItemUtil.isForgeReady(stack) ? 1.0F : 0.0F);
    }
}
