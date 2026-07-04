package com.skyeshade.skyent.compat.jade;

import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.energy.EnergyUnits;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;
import snownee.jade.api.ui.ProgressStyle;
import snownee.jade.api.ui.ScreenDirection;

public enum RJComponentProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "rj_energy");
    private static final ResourceLocation RJ_BAR_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyesNuclearTech.MOD_ID, "textures/compat/rj_bar.png");
    private static final String DATA_STORED_RJ = "SkyentStoredRJ";
    private static final String DATA_CAPACITY_RJ = "SkyentCapacityRJ";
    private static final String DATA_GENERATION_RJ_PER_TICK = "SkyentGenerationRJPerTick";
    private static final String DATA_USAGE_RJ_PER_TICK = "SkyentUsageRJPerTick";
    private static final String DATA_MAX_OUTPUT_RJ_PER_TICK = "SkyentMaxOutputRJPerTick";
    private static final String DATA_VOLTAGE_TIER = "SkyentVoltageTier";
    private static final String DATA_BLOCK_ENTITY_CLASS = "SkyentBlockEntityClass";
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);
    private static final int ENERGY_BAR_WIDTH = 120;
    private static final int ENERGY_BAR_HEIGHT = 14;
    private static final int RJ_BAR_TEXTURE_WIDTH = 120;
    private static final int RJ_BAR_TEXTURE_HEIGHT = 12;
    private static final int EXPECTED_INNER_FILL_WIDTH = 118;
    private static final int EXPECTED_INNER_FILL_HEIGHT = 12;
    private static final int TEXT_SHADOW_COLOR = 0xFF202020;
    // Dev note: rj_bar is 113x16; the restored Jade frame is 120x14 with an expected ~118x12 inner fill, so the fill is tiled horizontally and cropped vertically.
    private static final long KILO_RJ = 1_000L;
    private static final long MEGA_RJ = 1_000_000L;
    private static final long GIGA_RJ = 1_000_000_000L;

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public int getDefaultPriority() {
        return 1000;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (!(blockEntity instanceof RJEnergyInfo energyInfo)) {
            return;
        }

        int storedRJ = Math.max(0, energyInfo.getEnergyStoredRJ());
        int capacityRJ = energyInfo.getEnergyCapacityRJ();
        String blockEntityClassName = blockEntity.getClass().getName();

        data.putInt(DATA_STORED_RJ, storedRJ);
        data.putInt(DATA_CAPACITY_RJ, capacityRJ);
        data.putInt(DATA_GENERATION_RJ_PER_TICK, energyInfo.getCurrentGenerationRJPerTick());
        data.putInt(DATA_USAGE_RJ_PER_TICK, energyInfo.getCurrentUsageRJPerTick());
        data.putInt(DATA_MAX_OUTPUT_RJ_PER_TICK, energyInfo.getMaxOutputRJPerTick());
        data.putString(DATA_VOLTAGE_TIER, energyInfo.getVoltageTierName());
        data.putString(DATA_BLOCK_ENTITY_CLASS, blockEntityClassName);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(DATA_CAPACITY_RJ)) {
            return;
        }

        int storedRJ = Math.max(0, data.getInt(DATA_STORED_RJ));
        int capacityRJ = data.getInt(DATA_CAPACITY_RJ);
        String blockEntityClassName = data.getString(DATA_BLOCK_ENTITY_CLASS);
        if (blockEntityClassName.isBlank()) {
            return;
        }

        int capacity = capacityRJ;
        if (capacity <= 0) {
            return;
        }

        tooltip.add(energyBar(storedRJ, capacity));

        int generation = data.getInt(DATA_GENERATION_RJ_PER_TICK);
        if (generation > 0) {
            tooltip.add(Component.literal("Generating: " + format(generation) + " " + EnergyUnits.UNIT_PER_TICK));
        }

        int maxOutput = data.getInt(DATA_MAX_OUTPUT_RJ_PER_TICK);
        if (maxOutput > 0) {
            tooltip.add(Component.literal("Max Export: " + format(maxOutput) + " " + EnergyUnits.UNIT_PER_TICK));
        }

        int usage = data.getInt(DATA_USAGE_RJ_PER_TICK);
        if (usage > 0) {
            tooltip.add(Component.literal("Usage: " + format(usage) + " " + EnergyUnits.UNIT_PER_TICK));
        }

        String voltageTier = data.getString(DATA_VOLTAGE_TIER);
        if (!voltageTier.isBlank()) {
            tooltip.add(Component.literal("Voltage: " + voltageTier));
        }
    }

    private static String format(int value) {
        return NUMBER_FORMAT.format(value);
    }

    private static IElement energyBar(int stored, int capacity) {
        float progress = Math.min(1.0F, stored / (float) capacity);
        ProgressStyle style = new RJTextureProgressStyle()
                .textColor(0xFFFFFFFF)
                .direction(ScreenDirection.RIGHT);
        return IElementHelper.get()
                .progress(progress, energyLabel(stored, capacity), style, BoxStyle.getNestedBox(), true)
                .size(new Vec2(ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT));
    }

    private static Component energyLabel(long stored, long capacity) {
        MutableComponent current = Component.literal(formatRJ(stored)).withStyle(ChatFormatting.WHITE);
        return current.append(Component.literal(" / " + formatRJ(capacity)).withStyle(ChatFormatting.GRAY));
    }

    private static String formatRJ(long value) {
        if (value < KILO_RJ) {
            return value + " " + EnergyUnits.UNIT;
        }

        if (value < MEGA_RJ) {
            return compact(value / (double) KILO_RJ) + " kRJ";
        }

        if (value < GIGA_RJ) {
            return compact(value / (double) MEGA_RJ) + " MRJ";
        }

        return compact(value / (double) GIGA_RJ) + " GRJ";
    }

    private static String compact(double value) {
        String formatted = String.format(Locale.US, "%.2f", value);
        while (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }

        return formatted;
    }

    private static final class RJTextureProgressStyle extends ProgressStyle {
        private int textColor = 0xFFFFFFFF;

        @Override
        public ProgressStyle color(int firstColor, int secondColor) {
            return this;
        }

        @Override
        public ProgressStyle textColor(int textColor) {
            this.textColor = textColor;
            return this;
        }

        @Override
        public void render(GuiGraphics guiGraphics, float x, float y, float width, float height, float progress, Component text) {
            int filledWidth = Math.min(Math.round(width), Math.round(width * Math.max(0.0F, Math.min(1.0F, progress))));
            if (filledWidth > 0) {
                renderTiledFill(guiGraphics, Math.round(x), Math.round(y), filledWidth, Math.round(height));
            }

            if (text != null) {
                Font font = Minecraft.getInstance().font;
                int textX = Math.round(x) + 1;
                int textY = Math.round(y + height - font.lineHeight);
                guiGraphics.drawString(font, Component.literal(text.getString()), textX + 1, textY + 1, TEXT_SHADOW_COLOR, false);
                guiGraphics.drawString(font, text, textX, textY, textColor, false);
            }
        }

        private static void renderTiledFill(GuiGraphics guiGraphics, int x, int y, int width, int height) {
            int remainingWidth = width;
            int drawX = x;
            while (remainingWidth > 0) {
                int tileWidth = Math.min(RJ_BAR_TEXTURE_WIDTH, remainingWidth);
                guiGraphics.blit(
                        RJ_BAR_TEXTURE,
                        drawX,
                        y,
                        0,
                        0,
                        tileWidth,
                        height,
                        RJ_BAR_TEXTURE_WIDTH,
                        RJ_BAR_TEXTURE_HEIGHT
                );
                drawX += tileWidth;
                remainingWidth -= tileWidth;
            }
        }
    }
}
