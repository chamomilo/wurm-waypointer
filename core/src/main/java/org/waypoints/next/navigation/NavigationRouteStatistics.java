package org.waypoints.next.navigation;

import java.util.Collections;
import java.util.List;

/** Immutable summary of the selected route, including staged future sections. */
public final class NavigationRouteStatistics {
    public static final float OFFROAD_SPEED_KILOMETRES_PER_HOUR = 8.0f;
    public static final float ROAD_SPEED_KILOMETRES_PER_HOUR = 16.0f;
    public static final float HIGHWAY_SPEED_KILOMETRES_PER_HOUR = 24.0f;
    private static final double OFFROAD_SPEED_METRES_PER_SECOND =
            OFFROAD_SPEED_KILOMETRES_PER_HOUR / 3.6d;

    private final int pointCount;
    private final double lengthMetres;
    private final long estimatedDurationSeconds;
    private final boolean hasEndpoint;
    private final int endpointTileX;
    private final int endpointTileY;
    private final int targetTileX;
    private final int targetTileY;
    private final boolean reachedTarget;

    private NavigationRouteStatistics(int pointCount, double lengthMetres,
                                      long estimatedDurationSeconds,
                                      boolean hasEndpoint, int endpointTileX,
                                      int endpointTileY, int targetTileX,
                                      int targetTileY, boolean reachedTarget) {
        this.pointCount = pointCount;
        this.lengthMetres = lengthMetres;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
        this.hasEndpoint = hasEndpoint;
        this.endpointTileX = endpointTileX;
        this.endpointTileY = endpointTileY;
        this.targetTileX = targetTileX;
        this.targetTileY = targetTileY;
        this.reachedTarget = reachedTarget;
    }

    public static NavigationRouteStatistics empty(int targetTileX,
                                                  int targetTileY) {
        return calculate(Collections.<GroundRouteTrace.Point>emptyList(),
                false, targetTileX, targetTileY, 0.0f, 0.0f);
    }

    public static NavigationRouteStatistics calculate(
            List<GroundRouteTrace.Point> route, boolean reachedTarget,
            int targetTileX, int targetTileY, float maximumSlopeDirt,
            float maximumWaterDepthMetres) {
        List<GroundRouteTrace.Point> points = route == null
                ? Collections.<GroundRouteTrace.Point>emptyList() : route;
        double length = 0.0d;
        double duration = 0.0d;
        for (int i = 1; i < points.size(); i++) {
            GroundRouteTrace.Point from = points.get(i - 1);
            GroundRouteTrace.Point to = points.get(i);
            double deltaX = (to.getTileX() - from.getTileX())
                    * GroundRouteTrace.TILE_SIZE_METRES;
            double deltaY = (to.getTileY() - from.getTileY())
                    * GroundRouteTrace.TILE_SIZE_METRES;
            double horizontal = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (!positiveFinite(horizontal)) continue;
            length += horizontal;
            duration += estimatedSegmentSeconds(from, to, horizontal,
                    maximumSlopeDirt, maximumWaterDepthMetres);
        }

        boolean hasEndpoint = !points.isEmpty();
        GroundRouteTrace.Point endpoint = hasEndpoint
                ? points.get(points.size() - 1) : null;
        boolean actuallyReached = reachedTarget && endpoint != null
                && endpoint.getTileX() == targetTileX
                && endpoint.getTileY() == targetTileY;
        return new NavigationRouteStatistics(points.size(), length,
                (long) Math.ceil(duration), hasEndpoint,
                endpoint == null ? 0 : endpoint.getTileX(),
                endpoint == null ? 0 : endpoint.getTileY(),
                targetTileX, targetTileY, actuallyReached);
    }

    /**
     * Summarizes the complete sparse Highway plan, including access from the
     * live start and its continuation from the last network tile to the final
     * target. This is intentionally independent of the currently renderable
     * staged prefix.
     */
    public static NavigationRouteStatistics calculateCompleteHighwayPlan(
            HighwayRoutePlanner.Plan plan, int startTileX, int startTileY,
            int targetTileX, int targetTileY) {
        if (plan == null || plan.getHighwaySteps().isEmpty()) {
            return empty(targetTileX, targetTileY);
        }
        List<HighwayRoutePlanner.TileStep> steps = plan.getHighwaySteps();
        double lengthTiles = 0.0d;
        int points = 0;
        int previousX = startTileX;
        int previousY = startTileY;
        points++;
        for (HighwayRoutePlanner.TileStep step : steps) {
            if (step.getTileX() == previousX
                    && step.getTileY() == previousY) continue;
            lengthTiles += Math.hypot(step.getTileX() - previousX,
                    step.getTileY() - previousY);
            previousX = step.getTileX();
            previousY = step.getTileY();
            points++;
        }
        if (previousX != targetTileX || previousY != targetTileY) {
            lengthTiles += Math.hypot(targetTileX - previousX,
                    targetTileY - previousY);
            points++;
        }
        double secondsPerOffroadTile = GroundRouteTrace.TILE_SIZE_METRES
                / OFFROAD_SPEED_METRES_PER_SECOND;
        double estimatedSeconds = plan.getEstimatedTimeTiles()
                * secondsPerOffroadTile;
        if (!positiveFinite(estimatedSeconds)) {
            estimatedSeconds = lengthTiles * secondsPerOffroadTile;
        }
        return new NavigationRouteStatistics(points,
                lengthTiles * GroundRouteTrace.TILE_SIZE_METRES,
                (long) Math.ceil(estimatedSeconds), true,
                targetTileX, targetTileY, targetTileX, targetTileY, true);
    }

    private static double estimatedSegmentSeconds(
            GroundRouteTrace.Point from, GroundRouteTrace.Point to,
            double horizontalMetres, float maximumSlopeDirt,
            float maximumWaterDepthMetres) {
        if (GroundRouteTrace.sameTrustedPublishedHighway(from, to)) {
            return horizontalMetres
                    / (OFFROAD_SPEED_METRES_PER_SECOND * 3.0d);
        }

        double speedMultiplier = from.isRoad() && to.isRoad() ? 2.0d : 1.0d;
        double uncertainty = from.isVerified() && to.isVerified() ? 1.0d : 1.35d;
        float slope = GroundRouteTrace.slopeDirtEstimate(
                to.getGroundHeightMetres() - from.getGroundHeightMetres(),
                (float) horizontalMetres);
        slope = maximumFinite(slope, maximumFinite(
                from.getTileMaximumSlopeDirt(),
                to.getTileMaximumSlopeDirt()));
        if (finite(slope)) slope *= GroundRouteTrace.slopeSafetyFactor(from, to);
        double slopeRatio = maximumSlopeDirt > 0.0f && finite(slope)
                ? slope / maximumSlopeDirt : 0.0d;

        double waterDepth = Math.max(from.getWaterDepthMetres(),
                to.getWaterDepthMetres());
        double waterRatio = maximumWaterDepthMetres > 0.0f
                && finite(waterDepth)
                ? waterDepth / maximumWaterDepthMetres : 0.0d;
        double terrainPenalty = 1.0d + slopeRatio * slopeRatio * 2.0d
                + waterRatio * waterRatio * 3.0d;
        return horizontalMetres / (OFFROAD_SPEED_METRES_PER_SECOND
                * speedMultiplier) * uncertainty * terrainPenalty;
    }

    public int getPointCount() { return pointCount; }
    public double getLengthMetres() { return lengthMetres; }
    public long getEstimatedDurationSeconds() { return estimatedDurationSeconds; }
    public boolean hasEndpoint() { return hasEndpoint; }
    public int getEndpointTileX() { return endpointTileX; }
    public int getEndpointTileY() { return endpointTileY; }
    public int getTargetTileX() { return targetTileX; }
    public int getTargetTileY() { return targetTileY; }
    public boolean isReachedTarget() { return reachedTarget; }

    public static String formatDuration(long seconds) {
        long safe = Math.max(0L, seconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        long remainder = safe % 60L;
        if (hours > 0L) {
            return hours + " h " + twoDigits(minutes) + " min "
                    + twoDigits(remainder) + " s";
        }
        if (minutes > 0L) {
            return minutes + " min " + twoDigits(remainder) + " s";
        }
        return remainder + " s";
    }

    private static boolean positiveFinite(double value) {
        return value > 0.0d && finite(value);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static float maximumFinite(float left, float right) {
        if (!finite(left)) return right;
        if (!finite(right)) return left;
        return Math.max(left, right);
    }

    private static String twoDigits(long value) {
        return value < 10L ? "0" + value : Long.toString(value);
    }
}
