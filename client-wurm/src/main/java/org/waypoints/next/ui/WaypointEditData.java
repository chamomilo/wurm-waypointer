package org.waypoints.next.ui;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointLayer;

import java.time.Instant;
import java.util.UUID;

/** Minimal editable Phase 1 static record projection. */
public final class WaypointEditData {
    private final UUID id;
    private final String name;
    private final double tileX;
    private final double tileY;
    private final WaypointLayer layer;
    private final MarkerStyle markerStyle;
    private final int arrivalRadiusMetres;
    private final Instant expiresAt;

    public WaypointEditData(UUID id, String name, double tileX, double tileY,
                            WaypointLayer layer, MarkerStyle markerStyle,
                            int arrivalRadiusMetres, Instant expiresAt) {
        this.id = id;
        this.name = name;
        this.tileX = tileX;
        this.tileY = tileY;
        this.layer = layer;
        if (markerStyle == null) throw new IllegalArgumentException(
                "marker style is required");
        this.markerStyle = markerStyle;
        this.arrivalRadiusMetres = WaypointArrival.requireRadius(
                arrivalRadiusMetres);
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public double getTileX() { return tileX; }
    public double getTileY() { return tileY; }
    public WaypointLayer getLayer() { return layer; }
    public MarkerStyle getMarkerStyle() { return markerStyle; }
    public int getArrivalRadiusMetres() { return arrivalRadiusMetres; }
    public Instant getExpiresAt() { return expiresAt; }
}
