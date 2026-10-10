package org.waypoints.next.service;
import org.junit.Test;
import static org.junit.Assert.*;
public class TextFilterTest {
    @Test public void managerQueryUsesBothFieldsAcrossStoredRecordMetadata(){
        org.waypoints.next.model.WaypointRecord record=org.waypoints.next.TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000001","Harbour","Alice",org.waypoints.next.TestWaypoints.server("Novus",3726),10,20);
        record=org.waypoints.next.model.WaypointRecord.copyOf(record).description("Iron tools, safe dock").build();
        assertTrue(WaypointFilter.builder().allServers().text("horse, IRON").excludedText("danger").build().matches(record));
        assertFalse(WaypointFilter.builder().allServers().text("IRON").excludedText("safe, wolves").build().matches(record));
    }
    @Test public void includesAnyCommaSeparatedLiteralAndExcludesAnyMinusTerm(){TextFilter filter=new TextFilter(" horse, WOLF, ,horse ","old,champion");assertTrue(filter.matches("young HORSE"));assertTrue(filter.matches("black wolf"));assertFalse(filter.matches("old horse"));assertFalse(filter.matches("champion wolf"));assertFalse(filter.matches("bear"));}
    @Test public void blanksMinusOnlyUnicodeAndRegexCharactersAreLiteral(){assertTrue(new TextFilter(" , ","").matches(null));assertFalse(new TextFilter(""," iron , кузнец ").matches("КУЗНЕЦ у ворот"));assertTrue(new TextFilter("a.b","").matches("xx a.b yy"));assertFalse(new TextFilter("a.b","").matches("axb"));}
}
