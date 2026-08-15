package org.waypoints.next.integration;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.VanillaLandmarkKind;
import org.waypoints.next.model.VanillaLandmarkVisibility;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.persistence.VanillaLandmarkVisibilityStore;
import org.waypoints.next.service.WaypointRevisionSnapshot;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Captures server landmarks before vanilla construction and projects system records. */
final class VanillaLandmarkRuntime {
    private static final Instant SYSTEM_TIME = Instant.EPOCH;
    private final Logger logger;
    private final EnumMap<VanillaLandmarkKind, Capture> captures =
            new EnumMap<VanillaLandmarkKind, Capture>(VanillaLandmarkKind.class);
    private final Map<Long, VanillaLandmarkKind> capturedIds =
            new HashMap<Long, VanillaLandmarkKind>();
    private VanillaLandmarkVisibility visibility = new VanillaLandmarkVisibility();
    private VanillaLandmarkVisibilityStore store;
    private ServerIdentity server;
    private long revision;
    private long combinedRevision;
    private long combinedUserRevision = Long.MIN_VALUE;
    private long combinedLandmarkRevision = Long.MIN_VALUE;

    VanillaLandmarkRuntime(Logger logger) {
        this.logger = logger;
    }

    synchronized void configure(WaypointClientConfiguration configuration) {
        store = new VanillaLandmarkVisibilityStore(
                configuration.getVanillaLandmarkStateFile());
        try {
            visibility = store.load();
            logger.info("Vanilla landmark visibility loaded: path=\""
                    + store.getFile().toAbsolutePath() + "\", choices="
                    + visibility.entries().size());
        } catch (Throwable failure) {
            visibility = new VanillaLandmarkVisibility();
            logger.log(Level.WARNING,
                    "Vanilla landmark visibility is unreadable; all landmarks default On",
                    failure);
        }
        revision++;
    }

    synchronized boolean capture(long effectId, short effectType,
                                 float worldX, float worldY, float height,
                                 int layer) {
        VanillaLandmarkKind kind = kind(effectType);
        if (kind == null) return false;
        VanillaLandmarkKind previousKind = capturedIds.put(
                Long.valueOf(effectId), kind);
        if (previousKind != null && previousKind != kind) {
            Capture previous = captures.get(previousKind);
            if (previous != null && previous.effectId == effectId) {
                captures.remove(previousKind);
            }
        }
        Capture next = new Capture(effectId, worldX, worldY, height, layer);
        Capture previous = captures.put(kind, next);
        if (!next.equals(previous)) revision++;
        return true;
    }

    synchronized void removed(long effectId) {
        VanillaLandmarkKind kind = capturedIds.remove(Long.valueOf(effectId));
        if (kind == null) return;
        Capture capture = captures.get(kind);
        if (capture != null && capture.effectId == effectId) {
            captures.remove(kind);
            revision++;
        }
    }

    synchronized void bind(ServerIdentity value) {
        if (value == null || !value.isSafeForAutomaticRendering()) return;
        if (server != null && server.sameServer(value)) return;
        server = value;
        revision++;
    }

    synchronized void clearSession() {
        if (!captures.isEmpty() || server != null) revision++;
        captures.clear();
        capturedIds.clear();
        server = null;
    }

    synchronized List<WaypointRecord> records() {
        if (server == null || !server.isSafeForAutomaticRendering()
                || captures.isEmpty()) return Collections.emptyList();
        List<WaypointRecord> result = new ArrayList<WaypointRecord>(3);
        for (VanillaLandmarkKind kind : VanillaLandmarkKind.values()) {
            Capture capture = captures.get(kind);
            if (capture != null) result.add(record(kind, capture));
        }
        return Collections.unmodifiableList(result);
    }

    synchronized WaypointRevisionSnapshot combine(WaypointRevisionSnapshot user) {
        if (user == null) throw new IllegalArgumentException("user snapshot is required");
        if (combinedUserRevision != user.getRevision()
                || combinedLandmarkRevision != revision) {
            combinedUserRevision = user.getRevision();
            combinedLandmarkRevision = revision;
            combinedRevision++;
        }
        List<WaypointRecord> combined = new ArrayList<WaypointRecord>(
                captures.size() + user.getRecords().size());
        combined.addAll(records());
        combined.addAll(user.getRecords());
        return new WaypointRevisionSnapshot(combinedRevision, combined);
    }

    synchronized boolean isSystemId(UUID id) {
        return kindForId(id) != null;
    }

    synchronized String displayName(UUID id) {
        VanillaLandmarkKind kind = kindForId(id);
        return kind == null ? "Vanilla landmark" : kind.getDisplayName();
    }

    synchronized boolean setEnabled(UUID id, boolean enabled) {
        VanillaLandmarkKind kind = kindForId(id);
        if (kind == null || server == null) return false;
        String endpoint = server.getEndpointFingerprint();
        if (visibility.isEnabled(endpoint, kind) == enabled) return true;
        VanillaLandmarkVisibility changed = visibility.withEnabled(
                endpoint, kind, enabled);
        try {
            if (store != null) store.save(changed);
        } catch (Throwable failure) {
            logger.log(Level.WARNING,
                    "Unable to save vanilla landmark visibility", failure);
            throw new IllegalStateException(
                    "vanilla landmark On/Off choice was not saved", failure);
        }
        visibility = changed;
        revision++;
        return true;
    }

    synchronized long revision() { return revision; }

    private WaypointRecord record(VanillaLandmarkKind kind, Capture capture) {
        String endpoint = server.getEndpointFingerprint();
        MarkerStyle style = style(kind);
        return WaypointRecord.builder().id(id(endpoint, kind))
                .name(kind.getDisplayName()).description(
                        "Server-supplied vanilla landmark managed by Wurm Waypointer.")
                .createdByUser("Wurm").serverIdentity(server)
                .sourceType(WaypointSourceType.VANILLA_SYSTEM)
                .sourceKey(kind.name())
                .coordinate(new WaypointCoordinate(capture.worldX / 4.0d,
                        capture.worldY / 4.0d, Double.valueOf(capture.height),
                        capture.layer < 0 ? WaypointLayer.CAVE
                                : WaypointLayer.SURFACE))
                .resolution(WaypointResolution.STATIC_EXACT)
                .enabled(visibility.isEnabled(endpoint, kind))
                .markerStyle(style).createdAt(SYSTEM_TIME).updatedAt(SYSTEM_TIME)
                .lastResolvedAt(SYSTEM_TIME).build();
    }

    private VanillaLandmarkKind kindForId(UUID id) {
        if (id == null || server == null) return null;
        String endpoint = server.getEndpointFingerprint();
        for (VanillaLandmarkKind kind : VanillaLandmarkKind.values()) {
            if (id(endpoint, kind).equals(id)) return kind;
        }
        return null;
    }

    private static VanillaLandmarkKind kind(short effectType) {
        if (effectType == 2) return VanillaLandmarkKind.WHITE_LIGHT;
        if (effectType == 3) return VanillaLandmarkKind.BLACK_LIGHT;
        if (effectType == 25) return VanillaLandmarkKind.RIFT;
        return null;
    }

    private static UUID id(String endpoint, VanillaLandmarkKind kind) {
        return UUID.nameUUIDFromBytes(("wurm-waypointer:vanilla:"
                + endpoint.toLowerCase(java.util.Locale.ENGLISH) + ":" + kind.name())
                .getBytes(StandardCharsets.UTF_8));
    }

    private static MarkerStyle style(VanillaLandmarkKind kind) {
        switch (kind) {
            case WHITE_LIGHT:
                return new MarkerStyle(kind.getWorldStyle(), 1.0f, 1.0f, 1.0f,
                        1.0f, 9.0f, 2.0f, true, true);
            case BLACK_LIGHT:
                return new MarkerStyle(kind.getWorldStyle(), 0.6f, 0.6f, 0.6f,
                        1.0f, 9.0f, 2.0f, true, true);
            case RIFT:
            default:
                return new MarkerStyle(kind.getWorldStyle(), 1.0f, 0.0f, 0.0f,
                        1.0f, 9.0f, 2.0f, true, true);
        }
    }

    private static final class Capture {
        private final long effectId;
        private final float worldX;
        private final float worldY;
        private final float height;
        private final int layer;

        private Capture(long effectId, float worldX, float worldY,
                        float height, int layer) {
            this.effectId = effectId;
            this.worldX = worldX;
            this.worldY = worldY;
            this.height = height;
            this.layer = layer;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Capture)) return false;
            Capture that = (Capture) other;
            return effectId == that.effectId
                    && Float.compare(worldX, that.worldX) == 0
                    && Float.compare(worldY, that.worldY) == 0
                    && Float.compare(height, that.height) == 0
                    && layer == that.layer;
        }

        @Override public int hashCode() {
            int result = (int) (effectId ^ (effectId >>> 32));
            result = 31 * result + Float.floatToIntBits(worldX);
            result = 31 * result + Float.floatToIntBits(worldY);
            result = 31 * result + Float.floatToIntBits(height);
            return 31 * result + layer;
        }
    }
}
