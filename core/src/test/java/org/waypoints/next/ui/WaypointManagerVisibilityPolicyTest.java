package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WaypointManagerVisibilityPolicyTest {
    @Test
    public void closesOnlyAttachedWindowOwnedByCurrentHud() {
        assertTrue(WaypointManagerVisibilityPolicy.shouldClose(true, true, true));
        assertFalse(WaypointManagerVisibilityPolicy.shouldClose(false, true, true));
        assertFalse(WaypointManagerVisibilityPolicy.shouldClose(true, false, true));
        assertFalse(WaypointManagerVisibilityPolicy.shouldClose(true, true, false));
    }
}
