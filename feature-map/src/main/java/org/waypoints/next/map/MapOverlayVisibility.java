package org.waypoints.next.map;

/** Session-local visibility of independently switchable server-map overlays. */
public final class MapOverlayVisibility {
    public enum Layer { DEEDS, HIGHWAYS, WAYPOINTS }

    private boolean deeds;
    private boolean highways;
    private boolean waypoints;

    public MapOverlayVisibility(boolean deeds, boolean highways,
                                boolean waypoints) {
        this.deeds = deeds;
        this.highways = highways;
        this.waypoints = waypoints;
    }

    public boolean isVisible(Layer layer) {
        if (layer == null) return false;
        switch (layer) {
            case DEEDS: return deeds;
            case HIGHWAYS: return highways;
            case WAYPOINTS: return waypoints;
            default: return false;
        }
    }

    public void toggle(Layer layer) {
        if (layer == null) return;
        switch (layer) {
            case DEEDS: deeds = !deeds; break;
            case HIGHWAYS: highways = !highways; break;
            case WAYPOINTS: waypoints = !waypoints; break;
            default: break;
        }
    }
}
