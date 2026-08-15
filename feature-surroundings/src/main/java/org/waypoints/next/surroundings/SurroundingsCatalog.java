package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded event-driven catalog plus session-only waypoint selection. */
public final class SurroundingsCatalog {
    public static final int DEFAULT_MAX_ENTRIES = 20000;

    private final int maxEntries;
    private final Map<SurroundingKey, SurroundingEntry> entries =
            new LinkedHashMap<SurroundingKey, SurroundingEntry>();
    private final Set<SurroundingKey> marked = new LinkedHashSet<SurroundingKey>();
    private long revision;

    public SurroundingsCatalog() { this(DEFAULT_MAX_ENTRIES); }

    public SurroundingsCatalog(int maxEntries) {
        if (maxEntries < 1) throw new IllegalArgumentException("max entries must be positive");
        this.maxEntries = maxEntries;
    }

    public synchronized void upsert(SurroundingEntry entry) {
        if (entry == null) return;
        SurroundingEntry previous = entries.get(entry.getKey());
        if (previous == null && entries.size() >= maxEntries) return;
        if (previous != null) entry = entry.withFirstSeenAt(previous.getFirstSeenAt());
        entries.put(entry.getKey(), entry);
        revision++;
    }

    public synchronized void remove(SurroundingKey key) {
        if (key != null && entries.remove(key) != null) revision++;
    }

    public synchronized void clearSession() {
        if (entries.isEmpty() && marked.isEmpty()) return;
        entries.clear();
        marked.clear();
        revision++;
    }

    /** Drops unloaded renderables while retaining explicit session selections. */
    public synchronized void clearEntries() {
        if (entries.isEmpty()) return;
        entries.clear();
        revision++;
    }

    public synchronized boolean setWaypoint(SurroundingKey key, boolean enabled) {
        if (key == null) return false;
        boolean changed = enabled ? marked.add(key) : marked.remove(key);
        if (changed) revision++;
        return changed;
    }

    public synchronized int setWaypoints(Collection<SurroundingKey> keys,
                                         boolean enabled) {
        int changed = 0;
        if (keys != null) for (SurroundingKey key : keys) {
            if (key != null && (enabled ? marked.add(key) : marked.remove(key))) changed++;
        }
        if (changed > 0) revision++;
        return changed;
    }

    public synchronized int clearAllWaypoints() {
        int changed = marked.size();
        if (changed > 0) {
            marked.clear();
            revision++;
        }
        return changed;
    }

    public synchronized boolean isWaypointEnabled(SurroundingKey key) {
        return marked.contains(key);
    }

    public synchronized SurroundingEntry find(SurroundingKey key) {
        return key == null ? null : entries.get(key);
    }

    public synchronized List<SurroundingEntry> findAll(
            Collection<SurroundingKey> keys) {
        List<SurroundingEntry> result = new ArrayList<SurroundingEntry>();
        if (keys != null) for (SurroundingKey key : keys) {
            SurroundingEntry entry = entries.get(key);
            if (entry != null) result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized void reconcileWaypoints(Collection<SurroundingKey> keys) {
        LinkedHashSet<SurroundingKey> next = new LinkedHashSet<SurroundingKey>();
        if (keys != null) for (SurroundingKey key : keys) {
            if (key != null) next.add(key);
        }
        if (next.equals(marked)) return;
        marked.clear();
        marked.addAll(next);
        revision++;
    }

    public synchronized void updateDeedAreas(Collection<DeedArea> areas,
                                             boolean dataAvailable) {
        List<DeedArea> safe = areas == null
                ? Collections.<DeedArea>emptyList()
                : new ArrayList<DeedArea>(areas);
        boolean changed = false;
        for (Map.Entry<SurroundingKey, SurroundingEntry> value
                : entries.entrySet()) {
            SurroundingEntry entry = value.getValue();
            DeedStatus status = deedStatus(entry, safe, dataAvailable);
            if (entry.getDeedStatus() != status) {
                value.setValue(entry.withDeedStatus(status));
                changed = true;
            }
        }
        if (changed) revision++;
    }

    public synchronized long revision() { return revision; }

    public synchronized List<SurroundingEntry> selectedEntries() {
        List<SurroundingEntry> result = new ArrayList<SurroundingEntry>();
        for (SurroundingKey key : marked) {
            SurroundingEntry entry = entries.get(key);
            if (entry != null) result.add(entry);
        }
        Collections.sort(result, new Comparator<SurroundingEntry>() {
            @Override public int compare(SurroundingEntry left, SurroundingEntry right) {
                return left.getKey().compareTo(right.getKey());
            }
        });
        return result;
    }

    public synchronized SurroundingsSnapshot snapshot(SurroundingsQuery query,
                                                       double originWorldX,
                                                       double originWorldY) {
        if (query == null) query = SurroundingsQuery.builder().build();
        int total = 0;
        int markedLoaded = 0;
        List<SurroundingsRow> rows = new ArrayList<SurroundingsRow>();
        for (SurroundingEntry entry : entries.values()) {
            boolean selected = marked.contains(entry.getKey());
            if (selected) markedLoaded++;
            if (entry.getKind() != query.getKind()) continue;
            total++;
            if (!query.matches(entry, selected)) continue;
            rows.add(new SurroundingsRow(entry, selected,
                    distance(originWorldX, originWorldY,
                            entry.getWorldX(), entry.getWorldY())));
        }
        final SurroundingsQuery selectedQuery = query;
        Collections.sort(rows, new Comparator<SurroundingsRow>() {
            @Override public int compare(SurroundingsRow left, SurroundingsRow right) {
                int compared;
                switch (selectedQuery.getSort()) {
                    case NAME:
                        compared = left.getEntry().getName().compareToIgnoreCase(
                                right.getEntry().getName());
                        break;
                    case CATEGORY:
                        compared = left.getEntry().getCategory().compareToIgnoreCase(
                                right.getEntry().getCategory());
                        break;
                    case MATERIAL:
                        compared = left.getEntry().getMaterial().compareToIgnoreCase(
                                right.getEntry().getMaterial());
                        break;
                    case RARITY:
                        compared = Integer.compare(left.getEntry().getRarity(),
                                right.getEntry().getRarity());
                        break;
                    default:
                        compared = Integer.compare(left.getDistanceMetres(),
                                right.getDistanceMetres());
                }
                if (compared == 0) compared = left.getEntry().getKey().compareTo(
                        right.getEntry().getKey());
                return selectedQuery.isAscending() ? compared : -compared;
            }
        });
        return new SurroundingsSnapshot(revision, total, rows.size(), markedLoaded, rows);
    }

    private static int distance(double x1, double y1, double x2, double y2) {
        return (int) Math.round(Math.hypot(x2 - x1, y2 - y1));
    }

    public static DeedStatus deedStatus(SurroundingEntry entry,
                                        Collection<DeedArea> areas,
                                        boolean dataAvailable) {
        if (entry == null || !dataAvailable) return DeedStatus.UNKNOWN;
        int tileX = (int) Math.floor(entry.getWorldX() / 4.0d);
        int tileY = (int) Math.floor(entry.getWorldY() / 4.0d);
        if (areas != null) for (DeedArea area : areas) {
            if (area != null && area.contains(tileX, tileY)) {
                return DeedStatus.ON_DEED;
            }
        }
        return DeedStatus.OFF_DEED;
    }
}
