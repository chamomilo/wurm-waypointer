package org.waypoints.next.archaeology;

import org.junit.Test;

import static org.junit.Assert.*;

public class ArchaeologyMessageParserTest {
    private final ArchaeologyMessageParser parser = new ArchaeologyMessageParser();

    @Test public void parsesReadyDirectionAndCacheFoundPhrases() {
        ArchaeologyMessage ready = parser.parse(":Event",
                "You feel confident you know exactly where Old Haven once lay, and complete the location details in the report.");
        assertNotNull(ready);
        assertEquals(ArchaeologyMessage.Kind.REPORT_READY, ready.getKind());
        assertEquals("Old Haven", ready.getDeedName());

        ArchaeologyMessage direction = parser.parse("Event",
                "Reading details from the report, Old Haven looks like it may have been quite distant to the north-east.");
        assertNotNull(direction);
        assertEquals(ArchaeologyMessage.Kind.DIRECTION, direction.getKind());
        assertEquals(ArchaeologyDistanceBand.QUITE_DISTANT,
                direction.getDistanceBand());
        assertEquals(ArchaeologyDirection.NORTH_EAST,
                direction.getDirection());

        ArchaeologyMessage cache = parser.parse(":Event",
                "As you discover an Old Haven hidden cache the report is crumpled up and ruined.");
        assertNotNull(cache);
        assertEquals(ArchaeologyMessage.Kind.CACHE_FOUND, cache.getKind());
    }

    @Test public void acceptsPunctuationAndAllEightDirections() {
        String[] phrases = {"north", "north east", "east", "south-east",
                "south", "south west", "west", "north-west"};
        for (String phrase : phrases) {
            ArchaeologyMessage parsed = parser.parse(":Event",
                    "Reading details from the report, A looks like it may have been very close to the "
                            + phrase + "!");
            assertNotNull(phrase, parsed);
        }
    }

    @Test public void acceptsLiveTimestampAndJoinedDiagonalDirection() {
        ArchaeologyMessage direction = parser.parse(":Event",
                "[21:01:13] Reading details from the report, Haven looks like "
                        + "it may have been nearby to the northeast.");

        assertNotNull(direction);
        assertEquals("Haven", direction.getDeedName());
        assertEquals(ArchaeologyDistanceBand.NEARBY,
                direction.getDistanceBand());
        assertEquals(ArchaeologyDirection.NORTH_EAST,
                direction.getDirection());
    }

    @Test public void parsesReadySentenceInsideLiveInvestigateResult() {
        ArchaeologyMessage ready = parser.parse(":Event",
                "You can see signs of a single abandoned settlement here. "
                        + "Based on your knowledge of the area and small hints you can find, "
                        + "the settlement must have been called Haven. "
                        + "You feel confident you know exactly where Haven once lay, "
                        + "and complete the location details in the report. "
                        + "You find a scrap of washed out parchment signed by the last mayor, Enim. "
                        + "You write that down in your report. "
                        + "You recall this settlement, and remember the name of the founder as Enim.");

        assertNotNull(ready);
        assertEquals(ArchaeologyMessage.Kind.REPORT_READY, ready.getKind());
        assertEquals("Haven", ready.getDeedName());
    }

    @Test public void ignoresInvestigateHintsWrongTabsAndMalformedText() {
        assertNull(parser.parse(":Event",
                "You spot some markers in the ground. It looks like it may be far to the north."));
        assertNull(parser.parse(":Combat",
                "Reading details from the report, A looks like it may have been far to the north."));
        assertNull(parser.parse(":Event",
                "Reading details from the report, A looks like it may have been somewhere to the north."));
        assertNull(parser.parse(":Event",
                "Someone says You feel confident you know exactly where A once lay, "
                        + "and complete the location details in the report."));
    }
}
