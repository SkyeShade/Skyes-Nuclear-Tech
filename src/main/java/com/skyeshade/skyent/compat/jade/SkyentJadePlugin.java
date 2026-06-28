package com.skyeshade.skyent.compat.jade;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.CombustionGeneratorBlock;
import com.skyeshade.skyent.content.block.ElectricFurnaceBlock;
import com.skyeshade.skyent.content.block.LVElectricPumpBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin(SkyesNuclearTech.MOD_ID)
public final class SkyentJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, CombustionGeneratorBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, ElectricFurnaceBlock.class);
        registration.registerBlockDataProvider(RJComponentProvider.INSTANCE, LVElectricPumpBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, CombustionGeneratorBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, ElectricFurnaceBlock.class);
        registration.registerBlockComponent(RJComponentProvider.INSTANCE, LVElectricPumpBlock.class);
    }
}
