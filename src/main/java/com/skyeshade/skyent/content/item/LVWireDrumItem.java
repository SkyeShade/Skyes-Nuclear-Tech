package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.event.systems.LVElectricalNetworkSystem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LVWireDrumItem extends Item {
    public static final int MAX_CONNECTION_DISTANCE = 32;

    private static final String SELECTED_CONNECTOR_TAG = "SelectedConnector";
    private static final double PATH_SAMPLE_EPSILON = 0.001D;

    private final LVWireType wireType;
    private final String tooltipPrefix;

    public LVWireDrumItem(Properties properties) {
        this(properties, LVWireType.COPPER, "tooltip.skyent.lv_copper_wire_drum");
    }

    public LVWireDrumItem(Properties properties, LVWireType wireType, String tooltipPrefix) {
        super(properties);
        this.wireType = wireType;
        this.tooltipPrefix = tooltipPrefix;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(tooltipPrefix + ".description").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipPrefix + ".voltage").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipPrefix + ".current").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipPrefix + ".max_transfer").withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable(tooltipPrefix + ".voltage_drop").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return useOnWireEndpoint(context.getLevel(), context.getClickedPos(), context.getPlayer(), context.getItemInHand());
    }

    public InteractionResult useOnWireEndpoint(Level level, BlockPos clickedBlockPos, Player player, ItemStack stack) {
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                clearSelection(stack);
                message(player, "Cleared connector selection.");
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        WireEndpoint clickedEndpoint = resolveEndpoint(level, clickedBlockPos);
        if (clickedEndpoint == null) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickedPos = clickedEndpoint.pos();
        if (!clickedEndpoint.canAccept(wireType)) {
            message(player, incompatibleConnectorMessage());
            return InteractionResult.CONSUME;
        }

        BlockPos selectedPos = getSelectedConnector(stack);
        if (selectedPos == null) {
            setSelectedConnector(stack, clickedPos);
            message(player, "Selected first connector.");
            return InteractionResult.CONSUME;
        }

        if (selectedPos.equals(clickedPos)) {
            clearSelection(stack);
            message(player, "Connector selection cleared.");
            return InteractionResult.CONSUME;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            clearSelection(stack);
            message(player, "Connector missing. Selection cleared.");
            return InteractionResult.CONSUME;
        }

        WireEndpoint selectedEndpoint = resolveEndpoint(serverLevel, selectedPos);
        clickedEndpoint = resolveEndpoint(serverLevel, clickedPos);
        if (selectedEndpoint == null || clickedEndpoint == null) {
            clearSelection(stack);
            message(player, "Connector missing. Selection cleared.");
            return InteractionResult.CONSUME;
        }

        if (!selectedEndpoint.canAccept(wireType)) {
            clearSelection(stack);
            message(player, incompatibleConnectorMessage());
            return InteractionResult.CONSUME;
        }

        if (selectedPos.distSqr(clickedPos) > MAX_CONNECTION_DISTANCE * MAX_CONNECTION_DISTANCE) {
            message(player, "Connectors are too far apart.");
            return InteractionResult.CONSUME;
        }

        if (!selectedEndpoint.canConnectTo(clickedEndpoint, wireType)) {
            clearSelection(stack);
            message(player, "Connector cannot accept this cable.");
            return InteractionResult.CONSUME;
        }

        if (isCablePathObstructed(serverLevel, selectedPos, clickedPos)) {
            message(player, "Cable path is obstructed.");
            return InteractionResult.CONSUME;
        }

        if (!selectedEndpoint.addConnection(clickedPos, wireType)) {
            clearSelection(stack);
            message(player, "Connector cannot accept this cable.");
            return InteractionResult.CONSUME;
        }
        if (!clickedEndpoint.addConnection(selectedPos, wireType)) {
            selectedEndpoint.removeConnection(clickedPos);
            clearSelection(stack);
            message(player, "Connector cannot accept this cable.");
            return InteractionResult.CONSUME;
        }

        clearSelection(stack);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        message(player, "Connected cable.");
        return InteractionResult.CONSUME;
    }

    private static WireEndpoint resolveEndpoint(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LVConnectorBlockEntity connector) {
            return new WireEndpoint(connector.getBlockPos(), connector.getConnectorTier(), connector);
        }

        BlockState state = level.getBlockState(pos);
        if (LVMVTransformerBlock.isMVTerminal(state)) {
            BlockPos masterPos = LVMVTransformerBlock.getMasterPos(state, pos);
            if (level.getBlockEntity(masterPos) instanceof com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity transformer) {
                return new WireEndpoint(pos.immutable(), ElectricalTier.MV, null, transformer);
            }
        }

        return null;
    }

    private static BlockPos getSelectedConnector(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        CompoundTag tag = customData.copyTag();
        return tag.contains(SELECTED_CONNECTOR_TAG) ? BlockPos.of(tag.getLong(SELECTED_CONNECTOR_TAG)) : null;
    }

    private static void setSelectedConnector(ItemStack stack, BlockPos pos) {
        CompoundTag tag = getOrCreateCustomTag(stack);
        tag.putLong(SELECTED_CONNECTOR_TAG, pos.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static void clearSelection(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return;
        }

        CompoundTag tag = customData.copyTag();
        tag.remove(SELECTED_CONNECTOR_TAG);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private static CompoundTag getOrCreateCustomTag(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData == null ? new CompoundTag() : customData.copyTag();
    }

    private static boolean isCablePathObstructed(ServerLevel level, BlockPos startPos, BlockPos endPos) {
        for (int sample = 1; sample < LVElectricalNetworkSystem.CABLE_SEGMENTS; sample++) {
            double t = sample / (double) LVElectricalNetworkSystem.CABLE_SEGMENTS;
            Vec3 point = LVElectricalNetworkSystem.sagPoint(level, startPos, endPos, t);
            BlockPos blockPos = BlockPos.containing(point);
            if (blockPos.equals(startPos) || blockPos.equals(endPos)) {
                continue;
            }

            BlockState state = level.getBlockState(blockPos);
            VoxelShape collisionShape = state.getCollisionShape(level, blockPos);
            if (!collisionShape.isEmpty() && collisionShape.bounds().inflate(PATH_SAMPLE_EPSILON).contains(point.subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ()))) {
                return true;
            }
        }

        return false;
    }

    private String incompatibleConnectorMessage() {
        return wireType == LVWireType.MV_COPPER
                ? "MV copper cable can only connect MV connectors or transformer MV terminals."
                : "LV cable cannot connect to MV connectors.";
    }

    private static void message(Player player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private record WireEndpoint(BlockPos pos, ElectricalTier tier, LVConnectorBlockEntity connector, com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity transformer) {
        private WireEndpoint(BlockPos pos, ElectricalTier tier, LVConnectorBlockEntity connector) {
            this(pos, tier, connector, null);
        }

        private boolean canAccept(LVWireType wireType) {
            return wireType.isTier(tier);
        }

        private boolean canConnectTo(WireEndpoint other, LVWireType wireType) {
            if (!canAccept(wireType) || !other.canAccept(wireType)) {
                return false;
            }
            return (connector == null || connector.canAddConnection(other.pos, wireType))
                    && (other.connector == null || other.connector.canAddConnection(pos, wireType))
                    && (transformer == null || transformer.canAddTerminalConnection(pos, other.pos, wireType))
                    && (other.transformer == null || other.transformer.canAddTerminalConnection(other.pos, pos, wireType));
        }

        private boolean addConnection(BlockPos otherPos, LVWireType wireType) {
            if (connector != null) {
                return connector.addConnection(otherPos, wireType);
            }
            if (transformer != null) {
                return transformer.addTerminalConnection(pos, otherPos, wireType);
            }
            return false;
        }

        private void removeConnection(BlockPos otherPos) {
            if (connector != null) {
                connector.removeConnection(otherPos);
            }
            if (transformer != null) {
                transformer.removeTerminalConnection(pos, otherPos);
            }
        }
    }
}
