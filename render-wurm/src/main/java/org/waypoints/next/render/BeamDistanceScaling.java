package org.waypoints.next.render;

/** Keeps the through-wall fallback legible under perspective at long range. */
public final class BeamDistanceScaling {
    // A world-axis cross can present only about 70% of its nominal width to
    // the camera. Two nominal pixels can therefore rasterize as a single,
    // intermittent column at cave distances. Four keeps the depth-independent
    // leg stable without widening nearby beams that already exceed this size.
    private static final float MINIMUM_SCREEN_PIXELS = 4.0f;
    private static final float DISTANCE_HEIGHT_MULTIPLIER = 2.0f;
    // Wurm still clips vertices against the camera far plane even when a
    // primitive uses an ALWAYS depth test. Keep the navigation overlay on the
    // same camera ray but pull very distant geometry inside a conservative
    // render distance. Its bearing is unchanged, while waypoints several
    // kilometres away no longer disappear before rasterization.
    private static final float MAXIMUM_GEOMETRY_DISTANCE = 256.0f;
    // Cave projection clips small overlays after roughly 8-10 tiles. Keep the
    // same target ray but submit pictogram geometry inside six tiles.
    private static final float MAXIMUM_CAVE_SYMBOL_GEOMETRY_DISTANCE = 24.0f;
    private static final float MINIMUM_FAR_LINE_ABOVE_TARGET = 128.0f;

    private BeamDistanceScaling() {
    }

    public static float throughWallWidth(float configuredWidth, float distance,
                                         int screenWidth, float horizontalFovDegrees) {
        if (!finitePositive(distance) || screenWidth <= 0) return configuredWidth;
        float safeFov = Math.max(10.0f, Math.min(170.0f, horizontalFovDegrees));
        double halfFovRadians = Math.toRadians(safeFov * 0.5f);
        float worldUnitsPerPixel = (float) (2.0 * distance * Math.tan(halfFovRadians)
                / screenWidth);
        return Math.max(configuredWidth, worldUnitsPerPixel * MINIMUM_SCREEN_PIXELS);
    }

    public static float halfHeight(float configuredHeight, float distance) {
        if (!finitePositive(distance)) return configuredHeight;
        return Math.max(configuredHeight, distance * DISTANCE_HEIGHT_MULTIPLIER);
    }

    public static float geometryDistance(float actualDistance) {
        if (!finitePositive(actualDistance)) return actualDistance;
        return Math.min(actualDistance, MAXIMUM_GEOMETRY_DISTANCE);
    }

    public static float symbolGeometryDistance(float actualDistance,
                                               int targetLayer) {
        float surfaceSafe = geometryDistance(actualDistance);
        return targetLayer < 0
                ? Math.min(surfaceSafe, MAXIMUM_CAVE_SYMBOL_GEOMETRY_DISTANCE)
                : surfaceSafe;
    }

    /** Complex fields become a single line when pulled inside the far plane. */
    public static boolean beamRequiresLineOnlyFallback(float actualDistance) {
        return finitePositive(actualDistance)
                && actualDistance > MAXIMUM_GEOMETRY_DISTANCE;
    }

    /** Keeps a distant ray tall above the horizon without wasting it below ground. */
    public static float farLineAboveTarget(float requestedHalfHeight) {
        return Math.max(MINIMUM_FAR_LINE_ABOVE_TARGET, requestedHalfHeight);
    }

    public static float farLineBelowTarget(float requestedHalfHeight) {
        return 0.0f;
    }

    private static boolean finitePositive(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value) && value > 0.0f;
    }
}
