package org.waypoints.next.source;

/** Exclusive tile bounds for one configured map. */
public final class MapBounds {
    private final int width;
    private final int height;

    public MapBounds(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("map bounds must be positive");
        }
        this.width = width;
        this.height = height;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void requireContains(double tileX, double tileY) {
        if (tileX < 0.0d || tileY < 0.0d || tileX >= width || tileY >= height) {
            throw new IllegalArgumentException("coordinates are outside map bounds 0.."
                    + (width - 1) + " x 0.." + (height - 1));
        }
    }
}
