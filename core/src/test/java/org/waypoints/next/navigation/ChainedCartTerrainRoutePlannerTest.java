package org.waypoints.next.navigation;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChainedCartTerrainRoutePlannerTest {
    @Test public void continuesPastBlockedSyntheticLegGoal() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Terrain terrain =
                new CartTerrainRoutePlanner.Terrain() {
                    @Override public GroundRouteTrace.Point sample(
                            int tileX, int tileY) {
                        return point(tileX, tileY,
                                tileX == 6 && tileY == 5 ? 100.0f : 0.0f);
                    }
                };

        CartTerrainRoutePlanner.Plan first = planner.plan(
                0, 5, 12, 5, terrain);
        assertFalse(first.isReachedPlanningGoal());
        assertFalse(first.isReachedFinalTarget());

        ChainedCartTerrainRoutePlanner.Plan complete =
                ChainedCartTerrainRoutePlanner.plan(planner, terrain,
                        0, 5, 12, 5, 8, 200);

        assertTrue(complete.isReachedFinalTarget());
        assertTrue(complete.getAttemptedLegs() >= 2);
        assertFalse(contains(complete.getPoints(), 6, 5));
        GroundRouteTrace.Point last = complete.getPoints().get(
                complete.getPoints().size() - 1);
        assertEquals(12, last.getTileX());
        assertEquals(5, last.getTileY());
    }

    @Test public void unreachableFinalTargetStopsWithoutCycling() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Terrain terrain =
                new CartTerrainRoutePlanner.Terrain() {
                    @Override public GroundRouteTrace.Point sample(
                            int tileX, int tileY) {
                        return point(tileX, tileY,
                                tileX == 10 ? 100.0f : 0.0f);
                    }
                };

        ChainedCartTerrainRoutePlanner.Plan partial =
                ChainedCartTerrainRoutePlanner.plan(planner, terrain,
                        0, 5, 12, 5, 32, 200);

        assertFalse(partial.isReachedFinalTarget());
        assertEquals(3, partial.getAttemptedLegs());
        assertTrue(partial.getPoints().size() < 20);
        GroundRouteTrace.Point last = partial.getPoints().get(
                partial.getPoints().size() - 1);
        assertEquals(9, last.getTileX());
        assertEquals(5, last.getTileY());
    }

    @Test public void lateralOrBackwardEndpointIsNotProgress() {
        assertFalse(ChainedCartTerrainRoutePlanner.strictlyCloser(
                5, 5, 5, 5, 10, 5));
        assertFalse(ChainedCartTerrainRoutePlanner.strictlyCloser(
                5, 5, 4, 5, 10, 5));
        assertTrue(ChainedCartTerrainRoutePlanner.strictlyCloser(
                5, 5, 6, 6, 10, 5));
    }

    private static CartTerrainRoutePlanner planner() {
        return new CartTerrainRoutePlanner(20, 11, 6, 5,
                10000, 200, 40.0f, 0.7f);
    }

    private static GroundRouteTrace.Point point(int x, int y, float slope) {
        return new GroundRouteTrace.Point(x, y, 0.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR,
                HighwayTileIndex.Kind.NONE, false, slope, false);
    }

    private static boolean contains(List<GroundRouteTrace.Point> points,
                                    int x, int y) {
        for (GroundRouteTrace.Point point : points) {
            if (point.getTileX() == x && point.getTileY() == y) return true;
        }
        return false;
    }
}
