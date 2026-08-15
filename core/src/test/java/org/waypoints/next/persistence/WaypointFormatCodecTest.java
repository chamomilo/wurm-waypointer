package org.waypoints.next.persistence;

import org.junit.Test;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WaypointFormatCodecTest {
    private final WaypointFormatCodec codec =
            new WaypointFormatCodec(new WaypointRecordValidator());

    @Test public void roundTripKeepsIdentityCoordinatesAndStyle() throws Exception {
        WaypointRecord record = WaypointRecord.copyOf(TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000001", "Harbour", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 123.5, 456.25))
                .arrivalRadiusMetres(40)
                .expiresAt(Instant.ofEpochMilli(1_700_003_600_000L)).build();
        WaypointDocument loaded = codec.decode(codec.encode(new WaypointDocument(
                Collections.singletonList(record),
                Collections.<OpaqueWaypointRecord>emptyList())));

        assertEquals(1, loaded.getRecords().size());
        WaypointRecord restored = loaded.getRecords().get(0);
        assertEquals(record.getId(), restored.getId());
        assertEquals(record.getServerIdentity(), restored.getServerIdentity());
        assertEquals(record.getCoordinate(), restored.getCoordinate());
        assertEquals(record.getMarkerStyle(), restored.getMarkerStyle());
        assertEquals(40, restored.getArrivalRadiusMetres());
        assertEquals(record.getExpiresAt(), restored.getExpiresAt());
    }

    @Test public void futureRecordIsPreservedOpaqueInsteadOfSilentlyDropped() throws Exception {
        String future = WaypointFormatCodec.MAGIC + "\nBEGIN WAYPOINT\n"
                + "modelVersion=2\nid=00000000-0000-0000-0000-000000000099\n"
                + "future.payload=keep-me\nEND WAYPOINT\n";
        WaypointDocument loaded = codec.decode(future.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, loaded.getRecords().size());
        assertEquals(1, loaded.getOpaqueRecords().size());
        String saved = new String(codec.encode(loaded), StandardCharsets.UTF_8);
        assertTrue(saved.contains("future.payload=keep-me"));
    }

    @Test public void roundTripKeepsCustomizedColorAlphaSizeAndThickness()
            throws Exception {
        WaypointRecord base = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000011", "Styled", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 10.0, 20.0);
        MarkerStyle custom = new MarkerStyle(MarkerStyle.WorldStyle.PLUS,
                0.17f, 0.43f, 0.91f, 0.62f, 14.5f, 5.25f, true, true);
        WaypointRecord record = WaypointRecord.copyOf(base)
                .markerStyle(custom).build();

        WaypointDocument loaded = codec.decode(codec.encode(new WaypointDocument(
                Collections.singletonList(record),
                Collections.<OpaqueWaypointRecord>emptyList())));

        assertEquals(custom, loaded.getRecords().get(0).getMarkerStyle());
    }

    @Test public void legacyRecordWithoutArrivalFieldDefaultsToDisabled()
            throws Exception {
        WaypointRecord record = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000012", "Legacy", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 10.0, 20.0);
        String encoded = new String(codec.encode(new WaypointDocument(
                Collections.singletonList(record),
                Collections.<OpaqueWaypointRecord>emptyList())),
                StandardCharsets.UTF_8);
        String legacy = encoded.replace("arrivalRadiusMetres=0\n", "");
        legacy = legacy.replace("expiresAt=\n", "");
        WaypointDocument loaded = codec.decode(legacy.getBytes(StandardCharsets.UTF_8));
        assertEquals(0, loaded.getRecords().get(0).getArrivalRadiusMetres());
        assertEquals(null, loaded.getRecords().get(0).getExpiresAt());
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsMalformedSupportedRecord() throws Exception {
        String corrupt = WaypointFormatCodec.MAGIC + "\nBEGIN WAYPOINT\n"
                + "modelVersion=1\nid=not-a-uuid\nEND WAYPOINT\n";
        codec.decode(corrupt.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = java.io.IOException.class)
    public void rejectsFutureDocumentSchemaUntilMigrationExists() throws Exception {
        codec.decode("WURM-WAYPOINTER\t2\n".getBytes(StandardCharsets.UTF_8));
    }
}
