package org.waypoints.next.navigation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Bounded eight-connected A* for the currently observable cart terrain. */
public final class CartTerrainRoutePlanner {
    public static final String ALGORITHM_VERSION = "bounded-cart-a-star-v2";
    private static final float DIAGONAL = (float) Math.sqrt(2.0d);
    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] DY = {0, 1, 1, 1, 0, -1, -1, -1};

    public interface Terrain {
        /** Returns null when the tile has no usable terrain observation. */
        GroundRouteTrace.Point sample(int tileX, int tileY);
    }

    public static final class Plan {
        private final List<GroundRouteTrace.Point> points;
        private final int planningGoalX;
        private final int planningGoalY;
        private final boolean reachedPlanningGoal;
        private final boolean reachedFinalTarget;
        private final boolean truncated;
        private final int expandedNodes;
        private final int rejectedSlopeEdges;
        private final int rejectedWaterEdges;
        private final int rejectedUnknownEdges;
        private final int rejectedCornerEdges;

        private Plan(List<GroundRouteTrace.Point> points, int planningGoalX,
                     int planningGoalY, boolean reachedPlanningGoal,
                     boolean reachedFinalTarget, boolean truncated,
                     int expandedNodes, int rejectedSlopeEdges,
                     int rejectedWaterEdges, int rejectedUnknownEdges,
                     int rejectedCornerEdges) {
            this.points = Collections.unmodifiableList(
                    new ArrayList<GroundRouteTrace.Point>(points));
            this.planningGoalX = planningGoalX;
            this.planningGoalY = planningGoalY;
            this.reachedPlanningGoal = reachedPlanningGoal;
            this.reachedFinalTarget = reachedFinalTarget;
            this.truncated = truncated;
            this.expandedNodes = expandedNodes;
            this.rejectedSlopeEdges = rejectedSlopeEdges;
            this.rejectedWaterEdges = rejectedWaterEdges;
            this.rejectedUnknownEdges = rejectedUnknownEdges;
            this.rejectedCornerEdges = rejectedCornerEdges;
        }

        public List<GroundRouteTrace.Point> getPoints() { return points; }
        public int getPlanningGoalX() { return planningGoalX; }
        public int getPlanningGoalY() { return planningGoalY; }
        public boolean isReachedPlanningGoal() { return reachedPlanningGoal; }
        public boolean isReachedFinalTarget() { return reachedFinalTarget; }
        public boolean isTruncated() { return truncated; }
        public int getExpandedNodes() { return expandedNodes; }
        public int getRejectedSlopeEdges() { return rejectedSlopeEdges; }
        public int getRejectedWaterEdges() { return rejectedWaterEdges; }
        public int getRejectedUnknownEdges() { return rejectedUnknownEdges; }
        public int getRejectedCornerEdges() { return rejectedCornerEdges; }
    }

    private static final class OpenNode {
        private final int index;
        private final float g;
        private final float f;

        private OpenNode(int index, float g, float f) {
            this.index = index;
            this.g = g;
            this.f = f;
        }
    }

    private static final class SearchStats {
        private int expanded;
        private int slope;
        private int water;
        private int unknown;
        private int corner;
    }

    private final int mapWidth;
    private final int mapHeight;
    private final int maximumLegTiles;
    private final int detourMarginTiles;
    private final int maximumExpandedNodes;
    private final int maximumRoutePoints;
    private final float maximumSlopeDirt;
    private final float maximumWaterDepthMetres;

    public CartTerrainRoutePlanner(int mapWidth, int mapHeight,
                                   int maximumLegTiles,
                                   int detourMarginTiles,
                                   int maximumExpandedNodes,
                                   int maximumRoutePoints,
                                   float maximumSlopeDirt,
                                   float maximumWaterDepthMetres) {
        if (mapWidth < 1 || mapHeight < 1 || maximumLegTiles < 1
                || detourMarginTiles < 0 || maximumExpandedNodes < 1
                || maximumRoutePoints < 2 || maximumSlopeDirt <= 0.0f
                || maximumWaterDepthMetres < 0.0f) {
            throw new IllegalArgumentException("invalid bounded cart planner settings");
        }
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        this.maximumLegTiles = maximumLegTiles;
        this.detourMarginTiles = detourMarginTiles;
        this.maximumExpandedNodes = maximumExpandedNodes;
        this.maximumRoutePoints = maximumRoutePoints;
        this.maximumSlopeDirt = maximumSlopeDirt;
        this.maximumWaterDepthMetres = maximumWaterDepthMetres;
    }

    public Plan plan(int startX, int startY, int targetX, int targetY,
                     Terrain terrain) {
        if (terrain == null) throw new IllegalArgumentException("terrain is required");
        if (!insideMap(startX, startY) || !insideMap(targetX, targetY)) {
            return empty(targetX, targetY);
        }

        int deltaX = targetX - startX;
        int deltaY = targetY - startY;
        int chebyshev = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        double scale = chebyshev <= maximumLegTiles || chebyshev == 0
                ? 1.0d : maximumLegTiles / (double) chebyshev;
        int goalX = clamp(startX + (int) Math.round(deltaX * scale),
                0, mapWidth - 1);
        int goalY = clamp(startY + (int) Math.round(deltaY * scale),
                0, mapHeight - 1);

        int minimumX = clamp(Math.min(startX, goalX) - detourMarginTiles,
                0, mapWidth - 1);
        int maximumX = clamp(Math.max(startX, goalX) + detourMarginTiles,
                0, mapWidth - 1);
        int minimumY = clamp(Math.min(startY, goalY) - detourMarginTiles,
                0, mapHeight - 1);
        int maximumY = clamp(Math.max(startY, goalY) + detourMarginTiles,
                0, mapHeight - 1);
        int width = maximumX - minimumX + 1;
        int height = maximumY - minimumY + 1;
        int size = width * height;

        float[] gScore = new float[size];
        Arrays.fill(gScore, Float.POSITIVE_INFINITY);
        int[] parent = new int[size];
        Arrays.fill(parent, -1);
        boolean[] closed = new boolean[size];
        byte[] sampleState = new byte[size];
        GroundRouteTrace.Point[] samples = new GroundRouteTrace.Point[size];
        PriorityQueue<OpenNode> open = new PriorityQueue<OpenNode>(64,
                new Comparator<OpenNode>() {
                    @Override public int compare(OpenNode left, OpenNode right) {
                        int f = Float.compare(left.f, right.f);
                        if (f != 0) return f;
                        return Float.compare(right.g, left.g);
                    }
                });
        SearchStats stats = new SearchStats();
        int start = index(startX, startY, minimumX, minimumY, width);
        int goal = index(goalX, goalY, minimumX, minimumY, width);
        if (sample(start, minimumX, minimumY, width, sampleState, samples,
                terrain, stats) == null) return empty(goalX, goalY);
        gScore[start] = 0.0f;
        open.add(new OpenNode(start, 0.0f,
                heuristic(startX, startY, goalX, goalY)));
        int best = start;
        float bestHeuristic = heuristic(startX, startY, goalX, goalY);
        boolean reached = false;

        while (!open.isEmpty() && stats.expanded < maximumExpandedNodes) {
            OpenNode currentEntry = open.poll();
            int current = currentEntry.index;
            if (closed[current] || currentEntry.g != gScore[current]) continue;
            closed[current] = true;
            stats.expanded++;
            int currentX = minimumX + current % width;
            int currentY = minimumY + current / width;
            float currentHeuristic = heuristic(currentX, currentY, goalX, goalY);
            if (currentHeuristic < bestHeuristic
                    || (currentHeuristic == bestHeuristic
                    && gScore[current] < gScore[best])) {
                best = current;
                bestHeuristic = currentHeuristic;
            }
            if (current == goal) {
                best = current;
                reached = true;
                break;
            }

            GroundRouteTrace.Point currentSample = samples[current];
            for (int direction = 0; direction < DX.length; direction++) {
                int nextX = currentX + DX[direction];
                int nextY = currentY + DY[direction];
                if (nextX < minimumX || nextX > maximumX
                        || nextY < minimumY || nextY > maximumY) continue;
                int next = index(nextX, nextY, minimumX, minimumY, width);
                if (closed[next]) continue;
                GroundRouteTrace.Point nextSample = sample(next, minimumX,
                        minimumY, width, sampleState, samples, terrain, stats);
                if (nextSample == null) continue;
                boolean diagonal = DX[direction] != 0 && DY[direction] != 0;
                if (diagonal && !diagonalClear(currentX, currentY,
                        DX[direction], DY[direction], minimumX, minimumY, width,
                        maximumX, maximumY, sampleState, samples, terrain,
                        currentSample, stats)) continue;
                float edgeCost = edgeCost(currentSample, nextSample,
                        diagonal ? DIAGONAL : 1.0f, stats, true);
                if (Float.isInfinite(edgeCost)) continue;
                float tentative = gScore[current] + edgeCost;
                if (tentative >= gScore[next]) continue;
                gScore[next] = tentative;
                parent[next] = current;
                open.add(new OpenNode(next, tentative, tentative
                        + heuristic(nextX, nextY, goalX, goalY)));
            }
        }

        List<GroundRouteTrace.Point> reversed =
                new ArrayList<GroundRouteTrace.Point>();
        int cursor = best;
        while (cursor >= 0) {
            GroundRouteTrace.Point point = samples[cursor];
            if (point == null) break;
            reversed.add(point);
            if (cursor == start) break;
            cursor = parent[cursor];
        }
        Collections.reverse(reversed);
        boolean truncated = reversed.size() > maximumRoutePoints;
        if (truncated) {
            reversed = new ArrayList<GroundRouteTrace.Point>(
                    reversed.subList(0, maximumRoutePoints));
        }
        boolean finalTarget = reached && goalX == targetX && goalY == targetY
                && !truncated;
        return new Plan(reversed, goalX, goalY, reached, finalTarget, truncated,
                stats.expanded, stats.slope, stats.water, stats.unknown,
                stats.corner);
    }

    private boolean diagonalClear(int currentX, int currentY, int dx, int dy,
                                  int minimumX, int minimumY, int width,
                                  int maximumX, int maximumY,
                                  byte[] sampleState,
                                  GroundRouteTrace.Point[] samples,
                                  Terrain terrain,
                                  GroundRouteTrace.Point current,
                                  SearchStats stats) {
        int sideAX = currentX + dx;
        int sideAY = currentY;
        int sideBX = currentX;
        int sideBY = currentY + dy;
        if (sideAX < minimumX || sideAX > maximumX || sideAY < minimumY
                || sideAY > maximumY || sideBX < minimumX
                || sideBX > maximumX || sideBY < minimumY
                || sideBY > maximumY) {
            stats.corner++;
            return false;
        }
        GroundRouteTrace.Point sideA = sample(index(sideAX, sideAY, minimumX,
                minimumY, width), minimumX, minimumY, width, sampleState,
                samples, terrain, stats);
        GroundRouteTrace.Point sideB = sample(index(sideBX, sideBY, minimumX,
                minimumY, width), minimumX, minimumY, width, sampleState,
                samples, terrain, stats);
        if (sideA == null || sideB == null
                || Float.isInfinite(edgeCost(current, sideA, 1.0f, stats, false))
                || Float.isInfinite(edgeCost(current, sideB, 1.0f, stats, false))) {
            stats.corner++;
            return false;
        }
        return true;
    }

    private float edgeCost(GroundRouteTrace.Point from,
                           GroundRouteTrace.Point to, float distanceTiles,
                           SearchStats stats, boolean countRejection) {
        if (!allowedHighwayTransition(from, to)) {
            if (countRejection) stats.unknown++;
            return Float.POSITIVE_INFINITY;
        }
        boolean trustedHighway = GroundRouteTrace.sameTrustedPublishedHighway(
                from, to);
        if (trustedHighway) return distanceTiles / 3.0f;
        float horizontal = distanceTiles * GroundRouteTrace.TILE_SIZE_METRES;
        float slope = GroundRouteTrace.slopeDirtEstimate(
                to.getGroundHeightMetres() - from.getGroundHeightMetres(),
                horizontal);
        slope = maximumFinite(slope, maximumFinite(
                from.getTileMaximumSlopeDirt(),
                to.getTileMaximumSlopeDirt()));
        if (!Float.isNaN(slope) && !Float.isInfinite(slope)) {
            slope *= GroundRouteTrace.slopeSafetyFactor(from, to);
        }
        if (Float.isNaN(slope) || Float.isInfinite(slope)) {
            if (countRejection) stats.unknown++;
            return Float.POSITIVE_INFINITY;
        }
        if (slope > maximumSlopeDirt) {
            if (countRejection) stats.slope++;
            return Float.POSITIVE_INFINITY;
        }
        float depth = Math.max(from.getWaterDepthMetres(),
                to.getWaterDepthMetres());
        if (Float.isNaN(depth) || Float.isInfinite(depth)) {
            if (countRejection) stats.unknown++;
            return Float.POSITIVE_INFINITY;
        }
        if (depth > maximumWaterDepthMetres) {
            if (countRejection) stats.water++;
            return Float.POSITIVE_INFINITY;
        }
        float roadMultiplier = from.isRoad() && to.isRoad() ? 0.5f : 1.0f;
        float slopeRatio = slope / maximumSlopeDirt;
        float waterRatio = maximumWaterDepthMetres <= 0.0f ? 0.0f
                : depth / maximumWaterDepthMetres;
        float uncertainty = from.isVerified() && to.isVerified() ? 1.0f : 1.35f;
        return distanceTiles * roadMultiplier * uncertainty
                * (1.0f + slopeRatio * slopeRatio * 2.0f
                + waterRatio * waterRatio * 3.0f);
    }

    private static boolean allowedHighwayTransition(GroundRouteTrace.Point from,
                                                     GroundRouteTrace.Point to) {
        HighwayTileIndex.Kind fromKind = from.getHighwayKind();
        HighwayTileIndex.Kind toKind = to.getHighwayKind();
        if (fromKind == toKind) return true;
        // A published tunnel line is only a topology hint once both samples
        // are already on Wurm's cave layer. The cave buffer proves the actual
        // neighbouring floors are connected, so a cart may move laterally
        // onto/off the line without driving to a distant surface portal.
        // Surface tunnel entry and every bridge transition still require an
        // authoritative portal.
        boolean caveFloorTransition = from.getHeightSource()
                == GroundRouteTrace.HeightSource.CAVE
                && to.getHeightSource() == GroundRouteTrace.HeightSource.CAVE
                && fromKind != HighwayTileIndex.Kind.BRIDGE
                && toKind != HighwayTileIndex.Kind.BRIDGE
                && (fromKind == HighwayTileIndex.Kind.TUNNEL
                || toKind == HighwayTileIndex.Kind.TUNNEL);
        if (caveFloorTransition) return true;
        boolean fromSpecial = fromKind == HighwayTileIndex.Kind.BRIDGE
                || fromKind == HighwayTileIndex.Kind.TUNNEL;
        boolean toSpecial = toKind == HighwayTileIndex.Kind.BRIDGE
                || toKind == HighwayTileIndex.Kind.TUNNEL;
        return (!fromSpecial || from.isHighwayPortal())
                && (!toSpecial || to.isHighwayPortal());
    }

    private GroundRouteTrace.Point sample(int index, int minimumX, int minimumY,
                                          int width, byte[] sampleState,
                                          GroundRouteTrace.Point[] samples,
                                          Terrain terrain, SearchStats stats) {
        if (sampleState[index] == 1) return samples[index];
        if (sampleState[index] == 2) return null;
        int x = minimumX + index % width;
        int y = minimumY + index / width;
        GroundRouteTrace.Point point = terrain.sample(x, y);
        if (point == null) {
            sampleState[index] = 2;
            stats.unknown++;
            return null;
        }
        sampleState[index] = 1;
        samples[index] = point;
        return point;
    }

    private Plan empty(int goalX, int goalY) {
        return new Plan(Collections.<GroundRouteTrace.Point>emptyList(),
                goalX, goalY, false, false, false, 0, 0, 0, 1, 0);
    }

    private boolean insideMap(int x, int y) {
        return x >= 0 && y >= 0 && x < mapWidth && y < mapHeight;
    }

    private static int index(int x, int y, int minimumX, int minimumY,
                             int width) {
        return x - minimumX + (y - minimumY) * width;
    }

    /** Octile distance divided by the fastest published-Highway speed. */
    private static float heuristic(int x, int y, int goalX, int goalY) {
        int dx = Math.abs(goalX - x);
        int dy = Math.abs(goalY - y);
        int diagonal = Math.min(dx, dy);
        int cardinal = Math.max(dx, dy) - diagonal;
        return (diagonal * DIAGONAL + cardinal) / 3.0f;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float maximumFinite(float left, float right) {
        if (Float.isNaN(left) || Float.isInfinite(left)) return right;
        if (Float.isNaN(right) || Float.isInfinite(right)) return left;
        return Math.max(left, right);
    }
}
