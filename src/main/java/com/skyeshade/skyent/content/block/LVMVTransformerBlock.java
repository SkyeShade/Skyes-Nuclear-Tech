package com.skyeshade.skyent.content.block;

import com.mojang.serialization.MapCodec;
import com.skyeshade.skyent.content.blockentity.LVMVTransformerBlockEntity;
import com.skyeshade.skyent.content.blockentity.LVConnectorBlockEntity;
import com.skyeshade.skyent.content.item.WrenchUtil;
import com.skyeshade.skyent.content.item.LVWireDrumItem;
import com.skyeshade.skyent.registry.ModBlockEntities;
import com.skyeshade.skyent.registry.ModBlocks;
import com.skyeshade.skyent.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LVMVTransformerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final MapCodec<LVMVTransformerBlock> CODEC = simpleCodec(LVMVTransformerBlock::new);
    private static final ThreadLocal<Boolean> REMOVING = ThreadLocal.withInitial(() -> false);
    // Generated from mv_transformer.json with the scaled-block transform
    // scale=2.0, origin=[8,0,8], translation=[0,0,8], sliced into the 1x2x2 footprint.
    private static final VoxelShape LOWER_FRONT_NORTH_SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 4.0D, 16.0D, 2.0D, 12.0D),
            Block.box(1.0D, 2.0D, 3.0D, 15.0D, 16.0D, 16.0D),
            Block.box(0.0D, 4.0D, 4.0D, 1.0D, 12.0D, 12.0D),
            Block.box(0.0D, 4.0D, 12.0D, 1.0D, 12.0D, 16.0D),
            Block.box(15.0D, 4.0D, 12.0D, 16.0D, 12.0D, 16.0D),
            Block.box(15.0D, 4.0D, 4.0D, 16.0D, 12.0D, 12.0D),
            Block.box(4.0D, 4.0D, 0.0D, 12.0D, 12.0D, 3.0D),
            Block.box(2.4D, 14.0D, 0.0D, 3.4D, 16.0D, 3.0D),
            Block.box(4.4D, 14.0D, 0.0D, 5.4D, 16.0D, 3.0D),
            Block.box(6.4D, 14.0D, 0.0D, 7.4D, 16.0D, 3.0D),
            Block.box(8.4D, 14.0D, 0.0D, 9.4D, 16.0D, 3.0D),
            Block.box(10.6D, 14.0D, 0.0D, 11.6D, 16.0D, 3.0D),
            Block.box(12.6D, 14.0D, 0.0D, 13.6D, 16.0D, 3.0D)
    );
    private static final VoxelShape LOWER_REAR_NORTH_SHAPE = Shapes.or(
            Block.box(1.0D, 2.0D, 0.0D, 15.0D, 16.0D, 13.0D),
            Block.box(0.0D, 4.0D, 0.0D, 1.0D, 12.0D, 4.0D),
            Block.box(0.0D, 4.0D, 4.0D, 1.0D, 12.0D, 12.0D),
            Block.box(15.0D, 4.0D, 4.0D, 16.0D, 12.0D, 12.0D),
            Block.box(15.0D, 4.0D, 0.0D, 16.0D, 12.0D, 4.0D),
            Block.box(4.0D, 4.0D, 13.0D, 12.0D, 12.0D, 16.0D),
            Block.box(12.6D, 14.0D, 13.0D, 13.6D, 16.0D, 16.0D),
            Block.box(10.6D, 14.0D, 13.0D, 11.6D, 16.0D, 16.0D),
            Block.box(8.4D, 14.0D, 13.0D, 9.4D, 16.0D, 16.0D),
            Block.box(6.4D, 14.0D, 13.0D, 7.4D, 16.0D, 16.0D),
            Block.box(4.4D, 14.0D, 13.0D, 5.4D, 16.0D, 16.0D),
            Block.box(2.4D, 14.0D, 13.0D, 3.4D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 4.0D, 16.0D, 2.0D, 12.0D)
    );
    private static final VoxelShape UPPER_FRONT_NORTH_SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 3.0D, 15.0D, 6.0D, 16.0D),
            Block.box(0.0D, 6.0D, 2.0D, 16.0D, 7.0D, 16.0D),
            Block.box(4.0D, 7.0D, 4.0D, 12.0D, 8.0D, 12.0D),
            Block.box(6.0D, 7.0D, 12.0D, 10.0D, 8.0D, 16.0D),
            Block.box(2.4D, 0.0D, 0.0D, 3.4D, 4.0D, 3.0D),
            Block.box(4.4D, 0.0D, 0.0D, 5.4D, 4.0D, 3.0D),
            Block.box(6.4D, 0.0D, 0.0D, 7.4D, 4.0D, 3.0D),
            Block.box(8.4D, 0.0D, 0.0D, 9.4D, 4.0D, 3.0D),
            Block.box(10.6D, 0.0D, 0.0D, 11.6D, 4.0D, 3.0D),
            Block.box(12.6D, 0.0D, 0.0D, 13.6D, 4.0D, 3.0D),
            Block.box(5.0D, 13.0D, 5.0D, 11.0D, 14.0D, 11.0D),
            Block.box(5.0D, 10.0D, 5.0D, 11.0D, 12.0D, 11.0D),
            Block.box(5.0D, 7.0D, 5.0D, 11.0D, 9.0D, 11.0D),
            Block.box(6.0D, 9.0D, 6.0D, 10.0D, 15.0D, 10.0D),
            Block.box(6.5D, 15.0D, 6.5D, 9.5D, 16.0D, 9.5D)
    );
    private static final VoxelShape UPPER_REAR_NORTH_SHAPE = Shapes.or(
            Block.box(1.0D, 0.0D, 0.0D, 15.0D, 6.0D, 13.0D),
            Block.box(0.0D, 6.0D, 0.0D, 16.0D, 7.0D, 14.0D),
            Block.box(4.0D, 7.0D, 4.0D, 12.0D, 8.0D, 12.0D),
            Block.box(6.0D, 7.0D, 0.0D, 10.0D, 8.0D, 4.0D),
            Block.box(12.6D, 0.0D, 13.0D, 13.6D, 4.0D, 16.0D),
            Block.box(10.6D, 0.0D, 13.0D, 11.6D, 4.0D, 16.0D),
            Block.box(8.4D, 0.0D, 13.0D, 9.4D, 4.0D, 16.0D),
            Block.box(6.4D, 0.0D, 13.0D, 7.4D, 4.0D, 16.0D),
            Block.box(4.4D, 0.0D, 13.0D, 5.4D, 4.0D, 16.0D),
            Block.box(2.4D, 0.0D, 13.0D, 3.4D, 4.0D, 16.0D),
            Block.box(5.0D, 13.0D, 5.0D, 11.0D, 14.0D, 11.0D),
            Block.box(5.0D, 10.0D, 5.0D, 11.0D, 12.0D, 11.0D),
            Block.box(5.0D, 7.0D, 5.0D, 11.0D, 9.0D, 11.0D),
            Block.box(6.0D, 9.0D, 6.0D, 10.0D, 15.0D, 10.0D),
            Block.box(6.5D, 15.0D, 6.5D, 9.5D, 16.0D, 9.5D)
    );
    private static final VoxelShape[][] LOCAL_SHAPES = {
            shapesByFacing(LOWER_FRONT_NORTH_SHAPE),
            shapesByFacing(LOWER_REAR_NORTH_SHAPE),
            shapesByFacing(UPPER_FRONT_NORTH_SHAPE),
            shapesByFacing(UPPER_REAR_NORTH_SHAPE)
    };

    public LVMVTransformerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LVMVTransformerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide
                ? createTickerHelper(blockEntityType, ModBlockEntities.LV_MV_TRANSFORMER.get(), LVMVTransformerBlockEntity::clientTick)
                : createTickerHelper(blockEntityType, ModBlockEntities.LV_MV_TRANSFORMER.get(), LVMVTransformerBlockEntity::serverTick);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getCounterClockWise();
        BlockPos origin = context.getClickedPos();
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 0; x++) {
                for (int z = 0; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    if (!canPlacePartAt(context, localToWorld(origin, facing, x, y, z))) {
                        return null;
                    }
                }
            }
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }

        Direction facing = state.getValue(FACING);
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 0; x++) {
                for (int z = 0; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    level.setBlock(localToWorld(pos, facing, x, y, z), ModBlocks.LV_MV_TRANSFORMER_PART.get().defaultBlockState()
                            .setValue(LVMVTransformerPartBlock.FACING, facing)
                            .setValue(LVMVTransformerPartBlock.PART_X, x)
                            .setValue(LVMVTransformerPartBlock.PART_Y, y)
                            .setValue(LVMVTransformerPartBlock.PART_Z, z), Block.UPDATE_ALL);
                }
            }
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !REMOVING.get()) {
            removeWholeMachine(level, pos, !player.isCreative());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && !REMOVING.get()) {
            removeParts(level, pos, state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForLocal(0, 0, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeForLocal(0, 0, state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!WrenchUtil.isWrench(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return toggleMode(level, pos, player).consumesAction()
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.LV_MV_TRANSFORMER.get());
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

    public static void removeWholeMachine(Level level, BlockPos masterPos, boolean drop) {
        if (REMOVING.get()) {
            return;
        }

        REMOVING.set(true);
        try {
            BlockState state = level.getBlockState(masterPos);
            Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
            spawnDestroyParticles(level, masterPos, facing);
            removeTerminalConnections(level, masterPos, facing);
            removeParts(level, masterPos, facing);
            if (level.getBlockState(masterPos).is(ModBlocks.LV_MV_TRANSFORMER.get())) {
                level.setBlock(masterPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (drop) {
                Containers.dropItemStack(level, masterPos.getX() + 0.5D, masterPos.getY() + 0.5D, masterPos.getZ() + 0.5D,
                        new ItemStack(ModItems.LV_MV_TRANSFORMER.get()));
            }
        } finally {
            REMOVING.set(false);
        }
    }

    public static BlockPos getMasterPos(BlockState state, BlockPos pos) {
        if (state.is(ModBlocks.LV_MV_TRANSFORMER.get())) {
            return pos;
        }
        if (state.is(ModBlocks.LV_MV_TRANSFORMER_PART.get())
                && state.hasProperty(LVMVTransformerPartBlock.FACING)
                && state.hasProperty(LVMVTransformerPartBlock.PART_X)
                && state.hasProperty(LVMVTransformerPartBlock.PART_Y)
                && state.hasProperty(LVMVTransformerPartBlock.PART_Z)) {
            BlockPos local = new BlockPos(
                    state.getValue(LVMVTransformerPartBlock.PART_X),
                    state.getValue(LVMVTransformerPartBlock.PART_Y),
                    state.getValue(LVMVTransformerPartBlock.PART_Z)
            );
            return pos.subtract(rotateLocalOffset(local, state.getValue(LVMVTransformerPartBlock.FACING)));
        }
        return pos;
    }

    public static BlockPos resolveDestroyProgressPos(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.is(ModBlocks.LV_MV_TRANSFORMER_PART.get())) {
            BlockPos masterPos = getMasterPos(state, pos);
            if (level.getBlockState(masterPos).is(ModBlocks.LV_MV_TRANSFORMER.get())) {
                return masterPos;
            }
        }
        return pos;
    }

    public static InteractionResult toggleMode(Level level, BlockPos masterPos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer) {
            transformer.toggleMode(player);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public static boolean isMVTerminal(BlockState state) {
        return state.is(ModBlocks.LV_MV_TRANSFORMER_PART.get())
                && state.hasProperty(LVMVTransformerPartBlock.PART_Y)
                && state.hasProperty(LVMVTransformerPartBlock.PART_Z)
                && state.getValue(LVMVTransformerPartBlock.PART_Y) == 1
                && (state.getValue(LVMVTransformerPartBlock.PART_Z) == 0 || state.getValue(LVMVTransformerPartBlock.PART_Z) == 1);
    }

    public static boolean isConnectorSupportCell(BlockState state) {
        if (state.is(ModBlocks.LV_MV_TRANSFORMER.get())) {
            return true;
        }
        return state.is(ModBlocks.LV_MV_TRANSFORMER_PART.get())
                && state.hasProperty(LVMVTransformerPartBlock.PART_Y)
                && state.getValue(LVMVTransformerPartBlock.PART_Y) == 0;
    }

    public static Vec3 mvTerminalAnchor(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5D, pos.getY() + 15.0D / 16.0D, pos.getZ() + 0.5D);
    }

    public static BlockPos localToWorld(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.offset(rotateLocalOffset(new BlockPos(x, y, z), facing));
    }

    public static BlockPos rotateLocalOffset(BlockPos local, Direction facing) {
        int x = local.getX();
        int y = local.getY();
        int z = local.getZ();
        return switch (facing) {
            case NORTH -> new BlockPos(x, y, z);
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> local;
        };
    }

    private static void spawnDestroyParticles(Level level, BlockPos masterPos, Direction facing) {
        BlockState visualState = ModBlocks.LV_MV_TRANSFORMER.get().defaultBlockState().setValue(FACING, facing);
        int visualStateId = Block.getId(visualState);
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 0; x++) {
                for (int z = 0; z <= 1; z++) {
                    level.levelEvent(2001, localToWorld(masterPos, facing, x, y, z), visualStateId);
                }
            }
        }
    }

    private static void removeParts(Level level, BlockPos masterPos, Direction facing) {
        for (int y = 0; y <= 1; y++) {
            for (int x = 0; x <= 0; x++) {
                for (int z = 0; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos partPos = localToWorld(masterPos, facing, x, y, z);
                    if (level.getBlockState(partPos).is(ModBlocks.LV_MV_TRANSFORMER_PART.get())) {
                        level.setBlock(partPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    private static void removeTerminalConnections(Level level, BlockPos masterPos, Direction facing) {
        if (level.getBlockEntity(masterPos) instanceof LVMVTransformerBlockEntity transformer) {
            for (LVMVTransformerBlockEntity.TerminalConnection connection : transformer.terminalConnections()) {
                BlockPos connectedPos = connection.connectionPos();
                if (level.getBlockEntity(connectedPos) instanceof LVConnectorBlockEntity connector) {
                    connector.removeConnection(connection.terminalPos());
                    continue;
                }

                BlockState connectedState = level.getBlockState(connectedPos);
                if (isMVTerminal(connectedState)) {
                    BlockPos connectedMaster = getMasterPos(connectedState, connectedPos);
                    if (level.getBlockEntity(connectedMaster) instanceof LVMVTransformerBlockEntity connectedTransformer) {
                        connectedTransformer.removeTerminalConnection(connectedPos, connection.terminalPos());
                    }
                }
            }
            transformer.removeAllTerminalConnections();
        }

        for (int z = 0; z <= 1; z++) {
            BlockPos terminalPos = localToWorld(masterPos, facing, 0, 1, z);
            int radius = LVWireDrumItem.MAX_CONNECTION_DISTANCE;
            for (BlockPos scanPos : BlockPos.betweenClosed(
                    terminalPos.offset(-radius, -radius, -radius),
                    terminalPos.offset(radius, radius, radius))) {
                if (level.getBlockEntity(scanPos) instanceof LVConnectorBlockEntity connector) {
                    connector.removeConnection(terminalPos);
                }
            }
        }
    }

    private static boolean canPlacePartAt(BlockPlaceContext context, BlockPos pos) {
        BlockState state = context.getLevel().getBlockState(pos);
        return state.canBeReplaced(context);
    }

    public static VoxelShape shapeForLocal(int y, int z, Direction facing) {
        int index = Math.max(0, Math.min(1, y)) * 2 + Math.max(0, Math.min(1, z));
        return shapeForFacing(LOCAL_SHAPES[index], facing);
    }

    private static VoxelShape[] shapesByFacing(VoxelShape northShape) {
        return new VoxelShape[] {
                northShape,
                rotateShape(northShape, Direction.EAST),
                rotateShape(northShape, Direction.SOUTH),
                rotateShape(northShape, Direction.WEST)
        };
    }

    private static VoxelShape shapeForFacing(VoxelShape[] shapes, Direction facing) {
        return switch (facing) {
            case EAST -> shapes[1];
            case SOUTH -> shapes[2];
            case WEST -> shapes[3];
            default -> shapes[0];
        };
    }

    private static VoxelShape rotateShape(VoxelShape shape, Direction facing) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> {
            VoxelShape box = switch (facing) {
                case EAST -> Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX);
                case SOUTH -> Shapes.box(1.0D - maxX, minY, 1.0D - maxZ, 1.0D - minX, maxY, 1.0D - minZ);
                case WEST -> Shapes.box(minZ, minY, 1.0D - maxX, maxZ, maxY, 1.0D - minX);
                default -> Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
            };
            rotated[0] = Shapes.or(rotated[0], box);
        });
        return rotated[0];
    }
}
