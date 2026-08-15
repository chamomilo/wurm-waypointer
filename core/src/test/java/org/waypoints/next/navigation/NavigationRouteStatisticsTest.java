package org.waypoints.next.navigation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NavigationRouteStatisticsTest {
    @Test public void reportsPublishedRouteEndpointAndReachStatus() {
        NavigationRouteStatistics statistics = NavigationRouteStatistics.calculate(
                Arrays.asList(point(10, 20), point(11, 20), point(12, 21)),
                true, 12, 21, 40.0f, 0.7f);

        assertEquals(3, statistics.getPointCount());
        assertEquals(4.0d + Math.sqrt(32.0d),
                statistics.getLengthMetres(), 0.0001d);
        assertTrue(statistics.hasEndpoint());
        assertEquals(12, statistics.getEndpointTileX());
        assertEquals(21, statistics.getEndpointTileY());
        assertTrue(statistics.isReachedTarget());
    }

    @Test public void neverCallsAPartialEndpointTheDestination() {
        NavigationRouteStatistics statistics = NavigationRouteStatistics.calculate(
                Arrays.asList(point(10, 20), point(11, 20)),
                false, 99, 100, 40.0f, 0.7f);

        assertEquals(11, statistics.getEndpointTileX());
        assertEquals(20, statistics.getEndpointTileY());
        assertFalse(statistics.isReachedTarget());
    }

    @Test public void usesEightSixteenTwentyFourKilometreSpeedModel() {
        NavigationRouteStatistics offroad = NavigationRouteStatistics.calculate(
                Arrays.asList(point(0, 0), point(1, 0)), false,
                2, 0, 40.0f, 0.7f);
        NavigationRouteStatistics road = NavigationRouteStatistics.calculate(
                Arrays.asList(road(0, 0), road(1, 0)), false,
                2, 0, 40.0f, 0.7f);
        NavigationRouteStatistics highway = NavigationRouteStatistics.calculate(
                Arrays.asList(highway(0, 0), highway(1, 0)), false,
                2, 0, 40.0f, 0.7f);

        assertEquals(2L, offroad.getEstimatedDurationSeconds());
        assertEquals(1L, road.getEstimatedDurationSeconds());
        assertEquals(1L, highway.getEstimatedDurationSeconds());
    }

    @Test public void emptyRouteHasNoEndpoint() {
        NavigationRouteStatistics statistics = NavigationRouteStatistics.calculate(
                Collections.<GroundRouteTrace.Point>emptyList(), false,
                44, 55, 40.0f, 0.7f);

        assertEquals(0, statistics.getPointCount());
        assertEquals(0.0d, statistics.getLengthMetres(), 0.0d);
        assertEquals(0L, statistics.getEstimatedDurationSeconds());
        assertFalse(statistics.hasEndpoint());
        assertFalse(statistics.isReachedTarget());
        assertEquals(44, statistics.getTargetTileX());
        assertEquals(55, statistics.getTargetTileY());
    }

    @Test public void reportsTheCompleteCrossLayerPlanBeyondVisibleStage() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":4,\"endX\":3,\"endY\":4,\"type\":2},"
                        + "{\"startX\":4,\"startY\":4,\"endX\":16,\"endY\":4,\"type\":1},"
                        + "{\"startX\":17,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2},"
                        + "{\"startX\":3,\"startY\":4,\"endX\":3,\"endY\":12,\"type\":2},"
                        + "{\"startX\":3,\"startY\":12,\"endX\":17,\"endY\":12,\"type\":2},"
                        + "{\"startX\":17,\"startY\":12,\"endX\":17,\"endY\":4,\"type\":2}"
                        + "]", 32, 32);
        HighwayRoutePlanner.Plan complete = new HighwayRoutePlanner()
                .planAcrossLayers(0, 4, false, 20, 4, false, index, true);

        NavigationRouteStatistics statistics = NavigationRouteStatistics
                .calculateCompleteHighwayPlan(complete, 0, 4, 20, 4);

        assertTrue(statistics.isReachedTarget());
        assertEquals(20, statistics.getEndpointTileX());
        assertEquals(4, statistics.getEndpointTileY());
        assertEquals(21, statistics.getPointCount());
        assertEquals(80.0d, statistics.getLengthMetres(), 0.0001d);
        assertTrue(statistics.getEstimatedDurationSeconds() > 0L);
        assertTrue(statistics.getEstimatedDurationSeconds() < 30L);
    }

    @Test public void formatsSecondsMinutesAndHoursCompactly() {
        assertEquals("0 s", NavigationRouteStatistics.formatDuration(0L));
        assertEquals("59 s", NavigationRouteStatistics.formatDuration(59L));
        assertEquals("1 min 01 s",
                NavigationRouteStatistics.formatDuration(61L));
        assertEquals("1 h 01 min 01 s",
                NavigationRouteStatistics.formatDuration(3661L));
    }

    private static GroundRouteTrace.Point point(int x, int y) {
        return new GroundRouteTrace.Point(x, y, 10.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR);
    }

    private static GroundRouteTrace.Point road(int x, int y) {
        return new GroundRouteTrace.Point(x, y, 10.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR, true);
    }

    private static GroundRouteTrace.Point highway(int x, int y) {
        return new GroundRouteTrace.Point(x, y, 10.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR,
                HighwayTileIndex.Kind.ROAD, false, 0.0f, true);
    }
}
