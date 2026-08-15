package org.waypoints.next.integration;

import org.waypoints.next.source.CoordinateInputParser;
import org.waypoints.next.source.MapBounds;
import org.waypoints.next.source.ParsedCoordinate;

/** Pure parsing helper for /wp add so Wurm command tokenization stays at the edge. */
public final class WaypointCommandArguments {
    public static final class AddRequest {
        private final String name;
        private final ParsedCoordinate coordinate;

        private AddRequest(String name, ParsedCoordinate coordinate) {
            this.name = name;
            this.coordinate = coordinate;
        }

        public String getName() { return name; }
        public ParsedCoordinate getCoordinate() { return coordinate; }
    }

    private WaypointCommandArguments() { }

    /** Wurm includes the command itself at data[0]; tests/tools may omit it. */
    public static String[] withoutRepeatedCommand(String command, String[] arguments) {
        if (arguments == null || arguments.length == 0) return new String[0];
        String expected = normalizeCommand(command);
        if (!normalizeCommand(arguments[0]).equalsIgnoreCase(expected)) return arguments;
        String[] result = new String[arguments.length - 1];
        System.arraycopy(arguments, 1, result, 0, result.length);
        return result;
    }

    public static AddRequest parseAdd(String[] arguments, CoordinateInputParser parser,
                                      MapBounds bounds) {
        if (arguments == null || arguments.length < 3) {
            throw new IllegalArgumentException("usage: /wp add <name> <x> <y|map-link>");
        }
        String joined = join(arguments, 1, arguments.length);
        int separator = joined.lastIndexOf('|');
        if (separator > 0 && separator + 1 < joined.length()) {
            return request(joined.substring(0, separator), joined.substring(separator + 1),
                    parser, bounds);
        }

        // Prefer the required /wp add <name> <x> <y> form.
        String twoTokens = arguments[arguments.length - 2] + " "
                + arguments[arguments.length - 1];
        try {
            return request(join(arguments, 1, arguments.length - 2), twoTokens,
                    parser, bounds);
        } catch (IllegalArgumentException notTwoCoordinates) {
            // A map URL or #X_Y occupies one token; preserve names with spaces.
            return request(join(arguments, 1, arguments.length - 1),
                    arguments[arguments.length - 1], parser, bounds);
        }
    }

    public static String join(String[] values, int start, int end) {
        StringBuilder result = new StringBuilder();
        if (values != null) for (int i = start; i < end && i < values.length; i++) {
            if (result.length() > 0) result.append(' ');
            result.append(values[i]);
        }
        return result.toString().trim();
    }

    private static AddRequest request(String name, String coordinates,
                                      CoordinateInputParser parser, MapBounds bounds) {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) throw new IllegalArgumentException("waypoint name is required");
        return new AddRequest(cleanName, parser.parse(coordinates, bounds));
    }

    private static String normalizeCommand(String value) {
        String clean = value == null ? "" : value.trim();
        return clean.startsWith("/") ? clean.substring(1) : clean;
    }
}
