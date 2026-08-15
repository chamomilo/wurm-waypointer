package org.waypoints.next.service;

import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointRecord;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WaypointShareCodecTest {
    private final WaypointShareCodec codec = new WaypointShareCodec();

    @Test public void roundTripKeepsServerCoordinatesAndCompleteStyle() {
        WaypointRecord base = TestWaypoints.staticRecord(
                "10000000-0000-0000-0000-000000000001", "Storm eye",
                "Tester", TestWaypoints.server("Novus", 3726), 12.5d, 25.25d);
        MarkerStyle style = new MarkerStyle(MarkerStyle.WorldStyle.CIRCLE_BEAM,
                0.1f, 0.2f, 0.3f, 0.4f, 27.5f, 6.25f, false, true);
        WaypointRecord record = WaypointRecord.copyOf(base).markerStyle(style)
                .arrivalRadiusMetres(36)
                .expiresAt(Instant.ofEpochMilli(1_700_003_600_000L)).build();

        WaypointShareCodec.SharedWaypoint decoded = codec.decode(codec.encode(record));

        assertEquals(record.getServerIdentity().getEndpointFingerprint(),
                decoded.getServerIdentity().getEndpointFingerprint());
        assertEquals(record.getServerIdentity().getShortName(),
                decoded.getServerIdentity().getShortName());
        assertEquals(record.getServerIdentity().getFullName(),
                decoded.getServerIdentity().getFullName());
        assertEquals(record.getServerIdentity().getEndpoint().getQueryPort(),
                decoded.getServerIdentity().getEndpoint().getQueryPort());
        assertEquals(record.getName(), decoded.getName());
        assertEquals(record.getCoordinate(), decoded.getCoordinate());
        assertEquals(style, decoded.getMarkerStyle());
        assertEquals(36, decoded.getArrivalRadiusMetres());
        assertEquals(record.getExpiresAt(), decoded.getExpiresAt());
    }

    @Test public void eventPrefixAndTimestampMayBeCopiedWithTheToken() {
        WaypointRecord record = TestWaypoints.staticRecord(
                "10000000-0000-0000-0000-000000000002", "Home with spaces",
                "Tester", TestWaypoints.server("Novus", 3726), 1.0d, 2.0d);
        String token = codec.encode(record);
        WaypointShareCodec.SharedWaypoint decoded = codec.decode(
                "[20:00:00] [Wurm Waypointer] Share copied: " + token + "\r\n");
        assertEquals("Home with spaces", decoded.getName());
        assertTrue(token.startsWith(WaypointShareCodec.PREFIX));
        assertTrue(WaypointShareCodec.containsSharedToken(
                "[20:00:00] Share copied: " + token));
        assertTrue(token.startsWith("WWP1|C|"));
        assertTrue(("[20:00:00] [Wurm Waypointer] Share: " + token)
                .length() <= 200);
    }

    @Test(expected = IllegalArgumentException.class)
    public void malformedTokenIsRejected() {
        codec.decode("WWP1|too|short");
    }

    @Test public void decodesCapturedTimedTokenFromEventChat() {
        String token = "[19:34:46] [Wurm Waypointer] Share copied: "
                + "WWP1|MTc2LjkuMTQ5LjI0OQ|3726|||Tm92dXM|dGltZWQ|"
                + "3017.26416015625|913.4608764648438|0.7000000476837158|"
                + "SURFACE|COLORED_BEAM|1.0|0.2|0.2|0.85|9.0|2.0|1|1|0|"
                + "1786207144463";
        WaypointShareCodec.SharedWaypoint decoded = codec.decode(token);
        assertEquals("timed", decoded.getName());
        assertEquals(Instant.ofEpochMilli(1_786_207_144_463L),
                decoded.getExpiresAt());
        assertEquals("176.9.149.249",
                decoded.getServerIdentity().getEndpoint().getHost());
    }

    @Test public void olderTokensDefaultMissingLifetimeAndArrival() {
        String token = "WWP1|MTc2LjkuMTQ5LjI0OQ|3726|||Tm92dXM|TGVnYWN5|"
                + "1.0|2.0||SURFACE|COLORED_BEAM|1.0|0.2|0.2|0.85|9.0|"
                + "2.0|1|1|0|0";
        String withoutLifetime = token.substring(0, token.lastIndexOf('|'));
        assertEquals(null, codec.decode(withoutLifetime).getExpiresAt());
        String legacy = withoutLifetime.substring(0,
                withoutLifetime.lastIndexOf('|'));
        assertEquals(0, codec.decode(legacy).getArrivalRadiusMetres());
        assertEquals(null, codec.decode(legacy).getExpiresAt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void truncatedCompactTokenIsRejected() {
        WaypointRecord record = TestWaypoints.staticRecord(
                "10000000-0000-0000-0000-000000000003", "Truncated",
                "Tester", TestWaypoints.server("Novus", 3726), 1.0d, 2.0d);
        String token = codec.encode(record);
        codec.decode(token.substring(0, token.length() - 12));
    }
}
