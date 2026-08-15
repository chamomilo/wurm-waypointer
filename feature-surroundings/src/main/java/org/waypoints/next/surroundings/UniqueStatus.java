package org.waypoints.next.surroundings;

/** Creature-template classification exposed independently from Wurm modifiers. */
public enum UniqueStatus {
    UNIQUE("Unique"),
    NON_UNIQUE("Non-unique");

    private final String label;

    UniqueStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
