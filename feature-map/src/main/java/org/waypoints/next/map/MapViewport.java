package org.waypoints.next.map;

/**
 * Mutable, renderer-independent viewport. Map coordinates increase east and
 * south exactly like the published Sklotopolis PNGs.
 */
public final class MapViewport {
    private static final double MAXIMUM_PIXELS_PER_TILE = 16.0d;

    private final int mapWidth;
    private final int mapHeight;
    private int viewportWidth;
    private int viewportHeight;
    private double centerX;
    private double centerY;
    private double pixelsPerTile;

    public MapViewport(int mapWidth, int mapHeight, int viewportWidth,
                       int viewportHeight, double initialCenterX,
                       double initialCenterY) {
        if (mapWidth < 1 || mapHeight < 1) throw new IllegalArgumentException(
                "map bounds must be positive");
        this.mapWidth = mapWidth;
        this.mapHeight = mapHeight;
        resize(viewportWidth, viewportHeight);
        this.pixelsPerTile = fitScale();
        centerOn(initialCenterX, initialCenterY);
    }

    public void resize(int width, int height) {
        if (width < 1 || height < 1) throw new IllegalArgumentException(
                "viewport bounds must be positive");
        double oldMinimum = viewportWidth < 1 ? 0.0d : fitScale();
        viewportWidth = width;
        viewportHeight = height;
        double minimum = fitScale();
        if (pixelsPerTile <= 0.0d || pixelsPerTile <= oldMinimum * 1.000001d) {
            pixelsPerTile = minimum;
        } else if (pixelsPerTile < minimum) {
            pixelsPerTile = minimum;
        }
        clampCenter();
    }

    public void centerOn(double tileX, double tileY) {
        centerX = finite(tileX) ? tileX : mapWidth / 2.0d;
        centerY = finite(tileY) ? tileY : mapHeight / 2.0d;
        clampCenter();
    }

    /** Centers a target and zooms in to at least the requested detail level. */
    public void focusOn(double tileX, double tileY,
                        double minimumPixelsPerTile) {
        if (!finite(minimumPixelsPerTile) || minimumPixelsPerTile <= 0.0d) {
            throw new IllegalArgumentException(
                    "minimum pixels per tile must be positive");
        }
        pixelsPerTile = clamp(Math.max(pixelsPerTile, minimumPixelsPerTile),
                fitScale(), Math.max(fitScale(), MAXIMUM_PIXELS_PER_TILE));
        centerX = finite(tileX) ? tileX : mapWidth / 2.0d;
        centerY = finite(tileY) ? tileY : mapHeight / 2.0d;
        clampCenter();
    }

    /** Positive wheel steps zoom in; the tile under the cursor stays fixed. */
    public void zoomAt(double screenX, double screenY, double wheelSteps) {
        if (!finite(wheelSteps) || wheelSteps == 0.0d) return;
        MapPoint anchor = screenToMap(screenX, screenY);
        double minimum = fitScale();
        double maximum = Math.max(minimum, MAXIMUM_PIXELS_PER_TILE);
        pixelsPerTile = clamp(pixelsPerTile * Math.pow(1.2d, wheelSteps),
                minimum, maximum);
        centerX = anchor.getX()
                - (screenX - viewportWidth / 2.0d) / pixelsPerTile;
        centerY = anchor.getY()
                - (screenY - viewportHeight / 2.0d) / pixelsPerTile;
        clampCenter();
    }

    /** Drag deltas are in screen pixels; the map follows the pointer. */
    public void panByPixels(double deltaX, double deltaY) {
        if (!finite(deltaX) || !finite(deltaY)) return;
        centerX -= deltaX / pixelsPerTile;
        centerY -= deltaY / pixelsPerTile;
        clampCenter();
    }

    public MapPoint screenToMap(double screenX, double screenY) {
        return new MapPoint(centerX
                + (screenX - viewportWidth / 2.0d) / pixelsPerTile,
                centerY + (screenY - viewportHeight / 2.0d) / pixelsPerTile);
    }

    public MapPoint mapToScreen(double tileX, double tileY) {
        return new MapPoint(viewportWidth / 2.0d
                + (tileX - centerX) * pixelsPerTile,
                viewportHeight / 2.0d
                + (tileY - centerY) * pixelsPerTile);
    }

    public boolean containsMapPoint(MapPoint point) {
        return point != null && point.getX() >= 0.0d && point.getY() >= 0.0d
                && point.getX() < mapWidth && point.getY() < mapHeight;
    }

    public double getImageLeft() { return mapToScreen(0.0d, 0.0d).getX(); }
    public double getImageTop() { return mapToScreen(0.0d, 0.0d).getY(); }
    public double getImageWidth() { return mapWidth * pixelsPerTile; }
    public double getImageHeight() { return mapHeight * pixelsPerTile; }
    public double getPixelsPerTile() { return pixelsPerTile; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public int getMapWidth() { return mapWidth; }
    public int getMapHeight() { return mapHeight; }
    public int getViewportWidth() { return viewportWidth; }
    public int getViewportHeight() { return viewportHeight; }

    private double fitScale() {
        return Math.min(viewportWidth / (double) mapWidth,
                viewportHeight / (double) mapHeight);
    }

    private void clampCenter() {
        centerX = clampAxis(centerX, mapWidth,
                viewportWidth / (2.0d * pixelsPerTile));
        centerY = clampAxis(centerY, mapHeight,
                viewportHeight / (2.0d * pixelsPerTile));
    }

    private static double clampAxis(double center, double mapSize,
                                    double visibleHalf) {
        if (visibleHalf * 2.0d >= mapSize) return mapSize / 2.0d;
        return clamp(center, visibleHalf, mapSize - visibleHalf);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
