package dev.uppermoon.solintegrations.integration.tfc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeedingNutritionScopeTest {
    @Test
    void bindsExactStackAndConsumer() {
        Object stack = new Object();
        Object player = new Object();
        try (FeedingNutritionScope scope = FeedingNutritionScope.open(stack, player, 8)) {
            assertSame(scope, FeedingNutritionScope.current());
            assertSame(stack, scope.stack());
            assertSame(player, scope.consumer());
            assertEquals(8, scope.nativeHunger());
        }
        assertNull(FeedingNutritionScope.current());
    }

    @Test
    void nonTfcNestedCallMasksOuterValueThenRestoresIt() {
        try (FeedingNutritionScope outer = FeedingNutritionScope.open(new Object(), new Object(), 8)) {
            try (FeedingNutritionScope inner = FeedingNutritionScope.open(new Object(), new Object(), null)) {
                assertSame(inner, FeedingNutritionScope.current());
                assertNull(FeedingNutritionScope.current().nativeHunger());
            }
            assertSame(outer, FeedingNutritionScope.current());
            assertEquals(8, FeedingNutritionScope.current().nativeHunger());
        }
    }

    @Test
    void exceptionRestoresOuterAndRemovesFinalContext() {
        assertThrows(IllegalStateException.class, () -> {
            try (FeedingNutritionScope outer = FeedingNutritionScope.open(new Object(), new Object(), 8)) {
                assertThrows(IllegalArgumentException.class, () -> {
                    try (FeedingNutritionScope ignored = FeedingNutritionScope.open(new Object(), new Object(), 2)) {
                        throw new IllegalArgumentException("nested eligibility failure");
                    }
                });
                assertSame(outer, FeedingNutritionScope.current());
                throw new IllegalStateException("outer eligibility failure");
            }
        });
        assertNull(FeedingNutritionScope.current());
    }

    @Test
    void doesNotLeakToOtherThread() throws InterruptedException {
        FeedingNutritionScope[] seen = new FeedingNutritionScope[1];
        try (FeedingNutritionScope ignored = FeedingNutritionScope.open(new Object(), new Object(), 8)) {
            Thread thread = new Thread(() -> seen[0] = FeedingNutritionScope.current());
            thread.start();
            thread.join();
        }
        assertNull(seen[0]);
        assertNull(FeedingNutritionScope.current());
    }
}
