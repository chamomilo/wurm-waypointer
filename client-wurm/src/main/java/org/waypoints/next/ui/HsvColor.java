package org.waypoints.next.ui;

/** Allocation-free RGB/HSV conversion used by the native color picker. */
public final class HsvColor {
    private HsvColor() { }

    public static void fromRgb(float red, float green, float blue, float[] output) {
        requireOutput(output);
        float r = unit(red);
        float g = unit(green);
        float b = unit(blue);
        float maximum = Math.max(r, Math.max(g, b));
        float minimum = Math.min(r, Math.min(g, b));
        float delta = maximum - minimum;
        float hue;
        if (delta == 0.0f) hue = 0.0f;
        else if (maximum == r) hue = ((g - b) / delta) % 6.0f;
        else if (maximum == g) hue = (b - r) / delta + 2.0f;
        else hue = (r - g) / delta + 4.0f;
        hue /= 6.0f;
        if (hue < 0.0f) hue += 1.0f;
        output[0] = hue;
        output[1] = maximum == 0.0f ? 0.0f : delta / maximum;
        output[2] = maximum;
    }

    public static void toRgb(float hue, float saturation, float value,
                             float[] output) {
        requireOutput(output);
        float h = wrap(hue) * 6.0f;
        float s = unit(saturation);
        float v = unit(value);
        int sector = (int) Math.floor(h);
        float fraction = h - sector;
        float p = v * (1.0f - s);
        float q = v * (1.0f - fraction * s);
        float t = v * (1.0f - (1.0f - fraction) * s);
        switch (sector % 6) {
            case 0: output[0] = v; output[1] = t; output[2] = p; break;
            case 1: output[0] = q; output[1] = v; output[2] = p; break;
            case 2: output[0] = p; output[1] = v; output[2] = t; break;
            case 3: output[0] = p; output[1] = q; output[2] = v; break;
            case 4: output[0] = t; output[1] = p; output[2] = v; break;
            default: output[0] = v; output[1] = p; output[2] = q; break;
        }
    }

    private static float wrap(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("hue must be finite");
        }
        float wrapped = value - (float) Math.floor(value);
        return wrapped == 1.0f ? 0.0f : wrapped;
    }

    private static float unit(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)
                || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException("color component must be in 0..1");
        }
        return value;
    }

    private static void requireOutput(float[] output) {
        if (output == null || output.length < 3) {
            throw new IllegalArgumentException("output must contain three values");
        }
    }
}
