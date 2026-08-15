package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NavigationMathTest {
    @Test public void absoluteBearingCoversEveryQuadrant() {
        assertEquals(0.0d, NavigationMath.absoluteBearingDegrees(0, 0, 0, -1), 0.0001d);
        assertEquals(45.0d, NavigationMath.absoluteBearingDegrees(0, 0, 1, -1), 0.0001d);
        assertEquals(90.0d, NavigationMath.absoluteBearingDegrees(0, 0, 1, 0), 0.0001d);
        assertEquals(135.0d, NavigationMath.absoluteBearingDegrees(0, 0, 1, 1), 0.0001d);
        assertEquals(180.0d, NavigationMath.absoluteBearingDegrees(0, 0, 0, 1), 0.0001d);
        assertEquals(225.0d, NavigationMath.absoluteBearingDegrees(0, 0, -1, 1), 0.0001d);
        assertEquals(270.0d, NavigationMath.absoluteBearingDegrees(0, 0, -1, 0), 0.0001d);
        assertEquals(315.0d, NavigationMath.absoluteBearingDegrees(0, 0, -1, -1), 0.0001d);
    }

    @Test public void facingAndZeroWrapUseSignedShortestTurn() {
        assertEquals(0.0d, NavigationMath.relativeBearingDegrees(0, 0, 1, 0, 90), 0.0001d);
        assertEquals(-20.0d, NavigationMath.normalizeSignedDegrees(350 - 10), 0.0001d);
        assertEquals(20.0d, NavigationMath.normalizeSignedDegrees(10 - 350), 0.0001d);
        assertEquals(-90.0d, NavigationMath.relativeBearingDegrees(0, 0, 0, -1, 90), 0.0001d);
    }

    @Test public void coincidentPositionIsStableAndDistanceUsesWurmTiles() {
        assertEquals(0.0d, NavigationMath.absoluteBearingDegrees(5, 5, 5, 5), 0.0d);
        assertEquals(0.0d, NavigationMath.relativeBearingDegrees(5, 5, 5, 5, 271), 0.0d);
        assertEquals(20, NavigationMath.distanceMetres(0, 0, 3, 4));
    }
}
