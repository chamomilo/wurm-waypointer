package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WaypointManagerTableLayoutTest {
    @Test public void narrowWindowIsClampedAndKeepsEveryColumnVisible() {
        int minimum = WaypointManagerTableLayout.minimumWindowWidth();
        int[] columns = WaypointManagerTableLayout.columns(320);
        assertEquals(WaypointManagerTableLayout.COLUMN_COUNT, columns.length);
        assertEquals(WaypointManagerTableLayout.contentWidth(minimum), sum(columns));
        for (int width : columns) assertTrue(width >= 42);
    }

    @Test public void columnsShrinkAndExpandWithWindow() {
        int minimum = WaypointManagerTableLayout.minimumWindowWidth();
        int[] narrow = WaypointManagerTableLayout.columns(minimum);
        int preferredWindow = 1134;
        int[] normal = WaypointManagerTableLayout.columns(preferredWindow);
        int[] wide = WaypointManagerTableLayout.columns(1367);
        assertEquals(WaypointManagerTableLayout.contentWidth(minimum), sum(narrow));
        assertEquals(WaypointManagerTableLayout.contentWidth(preferredWindow), sum(normal));
        assertEquals(WaypointManagerTableLayout.contentWidth(1367), sum(wide));
        assertTrue(narrow[2] < normal[2]);
        assertTrue(normal[2] < wide[2]);
        assertTrue(normal[4] < wide[4]);
        assertEquals(normal[0], wide[0]);
        assertEquals(normal[9], wide[9]);
    }

    private static int sum(int[] values) {
        int result = 0;
        for (int value : values) result += value;
        return result;
    }
}
