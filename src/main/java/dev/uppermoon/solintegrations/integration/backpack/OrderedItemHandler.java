package dev.uppermoon.solintegrations.integration.backpack;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.Objects;

/**
 * Read-only traversal view. No stacks move in the backing inventory, and the
 * feeding callback must translate view indices with {@link #physicalSlot(int)}.
 * Actual consumption continues through the callback's original storage handler.
 */
public final class OrderedItemHandler implements IItemHandler {
    private final IItemHandler backing;
    private final int[] order;

    public OrderedItemHandler(IItemHandler backing, int[] order) {
        this.backing = Objects.requireNonNull(backing, "backing");
        this.order = order.clone();
        if (order.length != backing.getSlots()) {
            throw new IllegalArgumentException("Order must contain every inventory slot");
        }
        boolean[] seen = new boolean[order.length];
        for (int slot : this.order) {
            if (slot < 0 || slot >= seen.length || seen[slot]) {
                throw new IllegalArgumentException("Order must be a permutation of inventory slots");
            }
            seen[slot] = true;
        }
    }

    public int physicalSlot(int viewSlot) {
        return order[viewSlot];
    }

    @Override
    public int getSlots() {
        return order.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return backing.getStackInSlot(physicalSlot(slot));
    }

    @Override
    public int getSlotLimit(int slot) {
        return backing.getSlotLimit(physicalSlot(slot));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return backing.isItemValid(physicalSlot(slot), stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        throw new UnsupportedOperationException("The feeding traversal view is read-only");
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        throw new UnsupportedOperationException("The feeding traversal view is read-only");
    }
}
