package org.waypoints.next.ui;

import java.util.Locale;

/** Pure selected-color state shared by picker rendering and editor binding. */
public final class WaypointColorPickerState {
    private final float[] hsv = new float[3];
    private final float[] rgb = new float[3];
    private String hex = "#FFFFFF";

    public WaypointColorPickerState(float red, float green, float blue) {
        setColor(red, green, blue);
    }

    public void setColor(float red, float green, float blue) {
        HsvColor.fromRgb(red, green, blue, hsv);
        rgb[0] = red;
        rgb[1] = green;
        rgb[2] = blue;
        updateHex();
    }

    public void setHue(float hue) {
        if (Float.isNaN(hue) || Float.isInfinite(hue)) {
            throw new IllegalArgumentException("hue must be finite");
        }
        hsv[0] = hue - (float) Math.floor(hue);
        HsvColor.toRgb(hsv[0], hsv[1], hsv[2], rgb);
        updateHex();
    }

    public void setSaturationAndValue(float saturation, float value) {
        HsvColor.toRgb(hsv[0], saturation, value, rgb);
        hsv[1] = saturation;
        hsv[2] = value;
        updateHex();
    }

    public boolean matches(float red, float green, float blue) {
        return Float.compare(rgb[0], red) == 0
                && Float.compare(rgb[1], green) == 0
                && Float.compare(rgb[2], blue) == 0;
    }

    public float getHue() { return hsv[0]; }
    public float getSaturation() { return hsv[1]; }
    public float getValue() { return hsv[2]; }
    public float getRed() { return rgb[0]; }
    public float getGreen() { return rgb[1]; }
    public float getBlue() { return rgb[2]; }
    public String getHex() { return hex; }

    private void updateHex() {
        int red = Math.max(0, Math.min(255, Math.round(rgb[0] * 255.0f)));
        int green = Math.max(0, Math.min(255, Math.round(rgb[1] * 255.0f)));
        int blue = Math.max(0, Math.min(255, Math.round(rgb[2] * 255.0f)));
        hex = String.format(Locale.ENGLISH, "#%02X%02X%02X", red, green, blue);
    }
}
