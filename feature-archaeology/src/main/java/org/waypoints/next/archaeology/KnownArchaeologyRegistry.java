package org.waypoints.next.archaeology;

import org.waypoints.next.model.WaypointLayer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-scoped exact-location registry, independent of active report sessions. */
public final class KnownArchaeologyRegistry {
    private final Map<String, KnownArchaeologyLocation> values =
            new LinkedHashMap<String, KnownArchaeologyLocation>();

    public KnownArchaeologyRegistry() { }

    public KnownArchaeologyRegistry(Collection<KnownArchaeologyLocation> locations) {
        if (locations != null) for (KnownArchaeologyLocation location : locations) {
            values.put(location.getLocationKey(), location);
        }
    }

    public synchronized KnownArchaeologyLocation find(String serverFingerprint,
                                                       String deedName) {
        return values.get(ArchaeologyReportSession.reportKey(serverFingerprint,
                ArchaeologyMessageParser.normalizedDeedKey(deedName)));
    }

    public synchronized KnownArchaeologyLocation confirm(String serverFingerprint,
                                                          String deedName,
                                                          double tileX, double tileY,
                                                          WaypointLayer layer,
                                                          Instant at,
                                                          UUID sourceSessionId) {
        KnownArchaeologyLocation previous = find(serverFingerprint, deedName);
        KnownArchaeologyLocation saved = new KnownArchaeologyLocation(
                serverFingerprint, deedName, tileX, tileY, layer,
                previous == null ? at : previous.getDiscoveredAt(), at,
                sourceSessionId, false);
        values.put(saved.getLocationKey(), saved);
        return saved;
    }

    public synchronized void requireConfirmation(KnownArchaeologyLocation location,
                                                 Instant at) {
        if (location != null) values.put(location.getLocationKey(),
                location.requiringConfirmation(at));
    }

    public synchronized KnownArchaeologyLocation trustSavedLocation(
            KnownArchaeologyLocation location) {
        if (location == null) return null;
        KnownArchaeologyLocation trusted = location.trustingSavedLocation();
        values.put(trusted.getLocationKey(), trusted);
        return trusted;
    }

    public synchronized int clearServer(String serverFingerprint) {
        List<String> remove = new ArrayList<String>();
        for (KnownArchaeologyLocation location : values.values()) {
            if (location.getServerFingerprint().equals(serverFingerprint)) {
                remove.add(location.getLocationKey());
            }
        }
        for (String key : remove) values.remove(key);
        return remove.size();
    }

    public synchronized int clearAll() {
        int count = values.size();
        values.clear();
        return count;
    }

    public synchronized List<KnownArchaeologyLocation> snapshot() {
        return Collections.unmodifiableList(
                new ArrayList<KnownArchaeologyLocation>(values.values()));
    }
}
