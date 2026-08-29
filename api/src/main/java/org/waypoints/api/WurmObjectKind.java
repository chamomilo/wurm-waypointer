package org.waypoints.api;

/** Public Wurm-object identity without exposing Waypointer's internal catalog types. */
public enum WurmObjectKind {
    /** Let Waypointer resolve the kind from its live object catalog. */
    AUTO,
    CREATURE,
    ITEM,
    CONTAINER
}
