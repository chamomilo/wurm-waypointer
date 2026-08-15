package org.waypoints.next.validation;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Central strict validation used by CRUD, storage and import. */
public final class WaypointRecordValidator {
    public void validateAll(List<WaypointRecord> records) {
        if (records == null) throw new IllegalArgumentException("records are required");
        if (records.size() > WaypointLimits.MAX_RECORDS) {
            throw new IllegalArgumentException("too many waypoint records");
        }
        Set<UUID> ids = new HashSet<UUID>();
        for (WaypointRecord record : records) {
            validate(record);
            if (!ids.add(record.getId())) {
                throw new IllegalArgumentException("duplicate waypoint id " + record.getId());
            }
        }
    }

    public void validate(WaypointRecord record) {
        if (record == null) throw new IllegalArgumentException("waypoint record is required");
        if (record.getModelVersion() != WaypointRecord.MODEL_VERSION) {
            throw new IllegalArgumentException("unsupported waypoint model version "
                    + record.getModelVersion());
        }
        if (record.getId() == null) throw new IllegalArgumentException("waypoint id is required");
        required(record.getName(), WaypointLimits.MAX_NAME, "name");
        bounded(record.getDescription(), WaypointLimits.MAX_DESCRIPTION, "description");
        required(record.getCreatedByUser(), WaypointLimits.MAX_USER, "created by user");
        if (record.getSourceType() == null) throw new IllegalArgumentException("source type is required");
        bounded(record.getSourceKey(), WaypointLimits.MAX_SOURCE_KEY, "source key");
        if (record.getResolution() == null) throw new IllegalArgumentException("resolution is required");
        if (record.getMarkerStyle() == null) throw new IllegalArgumentException("marker style is required");
        boolean lootScroll = record.getMarkerStyle().getWorldStyle()
                == org.waypoints.next.model.MarkerStyle.WorldStyle.LOOT_MAP_SCROLL;
        boolean archaeologyScroll = record.getMarkerStyle().getWorldStyle()
                == org.waypoints.next.model.MarkerStyle.WorldStyle.ARCHAEOLOGY_REPORT_SCROLL;
        boolean surroundingsExclamation = record.getMarkerStyle().getWorldStyle()
                == org.waypoints.next.model.MarkerStyle.WorldStyle.EXCLAMATION;
        boolean surroundingsSource = record.getSourceType()
                == WaypointSourceType.MANAGED_ANIMAL
                || record.getSourceType() == WaypointSourceType.MANAGED_ITEM;
        if (lootScroll && record.getSourceType() != WaypointSourceType.LOOT_MAP) {
            throw new IllegalArgumentException(
                    "loot map scroll style is reserved for Loot Map waypoints");
        }
        if (record.getSourceType() == WaypointSourceType.LOOT_MAP && !lootScroll) {
            throw new IllegalArgumentException(
                    "Loot Map waypoints require their reserved scroll style");
        }
        if (archaeologyScroll
                && record.getSourceType() != WaypointSourceType.ARCHAEOLOGY_REPORT) {
            throw new IllegalArgumentException(
                    "archaeology report scroll style is reserved for archaeology waypoints");
        }
        if (record.getSourceType() == WaypointSourceType.ARCHAEOLOGY_REPORT
                && !archaeologyScroll) {
            throw new IllegalArgumentException(
                    "archaeology waypoints require their reserved report scroll style");
        }
        if (surroundingsExclamation && !surroundingsSource) {
            throw new IllegalArgumentException(
                    "exclamation style is reserved for Surroundings marks");
        }
        if (record.getSourceType() == WaypointSourceType.ARCHAEOLOGY_REPORT) {
            if (record.getResolution() != WaypointResolution.PENDING
                    && record.getResolution() != WaypointResolution.SEARCH_STEP
                    && record.getResolution() != WaypointResolution.EXACT_SAVED) {
                throw new IllegalArgumentException(
                        "archaeology waypoints require a report-tracker resolution");
            }
            if (record.getCoordinate() == null) {
                throw new IllegalArgumentException(
                        "archaeology report waypoints require coordinates");
            }
        } else if (record.getResolution() == WaypointResolution.SEARCH_STEP
                || record.getResolution() == WaypointResolution.EXACT_SAVED) {
            throw new IllegalArgumentException(
                    "archaeology resolutions are reserved for archaeology waypoints");
        }
        WaypointArrival.requireRadius(record.getArrivalRadiusMetres());
        bounded(record.getGroup(), WaypointLimits.MAX_GROUP, "group");
        if (record.getCreatedAt() == null || record.getUpdatedAt() == null) {
            throw new IllegalArgumentException("created and updated timestamps are required");
        }
        if (record.getUpdatedAt().isBefore(record.getCreatedAt())) {
            throw new IllegalArgumentException("updated timestamp precedes creation");
        }
        if (record.getExpiresAt() != null) {
            record.getExpiresAt().toEpochMilli();
            if (record.getExpiresAt().isBefore(record.getCreatedAt())) {
                throw new IllegalArgumentException("expiry timestamp precedes creation");
            }
            if (record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM) {
                throw new IllegalArgumentException(
                        "managed vanilla landmarks cannot expire");
            }
        }
        validateServer(record.getServerIdentity());
        validateTags(record);
        validateExtensions(record);
        if (record.getUncertaintyObservations().size() > WaypointLimits.MAX_OBSERVATIONS) {
            throw new IllegalArgumentException("too many uncertainty observations");
        }
        if (record.getSourceType() == WaypointSourceType.STATIC
                || record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM) {
            if (record.getCoordinate() == null) {
                throw new IllegalArgumentException("exact waypoint requires coordinates");
            }
            if (record.getResolution() != WaypointResolution.STATIC_EXACT) {
                throw new IllegalArgumentException("exact waypoint must be STATIC_EXACT");
            }
            if (record.getSourceType() == WaypointSourceType.STATIC
                    && !record.getSourceKey().isEmpty()) {
                throw new IllegalArgumentException("static waypoint cannot have a source key");
            }
            if (record.getSourceType() == WaypointSourceType.VANILLA_SYSTEM
                    && record.getSourceKey().isEmpty()) {
                throw new IllegalArgumentException(
                        "vanilla system waypoint requires a source key");
            }
        }
        if ((record.getResolution() == WaypointResolution.STATIC_EXACT
                || record.getResolution() == WaypointResolution.LIVE_EXACT
                || record.getResolution() == WaypointResolution.LAST_SEEN)
                && record.getCoordinate() == null) {
            throw new IllegalArgumentException("exact or last-seen resolution requires coordinates");
        }
        if (record.getResolution() == WaypointResolution.SERVER_BEARING
                && record.getUncertaintyObservations().isEmpty()) {
            throw new IllegalArgumentException("server bearing requires an observation");
        }
    }

    private static void validateServer(ServerIdentity server) {
        if (server == null) return; // Explicitly unassigned records are supported.
        bounded(server.getFullName(), WaypointLimits.MAX_SERVER_NAME, "server full name");
        bounded(server.getShortName(), WaypointLimits.MAX_SERVER_NAME, "server short name");
        if (server.getAliases().size() > WaypointLimits.MAX_ALIASES) {
            throw new IllegalArgumentException("too many server aliases");
        }
        for (String alias : server.getAliases()) {
            bounded(alias, WaypointLimits.MAX_SERVER_NAME, "server alias");
        }
        if (server.getEndpoint() == null && server.getResolution() == ServerIdentity.Resolution.RESOLVED) {
            throw new IllegalArgumentException("resolved server requires an endpoint");
        }
    }

    private static void validateTags(WaypointRecord record) {
        if (record.getTags().size() > WaypointLimits.MAX_TAGS) {
            throw new IllegalArgumentException("too many tags");
        }
        for (String tag : record.getTags()) required(tag, WaypointLimits.MAX_TAG, "tag");
    }

    private static void validateExtensions(WaypointRecord record) {
        if (record.getExtensions().size() > WaypointLimits.MAX_EXTENSION_FIELDS) {
            throw new IllegalArgumentException("too many extension fields");
        }
        for (Map.Entry<String, List<String>> entry : record.getExtensions().entrySet()) {
            required(entry.getKey(), 120, "extension key");
            for (String value : entry.getValue()) {
                bounded(value, WaypointLimits.MAX_EXTENSION_VALUE, "extension value");
            }
        }
    }

    private static void required(String value, int maximum, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required");
        }
        bounded(value, maximum, label);
    }

    private static void bounded(String value, int maximum, String label) {
        if (value != null && value.length() > maximum) {
            throw new IllegalArgumentException(label + " is too long");
        }
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException(label + " must be one line");
        }
    }
}
