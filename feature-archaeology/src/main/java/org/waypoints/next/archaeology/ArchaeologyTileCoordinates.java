package org.waypoints.next.archaeology;

/** Conversion between Wurm integer tile indexes and canonical tile centres. */
public final class ArchaeologyTileCoordinates {
    private static final double HALF_TILE = 0.5d;

    private ArchaeologyTileCoordinates() { }

    public static double centerOf(int tileIndex) {
        if (tileIndex < 0) {
            throw new IllegalArgumentException("tile index must not be negative");
        }
        return tileIndex + HALF_TILE;
    }

    /** Format v1 stored integer tile indexes rather than fractional centres. */
    static double migrateV1(double tileIndex) {
        if (Double.isNaN(tileIndex) || Double.isInfinite(tileIndex)) {
            throw new IllegalArgumentException("legacy tile index must be finite");
        }
        return tileIndex + HALF_TILE;
    }

    static double clampToMapCentre(double coordinate, int mapSize) {
        if (mapSize <= 0) {
            throw new IllegalArgumentException("map size must be positive");
        }
        return Math.max(HALF_TILE,
                Math.min(mapSize - HALF_TILE, coordinate));
    }
}
