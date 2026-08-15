package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CartTerrainRoutePlannerTest {
    @Test public void replansAroundAnOverLimitSlopeInsteadOfStopping() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Plan plan = planner.plan(0, 2, 5, 2,
                new GridTerrain() {
                    @Override float height(int x, int y) {
                        return x == 2 && y == 2 ? 10.0f : 0.0f;
                    }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertFalse(contains(plan, 2, 2));
        assertTrue(plan.getRejectedSlopeEdges() > 0);
    }

    @Test public void replansAroundWaterDeeperThanTheCartProfile() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Plan plan = planner.plan(0, 2, 5, 2,
                new GridTerrain() {
                    @Override float water(int x, int y) {
                        return x == 2 && y == 2 ? 1.0f : 0.0f;
                    }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertFalse(contains(plan, 2, 2));
        assertTrue(plan.getRejectedWaterEdges() > 0);
    }

    @Test public void prefersAUsefulRoadDetourByTravelTime() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Plan plan = planner.plan(0, 2, 6, 2,
                new GridTerrain() {
                    @Override boolean road(int x, int y) {
                        return y == 1 || (x == 0 && y == 2)
                                || (x == 6 && y == 2);
                    }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertTrue(containsY(plan, 1));
    }

    @Test public void replansAroundSteepTileCornersHiddenByFlatCentres() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Plan plan = planner.plan(0, 2, 5, 2,
                new GridTerrain() {
                    @Override float tileSlope(int x, int y) {
                        return x == 2 && y == 2 ? 55.0f : 0.0f;
                    }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertFalse(contains(plan, 2, 2));
        assertTrue(plan.getRejectedSlopeEdges() > 0);
    }

    @Test public void publishedHighwayOutranksAPlayerMadeRoad() {
        CartTerrainRoutePlanner planner = planner();
        CartTerrainRoutePlanner.Plan plan = planner.plan(0, 2, 9, 2,
                new GridTerrain() {
                    @Override boolean road(int x, int y) { return y == 2; }
                    @Override boolean highway(int x, int y) { return y == 1; }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertTrue(containsY(plan, 1));
    }

    @Test public void caveRouteJoinsTunnelMidspanInsteadOfDrivingToPortal() {
        CartTerrainRoutePlanner.Plan plan = planner().plan(9, 2, 2, 3,
                new CartTerrainRoutePlanner.Terrain() {
                    @Override public GroundRouteTrace.Point sample(
                            int tileX, int tileY) {
                        if (tileY == 2 && tileX >= 2 && tileX <= 13) {
                            return cavePoint(tileX, tileY,
                                    HighwayTileIndex.Kind.ROAD, false, false);
                        }
                        if (tileY == 3 && tileX >= 2 && tileX <= 13) {
                            return cavePoint(tileX, tileY,
                                    HighwayTileIndex.Kind.TUNNEL,
                                    tileX == 13, true);
                        }
                        return null;
                    }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertTrue(contains(plan, 2, 3));
        assertFalse(contains(plan, 13, 3));
        for (GroundRouteTrace.Point point : plan.getPoints()) {
            assertTrue("a target behind must not start with an eastward detour",
                    point.getTileX() <= 9);
        }
    }

    @Test public void caveTJunctionDoesNotUseDistantEntranceBehindTarget() {
        CartTerrainRoutePlanner planner = new CartTerrainRoutePlanner(
                4096, 4096, 64, 32, 10000, 512, 40.0f, 0.7f);
        CartTerrainRoutePlanner.Plan plan = planner.plan(
                3091, 1002, 3102, 1003,
                new CartTerrainRoutePlanner.Terrain() {
                    @Override public GroundRouteTrace.Point sample(
                            int tileX, int tileY) {
                        if (tileY == 1002 && tileX >= 3091
                                && tileX <= 3101) {
                            return cavePoint(tileX, tileY,
                                    HighwayTileIndex.Kind.ROAD, false, false);
                        }
                        if (tileX == 3101 && tileY >= 986
                                && tileY <= 1002) {
                            return cavePoint(tileX, tileY,
                                    HighwayTileIndex.Kind.ROAD, false, false);
                        }
                        if (tileY == 1003 && tileX >= 3056
                                && tileX <= 3102) {
                            return cavePoint(tileX, tileY,
                                    HighwayTileIndex.Kind.TUNNEL,
                                    tileX == 3056, true);
                        }
                        if (tileX == 3102 && tileY >= 986
                                && tileY <= 1003) {
                            return cavePoint(tileX, tileY,
                                    HighwayTileIndex.Kind.TUNNEL,
                                    tileY == 986, true);
                        }
                        return null;
                    }
                });

        assertTrue(plan.isReachedFinalTarget());
        assertTrue(plan.getPoints().size() <= 12);
        assertFalse(contains(plan, 3102, 986));
        for (GroundRouteTrace.Point point : plan.getPoints()) {
            assertTrue("route must stay on the adjacent tunnel lanes",
                    point.getTileY() >= 1002);
        }
    }

    private static CartTerrainRoutePlanner planner() {
        return new CartTerrainRoutePlanner(32, 32, 16, 4, 10000,
                512, 40.0f, 0.7f);
    }

    private static boolean contains(CartTerrainRoutePlanner.Plan plan,
                                    int x, int y) {
        for (GroundRouteTrace.Point point : plan.getPoints()) {
            if (point.getTileX() == x && point.getTileY() == y) return true;
        }
        return false;
    }

    private static boolean containsY(CartTerrainRoutePlanner.Plan plan, int y) {
        for (GroundRouteTrace.Point point : plan.getPoints()) {
            if (point.getTileY() == y) return true;
        }
        return false;
    }

    private static GroundRouteTrace.Point cavePoint(
            int x, int y, HighwayTileIndex.Kind kind, boolean portal,
            boolean published) {
        return new GroundRouteTrace.Point(x, y, 10.0f,
                GroundRouteTrace.HeightSource.CAVE, 0.0f,
                GroundRouteTrace.WaterSource.CAVE, kind, portal, 0.0f,
                published);
    }

    private abstract static class GridTerrain
            implements CartTerrainRoutePlanner.Terrain {
        float height(int x, int y) { return 0.0f; }
        float water(int x, int y) { return 0.0f; }
        boolean road(int x, int y) { return false; }
        boolean highway(int x, int y) { return false; }
        float tileSlope(int x, int y) { return Float.NaN; }

        @Override public GroundRouteTrace.Point sample(int tileX, int tileY) {
            boolean published = highway(tileX, tileY);
            return new GroundRouteTrace.Point(tileX, tileY,
                    height(tileX, tileY), GroundRouteTrace.HeightSource.NEAR,
                    water(tileX, tileY), GroundRouteTrace.WaterSource.NEAR,
                    road(tileX, tileY) || published
                            ? HighwayTileIndex.Kind.ROAD
                            : HighwayTileIndex.Kind.NONE,
                    false, tileSlope(tileX, tileY), published);
        }
    }
}
