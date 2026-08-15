package org.waypoints.next.ui;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.UserMarkerStyles;

/** Mutable editor-only state; persisted records and render snapshots stay immutable. */
public final class MarkerStyleEditorState {
    private MarkerStyle style;

    public MarkerStyleEditorState(MarkerStyle initial) {
        if (initial == null) throw new IllegalArgumentException("marker style is required");
        style = initial;
    }

    public MarkerStyle getStyle() {
        return style;
    }

    public void selectWorldStyle(MarkerStyle.WorldStyle worldStyle) {
        style = UserMarkerStyles.withWorldStyle(worldStyle, style);
    }

    public void setColor(float red, float green, float blue) {
        style = new MarkerStyle(style.getWorldStyle(), red, green, blue,
                style.getAlpha(), style.getMarkerSize(), style.getBeamWidth(),
                style.isShowLabel(), style.isShowDistance());
    }

    public void setAlpha(float alpha) {
        style = new MarkerStyle(style.getWorldStyle(), style.getRed(),
                style.getGreen(), style.getBlue(), alpha,
                style.getMarkerSize(), style.getBeamWidth(),
                style.isShowLabel(), style.isShowDistance());
    }

    public void setMarkerSize(float markerSize) {
        style = new MarkerStyle(style.getWorldStyle(), style.getRed(),
                style.getGreen(), style.getBlue(), style.getAlpha(), markerSize,
                style.getBeamWidth(), style.isShowLabel(),
                style.isShowDistance());
    }

    public void setBeamWidth(float beamWidth) {
        style = new MarkerStyle(style.getWorldStyle(), style.getRed(),
                style.getGreen(), style.getBlue(), style.getAlpha(),
                style.getMarkerSize(), beamWidth, style.isShowLabel(),
                style.isShowDistance());
    }
}
