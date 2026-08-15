package org.waypoints.next.lootmap;

import org.junit.Test;

import static org.junit.Assert.*;

public class LootMapMessageParserTest {
    private final LootMapMessageParser parser = new LootMapMessageParser();

    @Test public void parsesOverlappingDistancePhrasesInSpecificOrder() {
        assertReading("The marked spot is quite some distance away in front of you.",
                LootMapDistanceBand.FIFTY_TO_ONE_NINETY_NINE,
                LootMapRelativeDirection.AHEAD);
        assertReading("The marked spot is some distance away behind you to the left.",
                LootMapDistanceBand.TWENTY_TO_FORTY_NINE,
                LootMapRelativeDirection.BEHIND_LEFT);
        assertReading("The marked spot is pretty far away ahead of you to the right.",
                LootMapDistanceBand.FIVE_HUNDRED_TO_NINE_NINETY_NINE,
                LootMapRelativeDirection.AHEAD_RIGHT);
        assertReading("The marked spot is very far away right of you.",
                LootMapDistanceBand.TWO_THOUSAND_PLUS,
                LootMapRelativeDirection.RIGHT);
    }

    @Test public void ignoresNumericDiagnosticWithoutDirection() {
        assertNull(parser.parse(":Event",
                "The marked spot is between 50-199 tiles away."));
        assertNull(parser.parse("GL-Freedom",
                "The marked spot is quite some distance away in front of you."));
    }

    @Test public void recognizesTerminalEvents() {
        LootMapMessage exact = parser.parse(":Event",
                "You are practically standing on the marked spot!");
        assertNotNull(exact);
        assertEquals(LootMapDistanceBand.EXACT, exact.getBand());
        assertChestFound("You find a loot chest!");
        assertChestFound("You find a treasure chest!");
        assertChestFound("You dig up a loot chest!");
        assertChestFound("You dig up a treasure chest!");
        assertNull(parser.parse(":Event", "Wolfbane digs up a loot chest!"));
    }

    @Test public void distanceBandsExposeRangesWithoutFalsePrecision() {
        assertEquals("0 tiles", LootMapDistanceBand.EXACT.displayRangeTiles());
        assertEquals("1-3 tiles",
                LootMapDistanceBand.ONE_TO_THREE.displayRangeTiles());
        assertEquals("500-999 tiles",
                LootMapDistanceBand.FIVE_HUNDRED_TO_NINE_NINETY_NINE
                        .displayRangeTiles());
        assertEquals("2000+ tiles",
                LootMapDistanceBand.TWO_THOUSAND_PLUS.displayRangeTiles());
    }

    private void assertChestFound(String text) {
        LootMapMessage parsed = parser.parse(":Event", text);
        assertNotNull(parsed);
        assertEquals(LootMapMessage.Kind.CHEST_DUG_UP, parsed.getKind());
    }

    private void assertReading(String text, LootMapDistanceBand band,
                               LootMapRelativeDirection direction) {
        LootMapMessage parsed = parser.parse(":Event", text);
        assertNotNull(parsed);
        assertEquals(LootMapMessage.Kind.READING, parsed.getKind());
        assertEquals(band, parsed.getBand());
        assertEquals(direction, parsed.getDirection());
    }
}
