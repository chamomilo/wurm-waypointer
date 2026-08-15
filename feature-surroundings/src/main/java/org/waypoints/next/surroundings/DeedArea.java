package org.waypoints.next.surroundings;

/** Inclusive deed bounds in Wurm tile coordinates; perimeter is intentionally excluded. */
public final class DeedArea {
    private final int minimumX;
    private final int maximumX;
    private final int minimumY;
    private final int maximumY;

    public DeedArea(int minimumX, int maximumX, int minimumY, int maximumY) {
        if (minimumX > maximumX || minimumY > maximumY) {
            throw new IllegalArgumentException("deed bounds are inverted");
        }
        this.minimumX = minimumX;
        this.maximumX = maximumX;
        this.minimumY = minimumY;
        this.maximumY = maximumY;
    }

    public boolean contains(int tileX, int tileY) {
        return tileX >= minimumX && tileX <= maximumX
                && tileY >= minimumY && tileY <= maximumY;
    }
}
