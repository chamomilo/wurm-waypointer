package org.waypoints.next.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable diagnostic evaluation of one tile-centre route candidate.
 *
 * <p>The trace is shared by the local terrain A* and the global highway graph,
 * so every published and observed route can be compared in one log format.</p>
 */
public final class GroundRouteTrace {
    public static final String ALGORITHM_VERSION =
            "hierarchical-highway-cart-a-star-v16";
    public static final float TILE_SIZE_METRES = 4.0f;

    public enum HeightSource {
        NEAR, DISTANT, CAVE, BRIDGE_GEOMETRY, HIGHWAY_INTERPOLATED, UNKNOWN
    }
    public enum WaterSource {
        NEAR, DISTANT_SEA_LEVEL_ESTIMATE, CAVE, HIGHWAY_ASSUMED_CLEAR, UNKNOWN
    }
    public enum SegmentStatus {
        PASSABLE,
        UNVERIFIED,
        BLOCKED_SLOPE,
        BLOCKED_WATER,
        BLOCKED_SLOPE_AND_WATER
    }
    public enum Result { CLEAR, PARTIAL_OR_UNVERIFIED, BLOCKED }

    public static final class Point {
        private final int tileX;
        private final int tileY;
        private final float groundHeightMetres;
        private final HeightSource heightSource;
        private final float waterDepthMetres;
        private final WaterSource waterSource;
        private final HighwayTileIndex.Kind highwayKind;
        private final boolean highwayPortal;
        private final float tileMaximumSlopeDirt;
        private final boolean publishedHighway;

        public Point(int tileX, int tileY, float groundHeightMetres,
                     HeightSource heightSource, float waterDepthMetres,
                     WaterSource waterSource) {
            this(tileX, tileY, groundHeightMetres, heightSource,
                    waterDepthMetres, waterSource,
                    HighwayTileIndex.Kind.NONE, false, Float.NaN);
        }

        public Point(int tileX, int tileY, float groundHeightMetres,
                     HeightSource heightSource, float waterDepthMetres,
                     WaterSource waterSource, boolean road) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.groundHeightMetres = groundHeightMetres;
            this.heightSource = heightSource == null
                    ? HeightSource.UNKNOWN : heightSource;
            this.waterDepthMetres = waterDepthMetres;
            this.waterSource = waterSource == null
                    ? WaterSource.UNKNOWN : waterSource;
            this.highwayKind = road ? HighwayTileIndex.Kind.ROAD
                    : HighwayTileIndex.Kind.NONE;
            this.highwayPortal = false;
            this.tileMaximumSlopeDirt = Float.NaN;
            this.publishedHighway = false;
        }

        public Point(int tileX, int tileY, float groundHeightMetres,
                     HeightSource heightSource, float waterDepthMetres,
                     WaterSource waterSource,
                     HighwayTileIndex.Kind highwayKind,
                     boolean highwayPortal) {
            this(tileX, tileY, groundHeightMetres, heightSource,
                    waterDepthMetres, waterSource, highwayKind,
                    highwayPortal, Float.NaN,
                    highwayKind == HighwayTileIndex.Kind.BRIDGE
                            || highwayKind == HighwayTileIndex.Kind.TUNNEL);
        }

        public Point(int tileX, int tileY, float groundHeightMetres,
                     HeightSource heightSource, float waterDepthMetres,
                     WaterSource waterSource,
                     HighwayTileIndex.Kind highwayKind,
                     boolean highwayPortal,
                     float tileMaximumSlopeDirt) {
            this(tileX, tileY, groundHeightMetres, heightSource,
                    waterDepthMetres, waterSource, highwayKind,
                    highwayPortal, tileMaximumSlopeDirt,
                    highwayKind == HighwayTileIndex.Kind.BRIDGE
                            || highwayKind == HighwayTileIndex.Kind.TUNNEL);
        }

        public Point(int tileX, int tileY, float groundHeightMetres,
                     HeightSource heightSource, float waterDepthMetres,
                     WaterSource waterSource,
                     HighwayTileIndex.Kind highwayKind,
                     boolean highwayPortal,
                     float tileMaximumSlopeDirt,
                     boolean publishedHighway) {
            this.tileX = tileX;
            this.tileY = tileY;
            this.groundHeightMetres = groundHeightMetres;
            this.heightSource = heightSource == null
                    ? HeightSource.UNKNOWN : heightSource;
            this.waterDepthMetres = waterDepthMetres;
            this.waterSource = waterSource == null
                    ? WaterSource.UNKNOWN : waterSource;
            this.highwayKind = highwayKind == null
                    ? HighwayTileIndex.Kind.NONE : highwayKind;
            this.highwayPortal = highwayPortal;
            this.tileMaximumSlopeDirt = tileMaximumSlopeDirt;
            this.publishedHighway = publishedHighway;
        }

        public int getTileX() { return tileX; }
        public int getTileY() { return tileY; }
        public float getGroundHeightMetres() { return groundHeightMetres; }
        public HeightSource getHeightSource() { return heightSource; }
        public float getWaterDepthMetres() { return waterDepthMetres; }
        public WaterSource getWaterSource() { return waterSource; }
        public boolean isRoad() {
            return highwayKind != HighwayTileIndex.Kind.NONE;
        }
        public HighwayTileIndex.Kind getHighwayKind() { return highwayKind; }
        public boolean isHighwayPortal() { return highwayPortal; }
        public float getTileMaximumSlopeDirt() {
            return tileMaximumSlopeDirt;
        }
        public boolean isPublishedHighway() { return publishedHighway; }

        public Point withHighway(HighwayTileIndex.Kind kind, boolean portal) {
            HighwayTileIndex.Kind replacement = kind == null
                    ? HighwayTileIndex.Kind.NONE : kind;
            if (replacement == HighwayTileIndex.Kind.NONE) return this;
            return new Point(tileX, tileY, groundHeightMetres, heightSource,
                    waterDepthMetres, waterSource, replacement, portal,
                    tileMaximumSlopeDirt, true);
        }

        boolean isVerified() {
            boolean exactHeight = heightSource == HeightSource.NEAR
                    || heightSource == HeightSource.CAVE
                    || heightSource == HeightSource.BRIDGE_GEOMETRY;
            boolean exactWater = waterSource == WaterSource.NEAR
                    || waterSource == WaterSource.CAVE
                    || (waterSource == WaterSource.HIGHWAY_ASSUMED_CLEAR
                    && publishedHighway
                    && (highwayKind == HighwayTileIndex.Kind.BRIDGE
                    || highwayKind == HighwayTileIndex.Kind.TUNNEL));
            return exactHeight && exactWater
                    && finite(groundHeightMetres) && finite(waterDepthMetres);
        }
    }

    public static final class Segment {
        private final int index;
        private final int fromPointIndex;
        private final int toPointIndex;
        private final float horizontalMetres;
        private final float heightDeltaMetres;
        private final float absoluteSlopeDirtEstimate;
        private final float maximumTraversedSlopeDirt;
        private final float gradePercent;
        private final float maximumWaterDepthMetres;
        private final SegmentStatus status;

        private Segment(int index, int fromPointIndex, int toPointIndex,
                        float horizontalMetres, float heightDeltaMetres,
                        float absoluteSlopeDirtEstimate,
                        float maximumTraversedSlopeDirt, float gradePercent,
                        float maximumWaterDepthMetres, SegmentStatus status) {
            this.index = index;
            this.fromPointIndex = fromPointIndex;
            this.toPointIndex = toPointIndex;
            this.horizontalMetres = horizontalMetres;
            this.heightDeltaMetres = heightDeltaMetres;
            this.absoluteSlopeDirtEstimate = absoluteSlopeDirtEstimate;
            this.maximumTraversedSlopeDirt = maximumTraversedSlopeDirt;
            this.gradePercent = gradePercent;
            this.maximumWaterDepthMetres = maximumWaterDepthMetres;
            this.status = status;
        }

        public int getIndex() { return index; }
        public int getFromPointIndex() { return fromPointIndex; }
        public int getToPointIndex() { return toPointIndex; }
        public float getHorizontalMetres() { return horizontalMetres; }
        public float getHeightDeltaMetres() { return heightDeltaMetres; }
        public float getAbsoluteSlopeDirtEstimate() {
            return absoluteSlopeDirtEstimate;
        }
        public float getMaximumTraversedSlopeDirt() {
            return maximumTraversedSlopeDirt;
        }
        public float getGradePercent() { return gradePercent; }
        public float getMaximumWaterDepthMetres() {
            return maximumWaterDepthMetres;
        }
        public SegmentStatus getStatus() { return status; }
    }

    private final int targetTileX;
    private final int targetTileY;
    private final int layer;
    private final int candidatePointCount;
    private final boolean reachedTarget;
    private final float maximumSlopeDirt;
    private final float maximumWaterDepthMetres;
    private final List<Point> points;
    private final List<Segment> segments;
    private final Result result;
    private final int blockingPointIndex;
    private final int blockingSegmentIndex;
    private final int renderablePointCount;
    private final float observedMaximumSlopeDirt;
    private final float observedMaximumWaterDepthMetres;
    private final int unverifiedSegmentCount;

    private GroundRouteTrace(int targetTileX, int targetTileY, int layer,
                             int candidatePointCount, boolean reachedTarget,
                             float maximumSlopeDirt,
                             float maximumWaterDepthMetres, List<Point> points,
                             List<Segment> segments, Result result,
                             int blockingPointIndex, int blockingSegmentIndex,
                             int renderablePointCount,
                             float observedMaximumSlopeDirt,
                             float observedMaximumWaterDepthMetres,
                             int unverifiedSegmentCount) {
        this.targetTileX = targetTileX;
        this.targetTileY = targetTileY;
        this.layer = layer;
        this.candidatePointCount = candidatePointCount;
        this.reachedTarget = reachedTarget;
        this.maximumSlopeDirt = maximumSlopeDirt;
        this.maximumWaterDepthMetres = maximumWaterDepthMetres;
        this.points = Collections.unmodifiableList(new ArrayList<Point>(points));
        this.segments = Collections.unmodifiableList(new ArrayList<Segment>(segments));
        this.result = result;
        this.blockingPointIndex = blockingPointIndex;
        this.blockingSegmentIndex = blockingSegmentIndex;
        this.renderablePointCount = renderablePointCount;
        this.observedMaximumSlopeDirt = observedMaximumSlopeDirt;
        this.observedMaximumWaterDepthMetres = observedMaximumWaterDepthMetres;
        this.unverifiedSegmentCount = unverifiedSegmentCount;
    }

    public static GroundRouteTrace analyse(int targetTileX, int targetTileY,
                                           int layer, int candidatePointCount,
                                           boolean reachedTarget,
                                           float maximumSlopeDirt,
                                           float maximumWaterDepthMetres,
                                           List<Point> sourcePoints) {
        if (!positiveFinite(maximumSlopeDirt)) {
            throw new IllegalArgumentException("maximum slope must be positive");
        }
        if (!finite(maximumWaterDepthMetres) || maximumWaterDepthMetres < 0.0f) {
            throw new IllegalArgumentException("maximum water depth must be non-negative");
        }
        if (sourcePoints == null) throw new IllegalArgumentException("points are required");

        List<Point> points = new ArrayList<Point>(sourcePoints);
        List<Segment> segments = new ArrayList<Segment>(Math.max(0, points.size() - 1));
        int blockingPoint = -1;
        int blockingSegment = -1;
        int renderable = points.size();
        float maximumObservedSlope = 0.0f;
        float maximumObservedWater = 0.0f;
        int unverified = 0;

        if (!points.isEmpty()) {
            Point first = points.get(0);
            if (finite(first.waterDepthMetres)) {
                maximumObservedWater = Math.max(maximumObservedWater,
                        first.waterDepthMetres);
                if (!isSpecialHighway(first)
                        && first.waterDepthMetres > maximumWaterDepthMetres) {
                    blockingPoint = 0;
                    renderable = 0;
                }
            }
        }

        for (int i = 1; i < points.size(); i++) {
            Point from = points.get(i - 1);
            Point to = points.get(i);
            float dx = (to.tileX - from.tileX) * TILE_SIZE_METRES;
            float dy = (to.tileY - from.tileY) * TILE_SIZE_METRES;
            float horizontal = (float) Math.sqrt(dx * dx + dy * dy);
            float delta = to.groundHeightMetres - from.groundHeightMetres;
            float slope = slopeDirtEstimate(delta, horizontal);
            float traversedSlope = maximumFinite(slope, maximumFinite(
                    from.tileMaximumSlopeDirt, to.tileMaximumSlopeDirt));
            if (finite(traversedSlope)) {
                traversedSlope *= slopeSafetyFactor(from, to);
            }
            float grade = horizontal <= 0.0f ? Float.NaN
                    : Math.abs(delta) * 100.0f / horizontal;
            float water = maximumFinite(from.waterDepthMetres,
                    to.waterDepthMetres);
            maximumObservedSlope = Math.max(maximumObservedSlope,
                    finite(traversedSlope) ? traversedSlope : 0.0f);
            maximumObservedWater = Math.max(maximumObservedWater,
                    finite(water) ? water : 0.0f);

            boolean trustedHighway = sameTrustedPublishedHighway(from, to);
            boolean slopeBlocked = !trustedHighway && finite(traversedSlope)
                    && traversedSlope > maximumSlopeDirt;
            boolean waterBlocked = !trustedHighway && finite(water)
                    && water > maximumWaterDepthMetres;
            SegmentStatus status;
            if (slopeBlocked && waterBlocked) {
                status = SegmentStatus.BLOCKED_SLOPE_AND_WATER;
            } else if (slopeBlocked) {
                status = SegmentStatus.BLOCKED_SLOPE;
            } else if (waterBlocked) {
                status = SegmentStatus.BLOCKED_WATER;
            } else if (!from.isVerified() || !to.isVerified()
                    || !finite(slope) || !finite(water)) {
                status = SegmentStatus.UNVERIFIED;
                unverified++;
            } else {
                status = SegmentStatus.PASSABLE;
            }
            Segment segment = new Segment(i - 1, i - 1, i, horizontal,
                    delta, slope, traversedSlope, grade, water, status);
            segments.add(segment);
            if (blockingSegment < 0 && isBlocked(status)) {
                blockingSegment = segment.index;
                renderable = Math.min(renderable, i);
            }
        }

        Result result;
        if (blockingPoint >= 0 || blockingSegment >= 0) {
            result = Result.BLOCKED;
        } else if (!reachedTarget || points.size() < candidatePointCount
                || unverified > 0) {
            result = Result.PARTIAL_OR_UNVERIFIED;
        } else {
            result = Result.CLEAR;
        }
        return new GroundRouteTrace(targetTileX, targetTileY, layer,
                candidatePointCount, reachedTarget, maximumSlopeDirt,
                maximumWaterDepthMetres, points, segments, result,
                blockingPoint, blockingSegment, renderable,
                maximumObservedSlope, maximumObservedWater, unverified);
    }

    /**
     * Wurm terrain heights are metres while the familiar slope unit is a
     * tenth of a metre per four horizontal metres. Diagonal transitions are
     * normalized to the same four-metre run.
     */
    public static float slopeDirtEstimate(float heightDeltaMetres,
                                          float horizontalMetres) {
        if (!finite(heightDeltaMetres) || !positiveFinite(horizontalMetres)) {
            return Float.NaN;
        }
        return Math.abs(heightDeltaMetres) * 10.0f
                * TILE_SIZE_METRES / horizontalMetres;
    }

    public int getTargetTileX() { return targetTileX; }
    public int getTargetTileY() { return targetTileY; }
    public int getLayer() { return layer; }
    public int getCandidatePointCount() { return candidatePointCount; }
    public boolean isReachedTarget() { return reachedTarget; }
    public float getMaximumSlopeDirt() { return maximumSlopeDirt; }
    public float getMaximumWaterDepthMetres() { return maximumWaterDepthMetres; }
    public List<Point> getPoints() { return points; }
    public List<Segment> getSegments() { return segments; }
    public Result getResult() { return result; }
    public int getBlockingPointIndex() { return blockingPointIndex; }
    public int getBlockingSegmentIndex() { return blockingSegmentIndex; }
    public int getRenderablePointCount() { return renderablePointCount; }
    public float getObservedMaximumSlopeDirt() { return observedMaximumSlopeDirt; }
    public float getObservedMaximumWaterDepthMetres() {
        return observedMaximumWaterDepthMetres;
    }
    public int getUnverifiedSegmentCount() { return unverifiedSegmentCount; }

    private static boolean isBlocked(SegmentStatus status) {
        return status == SegmentStatus.BLOCKED_SLOPE
                || status == SegmentStatus.BLOCKED_WATER
                || status == SegmentStatus.BLOCKED_SLOPE_AND_WATER;
    }

    static boolean sameSpecialHighway(Point from, Point to) {
        HighwayTileIndex.Kind kind = from.getHighwayKind();
        return kind == to.getHighwayKind()
                && (kind == HighwayTileIndex.Kind.BRIDGE
                || kind == HighwayTileIndex.Kind.TUNNEL);
    }

    static boolean sameTrustedPublishedHighway(Point from, Point to) {
        if (!from.isPublishedHighway() || !to.isPublishedHighway()) {
            return false;
        }
        if (from.getHighwayKind() == to.getHighwayKind()) {
            return from.getHighwayKind() != HighwayTileIndex.Kind.NONE;
        }
        return publishedRoadToSpecialPortal(from, to)
                || publishedRoadToSpecialPortal(to, from);
    }

    private static boolean publishedRoadToSpecialPortal(Point road,
                                                        Point special) {
        return road.getHighwayKind() == HighwayTileIndex.Kind.ROAD
                && special.isHighwayPortal()
                && (special.getHighwayKind() == HighwayTileIndex.Kind.BRIDGE
                || special.getHighwayKind() == HighwayTileIndex.Kind.TUNNEL);
    }

    /** Centre/interpolated terrain uses half the effective cart limit. */
    static float slopeSafetyFactor(Point from, Point to) {
        boolean exactTileSlopes = finite(from.getTileMaximumSlopeDirt())
                && finite(to.getTileMaximumSlopeDirt());
        return from.getHeightSource() == HeightSource.DISTANT
                || to.getHeightSource() == HeightSource.DISTANT
                || !exactTileSlopes ? 2.0f : 1.0f;
    }

    private static boolean isSpecialHighway(Point point) {
        HighwayTileIndex.Kind kind = point.getHighwayKind();
        return kind == HighwayTileIndex.Kind.BRIDGE
                || kind == HighwayTileIndex.Kind.TUNNEL;
    }

    private static float maximumFinite(float left, float right) {
        if (!finite(left)) return right;
        if (!finite(right)) return left;
        return Math.max(left, right);
    }

    private static boolean positiveFinite(float value) {
        return finite(value) && value > 0.0f;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
