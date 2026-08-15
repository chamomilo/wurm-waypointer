package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WaypointColorPickerStateTest {
    @Test public void editInitializesHsvSwatchAndHexFromWaypointRgb() {
        WaypointColorPickerState state = new WaypointColorPickerState(
                1.0f, 0.3f, 0.15f);

        assertTrue(state.matches(1.0f, 0.3f, 0.15f));
        assertEquals("#FF4D26", state.getHex());
        assertEquals(0.85f, state.getSaturation(), 0.0001f);
        assertEquals(1.0f, state.getValue(), 0.0001f);
    }

    @Test public void bindingReplacesStaleWhitePickerState() {
        WaypointColorPickerState state = new WaypointColorPickerState(
                1.0f, 1.0f, 1.0f);

        state.setColor(0.2f, 0.6f, 1.0f);

        assertTrue(state.matches(0.2f, 0.6f, 1.0f));
        assertEquals("#3399FF", state.getHex());
    }
}
