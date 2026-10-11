package dev.uppermoon.solintegrations.integration.tfc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RestorationScopeTest {
    @Test
    void closesAfterNativeConsumptionThrows() {
        Object target = new Object();
        assertThrows(IllegalStateException.class, () -> {
            try (RestorationScope ignored = RestorationScope.open(target, 0.25F, false)) {
                assertNotNull(RestorationScope.current(target));
                throw new IllegalStateException("simulated native food failure");
            }
        });
        assertNull(RestorationScope.current(target));
    }

    @Test
    void nestedConsumptionRestoresOuterContext() {
        Object outer = new Object();
        Object inner = new Object();
        try (RestorationScope outerScope = RestorationScope.open(outer, 0.5F, false)) {
            try (RestorationScope innerScope = RestorationScope.open(inner, 1.0F, true)) {
                assertSame(innerScope, RestorationScope.current(inner));
                assertNull(RestorationScope.current(outer));
                assertTrue(innerScope.blacklisted());
            }
            assertSame(outerScope, RestorationScope.current(outer));
            assertEquals(0.5F, outerScope.multiplier());
        }
        assertNull(RestorationScope.current(outer));
        assertNull(RestorationScope.current(inner));
    }

    @Test
    void scopeIsThreadLocal() throws InterruptedException {
        Object target = new Object();
        boolean[] leaked = {true};
        try (RestorationScope ignored = RestorationScope.open(target, 0.5F, false)) {
            Thread other = new Thread(() -> leaked[0] = RestorationScope.current(target) != null);
            other.start();
            other.join();
        }
        assertFalse(leaked[0]);
    }

    @Test
    void closingAlreadyClosedScopeIsSafe() {
        Object target = new Object();
        RestorationScope scope = RestorationScope.open(target, 1.0F, false);
        scope.close();
        scope.close();
        assertNull(RestorationScope.current(target));
    }
}
