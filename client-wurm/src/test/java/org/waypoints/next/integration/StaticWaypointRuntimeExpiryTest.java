package org.waypoints.next.integration;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.persistence.OpaqueWaypointRecord;
import org.waypoints.next.persistence.WaypointDocument;
import org.waypoints.next.persistence.WaypointFormatCodec;
import org.waypoints.next.persistence.WaypointStore;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.Properties;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public final class StaticWaypointRuntimeExpiryTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void expiryImmediatelyChangesRevisionAndQueuesDurableRemoval()
            throws Exception {
        Path directory = temporary.newFolder("expiry").toPath();
        Path data = directory.resolve("waypoints.wpt");
        long created = 1_700_000_000_000L;
        long expiry = created + 60_000L;
        WaypointRecord record = WaypointRecord.builder().name("Temporary")
                .createdByUser("Tester").serverIdentity(ServerIdentity.of(
                        new ServerEndpoint("example.test", 3724, 27016),
                        "Example", "Example", ServerIdentity.Resolution.RESOLVED))
                .coordinate(new WaypointCoordinate(10, 20, null,
                        WaypointLayer.SURFACE))
                .createdAt(Instant.ofEpochMilli(created))
                .updatedAt(Instant.ofEpochMilli(created))
                .lastResolvedAt(Instant.ofEpochMilli(created))
                .expiresAt(Instant.ofEpochMilli(expiry)).build();
        WaypointFormatCodec codec = new WaypointFormatCodec(
                new WaypointRecordValidator());
        WaypointStore store = new WaypointStore(data, codec);
        store.save(new WaypointDocument(Collections.singletonList(record),
                Collections.<OpaqueWaypointRecord>emptyList()));

        Properties properties = new Properties();
        properties.setProperty("waypointDataFile", data.toString());
        properties.setProperty("waypointTransferFile",
                directory.resolve("transfer.wpt").toString());
        properties.setProperty("vanillaLandmarkStateFile",
                directory.resolve("vanilla.state").toString());
        StaticWaypointRuntime runtime = new StaticWaypointRuntime(
                Logger.getAnonymousLogger());
        runtime.configureAndLoad(WaypointClientConfiguration.from(properties));

        runtime.expireDue(expiry - 1L);
        assertEquals(1, runtime.recordCount());
        long revision = runtime.revision();
        runtime.expireDue(expiry);
        assertEquals(0, runtime.recordCount());
        assertEquals(revision + 1L, runtime.revision());
        assertEquals(0, runtime.revisionSnapshot().getRecords().size());

        long deadline = System.currentTimeMillis() + 2_000L;
        while (System.currentTimeMillis() < deadline
                && store.load().getRecords().size() != 0) {
            Thread.sleep(10L);
        }
        assertEquals(0, store.load().getRecords().size());
    }
}
