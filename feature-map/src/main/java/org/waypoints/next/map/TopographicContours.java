package org.waypoints.next.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/** Marching-squares height contours in absolute tile coordinates and metres. */
public final class TopographicContours {
    // Bound work for corrupt height data and exceptionally dense one-metre contours.
    private static final int MAXIMUM_LEVELS_PER_CELL = 128;

    private TopographicContours() { }

    public static List<Segment> build(int originX, int originY, int columns, int rows,
                                      float[] heights, int intervalMetres) {
        if (intervalMetres <= 0) return Collections.emptyList();
        List<Segment> result = new ArrayList<Segment>();
        forEach(originX, originY, columns, rows, heights, intervalMetres, result::add);
        return result;
    }

    /** Visits the entire received grid without a global segment cutoff or retained list. */
    public static void forEach(int originX, int originY, int columns, int rows,
                               float[] heights, int intervalMetres, Consumer<Segment> consumer) {
        if (intervalMetres <= 0) return;
        if (columns < 2 || rows < 2 || heights == null
                || (long) columns * rows != heights.length)
            throw new IllegalArgumentException("Height grid must contain every vertex");
        double[] h = new double[4];
        double[] crossingX = new double[4], crossingY = new double[4];
        for (int y = 0; y < rows - 1; y++) for (int x = 0; x < columns - 1; x++) {
            h[0] = heights[x + y * columns];
            h[1] = heights[x + 1 + y * columns];
            h[2] = heights[x + 1 + (y + 1) * columns];
            h[3] = heights[x + (y + 1) * columns];
            boolean known = true;
            double minimum = Double.POSITIVE_INFINITY, maximum = Double.NEGATIVE_INFINITY;
            for (double height : h) {
                if (Double.isNaN(height) || Double.isInfinite(height)) known = false;
                minimum = Math.min(minimum, height);
                maximum = Math.max(maximum, height);
            }
            if (!known || minimum == maximum) continue;
            double firstLevel = (Math.floor(minimum / intervalMetres) + 1) * intervalMetres;
            for (int step = 0; step < MAXIMUM_LEVELS_PER_CELL; step++) {
                double level = firstLevel + step * (double) intervalMetres;
                if (level > maximum) break;
                int count = 0;
                for (int edge = 0; edge < 4; edge++) {
                    int next = (edge + 1) % 4;
                    if ((h[edge] < level) == (h[next] < level)) continue;
                    double fraction = (level - h[edge]) / (h[next] - h[edge]);
                    double ax = edge == 1 || edge == 2 ? 1 : 0;
                    double ay = edge >= 2 ? 1 : 0;
                    double bx = next == 1 || next == 2 ? 1 : 0;
                    double by = next >= 2 ? 1 : 0;
                    crossingX[count] = originX + x + ax + (bx - ax) * fraction;
                    crossingY[count++] = originY + y + ay + (by - ay) * fraction;
                }
                if (count == 2) add(consumer, crossingX, crossingY, 0, 1, level);
                else if (count == 4) {
                    double determinant = (h[0] - level) * (h[2] - level)
                            - (h[1] - level) * (h[3] - level);
                    if (determinant >= 0) {
                        add(consumer, crossingX, crossingY, 0, 1, level);
                        add(consumer, crossingX, crossingY, 2, 3, level);
                    } else {
                        add(consumer, crossingX, crossingY, 0, 3, level);
                        add(consumer, crossingX, crossingY, 1, 2, level);
                    }
                }
            }
        }
    }

    private static void add(Consumer<Segment> consumer, double[] x, double[] y,
                             int a, int b, double height) {
        if (Math.hypot(x[a] - x[b], y[a] - y[b]) < 0.000001d) return;
        consumer.accept(new Segment(x[a], y[a], x[b], y[b], height));
    }

    public static final class Segment {
        public final double x1, y1, x2, y2, heightMetres;

        private Segment(double x1, double y1, double x2, double y2, double height) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
            this.heightMetres = height;
        }
    }
}
