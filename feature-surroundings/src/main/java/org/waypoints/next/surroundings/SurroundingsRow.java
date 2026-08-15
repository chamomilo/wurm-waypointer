package org.waypoints.next.surroundings;

/** One filtered row with player-relative data. */
public final class SurroundingsRow {
    private final SurroundingEntry entry;
    private final boolean waypointEnabled;
    private final int distanceMetres;

    SurroundingsRow(SurroundingEntry entry, boolean waypointEnabled,
                    int distanceMetres) {
        this.entry = entry;
        this.waypointEnabled = waypointEnabled;
        this.distanceMetres = distanceMetres;
    }

    public SurroundingEntry getEntry() { return entry; }
    public boolean isWaypointEnabled() { return waypointEnabled; }
    public int getDistanceMetres() { return distanceMetres; }
}
