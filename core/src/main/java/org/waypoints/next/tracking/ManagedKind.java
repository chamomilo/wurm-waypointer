package org.waypoints.next.tracking;

/** The three read-only vanilla Manage lists. */
public enum ManagedKind {
    ANIMAL(663), VEHICLE(665), SHIP(668), PLAYER(0);
    private final int action;
    ManagedKind(int action) { this.action = action; }
    public int action() { return action; }
}
