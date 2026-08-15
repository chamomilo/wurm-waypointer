package org.waypoints.next.render;

/** Responsive pixel sizing for markers drawn over Wurm's resizable compass. */
public final class CompassMarkerScale {
    private static final int REFERENCE_COMPASS_DIAMETER = 128;
    private static final int REFERENCE_MARKER_PIXELS = 10;
    private static final int MINIMUM_MARKER_PIXELS = 5;
    private static final int MAXIMUM_MARKER_PIXELS = 18;

    private CompassMarkerScale() {
    }

    public static int pixels(int compassWidth, int compassHeight) {
        int diameter = Math.max(1, Math.min(compassWidth, compassHeight));
        float scale = (float) Math.sqrt(
                diameter / (float) REFERENCE_COMPASS_DIAMETER);
        int pixels = Math.round(REFERENCE_MARKER_PIXELS * scale);
        return Math.max(MINIMUM_MARKER_PIXELS,
                Math.min(MAXIMUM_MARKER_PIXELS, pixels));
    }

    public static int selectionPadding(int markerPixels) {
        return Math.max(4, Math.round(Math.max(1, markerPixels) * 0.2f));
    }

    public static int clusterPixels(int markerPixels) {
        int marker = Math.max(MINIMUM_MARKER_PIXELS, markerPixels);
        return marker + Math.max(2, marker / 4);
    }
}
