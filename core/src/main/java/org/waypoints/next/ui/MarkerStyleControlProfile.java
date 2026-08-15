package org.waypoints.next.ui;

import org.waypoints.next.model.MarkerStyle;

/** Context-specific slider labels and visibility for one world presentation. */
public final class MarkerStyleControlProfile {
    public enum Kind { BEAM, CIRCLE, SYMBOL, NONE }

    private final Kind kind;
    private final String alphaLabel;
    private final String primaryLabel;
    private final String secondaryLabel;

    private MarkerStyleControlProfile(Kind kind, String alphaLabel,
                                      String primaryLabel,
                                      String secondaryLabel) {
        this.kind = kind;
        this.alphaLabel = alphaLabel;
        this.primaryLabel = primaryLabel;
        this.secondaryLabel = secondaryLabel;
    }

    public static MarkerStyleControlProfile forStyle(
            MarkerStyle.WorldStyle style) {
        if (style == null) throw new IllegalArgumentException(
                "world style is required");
        switch (style) {
            case COLORED_BEAM:
                return new MarkerStyleControlProfile(Kind.BEAM,
                        "Beam alpha", "Beam field size", "Beam thickness");
            case WHITE_LIGHT:
            case BLACK_LIGHT:
            case RIFT:
                throw new IllegalArgumentException(
                        "vanilla White Light, Black Light, and Rift are system-only styles");
            case CIRCLE_BEAM:
                return new MarkerStyleControlProfile(Kind.CIRCLE,
                        "Circle alpha", "Circle radius",
                        "Center beam thickness");
            case TARGET_CROSSHAIR:
            case HOLLOW_CIRCLE:
            case PLUS:
            case HOUSE:
            case DIAMOND:
            case PICKAXE:
            case SHOVEL:
            case PICKAXE_AND_SHOVEL:
            case MONEY_SIGN:
            case CROSSED_SWORDS:
            case LIGHTHOUSE:
                return new MarkerStyleControlProfile(Kind.SYMBOL,
                        "Symbol alpha", "Symbol size", "Stroke thickness");
            case COMPASS_ONLY:
            case HIDDEN:
                return new MarkerStyleControlProfile(Kind.NONE, null, null, null);
            default:
                throw new IllegalArgumentException("unsupported world style: " + style);
        }
    }

    public Kind getKind() { return kind; }
    public int getSliderCount() {
        return kind == Kind.NONE ? 0 : 3;
    }
    public boolean isColorEditable() { return true; }
    public String getAlphaLabel() { return alphaLabel; }
    public String getPrimaryLabel() { return primaryLabel; }
    public String getSecondaryLabel() { return secondaryLabel; }
}
