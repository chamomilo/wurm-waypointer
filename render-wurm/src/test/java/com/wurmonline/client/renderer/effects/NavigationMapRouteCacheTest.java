package com.wurmonline.client.renderer.effects;

import org.junit.Test;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.navigation.GroundRouteTrace;
import org.waypoints.next.navigation.HighwayRoutePlanner;
import org.waypoints.next.navigation.HighwayTileIndex;
import org.waypoints.next.navigation.NavigationTargetKey;
import java.util.Collections;
import java.util.UUID;
import static org.junit.Assert.*;

public final class NavigationMapRouteCacheTest {
    @Test public void surfaceLineSurvivesEffectReplacementButNotTargetChangeOrStop() {
        NavigationMapRouteCache cache = new NavigationMapRouteCache();
        NavigationTargetKey key = new NavigationTargetKey("server", UUID.randomUUID());
        WaypointCoordinate goal = new WaypointCoordinate(20, 4, null, WaypointLayer.SURFACE);
        GroundNavigationRouteEffect.RouteSnapshot route = surface();
        assertSame(route, cache.update(key, goal, route));
        assertSame(route, cache.update(key, goal, null));
        GroundRouteTrace.Point cave = new GroundRouteTrace.Point(4, 5, -5,
                GroundRouteTrace.HeightSource.CAVE, 0, GroundRouteTrace.WaterSource.CAVE);
        GroundNavigationRouteEffect.RouteSnapshot waitingForExit = GroundNavigationRouteEffect
                .completeMapRoute(null, Collections.singletonList(cave),
                        Collections.<GroundRouteTrace.Point>emptyList());
        assertSame(route, cache.update(key, goal, waitingForExit));
        assertNull(cache.update(key, new WaypointCoordinate(20, 5, null, WaypointLayer.SURFACE), null));
        cache.update(key, goal, route);
        assertNull(cache.update(new NavigationTargetKey("other-server", key.getWaypointId()), goal, null));
        cache.update(key, goal, route);
        assertNull(cache.update(null, null, null));
        assertNull(cache.update(key, goal, null));
    }

    private static GroundNavigationRouteEffect.RouteSnapshot surface() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2}]", 32, 32);
        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(0, 4, 20, 4, index);
        GroundRouteTrace.Point first = new GroundRouteTrace.Point(0, 4, 0,
                GroundRouteTrace.HeightSource.NEAR, 0, GroundRouteTrace.WaterSource.NEAR,
                HighwayTileIndex.Kind.ROAD, false, 0, true);
        return GroundNavigationRouteEffect.completeMapRoute(plan,
                Collections.singletonList(first), Collections.<GroundRouteTrace.Point>emptyList());
    }

    @Test public void caveOnlyMapRouteSurvivesEffectReplacement() {
        NavigationMapRouteCache cache = new NavigationMapRouteCache();
        NavigationTargetKey key = new NavigationTargetKey("server", UUID.randomUUID());
        WaypointCoordinate goal = new WaypointCoordinate(4, 6, null, WaypointLayer.CAVE);
        GroundNavigationRouteEffect.RouteSnapshot cave = GroundNavigationRouteEffect.completeMapRoute(null,
                java.util.Arrays.asList(
                        new GroundRouteTrace.Point(4, 5, -5, GroundRouteTrace.HeightSource.CAVE,
                                0, GroundRouteTrace.WaterSource.CAVE),
                        new GroundRouteTrace.Point(4, 6, -5, GroundRouteTrace.HeightSource.CAVE,
                                0, GroundRouteTrace.WaterSource.CAVE)),
                Collections.<GroundRouteTrace.Point>emptyList());
        assertSame(cave, cache.update(key, goal, cave));
        assertSame(cave, cache.update(key, goal, null));
    }
}
