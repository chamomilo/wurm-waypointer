package org.waypoints.next.model;

/** Persisted per-waypoint arrival policy bounds. */
public final class WaypointArrival {
    public static final int DISABLED = 0;
    public static final int DEFAULT_RADIUS_METRES = 20;
    public static final int MAXIMUM_RADIUS_METRES = 1000;
    public static final int REARM_HYSTERESIS_METRES = 8;

    private WaypointArrival() { }

    public static int requireRadius(int metres) {
        if (metres < DISABLED || metres > MAXIMUM_RADIUS_METRES) {
            throw new IllegalArgumentException("arrival radius must be in 0.."
                    + MAXIMUM_RADIUS_METRES + " metres");
        }
        return metres;
    }
}
