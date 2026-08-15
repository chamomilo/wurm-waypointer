package org.waypoints.next.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Immutable, versioned waypoint definition with a stable UUID. */
public final class WaypointRecord {
    public static final int MODEL_VERSION = 1;

    private final int modelVersion;
    private final UUID id;
    private final String name;
    private final String description;
    private final String createdByUser;
    private final ServerIdentity serverIdentity;
    private final WaypointSourceType sourceType;
    private final String sourceKey;
    private final WaypointCoordinate coordinate;
    private final WaypointResolution resolution;
    private final List<UncertaintyObservation> uncertaintyObservations;
    private final boolean enabled;
    private final MarkerStyle markerStyle;
    private final int arrivalRadiusMetres;
    private final Instant expiresAt;
    private final String group;
    private final Set<String> tags;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final Instant lastResolvedAt;
    private final Map<String, List<String>> extensions;

    private WaypointRecord(Builder builder) {
        modelVersion = builder.modelVersion;
        id = builder.id;
        name = clean(builder.name);
        description = clean(builder.description);
        createdByUser = clean(builder.createdByUser);
        serverIdentity = builder.serverIdentity;
        sourceType = builder.sourceType;
        sourceKey = clean(builder.sourceKey);
        coordinate = builder.coordinate;
        resolution = builder.resolution;
        uncertaintyObservations = immutableList(builder.uncertaintyObservations);
        enabled = builder.enabled;
        markerStyle = builder.markerStyle;
        arrivalRadiusMetres = builder.arrivalRadiusMetres;
        expiresAt = builder.expiresAt;
        group = clean(builder.group);
        tags = immutableTags(builder.tags);
        createdAt = builder.createdAt;
        updatedAt = builder.updatedAt;
        lastResolvedAt = builder.lastResolvedAt;
        extensions = immutableExtensions(builder.extensions);
    }

    public static Builder builder() { return new Builder(); }
    public static Builder copyOf(WaypointRecord record) { return new Builder(record); }

    public int getModelVersion() { return modelVersion; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getCreatedByUser() { return createdByUser; }
    public ServerIdentity getServerIdentity() { return serverIdentity; }
    public WaypointSourceType getSourceType() { return sourceType; }
    public String getSourceKey() { return sourceKey; }
    public WaypointCoordinate getCoordinate() { return coordinate; }
    public WaypointResolution getResolution() { return resolution; }
    public List<UncertaintyObservation> getUncertaintyObservations() {
        return uncertaintyObservations;
    }
    public boolean isEnabled() { return enabled; }
    public MarkerStyle getMarkerStyle() { return markerStyle; }
    public int getArrivalRadiusMetres() { return arrivalRadiusMetres; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isTemporary() { return expiresAt != null; }
    public String getGroup() { return group; }
    public Set<String> getTags() { return tags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastResolvedAt() { return lastResolvedAt; }
    public Map<String, List<String>> getExtensions() { return extensions; }

    public WaypointRecord withEnabled(boolean value, Instant changedAt) {
        return copyOf(this).enabled(value).updatedAt(changedAt).build();
    }

    public WaypointRecord duplicate(String duplicateName, Instant now) {
        return copyOf(this).id(UUID.randomUUID()).name(duplicateName)
                .createdAt(now).updatedAt(now).build();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutableList(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values == null
                ? Collections.<T>emptyList() : values));
    }

    private static Set<String> immutableTags(Set<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        if (values != null) for (String value : values) {
            String tag = clean(value);
            if (!tag.isEmpty()) result.add(tag);
        }
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, List<String>> immutableExtensions(
            Map<String, List<String>> values) {
        LinkedHashMap<String, List<String>> result =
                new LinkedHashMap<String, List<String>>();
        if (values != null) for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<String>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    public static final class Builder {
        private int modelVersion = MODEL_VERSION;
        private UUID id = UUID.randomUUID();
        private String name = "";
        private String description = "";
        private String createdByUser = "";
        private ServerIdentity serverIdentity;
        private WaypointSourceType sourceType = WaypointSourceType.STATIC;
        private String sourceKey = "";
        private WaypointCoordinate coordinate;
        private WaypointResolution resolution = WaypointResolution.STATIC_EXACT;
        private List<UncertaintyObservation> uncertaintyObservations =
                new ArrayList<UncertaintyObservation>();
        private boolean enabled = true;
        private MarkerStyle markerStyle = MarkerStyle.defaultColoredBeam();
        private int arrivalRadiusMetres = WaypointArrival.DISABLED;
        private Instant expiresAt;
        private String group = "";
        private Set<String> tags = new LinkedHashSet<String>();
        private Instant createdAt = Instant.now();
        private Instant updatedAt = createdAt;
        private Instant lastResolvedAt = createdAt;
        private Map<String, List<String>> extensions =
                new LinkedHashMap<String, List<String>>();

        private Builder() { }

        private Builder(WaypointRecord record) {
            modelVersion = record.modelVersion;
            id = record.id;
            name = record.name;
            description = record.description;
            createdByUser = record.createdByUser;
            serverIdentity = record.serverIdentity;
            sourceType = record.sourceType;
            sourceKey = record.sourceKey;
            coordinate = record.coordinate;
            resolution = record.resolution;
            uncertaintyObservations = new ArrayList<UncertaintyObservation>(
                    record.uncertaintyObservations);
            enabled = record.enabled;
            markerStyle = record.markerStyle;
            arrivalRadiusMetres = record.arrivalRadiusMetres;
            expiresAt = record.expiresAt;
            group = record.group;
            tags = new LinkedHashSet<String>(record.tags);
            createdAt = record.createdAt;
            updatedAt = record.updatedAt;
            lastResolvedAt = record.lastResolvedAt;
            extensions = new LinkedHashMap<String, List<String>>(record.extensions);
        }

        public Builder modelVersion(int value) { modelVersion = value; return this; }
        public Builder id(UUID value) { id = value; return this; }
        public Builder name(String value) { name = value; return this; }
        public Builder description(String value) { description = value; return this; }
        public Builder createdByUser(String value) { createdByUser = value; return this; }
        public Builder serverIdentity(ServerIdentity value) { serverIdentity = value; return this; }
        public Builder sourceType(WaypointSourceType value) { sourceType = value; return this; }
        public Builder sourceKey(String value) { sourceKey = value; return this; }
        public Builder coordinate(WaypointCoordinate value) { coordinate = value; return this; }
        public Builder resolution(WaypointResolution value) { resolution = value; return this; }
        public Builder uncertaintyObservations(List<UncertaintyObservation> values) {
            uncertaintyObservations = values == null
                    ? new ArrayList<UncertaintyObservation>()
                    : new ArrayList<UncertaintyObservation>(values);
            return this;
        }
        public Builder enabled(boolean value) { enabled = value; return this; }
        public Builder markerStyle(MarkerStyle value) { markerStyle = value; return this; }
        public Builder arrivalRadiusMetres(int value) {
            arrivalRadiusMetres = value;
            return this;
        }
        public Builder expiresAt(Instant value) { expiresAt = value; return this; }
        public Builder group(String value) { group = value; return this; }
        public Builder tags(Set<String> values) {
            tags = values == null ? new LinkedHashSet<String>()
                    : new LinkedHashSet<String>(values);
            return this;
        }
        public Builder createdAt(Instant value) { createdAt = value; return this; }
        public Builder updatedAt(Instant value) { updatedAt = value; return this; }
        public Builder lastResolvedAt(Instant value) { lastResolvedAt = value; return this; }
        public Builder extensions(Map<String, List<String>> values) {
            extensions = values == null ? new LinkedHashMap<String, List<String>>()
                    : new LinkedHashMap<String, List<String>>(values);
            return this;
        }
        public WaypointRecord build() { return new WaypointRecord(this); }
    }
}
