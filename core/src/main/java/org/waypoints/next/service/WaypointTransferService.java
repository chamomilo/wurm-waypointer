package org.waypoints.next.service;

import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.persistence.OpaqueWaypointRecord;
import org.waypoints.next.persistence.WaypointDocument;
import org.waypoints.next.persistence.WaypointStore;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicit import/export merge policy for the versioned native format. */
public final class WaypointTransferService {
    public static final class ImportResult {
        private final int imported;
        private final int skippedExisting;
        private final int preservedOpaque;
        private final List<OpaqueWaypointRecord> opaqueRecords;

        private ImportResult(int imported, int skippedExisting,
                             List<OpaqueWaypointRecord> opaqueRecords) {
            this.imported = imported;
            this.skippedExisting = skippedExisting;
            this.opaqueRecords = java.util.Collections.unmodifiableList(
                    new ArrayList<OpaqueWaypointRecord>(opaqueRecords));
            this.preservedOpaque = opaqueRecords.size();
        }

        public int getImported() { return imported; }
        public int getSkippedExisting() { return skippedExisting; }
        public int getPreservedOpaque() { return preservedOpaque; }
        public List<OpaqueWaypointRecord> getOpaqueRecords() { return opaqueRecords; }
    }

    public void exportTo(WaypointStore destination, List<WaypointRecord> records)
            throws IOException {
        destination.save(new WaypointDocument(records,
                java.util.Collections.<OpaqueWaypointRecord>emptyList()));
    }

    public ImportResult importFrom(WaypointStore source, WaypointManager target,
                                   Instant importedAt) throws IOException {
        WaypointDocument incoming = source.load();
        List<WaypointRecord> current = target.snapshot();
        Set<UUID> ids = new HashSet<UUID>();
        for (WaypointRecord record : current) ids.add(record.getId());
        List<WaypointRecord> merged = new ArrayList<WaypointRecord>(current);
        int imported = 0;
        int skipped = 0;
        for (WaypointRecord record : incoming.getRecords()) {
            if (!ids.add(record.getId())) {
                skipped++;
                continue;
            }
            // Retain original ownership and creation time; only the update timestamp records import.
            merged.add(WaypointRecord.copyOf(record).updatedAt(importedAt).build());
            imported++;
        }
        target.replaceAll(merged);
        return new ImportResult(imported, skipped, incoming.getOpaqueRecords());
    }
}
