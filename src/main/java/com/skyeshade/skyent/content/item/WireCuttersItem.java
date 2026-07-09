package com.skyeshade.skyent.content.item;

import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;

public class WireCuttersItem extends Item {
    public WireCuttersItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        BlockPos clickedPos = context.getClickedPos();
        boolean hasConnections = hasCuttableConnections(level, clickedPos);
        if (!hasConnections) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            List<RemovedWire> removedWires = cutConnections(level, clickedPos);
            if (!removedWires.isEmpty()) {
                dropWireDrums(level, clickedPos, removedWires);
                context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
                level.playSound(null, clickedPos, SoundEvents.SHEEP_SHEAR, SoundSource.BLOCKS, 0.8F, 1.1F);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean hasCuttableConnections(Level level, BlockPos clickedPos) {
        if (level.getBlockEntity(clickedPos) instanceof LVConnectorBlockEntity connector) {
            return !connector.getConnections().isEmpty();
        }

        BlockState state = level.getBlockState(clickedPos);
        if (!LVMVTransformerBlock.isMVTerminal(state)) {
            return false;
        }

        BlockPos masterPos = LVMVTransformerBlock.getMasterPos(state, clickedPos);
        if (!(level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer)) {
            return false;
        }

        return transformer.terminalConnections().stream()
                .anyMatch(connection -> connection.terminalPos().equals(clickedPos));
    }

    private static List<RemovedWire> cutConnections(Level level, BlockPos clickedPos) {
        if (level.getBlockEntity(clickedPos) instanceof LVConnectorBlockEntity connector) {
            List<RemovedWire> removedWires = new ArrayList<>();
            for (BlockPos connection : connector.getConnections()) {
                removedWires.add(new RemovedWire(clickedPos.immutable(), connection, connector.getConnectionWireType(connection)));
            }
            connector.removeAllConnections();
            return removedWires;
        }

        BlockState state = level.getBlockState(clickedPos);
        if (!LVMVTransformerBlock.isMVTerminal(state)) {
            return List.of();
        }

        BlockPos masterPos = LVMVTransformerBlock.getMasterPos(state, clickedPos);
        if (!(level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer)) {
            return List.of();
        }

        List<LVMVTransformerBlockEntity.TerminalConnection> connections = transformer.terminalConnections().stream()
                .filter(connection -> connection.terminalPos().equals(clickedPos))
                .toList();
        List<RemovedWire> removedWires = new ArrayList<>();
        for (LVMVTransformerBlockEntity.TerminalConnection connection : connections) {
            removedWires.add(new RemovedWire(clickedPos.immutable(), connection.connectionPos(), connection.wireType()));
            removeReciprocalConnection(level, clickedPos, connection.connectionPos());
            transformer.removeTerminalConnection(clickedPos, connection.connectionPos());
        }
        return removedWires;
    }

    private static void removeReciprocalConnection(Level level, BlockPos terminalPos, BlockPos connectedPos) {
        if (level.getBlockEntity(connectedPos) instanceof LVConnectorBlockEntity connector) {
            connector.removeConnection(terminalPos);
            return;
        }

        BlockState connectedState = level.getBlockState(connectedPos);
        if (LVMVTransformerBlock.isMVTerminal(connectedState)) {
            BlockPos connectedMasterPos = LVMVTransformerBlock.getMasterPos(connectedState, connectedPos);
            if (level.getBlockEntity(connectedMasterPos) instanceof LVMVTransformerBlockEntity connectedTransformer) {
                connectedTransformer.removeTerminalConnection(connectedPos, terminalPos);
            }
        }
    }

    private static void dropWireDrums(Level level, BlockPos clickedPos, List<RemovedWire> removedWires) {
        double x = clickedPos.getX() + 0.5D;
        double y = clickedPos.getY() + 0.5D;
        double z = clickedPos.getZ() + 0.5D;
        BlockState state = level.getBlockState(clickedPos);
        if (LVMVTransformerBlock.isMVTerminal(state)) {
            var anchor = LVMVTransformerBlock.mvTerminalAnchor(clickedPos);
            x = anchor.x;
            y = anchor.y;
            z = anchor.z;
        }

        for (RemovedWire removedWire : removedWires) {
            ItemLike drumItem = drumItemForWireType(removedWire.wireType());
            if (drumItem != null) {
                ItemStack drop = new ItemStack(drumItem);
                drop.remove(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                Containers.dropItemStack(level, x, y, z, drop);
            }
        }
    }

    private static ItemLike drumItemForWireType(LVWireType wireType) {
        return switch (wireType) {
            case COPPER -> ModItems.COPPER_WIRE_DRUM.get();
            case STEEL -> ModItems.STEEL_WIRE_DRUM.get();
            case COBALT_BRONZE -> ModItems.COBALT_BRONZE_WIRE_DRUM.get();
        };
    }

    private record RemovedWire(BlockPos from, BlockPos to, LVWireType wireType) {
    }

    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        ItemStack remainder = stack.copy();
        remainder.setCount(1);
        int nextDamage = remainder.getDamageValue() + 1;
        if (nextDamage >= remainder.getMaxDamage()) {
            return ItemStack.EMPTY;
        }
        remainder.setDamageValue(nextDamage);
        return remainder;
    }
}
