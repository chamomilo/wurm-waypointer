package org.waypoints.next.model;

import java.util.Objects;

/** Persisted visual intent. Renderer-specific objects never enter the core model. */
public final class MarkerStyle {
    public enum WorldStyle {
        WHITE_LIGHT,
        BLACK_LIGHT,
        RIFT,
        COLORED_BEAM,
        CIRCLE_BEAM,
        TARGET_CROSSHAIR,
        HOLLOW_CIRCLE,
        PLUS,
        HOUSE,
        DIAMOND,
        PICKAXE,
        SHOVEL,
        PICKAXE_AND_SHOVEL,
        MONEY_SIGN,
        CROSSED_SWORDS,
        LIGHTHOUSE,
        COMPASS_ONLY,
        HIDDEN,
        /** Internal source-locked pictogram; never exposed by UserMarkerStyles. */
        LOOT_MAP_SCROLL,
        /** Internal source-locked completed archaeology-report pictogram. */
        ARCHAEOLOGY_REPORT_SCROLL,
        /** Internal pictogram used by temporary Surroundings marks. */
        EXCLAMATION
    }

    private final WorldStyle worldStyle;
    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    private final float markerSize;
    private final float beamWidth;
    private final boolean showLabel;
    private final boolean showDistance;

    public MarkerStyle(WorldStyle worldStyle, float red, float green, float blue,
                       float alpha, float markerSize, float beamWidth,
                       boolean showLabel, boolean showDistance) {
        if (worldStyle == null) throw new IllegalArgumentException("world style is required");
        this.worldStyle = worldStyle;
        this.red = unit(red, "red");
        this.green = unit(green, "green");
        this.blue = unit(blue, "blue");
        this.alpha = unit(alpha, "alpha");
        this.markerSize = positiveFinite(markerSize, "marker size");
        this.beamWidth = positiveFinite(beamWidth, "beam width");
        this.showLabel = showLabel;
        this.showDistance = showDistance;
    }

    public static MarkerStyle defaultColoredBeam() {
        return new MarkerStyle(WorldStyle.COLORED_BEAM, 1.0f, 0.2f, 0.2f,
                0.85f, 9.0f, 2.0f, true, true);
    }

    public WorldStyle getWorldStyle() { return worldStyle; }
    public float getRed() { return red; }
    public float getGreen() { return green; }
    public float getBlue() { return blue; }
    public float getAlpha() { return alpha; }
    public float getMarkerSize() { return markerSize; }
    public float getBeamWidth() { return beamWidth; }
    public boolean isShowLabel() { return showLabel; }
    public boolean isShowDistance() { return showDistance; }

    private static float unit(float value, String label) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value < 0.0f || value > 1.0f) {
            throw new IllegalArgumentException(label + " must be in 0..1");
        }
        return value;
    }

    private static float positiveFinite(float value, String label) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0.0f) {
            throw new IllegalArgumentException(label + " must be finite and positive");
        }
        return value;
    }

    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MarkerStyle)) return false;
        MarkerStyle that = (MarkerStyle) other;
        return worldStyle == that.worldStyle
                && Float.compare(red, that.red) == 0
                && Float.compare(green, that.green) == 0
                && Float.compare(blue, that.blue) == 0
                && Float.compare(alpha, that.alpha) == 0
                && Float.compare(markerSize, that.markerSize) == 0
                && Float.compare(beamWidth, that.beamWidth) == 0
                && showLabel == that.showLabel && showDistance == that.showDistance;
    }

    @Override public int hashCode() {
        return Objects.hash(worldStyle, red, green, blue, alpha, markerSize,
                beamWidth, showLabel, showDistance);
    }
}
