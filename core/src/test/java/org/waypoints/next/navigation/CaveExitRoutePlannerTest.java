package org.waypoints.next.navigation;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public final class CaveExitRoutePlannerTest {
    private final CartTerrainRoutePlanner planner = new CartTerrainRoutePlanner(
            20, 20, 20, 20, 10000, 200, 40, 0.7f);

    @Test public void takesExitBehindPlayerInsteadOfStoppingAtWallTowardSurfaceTarget() {
        CartTerrainRoutePlanner.Terrain cave = new CartTerrainRoutePlanner.Terrain() {
            @Override public GroundRouteTrace.Point sample(int x, int y) {
                return y == 5 && x >= 5 && x <= 10 ? floor(x, y) : null;
            }
        };
        CartTerrainRoutePlanner.Plan route = CaveExitRoutePlanner.plan(planner, cave,
                5, 5, Arrays.asList(new CaveExitRoutePlanner.Exit(10, 5, 20)));
        assertNotNull(route);
        assertTrue(route.isReachedFinalTarget());
        assertEquals(10, route.getPoints().get(route.getPoints().size() - 1).getTileX());
    }

    @Test public void ignoresCloserExitAcrossRockAndUsesConnectedExit() {
        CartTerrainRoutePlanner.Terrain cave = new CartTerrainRoutePlanner.Terrain() {
            @Override public GroundRouteTrace.Point sample(int x, int y) {
                return (y == 5 && x >= 5 && x <= 10) || (x == 4 && y == 4)
                        ? floor(x, y) : null;
            }
        };
        CartTerrainRoutePlanner.Plan route = CaveExitRoutePlanner.plan(planner, cave,
                5, 5, Arrays.asList(new CaveExitRoutePlanner.Exit(4, 4, 0),
                        new CaveExitRoutePlanner.Exit(10, 5, 20)));
        assertNotNull(route);
        assertEquals(10, route.getPlanningGoalX());
    }

    @Test public void weighsSurfaceContinuationWhenBothExitsAreReachable() {
        CartTerrainRoutePlanner.Terrain cave = new CartTerrainRoutePlanner.Terrain() {
            @Override public GroundRouteTrace.Point sample(int x, int y) {
                return y == 5 && x >= 4 && x <= 10 ? floor(x, y) : null;
            }
        };
        CartTerrainRoutePlanner.Plan route = CaveExitRoutePlanner.plan(planner, cave,
                5, 5, Arrays.asList(new CaveExitRoutePlanner.Exit(4, 5, 100),
                        new CaveExitRoutePlanner.Exit(10, 5, 20)));
        assertEquals(10, route.getPlanningGoalX());
    }

    private static GroundRouteTrace.Point floor(int x, int y) {
        return new GroundRouteTrace.Point(x, y, -5, GroundRouteTrace.HeightSource.CAVE,
                0, GroundRouteTrace.WaterSource.CAVE, HighwayTileIndex.Kind.NONE, false, 0);
    }
}
