package org.waypoints.next.surroundings;

/** Immutable RGBA colour used by a Scanner profile. */
public final class ScannerColor {
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;

    public ScannerColor(float red, float green, float blue, float alpha) {
        this.red = component(red, "red");
        this.green = component(green, "green");
        this.blue = component(blue, "blue");
        this.alpha = component(alpha, "alpha");
    }

    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public float getAlpha() { return alpha; }

    private static float component(float value, String label) {
        if (!Float.isFinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(label + " must be within 0..1");
        }
        return value;
    }
}
