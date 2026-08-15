package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CompassMarkerScaleTest {
    @Test public void markerShrinksWithCompassButRetainsReadableMinimum() {
        assertEquals(10, CompassMarkerScale.pixels(128, 128));
        assertEquals(7, CompassMarkerScale.pixels(64, 64));
        assertEquals(5, CompassMarkerScale.pixels(32, 32));
        assertEquals(14, CompassMarkerScale.pixels(256, 256));
        assertEquals(18, CompassMarkerScale.pixels(1024, 1024));
        assertEquals(6, CompassMarkerScale.selectionPadding(30));
        assertEquals(4, CompassMarkerScale.selectionPadding(15));
    }
}
