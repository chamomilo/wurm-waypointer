package org.waypoints.next;

import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;

import java.time.Instant;
import java.util.UUID;

public final class TestWaypoints {
    private TestWaypoints() { }

    public static ServerIdentity server(String name, int port) {
        return ServerIdentity.of(new ServerEndpoint("176.9.149.249", port, 27016),
                "Sklotopolis - " + name, name, ServerIdentity.Resolution.RESOLVED);
    }

    public static WaypointRecord staticRecord(String id, String name, String user,
                                              ServerIdentity server, double x, double y) {
        Instant now = Instant.ofEpochMilli(1_700_000_000_000L);
        return WaypointRecord.builder().id(UUID.fromString(id)).name(name)
                .description("test waypoint").createdByUser(user).serverIdentity(server)
                .sourceType(WaypointSourceType.STATIC).sourceKey("")
                .coordinate(new WaypointCoordinate(x, y, null, WaypointLayer.SURFACE))
                .resolution(WaypointResolution.STATIC_EXACT).createdAt(now)
                .updatedAt(now).lastResolvedAt(now).build();
    }
}
