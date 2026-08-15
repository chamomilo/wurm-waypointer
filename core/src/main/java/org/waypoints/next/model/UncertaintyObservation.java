package org.waypoints.next.model;

import java.time.Instant;
import java.util.Objects;

/** One bounded bearing/range observation for an approximate future source. */
public final class UncertaintyObservation {
    private final double bearingDegrees;
    private final double halfWidthDegrees;
    private final double minimumTiles;
    private final double maximumTiles;
    private final Instant observedAt;

    public UncertaintyObservation(double bearingDegrees, double halfWidthDegrees,
                                  double minimumTiles, double maximumTiles,
                                  Instant observedAt) {
        if (!finite(bearingDegrees) || bearingDegrees < 0.0d || bearingDegrees >= 360.0d) {
            throw new IllegalArgumentException("bearing must be in 0..<360");
        }
        if (!finite(halfWidthDegrees) || halfWidthDegrees <= 0.0d
                || halfWidthDegrees > 180.0d) {
            throw new IllegalArgumentException("bearing half-width must be in 0..180");
        }
        if (!finite(minimumTiles) || !finite(maximumTiles) || minimumTiles < 0.0d
                || maximumTiles < minimumTiles) {
            throw new IllegalArgumentException("range must be finite and ordered");
        }
        if (observedAt == null) throw new IllegalArgumentException("observation time is required");
        this.bearingDegrees = bearingDegrees;
        this.halfWidthDegrees = halfWidthDegrees;
        this.minimumTiles = minimumTiles;
        this.maximumTiles = maximumTiles;
        this.observedAt = observedAt;
    }

    public double getBearingDegrees() { return bearingDegrees; }
    public double getHalfWidthDegrees() { return halfWidthDegrees; }
    public double getMinimumTiles() { return minimumTiles; }
    public double getMaximumTiles() { return maximumTiles; }
    public Instant getObservedAt() { return observedAt; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof UncertaintyObservation)) return false;
        UncertaintyObservation that = (UncertaintyObservation) other;
        return Double.compare(bearingDegrees, that.bearingDegrees) == 0
                && Double.compare(halfWidthDegrees, that.halfWidthDegrees) == 0
                && Double.compare(minimumTiles, that.minimumTiles) == 0
                && Double.compare(maximumTiles, that.maximumTiles) == 0
                && observedAt.equals(that.observedAt);
    }

    @Override public int hashCode() {
        return Objects.hash(bearingDegrees, halfWidthDegrees, minimumTiles,
                maximumTiles, observedAt);
    }
}
