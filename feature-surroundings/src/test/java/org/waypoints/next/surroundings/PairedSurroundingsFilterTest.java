package org.waypoints.next.surroundings;
import org.junit.Test;
import static org.junit.Assert.*;
public class PairedSurroundingsFilterTest {
    @Test public void bothFragmentsSearchAllFieldsWithExclusionPriority(){
        SurroundingEntry entry=SurroundingEntry.builder().kind(SurroundingKind.ITEM).wurmId(42).name("large tool").category("tools").material("iron").position(40,40,2).build();
        SurroundingsQuery included=SurroundingsQuery.builder().kind(SurroundingKind.ITEM).text("horse, IRON").excludedText("copper").build();
        SurroundingsQuery excluded=SurroundingsQuery.builder().kind(SurroundingKind.ITEM).text("horse, IRON").excludedText("TOOL").build();
        assertTrue(included.matches(entry,false));assertFalse(excluded.matches(entry,false));
    }
}
