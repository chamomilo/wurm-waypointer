package org.waypoints.next.service;

import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds deterministic manager rows and filters entirely outside Wurm. */
public final class WaypointManagerViewService {
    public WaypointManagerSnapshot snapshot(List<WaypointRecord> records,
                                            WaypointManagerQuery query) {
        if (records == null) throw new IllegalArgumentException("records are required");
        if (query == null) throw new IllegalArgumentException("query is required");
        WaypointFilter filter = query.filter();
        List<WaypointManagerRow> rows = new ArrayList<WaypointManagerRow>();
        for (WaypointRecord record : records) {
            if (filter.matches(record)) rows.add(row(record, query));
        }
        Collections.sort(rows, comparator(query));
        return new WaypointManagerSnapshot(records.size(), rows,
                userOptions(records), serverOptions(records));
    }

    private static WaypointManagerRow row(WaypointRecord record,
                                          WaypointManagerQuery query) {
        ServerIdentity server = record.getServerIdentity();
        WaypointCoordinate coordinate = record.getCoordinate();
        Integer distance = distance(record, query);
        return new WaypointManagerRow(record.getId(), record.isEnabled(), record.getName(),
                record.getSourceType(), serverLabel(server),
                server == null ? "" : server.getEndpointFingerprint(),
                record.getCreatedByUser(), record.getResolution(), distance,
                record.getMarkerStyle().getWorldStyle(),
                coordinate == null ? 0.0d : coordinate.getTileX(),
                coordinate == null ? 0.0d : coordinate.getTileY(),
                record.getExpiresAt());
    }

    private static Integer distance(WaypointRecord record, WaypointManagerQuery query) {
        if (query.getOriginTileX() == null || record.getCoordinate() == null
                || query.getCurrentServer() == null || record.getServerIdentity() == null
                || !record.getServerIdentity().sameServer(query.getCurrentServer())) return null;
        return Integer.valueOf(WaypointDistance.metres(
                record.getCoordinate().getTileX(), record.getCoordinate().getTileY(),
                query.getOriginTileX(), query.getOriginTileY()));
    }

    private static Comparator<WaypointManagerRow> comparator(
            final WaypointManagerQuery query) {
        return new Comparator<WaypointManagerRow>() {
            @Override public int compare(WaypointManagerRow left,
                                         WaypointManagerRow right) {
                if (left.isSystemManaged() != right.isSystemManaged()) {
                    return left.isSystemManaged() ? -1 : 1;
                }
                if (left.isSystemManaged()) {
                    int systemOrder = systemOrder(left) - systemOrder(right);
                    if (systemOrder != 0) return systemOrder;
                }
                int value = compareColumn(left, right, query.getSortColumn());
                if (!query.isAscending()) value = -value;
                if (value != 0) return value;
                return left.getId().toString().compareTo(right.getId().toString());
            }
        };
    }

    private static int systemOrder(WaypointManagerRow row) {
        if (row.getWorldStyle()
                == org.waypoints.next.model.MarkerStyle.WorldStyle.WHITE_LIGHT) {
            return 0;
        }
        if (row.getWorldStyle()
                == org.waypoints.next.model.MarkerStyle.WorldStyle.BLACK_LIGHT) {
            return 1;
        }
        return 2;
    }

    private static int compareColumn(WaypointManagerRow left,
                                     WaypointManagerRow right,
                                     WaypointManagerQuery.SortColumn column) {
        switch (column) {
            case ENABLED: return Boolean.compare(left.isEnabled(), right.isEnabled());
            case NAME: return text(left.getName(), right.getName());
            case TYPE: return left.getSourceType().compareTo(right.getSourceType());
            case SERVER: return text(left.getServerLabel(), right.getServerLabel());
            case USER: return text(left.getUser(), right.getUser());
            case STATUS: return left.getResolution().compareTo(right.getResolution());
            case DISTANCE: return nullableDistance(left.getDistanceMetres(), right.getDistanceMetres());
            case STYLE: return left.getWorldStyle().compareTo(right.getWorldStyle());
            case ID: return left.getId().toString().compareTo(right.getId().toString());
            default: throw new IllegalStateException("unsupported sort column: " + column);
        }
    }

    private static int nullableDistance(Integer left, Integer right) {
        if (left == null && right == null) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        return left.compareTo(right);
    }

    private static int text(String left, String right) {
        return left.toLowerCase(Locale.ENGLISH)
                .compareTo(right.toLowerCase(Locale.ENGLISH));
    }

    private static List<WaypointFilterOption> userOptions(List<WaypointRecord> records) {
        Map<String, String> unique = new LinkedHashMap<String, String>();
        for (WaypointRecord record : records) {
            if (record.getSourceType()
                    == org.waypoints.next.model.WaypointSourceType.VANILLA_SYSTEM) {
                continue;
            }
            String user = clean(record.getCreatedByUser());
            if (!user.isEmpty()) unique.put(user.toLowerCase(Locale.ENGLISH), user);
        }
        List<String> labels = new ArrayList<String>(unique.values());
        Collections.sort(labels, String.CASE_INSENSITIVE_ORDER);
        List<WaypointFilterOption> result = new ArrayList<WaypointFilterOption>();
        for (String value : labels) result.add(new WaypointFilterOption(value, value));
        return result;
    }

    private static List<WaypointFilterOption> serverOptions(List<WaypointRecord> records) {
        Map<String, String> unique = new LinkedHashMap<String, String>();
        for (WaypointRecord record : records) {
            ServerIdentity server = record.getServerIdentity();
            if (server == null || server.getEndpointFingerprint().isEmpty()) continue;
            String fingerprint = server.getEndpointFingerprint();
            unique.put(fingerprint.toLowerCase(Locale.ENGLISH),
                    serverLabel(server) + " [" + fingerprint + "]");
        }
        List<WaypointFilterOption> result = new ArrayList<WaypointFilterOption>();
        for (Map.Entry<String, String> entry : unique.entrySet()) {
            result.add(new WaypointFilterOption(entry.getValue(), entry.getKey()));
        }
        Collections.sort(result, new Comparator<WaypointFilterOption>() {
            @Override public int compare(WaypointFilterOption left,
                                         WaypointFilterOption right) {
                return text(left.getLabel(), right.getLabel());
            }
        });
        return result;
    }

    private static String serverLabel(ServerIdentity server) {
        if (server == null) return "Unassigned";
        String shortName = clean(server.getShortName());
        if (!shortName.isEmpty()) return shortName;
        String fullName = clean(server.getFullName());
        if (!fullName.isEmpty()) return fullName;
        String endpoint = clean(server.getEndpointFingerprint());
        return endpoint.isEmpty() ? "Unassigned" : endpoint;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
