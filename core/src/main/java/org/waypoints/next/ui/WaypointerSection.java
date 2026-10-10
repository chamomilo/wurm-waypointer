package org.waypoints.next.ui;

/** Stable selectors owned by the hub, independent of native layout and language. */
public enum WaypointerSection {
    ALL_WAYPOINTS("ALL WAYPOINTS"), MOBS_AROUND("MOBS AROUND"),
    CONTAINERS_AROUND("CONTAINERS AROUND"), OBJECTS_AROUND("OBJECTS AND ITEMS AROUND"),
    MY_VEHICLES("VEHICLES"), MY_ANIMALS("MY ANIMALS"), SETTINGS("SETTINGS");
    private final String label;
    WaypointerSection(String label){this.label=label;}
    public String label(){return label;}
}
