package org.waypoints.next.map;

/** Double-precision point used by viewport transforms. */
public final class MapPoint {
    private final double x;
    private final double y;

    public MapPoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double getX() { return x; }
    public double getY() { return y; }
}
