package org.waypoints.next.render;

/** Pure Wurm-bearing and compass-marker geometry. */
public final class CompassMarkerGeometry {
    private CompassMarkerGeometry() {
    }

    public static Position locate(float playerX, float playerY, float playerFacing,
                                  float targetX, float targetY,
                                  int compassX, int compassY,
                                  int compassWidth, int compassHeight) {
        int[] result = new int[3];
        locateInto(playerX, playerY, playerFacing, targetX, targetY,
                compassX, compassY, compassWidth, compassHeight, result);
        return new Position(result[0], result[1], result[2] != 0);
    }

    /** Allocation-free hot-path variant: output is x, y, arrived(0/1). */
    public static void locateInto(float playerX, float playerY, float playerFacing,
                                  float targetX, float targetY,
                                  int compassX, int compassY,
                                  int compassWidth, int compassHeight,
                                  int[] output) {
        if (output == null || output.length < 3) {
            throw new IllegalArgumentException("output must contain at least three integers");
        }
        float centerX = compassX + compassWidth * 0.5f;
        float centerY = compassY + compassHeight * 0.5f;
        float deltaX = targetX - playerX;
        float deltaY = targetY - playerY;
        if (deltaX == 0.0f && deltaY == 0.0f) {
            output[0] = Math.round(centerX);
            output[1] = Math.round(centerY);
            output[2] = 1;
            return;
        }

        // Wurm uses 0=north, 90=east, while world Y increases southward.
        float bearing = normalize((float) Math.toDegrees(Math.atan2(deltaX, -deltaY)));
        float relativeRadians = (float) Math.toRadians(normalizeSigned(bearing - playerFacing));
        float radius = Math.min(compassWidth, compassHeight) * 0.39f;
        int markerX = Math.round(centerX + (float) Math.sin(relativeRadians) * radius);
        int markerY = Math.round(centerY - (float) Math.cos(relativeRadians) * radius);
        output[0] = markerX;
        output[1] = markerY;
        output[2] = 0;
    }

    public static boolean hit(Position marker, int mouseX, int mouseY, int radius) {
        if (marker == null || radius < 0) return false;
        long dx = (long) mouseX - marker.getX();
        long dy = (long) mouseY - marker.getY();
        return dx * dx + dy * dy <= (long) radius * radius;
    }

    static float normalize(float degrees) {
        float result = degrees % 360.0f;
        return result < 0.0f ? result + 360.0f : result;
    }

    static float normalizeSigned(float degrees) {
        float result = normalize(degrees);
        return result > 180.0f ? result - 360.0f : result;
    }

    public static final class Position {
        private final int x;
        private final int y;
        private final boolean arrived;

        private Position(int x, int y, boolean arrived) {
            this.x = x;
            this.y = y;
            this.arrived = arrived;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public boolean isArrived() { return arrived; }
    }
}
