package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WaypointDistanceLabelTest {
    @Test
    public void formatsRequestedNameDashDistanceInMetres() {
        assertEquals(955, WaypointDistanceLabel.roundedMeters(0.0f, 0.0f, 955.0f, 0.0f));
        assertEquals("Waypoint 2 - 955m", WaypointDistanceLabel.format("Waypoint 2", 955));
        assertEquals("Waypoint 1 - 15m - 0:15 remaining",
                WaypointDistanceLabel.format("Waypoint 1", 15, 15));
        assertEquals("Waypoint 1 - 15m - 1:15 remaining",
                WaypointDistanceLabel.format("Waypoint 1", 15, 75));
        assertEquals("Waypoint 1 - 15m - 01:15:00 remaining",
                WaypointDistanceLabel.format("Waypoint 1", 15, 4500));
    }

    @Test
    public void roundsEuclideanWorldDistance() {
        assertEquals(5, WaypointDistanceLabel.roundedMeters(10.0f, 20.0f, 13.0f, 24.0f));
    }

    @Test public void remainingSecondsUsesCeilingAndPermanentSentinel() {
        assertEquals(-1L, WaypointDistanceLabel.remainingSeconds(0L, 10L));
        assertEquals(900L, WaypointDistanceLabel.remainingSeconds(
                900_000L, 1L));
        assertEquals(0L, WaypointDistanceLabel.remainingSeconds(10L, 10L));
    }
}
