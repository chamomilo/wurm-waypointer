package org.waypoints.next.ui;

/** Pure decision boundary for compass-click Manager visibility toggling. */
public final class WaypointManagerVisibilityPolicy {
    private WaypointManagerVisibilityPolicy() { }

    public static boolean shouldClose(boolean windowExists, boolean sameHud,
                                      boolean attachedToHud) {
        return windowExists && sameHud && attachedToHud;
    }
}
