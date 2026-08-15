package org.waypoints.next.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WaypointManagerWindowSizingTest {
    @Test public void listRemainsVerticallyResizable() {
        assertEquals(300, WaypointManagerWindowSizing.height(true, 100, 430));
        assertEquals(700, WaypointManagerWindowSizing.height(true, 700, 430));
    }

    @Test public void editorIgnoresVerticalResizeRequests() {
        assertEquals(430, WaypointManagerWindowSizing.height(false, 100, 430));
        assertEquals(430, WaypointManagerWindowSizing.height(false, 900, 430));
    }
}
