package org.waypoints.next.navigation;

/** Allocation-free eight-connected test route between two Wurm tiles. */
public final class StraightTileRoute {
    private StraightTileRoute() {
    }

    public static int fill(int startX, int startY, int targetX, int targetY,
                           int[] outputX, int[] outputY) {
        if (outputX == null || outputY == null
                || outputX.length == 0 || outputX.length != outputY.length) {
            throw new IllegalArgumentException(
                    "equal non-empty route buffers are required");
        }
        int currentX = startX;
        int currentY = startY;
        int deltaX = Math.abs(targetX - currentX);
        int deltaY = Math.abs(targetY - currentY);
        int stepX = currentX < targetX ? 1 : -1;
        int stepY = currentY < targetY ? 1 : -1;
        int error = deltaX - deltaY;
        int count = 0;
        while (count < outputX.length) {
            outputX[count] = currentX;
            outputY[count] = currentY;
            count++;
            if (currentX == targetX && currentY == targetY) break;
            int doubled = error * 2;
            if (doubled > -deltaY) {
                error -= deltaY;
                currentX += stepX;
            }
            if (doubled < deltaX) {
                error += deltaX;
                currentY += stepY;
            }
        }
        return count;
    }
}
