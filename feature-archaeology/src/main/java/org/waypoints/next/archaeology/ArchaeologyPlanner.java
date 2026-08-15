package org.waypoints.next.archaeology;

import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.source.MapBounds;

/** Deterministic next-reading geometry from the player's factual current tile. */
public final class ArchaeologyPlanner {
    private static final int[] VERY_CLOSE_STEPS = {7, 5, 3, 2, 1, 1, 1};

    public WaypointCoordinate next(double playerTileX, double playerTileY,
                                   WaypointLayer layer,
                                   ArchaeologyDistanceBand band,
                                   ArchaeologyDirection direction,
                                   int terminalStep, MapBounds bounds) {
        if (band == null || direction == null) {
            throw new IllegalArgumentException("distance band and direction are required");
        }
        int step = band.isVeryClose()
                ? VERY_CLOSE_STEPS[Math.max(0, Math.min(
                terminalStep, VERY_CLOSE_STEPS.length - 1))]
                : band.step(direction);
        double x = playerTileX + direction.getDeltaX() * step;
        double y = playerTileY + direction.getDeltaY() * step;
        if (bounds != null) {
            x = ArchaeologyTileCoordinates.clampToMapCentre(
                    x, bounds.getWidth());
            y = ArchaeologyTileCoordinates.clampToMapCentre(
                    y, bounds.getHeight());
        }
        return new WaypointCoordinate(x, y, null,
                layer == null ? WaypointLayer.SURFACE : layer);
    }

    public boolean compatible(double playerTileX, double playerTileY,
                              double knownTileX, double knownTileY,
                              ArchaeologyDistanceBand band,
                              ArchaeologyDirection direction) {
        if (band == null || direction == null) return false;
        double dx = knownTileX - playerTileX;
        double dy = knownTileY - playerTileY;
        double chebyshev = Math.max(Math.abs(dx), Math.abs(dy));
        return band.contains(chebyshev)
                && direction == ArchaeologyDirection.fromDelta(dx, dy);
    }

    public static int nextTerminalStep(ArchaeologyDistanceBand band,
                                       int previousStep) {
        if (band == null || !band.isVeryClose()) return 0;
        return Math.min(VERY_CLOSE_STEPS.length - 1, Math.max(0, previousStep) + 1);
    }

    public static int[] veryCloseSteps() { return VERY_CLOSE_STEPS.clone(); }
}
