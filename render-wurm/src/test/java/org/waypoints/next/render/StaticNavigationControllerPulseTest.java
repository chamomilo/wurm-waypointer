package org.waypoints.next.render;

import org.junit.Test;
import org.waypoints.next.navigation.NavigationRouteVisualStyle;
import org.waypoints.next.model.ServerIdentity;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

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

    @Test public void selectingAStyleUpdatesStateAndNotifiesPersistenceSink() {
        StaticNavigationController controller = new StaticNavigationController(
                Logger.getAnonymousLogger(), new EmptyHighways());
        AtomicReference<NavigationRouteVisualStyle> saved =
                new AtomicReference<NavigationRouteVisualStyle>();
        controller.setNavigationRouteVisualStyleSink(saved::set);

        controller.selectNavigationRouteVisualStyle(
                NavigationRouteVisualStyle.PULSE);

        assertEquals(NavigationRouteVisualStyle.PULSE,
                controller.getNavigationRouteVisualStyle());
        assertEquals(NavigationRouteVisualStyle.PULSE, saved.get());
    }

    private static final class EmptyHighways implements NavigationHighwaySource {
        @Override public void configure(WaypointRenderConfiguration configuration) { }
        @Override public void activate(ServerIdentity server) { }
        @Override public void deactivate() { }
        @Override public org.waypoints.next.navigation.HighwayTileIndex current() {
            return org.waypoints.next.navigation.HighwayTileIndex.empty();
        }
        @Override public long revision() { return 0L; }
    }
}
