package com.wurmonline.client.renderer.effects;

import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.navigation.NavigationTargetKey;

/** Keeps an already built map line during effect replacement and cave receipt gaps. */
public final class NavigationMapRouteCache {
    private NavigationTargetKey key;
    private WaypointCoordinate coordinate;
    private GroundNavigationRouteEffect.RouteSnapshot route;

    public GroundNavigationRouteEffect.RouteSnapshot update(NavigationTargetKey nextKey,
            WaypointCoordinate nextCoordinate, GroundNavigationRouteEffect.RouteSnapshot candidate) {
        if (nextKey == null || nextCoordinate == null) {
            clear();
            return null;
        }
        if (!nextKey.equals(key) || !nextCoordinate.equals(coordinate)) {
            clear();
            key = nextKey;
            coordinate = nextCoordinate;
        }
        if (candidate != null && candidate.getPointCount() >= 2) route = candidate;
        return route == null ? candidate : route;
    }

    public void clear() { key = null; coordinate = null; route = null; }
}
