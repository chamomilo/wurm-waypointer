package org.waypoints.next.lootmap;

/** Eight relative phrases emitted by Wurm's locate-style messages. */
public enum LootMapRelativeDirection {
    AHEAD(0.0d), AHEAD_RIGHT(45.0d), RIGHT(90.0d), BEHIND_RIGHT(135.0d),
    BEHIND(180.0d), BEHIND_LEFT(-135.0d), LEFT(-90.0d), AHEAD_LEFT(-45.0d);

    private final double offsetDegrees;

    LootMapRelativeDirection(double offsetDegrees) {
        this.offsetDegrees = offsetDegrees;
    }

    public double getOffsetDegrees() { return offsetDegrees; }
}
