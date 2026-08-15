package org.waypoints.next.render;

/** One-shot monotonic deadline used to refresh render resources after transfer. */
public final class RendererStabilizationGate {
    private boolean pending;
    private long deadlineNanos;

    public synchronized void schedule(long nowNanos, long delayNanos) {
        if (delayNanos < 0L) throw new IllegalArgumentException("delay must be non-negative");
        deadlineNanos = nowNanos + delayNanos;
        pending = true;
    }

    public synchronized boolean takeIfDue(long nowNanos) {
        if (!pending || nowNanos - deadlineNanos < 0L) return false;
        pending = false;
        return true;
    }

    public synchronized void cancel() {
        pending = false;
    }

    synchronized boolean isPending() {
        return pending;
    }
}
