package org.waypoints.next.map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class MapViewportTest {
    private static final double EPSILON = 0.000001d;

    @Test public void fitPreservesSquareMapAspectInsideWideWurmWindow() {
        MapViewport viewport = new MapViewport(2048, 2048,
                920, 620, 1024.0d, 1024.0d);
        assertEquals(620.0d, viewport.getImageWidth(), EPSILON);
        assertEquals(620.0d, viewport.getImageHeight(), EPSILON);
        assertEquals(150.0d, viewport.getImageLeft(), EPSILON);
        assertEquals(0.0d, viewport.getImageTop(), EPSILON);
    }

    @Test public void screenAndMapTransformsRoundTrip() {
        MapViewport viewport = new MapViewport(4096, 4096,
                920, 620, 1234.5d, 2345.5d);
        viewport.zoomAt(460.0d, 310.0d, 7.0d);
        MapPoint screen = viewport.mapToScreen(1300.25d, 2200.75d);
        MapPoint restored = viewport.screenToMap(screen.getX(), screen.getY());
        assertEquals(1300.25d, restored.getX(), EPSILON);
        assertEquals(2200.75d, restored.getY(), EPSILON);
    }

    @Test public void cursorAnchoredZoomKeepsSelectedTileStationary() {
        MapViewport viewport = new MapViewport(2048, 2048,
                920, 620, 1000.0d, 1000.0d);
        viewport.zoomAt(460.0d, 310.0d, 4.0d);
        MapPoint before = viewport.screenToMap(700.0d, 200.0d);
        viewport.zoomAt(700.0d, 200.0d, 3.0d);
        MapPoint after = viewport.screenToMap(700.0d, 200.0d);
        assertEquals(before.getX(), after.getX(), EPSILON);
        assertEquals(before.getY(), after.getY(), EPSILON);
    }

    @Test public void panAndExtremeZoomStayInsideMap() {
        MapViewport viewport = new MapViewport(2048, 2048,
                920, 620, 10.0d, 10.0d);
        viewport.zoomAt(460.0d, 310.0d, 50.0d);
        viewport.panByPixels(1_000_000.0d, 1_000_000.0d);
        assertTrue(viewport.getCenterX() >= 0.0d);
        assertTrue(viewport.getCenterY() >= 0.0d);
        assertTrue(viewport.getCenterX() <= viewport.getMapWidth());
        assertTrue(viewport.getCenterY() <= viewport.getMapHeight());
        MapPoint mapOrigin = viewport.mapToScreen(0.0d, 0.0d);
        assertEquals(viewport.getViewportWidth() / 2.0d,
                mapOrigin.getX(), EPSILON);
        assertEquals(viewport.getViewportHeight() / 2.0d,
                mapOrigin.getY(), EPSILON);
    }

    @Test public void focusedOpeningViewKeepsPlayersAtVerticalEdgesVisible() {
        MapViewport north = new MapViewport(4096, 4096,
                920, 620, 2048.5d, 0.5d, 0.42d);
        MapPoint northPlayer = north.mapToScreen(2048.5d, 0.5d);
        assertEquals(0.42d, north.getPixelsPerTile(), EPSILON);
        assertEquals(460.0d, northPlayer.getX(), EPSILON);
        assertEquals(310.0d, northPlayer.getY(), EPSILON);

        MapViewport south = new MapViewport(4096, 4096,
                920, 620, 2048.5d, 4095.5d, 0.42d);
        MapPoint southPlayer = south.mapToScreen(2048.5d, 4095.5d);
        assertEquals(460.0d, southPlayer.getX(), EPSILON);
        assertEquals(310.0d, southPlayer.getY(), EPSILON);
    }

    @Test public void deedFocusCentersAndZoomsWithoutZoomingOut() {
        MapViewport viewport = new MapViewport(4096, 4096,
                920, 620, 1000.0d, 1000.0d);
        viewport.focusOn(3019.5d, 912.5d, 1.5d);
        assertEquals(3019.5d, viewport.getCenterX(), EPSILON);
        assertEquals(912.5d, viewport.getCenterY(), EPSILON);
        assertEquals(1.5d, viewport.getPixelsPerTile(), EPSILON);
        viewport.zoomAt(460.0d, 310.0d, 5.0d);
        double zoomed = viewport.getPixelsPerTile();
        viewport.focusOn(2000.5d, 2000.5d, 1.5d);
        assertEquals(zoomed, viewport.getPixelsPerTile(), EPSILON);
    }
}
