package com.skyeshade.skyent.event;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.client.debug.RadiationDebugOverlayClient;
import com.skyeshade.skyent.client.debug.RadiationRayDebugClient;
import com.skyeshade.skyent.client.item.GeigerCounterClientState;
import com.skyeshade.skyent.client.item.GeigerCounterSoundManager;
import com.skyeshade.skyent.client.item.PlacedGeigerCounterSoundManager;
import com.skyeshade.skyent.client.item.SteelFluidBarrelFluidDecorator;
import com.skyeshade.skyent.client.renderer.blockentity.CoalForgeRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.ForgingAnvilRenderer;
import com.skyeshade.skyent.client.renderer.blockentity.GeigerCounterPlacedRenderer;
import com.skyeshade.skyent.client.renderer.LVConnectorRenderer;
import com.skyeshade.skyent.client.renderer.LVElectricPumpRenderer;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.client.screen.BrickBlastFurnaceScreen;
import com.skyeshade.skyent.client.screen.CombustionGeneratorScreen;
import com.skyeshade.skyent.client.screen.ElectricFurnaceScreen;
import com.skyeshade.skyent.client.screen.LVElectricPumpScreen;
import com.skyeshade.skyent.client.screen.LVSteamTurbineScreen;
import com.skyeshade.skyent.event.systems.BootstrapSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModItems;
import com.skyeshade.skyent.registry.ModMenus;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
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
        modEventBus.addListener(ClientEvents::onRegisterItemDecorations);
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
        event.register(ModMenus.BRICK_BLAST_FURNACE.get(), BrickBlastFurnaceScreen::new);
        event.register(ModMenus.LV_ELECTRIC_PUMP.get(), LVElectricPumpScreen::new);
        event.register(ModMenus.LV_STEAM_TURBINE.get(), LVSteamTurbineScreen::new);
    }

    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.LV_CONNECTOR.get(), LVConnectorRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LV_ELECTRIC_PUMP.get(), LVElectricPumpRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GEIGER_COUNTER_PLACED.get(), GeigerCounterPlacedRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COAL_FORGE.get(), CoalForgeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.FORGING_ANVIL.get(), ForgingAnvilRenderer::new);
    }

    public static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.STEEL_FLUID_BARREL.get(), new SteelFluidBarrelFluidDecorator());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        GeigerCounterClientState.clientTick();
        GeigerCounterSoundManager.clientTick();
        PlacedGeigerCounterSoundManager.clientTick();
        RadiationRayDebugClient.onClientTick(event);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        RadiationRayDebugClient.onRenderLevel(event);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        RadiationDebugOverlayClient.onRenderGui(event);
    }

    public static void onItemTooltip(ItemTooltipEvent event) {
        HotItemUtil.appendTooltip(event.getItemStack(), event.getToolTip());
    }

    private static void registerHotIngotItemProperties() {
        ResourceLocation hotProperty = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "hot");
        registerHotIngotProperty(Items.IRON_INGOT, hotProperty);
        registerHotIngotProperty(Items.COPPER_INGOT, hotProperty);
        registerHotIngotProperty(Items.GOLD_INGOT, hotProperty);
        registerHotIngotProperty(ModItems.STEEL_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.ALUMINUM_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.TITANIUM_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.LEAD_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.TUNGSTEN_INGOT.get(), hotProperty);
        registerHotIngotProperty(ModItems.URANIUM_INGOT.get(), hotProperty);
    }

    private static void registerHotIngotProperty(Item item, ResourceLocation property) {
        ItemProperties.register(item, property, (stack, level, entity, seed) -> HotItemUtil.isForgeReady(stack) ? 1.0F : 0.0F);
    }
}
