package org.waypoints.next.deeds;

/** Observable provider health; it never blocks static waypoint rendering. */
public enum DeedProviderStatus {
    NO_PROVIDER,
    LOADING,
    READY,
    CACHED,
    ERROR
}
