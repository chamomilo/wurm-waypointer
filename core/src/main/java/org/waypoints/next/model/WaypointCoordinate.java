package org.waypoints.next.model;

import java.util.Objects;

/** Canonical fractional tile coordinate. World-meter conversion belongs at the client edge. */
public final class WaypointCoordinate {
    private final double tileX;
    private final double tileY;
    private final Double height;
    private final WaypointLayer layer;

    public WaypointCoordinate(double tileX, double tileY, Double height,
                              WaypointLayer layer) {
        this.tileX = finiteNonNegative(tileX, "tile X");
        this.tileY = finiteNonNegative(tileY, "tile Y");
        if (height != null && (height.isNaN() || height.isInfinite())) {
            throw new IllegalArgumentException("height must be finite when present");
        }
        if (layer == null) throw new IllegalArgumentException("layer is required");
        this.height = height;
        this.layer = layer;
    }

    public double getTileX() { return tileX; }
    public double getTileY() { return tileY; }
    public Double getHeight() { return height; }
    public WaypointLayer getLayer() { return layer; }

    public double worldX() { return tileX * 4.0d; }
    public double worldY() { return tileY * 4.0d; }

    private static double finiteNonNegative(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
        return value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof WaypointCoordinate)) return false;
        WaypointCoordinate that = (WaypointCoordinate) other;
        return Double.compare(tileX, that.tileX) == 0
                && Double.compare(tileY, that.tileY) == 0
                && Objects.equals(height, that.height) && layer == that.layer;
    }

    @Override public int hashCode() {
        return Objects.hash(tileX, tileY, height, layer);
    }
}
