package org.waypoints.next.persistence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Unsupported future record preserved byte-for-byte inside the current document. */
public final class OpaqueWaypointRecord {
    private final List<String> bodyLines;

    public OpaqueWaypointRecord(List<String> bodyLines) {
        if (bodyLines == null || bodyLines.isEmpty()) {
            throw new IllegalArgumentException("opaque record body is required");
        }
        this.bodyLines = Collections.unmodifiableList(new ArrayList<String>(bodyLines));
    }

    public List<String> getBodyLines() { return bodyLines; }
}
