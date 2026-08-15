package org.waypoints.next.service;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WaypointDistanceTest {
    @Test public void convertsFractionalTileOffsetsToRoundedMetres() {
        assertEquals(2, WaypointDistance.metres(10.5d, 20.0d, 10.0d, 20.0d));
        assertEquals(6, WaypointDistance.metres(11.0d, 21.0d, 10.0d, 20.0d));
    }

    @Test public void clampsDistancesThatDoNotFitAnInteger() {
        assertEquals(Integer.MAX_VALUE,
                WaypointDistance.metres(Double.MAX_VALUE, 0.0d, 0.0d, 0.0d));
    }
}
