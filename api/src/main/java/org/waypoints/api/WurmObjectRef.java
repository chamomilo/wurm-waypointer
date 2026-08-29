package org.waypoints.api;

import java.util.Objects;

/** Stable reference to one creature, ground item, or container in the live world. */
public final class WurmObjectRef {
    private final WurmObjectKind kind;
    private final long wurmId;

    public WurmObjectRef(WurmObjectKind kind, long wurmId) {
        if (kind == null) throw new IllegalArgumentException("object kind is required");
        this.kind = kind;
        this.wurmId = wurmId;
    }

    public WurmObjectKind getKind() { return kind; }
    public long getWurmId() { return wurmId; }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WurmObjectRef)) return false;
        WurmObjectRef that = (WurmObjectRef) other;
        return wurmId == that.wurmId && kind == that.kind;
    }

    @Override public int hashCode() { return Objects.hash(kind, wurmId); }
    @Override public String toString() { return kind.name() + ":" + wurmId; }
}
