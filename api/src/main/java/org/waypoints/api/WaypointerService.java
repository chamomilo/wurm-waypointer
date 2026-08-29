package org.waypoints.api;

import java.util.Set;
import java.util.UUID;

/** Runtime service implemented by Waypointer and consumed through {@link WaypointerApi}. */
public interface WaypointerService {
    int apiVersion();
    Set<WaypointerCapability> capabilities();
    MarkResult markObject(ObjectMarkRequest request);
    int subjectVanished(WurmObjectRef subject);
    boolean removeOwnedMarker(String ownerId, UUID markerId);
    boolean setNavigation(String ownerId, UUID markerId, boolean active);
}
