package dev.uppermoon.solintegrations.integration.tfc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConsumptionScopeTest {
    @Test
    void observesExactNativeCallSoFallbackDoesNotDuplicateIt() {
        Object target = new Object();
        Object food = new Object();
        Object stack = new Object();
        int[] nativeCalls = {0};
        try (ConsumptionScope scope = ConsumptionScope.open(target, food, stack, 0.4F, false)) {
            ConsumptionScope observed = ConsumptionScope.forFood(target, food);
            assertNotNull(observed);
            nativeCalls[0]++;
            observed.markNativeConsumed();
            if (!scope.nativeConsumed()) {
                nativeCalls[0]++;
            }
            assertSame(scope, ConsumptionScope.forStack(stack));
        }
        assertEquals(1, nativeCalls[0]);
        assertNull(ConsumptionScope.forFood(target, food));
        assertNull(ConsumptionScope.forStack(stack));
    }

    @Test
    void unrelatedNativeFoodCannotSuppressTheMeal() {
        Object target = new Object();
        Object food = new Object();
        try (ConsumptionScope scope = ConsumptionScope.open(target, food, new Object(), 0.4F, false)) {
            assertNull(ConsumptionScope.forFood(target, new Object()));
            assertNull(ConsumptionScope.forFood(new Object(), food));
            assertFalse(scope.nativeConsumed());
        }
    }

    @Test
    void nestedMealCannotClaimOuterMealAndRestoresOuterScope() {
        Object target = new Object();
        Object food = new Object();
        Object stack = new Object();
        try (ConsumptionScope outer = ConsumptionScope.open(target, food, stack, 0.4F, false)) {
            try (ConsumptionScope inner = ConsumptionScope.open(target, new Object(), new Object(), 0.2F, false)) {
                assertNull(ConsumptionScope.forFood(target, food));
                assertNull(ConsumptionScope.forStack(stack));
                inner.markNativeConsumed();
            }
            assertSame(outer, ConsumptionScope.forFood(target, food));
            assertFalse(outer.nativeConsumed());
        }
    }

    @Test
    void exceptionInOriginalPlayerEatCannotLeakContext() {
        Object target = new Object();
        Object food = new Object();
        Object stack = new Object();
        assertThrows(IllegalArgumentException.class, () -> {
            try (ConsumptionScope ignored = ConsumptionScope.open(target, food, stack, 0.5F, false)) {
                throw new IllegalArgumentException("simulated original Player.eat failure");
            }
        });
        assertNull(ConsumptionScope.forFood(target, food));
        assertNull(ConsumptionScope.forStack(stack));
    }

    @Test
    void observerUsesIdentityRatherThanEqualFoodValues() {
        Object target = new Object();
        String food = new String("food");
        String equalFood = new String("food");
        try (ConsumptionScope ignored = ConsumptionScope.open(target, food, new Object(), 1, true)) {
            assertNull(ConsumptionScope.forFood(target, equalFood));
            assertTrue(ConsumptionScope.forFood(target, food).blacklisted());
        }
    }
}
