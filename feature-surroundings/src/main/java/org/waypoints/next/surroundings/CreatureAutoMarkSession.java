package org.waypoints.next.surroundings;

import java.util.*;

/** A successful mark is remembered through movement, cell re-adds and view unloads. */
public final class CreatureAutoMarkSession {
    private final Set<SurroundingKey> handled=new HashSet<SurroundingKey>();
    public CreatureHighlightRule candidate(CreatureHighlightSettings settings,SurroundingEntry entry) {
        return entry==null || handled.contains(entry.getKey())?null:CreatureHighlightPolicy.autoMark(settings,entry);
    }
    public void marked(SurroundingKey key) { handled.add(key); }
    public void clear() { handled.clear(); }
}
