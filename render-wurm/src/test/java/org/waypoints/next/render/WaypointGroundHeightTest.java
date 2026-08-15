package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WaypointGroundHeightTest {
    @Test public void usableTerrainHeightReplacesConnectionFallback() {
        assertEquals(12.75f, WaypointGroundHeight.usableOrFallback(
                12.75f, 90.0f), 0.0f);
        assertTrue(WaypointGroundHeight.usableHeight(-2.5f));
    }

    @Test public void unloadedOrInvalidTerrainKeepsSafeFallback() {
        assertEquals(90.0f, WaypointGroundHeight.usableOrFallback(
                Float.NaN, 90.0f), 0.0f);
        assertEquals(90.0f, WaypointGroundHeight.usableOrFallback(
                -3000.0f, 90.0f), 0.0f);
        assertEquals(0.0f, WaypointGroundHeight.usableOrFallback(
                Float.POSITIVE_INFINITY, Float.NaN), 0.0f);
        assertFalse(WaypointGroundHeight.usableHeight(Float.NEGATIVE_INFINITY));
    }
}
