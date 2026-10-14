package org.waypoints.next.navigation;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public final class HighwayApproachTest {
    @Test public void kaminoApproachUsesShiftedBottomRoadInsteadOfNorthernEntry() {
        final HighwayTileIndex roads = HighwayTileIndex.parse("["
                + "{\"startX\":2866,\"startY\":1002,\"endX\":2866,\"endY\":969,\"type\":2},"
                + "{\"startX\":2866,\"startY\":1002,\"endX\":2876,\"endY\":1002,\"type\":2},"
                + "{\"startX\":2876,\"startY\":1003,\"endX\":2911,\"endY\":1003,\"type\":2},"
                + "{\"startX\":2866,\"startY\":1002,\"endX\":2781,\"endY\":1002,\"type\":2},"
                + "{\"startX\":2911,\"startY\":1003,\"endX\":2911,\"endY\":969,\"type\":2},"
                + "{\"startX\":2911,\"startY\":1003,\"endX\":2952,\"endY\":1003,\"type\":2},"
                + "{\"startX\":2909,\"startY\":969,\"endX\":2909,\"endY\":954,\"type\":2},"
                + "{\"startX\":2909,\"startY\":969,\"endX\":2866,\"endY\":969,\"type\":2}]",
                4096, 4096);
        CartTerrainRoutePlanner.Terrain terrain = new CartTerrainRoutePlanner.Terrain() {
            @Override public GroundRouteTrace.Point sample(int x, int y) {
                return roads.get(x, y).hasKind(HighwayTileIndex.Kind.ROAD)
                        ? road(x, y) : null;
            }
        };
        List<GroundRouteTrace.Point> highway = new ArrayList<GroundRouteTrace.Point>();
        line(highway, 2909, 954, 2909, 969);
        line(highway, 2909, 969, 2866, 969);
        line(highway, 2866, 969, 2866, 1002);
        line(highway, 2866, 1002, 2781, 1002);
        CartTerrainRoutePlanner planner = new CartTerrainRoutePlanner(
                4096, 4096, 512, 96, 100000, 4096, 40.0f, 0.7f);

        CartTerrainRoutePlanner.Plan oldEntry = planner.plan(2950, 1002,
                2909, 954, terrain);
        assertTrue(oldEntry.isReachedFinalTarget());
        assertTrue(oldEntry.getPoints().stream().anyMatch(p -> p.getTileY() < 970));

        CartTerrainRoutePlanner.Plan approach = planner.planToHighway(
                2950, 1002, highway, terrain);
        assertTrue(approach.isReachedFinalTarget());
        List<GroundRouteTrace.Point> result = NavigationRouteAssembly.joinHighway(
                approach.getPoints(), highway, Collections.<GroundRouteTrace.Point>emptyList());
        assertFalse(result.isEmpty());
        assertEquals(2781, result.get(result.size() - 1).getTileX());
        for (GroundRouteTrace.Point point : result) {
            assertTrue("the received bottom road is continuous despite its coordinate shift",
                    point.getTileY() >= 1001);
        }
        assertTrue(result.size() <= 171);
    }

    @Test public void includesRemainingHighwayTimeInsteadOfChoosingNearestEntry() {
        List<GroundRouteTrace.Point> highway = new ArrayList<GroundRouteTrace.Point>();
        line(highway, 2, 2, 2, 12);
        line(highway, 2, 12, 12, 12);
        line(highway, 12, 12, 12, 2);
        CartTerrainRoutePlanner.Plan approach = new CartTerrainRoutePlanner(
                32, 32, 16, 16, 10000, 512, 40.0f, 0.7f).planToHighway(
                3, 2, highway, new CartTerrainRoutePlanner.Terrain() {
                    @Override public GroundRouteTrace.Point sample(int x, int y) {
                        return x == 2 || x == 12 || y == 12 ? road(x, y)
                                : new GroundRouteTrace.Point(x, y, 0.0f,
                                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                                GroundRouteTrace.WaterSource.NEAR,
                                HighwayTileIndex.Kind.NONE, false, 0.0f, false);
                    }
                });
        assertTrue(approach.isReachedFinalTarget());
        assertEquals(12, approach.getPlanningGoalX());
        assertEquals(2, approach.getPlanningGoalY());
    }

    @Test public void neverJoinsAnOverlappingCaveRoadOrBridgeDeckFromSurface() {
        List<GroundRouteTrace.Point> highway = new ArrayList<GroundRouteTrace.Point>();
        highway.add(new GroundRouteTrace.Point(2, 2, 0.0f,
                GroundRouteTrace.HeightSource.CAVE, 0.0f,
                GroundRouteTrace.WaterSource.CAVE, HighwayTileIndex.Kind.ROAD,
                false, 0.0f, true));
        highway.add(new GroundRouteTrace.Point(3, 2, 10.0f,
                GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.BRIDGE, false, 0.0f, true));
        CartTerrainRoutePlanner.Plan approach = new CartTerrainRoutePlanner(
                32, 32, 16, 4, 100, 512, 40.0f, 0.7f).planToHighway(
                2, 2, highway, new CartTerrainRoutePlanner.Terrain() {
                    @Override public GroundRouteTrace.Point sample(int x, int y) {
                        return road(x, y);
                    }
                });
        assertFalse(approach.isReachedFinalTarget());
        assertTrue(approach.getPoints().isEmpty());
    }

    private static GroundRouteTrace.Point road(int x, int y) {
        return new GroundRouteTrace.Point(x, y, 0.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR, HighwayTileIndex.Kind.ROAD,
                false, 0.0f, true);
    }

    private static void line(List<GroundRouteTrace.Point> points,
                             int x, int y, int endX, int endY) {
        int dx = Integer.signum(endX - x), dy = Integer.signum(endY - y);
        while (true) {
            points.add(road(x, y));
            if (x == endX && y == endY) return;
            x += dx; y += dy;
        }
    }
}
