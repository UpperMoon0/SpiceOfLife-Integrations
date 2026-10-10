package dev.uppermoon.solintegrations.integration.backpack;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedItemHandlerTest {
    @Test
    void mapsReadsAndMetadataToPhysicalSlotsWithoutMovingInventory() {
        RecordingHandler backing = new RecordingHandler(3);
        OrderedItemHandler view = new OrderedItemHandler(backing, new int[]{2, 0, 1});

        view.getStackInSlot(0);
        view.getStackInSlot(1);
        assertEquals(List.of(2, 0), backing.reads);
        assertEquals(102, view.getSlotLimit(0));
        assertTrue(view.isItemValid(0, null));
        assertEquals(2, backing.validitySlot);
        assertEquals(3, view.getSlots());
        assertEquals(0, backing.writes);
    }

    @Test
    void iteratorAndItsPhysicalCallbackStayConsistent() {
        RecordingHandler backing = new RecordingHandler(3);
        OrderedItemHandler view = new OrderedItemHandler(backing, new int[]{2, 0, 1});
        List<Integer> physicalAttempts = new ArrayList<>();
        // Same public IItemHandler/BiFunction traversal contract as Core's
        // original iterator; the callback closes over the real inventory.
        for (int slot = 0; slot < view.getSlots(); slot++) {
            view.getStackInSlot(slot);
            int physicalSlot = view.physicalSlot(slot);
            physicalAttempts.add(physicalSlot);
            if (physicalSlot == 0) {
                break;
            }
        }
        assertEquals(List.of(2, 0), backing.reads);
        assertEquals(backing.reads, physicalAttempts);
    }

    @Test
    void viewNeverPermitsMutatingTheBackingInventory() {
        RecordingHandler backing = new RecordingHandler(1);
        OrderedItemHandler view = new OrderedItemHandler(backing, new int[]{0});
        assertThrows(UnsupportedOperationException.class, () -> view.insertItem(0, null, false));
        assertThrows(UnsupportedOperationException.class, () -> view.extractItem(0, 1, false));
        assertThrows(UnsupportedOperationException.class, () -> view.insertItem(0, null, true));
        assertThrows(UnsupportedOperationException.class, () -> view.extractItem(0, 1, true));
        assertEquals(0, backing.writes);
    }

    @Test
    void retainsItsOwnValidatedOrderSnapshot() {
        int[] order = {1, 0};
        OrderedItemHandler view = new OrderedItemHandler(new RecordingHandler(2), order);
        order[0] = 0;
        assertEquals(1, view.physicalSlot(0));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedItemHandler(new RecordingHandler(2), new int[]{0}));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedItemHandler(new RecordingHandler(2), new int[]{0, 0}));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedItemHandler(new RecordingHandler(2), new int[]{0, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> new OrderedItemHandler(new RecordingHandler(2), new int[]{0, -1}));
    }

    /** Null stack sentinels avoid initializing Minecraft registries in a unit test. */
    private static final class RecordingHandler implements IItemHandler {
        final int slots;
        final List<Integer> reads = new ArrayList<>();
        int writes;
        int validitySlot = -1;

        RecordingHandler(int slots) {
            this.slots = slots;
        }

        @Override
        public int getSlots() { return slots; }

        @Override
        public ItemStack getStackInSlot(int slot) {
            reads.add(slot);
            return null;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            writes++;
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            writes++;
            return null;
        }

        @Override
        public int getSlotLimit(int slot) { return 100 + slot; }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            validitySlot = slot;
            return true;
        }
    }
}
