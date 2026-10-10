package dev.uppermoon.solintegrations.integration.tfc;

/** A small, reentrant scope active only during the native TFC consume call. */
public final class RestorationScope implements AutoCloseable {
    private static final ThreadLocal<RestorationScope> CURRENT = new ThreadLocal<>();

    private final RestorationScope previous;
    private final Object target;
    private final float multiplier;
    private final boolean blacklisted;
    private boolean closed;

    private RestorationScope(Object target, float multiplier, boolean blacklisted) {
        this.previous = CURRENT.get();
        this.target = target;
        this.multiplier = multiplier;
        this.blacklisted = blacklisted;
        CURRENT.set(this);
    }

    public static RestorationScope open(Object target, float multiplier, boolean blacklisted) {
        return new RestorationScope(target, multiplier, blacklisted);
    }

    public static RestorationScope current(Object target) {
        RestorationScope value = CURRENT.get();
        return value != null && value.target == target ? value : null;
    }

    public float multiplier() {
        return multiplier;
    }

    public boolean blacklisted() {
        return blacklisted;
    }

    @Override
    public void close() {
        if (!closed) {
            if (CURRENT.get() != this) {
                throw new IllegalStateException("TFC restoration scopes must close in reverse order");
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
