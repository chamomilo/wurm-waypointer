package org.waypoints.next.service;

import org.junit.Test;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ServerIdentityRepairTest {
    private final ServerIdentityRepair repair = new ServerIdentityRepair();
    private final Instant created = Instant.parse("2026-08-05T00:00:00Z");
    private final Instant repairedAt = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    public void repairsMatchingTransferRecordWithoutChangingEndpointIdentity() {
        WaypointRecord record = record("Novus", "176.9.149.249", 3726,
                ServerIdentity.Resolution.UNRESOLVED_SERVER_TRANSFER);
        ServerIdentity confirmed = identity("Novus", "176.9.149.249", 3726,
                ServerIdentity.Resolution.RESOLVED);

        ServerIdentityRepair.RepairResult result = repair.repairUnresolvedTransfers(
                Collections.singletonList(record), confirmed, repairedAt);
        WaypointRecord changed = result.getRecords().get(0);

        assertEquals(1, result.getRepairedCount());
        assertEquals(record.getId(), changed.getId());
        assertEquals(record.getCoordinate(), changed.getCoordinate());
        assertEquals(ServerIdentity.Resolution.RESOLVED,
                changed.getServerIdentity().getResolution());
        assertTrue(changed.getServerIdentity().sameServer(confirmed));
        assertEquals(repairedAt, changed.getUpdatedAt());
        assertEquals(repairedAt, changed.getLastResolvedAt());
    }

    @Test
    public void refusesDifferentEndpointNameOrUnresolvedKind() {
        WaypointRecord otherEndpoint = record("Novus", "176.9.149.250", 3726,
                ServerIdentity.Resolution.UNRESOLVED_SERVER_TRANSFER);
        WaypointRecord otherName = record("Liberty", "176.9.149.249", 3726,
                ServerIdentity.Resolution.UNRESOLVED_SERVER_TRANSFER);
        WaypointRecord direct = record("Novus", "176.9.149.249", 3726,
                ServerIdentity.Resolution.UNRESOLVED_DIRECT_CONNECT);
        ServerIdentity confirmed = identity("Novus", "176.9.149.249", 3726,
                ServerIdentity.Resolution.RESOLVED);

        ServerIdentityRepair.RepairResult result = repair.repairUnresolvedTransfers(
                Arrays.asList(otherEndpoint, otherName, direct), confirmed, repairedAt);

        assertEquals(0, result.getRepairedCount());
        assertSame(otherEndpoint, result.getRecords().get(0));
        assertSame(otherName, result.getRecords().get(1));
        assertSame(direct, result.getRecords().get(2));
    }

    private WaypointRecord record(String name, String host, int port,
                                  ServerIdentity.Resolution resolution) {
        return WaypointRecord.builder().name("Target").createdByUser("Tester")
                .serverIdentity(identity(name, host, port, resolution))
                .coordinate(new WaypointCoordinate(100.0d, 200.0d, null,
                        WaypointLayer.SURFACE))
                .createdAt(created).updatedAt(created).lastResolvedAt(created).build();
    }

    private static ServerIdentity identity(String name, String host, int port,
                                           ServerIdentity.Resolution resolution) {
        return ServerIdentity.of(ServerEndpoint.direct(host, port), "", name, resolution);
    }
}
