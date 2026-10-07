package org.waypoints.next.navigation;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/** Receipt coordinates for the native 64x64 cave ring, independent of height. */
public final class CaveTileCoverage {
    // Object keeps the preInit bridge from loading CaveDataBuffer before it is patched.
    private static final Map<Object, Coverage> BUFFERS = new WeakHashMap<Object, Coverage>();

    private CaveTileCoverage() { }

    public static synchronized void clear(Object buffer) {
        BUFFERS.remove(buffer);
    }

    public static synchronized void beforeStrip(Object buffer, int x, int y,
                                                 int width, int height) {
        if (buffer == null || width <= 0 || height <= 0) return;
        Coverage coverage = BUFFERS.get(buffer);
        if (coverage == null) {
            coverage = new Coverage();
            BUFFERS.put(buffer, coverage);
        }
        coverage.moveBounds(x, y, width, height);
        // Slots being overwritten stay unknown until tileStrip finishes successfully.
        coverage.write(x, y, width, height, false);
    }

    public static synchronized void afterStrip(Object buffer, int x, int y,
                                                int width, int height) {
        Coverage coverage = BUFFERS.get(buffer);
        if (coverage != null) coverage.write(x, y, width, height, true);
    }

    public static synchronized boolean isReceived(Object buffer, int x, int y) {
        Coverage coverage = BUFFERS.get(buffer);
        return coverage != null && coverage.contains(x, y);
    }

    public static synchronized boolean hasFloorCorners(Object buffer, int x, int y) {
        Coverage coverage = BUFFERS.get(buffer);
        return coverage != null && coverage.contains(x, y) && coverage.contains(x + 1, y)
                && coverage.contains(x, y + 1) && coverage.contains(x + 1, y + 1);
    }

    private static int offset(int x, int y) { return (x & 63) | ((y & 63) << 6); }
    private static long coordinate(int x, int y) {
        return ((long) x << 32) | (y & 0xffffffffL);
    }

    private static final class Coverage {
        final long[] coordinates = new long[64 * 64];
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

        Coverage() { Arrays.fill(coordinates, Long.MIN_VALUE); }

        void moveBounds(int x, int y, int width, int height) {
            // Match CaveDataBuffer.tileStrip's absolute bounds, including the
            // initially unfilled part of its 64x64 allocation.
            int endX = x + width - 1, endY = y + height - 1;
            if (endX > maxX) { maxX = endX; minX = endX - 63; }
            else if (x < minX) { minX = x; maxX = x + 63; }
            if (endY > maxY) { maxY = endY; minY = endY - 63; }
            else if (y < minY) { minY = y; maxY = y + 63; }
        }

        void write(int x, int y, int width, int height, boolean received) {
            for (int tx = x; tx < x + width; tx++)
                for (int ty = y; ty < y + height; ty++)
                    coordinates[offset(tx, ty)] = received
                            ? coordinate(tx, ty) : Long.MIN_VALUE;
        }

        boolean contains(int x, int y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY
                    && coordinates[offset(x, y)] == coordinate(x, y);
        }
    }
}
