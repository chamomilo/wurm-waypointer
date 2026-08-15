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

    @Test public void userCloseSuppressesOnlyCurrentTargetOnCurrentHud() {
        assertTrue(NavigationRouteStatisticsWindowBridge.shouldRemainDismissed(
                true, true, true));
        assertFalse(NavigationRouteStatisticsWindowBridge.shouldRemainDismissed(
                true, true, false));
        assertFalse(NavigationRouteStatisticsWindowBridge.shouldRemainDismissed(
                true, false, true));
        assertFalse(NavigationRouteStatisticsWindowBridge.shouldRemainDismissed(
                false, true, true));
    }
}
