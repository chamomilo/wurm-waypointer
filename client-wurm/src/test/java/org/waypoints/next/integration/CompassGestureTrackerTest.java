package org.waypoints.next.integration;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CompassGestureTrackerTest {
    @Test
    public void releaseWithinThresholdIsClickAndClearsState() {
        CompassGestureTracker tracker = new CompassGestureTracker(5);
        Object compass = new Object();
        tracker.pressed(compass, 100, 100);
        tracker.dragged(compass, 103, 103);

        assertTrue(tracker.released(compass, 104, 102));
        assertEquals(0, tracker.activeGestureCount());
    }

    @Test
    public void nativeScaleDragDoesNotOpenManager() {
        CompassGestureTracker tracker = new CompassGestureTracker(5);
        Object compass = new Object();
        tracker.pressed(compass, 100, 100);
        tracker.dragged(compass, 120, 100);

        assertFalse(tracker.released(compass, 120, 100));
    }

    @Test
    public void releaseWithoutPressIsIgnored() {
        assertFalse(new CompassGestureTracker(5).released(new Object(), 1, 1));
    }

    @Test
    public void markerClickHasPriorityOverGenericCompassClick() {
        CompassGestureTracker tracker = new CompassGestureTracker(5);
        Object compass = new Object();
        tracker.pressed(compass, 50, 50, true);

        assertEquals(CompassGestureTracker.ClickTarget.WAYPOINT_MARKER,
                tracker.releasedTarget(compass, 52, 51));
    }

    @Test
    public void draggingMarkerStillRemainsNativeDrag() {
        CompassGestureTracker tracker = new CompassGestureTracker(5);
        Object compass = new Object();
        tracker.pressed(compass, 50, 50, true);
        tracker.dragged(compass, 70, 50);

        assertEquals(CompassGestureTracker.ClickTarget.NONE,
                tracker.releasedTarget(compass, 70, 50));
    }
}
