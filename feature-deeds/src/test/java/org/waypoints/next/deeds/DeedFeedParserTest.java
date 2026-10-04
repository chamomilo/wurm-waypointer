package org.waypoints.next.deeds;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class DeedFeedParserTest {
    @Test public void parsesOnlyExpectedSklotopolisWrapperAsJson() {
        String source = "var deeds; deeds = [{\"name\":\"Caza \\u2605\","
                + "\"tag\":\"caza-42\",\"x\":12,\"y\":34,"
                + "\"tilesNorth\":1,\"mayor\":\"A\"}];";
        List<DeedRecord> records = DeedFeedParser.parse(source,
                DeedFeedFormat.SKLOTOPOLIS_JSON, 100, 100);
        assertEquals(1, records.size());
        assertEquals("Caza ★", records.get(0).getName());
        assertEquals("caza-42", records.get(0).getStableKey());
        assertEquals("A", records.get(0).metadata("mayor"));

        rejected("alert(1); deeds=[{\"name\":\"Bad\",\"x\":1,\"y\":1}]",
                DeedFeedFormat.SKLOTOPOLIS_JSON);
    }

    @Test public void parsesGenericRootObjectAndStableId() {
        List<DeedRecord> records = DeedFeedParser.parse(
                "{\"deeds\":[{\"id\":42,\"name\":\"Haven\","
                        + "\"tile_x\":7,\"tile-y\":9,\"alliance\":\"North\"}]}",
                DeedFeedFormat.JSON, 20, 20);
        assertEquals("42", records.get(0).getStableKey());
        assertEquals(7, records.get(0).getTileX());
        assertEquals("North", records.get(0).metadata("alliance"));
    }

    @Test public void parsesQuotedCsvAndRejectsBadCoordinatesOrEmptyCatalog() {
        List<DeedRecord> records = DeedFeedParser.parse(
                "id,name,x,y,mayor\r\nalpha,\"New, Haven\",3,4,\"A \"\"Mayor\"\"\"\r\n",
                DeedFeedFormat.CSV, 10, 10);
        assertEquals("New, Haven", records.get(0).getName());
        assertEquals("A \"Mayor\"", records.get(0).metadata("mayor"));

        rejected("name,x,y\nOutside,10,1", DeedFeedFormat.CSV);
        rejected("[]", DeedFeedFormat.JSON);
    }

    @Test public void duplicateStableKeysAreRejected() {
        try {
            DeedFeedParser.parse("[{\"name\":\"One\",\"id\":\"x\",\"x\":1,\"y\":1},"
                    + "{\"name\":\"Two\",\"id\":\"X\",\"x\":2,\"y\":2}]",
                    DeedFeedFormat.JSON, 10, 10);
            fail("duplicate key accepted");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("duplicate"));
        }
    }

    private static void rejected(String source, DeedFeedFormat format) {
        try {
            DeedFeedParser.parse(source, format, 10, 10);
            fail("invalid deed feed accepted");
        } catch (IllegalArgumentException expected) { }
    }
}
