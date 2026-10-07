package org.waypoints.next.map;

/** Mutable session settings for the player-centred mini-map. */
public final class MiniMapState {
    public static final int MINIMUM_VISIBLE_TILES = 20;
    public static final int MAXIMUM_VISIBLE_TILES = 160;
    public static final int DEFAULT_VISIBLE_TILES = 80;
    public static final int CAVE_MINIMUM_VISIBLE_TILES = 17;
    public static final int CAVE_MAXIMUM_VISIBLE_TILES = 48;
    public static final int MAXIMUM_TOPOGRAPHIC_INTERVAL_METRES = 99;

    private int visibleTiles;
    private boolean deedsVisible;
    private boolean roadsVisible;
    private boolean navigationLineVisible;
    private boolean caveView;
    private int caveVisibleTiles = CAVE_MINIMUM_VISIBLE_TILES;
    private Boolean observedCaveLayer;
    private int topographicIntervalMetres;
    private final MapZoomFactor zoomFactor = new MapZoomFactor();

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

    public int getVisibleTiles() { return caveView ? caveVisibleTiles : visibleTiles; }

    public boolean areTileBordersVisible() {
        return getVisibleTiles() == (caveView
                ? CAVE_MINIMUM_VISIBLE_TILES : MINIMUM_VISIBLE_TILES);
    }

    public int getTopographicIntervalMetres() { return topographicIntervalMetres; }

    public int getZoomFactor() { return zoomFactor.getMultiplier(); }
    public int cycleZoomFactor() { return zoomFactor.cycle(); }
    public double fullMapWheelSteps(int wheelDelta) { return zoomFactor.fullMapWheelSteps(wheelDelta); }
    public int miniMapWheelTiles(int wheelDelta) { return zoomFactor.miniMapWheelTiles(wheelDelta, caveView); }

    public int setTopographicIntervalMetres(int value) {
        topographicIntervalMetres = Math.max(0, Math.min(MAXIMUM_TOPOGRAPHIC_INTERVAL_METRES, value));
        return topographicIntervalMetres;
    }

    public int changeTopographicInterval(int delta) {
        return setTopographicIntervalMetres(topographicIntervalMetres + delta);
    }

    /** Empty input temporarily disables contours while the user edits the field. */
    public boolean setTopographicInput(String value) {
        if (value == null || value.length() > 2) return false;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        }
        setTopographicIntervalMetres(value.isEmpty() ? 0 : Integer.parseInt(value));
        return true;
    }

    public boolean isCaveView() { return caveView; }

    /** Manual selection survives ticks until the next actual layer transition. */
    public void toggleMapView() { selectMapView(!caveView); }

    public void selectMapView(boolean cave) {
        caveView = cave;
        if (cave) caveVisibleTiles = CAVE_MINIMUM_VISIBLE_TILES;
    }

    public boolean observePlayerLayer(boolean cave) {
        if (observedCaveLayer != null && observedCaveLayer == cave) return false;
        observedCaveLayer = cave;
        selectMapView(cave);
        return true;
    }

    public void resetPlayerLayerObservation() { observedCaveLayer = null; }

    public boolean areDeedsVisible() { return deedsVisible; }

    public boolean areRoadsVisible() { return roadsVisible; }

    public boolean isNavigationLineVisible() { return navigationLineVisible; }

    public int increaseVisibleTiles() {
        return zoomOut(1);
    }

    public int decreaseVisibleTiles() {
        return zoomIn(1);
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
        if (caveView) {
            caveVisibleTiles = clampCave(caveVisibleTiles - positiveSteps(steps));
            return caveVisibleTiles;
        }
        visibleTiles = clamp(visibleTiles - positiveSteps(steps));
        return visibleTiles;
    }

    /** One wheel step zooms out by showing one more tile per side. */
    public int zoomOut(int steps) {
        if (caveView) {
            caveVisibleTiles = clampCave(caveVisibleTiles + positiveSteps(steps));
            return caveVisibleTiles;
        }
        visibleTiles = clamp(visibleTiles + positiveSteps(steps));
        return visibleTiles;
    }

    public double pixelsPerTile(int viewportPixels) {
        if (viewportPixels < 1) throw new IllegalArgumentException(
                "mini-map viewport must be positive");
        return viewportPixels / (double) getVisibleTiles();
    }

    private static int clamp(int value) {
        return Math.max(MINIMUM_VISIBLE_TILES,
                Math.min(MAXIMUM_VISIBLE_TILES, value));
    }

    private static int positiveSteps(int value) {
        return Math.max(1, value);
    }

    private static int clampCave(int value) {
        return Math.max(CAVE_MINIMUM_VISIBLE_TILES,
                Math.min(CAVE_MAXIMUM_VISIBLE_TILES, value));
    }
}
