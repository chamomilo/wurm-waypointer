package org.waypoints.next.archaeology;

import org.junit.Test;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.source.MapBounds;

import static org.junit.Assert.*;

public class ArchaeologyPlannerTest {
    private final ArchaeologyPlanner planner = new ArchaeologyPlanner();
    private final MapBounds bounds = new MapBounds(4096, 4096);

    @Test public void usesSpecifiedCardinalAndDiagonalMinimaxSteps() {
        assertPoint(1000.5d, 969.5d, ArchaeologyDistanceBand.NEARBY,
                ArchaeologyDirection.NORTH);
        assertPoint(1024.5d, 976.5d, ArchaeologyDistanceBand.NEARBY,
                ArchaeologyDirection.NORTH_EAST);
        assertPoint(1000.5d, 939.5d, ArchaeologyDistanceBand.CLOSE,
                ArchaeologyDirection.NORTH);
        assertPoint(1048.5d, 952.5d, ArchaeologyDistanceBand.CLOSE,
                ArchaeologyDirection.NORTH_EAST);
        assertPoint(1101.5d, 1000.5d, ArchaeologyDistanceBand.FAR,
                ArchaeologyDirection.EAST);
        assertPoint(1077.5d, 1077.5d, ArchaeologyDistanceBand.FAR,
                ArchaeologyDirection.SOUTH_EAST);
        assertPoint(849.5d, 1000.5d, ArchaeologyDistanceBand.QUITE_DISTANT,
                ArchaeologyDirection.WEST);
        assertPoint(885.5d, 885.5d, ArchaeologyDistanceBand.QUITE_DISTANT,
                ArchaeologyDirection.NORTH_WEST);
        assertPoint(1217.5d, 1000.5d, ArchaeologyDistanceBand.VERY_FAR,
                ArchaeologyDirection.EAST);
        assertPoint(1217.5d, 1217.5d, ArchaeologyDistanceBand.VERY_FAR,
                ArchaeologyDirection.SOUTH_EAST);
    }

    @Test public void followsBoundedVeryCloseScheduleAndClipsMapEdges() {
        int[] expected = {7, 5, 3, 2, 1, 1, 1, 1};
        int step = 0;
        for (int distance : expected) {
            WaypointCoordinate point = planner.next(100.5d, 100.5d,
                    WaypointLayer.SURFACE, ArchaeologyDistanceBand.VERY_CLOSE,
                    ArchaeologyDirection.SOUTH_EAST, step, bounds);
            assertEquals(100.5d + distance, point.getTileX(), 0.0d);
            assertEquals(100.5d + distance, point.getTileY(), 0.0d);
            step = ArchaeologyPlanner.nextTerminalStep(
                    ArchaeologyDistanceBand.VERY_CLOSE, step);
        }
        WaypointCoordinate clipped = planner.next(2.5d, 2.5d, WaypointLayer.SURFACE,
                ArchaeologyDistanceBand.VERY_FAR,
                ArchaeologyDirection.NORTH_WEST, 0, bounds);
        assertEquals(0.5d, clipped.getTileX(), 0.0d);
        assertEquals(0.5d, clipped.getTileY(), 0.0d);
    }

    @Test public void validatesKnownPointAgainstBandAndAbsoluteSector() {
        assertTrue(planner.compatible(100.5d, 100.5d, 130.5d, 70.5d,
                ArchaeologyDistanceBand.NEARBY,
                ArchaeologyDirection.NORTH_EAST));
        assertFalse(planner.compatible(100.5d, 100.5d, 130.5d, 70.5d,
                ArchaeologyDistanceBand.CLOSE,
                ArchaeologyDirection.NORTH_EAST));
        assertFalse(planner.compatible(100.5d, 100.5d, 130.5d, 70.5d,
                ArchaeologyDistanceBand.NEARBY,
                ArchaeologyDirection.SOUTH_EAST));
    }

    private void assertPoint(double x, double y, ArchaeologyDistanceBand band,
                             ArchaeologyDirection direction) {
        WaypointCoordinate point = planner.next(1000.5d, 1000.5d,
                WaypointLayer.SURFACE, band, direction, 0, bounds);
        assertEquals(x, point.getTileX(), 0.0d);
        assertEquals(y, point.getTileY(), 0.0d);
    }
}
