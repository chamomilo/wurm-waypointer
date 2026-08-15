package org.waypoints.next.persistence;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.UncertaintyObservation;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;
import org.waypoints.next.model.WaypointResolution;
import org.waypoints.next.model.WaypointSourceType;
import org.waypoints.next.validation.WaypointLimits;
import org.waypoints.next.validation.WaypointRecordValidator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic, line-oriented WURM-WAYPOINTER format shared by store and transfer files. */
public final class WaypointFormatCodec {
    public static final String MAGIC = "WURM-WAYPOINTER\t1";
    private static final String BEGIN = "BEGIN WAYPOINT";
    private static final String END = "END WAYPOINT";

    private final WaypointRecordValidator validator;

    public WaypointFormatCodec(WaypointRecordValidator validator) {
        if (validator == null) throw new IllegalArgumentException("validator is required");
        this.validator = validator;
    }

    public byte[] encode(WaypointDocument document) throws IOException {
        if (document == null) throw new IllegalArgumentException("document is required");
        validator.validateAll(document.getRecords());
        if (document.getRecords().size() + document.getOpaqueRecords().size()
                > WaypointLimits.MAX_RECORDS) throw new IOException("too many waypoint records");
        StringBuilder out = new StringBuilder();
        out.append(MAGIC).append('\n');
        for (WaypointRecord record : document.getRecords()) writeRecord(out, record);
        for (OpaqueWaypointRecord opaque : document.getOpaqueRecords()) {
            out.append(BEGIN).append('\n');
            for (String line : opaque.getBodyLines()) {
                if (line == null || line.indexOf('\r') >= 0 || line.indexOf('\n') >= 0) {
                    throw new IOException("invalid opaque record line");
                }
                out.append(line).append('\n');
            }
            out.append(END).append('\n');
        }
        byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > WaypointLimits.MAX_FILE_BYTES) {
            throw new IOException("waypoint document exceeds maximum size");
        }
        return bytes;
    }

    public WaypointDocument decode(byte[] bytes) throws IOException {
        if (bytes == null) throw new IllegalArgumentException("document bytes are required");
        if (bytes.length > WaypointLimits.MAX_FILE_BYTES) {
            throw new IOException("waypoint document exceeds maximum size");
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lines.length == 0 || !MAGIC.equals(lines[0])) {
            throw new IOException("not a WURM-WAYPOINTER schema 1 document");
        }
        List<WaypointRecord> records = new ArrayList<WaypointRecord>();
        List<OpaqueWaypointRecord> opaque = new ArrayList<OpaqueWaypointRecord>();
        int index = 1;
        while (index < lines.length) {
            String line = lines[index++];
            if (line.trim().isEmpty() || line.startsWith("#")) continue;
            if (!BEGIN.equals(line)) throw new IOException("unexpected line before record: " + line);
            List<String> body = new ArrayList<String>();
            while (index < lines.length && !END.equals(lines[index])) body.add(lines[index++]);
            if (index >= lines.length) throw new IOException("unterminated waypoint record");
            index++;
            if (body.isEmpty()) throw new IOException("empty waypoint record");
            try {
                records.add(readRecord(body));
            } catch (UnsupportedRecord unsupported) {
                opaque.add(new OpaqueWaypointRecord(body));
            }
            if (records.size() + opaque.size() > WaypointLimits.MAX_RECORDS) {
                throw new IOException("too many waypoint records");
            }
        }
        validator.validateAll(records);
        return new WaypointDocument(records, opaque);
    }

    private void writeRecord(StringBuilder out, WaypointRecord record) throws IOException {
        out.append(BEGIN).append('\n');
        line(out, "modelVersion", Integer.toString(record.getModelVersion()));
        line(out, "id", record.getId().toString());
        encoded(out, "name", record.getName());
        encoded(out, "description", record.getDescription());
        encoded(out, "createdByUser", record.getCreatedByUser());
        ServerIdentity server = record.getServerIdentity();
        line(out, "server.present", Boolean.toString(server != null));
        if (server != null) {
            ServerEndpoint endpoint = server.getEndpoint();
            line(out, "server.endpointPresent", Boolean.toString(endpoint != null));
            if (endpoint != null) {
                encoded(out, "server.host", endpoint.getHost());
                line(out, "server.gamePort", Integer.toString(endpoint.getGamePort()));
                line(out, "server.queryPort", endpoint.getQueryPort() == null ? ""
                        : Integer.toString(endpoint.getQueryPort().intValue()));
            }
            encoded(out, "server.fullName", server.getFullName());
            encoded(out, "server.shortName", server.getShortName());
            line(out, "server.resolution", server.getResolution().name());
            for (String alias : server.getAliases()) encoded(out, "server.alias", alias);
        }
        line(out, "sourceType", record.getSourceType().name());
        encoded(out, "sourceKey", record.getSourceKey());
        WaypointCoordinate coordinate = record.getCoordinate();
        line(out, "coordinate.present", Boolean.toString(coordinate != null));
        if (coordinate != null) {
            line(out, "coordinate.tileX", Double.toString(coordinate.getTileX()));
            line(out, "coordinate.tileY", Double.toString(coordinate.getTileY()));
            line(out, "coordinate.height", coordinate.getHeight() == null ? ""
                    : Double.toString(coordinate.getHeight().doubleValue()));
            line(out, "coordinate.layer", coordinate.getLayer().name());
        }
        line(out, "resolution", record.getResolution().name());
        for (UncertaintyObservation observation : record.getUncertaintyObservations()) {
            line(out, "observation", Double.toString(observation.getBearingDegrees()) + ","
                    + Double.toString(observation.getHalfWidthDegrees()) + ","
                    + Double.toString(observation.getMinimumTiles()) + ","
                    + Double.toString(observation.getMaximumTiles()) + ","
                    + Long.toString(observation.getObservedAt().toEpochMilli()));
        }
        line(out, "enabled", Boolean.toString(record.isEnabled()));
        MarkerStyle style = record.getMarkerStyle();
        line(out, "style.world", style.getWorldStyle().name());
        line(out, "style.red", Float.toString(style.getRed()));
        line(out, "style.green", Float.toString(style.getGreen()));
        line(out, "style.blue", Float.toString(style.getBlue()));
        line(out, "style.alpha", Float.toString(style.getAlpha()));
        line(out, "style.markerSize", Float.toString(style.getMarkerSize()));
        line(out, "style.beamWidth", Float.toString(style.getBeamWidth()));
        line(out, "style.showLabel", Boolean.toString(style.isShowLabel()));
        line(out, "style.showDistance", Boolean.toString(style.isShowDistance()));
        line(out, "arrivalRadiusMetres", Integer.toString(
                record.getArrivalRadiusMetres()));
        line(out, "expiresAt", record.getExpiresAt() == null ? ""
                : Long.toString(record.getExpiresAt().toEpochMilli()));
        encoded(out, "group", record.getGroup());
        for (String tag : record.getTags()) encoded(out, "tag", tag);
        line(out, "createdAt", Long.toString(record.getCreatedAt().toEpochMilli()));
        line(out, "updatedAt", Long.toString(record.getUpdatedAt().toEpochMilli()));
        line(out, "lastResolvedAt", record.getLastResolvedAt() == null ? ""
                : Long.toString(record.getLastResolvedAt().toEpochMilli()));
        for (Map.Entry<String, List<String>> extension : record.getExtensions().entrySet()) {
            requireFieldName(extension.getKey());
            for (String value : extension.getValue()) line(out, extension.getKey(), value);
        }
        out.append(END).append('\n');
    }

    private WaypointRecord readRecord(List<String> body) throws IOException, UnsupportedRecord {
        Fields fields = Fields.parse(body);
        int modelVersion = integer(fields.one("modelVersion"), "modelVersion");
        if (modelVersion != WaypointRecord.MODEL_VERSION) throw new UnsupportedRecord();
        UUID id;
        try { id = UUID.fromString(fields.one("id")); }
        catch (IllegalArgumentException invalid) { throw new IOException("invalid waypoint UUID", invalid); }
        String name = decode(fields.one("name"), "name");
        String description = decode(fields.one("description"), "description");
        String user = decode(fields.one("createdByUser"), "createdByUser");
        ServerIdentity server = readServer(fields);
        WaypointSourceType sourceType = enumValue(WaypointSourceType.class,
                fields.one("sourceType"));
        String sourceKey = decode(fields.one("sourceKey"), "sourceKey");
        WaypointCoordinate coordinate = readCoordinate(fields);
        WaypointResolution resolution = enumValue(WaypointResolution.class,
                fields.one("resolution"));
        List<UncertaintyObservation> observations = readObservations(fields.many("observation"));
        boolean enabled = bool(fields.one("enabled"), "enabled");
        MarkerStyle style = readStyle(fields);
        String arrivalValue = fields.optionalOne("arrivalRadiusMetres");
        int arrivalRadiusMetres = arrivalValue == null ? 0
                : integer(arrivalValue, "arrivalRadiusMetres");
        String expiryValue = fields.optionalOne("expiresAt");
        Instant expiresAt = expiryValue == null || expiryValue.isEmpty() ? null
                : instant(expiryValue, "expiresAt");
        String group = decode(fields.one("group"), "group");
        Set<String> tags = new LinkedHashSet<String>();
        for (String value : fields.many("tag")) tags.add(decode(value, "tag"));
        Instant createdAt = instant(fields.one("createdAt"), "createdAt");
        Instant updatedAt = instant(fields.one("updatedAt"), "updatedAt");
        String resolved = fields.one("lastResolvedAt");
        Instant lastResolvedAt = resolved.isEmpty() ? null : instant(resolved, "lastResolvedAt");

        WaypointRecord record = WaypointRecord.builder().modelVersion(modelVersion).id(id)
                .name(name).description(description).createdByUser(user)
                .serverIdentity(server).sourceType(sourceType).sourceKey(sourceKey)
                .coordinate(coordinate).resolution(resolution)
                .uncertaintyObservations(observations).enabled(enabled).markerStyle(style)
                .arrivalRadiusMetres(arrivalRadiusMetres)
                .expiresAt(expiresAt)
                .group(group).tags(tags).createdAt(createdAt).updatedAt(updatedAt)
                .lastResolvedAt(lastResolvedAt).extensions(fields.remaining()).build();
        try { validator.validate(record); }
        catch (IllegalArgumentException invalid) {
            throw new IOException("invalid waypoint record " + id + ": " + invalid.getMessage(), invalid);
        }
        return record;
    }

    private ServerIdentity readServer(Fields fields) throws IOException, UnsupportedRecord {
        if (!bool(fields.one("server.present"), "server.present")) return null;
        ServerEndpoint endpoint = null;
        if (bool(fields.one("server.endpointPresent"), "server.endpointPresent")) {
            String host = decode(fields.one("server.host"), "server.host");
            int gamePort = integer(fields.one("server.gamePort"), "server.gamePort");
            String query = fields.one("server.queryPort");
            endpoint = new ServerEndpoint(host, gamePort,
                    query.isEmpty() ? null : Integer.valueOf(integer(query, "server.queryPort")));
        }
        String fullName = decode(fields.one("server.fullName"), "server.fullName");
        String shortName = decode(fields.one("server.shortName"), "server.shortName");
        ServerIdentity.Resolution resolution = enumValue(ServerIdentity.Resolution.class,
                fields.one("server.resolution"));
        List<String> aliases = new ArrayList<String>();
        for (String alias : fields.many("server.alias")) aliases.add(decode(alias, "server.alias"));
        return ServerIdentity.restored(endpoint, fullName, shortName, aliases, resolution);
    }

    private WaypointCoordinate readCoordinate(Fields fields) throws IOException, UnsupportedRecord {
        if (!bool(fields.one("coordinate.present"), "coordinate.present")) return null;
        double x = decimal(fields.one("coordinate.tileX"), "coordinate.tileX");
        double y = decimal(fields.one("coordinate.tileY"), "coordinate.tileY");
        String heightValue = fields.one("coordinate.height");
        Double height = heightValue.isEmpty() ? null
                : Double.valueOf(decimal(heightValue, "coordinate.height"));
        WaypointLayer layer = enumValue(WaypointLayer.class, fields.one("coordinate.layer"));
        return new WaypointCoordinate(x, y, height, layer);
    }

    private MarkerStyle readStyle(Fields fields) throws IOException, UnsupportedRecord {
        return new MarkerStyle(enumValue(MarkerStyle.WorldStyle.class, fields.one("style.world")),
                real(fields.one("style.red"), "style.red"),
                real(fields.one("style.green"), "style.green"),
                real(fields.one("style.blue"), "style.blue"),
                real(fields.one("style.alpha"), "style.alpha"),
                real(fields.one("style.markerSize"), "style.markerSize"),
                real(fields.one("style.beamWidth"), "style.beamWidth"),
                bool(fields.one("style.showLabel"), "style.showLabel"),
                bool(fields.one("style.showDistance"), "style.showDistance"));
    }

    private static List<UncertaintyObservation> readObservations(List<String> values)
            throws IOException {
        List<UncertaintyObservation> result = new ArrayList<UncertaintyObservation>();
        for (String value : values) {
            String[] parts = value.split(",", -1);
            if (parts.length != 5) throw new IOException("invalid uncertainty observation");
            result.add(new UncertaintyObservation(decimal(parts[0], "observation bearing"),
                    decimal(parts[1], "observation width"),
                    decimal(parts[2], "observation minimum"),
                    decimal(parts[3], "observation maximum"),
                    instant(parts[4], "observation time")));
        }
        return result;
    }

    private static void line(StringBuilder out, String key, String value) throws IOException {
        requireFieldName(key);
        if (value == null || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IOException("invalid value for " + key);
        }
        out.append(key).append('=').append(value).append('\n');
    }

    private static void encoded(StringBuilder out, String key, String value) throws IOException {
        line(out, key, Base64.getUrlEncoder().withoutPadding().encodeToString(
                (value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
    }

    private static String decode(String value, String field) throws IOException {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid base64 in " + field, invalid);
        }
    }

    private static int integer(String value, String field) throws IOException {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IOException("invalid integer in " + field, invalid); }
    }

    private static double decimal(String value, String field) throws IOException {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IOException("invalid decimal in " + field, invalid);
        }
    }

    private static float real(String value, String field) throws IOException {
        try {
            float parsed = Float.parseFloat(value);
            if (Float.isNaN(parsed) || Float.isInfinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IOException("invalid float in " + field, invalid);
        }
    }

    private static boolean bool(String value, String field) throws IOException {
        if ("true".equals(value)) return true;
        if ("false".equals(value)) return false;
        throw new IOException("invalid boolean in " + field);
    }

    private static Instant instant(String value, String field) throws IOException {
        try { return Instant.ofEpochMilli(Long.parseLong(value)); }
        catch (RuntimeException invalid) { throw new IOException("invalid timestamp in " + field, invalid); }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value)
            throws UnsupportedRecord {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException unknown) { throw new UnsupportedRecord(); }
    }

    private static void requireFieldName(String key) throws IOException {
        if (key == null || !key.matches("[A-Za-z0-9_.-]{1,120}")) {
            throw new IOException("invalid waypoint field name");
        }
    }

    private static final class UnsupportedRecord extends Exception { }

    private static final class Fields {
        private final LinkedHashMap<String, List<String>> values;

        private Fields(LinkedHashMap<String, List<String>> values) { this.values = values; }

        static Fields parse(List<String> lines) throws IOException {
            LinkedHashMap<String, List<String>> values =
                    new LinkedHashMap<String, List<String>>();
            for (String line : lines) {
                int equals = line.indexOf('=');
                if (equals <= 0) throw new IOException("invalid waypoint field line");
                String key = line.substring(0, equals);
                requireFieldName(key);
                String value = line.substring(equals + 1);
                List<String> list = values.get(key);
                if (list == null) {
                    list = new ArrayList<String>();
                    values.put(key, list);
                }
                list.add(value);
            }
            return new Fields(values);
        }

        String one(String key) throws IOException {
            List<String> list = values.remove(key);
            if (list == null) throw new IOException("missing waypoint field " + key);
            if (list.size() != 1) throw new IOException("duplicate waypoint field " + key);
            return list.get(0);
        }

        String optionalOne(String key) throws IOException {
            List<String> list = values.remove(key);
            if (list == null) return null;
            if (list.size() != 1) throw new IOException(
                    "duplicate waypoint field " + key);
            return list.get(0);
        }

        List<String> many(String key) {
            List<String> list = values.remove(key);
            return list == null ? Collections.<String>emptyList() : list;
        }

        Map<String, List<String>> remaining() {
            return new LinkedHashMap<String, List<String>>(values);
        }
    }
}
