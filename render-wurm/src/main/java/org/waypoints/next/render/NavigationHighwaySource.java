package org.waypoints.next.render;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.navigation.HighwayIndexSource;

/** Lifecycle port for an optional highway provider owned outside the renderer. */
public interface NavigationHighwaySource extends HighwayIndexSource {
    void configure(WaypointRenderConfiguration configuration);
    void activate(ServerIdentity server);
    void deactivate();
}
