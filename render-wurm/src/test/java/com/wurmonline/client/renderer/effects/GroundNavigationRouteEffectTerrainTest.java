package com.wurmonline.client.renderer.effects;

import org.junit.Test;
import org.waypoints.next.navigation.GroundRouteTrace;
import org.waypoints.next.navigation.HighwayRoutePlanner;
import org.waypoints.next.navigation.HighwayTileIndex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GroundNavigationRouteEffectTerrainTest {
    @Test public void localTerrainRejectsRepeatedBufferCoordinates() {
        assertTrue(GroundNavigationRouteEffect.insideLocalTerrainWindow(
                1000, 800, 4002.0f, 3202.0f));
        assertFalse(GroundNavigationRouteEffect.insideLocalTerrainWindow(
                1512, 800, 4002.0f, 3202.0f));
        assertFalse(GroundNavigationRouteEffect.insideLocalTerrainWindow(
                1000, 288, 4002.0f, 3202.0f));
    }

    @Test public void localTerrainRequiresAllFourCornersInsideWindow() {
        assertTrue(GroundNavigationRouteEffect.insideLocalTerrainWindow(
                1254, 545, 4000.0f, 3200.0f));
        assertFalse(GroundNavigationRouteEffect.insideLocalTerrainWindow(
                1255, 800, 4000.0f, 3200.0f));
        assertFalse(GroundNavigationRouteEffect.insideLocalTerrainWindow(
                1000, 544, 4000.0f, 3200.0f));
    }

    @Test public void tunnelPlanMustActuallyEndAtUndergroundTarget() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":0,\"endX\":4,\"endY\":0,\"type\":2},"
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":8,\"type\":1}"
                        + "]", 16, 16);
        HighwayRoutePlanner planner = new HighwayRoutePlanner();
        HighwayRoutePlanner.Plan direct = planner
                .planIncludingNecessaryDetours(4, 0, 4, 8, index,
                        HighwayRoutePlanner.NetworkLayer.TUNNEL);
        HighwayRoutePlanner.Plan exit = planner.planTunnelToSurfacePortal(
                4, 8, 0, 0, index);

        assertTrue(GroundNavigationRouteEffect.planEndsAt(direct, 4, 8));
        assertFalse(GroundNavigationRouteEffect.planEndsAt(exit, 4, 8));
        assertTrue(GroundNavigationRouteEffect.planEndsAt(exit, 4, 0));
    }

    @Test public void surfacePreviewSelectsOnlyTheFirstTunnelStage() {
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

        List<HighwayRoutePlanner.TileStep> preview =
                GroundNavigationRouteEffect.firstTunnelStageSteps(complete);

        assertEquals(13, preview.size());
        assertEquals(4, preview.get(0).getTileX());
        assertEquals(16, preview.get(preview.size() - 1).getTileX());
        assertTrue(preview.get(0).isPortal());
        assertTrue(preview.get(preview.size() - 1).isPortal());
        for (HighwayRoutePlanner.TileStep step : preview) {
            assertEquals(HighwayTileIndex.Kind.TUNNEL, step.getKind());
        }
    }

    @Test public void mapRouteContinuesThroughTunnelAndToFinalTarget() {
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

        GroundNavigationRouteEffect.RouteSnapshot route =
                GroundNavigationRouteEffect.completeMapRoute(
                        complete, Collections.singletonList(mapRoad(0, 4)),
                        java.util.Arrays.asList(mapRoad(20, 4), mapRoad(21, 5),
                                mapRoad(22, 6), mapRoad(23, 6)));

        assertEquals(0, route.getTileX(0));
        assertEquals(4, route.getTileY(0));
        assertTrue(contains(route, 10, 4));
        assertTrue(contains(route, 20, 4));
        for (int i = 0; i < route.getPointCount(); i++) {
            if (route.getTileX(i) >= 4 && route.getTileX(i) <= 16)
                assertEquals(-1, route.getLayer(i));
            if (route.getTileX(i) >= 17) assertEquals(0, route.getLayer(i));
        }
        assertEquals(23, route.getTileX(route.getPointCount() - 1));
        assertEquals(6, route.getTileY(route.getPointCount() - 1));
    }

    @Test public void mapDoesNotInventTargetConnectorAcrossUnreceivedTerrain() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2}]", 32, 32);
        HighwayRoutePlanner.Plan complete = new HighwayRoutePlanner().plan(0, 4, 20, 4, index);
        GroundNavigationRouteEffect.RouteSnapshot route = GroundNavigationRouteEffect.completeMapRoute(
                complete, Collections.singletonList(mapRoad(0, 4)),
                Collections.singletonList(mapRoad(20, 4)));
        assertEquals(20, route.getTileX(route.getPointCount() - 1));
        assertEquals(4, route.getTileY(route.getPointCount() - 1));
    }

    @Test public void mapDoesNotReplaceIncompleteApproachWithStraightLine() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":10,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2}]", 32, 32);
        HighwayRoutePlanner.Plan complete = new HighwayRoutePlanner().plan(10, 4, 20, 4, index);
        GroundNavigationRouteEffect.RouteSnapshot route = GroundNavigationRouteEffect.completeMapRoute(
                complete, java.util.Arrays.asList(mapRoad(0, 4), mapRoad(1, 4)),
                Collections.<GroundRouteTrace.Point>emptyList());
        assertEquals(2, route.getPointCount());
        assertEquals(1, route.getTileX(1));
    }

    @Test public void mapJoinsWhereApproachActuallyMeetsTrunkAfterBranchRemoval() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2}]", 32, 32);
        HighwayRoutePlanner.Plan complete = new HighwayRoutePlanner().plan(0, 4, 20, 4, index);
        GroundNavigationRouteEffect.RouteSnapshot route = GroundNavigationRouteEffect.completeMapRoute(
                complete, java.util.Arrays.asList(mapRoad(5, 5), mapRoad(5, 4)),
                Collections.<GroundRouteTrace.Point>emptyList());
        assertEquals(5, route.getTileX(0));
        assertFalse(contains(route, 0, 4));
        assertEquals(20, route.getTileX(route.getPointCount() - 1));
    }

    @Test public void fullMapIncludesCaveApproachAndSurfaceContinuationFromItsExit() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2}]", 32, 32);
        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(0, 4, 20, 4, index);
        GroundNavigationRouteEffect.RouteSnapshot surface = GroundNavigationRouteEffect.completeMapRoute(
                plan, Collections.singletonList(mapRoad(0, 4)),
                Collections.<GroundRouteTrace.Point>emptyList());
        GroundNavigationRouteEffect.RouteSnapshot whole = GroundNavigationRouteEffect.prependMapRoute(
                java.util.Arrays.asList(cavePoint(0, 5), cavePoint(0, 4)), surface);
        assertEquals(surface.getPointCount() + 2, whole.getPointCount());
        assertEquals(5, whole.getTileY(0));
        assertEquals(-1, whole.getLayer(0));
        assertEquals(-1, whole.getLayer(1));
        assertEquals(0, whole.getLayer(2));
        assertEquals(20, whole.getTileX(whole.getPointCount() - 1));
    }

    @Test public void caveApproachDoesNotAttachSurfaceRouteFromADifferentExit() {
        GroundNavigationRouteEffect.RouteSnapshot otherExit = GroundNavigationRouteEffect.completeMapRoute(
                null, java.util.Arrays.asList(mapRoad(8, 4), mapRoad(9, 4)),
                Collections.<GroundRouteTrace.Point>emptyList());
        GroundNavigationRouteEffect.RouteSnapshot whole = GroundNavigationRouteEffect.prependMapRoute(
                java.util.Arrays.asList(cavePoint(0, 5), cavePoint(0, 4)), otherExit);
        assertEquals(2, whole.getPointCount());
        assertEquals(0, whole.getTileX(1));
    }

    private static GroundRouteTrace.Point cavePoint(int x, int y) {
        return new GroundRouteTrace.Point(x, y, -5, GroundRouteTrace.HeightSource.CAVE,
                0, GroundRouteTrace.WaterSource.CAVE);
    }

    private static GroundRouteTrace.Point mapRoad(int x, int y) {
        return new GroundRouteTrace.Point(x, y, 0, GroundRouteTrace.HeightSource.NEAR,
                0, GroundRouteTrace.WaterSource.NEAR, HighwayTileIndex.Kind.ROAD, false, 0, true);
    }

    @Test public void crossingUsesRoadOnSurfaceAndTunnelUnderground() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":8,\"type\":1},"
                        + "{\"startX\":0,\"startY\":4,\"endX\":8,\"endY\":4,\"type\":2}"
                        + "]", 16, 16);
        HighwayTileIndex.Tile crossing = index.get(4, 4);

        assertEquals(HighwayTileIndex.Kind.ROAD,
                GroundNavigationRouteEffect.publishedKindFor(crossing,
                        GroundNavigationRouteEffect.TravelLayer.SURFACE));
        assertEquals(HighwayTileIndex.Kind.TUNNEL,
                GroundNavigationRouteEffect.publishedKindFor(crossing,
                        GroundNavigationRouteEffect.TravelLayer.TUNNEL));
    }

    @Test public void bridgeMarkIsUsedOnlyWhileActuallyOnBridge() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":4,\"endX\":8,\"endY\":4,\"type\":0},"
                        + "{\"startX\":0,\"startY\":4,\"endX\":8,\"endY\":4,\"type\":2}"
                        + "]", 16, 16);
        HighwayTileIndex.Tile overlap = index.get(4, 4);

        assertEquals(HighwayTileIndex.Kind.ROAD,
                GroundNavigationRouteEffect.publishedKindFor(overlap,
                        GroundNavigationRouteEffect.TravelLayer.SURFACE));
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                GroundNavigationRouteEffect.publishedKindFor(overlap,
                        GroundNavigationRouteEffect.TravelLayer.BRIDGE));
        assertEquals(HighwayTileIndex.Kind.NONE,
                GroundNavigationRouteEffect.publishedKindFor(overlap,
                        GroundNavigationRouteEffect.TravelLayer.TUNNEL));
    }

    @Test public void bridgeOnlyTileIsOrdinaryTerrainFromBelow() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":4,\"endX\":8,"
                        + "\"endY\":4,\"type\":0}]", 16, 16);
        HighwayTileIndex.Tile deckAbove = index.get(4, 4);

        assertEquals(HighwayTileIndex.Kind.NONE,
                GroundNavigationRouteEffect.publishedKindFor(deckAbove,
                        GroundNavigationRouteEffect.TravelLayer.SURFACE));
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                GroundNavigationRouteEffect.publishedKindFor(deckAbove,
                        GroundNavigationRouteEffect.TravelLayer.BRIDGE));
    }

    @Test public void unloadedPublishedBridgeUsesLiveDeckPlaceholder() {
        org.waypoints.next.navigation.GroundRouteTrace.Point point =
                GroundNavigationRouteEffect.interpolatedBridgePoint(
                        3362, 1081, false, 12.5f);

        assertEquals(12.5f, point.getGroundHeightMetres(), 0.0001f);
        assertEquals(org.waypoints.next.navigation.GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED,
                point.getHeightSource());
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                point.getHighwayKind());
        assertEquals(0.0f, point.getWaterDepthMetres(), 0.0001f);
    }

    @Test public void unloadedPublishedTunnelKeepsRoutePlaceholder() {
        org.waypoints.next.navigation.GroundRouteTrace.Point point =
                GroundNavigationRouteEffect.interpolatedTunnelPoint(
                        3072, 1003, false, 64.4f);

        assertEquals(64.4f, point.getGroundHeightMetres(), 0.0001f);
        assertEquals(org.waypoints.next.navigation.GroundRouteTrace.HeightSource.HIGHWAY_INTERPOLATED,
                point.getHeightSource());
        assertEquals(HighwayTileIndex.Kind.TUNNEL,
                point.getHighwayKind());
        assertEquals(0.0f, point.getWaterDepthMetres(), 0.0001f);
    }

    @Test public void exactCornerEdgeProducesUnsmoothenedWurmSlope() {
        assertEquals(55.0f, GroundNavigationRouteEffect.tileMaximumSlopeDirt(
                0.0f, 5.5f, 0.0f, 0.0f), 0.0001f);
    }

    @Test public void oppositeCornersUseServerVehicleSteepnessRange() {
        // Every cardinal edge is only 39 slope, but Wurm compares the full
        // 7.8 metre corner range and therefore treats this as 78 slope.
        assertEquals(78.0f, GroundNavigationRouteEffect.tileMaximumSlopeDirt(
                0.0f, 3.9f, 3.9f, 7.8f), 0.0001f);
    }

    @Test public void waterDepthCanUseLowestCornerInsteadOfTileCentre() {
        assertEquals(-1.25f, GroundNavigationRouteEffect.minimumTileHeight(
                0.5f, 0.75f, -1.25f, 0.25f), 0.0001f);
    }

    @Test public void legacyStableTailConnectorIsAbsent() {
        for (java.lang.reflect.Method method
                : GroundNavigationRouteEffect.class.getDeclaredMethods()) {
            assertFalse("old route-tail stabilizer must stay removed",
                    "stabilizeRoute".equals(method.getName()));
        }
        for (java.lang.reflect.Field field
                : GroundNavigationRouteEffect.class.getDeclaredFields()) {
            assertFalse("old published route must not feed the next plan",
                    "publishedPlan".equals(field.getName()));
        }
    }

    @Test public void diagnosticsIgnoreProgressAlongTheSelectedRouteTail() {
        List<GroundRouteTrace.Point> original = diagnosticRoute(0, 24);
        List<GroundRouteTrace.Point> advanced = new ArrayList<GroundRouteTrace.Point>(
                original.subList(7, original.size()));

        assertTrue(GroundNavigationRouteEffect.stableDiagnosticContinuation(
                original, advanced, 8));

        List<GroundRouteTrace.Point> localConnector = diagnosticRoute(100, 3);
        localConnector.addAll(original.subList(7, original.size()));
        assertTrue(GroundNavigationRouteEffect.stableDiagnosticContinuation(
                original, localConnector, 8));

        List<GroundRouteTrace.Point> differentRoute = diagnosticRoute(100, 12);
        differentRoute.addAll(original.subList(19, original.size()));
        assertFalse(GroundNavigationRouteEffect.stableDiagnosticContinuation(
                original, differentRoute, 8));

        List<GroundRouteTrace.Point> changedDestination =
                new ArrayList<GroundRouteTrace.Point>(advanced);
        changedDestination.set(changedDestination.size() - 1,
                diagnosticPoint(23, 2.0f));
        assertFalse(GroundNavigationRouteEffect.stableDiagnosticContinuation(
                original, changedDestination, 8));
    }

    @Test public void dashPhaseMovesForwardAndWrapsOncePerSecond() {
        float start = GroundNavigationRouteEffect.routeDashPhase(0L);
        float middle = GroundNavigationRouteEffect.routeDashPhase(500_000_000L);
        float wrapped = GroundNavigationRouteEffect.routeDashPhase(
                1_000_000_000L);
        assertEquals(0.0f, start, 0.0001f);
        assertEquals(1.75f, middle, 0.0001f);
        assertEquals(0.0f, wrapped, 0.0001f);
    }

    @Test public void pulseTravelsOneSecondLingersAndLaunchesEveryTwoSeconds() {
        assertEquals(0.0f, GroundNavigationRouteEffect.pulseTravelProgress(0L),
                0.0001f);
        assertEquals(0.5f, GroundNavigationRouteEffect.pulseTravelProgress(
                500_000_000L), 0.0001f);
        assertEquals(1.0f, GroundNavigationRouteEffect.pulseTravelProgress(
                1_000_000_000L), 0.0001f);
        assertEquals(1.0f, GroundNavigationRouteEffect.pulseTravelProgress(
                1_250_000_000L), 0.0001f);
        assertEquals(-1.0f, GroundNavigationRouteEffect.pulseTravelProgress(
                1_500_000_000L), 0.0001f);
        assertEquals(0.0f, GroundNavigationRouteEffect.pulseTravelProgress(
                2_000_000_000L), 0.0001f);
        assertEquals(0.5f,
                GroundNavigationRouteEffect.pulseTrailStartProgress(
                        1_000_000_000L), 0.0001f);
        assertEquals(0.75f,
                GroundNavigationRouteEffect.pulseTrailStartProgress(
                        1_250_000_000L), 0.0001f);
        assertEquals(1.0f,
                GroundNavigationRouteEffect.pulseTrailStartProgress(
                        1_500_000_000L), 0.0001f);
        assertEquals(1.0f, GroundNavigationRouteEffect.pulseTrailAlpha(
                750_000_000L, 0.75f), 0.0001f);
        assertEquals(0.5f, GroundNavigationRouteEffect.pulseTrailAlpha(
                1_000_000_000L, 0.75f), 0.0001f);
        assertEquals(0.0f, GroundNavigationRouteEffect.pulseTrailAlpha(
                1_250_000_000L, 0.75f), 0.0001f);
        assertTrue(GroundNavigationRouteEffect.pulseHeadVisible(
                999_999_999L));
        assertFalse(GroundNavigationRouteEffect.pulseHeadVisible(
                1_000_000_000L));
        assertEquals(0L, GroundNavigationRouteEffect.pulseCycleIndex(
                1_999_999_999L));
        assertEquals(1L, GroundNavigationRouteEffect.pulseCycleIndex(
                2_000_000_000L));
    }

    @Test public void pulseCapturesWholeConfiguredRoutePrefix() {
        int routePoints = 100;
        int[] tileX = new int[routePoints];
        int[] tileY = new int[routePoints];
        float[] height = new float[routePoints];
        for (int i = 0; i < routePoints; i++) {
            tileX[i] = i;
            tileY[i] = 0;
        }
        float[] capturedX = new float[routePoints + 1];
        float[] capturedY = new float[routePoints + 1];
        float[] capturedHeight = new float[routePoints + 1];
        float[] capturedDistance = new float[routePoints + 1];

        int capturedPoints = GroundNavigationRouteEffect.capturePulsePath(
                2.0f, 2.0f, 0.0f, tileX, tileY, height, routePoints,
                240.0f, capturedX, capturedY, capturedHeight,
                capturedDistance);

        assertEquals(61, capturedPoints);
        assertEquals(240.0f, capturedDistance[capturedPoints - 1], 0.0001f);
        assertEquals(242.0f, capturedX[capturedPoints - 1], 0.0001f);
    }

    private static List<GroundRouteTrace.Point> diagnosticRoute(int startX,
                                                                 int count) {
        List<GroundRouteTrace.Point> points =
                new ArrayList<GroundRouteTrace.Point>();
        for (int i = 0; i < count; i++) {
            points.add(diagnosticPoint(startX + i, 1.0f));
        }
        return points;
    }

    private static GroundRouteTrace.Point diagnosticPoint(int x,
                                                            float height) {
        return new GroundRouteTrace.Point(x, 0, height,
                GroundRouteTrace.HeightSource.NEAR, 0.0f,
                GroundRouteTrace.WaterSource.NEAR);
    }

    private static boolean contains(
            GroundNavigationRouteEffect.RouteSnapshot route, int x, int y) {
        for (int index = 0; index < route.getPointCount(); index++) {
            if (route.getTileX(index) == x && route.getTileY(index) == y) {
                return true;
            }
        }
        return false;
    }

}
