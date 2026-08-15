package org.waypoints.next.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WaypointStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void saveLoadAndRollingBackupRecovery() throws Exception {
        Path path = temporary.newFolder("store").toPath().resolve("waypoints.wpt");
        WaypointStore store = store(path);
        store.save(document("First", "00000000-0000-0000-0000-000000000001"));
        store.save(document("Second", "00000000-0000-0000-0000-000000000002"));
        assertTrue(Files.isRegularFile(store.backupFile()));

        Files.write(path, "corrupt".getBytes(StandardCharsets.UTF_8));
        WaypointDocument recovered = store.load();
        assertTrue(store.wasRecoveredFromBackup());
        assertEquals("First", recovered.getRecords().get(0).getName());

        store.save(recovered);
        assertFalse(store.wasRecoveredFromBackup());
        assertEquals("First", store.load().getRecords().get(0).getName());
    }

    @Test public void atomicMoveFallbackIsExercisedAndVerified() throws Exception {
        Path path = temporary.newFolder("fallback").toPath().resolve("waypoints.wpt");
        WaypointFormatCodec codec = new WaypointFormatCodec(new WaypointRecordValidator());
        WaypointStore store = new WaypointStore(path, codec, new WaypointStore.FileMover() {
            @Override public void move(Path source, Path target, boolean atomic) throws IOException {
                if (atomic) throw new AtomicMoveNotSupportedException(
                        source.toString(), target.toString(), "test fallback");
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        });

        store.save(document("Fallback", "00000000-0000-0000-0000-000000000003"));
        assertTrue(store.usedAtomicFallback());
        assertEquals("Fallback", store.load().getRecords().get(0).getName());
    }

    @Test(expected = IOException.class)
    public void primaryAndBackupCorruptionAreReported() throws Exception {
        Path path = temporary.newFolder("corrupt").toPath().resolve("waypoints.wpt");
        Files.write(path, "bad-main".getBytes(StandardCharsets.UTF_8));
        Files.write(path.resolveSibling("waypoints.wpt.bak"),
                "bad-backup".getBytes(StandardCharsets.UTF_8));
        store(path).load();
    }

    private WaypointStore store(Path path) {
        return new WaypointStore(path,
                new WaypointFormatCodec(new WaypointRecordValidator()));
    }

    private WaypointDocument document(String name, String id) {
        WaypointRecord record = TestWaypoints.staticRecord(id, name, "Chamomilo",
                TestWaypoints.server("Novus", 3726), 12, 34);
        return new WaypointDocument(Collections.singletonList(record),
                Collections.<OpaqueWaypointRecord>emptyList());
    }
}
