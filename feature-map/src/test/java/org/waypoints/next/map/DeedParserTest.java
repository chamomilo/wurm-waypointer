package org.waypoints.next.map;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DeedParserTest {
    @Test public void parsesJavascriptWrapperAndUnicodeEscapes() {
        String source = "var deeds; deeds =["
                + "{\"name\":\"Caza \\u2605\",\"tilesNorth\":11,"
                + "\"mayor\":\"Chamnomilo\","
                + "\"allianceName\":\"Havenly Alliance\","
                + "\"founderName\":\"Balrog\",\"motto\":\"Too deep\","
                + "\"lastActive\":\"Last active: 0 days ago.\","
                + "\"guards\":2,\"amountOfCitizens\":7,"
                + "\"creationDate\":1763571600000,"
                + "\"tilesSouth\":12,\"tilesEast\":13,\"tilesWest\":14,"
                + "\"tilesPerimeter\":5,\"isSpawnPoint\":true,"
                + "\"type\":\"small\",\"x\":317,\"y\":457}]";
        List<Deed> deeds = DeedParser.parse(source, 2048, 2048);
        assertEquals(1, deeds.size());
        Deed deed = deeds.get(0);
        assertEquals("Caza ★", deed.getName());
        assertEquals("Chamnomilo", deed.getMayor());
        assertEquals("Havenly Alliance", deed.getAllianceName());
        assertEquals("Balrog", deed.getFounderName());
        assertEquals("Too deep", deed.getMotto());
        assertEquals("Last active: 0 days ago.", deed.getLastActive());
        assertEquals(2, deed.getGuards());
        assertEquals(7, deed.getCitizens());
        assertEquals(1763571600000L, deed.getCreationDate());
        assertEquals(303, deed.getMinimumX());
        assertEquals(330, deed.getMaximumX());
        assertEquals(446, deed.getMinimumY());
        assertEquals(469, deed.getMaximumY());
        assertEquals(298, deed.getPerimeterMinimumX());
        assertTrue(deed.isSpawnPoint());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCoordinatesOutsideProfile() {
        DeedParser.parse("[{\"name\":\"Bad\",\"tilesNorth\":1,"
                + "\"tilesSouth\":1,\"tilesEast\":1,\"tilesWest\":1,"
                + "\"x\":2048,\"y\":1}]", 2048, 2048);
    }
}
