package com.wurmonline.client.renderer.gui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NavigationRouteStatisticsWindowBridgeTest {
    @Test public void sameHudReusesRegisteredWindowAcrossTargetChanges() {
        assertFalse(NavigationRouteStatisticsWindowBridge.shouldCreateWindow(
                true, true));
        assertTrue(NavigationRouteStatisticsWindowBridge.shouldCreateWindow(
                false, true));
        assertTrue(NavigationRouteStatisticsWindowBridge.shouldCreateWindow(
                true, false));
    }
}
