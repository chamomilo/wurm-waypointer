package org.waypoints.next.service;

import org.junit.Before;
import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointSourceType;

import java.util.Arrays;
import java.util.List;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WaypointManagerViewServiceTest {
    private final WaypointManagerViewService service = new WaypointManagerViewService();
    private ServerIdentity novus;
    private ServerIdentity liberty;
    private List<WaypointRecord> records;

    @Before public void setUp() {
        novus = TestWaypoints.server("Novus", 3726);
        liberty = TestWaypoints.server("Liberty", 3725);
        records = Arrays.asList(
                TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000003",
                        "Far", "Chamomilo", novus, 30, 40),
                TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000001",
                        "Near", "Chamomilo", novus, 11, 10),
                TestWaypoints.staticRecord("00000000-0000-0000-0000-000000000002",
                        "Other shard", "Alt", liberty, 10, 10));
    }

    @Test public void defaultsCanBeCurrentUserCurrentEndpointAndDistanceSorted() {
        WaypointManagerSnapshot snapshot = service.snapshot(records,
                WaypointManagerQuery.builder().currentServer(novus)
                        .user("chamomilo").originTiles(10, 10)
                        .sort(WaypointManagerQuery.SortColumn.DISTANCE, true).build());
        assertEquals(3, snapshot.getTotalCount());
        assertEquals(2, snapshot.getFilteredCount());
        assertEquals("Near", snapshot.getRows().get(0).getName());
        assertEquals(Integer.valueOf(4), snapshot.getRows().get(0).getDistanceMetres());
    }

    @Test public void allServerViewNeverComputesCrossServerDistance() {
        WaypointManagerSnapshot snapshot = service.snapshot(records,
                WaypointManagerQuery.builder().allServers().currentContext(novus)
                        .originTiles(10, 10).sort(
                                WaypointManagerQuery.SortColumn.NAME, true).build());
        WaypointManagerRow other = null;
        for (WaypointManagerRow row : snapshot.getRows()) {
            if ("Other shard".equals(row.getName())) other = row;
        }
        assertNull(other.getDistanceMetres());
    }

    @Test public void exposesDeterministicUserAndSpecificServerOptions() {
        WaypointManagerSnapshot snapshot = service.snapshot(records,
                WaypointManagerQuery.builder().allServers().build());
        assertEquals(2, snapshot.getUsers().size());
        assertEquals("Alt", snapshot.getUsers().get(0).getLabel());
        assertEquals(2, snapshot.getServers().size());
        assertEquals(liberty.getEndpointFingerprint().toLowerCase(),
                snapshot.getServers().get(0).getValue());
    }

    @Test public void duplicateNamesStayDistinctByStableId() {
        WaypointRecord duplicate = TestWaypoints.staticRecord(
                "40000000-0000-0000-0000-000000000004",
                "Near", "Chamomilo", novus, 12, 10);
        WaypointManagerSnapshot snapshot = service.snapshot(
                Arrays.asList(records.get(1), duplicate),
                WaypointManagerQuery.builder().allServers().build());
        assertEquals(2, snapshot.getRows().size());
        assertEquals("00000000", snapshot.getRows().get(0).getShortId());
        assertEquals("40000000", snapshot.getRows().get(1).getShortId());
        assertEquals("Near", snapshot.getRows().get(1).getName());
    }

    @Test public void exposesTemporaryExpiryToManagerRows() {
        Instant expiry = Instant.ofEpochMilli(1_700_003_600_000L);
        WaypointRecord temporary = WaypointRecord.copyOf(records.get(0))
                .expiresAt(expiry).build();
        WaypointManagerRow row = service.snapshot(
                Arrays.asList(temporary),
                WaypointManagerQuery.builder().allServers().build())
                .getRows().get(0);
        assertEquals(true, row.isTemporary());
        assertEquals(expiry, row.getExpiresAt());
    }

    @Test public void vanillaRowsStayFirstFixedOrderAndIgnoreUserFilter() {
        WaypointRecord white = systemRecord(
                "50000000-0000-0000-0000-000000000001", "Vanilla White Light",
                "WHITE_LIGHT", MarkerStyle.WorldStyle.WHITE_LIGHT);
        WaypointRecord black = systemRecord(
                "50000000-0000-0000-0000-000000000002", "Vanilla Black Light",
                "BLACK_LIGHT", MarkerStyle.WorldStyle.BLACK_LIGHT);
        WaypointRecord rift = systemRecord(
                "50000000-0000-0000-0000-000000000003", "Vanilla Rift",
                "RIFT", MarkerStyle.WorldStyle.RIFT);
        WaypointManagerSnapshot snapshot = service.snapshot(
                Arrays.asList(records.get(0), rift, black, white),
                WaypointManagerQuery.builder().currentServer(novus)
                        .user("Chamomilo")
                        .sort(WaypointManagerQuery.SortColumn.NAME, false).build());

        assertEquals(4, snapshot.getRows().size());
        assertEquals("Vanilla White Light", snapshot.getRows().get(0).getName());
        assertEquals("Vanilla Black Light", snapshot.getRows().get(1).getName());
        assertEquals("Vanilla Rift", snapshot.getRows().get(2).getName());
        assertEquals("Far", snapshot.getRows().get(3).getName());
        assertEquals(true, snapshot.getRows().get(0).isSystemManaged());
    }

    private WaypointRecord systemRecord(String id, String name, String key,
                                        MarkerStyle.WorldStyle worldStyle) {
        WaypointRecord base = TestWaypoints.staticRecord(id, name, "Wurm",
                novus, 10, 10);
        MarkerStyle style = new MarkerStyle(worldStyle, 1.0f, 1.0f, 1.0f,
                1.0f, 9.0f, 2.0f, true, true);
        return WaypointRecord.copyOf(base)
                .sourceType(WaypointSourceType.VANILLA_SYSTEM)
                .sourceKey(key).markerStyle(style).build();
    }
}
