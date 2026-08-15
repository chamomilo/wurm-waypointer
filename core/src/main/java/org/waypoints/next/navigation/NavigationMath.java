package org.waypoints.next.navigation;

/** Pure Wurm-coordinate bearing and distance math. */
public final class NavigationMath {
    private NavigationMath() { }

    /** Wurm bearing: 0=north, 90=east, and world Y grows southward. */
    public static double absoluteBearingDegrees(double originX, double originY,
                                                double targetX, double targetY) {
        requireFinite(originX, "origin X");
        requireFinite(originY, "origin Y");
        requireFinite(targetX, "target X");
        requireFinite(targetY, "target Y");
        if (originX == targetX && originY == targetY) return 0.0d;
        return normalizeDegrees(Math.toDegrees(
                Math.atan2(targetX - originX, -(targetY - originY))));
    }

    public static double relativeBearingDegrees(double originX, double originY,
                                                double targetX, double targetY,
                                                double playerFacingDegrees) {
        requireFinite(playerFacingDegrees, "player facing");
        if (originX == targetX && originY == targetY) return 0.0d;
        return normalizeSignedDegrees(absoluteBearingDegrees(
                originX, originY, targetX, targetY) - playerFacingDegrees);
    }

    public static int distanceMetres(double originTileX, double originTileY,
                                     double targetTileX, double targetTileY) {
        requireFinite(originTileX, "origin tile X");
        requireFinite(originTileY, "origin tile Y");
        requireFinite(targetTileX, "target tile X");
        requireFinite(targetTileY, "target tile Y");
        double dx = (targetTileX - originTileX) * 4.0d;
        double dy = (targetTileY - originTileY) * 4.0d;
        double distance = Math.sqrt(dx * dx + dy * dy);
        return distance >= Integer.MAX_VALUE ? Integer.MAX_VALUE
                : Math.max(0, (int) Math.round(distance));
    }

    public static double normalizeDegrees(double degrees) {
        requireFinite(degrees, "degrees");
        double result = degrees % 360.0d;
        return result < 0.0d ? result + 360.0d : result;
    }

    public static double normalizeSignedDegrees(double degrees) {
        double result = normalizeDegrees(degrees);
        return result > 180.0d ? result - 360.0d : result;
    }

    private static void requireFinite(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
