package org.waypoints.next.archaeology;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Strict, fail-open parser limited to completed-report Event phrases. */
public final class ArchaeologyMessageParser {
    private static final int MAX_TEXT = 1024;
    private static final int MAX_DEED = 120;
    private static final String DEED = "(.{1," + MAX_DEED + "}?)";
    private static final Pattern READY = Pattern.compile(
            "(?:^|(?<=[.!?])\\s+)You feel confident you know exactly where "
                    + DEED
                    + " once lay, and complete the location details in the report"
                    + "(?:[.!](?=\\s|$)|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECTION = Pattern.compile(
            "^Reading details from the report, " + DEED
                    + " looks like it may have been "
                    + "(very close|nearby|close|far|quite distant|very far)"
                    + " to the (north(?:[ -]?east|[ -]?west)?|south(?:[ -]?east|[ -]?west)?|east|west)[.!]?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CACHE = Pattern.compile(
            "^As you discover (?:an? |the )?" + DEED
                    + " hidden cache,? the report is crumpled up and ruined[.!]?$",
            Pattern.CASE_INSENSITIVE);

    public ArchaeologyMessage parse(String tab, String text) {
        if (!eventTab(tab) || text == null) return null;
        String clean = normalize(text);
        if (clean.isEmpty() || clean.length() > MAX_TEXT) return null;
        Matcher matcher = READY.matcher(clean);
        if (matcher.find()) return message(ArchaeologyMessage.Kind.REPORT_READY,
                matcher.group(1), null, null, matcher.group(0).trim());
        matcher = DIRECTION.matcher(clean);
        if (matcher.matches()) {
            ArchaeologyDistanceBand band = ArchaeologyDistanceBand.parse(matcher.group(2));
            ArchaeologyDirection direction = ArchaeologyDirection.parse(matcher.group(3));
            if (band == null || direction == null) return null;
            return message(ArchaeologyMessage.Kind.DIRECTION, matcher.group(1),
                    band, direction, clean);
        }
        matcher = CACHE.matcher(clean);
        return matcher.matches() ? message(ArchaeologyMessage.Kind.CACHE_FOUND,
                matcher.group(1), null, null, clean) : null;
    }

    private static ArchaeologyMessage message(ArchaeologyMessage.Kind kind,
                                                String deed,
                                                ArchaeologyDistanceBand band,
                                                ArchaeologyDirection direction,
                                                String source) {
        String display = normalizeDeed(deed);
        if (display.isEmpty() || display.length() > MAX_DEED) return null;
        return new ArchaeologyMessage(kind, display, band, direction,
                kind.name() + ":" + source.toLowerCase(Locale.ENGLISH));
    }

    public static String normalizeDeed(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }

    public static String normalizedDeedKey(String value) {
        return normalizeDeed(value).toLowerCase(Locale.ENGLISH);
    }

    private static String normalize(String value) {
        String clean = value.trim().replace('\u2018', '\'').replace('\u2019', '\'')
                .replace('\u201c', '"').replace('\u201d', '"')
                .replaceAll("\\s+", " ");
        // ChatManagerManager adds the visible timestamp before forwarding the
        // message to ChatPanelComponent, where the client hook observes it.
        return clean.replaceFirst("^\\[[0-2]?\\d:[0-5]\\d:[0-5]\\d\\]\\s*", "");
    }

    private static boolean eventTab(String tab) {
        if (tab == null) return false;
        String clean = tab.trim();
        if (clean.startsWith(":")) clean = clean.substring(1);
        return "event".equalsIgnoreCase(clean);
    }
}
