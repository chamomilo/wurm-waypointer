package org.waypoints.next.ui;

/** Keeps live catalog refreshes from fighting an active list scroll. */
public final class SurroundingsScrollState {
    private final long settleMillis;
    private int offset;
    private long refreshDeferredUntil;
    private boolean initialized;

    public SurroundingsScrollState(long settleMillis) {
        if (settleMillis < 0L) throw new IllegalArgumentException(
                "scroll settle time must be non-negative");
        this.settleMillis = settleMillis;
    }

    /** Records a user-driven offset change and starts a fresh quiet period. */
    public boolean observe(int nextOffset, long nowMillis) {
        int normalized = Math.max(0, nextOffset);
        if (!initialized) {
            synchronize(normalized);
            return false;
        }
        if (normalized == offset) return false;
        offset = normalized;
        refreshDeferredUntil = saturatingAdd(nowMillis, settleMillis);
        return true;
    }

    /** Records a programmatic restore without treating it as user input. */
    public void synchronize(int nextOffset) {
        offset = Math.max(0, nextOffset);
        refreshDeferredUntil = 0L;
        initialized = true;
    }

    public boolean permitsAutoRefresh(long nowMillis) {
        return nowMillis >= refreshDeferredUntil;
    }

    public int getOffset() { return offset; }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) return Long.MAX_VALUE;
        return left + right;
    }
}
