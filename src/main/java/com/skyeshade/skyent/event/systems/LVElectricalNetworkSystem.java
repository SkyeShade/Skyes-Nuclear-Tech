package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.content.block.LVMVTransformerBlock;
import com.skyeshade.skyent.content.block.CentrifugeBlock;
import com.skyeshade.skyent.content.block.MVInlinePumpBlock;
import com.skyeshade.skyent.content.block.MVAssemblerBlock;
import com.skyeshade.skyent.content.block.MVChemicalReactorBlock;
import com.skyeshade.skyent.content.block.HeatingChamberBlock;
import com.skyeshade.skyent.content.block.IndustrialPressBlock;
import com.skyeshade.skyent.content.block.RollingMillBlock;
import com.skyeshade.skyent.content.block.WireMillBlock;
import com.skyeshade.skyent.content.blockentity.ElectricFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.CentrifugeBlockEntity;
import com.skyeshade.skyent.content.blockentity.HeatingChamberBlockEntity;
import com.skyeshade.skyent.content.blockentity.IndustrialPressBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVCrusherBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVElectricPumpBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVSteamTurbineBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVRJConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity;
import com.skyeshade.skyent.content.blockentity.MVAssemblerBlockEntity;
import com.skyeshade.skyent.content.blockentity.MVChemicalReactorBlockEntity;
import com.skyeshade.skyent.content.blockentity.MVInlinePumpBlockEntity;
import com.skyeshade.skyent.content.blockentity.RollingMillBlockEntity;
import com.skyeshade.skyent.content.blockentity.WireMillBlockEntity;
import com.skyeshade.skyent.content.energy.CopperWireConstants;
import com.skyeshade.skyent.content.energy.ElectricalTier;
import com.skyeshade.skyent.content.energy.LVWireType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class LVElectricalNetworkSystem {
    public static final int CABLE_SEGMENTS = 16;
    public static final double CABLE_BASE_SAG = 0.15D;
    public static final double CABLE_SAG_PER_BLOCK = 0.01D;
    public static final double CABLE_MAX_SAG = 0.75D;
    private static final int HOT_PARTICLE_ATTEMPTS = 2;
    private static final int BURNOUT_PARTICLE_SAMPLES = 12;
    private static final float BURNOUT_FIRE_CHANCE = 0.18F;
    private static final float BURNOUT_SOUND_VOLUME = 0.85F;
    private static final float BURNOUT_SOUND_BASE_PITCH = 0.85F;
    private static final float BURNOUT_SOUND_RANDOM_PITCH = 0.35F;

    private LVElectricalNetworkSystem() {
    }

    public static void onConnectorTick(LVConnectorBlockEntity startConnector) {
        if (!(startConnector.getLevel() instanceof ServerLevel level)) {
            return;
        }

        Map<BlockPos, WireNode> nodes = collectNetwork(level, startConnector.getBlockPos());
        if (nodes.isEmpty() || !startConnector.getBlockPos().equals(owner(nodes))) {
            return;
        }

        tickNetwork(level, nodes);
    }

    public static void onTransformerTerminalTick(LVMVTransformerBlockEntity transformer, BlockPos terminalPos) {
        if (!(transformer.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!LVMVTransformerBlock.isMVTerminal(level.getBlockState(terminalPos))) {
            return;
        }

        Map<BlockPos, WireNode> nodes = collectNetwork(level, terminalPos);
        if (nodes.isEmpty() || !terminalPos.equals(owner(nodes))) {
            return;
        }

        tickNetwork(level, nodes);
    }

    private static void tickNetwork(ServerLevel level, Map<BlockPos, WireNode> nodes) {
        List<Producer> producers = new ArrayList<>();
        List<Consumer> consumers = new ArrayList<>();
        for (WireNode node : nodes.values()) {
            collectAttachedEndpoints(level, node, producers, consumers);
        }

        Map<EdgeKey, Double> edgeCurrent = new HashMap<>();
        if (!producers.isEmpty() && !consumers.isEmpty()) {
            for (Producer producer : producers) {
                int availableOutput = Math.min(producer.endpoint.availableOutputRJ(), producer.endpoint.maxOutputRJPerTick());
                if (availableOutput <= 0) {
                    continue;
                }

                List<TransferTarget> targets = reachableConsumers(nodes, producer, consumers);
                int remainingOutput = availableOutput;
                for (int index = 0; index < targets.size() && remainingOutput > 0; index++) {
                    TransferTarget target = targets.get(index);
                    int remainingTargets = targets.size() - index;
                    int evenShare = Math.max(1, remainingOutput / remainingTargets);
                    int accepted = transferToConsumer(nodes, producer, target, evenShare, edgeCurrent, false, true);
                    remainingOutput -= accepted;
                }
            }
        }

        updateCableHeat(level, nodes);
    }

    public static int insertRJFromConverter(ServerLevel level, BlockPos converterPos, int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }

        List<BlockPos> connectorPositions = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(converterPos.relative(direction)) instanceof LVConnectorBlockEntity connector) {
                connectorPositions.add(connector.getBlockPos());
            }
        }

        if (connectorPositions.isEmpty()) {
            return 0;
        }

        int[] remaining = {maxAmount};
        List<BlockPos> handledNetworks = new ArrayList<>();
        for (BlockPos connectorPos : connectorPositions) {
            if (remaining[0] <= 0) {
                break;
            }

            if (!(level.getBlockEntity(connectorPos) instanceof LVConnectorBlockEntity connector)) {
                continue;
            }

            Map<BlockPos, WireNode> nodes = collectNetwork(level, connector.getBlockPos());
            BlockPos owner = owner(nodes);
            if (handledNetworks.contains(owner)) {
                continue;
            }
            handledNetworks.add(owner);

            List<Producer> ignoredProducers = new ArrayList<>();
            List<Consumer> consumers = new ArrayList<>();
            for (WireNode node : nodes.values()) {
                collectAttachedEndpoints(level, node, ignoredProducers, consumers);
            }

            if (consumers.isEmpty()) {
                continue;
            }

            Producer converterProducer = new Producer(connectorPos, new NetworkProducer() {
                @Override
                public int voltage() {
                    return ElectricalTier.LV.voltage();
                }

                @Override
                public int availableOutputRJ() {
                    return remaining[0];
                }

                @Override
                public int maxOutputRJPerTick() {
                    return remaining[0];
                }

                @Override
                public int extractRJ(int amount, boolean extractionSimulate) {
                    int extracted = Math.min(amount, remaining[0]);
                    if (extracted > 0) {
                        remaining[0] -= extracted;
                    }
                    return extracted;
                }
            });

            List<TransferTarget> targets = reachableConsumers(nodes, converterProducer, consumers);
            for (int index = 0; index < targets.size() && remaining[0] > 0; index++) {
                TransferTarget target = targets.get(index);
                int remainingTargets = targets.size() - index;
                int evenShare = Math.max(1, remaining[0] / remainingTargets);
                transferToConsumer(nodes, converterProducer, target, evenShare, new HashMap<>(), simulate, false);
            }
        }

        return maxAmount - remaining[0];
    }

    private static Map<BlockPos, WireNode> collectNetwork(ServerLevel level, BlockPos startPos) {
        Map<BlockPos, WireNode> nodes = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPos.immutable());

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (nodes.containsKey(pos)) {
                continue;
            }

            WireNode node = wireNodeAt(level, pos);
            if (node == null) {
                continue;
            }

            nodes.put(pos, node);
            for (BlockPos connection : node.connections()) {
                if (!nodes.containsKey(connection) && wireNodeAt(level, connection) != null) {
                    queue.add(connection.immutable());
                }
            }
        }

        return nodes;
    }

    private static BlockPos owner(Map<BlockPos, WireNode> nodes) {
        return nodes.keySet().stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
    }

    private static void collectAttachedEndpoints(ServerLevel level, WireNode node, List<Producer> producers, List<Consumer> consumers) {
        BlockPos connectorPos = node.pos();
        if (node instanceof TransformerTerminalNode terminalNode) {
            collectTransformerTerminalEndpoint(terminalNode, producers, consumers);
            return;
        }

        if (!(node instanceof ConnectorNode connectorNode)) {
            return;
        }

        LVConnectorBlockEntity attachedConnector = connectorNode.connector();
        for (Direction direction : Direction.values()) {
            BlockPos endpointPos = connectorPos.relative(direction);
            BlockState endpointState = level.getBlockState(endpointPos);
            BlockEntity blockEntity = level.getBlockEntity(endpointPos);
            HeatingChamberBlockEntity heatingChamber = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveHeatingChamber(level, endpointState, endpointPos)
                    : null;
            IndustrialPressBlockEntity industrialPress = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveIndustrialPress(level, endpointState, endpointPos)
                    : null;
            RollingMillBlockEntity rollingMill = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveRollingMill(level, endpointState, endpointPos, direction.getOpposite())
                    : null;
            WireMillBlockEntity wireMill = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveWireMill(level, endpointState, endpointPos)
                    : null;
            MVAssemblerBlockEntity assembler = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveMVAssembler(level, endpointState, endpointPos)
                    : null;
            MVChemicalReactorBlockEntity chemicalReactor = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveMVChemicalReactor(level, endpointState, endpointPos)
                    : null;
            CentrifugeBlockEntity centrifuge = attachedConnector != null && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    ? resolveCentrifuge(level, endpointState, endpointPos)
                    : null;
            MVInlinePumpBlockEntity inlinePump = attachedConnector != null
                    && attachedConnector.getConnectorTier() == ElectricalTier.MV
                    && blockEntity instanceof MVInlinePumpBlockEntity pump
                    && MVInlinePumpBlock.isValidEnergyConnection(endpointState, direction.getOpposite())
                    ? pump
                    : null;
            LVMVTransformerBlockEntity transformerBody = attachedConnector != null
                    ? resolveTransformerBody(level, endpointState, endpointPos)
                    : null;
            if (blockEntity instanceof ElectricFurnaceBlockEntity furnace) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return furnace.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return furnace.receiveRJ(amount, simulate);
                    }
                }));
            } else if (blockEntity instanceof LVCrusherBlockEntity crusher) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return crusher.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return crusher.receiveRJ(amount, simulate);
                    }
                }));
            } else if (blockEntity instanceof LVElectricPumpBlockEntity pump) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return pump.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return pump.receiveRJ(amount, simulate);
                    }
                }));
            } else if (blockEntity instanceof LVRJConverterBlockEntity converter) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return converter.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return converter.receiveRJ(amount, simulate);
                    }
                }));
            } else if (inlinePump != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return inlinePump.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return inlinePump.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (transformerBody != null && transformerBody.canReceiveFromLVSide(attachedConnector.getConnectorTier())) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return transformerBody.getAvailableInputCapacityRJ(attachedConnector.getConnectorTier());
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return transformerBody.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (heatingChamber != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return heatingChamber.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return heatingChamber.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (industrialPress != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return industrialPress.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return industrialPress.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (rollingMill != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return rollingMill.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return rollingMill.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (wireMill != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return wireMill.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return wireMill.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (assembler != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return assembler.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return assembler.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (chemicalReactor != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return chemicalReactor.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return chemicalReactor.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (centrifuge != null) {
                consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                    @Override
                    public int availableRJCapacity() {
                        return centrifuge.getAvailableRJCapacity();
                    }

                    @Override
                    public int receiveRJ(int amount, boolean simulate) {
                        return centrifuge.receiveRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            } else if (blockEntity instanceof LVSteamTurbineBlockEntity turbine) {
                producers.add(new Producer(connectorPos, new NetworkProducer() {
                    @Override
                    public int voltage() {
                        return ElectricalTier.LV.voltage();
                    }

                    @Override
                    public int availableOutputRJ() {
                        return turbine.getStoredRJ();
                    }

                    @Override
                    public int maxOutputRJPerTick() {
                        return turbine.getMaxOutputRJPerTick();
                    }

                    @Override
                    public int extractRJ(int amount, boolean simulate) {
                        return turbine.extractRJ(amount, simulate);
                    }
                }));
            } else if (transformerBody != null && transformerBody.canOutputToLVSide(attachedConnector.getConnectorTier())) {
                producers.add(new Producer(connectorPos, new NetworkProducer() {
                    @Override
                    public int voltage() {
                        return ElectricalTier.LV.voltage();
                    }

                    @Override
                    public int availableOutputRJ() {
                        return transformerBody.getAvailableOutputRJ(attachedConnector.getConnectorTier());
                    }

                    @Override
                    public int maxOutputRJPerTick() {
                        return transformerBody.getAvailableOutputRJ(attachedConnector.getConnectorTier());
                    }

                    @Override
                    public int extractRJ(int amount, boolean simulate) {
                        return transformerBody.extractRJ(attachedConnector.getConnectorTier(), amount, simulate);
                    }
                }));
            }
        }
    }

    private static void collectTransformerTerminalEndpoint(TransformerTerminalNode terminalNode, List<Producer> producers, List<Consumer> consumers) {
        LVMVTransformerBlockEntity transformer = terminalNode.transformer();
        ElectricalTier connectorTier = terminalNode.tier();
        BlockPos connectorPos = terminalNode.pos();
        if (transformer.canOutputToMVSide(connectorTier)) {
            producers.add(new Producer(connectorPos, new NetworkProducer() {
                @Override
                public int voltage() {
                    return connectorTier.voltage();
                }

                @Override
                public int availableOutputRJ() {
                    return transformer.getAvailableOutputRJ(connectorTier);
                }

                @Override
                public int maxOutputRJPerTick() {
                    return transformer.getAvailableOutputRJ(connectorTier);
                }

                @Override
                public int extractRJ(int amount, boolean simulate) {
                    return transformer.extractRJ(connectorTier, amount, simulate);
                }
            }));
        }

        if (transformer.canReceiveFromMVSide(connectorTier)) {
            consumers.add(new Consumer(connectorPos, new NetworkConsumer() {
                @Override
                public int availableRJCapacity() {
                    return transformer.getAvailableInputCapacityRJ(connectorTier);
                }

                @Override
                public int receiveRJ(int amount, boolean simulate) {
                    return transformer.receiveRJ(connectorTier, amount, simulate);
                }
            }));
        }
    }

    @Nullable
    private static HeatingChamberBlockEntity resolveHeatingChamber(ServerLevel level, BlockState state, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof HeatingChamberBlockEntity chamber) {
            return chamber;
        }
        return HeatingChamberBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static IndustrialPressBlockEntity resolveIndustrialPress(ServerLevel level, BlockState state, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IndustrialPressBlockEntity press) {
            return press;
        }
        return IndustrialPressBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static RollingMillBlockEntity resolveRollingMill(ServerLevel level, BlockState state, BlockPos pos, Direction supportFace) {
        if (!RollingMillBlock.isConnectorSupportCell(level, state, pos, supportFace)) {
            return null;
        }
        return RollingMillBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static WireMillBlockEntity resolveWireMill(ServerLevel level, BlockState state, BlockPos pos) {
        if (!WireMillBlock.isConnectorSupportCell(state)) {
            return null;
        }
        return WireMillBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static MVAssemblerBlockEntity resolveMVAssembler(ServerLevel level, BlockState state, BlockPos pos) {
        return MVAssemblerBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static MVChemicalReactorBlockEntity resolveMVChemicalReactor(ServerLevel level, BlockState state, BlockPos pos) {
        return MVChemicalReactorBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static CentrifugeBlockEntity resolveCentrifuge(ServerLevel level, BlockState state, BlockPos pos) {
        return CentrifugeBlock.getMasterBlockEntity(level, state, pos).orElse(null);
    }

    @Nullable
    private static LVMVTransformerBlockEntity resolveTransformerBody(ServerLevel level, BlockState state, BlockPos pos) {
        if (!LVMVTransformerBlock.isConnectorSupportCell(state)) {
            return null;
        }

        BlockPos masterPos = LVMVTransformerBlock.getMasterPos(state, pos);
        if (level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer) {
            return transformer;
        }
        return null;
    }

    private static List<TransferTarget> reachableConsumers(Map<BlockPos, WireNode> nodes, Producer producer, List<Consumer> consumers) {
        List<TransferTarget> targets = new ArrayList<>();
        for (Consumer consumer : consumers) {
            int capacity = consumer.endpoint.availableRJCapacity();
            if (capacity <= 0) {
                continue;
            }

            Path path = shortestPath(nodes, producer.connectorPos, consumer.connectorPos);
            if (path != null) {
                targets.add(new TransferTarget(consumer, path, capacity));
            }
        }

        return targets;
    }

    private static int transferToConsumer(Map<BlockPos, WireNode> nodes, Producer producer, TransferTarget target, int maxSentRJ, Map<EdgeKey, Double> edgeCurrent, boolean simulate, boolean applySafeTransferCap) {
        double sourceVoltage = producer.endpoint.voltage();
        double voltageDrop = target.path.voltageDrop;
        double deliveredVoltage = Math.max(0.0D, sourceVoltage - voltageDrop);
        if (deliveredVoltage <= 0.0D) {
            return 0;
        }

        int capacityAdjustedSent = (int) Math.ceil(target.capacity * sourceVoltage / deliveredVoltage);
        int sent = Math.min(maxSentRJ, capacityAdjustedSent);
        if (applySafeTransferCap) {
            sent = Math.min(sent, target.path.maxTransferRJPerTick);
        }
        if (sent <= 0) {
            return 0;
        }

        double amps = sent / sourceVoltage;
        int delivered = (int) Math.floor(deliveredVoltage * amps);
        if (delivered <= 0 || target.consumer.endpoint.receiveRJ(delivered, true) <= 0) {
            return 0;
        }

        int extracted = producer.endpoint.extractRJ(sent, simulate);
        if (extracted <= 0) {
            return 0;
        }

        int adjustedDelivered = Math.min((int) Math.floor(deliveredVoltage * (extracted / sourceVoltage)), target.capacity);
        int accepted = target.consumer.endpoint.receiveRJ(adjustedDelivered, simulate);
        if (accepted > 0 && !simulate) {
            addPower(nodes, target.path.edges, edgeCurrent, extracted);
        }

        return extracted;
    }

    private static Path shortestPath(Map<BlockPos, WireNode> nodes, BlockPos start, BlockPos end) {
        if (start.equals(end)) {
            return new Path(List.of(), 0.0D, 0.0D, Integer.MAX_VALUE);
        }

        PriorityQueue<PathNode> queue = new PriorityQueue<>(Comparator.comparingDouble(PathNode::distance));
        Map<BlockPos, Double> distances = new HashMap<>();
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        queue.add(new PathNode(start, 0.0D));
        distances.put(start, 0.0D);

        while (!queue.isEmpty()) {
            PathNode current = queue.poll();
            if (current.distance > distances.getOrDefault(current.pos, Double.MAX_VALUE)) {
                continue;
            }

            if (current.pos.equals(end)) {
                break;
            }

            WireNode node = nodes.get(current.pos);
            if (node == null) {
                continue;
            }

            for (BlockPos next : node.connections()) {
                if (!nodes.containsKey(next)) {
                    continue;
                }

                double distance = current.distance + Math.sqrt(current.pos.distSqr(next));
                if (distance < distances.getOrDefault(next, Double.MAX_VALUE)) {
                    distances.put(next, distance);
                    previous.put(next, current.pos);
                    queue.add(new PathNode(next, distance));
                }
            }
        }

        Double distance = distances.get(end);
        if (distance == null) {
            return null;
        }

        List<EdgeKey> edges = new ArrayList<>();
        double pathDistance = 0.0D;
        double voltageDrop = 0.0D;
        int maxTransferRJPerTick = Integer.MAX_VALUE;
        BlockPos current = end;
        while (!current.equals(start)) {
            BlockPos previousPos = previous.get(current);
            if (previousPos == null) {
                return null;
            }

            edges.add(new EdgeKey(previousPos, current));
            WireNode previousNode = nodes.get(previousPos);
            LVWireType wireType = previousNode == null ? LVWireType.COPPER : previousNode.wireType(current);
            double edgeDistance = Math.sqrt(previousPos.distSqr(current));
            pathDistance += edgeDistance;
            voltageDrop += edgeDistance * wireType.resistancePerBlock();
            ElectricalTier edgeTier = previousNode == null ? ElectricalTier.LV : previousNode.tier();
            maxTransferRJPerTick = Math.min(maxTransferRJPerTick, wireType.maxTransferRJPerTick(edgeTier));
            current = previousPos;
        }

        return new Path(edges, pathDistance, voltageDrop, maxTransferRJPerTick);
    }

    private static void addPower(Map<BlockPos, WireNode> nodes, List<EdgeKey> edges, Map<EdgeKey, Double> edgePower, int sentRJ) {
        for (EdgeKey edge : edges) {
            edgePower.merge(edge, (double) sentRJ, Double::sum);
            WireNode first = nodes.get(BlockPos.of(edge.first));
            WireNode second = nodes.get(BlockPos.of(edge.second));
            if (first != null && second != null) {
                first.recordCableLoad(second.pos(), sentRJ);
                second.recordCableLoad(first.pos(), sentRJ);
            }
        }
    }

    private static void updateCableHeat(ServerLevel level, Map<BlockPos, WireNode> nodes) {
        List<EdgeKey> burnouts = new ArrayList<>();
        for (WireNode node : nodes.values()) {
            for (BlockPos connection : node.connections()) {
                if (node.pos().asLong() > connection.asLong()) {
                    continue;
                }

                WireNode other = nodes.get(connection);
                if (other == null) {
                    continue;
                }

                int transferred = node.currentTickTransferredRJ(connection);
                LVWireType wireType = node.wireType(connection);
                double current = transferred / (double) node.tier().voltage();
                double heat = Math.max(node.connectionHeat(connection), other.connectionHeat(node.pos()));
                if (current <= 0.0D && heat <= 0.0D) {
                    continue;
                }

                if (current > wireType.maxCurrentAmps()) {
                    // TODO: add explicit overload logging/debug visualization once cable diagnostics exist.
                    heat += (current - wireType.maxCurrentAmps()) * CopperWireConstants.COPPER_HEAT_PER_AMP_OVER;
                } else {
                    heat = Math.max(0.0D, heat - CopperWireConstants.COPPER_COOLING_PER_TICK);
                }

                if (heat >= CopperWireConstants.COPPER_BURNOUT_HEAT) {
                    burnouts.add(new EdgeKey(node.pos(), connection));
                } else {
                    node.setConnectionHeat(connection, heat);
                    other.setConnectionHeat(node.pos(), heat);
                    spawnHeatParticles(level, node.pos(), connection, heat);
                }
            }
        }

        for (EdgeKey burnout : burnouts) {
            BlockPos firstPos = BlockPos.of(burnout.first);
            BlockPos secondPos = BlockPos.of(burnout.second);
            spawnBurnoutEffects(level, firstPos, secondPos);
            removeWireConnection(level, firstPos, secondPos);
            removeWireConnection(level, secondPos, firstPos);
        }

        nodes.values().forEach(WireNode::clearCableLoads);
    }

    private static void spawnHeatParticles(ServerLevel level, BlockPos startPos, BlockPos endPos, double heat) {
        if (heat < CopperWireConstants.COPPER_SMOKE_HEAT) {
            return;
        }

        RandomSource random = level.random;
        int attempts = heat >= CopperWireConstants.COPPER_GLOW_ORANGE_HEAT ? HOT_PARTICLE_ATTEMPTS + 1 : HOT_PARTICLE_ATTEMPTS;
        for (int index = 0; index < attempts; index++) {
            Vec3 point = sagPoint(level, startPos, endPos, random.nextDouble());
            level.sendParticles(ParticleTypes.SMOKE, point.x, point.y, point.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            if (heat >= CopperWireConstants.COPPER_GLOW_ORANGE_HEAT && random.nextFloat() < 0.35F) {
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.01D, 0.01D, 0.01D, 0.0D);
            }
        }
    }

    private static void spawnBurnoutEffects(ServerLevel level, BlockPos startPos, BlockPos endPos) {
        playBurnoutSound(level, startPos, endPos);

        BlockState fireState = Blocks.FIRE.defaultBlockState();
        for (int index = 0; index <= BURNOUT_PARTICLE_SAMPLES; index++) {
            double t = index / (double) BURNOUT_PARTICLE_SAMPLES;
            Vec3 point = sagPoint(level, startPos, endPos, t);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, point.x, point.y, point.z, 2, 0.04D, 0.04D, 0.04D, 0.0D);

            if (level.random.nextFloat() > BURNOUT_FIRE_CHANCE) {
                continue;
            }

            BlockPos firePos = BlockPos.containing(point).offset(level.random.nextInt(3) - 1, level.random.nextInt(2) - 1, level.random.nextInt(3) - 1);
            if (level.isEmptyBlock(firePos) && fireState.canSurvive(level, firePos)) {
                level.setBlock(firePos, fireState, Block.UPDATE_ALL);
            }
        }
    }

    private static void playBurnoutSound(ServerLevel level, BlockPos startPos, BlockPos endPos) {
        Vec3 midpoint = sagPoint(level, startPos, endPos, 0.5D);
        level.playSound(
                null,
                midpoint.x,
                midpoint.y,
                midpoint.z,
                SoundEvents.FIRE_EXTINGUISH,
                SoundSource.BLOCKS,
                BURNOUT_SOUND_VOLUME,
                BURNOUT_SOUND_BASE_PITCH + level.random.nextFloat() * BURNOUT_SOUND_RANDOM_PITCH
        );
    }

    public static Vec3 sagPoint(BlockPos startPos, BlockPos endPos, double t) {
        Vec3 start = anchor(startPos);
        Vec3 end = anchor(endPos);
        return sagPoint(start, end, t);
    }

    public static Vec3 sagPoint(net.minecraft.world.level.BlockGetter level, BlockPos startPos, BlockPos endPos, double t) {
        Vec3 start = anchor(level, startPos);
        Vec3 end = anchor(level, endPos);
        return sagPoint(start, end, t);
    }

    private static Vec3 sagPoint(Vec3 start, Vec3 end, double t) {
        double distance = start.distanceTo(end);
        double sag = Math.min(CABLE_MAX_SAG, CABLE_BASE_SAG + distance * CABLE_SAG_PER_BLOCK);
        return start.lerp(end, t).subtract(0.0D, Math.sin(Math.PI * t) * sag, 0.0D);
    }

    private static Vec3 anchor(BlockPos pos) {
        return new Vec3(LVConnectorBlockEntity.anchorX(pos), LVConnectorBlockEntity.anchorY(pos), LVConnectorBlockEntity.anchorZ(pos));
    }

    private static Vec3 anchor(net.minecraft.world.level.BlockGetter level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return LVMVTransformerBlock.isMVTerminal(state)
                ? LVMVTransformerBlock.mvTerminalAnchor(pos)
                : LVConnectorBlockEntity.anchor(state, pos);
    }

    @Nullable
    private static WireNode wireNodeAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof LVConnectorBlockEntity connector) {
            return new ConnectorNode(connector);
        }

        BlockState state = level.getBlockState(pos);
        if (!LVMVTransformerBlock.isMVTerminal(state)) {
            return null;
        }

        BlockPos masterPos = LVMVTransformerBlock.getMasterPos(state, pos);
        if (level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer) {
            return new TransformerTerminalNode(pos.immutable(), transformer);
        }
        return null;
    }

    private static void removeWireConnection(ServerLevel level, BlockPos fromPos, BlockPos toPos) {
        if (level.getBlockEntity(fromPos) instanceof LVConnectorBlockEntity connector) {
            connector.removeConnection(toPos);
            return;
        }

        BlockState state = level.getBlockState(fromPos);
        if (!LVMVTransformerBlock.isMVTerminal(state)) {
            return;
        }

        BlockPos masterPos = LVMVTransformerBlock.getMasterPos(state, fromPos);
        if (level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer) {
            transformer.removeTerminalConnection(fromPos, toPos);
        }
    }

    private interface NetworkProducer {
        int voltage();

        int availableOutputRJ();

        int maxOutputRJPerTick();

        int extractRJ(int amount, boolean simulate);
    }

    private interface NetworkConsumer {
        int availableRJCapacity();

        int receiveRJ(int amount, boolean simulate);
    }

    private record Producer(BlockPos connectorPos, NetworkProducer endpoint) {
    }

    private record Consumer(BlockPos connectorPos, NetworkConsumer endpoint) {
    }

    private record TransferTarget(Consumer consumer, Path path, int capacity) {
    }

    private record Path(List<EdgeKey> edges, double distance, double voltageDrop, int maxTransferRJPerTick) {
    }

    private record PathNode(BlockPos pos, double distance) {
    }

    private record EdgeKey(long first, long second) {
        private EdgeKey(BlockPos first, BlockPos second) {
            this(Math.min(first.asLong(), second.asLong()), Math.max(first.asLong(), second.asLong()));
        }
    }

    private interface WireNode {
        BlockPos pos();

        ElectricalTier tier();

        List<BlockPos> connections();

        LVWireType wireType(BlockPos connection);

        void recordCableLoad(BlockPos connection, int sentRJ);

        int currentTickTransferredRJ(BlockPos connection);

        double connectionHeat(BlockPos connection);

        void setConnectionHeat(BlockPos connection, double heat);

        void clearCableLoads();
    }

    private record ConnectorNode(LVConnectorBlockEntity connector) implements WireNode {
        @Override
        public BlockPos pos() {
            return connector.getBlockPos();
        }

        @Override
        public ElectricalTier tier() {
            return connector.getConnectorTier();
        }

        @Override
        public List<BlockPos> connections() {
            return connector.getConnections();
        }

        @Override
        public LVWireType wireType(BlockPos connection) {
            return connector.getConnectionWireType(connection);
        }

        @Override
        public void recordCableLoad(BlockPos connection, int sentRJ) {
            connector.recordCableLoad(connection, sentRJ);
        }

        @Override
        public int currentTickTransferredRJ(BlockPos connection) {
            return connector.getCurrentTickTransferredRJ(connection);
        }

        @Override
        public double connectionHeat(BlockPos connection) {
            return connector.getConnectionHeat(connection);
        }

        @Override
        public void setConnectionHeat(BlockPos connection, double heat) {
            connector.setConnectionHeat(connection, heat);
        }

        @Override
        public void clearCableLoads() {
            connector.clearCableLoads();
        }
    }

    private record TransformerTerminalNode(BlockPos pos, LVMVTransformerBlockEntity transformer) implements WireNode {
        @Override
        public ElectricalTier tier() {
            return ElectricalTier.MV;
        }

        @Override
        public List<BlockPos> connections() {
            return transformer.getTerminalConnections(pos);
        }

        @Override
        public LVWireType wireType(BlockPos connection) {
            return transformer.getTerminalConnectionWireType(pos, connection);
        }

        @Override
        public void recordCableLoad(BlockPos connection, int sentRJ) {
            transformer.recordTerminalCableLoad(pos, connection, sentRJ);
        }

        @Override
        public int currentTickTransferredRJ(BlockPos connection) {
            return transformer.getTerminalCurrentTickTransferredRJ(pos, connection);
        }

        @Override
        public double connectionHeat(BlockPos connection) {
            return transformer.getTerminalConnectionHeat(pos, connection);
        }

        @Override
        public void setConnectionHeat(BlockPos connection, double heat) {
            transformer.setTerminalConnectionHeat(pos, connection, heat);
        }

        @Override
        public void clearCableLoads() {
            transformer.clearTerminalCableLoads();
        }
    }
}
