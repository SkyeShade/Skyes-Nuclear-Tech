package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.ForgingAnvilBlockEntity;
import com.skyeshade.skyent.content.item.ForgingAnvilRecipes;
import com.skyeshade.skyent.content.item.HotItemUtil;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ForgingAnvilBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<ForgingAnvilBlock> CODEC = simpleCodec(ForgingAnvilBlock::new);
    private static final int MANUAL_PLATE_REQUIRED_HITS = 6;
    private static final int MANUAL_STAGE_1_HIT = 2;
    private static final int MANUAL_STAGE_2_HIT = 4;
    private static final VoxelShape SHAPE_NORTH = createShape(Direction.NORTH);
    private static final VoxelShape SHAPE_SOUTH = createShape(Direction.SOUTH);
    private static final VoxelShape SHAPE_WEST = createShape(Direction.WEST);
    private static final VoxelShape SHAPE_EAST = createShape(Direction.EAST);

    public ForgingAnvilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // The anvil model is intentionally placed sideways relative to the player.
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getClockWise());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForFacing(state.getValue(FACING));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (anvil.isFinished()) {
            if (stack.is(ModItems.FORGING_HAMMER.get())) {
                collectOutput(level, pos, player, anvil);
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.is(ModItems.FORGING_HAMMER.get()) && anvil.hasInput()) {
            return strike(level, pos, player, hand, anvil);
        }

        if (stack.is(ModItems.FORGING_HAMMER.get()) && hand == InteractionHand.MAIN_HAND && !anvil.hasInput()) {
            ItemStack offhandStack = player.getOffhandItem();
            if (ForgingAnvilRecipes.isAnvilInput(offhandStack)) {
                return placeInput(level, pos, player, anvil, offhandStack);
            }
        }

        if (!anvil.hasInput() && ForgingAnvilRecipes.isAnvilInput(stack)) {
            return placeInput(level, pos, player, anvil, stack);
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil) || !anvil.hasInput()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide) {
            collectOutput(level, pos, player, anvil);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof ForgingAnvilBlockEntity anvil) {
            ItemStack input = anvil.getInput();
            if (!input.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D, input.copy());
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ForgingAnvilBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return null;
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
        builder.add(FACING);
    }

    private static ItemInteractionResult strike(Level level, BlockPos pos, Player player, InteractionHand hand, ForgingAnvilBlockEntity anvil) {
        ItemStack input = anvil.getInput();
        ItemStack powderOutput = ForgingAnvilRecipes.getPowderOutput(input).orElse(ItemStack.EMPTY);
        if (!powderOutput.isEmpty()) {
            if (!level.isClientSide) {
                anvil.setFinishedOutput(powderOutput);
                damageHammer(player, hand);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, 4, 0.18D, 0.05D, 0.18D, 0.02D);
                }
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.75F, 1.35F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        ItemStack coldBoltOutput = ForgingAnvilRecipes.getColdBoltOutput(input).orElse(ItemStack.EMPTY);
        if (!coldBoltOutput.isEmpty() && !HotItemUtil.isForgeReady(input)) {
            if (!level.isClientSide) {
                HotItemUtil.clearTemperature(coldBoltOutput);
                anvil.setFinishedOutput(coldBoltOutput);
                damageHammer(player, hand);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, 4, 0.18D, 0.05D, 0.18D, 0.02D);
                }
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.75F, 1.35F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!ForgingAnvilRecipes.isForgeablePlateInput(input) || !HotItemUtil.isForgeReady(input)) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.skyent.too_cold_to_forge"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            int strikes = anvil.incrementStrikes();
            damageHammer(player, hand);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, 4, 0.18D, 0.05D, 0.18D, 0.02D);
            }
            if (strikes >= MANUAL_PLATE_REQUIRED_HITS) {
                ItemStack output = ForgingAnvilRecipes.getPlateOutput(input).orElse(ItemStack.EMPTY);
                HotItemUtil.clearTemperature(output);
                anvil.setFinishedOutput(output);
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.9F, 1.15F);
            } else {
                level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.65F, 1.35F);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void damageHammer(Player player, InteractionHand hand) {
        ItemStack hammer = player.getItemInHand(hand);
        if (hammer.is(ModItems.FORGING_HAMMER.get())) {
            hammer.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
    }

    private static ItemInteractionResult placeInput(Level level, BlockPos pos, Player player, ForgingAnvilBlockEntity anvil, ItemStack stack) {
        if (ForgingAnvilRecipes.isForgeablePlateInput(stack)
                && !ForgingAnvilRecipes.isColdBoltInput(stack)
                && !HotItemUtil.isForgeReady(stack)) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.skyent.too_cold_to_forge"), true);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!level.isClientSide) {
            ItemStack inserted = stack.copyWithCount(1);
            anvil.setInput(inserted);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void collectOutput(Level level, BlockPos pos, Player player, ForgingAnvilBlockEntity anvil) {
        if (level.isClientSide) {
            return;
        }
        ItemStack removed = anvil.removeInput();
        giveOrDrop(level, pos, player, removed);
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    private static void giveOrDrop(Level level, BlockPos pos, Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack) || !stack.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 1.05D, pos.getZ() + 0.5D, stack);
        }
    }

    private static VoxelShape shapeForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    private static VoxelShape createShape(Direction facing) {
        return Shapes.or(
                box(facing, 2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 15.0D),
                box(facing, 3.0D, 3.0D, 3.0D, 13.0D, 5.0D, 14.0D),
                box(facing, 6.0D, 5.0D, 5.0D, 10.0D, 9.0D, 13.0D),
                box(facing, 5.0D, 9.0D, 4.0D, 11.0D, 11.0D, 14.0D),
                box(facing, 4.0D, 12.0D, 0.0D, 12.0D, 16.0D, 4.0D),
                box(facing, 3.0D, 11.0D, 4.0D, 13.0D, 16.0D, 16.0D)
        );
    }

    private static VoxelShape box(Direction facing, double x1, double y1, double z1, double x2, double y2, double z2) {
        return switch (facing) {
            case SOUTH -> Block.box(16.0D - x2, y1, 16.0D - z2, 16.0D - x1, y2, 16.0D - z1);
            case EAST -> Block.box(16.0D - z2, y1, x1, 16.0D - z1, y2, x2);
            case WEST -> Block.box(z1, y1, 16.0D - x2, z2, y2, 16.0D - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
