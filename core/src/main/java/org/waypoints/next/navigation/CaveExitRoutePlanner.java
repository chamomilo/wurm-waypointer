package org.waypoints.next.navigation;

import java.util.List;

/** Chooses a reachable received cave exit using cave travel and surface continuation. */
public final class CaveExitRoutePlanner {
    private CaveExitRoutePlanner() { }

    public static final class Exit {
        public final int x;
        public final int y;
        public final float surfaceTimeTiles;
        public Exit(int x, int y, float surfaceTimeTiles) {
            this.x = x;
            this.y = y;
            this.surfaceTimeTiles = surfaceTimeTiles;
        }
    }

    public static CartTerrainRoutePlanner.Plan plan(CartTerrainRoutePlanner planner,
                                                    CartTerrainRoutePlanner.Terrain terrain,
                                                    int startX, int startY,
                                                    List<Exit> exits) {
        CartTerrainRoutePlanner.Plan best = null;
        float bestCost = Float.POSITIVE_INFINITY;
        for (Exit exit : exits) {
            CartTerrainRoutePlanner.Plan candidate = planner.plan(startX, startY,
                    exit.x, exit.y, terrain);
            if (!candidate.isReachedFinalTarget()) continue;
            List<GroundRouteTrace.Point> points = candidate.getPoints();
            float cost = exit.surfaceTimeTiles;
            for (int i = 1; i < points.size(); i++) {
                GroundRouteTrace.Point from = points.get(i - 1);
                GroundRouteTrace.Point to = points.get(i);
                float speedCost = from.isRoad() && to.isRoad() ? 0.5f : 1.0f;
                cost += (float) Math.hypot(to.getTileX() - from.getTileX(),
                        to.getTileY() - from.getTileY()) * speedCost;
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = candidate;
            }
        }
        return best;
    }
}
