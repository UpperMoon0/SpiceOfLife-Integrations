package dev.uppermoon.solintegrations.integration.tfc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Original persisted food data, captured BEFORE TFC's lazy decay accessors run.
 * ItemStack 1.20.1 uses Object equality, so these weak keys compare by identity.
 * Values never reference their keys; unused selector candidates can be collected.
 */
public final class BasketMealOrigins {
    private static final Map<ItemStack, CompoundTag> ORIGINS = Collections.synchronizedMap(new WeakHashMap<>());

    private BasketMealOrigins() {}

    public static void remember(ItemStack stack, CompoundTag source) {
        ORIGINS.put(stack, source.copy());
    }

    @Nullable
    public static CompoundTag get(ItemStack stack) {
        CompoundTag tag = ORIGINS.get(stack);
        return tag == null ? null : tag.copy();
    }

    public static boolean same(CompoundTag left, CompoundTag right) {
        CompoundTag a = left.copy();
        CompoundTag b = right.copy();
        a.remove("Count");
        b.remove("Count");
        return a.equals(b);
    }

    /** Atomic, exact persisted-entry consumption; no live IFood serialization. */
    public static boolean consume(ItemStack basket, @Nullable CompoundTag selected) {
        if (selected == null || basket.getTag() == null) {
            return false;
        }
        ListTag inventory = basket.getTag().getList("basket_inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < inventory.size(); i++) {
            CompoundTag entry = inventory.getCompound(i);
            int count = entry.getInt("Count");
            if (count > 0 && same(entry, selected)) {
                ListTag updated = inventory.copy();
                if (count == 1) {
                    updated.remove(i);
                } else {
                    updated.getCompound(i).putByte("Count", (byte) (count - 1));
                }
                basket.getOrCreateTag().put("basket_inventory", updated);
                return true;
            }
        }
        return false;
    }
}
