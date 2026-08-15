package org.waypoints.next.model;

import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class WaypointRecordTest {
    @Test
    public void immutableCopyKeepsStableIdAndDuplicateGetsNewId() {
        WaypointRecord original = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000001", "Harbour", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 123.5, 456.25);
        Instant later = original.getUpdatedAt().plusSeconds(1);
        WaypointRecord disabled = original.withEnabled(false, later);
        WaypointRecord duplicate = original.duplicate("Harbour Copy", later);

        assertEquals(original.getId(), disabled.getId());
        assertFalse(disabled.isEnabled());
        assertNotEquals(original.getId(), duplicate.getId());
        assertEquals("Harbour Copy", duplicate.getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void staticRecordCannotEncodeUnknownCoordinateAsMissing() {
        WaypointRecord invalid = WaypointRecord.builder().name("Missing")
                .createdByUser("Chamomilo").serverIdentity(TestWaypoints.server("Novus", 3726))
                .sourceType(WaypointSourceType.STATIC).coordinate(null)
                .resolution(WaypointResolution.STATIC_EXACT).build();
        new WaypointRecordValidator().validate(invalid);
    }

    @Test(expected = IllegalArgumentException.class)
    public void lootMapScrollCannotBeAssignedToAnOrdinaryWaypoint() {
        WaypointRecord ordinary = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000002", "Ordinary", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 123.5, 456.25);
        WaypointRecord invalid = WaypointRecord.copyOf(ordinary).markerStyle(
                new MarkerStyle(MarkerStyle.WorldStyle.LOOT_MAP_SCROLL,
                        1.0f, 0.6f, 0.1f, 1.0f, 11.0f, 2.0f,
                        true, true)).build();
        new WaypointRecordValidator().validate(invalid);
    }

    @Test public void lootMapSourceAcceptsOnlyItsReservedScroll() {
        WaypointRecord ordinary = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000003", "Loot", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 123.5, 456.25);
        WaypointRecord loot = WaypointRecord.copyOf(ordinary)
                .sourceType(WaypointSourceType.LOOT_MAP).sourceKey("hunt")
                .markerStyle(new MarkerStyle(MarkerStyle.WorldStyle.LOOT_MAP_SCROLL,
                        1.0f, 0.6f, 0.1f, 1.0f, 11.0f, 2.0f,
                        true, true)).build();
        new WaypointRecordValidator().validate(loot);
    }

    @Test(expected = IllegalArgumentException.class)
    public void surroundingsExclamationCannotBeAssignedToOrdinaryWaypoint() {
        WaypointRecord ordinary = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000004", "Ordinary",
                "Chamomilo", TestWaypoints.server("Novus", 3726),
                123.5, 456.25);
        WaypointRecord invalid = WaypointRecord.copyOf(ordinary).markerStyle(
                new MarkerStyle(MarkerStyle.WorldStyle.EXCLAMATION,
                        1.0f, 0.6f, 0.1f, 1.0f, 11.0f, 2.0f,
                        true, true)).build();
        new WaypointRecordValidator().validate(invalid);
    }
}
