package org.waypoints.next.ui;

import org.waypoints.next.surroundings.SurroundingKey;
import org.waypoints.next.surroundings.SurroundingsQuery;
import org.waypoints.next.surroundings.SurroundingsSnapshot;

import java.util.Collection;

/** Port used by the native Surroundings window. */
public interface SurroundingsController {
    SurroundingsSnapshot snapshot(SurroundingsQuery query);
    void setWaypoint(SurroundingKey key, boolean enabled);
    void setWaypoints(Collection<SurroundingKey> keys, boolean enabled);
    void clearAllWaypoints();
    void openWaypointManager();
    long revision();
    void reportFailure(String operation, Throwable failure);
}
