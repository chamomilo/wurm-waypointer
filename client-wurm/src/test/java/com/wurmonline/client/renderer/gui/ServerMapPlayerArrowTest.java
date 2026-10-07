package com.wurmonline.client.renderer.gui;

import org.junit.Test;
import org.waypoints.next.map.MapPoint;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.MiniMapState;
import static org.junit.Assert.*;

public final class ServerMapPlayerArrowTest {
    @Test public void tipFollowsWurmHeadingOnNorthUpMaps() {
        float[][] expected = {{100, 193.6f}, {106.4f, 200}, {100, 206.4f}, {93.6f, 200}};
        for (int direction = 0; direction < 4; direction++) {
            float[] arrow = PlayerArrowGeometry.points(100, 200, direction * 90);
            assertEquals(expected[direction][0], arrow[0], 0.0001f);
            assertEquals(expected[direction][1], arrow[1], 0.0001f);
        }
    }

    @Test public void rotatingArrowAndOutlineKeepMaximumZoomBoundsAtEveryHeading() {
        float size = PlayerArrowGeometry.REFERENCE_TILE_SIZE_PIXELS;
        float outlineHalfWidth = PlayerArrowGeometry.OUTLINE_WIDTH_PIXELS * 0.5f;
        for (int heading = 0; heading < 360; heading++) {
            float[] arrow = PlayerArrowGeometry.points(0, 0, heading);
            for (float coordinate : arrow) assertTrue(Math.abs(coordinate) + outlineHalfWidth <= size * 0.5f);
        }
    }

    @Test public void fullMapAndBothMiniMapLayersKeepMaximumZoomArrowSize() {
        MapViewport fullMap = new MapViewport(4096, 4096, 920, 620, 2000, 2000);
        fullMap.zoomAt(460, 310, 100);
        assertEquals(fullMap.getPixelsPerTile(), PlayerArrowGeometry.REFERENCE_TILE_SIZE_PIXELS, 0.0001);
        assertScreenDimensions(fullMap);
        fullMap.zoomAt(460, 310, -200);
        assertScreenDimensions(fullMap);

        for (boolean cave : new boolean[] {false, true}) {
            MiniMapState settings = new MiniMapState();
            settings.selectMapView(cave);
            settings.zoomIn(1000);
            assertMiniMapDimensions(settings);
            settings.zoomOut(1000);
            assertMiniMapDimensions(settings);
        }
    }

    private static void assertMiniMapDimensions(MiniMapState settings) {
        MapViewport viewport = new MapViewport(4096, 4096, 256, 256, 2000, 2000,
                settings.pixelsPerTile(256));
        assertScreenDimensions(viewport);
    }

    private static void assertScreenDimensions(MapViewport viewport) {
        // Use an off-centre tile so zoom also changes the marker's screen position.
        MapPoint position = viewport.mapToScreen(2002.5, 2001.5);
        float x = (float) position.getX(), y = (float) position.getY();
        float[] arrow = PlayerArrowGeometry.points(x, y, 0);
        assertEquals(12.8f, arrow[7] - arrow[1], 0.0001f);
        assertEquals(9.6f, arrow[4] - arrow[2], 0.0001f);
        assertEquals(2.56f, PlayerArrowGeometry.OUTLINE_WIDTH_PIXELS, 0.0001f);
        assertEquals(1.36f, PlayerArrowGeometry.STROKE_WIDTH_PIXELS, 0.0001f);
    }
}
