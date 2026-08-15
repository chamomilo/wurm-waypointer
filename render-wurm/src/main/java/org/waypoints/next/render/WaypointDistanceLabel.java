package org.waypoints.next.render;

/** Allocation-conscious label formatting; text changes only when rounded metres change. */
public final class WaypointDistanceLabel {
    private WaypointDistanceLabel() { }

    public static int roundedMeters(float playerX, float playerY,
                                    float targetX, float targetY) {
        double dx = (double) targetX - playerX;
        double dy = (double) targetY - playerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(0, (int) Math.round(distance));
    }

    public static String format(String waypointName, int metres) {
        return waypointName + " - " + metres + "m";
    }

    public static String format(String waypointName, int metres,
                                long remainingSeconds) {
        if (remainingSeconds < 0L) return format(waypointName, metres);
        long hours = remainingSeconds / 3600L;
        long minutes = (remainingSeconds % 3600L) / 60L;
        long seconds = remainingSeconds % 60L;
        String timer = hours == 0L
                ? minutes + ":" + twoDigits(seconds)
                : twoDigits(hours) + ":" + twoDigits(minutes) + ":"
                + twoDigits(seconds);
        return format(waypointName, metres) + " - " + timer + " remaining";
    }

    /** Ceiling keeps the visible second from reaching zero prematurely. */
    public static long remainingSeconds(long expiresAtEpochMillis,
                                        long nowEpochMillis) {
        if (expiresAtEpochMillis <= 0L) return -1L;
        if (expiresAtEpochMillis <= nowEpochMillis) return 0L;
        long remaining = expiresAtEpochMillis - nowEpochMillis;
        return 1L + (remaining - 1L) / 1_000L;
    }

    private static String twoDigits(long value) {
        return (value < 10L ? "0" : "") + value;
    }
}
