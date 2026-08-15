package org.waypoints.next.source;

import org.waypoints.next.model.WaypointCoordinate;

/** User input after parsing but before server-hint confirmation in UI. */
public final class ParsedCoordinate {
    private final WaypointCoordinate coordinate;
    private final String serverHint;
    private final String sourceKind;

    public ParsedCoordinate(WaypointCoordinate coordinate, String serverHint,
                            String sourceKind) {
        if (coordinate == null) throw new IllegalArgumentException("coordinate is required");
        this.coordinate = coordinate;
        this.serverHint = serverHint == null ? "" : serverHint.trim();
        this.sourceKind = sourceKind == null ? "" : sourceKind.trim();
    }

    public WaypointCoordinate getCoordinate() { return coordinate; }
    public String getServerHint() { return serverHint; }
    public String getSourceKind() { return sourceKind; }
}
