package org.waypoints.next.navigation;

/** Live, atomically replaceable published-highway snapshot. */
public interface HighwayIndexSource {
    HighwayTileIndex current();
    long revision();
}
