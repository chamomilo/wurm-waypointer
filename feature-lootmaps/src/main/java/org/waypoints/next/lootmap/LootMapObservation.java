package org.waypoints.next.lootmap;

import org.waypoints.next.navigation.NavigationMath;

import java.time.Instant;

/** One immutable reading captured at the player's actual tile and facing. */
public final class LootMapObservation {
    private final double originX;
    private final double originY;
    private final double playerFacingDegrees;
    private final LootMapRelativeDirection relativeDirection;
    private final double absoluteSectorDegrees;
    private final LootMapDistanceBand band;
    private final Instant observedAt;

    public LootMapObservation(double originX, double originY,
                              double playerFacingDegrees,
                              LootMapRelativeDirection relativeDirection,
                              LootMapDistanceBand band, Instant observedAt) {
        if (!finite(originX) || !finite(originY) || originX < 0.0d || originY < 0.0d) {
            throw new IllegalArgumentException("origin must be finite and non-negative");
        }
        if (!finite(playerFacingDegrees)) {
            throw new IllegalArgumentException("player facing must be finite");
        }
        if (relativeDirection == null || band == null || observedAt == null) {
            throw new IllegalArgumentException("direction, band and time are required");
        }
        this.originX = originX;
        this.originY = originY;
        this.playerFacingDegrees = NavigationMath.normalizeDegrees(playerFacingDegrees);
        this.relativeDirection = relativeDirection;
        // The server first quantizes the absolute target direction and only
        // then expresses it relative to the player's rotation. Recover that
        // absolute octant by quantizing the facing at the same 45-degree scale.
        double facingOctant = Math.floor((this.playerFacingDegrees + 22.5d) / 45.0d) * 45.0d;
        this.absoluteSectorDegrees = NavigationMath.normalizeDegrees(
                facingOctant + relativeDirection.getOffsetDegrees());
        this.band = band;
        this.observedAt = observedAt;
    }

    public double getOriginX() { return originX; }
    public double getOriginY() { return originY; }
    public double getPlayerFacingDegrees() { return playerFacingDegrees; }
    public LootMapRelativeDirection getRelativeDirection() { return relativeDirection; }
    public double getAbsoluteSectorDegrees() { return absoluteSectorDegrees; }
    public LootMapDistanceBand getBand() { return band; }
    public Instant getObservedAt() { return observedAt; }

    public boolean accepts(double targetX, double targetY) {
        double dx = targetX - originX;
        double dy = targetY - originY;
        int distance = (int) Math.floor(Math.sqrt(dx * dx + dy * dy));
        if (!band.containsFlooredDistance(distance)) return false;
        if (distance == 0) return band == LootMapDistanceBand.EXACT;
        double bearing = NavigationMath.absoluteBearingDegrees(
                originX, originY, targetX, targetY);
        double delta = NavigationMath.normalizeSignedDegrees(
                bearing - absoluteSectorDegrees);
        return Math.abs(delta) <= 22.5d + 1.0e-9d;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
