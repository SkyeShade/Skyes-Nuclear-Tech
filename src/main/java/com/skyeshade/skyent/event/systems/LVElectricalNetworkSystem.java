package com.skyeshade.skyent.event.systems;

import com.skyeshade.skyent.content.blockentity.CombustionGeneratorBlockEntity;
import com.skyeshade.skyent.content.blockentity.ElectricFurnaceBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVRJConverterBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.energy.CopperWireConstants;
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

        Map<BlockPos, LVConnectorBlockEntity> connectors = collectNetwork(level, startConnector);
        if (connectors.isEmpty() || !startConnector.getBlockPos().equals(owner(connectors))) {
            return;
        }

        List<Producer> producers = new ArrayList<>();
        List<Consumer> consumers = new ArrayList<>();
        for (LVConnectorBlockEntity connector : connectors.values()) {
            collectAttachedEndpoints(level, connector.getBlockPos(), producers, consumers);
        }

        Map<EdgeKey, Double> edgeCurrent = new HashMap<>();
        if (!producers.isEmpty() && !consumers.isEmpty()) {
            for (Producer producer : producers) {
                int availableOutput = Math.min(producer.endpoint.availableOutputRJ(), producer.endpoint.maxOutputRJPerTick());
                if (availableOutput <= 0) {
                    continue;
                }

                List<TransferTarget> targets = reachableConsumers(connectors, producer, consumers);
                int remainingOutput = availableOutput;
                for (int index = 0; index < targets.size() && remainingOutput > 0; index++) {
                    TransferTarget target = targets.get(index);
                    int remainingTargets = targets.size() - index;
                    int evenShare = Math.max(1, remainingOutput / remainingTargets);
                    int accepted = transferToConsumer(connectors, producer, target, evenShare, edgeCurrent, false, true);
                    remainingOutput -= accepted;
                }
            }
        }

        updateCableHeat(level, connectors);
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

            Map<BlockPos, LVConnectorBlockEntity> connectors = collectNetwork(level, connector);
            BlockPos owner = owner(connectors);
            if (handledNetworks.contains(owner)) {
                continue;
            }
            handledNetworks.add(owner);

            List<Producer> ignoredProducers = new ArrayList<>();
            List<Consumer> consumers = new ArrayList<>();
            for (LVConnectorBlockEntity networkConnector : connectors.values()) {
                collectAttachedEndpoints(level, networkConnector.getBlockPos(), ignoredProducers, consumers);
            }

            if (consumers.isEmpty()) {
                continue;
            }

            Producer converterProducer = new Producer(connectorPos, new NetworkProducer() {
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

            List<TransferTarget> targets = reachableConsumers(connectors, converterProducer, consumers);
            for (int index = 0; index < targets.size() && remaining[0] > 0; index++) {
                TransferTarget target = targets.get(index);
                int remainingTargets = targets.size() - index;
                int evenShare = Math.max(1, remaining[0] / remainingTargets);
                transferToConsumer(connectors, converterProducer, target, evenShare, new HashMap<>(), simulate, false);
            }
        }

        return maxAmount - remaining[0];
    }

    private static Map<BlockPos, LVConnectorBlockEntity> collectNetwork(ServerLevel level, LVConnectorBlockEntity startConnector) {
        Map<BlockPos, LVConnectorBlockEntity> connectors = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startConnector.getBlockPos());

        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            if (connectors.containsKey(pos)) {
                continue;
            }

            if (!(level.getBlockEntity(pos) instanceof LVConnectorBlockEntity connector)) {
                continue;
            }

            connectors.put(pos, connector);
            for (BlockPos connection : connector.getConnections()) {
                if (!connectors.containsKey(connection) && level.getBlockEntity(connection) instanceof LVConnectorBlockEntity) {
                    queue.add(connection);
                }
            }
        }

        return connectors;
    }

    private static BlockPos owner(Map<BlockPos, LVConnectorBlockEntity> connectors) {
        return connectors.keySet().stream().min(Comparator.comparingLong(BlockPos::asLong)).orElseThrow();
    }

    private static void collectAttachedEndpoints(ServerLevel level, BlockPos connectorPos, List<Producer> producers, List<Consumer> consumers) {
        for (Direction direction : Direction.values()) {
            BlockEntity blockEntity = level.getBlockEntity(connectorPos.relative(direction));
            if (blockEntity instanceof CombustionGeneratorBlockEntity generator) {
                producers.add(new Producer(connectorPos, new NetworkProducer() {
                    @Override
                    public int availableOutputRJ() {
                        return generator.getStoredRJ();
                    }

                    @Override
                    public int maxOutputRJPerTick() {
                        return generator.getMaxOutputRJPerTick();
                    }

                    @Override
                    public int extractRJ(int amount, boolean simulate) {
                        return generator.extractRJ(amount, simulate);
                    }
                }));
            } else if (blockEntity instanceof ElectricFurnaceBlockEntity furnace) {
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
            }
        }
    }

    private static List<TransferTarget> reachableConsumers(Map<BlockPos, LVConnectorBlockEntity> connectors, Producer producer, List<Consumer> consumers) {
        List<TransferTarget> targets = new ArrayList<>();
        for (Consumer consumer : consumers) {
            int capacity = consumer.endpoint.availableRJCapacity();
            if (capacity <= 0) {
                continue;
            }

            Path path = shortestPath(connectors, producer.connectorPos, consumer.connectorPos);
            if (path != null) {
                targets.add(new TransferTarget(consumer, path, capacity));
            }
        }

        return targets;
    }

    private static int transferToConsumer(Map<BlockPos, LVConnectorBlockEntity> connectors, Producer producer, TransferTarget target, int maxSentRJ, Map<EdgeKey, Double> edgeCurrent, boolean simulate, boolean applySafeTransferCap) {
        double sourceVoltage = CopperWireConstants.VOLTAGE;
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
            addPower(connectors, target.path.edges, edgeCurrent, extracted);
        }

        return extracted;
    }

    private static Path shortestPath(Map<BlockPos, LVConnectorBlockEntity> connectors, BlockPos start, BlockPos end) {
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

            LVConnectorBlockEntity connector = connectors.get(current.pos);
            if (connector == null) {
                continue;
            }

            for (BlockPos next : connector.getConnections()) {
                if (!connectors.containsKey(next)) {
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
            LVConnectorBlockEntity previousConnector = connectors.get(previousPos);
            LVWireType wireType = previousConnector == null ? LVWireType.COPPER : previousConnector.getConnectionWireType(current);
            double edgeDistance = Math.sqrt(previousPos.distSqr(current));
            pathDistance += edgeDistance;
            voltageDrop += edgeDistance * wireType.voltageDropPerBlock();
            maxTransferRJPerTick = Math.min(maxTransferRJPerTick, wireType.maxTransferRJPerTick());
            current = previousPos;
        }

        return new Path(edges, pathDistance, voltageDrop, maxTransferRJPerTick);
    }

    private static void addPower(Map<BlockPos, LVConnectorBlockEntity> connectors, List<EdgeKey> edges, Map<EdgeKey, Double> edgePower, int sentRJ) {
        for (EdgeKey edge : edges) {
            edgePower.merge(edge, (double) sentRJ, Double::sum);
            LVConnectorBlockEntity first = connectors.get(BlockPos.of(edge.first));
            LVConnectorBlockEntity second = connectors.get(BlockPos.of(edge.second));
            if (first != null && second != null) {
                first.recordCableLoad(second.getBlockPos(), sentRJ);
                second.recordCableLoad(first.getBlockPos(), sentRJ);
            }
        }
    }

    private static void updateCableHeat(ServerLevel level, Map<BlockPos, LVConnectorBlockEntity> connectors) {
        List<EdgeKey> burnouts = new ArrayList<>();
        for (LVConnectorBlockEntity connector : connectors.values()) {
            for (BlockPos connection : connector.getConnections()) {
                if (connector.getBlockPos().asLong() > connection.asLong()) {
                    continue;
                }

                LVConnectorBlockEntity other = connectors.get(connection);
                if (other == null) {
                    continue;
                }

                int transferred = connector.getCurrentTickTransferredRJ(connection);
                double current = transferred / (double) CopperWireConstants.VOLTAGE;
                LVWireType wireType = connector.getConnectionWireType(connection);
                double heat = Math.max(connector.getConnectionHeat(connection), other.getConnectionHeat(connector.getBlockPos()));
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
                    burnouts.add(new EdgeKey(connector.getBlockPos(), connection));
                } else {
                    connector.setConnectionHeat(connection, heat);
                    other.setConnectionHeat(connector.getBlockPos(), heat);
                    spawnHeatParticles(level, connector.getBlockPos(), connection, heat);
                }
            }
        }

        for (EdgeKey burnout : burnouts) {
            BlockPos firstPos = BlockPos.of(burnout.first);
            BlockPos secondPos = BlockPos.of(burnout.second);
            spawnBurnoutEffects(level, firstPos, secondPos);
            if (level.getBlockEntity(firstPos) instanceof LVConnectorBlockEntity first) {
                first.removeConnection(secondPos);
            }
            if (level.getBlockEntity(secondPos) instanceof LVConnectorBlockEntity second) {
                second.removeConnection(firstPos);
            }
        }

        connectors.values().forEach(LVConnectorBlockEntity::clearCableLoads);
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
        Vec3 start = LVConnectorBlockEntity.anchor(level.getBlockState(startPos), startPos);
        Vec3 end = LVConnectorBlockEntity.anchor(level.getBlockState(endPos), endPos);
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

    private interface NetworkProducer {
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
}
