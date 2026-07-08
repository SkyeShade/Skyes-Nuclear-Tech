package com.skyeshade.skyent.compat.jade;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.HeatingChamberPartBlock;
import com.skyeshade.skyent.content.block.LVCrusherBlock;
import com.skyeshade.skyent.content.block.LVElectricPumpBlock;
import com.skyeshade.skyent.content.block.LVSteamTurbineBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(SkyesNuclearTech.MOD_ID)
public final class SkyentJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, ElectricFurnaceBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, LVCrusherBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, LVElectricPumpBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, LVSteamTurbineBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, HeatingChamberBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, HeatingChamberPartBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, ElectricFurnaceBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVCrusherBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVElectricPumpBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVSteamTurbineBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, HeatingChamberBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, HeatingChamberPartBlock.class);
    }
}
