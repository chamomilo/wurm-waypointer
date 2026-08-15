package org.waypoints.next.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class WaypointLabelOpacityTest {
    @Test public void labelsStayFullyBrightForEveryWorldEffectAlpha() {
        assertEquals(1.0f, WaypointLabelOpacity.textAlpha(0.0f), 0.0f);
        assertEquals(1.0f, WaypointLabelOpacity.textAlpha(0.5f), 0.0f);
        assertEquals(1.0f, WaypointLabelOpacity.textAlpha(1.0f), 0.0f);
    }
}
