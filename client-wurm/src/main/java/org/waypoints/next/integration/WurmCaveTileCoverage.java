package org.waypoints.next.integration;

import org.waypoints.next.navigation.CaveTileCoverage;

/** Native strip hooks shared by the cave map and route planner. */
public final class WurmCaveTileCoverage {
    private WurmCaveTileCoverage() { }

    public static void clear(Object buffer) { CaveTileCoverage.clear(buffer); }

    public static void beforeStrip(Object buffer, int x, int y, int width, int height) {
        CaveTileCoverage.beforeStrip(buffer, x, y, width, height);
    }

    public static void afterStrip(Object buffer, int x, int y, int width, int height) {
        CaveTileCoverage.afterStrip(buffer, x, y, width, height);
    }

    static boolean isReceived(Object buffer, int x, int y) {
        return CaveTileCoverage.isReceived(buffer, x, y);
    }
}
