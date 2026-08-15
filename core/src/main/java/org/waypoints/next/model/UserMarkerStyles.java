package org.waypoints.next.model;

/** User-selectable styles. Vanilla lights and Rift are system landmarks only. */
public final class UserMarkerStyles {
    private static final MarkerStyle.WorldStyle[] VALUES = {
            MarkerStyle.WorldStyle.COLORED_BEAM,
            MarkerStyle.WorldStyle.CIRCLE_BEAM,
            MarkerStyle.WorldStyle.TARGET_CROSSHAIR,
            MarkerStyle.WorldStyle.HOLLOW_CIRCLE,
            MarkerStyle.WorldStyle.PLUS,
            MarkerStyle.WorldStyle.HOUSE,
            MarkerStyle.WorldStyle.DIAMOND,
            MarkerStyle.WorldStyle.PICKAXE,
            MarkerStyle.WorldStyle.SHOVEL,
            MarkerStyle.WorldStyle.PICKAXE_AND_SHOVEL,
            MarkerStyle.WorldStyle.MONEY_SIGN,
            MarkerStyle.WorldStyle.CROSSED_SWORDS,
            MarkerStyle.WorldStyle.LIGHTHOUSE,
            MarkerStyle.WorldStyle.COMPASS_ONLY,
            MarkerStyle.WorldStyle.HIDDEN
    };

    private UserMarkerStyles() { }

    public static MarkerStyle.WorldStyle[] values() { return VALUES.clone(); }

    public static boolean isSelectable(MarkerStyle.WorldStyle style) {
        if (style == null) return false;
        for (MarkerStyle.WorldStyle value : VALUES) {
            if (value == style) return true;
        }
        return false;
    }

    public static int indexOf(MarkerStyle.WorldStyle style) {
        for (int i = 0; i < VALUES.length; i++) {
            if (VALUES[i] == style) return i;
        }
        return 0;
    }

    /** Changes only the rendered shape/type; every user-tuned value is retained. */
    public static MarkerStyle withWorldStyle(MarkerStyle.WorldStyle worldStyle,
                                             MarkerStyle current) {
        if (worldStyle == null) throw new IllegalArgumentException(
                "world style is required");
        if (!isSelectable(worldStyle)) throw new IllegalArgumentException(
                "vanilla White Light, Black Light, and Rift are system-only styles");
        MarkerStyle base = editable(current);
        if (base.getWorldStyle() == worldStyle) return base;
        return new MarkerStyle(worldStyle, base.getRed(), base.getGreen(),
                base.getBlue(), base.getAlpha(), base.getMarkerSize(),
                base.getBeamWidth(), base.isShowLabel(), base.isShowDistance());
    }

    /** Legacy user White/Black/Rift records remain readable as colored beams. */
    public static MarkerStyle editable(MarkerStyle style) {
        MarkerStyle current = style == null ? MarkerStyle.defaultColoredBeam() : style;
        if (isSelectable(current.getWorldStyle())) return current;
        return new MarkerStyle(MarkerStyle.WorldStyle.COLORED_BEAM,
                current.getRed(), current.getGreen(), current.getBlue(),
                current.getAlpha(), current.getMarkerSize(), current.getBeamWidth(),
                current.isShowLabel(), current.isShowDistance());
    }
}
