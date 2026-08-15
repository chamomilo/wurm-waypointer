package org.waypoints.next.surroundings;

import java.util.Objects;

/** Session-local identity; Wurm IDs are never compared without their object kind. */
public final class SurroundingKey implements Comparable<SurroundingKey> {
    private final SurroundingKind kind;
    private final long wurmId;

    public SurroundingKey(SurroundingKind kind, long wurmId) {
        if (kind == null) throw new IllegalArgumentException("kind is required");
        this.kind = kind;
        this.wurmId = wurmId;
    }

    public SurroundingKind getKind() { return kind; }
    public long getWurmId() { return wurmId; }

    public static SurroundingKey parse(String value) {
        if (value == null) return null;
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) return null;
        try {
            return new SurroundingKey(SurroundingKind.valueOf(
                    value.substring(0, separator)), Long.parseLong(
                    value.substring(separator + 1)));
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override public int compareTo(SurroundingKey other) {
        int kindOrder = kind.compareTo(other.kind);
        return kindOrder != 0 ? kindOrder : Long.compare(wurmId, other.wurmId);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SurroundingKey)) return false;
        SurroundingKey that = (SurroundingKey) other;
        return wurmId == that.wurmId && kind == that.kind;
    }

    @Override public int hashCode() { return Objects.hash(kind, wurmId); }
    @Override public String toString() { return kind.name() + ":" + wurmId; }
}
