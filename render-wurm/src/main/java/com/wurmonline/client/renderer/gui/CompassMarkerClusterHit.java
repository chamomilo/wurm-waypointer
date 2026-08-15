package com.wurmonline.client.renderer.gui;

import org.waypoints.next.navigation.NavigationTargetKey;

/** Immutable click-time snapshot of one clustered compass marker. */
public final class CompassMarkerClusterHit {
    private final NavigationTargetKey[] keys;
    private final int screenX;
    private final int screenY;

    CompassMarkerClusterHit(NavigationTargetKey[] keys, int screenX, int screenY) {
        this.keys = keys;
        this.screenX = screenX;
        this.screenY = screenY;
    }

    public int size() {
        return keys.length;
    }

    public NavigationTargetKey get(int index) {
        return keys[index];
    }

    public int getScreenX() {
        return screenX;
    }

    public int getScreenY() {
        return screenY;
    }
}
