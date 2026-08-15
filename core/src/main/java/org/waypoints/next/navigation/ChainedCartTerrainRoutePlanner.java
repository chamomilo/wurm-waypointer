package org.waypoints.next.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chains bounded terrain searches while preserving a safe partial route when
 * either an artificial leg goal or the final destination is unreachable.
 */
public final class ChainedCartTerrainRoutePlanner {
    public static final class Plan {
        private final List<GroundRouteTrace.Point> points;
        private final boolean reachedFinalTarget;
        private final int attemptedLegs;
        private final int expandedNodes;
        private final int rejectedSlopeEdges;
        private final int rejectedWaterEdges;
        private final int rejectedUnknownEdges;
        private final int rejectedCornerEdges;

        private Plan(List<GroundRouteTrace.Point> points,
                     boolean reachedFinalTarget, int attemptedLegs,
                     int expandedNodes, int rejectedSlopeEdges,
                     int rejectedWaterEdges, int rejectedUnknownEdges,
                     int rejectedCornerEdges) {
            this.points = Collections.unmodifiableList(
                    new ArrayList<GroundRouteTrace.Point>(points));
            this.reachedFinalTarget = reachedFinalTarget;
            this.attemptedLegs = attemptedLegs;
            this.expandedNodes = expandedNodes;
            this.rejectedSlopeEdges = rejectedSlopeEdges;
            this.rejectedWaterEdges = rejectedWaterEdges;
            this.rejectedUnknownEdges = rejectedUnknownEdges;
            this.rejectedCornerEdges = rejectedCornerEdges;
        }

        public List<GroundRouteTrace.Point> getPoints() { return points; }
        public boolean isReachedFinalTarget() { return reachedFinalTarget; }
        public int getAttemptedLegs() { return attemptedLegs; }
        public int getExpandedNodes() { return expandedNodes; }
        public int getRejectedSlopeEdges() { return rejectedSlopeEdges; }
        public int getRejectedWaterEdges() { return rejectedWaterEdges; }
        public int getRejectedUnknownEdges() { return rejectedUnknownEdges; }
        public int getRejectedCornerEdges() { return rejectedCornerEdges; }
    }

    private ChainedCartTerrainRoutePlanner() { }

    public static Plan plan(CartTerrainRoutePlanner planner,
                            CartTerrainRoutePlanner.Terrain terrain,
                            int startX, int startY, int targetX, int targetY,
                            int maximumLegs, int maximumPoints) {
        if (planner == null) throw new IllegalArgumentException(
                "planner is required");
        if (terrain == null) throw new IllegalArgumentException(
                "terrain is required");
        if (maximumLegs < 1) throw new IllegalArgumentException(
                "maximum legs must be positive");
        if (maximumPoints < 2) throw new IllegalArgumentException(
                "maximum points must be at least two");

        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>();
        Set<Long> continuationEndpoints = new HashSet<Long>();
        continuationEndpoints.add(tileKey(startX, startY));
        int currentX = startX;
        int currentY = startY;
        int attemptedLegs = 0;
        int expanded = 0;
        int rejectedSlope = 0;
        int rejectedWater = 0;
        int rejectedUnknown = 0;
        int rejectedCorner = 0;
        boolean reached = false;

        while (attemptedLegs < maximumLegs
                && points.size() < maximumPoints) {
            attemptedLegs++;
            CartTerrainRoutePlanner.Plan leg = planner.plan(currentX,
                    currentY, targetX, targetY, terrain);
            expanded += leg.getExpandedNodes();
            rejectedSlope += leg.getRejectedSlopeEdges();
            rejectedWater += leg.getRejectedWaterEdges();
            rejectedUnknown += leg.getRejectedUnknownEdges();
            rejectedCorner += leg.getRejectedCornerEdges();
            append(points, leg.getPoints(), maximumPoints);
            if (leg.isReachedFinalTarget()) {
                reached = true;
                break;
            }
            if (leg.getPoints().isEmpty()) break;

            GroundRouteTrace.Point last = leg.getPoints().get(
                    leg.getPoints().size() - 1);
            int nextX = last.getTileX();
            int nextY = last.getTileY();
            // A failed synthetic leg goal is allowed to continue only when it
            // made strict progress toward the real destination. The visited
            // endpoint set and hard leg budget independently rule out cycles.
            if (!strictlyCloser(currentX, currentY, nextX, nextY,
                    targetX, targetY)) break;
            if (!continuationEndpoints.add(tileKey(nextX, nextY))) break;
            currentX = nextX;
            currentY = nextY;
        }

        return new Plan(points, reached, attemptedLegs, expanded,
                rejectedSlope, rejectedWater, rejectedUnknown,
                rejectedCorner);
    }

    static boolean strictlyCloser(int fromX, int fromY, int toX, int toY,
                                  int targetX, int targetY) {
        return distanceSquared(toX, toY, targetX, targetY)
                < distanceSquared(fromX, fromY, targetX, targetY);
    }

    private static double distanceSquared(int x, int y,
                                          int targetX, int targetY) {
        double dx = (double) targetX - x;
        double dy = (double) targetY - y;
        return dx * dx + dy * dy;
    }

    private static long tileKey(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    private static void append(List<GroundRouteTrace.Point> target,
                               List<GroundRouteTrace.Point> source,
                               int maximumPoints) {
        for (GroundRouteTrace.Point point : source) {
            if (!target.isEmpty()) {
                GroundRouteTrace.Point last = target.get(target.size() - 1);
                if (last.getTileX() == point.getTileX()
                        && last.getTileY() == point.getTileY()) {
                    target.set(target.size() - 1, point);
                    continue;
                }
            }
            if (target.size() >= maximumPoints) return;
            target.add(point);
        }
    }
}
