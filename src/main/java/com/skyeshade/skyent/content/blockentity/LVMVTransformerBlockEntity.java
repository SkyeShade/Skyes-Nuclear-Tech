package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.client.model.SkyentModelData;
import com.skyeshade.skyent.client.render.LVMVTransformerLighting;
import com.skyeshade.skyent.SkyesNuclearTech;
import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.LVWireType;
import com.skyeshade.skyent.content.energy.RJEnergyInfo;
import com.skyeshade.skyent.content.energy.RJStorage;
import com.skyeshade.skyent.event.systems.LVElectricalNetworkSystem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Method;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

public class LVMVTransformerBlockEntity extends BlockEntity implements RJEnergyInfo {
    public static final int MAX_TERMINAL_CONNECTIONS = 4;
    private static final boolean DEBUG_TRANSFORMER_POWER = false;
    private static final int ENERGY_CAPACITY_RJ = 32_000;
    private static final double LV_RATED_CURRENT_AMPS = 16.0D;
    private static final double MV_RATED_CURRENT_AMPS = 4.0D;
    private static final int LV_RATED_RJ_PER_TICK = ratedRJPerTick(ElectricalTier.LV, LV_RATED_CURRENT_AMPS);
    private static final int MV_RATED_RJ_PER_TICK = ratedRJPerTick(ElectricalTier.MV, MV_RATED_CURRENT_AMPS);
    private static final int MAX_THROUGHPUT_RJ_PER_TICK = LV_RATED_RJ_PER_TICK;
    private static final String TAG_MODE = "Mode";
    private static final String TAG_STORED_RJ = "StoredRJ";
    private static final String TAG_CONVERTED_OUTPUT_BUDGET_RJ = "ConvertedOutputBudgetRJ";
    private static final String TAG_TRANSFORMING = "Transforming";
    private static final String TAG_TERMINAL_CONNECTIONS = "TerminalConnections";
    private static final String TAG_TERMINAL = "Terminal";
    private static final String TAG_CONNECTION = "Connection";
    private static final String TAG_WIRE_TYPE = "WireType";
    private static final int LIGHT_REFRESH_INTERVAL_TICKS = 20;
    private static final float TRANSFORMER_LOOP_VOLUME = 0.65F;
    private static final float TRANSFORMER_LOOP_PITCH = 1.0F;

    private TransformerMode mode = TransformerMode.STEP_UP;
    private final RJStorage rjStorage = new RJStorage(ENERGY_CAPACITY_RJ);
    private int convertedOutputBudgetRJ;
    private int currentInputRJPerTick;
    private int currentOutputRJPerTick;
    private long lastPowerAccountingTick = Long.MIN_VALUE;
    private long lastTransformingTick = Long.MIN_VALUE;
    private boolean transforming;
    private final Map<BlockPos, Set<BlockPos>> terminalConnections = new HashMap<>();
    private final Map<ConnectionKey, LVWireType> terminalWireTypes = new HashMap<>();
    private final Map<ConnectionKey, Integer> terminalCurrentTickTransferredRJ = new HashMap<>();
    private final Map<ConnectionKey, Double> terminalConnectionHeat = new HashMap<>();
    private int cachedSharedPackedLight;
    private int lightCheckTicks;

    static {
        if (LV_RATED_RJ_PER_TICK != MV_RATED_RJ_PER_TICK) {
            throw new IllegalStateException("LV-MV Transformer ratings must conserve RJ/t");
        }
    }

    public LVMVTransformerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.LV_MV_TRANSFORMER.get(), pos, blockState);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LVMVTransformerBlockEntity transformer) {
        transformer.beginPowerAccountingTick();
        if (transformer.transforming && level.getGameTime() - transformer.lastTransformingTick > 1L) {
            transformer.setTransforming(false);
        }
        Direction facing = state.hasProperty(LVMVTransformerBlock.FACING)
                ? state.getValue(LVMVTransformerBlock.FACING)
                : Direction.NORTH;
        for (int z = 0; z <= 1; z++) {
            LVElectricalNetworkSystem.onTransformerTerminalTick(transformer, LVMVTransformerBlock.localToWorld(pos, facing, 0, 1, z));
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, LVMVTransformerBlockEntity transformer) {
        if (++transformer.lightCheckTicks >= LIGHT_REFRESH_INTERVAL_TICKS) {
            transformer.lightCheckTicks = 0;
            transformer.refreshSharedLight(false);
        }
        if (transformer.isTransforming()) {
            startClientTransformerLoop(level, pos, transformer.transformerCenter());
        } else {
            stopClientTransformerLoop(level, pos);
        }
    }

    @Override
    public ModelData getModelData() {
        int packedLight = cachedSharedPackedLight;
        if (packedLight == 0 && level != null) {
            packedLight = computePackedLight(level);
        }
        return ModelData.of(SkyentModelData.SHARED_PACKED_LIGHT, packedLight);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshSharedLight(true);
    }

    @Override
    public void setRemoved() {
        if (level != null && level.isClientSide) {
            stopClientTransformerLoop(level, worldPosition);
        }
        super.setRemoved();
    }

    public ElectricalTier inputTier() {
        return mode == TransformerMode.STEP_UP ? ElectricalTier.LV : ElectricalTier.MV;
    }

    public ElectricalTier outputTier() {
        return mode == TransformerMode.STEP_UP ? ElectricalTier.MV : ElectricalTier.LV;
    }

    public TransformerMode mode() {
        return mode;
    }

    public boolean canReceiveFromLVSide(ElectricalTier tier) {
        return tier == ElectricalTier.LV && inputTier() == ElectricalTier.LV;
    }

    public boolean canReceiveFromMVSide(ElectricalTier tier) {
        return tier == ElectricalTier.MV && inputTier() == ElectricalTier.MV;
    }

    public boolean canOutputToLVSide(ElectricalTier tier) {
        return tier == ElectricalTier.LV && outputTier() == ElectricalTier.LV;
    }

    public boolean canOutputToMVSide(ElectricalTier tier) {
        return tier == ElectricalTier.MV && outputTier() == ElectricalTier.MV;
    }

    public int getAvailableInputCapacityRJ(ElectricalTier tier) {
        if (tier != inputTier()) {
            return 0;
        }
        beginPowerAccountingTick();
        return Math.max(0, min(
                rjStorage.getAvailableRJCapacity(),
                ratedRJPerTickForTier(tier),
                remainingInputThroughputRJThisTick()
        ));
    }

    public int receiveRJ(ElectricalTier tier, int maxAmount, boolean simulate) {
        if (tier != inputTier()) {
            return 0;
        }
        beginPowerAccountingTick();
        int accepted = Math.max(0, min(
                maxAmount,
                rjStorage.getAvailableRJCapacity(),
                ratedRJPerTickForTier(tier),
                remainingInputThroughputRJThisTick()
        ));
        int received = rjStorage.receiveRJ(accepted, simulate);
        if (received > 0 && !simulate) {
            convertedOutputBudgetRJ = Math.min(rjStorage.getStoredRJ(), convertedOutputBudgetRJ + received);
            currentInputRJPerTick += received;
            debugConversion(tier, received, outputTier(), received);
            setChangedAndSync();
        }
        return received;
    }

    public int getAvailableOutputRJ(ElectricalTier tier) {
        if (tier != outputTier()) {
            return 0;
        }
        beginPowerAccountingTick();
        return Math.max(0, min(
                rjStorage.getStoredRJ(),
                convertedOutputBudgetRJ,
                ratedRJPerTickForTier(tier),
                remainingOutputThroughputRJThisTick()
        ));
    }

    public int extractRJ(ElectricalTier tier, int maxAmount, boolean simulate) {
        if (tier != outputTier()) {
            return 0;
        }
        beginPowerAccountingTick();
        int requested = Math.max(0, min(
                maxAmount,
                rjStorage.getStoredRJ(),
                convertedOutputBudgetRJ,
                ratedRJPerTickForTier(tier),
                remainingOutputThroughputRJThisTick()
        ));
        int extracted = rjStorage.extractRJ(requested, simulate);
        if (extracted > 0 && !simulate) {
            convertedOutputBudgetRJ = Math.max(0, convertedOutputBudgetRJ - extracted);
            currentOutputRJPerTick += extracted;
            lastTransformingTick = level == null ? Long.MIN_VALUE : level.getGameTime();
            setTransforming(true);
            debugConversion(inputTier(), 0, tier, extracted);
            setChangedAndSync();
        }
        return extracted;
    }

    @Override
    public int getEnergyStoredRJ() {
        return rjStorage.getStoredRJ();
    }

    @Override
    public int getEnergyCapacityRJ() {
        return rjStorage.getCapacityRJ();
    }

    @Override
    public String getVoltageTierName() {
        return inputTier().displayName() + " input / " + outputTier().displayName() + " output";
    }

    @Override
    public int getCurrentGenerationRJPerTick() {
        return currentOutputRJPerTick;
    }

    @Override
    public int getCurrentUsageRJPerTick() {
        return currentInputRJPerTick;
    }

    public boolean isTransforming() {
        return transforming;
    }

    public static int ratedThroughputRJPerTick() {
        return MAX_THROUGHPUT_RJ_PER_TICK;
    }

    public static double lvRatedCurrentAmps() {
        return LV_RATED_CURRENT_AMPS;
    }

    public static double mvRatedCurrentAmps() {
        return MV_RATED_CURRENT_AMPS;
    }

    public List<TerminalConnection> terminalConnections() {
        List<TerminalConnection> connections = new ArrayList<>();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : terminalConnections.entrySet()) {
            for (BlockPos connection : entry.getValue()) {
                connections.add(new TerminalConnection(entry.getKey(), connection, terminalWireTypes.getOrDefault(new ConnectionKey(entry.getKey(), connection), LVWireType.MV_COPPER)));
            }
        }
        return List.copyOf(connections);
    }

    public List<BlockPos> getTerminalConnections(BlockPos terminalPos) {
        return List.copyOf(terminalConnections.getOrDefault(terminalPos, Set.of()));
    }

    public LVWireType getTerminalConnectionWireType(BlockPos terminalPos, BlockPos connectionPos) {
        return terminalWireTypes.getOrDefault(new ConnectionKey(terminalPos, connectionPos), LVWireType.MV_COPPER);
    }

    public boolean canAddTerminalConnection(BlockPos terminalPos, BlockPos connectionPos, LVWireType wireType) {
        if (level == null || !wireType.isTier(ElectricalTier.MV) || terminalPos.equals(connectionPos)) {
            return false;
        }
        if (!LVMVTransformerBlock.isMVTerminal(level.getBlockState(terminalPos))) {
            return false;
        }
        Set<BlockPos> connections = terminalConnections.getOrDefault(terminalPos, Set.of());
        return connections.size() < MAX_TERMINAL_CONNECTIONS && !connections.contains(connectionPos);
    }

    public boolean addTerminalConnection(BlockPos terminalPos, BlockPos connectionPos, LVWireType wireType) {
        if (!canAddTerminalConnection(terminalPos, connectionPos, wireType)) {
            return false;
        }

        BlockPos terminal = terminalPos.immutable();
        BlockPos connection = connectionPos.immutable();
        terminalConnections.computeIfAbsent(terminal, ignored -> new LinkedHashSet<>()).add(connection);
        terminalWireTypes.put(new ConnectionKey(terminal, connection), wireType);
        terminalConnectionHeat.put(new ConnectionKey(terminal, connection), 0.0D);
        setChangedAndSync();
        return true;
    }

    public void removeTerminalConnection(BlockPos terminalPos, BlockPos connectionPos) {
        Set<BlockPos> connections = terminalConnections.get(terminalPos);
        if (connections == null || !connections.remove(connectionPos)) {
            return;
        }
        terminalWireTypes.remove(new ConnectionKey(terminalPos, connectionPos));
        terminalCurrentTickTransferredRJ.remove(new ConnectionKey(terminalPos, connectionPos));
        terminalConnectionHeat.remove(new ConnectionKey(terminalPos, connectionPos));
        if (connections.isEmpty()) {
            terminalConnections.remove(terminalPos);
        }
        setChangedAndSync();
    }

    public void removeAllTerminalConnections() {
        if (terminalConnections.isEmpty()) {
            return;
        }
        terminalConnections.clear();
        terminalWireTypes.clear();
        terminalCurrentTickTransferredRJ.clear();
        terminalConnectionHeat.clear();
        setChangedAndSync();
    }

    public void clearTerminalCableLoads() {
        terminalCurrentTickTransferredRJ.clear();
    }

    public void recordTerminalCableLoad(BlockPos terminalPos, BlockPos connectionPos, int sentRJ) {
        if (terminalConnections.getOrDefault(terminalPos, Set.of()).contains(connectionPos)) {
            terminalCurrentTickTransferredRJ.merge(new ConnectionKey(terminalPos, connectionPos), sentRJ, Integer::sum);
        }
    }

    public int getTerminalCurrentTickTransferredRJ(BlockPos terminalPos, BlockPos connectionPos) {
        return terminalCurrentTickTransferredRJ.getOrDefault(new ConnectionKey(terminalPos, connectionPos), 0);
    }

    public double getTerminalConnectionHeat(BlockPos terminalPos, BlockPos connectionPos) {
        return terminalConnectionHeat.getOrDefault(new ConnectionKey(terminalPos, connectionPos), 0.0D);
    }

    public void setTerminalConnectionHeat(BlockPos terminalPos, BlockPos connectionPos, double heat) {
        if (terminalConnections.getOrDefault(terminalPos, Set.of()).contains(connectionPos)) {
            terminalConnectionHeat.put(new ConnectionKey(terminalPos, connectionPos), Math.max(0.0D, heat));
            setChanged();
        }
    }

    public void toggleMode(net.minecraft.world.entity.player.Player player) {
        mode = mode == TransformerMode.STEP_UP ? TransformerMode.STEP_DOWN : TransformerMode.STEP_UP;
        setChangedAndSync();
        if (level != null && !level.isClientSide) {
            Vec3 center = transformerCenter();
            level.playSound(null, center.x, center.y, center.z, ModSounds.MECHANICAL_LEVER.get(), SoundSource.BLOCKS, 0.85F, 1.0F);
            player.displayClientMessage(Component.literal("Transformer mode: " + mode.displayName()), true);
        }
    }

    public void refreshSharedLight(boolean forceRenderUpdate) {
        if (level == null || !level.isClientSide) {
            return;
        }

        int packedLight = computePackedLight(level);
        if (!forceRenderUpdate && packedLight == cachedSharedPackedLight) {
            return;
        }

        cachedSharedPackedLight = packedLight;
        requestModelDataUpdate();
        BlockState state = getBlockState();
        level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
    }

    private int computePackedLight(Level level) {
        Direction facing = getBlockState().hasProperty(LVMVTransformerBlock.FACING)
                ? getBlockState().getValue(LVMVTransformerBlock.FACING)
                : Direction.NORTH;
        return LVMVTransformerLighting.computeMaxPackedLight(level, worldPosition, facing);
    }

    private Vec3 transformerCenter() {
        Direction facing = getBlockState().hasProperty(LVMVTransformerBlock.FACING)
                ? getBlockState().getValue(LVMVTransformerBlock.FACING)
                : Direction.NORTH;
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (int y = 0; y <= 1; y++) {
            for (int z = 0; z <= 1; z++) {
                BlockPos cell = LVMVTransformerBlock.localToWorld(worldPosition, facing, 0, y, z);
                sum = sum.add(cell.getX() + 0.5D, cell.getY() + 0.5D, cell.getZ() + 0.5D);
                count++;
            }
        }
        return sum.scale(1.0D / count);
    }

    private void setTransforming(boolean transforming) {
        if (this.transforming == transforming) {
            return;
        }

        this.transforming = transforming;
        setChangedAndSync();
    }

    private void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString(TAG_MODE, mode.serializedName);
        tag.putInt(TAG_STORED_RJ, rjStorage.getStoredRJ());
        tag.putInt(TAG_CONVERTED_OUTPUT_BUDGET_RJ, convertedOutputBudgetRJ);
        tag.putBoolean(TAG_TRANSFORMING, transforming);
        saveTerminalConnections(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = tag.contains(TAG_MODE) ? TransformerMode.byName(tag.getString(TAG_MODE)) : TransformerMode.STEP_UP;
        rjStorage.setStoredRJ(tag.getInt(TAG_STORED_RJ));
        convertedOutputBudgetRJ = tag.contains(TAG_CONVERTED_OUTPUT_BUDGET_RJ)
                ? Math.min(tag.getInt(TAG_CONVERTED_OUTPUT_BUDGET_RJ), rjStorage.getStoredRJ())
                : rjStorage.getStoredRJ();
        transforming = tag.getBoolean(TAG_TRANSFORMING);
        loadTerminalConnections(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        loadAdditional(pkt.getTag(), lookupProvider);
    }

    private void saveTerminalConnections(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Set<BlockPos>> entry : terminalConnections.entrySet()) {
            for (BlockPos connection : entry.getValue()) {
                CompoundTag connectionTag = new CompoundTag();
                connectionTag.putLong(TAG_TERMINAL, entry.getKey().asLong());
                connectionTag.putLong(TAG_CONNECTION, connection.asLong());
                connectionTag.putString(TAG_WIRE_TYPE, terminalWireTypes.getOrDefault(new ConnectionKey(entry.getKey(), connection), LVWireType.MV_COPPER).serializedName());
                list.add(connectionTag);
            }
        }
        tag.put(TAG_TERMINAL_CONNECTIONS, list);
    }

    private void loadTerminalConnections(CompoundTag tag) {
        terminalConnections.clear();
        terminalWireTypes.clear();
        terminalCurrentTickTransferredRJ.clear();
        terminalConnectionHeat.clear();
        ListTag list = tag.getList(TAG_TERMINAL_CONNECTIONS, Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            CompoundTag connectionTag = list.getCompound(index);
            BlockPos terminal = BlockPos.of(connectionTag.getLong(TAG_TERMINAL)).immutable();
            BlockPos connection = BlockPos.of(connectionTag.getLong(TAG_CONNECTION)).immutable();
            LVWireType wireType = LVWireType.byName(connectionTag.getString(TAG_WIRE_TYPE));
            if (!wireType.isTier(ElectricalTier.MV) || terminal.equals(connection)) {
                continue;
            }
            Set<BlockPos> connections = terminalConnections.computeIfAbsent(terminal, ignored -> new LinkedHashSet<>());
            if (connections.size() < MAX_TERMINAL_CONNECTIONS && connections.add(connection)) {
                terminalWireTypes.put(new ConnectionKey(terminal, connection), wireType);
                terminalConnectionHeat.put(new ConnectionKey(terminal, connection), 0.0D);
            }
        }
    }

    private void beginPowerAccountingTick() {
        if (level == null) {
            return;
        }

        long gameTime = level.getGameTime();
        if (lastPowerAccountingTick == gameTime) {
            return;
        }

        lastPowerAccountingTick = gameTime;
        currentInputRJPerTick = 0;
        currentOutputRJPerTick = 0;
        convertedOutputBudgetRJ = Math.min(convertedOutputBudgetRJ, rjStorage.getStoredRJ());
    }

    private int maxThroughputRJPerTick() {
        return MAX_THROUGHPUT_RJ_PER_TICK;
    }

    private int remainingInputThroughputRJThisTick() {
        return Math.max(0, maxThroughputRJPerTick() - currentInputRJPerTick);
    }

    private int remainingOutputThroughputRJThisTick() {
        return Math.max(0, maxThroughputRJPerTick() - currentOutputRJPerTick);
    }

    private void debugConversion(ElectricalTier inputTier, int inputRJPerTick, ElectricalTier outputTier, int outputRJPerTick) {
        if (!DEBUG_TRANSFORMER_POWER) {
            return;
        }

        double inputAmps = currentInputRJPerTick / (double) inputTier.voltage();
        double outputAmps = currentOutputRJPerTick / (double) outputTier.voltage();
        SkyesNuclearTech.LOGGER.info(
                "[LV-MV Transformer {}] mode={} inputTier={} outputTier={} input={}/{} RJ/t ({}A) output={}/{} RJ/t ({}A) lastInput={}RJ/t lastOutput={}RJ/t",
                worldPosition,
                mode.displayName(),
                inputTier.name(),
                outputTier.name(),
                currentInputRJPerTick,
                maxThroughputRJPerTick(),
                inputAmps,
                currentOutputRJPerTick,
                maxThroughputRJPerTick(),
                outputAmps,
                inputRJPerTick,
                outputRJPerTick
        );
    }

    private static int ratedRJPerTickForTier(ElectricalTier tier) {
        return switch (tier) {
            case LV -> LV_RATED_RJ_PER_TICK;
            case MV -> MV_RATED_RJ_PER_TICK;
            default -> 0;
        };
    }

    private static int ratedRJPerTick(ElectricalTier tier, double currentAmps) {
        return (int) Math.round(tier.voltage() * currentAmps);
    }

    private static int min(int first, int... rest) {
        int result = first;
        for (int value : rest) {
            result = Math.min(result, value);
        }
        return result;
    }

    private static void startClientTransformerLoop(Level level, BlockPos pos, Vec3 center) {
        invokeClientLoopMethod("startOrUpdateNamedLoop", level, pos, center);
    }

    private static void stopClientTransformerLoop(Level level, BlockPos pos) {
        invokeClientLoopMethod("stopNamedLoop", level, pos, Vec3.ZERO);
    }

    private static String transformerLoopKey(BlockPos pos) {
        return "lv_mv_transformer:" + pos.asLong();
    }

    private static void invokeClientLoopMethod(String methodName, Level level, BlockPos pos, Vec3 center) {
        if (!level.isClientSide) {
            return;
        }

        try {
            Class<?> clientLevelClass = Class.forName("net.minecraft.client.multiplayer.ClientLevel");
            if (!clientLevelClass.isInstance(level)) {
                return;
            }

            Class<?> managerClass = Class.forName("com.skyeshade.skyent.client.sound.MachineSoundManager");
            if ("startOrUpdateNamedLoop".equals(methodName)) {
                Method method = managerClass.getMethod(
                        methodName,
                        clientLevelClass,
                        String.class,
                        net.minecraft.sounds.SoundEvent.class,
                        SoundSource.class,
                        java.util.function.Supplier.class,
                        float.class,
                        float.class,
                        java.util.function.BooleanSupplier.class
                );
                method.invoke(
                        null,
                        level,
                        transformerLoopKey(pos),
                        ModSounds.TRANSFORMER_LOOP.get(),
                        SoundSource.BLOCKS,
                        (java.util.function.Supplier<Vec3>) () -> center,
                        TRANSFORMER_LOOP_VOLUME,
                        TRANSFORMER_LOOP_PITCH,
                        (java.util.function.BooleanSupplier) () -> level.getBlockEntity(pos) instanceof LVMVTransformerBlockEntity transformer && transformer.isTransforming()
                );
            } else {
                Method method = managerClass.getMethod(methodName, clientLevelClass, String.class, net.minecraft.sounds.SoundEvent.class);
                method.invoke(null, level, transformerLoopKey(pos), ModSounds.TRANSFORMER_LOOP.get());
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to update LV-MV Transformer client loop sound", exception);
        }
    }

    public enum TransformerMode {
        STEP_UP("step_up", "LV \u2192 MV"),
        STEP_DOWN("step_down", "MV \u2192 LV");

        private final String serializedName;
        private final String displayName;

        TransformerMode(String serializedName, String displayName) {
            this.serializedName = serializedName;
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }

        private static TransformerMode byName(String name) {
            for (TransformerMode mode : values()) {
                if (mode.serializedName.equals(name)) {
                    return mode;
                }
            }
            return STEP_UP;
        }
    }

    public record TerminalConnection(BlockPos terminalPos, BlockPos connectionPos, LVWireType wireType) {
    }

    private record ConnectionKey(BlockPos terminalPos, BlockPos connectionPos) {
    }
}
