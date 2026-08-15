package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HighwayRoutePlannerTest {
    @Test public void choosesLongerRoadWhenItsTravelTimeIsLower() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "var highways=["
                        + "{\"startX\":0,\"startY\":2,\"endX\":10,\"endY\":2,\"type\":\"2\"},"
                        + "{\"startX\":10,\"startY\":2,\"endX\":20,\"endY\":0,\"type\":\"2\"}];",
                64, 64);
        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 0, 20, 0, index);
        assertTrue(plan.usesHighway());
        assertTrue(plan.getEstimatedTimeTiles()
                < plan.getDirectOffroadTimeTiles());
        assertEquals(0, plan.getEntryX());
        assertEquals(2, plan.getEntryY());
        assertEquals(20, plan.getExitX());
        assertEquals(0, plan.getExitY());
    }

    @Test public void rejectsRoadDetourThatDoesNotSaveMeaningfulTime() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":30,\"endX\":20,\"endY\":30,\"type\":2}]",
                64, 64);
        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 0, 20, 0, index);
        assertFalse(plan.usesHighway());
    }

    @Test public void permitsPublishedDetourWhenTerrainRouteIsBlocked() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":30,\"endX\":20,\"endY\":30,\"type\":2}]",
                64, 64);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planIncludingNecessaryDetours(0, 0, 20, 0, index);

        assertTrue(plan.usesHighway());
        assertTrue(plan.getEstimatedTimeTiles()
                > plan.getDirectOffroadTimeTiles());
    }

    @Test public void joinsAdjacentInclusiveEndpointsOnSklotopolisBridge() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":1147,\"startY\":695,\"endX\":1285,\"endY\":695,\"type\":2},"
                        + "{\"startX\":1312,\"startY\":695,\"endX\":1285,\"endY\":695,\"type\":2},"
                        + "{\"startX\":1346,\"startY\":695,\"endX\":1312,\"endY\":695,\"type\":2},"
                        + "{\"startX\":1346,\"startY\":695,\"endX\":1377,\"endY\":695,\"type\":2},"
                        + "{\"startX\":1378,\"startY\":695,\"endX\":1386,\"endY\":695,\"type\":0},"
                        + "{\"startX\":1387,\"startY\":695,\"endX\":1390,\"endY\":695,\"type\":2},"
                        + "{\"startX\":1391,\"startY\":695,\"endX\":1396,\"endY\":695,\"type\":0},"
                        + "{\"startX\":1397,\"startY\":695,\"endX\":1418,\"endY\":695,\"type\":0},"
                        + "{\"startX\":1419,\"startY\":695,\"endX\":1428,\"endY\":695,\"type\":0},"
                        + "{\"startX\":1429,\"startY\":695,\"endX\":1438,\"endY\":695,\"type\":0},"
                        + "{\"startX\":1439,\"startY\":695,\"endX\":1486,\"endY\":695,\"type\":2}"
                        + "]", 4096, 4096);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                1440, 708, 802, 779, index);

        assertTrue(plan.usesHighway());
        assertTrue(contains(plan, 1429, 695, HighwayTileIndex.Kind.BRIDGE));
        assertTrue(contains(plan, 1397, 695, HighwayTileIndex.Kind.BRIDGE));
        assertEquals(1438, plan.getEntryX());
        assertEquals(695, plan.getEntryY());
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                plan.getHighwaySteps().get(0).getKind());
        assertTrue(plan.getHighwaySteps().get(0).isPortal());
    }

    @Test public void preservesBridgeAsPublishedSegment() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":0,\"endX\":20,\"endY\":0,\"type\":0}]",
                64, 64);
        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 1, 20, 1, index);
        assertTrue(plan.usesHighway());
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                plan.getHighwaySteps().get(10).getKind());
        assertFalse(plan.getHighwaySteps().get(10).isPortal());
        assertTrue(plan.getHighwaySteps().get(0).isPortal());
    }

    @Test public void startsFromOccupiedMiddleOfPublishedBridge() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":0,\"endX\":4,\"endY\":0,\"type\":2},"
                        + "{\"startX\":5,\"startY\":0,\"endX\":10,\"endY\":0,\"type\":0},"
                        + "{\"startX\":11,\"startY\":0,\"endX\":20,\"endY\":0,\"type\":2}"
                        + "]", 64, 64);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                7, 0, 20, 0, index);

        assertTrue(plan.usesHighway());
        assertEquals(7, plan.getEntryX());
        assertEquals(0, plan.getEntryY());
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                plan.getHighwaySteps().get(0).getKind());
        assertFalse(plan.getHighwaySteps().get(0).isPortal());
    }

    @Test public void surfaceBelowBridgeCannotEnterItsMiddle() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":0,\"endX\":4,\"endY\":0,\"type\":2},"
                        + "{\"startX\":5,\"startY\":0,\"endX\":10,\"endY\":0,\"type\":0},"
                        + "{\"startX\":11,\"startY\":0,\"endX\":20,\"endY\":0,\"type\":2}"
                        + "]", 64, 64);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                7, 0, 20, 0, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        assertEquals(10, plan.getEntryX());
        assertEquals(0, plan.getEntryY());
        assertEquals(HighwayTileIndex.Kind.BRIDGE,
                plan.getHighwaySteps().get(0).getKind());
        assertTrue(plan.getHighwaySteps().get(0).isPortal());
    }

    @Test public void surfaceRoadUnderBridgeStaysOnSurfaceLayer() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":5,\"endX\":10,\"endY\":5,\"type\":2},"
                        + "{\"startX\":5,\"startY\":0,\"endX\":5,\"endY\":10,\"type\":0}"
                        + "]", 16, 16);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 5, 5, 5, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            assertEquals(HighwayTileIndex.Kind.ROAD, step.getKind());
        }
        assertTrue(index.get(5, 5).hasKind(HighwayTileIndex.Kind.ROAD));
        assertTrue(index.get(5, 5).hasKind(HighwayTileIndex.Kind.BRIDGE));
    }

    @Test public void actualBridgeLayerCanStartInMiddleOfDeck() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":0,\"endX\":4,\"endY\":0,\"type\":2},"
                        + "{\"startX\":5,\"startY\":0,\"endX\":10,\"endY\":0,\"type\":0},"
                        + "{\"startX\":11,\"startY\":0,\"endX\":20,\"endY\":0,\"type\":2}"
                        + "]", 64, 64);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                7, 0, 20, 0, index,
                HighwayRoutePlanner.NetworkLayer.BRIDGE);

        assertTrue(plan.usesHighway());
        assertEquals(7, plan.getEntryX());
        assertEquals(0, plan.getEntryY());
        assertFalse(plan.getHighwaySteps().get(0).isPortal());
    }

    @Test public void liveConfirmedSecondLaneProjectsToBridgeAxis() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":8,\"startY\":0,\"endX\":8,\"endY\":12,\"type\":0},"
                        + "{\"startX\":8,\"startY\":13,\"endX\":8,\"endY\":20,\"type\":2}"
                        + "]", 32, 32);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planBridgeToSurfacePortal(7, 6, 8, 20, index);

        assertTrue(plan.usesHighway());
        assertEquals(7, plan.getEntryX());
        assertEquals(6, plan.getEntryY());
        assertEquals(8, plan.getExitX());
        assertEquals(13, plan.getExitY());
    }

    @Test public void everyLaneOfObservedBridgeComponentsFindsForwardRamp() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3363,\"startY\":1018,\"endX\":3363,\"endY\":1050,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1051,\"endX\":3363,\"endY\":1052,\"type\":2},"
                        + "{\"startX\":3363,\"startY\":1053,\"endX\":3363,\"endY\":1085,\"type\":0},"
                        + "{\"startX\":3391,\"startY\":1087,\"endX\":3363,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1087,\"endX\":3363,\"endY\":1120,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1121,\"endX\":3363,\"endY\":1134,\"type\":2}"
                        + "]", 4096, 4096);
        HighwayRoutePlanner planner = new HighwayRoutePlanner();

        for (int x = 3362; x <= 3363; x++) {
            for (int y = 1053; y <= 1085; y++) {
                HighwayRoutePlanner.Plan plan = planner
                        .planBridgeToSurfacePortal(x, y, 3222, 1157, index);
                assertTrue("missing north bridge route at " + x + ',' + y,
                        plan.usesHighway());
                assertEquals(x, plan.getEntryX());
                assertEquals(y, plan.getEntryY());
                assertEquals(1086, plan.getExitY());
            }
            for (int y = 1087; y <= 1120; y++) {
                HighwayRoutePlanner.Plan plan = planner
                        .planBridgeToSurfacePortal(x, y, 3222, 1157, index);
                assertTrue("missing south bridge route at " + x + ',' + y,
                        plan.usesHighway());
                assertEquals(x, plan.getEntryX());
                assertEquals(y, plan.getEntryY());
                assertEquals(1120, plan.getExitY());
            }
        }
    }

    @Test public void occupiedCompositeBridgeRunsAcrossEverySpanToRamp() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":4,\"endX\":4,\"endY\":4,\"type\":2},"
                        + "{\"startX\":5,\"startY\":4,\"endX\":10,\"endY\":4,\"type\":0},"
                        + "{\"startX\":10,\"startY\":4,\"endX\":15,\"endY\":4,\"type\":0},"
                        + "{\"startX\":16,\"startY\":4,\"endX\":24,\"endY\":4,\"type\":2}"
                        + "]", 32, 16);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planBridgeToSurfacePortal(7, 4, 24, 4, index);

        assertTrue(plan.usesHighway());
        assertEquals(7, plan.getHighwaySteps().get(0).getTileX());
        assertFalse(plan.getHighwaySteps().get(0).isPortal());
        assertTrue(contains(plan, 10, 4, HighwayTileIndex.Kind.BRIDGE));
        HighwayRoutePlanner.TileStep portal = plan.getHighwaySteps().get(
                plan.getHighwaySteps().size() - 2);
        assertEquals(15, portal.getTileX());
        assertEquals(4, portal.getTileY());
        assertTrue(portal.isPortal());
        HighwayRoutePlanner.TileStep handoff = plan.getHighwaySteps().get(
                plan.getHighwaySteps().size() - 1);
        assertEquals(16, handoff.getTileX());
        assertEquals(4, handoff.getTileY());
        assertEquals(HighwayTileIndex.Kind.ROAD, handoff.getKind());
    }

    @Test public void completeObservedBridgeChainRoutesFromEveryLayer() {
        HighwayTileIndex index = observedWhiteLightBridgeChain();
        HighwayRoutePlanner planner = new HighwayRoutePlanner();

        HighwayRoutePlanner.Plan eastern = planner
                .planFromOccupiedBridgeToTarget(3421, 1086, 3222, 1157,
                        index);
        assertObservedWhiteLightChain(eastern, 3421, 1086);
        assertTrue(contains(eastern, 3395, 1087,
                HighwayTileIndex.Kind.BRIDGE));
        assertTrue(contains(eastern, 3394, 1087,
                HighwayTileIndex.Kind.ROAD));

        HighwayRoutePlanner.Plan curved = planner
                .planFromOccupiedBridgeToTarget(3384, 1086, 3222, 1157,
                        index);
        assertObservedWhiteLightChain(curved, 3384, 1086);

        HighwayRoutePlanner.Plan north = planner
                .planFromOccupiedBridgeToTarget(3362, 1078, 3222, 1157,
                        index);
        assertObservedWhiteLightChain(north, 3362, 1078);
        assertTrue(contains(north, 3363, 1086,
                HighwayTileIndex.Kind.ROAD));

        assertEveryObservedBridgeTileRoutesToWhiteLight(planner, index);

        for (int x = 3392; x <= 3394; x++) {
            for (int y = 1086; y <= 1087; y++) {
                HighwayRoutePlanner.Plan platform = planner
                        .planIncludingNecessaryDetours(x, y, 3222, 1157,
                                index,
                                HighwayRoutePlanner.NetworkLayer.SURFACE);
                assertTrue("missing 3x2 platform route at " + x + ',' + y,
                        platform.usesHighway());
                assertTrue(contains(platform, 3391, 1087,
                        HighwayTileIndex.Kind.BRIDGE));
                assertTrue(contains(platform, 3363, 1120,
                        HighwayTileIndex.Kind.BRIDGE));
            }
        }
    }

    private static void assertEveryObservedBridgeTileRoutesToWhiteLight(
            HighwayRoutePlanner planner, HighwayTileIndex index) {
        for (int x = 3395; x <= 3429; x++) {
            assertBridgeLaneRoutes(planner, index, x, 1086);
            assertBridgeLaneRoutes(planner, index, x, 1087);
        }
        for (int x = 3363; x <= 3391; x++) {
            assertBridgeLaneRoutes(planner, index, x, 1086);
            assertBridgeLaneRoutes(planner, index, x, 1087);
        }
        for (int y = 1053; y <= 1085; y++) {
            assertBridgeLaneRoutes(planner, index, 3362, y);
            assertBridgeLaneRoutes(planner, index, 3363, y);
        }
        for (int y = 1087; y <= 1120; y++) {
            assertBridgeLaneRoutes(planner, index, 3362, y);
            assertBridgeLaneRoutes(planner, index, 3363, y);
        }
    }

    private static void assertBridgeLaneRoutes(
            HighwayRoutePlanner planner, HighwayTileIndex index,
            int startX, int startY) {
        HighwayRoutePlanner.Plan plan = planner
                .planFromOccupiedBridgeToTarget(startX, startY, 3222, 1157,
                        index);
        assertTrue("missing bridge route at " + startX + ',' + startY,
                plan.usesHighway());
        assertEquals(startX, plan.getEntryX());
        assertEquals(startY, plan.getEntryY());
        assertTrue("route misses final ramp at " + startX + ',' + startY,
                contains(plan, 3363, 1120,
                        HighwayTileIndex.Kind.BRIDGE));
        assertTrue("route misses surface Highway at " + startX + ','
                        + startY, contains(plan, 3363, 1121,
                HighwayTileIndex.Kind.ROAD));
    }

    private static HighwayTileIndex observedWhiteLightBridgeChain() {
        return HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3363,\"startY\":1053,\"endX\":3363,\"endY\":1085,\"type\":0},"
                        + "{\"startX\":3391,\"startY\":1087,\"endX\":3363,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3394,\"startY\":1087,\"endX\":3392,\"endY\":1087,\"type\":2},"
                        + "{\"startX\":3429,\"startY\":1087,\"endX\":3395,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1120,\"endX\":3363,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1134,\"endX\":3363,\"endY\":1121,\"type\":2},"
                        + "{\"startX\":3363,\"startY\":1134,\"endX\":3220,\"endY\":1134,\"type\":2}"
                        + "]", 4096, 4096);
    }

    private static void assertObservedWhiteLightChain(
            HighwayRoutePlanner.Plan plan, int startX, int startY) {
        assertTrue(plan.usesHighway());
        assertEquals(startX, plan.getEntryX());
        assertEquals(startY, plan.getEntryY());
        assertTrue(contains(plan, 3363, 1087,
                HighwayTileIndex.Kind.BRIDGE));
        assertTrue(contains(plan, 3363, 1120,
                HighwayTileIndex.Kind.BRIDGE));
        assertTrue(contains(plan, 3363, 1121,
                HighwayTileIndex.Kind.ROAD));
        assertTrue(contains(plan, 3222, 1134,
                HighwayTileIndex.Kind.ROAD));
    }

    @Test public void observedBridgeSideLaneRoutesSouthAcrossJunction() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3391,\"startY\":1087,\"endX\":3363,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1087,\"endX\":3363,\"endY\":1120,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1121,\"endX\":3363,\"endY\":1134,\"type\":2}"
                        + "]", 4096, 4096);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planBridgeToSurfacePortal(3369, 1086, 3222, 1157, index);

        assertTrue(plan.usesHighway());
        assertEquals(3369, plan.getEntryX());
        assertEquals(1086, plan.getEntryY());
        assertEquals(3363, plan.getExitX());
        assertEquals(1120, plan.getExitY());
    }

    @Test public void surfaceCanEnterObservedTurningBridgeJunction() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3391,\"startY\":1087,\"endX\":3363,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1087,\"endX\":3363,\"endY\":1120,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1121,\"endX\":3363,\"endY\":1134,\"type\":2},"
                        + "{\"startX\":3363,\"startY\":1134,\"endX\":3220,\"endY\":1134,\"type\":2}"
                        + "]", 4096, 4096);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planIncludingNecessaryDetours(3363, 1086, 3222, 1157,
                        index, HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        HighwayRoutePlanner.TileStep bridge = null;
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            if (step.getKind() == HighwayTileIndex.Kind.BRIDGE) {
                bridge = step;
                break;
            }
        }
        assertTrue(bridge != null);
        assertEquals(3363, bridge.getTileX());
        assertEquals(1087, bridge.getTileY());
        assertTrue(bridge.isPortal());
    }

    @Test public void tunnelSecondLaneRoutesDirectlyToTargetBehindPlayer() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":4,\"startY\":8,\"endX\":16,"
                        + "\"endY\":8,\"type\":1}]", 32, 32);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planIncludingNecessaryDetours(12, 7, 4, 7, index,
                        HighwayRoutePlanner.NetworkLayer.TUNNEL);

        assertTrue(plan.usesHighway());
        assertEquals(12, plan.getEntryX());
        assertEquals(7, plan.getEntryY());
        assertEquals(4, plan.getExitX());
        assertEquals(7, plan.getExitY());
    }

    @Test public void roadCrossingSplitsBothPublishedSegments() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":8,\"endX\":16,\"endY\":8,\"type\":2},"
                        + "{\"startX\":8,\"startY\":0,\"endX\":8,\"endY\":16,\"type\":2}"
                        + "]", 32, 32);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 8, 8, 16, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        assertEquals(8, plan.getExitX());
        assertEquals(16, plan.getExitY());
        assertTrue(contains(plan, 8, 8, HighwayTileIndex.Kind.ROAD));
    }

    @Test public void tJunctionSplitsTrunkAtBranchEndpoint() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":8,\"endX\":16,\"endY\":8,\"type\":2},"
                        + "{\"startX\":8,\"startY\":8,\"endX\":8,\"endY\":16,\"type\":2}"
                        + "]", 32, 32);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 8, 8, 16, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        assertEquals(8, plan.getExitX());
        assertEquals(16, plan.getExitY());
        assertTrue(contains(plan, 8, 8, HighwayTileIndex.Kind.ROAD));
    }

    @Test public void searchesAllEndpointsInsteadOfOnlyNearbyDeadEnds() {
        StringBuilder source = new StringBuilder("[");
        for (int y = 1; y <= 24; y++) {
            if (y > 1) source.append(',');
            source.append("{\"startX\":0,\"startY\":").append(y)
                    .append(",\"endX\":1,\"endY\":").append(y)
                    .append(",\"type\":2}");
        }
        source.append(",{\"startX\":30,\"startY\":0,"
                + "\"endX\":100,\"endY\":0,\"type\":2}]");
        HighwayTileIndex index = HighwayTileIndex.parse(source.toString(),
                128, 128);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 0, 100, 0, index);

        assertTrue(plan.usesHighway());
        assertEquals(30, plan.getEntryX());
        assertEquals(100, plan.getExitX());
    }

    @Test public void entersAndLeavesLongRoadAtProjectedMidSegmentTiles() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":0,\"endX\":100,"
                        + "\"endY\":0,\"type\":2}]", 128, 128);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                50, 5, 90, 5, index);

        assertTrue(plan.usesHighway());
        assertEquals(50, plan.getEntryX());
        assertEquals(0, plan.getEntryY());
        assertEquals(90, plan.getExitX());
        assertEquals(0, plan.getExitY());
    }

    @Test public void surfaceNetworkIgnoresOverlappingTunnel() {
        HighwayTileIndex index = crossingRoadAndTunnel();

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 4, 8, 4, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            assertEquals(HighwayTileIndex.Kind.ROAD, step.getKind());
        }
    }

    @Test public void tunnelNetworkIgnoresOverlappingSurfaceRoad() {
        HighwayTileIndex index = crossingRoadAndTunnel();

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                4, 0, 4, 8, index,
                HighwayRoutePlanner.NetworkLayer.TUNNEL);

        assertTrue(plan.usesHighway());
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            assertEquals(HighwayTileIndex.Kind.TUNNEL, step.getKind());
        }
    }

    @Test public void surfaceNetworkDoesNotUseTunnelOnlyShortcut() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":0,\"startY\":0,\"endX\":20,"
                        + "\"endY\":0,\"type\":1}]", 64, 64);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 0, 20, 0, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertFalse(plan.usesHighway());
    }

    @Test public void adjacentRoadStaysConnectedWhenBothEndsAlsoHaveTunnel() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":4,\"endX\":4,\"endY\":4,\"type\":2},"
                        + "{\"startX\":5,\"startY\":4,\"endX\":8,\"endY\":4,\"type\":2},"
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":4,\"type\":1},"
                        + "{\"startX\":5,\"startY\":4,\"endX\":5,\"endY\":8,\"type\":1}"
                        + "]", 16, 16);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner().plan(
                0, 4, 8, 4, index,
                HighwayRoutePlanner.NetworkLayer.SURFACE);

        assertTrue(plan.usesHighway());
        assertTrue(contains(plan, 4, 4, HighwayTileIndex.Kind.ROAD));
        assertTrue(contains(plan, 5, 4, HighwayTileIndex.Kind.ROAD));
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            assertEquals(HighwayTileIndex.Kind.ROAD, step.getKind());
        }
    }

    @Test public void occupiedTunnelRoutesFromCurrentTileTowardAnExit() {
        HighwayTileIndex index = crossingRoadAndTunnel();

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planIncludingNecessaryDetours(4, 4, 12, 8, index,
                        HighwayRoutePlanner.NetworkLayer.TUNNEL);

        assertTrue(plan.usesHighway());
        assertEquals(4, plan.getHighwaySteps().get(0).getTileX());
        assertEquals(4, plan.getHighwaySteps().get(0).getTileY());
        HighwayRoutePlanner.TileStep last = plan.getHighwaySteps().get(
                plan.getHighwaySteps().size() - 1);
        assertEquals(4, last.getTileX());
        assertEquals(8, last.getTileY());
        assertEquals(HighwayTileIndex.Kind.TUNNEL, last.getKind());
        assertTrue(last.isPortal());
    }

    @Test public void undergroundTargetSelectsRoadConnectedTunnelEntrance() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":0,\"endX\":4,\"endY\":0,\"type\":2},"
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":8,\"type\":1}"
                        + "]", 16, 16);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planTunnelToSurfacePortal(4, 8, 0, 0, index);

        assertTrue(plan.usesHighway());
        HighwayRoutePlanner.TileStep last = plan.getHighwaySteps().get(
                plan.getHighwaySteps().size() - 1);
        assertEquals(4, last.getTileX());
        assertEquals(0, last.getTileY());
        assertEquals(HighwayTileIndex.Kind.TUNNEL, last.getKind());
        assertTrue(last.isPortal());
    }

    @Test public void undergroundTargetRejectsTunnelWithoutSurfaceEntrance() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":4,\"startY\":0,\"endX\":4,"
                        + "\"endY\":8,\"type\":1}]", 16, 16);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planTunnelToSurfacePortal(4, 8, 0, 0, index);

        assertFalse(plan.usesHighway());
    }

    @Test public void undergroundJunctionChoosesEntranceNearSurfacePlayer() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3056,\"startY\":1003,\"endX\":3102,\"endY\":1003,\"type\":1},"
                        + "{\"startX\":3114,\"startY\":1003,\"endX\":3102,\"endY\":1003,\"type\":1},"
                        + "{\"startX\":3102,\"startY\":1003,\"endX\":3102,\"endY\":986,\"type\":1},"
                        + "{\"startX\":3102,\"startY\":985,\"endX\":3102,\"endY\":957,\"type\":2}"
                        + "]", 4096, 4096);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planTunnelToSurfacePortal(3102, 1003, 3101, 982, index);

        assertTrue(plan.usesHighway());
        HighwayRoutePlanner.TileStep last = plan.getHighwaySteps().get(
                plan.getHighwaySteps().size() - 1);
        assertEquals(3102, last.getTileX());
        assertEquals(986, last.getTileY());
        assertTrue(last.isPortal());
    }

    @Test public void surfaceRouteCanUseTunnelOnlyThroughItsEntrances() {
        HighwayTileIndex index = shortcutTunnelWithSurfaceDetour();

        HighwayRoutePlanner planner = new HighwayRoutePlanner();
        HighwayRoutePlanner.Plan plan = planner.planAcrossLayers(
                0, 4, false, 20, 4, false, index, true);

        assertTrue(plan.usesHighway());
        assertTrue(contains(plan, 4, 4, HighwayTileIndex.Kind.TUNNEL));
        assertTrue(contains(plan, 16, 4, HighwayTileIndex.Kind.TUNNEL));
        HighwayRoutePlanner.Plan surface = planner.leadingSurfaceStage(plan);
        HighwayRoutePlanner.TileStep last = surface.getHighwaySteps().get(
                surface.getHighwaySteps().size() - 1);
        assertEquals(3, last.getTileX());
        assertEquals(4, last.getTileY());
        assertEquals(HighwayTileIndex.Kind.ROAD, last.getKind());
    }

    @Test public void occupiedTunnelUsesExitWithBestSurfaceContinuation() {
        HighwayTileIndex index = shortcutTunnelWithSurfaceDetour();

        HighwayRoutePlanner planner = new HighwayRoutePlanner();
        HighwayRoutePlanner.Plan complete = planner.planAcrossLayers(
                10, 4, true, 0, 4, false, index, false);
        HighwayRoutePlanner.Plan tunnel = planner.leadingTunnelStage(complete);

        assertTrue(tunnel.usesHighway());
        HighwayRoutePlanner.TileStep last = tunnel.getHighwaySteps().get(
                tunnel.getHighwaySteps().size() - 1);
        assertEquals(4, last.getTileX());
        assertEquals(4, last.getTileY());
        assertEquals(HighwayTileIndex.Kind.TUNNEL, last.getKind());
        assertTrue(last.isPortal());
    }

    @Test public void surfaceCrossingDoesNotConnectToTunnelMidSpan() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":5,\"endX\":10,\"endY\":5,\"type\":2},"
                        + "{\"startX\":5,\"startY\":0,\"endX\":5,\"endY\":10,\"type\":1}"
                        + "]", 32, 32);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planAcrossLayers(0, 5, false, 10, 5, false, index, false);

        assertTrue(plan.usesHighway());
        assertFalse(HighwayRoutePlanner.containsKind(plan,
                HighwayTileIndex.Kind.TUNNEL));
    }

    @Test public void internalTunnelJoinUnderRoadIsNotAnEntrance() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":5,\"endX\":10,\"endY\":5,\"type\":2},"
                        + "{\"startX\":5,\"startY\":0,\"endX\":5,\"endY\":5,\"type\":1},"
                        + "{\"startX\":5,\"startY\":5,\"endX\":5,\"endY\":10,\"type\":1}"
                        + "]", 32, 32);

        HighwayRoutePlanner.Plan plan = new HighwayRoutePlanner()
                .planAcrossLayers(0, 5, false, 10, 5, false, index, false);

        assertTrue(plan.usesHighway());
        assertFalse(HighwayRoutePlanner.containsKind(plan,
                HighwayTileIndex.Kind.TUNNEL));
    }

    @Test public void sklotopolisHomeRouteSelectsWestTunnelShortcut() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3034,\"startY\":915,\"endX\":3103,\"endY\":915,\"type\":2},"
                        + "{\"startX\":3137,\"startY\":915,\"endX\":3103,\"endY\":915,\"type\":2},"
                        + "{\"startX\":3102,\"startY\":957,\"endX\":3072,\"endY\":957,\"type\":2},"
                        + "{\"startX\":3137,\"startY\":957,\"endX\":3102,\"endY\":957,\"type\":2},"
                        + "{\"startX\":3102,\"startY\":985,\"endX\":3102,\"endY\":957,\"type\":2},"
                        + "{\"startX\":3034,\"startY\":1003,\"endX\":3034,\"endY\":915,\"type\":2},"
                        + "{\"startX\":3034,\"startY\":1003,\"endX\":3055,\"endY\":1003,\"type\":2},"
                        + "{\"startX\":3056,\"startY\":1003,\"endX\":3102,\"endY\":1003,\"type\":1},"
                        + "{\"startX\":3102,\"startY\":1003,\"endX\":3102,\"endY\":986,\"type\":1},"
                        + "{\"startX\":3114,\"startY\":1003,\"endX\":3102,\"endY\":1003,\"type\":1},"
                        + "{\"startX\":3137,\"startY\":1003,\"endX\":3115,\"endY\":1003,\"type\":2},"
                        + "{\"startX\":3137,\"startY\":1003,\"endX\":3137,\"endY\":915,\"type\":2}"
                        + "]", 4096, 4096);

        HighwayRoutePlanner planner = new HighwayRoutePlanner();
        HighwayRoutePlanner.Plan complete = planner.planAcrossLayers(
                3114, 1002, false, 3019, 921, false, index, true);

        assertTrue(HighwayRoutePlanner.containsKind(complete,
                HighwayTileIndex.Kind.TUNNEL));
        HighwayRoutePlanner.Plan surface = planner.leadingSurfaceStage(
                complete);
        HighwayRoutePlanner.TileStep approach = surface.getHighwaySteps().get(
                surface.getHighwaySteps().size() - 1);
        assertEquals(3115, approach.getTileX());
        assertEquals(1003, approach.getTileY());
        assertTrue(contains(complete, 3114, 1003,
                HighwayTileIndex.Kind.TUNNEL));
        assertTrue(contains(complete, 3056, 1003,
                HighwayTileIndex.Kind.TUNNEL));
    }

    private static HighwayTileIndex shortcutTunnelWithSurfaceDetour() {
        return HighwayTileIndex.parse(
                "["
                        + "{\"startX\":0,\"startY\":4,\"endX\":3,\"endY\":4,\"type\":2},"
                        + "{\"startX\":4,\"startY\":4,\"endX\":16,\"endY\":4,\"type\":1},"
                        + "{\"startX\":17,\"startY\":4,\"endX\":20,\"endY\":4,\"type\":2},"
                        + "{\"startX\":3,\"startY\":4,\"endX\":3,\"endY\":12,\"type\":2},"
                        + "{\"startX\":3,\"startY\":12,\"endX\":17,\"endY\":12,\"type\":2},"
                        + "{\"startX\":17,\"startY\":12,\"endX\":17,\"endY\":4,\"type\":2}"
                        + "]", 32, 32);
    }

    private static HighwayTileIndex crossingRoadAndTunnel() {
        return HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":8,\"type\":1},"
                        + "{\"startX\":0,\"startY\":4,\"endX\":8,\"endY\":4,\"type\":2}"
                        + "]", 16, 16);
    }

    private static boolean contains(HighwayRoutePlanner.Plan plan, int x, int y,
                                    HighwayTileIndex.Kind kind) {
        for (HighwayRoutePlanner.TileStep step : plan.getHighwaySteps()) {
            if (step.getTileX() == x && step.getTileY() == y
                    && step.getKind() == kind) return true;
        }
        return false;
    }
}
