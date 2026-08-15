package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class StraightTileRouteTest {
    @Test public void visitsAnEightConnectedSequenceIncludingBothEnds() {
        int[] x = new int[8];
        int[] y = new int[8];

        int count = StraightTileRoute.fill(10, 20, 14, 22, x, y);

        assertEquals(5, count);
        assertEquals(10, x[0]);
        assertEquals(20, y[0]);
        assertEquals(14, x[count - 1]);
        assertEquals(22, y[count - 1]);
        for (int i = 1; i < count; i++) {
            assertEquals(1, Math.max(Math.abs(x[i] - x[i - 1]),
                    Math.abs(y[i] - y[i - 1])));
        }
    }

    @Test public void truncatesAWorldScaleRouteToTheProvidedLocalBuffer() {
        int[] x = new int[3];
        int[] y = new int[3];

        int count = StraightTileRoute.fill(0, 0, 1000, 0, x, y);

        assertEquals(3, count);
        assertEquals(0, x[0]);
        assertEquals(2, x[2]);
    }
}
