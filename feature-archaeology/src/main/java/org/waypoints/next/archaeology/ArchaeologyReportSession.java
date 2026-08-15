package org.waypoints.next.archaeology;

import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Versioned persistent state for one server/deed navigation session. */
public final class ArchaeologyReportSession {
    public static final int MODEL_VERSION = 1;

    private final UUID id;
    private final String reportKey;
    private final String serverFingerprint;
    private final String user;
    private final String deedName;
    private final String normalizedDeedName;
    private final Long reportItemId;
    private final ArchaeologyReportStatus status;
    private final double lastPlayerTileX;
    private final double lastPlayerTileY;
    private final WaypointLayer lastPlayerLayer;
    private final double waypointTileX;
    private final double waypointTileY;
    private final WaypointLayer waypointLayer;
    private final ArchaeologyDistanceBand distanceBand;
    private final ArchaeologyDirection direction;
    private final int terminalStep;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String lastEventFingerprint;
    private final boolean readyChimed;

    private ArchaeologyReportSession(UUID id, String reportKey,
                                     String serverFingerprint, String user,
                                     String deedName, String normalizedDeedName,
                                     Long reportItemId,
                                     ArchaeologyReportStatus status,
                                     double lastPlayerTileX, double lastPlayerTileY,
                                     WaypointLayer lastPlayerLayer,
                                     double waypointTileX, double waypointTileY,
                                     WaypointLayer waypointLayer,
                                     ArchaeologyDistanceBand distanceBand,
                                     ArchaeologyDirection direction,
                                     int terminalStep, Instant createdAt,
                                     Instant updatedAt,
                                     String lastEventFingerprint,
                                     boolean readyChimed) {
        this.id = id;
        this.reportKey = clean(reportKey);
        this.serverFingerprint = clean(serverFingerprint);
        this.user = clean(user);
        this.deedName = ArchaeologyMessageParser.normalizeDeed(deedName);
        this.normalizedDeedName = clean(normalizedDeedName);
        this.reportItemId = reportItemId;
        this.status = status;
        this.lastPlayerTileX = finite(lastPlayerTileX, "last player X");
        this.lastPlayerTileY = finite(lastPlayerTileY, "last player Y");
        this.lastPlayerLayer = lastPlayerLayer == null
                ? WaypointLayer.SURFACE : lastPlayerLayer;
        this.waypointTileX = finite(waypointTileX, "waypoint X");
        this.waypointTileY = finite(waypointTileY, "waypoint Y");
        this.waypointLayer = waypointLayer == null
                ? WaypointLayer.SURFACE : waypointLayer;
        this.distanceBand = distanceBand;
        this.direction = direction;
        this.terminalStep = Math.max(0, terminalStep);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.lastEventFingerprint = clean(lastEventFingerprint);
        this.readyChimed = readyChimed;
        validate();
    }

    public static ArchaeologyReportSession create(String serverFingerprint,
                                                   String user, String deedName,
                                                   double playerTileX,
                                                   double playerTileY,
                                                   WaypointLayer playerLayer,
                                                   Instant at) {
        String normalized = ArchaeologyMessageParser.normalizedDeedKey(deedName);
        String reportKey = reportKey(serverFingerprint, normalized);
        return new ArchaeologyReportSession(UUID.randomUUID(), reportKey,
                serverFingerprint, user, deedName, normalized, null,
                ArchaeologyReportStatus.REPORT_READY,
                playerTileX, playerTileY, playerLayer,
                playerTileX, playerTileY, playerLayer,
                null, null, 0, at, at, "", false);
    }

    public static ArchaeologyReportSession restore(UUID id, String reportKey,
                                                    String serverFingerprint,
                                                    String user, String deedName,
                                                    String normalizedDeedName,
                                                    Long reportItemId,
                                                    ArchaeologyReportStatus status,
                                                    double lastPlayerTileX,
                                                    double lastPlayerTileY,
                                                    WaypointLayer lastPlayerLayer,
                                                    double waypointTileX,
                                                    double waypointTileY,
                                                    WaypointLayer waypointLayer,
                                                    ArchaeologyDistanceBand distanceBand,
                                                    ArchaeologyDirection direction,
                                                    int terminalStep,
                                                    Instant createdAt,
                                                    Instant updatedAt,
                                                    String lastEventFingerprint,
                                                    boolean readyChimed) {
        return new ArchaeologyReportSession(id, reportKey, serverFingerprint,
                user, deedName, normalizedDeedName, reportItemId, status,
                lastPlayerTileX, lastPlayerTileY, lastPlayerLayer,
                waypointTileX, waypointTileY, waypointLayer, distanceBand,
                direction, terminalStep, createdAt, updatedAt,
                lastEventFingerprint, readyChimed);
    }

    public ArchaeologyReportSession transition(ArchaeologyReportStatus nextStatus,
                                                double playerTileX,
                                                double playerTileY,
                                                WaypointLayer playerLayer,
                                                WaypointCoordinate waypoint,
                                                ArchaeologyDistanceBand nextBand,
                                                ArchaeologyDirection nextDirection,
                                                int nextTerminalStep,
                                                String eventFingerprint,
                                                Instant at,
                                                boolean hasReadyChimed) {
        return new ArchaeologyReportSession(id, reportKey, serverFingerprint,
                user, deedName, normalizedDeedName, reportItemId, nextStatus,
                playerTileX, playerTileY, playerLayer,
                waypoint == null ? waypointTileX : waypoint.getTileX(),
                waypoint == null ? waypointTileY : waypoint.getTileY(),
                waypoint == null ? waypointLayer : waypoint.getLayer(),
                nextBand, nextDirection, nextTerminalStep, createdAt, at,
                eventFingerprint, readyChimed || hasReadyChimed);
    }

    public ArchaeologyReportSession withReportItemId(Long value, Instant at) {
        return new ArchaeologyReportSession(id, reportKey, serverFingerprint,
                user, deedName, normalizedDeedName, value, status,
                lastPlayerTileX, lastPlayerTileY, lastPlayerLayer,
                waypointTileX, waypointTileY, waypointLayer,
                distanceBand, direction, terminalStep, createdAt, at,
                lastEventFingerprint, readyChimed);
    }

    ArchaeologyReportSession migrateV1TileIndexes() {
        return new ArchaeologyReportSession(id, reportKey, serverFingerprint,
                user, deedName, normalizedDeedName, reportItemId, status,
                ArchaeologyTileCoordinates.migrateV1(lastPlayerTileX),
                ArchaeologyTileCoordinates.migrateV1(lastPlayerTileY),
                lastPlayerLayer,
                ArchaeologyTileCoordinates.migrateV1(waypointTileX),
                ArchaeologyTileCoordinates.migrateV1(waypointTileY),
                waypointLayer, distanceBand, direction, terminalStep,
                createdAt, updatedAt, lastEventFingerprint, readyChimed);
    }

    public UUID getId() { return id; }
    public String getReportKey() { return reportKey; }
    public String getServerFingerprint() { return serverFingerprint; }
    public String getUser() { return user; }
    public String getDeedName() { return deedName; }
    public String getNormalizedDeedName() { return normalizedDeedName; }
    public Long getReportItemId() { return reportItemId; }
    public ArchaeologyReportStatus getStatus() { return status; }
    public double getLastPlayerTileX() { return lastPlayerTileX; }
    public double getLastPlayerTileY() { return lastPlayerTileY; }
    public WaypointLayer getLastPlayerLayer() { return lastPlayerLayer; }
    public double getWaypointTileX() { return waypointTileX; }
    public double getWaypointTileY() { return waypointTileY; }
    public WaypointLayer getWaypointLayer() { return waypointLayer; }
    public ArchaeologyDistanceBand getDistanceBand() { return distanceBand; }
    public ArchaeologyDirection getDirection() { return direction; }
    public int getTerminalStep() { return terminalStep; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getLastEventFingerprint() { return lastEventFingerprint; }
    public boolean isReadyChimed() { return readyChimed; }
    public boolean isActive() { return status.isActive(); }
    public WaypointCoordinate getWaypointCoordinate() {
        return new WaypointCoordinate(waypointTileX, waypointTileY, null,
                waypointLayer);
    }
    public String getSessionKey() {
        return user.toLowerCase(Locale.ENGLISH) + "|" + reportKey;
    }

    public static String reportKey(String serverFingerprint,
                                   String normalizedDeedName) {
        return clean(serverFingerprint) + "|" + clean(normalizedDeedName);
    }

    private void validate() {
        if (id == null || reportKey.isEmpty() || serverFingerprint.isEmpty()
                || user.isEmpty() || deedName.isEmpty()
                || normalizedDeedName.isEmpty() || status == null
                || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("archaeology session is incomplete");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("session update precedes creation");
        }
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
