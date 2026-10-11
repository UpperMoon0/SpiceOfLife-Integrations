package dev.uppermoon.solintegrations.integration.tfc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** A native meal is awarded only after its exact persisted basket entry is removed. */
public final class BasketConsumptionScope implements AutoCloseable {
    private static final ThreadLocal<BasketConsumptionScope> CURRENT = new ThreadLocal<>();
    private final BasketConsumptionScope previous;
    private final ItemStack basket;
    private final LivingEntity consumer;
    private ItemStack delegated = ItemStack.EMPTY;
    private CompoundTag selectedOrigin;
    private CompoundTag removedOrigin;
    private TfcConsumptionBridge.Meal pending;
    private boolean nativeAlreadyConsumed;
    private boolean claimed;
    private boolean closed;

    private BasketConsumptionScope(ItemStack basket, LivingEntity consumer) {
        previous = CURRENT.get();
        this.basket = basket;
        this.consumer = consumer;
        CURRENT.set(this);
    }

    public static BasketConsumptionScope open(ItemStack basket, LivingEntity consumer) { return new BasketConsumptionScope(basket, consumer); }
    @Nullable public static BasketConsumptionScope current() { return CURRENT.get(); }
    @Nullable public static BasketConsumptionScope forBasket(ItemStack basket) {
        BasketConsumptionScope scope = CURRENT.get();
        return scope != null && scope.basket == basket ? scope : null;
    }
    public ItemStack basket() { return basket; }

    public void selected(ItemStack stack, LivingEntity consumer) {
        if (!claimed && this.consumer == consumer) {
            selectedOrigin = BasketMealOrigins.get(stack);
        }
    }

    @Nullable
    public CompoundTag claim(ItemStack stack, LivingEntity consumer) {
        if (claimed || selectedOrigin == null || this.consumer != consumer || delegated != stack) {
            return null;
        }
        claimed = true;
        return selectedOrigin.copy();
    }

    public void beginDelegation(ItemStack stack, LivingEntity consumer) {
        if (this.consumer == consumer && !claimed) delegated = stack;
    }

    public void endDelegation(ItemStack stack) {
        if (delegated == stack) delegated = ItemStack.EMPTY;
    }

    public void removed(CompoundTag origin) { removedOrigin = origin.copy(); }

    public void defer(TfcConsumptionBridge.Meal meal, boolean alreadyConsumed) {
        pending = meal;
        nativeAlreadyConsumed = alreadyConsumed;
    }

    public void complete() {
        if (pending != null && !nativeAlreadyConsumed && removedOrigin != null
                && pending.basketOrigin() != null && BasketMealOrigins.same(removedOrigin, pending.basketOrigin())) {
            // The Player.eat observation scope has ended for normal basket use.
            // Re-open only around the confirmed native consumption.
            try (ConsumptionScope ignored = pending.openScope()) {
                pending.consume();
            }
        }
        pending = null;
    }

    @Override public void close() {
        if (!closed) {
            if (CURRENT.get() != this) throw new IllegalStateException("Basket scopes must close in reverse order");
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            closed = true;
        }
    }
}
