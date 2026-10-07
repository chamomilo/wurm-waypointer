package org.waypoints.next.navigation;

import org.junit.Test;
import static org.junit.Assert.*;

public final class CaveTileCoverageTest {
    @Test public void routingRequiresFourReceivedCornersAndRejectsRingAliases() {
        Object cave = new Object();
        CaveTileCoverage.beforeStrip(cave, 100, 100, 2, 2);
        assertFalse(CaveTileCoverage.hasFloorCorners(cave, 100, 100));
        CaveTileCoverage.afterStrip(cave, 100, 100, 2, 2);
        assertTrue(CaveTileCoverage.hasFloorCorners(cave, 100, 100));
        assertFalse(CaveTileCoverage.hasFloorCorners(cave, 101, 100));
        assertFalse(CaveTileCoverage.hasFloorCorners(cave, 164, 100));
        CaveTileCoverage.beforeStrip(cave, 101, 101, 1, 1);
        assertFalse(CaveTileCoverage.hasFloorCorners(cave, 100, 100));
        CaveTileCoverage.afterStrip(cave, 101, 101, 1, 1);
        assertTrue(CaveTileCoverage.hasFloorCorners(cave, 100, 100));
        CaveTileCoverage.clear(cave);
        assertFalse(CaveTileCoverage.hasFloorCorners(cave, 100, 100));
    }
}
