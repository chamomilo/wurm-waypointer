package org.waypoints.next.render;

import org.junit.Test;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;

import static org.junit.Assert.assertEquals;

public class StaticNavigationControllerPulseTest {
    @Test public void disablingPulseKeepsANonAnimatedSolidRoute() {
        assertEquals(NavigationRouteVisualStyle.SOLID,
                StaticNavigationController.effectiveNavigationRouteVisualStyle(
                        NavigationRouteVisualStyle.PULSE, false));
        assertEquals(NavigationRouteVisualStyle.SOLID,
                StaticNavigationController.effectiveNavigationRouteVisualStyle(
                        NavigationRouteVisualStyle.MOVING_DASHES, false));
    }

    @Test public void enablingPulsePreservesTheConfiguredVisualStyle() {
        assertEquals(NavigationRouteVisualStyle.PULSE,
                StaticNavigationController.effectiveNavigationRouteVisualStyle(
                        NavigationRouteVisualStyle.PULSE, true));
        assertEquals(NavigationRouteVisualStyle.MOVING_DASHES,
                StaticNavigationController.effectiveNavigationRouteVisualStyle(
                        NavigationRouteVisualStyle.MOVING_DASHES, true));
    }
}
