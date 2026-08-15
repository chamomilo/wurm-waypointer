package org.waypoints.next.archaeology;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.*;

public class ArchaeologyPersistenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void sessionsAndKnownLocationsRoundTripUnicodeAndExactState()
            throws Exception {
        Instant created = Instant.parse("2026-08-10T10:00:00Z");
        ArchaeologyReportSession session = ArchaeologyReportSession.create(
                "host:3724:27016", "Tester", "Ancient Hávën",
                100.5d, 200.5d, WaypointLayer.CAVE, created)
                .transition(ArchaeologyReportStatus.VERY_CLOSE,
                        101.5d, 201.5d, WaypointLayer.CAVE,
                        new WaypointCoordinate(106.5d, 196.5d, null,
                                WaypointLayer.CAVE),
                        ArchaeologyDistanceBand.VERY_CLOSE,
                        ArchaeologyDirection.NORTH_EAST, 2,
                        "direction:event", created.plusSeconds(10), true)
                .withReportItemId(9988L, created.plusSeconds(11));
        Path sessionsFile = temporary.newFile("sessions.properties").toPath();
        ArchaeologySessionStore sessions = new ArchaeologySessionStore(sessionsFile);
        sessions.save(Collections.singletonList(session), 8);
        List<ArchaeologyReportSession> loaded = sessions.load();
        assertEquals(1, loaded.size());
        assertEquals(session.getId(), loaded.get(0).getId());
        assertEquals("Ancient Hávën", loaded.get(0).getDeedName());
        assertEquals(Long.valueOf(9988L), loaded.get(0).getReportItemId());
        assertEquals(ArchaeologyReportStatus.VERY_CLOSE, loaded.get(0).getStatus());
        assertEquals(2, loaded.get(0).getTerminalStep());

        KnownArchaeologyLocation known = new KnownArchaeologyLocation(
                "host:3724:27016", "Ancient Hávën", 500.5d, 600.5d,
                WaypointLayer.SURFACE, created, created.plusSeconds(20),
                session.getId(), false);
        Path knownFile = temporary.newFile("known.properties").toPath();
        KnownArchaeologyLocationStore locations =
                new KnownArchaeologyLocationStore(knownFile);
        locations.save(Collections.singletonList(known));
        KnownArchaeologyLocation restored = locations.load().get(0);
        assertEquals(500.5d, restored.getTileX(), 0.0d);
        assertEquals(600.5d, restored.getTileY(), 0.0d);
        assertEquals(session.getId(), restored.getSourceSessionId());
    }

    @Test public void checksumFailureRecoversLastKnownGoodBackup() throws Exception {
        Path file = temporary.newFile("known-recovery.properties").toPath();
        Files.delete(file);
        KnownArchaeologyLocationStore store = new KnownArchaeologyLocationStore(file);
        Instant at = Instant.parse("2026-08-10T10:00:00Z");
        KnownArchaeologyLocation first = new KnownArchaeologyLocation(
                "server", "First", 10, 20, WaypointLayer.SURFACE,
                at, at, null, false);
        store.save(Collections.singletonList(first));
        KnownArchaeologyLocation second = new KnownArchaeologyLocation(
                "server", "Second", 30, 40, WaypointLayer.SURFACE,
                at, at, null, false);
        store.save(Collections.singletonList(second));
        Files.write(file, "truncated".getBytes(StandardCharsets.UTF_8));

        List<KnownArchaeologyLocation> recovered = store.load();
        assertTrue(store.wasRecoveredFromBackup());
        assertEquals(1, recovered.size());
        assertEquals("First", recovered.get(0).getDeedName());
    }

    @Test public void v1SessionIndexesLoadAsCentresAndSaveAsV2()
            throws Exception {
        Instant at = Instant.parse("2026-08-12T18:14:04Z");
        ArchaeologyReportSession legacy = ArchaeologyReportSession.create(
                "server", "Tester", "Haven", 3073.0d, 960.0d,
                WaypointLayer.SURFACE, at)
                .transition(ArchaeologyReportStatus.VERY_CLOSE,
                        3073.0d, 960.0d, WaypointLayer.SURFACE,
                        new WaypointCoordinate(3072.0d, 960.0d, null,
                                WaypointLayer.SURFACE),
                        ArchaeologyDistanceBand.VERY_CLOSE,
                        ArchaeologyDirection.WEST, 5, "legacy", at, false);
        Path file = temporary.newFile("legacy-sessions.properties").toPath();
        ArchaeologySessionStore store = new ArchaeologySessionStore(file);
        store.save(Collections.singletonList(legacy), 8);

        AtomicArchaeologyProperties atomic =
                new AtomicArchaeologyProperties(file);
        Properties values = atomic.load();
        values.setProperty("format.version", "1");
        atomic.save(values, "legacy archaeology session fixture");

        ArchaeologyReportSession migrated = store.load().get(0);
        assertEquals(3073.5d, migrated.getLastPlayerTileX(), 0.0d);
        assertEquals(960.5d, migrated.getLastPlayerTileY(), 0.0d);
        assertEquals(3072.5d, migrated.getWaypointTileX(), 0.0d);
        assertEquals(960.5d, migrated.getWaypointTileY(), 0.0d);

        store.save(Collections.singletonList(migrated), 8);
        assertEquals("2", atomic.load().getProperty("format.version"));

        Path knownFile = temporary.newFile("legacy-known.properties").toPath();
        KnownArchaeologyLocationStore knownStore =
                new KnownArchaeologyLocationStore(knownFile);
        knownStore.save(Collections.singletonList(new KnownArchaeologyLocation(
                "server", "Haven", 3068.0d, 961.0d,
                WaypointLayer.SURFACE, at, at, legacy.getId(), false)));
        AtomicArchaeologyProperties knownAtomic =
                new AtomicArchaeologyProperties(knownFile);
        Properties knownValues = knownAtomic.load();
        knownValues.setProperty("format.version", "1");
        knownAtomic.save(knownValues, "legacy known location fixture");

        KnownArchaeologyLocation migratedKnown = knownStore.load().get(0);
        assertEquals(3068.5d, migratedKnown.getTileX(), 0.0d);
        assertEquals(961.5d, migratedKnown.getTileY(), 0.0d);
    }
}
