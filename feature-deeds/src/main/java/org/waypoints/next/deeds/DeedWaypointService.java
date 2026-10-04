package org.waypoints.next.deeds;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Creates and reconciles persistent DEED waypoints without deleting stale data. */
public final class DeedWaypointService {
    public static final String PROVIDER_EXTENSION = "deed.providerKey";
    public static final String STABLE_KEY_EXTENSION = "deed.stableKey";
    public static final String SNAPSHOT_AT_EXTENSION = "deed.snapshotAt";
    public static final String MISSING_AT_EXTENSION = "deed.missingAt";

    public WaypointRecord track(List<WaypointRecord> existing,
                                DeedProviderSnapshot snapshot,
                                DeedRecord deed, ServerIdentity server,
                                String user, Instant now) {
        return track(existing, snapshot, deed, server, user, now,
                MarkerStyle.defaultColoredBeam());
    }

    public WaypointRecord track(List<WaypointRecord> existing,
                                DeedProviderSnapshot snapshot,
                                DeedRecord deed, ServerIdentity server,
                                String user, Instant now, MarkerStyle style) {
        requireUsable(snapshot, deed, server, user, now, style);
        WaypointRecord old = find(existing, server, snapshot.getProviderKey(),
                deed.getStableKey(), user);
        UUID id = old == null ? deterministicId(server, snapshot.getProviderKey(),
                deed.getStableKey(), user) : old.getId();
        Instant created = old == null ? now : old.getCreatedAt();
        MarkerStyle markerStyle = old == null ? style : old.getMarkerStyle();
        Map<String, List<String>> extensions = extensions(old,
                snapshot.getProviderKey(), deed.getStableKey(),
                snapshot.getDataTimestamp(), null);
        return WaypointRecord.builder().id(id).name(deed.getName())
                .description(description(deed, snapshot.getProviderKey()))
                .createdByUser(old == null ? user : old.getCreatedByUser())
                .serverIdentity(server).sourceType(WaypointSourceType.DEED)
                .sourceKey(sourceKey(snapshot.getProviderKey(), deed.getStableKey()))
                .coordinate(coordinate(deed)).resolution(WaypointResolution.LIVE_EXACT)
                // Track is an explicit player action, so a newly tracked or
                // re-tracked deed must immediately become visible.
                .enabled(true).markerStyle(markerStyle)
                .arrivalRadiusMetres(old == null ? WaypointArrival.DEFAULT_RADIUS_METRES
                        : old.getArrivalRadiusMetres())
                .expiresAt(null).group(old == null ? "Deeds" : old.getGroup())
                .tags(old == null ? Collections.<String>emptySet() : old.getTags())
                .createdAt(created).updatedAt(now)
                .lastResolvedAt(snapshot.getDataTimestamp()).extensions(extensions)
                .build();
    }

    /** Reappearance/movement updates the same UUID; a valid omission becomes STALE. */
    public Reconciliation reconcile(List<WaypointRecord> existing,
                                    DeedProviderSnapshot snapshot,
                                    ServerIdentity server, Instant now) {
        if (existing == null || snapshot == null || server == null || now == null) {
            throw new IllegalArgumentException("records, snapshot, server, and time are required");
        }
        if (snapshot.getProviderKey().isEmpty()
                || snapshot.getStatus() == DeedProviderStatus.NO_PROVIDER
                || snapshot.getStatus() == DeedProviderStatus.LOADING
                || snapshot.getStatus() == DeedProviderStatus.ERROR) {
            return Reconciliation.empty();
        }
        Map<String, DeedRecord> incoming = new LinkedHashMap<String, DeedRecord>();
        for (DeedRecord deed : snapshot.getDeeds()) {
            incoming.put(fold(deed.getStableKey()), deed);
        }
        List<WaypointRecord> updates = new ArrayList<WaypointRecord>();
        int moved = 0;
        int restored = 0;
        int stale = 0;
        for (WaypointRecord old : existing) {
            if (!isTrackedBy(old, server, snapshot.getProviderKey())) continue;
            String stableKey = extension(old, STABLE_KEY_EXTENSION);
            DeedRecord deed = incoming.get(fold(stableKey));
            if (deed != null) {
                WaypointCoordinate nextCoordinate = coordinate(deed);
                boolean coordinateChanged = !nextCoordinate.equals(old.getCoordinate());
                boolean wasStale = old.getResolution() == WaypointResolution.STALE;
                String recordedSnapshot = extension(old, SNAPSHOT_AT_EXTENSION);
                String nextSnapshot = instant(snapshot.getDataTimestamp());
                if (!coordinateChanged && !wasStale
                        && old.getName().equals(deed.getName())
                        && recordedSnapshot.equals(nextSnapshot)) continue;
                Map<String, List<String>> extensions = extensions(old,
                        snapshot.getProviderKey(), stableKey,
                        snapshot.getDataTimestamp(), null);
                updates.add(WaypointRecord.copyOf(old).name(deed.getName())
                        .description(description(deed, snapshot.getProviderKey()))
                        .coordinate(nextCoordinate).resolution(WaypointResolution.LIVE_EXACT)
                        .updatedAt(now).lastResolvedAt(snapshot.getDataTimestamp())
                        .extensions(extensions).build());
                if (coordinateChanged) moved++;
                if (wasStale) restored++;
            } else if (snapshot.getStatus() == DeedProviderStatus.READY
                    && old.getResolution() != WaypointResolution.STALE) {
                Map<String, List<String>> extensions = extensions(old,
                        snapshot.getProviderKey(), stableKey,
                        old.getLastResolvedAt(), snapshot.getDataTimestamp());
                updates.add(WaypointRecord.copyOf(old)
                        .resolution(WaypointResolution.STALE).updatedAt(now)
                        .extensions(extensions).build());
                stale++;
            }
        }
        return new Reconciliation(updates, moved, restored, stale);
    }

    public static boolean isDeedWaypoint(WaypointRecord record) {
        return record != null && record.getSourceType() == WaypointSourceType.DEED;
    }

    public static long dataAgeMillis(WaypointRecord record, Instant now) {
        if (!isDeedWaypoint(record) || record.getLastResolvedAt() == null || now == null) {
            return -1L;
        }
        return Math.max(0L, now.toEpochMilli()
                - record.getLastResolvedAt().toEpochMilli());
    }

    public static String describeAge(WaypointRecord record, Instant now) {
        long millis = dataAgeMillis(record, now);
        if (millis < 0L) return "unknown age";
        long minutes = millis / 60_000L;
        if (minutes < 1L) return "now";
        if (minutes < 60L) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 48L) return hours + "h";
        return hours / 24L + "d";
    }

    private static boolean isTrackedBy(WaypointRecord record,
                                       ServerIdentity server, String provider) {
        return isDeedWaypoint(record) && record.getServerIdentity() != null
                && record.getServerIdentity().sameServer(server)
                && provider.equals(extension(record, PROVIDER_EXTENSION));
    }

    private static WaypointRecord find(List<WaypointRecord> existing,
                                       ServerIdentity server, String provider,
                                       String stableKey, String user) {
        if (existing == null) return null;
        for (WaypointRecord record : existing) {
            if (isTrackedBy(record, server, provider)
                    && stableKey.equalsIgnoreCase(extension(
                            record, STABLE_KEY_EXTENSION))
                    && record.getCreatedByUser().equalsIgnoreCase(
                            user.trim())) return record;
        }
        return null;
    }

    private static void requireUsable(DeedProviderSnapshot snapshot,
                                      DeedRecord deed, ServerIdentity server,
                                      String user, Instant now, MarkerStyle style) {
        if (snapshot == null || deed == null || server == null || now == null
                || style == null) throw new IllegalArgumentException(
                "snapshot, deed, server, time, and style are required");
        if (snapshot.getProviderKey().isEmpty()
                || snapshot.getStatus() == DeedProviderStatus.NO_PROVIDER
                || !snapshot.hasData()) throw new IllegalArgumentException(
                "a usable provider snapshot is required");
        if (user == null || user.trim().isEmpty()) throw new IllegalArgumentException(
                "player name is required");
    }

    private static WaypointCoordinate coordinate(DeedRecord deed) {
        return new WaypointCoordinate(deed.getTileX() + 0.5d,
                deed.getTileY() + 0.5d, null, WaypointLayer.SURFACE);
    }

    private static String sourceKey(String provider, String stableKey) {
        String value = provider.trim() + "|" + stableKey.trim();
        if (value.length() > 512) throw new IllegalArgumentException(
                "combined deed provider/stable key is too long");
        return value;
    }

    private static UUID deterministicId(ServerIdentity server, String provider,
                                        String stableKey, String user) {
        String value = "deed|" + server.getEndpointFingerprint().toLowerCase(
                Locale.ENGLISH) + "|" + provider.toLowerCase(Locale.ENGLISH)
                + "|" + stableKey.toLowerCase(Locale.ENGLISH)
                + "|" + user.trim().toLowerCase(Locale.ENGLISH);
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, List<String>> extensions(
            WaypointRecord old, String provider, String stableKey,
            Instant snapshotAt, Instant missingAt) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (old != null) result.putAll(old.getExtensions());
        result.put(PROVIDER_EXTENSION, Collections.singletonList(provider));
        result.put(STABLE_KEY_EXTENSION, Collections.singletonList(stableKey));
        result.put(SNAPSHOT_AT_EXTENSION, Collections.singletonList(instant(snapshotAt)));
        if (missingAt == null) result.remove(MISSING_AT_EXTENSION);
        else result.put(MISSING_AT_EXTENSION, Collections.singletonList(instant(missingAt)));
        return result;
    }

    private static String description(DeedRecord deed, String provider) {
        StringBuilder result = new StringBuilder("Provider deed [")
                .append(provider).append(']');
        append(result, "mayor", deed.metadata("mayor"));
        append(result, "alliance", deed.metadata("allianceName"));
        append(result, "last active", deed.metadata("lastActive"));
        return result.length() <= 4096 ? result.toString()
                : result.substring(0, 4096);
    }

    private static void append(StringBuilder result, String label, String value) {
        if (value != null && !value.trim().isEmpty()) result.append("; ")
                .append(label).append(": ").append(value.trim());
    }

    private static String extension(WaypointRecord record, String key) {
        List<String> values = record.getExtensions().get(key);
        return values == null || values.isEmpty() || values.get(0) == null
                ? "" : values.get(0).trim();
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }

    private static String fold(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    public static final class Reconciliation {
        private final List<WaypointRecord> updates;
        private final int moved;
        private final int restored;
        private final int stale;

        private Reconciliation(List<WaypointRecord> updates, int moved,
                               int restored, int stale) {
            this.updates = Collections.unmodifiableList(
                    new ArrayList<WaypointRecord>(updates));
            this.moved = moved;
            this.restored = restored;
            this.stale = stale;
        }

        public static Reconciliation empty() {
            return new Reconciliation(Collections.<WaypointRecord>emptyList(), 0, 0, 0);
        }
        public List<WaypointRecord> getUpdates() { return updates; }
        public int getMoved() { return moved; }
        public int getRestored() { return restored; }
        public int getStale() { return stale; }
        public boolean isChanged() { return !updates.isEmpty(); }
    }
}
