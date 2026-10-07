package org.waypoints.next.map;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TopographicContoursTest {
    @Test public void denseOneMetreContoursVisitTheFullGridPastTheOldSegmentLimit() {
        int vertices = 161;
        float[] heights = new float[vertices * vertices];
        for (int y = 0; y < vertices; y++) for (int x = 0; x < vertices; x++)
            heights[x + y * vertices] = (y % 2) * 3;
        int[] count = {0};
        double[] maximumY = {0};
        TopographicContours.forEach(1000, 2000, vertices, vertices, heights, 1,
                segment -> {
                    count[0]++;
                    maximumY[0] = Math.max(maximumY[0], Math.max(segment.y1, segment.y2));
                });
        assertEquals(76800, count[0]);
        assertEquals(2160 - 1.0d / 3, maximumY[0], 0.000001);
    }

    @Test public void planarSlopeProducesLinesAtRequestedMetreIntervals() {
        List<TopographicContours.Segment> lines = TopographicContours.build(100, 200,
                2, 2, new float[] {0, 10, 0, 10}, 2);
        assertEquals(5, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            TopographicContours.Segment line = lines.get(i);
            assertEquals((i + 1) * 2, line.heightMetres, 0.000001);
            assertEquals(100 + (i + 1) * 0.2, line.x1, 0.000001);
            assertEquals(line.x1, line.x2, 0.000001);
            assertEquals(200, Math.min(line.y1, line.y2), 0.000001);
            assertEquals(201, Math.max(line.y1, line.y2), 0.000001);
        }
        assertEquals(2, TopographicContours.build(100, 200, 2, 2,
                new float[] {0, 10, 0, 10}, 5).size());
    }

    @Test public void zeroFlatAndUnknownTerrainHaveNoContours() {
        assertTrue(TopographicContours.build(0, 0, 2, 2,
                new float[] {0, 10, 0, 10}, 0).isEmpty());
        assertTrue(TopographicContours.build(0, 0, 2, 2,
                new float[] {5, 5, 5, 5}, 1).isEmpty());
        assertTrue(TopographicContours.build(0, 0, 2, 2,
                new float[] {0, Float.NaN, 0, 10}, 1).isEmpty());
    }

    @Test public void negativeCaveFloorsIncludeZeroAtTheCorrectPosition() {
        List<TopographicContours.Segment> lines = TopographicContours.build(0, 0,
                2, 2, new float[] {-3, 3, -3, 3}, 2);
        assertEquals(3, lines.size());
        assertEquals(-2, lines.get(0).heightMetres, 0.000001);
        assertEquals(0, lines.get(1).heightMetres, 0.000001);
        assertEquals(0.5, lines.get(1).x1, 0.000001);
        assertEquals(2, lines.get(2).heightMetres, 0.000001);
    }

    @Test public void saddleUsesSeparateSegmentsAndNoInventedDiagonal() {
        List<TopographicContours.Segment> lines = TopographicContours.build(0, 0,
                2, 2, new float[] {3, -1, -1, 3}, 4);
        // The only level within this cell is zero.
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).heightMetres, 0.000001);
        assertEquals(0, lines.get(0).y1, 0.000001);
        assertEquals(1, lines.get(0).x2, 0.000001);
        assertEquals(1, lines.get(1).y1, 0.000001);
        assertEquals(0, lines.get(1).x2, 0.000001);
    }

    @Test public void neighbouringCellsMeetAtTheSameEdgePoint() {
        List<TopographicContours.Segment> lines = TopographicContours.build(0, 0,
                3, 2, new float[] {0, 0, 0, 10, 10, 10}, 5);
        assertEquals(4, lines.size());
        assertEquals(lines.get(0).x1, lines.get(2).x2, 0.000001);
        assertEquals(lines.get(0).y1, lines.get(2).y2, 0.000001);
    }
}
