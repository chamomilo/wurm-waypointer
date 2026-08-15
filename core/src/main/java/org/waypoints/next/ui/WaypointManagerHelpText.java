package org.waypoints.next.ui;

/** Tested English help text for the native Manager's coordinate workflow. */
public final class WaypointManagerHelpText {
    public static final String GPS_EXAMPLE =
            "GPS: You are at tile 3044, 899 in a cave";
    public static final String COORDINATE_SOURCE =
            "Run /gps, copy the coordinate line, then paste it here; map links and x=X y=Y also work.";
    public static final String COORDINATE_INPUT =
            "Run /gps and paste a line such as '" + GPS_EXAMPLE
                    + "'. Also accepts 'x=3044 y=899', '#3044_899', or a full wu-map link.";

    private WaypointManagerHelpText() {
    }
}
