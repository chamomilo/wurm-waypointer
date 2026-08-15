package org.waypoints.next.service;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;

import java.util.Locale;

/** Immutable manager filter shared by the native Wurm window and console path. */
public final class WaypointFilter {
    public enum ServerMode { CURRENT, ALL, SPECIFIC, UNASSIGNED }

    private final String text;
    private final ServerMode serverMode;
    private final ServerIdentity currentServer;
    private final String specificServerFingerprint;
    private final String user;
    private final WaypointSourceType sourceType;
    private final WaypointResolution resolution;

    private WaypointFilter(Builder builder) {
        text = clean(builder.text);
        serverMode = builder.serverMode;
        currentServer = builder.currentServer;
        specificServerFingerprint = clean(builder.specificServerFingerprint);
        user = clean(builder.user);
        sourceType = builder.sourceType;
        resolution = builder.resolution;
    }

    public static Builder builder() { return new Builder(); }

    public boolean matches(WaypointRecord record) {
        if (record == null || !matchesServer(record) || !matchesUser(record)
                || (sourceType != null && sourceType != record.getSourceType())
                || (resolution != null && resolution != record.getResolution())) return false;
        if (text.isEmpty()) return true;
        String needle = text.toLowerCase(Locale.ENGLISH);
        if (contains(record.getName(), needle) || contains(record.getDescription(), needle)
                || contains(record.getGroup(), needle)) return true;
        for (String tag : record.getTags()) if (contains(tag, needle)) return true;
        return false;
    }

    private boolean matchesServer(WaypointRecord record) {
        ServerIdentity server = record.getServerIdentity();
        switch (serverMode) {
            case ALL: return true;
            case UNASSIGNED: return server == null;
            case CURRENT:
                return server != null && currentServer != null
                        && server.sameServer(currentServer);
            case SPECIFIC:
                return server != null && !specificServerFingerprint.isEmpty()
                        && specificServerFingerprint.equalsIgnoreCase(
                        server.getEndpointFingerprint());
            default: return false;
        }
    }

    private boolean matchesUser(WaypointRecord record) {
        return record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM
                || user.isEmpty() || user.equalsIgnoreCase(record.getCreatedByUser());
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ENGLISH).contains(needle);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String text = "";
        private ServerMode serverMode = ServerMode.ALL;
        private ServerIdentity currentServer;
        private String specificServerFingerprint = "";
        private String user = "";
        private WaypointSourceType sourceType;
        private WaypointResolution resolution;

        public Builder text(String value) { text = value; return this; }
        public Builder currentServer(ServerIdentity value) {
            serverMode = ServerMode.CURRENT;
            currentServer = value;
            return this;
        }
        public Builder allServers() { serverMode = ServerMode.ALL; return this; }
        public Builder specificServer(String fingerprint) {
            serverMode = ServerMode.SPECIFIC;
            specificServerFingerprint = fingerprint;
            return this;
        }
        public Builder unassignedServer() {
            serverMode = ServerMode.UNASSIGNED;
            return this;
        }
        public Builder user(String value) { user = value; return this; }
        public Builder sourceType(WaypointSourceType value) { sourceType = value; return this; }
        public Builder resolution(WaypointResolution value) { resolution = value; return this; }
        public WaypointFilter build() {
            if (serverMode == null) throw new IllegalArgumentException("server mode is required");
            if (serverMode == ServerMode.SPECIFIC && clean(specificServerFingerprint).isEmpty()) {
                throw new IllegalArgumentException("specific server fingerprint is required");
            }
            return new WaypointFilter(this);
        }
    }
}
