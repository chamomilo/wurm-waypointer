package org.waypoints.next.navigation;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class GroundRouteTraceTest {
    @Test public void blocksBeforeASlopeAboveTheConfiguredCartLimit() {
        GroundRouteTrace trace = GroundRouteTrace.analyse(3, 0, 0, 4, true,
                20.0f, 0.7f, Arrays.asList(
                        exact(0, 0, 10.0f, 0.0f),
                        exact(1, 0, 10.5f, 0.0f),
                        exact(2, 0, 13.0f, 0.0f),
                        exact(3, 0, 13.0f, 0.0f)));

        assertEquals(GroundRouteTrace.Result.BLOCKED, trace.getResult());
        assertEquals(1, trace.getBlockingSegmentIndex());
        assertEquals(2, trace.getRenderablePointCount());
        assertEquals(25.0f, trace.getSegments().get(1)
                .getAbsoluteSlopeDirtEstimate(), 0.0001f);
        assertEquals(62.5f, trace.getSegments().get(1).getGradePercent(),
                0.0001f);
    }

    @Test public void blocksWaterDeeperThanTheLargeCartServerProfile() {
        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 0, 0, 2, true,
                40.0f, 0.7f, Arrays.asList(
                        exact(0, 0, 0.0f, 0.0f),
                        exact(1, 0, -0.8f, 0.8f)));

        assertEquals(GroundRouteTrace.SegmentStatus.BLOCKED_WATER,
                trace.getSegments().get(0).getStatus());
        assertEquals(1, trace.getRenderablePointCount());
    }

    @Test public void labelsDistantTerrainAsUnverifiedInsteadOfSafe() {
        GroundRouteTrace.Point distant = new GroundRouteTrace.Point(1, 1,
                1.0f, GroundRouteTrace.HeightSource.DISTANT, 0.0f,
                GroundRouteTrace.WaterSource.DISTANT_SEA_LEVEL_ESTIMATE);
        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 1, 0, 2, true,
                40.0f, 0.7f, Arrays.asList(exact(0, 0, 1.0f, 0.0f), distant));

        assertEquals(GroundRouteTrace.Result.PARTIAL_OR_UNVERIFIED,
                trace.getResult());
        assertEquals(GroundRouteTrace.SegmentStatus.UNVERIFIED,
                trace.getSegments().get(0).getStatus());
    }

    @Test public void normalizesDiagonalSlopeToFourMetreWurmDirtUnits() {
        float diagonal = (float) Math.sqrt(32.0d);
        assertEquals(10.0f, GroundRouteTrace.slopeDirtEstimate(
                (float) Math.sqrt(2.0d), diagonal), 0.0001f);
    }

    @Test public void publishedBridgeIgnoresUnderlyingTerrainSlope() {
        GroundRouteTrace.Point from = new GroundRouteTrace.Point(0, 0, 10.0f,
                GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.BRIDGE, true);
        GroundRouteTrace.Point to = new GroundRouteTrace.Point(1, 0, -10.0f,
                GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.BRIDGE, false);
        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 0, 0, 2, true,
                20.0f, 0.7f, Arrays.asList(from, to));

        assertFalse(trace.getResult() == GroundRouteTrace.Result.BLOCKED);
    }

    @Test public void publishedRoadToBridgeRampIgnoresGroundBelowDeck() {
        GroundRouteTrace.Point road = new GroundRouteTrace.Point(0, 0, 0.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR,
                HighwayTileIndex.Kind.ROAD, false, 0.0f, true);
        GroundRouteTrace.Point ramp = new GroundRouteTrace.Point(1, 0, 30.0f,
                GroundRouteTrace.HeightSource.BRIDGE_GEOMETRY, 0.0f,
                GroundRouteTrace.WaterSource.HIGHWAY_ASSUMED_CLEAR,
                HighwayTileIndex.Kind.BRIDGE, true, 0.0f, true);

        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 0, 0, 2, true,
                20.0f, 0.7f, Arrays.asList(road, ramp));

        assertFalse(trace.getResult() == GroundRouteTrace.Result.BLOCKED);
        assertEquals(GroundRouteTrace.SegmentStatus.PASSABLE,
                trace.getSegments().get(0).getStatus());
    }

    @Test public void exactTileCornerSlopeOverridesSmoothedCentreSlope() {
        GroundRouteTrace.Point steep = new GroundRouteTrace.Point(0, 0, 1.0f,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR,
                HighwayTileIndex.Kind.NONE, false, 55.0f);
        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 0, 0, 2, true,
                40.0f, 0.7f, Arrays.asList(steep,
                        new GroundRouteTrace.Point(1, 0, 1.0f,
                                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                                GroundRouteTrace.WaterSource.NEAR,
                                HighwayTileIndex.Kind.NONE, false, 0.0f)));

        assertEquals(GroundRouteTrace.SegmentStatus.BLOCKED_SLOPE,
                trace.getSegments().get(0).getStatus());
        assertEquals(0.0f, trace.getSegments().get(0)
                .getAbsoluteSlopeDirtEstimate(), 0.0001f);
        assertEquals(55.0f, trace.getSegments().get(0)
                .getMaximumTraversedSlopeDirt(), 0.0001f);
    }

    @Test public void distantSmoothedSlopeUsesHalfEffectiveCartLimit() {
        GroundRouteTrace.Point distant = new GroundRouteTrace.Point(1, 0,
                2.5f, GroundRouteTrace.HeightSource.DISTANT, 0.0f,
                GroundRouteTrace.WaterSource.DISTANT_SEA_LEVEL_ESTIMATE);
        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 0, 0, 2, true,
                40.0f, 0.7f, Arrays.asList(exact(0, 0, 0.0f, 0.0f), distant));

        assertEquals(GroundRouteTrace.SegmentStatus.BLOCKED_SLOPE,
                trace.getSegments().get(0).getStatus());
        assertEquals(50.0f, trace.getSegments().get(0)
                .getMaximumTraversedSlopeDirt(), 0.0001f);
    }

    @Test public void centreOnlySlopeUsesHalfEffectiveCartLimit() {
        GroundRouteTrace trace = GroundRouteTrace.analyse(1, 0, 0, 2, true,
                40.0f, 0.7f, Arrays.asList(
                        exact(0, 0, 0.0f, 0.0f),
                        exact(1, 0, 2.1f, 0.0f)));

        assertEquals(GroundRouteTrace.SegmentStatus.BLOCKED_SLOPE,
                trace.getSegments().get(0).getStatus());
        assertEquals(21.0f, trace.getSegments().get(0)
                .getAbsoluteSlopeDirtEstimate(), 0.0001f);
        assertEquals(42.0f, trace.getSegments().get(0)
                .getMaximumTraversedSlopeDirt(), 0.0001f);
    }

    private static GroundRouteTrace.Point exact(int x, int y, float height,
                                                 float waterDepth) {
        return new GroundRouteTrace.Point(x, y, height,
                GroundRouteTrace.HeightSource.NEAR, waterDepth,
                GroundRouteTrace.WaterSource.NEAR);
    }
}
