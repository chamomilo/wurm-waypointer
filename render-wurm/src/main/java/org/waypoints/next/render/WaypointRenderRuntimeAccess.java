package org.waypoints.next.render;

import org.waypoints.next.navigation.NavigationTargetKey;

/**
 * Runtime data and callbacks consumed by HUD rendering.
 *
 * The client adapter binds this interface once; renderer classes never import
 * or call the client runtime directly.
 */
public interface WaypointRenderRuntimeAccess {
    NavigationRenderFrame currentNavigationFrame();
    CompassMarkerSnapshot currentCompassMarker();
    void chooseCompassWaypoint(NavigationTargetKey key);
}
