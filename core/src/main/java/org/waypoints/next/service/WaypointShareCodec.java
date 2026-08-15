package org.waypoints.next.service;

import org.waypoints.next.model.MarkerStyle;
import org.waypoints.next.model.ServerEndpoint;
import org.waypoints.next.model.ServerIdentity;
import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointArrival;
import org.waypoints.next.model.WaypointLayer;
import org.waypoints.next.model.WaypointRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** Compact one-way clipboard representation for sharing one exact waypoint. */
public final class WaypointShareCodec {
    public static final String PREFIX = "WWP1|";
    private static final String COMPACT_PREFIX = PREFIX + "C|";
    private static final int LEGACY_FIELD_COUNT = 20;
    private static final int ARRIVAL_FIELD_COUNT = 21;
    private static final int FIELD_COUNT = 22;
    private static final int MAXIMUM_TEXT_LENGTH = 4096;

    public static boolean containsSharedToken(String input) {
        return input != null && input.indexOf(PREFIX) >= 0;
    }

    public String encode(WaypointRecord record) {
        if (record == null || record.getServerIdentity() == null
                || record.getCoordinate() == null || record.getMarkerStyle() == null) {
            throw new IllegalArgumentException("only exact server waypoints can be shared");
        }
        ServerIdentity server = record.getServerIdentity();
        if (!server.isSafeForAutomaticRendering()) throw new IllegalArgumentException(
                "waypoint server endpoint is unresolved");
        return encodeCompact(record, server);
    }

    private static String encodeCompact(WaypointRecord record,
                                        ServerIdentity server) {
        ServerEndpoint endpoint = server.getEndpoint();
        WaypointCoordinate coordinate = record.getCoordinate();
        MarkerStyle style = record.getMarkerStyle();
        int flags = (endpoint.getQueryPort() == null ? 0 : 1)
                | (server.getFullName().isEmpty() ? 0 : 2)
                | (server.getShortName().isEmpty() ? 0 : 4)
                | (coordinate.getHeight() == null ? 0 : 8)
                | (coordinate.getLayer() == WaypointLayer.CAVE ? 16 : 0)
                | (style.isShowLabel() ? 32 : 0)
                | (style.isShowDistance() ? 64 : 0)
                | (record.getExpiresAt() == null ? 0 : 128);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(128);
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(flags);
            writeText(output, endpoint.getHost(), "server host");
            output.writeShort(endpoint.getGamePort());
            if (endpoint.getQueryPort() != null) {
                output.writeShort(endpoint.getQueryPort().intValue());
            }
            if (!server.getFullName().isEmpty()) {
                writeText(output, server.getFullName(), "full server name");
            }
            if (!server.getShortName().isEmpty()) {
                writeText(output, server.getShortName(), "short server name");
            }
            writeText(output, record.getName(), "name");
            output.writeDouble(coordinate.getTileX());
            output.writeDouble(coordinate.getTileY());
            if (coordinate.getHeight() != null) {
                output.writeDouble(coordinate.getHeight().doubleValue());
            }
            output.writeByte(style.getWorldStyle().ordinal());
            output.writeFloat(style.getRed());
            output.writeFloat(style.getGreen());
            output.writeFloat(style.getBlue());
            output.writeFloat(style.getAlpha());
            output.writeFloat(style.getMarkerSize());
            output.writeFloat(style.getBeamWidth());
            output.writeInt(WaypointArrival.requireRadius(
                    record.getArrivalRadiusMetres()));
            if (record.getExpiresAt() != null) {
                output.writeLong(record.getExpiresAt().toEpochMilli());
            }
            output.flush();
            return COMPACT_PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("unable to encode shared waypoint", impossible);
        }
    }

    public SharedWaypoint decode(String input) {
        if (input == null || input.length() > MAXIMUM_TEXT_LENGTH) {
            throw new IllegalArgumentException("shared waypoint text is missing or too long");
        }
        int start = input.indexOf(PREFIX);
        if (start < 0) throw new IllegalArgumentException(
                "clipboard does not contain a WWP1 shared waypoint");
        int end = start;
        while (end < input.length() && !Character.isWhitespace(input.charAt(end))) end++;
        String token = input.substring(start, end);
        if (token.startsWith(COMPACT_PREFIX)) return decodeCompact(token);
        String[] fields = token.split("\\|", -1);
        if ((fields.length != LEGACY_FIELD_COUNT
                && fields.length != ARRIVAL_FIELD_COUNT
                && fields.length != FIELD_COUNT)
                || !"WWP1".equals(fields[0])) {
            throw new IllegalArgumentException("shared waypoint has an invalid field count");
        }
        String host = decodedText(fields[1], "server host");
        int gamePort = integer(fields[2], "game port");
        Integer queryPort = fields[3].isEmpty() ? null
                : Integer.valueOf(integer(fields[3], "query port"));
        ServerIdentity server = ServerIdentity.of(
                new ServerEndpoint(host, gamePort, queryPort),
                decodedOptionalText(fields[4], "full server name"),
                decodedOptionalText(fields[5], "short server name"),
                ServerIdentity.Resolution.RESOLVED);
        String name = decodedText(fields[6], "name");
        Double height = fields[9].isEmpty() ? null
                : Double.valueOf(real(fields[9], "height"));
        WaypointCoordinate coordinate = new WaypointCoordinate(
                real(fields[7], "tile X"), real(fields[8], "tile Y"), height,
                enumValue(WaypointLayer.class, fields[10], "layer"));
        MarkerStyle style = new MarkerStyle(
                enumValue(MarkerStyle.WorldStyle.class, fields[11], "world style"),
                decimal(fields[12], "red"), decimal(fields[13], "green"),
                decimal(fields[14], "blue"), decimal(fields[15], "alpha"),
                decimal(fields[16], "marker size"),
                decimal(fields[17], "beam width"),
                bit(fields[18], "show label"), bit(fields[19], "show distance"));
        int arrivalRadiusMetres = WaypointArrival.requireRadius(
                fields.length == LEGACY_FIELD_COUNT ? 0
                        : integer(fields[20], "arrival radius"));
        Instant expiresAt = fields.length < FIELD_COUNT || "0".equals(fields[21])
                ? null : Instant.ofEpochMilli(longInteger(fields[21], "expiry"));
        return new SharedWaypoint(server, name, coordinate, style,
                arrivalRadiusMetres, expiresAt);
    }

    private static SharedWaypoint decodeCompact(String token) {
        String encoded = token.substring(COMPACT_PREFIX.length());
        if (encoded.isEmpty()) throw new IllegalArgumentException(
                "compact shared waypoint payload is missing");
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            DataInputStream input = new DataInputStream(
                    new ByteArrayInputStream(bytes));
            int flags = input.readUnsignedByte();
            String host = readText(input, "server host", false);
            int gamePort = input.readUnsignedShort();
            Integer queryPort = (flags & 1) == 0 ? null
                    : Integer.valueOf(input.readUnsignedShort());
            String fullName = (flags & 2) == 0 ? ""
                    : readText(input, "full server name", true);
            String shortName = (flags & 4) == 0 ? ""
                    : readText(input, "short server name", true);
            String name = readText(input, "name", false);
            double tileX = input.readDouble();
            double tileY = input.readDouble();
            Double height = (flags & 8) == 0 ? null
                    : Double.valueOf(input.readDouble());
            MarkerStyle.WorldStyle[] styles = MarkerStyle.WorldStyle.values();
            int styleIndex = input.readUnsignedByte();
            if (styleIndex >= styles.length) throw new IllegalArgumentException(
                    "compact shared waypoint world style is invalid");
            MarkerStyle style = new MarkerStyle(styles[styleIndex],
                    input.readFloat(), input.readFloat(), input.readFloat(),
                    input.readFloat(), input.readFloat(), input.readFloat(),
                    (flags & 32) != 0, (flags & 64) != 0);
            int arrivalRadiusMetres = WaypointArrival.requireRadius(input.readInt());
            Instant expiresAt = (flags & 128) == 0 ? null
                    : Instant.ofEpochMilli(positiveLong(input.readLong(), "expiry"));
            if (input.available() != 0) throw new IllegalArgumentException(
                    "compact shared waypoint has trailing data");
            ServerIdentity server = ServerIdentity.of(
                    new ServerEndpoint(host, gamePort, queryPort),
                    fullName, shortName, ServerIdentity.Resolution.RESOLVED);
            WaypointCoordinate coordinate = new WaypointCoordinate(
                    tileX, tileY, height,
                    (flags & 16) == 0 ? WaypointLayer.SURFACE : WaypointLayer.CAVE);
            return new SharedWaypoint(server, name, coordinate, style,
                    arrivalRadiusMetres, expiresAt);
        } catch (EOFException truncated) {
            throw new IllegalArgumentException(
                    "compact shared waypoint was truncated by chat", truncated);
        } catch (IOException | IllegalArgumentException invalid) {
            if (invalid instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) invalid;
            }
            throw new IllegalArgumentException(
                    "compact shared waypoint is invalid", invalid);
        }
    }

    private static void writeText(DataOutputStream output, String value,
                                  String label) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 65535) throw new IllegalArgumentException(
                "shared waypoint " + label + " is too long");
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readText(DataInputStream input, String label,
                                   boolean allowEmpty) throws IOException {
        int length = input.readUnsignedShort();
        if (length > input.available()) throw new EOFException(label);
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8).trim();
        if (!allowEmpty && value.isEmpty()) throw new IllegalArgumentException(
                "compact shared waypoint " + label + " is empty");
        return value;
    }

    private static long positiveLong(long value, String label) {
        if (value <= 0L) throw new IllegalArgumentException(
                "compact shared waypoint " + label + " is invalid");
        return value;
    }

    private static String decodedText(String value, String label) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8).trim();
            if (decoded.isEmpty()) throw new IllegalArgumentException(
                    "shared waypoint " + label + " is empty");
            return decoded;
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "shared waypoint " + label + " is invalid", invalid);
        }
    }

    private static String decodedOptionalText(String value, String label) {
        if (value.isEmpty()) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException(
                    "shared waypoint " + label + " is invalid", invalid);
        }
    }

    private static double real(String value, String label) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("shared waypoint " + label + " is invalid");
        }
    }

    private static float decimal(String value, String label) {
        return (float) real(value, label);
    }

    private static int integer(String value, String label) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("shared waypoint " + label + " is invalid");
        }
    }

    private static long longInteger(String value, String label) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed <= 0L) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("shared waypoint " + label
                    + " is invalid");
        }
    }

    private static boolean bit(String value, String label) {
        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        throw new IllegalArgumentException("shared waypoint " + label + " is invalid");
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value,
                                                    String label) {
        try { return Enum.valueOf(type, value); }
        catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("shared waypoint " + label + " is invalid");
        }
    }

    public static final class SharedWaypoint {
        private final ServerIdentity serverIdentity;
        private final String name;
        private final WaypointCoordinate coordinate;
        private final MarkerStyle markerStyle;
        private final int arrivalRadiusMetres;
        private final Instant expiresAt;

        private SharedWaypoint(ServerIdentity serverIdentity, String name,
                               WaypointCoordinate coordinate,
                               MarkerStyle markerStyle,
                               int arrivalRadiusMetres, Instant expiresAt) {
            this.serverIdentity = serverIdentity;
            this.name = name;
            this.coordinate = coordinate;
            this.markerStyle = markerStyle;
            this.arrivalRadiusMetres = arrivalRadiusMetres;
            this.expiresAt = expiresAt;
        }

        public ServerIdentity getServerIdentity() { return serverIdentity; }
        public String getName() { return name; }
        public WaypointCoordinate getCoordinate() { return coordinate; }
        public MarkerStyle getMarkerStyle() { return markerStyle; }
        public int getArrivalRadiusMetres() { return arrivalRadiusMetres; }
        public Instant getExpiresAt() { return expiresAt; }
    }
}
