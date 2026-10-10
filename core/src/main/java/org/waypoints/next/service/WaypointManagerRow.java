package org.waypoints.next.service;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;

import java.time.Instant;
import java.util.UUID;

/** Stable projection consumed by the Wurm manager window. */
public final class WaypointManagerRow {
    private final UUID id;
    private final boolean enabled;
    private final String name;
    private final WaypointSourceType sourceType;
    private final String serverLabel;
    private final String serverFingerprint;
    private final String user;
    private final WaypointResolution resolution;
    private final Integer distanceMetres;
    private final MarkerStyle.WorldStyle worldStyle;
    private final double tileX;
    private final double tileY;
    private final Instant expiresAt;
    private final Instant lastResolvedAt;

    WaypointManagerRow(UUID id, boolean enabled, String name,
                       WaypointSourceType sourceType, String serverLabel,
                       String serverFingerprint, String user,
                       WaypointResolution resolution, Integer distanceMetres,
                       MarkerStyle.WorldStyle worldStyle, double tileX, double tileY,
                       Instant expiresAt, Instant lastResolvedAt) {
        this.id = id;
        this.enabled = enabled;
        this.name = name;
        this.sourceType = sourceType;
        this.serverLabel = serverLabel;
        this.serverFingerprint = serverFingerprint;
        this.user = user;
        this.resolution = resolution;
        this.distanceMetres = distanceMetres;
        this.worldStyle = worldStyle;
        this.tileX = tileX;
        this.tileY = tileY;
        this.expiresAt = expiresAt;
        this.lastResolvedAt = lastResolvedAt;
    }

    public UUID getId() { return id; }
    public boolean isEnabled() { return enabled; }
    public String getName() { return name; }
    public WaypointSourceType getSourceType() { return sourceType; }
    public String getServerLabel() { return serverLabel; }
    public String getServerFingerprint() { return serverFingerprint; }
    public String getUser() { return user; }
    public WaypointResolution getResolution() { return resolution; }
    public Integer getDistanceMetres() { return distanceMetres; }
    public MarkerStyle.WorldStyle getWorldStyle() { return worldStyle; }
    public double getTileX() { return tileX; }
    public double getTileY() { return tileY; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getLastResolvedAt() { return lastResolvedAt; }
    public boolean isTemporary() { return expiresAt != null; }
    public boolean isSystemManaged() {
        return sourceType == WaypointSourceType.VANILLA_SYSTEM;
    }

    /** Dynamic/system records shown in Manager but not owned by static storage. */
    public boolean isToggleOnlyManaged() {
        return sourceType == WaypointSourceType.VANILLA_SYSTEM
                || sourceType == WaypointSourceType.LOOT_MAP;
    }

    public boolean isTrackedTarget() {
        return sourceType == WaypointSourceType.PLAYER
                || (expiresAt == null && (sourceType == WaypointSourceType.MANAGED_ANIMAL
                || sourceType == WaypointSourceType.MANAGED_ITEM));
    }

    public boolean isProviderManaged() {
        return sourceType == WaypointSourceType.DEED;
    }

    /** Age of the last provider-confirmed position, or -1 when not applicable. */
    public long getDataAgeMillis(Instant now) {
        if ((sourceType != WaypointSourceType.DEED && sourceType != WaypointSourceType.PLAYER && sourceType != WaypointSourceType.MANAGED_ANIMAL && sourceType != WaypointSourceType.MANAGED_ITEM) || lastResolvedAt == null
                || now == null) return -1L;
        return Math.max(0L, now.toEpochMilli() - lastResolvedAt.toEpochMilli());
    }

    public String getDataAgeLabel(Instant now) {
        long age = getDataAgeMillis(now);
        if (age < 0L) return "";
        long minutes = age / 60_000L;
        if (minutes < 1L) return "now";
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 48L) return hours + "h";
        return hours / 24L + "d";
    }

    public String getShortId() {
        String value = id.toString();
        return value.substring(0, Math.min(8, value.length()));
    }
}
