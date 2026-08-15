package org.waypoints.next.render;

import org.waypoints.next.navigation.NavigationTargetKey;

/** Package-neutral bridge from render hooks to the client-owned runtime. */
public final class WaypointRenderRuntimeBridge {
    private static volatile WaypointRenderRuntimeAccess access;

    private WaypointRenderRuntimeBridge() {
    }

    public static void bind(WaypointRenderRuntimeAccess value) {
        access = value;
    }

    public static NavigationRenderFrame currentNavigationFrame() {
        WaypointRenderRuntimeAccess current = access;
        return current == null ? null : current.currentNavigationFrame();
    }

    public static CompassMarkerSnapshot currentCompassMarker() {
        WaypointRenderRuntimeAccess current = access;
        return current == null ? null : current.currentCompassMarker();
    }

    public static void chooseCompassWaypoint(NavigationTargetKey key) {
        WaypointRenderRuntimeAccess current = access;
        if (current != null && key != null) current.chooseCompassWaypoint(key);
    }
}
