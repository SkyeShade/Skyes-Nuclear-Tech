package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class LVConnectorBlockEntity extends BlockEntity {
    public static final int MAX_CONNECTIONS = 4;
    public static final double CONNECTOR_ANCHOR_Y_OFFSET = 0.45D - 1.0D / 16.0D;

    private static final String CONNECTIONS_TAG = "Connections";
    private static final String POSITION_TAG = "Position";
    private static final String HEAT_TAG = "Heat";
    private static final double HEAT_SYNC_THRESHOLD = 0.25D;
    private static final int HEAT_SYNC_INTERVAL_TICKS = 5;

    private final List<BlockPos> connections = new ArrayList<>();
    private final Map<BlockPos, Integer> currentTickTransferredRJ = new HashMap<>();
    private final Map<BlockPos, Double> connectionHeat = new HashMap<>();
    private final Map<BlockPos, Double> lastSyncedConnectionHeat = new HashMap<>();
    private final Map<BlockPos, Long> lastHeatSyncTick = new HashMap<>();

    public LVConnectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_CONNECTOR.get(), pos, blockState);
    }

    public List<BlockPos> getConnections() {
        return List.copyOf(connections);
    }

    public boolean canAddConnection(BlockPos pos) {
        return !worldPosition.equals(pos) && connections.size() < MAX_CONNECTIONS && !connections.contains(pos);
    }

    public boolean addConnection(BlockPos pos) {
        if (!canAddConnection(pos)) {
            return false;
        }

        connections.add(pos.immutable());
        connectionHeat.put(pos.immutable(), 0.0D);
        sync();
        return true;
    }

    public void removeConnection(BlockPos pos) {
        if (connections.remove(pos)) {
            currentTickTransferredRJ.remove(pos);
            connectionHeat.remove(pos);
            lastSyncedConnectionHeat.remove(pos);
            lastHeatSyncTick.remove(pos);
            sync();
        }
    }

    public void removeAllConnections() {
        if (level != null && !level.isClientSide) {
            for (BlockPos connection : List.copyOf(connections)) {
                if (level.getBlockEntity(connection) instanceof LVConnectorBlockEntity connector) {
                    connector.removeConnection(worldPosition);
                }
            }
        }

        if (!connections.isEmpty()) {
            connections.clear();
            currentTickTransferredRJ.clear();
            connectionHeat.clear();
            lastSyncedConnectionHeat.clear();
            lastHeatSyncTick.clear();
            sync();
        }
    }

    public void clearCableLoads() {
        currentTickTransferredRJ.clear();
    }

    public void recordCableLoad(BlockPos connection, int sentRJ) {
        if (connections.contains(connection)) {
            currentTickTransferredRJ.merge(connection, sentRJ, Integer::sum);
        }
    }

    public int getCurrentTickTransferredRJ(BlockPos connection) {
        return currentTickTransferredRJ.getOrDefault(connection, 0);
    }

    public double getConnectionHeat(BlockPos connection) {
        return connectionHeat.getOrDefault(connection, 0.0D);
    }

    public void setConnectionHeat(BlockPos connection, double heat) {
        if (!connections.contains(connection)) {
            return;
        }

        double clampedHeat = Math.max(0.0D, heat);
        double previousHeat = getConnectionHeat(connection);
        if (Math.abs(previousHeat - clampedHeat) < 0.01D) {
            return;
        }

        BlockPos immutableConnection = connection.immutable();
        connectionHeat.put(immutableConnection, clampedHeat);
        setChanged();

        if (shouldSyncHeat(immutableConnection, clampedHeat)) {
            markHeatSynced(immutableConnection, clampedHeat);
            sync();
        }
    }

    public static double anchorX(BlockPos pos) {
        return pos.getX() + 0.5D;
    }

    public static double anchorY(BlockPos pos) {
        return pos.getY() + CONNECTOR_ANCHOR_Y_OFFSET;
    }

    public static double anchorZ(BlockPos pos) {
        return pos.getZ() + 0.5D;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveConnections(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadConnections(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveConnections(tag);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadConnections(pkt.getTag());
    }

    private void saveConnections(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlockPos connection : connections) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(POSITION_TAG, connection.asLong());
            entry.putDouble(HEAT_TAG, getConnectionHeat(connection));
            list.add(entry);
        }

        tag.put(CONNECTIONS_TAG, list);
    }

    private void loadConnections(CompoundTag tag) {
        Map<BlockPos, Integer> previousLoads = new HashMap<>(currentTickTransferredRJ);
        connections.clear();
        connectionHeat.clear();
        lastSyncedConnectionHeat.clear();
        lastHeatSyncTick.clear();
        ListTag list = tag.getList(CONNECTIONS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && connections.size() < MAX_CONNECTIONS; index++) {
            BlockPos connection = BlockPos.of(list.getCompound(index).getLong(POSITION_TAG));
            if (!worldPosition.equals(connection) && !connections.contains(connection)) {
                connections.add(connection);
                double heat = list.getCompound(index).getDouble(HEAT_TAG);
                connectionHeat.put(connection, heat);
                lastSyncedConnectionHeat.put(connection, heat);
                lastHeatSyncTick.put(connection, level == null ? 0L : level.getGameTime());
            }
        }
        currentTickTransferredRJ.clear();
        for (BlockPos connection : connections) {
            Integer load = previousLoads.get(connection);
            if (load != null) {
                currentTickTransferredRJ.put(connection, load);
            }
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    private boolean shouldSyncHeat(BlockPos connection, double heat) {
        double lastSyncedHeat = lastSyncedConnectionHeat.getOrDefault(connection, 0.0D);
        if (Math.abs(lastSyncedHeat - heat) >= HEAT_SYNC_THRESHOLD || heat <= 0.0D && lastSyncedHeat > 0.0D) {
            return true;
        }

        long gameTime = level == null ? 0L : level.getGameTime();
        long lastSyncTick = lastHeatSyncTick.getOrDefault(connection, 0L);
        return heat > 0.0D && gameTime - lastSyncTick >= HEAT_SYNC_INTERVAL_TICKS;
    }

    private void markHeatSynced(BlockPos connection, double heat) {
        lastSyncedConnectionHeat.put(connection, heat);
        lastHeatSyncTick.put(connection, level == null ? 0L : level.getGameTime());
    }
}
