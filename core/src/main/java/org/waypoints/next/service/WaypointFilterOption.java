package org.waypoints.next.service;

/** Label/value pair for a manager filter without any Wurm GUI dependency. */
public final class WaypointFilterOption {
    private final String label;
    private final String value;

    public WaypointFilterOption(String label, String value) {
        this.label = label == null ? "" : label;
        this.value = value == null ? "" : value;
    }

    public String getLabel() { return label; }
    public String getValue() { return value; }
}
