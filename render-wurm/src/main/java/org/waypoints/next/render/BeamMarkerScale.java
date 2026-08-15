package org.waypoints.next.render;

/** Pure marker-size policy for beam fields and the Circle beam wall. */
public final class BeamMarkerScale {
    public static final float MINIMUM = 1.0f;
    public static final float DEFAULT = 9.0f;
    public static final float MAXIMUM = 30.0f;
    private static final float DEFAULT_CIRCLE_RADIUS = 9.0f;
    private static final float MAXIMUM_CIRCLE_RADIUS = 250.0f;
    private static final float MINIMUM_LINE_HEIGHT = 64.0f;

    private BeamMarkerScale() { }

    /**
     * Minimum suppresses the decorative field, the default preserves the
     * pre-slider beam, and maximum doubles it. The thin navigation line is
     * deliberately controlled separately and never scales away.
     */
    public static float fieldScale(float markerSize) {
        float size = clamp(markerSize);
        if (size <= MINIMUM) return 0.0f;
        if (size <= DEFAULT) {
            return (size - MINIMUM) / (DEFAULT - MINIMUM);
        }
        return 1.0f + (size - DEFAULT) / (MAXIMUM - DEFAULT);
    }

    public static float circleRadius(float markerSize) {
        float size = clamp(markerSize);
        if (size <= DEFAULT) {
            return DEFAULT_CIRCLE_RADIUS * fieldScale(size);
        }
        float progress = (size - DEFAULT) / (MAXIMUM - DEFAULT);
        return DEFAULT_CIRCLE_RADIUS
                + (MAXIMUM_CIRCLE_RADIUS - DEFAULT_CIRCLE_RADIUS) * progress;
    }

    /** Keeps the minimum line just above the horizon and reaches 2x at max. */
    public static float height(float adaptiveDefaultHeight, float markerSize) {
        float base = Math.max(MINIMUM_LINE_HEIGHT, adaptiveDefaultHeight);
        float size = clamp(markerSize);
        if (size <= DEFAULT) {
            float progress = (size - MINIMUM) / (DEFAULT - MINIMUM);
            return MINIMUM_LINE_HEIGHT
                    + (base - MINIMUM_LINE_HEIGHT) * progress;
        }
        float progress = (size - DEFAULT) / (MAXIMUM - DEFAULT);
        return base * (1.0f + progress);
    }

    private static float clamp(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return DEFAULT;
        return Math.max(MINIMUM, Math.min(MAXIMUM, value));
    }
}
