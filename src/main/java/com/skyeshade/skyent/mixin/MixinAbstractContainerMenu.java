package com.skyeshade.skyent.mixin;

import com.skyeshade.skyent.content.item.SteelTongsItem;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class MixinAbstractContainerMenu {
    @Shadow
    public abstract ItemStack getCarried();

    @Shadow
    public abstract void setCarried(ItemStack stack);

    @Shadow
    public NonNullList<Slot> slots;

    @Shadow
    public abstract void broadcastChanges();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void skyent$handleSteelTongsClick(int slotId, int button, ClickType clickType, Player player, CallbackInfo ci) {
        if (slotId < 0 || slotId >= slots.size() || button != 1 || clickType != ClickType.PICKUP) {
            return;
        }

        ItemStack tongs = getCarried();
        if (!SteelTongsItem.isTongs(tongs)) {
            return;
        }

        Slot slot = slots.get(slotId);
        if (SteelTongsItem.hasHeldStack(tongs)) {
            if (placeFromTongs(tongs, slot, player)) {
                setCarried(tongs);
                broadcastChanges();
            }
            ci.cancel();
            return;
        }

        if (pickUpIntoTongs(tongs, slot, player)) {
            setCarried(tongs);
            broadcastChanges();
        }
        ci.cancel();
    }

    private static boolean pickUpIntoTongs(ItemStack tongs, Slot slot, Player player) {
        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty() || SteelTongsItem.isTongs(slotStack) || !slot.mayPickup(player)) {
            return false;
        }

        // Steel Tongs intentionally carry full stacks for usability.
        // Higher-tier tongs can be differentiated later by heat resistance/reach/durability rather than stack size.
        ItemStack removed = slot.remove(slotStack.getCount());
        if (removed.isEmpty()) {
            return false;
        }

        SteelTongsItem.setHeldStack(tongs, removed, player.registryAccess());
        slot.setChanged();
        return true;
    }

    private static boolean placeFromTongs(ItemStack tongs, Slot slot, Player player) {
        ItemStack held = SteelTongsItem.getHeldStack(tongs, player.registryAccess());
        if (held.isEmpty()) {
            SteelTongsItem.clearHeldStack(tongs);
            return true;
        }

        ItemStack slotStack = slot.getItem();
        if (slotStack.isEmpty()) {
            if (!slot.mayPlace(held)) {
                return false;
            }

            int moveCount = Math.min(held.getCount(), Math.min(held.getMaxStackSize(), slot.getMaxStackSize(held)));
            if (moveCount <= 0) {
                return false;
            }

            slot.set(held.copyWithCount(moveCount));
            held.shrink(moveCount);
            SteelTongsItem.setHeldStack(tongs, held, player.registryAccess());
            slot.setChanged();
            return true;
        }

        if (ItemStack.isSameItemSameComponents(slotStack, held)) {
            if (!slot.mayPlace(held)) {
                return false;
            }

            int capacity = Math.min(slotStack.getMaxStackSize(), slot.getMaxStackSize(held));
            int moveCount = Math.min(held.getCount(), capacity - slotStack.getCount());
            if (moveCount <= 0) {
                return false;
            }

            slotStack.grow(moveCount);
            held.shrink(moveCount);
            SteelTongsItem.setHeldStack(tongs, held, player.registryAccess());
            slot.setChanged();
            return true;
        }

        if (SteelTongsItem.isTongs(slotStack) || !slot.mayPickup(player) || !slot.mayPlace(held)) {
            return false;
        }

        int capacity = Math.min(held.getMaxStackSize(), slot.getMaxStackSize(held));
        if (held.getCount() > capacity) {
            return false;
        }

        ItemStack swappedOut = slotStack.copy();
        slot.set(held.copy());
        SteelTongsItem.setHeldStack(tongs, swappedOut, player.registryAccess());
        slot.setChanged();
        return true;
    }
}
