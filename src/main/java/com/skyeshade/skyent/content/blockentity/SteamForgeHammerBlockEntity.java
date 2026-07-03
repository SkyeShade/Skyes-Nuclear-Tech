package com.skyeshade.skyent.content.blockentity;

import com.skyeshade.skyent.content.block.SteamForgeHammerBlock;
import com.skyeshade.skyent.content.item.ForgingAnvilRecipes;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModFluids;
import com.skyeshade.skyent.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

public class SteamForgeHammerBlockEntity extends BlockEntity {
    public static final int STEAM_CAPACITY_MB = 16_000;
    public static final int STEAM_PER_STRIKE_MB = 100;
    public static final int REQUIRED_STRIKES = 3;
    public static final int MIN_TICKS_PER_STRIKE = 10;
    public static final int MAX_TICKS_PER_STRIKE = 20;
    public static final float PISTON_TRAVEL_BLOCKS = 14 / 16.0F;
    public static final float IMPACT_POINT = 0.5F;
    private static final int RETURN_STEAM_TICKS = 6;
    private static final int STEAM_PARTICLES_PER_TICK = 4;
    private static final double STEAM_VENT_BACK_OFFSET = 0.45D;
    private static final double STEAM_VENT_Y = 0.6D;

    private final FluidTank steamTank = new FluidTank(STEAM_CAPACITY_MB, stack -> stack.is(ModFluids.STEAM.get())) {
        @Override
        protected void onContentsChanged() {
            setChangedAndSync();
        }
    };
    private final IFluidHandler automationFluidHandler = new SteamInputFluidHandler();
    private ItemStack workStack = ItemStack.EMPTY;
    private int strikesDone;
    private int strokeTicks;
    private int strokeTicksMax = MAX_TICKS_PER_STRIKE;
    private int returnSteamTicks;
    private boolean active;
    private boolean finished;
    private boolean strokeActive;
    private boolean hasImpactedThisStroke;
    private boolean outputReady;

    public SteamForgeHammerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.STEAM_FORGE_HAMMER.get(), pos, blockState);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SteamForgeHammerBlockEntity hammer) {
        if (level.isClientSide) {
            hammer.clientTick();
        } else {
            hammer.serverTick(state);
        }
    }

    private void serverTick(BlockState state) {
        boolean needsSync = false;
        boolean wasActive = active;
        active = false;

        if (returnSteamTicks > 0) {
            emitReturnSteam(state);
            returnSteamTicks--;
            setChanged();
        }

        if (strokeActive) {
            active = true;
            strokeTicks++;
            if (!hasImpactedThisStroke && getStrokeNormalized(0.0F) >= IMPACT_POINT) {
                needsSync |= performImpact(state);
            }
            if (strokeTicks >= strokeTicksMax) {
                endStroke();
                needsSync = true;
            }
            setChanged();
        } else if (canProcess()) {
            startStroke();
            needsSync = true;
        } else if (!hasWork() || finished || !HotItemUtil.isForgeReady(workStack)) {
            resetStrokeState();
        }

        if (wasActive != active) {
            needsSync = true;
        }
        if (needsSync) {
            setChangedAndSync();
        }
    }

    private void clientTick() {
        if (!strokeActive) {
            return;
        }
        strokeTicks++;
        if (strokeTicks >= strokeTicksMax) {
            strokeActive = false;
            strokeTicks = 0;
            hasImpactedThisStroke = false;
        }
    }

    public boolean hasWork() {
        return !workStack.isEmpty();
    }

    public ItemStack getWorkStack() {
        return workStack;
    }

    public int getStrikesDone() {
        return strikesDone;
    }

    public int getStrikeProgress() {
        return strokeTicks;
    }

    public int getCurrentTicksPerStrike() {
        double fillRatio = steamTank.getFluidAmount() / (double) STEAM_CAPACITY_MB;
        return Mth.clamp((int) Math.round(Mth.lerp(fillRatio, MAX_TICKS_PER_STRIKE, MIN_TICKS_PER_STRIKE)),
                MIN_TICKS_PER_STRIKE,
                MAX_TICKS_PER_STRIKE);
    }

    public float getPistonOffset(float partialTick) {
        if (!strokeActive || strokeTicksMax <= 0) {
            return 0.0F;
        }

        float normalized = getStrokeNormalized(partialTick);
        if (normalized <= IMPACT_POINT) {
            return normalized / IMPACT_POINT * PISTON_TRAVEL_BLOCKS;
        }
        return (1.0F - (normalized - IMPACT_POINT) / IMPACT_POINT) * PISTON_TRAVEL_BLOCKS;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isFinished() {
        return outputReady && hasWork();
    }

    public boolean shouldRenderPlateForgeStages() {
        return !finished && ForgingAnvilRecipes.isForgeablePlateInput(workStack) && HotItemUtil.isForgeReady(workStack);
    }

    public boolean isInteractionLocked() {
        return strokeActive || active || returnSteamTicks > 0 || hasWork() && strikesDone > 0 && !outputReady;
    }

    public IFluidHandler getAutomationFluidHandler() {
        return automationFluidHandler;
    }

    public FluidTank getSteamTank() {
        return steamTank;
    }

    public int getSteamAmount() {
        return steamTank.getFluidAmount();
    }

    public int getSteamCapacity() {
        return STEAM_CAPACITY_MB;
    }

    public boolean canPlaceWork(ItemStack stack) {
        return !hasWork()
                && ForgingAnvilRecipes.isForgeablePlateInput(stack) && HotItemUtil.isForgeReady(stack);
    }

    public void setWorkInput(ItemStack stack) {
        workStack = stack.copyWithCount(1);
        strikesDone = 0;
        returnSteamTicks = 0;
        active = false;
        finished = false;
        outputReady = false;
        resetStrokeState();
        setChangedAndSync();
    }

    public ItemStack removeWorkStack() {
        ItemStack removed = workStack.copy();
        clearWork();
        return removed;
    }

    public void clearWork() {
        workStack = ItemStack.EMPTY;
        strikesDone = 0;
        returnSteamTicks = 0;
        active = false;
        finished = false;
        outputReady = false;
        resetStrokeState();
        setChangedAndSync();
    }

    private boolean canProcess() {
        return hasWork()
                && !finished
                && strikesDone < REQUIRED_STRIKES
                && steamTank.getFluidAmount() >= STEAM_PER_STRIKE_MB
                && ForgingAnvilRecipes.isForgeablePlateInput(workStack) && HotItemUtil.isForgeReady(workStack);
    }

    private void startStroke() {
        strokeActive = true;
        strokeTicks = 0;
        strokeTicksMax = getCurrentTicksPerStrike();
        hasImpactedThisStroke = false;
        active = true;
        setChangedAndSync();
    }

    private boolean performImpact(BlockState state) {
        if (steamTank.getFluidAmount() < STEAM_PER_STRIKE_MB) {
            resetStrokeState();
            return true;
        }

        FluidStack drained = steamTank.drain(STEAM_PER_STRIKE_MB, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() < STEAM_PER_STRIKE_MB) {
            resetStrokeState();
            return true;
        }

        hasImpactedThisStroke = true;
        strikesDone++;
        returnSteamTicks = RETURN_STEAM_TICKS;
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.55F, 1.35F);
            level.playSound(null, worldPosition, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.65F, strikesDone >= REQUIRED_STRIKES ? 1.05F : 1.25F);
        }

        if (strikesDone >= REQUIRED_STRIKES) {
            ItemStack output = getMachineOutput(workStack);
            workStack = output;
            strikesDone = REQUIRED_STRIKES;
            finished = true;
            outputReady = false;
        }
        return true;
    }



    private static ItemStack getMachineOutput(ItemStack stack) {

        ItemStack output = ForgingAnvilRecipes.getPlateOutput(stack).orElse(ItemStack.EMPTY);
        HotItemUtil.clearTemperature(output);
        return output;
    }

    private void endStroke() {
        strokeActive = false;
        strokeTicks = 0;
        hasImpactedThisStroke = false;
        active = false;

        if (finished && hasWork()) {
            outputReady = true;
        }
    }

    private void resetStrokeState() {
        strokeActive = false;
        strokeTicks = 0;
        strokeTicksMax = MAX_TICKS_PER_STRIKE;
        hasImpactedThisStroke = false;
        active = false;
    }

    private float getStrokeNormalized(float partialTick) {
        if (strokeTicksMax <= 0) {
            return 0.0F;
        }
        return Mth.clamp((strokeTicks + partialTick) / (float) strokeTicksMax, 0.0F, 1.0F);
    }

    private void emitReturnSteam(BlockState state) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Direction facing = state.hasProperty(SteamForgeHammerBlock.FACING) ? state.getValue(SteamForgeHammerBlock.FACING) : Direction.NORTH;
        Direction back = facing.getOpposite();
        BlockPos topPos = worldPosition.above(2);
        double x = topPos.getX() + 0.5D + back.getStepX() * STEAM_VENT_BACK_OFFSET;
        double y = topPos.getY() + STEAM_VENT_Y;
        double z = topPos.getZ() + 0.5D + back.getStepZ() * STEAM_VENT_BACK_OFFSET;
        for (int i = 0; i < STEAM_PARTICLES_PER_TICK; i++) {
            double speed = 0.04D + level.random.nextDouble() * 0.03D;
            serverLevel.sendParticles(
                    ParticleTypes.CLOUD,
                    x,
                    y,
                    z,
                    1,
                    0.04D,
                    0.03D,
                    0.04D,
                    speed
            );
        }
    }

    public void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("SteamTank", steamTank.writeToNBT(registries, new CompoundTag()));
        if (!workStack.isEmpty()) {
            tag.put("WorkStack", workStack.save(registries));
        }
        tag.putInt("StrikesDone", strikesDone);
        tag.putInt("StrokeTicks", strokeTicks);
        tag.putInt("StrokeTicksMax", strokeTicksMax);
        tag.putInt("ReturnSteamTicks", returnSteamTicks);
        tag.putBoolean("Active", active);
        tag.putBoolean("Finished", finished);
        tag.putBoolean("StrokeActive", strokeActive);
        tag.putBoolean("HasImpactedThisStroke", hasImpactedThisStroke);
        tag.putBoolean("OutputReady", outputReady);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        steamTank.readFromNBT(registries, tag.getCompound("SteamTank"));
        workStack = tag.contains("WorkStack") ? ItemStack.parseOptional(registries, tag.getCompound("WorkStack")) : ItemStack.EMPTY;
        strikesDone = Mth.clamp(tag.getInt("StrikesDone"), 0, REQUIRED_STRIKES);
        strokeTicks = Math.max(0, tag.contains("StrokeTicks") ? tag.getInt("StrokeTicks") : tag.getInt("StrikeProgress"));
        strokeTicksMax = Mth.clamp(tag.getInt("StrokeTicksMax"), MIN_TICKS_PER_STRIKE, MAX_TICKS_PER_STRIKE);
        returnSteamTicks = Math.max(0, tag.getInt("ReturnSteamTicks"));
        active = tag.getBoolean("Active");
        finished = tag.getBoolean("Finished") && !workStack.isEmpty();
        strokeActive = tag.getBoolean("StrokeActive");
        hasImpactedThisStroke = tag.getBoolean("HasImpactedThisStroke");
        outputReady = tag.getBoolean("OutputReady") && finished && !workStack.isEmpty();
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

    private final class SteamInputFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return steamTank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return steamTank.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return steamTank.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return steamTank.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return steamTank.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }
}
