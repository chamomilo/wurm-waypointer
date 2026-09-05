package org.waypoints.next.surroundings;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Session-local active profile, minus-name rules, and transition detector. */
public final class ScannerSession {
    private ScannerProfile profile;
    private final Set<String> excludedNames = new LinkedHashSet<String>();
    private final Map<SurroundingKey, SurroundingEntry> matches =
            new LinkedHashMap<SurroundingKey, SurroundingEntry>();
    private boolean notificationsEnabled = true;
    private boolean outlinesEnabled = true;

    public synchronized int activate(ScannerProfile value,
                                     Collection<SurroundingEntry> entries) {
        if (value == null) throw new IllegalArgumentException("profile is required");
        profile = value;
        return reseed(entries);
    }

    public synchronized void deactivate() {
        profile = null;
        matches.clear();
    }

    public synchronized boolean isActive() { return profile != null; }
    public synchronized ScannerProfile getProfile() { return profile; }
    public synchronized boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }
    public synchronized void setNotificationsEnabled(boolean value) {
        notificationsEnabled = value;
    }
    public synchronized boolean isOutlinesEnabled() { return outlinesEnabled; }
    public synchronized void setOutlinesEnabled(boolean value) {
        outlinesEnabled = value;
    }

    public synchronized Set<String> getExcludedNames() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(excludedNames));
    }

    public synchronized boolean addExcludedName(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return false;
        for (String current : excludedNames) {
            if (current.equalsIgnoreCase(clean)) return false;
        }
        return excludedNames.add(clean);
    }

    public synchronized boolean removeExcludedName(String value) {
        String clean = clean(value);
        if (clean.isEmpty()) return false;
        for (String current : new ArrayList<String>(excludedNames)) {
            if (current.equalsIgnoreCase(clean)) return excludedNames.remove(current);
        }
        return false;
    }

    public synchronized void clearExcludedNames() { excludedNames.clear(); }

    public synchronized int reseed(Collection<SurroundingEntry> entries) {
        matches.clear();
        if (profile != null && entries != null) for (SurroundingEntry entry : entries) {
            if (matches(entry)) matches.put(entry.getKey(), entry);
        }
        return matches.size();
    }

    public synchronized ScannerEvent observeUpsert(SurroundingEntry entry) {
        if (profile == null || entry == null) return null;
        boolean matched = matches(entry);
        boolean wasMatched = matches.containsKey(entry.getKey());
        if (matched == wasMatched) {
            if (matched) matches.put(entry.getKey(), entry);
            return null;
        }
        if (matched) {
            matches.put(entry.getKey(), entry);
            return event(ScannerEvent.Type.APPEARED, entry);
        }
        matches.remove(entry.getKey());
        return event(ScannerEvent.Type.DISAPPEARED, entry);
    }

    public synchronized ScannerEvent observeRemoved(SurroundingEntry entry) {
        return entry == null ? null : observeRemoved(entry.getKey());
    }

    public synchronized ScannerEvent observeRemoved(SurroundingKey key) {
        if (profile == null || key == null) return null;
        SurroundingEntry removed = matches.remove(key);
        return removed == null ? null
                : event(ScannerEvent.Type.DISAPPEARED, removed);
    }

    /** Renderer clears are transport boundaries, not real disappearance events. */
    public synchronized void clearObserved() { matches.clear(); }

    public synchronized boolean matches(SurroundingEntry entry) {
        return profile != null && profile.matches(entry)
                && !SurroundingsQuery.matchesExcludedName(entry, excludedNames);
    }

    public synchronized int getMatchCount() { return matches.size(); }

    private ScannerEvent event(ScannerEvent.Type type, SurroundingEntry entry) {
        return notificationsEnabled ? new ScannerEvent(type, profile, entry) : null;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
    }
}
