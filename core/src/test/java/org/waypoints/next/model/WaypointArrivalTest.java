package org.waypoints.next.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WaypointArrivalTest {
    @Test public void acceptsDisabledDefaultAndMaximumRadius() {
        assertEquals(0, WaypointArrival.requireRadius(0));
        assertEquals(20, WaypointArrival.requireRadius(20));
        assertEquals(1000, WaypointArrival.requireRadius(1000));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNegativeRadius() {
        WaypointArrival.requireRadius(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnboundedRadius() {
        WaypointArrival.requireRadius(1001);
    }
}
