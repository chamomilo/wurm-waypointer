package com.wurmonline.client.renderer.gui;

import java.awt.image.BufferedImage;
import org.junit.Test;
import org.waypoints.next.map.MapViewport;
import static org.junit.Assert.*;

public final class MiniMapContourImageTest {
    @Test public void denseOneMetreContoursReachEveryPartOfTheLargestMiniMap() {
        int vertices = 161, size = 256;
        float[] heights = new float[vertices * vertices];
        for (int y = 0; y < vertices; y++) for (int x = 0; x < vertices; x++)
            heights[x + y * vertices] = (y % 2) * 3;
        MapViewport viewport = new MapViewport(4096, 4096, size, size,
                1080, 2080, size / 160.0d);
        BufferedImage image = MiniMapContourImage.render(viewport, size,
                1000, 2000, vertices, vertices, heights, 1);
        assertEquals(size, image.getWidth());
        assertEquals(size, image.getHeight());
        for (int y : new int[] {1, 64, 128, 192, 254})
            for (int x : new int[] {1, 64, 128, 192, 254})
                assertTrue("Missing dense contour at " + x + "," + y, alpha(image, x, y) > 0);
    }

    @Test public void backgroundAndUnknownTerrainStayTransparent() {
        MapViewport viewport = cellViewport();
        BufferedImage slope = MiniMapContourImage.render(viewport, 16,
                100, 200, 2, 2, new float[] {0, 4, 0, 4}, 1);
        assertTrue(alpha(slope, 4, 8) > 0);
        assertEquals(0, alpha(slope, 1, 8));
        BufferedImage unknown = MiniMapContourImage.render(viewport, 16,
                100, 200, 2, 2, new float[] {0, Float.NaN, 0, 4}, 1);
        BufferedImage disabled = MiniMapContourImage.render(viewport, 16,
                100, 200, 2, 2, new float[] {0, 4, 0, 4}, 0);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            assertEquals(0, unknown.getRGB(x, y));
            assertEquals(0, disabled.getRGB(x, y));
        }
    }

    @Test public void changingIntervalReplacesTheDenseOverlayWithoutResidualLines() {
        BufferedImage dense = MiniMapContourImage.render(cellViewport(), 16,
                100, 200, 2, 2, new float[] {0, 4, 0, 4}, 1);
        BufferedImage sparse = MiniMapContourImage.render(cellViewport(), 16,
                100, 200, 2, 2, new float[] {0, 4, 0, 4}, 2);
        assertTrue(alpha(dense, 4, 8) > 0);
        assertEquals(0, alpha(sparse, 4, 8));
        assertTrue(alpha(sparse, 8, 8) > 0);
    }

    @Test public void projectionTracksViewportZoomAndCentre() {
        MapViewport viewport = cellViewport();
        viewport.centerOn(100.75, 200.5);
        BufferedImage shifted = MiniMapContourImage.render(viewport, 16,
                100, 200, 2, 2, new float[] {0, 4, 0, 4}, 2);
        assertTrue(alpha(shifted, 4, 8) > 0);
        assertEquals(0, alpha(shifted, 8, 8));
        viewport.zoomAt(8, 8, -1);
        BufferedImage zoomed = MiniMapContourImage.render(viewport, 16,
                100, 200, 2, 2, new float[] {0, 4, 0, 4}, 2);
        assertTrue(alpha(zoomed, 5, 8) > 0);
        assertEquals(0, alpha(zoomed, 8, 8));
    }

    private static MapViewport cellViewport() {
        return new MapViewport(4096, 4096, 16, 16, 100.5, 200.5, 16);
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}
