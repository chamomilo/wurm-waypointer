package org.waypoints.next.ui;

import org.junit.Test;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.source.CoordinateInputParser;
import org.waypoints.next.source.MapBounds;
import org.waypoints.next.source.ParsedCoordinate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class WaypointManagerHelpTextTest {
    @Test public void documentedGpsExampleIsAcceptedByTheRealParser() {
        ParsedCoordinate parsed = new CoordinateInputParser().parse(
                WaypointManagerHelpText.GPS_EXAMPLE, new MapBounds(4096, 4096));
        assertEquals(3044.0d, parsed.getCoordinate().getTileX(), 0.0d);
        assertEquals(899.0d, parsed.getCoordinate().getTileY(), 0.0d);
        assertEquals(WaypointLayer.CAVE, parsed.getCoordinate().getLayer());
        assertTrue(WaypointManagerHelpText.COORDINATE_INPUT.contains("/gps"));
    }
}
