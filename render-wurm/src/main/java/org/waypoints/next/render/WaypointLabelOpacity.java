package org.waypoints.next.render;

/** Keeps navigation labels legible independently of translucent world effects. */
public final class WaypointLabelOpacity {
    private WaypointLabelOpacity() {
    }

    public static float textAlpha(float ignoredWorldEffectAlpha) {
        return 1.0f;
    }
}
