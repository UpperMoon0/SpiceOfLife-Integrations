package dev.uppermoon.solintegrations.integration.tfc;

/**
 * Observes one Player.eat call without storing world/player objects globally.
 * Identity checks prevent nested or unrelated food calls from claiming the meal.
 */
public final class ConsumptionScope implements AutoCloseable {
    private static final ThreadLocal<ConsumptionScope> CURRENT = new ThreadLocal<>();

    private final ConsumptionScope previous;
    private final Object target;
    private final Object food;
    private final Object stack;
    private final float multiplier;
    private final boolean blacklisted;
    private boolean nativeConsumed;
    private boolean closed;

    private ConsumptionScope(Object target, Object food, Object stack, float multiplier, boolean blacklisted) {
        previous = CURRENT.get();
        this.target = target;
        this.food = food;
        this.stack = stack;
        this.multiplier = multiplier;
        this.blacklisted = blacklisted;
        CURRENT.set(this);
    }

    public static ConsumptionScope open(Object target, Object food, Object stack,
                                        float multiplier, boolean blacklisted) {
        return new ConsumptionScope(target, food, stack, multiplier, blacklisted);
    }

    public static ConsumptionScope forFood(Object target, Object food) {
        ConsumptionScope scope = CURRENT.get();
        return scope != null && scope.target == target && scope.food == food ? scope : null;
    }

    public static ConsumptionScope forStack(Object stack) {
        ConsumptionScope scope = CURRENT.get();
        return scope != null && scope.stack == stack ? scope : null;
    }

    public Object food() { return food; }
    public float multiplier() { return multiplier; }
    public boolean blacklisted() { return blacklisted; }
    public boolean nativeConsumed() { return nativeConsumed; }
    public void markNativeConsumed() { nativeConsumed = true; }

    @Override
    public void close() {
        if (!closed) {
            if (CURRENT.get() != this) {
                throw new IllegalStateException("TFC consumption scopes must close in reverse order");
            }
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
            closed = true;
        }
    }
}
