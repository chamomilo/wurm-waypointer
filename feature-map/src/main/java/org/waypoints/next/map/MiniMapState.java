package org.waypoints.next.map;

/** Mutable session settings for the player-centred mini-map. */
public final class MiniMapState {
    public static final int MINIMUM_VISIBLE_TILES = 20;
    public static final int MAXIMUM_VISIBLE_TILES = 160;
    public static final int DEFAULT_VISIBLE_TILES = 80;

    private int visibleTiles;
    private boolean deedsVisible;
    private boolean roadsVisible;
    private boolean navigationLineVisible;

    public MiniMapState() {
        this(DEFAULT_VISIBLE_TILES, true, true, true);
    }

    public MiniMapState(int visibleTiles, boolean deedsVisible) {
        this(visibleTiles, deedsVisible, true, true);
    }

    public MiniMapState(int visibleTiles, boolean deedsVisible,
                        boolean navigationLineVisible) {
        this(visibleTiles, deedsVisible, true, navigationLineVisible);
    }

    public MiniMapState(int visibleTiles, boolean deedsVisible,
                        boolean roadsVisible,
                        boolean navigationLineVisible) {
        this.visibleTiles = clamp(visibleTiles);
        this.deedsVisible = deedsVisible;
        this.roadsVisible = roadsVisible;
        this.navigationLineVisible = navigationLineVisible;
    }

    public int getVisibleTiles() { return visibleTiles; }

    public boolean areDeedsVisible() { return deedsVisible; }

    public boolean areRoadsVisible() { return roadsVisible; }

    public boolean isNavigationLineVisible() { return navigationLineVisible; }

    public int increaseVisibleTiles() {
        visibleTiles = clamp(visibleTiles + 1);
        return visibleTiles;
    }

    public int decreaseVisibleTiles() {
        visibleTiles = clamp(visibleTiles - 1);
        return visibleTiles;
    }

    public boolean toggleDeeds() {
        deedsVisible = !deedsVisible;
        return deedsVisible;
    }

    public void setDeedsVisible(boolean value) { deedsVisible = value; }

    public boolean toggleRoads() {
        roadsVisible = !roadsVisible;
        return roadsVisible;
    }

    public void setRoadsVisible(boolean value) { roadsVisible = value; }

    public boolean toggleNavigationLine() {
        navigationLineVisible = !navigationLineVisible;
        return navigationLineVisible;
    }

    /** One wheel step zooms in by showing one fewer tile per side. */
    public int zoomIn(int steps) {
        visibleTiles = clamp(visibleTiles - positiveSteps(steps));
        return visibleTiles;
    }

    /** One wheel step zooms out by showing one more tile per side. */
    public int zoomOut(int steps) {
        visibleTiles = clamp(visibleTiles + positiveSteps(steps));
        return visibleTiles;
    }

    public double pixelsPerTile(int viewportPixels) {
        if (viewportPixels < 1) throw new IllegalArgumentException(
                "mini-map viewport must be positive");
        return viewportPixels / (double) visibleTiles;
    }

    private static int clamp(int value) {
        return Math.max(MINIMUM_VISIBLE_TILES,
                Math.min(MAXIMUM_VISIBLE_TILES, value));
    }

    private static int positiveSteps(int value) {
        return Math.max(1, value);
    }
}
