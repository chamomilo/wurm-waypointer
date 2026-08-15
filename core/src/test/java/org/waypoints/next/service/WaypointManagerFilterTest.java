package org.waypoints.next.service;

import org.junit.Before;
import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.time.Instant;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WaypointManagerFilterTest {
    private WaypointManager manager;
    private ServerIdentity novus;
    private ServerIdentity liberty;

    @Before public void setUp() {
        novus = TestWaypoints.server("Novus", 3726);
        liberty = TestWaypoints.server("Liberty", 3725);
        manager = new WaypointManager(new WaypointRecordValidator());
        manager.replaceAll(Arrays.asList(
                TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000001",
                        "Novus Harbour", "Chamomilo", novus, 10, 20),
                TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000002",
                        "Liberty Mine", "Chamomilo", liberty, 30, 40),
                TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000003",
                        "Other User", "Alt", novus, 50, 60)));
    }

    @Test public void currentServerAndUserAreEndpointFirst() {
        WaypointFilter filter = WaypointFilter.builder().currentServer(novus)
                .user("chamomilo").build();
        assertEquals(1, manager.filtered(filter).size());
        assertEquals("Novus Harbour", manager.filtered(filter).get(0).getName());
    }

    @Test public void allSpecificTextTypeAndStatusFiltersWork() {
        assertEquals(3, manager.filtered(WaypointFilter.builder().allServers().build()).size());
        assertEquals(1, manager.filtered(WaypointFilter.builder()
                .specificServer(liberty.getEndpointFingerprint()).build()).size());
        assertEquals(1, manager.filtered(WaypointFilter.builder().allServers()
                .text("mine").sourceType(WaypointSourceType.STATIC)
                .resolution(WaypointResolution.STATIC_EXACT).build()).size());
    }

    @Test public void staticCrudIsComplete() {
        WaypointRecord source = manager.snapshot().get(0);
        WaypointRecord duplicate = manager.duplicate(source.getId(),
                Instant.ofEpochMilli(1_700_000_001_000L));
        assertEquals(4, manager.snapshot().size());
        assertFalse(manager.setEnabled(duplicate.getId(), false,
                Instant.ofEpochMilli(1_700_000_002_000L)).isEnabled());
        assertTrue(manager.delete(duplicate.getId()));
        assertEquals(3, manager.snapshot().size());
    }
}
