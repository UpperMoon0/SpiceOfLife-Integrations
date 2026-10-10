package dev.uppermoon.solintegrations.integration.backpack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/**
 * Orders attempts, without moving stacks or performing a speculative use.
 * The caller retains responsibility for eligibility and actual consumption.
 */
public final class DepletionOrder {
    private DepletionOrder() {
    }

    /**
     * Recomputes scores for every feeding attempt, then invokes the original
     * action in descending multiplier order. Equal scores keep native slot
     * order. A slot is read again at invocation time, avoiding a stale stack
     * reference if an earlier unsuccessful action changed the handler.
     */
    public static <S, R> R iterate(
            int slotCount,
            IntFunction<S> readSlot,
            ToDoubleFunction<S> multiplier,
            BiFunction<Integer, S, R> attempt,
            Supplier<R> defaultResult,
            Predicate<R> shouldStop) {
        R result = defaultResult.get();
        int[] slots = slots(slotCount, readSlot, multiplier);
        for (int slot : slots) {
            result = attempt.apply(slot, readSlot.apply(slot));
            if (shouldStop.test(result)) {
                break;
            }
        }
        return result;
    }

    /** Returns a physical-slot permutation without modifying the inventory. */
    public static <S> int[] slots(int slotCount, IntFunction<S> readSlot, ToDoubleFunction<S> multiplier) {
        List<ScoredSlot> slots = new ArrayList<>(slotCount);
        for (int slot = 0; slot < slotCount; slot++) {
            double score = multiplier.applyAsDouble(readSlot.apply(slot));
            slots.add(new ScoredSlot(slot, Double.isNaN(score) ? Double.NEGATIVE_INFINITY : score));
        }
        slots.sort(Comparator.comparingDouble(ScoredSlot::score).reversed()
                .thenComparingInt(ScoredSlot::slot));

        return slots.stream().mapToInt(ScoredSlot::slot).toArray();
    }

    private record ScoredSlot(int slot, double score) {
    }
}
