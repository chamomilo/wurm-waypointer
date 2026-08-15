package org.waypoints.next.archaeology;

import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;

import java.time.Instant;
import java.util.UUID;

/** Exact location written only after the native hidden-cache discovery event. */
public final class KnownArchaeologyLocation {
    public static final int MODEL_VERSION = 1;

    private final String locationKey;
    private final String serverFingerprint;
    private final String deedName;
    private final String normalizedDeedName;
    private final double tileX;
    private final double tileY;
    private final WaypointLayer layer;
    private final Instant discoveredAt;
    private final Instant lastConfirmedAt;
    private final UUID sourceSessionId;
    private final boolean needsConfirmation;

    public KnownArchaeologyLocation(String serverFingerprint, String deedName,
                                    double tileX, double tileY,
                                    WaypointLayer layer,
                                    Instant discoveredAt,
                                    Instant lastConfirmedAt,
                                    UUID sourceSessionId,
                                    boolean needsConfirmation) {
        this.serverFingerprint = clean(serverFingerprint);
        this.deedName = ArchaeologyMessageParser.normalizeDeed(deedName);
        this.normalizedDeedName = ArchaeologyMessageParser.normalizedDeedKey(deedName);
        this.locationKey = ArchaeologyReportSession.reportKey(
                serverFingerprint, normalizedDeedName);
        this.tileX = finite(tileX, "known X");
        this.tileY = finite(tileY, "known Y");
        this.layer = layer == null ? WaypointLayer.SURFACE : layer;
        this.discoveredAt = discoveredAt;
        this.lastConfirmedAt = lastConfirmedAt;
        this.sourceSessionId = sourceSessionId;
        this.needsConfirmation = needsConfirmation;
        if (this.serverFingerprint.isEmpty() || this.deedName.isEmpty()
                || discoveredAt == null || lastConfirmedAt == null) {
            throw new IllegalArgumentException("known archaeology location is incomplete");
        }
    }

    public KnownArchaeologyLocation requiringConfirmation(Instant at) {
        return new KnownArchaeologyLocation(serverFingerprint, deedName,
                tileX, tileY, layer, discoveredAt,
                lastConfirmedAt, sourceSessionId, true);
    }

    public KnownArchaeologyLocation trustingSavedLocation() {
        if (!needsConfirmation) return this;
        return new KnownArchaeologyLocation(serverFingerprint, deedName,
                tileX, tileY, layer, discoveredAt,
                lastConfirmedAt, sourceSessionId, false);
    }

    KnownArchaeologyLocation migrateV1TileIndexes() {
        return new KnownArchaeologyLocation(serverFingerprint, deedName,
                ArchaeologyTileCoordinates.migrateV1(tileX),
                ArchaeologyTileCoordinates.migrateV1(tileY), layer,
                discoveredAt, lastConfirmedAt, sourceSessionId,
                needsConfirmation);
    }

    public String getLocationKey() { return locationKey; }
    public String getServerFingerprint() { return serverFingerprint; }
    public String getDeedName() { return deedName; }
    public String getNormalizedDeedName() { return normalizedDeedName; }
    public double getTileX() { return tileX; }
    public double getTileY() { return tileY; }
    public WaypointLayer getLayer() { return layer; }
    public Instant getDiscoveredAt() { return discoveredAt; }
    public Instant getLastConfirmedAt() { return lastConfirmedAt; }
    public UUID getSourceSessionId() { return sourceSessionId; }
    public boolean isNeedsConfirmation() { return needsConfirmation; }
    public WaypointCoordinate coordinate() {
        return new WaypointCoordinate(tileX, tileY, null, layer);
    }

    private static double finite(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
