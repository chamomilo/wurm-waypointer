package org.waypoints.next.surroundings;

/** Whether the object's tile is covered by the currently published deed data. */
public enum DeedStatus {
    ON_DEED("On deed"),
    OFF_DEED("Off deed"),
    UNKNOWN("Unknown");

    private final String label;

    DeedStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
