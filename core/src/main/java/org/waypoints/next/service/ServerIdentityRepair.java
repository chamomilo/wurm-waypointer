package org.waypoints.next.service;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Safely promotes records saved before a transferred endpoint was confirmed. */
public final class ServerIdentityRepair {
    public RepairResult repairUnresolvedTransfers(
            List<WaypointRecord> records, ServerIdentity confirmed, Instant now) {
        if (records == null) throw new IllegalArgumentException("records are required");
        if (now == null) throw new IllegalArgumentException("repair time is required");
        if (confirmed == null || !confirmed.isSafeForAutomaticRendering()) {
            return new RepairResult(records, 0);
        }

        List<WaypointRecord> repaired = new ArrayList<WaypointRecord>(records.size());
        int changed = 0;
        for (WaypointRecord record : records) {
            WaypointRecord replacement = repair(record, confirmed, now);
            repaired.add(replacement);
            if (replacement != record) changed++;
        }
        return new RepairResult(repaired, changed);
    }

    private static WaypointRecord repair(
            WaypointRecord record, ServerIdentity confirmed, Instant now) {
        if (record == null) return null;
        ServerIdentity saved = record.getServerIdentity();
        if (saved == null
                || saved.getResolution()
                != ServerIdentity.Resolution.UNRESOLVED_SERVER_TRANSFER
                || !saved.sameServer(confirmed)
                || saved.getShortName().isEmpty()
                || !saved.getShortName().equalsIgnoreCase(confirmed.getShortName())) {
            return record;
        }

        String fullName = saved.getFullName().isEmpty()
                ? confirmed.getFullName() : saved.getFullName();
        ServerIdentity repairedServer = ServerIdentity.restored(
                saved.getEndpoint(), fullName, confirmed.getShortName(),
                saved.getAliases(), ServerIdentity.Resolution.RESOLVED);
        Instant changedAt = record.getCreatedAt() != null && now.isBefore(record.getCreatedAt())
                ? record.getCreatedAt() : now;
        return WaypointRecord.copyOf(record).serverIdentity(repairedServer)
                .updatedAt(changedAt).lastResolvedAt(changedAt).build();
    }

    public static final class RepairResult {
        private final List<WaypointRecord> records;
        private final int repairedCount;

        private RepairResult(List<WaypointRecord> records, int repairedCount) {
            this.records = Collections.unmodifiableList(
                    new ArrayList<WaypointRecord>(records));
            this.repairedCount = repairedCount;
        }

        public List<WaypointRecord> getRecords() { return records; }
        public int getRepairedCount() { return repairedCount; }
    }
}
