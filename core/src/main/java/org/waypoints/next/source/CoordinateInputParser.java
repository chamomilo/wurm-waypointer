package org.waypoints.next.source;

import org.waypoints.next.model.WaypointCoordinate;
import org.waypoints.next.model.WaypointLayer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict parser for manual coordinates, map fragments/URLs and common /gps text. */
public final class CoordinateInputParser {
    private static final String NUMBER = "([0-9]+(?:\\.[0-9]+)?)";
    private static final Pattern FRAGMENT = Pattern.compile("^#?\\s*" + NUMBER
            + "\\s*_\\s*" + NUMBER + "\\s*$");
    private static final Pattern PLAIN = Pattern.compile("^\\s*" + NUMBER
            + "\\s*[,; ]\\s*" + NUMBER + "\\s*$");
    private static final Pattern LABELLED = Pattern.compile(
            "(?i)\\bx\\s*[:=]?\\s*" + NUMBER
                    + ".*?\\by\\s*[:=]?\\s*" + NUMBER);
    private static final Pattern TILE_PAIR = Pattern.compile(
            "(?i)\\b(?:tile|at|coordinates?)\\D{0,20}" + NUMBER
                    + "\\s*[,;/ ]\\s*" + NUMBER);
    private static final Pattern EMBEDDED_MAP_URL = Pattern.compile(
            "(?i)https?://[^\\s#]+#[0-9]+(?:\\.[0-9]+)?_[0-9]+(?:\\.[0-9]+)?");
    private static final Pattern MAP_ID = Pattern.compile("/(\\d+)/?$");

    public ParsedCoordinate parse(String input, MapBounds bounds) {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("coordinates or map link are required");
        }
        if (input.length() > 4096) throw new IllegalArgumentException("coordinate input is too long");
        String clean = input.trim();
        String serverHint = "";
        String candidate = clean;
        String kind = "coordinates";

        String mapUrl = extractSingleMapUrl(clean);
        if (mapUrl != null) {
            try {
                URI uri = new URI(mapUrl);
                candidate = uri.getFragment();
                if (candidate == null || candidate.trim().isEmpty()) {
                    throw new IllegalArgumentException("map link has no coordinate fragment");
                }
                candidate = "#" + candidate;
                serverHint = mapId(uri.getPath());
                kind = "map-url";
            } catch (URISyntaxException invalid) {
                throw new IllegalArgumentException("invalid map link", invalid);
            }
        } else if (looksLikeUrl(clean)) {
            throw new IllegalArgumentException("invalid map link or missing #X_Y fragment");
        } else if (clean.startsWith("#")) {
            kind = "map-fragment";
        } else if (containsGpsWords(clean)) {
            kind = "gps-text";
        }

        Matcher match = FRAGMENT.matcher(candidate);
        boolean matched = match.matches();
        if (!matched) {
            match = PLAIN.matcher(candidate);
            matched = match.matches();
        }
        if (!matched) {
            match = LABELLED.matcher(candidate);
            matched = match.find();
        }
        if (!matched) {
            match = TILE_PAIR.matcher(candidate);
            matched = match.find();
        }
        if (!matched) {
            throw new IllegalArgumentException("unrecognized coordinates; use X Y, X,Y, #X_Y or a map link");
        }

        double x = parseNumber(match.group(1), "X");
        double y = parseNumber(match.group(2), "Y");
        if (bounds != null) bounds.requireContains(x, y);
        WaypointLayer layer = layer(clean);
        return new ParsedCoordinate(new WaypointCoordinate(x, y, null, layer),
                serverHint, kind);
    }

    private static boolean looksLikeUrl(String value) {
        String lower = value.toLowerCase(Locale.ENGLISH);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private static String extractSingleMapUrl(String value) {
        Matcher matcher = EMBEDDED_MAP_URL.matcher(value);
        if (!matcher.find()) return null;
        String result = matcher.group();
        if (matcher.find()) {
            throw new IllegalArgumentException(
                    "clipboard contains multiple map links; copy only the intended link");
        }
        return result;
    }

    private static boolean containsGpsWords(String value) {
        String lower = value.toLowerCase(Locale.ENGLISH);
        return lower.contains("gps") || lower.contains("coordinate")
                || lower.contains("tile") || lower.contains("you are at");
    }

    private static String mapId(String path) {
        if (path == null) return "";
        Matcher match = MAP_ID.matcher(path);
        return match.find() ? match.group(1) : "";
    }

    private static WaypointLayer layer(String input) {
        String lower = input.toLowerCase(Locale.ENGLISH);
        return lower.contains("cave") || lower.contains("underground")
                || lower.contains("below ground") ? WaypointLayer.CAVE
                : WaypointLayer.SURFACE;
    }

    private static double parseNumber(String value, String label) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                throw new NumberFormatException("not finite");
            }
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException(label + " coordinate is invalid", invalid);
        }
    }
}
