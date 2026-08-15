package org.waypoints.next.integration;

import org.junit.Test;
import org.waypoints.next.source.CoordinateInputParser;
import org.waypoints.next.source.MapBounds;

import static org.junit.Assert.assertEquals;

public class WaypointCommandArgumentsTest {
    private final CoordinateInputParser parser = new CoordinateInputParser();
    private final MapBounds bounds = new MapBounds(4096, 4096);

    @Test public void parsesRequiredNameXYFormWithSpacesInName() {
        WaypointCommandArguments.AddRequest request = WaypointCommandArguments.parseAdd(
                new String[]{"add", "Northern", "Harbour", "3044", "899"},
                parser, bounds);
        assertEquals("Northern Harbour", request.getName());
        assertEquals(3044.0, request.getCoordinate().getCoordinate().getTileX(), 0.0);
    }

    @Test public void parsesMapLinkAndExplicitSeparatorGpsText() {
        WaypointCommandArguments.AddRequest link = WaypointCommandArguments.parseAdd(
                new String[]{"add", "Harbour", "http://andistyr.github.io/wu-map/14821/#3044_899"},
                parser, bounds);
        assertEquals("Harbour", link.getName());
        assertEquals("14821", link.getCoordinate().getServerHint());

        WaypointCommandArguments.AddRequest gps = WaypointCommandArguments.parseAdd(
                new String[]{"add", "Cave", "entrance", "|", "GPS:", "at", "tile", "100,", "200", "in", "cave"},
                parser, bounds);
        assertEquals("Cave entrance", gps.getName());
        assertEquals(200.0, gps.getCoordinate().getCoordinate().getTileY(), 0.0);
    }

    @Test public void removesCommandRepeatedByWurmConsoleContract() {
        String[] normalized = WaypointCommandArguments.withoutRepeatedCommand("wp",
                new String[]{"wp", "here", "Test", "point"});
        assertEquals(3, normalized.length);
        assertEquals("here", normalized[0]);

        String[] alreadyNormalized = WaypointCommandArguments.withoutRepeatedCommand("/wp",
                new String[]{"add", "Harbour", "10", "20"});
        assertEquals(4, alreadyNormalized.length);
        assertEquals("add", alreadyNormalized[0]);
    }
}
