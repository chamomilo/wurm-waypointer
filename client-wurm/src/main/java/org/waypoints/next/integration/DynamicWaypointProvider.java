package org.waypoints.next.integration;

import org.waypoints.next.navigation.NavigationTargetKey;
import org.waypoints.next.service.WaypointRevisionSnapshot;

/** Common lifecycle seam for dynamic waypoint features. */
interface DynamicWaypointProvider {
    void configure(WaypointClientConfiguration configuration);
    WaypointRevisionSnapshot combine(WaypointRevisionSnapshot base);
    NavigationTargetKey pollNavigationRequest();
    String pollMessage();
    void observeAction(long[] targets, String actionName);
    void connectionEnded();
    String navigationReason();
}
