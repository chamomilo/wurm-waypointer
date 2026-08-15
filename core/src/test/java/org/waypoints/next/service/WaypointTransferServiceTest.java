package org.waypoints.next.service;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.waypoints.next.TestWaypoints;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.persistence.WaypointFormatCodec;
import org.waypoints.next.persistence.WaypointStore;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class WaypointTransferServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void exportAndImportSkipExistingStableIds() throws Exception {
        WaypointRecord first = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000001", "First", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 1, 2);
        WaypointRecord second = TestWaypoints.staticRecord(
                "00000000-0000-0000-0000-000000000002", "Second", "Chamomilo",
                TestWaypoints.server("Novus", 3726), 3, 4);
        Path path = temporary.newFolder("transfer").toPath().resolve("export.wpt");
        WaypointStore file = new WaypointStore(path,
                new WaypointFormatCodec(new WaypointRecordValidator()));
        WaypointTransferService transfer = new WaypointTransferService();
        transfer.exportTo(file, Arrays.asList(first, second));

        WaypointManager target = new WaypointManager(new WaypointRecordValidator());
        target.add(first);
        WaypointTransferService.ImportResult result = transfer.importFrom(file, target,
                Instant.ofEpochMilli(1_700_000_100_000L));
        assertEquals(1, result.getImported());
        assertEquals(1, result.getSkippedExisting());
        assertEquals(2, target.snapshot().size());
    }
}
