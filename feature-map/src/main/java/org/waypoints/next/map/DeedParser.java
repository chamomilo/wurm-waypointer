package org.waypoints.next.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded parser for the JavaScript-wrapped Sklotopolis deeds array. */
public final class DeedParser {
    private static final int MAXIMUM_CHARACTERS = 2_000_000;
    private static final int MAXIMUM_DEEDS = 10_000;
    private static final Pattern OBJECT = Pattern.compile("\\{([^{}]+)\\}");
    private static final Pattern FIELD = Pattern.compile(
            "\\\"([A-Za-z][A-Za-z0-9]*)\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"|(-?\\d+)|(true|false))");

    private DeedParser() { }

    public static List<Deed> parse(String source, int mapWidth, int mapHeight) {
        if (source == null || source.length() > MAXIMUM_CHARACTERS) {
            throw new IllegalArgumentException("deed input is missing or oversized");
        }
        if (mapWidth < 1 || mapHeight < 1) throw new IllegalArgumentException(
                "map bounds must be positive");
        int arrayStart = source.indexOf('[');
        int arrayEnd = source.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd <= arrayStart) {
            throw new IllegalArgumentException("deed array is missing");
        }
        Matcher objects = OBJECT.matcher(source.substring(arrayStart + 1, arrayEnd));
        List<Deed> result = new ArrayList<Deed>();
        while (objects.find()) {
            if (result.size() >= MAXIMUM_DEEDS) throw new IllegalArgumentException(
                    "too many deeds");
            Fields fields = new Fields();
            Matcher matcher = FIELD.matcher(objects.group(1));
            while (matcher.find()) {
                fields.accept(matcher.group(1), matcher.group(2),
                        matcher.group(3), matcher.group(4));
            }
            Deed deed = fields.build(mapWidth, mapHeight);
            result.add(deed);
        }
        return Collections.unmodifiableList(result);
    }

    private static final class Fields {
        private String name;
        private String type = "";
        private String mayor = "";
        private String allianceName = "";
        private String founderName = "";
        private String motto = "";
        private String lastActive = "";
        private int guards;
        private int citizens;
        private long creationDate;
        private Integer x;
        private Integer y;
        private Integer north;
        private Integer south;
        private Integer east;
        private Integer west;
        private int perimeter;
        private boolean spawnPoint;

        void accept(String key, String stringValue, String numberValue,
                    String booleanValue) {
            if ("name".equals(key)) name = unescape(stringValue);
            else if ("type".equals(key)) type = unescape(stringValue);
            else if ("mayor".equals(key)) mayor = unescape(stringValue);
            else if ("allianceName".equals(key)) {
                allianceName = unescape(stringValue);
            } else if ("founderName".equals(key)) {
                founderName = unescape(stringValue);
            } else if ("motto".equals(key)) motto = unescape(stringValue);
            else if ("lastActive".equals(key)) {
                lastActive = unescape(stringValue);
            } else if ("guards".equals(key)) {
                guards = integer(numberValue).intValue();
            } else if ("amountOfCitizens".equals(key)) {
                citizens = integer(numberValue).intValue();
            } else if ("creationDate".equals(key)) {
                creationDate = longInteger(numberValue);
            }
            else if ("x".equals(key)) x = integer(numberValue);
            else if ("y".equals(key)) y = integer(numberValue);
            else if ("tilesNorth".equals(key)) north = integer(numberValue);
            else if ("tilesSouth".equals(key)) south = integer(numberValue);
            else if ("tilesEast".equals(key)) east = integer(numberValue);
            else if ("tilesWest".equals(key)) west = integer(numberValue);
            else if ("tilesPerimeter".equals(key)) {
                perimeter = integer(numberValue).intValue();
            } else if ("isSpawnPoint".equals(key)) {
                spawnPoint = "true".equals(booleanValue);
            }
        }

        Deed build(int width, int height) {
            if (name == null || x == null || y == null || north == null
                    || south == null || east == null || west == null) {
                throw new IllegalArgumentException("incomplete deed object");
            }
            requireRange(x.intValue(), 0, width - 1, "deed X");
            requireRange(y.intValue(), 0, height - 1, "deed Y");
            requireRange(north.intValue(), 0, height, "north extent");
            requireRange(south.intValue(), 0, height, "south extent");
            requireRange(east.intValue(), 0, width, "east extent");
            requireRange(west.intValue(), 0, width, "west extent");
            requireRange(perimeter, 0, Math.max(width, height), "perimeter");
            return new Deed(name, type, mayor, allianceName, founderName,
                    motto, lastActive, guards, citizens, creationDate,
                    x.intValue(), y.intValue(),
                    north.intValue(), south.intValue(), east.intValue(),
                    west.intValue(), perimeter, spawnPoint);
        }
    }

    private static Integer integer(String value) {
        if (value == null) throw new IllegalArgumentException(
                "numeric deed field is missing");
        return Integer.valueOf(value);
    }

    private static long longInteger(String value) {
        if (value == null) throw new IllegalArgumentException(
                "numeric deed field is missing");
        return Long.parseLong(value);
    }

    private static void requireRange(int value, int minimum, int maximum,
                                     String label) {
        if (value < minimum || value > maximum) throw new IllegalArgumentException(
                label + " is outside the map contract: " + value);
    }

    private static String unescape(String value) {
        if (value == null || value.indexOf('\\') < 0) return value == null ? "" : value;
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch != '\\' || i + 1 >= value.length()) {
                result.append(ch);
                continue;
            }
            char escaped = value.charAt(++i);
            if (escaped == 'n') result.append('\n');
            else if (escaped == 'r') result.append('\r');
            else if (escaped == 't') result.append('\t');
            else if (escaped == 'b') result.append('\b');
            else if (escaped == 'f') result.append('\f');
            else if (escaped == 'u' && i + 4 < value.length()) {
                try {
                    result.append((char) Integer.parseInt(
                            value.substring(i + 1, i + 5), 16));
                    i += 4;
                } catch (NumberFormatException invalid) {
                    throw new IllegalArgumentException("invalid deed unicode escape");
                }
            } else result.append(escaped);
        }
        return result.toString();
    }
}
