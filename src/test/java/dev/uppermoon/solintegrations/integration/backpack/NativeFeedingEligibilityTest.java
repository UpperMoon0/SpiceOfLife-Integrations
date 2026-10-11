package dev.uppermoon.solintegrations.integration.backpack;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeFeedingEligibilityTest {
    @Test
    void transformedTfgEligibilityCannotBeBypassedByBasketOrHurtPlayer() {
        List<String> calls = new ArrayList<>();
        assertFalse(NativeFeedingEligibility.allows(
                check(calls, "edible", false), check(calls, "filter", true),
                check(calls, "hunger", true), check(calls, "hurt", true)));
        assertEquals(List.of("edible"), calls);
    }

    @Test
    void filterRejectsBasketBeforeConsumptionOrHungerQueries() {
        List<String> calls = new ArrayList<>();
        AtomicInteger consumed = new AtomicInteger();
        boolean allowed = NativeFeedingEligibility.allows(
                check(calls, "edible", true), check(calls, "filter", false),
                check(calls, "hunger", true), check(calls, "hurt", true));
        if (allowed) {
            consumed.incrementAndGet();
        }
        assertFalse(allowed);
        assertEquals(0, consumed.get());
        assertEquals(List.of("edible", "filter"), calls);
    }

    @Test
    void basketMustMeetHungerThresholdUnlessNativeHurtExceptionApplies() {
        assertFalse(NativeFeedingEligibility.allows(() -> true, () -> true, () -> false, () -> false));
        assertTrue(NativeFeedingEligibility.allows(() -> true, () -> true, () -> false, () -> true));
    }

    @Test
    void sufficientHungerDoesNotEvaluateHurtException() {
        List<String> calls = new ArrayList<>();
        assertTrue(NativeFeedingEligibility.allows(
                check(calls, "edible", true), check(calls, "filter", true),
                check(calls, "hunger", true), check(calls, "hurt", false)));
        assertEquals(List.of("edible", "filter", "hunger"), calls);
    }

    private static BooleanSupplier check(List<String> calls, String name, boolean result) {
        return () -> {
            calls.add(name);
            return result;
        };
    }
}
