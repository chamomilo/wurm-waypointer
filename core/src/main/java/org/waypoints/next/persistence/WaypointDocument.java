package org.waypoints.next.persistence;

import org.waypoints.next.model.WaypointRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One versioned store/import-export document including unsupported records. */
public final class WaypointDocument {
    public static final int SCHEMA_VERSION = 1;

    private final List<WaypointRecord> records;
    private final List<OpaqueWaypointRecord> opaqueRecords;

    public WaypointDocument(List<WaypointRecord> records,
                            List<OpaqueWaypointRecord> opaqueRecords) {
        this.records = Collections.unmodifiableList(new ArrayList<WaypointRecord>(
                records == null ? Collections.<WaypointRecord>emptyList() : records));
        this.opaqueRecords = Collections.unmodifiableList(
                new ArrayList<OpaqueWaypointRecord>(opaqueRecords == null
                        ? Collections.<OpaqueWaypointRecord>emptyList() : opaqueRecords));
    }

    public static WaypointDocument empty() {
        return new WaypointDocument(Collections.<WaypointRecord>emptyList(),
                Collections.<OpaqueWaypointRecord>emptyList());
    }

    public List<WaypointRecord> getRecords() { return records; }
    public List<OpaqueWaypointRecord> getOpaqueRecords() { return opaqueRecords; }

    public WaypointDocument withRecords(List<WaypointRecord> values) {
        return new WaypointDocument(values, opaqueRecords);
    }
}
