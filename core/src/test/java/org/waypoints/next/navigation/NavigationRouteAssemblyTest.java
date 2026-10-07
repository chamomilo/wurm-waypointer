package org.waypoints.next.navigation;

import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public final class NavigationRouteAssemblyTest {
    @Test public void incompleteEntryStillUsesTheReachedPartOfTheSelectedHighway() {
        List<GroundRouteTrace.Point> highway = new ArrayList<GroundRouteTrace.Point>();
        for (int x = 2883; x >= 2850; x--) highway.add(road(x, 898));
        List<GroundRouteTrace.Point> approach = java.util.Arrays.asList(
                road(2865, 899), road(2864, 898));
        List<GroundRouteTrace.Point> joined = NavigationRouteAssembly.joinHighway(
                approach, highway, java.util.Collections.<GroundRouteTrace.Point>emptyList());
        assertEquals(16, joined.size());
        assertEquals(2865, joined.get(0).getTileX());
        assertEquals(2850, joined.get(joined.size() - 1).getTileX());
        for (GroundRouteTrace.Point point : joined) assertTrue(point.getTileX() <= 2865);
    }

    @Test public void missingHighwayTailDoesNotTeleportToTargetConnector() {
        List<GroundRouteTrace.Point> joined = NavigationRouteAssembly.joinHighway(
                java.util.Arrays.asList(road(0, 1), road(0, 0)),
                java.util.Arrays.asList(road(0, 0), road(1, 0)),
                java.util.Arrays.asList(road(8, 0), road(9, 0)));
        assertEquals(3, joined.size());
        assertEquals(1, joined.get(2).getTileX());
    }

    @Test public void cavePointCannotJoinSurfaceHighwayAtTheSameCoordinates() {
        GroundRouteTrace.Point cave = new GroundRouteTrace.Point(0, 0, -5,
                GroundRouteTrace.HeightSource.CAVE, 0, GroundRouteTrace.WaterSource.CAVE);
        assertTrue(NavigationRouteAssembly.joinHighway(java.util.Collections.singletonList(cave),
                java.util.Collections.singletonList(road(0, 0)),
                java.util.Collections.<GroundRouteTrace.Point>emptyList()).isEmpty());
    }

    @Test public void observedHighwayConnectorDoesNotVisitBranchAndReturn() {
        List<GroundRouteTrace.Point> points = new ArrayList<GroundRouteTrace.Point>();
        for (int x = 2860; x <= 2883; x++) points.add(road(x, 898));
        for (int x = 2882; x >= 2850; x--) points.add(road(x, 898));
        List<GroundRouteTrace.Point> route = NavigationRouteAssembly.withoutLoops(points);
        assertEquals(11, route.size());
        for (int i = 0; i < route.size(); i++) assertEquals(2860 - i, route.get(i).getTileX());
    }

    @Test public void surfaceAndCaveAtTheSameCoordinatesRemainDistinct() {
        List<GroundRouteTrace.Point> points = new ArrayList<GroundRouteTrace.Point>();
        points.add(road(5, 5));
        points.add(new GroundRouteTrace.Point(5, 5, -5,
                GroundRouteTrace.HeightSource.CAVE, 0, GroundRouteTrace.WaterSource.CAVE));
        points.add(new GroundRouteTrace.Point(6, 5, -5,
                GroundRouteTrace.HeightSource.CAVE, 0, GroundRouteTrace.WaterSource.CAVE));
        assertEquals(3, NavigationRouteAssembly.withoutLoops(points).size());
    }

    private static GroundRouteTrace.Point road(int x, int y) {
        return new GroundRouteTrace.Point(x, y, 0, GroundRouteTrace.HeightSource.NEAR,
                0, GroundRouteTrace.WaterSource.NEAR, HighwayTileIndex.Kind.ROAD, false, 0, true);
    }
}
