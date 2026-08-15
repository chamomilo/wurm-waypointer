package org.waypoints.next.render;

/** Rejects stale HUD hitboxes when their component stopped rendering. */
public final class RecentRenderGate {
    private RecentRenderGate() {
    }

    public static boolean isFresh(long nowNanos, long lastRenderNanos,
                                  long maximumAgeNanos) {
        if (lastRenderNanos <= 0L || maximumAgeNanos < 0L) return false;
        long age = nowNanos - lastRenderNanos;
        return age >= 0L && age <= maximumAgeNanos;
    }
}
