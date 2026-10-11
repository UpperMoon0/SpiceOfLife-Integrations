package dev.uppermoon.solintegrations.integration.tfc;

/**
 * Native hunger for one synchronous Core isEdible evaluation. A null native
 * hunger deliberately masks an outer evaluation for a non-TFC nested call.
 */
public final class FeedingNutritionScope implements AutoCloseable {
    private static final ThreadLocal<FeedingNutritionScope> CURRENT = new ThreadLocal<>();
    private final FeedingNutritionScope previous;
    private final Object stack;
    private final Object consumer;
    private final Integer nativeHunger;
    private boolean closed;

    private FeedingNutritionScope(Object stack, Object consumer, Integer nativeHunger) {
        this.previous = CURRENT.get();
        this.stack = stack;
        this.consumer = consumer;
        this.nativeHunger = nativeHunger;
        CURRENT.set(this);
    }

    public static FeedingNutritionScope open(Object stack, Object consumer, Integer nativeHunger) {
        return new FeedingNutritionScope(stack, consumer, nativeHunger);
    }

    public static FeedingNutritionScope current() { return CURRENT.get(); }
    public Object stack() { return stack; }
    public Object consumer() { return consumer; }
    public Integer nativeHunger() { return nativeHunger; }

    @Override public void close() {
        if (!closed) {
            if (CURRENT.get() != this) throw new IllegalStateException("Feeding scopes must close in reverse order");
            if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
            closed = true;
        }
    }
}
