package org.waypoints.next.navigation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class HighwayTileIndexTest {
    @Test public void parsesJavascriptWrappedRoadBridgeTunnelAndSkipsCanal() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "var highways; highways =["
                        + "{\"startX\":1,\"startY\":1,\"endX\":3,\"endY\":1,\"type\":\"2\"},"
                        + "{\"startX\":3,\"startY\":1,\"endX\":3,\"endY\":3,\"type\":\"0\"},"
                        + "{\"startX\":5,\"startY\":1,\"endX\":5,\"endY\":3,\"type\":\"1\"},"
                        + "{\"startX\":7,\"startY\":1,\"endX\":7,\"endY\":3,\"type\":\"3\"}];",
                16, 16);

        assertTrue(index.get(2, 1).hasKind(HighwayTileIndex.Kind.ROAD));
        assertTrue(index.get(2, 1).hasKind(HighwayTileIndex.Kind.BRIDGE));
        assertEquals(HighwayTileIndex.Kind.BRIDGE, index.get(3, 2).getKind());
        assertTrue(index.get(3, 3).isPortal());
        assertEquals(HighwayTileIndex.Kind.TUNNEL, index.get(5, 2).getKind());
        assertFalse(index.get(5, 2).isPortal());
        assertEquals(HighwayTileIndex.Kind.NONE, index.get(7, 2).getKind());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOutOfBoundsCoordinates() {
        HighwayTileIndex.parse("[{\"startX\":0,\"startY\":0,"
                + "\"endX\":99,\"endY\":0,\"type\":2}]", 8, 8);
    }

    @Test public void preservesSurfaceRoadWhereTunnelCrossesSameCoordinates() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":8,\"type\":1},"
                        + "{\"startX\":0,\"startY\":4,\"endX\":8,\"endY\":4,\"type\":2}"
                        + "]", 16, 16);

        HighwayTileIndex.Tile crossing = index.get(4, 4);
        assertTrue(crossing.hasKind(HighwayTileIndex.Kind.ROAD));
        assertTrue(crossing.hasKind(HighwayTileIndex.Kind.TUNNEL));
        assertFalse(crossing.isPortal(HighwayTileIndex.Kind.TUNNEL));
        assertFalse(crossing.isPortal(HighwayTileIndex.Kind.ROAD));
        assertEquals(HighwayTileIndex.Kind.TUNNEL, crossing.getKind());
    }

    @Test public void touchingTunnelChunksHaveOnlyTerminalPortals() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":4,\"type\":1},"
                        + "{\"startX\":4,\"startY\":4,\"endX\":4,\"endY\":8,\"type\":1}"
                        + "]", 16, 16);

        assertTrue(index.get(4, 0).isPortal(HighwayTileIndex.Kind.TUNNEL));
        assertFalse(index.get(4, 4).isPortal(HighwayTileIndex.Kind.TUNNEL));
        assertTrue(index.get(4, 8).isPortal(HighwayTileIndex.Kind.TUNNEL));
    }

    @Test public void adjacentTunnelChunksHaveOnlyTerminalPortals() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":0,\"endX\":4,\"endY\":3,\"type\":1},"
                        + "{\"startX\":4,\"startY\":4,\"endX\":4,\"endY\":8,\"type\":1}"
                        + "]", 16, 16);

        assertTrue(index.get(4, 0).isPortal(HighwayTileIndex.Kind.TUNNEL));
        assertFalse(index.get(4, 3).isPortal(HighwayTileIndex.Kind.TUNNEL));
        assertFalse(index.get(4, 4).isPortal(HighwayTileIndex.Kind.TUNNEL));
        assertTrue(index.get(4, 8).isPortal(HighwayTileIndex.Kind.TUNNEL));
    }

    @Test public void coincidentBridgeSpansFormOneBridgeWithTerminalRamps() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":5,\"startY\":4,\"endX\":10,\"endY\":4,\"type\":0},"
                        + "{\"startX\":10,\"startY\":4,\"endX\":15,\"endY\":4,\"type\":0}"
                        + "]", 32, 16);

        assertTrue(index.get(5, 4).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertFalse(index.get(10, 4).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertTrue(index.get(15, 4).isPortal(HighwayTileIndex.Kind.BRIDGE));
    }

    @Test public void turningBridgeJunctionIsPortalButStraightJoinIsNot() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":5,\"startY\":8,\"endX\":10,\"endY\":8,\"type\":0},"
                        + "{\"startX\":10,\"startY\":8,\"endX\":10,\"endY\":14,\"type\":0}"
                        + "]", 32, 32);

        assertTrue(index.get(10, 8).isPortal(
                HighwayTileIndex.Kind.BRIDGE));
        assertTrue(index.get(9, 8).isPortal(
                HighwayTileIndex.Kind.BRIDGE));
    }

    @Test public void horizontalPublishedBoundaryMarksBothAdjacentTiles() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":4,\"startY\":8,\"endX\":12,"
                        + "\"endY\":8,\"type\":2}]", 32, 32);

        assertTrue(index.get(8, 8).hasKind(HighwayTileIndex.Kind.ROAD));
        assertTrue(index.get(8, 7).hasKind(HighwayTileIndex.Kind.ROAD));
        assertFalse(index.get(8, 6).hasKind(HighwayTileIndex.Kind.ROAD));
        assertFalse(index.get(8, 9).hasKind(HighwayTileIndex.Kind.ROAD));
    }

    @Test public void verticalPublishedBoundaryMarksBothAdjacentTiles() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":8,\"startY\":4,\"endX\":8,"
                        + "\"endY\":12,\"type\":2}]", 32, 32);

        assertTrue(index.get(8, 8).hasKind(HighwayTileIndex.Kind.ROAD));
        assertTrue(index.get(7, 8).hasKind(HighwayTileIndex.Kind.ROAD));
        assertFalse(index.get(6, 8).hasKind(HighwayTileIndex.Kind.ROAD));
        assertFalse(index.get(9, 8).hasKind(HighwayTileIndex.Kind.ROAD));
    }

    @Test public void bothBridgeDeckTilesShareTerminalPortalOnly() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":5,\"startY\":8,\"endX\":10,\"endY\":8,\"type\":0},"
                        + "{\"startX\":10,\"startY\":8,\"endX\":15,\"endY\":8,\"type\":0}"
                        + "]", 32, 32);

        assertTrue(index.get(5, 8).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertTrue(index.get(5, 7).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertFalse(index.get(10, 8).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertFalse(index.get(10, 7).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertTrue(index.get(15, 8).isPortal(HighwayTileIndex.Kind.BRIDGE));
        assertTrue(index.get(15, 7).isPortal(HighwayTileIndex.Kind.BRIDGE));
    }

    @Test public void sklotopolisBridgeBoundaryIncludesObservedSideTiles() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":3391,\"startY\":1087,\"endX\":3363,\"endY\":1087,\"type\":0},"
                        + "{\"startX\":3363,\"startY\":1087,\"endX\":3363,\"endY\":1120,\"type\":0}"
                        + "]", 4096, 4096);

        assertTrue(index.get(3369, 1086).hasKind(
                HighwayTileIndex.Kind.BRIDGE));
        assertTrue(index.get(3362, 1107).hasKind(
                HighwayTileIndex.Kind.BRIDGE));
    }

    @Test public void rightAngleTurnContainsFullTwoByTwoHighwaySquare() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":8,\"endX\":10,\"endY\":8,\"type\":2},"
                        + "{\"startX\":10,\"startY\":8,\"endX\":10,\"endY\":14,\"type\":2}"
                        + "]", 32, 32);

        for (int x = 9; x <= 10; x++) {
            for (int y = 7; y <= 8; y++) {
                assertTrue(index.get(x, y).hasKind(
                        HighwayTileIndex.Kind.ROAD));
            }
        }
    }

    @Test public void diagonalBoundaryHasTwoSidesAndFilledTurns() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "[{\"startX\":4,\"startY\":4,\"endX\":8,"
                        + "\"endY\":8,\"type\":2}]", 32, 32);

        for (int x = 4; x <= 7; x++) {
            for (int y = x; y <= x + 1; y++) {
                assertTrue(index.get(x, y).hasKind(
                        HighwayTileIndex.Kind.ROAD));
            }
        }
    }

    @Test public void surfaceAndBridgeCrossingKeepsBothLayerMasks() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":8,\"endX\":12,\"endY\":8,\"type\":2},"
                        + "{\"startX\":8,\"startY\":4,\"endX\":8,\"endY\":12,\"type\":0}"
                        + "]", 32, 32);

        for (int x = 7; x <= 8; x++) {
            for (int y = 7; y <= 8; y++) {
                assertTrue(index.get(x, y).hasKind(
                        HighwayTileIndex.Kind.ROAD));
                assertTrue(index.get(x, y).hasKind(
                        HighwayTileIndex.Kind.BRIDGE));
            }
        }
    }

    @Test public void adjacentEndpointTurnAlsoFillsTwoByTwoJunction() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":8,\"endX\":8,\"endY\":8,\"type\":2},"
                        + "{\"startX\":9,\"startY\":9,\"endX\":9,\"endY\":14,\"type\":2}"
                        + "]", 32, 32);

        for (int x = 8; x <= 9; x++) {
            for (int y = 8; y <= 9; y++) {
                assertTrue(index.get(x, y).hasKind(
                        HighwayTileIndex.Kind.ROAD));
            }
        }
    }

    @Test public void tIntersectionFootprintIsFilledTwoByTwo() {
        HighwayTileIndex index = HighwayTileIndex.parse(
                "["
                        + "{\"startX\":4,\"startY\":8,\"endX\":12,\"endY\":8,\"type\":2},"
                        + "{\"startX\":8,\"startY\":8,\"endX\":8,\"endY\":14,\"type\":2}"
                        + "]", 32, 32);

        for (int x = 7; x <= 8; x++) {
            for (int y = 7; y <= 8; y++) {
                assertTrue(index.get(x, y).hasKind(
                        HighwayTileIndex.Kind.ROAD));
            }
        }
    }
}
