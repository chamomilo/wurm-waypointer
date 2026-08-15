package org.waypoints.next.service;

import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WaypointManagerExpiryTest {
    @Test public void removesAllDueRecordsInOneRevisionAndKeepsPermanent() {
        long now = 1_700_000_100_000L;
        WaypointRecord due = temporary(
                "60000000-0000-0000-0000-000000000001", now);
        WaypointRecord later = temporary(
                "60000000-0000-0000-0000-000000000002", now + 1_000L);
        WaypointRecord permanent = TestWaypoints.staticRecord(
                "60000000-0000-0000-0000-000000000003", "Permanent", "Tester",
                TestWaypoints.server("Novus", 3726), 1, 1);
        WaypointManager manager = new WaypointManager(new WaypointRecordValidator());
        manager.replaceAll(Arrays.asList(due, later, permanent));
        long before = manager.revision();

        List<WaypointRecord> expired = manager.removeExpired(now);

        assertEquals(1, expired.size());
        assertNull(manager.find(due.getId()));
        assertEquals(before + 1L, manager.revision());
        assertEquals(now + 1_000L, manager.nextExpiryEpochMilli());
        assertEquals(2, manager.snapshot().size());
    }

    private static WaypointRecord temporary(String id, long expiresAt) {
        WaypointRecord base = TestWaypoints.staticRecord(id, "Temporary", "Tester",
                TestWaypoints.server("Novus", 3726), 1, 1);
        return WaypointRecord.copyOf(base)
                .expiresAt(Instant.ofEpochMilli(expiresAt)).build();
    }
}
