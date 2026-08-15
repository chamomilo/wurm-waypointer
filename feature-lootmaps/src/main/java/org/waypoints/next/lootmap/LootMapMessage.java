package org.waypoints.next.lootmap;

/** Parsed event kind; raw chat text remains outside the planner. */
public final class LootMapMessage {
    public enum Kind { READING, CHEST_DUG_UP }

    private final Kind kind;
    private final LootMapDistanceBand band;
    private final LootMapRelativeDirection direction;

    private LootMapMessage(Kind kind, LootMapDistanceBand band,
                           LootMapRelativeDirection direction) {
        this.kind = kind;
        this.band = band;
        this.direction = direction;
    }

    public static LootMapMessage reading(LootMapDistanceBand band,
                                         LootMapRelativeDirection direction) {
        return new LootMapMessage(Kind.READING, band, direction);
    }

    public static LootMapMessage chestDugUp() {
        return new LootMapMessage(Kind.CHEST_DUG_UP, null, null);
    }

    public Kind getKind() { return kind; }
    public LootMapDistanceBand getBand() { return band; }
    public LootMapRelativeDirection getDirection() { return direction; }
}
