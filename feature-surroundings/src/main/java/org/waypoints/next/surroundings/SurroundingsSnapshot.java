package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable view returned to the native window. */
public final class SurroundingsSnapshot {
    private final long revision;
    private final int totalCount;
    private final int filteredCount;
    private final int markedCount;
    private final List<SurroundingsRow> rows;

    SurroundingsSnapshot(long revision, int totalCount, int filteredCount,
                         int markedCount, List<SurroundingsRow> rows) {
        this.revision = revision;
        this.totalCount = totalCount;
        this.filteredCount = filteredCount;
        this.markedCount = markedCount;
        this.rows = Collections.unmodifiableList(new ArrayList<SurroundingsRow>(rows));
    }

    public long getRevision() { return revision; }
    public int getTotalCount() { return totalCount; }
    public int getFilteredCount() { return filteredCount; }
    public int getMarkedCount() { return markedCount; }
    public List<SurroundingsRow> getRows() { return rows; }
}
