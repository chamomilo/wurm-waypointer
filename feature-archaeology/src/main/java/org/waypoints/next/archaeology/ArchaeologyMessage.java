package org.waypoints.next.archaeology;

/** One strictly parsed Event message from the native archaeology-report flow. */
public final class ArchaeologyMessage {
    public enum Kind { REPORT_READY, DIRECTION, CACHE_FOUND }

    private final Kind kind;
    private final String deedName;
    private final ArchaeologyDistanceBand distanceBand;
    private final ArchaeologyDirection direction;
    private final String fingerprint;

    ArchaeologyMessage(Kind kind, String deedName,
                       ArchaeologyDistanceBand distanceBand,
                       ArchaeologyDirection direction, String fingerprint) {
        this.kind = kind;
        this.deedName = deedName;
        this.distanceBand = distanceBand;
        this.direction = direction;
        this.fingerprint = fingerprint;
    }

    public Kind getKind() { return kind; }
    public String getDeedName() { return deedName; }
    public ArchaeologyDistanceBand getDistanceBand() { return distanceBand; }
    public ArchaeologyDirection getDirection() { return direction; }
    public String getFingerprint() { return fingerprint; }
}
