package org.waypoints.next.service;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;

/** Immutable, client-independent query for the native manager table. */
public final class WaypointManagerQuery {
    public enum SortColumn {
        ENABLED, NAME, TYPE, SERVER, USER, STATUS, DISTANCE, STYLE, ID
    }

    private final String text;
    private final WaypointFilter.ServerMode serverMode;
    private final ServerIdentity currentServer;
    private final String specificServerFingerprint;
    private final String user;
    private final WaypointSourceType sourceType;
    private final WaypointResolution resolution;
    private final SortColumn sortColumn;
    private final boolean ascending;
    private final Double originTileX;
    private final Double originTileY;

    private WaypointManagerQuery(Builder builder) {
        text = clean(builder.text);
        serverMode = builder.serverMode;
        currentServer = builder.currentServer;
        specificServerFingerprint = clean(builder.specificServerFingerprint);
        user = clean(builder.user);
        sourceType = builder.sourceType;
        resolution = builder.resolution;
        sortColumn = builder.sortColumn;
        ascending = builder.ascending;
        originTileX = builder.originTileX;
        originTileY = builder.originTileY;
    }

    public static Builder builder() { return new Builder(); }

    public String getText() { return text; }
    public WaypointFilter.ServerMode getServerMode() { return serverMode; }
    public ServerIdentity getCurrentServer() { return currentServer; }
    public String getSpecificServerFingerprint() { return specificServerFingerprint; }
    public String getUser() { return user; }
    public WaypointSourceType getSourceType() { return sourceType; }
    public WaypointResolution getResolution() { return resolution; }
    public SortColumn getSortColumn() { return sortColumn; }
    public boolean isAscending() { return ascending; }
    public Double getOriginTileX() { return originTileX; }
    public Double getOriginTileY() { return originTileY; }

    WaypointFilter filter() {
        WaypointFilter.Builder result = WaypointFilter.builder().text(text).user(user)
                .sourceType(sourceType).resolution(resolution);
        switch (serverMode) {
            case CURRENT: result.currentServer(currentServer); break;
            case SPECIFIC: result.specificServer(specificServerFingerprint); break;
            case UNASSIGNED: result.unassignedServer(); break;
            case ALL: result.allServers(); break;
            default: throw new IllegalStateException("unsupported server mode: " + serverMode);
        }
        return result.build();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private String text = "";
        private WaypointFilter.ServerMode serverMode = WaypointFilter.ServerMode.ALL;
        private ServerIdentity currentServer;
        private String specificServerFingerprint = "";
        private String user = "";
        private WaypointSourceType sourceType;
        private WaypointResolution resolution;
        private SortColumn sortColumn = SortColumn.NAME;
        private boolean ascending = true;
        private Double originTileX;
        private Double originTileY;

        public Builder text(String value) { text = value; return this; }
        public Builder currentServer(ServerIdentity value) {
            serverMode = WaypointFilter.ServerMode.CURRENT;
            currentServer = value;
            return this;
        }
        /** Supplies the live server for distance without changing an All view. */
        public Builder currentContext(ServerIdentity value) {
            currentServer = value;
            return this;
        }
        public Builder allServers() {
            serverMode = WaypointFilter.ServerMode.ALL;
            return this;
        }
        public Builder specificServer(String fingerprint) {
            serverMode = WaypointFilter.ServerMode.SPECIFIC;
            specificServerFingerprint = fingerprint;
            return this;
        }
        public Builder unassignedServer() {
            serverMode = WaypointFilter.ServerMode.UNASSIGNED;
            return this;
        }
        public Builder user(String value) { user = value; return this; }
        public Builder sourceType(WaypointSourceType value) { sourceType = value; return this; }
        public Builder resolution(WaypointResolution value) { resolution = value; return this; }
        public Builder sort(SortColumn column, boolean valueAscending) {
            sortColumn = column;
            ascending = valueAscending;
            return this;
        }
        public Builder originTiles(double tileX, double tileY) {
            if (!finiteNonNegative(tileX) || !finiteNonNegative(tileY)) {
                throw new IllegalArgumentException("origin tiles must be finite and non-negative");
            }
            originTileX = Double.valueOf(tileX);
            originTileY = Double.valueOf(tileY);
            return this;
        }
        public WaypointManagerQuery build() {
            if (serverMode == null) throw new IllegalArgumentException("server mode is required");
            if (sortColumn == null) throw new IllegalArgumentException("sort column is required");
            if ((originTileX == null) != (originTileY == null)) {
                throw new IllegalArgumentException("both origin coordinates are required");
            }
            if (serverMode == WaypointFilter.ServerMode.SPECIFIC
                    && clean(specificServerFingerprint).isEmpty()) {
                throw new IllegalArgumentException("specific server fingerprint is required");
            }
            return new WaypointManagerQuery(this);
        }

        private static boolean finiteNonNegative(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value) && value >= 0.0d;
        }
    }
}
