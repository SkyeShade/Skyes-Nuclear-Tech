package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.CoalForgeBlockEntity;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CoalForgeBlock extends BaseEntityBlock {
    public static final int MAX_LAYERS = 3;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 0, MAX_LAYERS);
    public static final EnumProperty<CoalForgeBedType> BED_TYPE = EnumProperty.create("bed_type", CoalForgeBedType.class);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final MapCodec<CoalForgeBlock> CODEC = simpleCodec(CoalForgeBlock::new);

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D),
            Block.box(0.0D, 8.0D, 0.0D, 16.0D, 16.0D, 2.0D),
            Block.box(0.0D, 8.0D, 14.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 8.0D, 2.0D, 2.0D, 16.0D, 14.0D),
            Block.box(14.0D, 8.0D, 2.0D, 16.0D, 16.0D, 14.0D)
    );

    public CoalForgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(LAYERS, 0)
                .setValue(BED_TYPE, CoalForgeBedType.EMPTY)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof CoalForgeBlockEntity forge) {
            if (stack.getItem() instanceof ShovelItem) {
                return useShovel(stack, state, level, pos, player, hand, forge);
            }

            if (stack.is(Items.FLINT_AND_STEEL)) {
                return lightForge(stack, state, level, pos, player, hand, forge);
            }

            if (forge.hasForgeableOutput()) {
                return extractForgeableOutput(level, pos, player, forge);
            }

            if (isFuel(stack)) {
                return addFuel(stack, state, level, player, forge);
            }

            if (HotItemUtil.isForgeableIngot(stack)) {
                return insertIngot(stack, level, player, forge);
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof CoalForgeBlockEntity forge)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack removed = forge.hasForgeableOutput() ? forge.removeForgeableOutput() : forge.removeLastIngot();
        if (removed.isEmpty()) {
            return InteractionResult.PASS;
        }

        giveOrDrop(level, pos, player, removed);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof CoalForgeBlockEntity forge) {
            forge.dropContents(level, pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }

        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.55D;
        double y = pos.getY() + 0.72D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.55D;
        if (random.nextDouble() < 0.12D) {
            level.playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.7F, 1.0F, false);
        }
        level.addParticle(ParticleTypes.SMOKE, x, y + 0.1D, z, 0.0D, 0.025D, 0.0D);
        if (random.nextBoolean()) {
            level.addParticle(ParticleTypes.FLAME, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoalForgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide) {
            return null;
        }

        return createTickerHelper(blockEntityType, ModBlockEntities.COAL_FORGE.get(), CoalForgeBlockEntity::serverTick);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LAYERS, BED_TYPE, LIT);
    }

    public static boolean isFuelBedAccepting(BlockState state) {
        CoalForgeBedType bedType = state.getValue(BED_TYPE);
        return (bedType == CoalForgeBedType.EMPTY || bedType == CoalForgeBedType.COAL) && state.getValue(LAYERS) < MAX_LAYERS;
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(Items.COAL) || stack.is(Items.CHARCOAL);
    }

    public static BlockState withBedState(BlockState state, CoalForgeBedType bedType, int layers, boolean lit) {
        int clampedLayers = Mth.clamp(layers, 0, MAX_LAYERS);
        CoalForgeBedType normalizedType = clampedLayers == 0 ? CoalForgeBedType.EMPTY : bedType;
        boolean normalizedLit = normalizedType == CoalForgeBedType.COAL && lit;
        return state.setValue(LAYERS, clampedLayers)
                .setValue(BED_TYPE, normalizedType)
                .setValue(LIT, normalizedLit);
    }

    private static ItemInteractionResult addFuel(ItemStack stack, BlockState state, Level level, Player player, CoalForgeBlockEntity forge) {
        if (!isFuelBedAccepting(state)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide && forge.addFuelLayer(stack)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, forge.getBlockPos(), SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 0.65F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult useShovel(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, CoalForgeBlockEntity forge) {
        CoalForgeBedType bedType = state.getValue(BED_TYPE);
        if (bedType != CoalForgeBedType.COAL && bedType != CoalForgeBedType.ASH || state.getValue(LAYERS) <= 0) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            boolean removed = bedType == CoalForgeBedType.COAL ? forge.removeCoalLayer() : forge.removeAshLayer();
            if (removed) {
                level.playSound(null, pos, bedType == CoalForgeBedType.COAL ? SoundEvents.GRAVEL_BREAK : SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.8F, 1.1F);
                if (!player.getAbilities().instabuild) {
                    stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
                }
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult lightForge(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, CoalForgeBlockEntity forge) {
        if (state.getValue(BED_TYPE) != CoalForgeBedType.COAL || state.getValue(LAYERS) <= 0 || state.getValue(LIT)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide && forge.light()) {
            level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, level.random.nextFloat() * 0.4F + 0.8F);
            if (!player.getAbilities().instabuild) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult extractForgeableOutput(Level level, BlockPos pos, Player player, CoalForgeBlockEntity forge) {
        if (!level.isClientSide) {
            ItemStack removed = forge.removeForgeableOutput();
            if (!removed.isEmpty()) {
                giveOrDrop(level, pos, player, removed);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static ItemInteractionResult insertIngot(ItemStack stack, Level level, Player player, CoalForgeBlockEntity forge) {
        if (!level.isClientSide && forge.insertIngot(stack)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, forge.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void giveOrDrop(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.8D, pos.getZ() + 0.5D, stack);
        }
    }
}
