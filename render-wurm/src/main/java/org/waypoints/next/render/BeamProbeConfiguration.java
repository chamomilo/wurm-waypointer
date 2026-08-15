package org.waypoints.next.render;

import java.util.Properties;

/** Validated, immutable settings for the opt-in Phase 0 rendering probe. */
public final class BeamProbeConfiguration {
    private final boolean enabled;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    private final float width;
    private final float height;
    private final boolean throughWalls;
    private final float throughWallWidth;
    private final String waypointName;

    private BeamProbeConfiguration(boolean enabled, float red, float green, float blue,
                                   float alpha, float width, float height,
                                   boolean throughWalls, float throughWallWidth,
                                   String waypointName) {
        this.enabled = enabled;
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        this.width = width;
        this.height = height;
        this.throughWalls = throughWalls;
        this.throughWallWidth = throughWallWidth;
        this.waypointName = waypointName;
    }

    public static BeamProbeConfiguration disabled() {
        return new BeamProbeConfiguration(false, 0.20f, 0.75f, 1.00f,
                0.70f, 2.00f, 400.00f, false, 0.15f, "Waypoint 1");
    }

    public static BeamProbeConfiguration from(Properties properties) {
        Properties values = properties == null ? new Properties() : properties;
        return new BeamProbeConfiguration(
                Boolean.parseBoolean(values.getProperty("phase0BeamProbe", "false").trim()),
                bounded(values, "phase0BeamRed", 0.20f, 0.0f, 1.0f),
                bounded(values, "phase0BeamGreen", 0.75f, 0.0f, 1.0f),
                bounded(values, "phase0BeamBlue", 1.00f, 0.0f, 1.0f),
                bounded(values, "phase0BeamAlpha", 0.70f, 0.0f, 1.0f),
                bounded(values, "phase0BeamWidth", 2.00f, 0.10f, 64.0f),
                bounded(values, "phase0BeamHeight", 400.00f, 4.0f, 2000.0f),
                Boolean.parseBoolean(values.getProperty(
                        "phase0BeamThroughWalls", "false").trim()),
                bounded(values, "phase0BeamThroughWallWidth", 0.15f, 0.02f, 1.0f),
                waypointName(values));
    }

    private static String waypointName(Properties values) {
        String value = values.getProperty("phase0BeamName", "Waypoint 1")
                .replace('\r', ' ').replace('\n', ' ').trim();
        if (value.isEmpty()) return "Waypoint 1";
        if (value.length() <= 80) return value;
        return value.substring(0, 79) + "\u2026";
    }

    private static float bounded(Properties values, String key, float fallback,
                                 float minimum, float maximum) {
        String text = values.getProperty(key);
        if (text == null || text.trim().isEmpty()) return fallback;
        float value = Float.parseFloat(text.trim());
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < minimum || value > maximum) {
            throw new IllegalArgumentException(key + " is outside " + minimum + ".." + maximum);
        }
        return value;
    }

    public boolean isEnabled() { return enabled; }
    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public float getAlpha() { return alpha; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isThroughWalls() { return throughWalls; }
    public float getThroughWallWidth() { return throughWallWidth; }
    public String getWaypointName() { return waypointName; }

    public String diagnosticSummary() {
        return "beamProbe=" + enabled
                + ", color=" + red + "," + green + "," + blue + "," + alpha
                + ", width=" + width + ", height=" + height
                + ", throughWalls=" + throughWalls
                + ", throughWallWidth=" + throughWallWidth
                + ", waypointName=\"" + waypointName + "\"";
    }
}
