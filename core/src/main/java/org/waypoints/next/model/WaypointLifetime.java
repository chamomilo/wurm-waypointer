package org.waypoints.next.model;

import java.time.Instant;

/** Persisted absolute expiry plus bounded UI lifetime selections. */
public final class WaypointLifetime {
    public static final int KEEP_CURRENT_MINUTES = -1;
    public static final int PERMANENT_MINUTES = 0;
    private static final int[] PRESET_MINUTES = {1, 5, 15, 30, 60, 360, 1440};

    private WaypointLifetime() { }

    public static int[] presetMinutes() { return PRESET_MINUTES.clone(); }

    public static int requireSelection(int minutes, boolean allowKeepCurrent) {
        if (allowKeepCurrent && minutes == KEEP_CURRENT_MINUTES) return minutes;
        if (minutes == PERMANENT_MINUTES) return minutes;
        for (int preset : PRESET_MINUTES) if (minutes == preset) return minutes;
        throw new IllegalArgumentException("waypoint lifetime must be Permanent, "
                + "Keep current, or a supported preset");
    }

    public static Instant resolve(int minutes, Instant current, Instant now,
                                  boolean allowKeepCurrent) {
        if (now == null) throw new IllegalArgumentException("current time is required");
        int selected = requireSelection(minutes, allowKeepCurrent);
        if (selected == KEEP_CURRENT_MINUTES) return current;
        if (selected == PERMANENT_MINUTES) return null;
        return now.plusSeconds(selected * 60L);
    }

    public static boolean isExpired(Instant expiresAt, long nowEpochMillis) {
        return expiresAt != null && expiresAt.toEpochMilli() <= nowEpochMillis;
    }
}
