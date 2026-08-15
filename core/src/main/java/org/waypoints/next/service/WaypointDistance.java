package org.waypoints.next.service;

/** Shared tile-coordinate distance calculation for manager snapshots and live labels. */
public final class WaypointDistance {
    private static final double METRES_PER_TILE = 4.0d;

    private WaypointDistance() { }

    public static int metres(double targetTileX, double targetTileY,
                             double originTileX, double originTileY) {
        double dx = (targetTileX - originTileX) * METRES_PER_TILE;
        double dy = (targetTileY - originTileY) * METRES_PER_TILE;
        double distance = Math.sqrt(dx * dx + dy * dy);
        if (distance >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(0, (int) Math.round(distance));
    }
}
