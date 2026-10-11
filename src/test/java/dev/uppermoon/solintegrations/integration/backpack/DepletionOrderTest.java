package dev.uppermoon.solintegrations.integration.backpack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DepletionOrderTest {
    @Test
    void prefersLeastDepletedRatherThanFirstSlot() {
        List<Double> scores = List.of(0.15D, 0.8D, 0.5D);
        List<Integer> attempts = new ArrayList<>();
        boolean result = DepletionOrder.iterate(scores.size(), scores::get, Double::doubleValue,
                (slot, score) -> {
                    attempts.add(slot);
                    return true;
                }, () -> false, Boolean::booleanValue);

        assertTrue(result);
        assertEquals(List.of(1), attempts);
    }

    @Test
    void preservesSlotOrderForEqualScores() {
        List<Double> scores = List.of(0.5D, 0.9D, 0.5D, 0.9D);
        List<Integer> attempts = visitAll(scores);
        assertEquals(List.of(1, 3, 0, 2), attempts);
    }

    @Test
    void originalCallbackStillDecidesEligibilityAndUseSuccess() {
        List<Double> scores = List.of(0.9D, 1.0D, 0.7D, 0.8D);
        List<Integer> attempts = new ArrayList<>();
        AtomicInteger consumed = new AtomicInteger();
        AtomicInteger history = new AtomicInteger();

        boolean result = DepletionOrder.iterate(scores.size(), scores::get, Double::doubleValue,
                (slot, score) -> {
                    attempts.add(slot);
                    // Native callback may reject filters, hunger, rotten food,
                    // or an item whose use does not return CONSUME.
                    if (slot != 3) {
                        return false;
                    }
                    consumed.incrementAndGet();
                    history.incrementAndGet();
                    return true;
                }, () -> false, Boolean::booleanValue);

        assertTrue(result);
        assertEquals(List.of(1, 0, 3), attempts);
        assertEquals(1, consumed.get());
        assertEquals(1, history.get());
    }

    @Test
    void scoresAreRecomputedAndSelectionCyclesAsDepletionChanges() {
        List<Meal> inventory = List.of(new Meal(1.0D), new Meal(1.0D), new Meal(1.0D));
        List<Integer> eaten = new ArrayList<>();
        for (int round = 0; round < 6; round++) {
            DepletionOrder.iterate(inventory.size(), inventory::get, meal -> meal.multiplier,
                    (slot, meal) -> {
                        eaten.add(slot);
                        meal.multiplier -= 0.25D;
                        meal.count--;
                        return true;
                    }, () -> false, Boolean::booleanValue);
        }
        assertEquals(List.of(0, 1, 2, 0, 1, 2), eaten);
        assertEquals(List.of(2, 2, 2), inventory.stream().map(meal -> meal.count).toList());
    }

    @Test
    void completesReadOnlyRankingBeforeCallingAnAttempt() {
        List<Meal> inventory = List.of(new Meal(0.4D), new Meal(0.9D), new Meal(0.3D));
        AtomicInteger scored = new AtomicInteger();
        AtomicInteger attempts = new AtomicInteger();

        DepletionOrder.iterate(inventory.size(), inventory::get,
                meal -> {
                    assertEquals(0, attempts.get());
                    assertEquals(List.of(4, 4, 4), inventory.stream().map(m -> m.count).toList());
                    scored.incrementAndGet();
                    return meal.multiplier;
                }, (slot, meal) -> {
                    assertEquals(3, scored.get());
                    assertEquals(List.of(4, 4, 4), inventory.stream().map(m -> m.count).toList());
                    attempts.incrementAndGet();
                    return true;
                }, () -> false, Boolean::booleanValue);

        assertEquals(1, attempts.get());
        assertEquals(List.of(4, 4, 4), inventory.stream().map(m -> m.count).toList());
    }

    @Test
    void readsCurrentStackAgainBeforeAttemptingIt() {
        List<Meal> inventory = new ArrayList<>(List.of(new Meal(1.0D), new Meal(0.5D)));
        Meal replacement = new Meal(0.1D);
        List<Meal> attempted = new ArrayList<>();

        DepletionOrder.iterate(inventory.size(), inventory::get, meal -> meal.multiplier,
                (slot, meal) -> {
                    attempted.add(meal);
                    if (slot == 0) {
                        inventory.set(1, replacement);
                    }
                    return false;
                }, () -> false, Boolean::booleanValue);

        assertEquals(replacement, attempted.get(1));
    }

    @Test
    void zeroScoresAreStillOfferedToNativeEligibility() {
        // The upstream minimum-nutrition setting may keep zero-multiplier
        // food edible. Ordering must not impose a new food blacklist.
        assertEquals(List.of(1, 0, 2), visitAll(List.of(0.0D, 0.2D, 0.0D)));
    }

    @Test
    void handlesEmptyInventoryAndNoSuccessfulCandidate() {
        assertFalse(DepletionOrder.iterate(0, slot -> 0.0D, Double::doubleValue,
                (slot, score) -> true, () -> false, Boolean::booleanValue));
        List<Integer> attempts = visitAll(List.of(0.1D, 0.7D));
        assertEquals(List.of(1, 0), attempts);
    }

    @Test
    void keepsInvalidScoresAtTheEndWithoutBreakingTies() {
        assertEquals(List.of(2, 0, 1, 3),
                visitAll(List.of(Double.NaN, Double.NEGATIVE_INFINITY, 0.2D, Double.NaN)));
    }

    @Test
    void retainsDefaultAndStopSemanticsForNonBooleanCallbacks() {
        AtomicInteger defaults = new AtomicInteger();
        String result = DepletionOrder.iterate(3, slot -> (double) slot, Double::doubleValue,
                (slot, score) -> slot == 1 ? "finished" : "continue",
                () -> {
                    defaults.incrementAndGet();
                    return "empty";
                }, "finished"::equals);
        assertEquals("finished", result);
        assertEquals(1, defaults.get());
    }

    private static List<Integer> visitAll(List<Double> scores) {
        List<Integer> attempts = new ArrayList<>();
        boolean result = DepletionOrder.iterate(scores.size(), scores::get, Double::doubleValue,
                (slot, score) -> {
                    attempts.add(slot);
                    return false;
                }, () -> false, Boolean::booleanValue);
        assertFalse(result);
        return attempts;
    }

    private static final class Meal {
        double multiplier;
        int count = 4;

        Meal(double multiplier) {
            this.multiplier = multiplier;
        }
    }
}
