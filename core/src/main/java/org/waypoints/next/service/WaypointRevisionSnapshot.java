package org.waypoints.next.service;

import org.waypoints.next.model.WaypointRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable record/revision boundary shared with read-only runtime consumers. */
public final class WaypointRevisionSnapshot {
    private final long revision;
    private final List<WaypointRecord> records;

    public WaypointRevisionSnapshot(long revision, List<WaypointRecord> records) {
        this.revision = revision;
        this.records = Collections.unmodifiableList(
                new ArrayList<WaypointRecord>(records));
    }

    public long getRevision() {
        return revision;
    }

    public List<WaypointRecord> getRecords() {
        return records;
    }
}
