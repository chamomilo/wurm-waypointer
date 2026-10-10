package org.waypoints.next.tracking;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/** Server-supplied catalogue metadata; coordinates are deliberately absent. */
public final class ManagedEntry {
    public final ManagedKind kind;
    public final long id;
    public final String name, type;
    public final List<String> details;
    public ManagedEntry(ManagedKind kind, long id, String name, String type, List<String> details) {
        this.kind = kind; this.id = id; this.name = name; this.type = type;
        this.details = Collections.unmodifiableList(new ArrayList<String>(details));
    }
    public String key() { return kind.name() + ":" + id; }
    public boolean canTrack() { return id > 0L; }
}
