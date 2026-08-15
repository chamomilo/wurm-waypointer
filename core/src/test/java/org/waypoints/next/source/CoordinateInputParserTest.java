package org.waypoints.next.source;

import org.junit.Test;
import org.waypoints.next.model.WaypointLayer;

import static org.junit.Assert.assertEquals;

public class CoordinateInputParserTest {
    private final CoordinateInputParser parser = new CoordinateInputParser();
    private final MapBounds bounds = new MapBounds(4096, 4096);

    @Test public void acceptsMapUrlAndExtractsServerHint() {
        ParsedCoordinate parsed = parser.parse(
                "http://andistyr.github.io/wu-map/14821/#3044_899", bounds);
        assertEquals(3044.0, parsed.getCoordinate().getTileX(), 0.0);
        assertEquals(899.0, parsed.getCoordinate().getTileY(), 0.0);
        assertEquals("14821", parsed.getServerHint());
        assertEquals("map-url", parsed.getSourceKind());
    }

    @Test public void extractsOneMapUrlFromMultilineClipboardInstructions() {
        ParsedCoordinate parsed = parser.parse(
                "Check the map link:\nCopy this:\n"
                        + "http://andistyr.github.io/wu-map/14821/#3044_899\n"
                        + "Then run: wp paste Phase1 Map\nwp list", bounds);
        assertEquals(3044.0, parsed.getCoordinate().getTileX(), 0.0);
        assertEquals(899.0, parsed.getCoordinate().getTileY(), 0.0);
        assertEquals("14821", parsed.getServerHint());
        assertEquals("map-url", parsed.getSourceKind());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAmbiguousClipboardWithTwoMapLinks() {
        parser.parse("http://example.test/1/#10_20\nhttp://example.test/2/#30_40",
                bounds);
    }

    @Test public void acceptsFragmentPlainLabelledAndGpsLayer() {
        assertEquals(3044.0, parser.parse("#3044_899", bounds)
                .getCoordinate().getTileX(), 0.0);
        assertEquals(899.5, parser.parse("3044, 899.5", bounds)
                .getCoordinate().getTileY(), 0.0);
        assertEquals(3044.25, parser.parse("x=3044.25 y=899", bounds)
                .getCoordinate().getTileX(), 0.0);
        assertEquals(WaypointLayer.CAVE, parser.parse(
                "GPS: You are at tile 3044, 899 in a cave", bounds)
                .getCoordinate().getLayer());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCoordinatesOutsideConfiguredMap() {
        parser.parse("4096 20", bounds);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnrelatedNumbers() {
        parser.parse("there are 12 players and 34 animals", bounds);
    }
}
