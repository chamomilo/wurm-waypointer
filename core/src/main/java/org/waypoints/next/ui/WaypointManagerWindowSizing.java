package org.waypoints.next.ui;

/** Pure vertical resize policy for the resizable list and fixed-height editors. */
public final class WaypointManagerWindowSizing {
    private WaypointManagerWindowSizing() { }

    public static int height(boolean listMode, int requestedHeight,
                             int editorHeight) {
        return listMode ? Math.max(300, requestedHeight) : editorHeight;
    }
}
