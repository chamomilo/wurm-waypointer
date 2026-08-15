package org.waypoints.next.model;

/** How accurately a waypoint is currently resolved. */
public enum WaypointResolution {
    STATIC_EXACT,
    LIVE_EXACT,
    SERVER_BEARING,
    LAST_SEEN,
    PENDING,
    STALE,
    SEARCH_STEP,
    EXACT_SAVED
}
