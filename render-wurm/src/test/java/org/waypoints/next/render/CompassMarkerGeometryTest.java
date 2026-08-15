package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompassMarkerGeometryTest {
    @Test
    public void cardinalBearingsUseWurmCoordinateConvention() {
        assertPosition(CompassMarkerGeometry.locate(0, 0, 0, 0, -10,
                100, 200, 100, 100), 150, 211);
        assertPosition(CompassMarkerGeometry.locate(0, 0, 0, 10, 0,
                100, 200, 100, 100), 189, 250);
        assertPosition(CompassMarkerGeometry.locate(0, 0, 0, 0, 10,
                100, 200, 100, 100), 150, 289);
        assertPosition(CompassMarkerGeometry.locate(0, 0, 0, -10, 0,
                100, 200, 100, 100), 111, 250);
    }

    @Test
    public void facingAndWrapAroundKeepTargetAhead() {
        CompassMarkerGeometry.Position eastWhileFacingEast =
                CompassMarkerGeometry.locate(0, 0, 90, 10, 0,
                        0, 0, 100, 100);
        assertPosition(eastWhileFacingEast, 50, 11);
        assertEquals(-20.0f, CompassMarkerGeometry.normalizeSigned(350.0f - 10.0f),
                0.0001f);
    }

    @Test
    public void coincidentTargetMovesToCenterAndHitTestIsCircular() {
        CompassMarkerGeometry.Position marker = CompassMarkerGeometry.locate(
                5, 5, 123, 5, 5, 20, 30, 64, 64);
        assertTrue(marker.isArrived());
        assertPosition(marker, 52, 62);
        assertTrue(CompassMarkerGeometry.hit(marker, 58, 62, 8));
        assertFalse(CompassMarkerGeometry.hit(marker, 61, 62, 8));
        int[] output = new int[3];
        CompassMarkerGeometry.locateInto(5, 5, 123, 5, 5,
                20, 30, 64, 64, output);
        assertEquals(52, output[0]);
        assertEquals(62, output[1]);
        assertEquals(1, output[2]);
    }

    private static void assertPosition(CompassMarkerGeometry.Position position,
                                       int x, int y) {
        assertEquals(x, position.getX());
        assertEquals(y, position.getY());
    }
}
