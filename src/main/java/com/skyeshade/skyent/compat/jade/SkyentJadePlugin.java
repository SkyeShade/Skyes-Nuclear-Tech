package com.skyeshade.skyent.compat.jade;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.HeatingChamberPartBlock;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.block.IndustrialPressPartBlock;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.block.LVMVTransformerPartBlock;
import com.skyeshade.skyent.content.block.LVCrusherBlock;
import com.skyeshade.skyent.content.block.LVElectricPumpBlock;
import com.skyeshade.skyent.content.block.LVSteamTurbineBlock;
import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.block.RollingMillPartBlock;
import com.skyeshade.skyent.content.block.WireMillBlock;
import com.skyeshade.skyent.content.block.WireMillPartBlock;
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
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, IndustrialPressBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, IndustrialPressPartBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, RollingMillBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, RollingMillPartBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, WireMillBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, WireMillPartBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, LVMVTransformerBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, LVMVTransformerPartBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, ElectricFurnaceBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVCrusherBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVElectricPumpBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVSteamTurbineBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, HeatingChamberBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, HeatingChamberPartBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, IndustrialPressBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, IndustrialPressPartBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, RollingMillBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, RollingMillPartBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, WireMillBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, WireMillPartBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVMVTransformerBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVMVTransformerPartBlock.class);
    }
}
