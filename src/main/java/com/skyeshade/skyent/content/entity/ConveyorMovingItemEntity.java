package com.skyeshade.skyent.content.entity;

import com.skyeshade.skyent.content.conveyor.ConveyorBeltSurface;
import com.skyeshade.skyent.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class ConveyorMovingItemEntity extends Entity {
    public static final double BELT_ITEM_SPEED = 0.062D;
    public static final double BELT_ITEM_Y = 5.0D / 16.0D;
    public static final double OUTPUT_EDGE_DISTANCE = 0.52D;
    public static final double BLOCKED_EDGE_DISTANCE = 0.42D;
    public static final double STRAIGHT_ITEM_SPACING = 0.28D;
    public static final double MERGE_ITEM_SPACING = 0.28D;
    public static final double MERGE_SPACING_DISTANCE_FROM_CENTER = 0.55D;
    public static final int MERGE_SPACING_TICKS = 12;
    public static final double ITEM_SPACING_DISTANCE = STRAIGHT_ITEM_SPACING;
    public static final double ITEM_SPACING_SEARCH_RADIUS = 0.75D;
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(
            ConveyorMovingItemEntity.class,
            EntityDataSerializers.ITEM_STACK
    );
    private BlockPos lastBeltPos;
    private Direction lastBeltFacing;
    private int mergeSpacingTicks;
    private static final EntityDataAccessor<Boolean> DATA_BLOCKED = SynchedEntityData.defineId(
            ConveyorMovingItemEntity.class,
            EntityDataSerializers.BOOLEAN
    );

    public ConveyorMovingItemEntity(EntityType<ConveyorMovingItemEntity> entityType, Level level) {
        super(entityType, level);
        setNoGravity(true);
        noPhysics = true;
    }

    public ConveyorMovingItemEntity(Level level, double x, double y, double z, ItemStack stack) {
        this(ModEntities.CONVEYOR_MOVING_ITEM.get(), level);
        setPos(x, y, z);
        setItemStack(stack);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_BLOCKED, false);
    }

    @Override
    public void tick() {
        super.tick();
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);

        if (getItemStack().isEmpty()) {
            discard();
            return;
        }

        if (level().isClientSide) {
            tickClientVisualMovement();
            return;
        }

        tickServerMovement();
    }

    private void tickServerMovement() {
        BeltContext belt = findCurrentBelt();

        if (belt == null && lastBeltPos != null) {
            belt = beltAt(lastBeltPos);
            if (belt != null) {
                tryHandleOutput(belt, true);
                tickMergeSpacing();
                return;
            }
        }

        if (belt == null || !belt.surface().canItemStay(level(), belt.pos(), position())) {
            dropAsNormalItem(position(), Vec3.ZERO);
            discard();
            return;
        }

        updateBeltTracking(belt);

        if (isBlocked()) {
            setPos(clampedFrontPosition(belt));
            tryHandleOutput(belt, true);
            tickMergeSpacing();
            return;
        }

        if (tryHandleOutput(belt, false)) {
            tickMergeSpacing();
            return;
        }

        Vec3 next = belt.surface().getTravelLocation(level(), belt.pos(), position(), BELT_ITEM_SPEED);

        if (wouldReachOutputEdge(belt, next) && shouldPreHandleOutput(belt)) {
            setPos(clampedFrontPosition(belt));
            tryHandleOutput(belt, true);
            tickMergeSpacing();
            return;
        }

        if (isItemAheadTooClose(belt, next)) {
            tickMergeSpacing();
            return;
        }

        setBlocked(false);
        setPos(next.x, next.y, next.z);
        tickMergeSpacing();
    }
    private boolean shouldPreHandleOutput(BeltContext belt) {
        BlockPos outputPos = belt.pos().relative(belt.facing());
        BlockState outputState = level().getBlockState(outputPos);


        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            return false;
        }


        return true;
    }
    private boolean wouldReachOutputEdge(BeltContext belt, Vec3 pos) {
        Vec3 center = belt.pos().getCenter();
        double forwardDistance =
                (pos.x - center.x) * belt.facing().getStepX()
                        + (pos.z - center.z) * belt.facing().getStepZ();

        return forwardDistance >= OUTPUT_EDGE_DISTANCE;
    }

    private void tickClientVisualMovement() {
        if (isBlocked()) {
            tickMergeSpacing();
            return;
        }

        BeltContext belt = findCurrentBelt();
        if (belt == null || !belt.surface().canItemStay(level(), belt.pos(), position())) {
            tickMergeSpacing();
            return;
        }

        // Client may track merge spacing for visuals/spacing, but must match server rules.
        updateBeltTracking(belt);

        Vec3 next = belt.surface().getTravelLocation(level(), belt.pos(), position(), BELT_ITEM_SPEED);

        // Client should also respect terminal outputs, but never insert/drop/discard.
        if (wouldReachOutputEdge(belt, next) && shouldPreHandleOutput(belt)) {
            setPos(clampedFrontPosition(belt));
            tickMergeSpacing();
            return;
        }

        // Client-side spacing prediction is needed so items do not visually pile up.
        if (isItemAheadTooClose(belt, next)) {
            tickMergeSpacing();
            return;
        }

        setPos(next.x, next.y, next.z);
        tickMergeSpacing();
    }

    private boolean tryHandleOutput(BeltContext belt, boolean force) {
        Vec3 center = belt.pos().getCenter();
        Vec3 position = position();
        double forwardDistance = (position.x - center.x) * belt.facing().getStepX()
                + (position.z - center.z) * belt.facing().getStepZ();
        if (!force && forwardDistance < OUTPUT_EDGE_DISTANCE) {
            return false;
        }

        BlockPos outputPos = belt.pos().relative(belt.facing());
        BlockState outputState = level().getBlockState(outputPos);
        if (outputState.getBlock() instanceof ConveyorBeltSurface) {
            if (outputState.hasProperty(com.skyeshade.skyent.content.block.BasicConveyorBeltBlock.FACING)
                    && outputState.getValue(com.skyeshade.skyent.content.block.BasicConveyorBeltBlock.FACING) == belt.facing().getOpposite()) {
                setBlocked(true);
                setPos(clampedFrontPosition(belt));
                return true;
            }
            setBlocked(false);
            return false;
        }

        var handler = level().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                outputPos,
                belt.facing().getOpposite()
        );
        if (handler != null) {
            ItemStack remainder = insertIntoHandler(handler, getItemStack());
            if (remainder.isEmpty()) {
                discard();
                return true;
            }
            if (remainder.getCount() != getItemStack().getCount()) {
                setItemStack(remainder);
            }
            setBlocked(true);
            setPos(clampedFrontPosition(belt));
            return true;
        }

        if (outputState.isAir()) {
            Vec3 dropPos = position.add(belt.facing().getStepX() * 0.15D, 0.02D, belt.facing().getStepZ() * 0.15D);
            dropAsNormalItem(dropPos, new Vec3(belt.facing().getStepX() * 0.08D, 0.04D, belt.facing().getStepZ() * 0.08D));
            discard();
            return true;
        }

        setBlocked(true);
        setPos(clampedFrontPosition(belt));
        return true;
    }

    private Vec3 clampedFrontPosition(BeltContext belt) {
        Vec3 snap = belt.surface().getClosestSnappingPosition(level(), belt.pos(), position());
        Vec3 center = belt.pos().getCenter();
        return new Vec3(
                center.x + belt.facing().getStepX() * BLOCKED_EDGE_DISTANCE,
                snap.y,
                center.z + belt.facing().getStepZ() * BLOCKED_EDGE_DISTANCE
        );
    }

    private boolean isItemAheadTooClose(BeltContext belt, Vec3 nextPosition) {
        AABB searchBox = getBoundingBox().inflate(ITEM_SPACING_SEARCH_RADIUS);
        for (ConveyorMovingItemEntity other : level().getEntitiesOfClass(ConveyorMovingItemEntity.class, searchBox, entity -> entity != this && !entity.isRemoved())) {
            double requiredSpacing = Math.max(getCurrentSpacingDistance(), other.getCurrentSpacingDistance());
            if (isUsingMergeSpacing() || other.isUsingMergeSpacing()) {
                double dx = other.getX() - nextPosition.x;
                double dz = other.getZ() - nextPosition.z;
                if (Math.sqrt(dx * dx + dz * dz) < requiredSpacing) {
                    return true;
                }
                continue;
            }

            Vec3 delta = other.position().subtract(nextPosition);
            double ahead = delta.x * belt.facing().getStepX() + delta.z * belt.facing().getStepZ();
            if (ahead > 0.0D && ahead < requiredSpacing) {
                return true;
            }
        }
        return false;
    }

    private void updateBeltTracking(BeltContext belt) {
        if (lastBeltPos != null && !lastBeltPos.equals(belt.pos()) && lastBeltFacing != null && lastBeltFacing != belt.facing()) {
            mergeSpacingTicks = MERGE_SPACING_TICKS;
        }
        lastBeltPos = belt.pos();
        lastBeltFacing = belt.facing();
    }

    private void tickMergeSpacing() {
        if (mergeSpacingTicks > 0) {
            mergeSpacingTicks--;
        }
    }

    private boolean isUsingMergeSpacing() {
        return mergeSpacingTicks > 0 || isNearBeltCenter();
    }

    private boolean isNearBeltCenter() {
        BeltContext belt = findCurrentBelt();
        if (belt == null) {
            return false;
        }

        Vec3 center = belt.pos().getCenter();
        double dx = getX() - center.x;
        double dz = getZ() - center.z;
        return Math.sqrt(dx * dx + dz * dz) < MERGE_SPACING_DISTANCE_FROM_CENTER && mergeSpacingTicks > 0;
    }

    private double getCurrentSpacingDistance() {
        return isUsingMergeSpacing() ? MERGE_ITEM_SPACING : STRAIGHT_ITEM_SPACING;
    }

    private BeltContext findCurrentBelt() {
        BlockPos current = BlockPos.containing(getX(), getY() - 0.05D, getZ());
        BeltContext context = beltAt(current);
        if (context != null) {
            return context;
        }
        return beltAt(current.below());
    }

    private BeltContext beltAt(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (!(state.getBlock() instanceof ConveyorBeltSurface surface)) {
            return null;
        }
        if (!state.hasProperty(com.skyeshade.skyent.content.block.BasicConveyorBeltBlock.FACING)) {
            return null;
        }
        return new BeltContext(pos, surface, state.getValue(com.skyeshade.skyent.content.block.BasicConveyorBeltBlock.FACING));
    }

    private static ItemStack insertIntoHandler(net.neoforged.neoforge.items.IItemHandler handler, ItemStack stack) {
        ItemStack remainder = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    public ItemStack getItemStack() {
        return entityData.get(DATA_ITEM);
    }

    public void setItemStack(ItemStack stack) {
        entityData.set(DATA_ITEM, stack.copy());
    }

    public boolean isBlocked() {
        return entityData.get(DATA_BLOCKED);
    }

    public void setBlocked(boolean blocked) {
        entityData.set(DATA_BLOCKED, blocked);
    }

    public void dropAsNormalItem(Vec3 position, Vec3 velocity) {
        if (level().isClientSide || getItemStack().isEmpty()) {
            return;
        }

        ItemEntity itemEntity = new ItemEntity(level(), position.x, position.y, position.z, getItemStack().copy());
        itemEntity.setDeltaMovement(velocity);
        itemEntity.setPickUpDelay(10);
        level().addFreshEntity(itemEntity);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        setItemStack(ItemStack.parseOptional(level().registryAccess(), compound.getCompound("Item")));
        setBlocked(compound.getBoolean("Blocked"));
        mergeSpacingTicks = compound.getInt("MergeSpacingTicks");
        if (compound.contains("LastBeltX")) {
            lastBeltPos = new BlockPos(compound.getInt("LastBeltX"), compound.getInt("LastBeltY"), compound.getInt("LastBeltZ"));
        }
        Direction loadedFacing = Direction.byName(compound.getString("LastBeltFacing"));
        if (loadedFacing != null) {
            lastBeltFacing = loadedFacing;
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (!getItemStack().isEmpty()) {
            compound.put("Item", getItemStack().save(level().registryAccess()));
        }
        compound.putBoolean("Blocked", isBlocked());
        compound.putInt("MergeSpacingTicks", mergeSpacingTicks);
        if (lastBeltPos != null) {
            compound.putInt("LastBeltX", lastBeltPos.getX());
            compound.putInt("LastBeltY", lastBeltPos.getY());
            compound.putInt("LastBeltZ", lastBeltPos.getZ());
        }
        if (lastBeltFacing != null) {
            compound.putString("LastBeltFacing", lastBeltFacing.getName());
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = getItemStack();
        if (stack.isEmpty()) {
            discard();
            return InteractionResult.CONSUME;
        }

        ItemStack remainder = stack.copy();
        int before = remainder.getCount();
        boolean added = player.getInventory().add(remainder);
        if (added || remainder.getCount() != before) {
            if (remainder.isEmpty()) {
                discard();
            } else {
                setItemStack(remainder);
            }
            level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4F, 1.0F);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    private record BeltContext(BlockPos pos, ConveyorBeltSurface surface, net.minecraft.core.Direction facing) {
    }
}
