package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.registry.ModBlocks;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class LVConnectorBlockEntity extends BlockEntity {
    public static final int MAX_CONNECTIONS = 4;
    public static final double CONNECTOR_ANCHOR_CENTER = 0.5D;
    public static final double CONNECTOR_ANCHOR_NEAR_FACE = 0.45D - 1.0D / 16.0D;
    public static final double CONNECTOR_ANCHOR_FAR_FACE = 1.0D - CONNECTOR_ANCHOR_NEAR_FACE;
    private static final double MV_CONNECTOR_ANCHOR_LOCAL_OFFSET = 2.0D / 16.0D;

    private static final String CONNECTIONS_TAG = "Connections";
    private static final String POSITION_TAG = "Position";
    private static final String HEAT_TAG = "Heat";
    private static final String WIRE_TYPE_TAG = "WireType";
    private static final double HEAT_SYNC_THRESHOLD = 0.25D;
    private static final int HEAT_SYNC_INTERVAL_TICKS = 5;

    private final Set<BlockPos> connections = new LinkedHashSet<>();
    private final Map<BlockPos, Integer> currentTickTransferredRJ = new HashMap<>();
    private final Map<BlockPos, Double> connectionHeat = new HashMap<>();
    private final Map<BlockPos, LVWireType> connectionWireTypes = new HashMap<>();
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

    public boolean canAddConnection(BlockPos pos, LVWireType wireType) {
        return canAddConnection(pos) && canAcceptWireType(wireType);
    }

    public boolean canConnectTo(LVConnectorBlockEntity other, LVWireType wireType) {
        return other != null
                && canAddConnection(other.getBlockPos(), wireType)
                && other.canAddConnection(getBlockPos(), wireType);
    }

    public boolean canAcceptWireType(LVWireType wireType) {
        return wireType.isTier(getConnectorTier());
    }

    public ElectricalTier getConnectorTier() {
        return isMVConnector() ? ElectricalTier.MV : ElectricalTier.LV;
    }

    public boolean isMVConnector() {
        return getBlockState().is(ModBlocks.MV_CONNECTOR.get());
    }

    public boolean addConnection(BlockPos pos) {
        return addConnection(pos, LVWireType.COPPER);
    }

    public boolean addConnection(BlockPos pos, LVWireType wireType) {
        if (!canAddConnection(pos, wireType)) {
            return false;
        }

        BlockPos immutablePos = pos.immutable();
        connections.add(immutablePos);
        connectionHeat.put(immutablePos, 0.0D);
        connectionWireTypes.put(immutablePos, wireType);
        sync();
        return true;
    }

    public void removeConnection(BlockPos pos) {
        if (connections.remove(pos)) {
            currentTickTransferredRJ.remove(pos);
            connectionHeat.remove(pos);
            connectionWireTypes.remove(pos);
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
                    continue;
                }

                BlockState connectionState = level.getBlockState(connection);
                if (LVMVTransformerBlock.isMVTerminal(connectionState)) {
                    BlockPos masterPos = LVMVTransformerBlock.getMasterPos(connectionState, connection);
                    if (level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer) {
                        transformer.removeTerminalConnection(connection, worldPosition);
                    }
                }
            }
        }

        if (!connections.isEmpty()) {
            connections.clear();
            currentTickTransferredRJ.clear();
            connectionHeat.clear();
            connectionWireTypes.clear();
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

    public LVWireType getConnectionWireType(BlockPos connection) {
        return connectionWireTypes.getOrDefault(connection, LVWireType.COPPER);
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
        return pos.getY() + CONNECTOR_ANCHOR_NEAR_FACE;
    }

    public static double anchorZ(BlockPos pos) {
        return pos.getZ() + 0.5D;
    }

    public static Vec3 anchor(BlockState state, BlockPos pos) {
        Direction facing = state.hasProperty(com.skyeshade.skyent.content.block.LVConnectorBlock.FACING)
                ? state.getValue(com.skyeshade.skyent.content.block.LVConnectorBlock.FACING)
                : Direction.UP;
        double x = pos.getX() + CONNECTOR_ANCHOR_CENTER;
        double y = pos.getY() + CONNECTOR_ANCHOR_CENTER;
        double z = pos.getZ() + CONNECTOR_ANCHOR_CENTER;

        Vec3 anchor = switch (facing) {
            case UP -> new Vec3(x, pos.getY() + CONNECTOR_ANCHOR_NEAR_FACE, z);
            case DOWN -> new Vec3(x, pos.getY() + CONNECTOR_ANCHOR_FAR_FACE, z);
            case NORTH -> new Vec3(x, y, pos.getZ() + CONNECTOR_ANCHOR_FAR_FACE);
            case SOUTH -> new Vec3(x, y, pos.getZ() + CONNECTOR_ANCHOR_NEAR_FACE);
            case EAST -> new Vec3(pos.getX() + CONNECTOR_ANCHOR_NEAR_FACE, y, z);
            case WEST -> new Vec3(pos.getX() + CONNECTOR_ANCHOR_FAR_FACE, y, z);
        };
        return state.is(ModBlocks.MV_CONNECTOR.get())
                ? anchor.add(
                facing.getStepX() * MV_CONNECTOR_ANCHOR_LOCAL_OFFSET,
                facing.getStepY() * MV_CONNECTOR_ANCHOR_LOCAL_OFFSET,
                facing.getStepZ() * MV_CONNECTOR_ANCHOR_LOCAL_OFFSET
        )
                : anchor;
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
            entry.putString(WIRE_TYPE_TAG, getConnectionWireType(connection).serializedName());
            list.add(entry);
        }

        tag.put(CONNECTIONS_TAG, list);
    }

    private void loadConnections(CompoundTag tag) {
        Map<BlockPos, Integer> previousLoads = new HashMap<>(currentTickTransferredRJ);
        connections.clear();
        connectionHeat.clear();
        connectionWireTypes.clear();
        lastSyncedConnectionHeat.clear();
        lastHeatSyncTick.clear();
        ListTag list = tag.getList(CONNECTIONS_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size() && connections.size() < MAX_CONNECTIONS; index++) {
            BlockPos connection = BlockPos.of(list.getCompound(index).getLong(POSITION_TAG));
            LVWireType wireType = LVWireType.byName(list.getCompound(index).getString(WIRE_TYPE_TAG));
            if (!worldPosition.equals(connection) && !connections.contains(connection) && canAcceptWireType(wireType)) {
                connection = connection.immutable();
                connections.add(connection);
                double heat = list.getCompound(index).getDouble(HEAT_TAG);
                connectionHeat.put(connection, heat);
                connectionWireTypes.put(connection, wireType);
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
