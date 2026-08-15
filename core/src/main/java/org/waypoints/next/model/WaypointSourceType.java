package org.waypoints.next.model;

/** Stable source discriminator persisted by the Phase 1 schema. */
public enum WaypointSourceType {
    STATIC,
    VANILLA_SYSTEM,
    LOOT_MAP,
    DEED,
    PLAYER,
    MANAGED_ANIMAL,
    MANAGED_ITEM,
    ROUTE_POINT,
    ARCHAEOLOGY_REPORT
}
