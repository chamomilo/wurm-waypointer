package org.waypoints.next.surroundings;

/** One authoritative appearance/disappearance transition for an active scan. */
public final class ScannerEvent {
    public enum Type { APPEARED, DISAPPEARED }

    private final Type type;
    private final ScannerProfile profile;
    private final SurroundingEntry entry;

    public ScannerEvent(Type type, ScannerProfile profile,
                        SurroundingEntry entry) {
        if (type == null || profile == null || entry == null) {
            throw new IllegalArgumentException("type, profile, and entry are required");
        }
        this.type = type;
        this.profile = profile;
        this.entry = entry;
    }

    public Type getType() { return type; }
    public ScannerProfile getProfile() { return profile; }
    public SurroundingEntry getEntry() { return entry; }
}
